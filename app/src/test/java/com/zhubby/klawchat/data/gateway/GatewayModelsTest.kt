package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayModelsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── WorkspaceSession ──

    @Test
    fun deserializesWorkspaceSessionFromV1WireFormat() {
        val element = json.parseToJsonElement(
            """{"session_key":"websocket:abc","title":"Agent abc","created_at_ms":1714200000000,"model_provider":"anthropic","model":"claude-sonnet-4-5"}"""
        ).jsonObject
        val session = element.toWorkspaceSession()
        assertNotNull(session)
        assertEquals("websocket:abc", session!!.sessionKey)
        assertEquals("Agent abc", session.title)
        assertEquals(1714200000000L, session.createdAtMs)
        assertEquals("anthropic", session.modelProvider)
        assertEquals("claude-sonnet-4-5", session.model)
    }

    @Test
    fun deserializesWorkspaceSessionWithNulls() {
        val element = json.parseToJsonElement(
            """{"session_key":"websocket:new"}"""
        ).jsonObject
        val session = element.toWorkspaceSession()
        assertNotNull(session)
        assertEquals("websocket:new", session!!.sessionKey)
        assertNull(session.title)
        assertNull(session.createdAtMs)
        assertNull(session.modelProvider)
        assertNull(session.model)
    }

    @Test
    fun toWorkspaceSessionReturnsNullForInvalidJson() {
        val element = json.parseToJsonElement(
            """{"not_a_session":true}"""
        ).jsonObject
        assertNull(element.toWorkspaceSession())
    }

    // ── ProviderCatalog ──

    @Test
    fun deserializesProviderCatalog() {
        val catalog = GatewayJson.decodeFromString(
            ProviderCatalog.serializer(),
            """{"providers":[{"id":"anthropic","default_model":"claude-sonnet-4-5"}],"default_provider":"anthropic"}"""
        )
        assertEquals(1, catalog.providers.size)
        assertEquals("anthropic", catalog.providers[0].id)
        assertEquals("claude-sonnet-4-5", catalog.providers[0].defaultModel)
        assertEquals("anthropic", catalog.defaultProvider)
    }

    @Test
    fun deserializesProviderCatalogWithDefaults() {
        val catalog = GatewayJson.decodeFromString(
            ProviderCatalog.serializer(),
            """{}"""
        )
        assertEquals(0, catalog.providers.size)
        assertNull(catalog.defaultProvider)
    }

    // ── ContentBlock ──

    @Test
    fun serializesTextContentBlock() {
        val block = ContentBlock(type = "text", text = "hello")
        val encoded = GatewayJson.encodeToString(ContentBlock.serializer(), block)
        val decoded = GatewayJson.decodeFromString(ContentBlock.serializer(), encoded)
        assertEquals("text", decoded.type)
        assertEquals("hello", decoded.text)
        assertNull(decoded.archiveId)
    }

    @Test
    fun serializesAttachmentContentBlock() {
        val block = ContentBlock(type = "attachment", archiveId = "archive-1", filename = "doc.pdf")
        val encoded = GatewayJson.encodeToString(ContentBlock.serializer(), block)
        val decoded = GatewayJson.decodeFromString(ContentBlock.serializer(), encoded)
        assertEquals("attachment", decoded.type)
        assertNull(decoded.text)
        assertEquals("archive-1", decoded.archiveId)
        assertEquals("doc.pdf", decoded.filename)
    }

    // ── ClientInfo / ClientCapabilities ──

    @Test
    fun serializesClientInfo() {
        val info = ClientInfo(name = "klawchat-android", title = "KlawChat Android", version = "1.0")
        val encoded = GatewayJson.encodeToString(ClientInfo.serializer(), info)
        val tree = json.parseToJsonElement(encoded).jsonObject
        assertEquals("klawchat-android", tree["name"]?.toString()?.trim('"'))
        assertEquals("KlawChat Android", tree["title"]?.toString()?.trim('"'))
        assertEquals("1.0", tree["version"]?.toString()?.trim('"'))
    }

    @Test
    fun serializesClientCapabilities() {
        val caps = ClientCapabilities()
        val encoded = GatewayJson.encodeToString(ClientCapabilities.serializer(), caps)
        // GatewayJson uses explicitNulls=false, so default values may be omitted
        // The important thing is that deserialization restores defaults correctly
        val decoded = GatewayJson.decodeFromString(ClientCapabilities.serializer(), encoded)
        assertEquals("v1", decoded.protocolVersion)
        assertFalse(decoded.experimental)
        assertTrue(decoded.turns)
        assertTrue(decoded.items)
    }

    // ── ArchiveAttachment ──

    @Test
    fun deserializesArchiveAttachment() {
        val attachment = GatewayJson.decodeFromString(
            ArchiveAttachment.serializer(),
            """{"archive_id":"arc-1","filename":"doc.pdf","mime_type":"application/pdf","size":1024}"""
        )
        assertEquals("arc-1", attachment.archiveId)
        assertEquals("doc.pdf", attachment.filename)
        assertEquals("application/pdf", attachment.mimeType)
        assertEquals(1024L, attachment.size)
    }

    // ── JSON helper extensions ──

    @Test
    fun asJsonConvertsString() {
        val primitive = "hello".asJson()
        assertEquals("hello", primitive.content)
    }

    @Test
    fun asJsonConvertsBoolean() {
        assertTrue(true.asJson().content == "true")
        assertFalse(false.asJson().content == "true")
    }

    @Test
    fun asJsonConvertsInt() {
        assertEquals("42", 42.asJson().content)
    }

    @Test
    fun asJsonConvertsLong() {
        assertEquals("9999999999", 9999999999L.asJson().content)
    }

    @Test
    fun nullableStringReturnsJsonPrimitiveForNonNull() {
        val element = nullableString("hello")
        assertTrue(element is JsonPrimitive)
        assertEquals("hello", (element as JsonPrimitive).content)
    }

    @Test
    fun nullableStringReturnsJsonNullForNull() {
        val element = nullableString(null)
        assertEquals(JsonNull, element)
    }

    @Test
    fun objectOrNullReturnsJsonObjectForJsonObject() {
        val obj = buildJsonObject { put("key", "value") }
        assertEquals(obj, obj.objectOrNull())
    }

    @Test
    fun objectOrNullReturnsNullForJsonPrimitive() {
        val primitive = JsonPrimitive("hello")
        assertNull(primitive.objectOrNull())
    }

    @Test
    fun stringExtensionExtractsStringValue() {
        val obj = buildJsonObject { put("name", "Alice") }
        assertEquals("Alice", obj.string("name"))
    }

    @Test
    fun stringExtensionReturnsNullForMissingKey() {
        val obj = JsonObject(emptyMap())
        assertNull(obj.string("missing"))
    }

    @Test
    fun booleanExtensionExtractsBooleanValue() {
        val obj = buildJsonObject { put("active", true) }
        assertEquals(true, obj.boolean("active"))
    }

    @Test
    fun intExtensionExtractsIntValue() {
        val obj = buildJsonObject { put("count", "42") }
        assertEquals(42, obj.int("count"))
    }

    @Test
    fun longExtensionExtractsLongValue() {
        val obj = buildJsonObject { put("ts", "1714200000000") }
        assertEquals(1714200000000L, obj.long("ts"))
    }

    @Test
    fun arrayExtensionReturnsJsonArray() {
        val obj = buildJsonObject {
            put("items", buildJsonArray {
                add(JsonPrimitive("a"))
                add(JsonPrimitive("b"))
            })
        }
        val arr = obj.array("items")
        assertNotNull(arr)
        assertEquals(2, arr!!.size)
    }

    @Test
    fun objectListExtensionExtractsObjectList() {
        val element = buildJsonObject {
            put("sessions", buildJsonArray {
                add(buildJsonObject { put("session_key", "s1") })
                add(buildJsonObject { put("session_key", "s2") })
            })
        }
        val list = element.objectList("sessions")
        assertEquals(2, list.size)
        assertEquals("s1", list[0].string("session_key"))
        assertEquals("s2", list[1].string("session_key"))
    }

    @Test
    fun objectListExtensionReturnsEmptyForMissingKey() {
        val element = buildJsonObject { }
        assertEquals(emptyList<JsonObject>(), element.objectList("missing"))
    }

    // ── chatMessage() v1 format ──

    @Test
    fun chatMessageFromV1ItemCompletedAgentMessage() {
        val payload = json.parseToJsonElement(
            """
            {
              "session_id": "ws:abc",
              "item": {
                "item_id": "item-agent-1",
                "type": "agentMessage",
                "payload": {
                  "message": {
                    "content": "Hello from agent",
                    "metadata": {},
                    "attachments": []
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject
        val message = payload.chatMessage(fallbackSessionKey = "fallback")
        assertNotNull(message)
        assertEquals("item-agent-1", message!!.id)
        assertEquals("ws:abc", message.sessionKey)
        assertEquals("assistant", message.role)
        assertEquals("Hello from agent", message.content)
    }

    @Test
    fun chatMessageFromV1ItemCompletedUserMessage() {
        val payload = json.parseToJsonElement(
            """
            {
              "session_id": "ws:abc",
              "item": {
                "item_id": "item-user-1",
                "type": "userMessage",
                "payload": {
                  "message": {
                    "content": "User question",
                    "metadata": {},
                    "attachments": [
                      { "archive_id": "arc-1", "filename": "doc.pdf" }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject
        val message = payload.chatMessage(fallbackSessionKey = "ws:abc")
        assertNotNull(message)
        assertEquals("item-user-1", message!!.id)
        assertEquals("user", message.role)
        assertEquals("User question", message.content)
        assertEquals(1, message.attachments.size)
        assertEquals("arc-1", message.attachments[0].archiveId)
    }

    @Test
    fun chatMessageFromFlatV1SessionKeyRoleContent() {
        val payload = json.parseToJsonElement(
            """{"session_key":"ws:abc","role":"user","content":"hi there"}"""
        ).jsonObject
        val message = payload.chatMessage()
        assertNotNull(message)
        assertEquals("ws:abc", message!!.sessionKey)
        assertEquals("user", message.role)
        assertEquals("hi there", message.content)
    }

    @Test
    fun chatMessageReturnsNullWhenNoSessionKeyAndNoFallback() {
        val payload = json.parseToJsonElement(
            """{"content":"no session"}"""
        ).jsonObject
        assertNull(payload.chatMessage())
    }

    @Test
    fun chatMessageUsesFallbackSessionKey() {
        val payload = json.parseToJsonElement(
            """{"role":"assistant","response":{"content":"fallback"}}"""
        ).jsonObject
        val message = payload.chatMessage(fallbackSessionKey = "ws:fallback")
        assertNotNull(message)
        assertEquals("ws:fallback", message!!.sessionKey)
    }

    @Test
    fun chatMessageFromLegacyResponseField() {
        val payload = json.parseToJsonElement(
            """
            {
              "session_key": "ws:abc",
              "response": {
                "content": "legacy reply",
                "request_id": "req-1"
              }
            }
            """.trimIndent()
        ).jsonObject
        val message = payload.chatMessage()
        assertNotNull(message)
        assertEquals("assistant", message!!.role)
        assertEquals("legacy reply", message.content)
        assertEquals("req-1", message.requestId)
    }

    @Test
    fun chatMessageFromNestedMessageWithResponse() {
        val payload = json.parseToJsonElement(
            """
            {
              "message": {
                "session_key": "ws:abc",
                "role": "assistant",
                "response": {
                  "content": "nested response content"
                }
              }
            }
            """.trimIndent()
        ).jsonObject
        val message = payload.chatMessage()
        assertNotNull(message)
        assertEquals("nested response content", message!!.content)
    }

    @Test
    fun chatMessageDefaultRoleIsAssistant() {
        val payload = json.parseToJsonElement(
            """{"session_key":"ws:abc","content":"no role specified"}"""
        ).jsonObject
        val message = payload.chatMessage()
        assertEquals("assistant", message?.role)
    }

    // ── streamDeltaText() v1 format ──

    @Test
    fun streamDeltaFromV1ItemPayloadMessageContent() {
        val payload = json.parseToJsonElement(
            """
            {
              "session_id": "ws:abc",
              "item": {
                "item_id": "item-agent-1",
                "payload": {
                  "message": {
                    "content": "streaming text"
                  }
                }
              }
            }
            """.trimIndent()
        ).jsonObject
        assertEquals("streaming text", payload.streamDeltaText())
    }

    @Test
    fun streamDeltaFromV1DeltaField() {
        val payload = json.parseToJsonElement(
            """{"delta":"hello delta"}"""
        ).jsonObject
        assertEquals("hello delta", payload.streamDeltaText())
    }

    @Test
    fun streamDeltaFromNestedDeltaContent() {
        val payload = json.parseToJsonElement(
            """{"delta":{"content":"nested delta content"}}"""
        ).jsonObject
        assertEquals("nested delta content", payload.streamDeltaText())
    }

    @Test
    fun streamDeltaFromContentField() {
        val payload = json.parseToJsonElement(
            """{"content":"direct content"}"""
        ).jsonObject
        assertEquals("direct content", payload.streamDeltaText())
    }

    @Test
    fun streamDeltaReturnsEmptyStringWhenNoDeltaContent() {
        val payload = json.parseToJsonElement(
            """{"session_id":"ws:abc"}"""
        ).jsonObject
        assertEquals("", payload.streamDeltaText())
    }

    // ── historyResult() ──

    @Test
    fun historyResultWithV1MessagesArray() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "ws:abc",
              "messages": [
                { "role": "user", "content": "question", "message_id": "m1", "timestamp_ms": "100" },
                { "role": "assistant", "content": "answer", "message_id": "m2", "timestamp_ms": "200" }
              ],
              "has_more": true,
              "oldest_loaded_message_id": "m1"
            }
            """.trimIndent()
        ).jsonObject
        val history = result.historyResult()
        assertEquals(2, history.messages.size)
        assertTrue(history.hasMore)
        assertEquals("m1", history.oldestLoadedMessageId)
    }

    @Test
    fun historyResultWithEmptyMessages() {
        val result = json.parseToJsonElement(
            """{"session_key":"ws:abc","messages":[],"has_more":false}"""
        ).jsonObject
        val history = result.historyResult()
        assertEquals(0, history.messages.size)
        assertFalse(history.hasMore)
    }

    @Test
    fun historyResultDefaultsHasMoreToFalse() {
        val result = json.parseToJsonElement(
            """{"session_key":"ws:abc","messages":[]}"""
        ).jsonObject
        val history = result.historyResult()
        assertFalse(history.hasMore)
    }

    @Test
    fun historyResultWithLegacyHistoryField() {
        val result = json.parseToJsonElement(
            """
            {
              "session_key": "ws:abc",
              "history": [
                { "role": "user", "content": "old question", "message_id": "h1" }
              ]
            }
            """.trimIndent()
        ).jsonObject
        val history = result.historyResult()
        assertEquals(1, history.messages.size)
        assertEquals("old question", history.messages[0].content)
    }
}
