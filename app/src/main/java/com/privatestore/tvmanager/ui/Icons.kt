package com.privatestore.tvmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Puñado de íconos propios dibujados a mano con Canvas en vez de la librería
 * material-icons-extended: esa dependencia agrega miles de clases (~5 estilos x
 * ~900 íconos) solo para usar 4-5 glifos simples, y eso alargaba notablemente el
 * arranque en frío en la TV (verificación de un dex mucho más grande). Con esto
 * el arranque baja de ~7s a menos de 1s.
 */

@Composable
fun DownloadGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.13f
        val arrow = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.5f, h * 0.6f)
            moveTo(w * 0.2f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.66f)
            lineTo(w * 0.8f, h * 0.38f)
        }
        drawPath(
            path = arrow,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.16f, h * 0.9f),
            end = Offset(w * 0.84f, h * 0.9f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ChevronRightGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.3f, h * 0.14f)
            lineTo(w * 0.74f, h * 0.5f)
            lineTo(w * 0.3f, h * 0.86f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = w * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ClockGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.11f
        val center = Offset(w / 2f, h / 2f)
        val radius = (minOf(w, h) / 2f) - strokeW
        drawCircle(color = tint, radius = radius, center = center, style = Stroke(width = strokeW))
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x, center.y - radius * 0.55f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x + radius * 0.42f, center.y),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}
