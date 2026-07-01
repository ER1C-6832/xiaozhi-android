# Phase 11C 流式对话命名与插话灵敏度修复

## 修改内容

- 将三种语音模式中文命名调整为：长按对话 / 自然对话 / 流式对话。
- 保留协议层 `mode=realtime`，只调整用户可见文案。
- AUTO_STOP 尾静音判断恢复为 900ms。
- 流式对话的 VAD 起声阈值提高，避免 TTS 外放残留被误判为用户插话。
- TTS 开始后的 2 秒内暂不允许插话打断，用于抑制首段播放回声误触发。
- 插话冷却从 1.2 秒提升到 2 秒。
- 播放中误触发 VAD 时不再强行把 UI 从 Speaking 切到 Listening。

## 覆盖文件

- `app/src/main/java/com/er1cmo/xiaozhiandroid/domain/ConversationUiState.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioEngine.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainScreen.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/BottomControlBar.kt`

## 验收

1. 模式按钮依次显示：长按对话 -> 自然对话 -> 流式对话。
2. 自然对话说完后大约 900ms 静音再 stop。
3. 流式对话第一轮 TTS 回复不应被自己的播放声音疯狂打断。
4. TTS 播放一小段时间后，用户明确插话仍能触发打断。
