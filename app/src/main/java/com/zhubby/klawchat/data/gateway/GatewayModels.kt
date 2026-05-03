package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceSession(
    @SerialName("session_key") val sessionKey: String,
    val title: String? = null,
    @SerialName("created_at_ms") val createdAtMs: Long? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val model: String? = null,
)

@Serializable
data class ChatMessage(
    @SerialName("message_id") val id: String,
    @SerialName("session_key") val sessionKey: String,
    val role: String,
    val content: String,
    @SerialName("timestamp_ms") val timestampMs: Long? = null,
    @SerialName("request_id") val requestId: String? = null,
    val attachments: List<ArchiveAttachment> = emptyList(),
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

@Serializable
data class ArchiveAttachment(
    @SerialName("archive_id") val archiveId: String,
    val filename: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val size: Long? = null,
)
