package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FrameCraftDarkScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF04131E),
    primaryContainer = CyanDeep,
    onPrimaryContainer = TextPrimary,
    secondary = AmberCut,
    onSecondary = Color(0xFF1A0E00),
    secondaryContainer = Color(0xFF3E2704),
    onSecondaryContainer = AmberGlow,
    tertiary = VioletMotion,
    onTertiary = Color(0xFF0D0E26),
    background = StudioBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceCard,
    outline = StudioBorder,
    error = CoralAlert,
    onError = Color.White
)

@Composable
fun FrameCraftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FrameCraftDarkScheme,
        typography = AppTypography,
        content = content
    )
}
