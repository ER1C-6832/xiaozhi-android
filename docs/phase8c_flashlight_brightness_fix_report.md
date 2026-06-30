# Phase 8C Fix: Flashlight Permission and Brightness Accuracy

## Summary

This patch fixes two MCP tool issues found during 8C validation:

1. `android.set_flashlight` now actively requests the CAMERA runtime permission through a small trampoline Activity when permission is missing.
2. `android.get_brightness` no longer reports an unreliable brightness percentage in automatic brightness mode or on OEM devices where the raw brightness range is not the standard 0..255.
3. `android.set_brightness` now opens the WRITE_SETTINGS special-permission page by default when permission is missing, and returns a clear MCP error instead of pretending the operation succeeded.

## Files

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/er1cmo/xiaozhiandroid/PermissionRequestActivity.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/mcp/tools/FlashlightTool.kt
app/src/main/java/com/er1cmo/xiaozhiandroid/mcp/tools/BrightnessTools.kt
docs/phase8c_flashlight_brightness_fix_report.md
```

## Notes

`CAMERA` is a runtime permission, so the app can request it with a real permission dialog. `WRITE_SETTINGS` is a special Android permission and cannot be requested using the normal runtime permission dialog; the correct behavior is to open the system grant page.

## Suggested commit message

```text
fix flashlight permission and brightness reporting
```
