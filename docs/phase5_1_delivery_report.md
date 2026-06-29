# Phase 5.1 交付报告：TTS 播放平滑化

## 本次目标

在 Phase 5 已经打通 TTS 下行播放、并修复 48kHz 播放采样率后，继续优化播放流畅度，降低模拟器或真机播放时出现的断点、毛刺和队列抖动。

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/
├── AudioEngine.kt
└── PlaybackQueue.kt

docs/
└── phase5_1_delivery_report.md
```

## 主要改动

1. 增加 TTS 播放预缓冲：收到下行 Opus 后先累计约 10 帧，或最多等待约 260ms，再启动 AudioTrack 播放。
2. AudioTrack 在 `play()` 前先写入预缓冲 PCM，减少一开始就欠载导致的断点。
3. 播放中改为较短的 idle timeout + 多次空闲确认，避免帧间轻微抖动导致播放线程过早退出。
4. 播放停止前增加短暂 drain grace，让最后一小段已写入的 PCM 有机会播完再释放 AudioTrack。
5. PlaybackQueue 增加队列深度统计，日志可观察“已解码帧数”和“当前队列帧数”。

## 验收建议

```text
1. 连接 WebSocket。
2. 发送文本，触发 TTS 回复。
3. 日志应出现：TTS 播放准备、TTS 预缓冲完成、TTS 已解码播放。
4. TTS 声线和语速应继续保持正常。
5. 重点听是否比 Phase 5 更少断点和毛刺。
6. 在 Pixel 模拟器和三星 S20 真机上分别测试；真机结果更有参考价值。
```

## 注意事项

当前仍使用 Android 系统 MediaCodec Opus 解码器。如果真机上仍有明显杂音或断点，下一步建议引入 `libopus + JNI/NDK` 替代 MediaCodec，获得更可控的 raw Opus 解码行为。

建议提交信息：smooth tts playback buffering
