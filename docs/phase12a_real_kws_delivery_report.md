# Phase 12A Real KWS delivery report

本包废弃上一版 Android `SpeechRecognizer` 前台桥，改为真正的 sherpa-onnx 本地 KWS 架构。

## 为什么废弃 SpeechRecognizer

上一版失败现象：

- 麦克风亮，但没有候选结果。
- 反复 network / language error。
- ROM 会反复响系统语音识别提示音。

原因：`SpeechRecognizer` 是系统/云/ROM ASR 服务，不是持续离线唤醒词。它依赖系统识别服务、中文离线包、网络和厂商实现，无法复刻 py-xiaozhi 的 KWS 能力。

## 本包改动

- `ForegroundWakeWordController.kt` 改为本地 KWS 控制器。
- 新增 `wakeword/` 模块：
  - `WakeWordConfig.kt`
  - `WakeWordEvent.kt`
  - `SherpaWakeWordDetector.kt`
  - `SherpaWakeWordEngine.kt`
- 新增 sherpa-onnx Kotlin API 最小封装：
  - `com/k2fsa/sherpa/onnx/FeatureConfig.kt`
  - `QnnConfig.kt`
  - `OnlineModelConfig.kt`
  - `OnlineStream.kt`
  - `KeywordSpotter.kt`
- 新增模型与 JNI 放置目录。
- 新增模型下载脚本：
  - `tools/prepare_sherpa_kws_assets.sh`
  - `tools/prepare_sherpa_kws_assets.ps1`

## 需要手动准备的内容

当前 ChatGPT 容器无法直接联网下载 GitHub 大文件，也不能替你编译 sherpa-onnx Android native library。因此本 overlay 不内置大模型和 `.so`。

你需要在本地仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File tools/prepare_sherpa_kws_assets.ps1
```

该脚本会下载并放置模型文件。

还需要准备原生库：

```text
app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
```

完整来源：克隆 `k2-fsa/sherpa-onnx`，按官方 `scripts/apk/build-apk-kws.sh` 构建 Android KWS，然后从 `build-android-arm64-v8a/install/lib/` 复制 `libsherpa-onnx-jni.so`。

## 验收

1. 准备模型和 `libsherpa-onnx-jni.so`。
2. 重新安装 App。
3. 连接到待命。
4. 点开唤醒。
5. 日志应显示：

```text
本地唤醒词模型初始化中：小智
本地唤醒词麦克风已打开
本地唤醒词监听中：小智
```

6. 说“小智”。
7. 日志应显示：

```text
本地唤醒词命中：...
前台唤醒词触发：...
```

## 注意

- 这不是后台常驻。
- 这不是系统 SpeechRecognizer。
- 不会再出现 SpeechRecognizer 的 network/language 错误和系统提示音。
- 若缺模型或缺 `.so`，App 会给明确日志，不会假装唤醒词已生效。

建议提交信息：

```text
replace speech recognizer wakeword with sherpa kws backend
```
