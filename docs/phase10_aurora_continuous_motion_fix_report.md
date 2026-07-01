# Phase 10 Aurora Continuous Motion Fix

## 问题
待命等低速状态使用 0..2PI 重复时间轴时，`time * animationSpeedScale` 在一个循环结束时并没有完成完整 2PI 周期。比如待命状态 speed=0.5，只走到半圈就重置为 0，造成流体“转半圈跳回原位”。

## 修复
- `AssistantFace.kt` 不再把重复 0..2PI tween 直接作为物理相位。
- 改为使用 `withFrameNanos` 生成单调递增的 elapsedSeconds。
- `fluidPhase = elapsedSeconds * TWO_PI / 6f * animationSpeedScale`，不会周期性跳回 0。
- Thinking 双核旋转改为 `elapsedSeconds * TWO_PI / 3.5f`，同样无重置跳变。
- 保持 `BlendMode.SrcOver`、高饱和极光色、Speaking 第三斑块公式和现有 UI 行为。

## 覆盖文件
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/AssistantFace.kt`

## 验收
1. 待命状态下流体持续缓慢运动，不再半圈跳回原位。
2. Thinking 双核旋转持续无缝。
3. 聆听、说话、错误状态视觉仍正常。
4. 编译通过，文本/语音/MCP 不受影响。
