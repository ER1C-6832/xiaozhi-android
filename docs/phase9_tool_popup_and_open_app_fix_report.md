# Phase 9 修复包：工具调用弹窗与打开 App 优化

## 修改目标

- 工具调用卡片不再常驻主界面。
- 主界面只显示最近一次工具调用的悬浮弹出卡片，工具完成后几秒自动隐藏。
- 设置页保留完整 MCP 工具列表与最近工具调用历史。
- 优化 android.open_app，在 Android 11+ 包可见性限制下更稳定打开 Chrome、Gmail 等常见应用。

## 修改文件

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/MainScreen.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/main/components/ToolCallPanel.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/er1cmo/xiaozhiandroid/mcp/tools/AppOpenTool.kt`

## 说明

### 工具调用卡片

主界面工具调用 UI 改为悬浮卡片：

- 工具执行中保持显示。
- 工具成功、失败或被二次确认拦截后，约 4.5 秒自动隐藏。
- 不再占据主界面常驻区域。
- 设置页新增“最近工具调用历史”，可长期查看参数、结果、耗时、成功/失败。

### 打开 App

`android.open_app` 支持：

- `packageName`
- `appName`
- 常见别名：Chrome、Gmail、Play Store、YouTube、Maps、微信、QQ、支付宝等

同时 Manifest 增加 `<queries>`：

- 查询 launcher app 的 intent 可见性。
- 声明常见包名，避免 Android 11+ 下 `getLaunchIntentForPackage()` 返回 null。

## 验收建议

1. 调用 `android.get_battery_status`，主界面出现悬浮工具卡片，几秒后自动消失。
2. 进入设置页，能看到最近工具调用历史。
3. 调用 `android.open_app`，传 `appName=Chrome`。
4. 调用 `android.open_app`，传 `appName=Gmail`。
5. 调用 `android.open_app`，传 `packageName=com.android.chrome` 或 `packageName=com.google.android.gm`。

## 建议提交信息

`make tool cards transient and improve app opening`
