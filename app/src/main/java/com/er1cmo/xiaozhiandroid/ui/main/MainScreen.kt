package com.er1cmo.xiaozhiandroid.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.ui.main.components.AssistantFace
import com.er1cmo.xiaozhiandroid.ui.main.components.BottomControlBar
import com.er1cmo.xiaozhiandroid.ui.main.components.DebugLogPanel
import com.er1cmo.xiaozhiandroid.ui.main.components.ForegroundWakeWordController
import com.er1cmo.xiaozhiandroid.ui.main.components.ToolCallPanel
import com.er1cmo.xiaozhiandroid.ui.theme.WarmCardWhite

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    wakeWordEnabled: Boolean,
    wakeWordKeyword: String,
    onWakeWordEnabledChange: (Boolean) -> Unit,
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    var pendingWakeWordEnable by rememberSaveable { mutableStateOf(false) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onMicrophonePermissionGranted()
            if (pendingWakeWordEnable) {
                onWakeWordEnabledChange(true)
                viewModel.appendLocalLog("麦克风权限已允许，前台唤醒词已开启")
            }
        } else {
            viewModel.onMicrophonePermissionDenied()
        }
        pendingWakeWordEnable = false
    }

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestMicrophonePermissionForWakeWord() {
        pendingWakeWordEnable = true
        viewModel.requestMicrophonePermission()
        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun handleStartListening() {
        if (hasMicrophonePermission()) {
            viewModel.startManualListening()
        } else {
            viewModel.requestMicrophonePermission()
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun handleToggleWakeWord() {
        val next = !wakeWordEnabled
        if (!next) {
            onWakeWordEnabledChange(false)
            viewModel.appendLocalLog("前台唤醒词已关闭")
            return
        }
        if (hasMicrophonePermission()) {
            onWakeWordEnabledChange(true)
            viewModel.appendLocalLog("前台唤醒词已开启：$wakeWordKeyword")
        } else {
            requestMicrophonePermissionForWakeWord()
        }
    }

    fun handleWakeWordDetected(detectedText: String) {
        viewModel.appendLocalLog("前台唤醒词触发：$detectedText，当前状态=${uiState.conversationState.label}")
        when (uiState.conversationState) {
            ConversationState.Speaking -> {
                viewModel.appendLocalLog("唤醒词打断：停止 TTS 并进入新一轮聆听")
                viewModel.abortConversation()
                handleStartListening()
            }
            ConversationState.Connected -> {
                viewModel.appendLocalLog("唤醒词启动：进入语音聆听")
                handleStartListening()
            }
            ConversationState.Idle -> {
                viewModel.appendLocalLog("唤醒词已听到，但当前未连接；请先完成连接")
            }
            ConversationState.Listening,
            ConversationState.Thinking,
            ConversationState.Activating,
            ConversationState.Connecting,
            ConversationState.Error -> {
                viewModel.appendLocalLog("唤醒词已听到，但当前状态不适合重复触发，已忽略")
            }
        }
    }

    val shouldWakeWordListen = remember(
        wakeWordEnabled,
        uiState.conversationState,
        uiState.isAudioInputActive,
    ) {
        wakeWordEnabled &&
            !uiState.isAudioInputActive &&
            uiState.conversationState in listOf(ConversationState.Connected, ConversationState.Speaking)
    }

    ForegroundWakeWordController(
        enabled = wakeWordEnabled,
        keyword = wakeWordKeyword,
        shouldListen = shouldWakeWordListen,
        onDetected = ::handleWakeWordDetected,
        onStatus = { status ->
            viewModel.appendLocalLog(status)
        },
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopBar(
                wakeWordEnabled = wakeWordEnabled,
                wakeWordKeyword = wakeWordKeyword,
                wakeWordActive = shouldWakeWordListen,
                onToggleWakeWord = ::handleToggleWakeWord,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = {
            BottomControlBar(
                textInput = uiState.textInput,
                isManualMode = uiState.isManualMode,
                voiceMode = uiState.voiceMode,
                isListening = uiState.isAudioInputActive,
                vadStatus = uiState.vadStatus,
                onTextChanged = viewModel::updateTextInput,
                onSendText = viewModel::sendText,
                onStartListening = ::handleStartListening,
                onStopListening = viewModel::stopManualListening,
                onAbort = viewModel::abortConversation,
                onToggleManualMode = viewModel::toggleManualMode,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AssistantFace(
                    state = uiState.conversationState,
                    modifier = Modifier.fillMaxWidth(),
                )
                DebugLogPanel(
                    uiState = uiState,
                    onToggleExpanded = viewModel::toggleDebugPanel,
                    onConnectClick = viewModel::handleConnectionEntry,
                    onClearLogs = viewModel::clearDebugLogs,
                )
                Spacer(modifier = Modifier.padding(bottom = 8.dp))
            }

            ToolCallPanel(
                toolCalls = uiState.mcpToolCalls,
                lastMcpStatus = uiState.lastMcpStatus,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MainTopBar(
    wakeWordEnabled: Boolean,
    wakeWordKeyword: String,
    wakeWordActive: Boolean,
    onToggleWakeWord: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WarmCardWhite,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "小智语音助手",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (wakeWordEnabled) {
                        "Phase 12A · 前台唤醒词：$wakeWordKeyword · ${if (wakeWordActive) "监听中" else "暂停"}"
                    } else {
                        "Phase 12A · 前台唤醒词已关闭"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleWakeWord) {
                    Text(if (wakeWordEnabled) "关唤醒" else "开唤醒")
                }
                OutlinedButton(onClick = onOpenSettings) {
                    Text("参数设置")
                }
            }
        }
    }
}
