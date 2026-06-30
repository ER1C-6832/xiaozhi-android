package com.er1cmo.xiaozhiandroid.domain

data class ConversationUiState(
    val conversationState: ConversationState = ConversationState.Idle,
    val textInput: String = "",
    val isManualMode: Boolean = true,
    val isDebugExpanded: Boolean = true,
    val clientId: String = "未生成",
    val deviceId: String = "未生成",
    val serialNumber: String = "未生成",
    val hmacKeyStatus: String = "未生成",
    val activationStatus: String = "未开始",
    val activationCode: String = "暂无",
    val activationMessage: String = "暂无",
    val otaStatus: String = "未请求",
    val websocketStatus: String = "未连接",
    val autoReconnectStatus: String = "未触发",
    val sessionId: String = "暂无",
    val audioUplinkStatus: String = "未启动",
    val audioPlaybackStatus: String = "未播放",
    val debugModeStatus: String = "已开启，日志保留最近 120 行",
    val lastError: String = "暂无",
    val otaUrl: String = "https://api.tenclass.net/xiaozhi/ota/",
    val authorizationUrl: String = "https://xiaozhi.me/",
    val websocketUrl: String = "等待 OTA 下发",
    val websocketTokenStatus: String = "未下发",
    val activationVersion: String = "v2",
    val lastServerJson: String = "暂无",
    val debugLogs: List<String> = listOf("[本地] App 启动，等待操作"),
) {
    val statusLabel: String
        get() = conversationState.label

    val statusDescription: String
        get() = conversationState.description
}
