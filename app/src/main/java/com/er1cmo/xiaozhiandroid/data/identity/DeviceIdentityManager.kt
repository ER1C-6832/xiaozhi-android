package com.er1cmo.xiaozhiandroid.data.identity

import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class DeviceIdentityManager(
    private val configRepository: ConfigRepository,
) {
    suspend fun ensureIdentity(): DeviceIdentity {
        configRepository.ensureDefaultConfig()

        val config = configRepository.getConfig()
        if (
            config.clientId.isNotBlank() &&
            config.deviceId.isNotBlank() &&
            config.serialNumber.isNotBlank() &&
            config.hmacKey.isNotBlank()
        ) {
            return DeviceIdentity(
                clientId = config.clientId,
                deviceId = config.deviceId,
                serialNumber = config.serialNumber,
                hmacKey = config.hmacKey,
            )
        }

        val clientId = config.clientId.ifBlank { UUID.randomUUID().toString() }
        val deviceId = config.deviceId.ifBlank { generatePseudoMac(clientId) }
        val serialNumber = config.serialNumber.ifBlank { generateSerialNumber(deviceId) }
        val hmacKey = config.hmacKey.ifBlank {
            sha256Hex("$clientId|$deviceId|$serialNumber")
        }

        val identity = DeviceIdentity(
            clientId = clientId,
            deviceId = deviceId,
            serialNumber = serialNumber,
            hmacKey = hmacKey,
        )
        configRepository.saveIdentity(identity)
        return identity
    }

    private fun generatePseudoMac(clientId: String): String {
        val hash = sha256Bytes("$clientId|${UUID.randomUUID()}")
        val tail = hash.take(5).map { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
        return listOf("02", *tail.toTypedArray()).joinToString(":")
    }

    private fun generateSerialNumber(deviceId: String): String {
        val macClean = deviceId.lowercase(Locale.US).replace(":", "")
        val shortHash = sha256Hex(macClean).take(8).uppercase(Locale.US)
        return "SN-$shortHash-$macClean"
    }

    private fun sha256Hex(value: String): String = sha256Bytes(value).joinToString("") { byte ->
        String.format(Locale.US, "%02x", byte.toInt() and 0xff)
    }

    private fun sha256Bytes(value: String): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    }
}
