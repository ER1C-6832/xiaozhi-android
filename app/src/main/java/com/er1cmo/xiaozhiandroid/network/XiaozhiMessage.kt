package com.er1cmo.xiaozhiandroid.network

import org.json.JSONObject

object XiaozhiMessage {
    fun hello(): String {
        return JSONObject()
            .put("type", "hello")
            .put("version", 1)
            .put(
                "features",
                JSONObject()
                    .put("mcp", true),
            )
            .put("transport", "websocket")
            .put(
                "audio_params",
                JSONObject()
                    .put("format", "opus")
                    .put("sample_rate", INPUT_SAMPLE_RATE)
                    .put("channels", CHANNELS)
                    .put("frame_duration", FRAME_DURATION_MS),
            )
            .toString()
    }

    fun listenDetect(
        sessionId: String,
        text: String,
    ): String {
        return JSONObject()
            .put("session_id", sessionId)
            .put("type", "listen")
            .put("state", "detect")
            .put("text", text)
            .toString()
    }

    fun startListening(
        sessionId: String,
        mode: String = "manual",
    ): String {
        return JSONObject()
            .put("session_id", sessionId)
            .put("type", "listen")
            .put("state", "start")
            .put("mode", mode)
            .toString()
    }

    fun stopListening(sessionId: String): String {
        return JSONObject()
            .put("session_id", sessionId)
            .put("type", "listen")
            .put("state", "stop")
            .toString()
    }

    fun abort(
        sessionId: String,
        reason: String = "user_interruption",
    ): String {
        return JSONObject()
            .put("session_id", sessionId)
            .put("type", "abort")
            .put("reason", reason)
            .toString()
    }

    fun prettyJson(raw: String): String {
        return try {
            JSONObject(raw).toString(2)
        } catch (_: Exception) {
            raw
        }
    }

    fun messageType(raw: String): String {
        return try {
            JSONObject(raw).optString("type", "unknown").ifBlank { "unknown" }
        } catch (_: Exception) {
            "invalid_json"
        }
    }

    private const val INPUT_SAMPLE_RATE = 16_000
    private const val CHANNELS = 1
    private const val FRAME_DURATION_MS = 20
}
