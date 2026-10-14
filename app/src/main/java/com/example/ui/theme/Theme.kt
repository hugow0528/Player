package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrightCyan,
    onPrimary = Obsidian,
    primaryContainer = NeonIndigo.copy(alpha = 0.25f),
    onPrimaryContainer = BrightCyan,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = ElectricViolet.copy(alpha = 0.25f),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = NeonPink,
    onTertiary = Color.White,
    background = Obsidian,
    onBackground = TextPrimary,
    surface = DeepNavy,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceLightDark,
    outlineVariant = SurfaceDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted rich music palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
