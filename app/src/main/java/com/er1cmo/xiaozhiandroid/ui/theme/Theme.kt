package com.er1cmo.xiaozhiandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Charcoal,
    onPrimary = Color.White,
    primaryContainer = WarmSurfaceVariant,
    onPrimaryContainer = WarmText,
    secondary = OrganicAccent,
    onSecondary = Color.White,
    secondaryContainer = SoftAmber,
    onSecondaryContainer = WarmText,
    tertiary = MutedTerracotta,
    onTertiary = Color.White,
    tertiaryContainer = SoftClay,
    onTertiaryContainer = WarmText,
    background = OatBackground,
    onBackground = WarmText,
    surface = WarmSurface,
    onSurface = WarmText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmTextSecondary,
    outline = WarmBorder,
    outlineVariant = WarmBorder,
    error = Color(0xFF9C5A50),
    errorContainer = SoftClay,
    onErrorContainer = WarmText,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF4E6D0),
    onPrimary = Color(0xFF1E1B17),
    primaryContainer = Color(0xFF2F2B25),
    onPrimaryContainer = Color(0xFFF7EFE4),
    secondary = Color(0xFFD8B889),
    onSecondary = Color(0xFF211B15),
    secondaryContainer = Color(0xFF403629),
    onSecondaryContainer = Color(0xFFF3E6D2),
    tertiary = Color(0xFFE0B9B2),
    onTertiary = Color(0xFF2B1B18),
    tertiaryContainer = Color(0xFF483330),
    onTertiaryContainer = Color(0xFFF4DDDA),
    background = Color(0xFF141311),
    onBackground = Color(0xFFF5F1EB),
    surface = Color(0xFF1F1D1A),
    onSurface = Color(0xFFF5F1EB),
    surfaceVariant = Color(0xFF2A2722),
    onSurfaceVariant = Color(0xFFC9C0B5),
    outline = Color(0xFF4A4640),
    outlineVariant = Color(0xFF36322D),
    error = Color(0xFFF0B8AF),
    errorContainer = Color(0xFF4B2C2A),
    onErrorContainer = Color(0xFFFFE6E1),
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
