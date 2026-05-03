package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ArchiveApi(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) {
    fun upload(
        baseUrl: String,
        token: String,
        bytes: ByteArray,
        filename: String,
        mimeType: String?,
        sessionKey: String?,
    ): ArchiveAttachment {
        val mediaType = mimeType?.toMediaTypeOrNull()
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, bytes.toRequestBody(mediaType))
            .apply {
                sessionKey?.let { addFormDataPart("session_key", it) }
            }
            .build()
        val requestBuilder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/archive/upload")
            .post(multipart)
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("Archive upload failed: HTTP ${response.code}")
            }
            val body = response.body.string()
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
}
