package com.er1cmo.xiaozhiandroid.ui.main

import com.er1cmo.xiaozhiandroid.audio.AudioEngine
import com.er1cmo.xiaozhiandroid.core.AppController
import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.ota.ActivationState
import com.er1cmo.xiaozhiandroid.domain.ConversationController
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
import com.er1cmo.xiaozhiandroid.domain.VoiceInteractionMode
import com.er1cmo.xiaozhiandroid.protocol.ProtocolEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val appController: AppController,
) : ConversationController {
    private val configRepository = appController.configRepository
    private val deviceIdentityManager = appController.deviceIdentityManager
    private val otaActivationClient = appController.otaActivationClient
    private val xiaozhiWebSocketClient = appController.protocolClient
    private val audioEngine = appController.audioEngine
    private val appScope = appController.appScope
    private val stateStore = appController.stateStore

    var uiState: ConversationUiState
        get() = stateStore.uiState
        private set(value) {
            stateStore.replace(value)
        }

    private var hasStartedConfigCollection = false
    private var isOtaActivationRunning = false
    private var isWebSocketConnecting = false
    private var isStoppingListening = false
    private var manualCloseRequested = false
    private var autoReconnectJob: Job? = null
    private var voiceResponseWatchdogJob: Job? = null
    private var voiceResponseGeneration = 0
    private var lastVoiceTurnTimedOut = false
    private var autoReconnectAttempts = 0
    private var pendingTextAfterReconnect: String? = null
    private var downlinkOpusFrames = 0
    private var downlinkBytes = 0L
    private var lastLogMessage = ""
    private var repeatedLogCount = 0

    suspend fun initialize() {
        if (hasStartedConfigCollection) return
        hasStartedConfigCollection = true

        appendLocalLog("初始化配置存储：DataStore")
        try {
            val identity = deviceIdentityManager.ensureIdentity()
            appendLocalLog("设备身份已准备：${identity.deviceId}")
        } catch (exception: Exception) {
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Error, "ui_error"),
                lastError = exception.message ?: exception::class.java.simpleName,
            )
            appendLocalLog("设备身份初始化失败：${exception.message ?: exception::class.java.simpleName}")
        }

        configRepository.configFlow.collectLatest { config ->
            applyConfig(config)
        }
    }

    fun updateTextInput(value: String) {
        uiState = uiState.copy(textInput = value)
    }

    fun toggleManualMode() {
        val nextMode = if (uiState.voiceMode == VoiceInteractionMode.Manual) {
            VoiceInteractionMode.AutoStop
        } else {
            VoiceInteractionMode.Manual
        }
        uiState = uiState.copy(
            voiceMode = nextMode,
            isManualMode = nextMode == VoiceInteractionMode.Manual,
            vadStatus = if (nextMode == VoiceInteractionMode.AutoStop) {
                "AUTO_STOP：点按开始，说完停顿后自动发送"
            } else {
                "MANUAL：按住说话"
            },
            vadSummary = nextMode.description,
        )
        appendLocalLog("切换语音模式：${nextMode.wireName}，${nextMode.description}")
    }

    fun toggleDebugPanel() {
        if (!uiState.developerModeEnabled) return
        uiState = uiState.copy(isDebugExpanded = !uiState.isDebugExpanded)
    }

    fun clearDebugLogs() {
        lastLogMessage = ""
        repeatedLogCount = 0
        uiState = uiState.copy(debugLogs = listOf("${timestamp()} 调试日志已清空"))
    }

    fun requestMicrophonePermission() {
        uiState = uiState.copy(audioUplinkStatus = "等待麦克风权限")
        appendLocalLog("需要麦克风权限：请允许 RECORD_AUDIO 后再次开始说话")
    }

    fun onMicrophonePermissionGranted() {
        uiState = uiState.copy(audioUplinkStatus = "权限已允许，等待开始说话")
        appendLocalLog("麦克风权限已允许，请再次开始语音输入")
    }

    fun onMicrophonePermissionDenied() {
        uiState = uiState.copy(
            audioUplinkStatus = "麦克风权限被拒绝",
            lastError = "麦克风权限被拒绝",
        )
        appendLocalLog("麦克风权限被拒绝，无法进行语音上行")
    }

    fun handleConnectionEntry() = connect()

    fun stopManualListening() = stopListening()

    fun abortConversation() = abort()

    fun saveSystemSettings(
        otaUrl: String,
        authorizationUrl: String,
        websocketUrl: String,
        websocketToken: String,
        activationVersion: String,
        developerModeEnabled: Boolean,
    ) {
        appScope.launch {
            configRepository.updateSystemSettings(
                otaUrl = otaUrl.trim(),
                authorizationUrl = authorizationUrl.trim(),
                websocketUrl = websocketUrl.trim(),
                websocketToken = websocketToken.trim(),
                activationVersion = activationVersion.trim(),
                developerModeEnabled = developerModeEnabled,
            )
            uiState = uiState.copy(
                isDebugExpanded = if (developerModeEnabled) uiState.isDebugExpanded else false,
                lastError = "暂无",
            )
            appendLocalLog("系统设置已保存")
        }
    }

    fun resetNetworkConfig() {
        if (isOtaActivationRunning || isWebSocketConnecting) {
            appendLocalLog("请等待当前连接或激活流程结束后再重置网络配置")
            return
        }
        appScope.launch {
            resetRuntimeForSettingsAction(
                action = "重置网络配置",
                websocketStatus = "已重置，未连接",
                autoReconnectStatus = "已暂停",
            )
            configRepository.resetNetworkConfigToDefaults()
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Idle, "ui_idle"),
                otaStatus = "未请求",
                websocketStatus = "未连接",
                sessionId = "暂无",
                lastServerJson = "暂无",
                lastError = "暂无",
            )
            appendLocalLog("网络配置已恢复默认：已清空 WebSocket URL、Access Token、激活缓存和当前连接")
        }
    }

    fun resetDeviceIdentity() {
        if (isOtaActivationRunning || isWebSocketConnecting) {
            appendLocalLog("请等待当前连接或激活流程结束后再重置设备身份")
            return
        }
        appScope.launch {
            resetRuntimeForSettingsAction(
                action = "重置设备身份",
                websocketStatus = "已重置，未连接",
                autoReconnectStatus = "已暂停",
            )
            val identity = deviceIdentityManager.resetIdentity()
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Idle, "ui_idle"),
                otaStatus = "未请求",
                websocketStatus = "未连接",
                sessionId = "暂无",
                lastServerJson = "暂无",
                lastError = "需要重新 OTA / 激活",
            )
            appendLocalLog("设备身份已重置：${identity.deviceId}，请重新 OTA / 激活并在小智官网重新绑定")
        }
    }

    fun reactivate() {
        if (isOtaActivationRunning || isWebSocketConnecting) {
            appendLocalLog("重新 OTA / 激活流程正在运行，请稍候")
            return
        }
        appScope.launch {
            resetRuntimeForSettingsAction(
                action = "重新 OTA / 激活",
                websocketStatus = "重新激活中",
                autoReconnectStatus = "已暂停",
            )
            configRepository.clearActivationAndWebSocket()
            deviceIdentityManager.ensureIdentity()
            manualCloseRequested = false
            val otaOk = runOtaActivationInternal()
            if (otaOk) {
                appendLocalLog("重新 OTA / 激活完成，开始连接 WebSocket")
                connectWebSocketInternal(allowAutoReconnect = true)
            } else {
                uiState = uiState.copy(
                    conversationState = transitionTo(ConversationState.Error, "ui_error"),
                    websocketStatus = "等待手动处理",
                    lastError = "重新 OTA / 激活未完成，请检查验证码或网络",
                )
            }
        }
    }

    override fun connect() {
        if (isOtaActivationRunning || isWebSocketConnecting) {
            appendLocalLog("连接流程正在运行，请稍候")
            return
        }
        if (xiaozhiWebSocketClient.hasActiveSession()) {
            appendLocalLog("WebSocket 已连接，session_id=${xiaozhiWebSocketClient.sessionId}")
            return
        }
        manualCloseRequested = false
        cancelVoiceResponseWatchdog()
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        appScope.launch {
            connectOrActivateInternal(reason = "连接入口", allowAutoReconnect = true)
        }
    }

    override fun reconnect() {
        if (isOtaActivationRunning || isWebSocketConnecting) {
            appendLocalLog("重连流程正在运行，请稍候")
            return
        }
        manualCloseRequested = false
        cancelVoiceResponseWatchdog()
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        autoReconnectAttempts = 0
        appScope.launch {
            appendLocalLog("开始手动重连")
            reconnectInternal(reason = "手动重连", allowAutoReconnect = true)
        }
    }

    override fun close() {
        manualCloseRequested = true
        cancelVoiceResponseWatchdog()
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        pendingTextAfterReconnect = null
        appController.closeRuntime(
            reason = "user_close",
            onAudioLog = ::appendLocalLogFromAnyThread,
            onAudioStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
        )
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Idle, "ui_idle"),
            websocketStatus = "已手动关闭",
            autoReconnectStatus = "已暂停",
            sessionId = "暂无",
            audioUplinkStatus = if (audioEngine.isRecording()) "停止中" else uiState.audioUplinkStatus,
            vadStatus = "已关闭连接",
        )
        appendLocalLog("已手动关闭连接并暂停自动重连")
    }

    override fun sendText() {
        val text = uiState.textInput.trim()
        if (text.isEmpty()) {
            appendLocalLog("发送文本失败：输入内容为空")
            return
        }

        if (!ensureReadyOrReconnect(action = "发送文本", pendingText = text)) {
            return
        }

        sendTextConnected(text, clearInput = true)
    }

    override fun startManualListening() {
        if (audioEngine.isRecording() || isStoppingListening) {
            appendLocalLog("音频上行正在启动或停止，请稍候")
            return
        }

        if (!ensureReadyOrReconnect(action = "开始语音", pendingText = null)) {
            uiState = uiState.copy(audioUplinkStatus = "等待连接恢复后再录音")
            return
        }

        cancelVoiceResponseWatchdog()

        if (lastVoiceTurnTimedOut || uiState.conversationState == ConversationState.Thinking) {
            val resetSent = xiaozhiWebSocketClient.sendAbort()
            lastVoiceTurnTimedOut = false
            appendLocalLog(
                if (resetSent) {
                    "开始新一轮语音前已发送 abort，清理上一轮未完成的服务端监听状态"
                } else {
                    "开始新一轮语音前尝试清理服务端状态，但 abort 未发送成功"
                },
            )
        }

        if (audioEngine.isPlaybackActive() || uiState.conversationState == ConversationState.Speaking) {
            audioEngine.stopPlayback(
                onLog = ::appendLocalLogFromAnyThread,
                onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
            )
            val aborted = xiaozhiWebSocketClient.sendAbort()
            appendLocalLog(if (aborted) "开始录音前已打断当前 TTS 播放" else "开始录音前停止了本地 TTS 播放")
        }

        val mode = uiState.voiceMode
        val autoStopEnabled = mode == VoiceInteractionMode.AutoStop
        val startSent = xiaozhiWebSocketClient.sendStartManualListening()
        if (!startSent) {
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Error, "ui_error"),
                audioUplinkStatus = "listen/start 发送失败",
                lastError = "listen/start/manual 发送失败",
            )
            appendLocalLog("listen/start/manual 发送失败，开始重新连接")
            scheduleAutoReconnect("listen/start 发送失败")
            return
        }

        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Listening, if (autoStopEnabled) "auto_stop_listen_start" else "manual_listen_start"),
            audioUplinkStatus = "正在启动录音",
            vadStatus = if (autoStopEnabled) "VAD 等待起声" else "MANUAL：松开后发送 stop",
            vadSummary = if (autoStopEnabled) "AUTO_STOP 已启用：检测到短暂停顿后自动 stop" else VoiceInteractionMode.Manual.description,
        )
        appendLocalLog(
            if (autoStopEnabled) {
                "已发送 listen/start/manual，AUTO_STOP 本地能量 VAD 已启用，开始麦克风录音与 Opus 上行"
            } else {
                "已发送 listen/start/manual，开始麦克风录音与 Opus 上行"
            },
        )

        audioEngine.startRecording(
            onEncodedFrame = { opusFrame -> xiaozhiWebSocketClient.sendAudioFrame(opusFrame) },
            onLog = ::appendLocalLogFromAnyThread,
            onStatusChanged = ::updateAudioStatusFromAnyThread,
            vadConfig = if (autoStopEnabled) AudioEngine.VadConfig.autoStop() else AudioEngine.VadConfig.Disabled,
            onVadStatusChanged = ::updateVadStatusFromAnyThread,
            onVadAutoStop = ::handleVadAutoStopFromAnyThread,
        )
    }

    override fun stopListening() {
        if (isStoppingListening) {
            appendLocalLog("停止聆听流程正在处理，请稍候")
            return
        }

        val wasRecording = audioEngine.isRecording()
        val shouldSendStop = xiaozhiWebSocketClient.hasActiveSession() &&
            (wasRecording || uiState.conversationState == ConversationState.Listening)
        val stopSessionId = xiaozhiWebSocketClient.sessionId

        if (!wasRecording && !shouldSendStop) {
            appendLocalLog("停止聆听：当前没有录音上行")
            return
        }

        isStoppingListening = true
        uiState = uiState.copy(audioUplinkStatus = if (wasRecording) "停止录音中" else uiState.audioUplinkStatus)
        appScope.launch {
            try {
                val stats = if (wasRecording) {
                    audioEngine.stopRecordingAndAwait()
                } else {
                    audioEngine.recordingStats()
                }
                val hasUsefulAudio = stats.opusPackets >= MIN_VALID_VOICE_OPUS_PACKETS
                val maybeSilent = stats.pcmFrames > 0 && stats.peakAbs < MIN_SPEECH_PEAK_HINT

                val stopState = when {
                    xiaozhiWebSocketClient.hasActiveSession() && hasUsefulAudio -> transitionTo(ConversationState.Thinking, "voice_stop_useful_audio")
                    xiaozhiWebSocketClient.hasActiveSession() -> transitionTo(ConversationState.Connected, "voice_stop_no_useful_audio")
                    else -> transitionTo(ConversationState.Idle, "voice_stop_disconnected")
                }
                uiState = uiState.copy(
                    conversationState = stopState,
                    audioUplinkStatus = if (wasRecording) {
                        "已停止，PCM ${stats.pcmFrames} 帧，Opus ${stats.opusPackets} 帧"
                    } else {
                        uiState.audioUplinkStatus
                    },
                    vadStatus = "已停止，等待服务端响应",
                    vadSummary = "peak=${stats.peakAbs}, rms=${stats.rms}, 静音 ${stats.silentRatioPercent}%",
                )

                if (shouldSendStop) {
                    val queuedBeforeStop = waitForOutgoingAudioDrain()
                    val sent = xiaozhiWebSocketClient.sendStopListening()
                    if (sent) {
                        if (hasUsefulAudio) {
                            appendLocalLog(
                                "已发送 listen/stop，等待服务端 STT/TTS 响应" +
                                    "（Opus ${stats.opusPackets} 帧，peak=${stats.peakAbs}, rms=${stats.rms}, " +
                                    "静音 ${stats.silentRatioPercent}%, 发送队列 ${queuedBeforeStop}B, session=$stopSessionId）",
                            )
                            if (maybeSilent) {
                                appendLocalLog("本轮麦克风输入峰值较低，若仍无 STT，请重点检查模拟器/真机麦克风输入")
                            }
                            scheduleVoiceResponseWatchdog()
                        } else {
                            uiState = uiState.copy(
                                conversationState = transitionTo(if (xiaozhiWebSocketClient.hasActiveSession()) ConversationState.Connected else ConversationState.Idle, "voice_no_useful_audio"),
                                lastError = "本次按住时间过短或未采集到有效语音帧",
                                vadStatus = if (uiState.voiceMode == VoiceInteractionMode.AutoStop) "AUTO_STOP：等待下一次点按" else "MANUAL：按住说话",
                            )
                            appendLocalLog(
                                "已发送 listen/stop，但本次有效 Opus 音频帧不足" +
                                    "（PCM ${stats.pcmFrames}，Opus ${stats.opusPackets}, peak=${stats.peakAbs}, rms=${stats.rms}），不等待服务端语音响应",
                            )
                        }
                    } else {
                        appendLocalLog("listen/stop 发送失败，准备重连")
                        scheduleAutoReconnect("listen/stop 发送失败")
                    }
                } else if (!xiaozhiWebSocketClient.hasActiveSession()) {
                    appendLocalLog("停止聆听：WebSocket 未连接或 session_id 缺失")
                }
            } finally {
                isStoppingListening = false
            }
        }
    }

    override fun abort() {
        cancelVoiceResponseWatchdog()
        val wasRecording = audioEngine.isRecording()
        if (wasRecording) {
            audioEngine.stopRecording()
            appendLocalLog("打断对话：已停止本地录音上行")
        }
        audioEngine.stopPlayback(
            onLog = ::appendLocalLogFromAnyThread,
            onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
        )
        val sent = if (xiaozhiWebSocketClient.hasActiveSession()) {
            xiaozhiWebSocketClient.sendAbort()
        } else {
            false
        }
        uiState = uiState.copy(
            conversationState = transitionTo(if (xiaozhiWebSocketClient.hasActiveSession()) ConversationState.Connected else ConversationState.Idle, "abort"),
            audioUplinkStatus = if (wasRecording) "已停止" else uiState.audioUplinkStatus,
            vadStatus = if (uiState.voiceMode == VoiceInteractionMode.AutoStop) "AUTO_STOP：等待下一次点按" else "MANUAL：按住说话",
        )
        appendLocalLog(if (sent) "已发送 abort/user_interruption" else "打断对话：本地已停止，WebSocket 未连接或 session_id 缺失")
    }

    fun startOtaActivation() {
        appScope.launch {
            runOtaActivationInternal()
        }
    }

    fun appendLocalLog(message: String) {
        if (message == lastLogMessage) {
            repeatedLogCount += 1
            if (repeatedLogCount % REPEATED_LOG_REPORT_INTERVAL != 0) return
            val compact = "$message（重复 ${repeatedLogCount} 次）"
            val nextLogs = (uiState.debugLogs + "${timestamp()} $compact").takeLast(MAX_LOG_LINES)
            uiState = uiState.copy(debugLogs = nextLogs)
            return
        }
        lastLogMessage = message
        repeatedLogCount = 0
        val nextLogs = (uiState.debugLogs + "${timestamp()} $message").takeLast(MAX_LOG_LINES)
        uiState = uiState.copy(debugLogs = nextLogs)
    }

    private fun handleVadAutoStopFromAnyThread(stats: AudioEngine.RecordingStats) {
        appScope.launch(Dispatchers.Main.immediate) {
            if (uiState.voiceMode != VoiceInteractionMode.AutoStop) return@launch
            if (uiState.conversationState != ConversationState.Listening) return@launch
            if (isStoppingListening) return@launch
            uiState = uiState.copy(
                vadStatus = "VAD 检测到停顿，自动 stop",
                vadSummary = "peak=${stats.peakAbs}, rms=${stats.rms}, 静音 ${stats.silentRatioPercent}%",
            )
            appendLocalLog(
                "VAD 自动停止触发：PCM ${stats.pcmFrames} 帧，Opus ${stats.opusPackets} 帧，" +
                    "peak=${stats.peakAbs}, rms=${stats.rms}, 静音 ${stats.silentRatioPercent}%",
            )
            stopListening()
        }
    }

    private fun ensureReadyOrReconnect(
        action: String,
        pendingText: String?,
    ): Boolean {
        if (xiaozhiWebSocketClient.hasActiveSession()) return true

        if (isOtaActivationRunning || isWebSocketConnecting || autoReconnectJob?.isActive == true) {
            appendLocalLog("$action 暂缓：连接流程正在进行，请稍候")
            return false
        }

        pendingTextAfterReconnect = pendingText
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Connecting, "ui_connecting"),
            websocketStatus = "自动重连中",
            autoReconnectStatus = "$action 触发重连",
        )
        appendLocalLog("$action 前检查发现 WebSocket 未连接或 session_id 缺失，开始自动重连")
        appScope.launch {
            val ok = reconnectInternal(reason = "$action 前自动重连", allowAutoReconnect = true)
            if (ok) {
                flushPendingTextAfterReconnect()
            }
        }
        return false
    }

    private suspend fun connectOrActivateInternal(
        reason: String,
        allowAutoReconnect: Boolean,
    ): Boolean {
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Connecting, "ui_connecting"),
            websocketStatus = "准备连接",
        )
        return try {
            val config = configRepository.getConfig()
            val hasWebSocketConfig = config.websocketUrl.isNotBlank() && config.websocketToken.isNotBlank()
            if (!hasWebSocketConfig) {
                appendLocalLog("$reason：未找到 WebSocket 配置，先执行 OTA / 激活")
                val otaOk = runOtaActivationInternal()
                if (!otaOk) return false
            } else {
                appendLocalLog("$reason：检测到已保存 WebSocket 配置，直接连接 WSS")
            }

            connectWebSocketInternal(allowAutoReconnect = allowAutoReconnect)
        } catch (exception: Exception) {
            val error = exception.message ?: exception::class.java.simpleName
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Error, "ui_error"),
                websocketStatus = "连接失败",
                lastError = error,
            )
            appendLocalLog("连接入口失败：$error")
            if (allowAutoReconnect) scheduleAutoReconnect("连接入口异常")
            false
        }
    }

    private suspend fun reconnectInternal(
        reason: String,
        allowAutoReconnect: Boolean,
    ): Boolean {
        if (xiaozhiWebSocketClient.hasActiveSession()) {
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Connected, "ui_connected"),
                websocketStatus = "已连接",
                autoReconnectStatus = "无需重连",
            )
            return true
        }
        cancelVoiceResponseWatchdog()
        xiaozhiWebSocketClient.close("reconnect")
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Connecting, "ui_connecting"),
            websocketStatus = "重连中",
            sessionId = "暂无",
        )
        appendLocalLog("$reason：重新建立 WebSocket")
        return connectOrActivateInternal(reason = reason, allowAutoReconnect = allowAutoReconnect)
    }

    private suspend fun runOtaActivationInternal(): Boolean {
        if (isOtaActivationRunning) {
            appendLocalLog("OTA / 激活流程正在运行，请稍候")
            return false
        }

        isOtaActivationRunning = true
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Activating, "ui_activating"),
            otaStatus = ActivationState.OtaRequesting.label,
            websocketStatus = "等待 OTA 下发 WebSocket 配置",
        )
        appendLocalLog("开始执行 OTA / 激活流程")

        return try {
            val outcome = otaActivationClient.runOtaAndActivation { message ->
                appendLocalLogFromAnyThread(message)
            }
            val hasWebSocketConfig = configRepository.getConfig().websocketUrl.isNotBlank()
            val otaConversationState = when (outcome.state) {
                ActivationState.Activated,
                ActivationState.OtaSuccess -> transitionTo(
                    if (hasWebSocketConfig) ConversationState.Connecting else ConversationState.Connected,
                    "ota_success",
                )
                ActivationState.Failed -> transitionTo(ConversationState.Error, "ota_failed")
                else -> transitionTo(ConversationState.Activating, "ota_running")
            }
            uiState = uiState.copy(
                conversationState = otaConversationState,
                otaStatus = outcome.state.label,
                websocketStatus = if (hasWebSocketConfig) {
                    "已获取配置，待连接"
                } else {
                    uiState.websocketStatus
                },
                lastError = if (outcome.state == ActivationState.Failed) outcome.message else uiState.lastError,
            )
            appendLocalLog(outcome.message)
            outcome.state != ActivationState.Failed && hasWebSocketConfig
        } catch (exception: Exception) {
            val error = exception.message ?: exception::class.java.simpleName
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Error, "ui_error"),
                otaStatus = "失败",
                websocketStatus = "未连接",
                lastError = error,
            )
            appendLocalLog("OTA / 激活失败：$error")
            false
        } finally {
            isOtaActivationRunning = false
        }
    }

    private suspend fun connectWebSocketInternal(allowAutoReconnect: Boolean): Boolean {
        if (xiaozhiWebSocketClient.hasActiveSession()) {
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Connected, "ui_connected"),
                websocketStatus = "已连接",
                autoReconnectStatus = "未触发",
            )
            appendLocalLog("WebSocket 已连接")
            return true
        }
        if (isWebSocketConnecting) {
            appendLocalLog("WebSocket 正在连接，请稍候")
            return false
        }

        isWebSocketConnecting = true
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Connecting, "ui_connecting"),
            websocketStatus = "WebSocket 连接中",
        )

        val connected = try {
            appController.connectProtocol { event ->
                handleProtocolEvent(
                    event = event,
                    allowAutoReconnect = allowAutoReconnect,
                )
            }
        } catch (exception: Exception) {
            val error = exception.message ?: exception::class.java.simpleName
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Error, "ui_error"),
                websocketStatus = "连接失败",
                lastError = error,
            )
            appendLocalLog("WebSocket 连接异常：$error")
            false
        } finally {
            isWebSocketConnecting = false
        }

        if (connected) {
            autoReconnectAttempts = 0
            uiState = uiState.copy(autoReconnectStatus = "已连接")
        } else {
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Error, "ui_error"),
                websocketStatus = "连接失败",
            )
            if (allowAutoReconnect) scheduleAutoReconnect("WebSocket 连接失败")
        }
        return connected
    }

    private fun handleProtocolEvent(
        event: ProtocolEvent,
        allowAutoReconnect: Boolean,
    ) {
        when (event) {
            is ProtocolEvent.Log -> appendLocalLog(event.message)
            is ProtocolEvent.Connected -> handleProtocolConnected(event.sessionId)
            is ProtocolEvent.JsonMessage -> handleIncomingJson(event)
            is ProtocolEvent.BinaryAudio -> handleIncomingAudioFrame(event.data)
            is ProtocolEvent.Closed -> handleSocketLost(
                reason = event.reason,
                asError = false,
                allowAutoReconnect = allowAutoReconnect,
            )
            is ProtocolEvent.Error -> handleSocketLost(
                reason = event.message,
                asError = true,
                allowAutoReconnect = allowAutoReconnect,
            )
            is ProtocolEvent.NetworkStateChanged -> uiState = uiState.copy(websocketStatus = event.label)
        }
    }

    private fun handleProtocolConnected(sessionId: String) {
        transitionTo(ConversationState.Connected, "protocol_connected")
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Connected, "ui_connected"),
            websocketStatus = "已连接",
            autoReconnectStatus = "已连接",
            sessionId = sessionId.ifBlank { "暂无" },
            lastError = "暂无",
        )
        appScope.launch {
            configRepository.setActivationStatus(true)
            configRepository.clearActivationData()
            appendLocalLog("WebSocket hello 成功，已同步激活状态")
        }
        flushPendingTextAfterReconnect()
    }

    private fun handleSocketLost(
        reason: String,
        asError: Boolean,
        allowAutoReconnect: Boolean,
    ) {
        if (reason.contains("reconnect", ignoreCase = true)) {
            appendLocalLog("旧 WebSocket 已关闭，正在重连")
            return
        }
        cancelVoiceResponseWatchdog()
        audioEngine.stopRecording()
        audioEngine.stopPlayback(
            onLog = ::appendLocalLogFromAnyThread,
            onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
        )
        uiState = uiState.copy(
            conversationState = transitionTo(if (asError) ConversationState.Error else ConversationState.Idle, "socket_lost"),
            websocketStatus = if (asError) "错误" else "已断开",
            sessionId = "暂无",
            audioUplinkStatus = if (audioEngine.isRecording()) "停止中" else uiState.audioUplinkStatus,
            lastError = if (asError) reason else uiState.lastError,
        )
        appendLocalLog(reason)
        if (allowAutoReconnect) {
            scheduleAutoReconnect(reason)
        }
    }

    private fun scheduleAutoReconnect(reason: String) {
        if (manualCloseRequested) {
            uiState = uiState.copy(autoReconnectStatus = "已暂停")
            appendLocalLog("自动重连已暂停：用户手动关闭连接")
            return
        }
        if (xiaozhiWebSocketClient.hasActiveSession()) return
        if (autoReconnectJob?.isActive == true) {
            appendLocalLog("自动重连已在等待中")
            return
        }
        if (autoReconnectAttempts >= MAX_AUTO_RECONNECT_ATTEMPTS) {
            uiState = uiState.copy(
                autoReconnectStatus = "已暂停：超过 ${MAX_AUTO_RECONNECT_ATTEMPTS} 次",
                websocketStatus = "等待手动重连",
            )
            appendLocalLog("自动重连已暂停：连续 ${MAX_AUTO_RECONNECT_ATTEMPTS} 次失败，请点击连接入口")
            return
        }

        autoReconnectAttempts += 1
        val attempt = autoReconnectAttempts
        val delayMs = AUTO_RECONNECT_DELAY_MS * attempt
        uiState = uiState.copy(
            autoReconnectStatus = "${delayMs / 1000}s 后第 $attempt 次重连",
            websocketStatus = "等待自动重连",
        )
        appendLocalLog("连接异常，准备自动重连：$reason（第 $attempt 次）")
        autoReconnectJob = appScope.launch {
            delay(delayMs)
            if (manualCloseRequested || xiaozhiWebSocketClient.hasActiveSession()) return@launch
            appendLocalLog("自动重连开始：第 $attempt 次")
            val ok = reconnectInternal(reason = "自动重连", allowAutoReconnect = false)
            if (ok) {
                uiState = uiState.copy(autoReconnectStatus = "自动重连成功")
                flushPendingTextAfterReconnect()
            } else {
                uiState = uiState.copy(autoReconnectStatus = "第 $attempt 次重连失败")
                autoReconnectJob = null
                scheduleAutoReconnect("自动重连失败")
            }
        }
    }

    private fun flushPendingTextAfterReconnect() {
        val pending = pendingTextAfterReconnect?.trim().orEmpty()
        if (pending.isBlank()) return
        if (!xiaozhiWebSocketClient.hasActiveSession()) return
        pendingTextAfterReconnect = null
        appendLocalLog("重连成功，补发待发送文本")
        sendTextConnected(pending, clearInput = true)
    }

    private fun sendTextConnected(
        text: String,
        clearInput: Boolean,
    ) {
        cancelVoiceResponseWatchdog()
        val sent = xiaozhiWebSocketClient.sendWakeText(text)
        if (!sent) {
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Error, "ui_error"),
                lastError = "发送 listen/detect/text 失败",
            )
            pendingTextAfterReconnect = text
            appendLocalLog("发送 listen/detect/text 失败，开始自动重连")
            scheduleAutoReconnect("发送文本失败")
            return
        }

        uiState = uiState.copy(
            textInput = if (clearInput) "" else uiState.textInput,
            conversationState = transitionTo(ConversationState.Thinking, "text_sent"),
            lastServerJson = "等待服务端 JSON 响应",
        )
        appendLocalLog("已发送 listen/detect/text：$text")
    }

    private fun handleIncomingJson(event: ProtocolEvent.JsonMessage) {
        val preview = event.prettyJson.take(MAX_JSON_PREVIEW_LENGTH)
        val type = event.type
        val state = event.state
        uiState = uiState.copy(lastServerJson = preview)

        when (type) {
            "tts" -> handleTtsJson(state)
            "stt" -> {
                markVoiceResponseArrived("stt")
                uiState = uiState.copy(conversationState = transitionTo(ConversationState.Thinking, "ui_thinking"))
            }
            "llm" -> {
                markVoiceResponseArrived("llm")
                uiState = uiState.copy(conversationState = transitionTo(ConversationState.Thinking, "ui_thinking"))
            }
            "listen" -> uiState = uiState.copy(conversationState = transitionTo(ConversationState.Listening, "ui_listening"))
            else -> {
                if (uiState.conversationState !in listOf(ConversationState.Speaking, ConversationState.Listening, ConversationState.Thinking)) {
                    uiState = uiState.copy(conversationState = transitionTo(ConversationState.Connected, "ui_connected"))
                }
            }
        }
        if (type == "tts" && state.isNotBlank()) {
            appendLocalLog("收到服务端 JSON：type=$type/state=$state")
        } else {
            appendLocalLog("收到服务端 JSON：type=$type")
        }
    }

    private fun handleTtsJson(state: String) {
        markVoiceResponseArrived("tts/${state.ifBlank { "unknown" }}")
        when (state) {
            "start" -> {
                downlinkOpusFrames = 0
                downlinkBytes = 0L
                audioEngine.markTtsStart(
                    onLog = ::appendLocalLogFromAnyThread,
                    onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
                )
                uiState = uiState.copy(conversationState = transitionTo(ConversationState.Speaking, "ui_speaking"))
            }
            "stop" -> {
                audioEngine.markTtsStop(
                    onLog = ::appendLocalLogFromAnyThread,
                    onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
                )
                uiState = uiState.copy(
                    conversationState = transitionTo(if (xiaozhiWebSocketClient.hasActiveSession()) ConversationState.Connected else ConversationState.Idle, "tts_stop"),
                )
            }
            else -> {
                uiState = uiState.copy(conversationState = transitionTo(ConversationState.Speaking, "ui_speaking"))
            }
        }
    }

    private fun handleIncomingAudioFrame(frame: ByteArray) {
        markVoiceResponseArrived("audio")
        downlinkOpusFrames += 1
        downlinkBytes += frame.size
        audioEngine.queuePlaybackFrame(
            opusFrame = frame,
            onLog = ::appendLocalLogFromAnyThread,
            onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
        )
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Speaking, "incoming_audio"),
            audioPlaybackStatus = "播放中，收到 ${downlinkOpusFrames} 帧",
        )
        if (downlinkOpusFrames == 1 || downlinkOpusFrames % DOWNLINK_LOG_INTERVAL_PACKETS == 0) {
            appendLocalLog("收到 TTS Opus 音频帧：${downlinkOpusFrames} 帧，累计 ${downlinkBytes}B")
        }
    }

    private fun scheduleVoiceResponseWatchdog() {
        voiceResponseGeneration += 1
        val generation = voiceResponseGeneration
        voiceResponseWatchdogJob?.cancel()
        voiceResponseWatchdogJob = appScope.launch {
            delay(VOICE_RESPONSE_TIMEOUT_MS)
            if (generation != voiceResponseGeneration) return@launch
            if (uiState.conversationState != ConversationState.Thinking) return@launch
            if (!xiaozhiWebSocketClient.hasActiveSession()) return@launch
            lastVoiceTurnTimedOut = true
            val abortSent = xiaozhiWebSocketClient.sendAbort()
            uiState = uiState.copy(
                conversationState = transitionTo(ConversationState.Connected, "voice_timeout"),
                audioUplinkStatus = "已停止，服务端未返回语音结果",
                lastError = "语音响应等待超时，已清理服务端监听状态，可重试",
            )
            appendLocalLog(
                if (abortSent) {
                    "语音响应等待超时：未收到 STT/TTS，已发送 abort 清理服务端状态并回到已连接"
                } else {
                    "语音响应等待超时：未收到 STT/TTS，已回到已连接；abort 未发送成功，请必要时重连"
                },
            )
        }
    }

    private fun markVoiceResponseArrived(reason: String) {
        lastVoiceTurnTimedOut = false
        if (voiceResponseWatchdogJob?.isActive == true) {
            appendLocalLog("已收到服务端语音响应：$reason")
        }
        cancelVoiceResponseWatchdog()
    }

    private fun cancelVoiceResponseWatchdog() {
        voiceResponseGeneration += 1
        voiceResponseWatchdogJob?.cancel()
        voiceResponseWatchdogJob = null
    }

    private suspend fun waitForOutgoingAudioDrain(): Long {
        val startedAt = System.currentTimeMillis()
        var queued = xiaozhiWebSocketClient.queuedBytes()
        if (queued <= OUTGOING_AUDIO_DRAIN_TARGET_BYTES) return queued

        appendLocalLog("等待 WebSocket 音频发送队列排空：${queued}B")
        while (System.currentTimeMillis() - startedAt < OUTGOING_AUDIO_DRAIN_TIMEOUT_MS) {
            delay(OUTGOING_AUDIO_DRAIN_POLL_MS)
            queued = xiaozhiWebSocketClient.queuedBytes()
            if (queued <= OUTGOING_AUDIO_DRAIN_TARGET_BYTES) break
        }
        if (queued > OUTGOING_AUDIO_DRAIN_TARGET_BYTES) {
            appendLocalLog("WebSocket 发送队列仍有 ${queued}B，继续发送 listen/stop")
        }
        return queued
    }

    private fun resetRuntimeForSettingsAction(
        action: String,
        websocketStatus: String,
        autoReconnectStatus: String,
    ) {
        manualCloseRequested = true
        cancelVoiceResponseWatchdog()
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        autoReconnectAttempts = 0
        pendingTextAfterReconnect = null
        appController.closeRuntime(
            reason = "settings_reconnect",
            onAudioLog = ::appendLocalLogFromAnyThread,
            onAudioStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
        )
        uiState = uiState.copy(
            conversationState = transitionTo(ConversationState.Idle, "ui_idle"),
            websocketStatus = websocketStatus,
            autoReconnectStatus = autoReconnectStatus,
            sessionId = "暂无",
            audioUplinkStatus = if (audioEngine.isRecording()) "停止中" else uiState.audioUplinkStatus,
            vadStatus = "运行时已重置",
        )
        appendLocalLog("$action：已关闭当前 WebSocket、停止录音/播放并清理运行时 session")
    }

    private fun appendLocalLogFromAnyThread(message: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            appendLocalLog(message)
        }
    }

    private fun updateAudioStatusFromAnyThread(status: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            uiState = uiState.copy(audioUplinkStatus = status)
        }
    }

    private fun updateAudioPlaybackStatusFromAnyThread(status: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            uiState = uiState.copy(audioPlaybackStatus = status)
        }
    }

    private fun updateVadStatusFromAnyThread(status: String) {
        appScope.launch(Dispatchers.Main.immediate) {
            uiState = uiState.copy(vadStatus = status)
        }
    }

    private fun applyConfig(config: AppConfig) {
        val hasWebSocketUrl = config.websocketUrl.isNotBlank()
        uiState = uiState.copy(
            developerModeEnabled = config.developerModeEnabled,
            isDebugExpanded = if (config.developerModeEnabled) uiState.isDebugExpanded else false,
            debugModeStatus = config.debugModeStatus,
            clientId = config.clientId.ifBlank { "未生成" },
            deviceId = config.deviceId.ifBlank { "未生成" },
            serialNumber = config.serialNumber.ifBlank { "未生成" },
            hmacKeyStatus = if (config.hmacKey.isBlank()) "未生成" else "已生成（隐藏）",
            activationStatus = when {
                config.activationStatus -> "已激活"
                config.activationCode.isNotBlank() -> "等待激活"
                else -> "未激活"
            },
            activationCode = config.activationCodeDisplay,
            activationMessage = config.activationMessage.ifBlank { "暂无" },
            otaUrl = config.otaUrl,
            authorizationUrl = config.authorizationUrl,
            websocketUrl = config.websocketUrlDisplay,
            rawWebsocketUrl = config.websocketUrl,
            rawWebsocketToken = config.websocketToken,
            websocketTokenStatus = config.websocketTokenStatus,
            activationVersion = config.activationVersion,
            websocketStatus = when {
                xiaozhiWebSocketClient.hasActiveSession() -> "已连接"
                uiState.conversationState in listOf(ConversationState.Activating, ConversationState.Connecting, ConversationState.Error) -> uiState.websocketStatus
                hasWebSocketUrl -> "已获取配置，待连接"
                else -> "未连接"
            },
            lastServerJson = config.lastJson.ifBlank { uiState.lastServerJson },
        )
    }

    private fun transitionTo(
        next: ConversationState,
        reason: String,
    ): ConversationState {
        return appController.transitionConversationState(next, reason)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private companion object {
        const val MAX_LOG_LINES = 120
        const val MAX_JSON_PREVIEW_LENGTH = 1_500
        const val DOWNLINK_LOG_INTERVAL_PACKETS = 50
        const val AUTO_RECONNECT_DELAY_MS = 1_500L
        const val MAX_AUTO_RECONNECT_ATTEMPTS = 3
        const val REPEATED_LOG_REPORT_INTERVAL = 5
        const val VOICE_RESPONSE_TIMEOUT_MS = 12_000L
        const val MIN_VALID_VOICE_OPUS_PACKETS = 5
        const val MIN_SPEECH_PEAK_HINT = 900
        const val OUTGOING_AUDIO_DRAIN_TIMEOUT_MS = 300L
        const val OUTGOING_AUDIO_DRAIN_POLL_MS = 25L
        const val OUTGOING_AUDIO_DRAIN_TARGET_BYTES = 0L
    }
}
