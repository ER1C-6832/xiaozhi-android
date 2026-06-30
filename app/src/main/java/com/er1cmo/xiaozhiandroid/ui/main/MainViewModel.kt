package com.er1cmo.xiaozhiandroid.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.audio.AudioEngine
import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.ActivationState
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.domain.ConversationController
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
import com.er1cmo.xiaozhiandroid.network.NetworkState
import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainViewModel(
    private val configRepository: ConfigRepository,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val otaActivationClient: OtaActivationClient,
    private val xiaozhiWebSocketClient: XiaozhiWebSocketClient,
    private val audioEngine: AudioEngine,
    private val appScope: CoroutineScope,
) : ConversationController {
    var uiState by mutableStateOf(ConversationUiState())
        private set

    private var hasStartedConfigCollection = false
    private var isOtaActivationRunning = false
    private var isWebSocketConnecting = false
    private var manualCloseRequested = false
    private var autoReconnectJob: Job? = null
    private var voiceResponseWatchdogJob: Job? = null
    private var voiceResponseGeneration = 0
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
                conversationState = ConversationState.Error,
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
        val nextMode = !uiState.isManualMode
        uiState = uiState.copy(isManualMode = nextMode)
        appendLocalLog(if (nextMode) "切换为手动对话模式" else "切换为自动对话模式（占位）")
    }

    fun toggleDebugPanel() {
        uiState = uiState.copy(isDebugExpanded = !uiState.isDebugExpanded)
    }

    fun requestMicrophonePermission() {
        uiState = uiState.copy(audioUplinkStatus = "等待麦克风权限")
        appendLocalLog("需要麦克风权限：请允许 RECORD_AUDIO 后再次按住说话")
    }

    fun onMicrophonePermissionGranted() {
        uiState = uiState.copy(audioUplinkStatus = "权限已允许，等待按住说话")
        appendLocalLog("麦克风权限已允许，请再次按住说话开始上传语音")
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
        audioEngine.stopRecording()
        audioEngine.stopPlayback(
            onLog = ::appendLocalLogFromAnyThread,
            onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
        )
        xiaozhiWebSocketClient.close("user_close")
        uiState = uiState.copy(
            conversationState = ConversationState.Idle,
            websocketStatus = "已手动关闭",
            autoReconnectStatus = "已暂停",
            sessionId = "暂无",
            audioUplinkStatus = if (audioEngine.isRecording()) "停止中" else uiState.audioUplinkStatus,
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
        if (audioEngine.isRecording()) {
            appendLocalLog("音频上行已在运行")
            return
        }

        if (!ensureReadyOrReconnect(action = "按住后说话", pendingText = null)) {
            uiState = uiState.copy(audioUplinkStatus = "等待连接恢复后再录音")
            return
        }

        cancelVoiceResponseWatchdog()

        if (audioEngine.isPlaybackActive() || uiState.conversationState == ConversationState.Speaking) {
            audioEngine.stopPlayback(
                onLog = ::appendLocalLogFromAnyThread,
                onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
            )
            val aborted = xiaozhiWebSocketClient.sendAbort()
            appendLocalLog(if (aborted) "开始录音前已打断当前 TTS 播放" else "开始录音前停止了本地 TTS 播放")
        }

        val startSent = xiaozhiWebSocketClient.sendStartManualListening()
        if (!startSent) {
            uiState = uiState.copy(
                conversationState = ConversationState.Error,
                audioUplinkStatus = "listen/start 发送失败",
                lastError = "listen/start/manual 发送失败",
            )
            appendLocalLog("listen/start/manual 发送失败，开始重新连接")
            scheduleAutoReconnect("listen/start 发送失败")
            return
        }

        uiState = uiState.copy(
            conversationState = ConversationState.Listening,
            audioUplinkStatus = "正在启动录音",
        )
        appendLocalLog("已发送 listen/start/manual，开始麦克风录音与 Opus 上行")

        audioEngine.startRecording(
            onEncodedFrame = { opusFrame -> xiaozhiWebSocketClient.sendAudioFrame(opusFrame) },
            onLog = ::appendLocalLogFromAnyThread,
            onStatusChanged = ::updateAudioStatusFromAnyThread,
        )
    }

    override fun stopListening() {
        val wasRecording = audioEngine.isRecording()
        val shouldSendStop = xiaozhiWebSocketClient.hasActiveSession() &&
            (wasRecording || uiState.conversationState == ConversationState.Listening)
        audioEngine.stopRecording()
        uiState = uiState.copy(
            conversationState = when {
                xiaozhiWebSocketClient.hasActiveSession() && wasRecording -> ConversationState.Thinking
                xiaozhiWebSocketClient.hasActiveSession() -> ConversationState.Connected
                else -> ConversationState.Idle
            },
            audioUplinkStatus = if (wasRecording) "停止中" else uiState.audioUplinkStatus,
        )
        if (shouldSendStop) {
            val sent = xiaozhiWebSocketClient.sendStopListening()
            if (sent) {
                appendLocalLog("已发送 listen/stop，等待服务端 STT/TTS 响应")
                if (wasRecording) scheduleVoiceResponseWatchdog()
            } else {
                appendLocalLog("listen/stop 发送失败，准备重连")
                scheduleAutoReconnect("listen/stop 发送失败")
            }
        } else if (!xiaozhiWebSocketClient.hasActiveSession()) {
            appendLocalLog("停止聆听：WebSocket 未连接或 session_id 缺失")
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
            conversationState = if (xiaozhiWebSocketClient.hasActiveSession()) ConversationState.Connected else ConversationState.Idle,
            audioUplinkStatus = if (wasRecording) "已停止" else uiState.audioUplinkStatus,
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
            conversationState = ConversationState.Connecting,
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
            conversationState = ConversationState.Connecting,
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
                conversationState = ConversationState.Error,
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
                conversationState = ConversationState.Connected,
                websocketStatus = "已连接",
                autoReconnectStatus = "无需重连",
            )
            return true
        }
        cancelVoiceResponseWatchdog()
        xiaozhiWebSocketClient.close("reconnect")
        uiState = uiState.copy(
            conversationState = ConversationState.Connecting,
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
            conversationState = ConversationState.Activating,
            otaStatus = ActivationState.OtaRequesting.label,
            websocketStatus = "等待 OTA 下发 WebSocket 配置",
        )
        appendLocalLog("开始执行 OTA / 激活流程")

        return try {
            val outcome = otaActivationClient.runOtaAndActivation { message ->
                appendLocalLogFromAnyThread(message)
            }
            val hasWebSocketConfig = configRepository.getConfig().websocketUrl.isNotBlank()
            uiState = uiState.copy(
                conversationState = when (outcome.state) {
                    ActivationState.Activated,
                    ActivationState.OtaSuccess -> if (hasWebSocketConfig) ConversationState.Connecting else ConversationState.Connected
                    ActivationState.Failed -> ConversationState.Error
                    else -> ConversationState.Activating
                },
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
                conversationState = ConversationState.Error,
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
                conversationState = ConversationState.Connected,
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
            conversationState = ConversationState.Connecting,
            websocketStatus = "WebSocket 连接中",
        )

        val connected = try {
            xiaozhiWebSocketClient.connect(
                callbacks = buildWebSocketCallbacks(allowAutoReconnect),
            )
        } catch (exception: Exception) {
            val error = exception.message ?: exception::class.java.simpleName
            uiState = uiState.copy(
                conversationState = ConversationState.Error,
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
                conversationState = ConversationState.Error,
                websocketStatus = "连接失败",
            )
            if (allowAutoReconnect) scheduleAutoReconnect("WebSocket 连接失败")
        }
        return connected
    }

    private fun buildWebSocketCallbacks(allowAutoReconnect: Boolean): XiaozhiWebSocketClient.Callbacks {
        return XiaozhiWebSocketClient.Callbacks(
            onLog = ::appendLocalLog,
            onConnected = { sessionId ->
                uiState = uiState.copy(
                    conversationState = ConversationState.Connected,
                    websocketStatus = "已连接",
                    autoReconnectStatus = "已连接",
                    sessionId = sessionId.ifBlank { "暂无" },
                    lastError = "暂无",
                )
                flushPendingTextAfterReconnect()
            },
            onIncomingJson = { prettyJson, type ->
                handleIncomingJson(prettyJson, type)
            },
            onBinaryFrame = { frame ->
                handleIncomingAudioFrame(frame)
            },
            onClosed = { reason ->
                handleSocketLost(
                    reason = reason,
                    asError = false,
                    allowAutoReconnect = allowAutoReconnect,
                )
            },
            onError = { message ->
                handleSocketLost(
                    reason = message,
                    asError = true,
                    allowAutoReconnect = allowAutoReconnect,
                )
            },
            onNetworkStateChanged = { state ->
                uiState = uiState.copy(websocketStatus = state.label)
            },
        )
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
            conversationState = if (asError) ConversationState.Error else ConversationState.Idle,
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
                conversationState = ConversationState.Error,
                lastError = "发送 listen/detect/text 失败",
            )
            pendingTextAfterReconnect = text
            appendLocalLog("发送 listen/detect/text 失败，开始自动重连")
            scheduleAutoReconnect("发送文本失败")
            return
        }

        uiState = uiState.copy(
            textInput = if (clearInput) "" else uiState.textInput,
            conversationState = ConversationState.Thinking,
            lastServerJson = "等待服务端 JSON 响应",
        )
        appendLocalLog("已发送 listen/detect/text：$text")
    }

    private fun handleIncomingJson(
        prettyJson: String,
        type: String,
    ) {
        val preview = prettyJson.take(MAX_JSON_PREVIEW_LENGTH)
        val state = jsonState(prettyJson)
        uiState = uiState.copy(lastServerJson = preview)

        when (type) {
            "tts" -> handleTtsJson(state)
            "stt" -> {
                markVoiceResponseArrived("stt")
                uiState = uiState.copy(conversationState = ConversationState.Thinking)
            }
            "llm" -> {
                markVoiceResponseArrived("llm")
                uiState = uiState.copy(conversationState = ConversationState.Thinking)
            }
            "listen" -> uiState = uiState.copy(conversationState = ConversationState.Listening)
            else -> {
                if (uiState.conversationState !in listOf(ConversationState.Speaking, ConversationState.Listening, ConversationState.Thinking)) {
                    uiState = uiState.copy(conversationState = ConversationState.Connected)
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
                uiState = uiState.copy(conversationState = ConversationState.Speaking)
            }
            "stop" -> {
                audioEngine.markTtsStop(
                    onLog = ::appendLocalLogFromAnyThread,
                    onStatusChanged = ::updateAudioPlaybackStatusFromAnyThread,
                )
                uiState = uiState.copy(
                    conversationState = if (xiaozhiWebSocketClient.hasActiveSession()) ConversationState.Connected else ConversationState.Idle,
                )
            }
            else -> {
                uiState = uiState.copy(conversationState = ConversationState.Speaking)
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
            conversationState = ConversationState.Speaking,
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
            uiState = uiState.copy(
                conversationState = ConversationState.Connected,
                audioUplinkStatus = "已停止，服务端未返回语音结果",
                lastError = "语音响应等待超时，可重试或检查麦克风/模拟器音频输入",
            )
            appendLocalLog("语音响应等待超时：未收到 STT/TTS，已回到已连接。可重试，或在真机检查麦克风输入")
        }
    }

    private fun markVoiceResponseArrived(reason: String) {
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

    private fun applyConfig(config: AppConfig) {
        val hasWebSocketUrl = config.websocketUrl.isNotBlank()
        uiState = uiState.copy(
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

    private fun jsonState(prettyJson: String): String {
        return runCatching { JSONObject(prettyJson).optString("state", "") }.getOrDefault("")
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
    }
}
