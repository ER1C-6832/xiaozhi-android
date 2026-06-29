package com.er1cmo.xiaozhiandroid.audio

import kotlinx.coroutines.channels.Channel

class PlaybackQueue(
    capacity: Int = DEFAULT_CAPACITY,
) {
    private val channel = Channel<ByteArray>(capacity)

    fun offer(frame: ByteArray): Boolean {
        return channel.trySend(frame).isSuccess
    }

    suspend fun receiveOrNull(): ByteArray? {
        return channel.receiveCatching().getOrNull()
    }

    fun clear() {
        while (channel.tryReceive().isSuccess) {
            // Drop queued frames.
        }
    }

    fun close() {
        channel.close()
    }

    private companion object {
        const val DEFAULT_CAPACITY = 120
    }
}
