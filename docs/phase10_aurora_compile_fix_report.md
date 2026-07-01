# Phase 10 Aurora Compile Fix

## Scope

Fix Kotlin compile errors after the Aurora Fluid Gradient UI overlay.

## Files

- `app/src/main/java/com/er1cmo/xiaozhiandroid/ui/theme/Color.kt`

## Fix

The aurora overlay renamed the canonical color constants, while `DebugLogPanel.kt`
and `ToolCallPanel.kt` still referenced the earlier organic-minimal names:

- `Charcoal`
- `OatBackground`
- `WarmBorder`
- `WarmSurface`
- `WarmText`
- `SoftAmber`
- `SoftClay`

This patch restores those names as aliases to the new aurora/warm-neutral palette.
No visual behavior is reverted; the main assistant visual still uses the new
faceless aurora fluid design.

## Validation

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected result: no unresolved color references.
