package com.er1cmo.xiaozhiandroid.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

class MainViewModel(
    private val configRepository: ConfigRepository,
    private val deviceIdentityManager: DeviceIdentityManager,
) {
    var uiState by mutableStateOf(ConversationUiState())
        private set

    private var hasStartedConfigCollection = false

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

    fun simulateConnectEntry() {
        uiState = uiState.copy(
            conversationState = ConversationState.Connecting,
            otaStatus = "Phase 2B 接入 OTA 请求",
            websocketStatus = "等待 OTA 下发 WebSocket 配置",
        )
        appendLocalLog("连接入口已触发：Phase 2A 已有配置与设备身份，下一步接入 OTA / 激活")
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

    private fun applyConfig(config: AppConfig) {
        val hasWebSocketUrl = config.websocketUrl.isNotBlank()
        uiState = uiState.copy(
            clientId = config.clientId.ifBlank { "未生成" },
            deviceId = config.deviceId.ifBlank { "未生成" },
            serialNumber = config.serialNumber.ifBlank { "未生成" },
            hmacKeyStatus = if (config.hmacKey.isBlank()) "未生成" else "已生成（隐藏）",
            activationStatus = if (config.activationStatus) "已激活" else "未激活",
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
        const val MAX_LOG_LINES = 80
    }
}
