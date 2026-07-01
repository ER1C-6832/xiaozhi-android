package com.er1cmo.xiaozhiandroid.network

import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

class XiaozhiWebSocketClient(
    private val configRepository: ConfigRepository,
    private val appScope: CoroutineScope,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var connected: Boolean = false

    @Volatile
    private var connecting: Boolean = false

    @Volatile
    var sessionId: String = ""
        private set

    private val sendLock = Any()

    fun isConnected(): Boolean = connected && webSocket != null

    fun isConnecting(): Boolean = connecting

    fun hasActiveSession(): Boolean = isConnected() && sessionId.isNotBlank()

    fun queuedBytes(): Long = webSocket?.queueSize() ?: 0L

    suspend fun connect(callbacks: Callbacks): Boolean {
        if (hasActiveSession()) {
            postLog(callbacks, "WebSocket 已连接，无需重复连接")
            return true
        }
        if (connecting) {
            postLog(callbacks, "WebSocket 正在连接中，请稍候")
            return false
        }

        val config = configRepository.getConfig()
        validateWebSocketConfig(config)

        closeSilently()
        connecting = true
        connected = false
        sessionId = ""

        val helloReceived = CompletableDeferred<Boolean>()
        val request = buildWebSocketRequest(config)

        postLog(callbacks, "WebSocket 连接开始：${config.websocketUrl}")
        postLog(callbacks, "WebSocket 请求头已准备：Authorization 已脱敏，Protocol-Version=1")

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                postLog(callbacks, "WebSocket 已打开，发送 hello")
                val sent = sendRawText(webSocket, XiaozhiMessage.hello(), requireSession = false)
                if (!sent && !helloReceived.isCompleted) {
                    helloReceived.complete(false)
                }
                postNetworkState(callbacks, NetworkState.HelloSent)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val pretty = XiaozhiMessage.prettyJson(text)
                saveLastJson(pretty)

                val type = XiaozhiMessage.messageType(text)
                val json = runCatching { JSONObject(text) }.getOrNull()
                val incomingSessionId = json?.optString("session_id").orEmpty()
                if (incomingSessionId.isNotBlank()) {
                    sessionId = incomingSessionId
                }

                if (type == "hello") {
                    val transport = json?.optString("transport").orEmpty()
                    if (transport.isNotBlank() && transport != "websocket") {
                        postError(callbacks, "服务端 hello transport 不支持：$transport")
                        if (!helloReceived.isCompleted) helloReceived.complete(false)
                        return
                    }

                    if (sessionId.isBlank()) {
                        postError(callbacks, "服务端 hello 未返回 session_id，需要重新连接")
                        if (!helloReceived.isCompleted) helloReceived.complete(false)
                        return
                    }

                    connected = true
                    connecting = false
                    postLog(callbacks, "收到服务端 hello，session_id=$sessionId")
                    postConnected(callbacks, sessionId)
                    postNetworkState(callbacks, NetworkState.Connected)
                    if (!helloReceived.isCompleted) helloReceived.complete(true)
                    return
                }

                postIncomingJson(callbacks, pretty, type)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                postBinaryFrame(callbacks, bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                connecting = false
                postLog(callbacks, "WebSocket 正在关闭：$code $reason")
                postNetworkState(callbacks, NetworkState.Closing)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                connecting = false
                sessionId = ""
                postClosed(callbacks, "WebSocket 已关闭：$code $reason")
                postNetworkState(callbacks, NetworkState.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                connecting = false
                sessionId = ""
                val responseInfo = response?.let { "HTTP ${it.code}" }.orEmpty()
                val message = listOf(responseInfo, t.message ?: t::class.java.simpleName)
                    .filter { it.isNotBlank() }
                    .joinToString("：")
                postError(callbacks, "WebSocket 连接失败：$message")
                postNetworkState(callbacks, NetworkState.Error)
                if (!helloReceived.isCompleted) helloReceived.complete(false)
            }
        }

        webSocket = httpClient.newWebSocket(request, listener)

        return try {
            val success = withTimeout(HELLO_TIMEOUT_MS) {
                helloReceived.await()
            }
            if (!success) {
                close("hello_failed")
            }
            success
        } catch (_: TimeoutCancellationException) {
            connecting = false
            connected = false
            sessionId = ""
            postError(callbacks, "等待服务端 hello 超时")
            close("hello_timeout")
            false
        } finally {
            connecting = false
        }
    }

    fun sendWakeText(text: String): Boolean {
        return sendSessionPayload(XiaozhiMessage.listenDetect(sessionId, text))
    }

    fun sendStartManualListening(): Boolean {
        return sendStartListening(mode = "manual")
    }

    fun sendStartListening(mode: String = "manual"): Boolean {
        return sendSessionPayload(XiaozhiMessage.startListening(sessionId, mode = mode))
    }

    fun sendStopListening(): Boolean {
        return sendSessionPayload(XiaozhiMessage.stopListening(sessionId))
    }

    fun sendAbort(): Boolean {
        return sendSessionPayload(XiaozhiMessage.abort(sessionId))
    }

    fun sendMcpMessage(payload: JSONObject): Boolean {
        return sendSessionPayload(XiaozhiMessage.mcp(sessionId, payload))
    }

    fun sendAudioFrame(opusFrame: ByteArray): Boolean {
        return synchronized(sendLock) {
            val socket = webSocket ?: return@synchronized false
            if (!hasActiveSession()) return@synchronized false
            socket.send(opusFrame.toByteString())
        }
    }

    fun close(reason: String = "client_close") {
        synchronized(sendLock) {
            connected = false
            connecting = false
            sessionId = ""
            webSocket?.close(NORMAL_CLOSE_CODE, reason.take(MAX_CLOSE_REASON_LENGTH))
            webSocket = null
        }
    }

    private fun closeSilently() {
        try {
            close("reconnect")
        } catch (_: Exception) {
            webSocket = null
            connected = false
            connecting = false
            sessionId = ""
        }
    }

    private fun sendSessionPayload(message: String): Boolean {
        return synchronized(sendLock) {
            val socket = webSocket ?: return@synchronized false
            sendRawText(socket, message, requireSession = true)
        }
    }

    private fun sendRawText(
        socket: WebSocket,
        message: String,
        requireSession: Boolean,
    ): Boolean {
        if (requireSession && !hasActiveSession()) return false
        return socket.send(message)
    }

    private fun validateWebSocketConfig(config: AppConfig) {
        require(config.websocketUrl.isNotBlank()) { "WebSocket URL 未配置，请先执行 OTA / 激活" }
        require(config.websocketToken.isNotBlank()) { "WebSocket token 未配置，请先执行 OTA / 激活" }
        require(config.clientId.isNotBlank()) { "Client ID 未生成" }
        require(config.deviceId.isNotBlank()) { "Device ID 未生成" }
    }

    private fun buildWebSocketRequest(config: AppConfig): Request {
        return Request.Builder()
            .url(config.websocketUrl)
            .addHeader("Authorization", "Bearer ${config.websocketToken}")
            .addHeader("Protocol-Version", "1")
            .addHeader("Device-Id", config.deviceId)
            .addHeader("Client-Id", config.clientId)
            .build()
    }

    private fun saveLastJson(pretty: String) {
        appScope.launch {
            configRepository.saveLastJson(pretty.take(MAX_LAST_JSON_LENGTH))
        }
    }

    private fun postLog(callbacks: Callbacks, message: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            callbacks.onLog(message)
        }
    }

    private fun postConnected(callbacks: Callbacks, sessionId: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            callbacks.onConnected(sessionId)
        }
    }

    private fun postIncomingJson(callbacks: Callbacks, pretty: String, type: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            callbacks.onIncomingJson(pretty, type)
        }
    }

    private fun postBinaryFrame(callbacks: Callbacks, frame: ByteArray) {
        appScope.launch(Dispatchers.Main.immediate) {
            callbacks.onBinaryFrame(frame)
        }
    }

    private fun postClosed(callbacks: Callbacks, reason: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            callbacks.onClosed(reason)
        }
    }

    private fun postError(callbacks: Callbacks, message: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            callbacks.onError(message)
        }
    }

    private fun postNetworkState(callbacks: Callbacks, state: NetworkState) {
        appScope.launch(Dispatchers.Main.immediate) {
            callbacks.onNetworkStateChanged(state)
        }
    }

    data class Callbacks(
        val onLog: (String) -> Unit,
        val onConnected: (sessionId: String) -> Unit,
        val onIncomingJson: (prettyJson: String, type: String) -> Unit,
        val onBinaryFrame: (ByteArray) -> Unit,
        val onClosed: (reason: String) -> Unit,
        val onError: (message: String) -> Unit,
        val onNetworkStateChanged: (NetworkState) -> Unit,
    )

    private companion object {
        const val HELLO_TIMEOUT_MS = 10_000L
        const val NORMAL_CLOSE_CODE = 1000
        const val MAX_CLOSE_REASON_LENGTH = 100
        const val MAX_LAST_JSON_LENGTH = 8_000
    }
}

private fun defaultHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
