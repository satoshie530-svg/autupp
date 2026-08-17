package com.privatestore.tvmanager.ui

import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.darkColorScheme

object TvAppManagerColors {
    val Background = Color(0xFF0B1120)
    val Surface = Color(0xFF141C30)
    val SurfaceVariant = Color(0xFF1C2740)
    val OnBackground = Color(0xFFE8ECF7)
    val OnSurface = Color(0xFFE8ECF7)
    val OnSurfaceVariant = Color(0xFFB8C2DA)
    val Primary = Color(0xFF4C8DFF)
    val OnPrimary = Color(0xFF08101F)
    val Error = Color(0xFFEF5350)
    val OnError = Color(0xFFFFFFFF)
    val UpdateBannerBackground = Color(0xFF15352C)
}

val TvAppManagerColorScheme: ColorScheme = darkColorScheme(
    primary = TvAppManagerColors.Primary,
    onPrimary = TvAppManagerColors.OnPrimary,
    background = TvAppManagerColors.Background,
    onBackground = TvAppManagerColors.OnBackground,
    surface = TvAppManagerColors.Surface,
    onSurface = TvAppManagerColors.OnSurface,
    surfaceVariant = TvAppManagerColors.SurfaceVariant,
    onSurfaceVariant = TvAppManagerColors.OnSurfaceVariant,
    border = TvAppManagerColors.Primary,
    error = TvAppManagerColors.Error,
    onError = TvAppManagerColors.OnError
)
