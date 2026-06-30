# Phase 8A-3 compile fix 交付报告

## 问题

Android Studio 编译时报错：

```text
e: .../protocol/XiaozhiProtocolClient.kt:50:52 Unresolved reference 'Disconnected'.
```

## 原因

Phase 8A-3 中 `ProtocolEvent` 已经把断开事件统一命名为：

```kotlin
ProtocolEvent.Closed
```

但 `XiaozhiProtocolClient.kt` 里 `NetworkState.toProtocolEventOrNull()` 仍然引用旧名称：

```kotlin
ProtocolEvent.Disconnected
```

因此产生未解析引用。

## 修改内容

修改文件：

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/protocol/XiaozhiProtocolClient.kt
```

修复为：

```kotlin
NetworkState.Disconnected -> ProtocolEvent.Closed(label)
```

## 验收建议

覆盖后重新 Sync / Run，重点确认：

```text
1. 编译错误消失
2. App 可以启动
3. 连接入口正常
4. 文本对话正常
5. 语音上行和 TTS 播放正常
```

## 建议提交信息

```text
fix protocol closed event reference
```
