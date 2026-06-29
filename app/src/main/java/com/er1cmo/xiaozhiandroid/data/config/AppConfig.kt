package com.er1cmo.xiaozhiandroid.data.config

/**
 * App-wide persisted configuration for Phase 2A.
 *
 * This intentionally mirrors the py-xiaozhi idea of keeping stable device
 * identity and network configuration, but uses Android DataStore instead of
 * desktop config files.
 */
data class AppConfig(
    val clientId: String = "",
    val deviceId: String = "",
    val serialNumber: String = "",
    val hmacKey: String = "",
    val activationStatus: Boolean = false,
    val otaUrl: String = DEFAULT_OTA_URL,
    val authorizationUrl: String = DEFAULT_AUTHORIZATION_URL,
    val websocketUrl: String = "",
    val websocketToken: String = "",
    val activationVersion: String = DEFAULT_ACTIVATION_VERSION,
    val lastJson: String = "",
) {
    val websocketTokenStatus: String
        get() = if (websocketToken.isBlank()) "未下发" else "已保存（隐藏）"

    val websocketUrlDisplay: String
        get() = websocketUrl.ifBlank { "等待 OTA 下发" }

    companion object {
        const val DEFAULT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"
        const val DEFAULT_AUTHORIZATION_URL = "https://xiaozhi.me/"
        const val DEFAULT_ACTIVATION_VERSION = "v2"
    }
}
