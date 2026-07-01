# Phase 11C streaming echo gate fix

## Problem

The previous streaming dialog still allowed local/remote false barge-in while TTS was playing. Even with Android AEC/NS enabled, the phone speaker can leak enough residual echo into the microphone to be treated as user speech.

Observed behavior:

- TTS is frequently interrupted even when the user says nothing.
- UI repeatedly jumps between Listening and Speaking.
- The TTS playback queue is cleared multiple times within one answer.

## Fix

This patch changes the streaming audio policy from local energy barge-in to conservative echo-gated streaming:

- Streaming mode still keeps the microphone AudioRecord open.
- Streaming mode no longer enables local energy VAD.
- While TTS playback is active, microphone frames are read and measured but are not encoded/sent upstream.
- When playback naturally ends, microphone uplink resumes automatically.
- Natural dialog AUTO_STOP VAD is unchanged and keeps the 900ms tail silence window.
- Manual interrupt remains available through the UI button.

## Files

- `app/src/main/java/com/er1cmo/xiaozhiandroid/audio/AudioEngine.kt`

## Expected behavior

- Assistant TTS should no longer be repeatedly interrupted by its own speaker output.
- UI should stop rapidly oscillating between Listening and Speaking during assistant replies.
- Logs should show a line similar to `流式对话回声门控已启用` when streaming mode starts.
- During TTS playback, logs/status may show that mic uplink is temporarily paused to avoid echo-triggered barge-in.

## Follow-up

If automatic spoken barge-in is required later, it should not be driven by simple local energy thresholds. It should be restored only after adding a stronger validator, such as server-confirmed user speech, echo correlation/reference cancellation, or a stricter wake/interruption model.
