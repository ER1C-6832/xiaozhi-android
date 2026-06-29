# Phase 5 Pitch Fix 交付报告

## 背景

Phase 5 首轮修复后，TTS 下行已经可以解码并通过 AudioTrack 播放，但实际听感明显低沉、变慢，类似“水牛声”。日志显示：

```text
TTS 播放停止：Opus 239 帧，PCM 458880B
```

239 帧如果按 20ms、24kHz、mono、PCM16 计算，理论 PCM 约为 229440B；实际约为 458880B，正好翻倍，说明 Android MediaCodec 的 Opus 解码输出更接近 48kHz mono PCM16。

## 本次修改

- 将 `AudioConstants.OUTPUT_SAMPLE_RATE` 从 `24_000` 改为 `48_000`。
- `AudioTrack` 因使用 `AudioConstants.OUTPUT_SAMPLE_RATE`，会同步改为 48kHz 播放。
- `OpusDecoder` 的 MediaFormat 也会同步使用 48kHz，和 Android 系统 Opus 解码器常见输出保持一致。
- 设置页下行播放说明改为 `Opus -> PCM16 / 48kHz / AudioTrack`。

## 预期效果

- TTS 不再明显降调和变慢。
- 日志中 PCM 字节数保持不变是正常的，因为变化的是播放采样率，不是解码出的 PCM 数据量。
- 如果仍然声线不对，下一步应在日志里打印 `MediaCodec.INFO_OUTPUT_FORMAT_CHANGED` 的真实 `sample-rate` 和 `channel-count`，或直接切换到 libopus JNI 解码。

## 验收建议

1. 覆盖后重新 Run App。
2. 连接 WebSocket。
3. 发送文本，听 TTS 声线和语速是否恢复正常。
4. 查看日志中 `TTS 播放启动` 是否显示 48000Hz/mono AudioTrack。

建议提交信息：fix tts playback sample rate
