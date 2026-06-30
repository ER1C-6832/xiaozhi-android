package com.er1cmo.xiaozhiandroid.protocol

import com.er1cmo.xiaozhiandroid.network.NetworkState
import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient

/** Protocol-facing abstraction over the current WebSocket client. */
interface XiaozhiProtocolClient {
    val sessionId: String
    fun hasActiveSession(): Boolean
    fun queuedBytes(): Long
    suspend fun connect(callbacks: XiaozhiWebSocketClient.Callbacks): Boolean
    fun sendWakeText(text: String): Boolean
    fun sendStartManualListening(): Boolean
    fun sendStopListening(): Boolean
    fun sendAbort(): Boolean
    fun sendAudioFrame(opusFrame: ByteArray): Boolean
    fun close(reason: String = "client_close")
}

class WebSocketXiaozhiProtocolClient(
    private val delegate: XiaozhiWebSocketClient,
) : XiaozhiProtocolClient {
    override val sessionId: String
        get() = delegate.sessionId

    override fun hasActiveSession(): Boolean = delegate.hasActiveSession()

    override fun queuedBytes(): Long = delegate.queuedBytes()

    override suspend fun connect(callbacks: XiaozhiWebSocketClient.Callbacks): Boolean {
        return delegate.connect(callbacks)
    }

    override fun sendWakeText(text: String): Boolean = delegate.sendWakeText(text)

    override fun sendStartManualListening(): Boolean = delegate.sendStartManualListening()

    override fun sendStopListening(): Boolean = delegate.sendStopListening()

    override fun sendAbort(): Boolean = delegate.sendAbort()

    override fun sendAudioFrame(opusFrame: ByteArray): Boolean = delegate.sendAudioFrame(opusFrame)

    override fun close(reason: String) = delegate.close(reason)
}

fun NetworkState.toProtocolEventOrNull(): ProtocolEvent? {
    return when (this) {
        NetworkState.Connected -> null
        NetworkState.Disconnected -> ProtocolEvent.Disconnected(label)
        NetworkState.Error -> ProtocolEvent.Error(label)
        else -> null
    }
}
