# Phase 5 交付报告：TTS 下行解码播放

## 本次目标

实现小智服务端下行二进制 Opus 音频帧的解码和播放，让文本发送或语音上行触发服务端 TTS 后，Android App 能直接播放回复音频。

## 新增文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/
├── AudioPlayer.kt
├── OpusDecoder.kt
└── PlaybackQueue.kt
```

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/
├── AudioConstants.kt
└── AudioEngine.kt

app/src/main/java/com/er1cmo/xiaozhiandroid/network/
└── XiaozhiWebSocketClient.kt

app/src/main/java/com/er1cmo/xiaozhiandroid/domain/
└── ConversationUiState.kt

app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/
└── MainViewModel.kt

app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/
└── DebugLogPanel.kt

app/src/main/java/com/er1cmo/xiaozhiandroid/ui/settings/
└── SettingsScreen.kt
```

## 已实现能力

```text
- WebSocket 收到二进制 Opus 帧后交给 AudioEngine
- Android MediaCodec Opus 解码为 PCM16
- AudioTrack 以 24kHz / mono / streaming mode 播放 TTS
- 收到 tts/start 时清空旧播放并准备新一轮 TTS
- 收到 tts/stop 时关闭播放队列，等待队列自然播放完毕
- 打断对话时立即停止本地播放并清空队列
- TTS 播放中再次按住说话时，先打断当前 TTS，再开始录音上行
- 文本发送和语音上行前如果 WebSocket 已断开，会提示重新点击连接入口
- 下行 Opus 帧日志改为每 50 帧摘要一次，避免刷屏
- 设置页和调试面板显示 TTS 播放状态
```

## 验收建议

```text
1. 启动 App 并点击“连接入口”，确认 WebSocket 已连接。
2. 输入文字后点击“发送”。
3. 观察日志出现 tts/start、TTS 播放启动、收到 TTS Opus 音频帧。
4. 确认模拟器或真机能听到小智回复。
5. TTS 播放时点击“打断对话”，确认播放立即停止。
6. TTS 播放时按住说话，确认先打断 TTS，再开始录音上行。
7. 连接断开后尝试发送文本，确认提示重新点击连接入口。
8. 长时间收到音频帧时，日志只显示摘要，不再逐帧刷屏。
```

## 注意事项

当前 TTS 解码使用 Android 系统 `MediaCodec` 的 `audio/opus` 解码器。如果某些设备的系统 Opus 解码器不支持服务端 raw Opus packet，日志会显示播放异常；后续可以替换为 `libopus + JNI/NDK`，接口已通过 `OpusDecoder` 隔离。

下行播放默认使用 `24kHz / mono`，与 py-xiaozhi 默认输出采样率保持一致。后续可在服务端 hello 返回音频参数后做动态切换。

建议提交信息：add tts audio playback
