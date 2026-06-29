package com.er1cmo.xiaozhiandroid.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.ActivationState
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
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
    private val appScope: CoroutineScope,
) {
    var uiState by mutableStateOf(ConversationUiState())
        private set

    private var hasStartedConfigCollection = false
    private var isOtaActivationRunning = false

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

    fun startManualListening() {
        uiState = uiState.copy(conversationState = ConversationState.Listening)
        appendLocalLog("按住后说话：开始聆听（本地状态模拟，后续接入录音与 WebSocket）")
    }

    fun stopManualListening() {
        uiState = uiState.copy(conversationState = ConversationState.Idle)
        appendLocalLog("按住后说话：停止聆听（本地状态模拟）")
    }

    fun abortConversation() {
        uiState = uiState.copy(conversationState = ConversationState.Idle)
        appendLocalLog("打断对话：已回到待命（后续发送 abort 消息）")
    }

    fun startOtaActivation() {
        if (isOtaActivationRunning) {
            appendLocalLog("OTA / 激活流程正在运行，请稍候")
            return
        }

        isOtaActivationRunning = true
        uiState = uiState.copy(
            conversationState = ConversationState.Connecting,
            otaStatus = ActivationState.OtaRequesting.label,
            websocketStatus = "等待 OTA 下发 WebSocket 配置",
        )
        appendLocalLog("连接入口已触发：开始执行 OTA / 激活流程")

        appScope.launch {
            try {
                val outcome = otaActivationClient.runOtaAndActivation { message ->
                    appendLocalLogFromAnyThread(message)
                }
                uiState = uiState.copy(
                    conversationState = when (outcome.state) {
                        ActivationState.Activated,
                        ActivationState.OtaSuccess -> ConversationState.Connected
                        ActivationState.Failed -> ConversationState.Error
                        else -> ConversationState.Connecting
                    },
                    otaStatus = outcome.state.label,
                    websocketStatus = if (uiState.websocketUrl != "等待 OTA 下发") {
                        "已获取配置，待连接"
                    } else {
                        uiState.websocketStatus
                    },
                )
                appendLocalLog(outcome.message)
            } catch (exception: Exception) {
                uiState = uiState.copy(
                    conversationState = ConversationState.Error,
                    otaStatus = "失败",
                    websocketStatus = "未连接",
                )
                appendLocalLog("OTA / 激活失败：${exception.message ?: exception::class.java.simpleName}")
            } finally {
                isOtaActivationRunning = false
            }
        }
    }

    fun sendText() {
        val text = uiState.textInput.trim()
        if (text.isEmpty()) {
            appendLocalLog("发送文本失败：输入内容为空")
            return
        }

        uiState = uiState.copy(
            textInput = "",
            conversationState = ConversationState.Connected,
            lastServerJson = "等待 Phase 3 服务端 JSON 响应",
        )
        appendLocalLog("发送文本：$text")
        appendLocalLog("后续将通过 listen/detect/text 发送到小智 WebSocket")
    }

    fun appendLocalLog(message: String) {
        val nextLogs = (uiState.debugLogs + "${timestamp()} $message").takeLast(MAX_LOG_LINES)
        uiState = uiState.copy(debugLogs = nextLogs)
    }

    private fun appendLocalLogFromAnyThread(message: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            appendLocalLog(message)
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
        const val MAX_LOG_LINES = 100
    }
}
