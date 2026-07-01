# Phase 10 Aurora Saturation / Physics Fix

## 目标

修复上一版 Aurora Fluid Gradient 在白底上因 Screen 混合导致颜色被洗掉、思考状态旋转存在跳变感、说话状态第三斑块颜色与 Y 轴拉伸不明显的问题。

## 修改文件

- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/theme/Color.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/theme/Theme.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/AssistantFace.kt`

## 关键改动

- 所有主视觉斑块绘制从 `BlendMode.Screen` 改为 `BlendMode.SrcOver`。
- 极光色值升级为高饱和清晨色相：
  - `ColorMistyBlue = #3A86FF`
  - `ColorLilac = #8338EC`
  - `ColorPeach = #FF006E`
  - `ColorSlateGray = #475569`
  - `ColorSunsetAmber = #FF9F1C`
  - `ColorRoseRed = #E63946`
- radialGradient 中心 alpha 提升至 0.75 - 0.9 区间，再在边缘平滑消隐到透明。
- Thinking 状态新增独立 `rotatingAngle` 无限循环轴：0 到 360 度，3500ms，LinearEasing，Restart。
- Thinking 圆心公转由 `rotatingAngle` 严格计算，避免使用往返动画造成跳变。
- Speaking 第三斑块 C 改为 `ColorSunsetAmber`。
- Speaking 第三斑块 C 使用指定公式：
  - `radiusC = R * (0.6f + volumeScale * 0.4f)`
  - `offsetC = center + Offset(0f, 25.dp + (volumeScale * 55.dp))`
- 状态参数按新规范覆盖：Idle / Connected / Error 已按指定值调整。

## 验收重点

1. 主视觉颜色不再灰白、泛淡。
2. 待命状态蓝紫色更饱满。
3. 未连接状态应是清晰的深灰玉石感光晕。
4. 错误状态红灰交织，红色明显。
5. 思考中双核旋转顺滑，不出现明显回跳。
6. 说话中下方琥珀斑块随音量向下拉伸更明显。
7. 无眼睛、嘴巴、黑点、同心圆雷达线。
