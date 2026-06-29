package com.er1cmo.xiaozhiandroid.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 1 ViewModel-like state holder.
 *
 * It intentionally has no Android framework dependency yet, so the first UI
 * skeleton can compile with the default Android Studio Compose dependencies.
 * Later we can replace it with androidx.lifecycle.ViewModel when adding
 * repositories, coroutines, OTA and WebSocket.
 */
class MainViewModel {
    var uiState by mutableStateOf(ConversationUiState())
        private set

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
            websocketStatus = "等待接入 XiaozhiWebSocketClient",
        )
        appendLocalLog("连接入口已触发：后续在这里接入 OTA / WebSocket")
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
            lastServerJson = "等待服务端 JSON 响应（Phase 1 占位）",
        )
        appendLocalLog("发送文本：$text")
        appendLocalLog("后续将通过 listen/detect/text 发送到小智 WebSocket")
    }

    fun appendLocalLog(message: String) {
        val nextLogs = (uiState.debugLogs + "${timestamp()} $message").takeLast(MAX_LOG_LINES)
        uiState = uiState.copy(debugLogs = nextLogs)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private companion object {
        const val MAX_LOG_LINES = 80
    }
}
