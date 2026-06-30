# Phase 7A 交付报告：系统设置可编辑与重置/重新激活入口

## 本次范围

本包是 Phase 7 的第一包，优先补齐“系统选项”和“网络配置”相关能力，避免一次性大改设置页、音频、MCP、摄像头和唤醒词。

## 新增/修改内容

### 1. 设置页改为可编辑

现在参数设置页可以编辑并保存：

```text
- OTA URL
- 授权 URL
- WebSocket URL
- Access Token
- 激活版本
- 开发者调试开关
```

Access Token 默认使用密码样式隐藏，可以点击“显示/隐藏”切换。保存后进入 DataStore，重启 App 后仍会保留。

### 2. 配置仓库扩展

修改：

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/data/config/AppConfig.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/data/config/ConfigKeys.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/data/config/ConfigRepository.kt
```

新增能力：

```text
- updateSystemSettings(...)
- clearActivationAndWebSocket()
- resetNetworkConfigToDefaults()
- clearIdentityAndActivation()
- developerModeEnabled 持久化字段
```

### 3. 重置与重新激活

设置页新增三个操作按钮：

```text
- 重新 OTA / 重新激活
- 重置网络配置
- 重置设备身份
```

重置网络配置会恢复默认 OTA URL、授权 URL、激活版本，并清空 WebSocket URL、Access Token、激活状态和最近 JSON。

重置设备身份会清空 client_id、device_id、serial_number、hmac_key，并重新生成新的设备身份，同时清空激活和 WebSocket 配置。

### 4. 音频、唤醒词、摄像头、MCP 继续预留

设置页已补齐展示入口：

```text
- 音频配置说明
- 模拟器麦克风 100% 静音说明
- 唤醒词 UI 预留
- 摄像头 CameraX 预留
- MCP 工具入口预留
```

### 5. 调试工具第一步

本包先加入“复制调试日志”能力，便于真机排查问题。

“清空日志”需要主 ViewModel 暴露清空接口，放到 Phase 7B 做，避免本包继续扩大修改范围。

## 验收建议

```text
1. 打开参数设置页，可以看到可编辑的 OTA URL / 授权 URL / WebSocket URL / Token / 激活版本
2. 修改 OTA URL 或激活版本后点击“保存系统设置”
3. 关闭并重启 App，再打开设置页，确认修改仍然存在
4. Access Token 默认不明文显示，点击显示后才可查看/修改
5. 点击“重置网络配置”，确认 WebSocket URL/token 被清空，默认 OTA/授权 URL 恢复
6. 点击“重置设备身份”，确认 client_id/device_id 发生变化，并需要重新激活
7. 点击“重新 OTA / 重新激活”，确认可重新进入 OTA/激活流程
8. 点击“复制调试日志”，确认剪贴板中有日志内容
```

## 暂未处理

```text
- 清空日志按钮真正清空主界面日志：Phase 7B
- 音频采样率、frame_duration、Opus 参数的真正可调：Phase 7B/7C
- MCP tools/list 真实拉取：后续 MCP 阶段
- CameraX：后续摄像头阶段
- 离线唤醒词：后续唤醒词阶段
```

建议提交信息：add editable system settings
