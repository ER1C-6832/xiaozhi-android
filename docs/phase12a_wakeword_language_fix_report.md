# Phase 12A foreground wake-word language fallback fix

## Problem

On a real Android 12 device, the foreground wake-word bridge showed:

```text
前台唤醒词监听中：小智
唤醒词识别错误：unknown_12，请稍后重试
```

`unknown_12` corresponds to a language-not-supported style recognizer failure on Android SpeechRecognizer implementations. The previous 12A bridge forced `zh-CN` language extras and short-query recognition. Some system recognizers reject that session before returning any partial/final candidates.

## Changes

Only `ForegroundWakeWordController.kt` is changed.

- Stop treating error 12/13 as generic retryable errors.
- Label them as:
  - `language_not_supported`
  - `language_unavailable`
- Remove the incorrect `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE` extra.
- Do not force offline recognition for the SpeechRecognizer bridge.
- Create multiple recognizer profiles and fall through them automatically:
  1. `zh-CN` short-query mode
  2. `zh-CN` free-form mode
  3. broad `zh` short-query mode
  4. device default language, if different
  5. system default with no language extras
- Recreate SpeechRecognizer after language/busy errors.
- Keep candidate logs so real-device recognizer output is visible.

## Expected logs

```text
前台唤醒词监听中：小智（中文短词）
唤醒词识别配置切换为：中文听写（语言不支持：language_not_supported）
唤醒词切换语言配置后重试
...
前台唤醒词监听中：小智（系统默认无语言约束）
唤醒词候选[partial]：小智
唤醒词命中：小智
```

If it reaches `系统默认无语言约束` and still keeps returning language errors, Android's platform SpeechRecognizer is not viable as a wake-word bridge on that ROM. The next step should be replacing the backend with a real local KWS engine such as sherpa-onnx instead of continuing to patch SpeechRecognizer.

## Verification

1. Install on real device.
2. Connect until app is in `待命`.
3. Turn on foreground wake word.
4. Say `小智`.
5. Check whether logs show candidate text and wake-word hit.

## Suggested commit message

```text
fix wake word recognizer language fallback
```
