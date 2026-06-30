# Phase 6 语音上行竞态修复交付报告

## 背景

Phase 6 之后，文本对话正常，但语音对话在多轮测试后可能卡在“思考中”。日志显示异常轮次里可能出现 `PCM 0 帧，Opus 0 帧`，说明这类失败并不是服务端已收到完整语音但不回复，而是客户端在某些轮次没有真正发送有效音频帧。

## 根因判断

原流程中，`startManualListening()` 会先发送 `listen/start/manual`，然后异步启动 `AudioRecord`；松开按钮时 `stopListening()` 会立即发送 `listen/stop` 并切到 `Thinking`。

如果用户松开较快、模拟器调度慢、上一轮音频资源释放较慢，可能出现：

```text
listen/start 已发送
stopListening 被触发
AudioRecord 协程尚未真正采集到 PCM/Opus
listen/stop 已发送
客户端进入 Thinking
服务端没有收到有效音频，所以不返回 STT/TTS
```

这会造成“卡思考中”。上一轮的 12 秒 watchdog 只能兜底恢复状态，但没有解决 0 帧音频轮次的根因。

## 修改内容

### AudioEngine.kt

- 新增 `RecordingStats(pcmFrames, opusPackets)`。
- 录音过程中实时记录当前 PCM 帧数和 Opus 包数。
- 新增 `recordingStats()`。
- 新增 `suspend fun stopRecordingAndAwait()`：请求停止录音后等待录音协程收尾，确保已编码的 Opus 帧尽量先发完。
- 增加启动取消判断，避免 stop 已经发生后仍然继续打开麦克风并打印误导性启动日志。

### MainViewModel.kt

- 新增 `isStoppingListening`，避免 stop 流程重复进入。
- `stopListening()` 改为异步收尾流程：
  - 等待 `AudioEngine.stopRecordingAndAwait()` 返回统计。
  - 先确认本轮是否有足够有效 Opus 帧。
  - 有效帧数达到阈值时，发送 `listen/stop` 并进入 `Thinking`。
  - 如果本轮没有有效 Opus 帧，仍发送 `listen/stop` 收口服务端状态，但不进入 `Thinking`，直接回到 `Connected`。
- 日志中增加本轮 PCM/Opus 帧统计，方便区分“服务端没回”和“客户端没上传有效音频”。

## 验收建议

1. 连续进行 6 到 10 轮按住说话测试。
2. 每轮至少按住 1 到 2 秒再松开。
3. 观察日志里 `listen/stop` 后是否带有本轮 Opus 帧数。
4. 如果某轮 `Opus 0` 或低于 5 帧，客户端应直接回到“已连接”，不再卡“思考中”。
5. 如果某轮 Opus 帧数足够，但 12 秒内仍无 STT/TTS，则会走 watchdog 超时；这时应重点怀疑模拟器麦克风输入或上行编码兼容性。
6. 真机上复测语音上行仍是后续必要项。

## 未处理内容

- 未改动 Opus 编码器实现。
- 未引入 libopus JNI。
- 未处理模拟器 TTS 毛刺。

建议提交信息：fix voice uplink stop race
