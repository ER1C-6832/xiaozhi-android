# Phase 4 交付报告：语音上行

## 本阶段目标

用 Android 原生音频能力完成小智 WebSocket 协议的语音上行最小闭环：

```text
按住后说话
→ 请求 RECORD_AUDIO 权限
→ AudioRecord 采集 PCM16
→ 16kHz / mono / 20ms 分帧
→ Android MediaCodec Opus 编码
→ WebSocket binary frame 上传
→ 松开发送 listen/stop 并停止录音
```

## 创建文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/
├── AudioConstants.kt
├── AudioEngine.kt
├── AudioRecorder.kt
├── OpusEncoder.kt
├── OpusEncodingException.kt
└── PcmFrame.kt
```

## 修改文件

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/er1cmo/xiaozhiandroid/domain/ConversationUiState.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/network/XiaozhiWebSocketClient.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/navigation/AppNavigation.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainScreen.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/DebugLogPanel.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/settings/SettingsScreen.kt
```

## 已实现能力

```text
- AndroidManifest 增加 RECORD_AUDIO 权限
- 首次按住说话时请求麦克风权限
- 未连接 WebSocket 时按住说话不会启动录音上传
- 已连接 WebSocket 时按下按钮发送 listen/start/manual
- 使用 AudioRecord 采集 PCM16 音频
- 固定 16000Hz、单声道、20ms、640B PCM 分帧
- 使用 Android MediaCodec 创建 audio/opus 编码器
- 将 Opus packet 作为 WebSocket binary frame 上传
- 松开按钮后停止录音并发送 listen/stop
- 打断时停止本地录音并发送 abort/user_interruption
- 调试面板和设置页显示音频上行状态
```

## 暂未实现

```text
- TTS 下行 Opus 解码播放
- AudioTrack 播放
- AEC / 回声消除
- 蓝牙耳机兼容
- 后台录音 / 前台服务
- 自动对话模式
- JNI/libopus 编码器替换
```

## 重要说明

当前 Opus 编码器使用 Android 系统 MediaCodec：

```text
MediaCodec.createEncoderByType("audio/opus")
```

大多数新系统镜像和真机应可用。如果某些设备没有系统 Opus 编码器，App 会在调试日志里提示“当前设备没有可用的系统 Opus 编码器”。后续可替换为 `libopus + JNI/NDK`，但本阶段先避免引入 NDK 复杂度。

## 验收步骤

```text
1. 覆盖代码并 Gradle Sync
2. 运行 App
3. 点击“连接入口”，确认 WebSocket 已连接
4. 长按“按住后说话”
5. 首次弹出麦克风权限时选择允许
6. 再次长按“按住后说话”并说话
7. 观察日志出现 listen/start/manual、音频上行启动、已发送 Opus 音频帧
8. 松开按钮，观察 listen/stop 和音频上行停止
9. 服务端应返回 stt / tts JSON
10. App 不应卡死或崩溃
```

## 建议提交信息

add microphone audio uplink
