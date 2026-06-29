package com.er1cmo.xiaozhiandroid.data.ota

enum class ActivationState(
    val label: String,
) {
    NotStarted("未开始"),
    OtaRequesting("OTA 请求中"),
    OtaSuccess("OTA 成功"),
    NeedActivation("等待激活"),
    Activating("激活轮询中"),
    Activated("已激活"),
    Failed("失败"),
}
