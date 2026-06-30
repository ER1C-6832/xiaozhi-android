# Phase 8B 交付报告：Android MCP 协议层

## 目标

在 Phase 8A 完成 App Core 重构后，接入 Android 本机 MCP 协议层，为后续 8C 的 Android 本机工具能力做基础。

本阶段不做高风险系统权限工具，只先打通：

- `type=mcp` 协议识别
- JSON-RPC 2.0 请求解析
- `initialize`
- `tools/list`
- `tools/call`
- result / error 响应
- MCP 响应通过 WebSocket `type=mcp` 包装回传

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/
├── core/
│   └── AppController.kt
├── network/
│   ├── XiaozhiMessage.kt
│   └── XiaozhiWebSocketClient.kt
├── protocol/
│   └── XiaozhiProtocolClient.kt
└── mcp/
    ├── AndroidMcpServer.kt
    ├── McpTool.kt
    ├── McpToolRegistry.kt
    └── tools/
        ├── AndroidPingTool.kt
        └── AndroidEchoTool.kt
```

## 新增能力

### 1. MCP 消息包装发送

新增：

```kotlin
XiaozhiMessage.mcp(sessionId, payload)
XiaozhiWebSocketClient.sendMcpMessage(payload)
XiaozhiProtocolClient.sendMcpMessage(payload)
```

发送格式与 py-xiaozhi 对齐：

```json
{
  "session_id": "...",
  "type": "mcp",
  "payload": {
    "jsonrpc": "2.0",
    "id": 1,
    "result": {}
  }
}
```

### 2. AndroidMcpServer

新增 Android 本机 MCP Server：

- 校验 JSON-RPC 2.0
- 忽略 `notifications/*`
- 支持 `initialize`
- 支持 `tools/list`
- 支持 `tools/call`
- 支持 JSON-RPC error

### 3. McpTool / McpToolRegistry

新增本机工具抽象：

```kotlin
interface McpTool {
    val name: String
    val description: String
    val inputSchema: JSONObject
    fun call(arguments: JSONObject): McpToolCallResult
}
```

后续 8C 的设备信息、电量、网络、音量、打开 App 等工具会基于这个接口扩展。

### 4. 第一批安全验证工具

本阶段只加入两个低风险调试工具，确保协议链路能验收：

```text
android.ping
android.echo
```

它们不涉及系统权限，不修改设备状态。

### 5. AppController 接管 MCP 路由

`AppController.publishProtocolEvent()` 现在会识别：

```text
type=mcp
```

并调用 `AndroidMcpServer` 处理请求，MainViewModel 不需要直接知道 MCP 协议细节。

## 验收建议

1. App 正常启动
2. 连接入口正常
3. 文本对话正常
4. 语音上行 / TTS / 打断正常
5. 观察服务端是否触发 MCP initialize / tools/list
6. 如果服务端触发 tools/list，应能返回 `android.ping` 和 `android.echo`
7. 如果服务端触发 tools/call android.ping，应返回 `pong from xiaozhi-android`
8. 如果服务端触发未知工具，应返回 JSON-RPC error

## 不包含内容

这些放到 Phase 8C / Phase 9：

- 电量工具
- 网络状态工具
- 音量工具
- 打开 App 工具
- 手电筒 / 亮度等权限工具
- 设置页 tools/list 真实预览
- 工具调用卡片 UI

## 是否需要删除文件

不需要删除文件，直接覆盖即可。

## 建议提交信息

```text
add android mcp protocol layer
```
