package com.zhubby.klawchat.data.gateway

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.Closeable

data class BootstrapResult(
    val sessions: List<WorkspaceSession>,
    val activeSessionKey: String?,
)

class ChatRepository(
    private val wsClient: GatewayWsClient,
) : Closeable {
    val events: SharedFlow<GatewayServerFrame.Notification> = wsClient.events
    val reverseRequests: SharedFlow<GatewayServerFrame.ReverseRequest> = wsClient.reverseRequests
    val connectionStates: SharedFlow<GatewayConnectionState> = wsClient.connectionStates

    suspend fun connect(endpoint: GatewayEndpoint) = wsClient.connectAndWait(endpoint)

    /** v1 bootstrap: session/list + provider/list after initialize */
    suspend fun bootstrap(): BootstrapResult {
        val sessionsResult = wsClient.sendAndWaitResult("session/list").jsonObject
        val sessions = sessionsResult.objectList("sessions").mapNotNull { it.toWorkspaceSession() }
        val activeSessionKey = sessionsResult.string("active_session_key")
        return BootstrapResult(
            sessions = sessions,
            activeSessionKey = activeSessionKey,
        )
    }

    suspend fun listProviders(): ProviderCatalog {
        val result = wsClient.sendAndWaitResult("provider/list")
        return runCatching {
            GatewayJson.decodeFromJsonElement(ProviderCatalog.serializer(), result)
        }.getOrDefault(ProviderCatalog())
    }

    suspend fun createSession(): WorkspaceSession {
        val result = wsClient.sendAndWaitResult("session/create").jsonObject
        val payload = result["session"]?.jsonObject ?: result
        return GatewayJson.decodeFromJsonElement(WorkspaceSession.serializer(), payload)
    }

    suspend fun updateSession(
        sessionKey: String,
        title: String,
        modelProvider: String?,
        model: String?,
    ) {
        wsClient.sendAndWaitResult(
            method = "session/update",
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
            method = "session/delete",
            params = buildJsonObject { put("session_key", sessionKey) },
        )
    }

    suspend fun subscribe(sessionKey: String) {
        wsClient.sendAndWaitResult(
            method = "session/subscribe",
            params = buildJsonObject { put("session_key", sessionKey) },
        )
    }

    suspend fun unsubscribe(sessionKey: String) {
        wsClient.sendAndWaitResult(
            method = "session/unsubscribe",
            params = buildJsonObject { put("session_key", sessionKey) },
        )
    }

    /** v1: thread/history (alias: thread/read) */
    suspend fun loadHistory(
        sessionKey: String,
        beforeMessageId: String? = null,
        limit: Int = 50,
    ): HistoryResult {
        val result = wsClient.sendAndWaitResult(
            method = "thread/history",
            params = buildJsonObject {
                put("session_key", sessionKey)
                put("limit", limit)
                put("before_message_id", nullableString(beforeMessageId))
            },
        ).jsonObject
        return result.historyResult()
    }

    /** v1: turn/start — replaces session.submit */
    suspend fun turnStart(
        sessionKey: String,
        input: String,
        stream: Boolean,
        modelProvider: String?,
        model: String?,
        attachments: List<ArchiveAttachment> = emptyList(),
    ): ChatMessage? {
        val contentBlocks = mutableListOf<ContentBlock>()
        contentBlocks.add(ContentBlock(type = "text", text = input))
        for (attachment in attachments) {
            contentBlocks.add(
                ContentBlock(
                    type = "attachment",
                    archiveId = attachment.archiveId,
                    filename = attachment.filename,
                )
            )
        }
        val params = buildJsonObject {
            put("session_id", sessionKey)
            put("thread_id", sessionKey)
            put("input", GatewayJson.encodeToJsonElement(contentBlocks))
            put("stream", stream)
            modelProvider?.let { put("model_provider", it) }
            model?.let { put("model", it) }
            if (attachments.isNotEmpty()) {
                put("archive_id", attachments.first().archiveId)
            }
        }
        return if (stream) {
            wsClient.send(method = "turn/start", params = params)
            null
        } else {
            wsClient.sendAndWaitResult(method = "turn/start", params = params)
                .jsonObject
                .chatMessage(fallbackSessionKey = sessionKey)
        }
    }

    /** Legacy compatibility: submit → delegates to turnStart */
    suspend fun submit(
        sessionKey: String,
        input: String,
        stream: Boolean,
        modelProvider: String?,
        model: String?,
        attachments: List<ArchiveAttachment> = emptyList(),
    ): ChatMessage? = turnStart(
        sessionKey = sessionKey,
        input = input,
        stream = stream,
        modelProvider = modelProvider,
        model = model,
        attachments = attachments,
    )

    /** v1: turn/cancel */
    suspend fun turnCancel(
        sessionKey: String,
        turnId: String,
    ) {
        wsClient.sendAndWaitResult(
            method = "turn/cancel",
            params = buildJsonObject {
                put("session_id", sessionKey)
                put("turn_id", turnId)
            },
        )
    }

    override fun close() {
        wsClient.close()
    }
}
