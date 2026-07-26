package com.syntona.util

import kotlin.math.roundToLong

/**
 * Formats a duration in seconds as `MM:SS.mmm`, for example `01:23.456`.
 *
 * Minutes are not wrapped at 60, so a long recording reads as `75:00.000` rather than rolling
 * over to an hours field. Negative values are clamped to zero.
 */
fun formatMinutesSecondsMillis(seconds: Double): String {
    if (seconds.isNaN()) return "00:00.000"
    val totalMillis = (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    val minutes = totalMillis / 60_000L
    val secs = (totalMillis / 1000L) % 60L
    val millis = totalMillis % 1000L
    return "${minutes.toString().padStart(2, '0')}:" +
        "${secs.toString().padStart(2, '0')}." +
        millis.toString().padStart(3, '0')
}
