package com.er1cmo.xiaozhiandroid.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import kotlin.math.max

class AudioRecorder {
    data class AudioProcessingRequest(
        val enableAec: Boolean = true,
        val enableNoiseSuppressor: Boolean = true,
        val enableAgc: Boolean = false,
    )

    data class AudioProcessingReport(
        val source: Int,
        val audioSessionId: Int,
        val aecAvailable: Boolean,
        val aecEnabled: Boolean,
        val noiseSuppressorAvailable: Boolean,
        val noiseSuppressorEnabled: Boolean,
        val agcAvailable: Boolean,
        val agcEnabled: Boolean,
    ) {
        val summary: String
            get() = buildString {
                append("source=").append(sourceLabel(source))
                append(", session=").append(audioSessionId)
                append(", AEC=").append(effectLabel(aecAvailable, aecEnabled))
                append(", NS=").append(effectLabel(noiseSuppressorAvailable, noiseSuppressorEnabled))
                append(", AGC=").append(effectLabel(agcAvailable, agcEnabled))
            }

        private fun effectLabel(
            available: Boolean,
            enabled: Boolean,
        ): String {
            return when {
                enabled -> "on"
                available -> "available_off"
                else -> "unavailable"
            }
        }

        private fun sourceLabel(source: Int): String {
            return when (source) {
                MediaRecorder.AudioSource.MIC -> "MIC"
                MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
                MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                else -> source.toString()
            }
        }
    }

    data class RecordSession(
        val audioRecord: AudioRecord,
        val processingReport: AudioProcessingReport,
        private val aec: AcousticEchoCanceler?,
        private val noiseSuppressor: NoiseSuppressor?,
        private val agc: AutomaticGainControl?,
    ) {
        fun release() {
            runCatching { aec?.release() }
            runCatching { noiseSuppressor?.release() }
            runCatching { agc?.release() }
            runCatching { audioRecord.release() }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createAudioRecord(): AudioRecord {
        return createRecordSession(AudioProcessingRequest(enableAec = false, enableNoiseSuppressor = false)).audioRecord
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createRecordSession(
        processingRequest: AudioProcessingRequest = AudioProcessingRequest(),
    ): RecordSession {
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

        val candidates = if (processingRequest.enableAec || processingRequest.enableNoiseSuppressor) {
            listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            )
        } else {
            listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            )
        }
        val errors = mutableListOf<String>()

        for (source in candidates) {
            val audioRecord = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            }.getOrElse { exception ->
                errors += "source=$source 创建失败：${exception.message ?: exception::class.java.simpleName}"
                null
            } ?: continue

            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                return attachAudioProcessing(audioRecord, source, processingRequest)
            }

            audioRecord.release()
            errors += "source=$source 初始化失败"
        }

        throw IllegalStateException(
            "AudioRecord 初始化失败：${errors.joinToString("；").ifBlank { "无可用 audio source" }}",
        )
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

    private fun attachAudioProcessing(
        audioRecord: AudioRecord,
        source: Int,
        request: AudioProcessingRequest,
    ): RecordSession {
        val sessionId = audioRecord.audioSessionId
        val aecAvailable = AcousticEchoCanceler.isAvailable()
        val nsAvailable = NoiseSuppressor.isAvailable()
        val agcAvailable = AutomaticGainControl.isAvailable()

        val aec = if (request.enableAec && aecAvailable) {
            runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
        } else {
            null
        }
        val noiseSuppressor = if (request.enableNoiseSuppressor && nsAvailable) {
            runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
        } else {
            null
        }
        val agc = if (request.enableAgc && agcAvailable) {
            runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()
        } else {
            null
        }

        val aecEnabled = enableEffect(aec)
        val nsEnabled = enableEffect(noiseSuppressor)
        val agcEnabled = enableEffect(agc)

        return RecordSession(
            audioRecord = audioRecord,
            processingReport = AudioProcessingReport(
                source = source,
                audioSessionId = sessionId,
                aecAvailable = aecAvailable,
                aecEnabled = aecEnabled,
                noiseSuppressorAvailable = nsAvailable,
                noiseSuppressorEnabled = nsEnabled,
                agcAvailable = agcAvailable,
                agcEnabled = agcEnabled,
            ),
            aec = aec,
            noiseSuppressor = noiseSuppressor,
            agc = agc,
        )
    }

    private fun enableEffect(effect: AudioEffect?): Boolean {
        if (effect == null) return false
        return runCatching {
            effect.enabled = true
            effect.enabled
        }.getOrDefault(false)
    }

}
