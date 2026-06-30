package com.er1cmo.xiaozhiandroid.core

/**
 * Android module contract.
 *
 * Modules are deliberately lifecycle-oriented instead of Python-plugin-like:
 * Android modules must respect Activity/ViewModel lifetime, permissions,
 * foreground service limits, camera lifecycle and audio focus.
 */
interface AppModule {
    val name: String

    suspend fun start() = Unit

    suspend fun stop() = Unit

    suspend fun dispose() = stop()
}
