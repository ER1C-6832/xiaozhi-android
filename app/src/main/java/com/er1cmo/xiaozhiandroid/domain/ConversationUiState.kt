package com.er1cmo.xiaozhiandroid.domain

data class ConversationUiState(
    val conversationState: ConversationState = ConversationState.Idle,
    val textInput: String = "",
    val isManualMode: Boolean = true,
    val isDebugExpanded: Boolean = true,
    val clientId: String = "未生成",
    val deviceId: String = "未生成",
    val activationStatus: String = "未开始",
    val otaStatus: String = "未请求",
    val websocketStatus: String = "未连接",
    val lastServerJson: String = "暂无",
    val debugLogs: List<String> = listOf("[本地] App 启动，等待操作"),
) {
    val statusLabel: String
        get() = conversationState.label

    val statusDescription: String
        get() = conversationState.description
}
