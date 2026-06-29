# Phase 2B 交付报告：OTA 请求 + v2 激活轮询

## 本次目标

本次交付在 Phase 2A 的配置存储和设备身份基础上，接入真实 OTA 请求与 v2 激活轮询。点击主界面的“连接入口”后，App 会先执行 OTA 请求，保存服务端下发的 WebSocket URL / token，并在设备需要激活时显示验证码、提示用户前往授权页输入验证码，随后轮询激活接口。

## 参考来源

实现逻辑参考 `py-xiaozhi/src/activation/service.py`：

- OTA headers：`Device-Id`、`Client-Id`、`Content-Type`、`User-Agent`、`Accept-Language`，v2 时附加 `Activation-Version`。
- OTA payload：`application.version`、`application.elf_sha256`、`board.type`、`board.name`、`board.ip`、`board.mac`。
- OTA 响应处理：保存 `websocket.url` 和 `websocket.token`；如果存在 `activation` 字段，则认为需要激活。
- 激活请求：向 `<ota_url>/activate` 发送 `Payload.algorithm`、`serial_number`、`challenge`、`hmac`。
- 激活轮询：HTTP 200 表示成功，HTTP 202 表示等待用户输入验证码，最多 60 次，每 5 秒一次。

## 创建或修改的文件

```text
app/build.gradle.kts                                  # 修改：启用 BuildConfig，加入 OkHttp 依赖
gradle/libs.versions.toml                             # 修改：新增 OkHttp 版本与依赖
app/src/main/AndroidManifest.xml                      # 修改：新增 INTERNET 权限

app/src/main/java/com/er1cmo/xiaozhiandroid/
├── data/
│   ├── config/
│   │   ├── AppConfig.kt                              # 修改：新增 activationCode/challenge/message
│   │   ├── ConfigKeys.kt                             # 修改：新增激活数据 key
│   │   └── ConfigRepository.kt                       # 修改：新增保存/清除激活数据
│   └── ota/
│       ├── ActivationState.kt                        # 新增：OTA/激活状态枚举
│       ├── OtaModels.kt                              # 新增：OTA 响应模型与 token 脱敏
│       └── OtaActivationClient.kt                    # 新增：OTA 请求与 v2 激活轮询
├── domain/
│   └── ConversationUiState.kt                        # 修改：新增激活码、激活提示
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt                          # 修改：注入 OtaActivationClient 和 CoroutineScope
│   ├── main/
│   │   └── MainScreen.kt                             # 修改：连接入口改为真实 OTA/激活流程
│   ├── main/components/
│   │   └── DebugLogPanel.kt                          # 修改：显示激活码
│   └── settings/
│       └── SettingsScreen.kt                         # 修改：显示激活码、激活提示、Phase 2B 说明

docs/phase2b_delivery_report.md                       # 新增：本交付说明
```

## 已实现能力

- 点击“连接入口”后执行真实 OTA 请求。
- OTA 请求使用 Phase 2A 生成的 `client_id`、`device_id`、`serial_number`、`hmac_key`。
- OTA payload 使用 Android 版字段映射：
  - `application.version = BuildConfig.VERSION_NAME`
  - `application.elf_sha256 = hmac_key`
  - `board.type = android`
  - `board.name = xiaozhi-android`
  - `board.ip = 当前 IPv4 或 127.0.0.1`
  - `board.mac = device_id`
- 保存服务端返回的 `websocket.url` 和 `websocket.token`。
- UI 只显示 token 状态，不明文显示 token。
- 最近 JSON 保存脱敏后的 OTA 响应。
- 如果 OTA 返回 `activation.code/challenge`，调试面板和设置页显示验证码。
- 根据 challenge 和本地 hmac_key 生成 HMAC-SHA256 签名。
- 向 `<ota_url>/activate` 执行激活轮询。
- HTTP 200 后持久化 `activation_status = true`。

## 暂未实现

- 尚未建立 WebSocket 连接。
- 尚未发送 hello。
- 尚未发送 `listen/detect/text`。
- 尚未接入语音、Opus、MCP、摄像头、唤醒词。

## 建议验收

1. 覆盖文件后执行 Gradle Sync。
2. 运行 App，确认设备 ID、Client ID 仍保持不变。
3. 点击主界面“连接入口”。
4. 确认调试日志出现 OTA 请求开始。
5. 如果 OTA 成功返回 WebSocket 配置，确认设置页 WebSocket URL 不再是“等待 OTA 下发”。
6. 确认访问令牌只显示“已保存（隐藏）”，不显示明文 token。
7. 如果服务端要求激活，确认界面显示激活码。
8. 打开授权 URL，输入验证码。
9. 等待 App 轮询到 HTTP 200 后，确认激活状态变为“已激活”。
10. 重启 App，确认激活状态和 WebSocket 配置仍然保留。
