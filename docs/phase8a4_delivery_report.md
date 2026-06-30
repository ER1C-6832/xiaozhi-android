# Phase 8A-4 Delivery Report

## 阶段目标

本阶段完成 App Core 架构重构的最后一段：将对话状态流转从 MainViewModel 进一步收敛到 AppController / AppStateStore / ConversationStateMachine。

目标不是新增业务功能，而是让后续 MCP、CameraX、WakeWord、自动对话等模块能够观察和复用统一状态流，不再直接依赖 MainViewModel。

## 本次修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/
├── core/
│   ├── AppController.kt
│   ├── AppEvent.kt
│   └── AppStateStore.kt
├── conversation/
│   └── ConversationStateMachine.kt
├── protocol/
│   └── XiaozhiProtocolClient.kt
└── ui/
    └── main/
        └── MainViewModel.kt
```

## 主要变化

### 1. AppStateStore 成为 UI 状态 backing store

MainViewModel 不再自己持有独立 mutableStateOf，而是通过 AppController.stateStore 读写 ConversationUiState。

这让 UI 状态所有权从 ViewModel 内部逐步迁移到 App Core。

### 2. ConversationStateMachine 增强

ConversationStateMachine 现在记录：

```text
- currentState
- lastTransition
- recentTransitions
- from / to / reason / timestamp
```

状态机仍然保持宽松，不改变现有功能行为，但每次迁移都有 reason，便于后续调试和模块观察。

### 3. AppController 统一状态迁移入口

新增：

```kotlin
transitionConversationState(next, reason)
resetConversationState(reason)
```

它会：

```text
- 更新 ConversationStateMachine
- 更新 AppStateStore.uiState.conversationState
- 发布 AppEvent.ConversationStateChanged
```

### 4. AppEvent 新增状态事件

新增：

```kotlin
ConversationStateChanged(from, to, reason)
```

后续 MCP 工具卡片、唤醒词、CameraX、通知栏、自动对话都可以监听这个事件。

### 5. MainViewModel 状态写入统一走 AppController

MainViewModel 仍作为 Compose presenter，但状态修改基本通过：

```kotlin
transitionTo(ConversationState.X, reason)
```

不再直接孤立修改状态机。

### 6. 保留 A3 编译修复

XiaozhiProtocolClient.kt 保留：

```kotlin
NetworkState.Disconnected -> ProtocolEvent.Closed(label)
```

避免再次出现 ProtocolEvent.Disconnected 未定义问题。

## 不需要删除文件

本包不需要删除任何文件。直接覆盖即可。

## 验收建议

覆盖后重点验收：

```text
1. App 正常启动
2. 连接入口正常
3. 文本对话正常
4. 按住说话 / 松开 / TTS / 打断正常
5. 断线重连正常
6. 设置页保存、重置网络、重置设备身份、重新激活正常
7. 清空日志 / 复制日志正常
8. 不出现编译错误
```

## 后续建议

Phase 8A 到这里可以视为 App Core 架构重构完成：

```text
8A-1：App Core 骨架和依赖收敛
8A-2：运行时资源所有权迁移
8A-3：Protocol Router 与 WebSocket 回调迁移
8A-4：状态流转与 AppStateStore 收敛
```

下一阶段可进入：

```text
Phase 8B：Android MCP 协议层
```

建议提交信息：

```text
centralize conversation state flow
```
