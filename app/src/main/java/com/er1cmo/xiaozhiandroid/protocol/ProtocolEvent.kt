package com.er1cmo.xiaozhiandroid.protocol

sealed interface ProtocolEvent {
    data class Connected(val sessionId: String) : ProtocolEvent
    data class Disconnected(val reason: String) : ProtocolEvent
    data class Error(val message: String) : ProtocolEvent
    data class JsonMessage(
        val prettyJson: String,
        val type: String,
        val state: String,
        val sessionId: String,
    ) : ProtocolEvent
    data class BinaryAudio(val data: ByteArray) : ProtocolEvent
}
