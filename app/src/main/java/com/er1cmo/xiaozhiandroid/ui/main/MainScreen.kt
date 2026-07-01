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
import com.er1cmo.xiaozhiandroid.ui.main.components.ToolCallPanel
import com.er1cmo.xiaozhiandroid.ui.theme.WarmCardWhite

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onMicrophonePermissionGranted()
        } else {
            viewModel.onMicrophonePermissionDenied()
        }
    }

    fun handleStartListening() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startManualListening()
        } else {
            viewModel.requestMicrophonePermission()
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopBar(onOpenSettings = onOpenSettings)
        },
        bottomBar = {
            BottomControlBar(
                textInput = uiState.textInput,
                isManualMode = uiState.isManualMode,
                voiceMode = uiState.voiceMode,
                isListening = uiState.conversationState == ConversationState.Listening,
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
            Column {
                Text(
                    text = "小智语音助手",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Phase 11A · AUTO_STOP VAD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenSettings) {
                Text("参数设置")
            }
        }
    }
}
