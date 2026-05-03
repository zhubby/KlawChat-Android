package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import java.util.UUID

fun String.asJson() = JsonPrimitive(this)

fun Boolean.asJson() = JsonPrimitive(this)

fun Int.asJson() = JsonPrimitive(this)

fun Long.asJson() = JsonPrimitive(this)

fun nullableString(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject

fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

inline fun <reified T> JsonElement.decodeAs(): T = GatewayJson.decodeFromJsonElement(serializer(), this)

fun JsonObject.chatMessage(fallbackSessionKey: String? = null): ChatMessage? {
    val response = this["response"]?.objectOrNull()
    val message = this["message"]?.objectOrNull()
    val sessionKey = string("session_key")
        ?: response?.string("session_key")
        ?: message?.string("session_key")
        ?: fallbackSessionKey
        ?: return null
    val role = string("role") ?: response?.string("role") ?: message?.string("role") ?: "assistant"
    val content = string("content") ?: response?.string("content") ?: message?.string("content").orEmpty()
    if (content.isBlank() && response == null && message == null) return null
    val requestId = string("request_id") ?: response?.string("request_id") ?: message?.string("request_id")
    val timestampMs = string("timestamp_ms")?.toLongOrNull()
        ?: response?.string("timestamp_ms")?.toLongOrNull()
        ?: message?.string("timestamp_ms")?.toLongOrNull()
    val messageId = string("message_id")
        ?: response?.string("message_id")
        ?: message?.string("message_id")
        ?: "local:${UUID.randomUUID()}"
    val attachments = (this["attachments"] ?: response?.get("attachments") ?: message?.get("attachments"))?.let { element ->
        runCatching { GatewayJson.decodeFromJsonElement<List<ArchiveAttachment>>(element) }.getOrDefault(emptyList())
    }.orEmpty()
    return ChatMessage(
        id = messageId,
        sessionKey = sessionKey,
        role = role,
        content = content,
        timestampMs = timestampMs,
        requestId = requestId,
        attachments = attachments,
    )
}

fun JsonElement.objectList(name: String): List<JsonObject> =
    objectOrNull()?.array(name)?.mapNotNull { it.objectOrNull() }.orEmpty()

fun JsonObject.historyResult(): HistoryResult {
    val sessionKey = string("session_key")
    val messages = (array("messages") ?: array("history"))
        ?.mapNotNull { it.objectOrNull()?.chatMessage(fallbackSessionKey = sessionKey) }
        .orEmpty()
    return HistoryResult(
        messages = messages,
        hasMore = boolean("has_more") ?: false,
        oldestLoadedMessageId = string("oldest_loaded_message_id"),
    )
}

fun JsonObject.streamDeltaText(): String {
    val delta = this["delta"]?.objectOrNull()
    val response = this["response"]?.objectOrNull()
    val message = this["message"]?.objectOrNull()
    return string("delta")
        ?: delta?.string("content")
        ?: delta?.string("text")
        ?: string("content")
        ?: string("text")
        ?: response?.string("content")
        ?: message?.string("content")
        ?: ""
}
