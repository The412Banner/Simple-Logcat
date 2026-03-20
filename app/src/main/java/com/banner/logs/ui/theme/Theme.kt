package com.banner.logs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = OrangePrimary,
    onPrimary        = SurfaceDark,
    primaryContainer = OrangeDark,
    secondary        = OrangeLight,
    background       = SurfaceDark,
    surface          = SurfaceDark,
    surfaceVariant   = SurfaceVariant,
    onBackground     = OnSurface,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceMuted,
    outline          = Color(0xFF333333),
)

@Composable
fun BannerLogsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
