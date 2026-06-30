# Phase 8A-1 交付报告：App Core 架构骨架与依赖收敛

## 背景

Phase 1 到 Phase 7 已完成小智 Android 版的核心链路：OTA / 激活、WebSocket、文本对话、语音上行、TTS 播放、状态机、设置页、重置与重新激活。

进入 MCP、CameraX、唤醒词、自动对话和 UI 2.0 前，当前架构需要从“MainViewModel 功能堆叠”逐步收敛成 Android 化的插件化主控。

## 本包定位

这是 Phase 8A 的第一个小包，目标是低风险搭好 App Core 骨架，并把对象创建从 `AppNavigation` 收拢到 `AppController`。

本包不新增业务功能，不改变现有用户操作路径。

## 新增文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/core/
├── AppController.kt
├── AppEvent.kt
├── AppEventBus.kt
├── AppStateStore.kt
├── AppModule.kt
├── ModuleManager.kt
└── ResourceRegistry.kt

app/src/main/java/com/er1cmo/xiaozhiandroid/conversation/
├── ConversationController.kt
├── ConversationStateMachine.kt
└── ConversationSession.kt

app/src/main/java/com/er1cmo/xiaozhiandroid/protocol/
├── XiaozhiProtocolClient.kt
├── XiaozhiMessageRouter.kt
└── ProtocolEvent.kt
```

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/navigation/AppNavigation.kt
```

## 已完成内容

- 新增 `AppController`，作为 Android 端主控容器。
- 新增 `AppEventBus`，为后续 MCP / CameraX / WakeWord / ToolCard 事件流做准备。
- 新增 `AppStateStore`，为后续 MainViewModel 瘦身提供 UI 状态承载点。
- 新增 `AppModule`、`ModuleManager`，为 Android 模块化插件注册做准备。
- 新增 `ResourceRegistry`，为 WebSocket、Audio、Camera、WakeWord 等资源统一释放做准备。
- 新增 `conversation/` 包，承载后续对话控制器、状态机、会话快照。
- 新增 `protocol/` 包，承载后续 WebSocket / MQTT / MCP 消息路由。
- `AppNavigation` 不再直接创建所有底层依赖，而是创建 `AppController` 后从主控容器取依赖。

## 保持不变

- 文本对话逻辑不变。
- 语音上行逻辑不变。
- TTS 播放逻辑不变。
- 设置页动作不变。
- OTA / 激活流程不变。
- WebSocket 自动重连逻辑不变。

## 为什么本包没有一次性移动全部 MainViewModel 逻辑

当前 `MainViewModel` 同时承担 UI 状态、连接控制、OTA / 激活、WebSocket 回调、音频上行、TTS 下行、设置动作和日志管理。一次性移动全部逻辑风险较高，容易把已经通过真机验收的语音链路打乱。

Phase 8A 采用分步重构：

1. 8A-1：建立 App Core 骨架，收拢依赖创建。
2. 8A-2：把连接 / OTA / 重置 / 设置动作迁入 AppController。
3. 8A-3：把 WebSocket JSON / binary 分发迁入 XiaozhiMessageRouter。
4. 8A-4：把状态流转迁入 ConversationStateMachine。
5. 8B：在此架构基础上接 Android MCP 协议层。

## 验收标准

```text
1. App 可以正常编译启动。
2. 连接入口仍可连接 WebSocket。
3. 文本发送仍可收到回复。
4. 按住说话、松开、TTS 播放、打断仍可用。
5. 设置页保存、重置网络、重置设备身份、重新激活仍可用。
6. 主界面日志、开发者调试开关仍可用。
7. 行为与 7B 版本一致，没有新功能变化。
```

## 下一步建议

Phase 8A-2：将 `MainViewModel` 中的连接、激活、重置和关闭逻辑迁入 `AppController`，让 `MainViewModel` 逐步变成 UI Presenter。

建议提交信息：

```text
introduce app core controller
```
