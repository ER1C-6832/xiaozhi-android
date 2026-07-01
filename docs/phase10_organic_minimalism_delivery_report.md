# Phase 10 Organic Minimalism UI Upgrade

## 目标

在 Phase 10 UI 2.0 基础上进一步完成美学升级：抛弃冷蓝金属球和蓝紫渐变按钮，改为温暖、有机、极简的 Android AI 助手界面。

## 覆盖文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/
├── domain/
│   └── ConversationState.kt
└── ui/
    ├── main/
    │   ├── MainScreen.kt
    │   └── components/
    │       ├── AssistantFace.kt
    │       ├── BottomControlBar.kt
    │       ├── DebugLogPanel.kt
    │       └── ToolCallPanel.kt
    └── theme/
        ├── Color.kt
        └── Theme.kt
```

## 实现内容

- 主背景改为暖灰 / 燕麦色 `#F9F9F7`。
- 主卡片改为纯白卡片，圆角 16dp，极轻阴影。
- 按住说话按钮改为深炭黑 pill，按下时微缩并变浅。
- 辅助按钮改为细边框极简按钮。
- 文本输入框去除强烈视觉层级，只保留暖灰背景和细灰边框。
- Assistant 主视觉重构为 Canvas Organic Blob。
- Organic Blob 使用多层贝塞尔 Path 绘制，并通过正弦 / 余弦控制点变化实现缓慢不规则形变。
- 状态动画映射：
  - 未连接：安静小水滴。
  - 待命：柔和呼吸。
  - 聆听中：小幅高频波纹。
  - 思考中：双核细胞环绕融合。
  - 说话中：更大幅变形和上下震荡。
  - 错误：暗淡红褐、干瘪形态。
- 主视觉中加入随状态变化的可爱表情。
- 主卡片状态文字改为轻字重、增加字间距。
- 顶栏移除重复状态文案，状态只保留在主卡片内。
- 开发者抽屉配色调整为暖色极简风。
- 工具调用浮层继续支持自动消失、上滑/左滑手动关闭。

## 验收建议

1. 未连接时显示“未连接”，不是“待命”。
2. 连接成功空闲后显示“待命”。
3. 主界面不再出现蓝色金属球 / 蓝紫渐变按钮。
4. 中央主视觉是柔和有机 blob，并能随状态动起来。
5. 聆听、思考、说话、错误状态有明显但不刺眼的不同动效。
6. 按住说话按钮为黑色 pill，按下有缩放和轻微变浅反馈。
7. 工具调用浮层仍可自动消失，也能上滑或左滑关闭。
8. 文本发送、语音、打断、设置页、MCP 工具调用仍正常。

## 建议提交信息

```text
refine ui with organic minimal assistant
```
