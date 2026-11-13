package com.syntona.dataflow.score

import kotlin.math.roundToInt

/**
 * Palette categories shown in the left-edge palette strip of [ScoreEditor].
 * Future phases can append additional entries (e.g., Articulations, Dynamics, Clefs).
 */
enum class ScorePaletteKind(
    val title: String,
    val buttonLabel: String,
) {
    NOTES_RESTS(
        title = "Notes/Rests",
        buttonLabel = "Notes/\nRests",
    ),
    KEY_SIGNATURES(
        title = "Key Signatures",
        buttonLabel = "Key",
    ),
}

/**
 * A selectable entry inside the Notes/Rests popup palette.
 */
data class ScoreNoteRestOption(
    val duration: NoteDuration,
    val isRest: Boolean,
    val glyph: String,
    val shortcutChar: Char,
    val label: String,
)

/**
 * A selectable entry inside the Key Signatures popup palette.
 */
data class ScoreKeyOption(
    val keySignature: KeySignature,
    val shortName: String,
    val countLabel: String,
)

/**
 * Definitions for popup score palettes.
 */
object ScorePalettes {
    val durations: List<NoteDuration> = listOf(
        NoteDuration.WHOLE,
        NoteDuration.HALF,
        NoteDuration.QUARTER,
        NoteDuration.EIGHTH,
        NoteDuration.SIXTEENTH,
        NoteDuration.THIRTY_SECOND,
    )

    val noteOptions: List<ScoreNoteRestOption> = durations.map { dur ->
        ScoreNoteRestOption(
            duration = dur,
            isRest = false,
            glyph = dur.noteSymbolGlyph,
            shortcutChar = dur.shortcutChar,
            label = "${dur.displayLabel} Note",
        )
    }

    val restOptions: List<ScoreNoteRestOption> = durations.map { dur ->
        ScoreNoteRestOption(
            duration = dur,
            isRest = true,
            glyph = dur.restGlyph,
            shortcutChar = dur.shortcutChar,
            label = "${dur.displayLabel} Rest",
        )
    }

    val sharpKeyOptions: List<ScoreKeyOption> = listOf(
        ScoreKeyOption(KeySignature.C_MAJOR, "C", "0"),
        ScoreKeyOption(KeySignature.G_MAJOR, "G", "1#"),
        ScoreKeyOption(KeySignature.D_MAJOR, "D", "2#"),
        ScoreKeyOption(KeySignature.A_MAJOR, "A", "3#"),
        ScoreKeyOption(KeySignature.E_MAJOR, "E", "4#"),
        ScoreKeyOption(KeySignature.B_MAJOR, "B", "5#"),
        ScoreKeyOption(KeySignature.F_SHARP, "F#", "6#"),
        ScoreKeyOption(KeySignature.C_SHARP, "C#", "7#"),
    )

    val flatKeyOptions: List<ScoreKeyOption> = listOf(
        ScoreKeyOption(KeySignature.F_MAJOR, "F", "1b"),
        ScoreKeyOption(KeySignature.B_FLAT, "Bb", "2b"),
        ScoreKeyOption(KeySignature.E_FLAT, "Eb", "3b"),
        ScoreKeyOption(KeySignature.A_FLAT, "Ab", "4b"),
        ScoreKeyOption(KeySignature.D_FLAT, "Db", "5b"),
        ScoreKeyOption(KeySignature.G_FLAT, "Gb", "6b"),
        ScoreKeyOption(KeySignature.C_FLAT, "Cb", "7b"),
    )
}

/**
 * Pure geometry helpers for [ScoreHorizontalScrollBar].
 */
object ScoreScrollMath {
    const val MIN_THUMB_FRACTION = 0.05f

    data class ThumbMetrics(
        val thumbX: Float,
        val thumbWidth: Float,
        val trackTravel: Float,
        val maxScrollPx: Float,
    )

    fun computeThumbMetrics(
        trackWidthPx: Float,
        scrollPx: Float,
        maxScrollPx: Float,
    ): ThumbMetrics {
        if (trackWidthPx <= 0f) {
            return ThumbMetrics(thumbX = 0f, thumbWidth = 0f, trackTravel = 0f, maxScrollPx = 0f)
        }
        val safeMax = maxScrollPx.coerceAtLeast(0f)
        val totalWidth = trackWidthPx + safeMax
        val widthFraction = if (totalWidth > 0f) {
            (trackWidthPx / totalWidth).coerceIn(MIN_THUMB_FRACTION, 1f)
        } else {
            1f
        }
        val thumbWidth = trackWidthPx * widthFraction
        val trackTravel = (trackWidthPx - thumbWidth).coerceAtLeast(0f)
        val scrollFraction = if (safeMax > 0f) {
            (scrollPx / safeMax).coerceIn(0f, 1f)
        } else {
            0f
        }
        return ThumbMetrics(
            thumbX = trackTravel * scrollFraction,
            thumbWidth = thumbWidth,
            trackTravel = trackTravel,
            maxScrollPx = safeMax,
        )
    }

    fun scrollForThumbDrag(
        startScrollPx: Float,
        deltaX: Float,
        trackTravelPx: Float,
        maxScrollPx: Float,
    ): Int {
        if (maxScrollPx <= 0f || trackTravelPx <= 0f) return 0
        val dScroll = (deltaX / trackTravelPx) * maxScrollPx
        return (startScrollPx + dScroll).roundToInt().coerceIn(0, maxScrollPx.roundToInt())
    }

    fun scrollForTrackClick(
        clickX: Float,
        thumbWidthPx: Float,
        trackTravelPx: Float,
        maxScrollPx: Float,
    ): Int {
        if (maxScrollPx <= 0f || trackTravelPx <= 0f) return 0
        val fraction = ((clickX - thumbWidthPx * 0.5f) / trackTravelPx).coerceIn(0f, 1f)
        return (fraction * maxScrollPx).roundToInt().coerceIn(0, maxScrollPx.roundToInt())
    }
}
