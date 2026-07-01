# Phase 10 Aurora Fluid Gradient 主视觉重构交付说明

## 目标

彻底移除上一版 Organic Blob 中的具象五官、肉色球体和可能引发 uncanny valley 的简笔画表达，改为无脸、抽象、柔和、现代的 Aurora Fluid Gradient 主视觉。

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
    │       └── BottomControlBar.kt
    └── theme/
        ├── Color.kt
        └── Theme.kt
```

## 主要变化

- 新增 Aurora Fluid Gradient 配色：
  - ColorMistyBlue `#D0E1FD`
  - ColorLilac `#E3D5FC`
  - ColorPeach `#FFE3D1`
  - ColorSlateGray `#CBD5E1`
  - ColorRoseDust `#E6C2C2`
- 主背景改为暖灰/燕麦色 `#F9F9F7`。
- 主卡片改为纯白背景、16dp 圆角、4dp 轻阴影。
- AssistantFace 完全移除眼睛、嘴巴、黑色点、描边、同心圆雷达线。
- Canvas 使用多个半透明 radialGradient 斑块，边缘透明羽化。
- 斑块采用 `BlendMode.Screen` 叠加，形成极光融合效果。
- 使用 `rememberInfiniteTransition` 驱动 0 到 `2 * PI` 的 6000ms 全局时间轴。
- 使用 `animateFloatAsState` / `animateColorAsState` 平滑过渡：
  - `globalAlpha`
  - `fluidScale`
  - `animationSpeedScale`
  - `colorBlendRatio`
  - 斑块 A/B 颜色
- 状态映射：
  - Idle：冷石灰、低透明度、极慢漂移
  - Connected：晨雾蓝 + 风信子紫，缓慢呼吸
  - Listening：蜜桃粉 + 晨雾蓝，高频柔波，并带 volumeScale 扰动
  - Thinking：双核顺时针公转缠绕
  - Speaking：蜜桃粉 + 风信子紫，并增加下方第三斑块 C
  - Error：烟粉 + 冷石灰，低透明、慢速衰弱
- 按住说话按钮保持深炭黑 pill 风格，去除蓝紫渐变。
- 主界面顶栏不再重复状态文字，状态只在主视觉卡片中呈现。

## 验收重点

1. 主视觉不再出现任何具象五官、嘴巴、眼睛、黑色点。
2. 不再出现同心圆雷达线或机械几何描边。
3. 不再出现肉黄色、米黄色球体。
4. 未连接 / 待命 / 聆听 / 思考 / 说话 / 错误具有不同流体动效和配色。
5. 工具浮层、文本输入、语音、打断、设置页仍正常。

## 建议提交信息

```text
replace assistant face with aurora fluid gradient
```
