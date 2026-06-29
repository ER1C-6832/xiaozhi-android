package com.er1cmo.xiaozhiandroid.data.identity

data class DeviceIdentity(
    val clientId: String,
    val deviceId: String,
    val serialNumber: String,
    val hmacKey: String,
)
