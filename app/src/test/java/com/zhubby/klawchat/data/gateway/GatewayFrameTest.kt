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
}
