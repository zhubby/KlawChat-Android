package com.zhubby.klawchat.ui.chat

import com.zhubby.klawchat.data.gateway.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStateReducerTest {
    @Test
    fun streamDeltaAppendsContentForSession() {
        val state = ChatUiState(selectedSessionKey = "session-1")

        val updated = ChatStateReducer.reduceStreamDelta(
            state = state,
            sessionKey = "session-1",
            requestId = "request-1",
            content = "hello",
        )

        assertEquals("hello", updated.streamingMessages["request-1"]?.content)
    }

    @Test
    fun completedMessageClearsMatchingStreamPlaceholder() {
        val state = ChatUiState(
            selectedSessionKey = "session-1",
            streamingMessages = mapOf(
                "request-1" to StreamingMessage(
                    sessionKey = "session-1",
                    requestId = "request-1",
                    content = "draft",
                ),
            ),
        )
        val message = ChatMessage(
            id = "message-1",
            sessionKey = "session-1",
            role = "assistant",
            content = "final",
            timestampMs = 10,
            requestId = "request-1",
        )

        val updated = ChatStateReducer.reduceMessage(state, message)

        assertNull(updated.streamingMessages["request-1"])
        assertEquals(listOf(message), updated.messagesBySession["session-1"])
    }
}
