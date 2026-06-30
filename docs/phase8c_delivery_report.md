# Phase 8C Delivery Report: Android Native MCP Tools

## Scope

This overlay adds the first usable Android native MCP tool set on top of the Phase 8B protocol layer and 8B-1 observability patch.

## Changed files

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/er1cmo/xiaozhiandroid/core/AppController.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/mcp/McpToolRegistry.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/mcp/tools/
├── AppOpenTool.kt
├── BatteryStatusTool.kt
├── BrightnessTools.kt
├── ClipboardTool.kt
├── DeviceInfoTool.kt
├── FlashlightTool.kt
├── NetworkStatusTool.kt
├── OpenSettingsTool.kt
├── TimeTool.kt
├── ToolJson.kt
└── VolumeTools.kt
```

## Added tools

```text
android.get_device_info
android.get_battery_status
android.get_network_status
android.get_current_time
android.get_volume
android.set_volume
android.get_ringer_mode
android.set_ringer_mode
android.open_app
android.get_brightness
android.set_brightness
android.set_flashlight
android.set_clipboard_text
android.open_settings
```

Existing test tools remain:

```text
android.ping
android.echo
```

## Permissions

Added manifest permissions:

```text
ACCESS_NETWORK_STATE
MODIFY_AUDIO_SETTINGS
CAMERA
FLASHLIGHT
WRITE_SETTINGS
```

Notes:

- Flashlight requires CAMERA runtime permission. If not granted, the tool returns a clear MCP error instead of crashing.
- Brightness write requires Android WRITE_SETTINGS special permission. If not granted, android.set_brightness returns a clear MCP error. Use android.open_settings target=write_settings to open the grant page.
- Some ringer/silent operations can be blocked by system DND / notification policy restrictions. The tool returns a clear MCP error if blocked.

## Validation prompts

```text
请通过 MCP tools/call 调用 android.get_device_info。
请通过 MCP tools/call 调用 android.get_battery_status。
请通过 MCP tools/call 调用 android.get_network_status。
请通过 MCP tools/call 调用 android.set_volume，把 music 音量调到 35%。
请通过 MCP tools/call 调用 android.get_volume。
请通过 MCP tools/call 调用 android.set_clipboard_text，把文本“安卓 MCP 已打通”复制到剪贴板。
请通过 MCP tools/call 调用 android.open_settings，target 为 wifi。
```

## Acceptance checklist

```text
- tools/list includes the new Android tools
- tools/call can invoke read-only tools successfully
- set_volume changes media volume and logs success
- protected tools return explicit MCP errors when permissions are missing
- debug log shows request, tool start, tool finish and response sent
- no sensitive token or websocket URL is printed by these tools
```

## Suggested commit message

```text
add android native mcp tools
```
