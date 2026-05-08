package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayFrameTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun encodesClientRequestFrameWithParams() {
        val frame = GatewayClientRequest(
            id = "42",
            method = "turn/start",
            params = buildJsonObject { put("input", "hello") },
        )

        val encoded = GatewayJson.encodeToString(GatewayClientRequest.serializer(), frame)
        val tree = json.parseToJsonElement(encoded).jsonObject

        assertEquals("42", tree["id"]?.toString()?.trim('"'))
        assertEquals("turn/start", tree["method"]?.toString()?.trim('"'))
        assertEquals("hello", tree["params"]?.jsonObject?.get("input")?.toString()?.trim('"'))
    }

    @Test
    fun encodesClientNotificationFrameWithoutId() {
        val frame = GatewayClientNotification(
            method = "initialized",
            params = JsonObject(emptyMap()),
        )

        val encoded = GatewayJson.encodeToString(GatewayClientNotification.serializer(), frame)
        val tree = json.parseToJsonElement(encoded).jsonObject

        assertTrue(tree.containsKey("method"))
        assertTrue(!tree.containsKey("id"))
        assertEquals("initialized", tree["method"]?.toString()?.trim('"'))
    }

    @Test
    fun decodesResultFrame() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"42","result":{"status":"ok"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Result)
        val result = decoded as GatewayServerFrame.Result
        assertEquals("42", result.id)
    }

    @Test
    fun decodesErrorFrame() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"42","error":{"code":"invalid_params","message":"bad input"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Error)
        val error = decoded as GatewayServerFrame.Error
        assertEquals("42", error.id)
        assertEquals("invalid_params", error.error.code)
    }

    @Test
    fun decodesNotificationFrame() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"method":"item/agentMessage/delta","params":{"session_id":"ws:abc","content":"Hi"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Notification)
        val notification = decoded as GatewayServerFrame.Notification
        assertEquals("item/agentMessage/delta", notification.method)
        assertEquals("Hi", notification.params["content"]?.toString()?.trim('"'))
    }

    @Test
    fun decodesReverseRequestFrame() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"srv_1","method":"approval/request","params":{"session_id":"ws:abc"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.ReverseRequest)
        val request = decoded as GatewayServerFrame.ReverseRequest
        assertEquals("srv_1", request.id)
        assertEquals("approval/request", request.method)
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
    fun parsesWrappedAssistantMessageResponsePayload() {
        val payload = json.parseToJsonElement(
            """
            {
              "message": {
                "session_key": "session-1",
                "message_id": "assistant-1",
                "role": "assistant",
                "response": {
                  "content": "wrapped assistant response",
                  "attachments": [
                    { "archive_id": "archive-1", "filename": "assistant.txt" }
                  ]
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val message = payload.chatMessage()

        assertEquals("assistant", message?.role)
        assertEquals("wrapped assistant response", message?.content)
        assertEquals("assistant.txt", message?.attachments?.single()?.filename)
    }

    @Test
    fun parsesHistoryMessagesResultFromGatewayPage() {
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
    fun parsesAssistantHistoryMessagesFromGatewayPage() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "session-1",
              "messages": [
                {
                  "role": "assistant",
                  "content": "previous answer",
                  "timestamp_ms": 42,
                  "metadata": {},
                  "message_id": "msg-2"
                }
              ],
              "has_more": false,
              "oldest_loaded_message_id": "msg-2"
            }
            """.trimIndent(),
        ).jsonObject

        val history = result.historyResult()
        val message = history.messages.single()

        assertEquals("msg-2", message.id)
        assertEquals("session-1", message.sessionKey)
        assertEquals("assistant", message.role)
        assertEquals("previous answer", message.content)
        assertEquals(42L, message.timestampMs)
    }

    @Test
    fun parsesAssistantHistoryMessagesWithResponseContent() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "session-1",
              "messages": [
                {
                  "role": "assistant",
                  "response": { "content": "previous response answer" },
                  "timestamp_ms": 42,
                  "message_id": "msg-2"
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val message = result.historyResult().messages.single()

        assertEquals("assistant", message.role)
        assertEquals("previous response answer", message.content)
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

    @Test
    fun parsesV1ItemCompletedNotificationAsMessage() {
        val payload = json.parseToJsonElement(
            """
            {
              "session_id": "session-1",
              "thread_id": "session-1",
              "turn_id": "turn-1",
              "item": {
                "item_id": "item-agent-1",
                "type": "agentMessage",
                "status": "completed",
                "payload": {
                  "message": {
                    "content": "agent response",
                    "metadata": {},
                    "attachments": []
                  }
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val message = payload.chatMessage(fallbackSessionKey = "session-1")

        assertEquals("agent response", message?.content)
        assertEquals("session-1", message?.sessionKey)
        assertEquals("assistant", message?.role)
    }
}
