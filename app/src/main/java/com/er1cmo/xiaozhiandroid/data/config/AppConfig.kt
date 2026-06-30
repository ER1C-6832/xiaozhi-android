package com.er1cmo.xiaozhiandroid.data.config

/**
 * App-wide persisted configuration.
 *
 * The model follows py-xiaozhi's idea of keeping stable identity, OTA and
 * activation state, while using Android DataStore instead of desktop files.
 */
data class AppConfig(
    val clientId: String = "",
    val deviceId: String = "",
    val serialNumber: String = "",
    val hmacKey: String = "",
    val activationStatus: Boolean = false,
    val activationCode: String = "",
    val activationChallenge: String = "",
    val activationMessage: String = "",
    val otaUrl: String = DEFAULT_OTA_URL,
    val authorizationUrl: String = DEFAULT_AUTHORIZATION_URL,
    val websocketUrl: String = "",
    val websocketToken: String = "",
    val activationVersion: String = DEFAULT_ACTIVATION_VERSION,
    val lastJson: String = "",
    val developerModeEnabled: Boolean = true,
) {
    val websocketTokenStatus: String
        get() = if (websocketToken.isBlank()) "未下发" else "已保存（隐藏）"

    val websocketUrlDisplay: String
        get() = websocketUrl.ifBlank { "等待 OTA 下发" }

    val activationCodeDisplay: String
        get() = activationCode.ifBlank { "暂无" }

    val debugModeStatus: String
        get() = if (developerModeEnabled) {
            "已开启，日志保留最近 120 行"
        } else {
            "已关闭，主界面隐藏详细日志"
        }

    companion object {
        const val DEFAULT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"
        const val DEFAULT_AUTHORIZATION_URL = "https://xiaozhi.me/"
        const val DEFAULT_ACTIVATION_VERSION = "v2"
    }
}
