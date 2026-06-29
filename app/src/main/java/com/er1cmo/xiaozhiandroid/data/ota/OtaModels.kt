package com.er1cmo.xiaozhiandroid.data.ota

import org.json.JSONObject

data class ActivationInfo(
    val code: String,
    val challenge: String,
    val message: String,
)

data class OtaResponse(
    val websocketUrl: String?,
    val websocketToken: String?,
    val activation: ActivationInfo?,
    val rawJson: String,
    val redactedJson: String,
)

data class OtaActivationOutcome(
    val state: ActivationState,
    val message: String,
)

fun parseOtaResponse(body: String): OtaResponse {
    val json = JSONObject(body)
    val websocket = json.optJSONObject("websocket")
    val websocketUrl = websocket
        ?.optString("url")
        ?.takeIf { it.isNotBlank() }
    val websocketToken = websocket
        ?.optString("token")
        ?.takeIf { it.isNotBlank() }

    val activationObject = json.optJSONObject("activation")
    val activation = activationObject?.let {
        ActivationInfo(
            code = it.optString("code"),
            challenge = it.optString("challenge"),
            message = it.optString("message", "请在 xiaozhi.me 输入验证码"),
        )
    }?.takeIf { it.code.isNotBlank() && it.challenge.isNotBlank() }

    return OtaResponse(
        websocketUrl = websocketUrl,
        websocketToken = websocketToken,
        activation = activation,
        rawJson = body,
        redactedJson = redactSensitiveJson(body),
    )
}

fun redactSensitiveJson(body: String): String {
    return try {
        val json = JSONObject(body)
        json.optJSONObject("websocket")?.let { websocket ->
            if (websocket.has("token")) {
                websocket.put("token", "***")
            }
        }
        json.toString(2)
    } catch (_: Exception) {
        body.replace(Regex("(?i)(\\\"token\\\"\\s*:\\s*)\\\"[^\\\"]*\\\""), "\$1\"***\"")
    }
}
