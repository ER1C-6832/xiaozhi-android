# Phase 8C Brightness V2 Fix

## 问题

部分 Android / OEM 系统中，`Settings.System.SCREEN_BRIGHTNESS` 只是兼容 raw int 值，不等于系统亮度滑杆的线性百分比。之前用 `raw / 255` 计算百分比会导致：

- 滑杆大半时报告 27%
- 滑杆一半时报告 8%
- 滑杆约 10% 时报告 1%

这属于错误换算，不能作为用户可理解的亮度百分比。

## 修复

- `android.get_brightness` 优先读取 `screen_brightness_float`，该值通常更接近系统亮度滑杆。
- 其次尝试读取 `Display.BrightnessInfo`。
- `rawSystemBrightness` 只保留为诊断字段，不再作为可靠百分比来源。
- 如果无法可靠获取百分比，则 `percent=null`，避免乱报。
- `android.set_brightness` 优先写入 `screen_brightness_float`，同时保留 raw int 兼容写入。

## 验收建议

1. 手动把亮度调到约 80%，调用 `android.get_brightness`，看 `percentSource` 和 `percent`。
2. 手动把亮度调到约 50%，调用 `android.get_brightness`，不应再报 8%。
3. 手动把亮度调到约 10%，调用 `android.get_brightness`，不应再把 raw int 误当线性百分比。
4. 调用 `android.set_brightness percent=50`，再调用 `android.get_brightness`。

如果某个厂商系统不开放可靠 float/display 亮度，工具会返回 `percent=null`，而不是继续乱报。
