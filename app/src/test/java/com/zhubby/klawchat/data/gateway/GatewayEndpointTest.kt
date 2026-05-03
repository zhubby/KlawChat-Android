package com.zhubby.klawchat.data.gateway

import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayEndpointTest {
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
}
