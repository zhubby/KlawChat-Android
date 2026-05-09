package com.zhubby.klawchat.data.gateway

data class GatewayEndpoint(
    val baseUrl: String,
    val token: String,
) {
    val webSocketUrl: String
        get() {
            val url = baseUrl.trimEnd('/')
            // Determine scheme and strip any existing path to set /ws/chat
            val (scheme, hostPort) = when {
                url.startsWith("https://") -> "wss" to url.removePrefix("https://")
                url.startsWith("http://") -> "ws" to url.removePrefix("http://")
                else -> "ws" to url
            }
            // Remove any existing path from hostPort (keep host:port only)
            val hostOnly = hostPort.substringBefore('/')
            val query = if (token.isNotBlank()) "?token=${encodeQueryParam(token)}" else ""
            return "$scheme://$hostOnly/ws/chat$query"
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
