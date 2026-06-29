package com.er1cmo.xiaozhiandroid.domain

/**
 * Main conversation/network state for the Android client.
 *
 * Phase 1 only simulates these states locally. Later phases will update them
 * from DeviceIdentityManager, OtaActivationClient and XiaozhiWebSocketClient.
 */
enum class ConversationState(
    val label: String,
    val description: String,
) {
    Idle(
        label = "待命",
        description = "等待用户操作",
    ),
    Disconnected(
        label = "未连接",
        description = "尚未连接小智服务端",
    ),
    Connecting(
        label = "连接中",
        description = "正在连接服务端",
    ),
    Connected(
        label = "已连接",
        description = "服务端连接已建立",
    ),
    Listening(
        label = "聆听中",
        description = "正在等待用户说话",
    ),
    Speaking(
        label = "说话中",
        description = "正在播放小智回复",
    ),
    Error(
        label = "错误",
        description = "发生异常，请查看调试日志",
    ),
}
