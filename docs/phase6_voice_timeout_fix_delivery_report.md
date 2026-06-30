# Phase 6 语音思考状态修复交付说明

## 背景

Phase 6 收敛状态机后，`stopListening()` 会在语音上行结束后把状态切到 `Thinking`，等待服务端返回 STT / LLM / TTS。

实际测试中，文本链路正常，但语音上行松开后可能长时间停留在“思考中”。日志显示 `listen/start/manual`、Opus 上行帧和 `listen/stop` 都已发送，因此问题不一定是 WebSocket 发送失败，更可能是服务端没有返回语音结果、模拟器麦克风输入异常、录音内容太短/太静，或 Android 上行音频仍需后续真机验证。

## 修改内容

修改文件：

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
```

新增能力：

```text
1. 新增语音响应 watchdog
2. 发送 listen/stop 后，如果本次确实有录音上行，则等待 STT/TTS 响应
3. 12 秒内收到 stt / llm / tts / audio 任意服务端响应，会取消 watchdog
4. 12 秒内没有任何语音响应时，不再无限卡在 Thinking，而是回到 Connected
5. 超时时写入最近错误，提示检查麦克风、模拟器输入或重试
6. reconnect / abort / close / socket lost 时会取消语音响应 watchdog
```

## 验收建议

```text
1. 连接 WebSocket
2. 长按说话 2~4 秒后松开
3. 如果服务端正常返回 STT/TTS，状态应进入 Thinking / Speaking / Connected 流程
4. 如果服务端没有返回任何语音结果，12 秒后应自动回到“已连接”
5. 调试日志应出现：语音响应等待超时
6. 文本发送链路不受影响
7. 打断、重连、关闭连接不应留下旧的 Thinking 状态
```

## 注意

本修复主要解决“状态机无限卡在思考中”的问题，不直接证明音频上行内容一定被服务端正确识别。

如果真机上也频繁触发语音响应超时，下一步应重点检查：

```text
1. Android MediaCodec Opus 编码出的 packet 是否被服务端稳定接受
2. 模拟器/真机麦克风是否真的有输入
3. AudioRecord 的 audio source 是否需要从 VOICE_RECOGNITION 切换为 MIC
4. 是否需要引入 libopus JNI 替代系统 MediaCodec Opus 编码
```

建议提交信息：fix voice thinking timeout
