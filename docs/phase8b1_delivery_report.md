# Phase 8B-1 交付报告：MCP 可观测性修复

## 本次目标

修复 Phase 8B 后 Android MCP 工具调用“看不出来是否真的执行”的问题。

8B 已经具备 `type=mcp`、`initialize`、`tools/list`、`tools/call`、JSON-RPC result/error 回传能力，但 MCP 内部日志只进入 `AppEventBus`，主界面调试日志没有稳定显示工具调用过程。本包把 MCP 请求、工具开始、工具完成、响应发送结果直接写入 `AppStateStore.debugLogs`，方便现场判断是否发生真实 tools/call。

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/core/AppController.kt
```

## 主要改动

1. 收到 `type=mcp` 时写入调试日志：
   - method
   - id
   - tool name

2. `AndroidMcpServer` 内部 `onLog` 现在会同步写入主界面调试日志。

3. `tools/call` 开始执行时写入：
   - `MCP 工具开始：<toolName> id=<id>`

4. `tools/call` 执行完成时写入：
   - `MCP 工具完成：<toolName> id=<id> success=<true/false> message=<message>`

5. 每次 MCP 响应回发服务端时写入：
   - method
   - id
   - send success
   - error flag

6. MCP 响应内容会写到 `lastServerJson`，显示为：

```json
{
  "type": "mcp",
  "direction": "android_response",
  "payload": { ... }
}
```

这样可以直接看到 Android 端是否真的回了 JSON-RPC result/error。

## 验收建议

开启开发者调试并展开主界面日志，然后输入：

```text
请不要直接回答。请先通过 MCP tools/call 调用 android.ping，并在最终回复中逐字包含工具返回值 pong from xiaozhi-android。
```

或：

```text
请通过 MCP tools/call 调用 android.echo，参数 text 精确设置为 8B_ECHO_73579。最终回复必须逐字包含工具返回值。
```

如果真的触发工具调用，应看到类似日志：

```text
MCP 收到请求：method=tools/call, id=..., tool=android.echo
MCP 请求：method=tools/call, id=...
MCP 工具开始：android.echo id=...
MCP 工具完成：android.echo id=... success=true message=success
MCP 响应已发送：method=tools/call, id=..., success=true, error=false
```

如果只有 `initialize` 或 `tools/list`，但没有 `tools/call`，说明服务端没有真正调用工具，模型后续自然语言说“调用成功”不能作为 MCP 成功依据。

## 是否需要删除文件

不需要。

建议提交信息：improve mcp debug observability
