# Phase 7B 交付报告：设置动作收口与调试开关联动

## 本次目标

修复 Phase 7A 中设置页直接操作 DataStore / OTA Client，导致主状态机、WebSocket 运行时、激活状态显示不同步的问题。

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/
├── domain/
│   └── ConversationUiState.kt
├── ui/
│   ├── main/
│   │   ├── MainScreen.kt
│   │   ├── MainViewModel.kt
│   │   └── components/
│   │       └── DebugLogPanel.kt
│   ├── navigation/
│   │   └── AppNavigation.kt
│   └── settings/
│       └── SettingsScreen.kt
```

## 主要改动

1. `SettingsScreen` 不再直接创建 `ConfigRepository`、`DeviceIdentityManager`、`OtaActivationClient`。
2. 设置页所有动作统一委托给 `MainViewModel`：
   - 保存系统设置
   - 重新 OTA / 重新激活
   - 重置网络配置
   - 重置设备身份
   - 清空调试日志
3. `AppNavigation` 将 `MainViewModel` 的设置动作传入 `SettingsScreen`。
4. 重新 OTA / 重置网络 / 重置设备身份前，会先关闭当前 WebSocket、停止录音、停止播放、清理 session，避免旧连接与新配置混用。
5. WebSocket hello 成功后，自动反写 `activation_status=true` 并清空激活码缓存，避免“能连接但仍显示未激活”。
6. 开发者调试开关真正影响主界面：关闭后隐藏详细 ID、JSON 与日志，只保留简洁状态。
7. 主界面调试面板和设置页都接入清空日志能力。
8. 设置页文案明确区分：
   - 重置网络配置：清空网络与激活缓存，不换设备身份。
   - 重置设备身份：生成新的 client_id/device_id，需要重新绑定。
   - 重新 OTA / 激活：由主状态机重新跑 OTA / 激活并连接。

## 验收建议

1. 保存 OTA URL / 授权 URL / WebSocket URL / token / 激活版本，重启 App 后仍保留。
2. 关闭开发者调试，返回主界面后详细日志和 ID 信息应隐藏；重新开启后恢复。
3. 点击“重置网络配置”后，当前 WebSocket 应关闭，Session ID 回到“暂无”，WebSocket 不应继续显示“已连接”。
4. 点击“重置设备身份”后，应生成新的客户端 ID / 设备 ID，并提示需要重新 OTA / 激活。
5. 点击“重新 OTA / 重新激活”后，流程应走主界面状态机；连接成功后激活状态应显示“已激活”。
6. 复制日志和清空日志都应可用，且 token 不应明文出现在日志中。

## 未包含内容

```text
- 音频参数真正可调
- MCP tools/list 真实预览
- CameraX
- 离线唤醒词
```

这些建议放到 Phase 7C 或后续阶段。

建议提交信息：fix settings actions and activation status
