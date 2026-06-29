package com.er1cmo.xiaozhiandroid.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.audio.AudioEngine
import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.ActivationState
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
import com.er1cmo.xiaozhiandroid.network.NetworkState
import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val configRepository: ConfigRepository,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val otaActivationClient: OtaActivationClient,
    private val xiaozhiWebSocketClient: XiaozhiWebSocketClient,
    private val audioEngine: AudioEngine,
    private val appScope: CoroutineScope,
) {
    var uiState by mutableStateOf(ConversationUiState())
        private set

    private var hasStartedConfigCollection = false
    private var isOtaActivationRunning = false
    private var isWebSocketConnecting = false

    suspend fun initialize() {
        if (hasStartedConfigCollection) return
        hasStartedConfigCollection = true

        appendLocalLog("初始化配置存储：DataStore")
        try {
            val identity = deviceIdentityManager.ensureIdentity()
            appendLocalLog("设备身份已准备：${identity.deviceId}")
        } catch (exception: Exception) {
            uiState = uiState.copy(conversationState = ConversationState.Error)
            appendLocalLog("设备身份初始化失败：${exception.message ?: exception::class.java.simpleName}")
        }

        configRepository.configFlow.collectLatest { config ->
            applyConfig(config)
        }
    }

    fun updateTextInput(value: String) {
        uiState = uiState.copy(textInput = value)
    }

    fun toggleManualMode() {
        val nextMode = !uiState.isManualMode
        uiState = uiState.copy(isManualMode = nextMode)
        appendLocalLog(if (nextMode) "切换为手动对话模式" else "切换为自动对话模式（占位）")
    }

    fun toggleDebugPanel() {
        uiState = uiState.copy(isDebugExpanded = !uiState.isDebugExpanded)
    }

    fun requestMicrophonePermission() {
        uiState = uiState.copy(audioUplinkStatus = "等待麦克风权限")
        appendLocalLog("需要麦克风权限：请允许 RECORD_AUDIO 后再次按住说话")
    }

    fun onMicrophonePermissionGranted() {
        uiState = uiState.copy(audioUplinkStatus = "权限已允许，等待按住说话")
        appendLocalLog("麦克风权限已允许，请再次按住说话开始上传语音")
    }

    fun onMicrophonePermissionDenied() {
        uiState = uiState.copy(audioUplinkStatus = "麦克风权限被拒绝")
        appendLocalLog("麦克风权限被拒绝，无法进行语音上行")
    }

    fun startManualListening() {
        if (!xiaozhiWebSocketClient.isConnected()) {
            uiState = uiState.copy(
                conversationState = ConversationState.Idle,
                audioUplinkStatus = "未连接，无法录音上传",
            )
            appendLocalLog("按住后说话失败：WebSocket 未连接，请先点击连接入口")
            return
        }
        if (audioEngine.isRecording()) {
            appendLocalLog("音频上行已在运行")
            return
        }

        val startSent = xiaozhiWebSocketClient.sendStartManualListening()
        if (!startSent) {
            uiState = uiState.copy(
                conversationState = ConversationState.Error,
                audioUplinkStatus = "listen/start 发送失败",
            )
            appendLocalLog("listen/start/manual 发送失败，未启动录音")
            return
        }

        uiState = uiState.copy(
            conversationState = ConversationState.Listening,
            audioUplinkStatus = "正在启动录音",
        )
        appendLocalLog("已发送 listen/start/manual，开始麦克风录音与 Opus 上行")

        audioEngine.startRecording(
            onEncodedFrame = { opusFrame -> xiaozhiWebSocketClient.sendAudioFrame(opusFrame) },
            onLog = ::appendLocalLogFromAnyThread,
            onStatusChanged = ::updateAudioStatusFromAnyThread,
        )
    }

    fun stopManualListening() {
        val wasRecording = audioEngine.isRecording()
        val shouldSendStop = xiaozhiWebSocketClient.isConnected() &&
            (wasRecording || uiState.conversationState == ConversationState.Listening)
        audioEngine.stopRecording()
        uiState = uiState.copy(
            conversationState = if (xiaozhiWebSocketClient.isConnected()) ConversationState.Connected else ConversationState.Idle,
            audioUplinkStatus = if (wasRecording) "停止中" else uiState.audioUplinkStatus,
        )
        if (shouldSendStop) {
            val sent = xiaozhiWebSocketClient.sendStopListening()
            appendLocalLog(if (sent) "已发送 listen/stop" else "listen/stop 发送失败")
        } else if (!xiaozhiWebSocketClient.isConnected()) {
            appendLocalLog("按住后说话：停止聆听（WebSocket 未连接）")
        }
    }

    fun abortConversation() {
        if (audioEngine.isRecording()) {
            audioEngine.stopRecording()
            appendLocalLog("打断对话：已停止本地录音上行")
        }
        val sent = if (xiaozhiWebSocketClient.isConnected()) {
            xiaozhiWebSocketClient.sendAbort()
        } else {
            false
        }
        uiState = uiState.copy(
            conversationState = if (xiaozhiWebSocketClient.isConnected()) ConversationState.Connected else ConversationState.Idle,
            audioUplinkStatus = if (audioEngine.isRecording()) "停止中" else uiState.audioUplinkStatus,
        )
        appendLocalLog(if (sent) "已发送 abort/user_interruption" else "打断对话：WebSocket 未连接，已本地回到待命")
    }

    fun handleConnectionEntry() {
        if (isOtaActivationRunning || isWebSocketConnecting) {
            appendLocalLog("连接流程正在运行，请稍候")
            return
        }
        if (xiaozhiWebSocketClient.isConnected()) {
            appendLocalLog("WebSocket 已连接，无需重复连接")
            return
        }

        appScope.launch {
            uiState = uiState.copy(
                conversationState = ConversationState.Connecting,
                websocketStatus = "准备连接",
            )
            try {
                val config = configRepository.getConfig()
                val hasWebSocketConfig = config.websocketUrl.isNotBlank() && config.websocketToken.isNotBlank()
                if (!hasWebSocketConfig) {
                    appendLocalLog("未找到 WebSocket 配置，先执行 OTA / 激活")
                    val otaOk = runOtaActivationInternal()
                    if (!otaOk) return@launch
                } else {
                    appendLocalLog("检测到已保存 WebSocket 配置，直接连接 WSS")
                }

                connectWebSocketInternal()
            } catch (exception: Exception) {
                uiState = uiState.copy(
                    conversationState = ConversationState.Error,
                    websocketStatus = "连接失败",
                )
                appendLocalLog("连接入口失败：${exception.message ?: exception::class.java.simpleName}")
            }
        }
    }

    fun startOtaActivation() {
        appScope.launch {
            runOtaActivationInternal()
        }
    }

    fun sendText() {
        val text = uiState.textInput.trim()
        if (text.isEmpty()) {
            appendLocalLog("发送文本失败：输入内容为空")
            return
        }

        if (!xiaozhiWebSocketClient.isConnected()) {
            appendLocalLog("发送文本失败：WebSocket 未连接，请先点击连接入口")
            return
        }

        val sent = xiaozhiWebSocketClient.sendWakeText(text)
        if (!sent) {
            uiState = uiState.copy(conversationState = ConversationState.Error)
            appendLocalLog("发送 listen/detect/text 失败")
            return
        }

        uiState = uiState.copy(
            textInput = "",
            conversationState = ConversationState.Connected,
            lastServerJson = "等待服务端 JSON 响应",
        )
        appendLocalLog("已发送 listen/detect/text：$text")
    }

    fun appendLocalLog(message: String) {
        val nextLogs = (uiState.debugLogs + "${timestamp()} $message").takeLast(MAX_LOG_LINES)
        uiState = uiState.copy(debugLogs = nextLogs)
    }

    private suspend fun runOtaActivationInternal(): Boolean {
        if (isOtaActivationRunning) {
            appendLocalLog("OTA / 激活流程正在运行，请稍候")
            return false
        }

        isOtaActivationRunning = true
        uiState = uiState.copy(
            conversationState = ConversationState.Connecting,
            otaStatus = ActivationState.OtaRequesting.label,
            websocketStatus = "等待 OTA 下发 WebSocket 配置",
        )
        appendLocalLog("开始执行 OTA / 激活流程")

        return try {
            val outcome = otaActivationClient.runOtaAndActivation { message ->
                appendLocalLogFromAnyThread(message)
            }
            val hasWebSocketConfig = configRepository.getConfig().websocketUrl.isNotBlank()
            uiState = uiState.copy(
                conversationState = when (outcome.state) {
                    ActivationState.Activated,
                    ActivationState.OtaSuccess -> if (hasWebSocketConfig) ConversationState.Connecting else ConversationState.Connected
                    ActivationState.Failed -> ConversationState.Error
                    else -> ConversationState.Connecting
                },
                otaStatus = outcome.state.label,
                websocketStatus = if (hasWebSocketConfig) {
                    "已获取配置，待连接"
                } else {
                    uiState.websocketStatus
                },
            )
            appendLocalLog(outcome.message)
            outcome.state != ActivationState.Failed && hasWebSocketConfig
        } catch (exception: Exception) {
            uiState = uiState.copy(
                conversationState = ConversationState.Error,
                otaStatus = "失败",
                websocketStatus = "未连接",
            )
            appendLocalLog("OTA / 激活失败：${exception.message ?: exception::class.java.simpleName}")
            false
        } finally {
            isOtaActivationRunning = false
        }
    }

    private suspend fun connectWebSocketInternal(): Boolean {
        if (xiaozhiWebSocketClient.isConnected()) {
            uiState = uiState.copy(
                conversationState = ConversationState.Connected,
                websocketStatus = "已连接",
            )
            appendLocalLog("WebSocket 已连接")
            return true
        }
        if (isWebSocketConnecting) {
            appendLocalLog("WebSocket 正在连接，请稍候")
            return false
        }

        isWebSocketConnecting = true
        uiState = uiState.copy(
            conversationState = ConversationState.Connecting,
            websocketStatus = "WebSocket 连接中",
        )

        val connected = try {
            xiaozhiWebSocketClient.connect(
                callbacks = XiaozhiWebSocketClient.Callbacks(
                    onLog = ::appendLocalLog,
                    onConnected = { sessionId ->
                        uiState = uiState.copy(
                            conversationState = ConversationState.Connected,
                            websocketStatus = "已连接",
                            sessionId = sessionId.ifBlank { "暂无" },
                        )
                    },
                    onIncomingJson = { prettyJson, type ->
                        val preview = prettyJson.take(MAX_JSON_PREVIEW_LENGTH)
                        uiState = uiState.copy(
                            conversationState = when (type) {
                                "tts" -> ConversationState.Speaking
                                else -> ConversationState.Connected
                            },
                            lastServerJson = preview,
                        )
                        appendLocalLog("收到服务端 JSON：type=$type")
                    },
                    onBinaryFrame = { size ->
                        appendLocalLog("收到二进制音频帧：${size}B（Phase 5 解码播放）")
                    },
                    onClosed = { reason ->
                        audioEngine.stopRecording()
                        uiState = uiState.copy(
                            conversationState = ConversationState.Disconnected,
                            websocketStatus = "已断开",
                            audioUplinkStatus = if (audioEngine.isRecording()) "停止中" else uiState.audioUplinkStatus,
                        )
                        appendLocalLog(reason)
                    },
                    onError = { message ->
                        audioEngine.stopRecording()
                        uiState = uiState.copy(
                            conversationState = ConversationState.Error,
                            websocketStatus = "错误",
                            audioUplinkStatus = if (audioEngine.isRecording()) "停止中" else uiState.audioUplinkStatus,
                        )
                        appendLocalLog(message)
                    },
                    onNetworkStateChanged = { state ->
                        uiState = uiState.copy(websocketStatus = state.label)
                    },
                ),
            )
        } catch (exception: Exception) {
            uiState = uiState.copy(
                conversationState = ConversationState.Error,
                websocketStatus = "连接失败",
            )
            appendLocalLog("WebSocket 连接异常：${exception.message ?: exception::class.java.simpleName}")
            false
        } finally {
            isWebSocketConnecting = false
        }

        if (!connected) {
            uiState = uiState.copy(
                conversationState = ConversationState.Error,
                websocketStatus = "连接失败",
            )
        }
        return connected
    }

    private fun appendLocalLogFromAnyThread(message: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            appendLocalLog(message)
        }
    }

    private fun updateAudioStatusFromAnyThread(status: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            uiState = uiState.copy(audioUplinkStatus = status)
        }
    }

    private fun applyConfig(config: AppConfig) {
        val hasWebSocketUrl = config.websocketUrl.isNotBlank()
        uiState = uiState.copy(
            clientId = config.clientId.ifBlank { "未生成" },
            deviceId = config.deviceId.ifBlank { "未生成" },
            serialNumber = config.serialNumber.ifBlank { "未生成" },
            hmacKeyStatus = if (config.hmacKey.isBlank()) "未生成" else "已生成（隐藏）",
            activationStatus = when {
                config.activationStatus -> "已激活"
                config.activationCode.isNotBlank() -> "等待激活"
                else -> "未激活"
            },
            activationCode = config.activationCodeDisplay,
            activationMessage = config.activationMessage.ifBlank { "暂无" },
            otaUrl = config.otaUrl,
            authorizationUrl = config.authorizationUrl,
            websocketUrl = config.websocketUrlDisplay,
            websocketTokenStatus = config.websocketTokenStatus,
            activationVersion = config.activationVersion,
            websocketStatus = when {
                xiaozhiWebSocketClient.isConnected() -> "已连接"
                uiState.conversationState == ConversationState.Connecting -> uiState.websocketStatus
                hasWebSocketUrl -> "已获取配置，待连接"
                else -> "未连接"
            },
            lastServerJson = config.lastJson.ifBlank { uiState.lastServerJson },
        )
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private companion object {
        const val MAX_LOG_LINES = 160
        const val MAX_JSON_PREVIEW_LENGTH = 1_500
    }
}
