package com.example.mitvplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Íconos de línea dibujados a mano (así no hace falta una librería de íconos grande). */
enum class Glyph { TV, CLAPPER, SERIES, EPG, GRID, CHECK, RADIO, REC, POWER, SWAP, CHEVRON, PLUS }

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.White
) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s / 15f
        val line = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun pt(x: Float, y: Float) = Offset(x * s, y * s)

        fun box(x: Float, y: Float, w: Float, h: Float, r: Float) {
            drawRoundRect(tint, pt(x, y), Size(w * s, h * s), CornerRadius(r * s), line)
        }

        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, pt(x1, y1), pt(x2, y2), sw, StrokeCap.Round)
        }

        fun poly(closed: Boolean, filled: Boolean, vararg p: Float) {
            val path = Path()
            path.moveTo(p[0] * s, p[1] * s)
            var i = 2
            while (i < p.size) {
                path.lineTo(p[i] * s, p[i + 1] * s)
                i += 2
            }
            if (closed) path.close()
            if (filled) drawPath(path, tint, style = Fill) else drawPath(path, tint, style = line)
        }

        when (glyph) {
            Glyph.TV -> {
                box(0.06f, 0.12f, 0.88f, 0.62f, 0.08f)
                poly(true, false, 0.43f, 0.27f, 0.43f, 0.59f, 0.68f, 0.43f)
                seg(0.30f, 0.90f, 0.70f, 0.90f)
                seg(0.50f, 0.74f, 0.50f, 0.90f)
            }
            Glyph.CLAPPER -> {
                box(0.10f, 0.38f, 0.80f, 0.52f, 0.05f)
                box(0.10f, 0.14f, 0.80f, 0.18f, 0.04f)
                seg(0.28f, 0.32f, 0.38f, 0.14f)
                seg(0.48f, 0.32f, 0.58f, 0.14f)
                seg(0.68f, 0.32f, 0.78f, 0.14f)
                poly(true, false, 0.42f, 0.50f, 0.42f, 0.78f, 0.64f, 0.64f)
            }
            Glyph.SERIES -> {
                box(0.08f, 0.40f, 0.84f, 0.50f, 0.06f)
                drawCircle(tint, radius = 0.09f * s, center = pt(0.28f, 0.24f), style = line)
                drawCircle(tint, radius = 0.09f * s, center = pt(0.50f, 0.20f), style = line)
                drawCircle(tint, radius = 0.09f * s, center = pt(0.72f, 0.24f), style = line)
                poly(true, false, 0.44f, 0.52f, 0.44f, 0.78f, 0.64f, 0.65f)
            }
            Glyph.EPG -> {
                box(0.20f, 0.14f, 0.60f, 0.78f, 0.06f)
                box(0.36f, 0.06f, 0.28f, 0.14f, 0.04f)
                poly(false, false, 0.30f, 0.40f, 0.35f, 0.46f, 0.44f, 0.34f)
                seg(0.52f, 0.40f, 0.70f, 0.40f)
                poly(false, false, 0.30f, 0.60f, 0.35f, 0.66f, 0.44f, 0.54f)
                seg(0.52f, 0.60f, 0.70f, 0.60f)
                poly(false, false, 0.30f, 0.80f, 0.35f, 0.86f, 0.44f, 0.74f)
                seg(0.52f, 0.80f, 0.70f, 0.80f)
            }
            Glyph.GRID -> {
                box(0.12f, 0.12f, 0.32f, 0.32f, 0.05f)
                box(0.56f, 0.12f, 0.32f, 0.32f, 0.05f)
                box(0.12f, 0.56f, 0.32f, 0.32f, 0.05f)
                box(0.56f, 0.56f, 0.32f, 0.32f, 0.05f)
            }
            Glyph.CHECK -> {
                drawCircle(tint, radius = 0.40f * s, center = pt(0.5f, 0.5f), style = line)
                poly(false, false, 0.30f, 0.52f, 0.45f, 0.67f, 0.72f, 0.36f)
            }
            Glyph.RADIO -> {
                box(0.08f, 0.34f, 0.84f, 0.54f, 0.07f)
                seg(0.22f, 0.34f, 0.78f, 0.10f)
                drawCircle(tint, radius = 0.12f * s, center = pt(0.33f, 0.61f), style = line)
                seg(0.58f, 0.52f, 0.78f, 0.52f)
                seg(0.58f, 0.68f, 0.78f, 0.68f)
            }
            Glyph.REC -> {
                box(0.06f, 0.24f, 0.62f, 0.54f, 0.08f)
                poly(true, false, 0.70f, 0.44f, 0.94f, 0.30f, 0.94f, 0.72f, 0.70f, 0.58f)
                drawCircle(Color(0xFFFF4D4D), radius = 0.10f * s, center = pt(0.37f, 0.51f), style = Fill)
            }
            Glyph.POWER -> {
                drawArc(
                    color = tint,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = pt(0.16f, 0.20f),
                    size = Size(0.68f * s, 0.68f * s),
                    style = line
                )
                seg(0.50f, 0.08f, 0.50f, 0.50f)
            }
            Glyph.SWAP -> {
                seg(0.14f, 0.34f, 0.86f, 0.34f)
                poly(false, false, 0.70f, 0.18f, 0.86f, 0.34f, 0.70f, 0.50f)
                seg(0.86f, 0.68f, 0.14f, 0.68f)
                poly(false, false, 0.30f, 0.52f, 0.14f, 0.68f, 0.30f, 0.84f)
            }
            Glyph.CHEVRON -> {
                poly(false, false, 0.36f, 0.20f, 0.66f, 0.50f, 0.36f, 0.80f)
            }
            Glyph.PLUS -> {
                seg(0.50f, 0.18f, 0.50f, 0.82f)
                seg(0.18f, 0.50f, 0.82f, 0.50f)
            }
        }
    }
}

/** Un diseño de pantalla múltiple: cada celda es un rectángulo con valores de 0 a 1. */
data class CellRect(val x: Float, val y: Float, val w: Float, val h: Float)

val MultiLayouts: List<List<CellRect>> = listOf(
    // 1. Cuatro iguales
    listOf(CellRect(0f, 0f, 0.5f, 0.5f), CellRect(0.5f, 0f, 0.5f, 0.5f), CellRect(0f, 0.5f, 0.5f, 0.5f), CellRect(0.5f, 0.5f, 0.5f, 0.5f)),
    // 2. Una grande arriba y tres abajo
    listOf(CellRect(0f, 0f, 1f, 0.62f), CellRect(0f, 0.62f, 1f / 3f, 0.38f), CellRect(1f / 3f, 0.62f, 1f / 3f, 0.38f), CellRect(2f / 3f, 0.62f, 1f / 3f, 0.38f)),
    // 3. Una grande arriba y dos abajo
    listOf(CellRect(0f, 0f, 1f, 0.5f), CellRect(0f, 0.5f, 0.5f, 0.5f), CellRect(0.5f, 0.5f, 0.5f, 0.5f)),
    // 4. Dos arriba y una grande abajo
    listOf(CellRect(0f, 0f, 0.5f, 0.5f), CellRect(0.5f, 0f, 0.5f, 0.5f), CellRect(0f, 0.5f, 1f, 0.5f)),
    // 5. Dos lado a lado
    listOf(CellRect(0f, 0f, 0.5f, 1f), CellRect(0.5f, 0f, 0.5f, 1f)),
    // 6. Dos apiladas
    listOf(CellRect(0f, 0f, 1f, 0.5f), CellRect(0f, 0.5f, 1f, 0.5f))
)

/** Miniatura de un diseño, como en el selector. */
@Composable
fun LayoutPreview(cells: List<CellRect>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFFC9C9C9))
        val pad = size.minDimension * 0.06f
        val innerW = size.width - 2 * pad
        val innerH = size.height - 2 * pad
        val gap = size.minDimension * 0.02f
        cells.forEach { cell ->
            drawRect(
                color = Color(0xFF111111),
                topLeft = Offset(pad + cell.x * innerW + gap, pad + cell.y * innerH + gap),
                size = Size(cell.w * innerW - 2 * gap, cell.h * innerH - 2 * gap)
            )
        }
    }
}
