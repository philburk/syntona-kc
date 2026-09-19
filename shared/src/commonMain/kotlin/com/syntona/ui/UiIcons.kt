package com.syntona.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A disclosure triangle widget used in tree nodes and expandable bundles.
 * Renders a solid down-pointing triangle (▼) when expanded,
 * or a solid right-pointing triangle (▶) when collapsed.
 * Drawn via Compose Canvas to avoid font missing-glyph fallbacks (☒) on Web targets.
 */
@Composable
fun DisclosureTriangle(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    size: Dp = 8.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val path = Path()
        val w = this.size.width
        val h = this.size.height
        if (isExpanded) {
            // Down-pointing triangle (▼)
            path.moveTo(0f, h * 0.2f)
            path.lineTo(w, h * 0.2f)
            path.lineTo(w * 0.5f, h * 0.8f)
            path.close()
        } else {
            // Right-pointing triangle (▶)
            path.moveTo(w * 0.2f, 0f)
            path.lineTo(w * 0.8f, h * 0.5f)
            path.lineTo(w * 0.2f, h)
            path.close()
        }
        drawPath(path, color = color)
    }
}

/**
 * A down-pointing triangle icon used for dropdown menus.
 */
@Composable
fun DropdownArrow(
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    size: Dp = 8.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val path = Path()
        val w = this.size.width
        val h = this.size.height
        path.moveTo(0f, h * 0.2f)
        path.lineTo(w, h * 0.2f)
        path.lineTo(w * 0.5f, h * 0.8f)
        path.close()
        drawPath(path, color = color)
    }
}

/**
 * A vertical 3-dot ellipsis menu icon (⋮) for module headers and overflow menus.
 */
@Composable
fun VerticalEllipsisIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 12.dp
) {
    Canvas(modifier = modifier.size(width = size / 2, height = size)) {
        val radius = 1.2.dp.toPx()
        val cx = this.size.width / 2f
        val topY = this.size.height * 0.2f
        val midY = this.size.height * 0.5f
        val botY = this.size.height * 0.8f
        drawCircle(color = color, radius = radius, center = Offset(cx, topY))
        drawCircle(color = color, radius = radius, center = Offset(cx, midY))
        drawCircle(color = color, radius = radius, center = Offset(cx, botY))
    }
}

/**
 * A magnifying glass search icon (🔍) for search/filter fields.
 */
@Composable
fun SearchIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.LightGray,
    size: Dp = 12.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 1.5.dp.toPx()
        val radius = this.size.width * 0.32f
        val center = Offset(this.size.width * 0.38f, this.size.height * 0.38f)
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        val handleStart = Offset(
            center.x + radius * 0.7071f,
            center.y + radius * 0.7071f
        )
        val handleEnd = Offset(this.size.width * 0.9f, this.size.height * 0.9f)
        drawLine(
            color = color,
            start = handleStart,
            end = handleEnd,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * A play icon (▶) drawn via Canvas.
 */
@Composable
fun PlayIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF34D399),
    size: Dp = 10.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val path = Path()
        val w = this.size.width
        val h = this.size.height
        path.moveTo(w * 0.15f, 0f)
        path.lineTo(w * 0.9f, h * 0.5f)
        path.lineTo(w * 0.15f, h)
        path.close()
        drawPath(path, color = color)
    }
}

/**
 * A stop icon (◼) drawn via Canvas.
 */
@Composable
fun StopIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Red,
    size: Dp = 10.dp
) {
    Canvas(modifier = modifier.size(size)) {
        drawRect(color = color)
    }
}

/**
 * A right chevron icon (›) for submenus.
 */
@Composable
fun ChevronRightIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    size: Dp = 12.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val path = Path()
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = 1.5.dp.toPx()
        path.moveTo(w * 0.3f, h * 0.15f)
        path.lineTo(w * 0.7f, h * 0.5f)
        path.lineTo(w * 0.3f, h * 0.85f)
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

/**
 * A lightbulb icon (💡) drawn via Canvas for splash/help text.
 */
@Composable
fun LightbulbIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFD54F),
    size: Dp = 16.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path()

        val centerX = w * 0.5f
        val centerY = h * 0.38f
        val bulbRadius = w * 0.35f

        drawCircle(color = color, radius = bulbRadius, center = Offset(centerX, centerY))

        path.moveTo(w * 0.32f, h * 0.5f)
        path.lineTo(w * 0.68f, h * 0.5f)
        path.lineTo(w * 0.60f, h * 0.78f)
        path.lineTo(w * 0.40f, h * 0.78f)
        path.close()
        drawPath(path, color = color)

        val baseWidth = w * 0.24f
        drawRect(
            color = color,
            topLeft = Offset(w * 0.38f, h * 0.82f),
            size = androidx.compose.ui.geometry.Size(baseWidth, h * 0.12f)
        )
    }
}

/**
 * A record icon (solid filled circle) drawn via Canvas to avoid missing-glyph fallbacks on Web.
 */
@Composable
fun RecordIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 10.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val radius = minOf(this.size.width, this.size.height) * 0.44f
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(this.size.width / 2f, this.size.height / 2f)
        )
    }
}

/**
 * A loop/repeat icon (two cycling arrows) drawn via Canvas to avoid missing-glyph fallbacks on Web.
 */
@Composable
fun LoopIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 12.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = 1.6.dp.toPx()

        // Top arc and line: curves from left going up and right
        val topPath = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.22f, h * 0.36f)
            quadraticTo(w * 0.22f, h * 0.22f, w * 0.38f, h * 0.22f)
            lineTo(w * 0.68f, h * 0.22f)
        }
        drawPath(topPath, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

        // Top arrowhead pointing right
        val topArrow = Path().apply {
            moveTo(w * 0.90f, h * 0.22f)
            lineTo(w * 0.62f, h * 0.04f)
            lineTo(w * 0.62f, h * 0.40f)
            close()
        }
        drawPath(topArrow, color = color)

        // Bottom arc and line: curves from right going down and left
        val botPath = Path().apply {
            moveTo(w * 0.78f, h * 0.48f)
            lineTo(w * 0.78f, h * 0.64f)
            quadraticTo(w * 0.78f, h * 0.78f, w * 0.62f, h * 0.78f)
            lineTo(w * 0.32f, h * 0.78f)
        }
        drawPath(botPath, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

        // Bottom arrowhead pointing left
        val botArrow = Path().apply {
            moveTo(w * 0.10f, h * 0.78f)
            lineTo(w * 0.38f, h * 0.60f)
            lineTo(w * 0.38f, h * 0.96f)
            close()
        }
        drawPath(botArrow, color = color)
    }
}

/**
 * A right-pointing arrow icon (play-through / non-looping mode) drawn via Canvas to avoid missing-glyph fallbacks on Web.
 */
@Composable
fun ArrowForwardIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.LightGray,
    size: Dp = 12.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = 1.8.dp.toPx()

        // Horizontal stem
        drawLine(
            color = color,
            start = Offset(w * 0.12f, h * 0.5f),
            end = Offset(w * 0.65f, h * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Arrowhead pointing right
        val arrow = Path().apply {
            moveTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.56f, h * 0.24f)
            lineTo(w * 0.56f, h * 0.76f)
            close()
        }
        drawPath(arrow, color = color)
    }
}

