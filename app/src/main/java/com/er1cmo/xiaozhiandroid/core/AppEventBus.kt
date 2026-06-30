package com.er1cmo.xiaozhiandroid.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android-side event bus backed by SharedFlow.
 *
 * It intentionally stays small: no reflection, no dynamic class loading, and no
 * global singleton. AppController owns one instance and passes it to modules.
 */
class AppEventBus(
    extraBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
) {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = extraBufferCapacity)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun publish(event: AppEvent) {
        _events.tryEmit(event)
    }

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    private companion object {
        const val DEFAULT_BUFFER_CAPACITY = 128
    }
}
