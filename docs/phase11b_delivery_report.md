# Phase 11B Delivery Report: Android AEC / NS 基础音频处理

## 范围

本包先落地 Android 端系统级音频前处理能力，不推进 11C 全双工状态机。

参考 py-xiaozhi-tao-analysis 的音频分层思路：输入流、输出流、编解码、音频处理能力分层，不把 VAD、编解码、播放队列混在 UI 侧。

## 改动文件

- `app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioRecorder.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioEngine.kt`

## 功能

- `AudioRecorder` 新增 `createRecordSession()`。
- 录音 source 优先使用 `VOICE_COMMUNICATION`，再 fallback 到 `VOICE_RECOGNITION`、`MIC`。
- 为 `AudioRecord.audioSessionId` 绑定 Android 系统音频效果：
  - `AcousticEchoCanceler`
  - `NoiseSuppressor`
  - `AutomaticGainControl` 预留但默认不开启
- `AudioEngine.startRecording()` 默认请求 AEC + NS。
- 启动录音时输出处理链状态日志，例如：
  - `Android 音频处理：source=VOICE_COMMUNICATION, session=..., AEC=on, NS=on, AGC=available_off`
- 不支持 AEC 的设备不会崩溃，会 fallback 并在日志中显示 `AEC=unavailable`。

## 说明

11B 这包先把 Android 系统 AEC / NS 接进去，让后续 11C 全双工有基础。真正的全双工仍需要：

- TTS 播放时保持采集
- VAD 区分用户真声与残余回声
- 检测到用户插话后发送 abort
- 进入新一轮 listen

这些放到 11C 做，不和本包混在一起。
