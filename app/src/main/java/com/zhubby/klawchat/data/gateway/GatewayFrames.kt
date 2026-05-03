package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

val GatewayJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
sealed class GatewayClientFrame {
    @Serializable
    @SerialName("method")
    data class Method(
        val id: String,
        val method: String,
        val params: Map<String, JsonElement> = emptyMap(),
    ) : GatewayClientFrame()
}

@Serializable
sealed class GatewayServerFrame {
    @Serializable
    @SerialName("result")
    data class Result(
        val id: String,
        val result: JsonElement,
    ) : GatewayServerFrame()

    @Serializable
    @SerialName("error")
    data class Error(
        val id: String? = null,
        val error: GatewayError,
    ) : GatewayServerFrame()

    @Serializable
    @SerialName("event")
    data class Event(
        val event: String,
        val payload: Map<String, JsonElement> = emptyMap(),
    ) : GatewayServerFrame()
}

@Serializable
data class GatewayError(
    val code: String,
    val message: String,
    val data: JsonElement? = null,
)
