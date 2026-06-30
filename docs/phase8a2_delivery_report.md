# Phase 8A-2 交付报告：App Core 运行时收敛

## 本阶段目标

Phase 8A-2 继续推进 Android 化插件化主控改造。本阶段不新增业务功能，重点是把运行时对象和基础控制能力从 UI 层进一步收敛到 `AppController`，为后续 MCP、CameraX、WakeWord、自动对话和 UI 2.0 打基础。

## 本次创建或修改

### 1. AppController 增强

修改：

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/core/AppController.kt
```

新增运行时持有对象：

```text
- protocolClient: XiaozhiProtocolClient
- messageRouter: XiaozhiMessageRouter
- conversationStateMachine: ConversationStateMachine
- conversationSession: ConversationSession
```

新增统一运行时关闭方法：

```kotlin
fun closeRuntime(
    reason: String,
    onAudioLog: (String) -> Unit = {},
    onAudioStatusChanged: (String) -> Unit = {},
)
```

用于集中停止录音、停止播放、关闭 WebSocket，并发布 `ProtocolDisconnected` 事件。

### 2. MainViewModel 构造方式收敛

修改：

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
```

从：

```kotlin
MainViewModel(
    configRepository,
    deviceIdentityManager,
    otaActivationClient,
    xiaozhiWebSocketClient,
    audioEngine,
    appScope,
)
```

改为：

```kotlin
MainViewModel(appController = appController)
```

MainViewModel 仍然作为 Compose UI presenter，但底层依赖不再由 ViewModel 构造参数分散持有，而是统一从 AppController 获取。

### 3. 主运行时清理改为 AppController 统一执行

以下场景改为调用 `appController.closeRuntime(...)`：

```text
- 用户手动关闭连接
- 设置页触发重置网络配置
- 设置页触发重置设备身份
- 设置页触发重新 OTA / 重新激活前的运行时清理
```

### 4. AppEventBus 开始承载真实运行事件

MainViewModel 现在会向 AppEventBus 发布：

```text
- AppEvent.Log
- AppEvent.ProtocolConnected
- AppEvent.IncomingJson
- AppEvent.IncomingAudio
```

这一步是为了后续工具卡片、MCP 调试日志、CameraX 结果卡片和通知栏控制可以监听核心事件，而不是直接依赖 MainViewModel。

### 5. ConversationStateMachine 开始接入 UI 状态流

本阶段把部分关键状态切换接入 `ConversationStateMachine`：

```text
- Listening
- Thinking
- Speaking
- Connected
```

目前仍保留 MainViewModel 的现有状态更新方式，避免一次性大改造成回归风险。后续 8A-3 会继续迁移 WebSocket 回调和 JSON 路由。

### 6. AppNavigation 进一步瘦身

修改：

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/navigation/AppNavigation.kt
```

现在 AppNavigation 只创建 AppController，并把 AppController 传给 MainViewModel，不再展开传递所有底层依赖。

## 本阶段不做

```text
- 不新增 MCP 功能
- 不新增 CameraX
- 不新增 WakeWord
- 不重做 UI
- 不删除旧 domain/ConversationController
- 不把 MainViewModel 完全清空
```

## 验收建议

覆盖后执行以下验收：

```text
1. App 正常启动
2. 点击连接入口，仍可正常 OTA / 连接 WebSocket
3. 文本发送仍可收到回复和 TTS
4. 按住说话、松开、TTS 播放、打断仍正常
5. 参数设置页保存、重置网络配置、重置设备身份、重新激活仍正常
6. 开发者调试开关、复制日志、清空日志仍正常
7. 重置网络配置后 WebSocket 不应继续显示已连接
8. 无编译错误、无启动崩溃
```

## 后续建议

下一步建议做：

```text
Phase 8A-3：Protocol Router 与 WebSocket 回调迁移
```

重点：

```text
- WebSocket JSON / binary 统一进入 XiaozhiMessageRouter
- type=mcp 预留给 AndroidMcpServer
- MainViewModel 不再直接解析所有协议消息
- ProtocolEvent 通过 AppEventBus 分发
```

建议提交信息：

```text
move runtime ownership into app controller
```
