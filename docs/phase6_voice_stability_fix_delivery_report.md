# Phase 6 语音多轮对话稳定性修复交付报告

## 背景

Phase 6 后，文本链路正常，但语音链路在多轮手动对话后可能出现：

```text
按住说话 -> Opus 帧已发送 -> listen/stop 已发送 -> 服务端不返回 STT/TTS -> UI 进入或停留在思考中
```

前两个补丁分别处理了“无限思考兜底”和“0 帧录音竞态”，但仍可能出现 Opus 帧数量足够、服务端仍不返回的情况。

## 本次排查结论

本轮重点不再只看状态机，而是同时检查控制消息顺序、WebSocket 发送队列、麦克风输入质量和上一轮服务端状态残留。

当前判断：

```text
1. 单纯超时兜底不能解决根因，只能防止 UI 卡死。
2. 0 帧竞态不是唯一问题，因为失败轮次也可能有 100+ Opus 帧。
3. 更可能存在以下一种或多种问题：
   - listen/stop 发出时 WebSocket binary 队列仍有音频帧待发送
   - 上一轮服务端 listen/tts 状态未完全收口，下一轮 start 被残留状态影响
   - 模拟器或系统 AudioRecord 输入偶发静音，但仍会编码出 Opus 包
   - 多线程调用 WebSocket.send 时需要更明确地串行化控制消息和音频消息
```

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioEngine.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioRecorder.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/network/XiaozhiWebSocketClient.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
```

## 主要修复

### 1. WebSocket 发送串行化与队列观测

`XiaozhiWebSocketClient` 增加：

```text
- sendLock
- queuedBytes()
```

文本控制消息和二进制音频帧都经过同一个发送锁，减少跨线程调用 `WebSocket.send()` 时的顺序不确定性。

`queuedBytes()` 用于在发送 `listen/stop` 前观察 OkHttp WebSocket 仍有多少数据排队。

### 2. listen/stop 前等待音频发送队列排空

`MainViewModel.stopListening()` 在录音线程收尾后、发送 `listen/stop` 前，会短暂等待 WebSocket outgoing queue 排空，最多 300ms。

目标是尽量保证：

```text
listen/start
Opus binary frames
listen/stop
```

这个顺序不仅是调用顺序，也是实际发送队列中的顺序。

### 3. 语音超时后主动 abort 清理服务端状态

如果等待 STT/TTS 超时，客户端会：

```text
- 回到 Connected
- 记录 lastVoiceTurnTimedOut
- 尝试发送 abort 清理服务端监听状态
```

下一轮语音开始前，如果检测到上一轮语音超时或仍处于 Thinking，会先发送 abort 清理旧状态，再发送新的 `listen/start/manual`。

### 4. 麦克风输入质量统计

`AudioEngine.RecordingStats` 增加：

```text
peakAbs
rms
silentFrames
silentRatioPercent
failedSends
```

停止录音时日志会显示：

```text
PCM xxx 帧，Opus xxx 帧，peak=xxx, rms=xxx, 静音 xx%
```

如果 Opus 帧很多但 peak/rms 很低，说明客户端可能只是上传了“静音 Opus”，这时应重点检查模拟器/真机麦克风输入。

### 5. AudioRecord source 调整

`AudioRecorder` 现在优先使用：

```text
MediaRecorder.AudioSource.MIC
```

如果失败，再回退到：

```text
MediaRecorder.AudioSource.VOICE_RECOGNITION
```

这样可以规避部分模拟器或设备上 VOICE_RECOGNITION 音频处理链偶发输出异常的问题。

## 验收建议

```text
1. 覆盖后重新 Run App。
2. 点击连接入口，确认 WebSocket 已连接并有 session_id。
3. 连续做 10 轮语音测试，每轮按住 2 到 4 秒。
4. 每轮观察日志：
   - 音频上行停止：PCM / Opus / peak / rms / 静音比例
   - 已发送 listen/stop：Opus / peak / rms / WebSocket 发送队列
   - 是否收到 stt / llm / tts
5. 如果某轮失败但 peak/rms 很低，优先怀疑麦克风输入。
6. 如果某轮失败但 peak/rms 正常、Opus 充足、发送队列已排空，则下一步应替换系统 MediaCodec Opus 编码为 libopus JNI。
```

## 预期变化

```text
- 多轮语音对话不应频繁卡在思考中。
- 失败轮次也会留下足够日志用于判断：是无有效麦克风输入、发送队列未排空、还是服务端/编码兼容问题。
- 如果服务端没有返回，客户端会主动 abort 清理旧状态，下一轮更容易恢复。
```

## 仍未处理

```text
- 未引入 libopus JNI。
- 未做 AEC / 回声消除。
- 未做真机专用音频路由优化。
```

建议提交信息：fix multi turn voice stability
