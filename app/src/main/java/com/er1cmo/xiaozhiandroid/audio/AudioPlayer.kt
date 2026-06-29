package com.er1cmo.xiaozhiandroid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

class AudioPlayer {
    private val audioTrack: AudioTrack
    private var released = false

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            AudioConstants.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            throw IllegalStateException("无法获取 AudioTrack 最小缓冲区大小")
        }

        val bufferSize = max(minBufferSize, AudioConstants.OUTPUT_PCM_FRAME_BYTES * 12)
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioConstants.OUTPUT_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            throw IllegalStateException("AudioTrack 初始化失败")
        }
    }

    fun play() {
        ensureNotReleased()
        audioTrack.play()
    }

    fun writePcm(pcm: ByteArray) {
        ensureNotReleased()
        var offset = 0
        while (offset < pcm.size) {
            val written = audioTrack.write(
                pcm,
                offset,
                pcm.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) {
                throw IllegalStateException("AudioTrack 写入失败：$written")
            }
            offset += written
        }
    }

    fun stopAndFlush() {
        if (released) return
        runCatching { audioTrack.pause() }
        runCatching { audioTrack.flush() }
        runCatching { audioTrack.stop() }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { audioTrack.pause() }
        runCatching { audioTrack.flush() }
        runCatching { audioTrack.stop() }
        runCatching { audioTrack.release() }
    }

    private fun ensureNotReleased() {
        if (released) throw IllegalStateException("AudioPlayer 已释放")
    }
}
