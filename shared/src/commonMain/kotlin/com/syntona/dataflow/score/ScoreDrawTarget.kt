package com.syntona.dataflow.score

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

/**
 * Shared vector drawing abstraction for staff notation rendering.
 * Screen rendering uses [ComposeScoreDrawTarget]; future PDF export can implement
 * this same interface so screen and printed output share one layout engine.
 */
interface ScoreDrawTarget {
    fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        color: Color,
    )

    fun fillRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color,
    )

    fun drawCubicBezier(
        x0: Float,
        y0: Float,
        cx1: Float,
        cy1: Float,
        cx2: Float,
        cy2: Float,
        x3: Float,
        y3: Float,
        strokeWidth: Float,
        color: Color,
    )

    /** Draws a SMuFL glyph string with its SMuFL baseline/origin at `(x, baselineY)`. */
    fun drawGlyph(
        glyph: String,
        x: Float,
        baselineY: Float,
        fontSizePx: Float,
        color: Color,
    )

    /** Draws plain annotation text with its baseline at `(x, baselineY)`. */
    fun drawText(
        text: String,
        x: Float,
        baselineY: Float,
        fontSizePx: Float,
        color: Color,
    )

    fun measureGlyphWidth(glyph: String, fontSizePx: Float): Float
}

/** [ScoreDrawTarget] implementation backed by Compose [DrawScope] and [TextMeasurer]. */
class ComposeScoreDrawTarget(
    private val drawScope: DrawScope,
    private val textMeasurer: TextMeasurer,
    private val density: Density,
    private val musicFontFamily: FontFamily,
    private val uiFontFamily: FontFamily = FontFamily.SansSerif,
) : ScoreDrawTarget {

    override fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        color: Color,
    ) {
        drawScope.drawLine(
            color = color,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = strokeWidth,
        )
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
        drawScope.drawRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(width, height),
        )
    }

    override fun drawCubicBezier(
        x0: Float,
        y0: Float,
        cx1: Float,
        cy1: Float,
        cx2: Float,
        cy2: Float,
        x3: Float,
        y3: Float,
        strokeWidth: Float,
        color: Color,
    ) {
        val path = Path().apply {
            moveTo(x0, y0)
            cubicTo(cx1, cy1, cx2, cy2, x3, y3)
        }
        drawScope.drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth),
        )
    }

    override fun drawGlyph(
        glyph: String,
        x: Float,
        baselineY: Float,
        fontSizePx: Float,
        color: Color,
    ) {
        drawMeasuredText(glyph, x, baselineY, fontSizePx, color, musicFontFamily)
    }

    override fun drawText(
        text: String,
        x: Float,
        baselineY: Float,
        fontSizePx: Float,
        color: Color,
    ) {
        drawMeasuredText(text, x, baselineY, fontSizePx, color, uiFontFamily)
    }

    override fun measureGlyphWidth(glyph: String, fontSizePx: Float): Float {
        val fontSizeSp = with(density) { fontSizePx.toSp() }
        val result = textMeasurer.measure(
            text = glyph,
            style = TextStyle(fontFamily = musicFontFamily, fontSize = fontSizeSp),
            softWrap = false,
            maxLines = 1,
        )
        return result.size.width.toFloat()
    }

    private fun drawMeasuredText(
        text: String,
        x: Float,
        baselineY: Float,
        fontSizePx: Float,
        color: Color,
        family: FontFamily,
    ) {
        val fontSizeSp = with(density) { fontSizePx.toSp() }
        val measured = textMeasurer.measure(
            text = text,
            style = TextStyle(fontFamily = family, fontSize = fontSizeSp, color = color),
            softWrap = false,
            maxLines = 1,
        )
        drawScope.drawText(
            textLayoutResult = measured,
            color = color,
            topLeft = Offset(x, baselineY - measured.firstBaseline),
        )
    }
}
