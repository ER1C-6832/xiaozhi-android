package com.er1cmo.xiaozhiandroid.audio

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

class PlaybackQueue(
    capacity: Int = DEFAULT_CAPACITY,
) {
    private val channel = Channel<ByteArray>(capacity)
    private val queuedFrames = AtomicInteger(0)

    fun offer(frame: ByteArray): Boolean {
        val accepted = channel.trySend(frame).isSuccess
        if (accepted) {
            queuedFrames.incrementAndGet()
        }
        return accepted
    }

    suspend fun receiveOrNull(timeoutMs: Long = 0L): ByteArray? {
        val frame = if (timeoutMs > 0L) {
            withTimeoutOrNull(timeoutMs) {
                channel.receiveCatching().getOrNull()
            }
        } else {
            channel.receiveCatching().getOrNull()
        }
        if (frame != null) {
            queuedFrames.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        }
        return frame
    }

    fun depth(): Int = queuedFrames.get().coerceAtLeast(0)

    fun clear() {
        while (true) {
            val frame = channel.tryReceive().getOrNull() ?: break
            if (frame.isNotEmpty()) {
                queuedFrames.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            }
        }
        queuedFrames.set(0)
    }

    fun close() {
        channel.close()
    }

    private companion object {
        const val DEFAULT_CAPACITY = 240
    }
}
