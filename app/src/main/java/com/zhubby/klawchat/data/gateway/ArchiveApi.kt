package com.zhubby.klawchat.data.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.jsonObject

class ArchiveApi(
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(GatewayJson) }
    },
) {
    suspend fun upload(
        baseUrl: String,
        token: String,
        bytes: ByteArray,
        filename: String,
        mimeType: String?,
        sessionKey: String?,
    ): ArchiveAttachment {
        val response = httpClient.submitFormWithBinaryData(
            formData {
                append("file", bytes, headers = {
                    if (mimeType != null) append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    if (mimeType != null) append(HttpHeaders.ContentType, mimeType)
                })
                if (sessionKey != null) append("session_key", sessionKey)
            },
        ) {
            url("${baseUrl.trimEnd('/')}/archive/upload")
            if (token.isNotBlank()) header("Authorization", "Bearer $token")
        }

        if (response.status.value >= 400) {
            error("Archive upload failed: HTTP ${response.status.value}")
        }

        val body = response.bodyAsText()
        val root = GatewayJson.parseToJsonElement(body).jsonObject
        val record = root["record"]?.jsonObject ?: root
        val archiveId = record.string("archive_id") ?: record.string("id")
            ?: error("Archive upload response is missing archive_id")
        return ArchiveAttachment(
            archiveId = archiveId,
            filename = record.string("filename") ?: filename,
            mimeType = record.string("mime_type") ?: mimeType,
            size = record.string("size")?.toLongOrNull(),
        )
    }
}
