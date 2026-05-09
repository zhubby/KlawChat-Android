package com.zhubby.klawchat.data.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayEndpointTest {
    // ── WebSocket URL building ──

    @Test
    fun httpBaseUrlBuildsWsChatUrlWithToken() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000/",
            token = "abc 123",
        )

        assertEquals(
            "ws://localhost:3000/ws/chat?token=abc%20123",
            endpoint.webSocketUrl,
        )
    }

    @Test
    fun httpsBaseUrlBuildsWssChatUrlWithoutToken() {
        val endpoint = GatewayEndpoint(
            baseUrl = "https://gateway.example.com/api",
            token = "",
        )

        assertEquals(
            "wss://gateway.example.com/ws/chat",
            endpoint.webSocketUrl,
        )
    }

    @Test
    fun httpBaseUrlWithPortBuildsWsUrl() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://192.168.1.1:8080",
            token = "",
        )

        assertEquals(
            "ws://192.168.1.1:8080/ws/chat",
            endpoint.webSocketUrl,
        )
    }

    @Test
    fun httpsBaseUrlWithPortBuildsWssUrl() {
        val endpoint = GatewayEndpoint(
            baseUrl = "https://gateway.example.com:8443",
            token = "my-token",
        )

        assertEquals(
            "wss://gateway.example.com:8443/ws/chat?token=my-token",
            endpoint.webSocketUrl,
        )
    }

    @Test
    fun bareHostBuildsWsUrl() {
        val endpoint = GatewayEndpoint(
            baseUrl = "192.168.1.1:8080",
            token = "secret",
        )

        assertEquals(
            "ws://192.168.1.1:8080/ws/chat?token=secret",
            endpoint.webSocketUrl,
        )
    }

    @Test
    fun bareHostWithoutPortBuildsWsUrl() {
        val endpoint = GatewayEndpoint(
            baseUrl = "gateway.local",
            token = "",
        )

        assertEquals(
            "ws://gateway.local/ws/chat",
            endpoint.webSocketUrl,
        )
    }

    @Test
    fun trailingSlashIsRemovedFromBaseUrl() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000/",
            token = "",
        )

        assertEquals(
            "ws://localhost:3000/ws/chat",
            endpoint.webSocketUrl,
        )
    }

    @Test
    fun pathInBaseUrlIsStripped() {
        val endpoint = GatewayEndpoint(
            baseUrl = "https://gateway.example.com/api/v2",
            token = "abc",
        )

        assertEquals(
            "wss://gateway.example.com/ws/chat?token=abc",
            endpoint.webSocketUrl,
        )
    }

    // ── Token encoding ──

    @Test
    fun tokenWithSpacesIsEncoded() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000",
            token = "hello world",
        )

        assertTrue(endpoint.webSocketUrl.contains("token=hello%20world"))
    }

    @Test
    fun tokenWithAmpersandIsEncoded() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000",
            token = "key&value",
        )

        assertTrue(endpoint.webSocketUrl.contains("token=key%26value"))
    }

    @Test
    fun tokenWithEqualsIsEncoded() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000",
            token = "a=b",
        )

        assertTrue(endpoint.webSocketUrl.contains("token=a%3Db"))
    }

    @Test
    fun tokenWithPlusIsEncoded() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000",
            token = "a+b",
        )

        assertTrue(endpoint.webSocketUrl.contains("token=a%2Bb"))
    }

    @Test
    fun tokenWithPercentIsEncoded() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000",
            token = "50%",
        )

        assertTrue(endpoint.webSocketUrl.contains("token=50%25"))
    }

    @Test
    fun emptyTokenProducesNoQueryParameter() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000",
            token = "",
        )

        assertFalse(endpoint.webSocketUrl.contains("?"))
        assertFalse(endpoint.webSocketUrl.contains("token"))
    }

    // ── httpBaseUrl ──

    @Test
    fun httpBaseUrlReturnsTrimmedBaseUrl() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://localhost:3000/",
            token = "",
        )

        assertEquals("http://localhost:3000", endpoint.httpBaseUrl)
    }

    @Test
    fun httpBaseUrlPreservesPort() {
        val endpoint = GatewayEndpoint(
            baseUrl = "http://192.168.1.1:8080",
            token = "",
        )

        assertEquals("http://192.168.1.1:8080", endpoint.httpBaseUrl)
    }

    @Test
    fun httpBaseUrlPreservesHttps() {
        val endpoint = GatewayEndpoint(
            baseUrl = "https://gateway.example.com/api",
            token = "",
        )

        assertEquals("https://gateway.example.com/api", endpoint.httpBaseUrl)
    }
}
