package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayFrameTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── Client request frame encoding ──

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
    fun encodesClientRequestFrameWithEmptyParams() {
        val frame = GatewayClientRequest(
            id = "create_1",
            method = "session/create",
        )

        val encoded = GatewayJson.encodeToString(GatewayClientRequest.serializer(), frame)
        val decoded = GatewayJson.decodeFromString(GatewayClientRequest.serializer(), encoded)

        assertEquals("create_1", decoded.id)
        assertEquals("session/create", decoded.method)
        // params defaults to empty JsonObject; round-trip preserves it
    }

    @Test
    fun encodesClientRequestProducesValidV1Envelope() {
        val frame = GatewayClientRequest(
            id = "init_1",
            method = "initialize",
            params = buildJsonObject { put("client_info", buildJsonObject { put("name", "test") }) },
        )

        val encoded = GatewayJson.encodeToString(GatewayClientRequest.serializer(), frame)
        // v1: no "type" field, no "jsonrpc" field
        assertFalse(json.parseToJsonElement(encoded).jsonObject.containsKey("type"))
        assertFalse(json.parseToJsonElement(encoded).jsonObject.containsKey("jsonrpc"))
    }

    // ── Client notification frame encoding ──

    @Test
    fun encodesClientNotificationFrameWithoutId() {
        val frame = GatewayClientNotification(
            method = "initialized",
            params = JsonObject(emptyMap()),
        )

        val encoded = GatewayJson.encodeToString(GatewayClientNotification.serializer(), frame)
        val tree = json.parseToJsonElement(encoded).jsonObject

        assertTrue(tree.containsKey("method"))
        assertFalse(tree.containsKey("id"))
        assertEquals("initialized", tree["method"]?.toString()?.trim('"'))
    }

    @Test
    fun encodesClientNotificationWithParams() {
        val frame = GatewayClientNotification(
            method = "initialized",
            params = buildJsonObject { put("protocol_version", "v1") },
        )

        val encoded = GatewayJson.encodeToString(GatewayClientNotification.serializer(), frame)
        val tree = json.parseToJsonElement(encoded).jsonObject

        assertEquals("v1", tree["params"]?.jsonObject?.get("protocol_version")?.toString()?.trim('"'))
    }

    // ── Server frame deserialization ──

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
    fun decodesResultFrameWithNestedObject() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"sessions_1","result":{"sessions":[{"session_key":"ws:abc"}],"active_session_key":"ws:abc"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Result)
        val result = decoded as GatewayServerFrame.Result
        assertEquals("sessions_1", result.id)
        assertNotNull(result.result)
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
        assertEquals("bad input", error.error.message)
    }

    @Test
    fun decodesErrorFrameWithNullId() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":null,"error":{"code":"invalid_request","message":"parse error"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Error)
        val error = decoded as GatewayServerFrame.Error
        assertNull(error.id)
        assertEquals("invalid_request", error.error.code)
    }

    @Test
    fun decodesErrorFrameWithData() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"turn_req_1","error":{"code":"too_many_active_turns","message":"too many turns","data":{"max_active_turns":4,"retryable":true}}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Error)
        val error = decoded as GatewayServerFrame.Error
        assertEquals("too_many_active_turns", error.error.code)
        assertNotNull(error.error.data)
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
    fun decodesNotificationFrameWithEmptyParams() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"method":"session/subscribed"}""",
        )

        assertTrue(decoded is GatewayServerFrame.Notification)
        val notification = decoded as GatewayServerFrame.Notification
        assertEquals("session/subscribed", notification.method)
        assertTrue(notification.params.isEmpty())
    }

    @Test
    fun decodesNotificationFrameWithMissingParamsKey() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"method":"initialized"}""",
        )

        assertTrue(decoded is GatewayServerFrame.Notification)
        val notification = decoded as GatewayServerFrame.Notification
        assertEquals("initialized", notification.method)
        assertTrue(notification.params.isEmpty())
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
    fun decodesReverseRequestToolRequestUserInput() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"srv_2","method":"tool/requestUserInput","params":{"prompt":"Enter value"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.ReverseRequest)
        val request = decoded as GatewayServerFrame.ReverseRequest
        assertEquals("srv_2", request.id)
        assertEquals("tool/requestUserInput", request.method)
    }

    @Test
    fun decodesResultWinsOverMethodWhenBothPresent() {
        // A response with result field — result takes precedence over method detection
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"42","method":"session/list","result":{"sessions":[]}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Result)
    }

    @Test
    fun decodesErrorWinsOverMethodWhenBothPresent() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"id":"42","method":"turn/start","error":{"code":"too_many_active_turns","message":"exceeded"}}""",
        )

        assertTrue(decoded is GatewayServerFrame.Error)
    }

    @Test
    fun decodesUnrecognizedFrameAsError() {
        val decoded = GatewayJson.decodeFromString(
            GatewayServerFrameDeserializer,
            """{"unknown_field":"value"}""",
        )

        assertTrue(decoded is GatewayServerFrame.Error)
        val error = decoded as GatewayServerFrame.Error
        assertEquals("invalid_request", error.error.code)
    }

    // ── GatewayError ──

    @Test
    fun deserializesGatewayError() {
        val error = GatewayJson.decodeFromString(
            GatewayError.serializer(),
            """{"code":"method_not_found","message":"method not found","data":{"available":["session/list","provider/list"]}}"""
        )
        assertEquals("method_not_found", error.code)
        assertEquals("method not found", error.message)
        assertNotNull(error.data)
    }

    // ── chatMessage() parsing (v1 + legacy) ──

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
        assertTrue(history.hasMore)
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
