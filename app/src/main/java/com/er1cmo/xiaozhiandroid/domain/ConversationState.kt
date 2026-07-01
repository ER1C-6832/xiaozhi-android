package com.er1cmo.xiaozhiandroid.domain

/**
 * Main product-facing conversation state for the Android client.
 *
 * Phase 10 organic UI separates the disconnected state from the connected
 * standby state: Idle is now product-facing "未连接", Connected is "待命".
 */
enum class ConversationState(
    val label: String,
    val description: String,
) {
    Idle(
        label = "未连接",
        description = "轻触连接入口，唤醒小智",
    ),
    Activating(
        label = "激活中",
        description = "正在完成设备激活",
    ),
    Connecting(
        label = "连接中",
        description = "正在连接服务端",
    ),
    Connected(
        label = "待命",
        description = "我在这里，随时可以开始",
    ),
    Listening(
        label = "聆听中",
        description = "请说，我在认真听",
    ),
    Thinking(
        label = "思考中",
        description = "正在整理你的问题",
    ),
    Speaking(
        label = "说话中",
        description = "正在回应你",
    ),
    Error(
        label = "出错了",
        description = "轻触重连或查看开发者抽屉",
    ),
}
