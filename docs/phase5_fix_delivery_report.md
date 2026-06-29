# Phase 5 修复交付报告

## 背景

Phase 5 初版在收到 TTS 二进制音频帧后，会反复出现：

```text
TTS 播放停止：Opus n 帧，PCM 0B
TTS 播放启动：Opus -> PCM16, 24000Hz/mono AudioTrack
TTS 播放异常：Invalid to call at Released state; only valid in executing state
```

## 修复内容

本修复包只针对 Phase 5 下行播放链路，不改 OTA、WebSocket 握手、语音上行主流程。

### 1. 修复播放生命周期竞争

修改 `AudioEngine.kt`：

- `stopPlayback()` 不再直接 cancel 播放 Job，改为关闭队列后让播放线程自然退出。
- 增加 `playbackGeneration`，避免旧播放线程释放资源时覆盖新播放线程状态。
- `markTtsStop()` 不再立即关闭播放队列，避免服务端 `tts/stop` 与尾部二进制音频帧乱序时反复启停。
- 播放线程在短暂空闲后自然结束，减少多段 TTS 下行时的队列竞争。

### 2. 改进 Android MediaCodec Opus 解码配置

修改 `OpusDecoder.kt`：

- 为 `audio/opus` 解码器补充 `csd-0` OpusHead。
- 补充 `csd-1` codec delay 和 `csd-2` seek preroll。
- 继续使用 24kHz / mono 作为当前协议下行播放参数。

### 3. 调整播放队列

修改 `PlaybackQueue.kt`：

- 增加带 timeout 的 `receiveOrNull(timeoutMs)`。
- 队列容量从 120 提升到 240，降低短时网络抖动时的丢帧概率。

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/
├── AudioEngine.kt
├── OpusDecoder.kt
└── PlaybackQueue.kt
```

## 验收建议

```text
1. 启动 App 并连接 WebSocket。
2. 输入文字发送。
3. 观察日志中是否仍出现 Invalid to call at Released state。
4. 观察是否能出现非 0 的 PCM 播放统计。
5. 若仍然没有声音但不再崩溃，下一步需要改为 libopus JNI 解码。
6. 打断按钮应仍能停止本地播放并发送 abort。
```

## 风险说明

当前仍使用 Android 系统 `MediaCodec` Opus 解码器。不同模拟器 / 真机对 raw Opus packet 的兼容性可能不一致。如果该修复后仍然无法正常播放，下一步应将下行解码器替换为 `libopus + JNI/NDK`，避免依赖系统 MediaCodec 对 raw Opus 的支持。

建议提交信息：fix tts playback lifecycle
