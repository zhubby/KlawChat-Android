package com.zhubby.klawchat.data.gateway

data class GatewayEndpoint(
    val baseUrl: String,
    val token: String,
) {
    val webSocketUrl: String
        get() {
            val url = baseUrl.trimEnd('/')
            val wsBase = when {
                url.startsWith("https://") -> url.replaceFirst("https://", "wss://")
                url.startsWith("http://") -> url.replaceFirst("http://", "ws://")
                else -> "ws://$url"
            }
            val query = if (token.isNotBlank()) "?token=${encodeQueryParam(token)}" else ""
            return "$wsBase/ws/chat$query"
        }

    val httpBaseUrl: String
        get() = baseUrl.trimEnd('/')

    private fun encodeQueryParam(value: String): String =
        value.replace("%", "%25")
            .replace(" ", "%20")
            .replace("&", "%26")
            .replace("=", "%3D")
            .replace("+", "%2B")
}
