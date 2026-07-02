# Phase 12A KWS 2025 model delivery report

本包把 Phase 12A Real KWS 默认模型从 2024 Wenetspeech 模型切换到官方当前 KWS 文档列出的 2025 zh-en 3M 模型：

```text
sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20
```

## 改动

- `WakeWordConfig.MODEL_DIR` 改为 2025 zh-en 模型目录。
- `KeywordSpotter.kt` 的 encoder / decoder / joiner / tokens 路径改为 2025 模型文件名：
  - `encoder-epoch-13-avg-2-chunk-16-left-64.onnx`
  - `decoder-epoch-13-avg-2-chunk-16-left-64.onnx`
  - `joiner-epoch-13-avg-2-chunk-16-left-64.onnx`
  - `tokens.txt`
- `SherpaWakeWordDetector.kt` 校验 2025 模型资产，并使用 `keywords_xiaozhi.txt`。
- 下载脚本切到 2025 模型，并修复 Windows GNU tar 的 `C:` 路径问题：使用 `tar --force-local`。
- 新增 `tools/prepare_sherpa_kws_jni_from_aar.ps1`，用于从官方 AAR 解出 native `.so`。
- 旧 2024 模型目录在脚本执行时会被删除，避免 APK 同时打进两个模型。

## 本包不内置大文件

不内置：

- 2025 模型 `.onnx` 大文件
- `libsherpa-onnx-jni.so` native 库

需要本地执行：

```powershell
powershell -ExecutionPolicy Bypass -File tools/prepare_sherpa_kws_assets.ps1
powershell -ExecutionPolicy Bypass -File tools/prepare_sherpa_kws_jni_from_aar.ps1
```

## 预期资产目录

```text
app/src/main/assets/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/
├── encoder-epoch-13-avg-2-chunk-16-left-64.onnx
├── decoder-epoch-13-avg-2-chunk-16-left-64.onnx
├── joiner-epoch-13-avg-2-chunk-16-left-64.onnx
├── tokens.txt
└── keywords_xiaozhi.txt
```

## 验收

1. 覆盖本包。
2. 运行模型脚本。
3. 运行 JNI AAR 解包脚本。
4. 重新安装 App。
5. 连接到待命。
6. 打开唤醒词。
7. 日志应显示：

```text
本地唤醒词模型初始化中：小智
本地唤醒词麦克风已打开
本地唤醒词监听中：小智
```

8. 说“小智”，应显示：

```text
本地唤醒词命中：...
前台唤醒词触发：...
```

## 建议提交信息

```text
switch wakeword kws to 2025 zh en model
```
