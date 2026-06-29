package com.er1cmo.xiaozhiandroid.network

/**
 * Lightweight WebSocket connection state used by Phase 3.
 */
enum class NetworkState(
    val label: String,
) {
    Disconnected("未连接"),
    Connecting("连接中"),
    HelloSent("已发送 hello"),
    Connected("已连接"),
    Closing("关闭中"),
    Error("错误"),
}
