package com.zhubby.klawchat.data.gateway

import okhttp3.HttpUrl.Companion.toHttpUrl

data class GatewayEndpoint(
    val baseUrl: String,
    val token: String,
) {
    val webSocketUrl: String
        get() {
            val httpUrl = baseUrl.toHttpUrl()
            val builder = httpUrl.newBuilder()
                .encodedPath("/ws/chat")
                .query(null)
            if (token.isNotBlank()) {
                builder.addQueryParameter("token", token)
            }
            val chatUrl = builder.build().toString()
            return when (httpUrl.scheme) {
                "https" -> chatUrl.replaceFirst("https://", "wss://")
                else -> chatUrl.replaceFirst("http://", "ws://")
            }
        }
}
