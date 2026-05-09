package com.zhubby.klawchat.data.gateway

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val GatewayJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/* ── Client Frames (v1 JSON-RPC outgoing) ── */

@Serializable
data class GatewayClientRequest(
    val id: String,
    val method: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class GatewayClientNotification(
    val method: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

/* ── Server Frames (v1 JSON-RPC incoming) ── */

sealed class GatewayServerFrame {
    @Serializable
    data class Result(
        val id: String,
        val result: JsonElement,
    ) : GatewayServerFrame()

    @Serializable
    data class Error(
        val id: String? = null,
        val error: GatewayError,
    ) : GatewayServerFrame()

    /** Server notification – no id, has method + params */
    data class Notification(
        val method: String,
        val params: JsonObject,
    ) : GatewayServerFrame()

    /** Server reverse request – has id + method + params (e.g. approval/request) */
    data class ReverseRequest(
        val id: String,
        val method: String,
        val params: JsonObject,
    ) : GatewayServerFrame()
}

@Serializable
data class GatewayError(
    val code: String,
    val message: String,
    val data: JsonElement? = null,
)

/**
 * Custom deserializer for v1 server frames.
 *
 * v1 uses JSON-RPC 2.0 semantics but omits the `jsonrpc` field.
 * Frame type is determined by field presence:
 *   - `result` present          → Result
 *   - `error` present           → Error
 *   - `method` present + `id`   → ReverseRequest
 *   - `method` present, no `id` → Notification
 */
object GatewayServerFrameDeserializer : DeserializationStrategy<GatewayServerFrame> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("GatewayServerFrame")

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): GatewayServerFrame {
        val jsonDecoder = decoder as kotlinx.serialization.json.JsonDecoder
        val element = jsonDecoder.decodeJsonElement().jsonObject

        // Result frame: { "id": "...", "result": { ... } }
        if (element.containsKey("result")) {
            return GatewayJson.decodeFromJsonElement(GatewayServerFrame.Result.serializer(), element)
        }

        // Error frame: { "id": "...", "error": { "code": "...", "message": "..." } }
        if (element.containsKey("error")) {
            return GatewayJson.decodeFromJsonElement(GatewayServerFrame.Error.serializer(), element)
        }

        // Method present → either Notification or ReverseRequest
        if (element.containsKey("method")) {
            val method = element["method"]!!.jsonPrimitive.content
            val params = (element["params"] as? JsonObject) ?: JsonObject(emptyMap())
            val id = element["id"]?.jsonPrimitive?.content

            return if (id != null) {
                GatewayServerFrame.ReverseRequest(id = id, method = method, params = params)
            } else {
                GatewayServerFrame.Notification(method = method, params = params)
            }
        }

        // Fallback – treat as error if nothing matched
        return GatewayServerFrame.Error(
            id = element["id"]?.jsonPrimitive?.contentOrNull,
            error = GatewayError(code = "invalid_request", message = "Unrecognized frame format"),
        )
    }
}
