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
 * Android 12 real-device fix:
 * ERROR 12 is language_not_supported on many SpeechRecognizer implementations. The
 * previous package forced zh-CN + language preference + offline/short query, which
 * can make the recognizer reject the session before returning any candidate. This
 * version probes several recognition profiles and finally drops language extras so
 * the device recognizer can use its own default language path.
 */
@Composable
fun ForegroundWakeWordController(
    enabled: Boolean,
    keyword: String,
    shouldListen: Boolean,
    onDetected: (String) -> Unit,
    onStatus: (String) -> Unit,
) {
    val context = LocalContext.current
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
    private val profiles = buildRecognitionProfiles()
    private var profileIndex = 0
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

    private fun recreateRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        ensureRecognizer()
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
        val profile = currentProfile()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, profile.languageModel)
            profile.languageTag?.let { languageTag ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            if (profile.preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, profile.minimumLengthMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, profile.completeSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, profile.possiblyCompleteSilenceMs)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        report("前台唤醒词监听中：$keyword（${profile.label}）")
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
        consecutiveNoMatch = 0
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
        report("前台唤醒词监听中：$keyword（${currentProfile().label}）")
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
                if (consecutiveNoMatch >= NO_MATCH_BEFORE_PROFILE_SWITCH && advanceProfile("连续未返回候选：$label")) {
                    recreateRecognizer()
                    scheduleRestart(delayMs = RESTART_AFTER_PROFILE_SWITCH_MS, reason = "唤醒词切换识别配置后重试")
                } else {
                    scheduleRestart(
                        delayMs = RESTART_AFTER_NO_MATCH_MS,
                        reason = "唤醒词未命中($label)，继续监听",
                    )
                }
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                recreateRecognizer()
                scheduleRestart(
                    delayMs = RESTART_AFTER_ERROR_MS,
                    reason = "唤醒词识别器忙，已重建识别器",
                )
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                active = false
                report("唤醒词监听失败：缺少麦克风权限")
            }
            ERROR_LANGUAGE_NOT_SUPPORTED_CODE,
            ERROR_LANGUAGE_UNAVAILABLE_CODE -> {
                if (advanceProfile("语言不支持：$label")) {
                    recreateRecognizer()
                    scheduleRestart(delayMs = RESTART_AFTER_PROFILE_SWITCH_MS, reason = "唤醒词切换语言配置后重试")
                } else {
                    recreateRecognizer()
                    scheduleRestart(
                        delayMs = RESTART_AFTER_UNSUPPORTED_LANGUAGE_MS,
                        reason = "系统识别器仍不支持当前唤醒词语言，延迟重试",
                    )
                }
            }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> scheduleRestart(
                delayMs = RESTART_AFTER_ERROR_MS,
                reason = "唤醒词识别错误：$label，稍后重试",
            )
            else -> {
                if (error >= ERROR_LANGUAGE_NOT_SUPPORTED_CODE && advanceProfile("未知识别错误：$label")) {
                    recreateRecognizer()
                    scheduleRestart(delayMs = RESTART_AFTER_PROFILE_SWITCH_MS, reason = "唤醒词切换识别配置后重试")
                } else {
                    scheduleRestart(
                        delayMs = RESTART_AFTER_ERROR_MS,
                        reason = "唤醒词识别错误：$label，稍后重试",
                    )
                }
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val values = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        handleCandidates(values, source = "final")
        if (active) restartAfterUtterance()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val values = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        handleCandidates(values, source = "partial")
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun currentProfile(): RecognitionProfile {
        return profiles[profileIndex.coerceIn(0, profiles.lastIndex)]
    }

    private fun advanceProfile(reason: String): Boolean {
        if (profileIndex >= profiles.lastIndex) {
            report("唤醒词识别配置已到最后一档，无法继续降级：$reason")
            return false
        }
        profileIndex += 1
        consecutiveNoMatch = 0
        lastCandidateLog = ""
        val nextProfile = currentProfile()
        report("唤醒词识别配置切换为：${nextProfile.label}（$reason）")
        return true
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
            ERROR_LANGUAGE_NOT_SUPPORTED_CODE -> "language_not_supported"
            ERROR_LANGUAGE_UNAVAILABLE_CODE -> "language_unavailable"
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
            .replace("xiaozhi", "小智")
            .replace("小智同学", "小智")
    }

    private fun buildRecognitionProfiles(): List<RecognitionProfile> {
        val list = mutableListOf(
            RecognitionProfile(
                label = "中文短词",
                languageTag = "zh-CN",
                languageModel = RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
                preferOffline = false,
                minimumLengthMs = 180L,
                completeSilenceMs = 360L,
                possiblyCompleteSilenceMs = 220L,
            ),
            RecognitionProfile(
                label = "中文听写",
                languageTag = "zh-CN",
                languageModel = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                preferOffline = false,
                minimumLengthMs = 260L,
                completeSilenceMs = 480L,
                possiblyCompleteSilenceMs = 300L,
            ),
            RecognitionProfile(
                label = "中文宽松",
                languageTag = "zh",
                languageModel = RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
                preferOffline = false,
                minimumLengthMs = 180L,
                completeSilenceMs = 420L,
                possiblyCompleteSilenceMs = 260L,
            ),
        )
        val defaultLanguage = Locale.getDefault().toLanguageTag()
            .takeIf { it.isNotBlank() && it != "und" && it != "zh-CN" && it != "zh" }
        if (defaultLanguage != null) {
            list += RecognitionProfile(
                label = "系统默认语言($defaultLanguage)",
                languageTag = defaultLanguage,
                languageModel = RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
                preferOffline = false,
                minimumLengthMs = 220L,
                completeSilenceMs = 500L,
                possiblyCompleteSilenceMs = 320L,
            )
        }
        list += RecognitionProfile(
            label = "系统默认无语言约束",
            languageTag = null,
            languageModel = RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
            preferOffline = false,
            minimumLengthMs = 220L,
            completeSilenceMs = 520L,
            possiblyCompleteSilenceMs = 340L,
        )
        return list
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

    private data class RecognitionProfile(
        val label: String,
        val languageTag: String?,
        val languageModel: String,
        val preferOffline: Boolean,
        val minimumLengthMs: Long,
        val completeSilenceMs: Long,
        val possiblyCompleteSilenceMs: Long,
    )

    private companion object {
        const val DEFAULT_KEYWORD = "小智"
        const val DETECTION_COOLDOWN_MS = 1_500L
        const val CALLBACK_DELAY_AFTER_HIT_MS = 300L
        const val RESTART_AFTER_NO_MATCH_MS = 240L
        const val RESTART_AFTER_UTTERANCE_MS = 340L
        const val RESTART_AFTER_ERROR_MS = 900L
        const val RESTART_AFTER_WATCHDOG_MS = 80L
        const val RESTART_AFTER_PROFILE_SWITCH_MS = 220L
        const val RESTART_AFTER_UNSUPPORTED_LANGUAGE_MS = 3_000L
        const val SESSION_WATCHDOG_MS = 6_500L
        const val NO_MATCH_BEFORE_PROFILE_SWITCH = 4
        const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
        const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13
    }
}

private const val DEFAULT_KEYWORD = "小智"
