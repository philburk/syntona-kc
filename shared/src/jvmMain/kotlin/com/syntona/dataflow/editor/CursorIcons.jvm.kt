package com.syntona.dataflow.editor

import androidx.compose.ui.input.pointer.PointerIcon
import com.syntona.dataflow.score.NoteDuration
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage

actual fun resizeCornerCursor(): PointerIcon = PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))

actual fun resizeHorizontalCursor(): PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

actual fun moveCursor(): PointerIcon = PointerIcon(Cursor(Cursor.MOVE_CURSOR))

actual fun crosshairCursor(): PointerIcon = PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR))

private data class CursorCacheKey(
    val duration: NoteDuration,
    val isRest: Boolean,
    val isDarkMode: Boolean,
)

private val scoreCursorCache = mutableMapOf<CursorCacheKey, PointerIcon>()

actual fun scoreSymbolCursor(
    duration: NoteDuration,
    isRest: Boolean,
    isDarkMode: Boolean,
): PointerIcon {
    val key = CursorCacheKey(duration, isRest, isDarkMode)
    scoreCursorCache[key]?.let { return it }
    val created = try {
        buildJvmScoreCursor(duration, isRest, isDarkMode)
    } catch (_: Throwable) {
        crosshairCursor()
    }
    scoreCursorCache[key] = created
    return created
}

private fun buildJvmScoreCursor(
    duration: NoteDuration,
    isRest: Boolean,
    isDarkMode: Boolean,
): PointerIcon {
    val toolkit = Toolkit.getDefaultToolkit()
    val size = 32
    val best = toolkit.getBestCursorSize(size, size)
    val w = best.width.coerceAtLeast(size)
    val h = best.height.coerceAtLeast(size)
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    val fg = if (isDarkMode) Color(0xF8, 0xFA, 0xFC) else Color(0x11, 0x18, 0x27)
    val halo = if (isDarkMode) Color(0x0F, 0x17, 0x2A, 210) else Color(0xFF, 0xFF, 0xFF, 225)

    // Draw a subtle contrasting halo first, then crisp foreground
    drawCursorSymbol(g, duration, isRest, halo, haloExpand = 2.2f)
    drawCursorSymbol(g, duration, isRest, fg, haloExpand = 0f)
    g.dispose()

    val (rawHotX, rawHotY) = if (isRest) 16 to 16 else 10 to 23
    val hotSpot = Point(rawHotX.coerceIn(0, w - 1), rawHotY.coerceIn(0, h - 1))
    val awtCursor = toolkit.createCustomCursor(
        image,
        hotSpot,
        "score_${duration.name}_${if (isRest) "rest" else "note"}",
    )
    return PointerIcon(awtCursor)
}

private fun drawCursorSymbol(
    g: Graphics2D,
    duration: NoteDuration,
    isRest: Boolean,
    color: Color,
    haloExpand: Float,
) {
    g.color = color
    if (!isRest) {
        val cx = 10.0
        val cy = 23.0
        val headW = if (duration == NoteDuration.WHOLE) 11.0 else 9.5
        val headH = 6.8
        val savedTransform = g.transform
        g.translate(cx, cy)
        if (duration != NoteDuration.WHOLE) {
            g.rotate(Math.toRadians(-20.0))
        }
        val oval = Ellipse2D.Double(-headW / 2.0, -headH / 2.0, headW, headH)
        if (duration == NoteDuration.WHOLE || duration == NoteDuration.HALF) {
            g.stroke = BasicStroke(1.8f + haloExpand, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(oval)
        } else {
            g.fill(oval)
            if (haloExpand > 0f) {
                g.stroke = BasicStroke(haloExpand, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(oval)
            }
        }
        g.transform = savedTransform

        if (duration.hasStem) {
            val stemX = (cx + headW / 2.0 - 0.8).toFloat()
            val stemBottom = (cy - 1.0).toFloat()
            val stemTop = if (duration.flagCount >= 3) 3.5f else 5.0f
            g.stroke = BasicStroke(1.7f + haloExpand, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(stemX.toInt(), stemBottom.toInt(), stemX.toInt(), stemTop.toInt())
            val flagStroke = BasicStroke(1.8f + haloExpand, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.stroke = flagStroke
            for (i in 0 until duration.flagCount) {
                val fy = stemTop + i * 4.2f
                val flag = GeneralPath().apply {
                    moveTo(stemX, fy)
                    curveTo(stemX + 5f, fy + 1.5f, stemX + 6.5f, fy + 5.5f, stemX + 3.5f, fy + 8.5f)
                }
                g.draw(flag)
            }
        }
    } else {
        when (duration) {
            NoteDuration.WHOLE, NoteDuration.HALF -> {
                g.stroke = BasicStroke(1.2f + haloExpand, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val lineY = if (duration == NoteDuration.WHOLE) 13 else 18
                g.drawLine(7, lineY, 25, lineY)
                val rectY = if (duration == NoteDuration.WHOLE) 13 else 14
                val pad = (haloExpand / 2f).toInt()
                g.fillRect(11 - pad, rectY - pad, 10 + pad * 2, 4 + pad * 2)
            }
            NoteDuration.QUARTER -> {
                val path = GeneralPath().apply {
                    moveTo(13f, 6f)
                    lineTo(18f, 12f)
                    lineTo(13.5f, 16.5f)
                    lineTo(18.5f, 22f)
                    curveTo(14.5f, 21.5f, 12.5f, 24.5f, 16.5f, 27f)
                }
                g.stroke = BasicStroke(2.2f + haloExpand, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(path)
            }
            NoteDuration.EIGHTH, NoteDuration.SIXTEENTH, NoteDuration.THIRTY_SECOND -> {
                val count = duration.flagCount
                val startY = if (count >= 3) 6f else 8f
                val stepY = 4.5f
                val topX = 18.5f
                val bottomX = 13.5f
                val bottomY = 25f
                g.stroke = BasicStroke(1.8f + haloExpand, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawLine(topX.toInt(), startY.toInt(), bottomX.toInt(), bottomY.toInt())
                for (i in 0 until count) {
                    val by = startY + i * stepY
                    val bx = topX - (i * 1.1f)
                    val arm = GeneralPath().apply {
                        moveTo(bx, by)
                        curveTo(bx - 4f, by + 2f, bx - 6f, by + 1f, bx - 6.5f, by - 0.5f)
                    }
                    g.draw(arm)
                    val r = 2.1 + haloExpand * 0.45
                    g.fill(Ellipse2D.Double(bx - 7.5 - r, by - r, r * 2, r * 2))
                }
            }
        }
    }
    // Reset transform in case caller inspects it
    val _t = AffineTransform()
}
