# Phase 12A KWS keyword variants

## What changed

This overlay keeps the 2025 sherpa-onnx KWS backend and only expands the XiaoZhi keyword grammar.

Changed files:

- `app/src/main/assets/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/keywords_xiaozhi.txt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/wakeword/SherpaWakeWordDetector.kt`
- `tools/prepare_sherpa_kws_assets.ps1`

## Keyword list

The keyword list now includes:

- 小智
- 小知 style tone variants
- zh / z flat-retroflex variants
- 小智小智 repeated wake phrase
- 小智同学 phrase variants

All short variants are normalized to the canonical `@小智` label so downstream state handling stays simple.

## PowerShell display note

If Notepad and GitHub show the text correctly but Windows PowerShell `Get-Content` displays mojibake, it is usually a console output codepage issue, not a file-byte issue.

For PowerShell checks, use:

```powershell
chcp 65001
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Get-Content -Encoding UTF8 ".\app\src\main\assets\sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20\keywords_xiaozhi.txt"
```

## Next validation

Run on a real device and verify these logs:

- `本地唤醒词模型初始化中：小智`
- `本地唤醒词麦克风已打开`
- `本地唤醒词监听中：小智`
- `本地唤醒词命中：...`

If the model starts but does not trigger, tune `keywordsScore`, `keywordsThreshold`, and `numTrailingBlanks` next.
