# Phase 6 交付报告：完整对话状态机与连接稳定化

## 本次目标

本阶段将前面已经跑通的 OTA、WebSocket、文本、语音上行、TTS 下行播放收敛成更稳定的语音助手对话流程。

核心目标：

```text
- 清理 ConversationState 状态机
- 增加 ConversationController 操作接口
- 发送前自动检查 WebSocket / session_id
- 断线后提示并自动重连
- 快速连续点击时避免重复连接或状态错乱
- TTS 播放中再次按住说话时，先打断再录音
- 打断逻辑统一：停止播放、停止录音、发送 abort
- UI 日志继续降噪
- 设置页增加状态机、自动重连、音频和调试说明
```

## 新增文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/domain/ConversationController.kt
```

定义稳定的控制接口：

```text
connect()
sendText()
startManualListening()
stopListening()
abort()
reconnect()
close()
```

当前由 MainViewModel 实现，后续可以再独立拆成真正的 controller 类。

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/domain/ConversationState.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/domain/ConversationUiState.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/network/XiaozhiWebSocketClient.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/DebugLogPanel.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/settings/SettingsScreen.kt
```

## 主要实现内容

### 1. 状态机收敛

ConversationState 调整为：

```text
Idle
Activating
Connecting
Connected
Listening
Thinking
Speaking
Error
```

断线不再单独作为主状态，而是回到 Idle，同时通过 WebSocket 状态和自动重连状态展示细节。

### 2. 连接状态稳定化

连接入口现在会先判断：

```text
- OTA / 激活是否正在运行
- WebSocket 是否正在连接
- 是否已经有 active session
- 是否已有 websocket url/token
```

如果没有 websocket 配置，仍会先走 OTA / 激活；如果已有配置，直接连 WSS。

### 3. session_id 丢失处理

XiaozhiWebSocketClient 新增：

```text
hasActiveSession()
isConnecting()
```

发送文本、开始录音、发送音频帧前都会要求：

```text
WebSocket 已连接 + session_id 非空
```

如果 session_id 缺失，会触发重连。

### 4. 自动重连

断线或发送失败后会自动重连，最多 3 次。UI 会显示：

```text
自动重连：1s 后第 1 次重连 / 自动重连成功 / 已暂停
```

用户手动 close 后会暂停自动重连。

### 5. 发送前自动检查

发送文本前如果 WebSocket 未连接或 session_id 缺失，会自动重连，并在重连成功后补发本次文本。

按住说话前如果连接不可用，会提示等待连接恢复，不会启动本地录音线程。

### 6. 手动对话流程收敛

```text
按住说话：
  检查连接和 session_id
  如 TTS 正在播放，先停止本地播放并发送 abort
  发送 listen/start/manual
  启动 AudioRecord + Opus 上行

松开：
  停止 AudioRecord
  发送 listen/stop
  状态进入 Thinking 或 Connected
```

### 7. 统一打断逻辑

打断按钮现在统一执行：

```text
停止本地录音
停止本地 TTS 播放
清空播放队列
如果 WebSocket/session 有效，发送 abort/user_interruption
状态回到 Connected 或 Idle
```

### 8. 日志降噪

连续完全重复的日志不会每条都展示，会按重复次数合并提示。日志保留行数从 160 调整为 120。

## 验收建议

```text
1. App 启动后状态为待命或已获取配置待连接
2. 点击连接入口，能连接 WebSocket 并获得 session_id
3. 快速连续点击连接入口，不会重复创建多个连接流程
4. 连接后发送文本，能进入思考中 / 说话中 / 已连接状态流转
5. TTS 播放中按住说话，会先打断当前 TTS，再开始录音上行
6. 打断按钮能停止播放、停止录音，并发送 abort
7. WebSocket 断开后，UI 显示自动重连状态
8. session_id 丢失或发送失败时，会尝试重新连接
9. 设置页能看到自动重连、状态机、音频和调试说明
```

## 暂未处理

```text
- TTS 播放在模拟器中的轻微毛刺问题，本阶段不继续死磕，待真机复测
- 后台服务 / 前台服务
- 唤醒词
- AEC / 回声消除
- MCP 工具
- CameraX
```

建议提交信息：stabilize voice conversation flow
