package com.zhubby.klawchat.ui.chat

import com.zhubby.klawchat.data.gateway.ChatMessage
import com.zhubby.klawchat.data.gateway.ArchiveAttachment
import com.zhubby.klawchat.data.gateway.Provider
import com.zhubby.klawchat.data.gateway.WorkspaceSession
import java.util.UUID

enum class ConnectionStatus {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

data class StreamingMessage(
    val sessionKey: String,
    val requestId: String,
    val content: String,
)

data class ChatUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val statusMessage: String? = null,
    val sessions: List<WorkspaceSession> = emptyList(),
    val selectedSessionKey: String? = null,
    val messagesBySession: Map<String, List<ChatMessage>> = emptyMap(),
    val streamingMessages: Map<String, StreamingMessage> = emptyMap(),
    val pendingAttachments: List<ArchiveAttachment> = emptyList(),
    val providers: List<Provider> = emptyList(),
    val defaultProvider: String? = null,
    val streamEnabled: Boolean = true,
    val isBusy: Boolean = false,
) {
    val selectedSession: WorkspaceSession?
        get() = sessions.firstOrNull { it.sessionKey == selectedSessionKey }

    val selectedMessages: List<ChatMessage>
        get() = messagesBySession[selectedSessionKey].orEmpty()
}

object ChatStateReducer {
    fun reduceLocalUserMessage(
        state: ChatUiState,
        sessionKey: String,
        content: String,
        attachments: List<ArchiveAttachment>,
        nowMs: Long = System.currentTimeMillis(),
    ): ChatUiState {
        return reduceMessage(
            state = state,
            message = ChatMessage(
                id = "local:user:${UUID.randomUUID()}",
                sessionKey = sessionKey,
                role = "user",
                content = content,
                timestampMs = nowMs,
                attachments = attachments,
            ),
        )
    }

    fun reduceStreamDelta(
        state: ChatUiState,
        sessionKey: String,
        requestId: String,
        content: String,
    ): ChatUiState {
        val hasSnapshotMessage = state.messagesBySession[sessionKey].orEmpty().any { message ->
            message.role.equals("assistant", ignoreCase = true) && message.requestId == requestId
        }
        if (hasSnapshotMessage) {
            return state
        }
        val streamKey = state.streamingMessages.values
            .firstOrNull { it.sessionKey == sessionKey }
            ?.requestId
            ?: requestId
        val existing = state.streamingMessages[streamKey]
        val next = StreamingMessage(
            sessionKey = sessionKey,
            requestId = streamKey,
            content = (existing?.content.orEmpty() + content),
        )
        return state.copy(streamingMessages = state.streamingMessages + (streamKey to next))
    }

    fun reduceMessage(state: ChatUiState, message: ChatMessage): ChatUiState {
        val currentMessages = state.messagesBySession[message.sessionKey].orEmpty()
        val withoutDuplicate = currentMessages.filterNot { existing ->
            existing.id == message.id || (
                message.role.equals("assistant", ignoreCase = true) &&
                    message.requestId != null &&
                    existing.role.equals("assistant", ignoreCase = true) &&
                    existing.requestId == message.requestId
                )
        }
        val nextMessages = withoutDuplicate + message
        val requestId = message.requestId
        val nextStreaming = if (requestId == null || state.streamingMessages.values.any { it.sessionKey == message.sessionKey }) {
            state.streamingMessages.filterValues { it.sessionKey != message.sessionKey }
        } else {
            state.streamingMessages - requestId
        }
        return state.copy(
            messagesBySession = state.messagesBySession + (message.sessionKey to nextMessages),
            streamingMessages = nextStreaming,
        )
    }

    fun reduceStreamClear(
        state: ChatUiState,
        sessionKey: String?,
        requestId: String?,
    ): ChatUiState {
        val nextStreaming = state.streamingMessages.filter { (key, stream) ->
            key != requestId && (sessionKey == null || stream.sessionKey != sessionKey)
        }
        return state.copy(streamingMessages = nextStreaming)
    }

    fun reduceHistory(
        state: ChatUiState,
        sessionKey: String,
        history: List<ChatMessage>,
    ): ChatUiState {
        val current = state.messagesBySession[sessionKey].orEmpty()
        val seenIds = current.map { it.id }.toMutableSet()
        val newHistory = history.filter { message ->
            seenIds.add(message.id)
        }
        return state.copy(
            messagesBySession = state.messagesBySession + (sessionKey to (newHistory + current)),
        )
    }
}
