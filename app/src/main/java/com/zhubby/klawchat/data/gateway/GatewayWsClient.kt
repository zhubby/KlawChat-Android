package com.zhubby.klawchat.data.gateway

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface GatewayConnectionState {
    data object Disconnected : GatewayConnectionState
    data object Connecting : GatewayConnectionState
    data object Connected : GatewayConnectionState
    data class Failed(val message: String) : GatewayConnectionState
}

class GatewayWsClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private var webSocket: WebSocket? = null
    private var openDeferred: CompletableDeferred<Unit>? = null

    private val _events = MutableSharedFlow<GatewayServerFrame.Event>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GatewayServerFrame.Event> = _events.asSharedFlow()

    private val _connectionStates = MutableSharedFlow<GatewayConnectionState>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val connectionStates: SharedFlow<GatewayConnectionState> = _connectionStates.asSharedFlow()

    fun connect(endpoint: GatewayEndpoint) {
        closeSocket()
        openDeferred = CompletableDeferred()
        _connectionStates.tryEmit(GatewayConnectionState.Connecting)
        val request = Request.Builder()
            .url(endpoint.webSocketUrl)
            .apply {
                if (endpoint.token.isNotBlank()) {
                    header("Authorization", "Bearer ${endpoint.token}")
                }
            }
            .build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    suspend fun connectAndWait(endpoint: GatewayEndpoint, timeoutMs: Long = 15_000) {
        connect(endpoint)
        withTimeout(timeoutMs) {
            checkNotNull(openDeferred).await()
        }
    }

    suspend fun sendAndWaitResult(
        method: String,
        params: Map<String, JsonElement> = emptyMap(),
        timeoutMs: Long = 15_000,
    ): JsonElement {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonElement>()
        pendingResults[id] = deferred
        sendFrame(GatewayClientFrame.Method(id = id, method = method, params = params))
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pendingResults.remove(id)
        }
    }

    fun send(
        method: String,
        params: Map<String, JsonElement> = emptyMap(),
    ): String {
        val id = UUID.randomUUID().toString()
        sendFrame(GatewayClientFrame.Method(id = id, method = method, params = params))
        return id
    }

    override fun close() {
        closeSocket()
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
    }

    private fun sendFrame(frame: GatewayClientFrame.Method) {
        val socket = checkNotNull(webSocket) { "Gateway WebSocket is not connected." }
        socket.send(GatewayJson.encodeToString(GatewayClientFrame.serializer(), frame))
    }

    private fun closeSocket() {
        webSocket?.close(1000, "Closing")
        webSocket = null
        pendingResults.values.forEach { it.cancel() }
        pendingResults.clear()
        openDeferred?.cancel()
        openDeferred = null
        _connectionStates.tryEmit(GatewayConnectionState.Disconnected)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionStates.tryEmit(GatewayConnectionState.Connected)
            openDeferred?.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                GatewayJson.decodeFromString(GatewayServerFrame.serializer(), text)
            }.onSuccess { frame ->
                when (frame) {
                    is GatewayServerFrame.Result -> pendingResults.remove(frame.id)?.complete(frame.result)
                    is GatewayServerFrame.Error -> frame.id?.let { id ->
                        pendingResults.remove(id)?.completeExceptionally(
                            IllegalStateException("${frame.error.code}: ${frame.error.message}"),
                        )
                    }
                    is GatewayServerFrame.Event -> scope.launch { _events.emit(frame) }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionStates.tryEmit(GatewayConnectionState.Failed(t.message ?: "WebSocket failed"))
            openDeferred?.completeExceptionally(t)
            pendingResults.values.forEach { it.completeExceptionally(t) }
            pendingResults.clear()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionStates.tryEmit(GatewayConnectionState.Disconnected)
        }
    }
}
