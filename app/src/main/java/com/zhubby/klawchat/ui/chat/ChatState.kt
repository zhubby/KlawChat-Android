package com.zhubby.klawchat.ui.chat

import com.zhubby.klawchat.data.gateway.ChatMessage
import com.zhubby.klawchat.data.gateway.ArchiveAttachment
import com.zhubby.klawchat.data.gateway.Provider
import com.zhubby.klawchat.data.gateway.WorkspaceSession

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
    fun reduceStreamDelta(
        state: ChatUiState,
        sessionKey: String,
        requestId: String,
        content: String,
    ): ChatUiState {
        val existing = state.streamingMessages[requestId]
        val next = StreamingMessage(
            sessionKey = sessionKey,
            requestId = requestId,
            content = (existing?.content.orEmpty() + content),
        )
        return state.copy(streamingMessages = state.streamingMessages + (requestId to next))
    }

    fun reduceMessage(state: ChatUiState, message: ChatMessage): ChatUiState {
        val currentMessages = state.messagesBySession[message.sessionKey].orEmpty()
        val withoutDuplicate = currentMessages.filterNot { it.id == message.id }
        val nextMessages = withoutDuplicate + message
        val requestId = message.requestId
        val nextStreaming = if (requestId == null) {
            state.streamingMessages
        } else {
            state.streamingMessages - requestId
        }
        return state.copy(
            messagesBySession = state.messagesBySession + (message.sessionKey to nextMessages),
            streamingMessages = nextStreaming,
        )
    }
}
