package com.privatestore.tvmanager.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.lightColorScheme

object TvAppManagerColors {
    val BackgroundGradientTop = Color(0xFFC9D8F5)
    val BackgroundGradientBottom = Color(0xFFE3EAFA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF1F4FA)
    val OnBackground = Color(0xFF14213D)
    val OnSurface = Color(0xFF14213D)
    val OnSurfaceVariant = Color(0xFF6B7280)
    val Primary = Color(0xFF3B6FF0)
    val OnPrimary = Color(0xFFFFFFFF)
    val Error = Color(0xFFE23D3D)
    val OnError = Color(0xFFFFFFFF)
    val UpdateBannerBackground = Color(0xFFE8F5EC)
    val Success = Color(0xFF22C55E)
    val Whatsapp = Color(0xFF25D366)

    /** Borde sutil pero visible entre tarjetas, independiente del foco. */
    val CardBorder = Color(0xFFE4E9F2)

    val BackgroundGradient = Brush.verticalGradient(listOf(BackgroundGradientTop, BackgroundGradientBottom))

    /** Paleta de acento para los cuadrados de ícono de respaldo (letra inicial),
     *  elegida de forma determinística por app para que cada tarjeta se distinga
     *  a simple vista aunque no tenga iconUrl. */
    val IconAccents = listOf(
        Color(0xFF3B6FF0), // azul
        Color(0xFF8B5CF6), // violeta
        Color(0xFF14B8A6), // verde azulado
        Color(0xFFEF4444), // rojo
        Color(0xFFF59E0B), // ámbar
        Color(0xFFEC4899), // rosa
        Color(0xFF22C55E), // verde
        Color(0xFF6366F1)  // índigo
    )
}

val TvAppManagerColorScheme: ColorScheme = lightColorScheme(
    primary = TvAppManagerColors.Primary,
    onPrimary = TvAppManagerColors.OnPrimary,
    background = TvAppManagerColors.BackgroundGradientTop,
    onBackground = TvAppManagerColors.OnBackground,
    surface = TvAppManagerColors.Surface,
    onSurface = TvAppManagerColors.OnSurface,
    surfaceVariant = TvAppManagerColors.SurfaceVariant,
    onSurfaceVariant = TvAppManagerColors.OnSurfaceVariant,
    border = TvAppManagerColors.CardBorder,
    error = TvAppManagerColors.Error,
    onError = TvAppManagerColors.OnError
)
