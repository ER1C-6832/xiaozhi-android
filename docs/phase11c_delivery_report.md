# Phase 11C Delivery Report: REALTIME Full Duplex

## Scope

This overlay promotes Phase 11 from MANUAL / AUTO_STOP to a first landed REALTIME full-duplex mode.

Phase 11B Android AEC / NS is expected to be mostly invisible in the UI. The log below is normal and healthy:

```text
Android 音频处理：source=VOICE_COMMUNICATION, session=..., AEC=on, NS=on, AGC=unavailable
```

AGC being unavailable is not a blocker. The important parts for 11C are that Android accepted `VOICE_COMMUNICATION`, AEC is on, and NS is on.

## Changed files

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioEngine.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioRecorder.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/domain/ConversationUiState.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/network/XiaozhiMessage.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/network/XiaozhiWebSocketClient.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/protocol/XiaozhiProtocolClient.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainScreen.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/BottomControlBar.kt
```

## Behavior

- Voice mode cycles through MANUAL -> AUTO_STOP -> REALTIME -> MANUAL.
- REALTIME sends `listen/start` with `mode=realtime`.
- REALTIME keeps microphone capture active while TTS is playing.
- Android AEC / NS stay attached to the recording session.
- Local VAD is reused as a speech detector in REALTIME mode.
- When user speech is detected during TTS playback, the app stops local playback, sends abort, and keeps recording.
- TTS stop returns to Listening in REALTIME instead of Connected, so the assistant keeps listening.
- Manual stop in REALTIME sends `listen/stop` and leaves full-duplex mode.

## Suggested validation

1. Connect.
2. Tap mode until `REALTIME` appears.
3. Tap `全双工`.
4. Speak once and wait for TTS.
5. While TTS is speaking, interrupt it with a new sentence.
6. Verify log contains `REALTIME 插话检测` and the mic remains active.
7. Tap `停止收音` to leave REALTIME.

## Known limits

- This is the first landed full-duplex mode. Effect depends heavily on the device AEC implementation.
- If a device reports AEC unavailable, full-duplex may still work with headphones but will be risky with speaker playback.
- Server-side realtime endpointing must support `mode=realtime`; if not, the client still records continuously but server behavior may degrade.
