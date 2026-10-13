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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// Knob spans from 7-o'clock (startAngle) to 5-o'clock, 270° total sweep.
// In Compose Canvas: 0° = East (3 o'clock), positive = clockwise.
// 7 o'clock = 135° from East; 5 o'clock = 45° from East (135° + 270° mod 360°).
private const val KNOB_START_ANGLE = 135f
private const val KNOB_SWEEP = 270f

@Composable
fun RotaryKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isExponential: Boolean = false,
    trackColor: Color = Color(0xFF3A3A3A),
    arcColor: Color = Color(0xFFE6A820),
    knobBodyColor: Color = Color(0xFF808080),
    needleColor: Color = Color(0xFFDD2222),
) {
    var accumX by remember { mutableStateOf(0.0) }
    // rememberUpdatedState lets the pointerInput lambda always read the latest
    // value without restarting the gesture handler on every recomposition.
    val latestValue by rememberUpdatedState(value)
    val latestRange by rememberUpdatedState(valueRange)
    val latestIsExponential by rememberUpdatedState(isExponential)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                accumX = 0.0
                var currentValue = latestValue
                drag(down.id) { change ->
                    change.consume()
                    val delta = change.position - change.previousPosition
                    accumX += delta.x
                    // Apply current sensitivity to this frame's delta.y only (incremental).
                    // Using total accumY would retroactively re-scale all past Y movement
                    // whenever X changes, causing value jumps when dragging horizontally.
                    val rStart = latestRange.start.toDouble()
                    val rEnd = latestRange.endInclusive.toDouble()
                    val currentFrac = com.syntona.dataflow.model.ExponentialTaper.valueToFraction(
                        currentValue.toDouble(),
                        rStart,
                        rEnd,
                        latestIsExponential
                    )
                    val deltaFrac = -delta.y.toDouble() / 400.0 * 10.0.pow(-accumX / 200.0)
                    val newFrac = (currentFrac + deltaFrac).coerceIn(0.0, 1.0)
                    currentValue = com.syntona.dataflow.model.ExponentialTaper.fractionToValue(
                        newFrac,
                        rStart,
                        rEnd,
                        latestIsExponential
                    ).toFloat()
                    latestOnValueChange(currentValue)
                }
            }
        }
    ) {
        val fraction = com.syntona.dataflow.model.ExponentialTaper.valueToFraction(
            value.toDouble(),
            valueRange.start.toDouble(),
            valueRange.endInclusive.toDouble(),
            isExponential
        ).toFloat().coerceIn(0f, 1f)

        val cx = size.width / 2f
        val cy = size.height / 2f
        val availableRadius = minOf(cx, cy) - 2.dp.toPx()
        val arcThickness = availableRadius * 0.20f
        val arcMidRadius = availableRadius - arcThickness / 2f
        val knobRadius = availableRadius - arcThickness - 1.dp.toPx()

        val arcTopLeft = Offset(cx - arcMidRadius, cy - arcMidRadius)
        val arcSize = Size(arcMidRadius * 2f, arcMidRadius * 2f)
        val arcStroke = Stroke(width = arcThickness, cap = StrokeCap.Square)

        // Background track arc
        drawArc(
            color = trackColor,
            startAngle = KNOB_START_ANGLE,
            sweepAngle = KNOB_SWEEP,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = arcStroke
        )

        // Value arc (amber)
        if (fraction > 0f) {
            drawArc(
                color = arcColor,
                startAngle = KNOB_START_ANGLE,
                sweepAngle = fraction * KNOB_SWEEP,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = arcStroke
            )
        }

        // Knob body — two-tone for subtle depth
        drawCircle(color = Color(0xFF484848), radius = knobRadius, center = Offset(cx, cy))
        drawCircle(
            color = knobBodyColor,
            radius = knobRadius * 0.88f,
            center = Offset(cx - knobRadius * 0.06f, cy - knobRadius * 0.06f)
        )

        // Needle
        val needleAngleRad = ((KNOB_START_ANGLE + fraction * KNOB_SWEEP) * PI / 180.0)
        val cosA = cos(needleAngleRad).toFloat()
        val sinA = sin(needleAngleRad).toFloat()
        drawLine(
            color = needleColor,
            start = Offset(cx + knobRadius * 0.18f * cosA, cy + knobRadius * 0.18f * sinA),
            end = Offset(cx + knobRadius * 0.78f * cosA, cy + knobRadius * 0.78f * sinA),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
