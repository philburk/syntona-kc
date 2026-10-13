package com.syntona.dataflow.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isExponential: Boolean = false,
    trackColor: Color = Color.Gray,
    fillColor: Color = Color(0xFF4FC3F7),
    thumbColor: Color = Color.White
) {
    var heightPx by remember { mutableStateOf(0f) }

    val latestRange by rememberUpdatedState(valueRange)
    val latestIsExponential by rememberUpdatedState(isExponential)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    fun yToValue(y: Float): Float {
        if (heightPx == 0f) return value
        val fraction = (1f - (y / heightPx)).coerceIn(0f, 1f).toDouble()
        val rStart = latestRange.start.toDouble()
        val rEnd = latestRange.endInclusive.toDouble()
        return com.syntona.dataflow.model.ExponentialTaper.fractionToValue(
            fraction,
            rStart,
            rEnd,
            latestIsExponential
        ).toFloat()
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    latestOnValueChange(yToValue(down.position.y))
                    drag(down.id) { change ->
                        change.consume()
                        latestOnValueChange(yToValue(change.position.y))
                    }
                }
            }
    ) {
        val thumbRadius = 12.dp.toPx()
        val trackWidth = 6.dp.toPx()
        val usableHeight = size.height - 2 * thumbRadius
        val fraction = com.syntona.dataflow.model.ExponentialTaper.valueToFraction(
            value.toDouble(),
            valueRange.start.toDouble(),
            valueRange.endInclusive.toDouble(),
            isExponential
        ).toFloat().coerceIn(0f, 1f)
        val thumbY = (1f - fraction) * usableHeight + thumbRadius
        val cx = size.width / 2

        // Background track
        drawLine(
            color = trackColor,
            start = Offset(cx, thumbRadius),
            end = Offset(cx, size.height - thumbRadius),
            strokeWidth = trackWidth
        )
        // Fill from thumb down to bottom
        drawLine(
            color = fillColor,
            start = Offset(cx, thumbY),
            end = Offset(cx, size.height - thumbRadius),
            strokeWidth = trackWidth
        )
        // Thumb
        drawCircle(color = thumbColor, radius = thumbRadius, center = Offset(cx, thumbY))
    }
}
