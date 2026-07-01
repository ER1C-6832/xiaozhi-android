package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
import com.er1cmo.xiaozhiandroid.ui.theme.Charcoal
import com.er1cmo.xiaozhiandroid.ui.theme.OatBackground
import com.er1cmo.xiaozhiandroid.ui.theme.WarmBorder
import com.er1cmo.xiaozhiandroid.ui.theme.WarmSurface
import com.er1cmo.xiaozhiandroid.ui.theme.WarmText
import com.er1cmo.xiaozhiandroid.ui.theme.WarmTextSecondary

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
        ConversationState.Idle -> "连接入口"
    }
    val connectEnabled = uiState.conversationState !in listOf(
        ConversationState.Activating,
        ConversationState.Connecting,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = WarmSurface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开发者抽屉",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Light,
                        color = WarmText,
                    )
                    Text(
                        text = uiState.websocketStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarmTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row {
                    if (uiState.developerModeEnabled) {
                        OutlinedButton(
                            onClick = onToggleExpanded,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, WarmBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = WarmSurface,
                                contentColor = WarmText,
                            ),
                        ) {
                            Text(if (uiState.isDebugExpanded) "收起" else "展开")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        enabled = connectEnabled,
                        onClick = onConnectClick,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Charcoal,
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            disabledContainerColor = WarmBorder,
                            disabledContentColor = WarmTextSecondary,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    ) {
                        Text(connectButtonText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            DebugInfoLine(label = "状态", value = uiState.statusLabel)
            DebugInfoLine(label = "WebSocket", value = uiState.websocketStatus)
            DebugInfoLine(label = "Session", value = uiState.sessionId)
            DebugInfoLine(label = "麦克风", value = uiState.audioUplinkStatus)
            DebugInfoLine(label = "播放", value = uiState.audioPlaybackStatus)

            if (uiState.developerModeEnabled) {
                DebugInfoLine(label = "设备 ID", value = uiState.deviceId)
                DebugInfoLine(label = "Client ID", value = uiState.clientId)
                DebugInfoLine(label = "激活", value = uiState.activationStatus)
                if (uiState.activationCode != "暂无") {
                    DebugInfoLine(label = "激活码", value = uiState.activationCode)
                }
                DebugInfoLine(label = "OTA", value = uiState.otaStatus)
                DebugInfoLine(label = "重连", value = uiState.autoReconnectStatus)
                if (uiState.lastError != "暂无") {
                    DebugInfoLine(label = "错误", value = uiState.lastError)
                }
                DebugInfoLine(label = "最近 JSON", value = uiState.lastServerJson)
            } else {
                Text(
                    text = "开发者调试已关闭：详细 ID、JSON 与日志已隐藏，可在参数设置中重新开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmTextSecondary,
                )
            }

            if (uiState.developerModeEnabled && uiState.isDebugExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onClearLogs,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, WarmBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = WarmSurface,
                            contentColor = WarmText,
                        ),
                    ) {
                        Text("清空日志")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            color = OatBackground,
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
                            color = WarmTextSecondary,
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
            modifier = Modifier.width(82.dp),
            style = MaterialTheme.typography.bodySmall,
            color = WarmTextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = WarmText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
