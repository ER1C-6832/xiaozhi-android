# Phase 9 Delivery Report: MCP 工具扩展与工具卡片 UI

## 目标

Phase 8 已完成 Android 本机 MCP 协议层与第一批工具。本阶段补齐工具体验层：

- 主界面展示工具调用卡片
- 显示工具名、参数、执行结果、耗时、成功/失败
- 设置页提供真实 tools/list 预览
- 工具权限说明
- 高风险工具二次确认

## 修改文件

```text
app/src/main/java/com/er1cmo/xiaozhiandroid/
├── core/
│   └── AppController.kt
├── domain/
│   └── ConversationUiState.kt
├── mcp/
│   ├── AndroidMcpServer.kt
│   ├── McpToolCatalog.kt
│   └── McpToolRegistry.kt
└── ui/
    ├── main/
    │   ├── MainScreen.kt
    │   └── components/
    │       └── ToolCallPanel.kt
    └── settings/
        └── SettingsScreen.kt
```

## 功能说明

### 1. 主界面工具调用卡片

新增 `ToolCallPanel`，展示最近 8 次 tools/call：

- tool name
- request id
- 调用参数
- 工具结果
- 成功 / 失败 / 执行中 / 待确认
- 调用耗时

### 2. App Core 记录工具生命周期

`AppController` 在 MCP 请求流转中更新 UI 状态：

- 收到 tools/call 时创建工具卡片
- 工具开始时显示执行中
- 工具完成时更新结果、耗时和状态
- MCP response 发送后同步 result preview

### 3. 设置页 tools/list 真实预览

新增 MCP 本机工具设置分组：

- 工具数量
- 最近 MCP 状态
- 工具名称
- 工具分类
- 风险等级
- 权限说明

### 4. 高风险工具二次确认

以下工具默认需要二次确认：

```text
android.set_volume
android.set_ringer_mode
android.open_app
android.set_brightness
android.set_flashlight
android.set_clipboard_text
android.open_settings
```

首次调用时如果没有 `confirmed=true`，Android 端会返回 `requires_confirmation`，不会直接执行。用户确认后，服务端可再次以 `confirmed=true` 调用。

### 5. tools/list schema 增强

高风险工具的 tools/list schema 会自动增加：

```json
{
  "confirmed": {
    "type": "boolean",
    "description": "高风险 Android 本机工具二次确认。用户明确确认后传 true。"
  }
}
```

并附加 Android 风险标记：

```json
{
  "x-android-risk": "high",
  "x-android-confirmation": "required"
}
```

## 验收建议

1. 连接成功后让服务端调用 `android.get_battery_status`，主界面应出现成功卡片。
2. 调用 `android.echo`，参数和结果应在工具卡片可见。
3. 调用 `android.set_volume` 且不传 `confirmed=true`，应显示“待确认”，且不执行。
4. 用户确认后再次调用 `android.set_volume confirmed=true`，应执行并显示成功或失败。
5. 设置页 MCP 分组应显示工具数量、工具列表、风险和权限说明。

## 建议提交信息

```text
add mcp tool cards and tool preview
```
