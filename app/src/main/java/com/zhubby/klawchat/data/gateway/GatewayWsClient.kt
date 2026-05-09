package com.zhubby.klawchat.data.gateway

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface GatewayConnectionState {
    data object Disconnected : GatewayConnectionState
    data object Connecting : GatewayConnectionState
    data object Initialized : GatewayConnectionState
    data class Failed(val message: String) : GatewayConnectionState
}

/**
 * Gateway WebSocket client using Ktor's [ws]/[wss] functions.
 *
 * Ktor's WebSocket API is block-based: [ws] enters a suspend block where
 * `this` is a `DefaultClientWebSocketSession`. We cannot store the session
 * outside the block. Instead, we launch the entire ws block in a coroutine
 * and relay frames to internal [SharedFlow]s.
 *
 * Sending is done through a channel-based approach: the ws block reads
 * outgoing frames from [_outgoing] and sends them, while incoming frames
 * are deserialized and emitted to [_events]/[_reverseRequests].
 */
class GatewayWsClient(
    private val httpClient: HttpClient = HttpClient {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    },
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private var connectionJob: kotlinx.coroutines.Job? = null
    private var wsConnected = CompletableDeferred<Unit>()

    private val _outgoing = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _events = MutableSharedFlow<GatewayServerFrame.Notification>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GatewayServerFrame.Notification> = _events.asSharedFlow()

    private val _reverseRequests = MutableSharedFlow<GatewayServerFrame.ReverseRequest>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val reverseRequests: SharedFlow<GatewayServerFrame.ReverseRequest> = _reverseRequests.asSharedFlow()

    private val _connectionStates = MutableSharedFlow<GatewayConnectionState>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val connectionStates: SharedFlow<GatewayConnectionState> = _connectionStates.asSharedFlow()

    /**
     * Connect to the gateway WebSocket endpoint and perform the v1 initialize handshake.
     */
    suspend fun connect(endpoint: GatewayEndpoint) {
        closeSocket()
        wsConnected = CompletableDeferred()
        _connectionStates.tryEmit(GatewayConnectionState.Connecting)

        connectionJob = scope.launch {
            runCatching {
                // Determine ws vs wss based on URL scheme
                val wsUrl = endpoint.webSocketUrl
                val isSecure = wsUrl.startsWith("wss://")
                if (isSecure) {
                    httpClient.wss(wsUrl, request = {
                        if (endpoint.token.isNotBlank()) {
                            header("Authorization", "Bearer ${endpoint.token}")
                        }
                    }, block = {
                        handleSession(endpoint)
                    })
                } else {
                    httpClient.ws(wsUrl, request = {
                        if (endpoint.token.isNotBlank()) {
                            header("Authorization", "Bearer ${endpoint.token}")
                        }
                    }, block = {
                        handleSession(endpoint)
                    })
                }
            }.onFailure { error ->
                if (scope.isActive) {
                    _connectionStates.tryEmit(
                        GatewayConnectionState.Failed(error.message ?: "WebSocket connection failed"),
                    )
                    wsConnected.completeExceptionally(error)
                    pendingResults.values.forEach { it.completeExceptionally(error) }
                    pendingResults.clear()
                }
            }
        }

        // Wait for WebSocket to be open before returning
        withTimeout(15_000) { wsConnected.await() }
    }

    suspend fun connectAndWait(endpoint: GatewayEndpoint, timeoutMs: Long = 15_000) {
        withTimeout(timeoutMs) {
            connect(endpoint)
        }
    }

    /**
     * Inside the ws() block, this function:
     * 1. Signals that the connection is open
     * 2. Starts outgoing frame writer (reads from _outgoing and sends text frames)
     * 3. Reads incoming frames and processes them
     * 4. Performs v1 initialize handshake
     */
    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.handleSession(
        endpoint: GatewayEndpoint,
    ) {
        // Signal connected
        wsConnected.complete(Unit)

        // Start outgoing frame writer
        val writerJob = launch {
            _outgoing.collect { text ->
                send(Frame.Text(text))
            }
        }

        // Perform v1 initialize handshake
        runCatching { initialize() }.onFailure { error ->
            _connectionStates.tryEmit(GatewayConnectionState.Failed(error.message ?: "Initialize failed"))
            writerJob.cancel()
            return
        }

        _connectionStates.tryEmit(GatewayConnectionState.Initialized)

        // Read incoming frames
        try {
            for (frame in incoming) {
                if (!isActive) break
                when (frame) {
                    is Frame.Text -> handleTextFrame(frame.readText())
                    is Frame.Binary -> {
                        // v1 spec: binary frames are invalid, ignore
                    }

                    else -> { /* Ping/Pong handled by Ktor */
                    }
                }
            }
        } catch (e: Exception) {
            if (scope.isActive) {
                _connectionStates.tryEmit(GatewayConnectionState.Failed(e.message ?: "WebSocket read failed"))
                pendingResults.values.forEach { it.completeExceptionally(e) }
                pendingResults.clear()
            }
        } finally {
            writerJob.cancel()
            if (scope.isActive) {
                _connectionStates.tryEmit(GatewayConnectionState.Disconnected)
            }
        }
    }

    /** v1 initialize handshake: send initialize request, then initialized notification */
    private suspend fun initialize() {
        sendAndWaitResult(
            method = "initialize",
            params = buildJsonObject {
                put("client_info", buildJsonObject {
                    put("name", "klawchat-android")
                    put("title", "KlawChat Android")
                    put("version", "1.0")
                })
                put("capabilities", buildJsonObject {
                    put("protocol_version", "v1")
                    put("experimental", false)
                    put("turns", true)
                    put("items", true)
                    put("tools", true)
                    put("approvals", true)
                    put("server_requests", true)
                    put("cancellation", true)
                    put("steering", true)
                    put("schema", true)
                })
            },
        )
        sendNotification("initialized")
    }

    suspend fun sendAndWaitResult(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
        timeoutMs: Long = 15_000,
    ): JsonElement {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonElement>()
        pendingResults[id] = deferred
        sendRequest(id = id, method = method, params = params)
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pendingResults.remove(id)
        }
    }

    fun send(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ): String {
        val id = UUID.randomUUID().toString()
        scope.launch { sendRequest(id = id, method = method, params = params) }
        return id
    }

    suspend fun sendNotification(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ) {
        val frame = GatewayClientNotification(method = method, params = params)
        _outgoing.emit(GatewayJson.encodeToString(GatewayClientNotification.serializer(), frame))
    }

    private suspend fun sendRequest(id: String, method: String, params: JsonObject) {
        val frame = GatewayClientRequest(id = id, method = method, params = params)
        _outgoing.emit(GatewayJson.encodeToString(GatewayClientRequest.serializer(), frame))
    }

    override fun close() {
        closeSocket()
        scope.cancel()
        httpClient.close()
    }

    private fun handleTextFrame(text: String) {
        runCatching {
            GatewayJson.decodeFromString(GatewayServerFrameDeserializer, text)
        }.onSuccess { frame ->
            when (frame) {
                is GatewayServerFrame.Result -> pendingResults.remove(frame.id)?.complete(frame.result)
                is GatewayServerFrame.Error -> frame.id?.let { id ->
                    pendingResults.remove(id)?.completeExceptionally(
                        IllegalStateException("${frame.error.code}: ${frame.error.message}"),
                    )
                }

                is GatewayServerFrame.Notification -> scope.launch { _events.emit(frame) }
                is GatewayServerFrame.ReverseRequest -> scope.launch { _reverseRequests.emit(frame) }
            }
        }.onFailure {
            // Log but don't crash on unrecognized frames
        }
    }

    private fun closeSocket() {
        connectionJob?.cancel()
        connectionJob = null
        wsConnected.cancel()
        pendingResults.values.forEach { it.cancel() }
        pendingResults.clear()
        _connectionStates.tryEmit(GatewayConnectionState.Disconnected)
    }
}
