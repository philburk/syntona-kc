package com.syntona.dataflow.modules

import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Utilities for calculating beat subdivisions, snapping, and ruler intervals.
 */
object TimelineBeatUtils {
    /**
     * Minimum distance in DP between visible subdivisions to ensure they are
     * as small as possible while still being easy to navigate and move with the mouse.
     */
    const val MIN_SUBDIVISION_SPACING_DP = 8.0

    /**
     * Minimum distance in DP between beat number text labels in the ruler to avoid collisions.
     */
    const val MIN_LABEL_SPACING_DP = 35.0

    /**
     * Calculates the smallest visible beat subdivision at the given pixel-per-beat scale.
     * Subdivisions are powers of 2: ..., 1/32, 1/16, 1/8, 1/4, 1/2, 1, 2, 4, 8, ...
     *
     * @param pxPerUnit Pixels per beat unit (including zoom and display density).
     * @param density Screen density factor.
     * @return The subdivision interval as a Double (e.g. 0.25 for 1/4 beat).
     */
    fun calculateSmallestVisibleSubdivision(pxPerUnit: Double, density: Float): Double {
        if (pxPerUnit <= 0.0) return 1.0
        val minSpacingPx = MIN_SUBDIVISION_SPACING_DP * density
        val ratio = minSpacingPx / pxPerUnit
        val k = ceil(log2(ratio)).toInt().coerceIn(-6, 6)
        return 2.0.pow(k.toDouble())
    }

    /**
     * Snaps a raw time value to the nearest multiple of [subdivision].
     * Uses exact integer arithmetic on denominators for fractional subdivisions.
     */
    fun snapToSubdivision(time: Double, subdivision: Double): Double {
        if (subdivision <= 0.0) return time
        return if (subdivision < 1.0) {
            val d = (1.0 / subdivision).roundToLong()
            if (d > 0) {
                (time * d).roundToLong().toDouble() / d.toDouble()
            } else {
                (time / subdivision).roundToLong().toDouble() * subdivision
            }
        } else {
            (time / subdivision).roundToLong().toDouble() * subdivision
        }
    }

    /**
     * Calculates the interval between beat number labels so they do not overlap.
     * Returns a whole-beat power of 2 (1, 2, 4, 8, 16, 32, ...).
     */
    fun calculateLabelInterval(pxPerUnit: Double, density: Float): Double {
        if (pxPerUnit <= 0.0) return 1.0
        val minLabelSpacingPx = MIN_LABEL_SPACING_DP * density
        val ratio = minLabelSpacingPx / pxPerUnit
        val k = ceil(log2(ratio)).toInt().coerceAtLeast(0).coerceAtMost(6)
        return 2.0.pow(k.toDouble())
    }

    /**
     * Formats a beat value for display in the ruler.
     */
    fun formatBeatLabel(beat: Double): String {
        return if (beat % 1.0 == 0.0) {
            "${beat.toInt()}"
        } else {
            val rounded = (beat * 100.0).roundToLong() / 100.0
            "${rounded}"
        }
    }
}
