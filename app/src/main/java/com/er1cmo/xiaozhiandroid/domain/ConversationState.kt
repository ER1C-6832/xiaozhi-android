package com.er1cmo.xiaozhiandroid.domain

/**
 * Main product-facing conversation state for the Android client.
 *
 * Phase 10 adjusts the user-facing labels: Idle means the app is not connected,
 * while Connected is the real standby state after WebSocket hello succeeds.
 */
enum class ConversationState(
    val label: String,
    val description: String,
) {
    Idle(
        label = "未连接",
        description = "尚未连接服务端",
    ),
    Activating(
        label = "激活中",
        description = "正在执行 OTA / 激活流程",
    ),
    Connecting(
        label = "连接中",
        description = "正在连接服务端",
    ),
    Connected(
        label = "待命",
        description = "已连接，等待你说话或输入",
    ),
    Listening(
        label = "聆听中",
        description = "正在捕捉你的声音",
    ),
    Thinking(
        label = "思考中",
        description = "小智正在组织回复",
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
