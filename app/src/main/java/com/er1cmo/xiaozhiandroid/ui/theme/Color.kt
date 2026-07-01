package com.er1cmo.xiaozhiandroid.ui.theme

import androidx.compose.ui.graphics.Color

// Phase 10 Aurora Fluid Gradient saturated palette.
// Uses high-chroma morning aurora hues. Keep the visual abstract: no skin-tone
// blobs, no metallic blue ball, no face-like strokes.
val ColorMistyBlue = Color(0xFF3A86FF)   // 饱满亮丽的极光蓝
val ColorLilac = Color(0xFF8338EC)       // 浓郁高贵的风信子紫
val ColorPeach = Color(0xFFFF006E)       // 极具活力的玫瑰桃红
val ColorSlateGray = Color(0xFF475569)   // 深邃质感的板岩灰
val ColorSunsetAmber = Color(0xFFFF9F1C) // 温暖而高级的落日琥珀橙
val ColorRoseRed = Color(0xFFE63946)     // 醒目的警示玫瑰红

// Product surface palette.
val WarmOatBackground = Color(0xFFF9F9F7)
val WarmCardWhite = Color(0xFFFFFFFF)
val WarmLine = Color(0xFFE5E5E3)
val CharcoalBlack = Color(0xFF1A1A1A)
val CharcoalPressed = Color(0xFF333333)
val WarmTextPrimary = Color(0xFF1E1E1C)
val WarmTextSecondary = Color(0xFF77746B)
val WarmSurfaceVariant = Color(0xFFF1F0EC)

// Backward-compatible aliases used by Phase 10 UI components.
// Keep these aliases so old component files compile while the main aurora visual
// uses the saturated palette above.
val ColorRoseDust = ColorRoseRed
val Charcoal = CharcoalBlack
val OatBackground = WarmOatBackground
val WarmBorder = WarmLine
val WarmSurface = WarmCardWhite
val WarmText = WarmTextPrimary
val SoftAmber = ColorSunsetAmber
val SoftClay = ColorPeach
