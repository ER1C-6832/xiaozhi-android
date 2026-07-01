package com.er1cmo.xiaozhiandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuroraLightColorScheme = lightColorScheme(
    primary = CharcoalBlack,
    onPrimary = Color.White,
    primaryContainer = ColorMistyBlue.copy(alpha = 0.45f),
    onPrimaryContainer = WarmTextPrimary,
    secondary = WarmTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = ColorLilac.copy(alpha = 0.38f),
    onSecondaryContainer = WarmTextPrimary,
    tertiary = ColorPeach,
    onTertiary = WarmTextPrimary,
    tertiaryContainer = ColorPeach.copy(alpha = 0.48f),
    onTertiaryContainer = WarmTextPrimary,
    background = WarmOatBackground,
    onBackground = WarmTextPrimary,
    surface = WarmCardWhite,
    onSurface = WarmTextPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmTextSecondary,
    outline = WarmLine,
    outlineVariant = WarmLine.copy(alpha = 0.72f),
    error = Color(0xFF9D5757),
    onError = Color.White,
    errorContainer = ColorRoseDust.copy(alpha = 0.55f),
    onErrorContainer = WarmTextPrimary,
)

private val AuroraDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF4F0E8),
    onPrimary = CharcoalBlack,
    primaryContainer = ColorMistyBlue.copy(alpha = 0.28f),
    onPrimaryContainer = Color(0xFFF4F0E8),
    secondary = Color(0xFFD8D2C8),
    onSecondary = CharcoalBlack,
    secondaryContainer = ColorLilac.copy(alpha = 0.24f),
    onSecondaryContainer = Color(0xFFF4F0E8),
    tertiary = ColorPeach.copy(alpha = 0.86f),
    onTertiary = CharcoalBlack,
    tertiaryContainer = ColorPeach.copy(alpha = 0.25f),
    onTertiaryContainer = Color(0xFFF4F0E8),
    background = Color(0xFF151513),
    onBackground = Color(0xFFF4F0E8),
    surface = Color(0xFF20201D),
    onSurface = Color(0xFFF4F0E8),
    surfaceVariant = Color(0xFF2A2925),
    onSurfaceVariant = Color(0xFFC8C1B4),
    outline = Color(0xFF3B3933),
    outlineVariant = Color(0xFF32302B),
    error = Color(0xFFE9B4B4),
    onError = CharcoalBlack,
    errorContainer = ColorRoseDust.copy(alpha = 0.28f),
    onErrorContainer = Color(0xFFF4F0E8),
)

@Composable
fun XiaozhiAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AuroraDarkColorScheme else AuroraLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
