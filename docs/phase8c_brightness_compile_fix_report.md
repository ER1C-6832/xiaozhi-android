# Phase 8C Brightness Compile Fix

## 问题

上一版 BrightnessTools 引入了 Display/BrightnessInfo 相关 API，在当前项目 compileSdk / API 兼容层下出现：

```text
e: BrightnessTools.kt:93:28 Unresolved reference 'brightnessInfo'
```

## 修复

- 移除 BrightnessInfo 依赖，避免 compileSdk / API 差异导致编译失败。
- get_brightness 只使用稳定可编译的 Settings.System 读取：
  - 优先读取字符串 key `screen_brightness_float`。
  - `SCREEN_BRIGHTNESS` raw int 只作为诊断字段。
  - 如果无法读取可靠 float，则 `percent=null`，不再用 raw/255 猜测百分比。
- set_brightness 写入：
  - `SCREEN_BRIGHTNESS_MODE=MANUAL`
  - `screen_brightness_float`
  - `SCREEN_BRIGHTNESS` raw 兼容值

## 验收

1. Debug 编译通过。
2. 调用 `android.get_brightness`。
3. 如果返回 `percentReliable=true`，百分比应来自 `settings_system_float`。
4. 如果系统不提供可靠 float，返回 `percent=null`，并在 note 中说明不乱报。
5. 调用 `android.set_brightness percent=50 mode=manual` 后，再调用 get_brightness 检查实际保存值。
