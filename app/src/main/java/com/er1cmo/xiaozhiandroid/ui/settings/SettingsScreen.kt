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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.er1cmo.xiaozhiandroid.data.config.AppConfig
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: ConversationUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val configRepository = remember { ConfigRepository(context) }
    val deviceIdentityManager = remember { DeviceIdentityManager(configRepository) }
    val otaActivationClient = remember { OtaActivationClient(configRepository) }
    val config by configRepository.configFlow.collectAsState(initial = AppConfig())

    var otaUrlDraft by rememberSaveable { mutableStateOf(AppConfig.DEFAULT_OTA_URL) }
    var authorizationUrlDraft by rememberSaveable { mutableStateOf(AppConfig.DEFAULT_AUTHORIZATION_URL) }
    var websocketUrlDraft by rememberSaveable { mutableStateOf("") }
    var websocketTokenDraft by rememberSaveable { mutableStateOf("") }
    var activationVersionDraft by rememberSaveable { mutableStateOf(AppConfig.DEFAULT_ACTIVATION_VERSION) }
    var developerModeDraft by rememberSaveable { mutableStateOf(true) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var localStatus by rememberSaveable { mutableStateOf("等待操作") }
    var pendingDialog by rememberSaveable { mutableStateOf<ConfirmAction?>(null) }

    LaunchedEffect(config) {
        otaUrlDraft = config.otaUrl
        authorizationUrlDraft = config.authorizationUrl
        websocketUrlDraft = config.websocketUrl
        websocketTokenDraft = config.websocketToken
        activationVersionDraft = config.activationVersion
        developerModeDraft = config.developerModeEnabled
    }

    pendingDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDialog = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDialog = null
                        when (action) {
                            ConfirmAction.ResetNetwork -> {
                                coroutineScope.launch {
                                    configRepository.resetNetworkConfigToDefaults()
                                    localStatus = "网络配置已恢复默认，WebSocket/token/激活信息已清空"
                                }
                            }

                            ConfirmAction.ResetIdentity -> {
                                coroutineScope.launch {
                                    val identity = deviceIdentityManager.resetIdentity()
                                    localStatus = "设备身份已重置：${identity.deviceId}，请重新 OTA / 激活"
                                }
                            }

                            ConfirmAction.Reactivate -> {
                                coroutineScope.launch {
                                    localStatus = "正在重新 OTA / 激活，请稍候"
                                    configRepository.clearActivationAndWebSocket()
                                    deviceIdentityManager.ensureIdentity()
                                    try {
                                        val outcome = otaActivationClient.runOtaAndActivation { message ->
                                            coroutineScope.launch { localStatus = message }
                                        }
                                        localStatus = outcome.message
                                    } catch (exception: Exception) {
                                        localStatus = "重新 OTA / 激活失败：${exception.message ?: exception::class.java.simpleName}"
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDialog = null }) {
                    Text("取消")
                }
            },
        )
    }

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
                text = "Phase 7A：系统设置可编辑、敏感 token 隐藏显示、重置配置、重置设备身份、重新 OTA / 激活。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = "系统身份") {
                SettingRow(label = "客户端 ID", value = uiState.clientId)
                SettingRow(label = "设备 ID", value = uiState.deviceId)
                SettingRow(label = "序列号", value = uiState.serialNumber)
                SettingRow(label = "HMAC 密钥", value = uiState.hmacKeyStatus)
                SettingRow(label = "激活状态", value = uiState.activationStatus)
                if (uiState.activationCode != "暂无") {
                    SettingRow(label = "激活码", value = uiState.activationCode)
                }
            }

            SettingsGroup(title = "网络配置") {
                EditableSettingField(
                    label = "OTA URL",
                    value = otaUrlDraft,
                    onValueChange = { otaUrlDraft = it },
                )
                EditableSettingField(
                    label = "授权 URL",
                    value = authorizationUrlDraft,
                    onValueChange = { authorizationUrlDraft = it },
                )
                EditableSettingField(
                    label = "WebSocket URL",
                    value = websocketUrlDraft,
                    onValueChange = { websocketUrlDraft = it },
                    placeholder = "等待 OTA 下发，可留空",
                )
                EditableSettingField(
                    label = "Access Token",
                    value = websocketTokenDraft,
                    onValueChange = { websocketTokenDraft = it },
                    placeholder = if (config.websocketToken.isBlank()) "未下发，可留空" else "已保存，可修改或清空",
                    isSensitive = true,
                    sensitiveVisible = showToken,
                    onToggleSensitive = { showToken = !showToken },
                )
                EditableSettingField(
                    label = "激活版本",
                    value = activationVersionDraft,
                    onValueChange = { activationVersionDraft = it },
                    placeholder = "v2",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开发者调试")
                        Text(
                            text = "关闭后主界面可隐藏详细日志，后续包会继续完善过滤策略",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = developerModeDraft,
                        onCheckedChange = { developerModeDraft = it },
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        coroutineScope.launch {
                            configRepository.updateSystemSettings(
                                otaUrl = otaUrlDraft.trim(),
                                authorizationUrl = authorizationUrlDraft.trim(),
                                websocketUrl = websocketUrlDraft.trim(),
                                websocketToken = websocketTokenDraft.trim(),
                                activationVersion = activationVersionDraft.trim(),
                                developerModeEnabled = developerModeDraft,
                            )
                            localStatus = "系统设置已保存"
                        }
                    },
                ) {
                    Text("保存系统设置")
                }
            }

            SettingsGroup(title = "重新激活与重置") {
                SettingRow(label = "OTA 状态", value = uiState.otaStatus)
                SettingRow(label = "WebSocket", value = uiState.websocketStatus)
                SettingRow(label = "访问令牌", value = config.websocketTokenStatus)
                SettingRow(label = "本页状态", value = localStatus)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingDialog = ConfirmAction.Reactivate },
                ) {
                    Text("重新 OTA / 重新激活")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingDialog = ConfirmAction.ResetNetwork },
                ) {
                    Text("重置网络配置")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingDialog = ConfirmAction.ResetIdentity },
                ) {
                    Text("重置设备身份")
                }
            }

            SettingsGroup(title = "音频配置") {
                SettingRow(label = "上行状态", value = uiState.audioUplinkStatus)
                SettingRow(label = "播放状态", value = uiState.audioPlaybackStatus)
                SettingRow(label = "输入采样率", value = "16000Hz，当前固定")
                SettingRow(label = "输出采样率", value = "48000Hz，当前固定")
                SettingRow(label = "frame_duration", value = "20ms")
                SettingRow(label = "Opus 参数", value = "MediaCodec Opus，后续可扩展 libopus")
                SettingRow(label = "播放音量", value = "跟随系统媒体音量，后续包可加 App 内音量")
                SettingRow(label = "模拟器麦克风", value = "Pixel 模拟器可能出现 100% 静音，语音验收以真机为准")
            }

            SettingsGroup(title = "唤醒词 / 摄像头 / MCP 预留") {
                SettingRow(label = "唤醒词", value = "UI 预留，第一版不实现离线唤醒")
                SettingRow(label = "摄像头", value = "UI 预留，后续接 CameraX")
                SettingRow(label = "MCP 工具", value = "本机工具开关、tools/list 预览、调试日志后续接入")
            }

            SettingsGroup(title = "调试工具") {
                SettingRow(label = "日志状态", value = config.debugModeStatus)
                SettingRow(label = "最近错误", value = uiState.lastError)
                SettingRow(label = "最近 JSON", value = uiState.lastServerJson)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(uiState.debugLogs.joinToString(separator = "\n")),
                        )
                        localStatus = "调试日志已复制到剪贴板"
                    },
                ) {
                    Text("复制调试日志")
                }
                Text(
                    text = "清空日志会在 Phase 7B 接入主 ViewModel；当前先提供复制能力，便于定位真机问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private enum class ConfirmAction(
    val title: String,
    val message: String,
) {
    Reactivate(
        title = "重新 OTA / 重新激活？",
        message = "将清空当前 WebSocket URL、Access Token 和激活缓存，然后重新请求 OTA / 激活。",
    ),
    ResetNetwork(
        title = "重置网络配置？",
        message = "将 OTA URL、授权 URL、激活版本恢复默认，并清空 WebSocket URL、Access Token、激活状态和最近 JSON。",
    ),
    ResetIdentity(
        title = "重置设备身份？",
        message = "将生成新的客户端 ID、设备 ID、序列号和 HMAC 密钥，并清空激活与 WebSocket 配置。该操作需要重新激活。",
    ),
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

@Composable
private fun EditableSettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isSensitive: Boolean = false,
    sensitiveVisible: Boolean = true,
    onToggleSensitive: (() -> Unit)? = null,
) {
    val placeholderContent: (@Composable () -> Unit)? = if (placeholder.isBlank()) {
        null
    } else {
        { Text(placeholder) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = placeholderContent,
            visualTransformation = if (isSensitive && !sensitiveVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (isSensitive && onToggleSensitive != null) {
                {
                    TextButton(onClick = onToggleSensitive) {
                        Text(if (sensitiveVisible) "隐藏" else "显示")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
            ),
        )
    }
}
