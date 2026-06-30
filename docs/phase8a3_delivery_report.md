# Phase 8A-3 交付报告：Protocol Router 与 WebSocket 回调迁移

## 目标

本阶段继续 App Core 架构收敛，重点把 WebSocket 原始回调从 `MainViewModel` 里迁出，改为由 `protocol/` 层统一适配为 `ProtocolEvent`，再交给 UI presenter 处理可见状态。

本阶段仍然不新增 MCP、CameraX、唤醒词等新功能，目标是保持现有功能不变，同时为 Phase 8B 的 MCP 路由打基础。

## 修改内容

### 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/core/AppController.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/protocol/ProtocolEvent.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/protocol/XiaozhiMessageRouter.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
```

## 主要变化

### 1. ProtocolEvent 扩展

`ProtocolEvent` 新增：

```text
Log
Closed
NetworkStateChanged
```

现在协议层可以表达：

```text
连接成功
连接关闭
连接错误
网络状态变化
收到 JSON
收到二进制音频
协议日志
```

### 2. XiaozhiMessageRouter 承接 WebSocket 回调适配

`XiaozhiMessageRouter` 新增 `buildCallbacks()`。

原来：

```text
MainViewModel 直接构造 XiaozhiWebSocketClient.Callbacks
```

现在：

```text
XiaozhiMessageRouter.buildCallbacks()
→ WebSocket 原始回调
→ ProtocolEvent
→ AppController / MainViewModel
```

这样 Phase 8B 接入 MCP 时，`type=mcp` 可以在 router / protocol 层优先分发，不需要继续扩大 `MainViewModel`。

### 3. AppController 承接协议连接入口

`AppController` 新增：

```kotlin
suspend fun connectProtocol(onEvent: (ProtocolEvent) -> Unit): Boolean
fun publishProtocolEvent(event: ProtocolEvent)
```

职责：

```text
- 使用 protocolClient 建立连接
- 使用 messageRouter 生成 WebSocket callbacks
- 把 ProtocolEvent 发布到 AppEventBus
- 同时把 ProtocolEvent 回调给 UI presenter
```

### 4. MainViewModel 不再构造 WebSocket callbacks

`MainViewModel` 删除 `buildWebSocketCallbacks()`，改为：

```kotlin
appController.connectProtocol { event ->
    handleProtocolEvent(event, allowAutoReconnect)
}
```

`MainViewModel` 仍负责 UI 可见状态、日志展示、重连策略和音频播放触发，但它不再直接接触 WebSocket 原始回调格式。

## 当前仍保留在 MainViewModel 的内容

以下内容留到 Phase 8A-4：

```text
- conversationState 的大部分切换逻辑
- auto reconnect 调度
- voice response watchdog
- UI 日志聚合
- TTS / STT / LLM 可见状态处理
```

Phase 8A-4 目标是把状态流转继续向 `ConversationStateMachine` / `ConversationSession` 收敛。

## 验收建议

覆盖后重点验收：

```text
1. App 正常启动
2. 连接入口正常
3. 文本对话正常
4. 按住说话 / 松开 / TTS / 打断正常
5. 断线重连逻辑正常
6. 设置页保存、重置网络、重置设备身份、重新激活正常
7. 调试日志仍能显示 WebSocket 连接、hello、JSON、二进制音频帧
```

## 是否需要删除文件

不需要。

## 建议提交信息

```text
route websocket callbacks through protocol events
```
