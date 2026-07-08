package com.manewreck.sdapp.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SdaDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextPrimary,
    background = GraphiteBackground,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteSurfaceAlt,
    outline = GraphiteOutline,
    error = DangerRed,
)

@Composable
fun SdaMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SdaDarkScheme,
        typography = SdaTypography,
        content = content,
    )
}
