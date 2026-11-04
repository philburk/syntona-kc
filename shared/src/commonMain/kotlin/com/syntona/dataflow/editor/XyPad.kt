package com.syntona.dataflow.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.syntona.dataflow.model.ExponentialTaper

@Composable
fun XyPadSurface(
    x: Float,
    y: Float,
    onValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {},
    xRange: ClosedFloatingPointRange<Float> = 0f..1f,
    yRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isExponentialX: Boolean = false,
    isExponentialY: Boolean = false,
    padBackground: Color = Color(0xFF1E1E1E),
    gridColor: Color = Color(0xFF333333),
    crosshairColor: Color = Color(0x664FC3F7),
    puckColor: Color = Color(0xFF4FC3F7)
) {
    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current.density

    val latestXRange by rememberUpdatedState(xRange)
    val latestYRange by rememberUpdatedState(yRange)
    val latestIsExpX by rememberUpdatedState(isExponentialX)
    val latestIsExpY by rememberUpdatedState(isExponentialY)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnPress by rememberUpdatedState(onPress)
    val latestOnRelease by rememberUpdatedState(onRelease)

    fun posToValues(touchX: Float, touchY: Float): Pair<Float, Float> {
        val puckRadius = 8f * density
        val usableW = (widthPx - 2 * puckRadius).coerceAtLeast(1f)
        val usableH = (heightPx - 2 * puckRadius).coerceAtLeast(1f)

        val fracX = ((touchX - puckRadius) / usableW).coerceIn(0f, 1f).toDouble()
        val fracY = (1f - (touchY - puckRadius) / usableH).coerceIn(0f, 1f).toDouble()

        val rStartX = latestXRange.start.toDouble()
        val rEndX = latestXRange.endInclusive.toDouble()
        val rStartY = latestYRange.start.toDouble()
        val rEndY = latestYRange.endInclusive.toDouble()

        val valX = ExponentialTaper.fractionToValue(fracX, rStartX, rEndX, latestIsExpX).toFloat()
        val valY = ExponentialTaper.fractionToValue(fracY, rStartY, rEndY, latestIsExpY).toFloat()

        return Pair(valX, valY)
    }

    Canvas(
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Crosshair)
            .onSizeChanged {
                widthPx = it.width.toFloat()
                heightPx = it.height.toFloat()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val (valX, valY) = posToValues(down.position.x, down.position.y)
                    latestOnValueChange(valX, valY)
                    latestOnPress()
                    drag(down.id) { change ->
                        change.consume()
                        val (dragX, dragY) = posToValues(change.position.x, change.position.y)
                        latestOnValueChange(dragX, dragY)
                    }
                    latestOnRelease()
                }
            }
    ) {
        val puckRadius = 8.dp.toPx()
        val usableW = (size.width - 2 * puckRadius).coerceAtLeast(1f)
        val usableH = (size.height - 2 * puckRadius).coerceAtLeast(1f)

        val fracX = ExponentialTaper.valueToFraction(
            x.toDouble(),
            xRange.start.toDouble(),
            xRange.endInclusive.toDouble(),
            isExponentialX
        ).toFloat().coerceIn(0f, 1f)

        val fracY = ExponentialTaper.valueToFraction(
            y.toDouble(),
            yRange.start.toDouble(),
            yRange.endInclusive.toDouble(),
            isExponentialY
        ).toFloat().coerceIn(0f, 1f)

        val puckX = puckRadius + fracX * usableW
        val puckY = puckRadius + (1f - fracY) * usableH

        // Background
        drawRoundRect(
            color = padBackground,
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF444444),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(1.dp.toPx())
        )

        // Center crosshair lines
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawLine(
            color = gridColor,
            start = Offset(cx, 0f),
            end = Offset(cx, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = 1.dp.toPx()
        )

        // Crosshairs through puck
        drawLine(
            color = crosshairColor,
            start = Offset(puckX, 0f),
            end = Offset(puckX, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = crosshairColor,
            start = Offset(0f, puckY),
            end = Offset(size.width, puckY),
            strokeWidth = 1.dp.toPx()
        )

        // Puck outer glow
        drawCircle(
            color = puckColor.copy(alpha = 0.25f),
            radius = puckRadius + 2.dp.toPx(),
            center = Offset(puckX, puckY)
        )
        // Puck body
        drawCircle(
            color = puckColor,
            radius = puckRadius,
            center = Offset(puckX, puckY)
        )
        // Puck center pip
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = Offset(puckX, puckY)
        )
    }
}
