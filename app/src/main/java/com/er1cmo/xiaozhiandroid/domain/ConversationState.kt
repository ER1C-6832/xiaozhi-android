package com.er1cmo.xiaozhiandroid.domain

/**
 * Main product-facing conversation state for the Android client.
 *
 * Phase 6 keeps this list intentionally small so network, recording and TTS
 * callbacks converge back to a predictable UI state instead of leaking transport
 * details into the main screen.
 */
enum class ConversationState(
    val label: String,
    val description: String,
) {
    Idle(
        label = "待命",
        description = "等待用户操作",
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
        label = "已连接",
        description = "服务端连接已建立",
    ),
    Listening(
        label = "聆听中",
        description = "正在等待用户说话",
    ),
    Thinking(
        label = "思考中",
        description = "已收到输入，等待服务端回复",
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
