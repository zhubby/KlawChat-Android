package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayFrameTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun encodesMethodFrameWithParams() {
        val frame = GatewayClientFrame.Method(
            id = "42",
            method = "session.submit",
            params = mapOf("input" to JsonPrimitive("hello")),
        )

        val encoded = json.encodeToString(GatewayClientFrame.serializer(), frame)
        val tree = json.parseToJsonElement(encoded).jsonObject

        assertEquals("method", tree["type"]?.toString()?.trim('"'))
        assertEquals("42", tree["id"]?.toString()?.trim('"'))
        assertEquals("session.submit", tree["method"]?.toString()?.trim('"'))
        assertEquals("hello", tree["params"]?.jsonObject?.get("input")?.toString()?.trim('"'))
    }

    @Test
    fun decodesEventFrame() {
        val decoded = json.decodeFromString(
            GatewayServerFrame.serializer(),
            """{"type":"event","event":"session.stream.delta","payload":{"content":"Hi"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Event)
        val event = decoded as GatewayServerFrame.Event
        assertEquals("session.stream.delta", event.event)
        assertEquals("Hi", event.payload["content"]?.toString()?.trim('"'))
    }

    @Test
    fun parsesMessageAttachmentsFromPayload() {
        val payload = json.parseToJsonElement(
            """
            {
              "session_key": "session-1",
              "message_id": "message-1",
              "role": "assistant",
              "content": "see file",
              "attachments": [
                {
                  "archive_id": "archive-1",
                  "filename": "log.txt",
                  "mime_type": "text/plain",
                  "size": 12
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val message = payload.chatMessage()

        assertEquals("archive-1", message?.attachments?.single()?.archiveId)
        assertEquals("log.txt", message?.attachments?.single()?.filename)
    }

    @Test
    fun parsesWrappedMessagePayload() {
        val payload = json.parseToJsonElement(
            """
            {
              "message": {
                "session_key": "session-1",
                "message_id": "message-1",
                "role": "assistant",
                "content": "wrapped",
                "attachments": [
                  { "archive_id": "archive-1", "filename": "wrapped.txt" }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val message = payload.chatMessage()

        assertEquals("wrapped", message?.content)
        assertEquals("wrapped.txt", message?.attachments?.single()?.filename)
    }

    @Test
    fun parsesHistoryMessagesResultFromGateway() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "session-1",
              "messages": [
                {
                  "message_id": "message-1",
                  "role": "user",
                  "content": "older message",
                  "timestamp_ms": 123
                }
              ],
              "has_more": true,
              "oldest_loaded_message_id": "message-1"
            }
            """.trimIndent(),
        ).jsonObject

        val history = result.historyResult()

        assertEquals("older message", history.messages.single().content)
        assertEquals(true, history.hasMore)
        assertEquals("message-1", history.oldestLoadedMessageId)
    }

    @Test
    fun readsStreamDeltaFromGatewayDeltaField() {
        val payload = json.parseToJsonElement(
            """{"session_key":"session-1","request_id":"request-1","delta":"hello"}""",
        ).jsonObject

        assertEquals("hello", payload.streamDeltaText())
    }

    @Test
    fun readsStreamDeltaFromNestedGatewayDeltaContent() {
        val payload = json.parseToJsonElement(
            """{"session_key":"session-1","request_id":"request-1","delta":{"content":"hello"}}""",
        ).jsonObject

        assertEquals("hello", payload.streamDeltaText())
    }

    @Test
    fun readsStreamDeltaFromTextField() {
        val payload = json.parseToJsonElement(
            """{"session_key":"session-1","request_id":"request-1","text":"hello"}""",
        ).jsonObject

        assertEquals("hello", payload.streamDeltaText())
    }

    @Test
    fun parsesSubmitResultResponseAsAssistantMessage() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "session-1",
              "message_id": "assistant-1",
              "request_id": "request-1",
              "response": {
                "content": "assistant reply"
              },
              "timestamp_ms": 1234
            }
            """.trimIndent(),
        ).jsonObject

        val message = result.chatMessage()

        assertEquals("assistant", message?.role)
        assertEquals("assistant reply", message?.content)
        assertEquals("request-1", message?.requestId)
    }

    @Test
    fun parsesAssistantResponseWithoutMessageId() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "session-1",
              "response": {
                "content": "assistant reply without id"
              }
            }
            """.trimIndent(),
        ).jsonObject

        val message = result.chatMessage()

        assertEquals("assistant", message?.role)
        assertEquals("assistant reply without id", message?.content)
        assertEquals("session-1", message?.sessionKey)
    }

    @Test
    fun generatedAssistantIdsAreUniqueWithoutMessageId() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "session-1",
              "response": {
                "content": "same reply"
              }
            }
            """.trimIndent(),
        ).jsonObject

        val first = result.chatMessage()
        val second = result.chatMessage()

        assert(first != null)
        assert(second != null)
        assert(first?.id != second?.id)
    }
}
