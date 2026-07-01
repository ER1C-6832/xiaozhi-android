# Phase 12A 前台唤醒词识别修复

## 背景

首版 12A 打开后右上角麦克风会亮，说明系统 `SpeechRecognizer` 已经占用麦克风，但喊“小智”没有触发。问题集中在前台唤醒桥本身，而不是对话链路。

## 根因判断

首版使用 `SpeechRecognizer + EXTRA_PREFER_OFFLINE=true + FREE_FORM + 900ms minimum length`。

很多 Android 设备虽然有 `SpeechRecognizer` 服务，但没有本机 zh-CN 离线语言包，短词“小智”又太短，容易直接返回 `no_match` 或不给 partial/final 候选。因此麦克风亮了，但没有可匹配文本。

## 修复内容

- 保留前台唤醒桥，不碰后台服务。
- 保留“关键词命中才触发”的 py-xiaozhi 风格，不恢复能量 VAD 插话。
- 从长句 `LANGUAGE_MODEL_FREE_FORM` 改为短查询更友好的 `LANGUAGE_MODEL_WEB_SEARCH`。
- 缩短短词识别静音窗口：minimum 220ms，complete silence 360ms。
- 离线优先，但连续 2 次 no_match 后自动切到系统混合识别回退。
- 增加 session watchdog，避免 SpeechRecognizer 会话挂住不返回。
- 增加 partial/final 候选日志：`唤醒词候选[partial/final]：...`。
- 扩展“小智”的常见 ASR 误识别别名。

## 验收

1. 连接成功后进入待命。
2. 点“开唤醒”。
3. 调试日志出现：`前台唤醒词监听中：小智`。
4. 喊“小智”。
5. 观察调试日志是否出现：
   - `唤醒词检测到声音，等待识别候选`
   - `唤醒词候选[partial/final]：...`
   - `唤醒词命中：...`
6. 命中后应自动进入当前语音模式的聆听流程。
7. TTS 播放中喊“小智”应打断 TTS 并进入新一轮聆听。

## 说明

这仍不是最终离线 KWS 模型，只是 12A 的前台识别桥修复。真正的稳定离线唤醒词后端建议在 12B/12C 引入 sherpa-onnx KWS 或其他本地 KWS 引擎。

建议提交信息：

```text
fix foreground wake word recognition bridge
```
