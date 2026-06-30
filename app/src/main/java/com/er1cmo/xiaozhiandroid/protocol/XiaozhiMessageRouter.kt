package com.er1cmo.xiaozhiandroid.protocol

import org.json.JSONObject

/**
 * Protocol message classifier.
 *
 * MainViewModel currently still handles most JSON side effects. This router is
 * introduced before MCP so type=mcp can be routed to AndroidMcpServer without
 * growing the ViewModel again.
 */
object XiaozhiMessageRouter {
    fun routeJson(prettyJson: String, type: String): ProtocolEvent.JsonMessage {
        val json = runCatching { JSONObject(prettyJson) }.getOrNull()
        return ProtocolEvent.JsonMessage(
            prettyJson = prettyJson,
            type = type,
            state = json?.optString("state").orEmpty(),
            sessionId = json?.optString("session_id").orEmpty(),
        )
    }

    fun routeBinaryAudio(data: ByteArray): ProtocolEvent.BinaryAudio {
        return ProtocolEvent.BinaryAudio(data)
    }
}
