package com.er1cmo.xiaozhiandroid.domain

data class ConversationUiState(
    val conversationState: ConversationState = ConversationState.Idle,
    val textInput: String = "",
    val isManualMode: Boolean = true,
    val voiceMode: VoiceInteractionMode = VoiceInteractionMode.Manual,
    val vadStatus: String = "MANUAL：按住说话",
    val vadSummary: String = "轻量能量阈值 VAD 已准备，AUTO_STOP 模式下启用",
    val isDebugExpanded: Boolean = true,
    val developerModeEnabled: Boolean = true,
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
    val rawWebsocketUrl: String = "",
    val rawWebsocketToken: String = "",
    val websocketTokenStatus: String = "未下发",
    val activationVersion: String = "v2",
    val lastServerJson: String = "暂无",
    val debugLogs: List<String> = listOf("[本地] App 启动，等待操作"),
    val mcpToolCount: Int = 0,
    val mcpToolsPreview: List<McpToolListItemUiState> = emptyList(),
    val mcpToolCalls: List<McpToolCallUiState> = emptyList(),
    val lastMcpStatus: String = "等待 MCP 请求",
) {
    val statusLabel: String
        get() = conversationState.label

    val statusDescription: String
        get() = conversationState.description
}

enum class VoiceInteractionMode(
    val wireName: String,
    val label: String,
    val description: String,
) {
    Manual(
        wireName = "MANUAL",
        label = "手动",
        description = "按住说话，松手后发送 stop。",
    ),
    AutoStop(
        wireName = "AUTO_STOP",
        label = "自然对话",
        description = "点一下开始，检测到停顿后自动发送 stop。",
    ),
    Realtime(
        wireName = "REALTIME",
        label = "实时",
        description = "Phase 11C 全双工模式预留。",
    ),
}

data class McpToolListItemUiState(
    val name: String,
    val category: String,
    val riskLevel: String,
    val permissionHint: String,
    val enabled: Boolean = true,
)

data class McpToolCallUiState(
    val requestId: String,
    val toolName: String,
    val argumentsPreview: String,
    val status: McpToolCallStatus,
    val success: Boolean? = null,
    val resultPreview: String = "等待结果",
    val durationMs: Long? = null,
    val startedAtText: String = "",
)

enum class McpToolCallStatus(val label: String) {
    Running("执行中"),
    Succeeded("成功"),
    Failed("失败"),
    Blocked("待确认"),
}
