# Phase 1.1 + Phase 2A 交付报告

## 本次目标

本次交付包含两个小阶段：

1. Phase 1.1：修复参数设置页顶部返回按钮，改为更标准的 TopAppBar：左侧返回箭头，标题为“参数设置”。
2. Phase 2A：接入配置持久化和设备身份生成，为后续 OTA、激活、WebSocket 连接提供真实基础数据。

## 创建或修改的文件

```text
app/build.gradle.kts                                      # 修改：新增 DataStore / Coroutines 依赖
gradle/libs.versions.toml                                # 修改：新增 DataStore / Coroutines 版本和库别名

app/src/main/java/com/er1cmo/xiaozhiandroid/
├── data/
│   ├── config/
│   │   ├── AppConfig.kt                                 # 新增：持久化配置模型
│   │   ├── ConfigKeys.kt                                # 新增：DataStore Preferences keys
│   │   └── ConfigRepository.kt                          # 新增：DataStore 读写仓库
│   └── identity/
│       ├── DeviceIdentity.kt                            # 新增：设备身份模型
│       └── DeviceIdentityManager.kt                     # 新增：首次生成并持久化设备身份
├── domain/
│   └── ConversationUiState.kt                           # 修改：增加配置和设备身份字段
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt                             # 修改：创建 ConfigRepository / DeviceIdentityManager 并初始化
│   ├── main/
│   │   └── MainViewModel.kt                             # 修改：从 DataStore 读取真实配置并更新 UI
│   └── settings/
│       └── SettingsScreen.kt                            # 修改：TopAppBar + 展示真实配置/身份

docs/phase2a_delivery_report.md                          # 新增：本交付说明
```

## 已实现能力

- 首次启动会自动准备默认配置。
- 首次启动会生成并保存 `client_id`、`device_id`、`serial_number`、`hmac_key`。
- `device_id` 使用 `02:xx:xx:xx:xx:xx` 伪 MAC 形式，不读取真实手机 MAC。
- 重启 App 后，设备身份会从 DataStore 读取并保持不变。
- 主界面调试面板展示真实设备 ID 和 Client ID。
- 参数设置页展示真实客户端 ID、设备 ID、序列号、HMAC 密钥状态、激活版本、OTA URL、授权 URL、WebSocket 配置状态。
- HMAC 密钥和 token 不在 UI 中明文展示。
- 参数设置页顶部改为标准 TopAppBar，左侧返回箭头，标题“参数设置”。

## 暂未实现

- 未接入 OTA 网络请求。
- 未接入激活验证码和 `/activate` 轮询。
- 未接入 WebSocket hello。
- 未接入文本消息真实发送。
- 未接入音频、Opus、TTS 播放、MCP。

## 建议验收

1. 覆盖文件后等待 Gradle Sync 完成。
2. 运行 App，确认不再出现编译错误。
3. 首次启动后，调试面板中的设备 ID 和 Client ID 不再显示“未生成”。
4. 进入参数设置页，确认顶部为左侧返回箭头 + 标题“参数设置”。
5. 参数设置页能显示客户端 ID、设备 ID、序列号、HMAC 密钥状态、OTA URL、授权 URL、激活版本。
6. 关闭并重新运行 App，确认客户端 ID 和设备 ID 保持不变。
7. 点击“连接入口”，确认日志提示下一步接入 OTA / 激活，而不是执行假 WebSocket。

## 建议提交信息

```bash
git commit -m "add config storage and device identity"
```
