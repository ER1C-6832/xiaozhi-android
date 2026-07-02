package com.er1cmo.xiaozhiandroid.wakeword

sealed class WakeWordEvent {
    data class Detected(
        val keyword: String,
        val rawKeyword: String,
        val source: String,
    ) : WakeWordEvent()

    data class Status(val message: String) : WakeWordEvent()

    data class Error(val message: String) : WakeWordEvent()
}
