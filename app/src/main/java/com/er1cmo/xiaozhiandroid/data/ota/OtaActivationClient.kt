package com.er1cmo.xiaozhiandroid.data.ota

import com.er1cmo.xiaozhiandroid.BuildConfig
import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OtaActivationClient(
    private val configRepository: ConfigRepository,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    suspend fun runOtaAndActivation(
        onLog: (String) -> Unit,
    ): OtaActivationOutcome = withContext(Dispatchers.IO) {
        val config = configRepository.getConfig()
        validateConfig(config)

        onLog("OTA 请求开始：${config.otaUrl}")
        val otaResponse = requestOta(config)
        configRepository.saveLastJson(otaResponse.redactedJson)
        onLog("OTA 响应已保存到最近 JSON（token 已脱敏）")

        if (!otaResponse.websocketUrl.isNullOrBlank()) {
            val token = otaResponse.websocketToken ?: DEFAULT_TOKEN_WHEN_EMPTY
            configRepository.saveWebSocketConfig(otaResponse.websocketUrl, token)
            onLog("WebSocket 配置已下发：${otaResponse.websocketUrl}")
        } else {
            onLog("OTA 响应未包含 websocket.url")
        }

        val activation = otaResponse.activation
        if (activation == null) {
            configRepository.setActivationStatus(true)
            configRepository.clearActivationData()
            onLog("OTA 未返回 activation 字段，按 py-xiaozhi 逻辑视为服务端已授权")
            return@withContext OtaActivationOutcome(
                state = ActivationState.Activated,
                message = "OTA 成功，设备已授权",
            )
        }

        configRepository.setActivationStatus(false)
        configRepository.saveActivationData(
            code = activation.code,
            challenge = activation.challenge,
            message = activation.message,
        )
        onLog("设备需要激活，验证码：${activation.code}")
        onLog("请打开 ${config.authorizationUrl} 添加设备并输入验证码")

        val activated = pollActivation(
            config = config,
            activation = activation,
            onLog = onLog,
        )
        if (activated) {
            configRepository.setActivationStatus(true)
            configRepository.clearActivationData()
            OtaActivationOutcome(
                state = ActivationState.Activated,
                message = "设备激活成功",
            )
        } else {
            configRepository.setActivationStatus(false)
            OtaActivationOutcome(
                state = ActivationState.Failed,
                message = "激活超时或失败，请确认验证码是否已在授权页面输入",
            )
        }
    }

    private fun validateConfig(config: AppConfig) {
        require(config.otaUrl.isNotBlank()) { "OTA URL 未配置" }
        require(config.clientId.isNotBlank()) { "Client ID 未生成" }
        require(config.deviceId.isNotBlank()) { "Device ID 未生成" }
        require(config.serialNumber.isNotBlank()) { "序列号未生成" }
        require(config.hmacKey.isNotBlank()) { "HMAC 密钥未生成" }
    }

    private fun requestOta(config: AppConfig): OtaResponse {
        val payload = buildOtaPayload(config).toString()
        val request = Request.Builder()
            .url(config.otaUrl)
            .headers(buildOtaHeaders(config))
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("OTA 服务器返回 HTTP ${response.code}: ${responseBody.take(200)}")
            }
            return parseOtaResponse(responseBody)
        }
    }

    private fun buildOtaHeaders(config: AppConfig): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
            .add("Device-Id", config.deviceId)
            .add("Client-Id", config.clientId)
            .add("Content-Type", "application/json")
            .add("User-Agent", "$BOARD_TYPE/$APP_NAME-${BuildConfig.VERSION_NAME}")
            .add("Accept-Language", "zh-CN")

        if (config.activationVersion == "v2") {
            // Mirrors py-xiaozhi OTA behavior: v2 requests include Activation-Version.
            builder.add("Activation-Version", BuildConfig.VERSION_NAME)
        }
        return builder.build()
    }

    private fun buildOtaPayload(config: AppConfig): JSONObject {
        return JSONObject()
            .put(
                "application",
                JSONObject()
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("elf_sha256", config.hmacKey),
            )
            .put(
                "board",
                JSONObject()
                    .put("type", BOARD_TYPE)
                    .put("name", APP_NAME)
                    .put("ip", getLocalIpAddress())
                    .put("mac", config.deviceId),
            )
    }

    private suspend fun pollActivation(
        config: AppConfig,
        activation: ActivationInfo,
        onLog: (String) -> Unit,
    ): Boolean {
        val activateUrl = "${config.otaUrl.trimEnd('/')}/activate"
        val signature = hmacSha256Hex(config.hmacKey, activation.challenge)
        val payload = JSONObject()
            .put(
                "Payload",
                JSONObject()
                    .put("algorithm", "hmac-sha256")
                    .put("serial_number", config.serialNumber)
                    .put("challenge", activation.challenge)
                    .put("hmac", signature),
            )
            .toString()

        val requestBuilder = Request.Builder()
            .url(activateUrl)
            .headers(buildActivationHeaders(config))
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))

        onLog("激活轮询开始：$activateUrl")
        repeat(ACTIVATION_MAX_RETRIES) { attempt ->
            val request = requestBuilder.build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            onLog("激活成功：HTTP 200")
                            return true
                        }
                        202 -> {
                            if (attempt == 0 || (attempt + 1) % 6 == 0) {
                                onLog("等待用户输入验证码：${activation.code}，第 ${attempt + 1}/$ACTIVATION_MAX_RETRIES 次检查")
                            }
                        }
                        else -> {
                            val body = response.body?.string().orEmpty().take(200)
                            onLog("激活服务器返回 HTTP ${response.code}，继续等待：$body")
                        }
                    }
                }
            } catch (exception: Exception) {
                onLog("激活请求异常，继续重试：${exception.message ?: exception::class.java.simpleName}")
            }

            delay(ACTIVATION_RETRY_INTERVAL_MS)
        }
        onLog("激活失败：达到最大轮询次数")
        return false
    }

    private fun buildActivationHeaders(config: AppConfig): okhttp3.Headers {
        return okhttp3.Headers.Builder()
            .add("Activation-Version", "2")
            .add("Device-Id", config.deviceId)
            .add("Client-Id", config.clientId)
            .add("Content-Type", "application/json")
            .build()
    }

    private fun hmacSha256Hex(key: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface ->
                    Collections.list(networkInterface.inetAddresses).asSequence()
                }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
                ?: FALLBACK_IP
        } catch (_: Exception) {
            FALLBACK_IP
        }
    }

    private companion object {
        const val APP_NAME = "xiaozhi-android"
        const val BOARD_TYPE = "android"
        const val FALLBACK_IP = "127.0.0.1"
        const val DEFAULT_TOKEN_WHEN_EMPTY = "test-token"
        const val ACTIVATION_MAX_RETRIES = 60
        const val ACTIVATION_RETRY_INTERVAL_MS = 5_000L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}
