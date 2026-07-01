package com.er1cmo.xiaozhiandroid.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.er1cmo.xiaozhiandroid.ui.main.components.AssistantFace
import com.er1cmo.xiaozhiandroid.ui.main.components.BottomControlBar
import com.er1cmo.xiaozhiandroid.ui.main.components.DebugLogPanel
import com.er1cmo.xiaozhiandroid.ui.main.components.ToolCallPanel
import com.er1cmo.xiaozhiandroid.ui.theme.OatBackground
import com.er1cmo.xiaozhiandroid.ui.theme.WarmBorder
import com.er1cmo.xiaozhiandroid.ui.theme.WarmText
import com.er1cmo.xiaozhiandroid.ui.theme.WarmTextSecondary

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
            .background(OatBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = OatBackground,
        topBar = {
            MainTopBar(onOpenSettings = onOpenSettings)
        },
        bottomBar = {
            BottomControlBar(
                textInput = uiState.textInput,
                isManualMode = uiState.isManualMode,
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
                .background(OatBackground)
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
        color = OatBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "小智语音助手",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Light,
                    color = WarmText,
                )
                Text(
                    text = "Organic AI Companion",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Light,
                    color = WarmTextSecondary,
                )
            }
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WarmBorder),
            ) {
                Text("设置", color = WarmText)
            }
        }
    }
}
