package com.er1cmo.xiaozhiandroid.domain

/**
 * Product-facing conversation state.
 *
 * Phase 10 distinguishes disconnected from connected-idle: Idle is now shown as
 * "未连接", while Connected is the calm ready state "待命".
 */
enum class ConversationState(
    val label: String,
    val description: String,
) {
    Idle(
        label = "未连接",
        description = "轻触连接入口后开始",
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
        description = "小智已准备好",
    ),
    Listening(
        label = "聆听中",
        description = "正在接收你的声音",
    ),
    Thinking(
        label = "思考中",
        description = "正在组织回复",
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
