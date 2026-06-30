package com.er1cmo.xiaozhiandroid.protocol

import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient
import org.json.JSONObject

/**
 * Protocol message classifier and WebSocket callback adapter.
 *
 * Phase 8A-3 moves raw WebSocket callbacks out of MainViewModel. The UI layer
 * now receives protocol-level events, while this router owns message
 * classification for JSON, binary audio and connection status callbacks.
 */
object XiaozhiMessageRouter {
    fun buildCallbacks(
        emit: (ProtocolEvent) -> Unit,
    ): XiaozhiWebSocketClient.Callbacks {
        return XiaozhiWebSocketClient.Callbacks(
            onLog = { message -> emit(ProtocolEvent.Log(message)) },
            onConnected = { sessionId -> emit(ProtocolEvent.Connected(sessionId)) },
            onIncomingJson = { prettyJson, type -> emit(routeJson(prettyJson, type)) },
            onBinaryFrame = { frame -> emit(routeBinaryAudio(frame)) },
            onClosed = { reason -> emit(ProtocolEvent.Closed(reason)) },
            onError = { message -> emit(ProtocolEvent.Error(message)) },
            onNetworkStateChanged = { state -> emit(ProtocolEvent.NetworkStateChanged(state.label)) },
        )
    }

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
