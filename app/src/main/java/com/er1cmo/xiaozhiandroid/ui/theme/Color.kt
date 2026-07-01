package com.er1cmo.xiaozhiandroid.ui.theme

import androidx.compose.ui.graphics.Color

// Phase 10 Aurora Fluid Gradient palette.
// D65-inspired translucent aurora colors. Avoid skin-tone blobs, metallic blues,
// and formulaic blue/purple button gradients.
val ColorMistyBlue = Color(0xFFD0E1FD)   // 晨雾蓝
val ColorLilac = Color(0xFFE3D5FC)       // 浅风信子紫
val ColorPeach = Color(0xFFFFE3D1)       // 温暖蜜桃粉
val ColorSlateGray = Color(0xFFCBD5E1)   // 未连接状态的冷石灰
val ColorRoseDust = Color(0xFFE6C2C2)    // 错误状态的烟粉色

val WarmOatBackground = Color(0xFFF9F9F7)
val WarmCardWhite = Color(0xFFFFFFFF)
val WarmLine = Color(0xFFE5E5E3)
val CharcoalBlack = Color(0xFF1A1A1A)
val CharcoalPressed = Color(0xFF333333)
val WarmTextPrimary = Color(0xFF1E1E1C)
val WarmTextSecondary = Color(0xFF77746B)
val WarmSurfaceVariant = Color(0xFFF1F0EC)

// Backward-compatible aliases used by Phase 10 support components.
// The aurora rewrite renamed the canonical palette above, but DebugLogPanel and
// ToolCallPanel still use the earlier organic-minimal names. Keep these aliases
// so all UI components compile while sharing the same neutral warm palette.
val Charcoal = CharcoalBlack
val OatBackground = WarmOatBackground
val WarmBorder = WarmLine
val WarmSurface = WarmCardWhite
val WarmText = WarmTextPrimary
val SoftAmber = ColorPeach
val SoftClay = ColorRoseDust
