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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Phase 12A foreground wake-word controller.
 *
 * This is still a foreground bridge, not the final offline KWS engine. It absorbs
 * py-xiaozhi's stable interrupt contract: only keyword-like recognition can trigger
 * start/abort; plain energy VAD never gets to interrupt TTS.
 *
 * Fix over the first 12A package:
 * - Do not hard-force offline recognition forever. Many Android devices expose a
 *   SpeechRecognizer service but have no local zh-CN language pack, so
 *   EXTRA_PREFER_OFFLINE=true can keep returning no_match for short words.
 * - Use short-query recognition, smaller silence windows, candidate logging, and
 *   offline-first then mixed/online fallback.
 * - Log partial/final candidates so real device behavior is observable.
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
    private var sessionGeneration = 0
    private var unavailableReported = false
    private var preferOffline = true
    private var consecutiveNoMatch = 0
    private var lastCandidateLog = ""

    fun updateKeyword(value: String) {
        keyword = value.trim().ifBlank { DEFAULT_KEYWORD }
    }

    fun start() {
        if (released) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (!unavailableReported) {
                unavailableReported = true
                report("前台唤醒词不可用：系统 SpeechRecognizer 不可用")
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
        sessionGeneration += 1
        listening = false
        lastCandidateLog = ""
        runCatching { recognizer?.cancel() }
        report(reason)
    }

    fun release() {
        released = true
        active = false
        restartGeneration += 1
        sessionGeneration += 1
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
        val generation = ++sessionGeneration
        val languageTag = Locale.CHINA.toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Short-query mode is more reliable for a two-syllable wake phrase than
            // long free-form dictation on many Android recognizers.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 220L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 360L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 260L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        report("前台唤醒词监听中：$keyword（${if (preferOffline) "离线优先" else "混合识别回退"}）")
        runCatching {
            recognizer?.startListening(intent)
            scheduleSessionWatchdog(generation)
        }.onFailure { error ->
            listening = false
            report("唤醒词监听启动失败：${error.message ?: error::class.java.simpleName}")
            scheduleRestart(delayMs = RESTART_AFTER_ERROR_MS, reason = "唤醒词监听重试")
        }
    }

    private fun scheduleSessionWatchdog(generation: Int) {
        handler.postDelayed({
            if (!active || released || generation != sessionGeneration || !listening) return@postDelayed
            listening = false
            runCatching { recognizer?.cancel() }
            scheduleRestart(delayMs = RESTART_AFTER_WATCHDOG_MS, reason = "唤醒词监听会话刷新")
        }, SESSION_WATCHDOG_MS)
    }

    private fun handleCandidates(
        values: List<String>,
        source: String,
    ) {
        if (!active || released || values.isEmpty()) return
        logCandidates(values, source)
        val hit = values.firstOrNull { candidate -> matchesWakeWord(candidate, keyword) } ?: return
        val now = System.currentTimeMillis()
        if (now - lastDetectedAt < DETECTION_COOLDOWN_MS) {
            report("唤醒词命中但在冷却中：$hit")
            return
        }
        lastDetectedAt = now
        active = false
        listening = false
        restartGeneration += 1
        sessionGeneration += 1
        consecutiveNoMatch = 0
        lastCandidateLog = ""
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

    private fun logCandidates(
        values: List<String>,
        source: String,
    ) {
        val compact = values
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString(" / ") { it.trim() }
        if (compact.isBlank() || compact == lastCandidateLog) return
        lastCandidateLog = compact
        report("唤醒词候选[$source]：$compact")
    }

    private fun restartAfterUtterance() {
        listening = false
        scheduleRestart(delayMs = RESTART_AFTER_UTTERANCE_MS, reason = "唤醒词继续监听")
    }

    override fun onReadyForSpeech(params: Bundle?) {
        report("前台唤醒词监听中：$keyword（${if (preferOffline) "离线优先" else "混合识别回退"}）")
    }

    override fun onBeginningOfSpeech() {
        report("唤醒词检测到声音，等待识别候选")
    }

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        report("唤醒词语音片段结束，等待结果")
    }

    override fun onError(error: Int) {
        listening = false
        val label = speechErrorLabel(error)
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                consecutiveNoMatch += 1
                maybeFallbackFromOffline()
                scheduleRestart(
                    delayMs = RESTART_AFTER_NO_MATCH_MS,
                    reason = "唤醒词未命中($label)，继续监听",
                )
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(
                delayMs = RESTART_AFTER_ERROR_MS,
                reason = "唤醒词识别器忙，稍后重试",
            )
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                active = false
                report("唤醒词监听失败：缺少麦克风权限")
            }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> {
                if (!preferOffline) {
                    preferOffline = true
                    consecutiveNoMatch = 0
                    report("混合识别网络异常，回到离线优先模式")
                }
                scheduleRestart(
                    delayMs = RESTART_AFTER_ERROR_MS,
                    reason = "唤醒词识别错误：$label，稍后重试",
                )
            }
            else -> scheduleRestart(
                delayMs = RESTART_AFTER_ERROR_MS,
                reason = "唤醒词识别错误：$label，稍后重试",
            )
        }
    }

    override fun onResults(results: Bundle?) {
        val values = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (values.isNotEmpty()) {
            consecutiveNoMatch = 0
        }
        handleCandidates(values, source = "final")
        if (active) restartAfterUtterance()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val values = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        handleCandidates(values, source = "partial")
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun maybeFallbackFromOffline() {
        if (preferOffline && consecutiveNoMatch >= OFFLINE_NO_MATCH_BEFORE_FALLBACK) {
            preferOffline = false
            consecutiveNoMatch = 0
            report("本机离线短词识别未命中，切换到系统混合识别回退")
        }
    }

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

        return normalize(keyword) == normalize(DEFAULT_KEYWORD) &&
            defaultWakeWordVariants.any { variant -> normalizedCandidate.contains(variant) }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\p{Punct}，。！？、,.!?：:；;\"'`~·_\\-]"), "")
            .replace("xiaozi", "xiaozhi")
            .replace("xiao zhi", "xiaozhi")
            .replace("xiaozhi", "小智")
            .replace("小智同学", "小智")
    }

    private val defaultWakeWordVariants = setOf(
        "小智",
        "晓智",
        "小志",
        "晓志",
        "小知",
        "晓知",
        "小至",
        "小治",
        "小制",
        "小质",
        "小之",
        "晓之",
        "校智",
        "小枝",
        "小只",
        "小芝",
        "小汁",
        "小吉",
        "小记",
        "小季",
        "小奇",
        "小琪",
        "小七",
        "消脂",
        "xiaozhi",
        "xiao zhi",
        "xiaozi",
    ).map(::normalize)

    private companion object {
        const val DEFAULT_KEYWORD = "小智"
        const val DETECTION_COOLDOWN_MS = 1_500L
        const val CALLBACK_DELAY_AFTER_HIT_MS = 300L
        const val RESTART_AFTER_NO_MATCH_MS = 220L
        const val RESTART_AFTER_UTTERANCE_MS = 320L
        const val RESTART_AFTER_ERROR_MS = 900L
        const val RESTART_AFTER_WATCHDOG_MS = 80L
        const val SESSION_WATCHDOG_MS = 6_500L
        const val OFFLINE_NO_MATCH_BEFORE_FALLBACK = 2
    }
}

private const val DEFAULT_KEYWORD = "小智"
