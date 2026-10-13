package com.syntona.dataflow.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Utility for mapping normalized widget positions (fraction in [0, 1]) to values
 * in a [min, max] range using linear or exponential tapers.
 *
 * For exponential tapers where the range includes zero, a linear taper is used in the
 * region of the zero crossing, not exceeding 1% of the manual drag range (fraction interval <= 0.01).
 */
object ExponentialTaper {
    /**
     * Maximum fraction of the manual drag range allocated to the linear region around zero.
     * Does not exceed 1% (0.01).
     */
    const val LINEAR_REGION_FRACTION = 0.01

    /**
     * Ratio of the linear threshold to the range span (10^-3 = 60 dB dynamic range).
     */
    private const val ZERO_THRESHOLD_RATIO = 1e-3

    /**
     * Converts a normalized drag fraction [fraction] in [0, 1] to a value in [[min], [max]].
     */
    fun fractionToValue(fraction: Double, min: Double, max: Double, isExponential: Boolean): Double {
        if (min == max) return min
        val u = fraction.coerceIn(0.0, 1.0)
        if (!isExponential) {
            return min + u * (max - min)
        }

        if (min > max) {
            return fractionToValue(1.0 - u, max, min, isExponential = true)
        }

        // Case 1: Strictly positive (zero is NOT in range)
        if (min > 0.0) {
            val ratio = max / min
            return min * exp(u * ln(ratio))
        }

        // Case 2: Strictly negative (zero is NOT in range)
        if (max < 0.0) {
            val ratio = abs(max) / abs(min)
            return min * exp(u * ln(ratio))
        }

        // Case 3: min == 0.0 and max > 0.0 (zero is at the bottom of the range)
        if (min == 0.0 && max > 0.0) {
            val uLin = LINEAR_REGION_FRACTION
            val vLin = max * ZERO_THRESHOLD_RATIO
            return if (u <= uLin) {
                (u / uLin) * vLin
            } else {
                val expFraction = (u - uLin) / (1.0 - uLin)
                vLin * exp(expFraction * ln(max / vLin))
            }
        }

        // Case 4: min < 0.0 and max == 0.0 (zero is at the top of the range)
        if (min < 0.0 && max == 0.0) {
            val uLin = LINEAR_REGION_FRACTION
            val uBoundary = 1.0 - uLin
            val vLin = -min * ZERO_THRESHOLD_RATIO
            return if (u >= uBoundary) {
                -vLin * ((1.0 - u) / uLin)
            } else {
                val expFraction = u / uBoundary
                min * exp(expFraction * ln(vLin / -min))
            }
        }

        // Case 5: min < 0.0 and max > 0.0 (zero is strictly inside the range)
        val spanNeg = -min
        val spanPos = max
        val uZero = spanNeg / (spanNeg + spanPos)
        val halfLin = LINEAR_REGION_FRACTION / 2.0
        val u1 = (uZero - halfLin).coerceAtLeast(0.0)
        val u2 = (uZero + halfLin).coerceAtMost(1.0)
        val vLinPos = spanPos * ZERO_THRESHOLD_RATIO
        val vLinNeg = spanNeg * ZERO_THRESHOLD_RATIO

        return when {
            u in u1..u2 -> {
                if (u >= uZero) {
                    val frac = if (u2 > uZero) (u - uZero) / (u2 - uZero) else 0.0
                    frac * vLinPos
                } else {
                    val frac = if (uZero > u1) (uZero - u) / (uZero - u1) else 0.0
                    -frac * vLinNeg
                }
            }
            u > u2 -> {
                val expFrac = (u - u2) / (1.0 - u2)
                vLinPos * exp(expFrac * ln(spanPos / vLinPos))
            }
            else -> {
                val expFrac = u / u1
                min * exp(expFrac * ln(vLinNeg / spanNeg))
            }
        }
    }

    /**
     * Converts a value in [[min], [max]] to a normalized fraction in [0, 1].
     */
    fun valueToFraction(value: Double, min: Double, max: Double, isExponential: Boolean): Double {
        if (min == max) return 0.0
        if (!isExponential) {
            return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        }

        if (min > max) {
            return 1.0 - valueToFraction(value, max, min, isExponential = true)
        }

        val low = min
        val high = max
        val v = value.coerceIn(low, high)

        // Case 1: Strictly positive
        if (min > 0.0) {
            val ratio = max / min
            return (ln(v / min) / ln(ratio)).coerceIn(0.0, 1.0)
        }

        // Case 2: Strictly negative
        if (max < 0.0) {
            val ratio = abs(max) / abs(min)
            return (ln(v / min) / ln(ratio)).coerceIn(0.0, 1.0)
        }

        // Case 3: min == 0.0 and max > 0.0
        if (min == 0.0 && max > 0.0) {
            val uLin = LINEAR_REGION_FRACTION
            val vLin = max * ZERO_THRESHOLD_RATIO
            return if (v <= vLin) {
                if (vLin > 0.0) ((v / vLin) * uLin).coerceIn(0.0, uLin) else 0.0
            } else {
                val expFrac = (ln(v / vLin) / ln(max / vLin)).coerceIn(0.0, 1.0)
                (uLin + expFrac * (1.0 - uLin)).coerceIn(0.0, 1.0)
            }
        }

        // Case 4: min < 0.0 and max == 0.0
        if (min < 0.0 && max == 0.0) {
            val uLin = LINEAR_REGION_FRACTION
            val uBoundary = 1.0 - uLin
            val vLin = -min * ZERO_THRESHOLD_RATIO
            return if (v >= -vLin) {
                if (vLin > 0.0) (1.0 - ((-v) / vLin) * uLin).coerceIn(uBoundary, 1.0) else 1.0
            } else {
                val expFrac = (ln(v / min) / ln(vLin / -min)).coerceIn(0.0, 1.0)
                (expFrac * uBoundary).coerceIn(0.0, uBoundary)
            }
        }

        // Case 5: min < 0.0 and max > 0.0
        val spanNeg = -min
        val spanPos = max
        val uZero = spanNeg / (spanNeg + spanPos)
        val halfLin = LINEAR_REGION_FRACTION / 2.0
        val u1 = (uZero - halfLin).coerceAtLeast(0.0)
        val u2 = (uZero + halfLin).coerceAtMost(1.0)
        val vLinPos = spanPos * ZERO_THRESHOLD_RATIO
        val vLinNeg = spanNeg * ZERO_THRESHOLD_RATIO

        return when {
            v in -vLinNeg..vLinPos -> {
                if (v >= 0.0) {
                    val frac = if (vLinPos > 0.0) v / vLinPos else 0.0
                    uZero + frac * (u2 - uZero)
                } else {
                    val frac = if (vLinNeg > 0.0) (-v) / vLinNeg else 0.0
                    uZero - frac * (uZero - u1)
                }
            }
            v > vLinPos -> {
                val expFrac = (ln(v / vLinPos) / ln(spanPos / vLinPos)).coerceIn(0.0, 1.0)
                u2 + expFrac * (1.0 - u2)
            }
            else -> {
                val expFrac = (ln(v / min) / ln(vLinNeg / spanNeg)).coerceIn(0.0, 1.0)
                expFrac * u1
            }
        }.coerceIn(0.0, 1.0)
    }
}
