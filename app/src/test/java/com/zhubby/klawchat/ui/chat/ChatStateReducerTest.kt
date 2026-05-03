package com.zhubby.klawchat.ui.chat

import com.zhubby.klawchat.data.gateway.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStateReducerTest {
    @Test
    fun localUserMessageIsAddedToSelectedSession() {
        val state = ChatUiState(selectedSessionKey = "session-1")

        val updated = ChatStateReducer.reduceLocalUserMessage(
            state = state,
            sessionKey = "session-1",
            content = "hello",
            attachments = emptyList(),
            nowMs = 123,
        )

        val message = updated.messagesBySession["session-1"]?.single()
        assertEquals("user", message?.role)
        assertEquals("hello", message?.content)
        assertEquals(123L, message?.timestampMs)
    }

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
    fun streamDeltaReusesExistingSessionBubbleWhenRequestIdChanges() {
        val state = ChatUiState(selectedSessionKey = "session-1")

        val first = ChatStateReducer.reduceStreamDelta(
            state = state,
            sessionKey = "session-1",
            requestId = "request-1",
            content = "hello",
        )
        val second = ChatStateReducer.reduceStreamDelta(
            state = first,
            sessionKey = "session-1",
            requestId = "request-2",
            content = " world",
        )

        assertEquals(1, second.streamingMessages.size)
        assertEquals("hello world", second.streamingMessages.values.single().content)
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

    @Test
    fun completedMessageClearsSessionStreamWhenRequestIdChangedDuringStreaming() {
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
            requestId = "request-2",
        )

        val updated = ChatStateReducer.reduceMessage(state, message)

        assertEquals(emptyMap<String, StreamingMessage>(), updated.streamingMessages)
        assertEquals(listOf(message), updated.messagesBySession["session-1"])
    }

    @Test
    fun streamClearRemovesSessionStreamWhenRequestIdChangedDuringStreaming() {
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

        val updated = ChatStateReducer.reduceStreamClear(
            state = state,
            sessionKey = "session-1",
            requestId = "request-2",
        )

        assertEquals(emptyMap<String, StreamingMessage>(), updated.streamingMessages)
    }

    @Test
    fun completedMessageWithoutRequestIdClearsSessionStreamingPlaceholder() {
        val state = ChatUiState(
            selectedSessionKey = "session-1",
            streamingMessages = mapOf(
                "session-1" to StreamingMessage(
                    sessionKey = "session-1",
                    requestId = "session-1",
                    content = "draft",
                ),
            ),
        )
        val message = ChatMessage(
            id = "generated",
            sessionKey = "session-1",
            role = "assistant",
            content = "final",
        )

        val updated = ChatStateReducer.reduceMessage(state, message)

        assertNull(updated.streamingMessages["session-1"])
        assertEquals(listOf(message), updated.messagesBySession["session-1"])
    }

    @Test
    fun historyPrependsAndKeepsExistingLiveMessages() {
        val liveMessage = ChatMessage(
            id = "message-live",
            sessionKey = "session-1",
            role = "assistant",
            content = "live",
        )
        val historyMessage = ChatMessage(
            id = "message-history",
            sessionKey = "session-1",
            role = "user",
            content = "history",
        )
        val state = ChatUiState(
            selectedSessionKey = "session-1",
            messagesBySession = mapOf("session-1" to listOf(liveMessage)),
        )

        val updated = ChatStateReducer.reduceHistory(
            state = state,
            sessionKey = "session-1",
            history = listOf(historyMessage),
        )

        assertEquals(listOf(historyMessage, liveMessage), updated.messagesBySession["session-1"])
    }

    @Test
    fun historyDoesNotDuplicateExistingMessageIds() {
        val existing = ChatMessage(
            id = "message-1",
            sessionKey = "session-1",
            role = "assistant",
            content = "existing",
        )
        val duplicateHistory = existing.copy(content = "older duplicate")
        val state = ChatUiState(
            selectedSessionKey = "session-1",
            messagesBySession = mapOf("session-1" to listOf(existing)),
        )

        val updated = ChatStateReducer.reduceHistory(
            state = state,
            sessionKey = "session-1",
            history = listOf(duplicateHistory),
        )

        assertEquals(listOf(existing), updated.messagesBySession["session-1"])
    }

    @Test
    fun historyBatchDeduplicatesRepeatedMessageIds() {
        val first = ChatMessage(
            id = "message-1",
            sessionKey = "session-1",
            role = "user",
            content = "first",
        )
        val repeated = first.copy(content = "repeated")
        val state = ChatUiState(selectedSessionKey = "session-1")

        val updated = ChatStateReducer.reduceHistory(
            state = state,
            sessionKey = "session-1",
            history = listOf(first, repeated),
        )

        assertEquals(listOf(first), updated.messagesBySession["session-1"])
    }
}
