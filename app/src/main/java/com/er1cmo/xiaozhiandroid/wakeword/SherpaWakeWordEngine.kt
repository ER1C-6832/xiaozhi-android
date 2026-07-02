package com.er1cmo.xiaozhiandroid.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SherpaWakeWordEngine(
    private val context: Context,
    private val onEvent: (WakeWordEvent) -> Unit,
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var job: Job? = null
    private var config = WakeWordConfig()
    private var lastDetectedAt = 0L

    fun updateKeyword(keyword: String) {
        config = config.copy(keyword = keyword.trim().ifBlank { WakeWordConfig.DEFAULT_KEYWORD })
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        job = engineScope.launch {
            runLoop()
        }
    }

    fun stop(reason: String) {
        if (!running.compareAndSet(true, false)) return
        job?.cancel()
        job = null
        onEvent(WakeWordEvent.Status(reason))
    }

    fun release() {
        running.set(false)
        job?.cancel()
        job = null
    }

    private suspend fun runLoop() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            running.set(false)
            emit(WakeWordEvent.Error("本地唤醒词启动失败：缺少麦克风权限"))
            return
        }

        var detector: SherpaWakeWordDetector? = null
        var audioRecord: AudioRecord? = null
        try {
            emit(WakeWordEvent.Status("本地唤醒词模型初始化中：${config.keyword}"))
            detector = SherpaWakeWordDetector(context = context, config = config)
            detector.initialize()
            emit(WakeWordEvent.Status("本地唤醒词监听中：${config.keyword}"))

            val minBufferBytes = AudioRecord.getMinBufferSize(
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(config.samplesPerFrame * 2 * 2)
            audioRecord = createAudioRecord(minBufferBytes)
            audioRecord.startRecording()

            val shortBuffer = ShortArray(config.samplesPerFrame)
            while (running.get()) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read <= 0) {
                    delay(20L)
                    continue
                }
                val samples = FloatArray(read) { index -> shortBuffer[index] / 32768.0f }
                val detected = detector.accept(samples)
                if (!detected.isNullOrBlank()) {
                    handleDetection(detected, detector)
                }
            }
        } catch (error: UnsatisfiedLinkError) {
            running.set(false)
            emit(
                WakeWordEvent.Error(
                    "本地唤醒词启动失败：缺少 sherpa-onnx-jni 原生库。" +
                        "请把 libsherpa-onnx-jni.so 放入 app/src/main/jniLibs/<ABI>/ 后重装。",
                ),
            )
        } catch (exception: Throwable) {
            running.set(false)
            emit(WakeWordEvent.Error("本地唤醒词异常：${exception.message ?: exception::class.java.simpleName}"))
        } finally {
            runCatching { audioRecord?.stop() }
            runCatching { audioRecord?.release() }
            runCatching { detector?.release() }
            emit(WakeWordEvent.Status("本地唤醒词已停止"))
        }
    }

    private suspend fun handleDetection(
        rawHit: String,
        detector: SherpaWakeWordDetector,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastDetectedAt < config.cooldownMs) {
            detector.resetStream()
            emit(WakeWordEvent.Status("本地唤醒词命中但在冷却中：$rawHit"))
            return
        }
        lastDetectedAt = now
        emit(WakeWordEvent.Status("本地唤醒词命中：$rawHit"))
        running.set(false)
        delay(config.callbackDelayAfterHitMs)
        emit(
            WakeWordEvent.Detected(
                keyword = normalizeWakeWordHit(config.keyword),
                rawKeyword = rawHit,
                source = "sherpa-onnx-kws",
            ),
        )
    }

    private fun createAudioRecord(minBufferBytes: Int): AudioRecord {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )
        var lastFailure: Throwable? = null
        for (source in sources) {
            val record = runCatching {
                AudioRecord(
                    source,
                    config.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferBytes,
                )
            }.onFailure { lastFailure = it }.getOrNull()
            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                emitSync("本地唤醒词麦克风已打开：source=$source, ${config.sampleRate}Hz")
                return record
            }
            runCatching { record?.release() }
        }
        throw IllegalStateException("无法创建唤醒词 AudioRecord：${lastFailure?.message.orEmpty()}")
    }

    private suspend fun emit(event: WakeWordEvent) {
        withContext(Dispatchers.Main.immediate) {
            onEvent(event)
        }
    }

    private fun emitSync(message: String) {
        engineScope.launch(Dispatchers.Main.immediate) {
            onEvent(WakeWordEvent.Status(message))
        }
    }
}
