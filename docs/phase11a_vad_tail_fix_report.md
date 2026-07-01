# Phase 11A VAD Tail Silence Fix

## 问题

第一版 AUTO_STOP 使用固定的 silencePeak/silenceRms 双阈值作为尾静音判断。在手机麦克风或模拟器背景噪声稍高时，用户说完话后虽然已经没有有效语音，但某些帧仍高于固定静音阈值，导致尾静音计数频繁清零，表现为“说完好几秒才 stop”。

## 修复

- AUTO_STOP 默认尾静音从 900ms 调整到 560ms。
- 起声检测从 5 帧降到 3 帧。
- warmup 从 260ms 调整到 160ms。
- 引入轻量自适应噪声底：在预热和未检测到语音前跟踪 peak/rms EMA。
- 使用动态 speech threshold：`max(staticThreshold, noiseFloor + margin)`。
- 尾静音判断改为“非明确语音即累计尾静音”，不再要求绝对安静。
- VAD 状态文案改成“短暂停顿”，避免继续暴露 900ms 旧行为。

## 行为

AUTO_STOP 下：

1. 点自然对话开始录音。
2. VAD 先预热并估算背景噪声。
3. 连续约 3 帧检测到语音后进入“等待短暂停顿”。
4. 说完后，只要后续帧不再像语音，就累计尾静音。
5. 约 560ms 后自动 stop。

## 说明

这仍然是 Phase 11A 的轻量能量阈值 VAD，不是 WebRTC VAD。它的目标是先把 AUTO_STOP 交互做顺滑。Phase 11B 再接 Android AEC / WebRTC VAD，Phase 11C 进入真正全双工。
