package com.er1cmo.xiaozhiandroid.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlin.math.max

class AudioRecorder {
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createAudioRecord(): AudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConstants.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("无法获取 AudioRecord 最小缓冲区大小")
        }

        val bufferSize = max(minBufferSize, AudioConstants.PCM_FRAME_BYTES * 8)
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioConstants.INPUT_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("AudioRecord 初始化失败")
        }
        return audioRecord
    }

    fun readPcmFrame(
        audioRecord: AudioRecord,
        frameBuffer: ByteArray,
    ): Boolean {
        var offset = 0
        while (offset < frameBuffer.size) {
            val read = audioRecord.read(frameBuffer, offset, frameBuffer.size - offset)
            when {
                read > 0 -> offset += read
                read == 0 -> continue
                read == AudioRecord.ERROR_INVALID_OPERATION -> return false
                read == AudioRecord.ERROR_BAD_VALUE -> return false
                read == AudioRecord.ERROR_DEAD_OBJECT -> return false
                else -> return false
            }
        }
        return true
    }
}
