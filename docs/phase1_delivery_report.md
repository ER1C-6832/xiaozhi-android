# Phase 1 交付报告：可演进主界面 + 连通性测试入口

## 本次目标

本次交付将 Android Studio 默认的 `Hello Android` 模板替换为一个可继续演进的正式 App 骨架，用于承载后续 OTA、激活、WebSocket hello、文本消息与服务端 JSON 响应验证。

## 创建或修改的文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/
├── MainActivity.kt                                  # 修改：入口改为 AppNavigation
├── domain/
│   ├── ConversationState.kt                         # 新增：对话/连接状态枚举
│   └── ConversationUiState.kt                       # 新增：主界面状态模型
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt                         # 新增：主界面与设置页的轻量导航
│   ├── main/
│   │   ├── MainScreen.kt                            # 新增：主界面骨架
│   │   ├── MainViewModel.kt                         # 新增：Phase 1 本地状态与日志
│   │   └── components/
│   │       ├── AssistantFace.kt                     # 新增：小智头像/表情占位
│   │       ├── BottomControlBar.kt                  # 新增：底部操作区
│   │       └── DebugLogPanel.kt                     # 新增：连通性调试面板
│   └── settings/
│       └── SettingsScreen.kt                        # 新增：参数设置占位页

docs/phase1_delivery_report.md                       # 新增：本交付说明
```

## 已实现能力

- App 启动后进入“小智语音助手”主界面，不再显示默认 `Hello Android`。
- 顶部显示当前状态和参数设置入口。
- 中间显示小智头像/表情占位和状态说明。
- 下方提供可折叠的连通性调试面板，展示设备 ID、Client ID、激活状态、OTA 状态、WebSocket 状态、最近 JSON 和本地日志。
- 底部提供按住后说话、打断对话、文本输入、发送、手动/自动模式切换。
- 点击发送后，文本会进入本地调试日志，为后续接入 `listen/detect/text` 预留入口。
- 点击连接入口会模拟进入连接中状态，为后续接入 OTA / WebSocket 预留入口。
- 参数设置页先展示系统选项、网络配置和后续阶段入口占位。

## 暂未实现

- 未接入 DeviceIdentityManager。
- 未接入 ConfigRepository / DataStore。
- 未接入 OtaActivationClient。
- 未接入 XiaozhiWebSocketClient。
- 未接入 AudioRecord、Opus、AudioTrack。
- 未实现 MCP、摄像头、唤醒词、AEC、后台服务。

## 建议验收

1. 用 Android Studio 打开项目并等待 Gradle Sync 完成。
2. 运行 App，确认不再显示 `Hello Android`。
3. 确认主界面显示“小智语音助手”和默认状态“待命”。
4. 长按“按住后说话”，确认状态变为“聆听中”，松开后回到“待命”。
5. 点击“打断对话”，确认日志追加对应事件。
6. 输入任意文本并点击“发送”，确认输入框清空，调试日志出现发送内容。
7. 点击“连接入口”，确认状态变为“连接中”，日志出现后续接入提示。
8. 点击“参数设置”，确认可以进入设置页并返回主界面。
