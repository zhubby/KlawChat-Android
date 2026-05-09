package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/* ── Workspace / Session models ── */

@Serializable
data class WorkspaceSession(
    @SerialName("session_key") val sessionKey: String,
    val title: String? = null,
    @SerialName("created_at_ms") val createdAtMs: Long? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val model: String? = null,
)

@Serializable
data class Provider(
    val id: String,
    val name: String? = null,
    @SerialName("default_model") val defaultModel: String? = null,
    val stream: Boolean = true,
    @SerialName("has_api_key") val hasApiKey: Boolean = false,
)

@Serializable
data class ProviderCatalog(
    val providers: List<Provider> = emptyList(),
    @SerialName("default_provider") val defaultProvider: String? = null,
)

/* ── v1 Initialize ── */

@Serializable
data class ClientInfo(
    val name: String,
    val title: String? = null,
    val version: String? = null,
)

@Serializable
data class ClientCapabilities(
    @SerialName("protocol_version") val protocolVersion: String = "v1",
    val experimental: Boolean = false,
    val turns: Boolean = true,
    val items: Boolean = true,
    val tools: Boolean = true,
    val approvals: Boolean = true,
    @SerialName("server_requests") val serverRequests: Boolean = true,
    val cancellation: Boolean = true,
    val steering: Boolean = true,
    val schema: Boolean = true,
    @SerialName("notification_opt_out") val notificationOptOut: List<String> = emptyList(),
)

/* ── v1 Turn / Item ── */

@Serializable
data class ContentBlock(
    val type: String,
    val text: String? = null,
    @SerialName("archive_id") val archiveId: String? = null,
    val filename: String? = null,
)

@Serializable
data class HistoryMessage(
    val role: String,
    val content: String? = null,
    @SerialName("timestamp_ms") val timestampMs: Long? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
    @SerialName("message_id") val messageId: String? = null,
)

/* ── Archive ── */

@Serializable
data class ArchiveAttachment(
    @SerialName("archive_id") val archiveId: String,
    val filename: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val size: Long? = null,
)

/* ── Internal UI models (not serialized directly from v1 wire) ── */

data class ChatMessage(
    val id: String,
    @SerialName("session_key") val sessionKey: String,
    val role: String,
    val content: String,
    @SerialName("timestamp_ms") val timestampMs: Long? = null,
    @SerialName("request_id") val requestId: String? = null,
    val attachments: List<ArchiveAttachment> = emptyList(),
)

/* ── JSON helper extensions ── */

fun String.asJson() = JsonPrimitive(this)

fun Boolean.asJson() = JsonPrimitive(this)

fun Int.asJson() = JsonPrimitive(this)

fun Long.asJson() = JsonPrimitive(this)

fun nullableString(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject

fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

fun JsonElement.objectList(name: String): List<JsonObject> =
    objectOrNull()?.array(name)?.mapNotNull { it.objectOrNull() }.orEmpty()

fun JsonObject.chatMessage(fallbackSessionKey: String? = null): ChatMessage? {
    // v1: item payload structure — params.item.payload.message
    val itemObj = this["item"]?.objectOrNull()
    val itemPayload = itemObj?.get("payload")?.objectOrNull() ?: this["payload"]?.objectOrNull()
    val messageObj = itemPayload?.get("message")?.objectOrNull() ?: this["message"]?.objectOrNull()
    // Legacy: response object (flat or nested)
    val responseObj = this["response"]?.objectOrNull() ?: messageObj?.get("response")?.objectOrNull()
    val sessionKey = string("session_key")
        ?: string("session_id")
        ?: itemObj?.string("session_key")
        ?: itemObj?.string("session_id")
        ?: itemPayload?.string("session_key")
        ?: messageObj?.string("session_key")
        ?: responseObj?.string("session_key")
        ?: fallbackSessionKey
        ?: return null
    val itemType = itemObj?.string("type")
    val role = string("role")
        ?: when (itemType) {
            "userMessage" -> "user"
            "agentMessage" -> "assistant"
            else -> null
        }
        ?: itemPayload?.string("role")
        ?: messageObj?.string("role")
        ?: responseObj?.string("role")
        ?: "assistant"
    val content = string("content")
        ?: itemPayload?.string("content")
        ?: messageObj?.string("content")
        ?: responseObj?.string("content")
        ?: ""
    if (content.isBlank() && itemPayload == null && messageObj == null && responseObj == null) return null
    val requestId = string("request_id")
        ?: itemPayload?.string("request_id")
        ?: messageObj?.string("request_id")
        ?: responseObj?.string("request_id")
    val timestampMs = long("timestamp_ms")
        ?: itemObj?.long("timestamp_ms")
        ?: itemPayload?.long("timestamp_ms")
        ?: messageObj?.long("timestamp_ms")
        ?: responseObj?.string("timestamp_ms")?.toLongOrNull()
    val messageId = string("message_id")
        ?: itemObj?.string("item_id")
        ?: itemPayload?.string("message_id")
        ?: messageObj?.string("message_id")
        ?: responseObj?.string("message_id")
        ?: string("item_id")
        ?: "local:${java.util.UUID.randomUUID()}"
    val attachments = (
            this["attachments"]
                ?: itemPayload?.get("attachments")
                ?: messageObj?.get("attachments")
                ?: responseObj?.get("attachments")
            )?.let { element ->
            runCatching {
                GatewayJson.decodeFromJsonElement(
                    kotlinx.serialization.serializer<List<ArchiveAttachment>>(),
                    element
                )
            }.getOrDefault(emptyList())
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

fun JsonObject.streamDeltaText(): String {
    // v1: item/agentMessage/delta → params.item.payload.message.content / params.delta
    val item = this["item"]?.objectOrNull()
    val payload = item?.get("payload")?.objectOrNull()
    val message = payload?.get("message")?.objectOrNull()
    val delta = this["delta"]?.objectOrNull()
    // Direct delta text
    return string("delta")
        ?: delta?.string("content")
        ?: delta?.string("text")
        ?: string("content")
        ?: string("text")
        ?: payload?.string("content")
        ?: message?.string("content")
        ?: ""
}

data class HistoryResult(
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
    val oldestLoadedMessageId: String?,
)

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

fun JsonObject.toWorkspaceSession(): WorkspaceSession? =
    runCatching { GatewayJson.decodeFromJsonElement(WorkspaceSession.serializer(), this) }.getOrNull()
