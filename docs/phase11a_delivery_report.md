# Phase 11A Delivery Report: AUTO_STOP VAD

## Scope

Phase 11A introduces the first safe step from press-to-talk toward natural conversation:

- MANUAL remains the default behavior: press and hold to speak, release to stop.
- AUTO_STOP is added: tap once to start recording, local energy-based VAD detects the end of speech and sends stop automatically.
- REALTIME remains a declared future mode for Phase 11C; Phase 11A does not attempt full-duplex capture during TTS.

## Changed files

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/domain/ConversationUiState.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioEngine.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainScreen.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainViewModel.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/BottomControlBar.kt
```

## Behavior

### MANUAL

- The primary voice button still uses press-and-hold.
- `listen/start` is sent when pressing.
- Local recording and Opus uplink run while the button is held.
- `listen/stop` is sent when released.

### AUTO_STOP

- The mode button switches from MANUAL to AUTO.
- The primary voice button becomes a tap button.
- Tapping starts recording and Opus uplink.
- The local VAD waits for speech onset, then waits for about 900 ms of trailing silence.
- When trailing silence is detected, recording stops and `listen/stop` is sent automatically.
- VAD status is shown under the voice buttons and logged in debug logs.

## VAD parameters

```text
speechPeakThreshold = 1200
speechRmsThreshold = 260
silencePeakThreshold = 700
silenceRmsThreshold = 180
minSpeechFrames = 5
warmupMs = 260
minRecordingMs = 700
trailingSilenceMs = 900
maxRecordingMs = 18000
```

These values are intentionally conservative. The first version uses peak/RMS energy only; WebRTC VAD and Android AEC are reserved for Phase 11B.

## Validation checklist

1. MANUAL mode still works exactly like before.
2. Tap the mode button until it shows AUTO.
3. Tap the primary voice button once.
4. Speak a short sentence and stop.
5. The app should automatically stop after the trailing silence and wait for STT/TTS.
6. Tap again for another AUTO_STOP turn.
7. In AUTO mode, tapping the button while listening should stop manually.
8. Abort should still stop recording/playback and send `abort` when connected.

## Notes

Full duplex is still the target for Phase 11. Phase 11A only establishes local endpointing and reliable AUTO_STOP. Phase 11B should add AEC/WebRTC-related suppression so that Phase 11C full-duplex does not self-trigger on TTS playback.
