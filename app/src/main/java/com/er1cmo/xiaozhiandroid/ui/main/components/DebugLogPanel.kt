package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState

@Composable
fun DebugLogPanel(
    uiState: ConversationUiState,
    onToggleExpanded: () -> Unit,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectButtonText = when (uiState.conversationState) {
        ConversationState.Activating,
        ConversationState.Connecting -> "连接中"
        ConversationState.Connected,
        ConversationState.Listening,
        ConversationState.Thinking,
        ConversationState.Speaking -> "已连接"
        ConversationState.Error -> "重连"
        ConversationState.Idle -> "连接入口"
    }
    val connectEnabled = uiState.conversationState !in listOf(
        ConversationState.Activating,
        ConversationState.Connecting,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "连通性调试",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row {
                    OutlinedButton(onClick = onToggleExpanded) {
                        Text(if (uiState.isDebugExpanded) "收起" else "展开")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = connectEnabled,
                        onClick = onConnectClick,
                    ) {
                        Text(connectButtonText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            DebugInfoLine(label = "状态机", value = uiState.statusLabel)
            DebugInfoLine(label = "设备 ID", value = uiState.deviceId)
            DebugInfoLine(label = "Client ID", value = uiState.clientId)
            DebugInfoLine(label = "激活状态", value = uiState.activationStatus)
            if (uiState.activationCode != "暂无") {
                DebugInfoLine(label = "激活码", value = uiState.activationCode)
            }
            DebugInfoLine(label = "OTA 状态", value = uiState.otaStatus)
            DebugInfoLine(label = "WebSocket", value = uiState.websocketStatus)
            DebugInfoLine(label = "自动重连", value = uiState.autoReconnectStatus)
            DebugInfoLine(label = "Session ID", value = uiState.sessionId)
            DebugInfoLine(label = "音频上行", value = uiState.audioUplinkStatus)
            DebugInfoLine(label = "TTS 播放", value = uiState.audioPlaybackStatus)
            if (uiState.lastError != "暂无") {
                DebugInfoLine(label = "最近错误", value = uiState.lastError)
            }
            DebugInfoLine(label = "最近 JSON", value = uiState.lastServerJson)

            if (uiState.isDebugExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    uiState.debugLogs.forEach { logLine ->
                        Text(
                            text = logLine,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugInfoLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(86.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
