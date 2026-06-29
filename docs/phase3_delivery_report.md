# Phase 3 交付报告：WebSocket 文本连通性

## 本次目标

在 Phase 2B 已完成 OTA / 激活、拿到 WebSocket URL 与 token 的基础上，接入小智 WebSocket 文本协议链路：

```text
点击连接入口
→ 如果已有 WebSocket 配置，直接连接 WSS
→ 如果没有配置，先执行 OTA / 激活
→ 携带 Authorization / Protocol-Version / Device-Id / Client-Id
→ 发送 hello
→ 收到服务端 hello
→ 保存 session_id
→ 输入文字发送 listen/detect/text
→ 接收服务端 JSON 并显示到调试面板
```

## 参考逻辑

本阶段参考 `py-xiaozhi` 的以下协议逻辑：

- `WebsocketProtocol` 连接时使用 `Authorization: Bearer <token>`、`Protocol-Version: 1`、`Device-Id`、`Client-Id`。
- 连接打开后发送 `type=hello`、`transport=websocket`、`features.mcp=true`、`audio_params`。
- 收到服务端 `hello` 后认为 WebSocket 通道已打开。
- 文本输入通过 `listen/detect/text` 触发。
- 打断通过 `abort/user_interruption`。
- 按住说话先发送 `listen/start/manual`，松开发送 `listen/stop`；音频数据仍留到 Phase 4/5 接入。

## 创建或修改的文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/
├── network/
│   ├── NetworkState.kt                 # 新增：WebSocket 连接状态枚举
│   ├── XiaozhiMessage.kt               # 新增：hello / listen / abort JSON 构造
│   └── XiaozhiWebSocketClient.kt        # 新增：OkHttp WebSocket 客户端
├── domain/
│   └── ConversationUiState.kt           # 修改：增加 sessionId 字段
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt             # 修改：创建并注入 XiaozhiWebSocketClient
│   ├── main/
│   │   ├── MainScreen.kt                # 修改：连接入口改为 handleConnectionEntry
│   │   └── MainViewModel.kt             # 修改：接入 WebSocket 连接、发送文本、打断、listen start/stop
│   ├── main/components/
│   │   └── DebugLogPanel.kt             # 修改：显示 Session ID 与 WebSocket 事件
│   └── settings/
│       └── SettingsScreen.kt            # 修改：显示 Phase 3 状态与 Session ID

docs/phase3_delivery_report.md           # 新增：本交付说明
```

## 已实现能力

- 点击“连接入口”时，如果已经有 WebSocket URL/token，会优先直接连接 WebSocket。
- 如果没有 WebSocket 配置，则沿用 Phase 2B 的 OTA / 激活流程，成功后再连接 WebSocket。
- WebSocket 请求头包含：
  - `Authorization: Bearer <token>`
  - `Protocol-Version: 1`
  - `Device-Id`
  - `Client-Id`
- 连接打开后自动发送客户端 `hello`。
- 收到服务端 `hello` 后进入“已连接”状态，并保存 / 显示 `session_id`。
- 输入文字并点击“发送”后，通过 `listen/detect/text` 发送到小智 WebSocket。
- 接收服务端 JSON，保存到最近 JSON，并追加调试日志。
- 收到二进制音频帧时仅记录日志，暂不解码播放。
- “打断对话”在 WebSocket 已连接时会发送 `abort/user_interruption`。
- “按住后说话”在 WebSocket 已连接时会发送 `listen/start/manual`，松开时发送 `listen/stop`。

## 暂未实现

- 未实现 AudioRecord 麦克风采集。
- 未实现 Opus 编码 / 解码。
- 未实现 AudioTrack 播放服务端 TTS 音频。
- 未实现 MCP tools/list、tools/call。
- 未实现 WebSocket 自动重连策略。
- 未实现后台服务、唤醒词、AEC、摄像头。

## 建议验收

1. 覆盖代码后执行 Gradle Sync。
2. 运行 App，确认设备仍显示“已激活”，WebSocket URL 仍存在。
3. 点击“连接入口”。
4. 确认日志出现 WebSocket 连接开始、发送 hello、收到服务端 hello。
5. 确认状态变为“已连接”。
6. 确认调试面板显示 Session ID；如果服务端未返回 session_id，则显示“暂无”，但连接仍可继续观察。
7. 输入一段文本并点击“发送”。
8. 确认日志出现“已发送 listen/detect/text”。
9. 确认最近 JSON 或日志中出现服务端返回的 JSON。
10. 点击“打断对话”，确认日志出现已发送 abort。
11. 长按“按住后说话”并松开，确认日志出现 listen/start/manual 与 listen/stop。

## 注意事项

- 本阶段只验证文本协议和 JSON 收发，不播放服务端返回的音频二进制帧。
- 如果点击发送后服务端立即返回 TTS 音频帧但无声音，这是预期行为，Phase 4/5 才处理音频链路。
- 如果 WebSocket 返回 401/403，优先检查 Phase 2B 保存的 token 是否来自最新 OTA。
- 如果连接超时，可以再次点击“连接入口”重试，也可以先重新执行 OTA / 激活获取新的配置。

建议提交信息：add websocket text connectivity
