package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KaliPrimary,
    onPrimary = Color(0xFF00354E),
    primaryContainer = Color(0xFF004D70),
    onPrimaryContainer = Color(0xFFC2E8FF),
    secondary = KaliSecondary,
    onSecondary = Color(0xFF003923),
    secondaryContainer = Color(0xFF005234),
    onSecondaryContainer = Color(0xFF86F8C1),
    tertiary = KaliTertiary,
    onTertiary = Color(0xFF49007A),
    tertiaryContainer = Color(0xFF671B9F),
    onTertiaryContainer = Color(0xFFF3DAFF),
    background = KaliDarkBg,
    onBackground = KaliText,
    surface = KaliSurfaceDark,
    onSurface = KaliText,
    surfaceVariant = KaliSurfaceVariant,
    onSurfaceVariant = KaliTextMuted,
    outline = KaliBorder,
    error = KaliRed,
    onError = Color.White
)

private val LightColorScheme = darkColorScheme(
    primary = KaliPrimary,
    background = KaliDarkBg,
    surface = KaliSurfaceDark,
    onBackground = KaliText,
    onSurface = KaliText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Security Workstation prefers a high-contrast dark cybersecurity aesthetic
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
