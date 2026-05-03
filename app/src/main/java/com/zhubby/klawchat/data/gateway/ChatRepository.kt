package com.zhubby.klawchat.data.gateway

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.Closeable

data class BootstrapResult(
    val sessions: List<WorkspaceSession>,
    val activeSessionKey: String?,
)

data class HistoryResult(
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
    val oldestLoadedMessageId: String?,
)

class ChatRepository(
    private val wsClient: GatewayWsClient,
) : Closeable {
    val events: SharedFlow<GatewayServerFrame.Event> = wsClient.events
    val connectionStates: SharedFlow<GatewayConnectionState> = wsClient.connectionStates

    suspend fun connect(endpoint: GatewayEndpoint) = wsClient.connectAndWait(endpoint)

    suspend fun ping(): Boolean {
        val result = wsClient.sendAndWaitResult("session.ping").jsonObject
        return result.boolean("ok") ?: true
    }

    suspend fun bootstrap(): BootstrapResult {
        val result = wsClient.sendAndWaitResult("workspace.bootstrap").jsonObject
        val sessions = result.objectList("sessions").mapNotNull { session ->
            runCatching { GatewayJson.decodeFromJsonElement<WorkspaceSession>(session) }.getOrNull()
        }
        return BootstrapResult(
            sessions = sessions,
            activeSessionKey = result.string("active_session_key"),
        )
    }

    suspend fun listProviders(): ProviderCatalog {
        val result = wsClient.sendAndWaitResult("provider.list")
        return runCatching { GatewayJson.decodeFromJsonElement<ProviderCatalog>(result) }
            .getOrDefault(ProviderCatalog())
    }

    suspend fun createSession(): WorkspaceSession {
        val result = wsClient.sendAndWaitResult("session.create").jsonObject
        val payload = result["session"] ?: result
        return GatewayJson.decodeFromJsonElement(payload)
    }

    suspend fun updateSession(
        sessionKey: String,
        title: String,
        modelProvider: String?,
        model: String?,
    ) {
        wsClient.sendAndWaitResult(
            method = "session.update",
            params = buildJsonObject {
                put("session_key", sessionKey)
                put("title", title)
                modelProvider?.let { put("model_provider", it) }
                model?.let { put("model", it) }
            },
        )
    }

    suspend fun deleteSession(sessionKey: String) {
        wsClient.sendAndWaitResult(
            method = "session.delete",
            params = buildJsonObject { put("session_key", sessionKey) },
        )
    }

    suspend fun subscribe(sessionKey: String) {
        wsClient.sendAndWaitResult(
            method = "session.subscribe",
            params = buildJsonObject { put("session_key", sessionKey) },
        )
    }

    suspend fun loadHistory(
        sessionKey: String,
        beforeMessageId: String? = null,
        limit: Int = 50,
    ): HistoryResult {
        val result = wsClient.sendAndWaitResult(
            method = "session.history.load",
            params = buildJsonObject {
                put("session_key", sessionKey)
                put("limit", limit)
                put("before_message_id", nullableString(beforeMessageId))
            },
        ).jsonObject
        val history = result.objectList("history").mapNotNull(JsonObject::chatMessage)
        return HistoryResult(
            messages = history,
            hasMore = result.boolean("has_more") ?: false,
            oldestLoadedMessageId = result.string("oldest_loaded_message_id"),
        )
    }

    fun submit(
        sessionKey: String,
        input: String,
        stream: Boolean,
        modelProvider: String?,
        model: String?,
        attachments: List<ArchiveAttachment> = emptyList(),
    ): String = wsClient.send(
        method = "session.submit",
        params = buildJsonObject {
            put("session_key", sessionKey)
            put("chat_id", sessionKey)
            put("input", input)
            put("stream", stream)
            modelProvider?.let { put("model_provider", it) }
            model?.let { put("model", it) }
            if (attachments.isNotEmpty()) {
                put("attachments", GatewayJson.encodeToJsonElement(attachments))
                put("archive_id", attachments.first().archiveId)
            }
        },
    )

    override fun close() {
        wsClient.close()
    }
}
