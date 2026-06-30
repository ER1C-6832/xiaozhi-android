package com.er1cmo.xiaozhiandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = XiaozhiCyan,
    onPrimary = Color(0xFF001F29),
    primaryContainer = Color(0xFF07384A),
    onPrimaryContainer = Color(0xFFD8F8FF),
    secondary = XiaozhiViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D245C),
    onSecondaryContainer = Color(0xFFE9E0FF),
    tertiary = XiaozhiPink,
    onTertiary = Color(0xFF331029),
    tertiaryContainer = Color(0xFF4A2141),
    onTertiaryContainer = Color(0xFFFFD7F2),
    background = XiaozhiDark,
    onBackground = Color(0xFFEAF4FF),
    surface = XiaozhiDarkSurface,
    onSurface = Color(0xFFEAF4FF),
    surfaceVariant = Color(0xFF17263B),
    onSurfaceVariant = Color(0xFFB8C8DF),
    error = XiaozhiError,
    errorContainer = Color(0xFF4A1527),
    onErrorContainer = Color(0xFFFFD9E2),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006C86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F3FF),
    onPrimaryContainer = Color(0xFF003543),
    secondary = Color(0xFF6750D8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DDFF),
    onSecondaryContainer = Color(0xFF211356),
    tertiary = Color(0xFF9B3D85),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F2),
    onTertiaryContainer = Color(0xFF37102E),
    background = XiaozhiLight,
    onBackground = Color(0xFF101828),
    surface = XiaozhiLightSurface,
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF506070),
    error = Color(0xFFB3264A),
    errorContainer = Color(0xFFFFD9E2),
    onErrorContainer = Color(0xFF41000F),
)

@Composable
fun XiaozhiAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
