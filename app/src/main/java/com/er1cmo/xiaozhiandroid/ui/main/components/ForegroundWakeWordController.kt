package com.er1cmo.xiaozhiandroid.ui.main.components

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Phase 12A foreground wake-word controller.
 *
 * This is intentionally conservative and follows py-xiaozhi's wake-word interrupt
 * model instead of Phase 11's fragile energy-VAD barge-in model:
 *
 * - It only emits a wake event when a keyword-like phrase is recognized.
 * - It has a cooldown and clears old recognition audio after a hit.
 * - It is active only in foreground, screen-on app usage.
 * - It is paused while normal conversation uplink owns the microphone.
 *
 * Backend note:
 * This first Android package uses the platform SpeechRecognizer with
 * EXTRA_PREFER_OFFLINE=true as a foreground/offline-preferred KWS bridge. If the
 * device has no on-device recognizer/language pack, it reports unavailable rather
 * than falling back to network silently. Phase 12B can swap the backend to a true
 * sherpa-onnx KWS model without changing the state-machine contract.
 */
@Composable
fun ForegroundWakeWordController(
    enabled: Boolean,
    keyword: String,
    shouldListen: Boolean,
    onDetected: (String) -> Unit,
    onStatus: (String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val latestOnDetected by rememberUpdatedState(onDetected)
    val latestOnStatus by rememberUpdatedState(onStatus)
    val normalizedKeyword = keyword.trim().ifBlank { DEFAULT_KEYWORD }
    val controller = remember(context) {
        SystemSpeechWakeWordController(
            context = context,
            onDetected = { detected -> latestOnDetected(detected) },
            onStatus = { status -> latestOnStatus(status) },
        )
    }

    LaunchedEffect(enabled, normalizedKeyword, shouldListen) {
        controller.updateKeyword(normalizedKeyword)
        if (enabled && shouldListen) {
            controller.start()
        } else {
            controller.stop(
                reason = when {
                    !enabled -> "唤醒词已关闭"
                    !shouldListen -> "当前状态暂停唤醒词监听"
                    else -> "暂停唤醒词监听"
                },
            )
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }
}

private class SystemSpeechWakeWordController(
    private val context: Context,
    private val onDetected: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) : RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var keyword: String = DEFAULT_KEYWORD
    private var active = false
    private var listening = false
    private var released = false
    private var lastDetectedAt = 0L
    private var lastStatus = ""
    private var restartGeneration = 0
    private var recognitionGeneration = 0
    private var unavailableReported = false

    fun updateKeyword(value: String) {
        keyword = value.trim().ifBlank { DEFAULT_KEYWORD }
    }

    fun start() {
        if (released) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (!unavailableReported) {
                unavailableReported = true
                report("离线唤醒词不可用：系统 SpeechRecognizer 不可用")
            }
            return
        }
        unavailableReported = false
        active = true
        ensureRecognizer()
        if (!listening) {
            scheduleRestart(delayMs = 0L, reason = "启动前台唤醒词监听")
        }
    }

    fun stop(reason: String) {
        if (!active && !listening) return
        active = false
        restartGeneration += 1
        listening = false
        runCatching { recognizer?.cancel() }
        report(reason)
    }

    fun release() {
        released = true
        active = false
        restartGeneration += 1
        listening = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer ->
            recognizer.setRecognitionListener(this)
        }
    }

    private fun scheduleRestart(delayMs: Long, reason: String) {
        if (!active || released) return
        val generation = ++restartGeneration
        if (reason.isNotBlank()) report(reason)
        handler.postDelayed({
            if (!active || released || generation != restartGeneration) return@postDelayed
            startListeningInternal()
        }, delayMs.coerceAtLeast(0L))
    }

    private fun startListeningInternal() {
        if (!active || released) return
        ensureRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.CHINA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        recognitionGeneration += 1
        runCatching {
            recognizer?.startListening(intent)
        }.onFailure { error ->
            listening = false
            report("唤醒词监听启动失败：${error.message ?: error::class.java.simpleName}")
            scheduleRestart(delayMs = RESTART_AFTER_ERROR_MS, reason = "唤醒词监听重试")
        }
    }

    private fun handleCandidates(values: List<String>) {
        if (!active || released || values.isEmpty()) return
        val hit = values.firstOrNull { candidate -> matchesWakeWord(candidate, keyword) } ?: return
        val now = System.currentTimeMillis()
        if (now - lastDetectedAt < DETECTION_COOLDOWN_MS) {
            return
        }
        lastDetectedAt = now
        active = false
        listening = false
        restartGeneration += 1
        report("唤醒词命中：$hit")
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }

        // py-xiaozhi style: pause briefly and drop stale recognition audio before callback.
        handler.postDelayed({
            if (!released) {
                onDetected(hit)
            }
        }, CALLBACK_DELAY_AFTER_HIT_MS)
    }

    private fun restartAfterUtterance() {
        listening = false
        scheduleRestart(delayMs = RESTART_AFTER_UTTERANCE_MS, reason = "唤醒词继续监听")
    }

    override fun onReadyForSpeech(params: Bundle?) {
        report("前台唤醒词监听中：$keyword")
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        listening = false
        val label = speechErrorLabel(error)
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(
                delayMs = RESTART_AFTER_NO_MATCH_MS,
                reason = "唤醒词未命中，继续监听",
            )
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(
                delayMs = RESTART_AFTER_ERROR_MS,
                reason = "唤醒词识别器忙，稍后重试",
            )
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                active = false
                report("唤醒词监听失败：缺少麦克风权限")
            }
            else -> scheduleRestart(
                delayMs = RESTART_AFTER_ERROR_MS,
                reason = "唤醒词识别错误：$label，稍后重试",
            )
        }
    }

    override fun onResults(results: Bundle?) {
        val values = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        handleCandidates(values)
        if (active) restartAfterUtterance()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val values = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        handleCandidates(values)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun report(message: String) {
        if (message == lastStatus) return
        lastStatus = message
        onStatus(message)
    }

    private fun speechErrorLabel(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "audio"
            SpeechRecognizer.ERROR_CLIENT -> "client"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient_permissions"
            SpeechRecognizer.ERROR_NETWORK -> "network"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network_timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer_busy"
            SpeechRecognizer.ERROR_SERVER -> "server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech_timeout"
            else -> "unknown_$error"
        }
    }

    private fun matchesWakeWord(candidate: String, keyword: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate.isBlank()) return false
        val normalizedKeyword = normalize(keyword.ifBlank { DEFAULT_KEYWORD })
        if (normalizedCandidate.contains(normalizedKeyword)) return true

        // Practical variants for the default Chinese wake word. These absorb common
        // on-device ASR substitutions without making the detector energy-based.
        val defaultVariants = setOf("小智", "晓智", "小志", "小知", "小至", "校智")
            .map(::normalize)
        return normalize(keyword) == normalize(DEFAULT_KEYWORD) &&
            defaultVariants.any { variant -> normalizedCandidate.contains(variant) }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\p{Punct}，。！？、,.!?：:；;\"'`~·-]"), "")
            .replace("xiaozi", "xiaozhi")
            .replace("xiaozhi", "小智")
    }

    private companion object {
        const val DEFAULT_KEYWORD = "小智"
        const val DETECTION_COOLDOWN_MS = 1_500L
        const val CALLBACK_DELAY_AFTER_HIT_MS = 300L
        const val RESTART_AFTER_NO_MATCH_MS = 180L
        const val RESTART_AFTER_UTTERANCE_MS = 260L
        const val RESTART_AFTER_ERROR_MS = 900L
    }
}

private const val DEFAULT_KEYWORD = "小智"
