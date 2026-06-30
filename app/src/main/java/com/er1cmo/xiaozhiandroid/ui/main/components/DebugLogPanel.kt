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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    onClearLogs: () -> Unit,
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
        ConversationState.Idle -> "连接"
    }
    val connectEnabled = uiState.conversationState !in listOf(
        ConversationState.Activating,
        ConversationState.Connecting,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开发者抽屉",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "连接、音频、MCP 与最近事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.developerModeEnabled) {
                        OutlinedButton(
                            shape = RoundedCornerShape(999.dp),
                            onClick = onToggleExpanded,
                        ) {
                            Text(if (uiState.isDebugExpanded) "收起" else "展开")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        enabled = connectEnabled,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        onClick = onConnectClick,
                    ) {
                        Text(connectButtonText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            StatusGrid(uiState = uiState)

            if (uiState.developerModeEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                DebugInfoLine(label = "设备 ID", value = uiState.deviceId)
                DebugInfoLine(label = "Client ID", value = uiState.clientId)
                DebugInfoLine(label = "激活", value = uiState.activationStatus)
                if (uiState.activationCode != "暂无") {
                    DebugInfoLine(label = "激活码", value = uiState.activationCode)
                }
                DebugInfoLine(label = "MCP", value = uiState.lastMcpStatus)
                if (uiState.lastError != "暂无") {
                    DebugInfoLine(label = "最近错误", value = uiState.lastError)
                }
                DebugInfoLine(label = "最近 JSON", value = uiState.lastServerJson)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开发者调试已关闭：详细 ID、JSON 与日志已隐藏，可在参数设置中重新开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.developerModeEnabled && uiState.isDebugExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        shape = RoundedCornerShape(999.dp),
                        onClick = onClearLogs,
                    ) {
                        Text("清空日志")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
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
private fun StatusGrid(uiState: ConversationUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(label = "状态", value = uiState.statusLabel, modifier = Modifier.weight(1f))
            StatusChip(label = "WS", value = uiState.websocketStatus, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(label = "上行", value = uiState.audioUplinkStatus, modifier = Modifier.weight(1f))
            StatusChip(label = "播放", value = uiState.audioPlaybackStatus, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
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
            modifier = Modifier.width(78.dp),
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
