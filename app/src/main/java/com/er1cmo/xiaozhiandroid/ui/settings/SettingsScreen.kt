package com.er1cmo.xiaozhiandroid.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState

@Composable
fun SettingsScreen(
    uiState: ConversationUiState,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "参数设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Phase 1 占位页，后续扩展为系统、音频、MCP、摄像头等设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
        }

        Spacer(modifier = Modifier.padding(top = 16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsGroup(title = "系统选项") {
                SettingRow(label = "客户端 ID", value = uiState.clientId)
                SettingRow(label = "设备 ID", value = uiState.deviceId)
                SettingRow(label = "激活状态", value = uiState.activationStatus)
                SettingRow(label = "OTA 状态", value = uiState.otaStatus)
                SettingRow(label = "WebSocket", value = uiState.websocketStatus)
                SettingRow(label = "最近 JSON", value = uiState.lastServerJson)
            }

            SettingsGroup(title = "网络配置占位") {
                SettingRow(label = "OTA URL", value = "https://api.tenclass.net/xiaozhi/ota/")
                SettingRow(label = "授权 URL", value = "https://xiaozhi.me/")
                SettingRow(label = "WebSocket URL", value = "等待 OTA 下发")
                SettingRow(label = "访问令牌", value = "默认隐藏，后续接入安全存储")
            }

            SettingsGroup(title = "后续阶段入口") {
                SettingRow(label = "设备身份", value = "Phase 2 接入 DeviceIdentityManager")
                SettingRow(label = "OTA / 激活", value = "Phase 2 接入 OtaActivationClient")
                SettingRow(label = "文本连通性", value = "Phase 3 接入 XiaozhiWebSocketClient")
                SettingRow(label = "语音能力", value = "Phase 4/5 接入 AudioRecord、Opus、AudioTrack")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            ) {
                Text("保存并返回（占位）")
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable Column.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.65f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
