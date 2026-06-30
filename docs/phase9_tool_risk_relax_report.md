# Phase 9 MCP 工具风险等级调整

## 目标

当前 Android 本机工具只涉及读取状态、打开系统入口、调整普通系统控制项，不涉及删除软件、删除/写入用户文件、卸载应用、支付、发送消息等破坏性操作。因此不应全部标为高风险，也不应默认要求 `confirmed=true`。

## 修改

- `McpToolCatalog.highRiskToolNames` 改为空集合。
- 当前所有工具在设置页显示为“普通工具”。
- 当前 tools/list 不再给普通工具注入 `confirmed` 参数。
- `AndroidMcpServer` 中高风险二次确认机制保留。
- `decorateToolJson()` 中高风险 schema 注入逻辑保留，未来新增真正高风险工具时只需把工具名加入 `highRiskToolNames`。

## 当前普通工具

- android.ping
- android.echo
- android.get_device_info
- android.get_battery_status
- android.get_network_status
- android.get_current_time
- android.get_volume
- android.set_volume
- android.get_ringer_mode
- android.set_ringer_mode
- android.open_app
- android.get_brightness
- android.set_brightness
- android.set_flashlight
- android.set_clipboard_text
- android.open_settings

## 真正高风险工具的后续标准

后续如果新增以下类型工具，再加入高风险集合并要求二次确认：

- 删除文件 / 写入文件 / 覆盖文件
- 卸载或禁用应用
- 发送短信、邮件、社交消息
- 拨打电话
- 支付、下单、转账
- 修改账号、隐私、安全配置
- 持续定位、录音、拍照等敏感采集动作

## 验收

1. 设置页 MCP 工具预览不再把当前工具标为高风险。
2. 调用 `android.set_flashlight` 不需要 `confirmed=true`。
3. 调用 `android.open_app` 不需要 `confirmed=true`。
4. 调用 `android.set_volume` 不需要 `confirmed=true`。
5. 高风险二次确认代码仍保留，未来加入工具名即可生效。
