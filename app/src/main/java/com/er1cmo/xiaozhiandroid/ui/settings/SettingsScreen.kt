package com.er1cmo.xiaozhiandroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: ConversationUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "参数设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 24.sp,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Phase 2A 已接入配置存储与设备身份，后续扩展为系统、音频、MCP、摄像头等设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = "系统选项") {
                SettingRow(label = "客户端 ID", value = uiState.clientId)
                SettingRow(label = "设备 ID", value = uiState.deviceId)
                SettingRow(label = "序列号", value = uiState.serialNumber)
                SettingRow(label = "HMAC 密钥", value = uiState.hmacKeyStatus)
                SettingRow(label = "激活状态", value = uiState.activationStatus)
                SettingRow(label = "激活版本", value = uiState.activationVersion)
            }

            SettingsGroup(title = "网络配置") {
                SettingRow(label = "OTA URL", value = uiState.otaUrl)
                SettingRow(label = "授权 URL", value = uiState.authorizationUrl)
                SettingRow(label = "WebSocket URL", value = uiState.websocketUrl)
                SettingRow(label = "访问令牌", value = uiState.websocketTokenStatus)
                SettingRow(label = "OTA 状态", value = uiState.otaStatus)
                SettingRow(label = "WebSocket", value = uiState.websocketStatus)
                SettingRow(label = "最近 JSON", value = uiState.lastServerJson)
            }

            SettingsGroup(title = "后续阶段入口") {
                SettingRow(label = "OTA / 激活", value = "Phase 2B 接入 OtaActivationClient")
                SettingRow(label = "文本连通性", value = "Phase 3 接入 XiaozhiWebSocketClient")
                SettingRow(label = "语音能力", value = "Phase 4/5 接入 AudioRecord、Opus、AudioTrack")
                SettingRow(label = "MCP 工具", value = "Phase 8 接入 Android 本机 MCP")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            ) {
                Text("返回主界面")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
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
            modifier = Modifier.weight(0.34f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.66f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
