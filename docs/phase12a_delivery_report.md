# Phase 12A Delivery Report: Foreground Offline Wake Word Bridge

## Scope

This package starts Phase 12A with a foreground wake-word trigger. It deliberately does not add background foreground-service wakeup yet.

Implemented:

- Foreground wake-word switch on the main top bar.
- Default keyword: `小智`.
- Android platform `SpeechRecognizer` backend with `EXTRA_PREFER_OFFLINE=true`.
- Wake-word detection is active only when the app is foreground and the normal conversation uplink is not using the microphone.
- Connected state: wake word starts the current voice mode's listening flow.
- Speaking state: wake word interrupts TTS, sends abort through the existing controller, clears playback, then starts listening.
- Listening / Thinking / Connecting / Error states ignore repeated wake triggers.
- 1.5s cooldown after detection.
- Short pause and stale-recognition cancellation after detection, following the py-xiaozhi wake-word pattern.
- Manifest query for `android.speech.RecognitionService` on Android 11+ package visibility.

## Why this route

Phase 11 proved that energy-based VAD is not a safe barge-in judge during TTS playback. This package follows the py-xiaozhi-style model: use a high-confidence wake-word trigger for interruption instead of raw mic energy.

## Important behavior

This first package uses Android's system speech recognizer in offline-preferred mode, so behavior depends on the device having an on-device recognizer / language pack. If no recognizer is available, it logs an unavailable status instead of crashing.

A true sherpa-onnx KWS backend can replace the backend in Phase 12B without changing the higher-level conversation-state handling.

## Verification

1. Build and install.
2. Connect the assistant.
3. Tap `开唤醒` in the top bar and allow microphone permission if prompted.
4. In Connected / 待命 state, say `小智`.
5. The app should enter the current voice listening mode.
6. While TTS is speaking and normal conversation uplink is not active, say `小智`.
7. TTS should stop, abort should be sent, and the app should enter listening.
8. Repeated `小智` within the cooldown window should not repeatedly trigger.

## Limitations intentionally left for 12B/12C

- Not a background always-on wake word service.
- Not a notification foreground service.
- Not a custom trained wake word model yet.
- In streaming mode, if the normal AudioRecord uplink owns the microphone, the system SpeechRecognizer backend is paused to avoid microphone contention.
- This package does not restore arbitrary free-speech barge-in; that remains a later echo-aware research phase.

## Suggested commit message

```text
add foreground wake word trigger
```
