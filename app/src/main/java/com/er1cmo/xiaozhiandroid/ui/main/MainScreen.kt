package com.er1cmo.xiaozhiandroid.ui.main

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.ui.main.components.AssistantFace
import com.er1cmo.xiaozhiandroid.ui.main.components.BottomControlBar
import com.er1cmo.xiaozhiandroid.ui.main.components.DebugLogPanel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val uiState = viewModel.uiState

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            MainTopBar(
                status = uiState.statusLabel,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = {
            BottomControlBar(
                textInput = uiState.textInput,
                isManualMode = uiState.isManualMode,
                onTextChanged = viewModel::updateTextInput,
                onSendText = viewModel::sendText,
                onStartListening = viewModel::startManualListening,
                onStopListening = viewModel::stopManualListening,
                onAbort = viewModel::abortConversation,
                onToggleManualMode = viewModel::toggleManualMode,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                onConnectClick = viewModel::simulateConnectEntry,
            )
            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun MainTopBar(
    status: String,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
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
                )
                Text(
                    text = "当前状态：$status",
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
