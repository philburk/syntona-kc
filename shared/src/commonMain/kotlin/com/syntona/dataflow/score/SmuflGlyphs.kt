package com.syntona.dataflow.score

/**
 * SMuFL (Standard Music Font Layout) glyph codepoints and Leland font engraving defaults.
 * Values are aligned with `leland_metadata.json` (`1.0` = 1 staff space; `em` = 4 staff spaces).
 */
object SmuflGlyphs {
    // Clefs
    const val G_CLEF = "\uE050"
    const val C_CLEF = "\uE05C"
    const val F_CLEF = "\uE062"

    // Time signature digits (U+E080..U+E089)
    const val TIME_SIG_ZERO = 0xE080

    fun timeSigDigit(digit: Int): String {
        val codePoint = TIME_SIG_ZERO + digit.coerceIn(0, 9)
        return codePoint.toChar().toString()
    }

    fun timeSigNumber(value: Int): String =
        value.toString().map { timeSigDigit(it - '0') }.joinToString("")

    // Noteheads
    const val NOTEHEAD_WHOLE = "\uE0A2"
    const val NOTEHEAD_HALF = "\uE0A3"
    const val NOTEHEAD_BLACK = "\uE0A4"

    // Flags
    const val FLAG_8TH_UP = "\uE240"
    const val FLAG_8TH_DOWN = "\uE241"
    const val FLAG_16TH_UP = "\uE242"
    const val FLAG_16TH_DOWN = "\uE243"

    // Augmentation dot
    const val AUGMENTATION_DOT = "\uE1E7"

    // Rests
    const val REST_WHOLE = "\uE4E3"
    const val REST_HALF = "\uE4E4"
    const val REST_QUARTER = "\uE4E5"
    const val REST_8TH = "\uE4E6"
    const val REST_16TH = "\uE4E7"

    // Accidentals
    const val ACCIDENTAL_FLAT = "\uE260"
    const val ACCIDENTAL_NATURAL = "\uE261"
    const val ACCIDENTAL_SHARP = "\uE262"

    // Engraving defaults from leland_metadata.json (in staff spaces)
    const val STAFF_LINE_THICKNESS = 0.11f
    const val STEM_THICKNESS = 0.10f
    const val LEGER_LINE_THICKNESS = 0.16f
    const val LEGER_LINE_EXTENSION = 0.33f
    const val THIN_BARLINE_THICKNESS = 0.18f
    const val THICK_BARLINE_THICKNESS = 0.55f
    const val THIN_THICK_SEPARATION = 0.37f

    // Glyph bounding widths & anchors from leland_metadata.json (in staff spaces)
    const val NOTEHEAD_WIDTH = 1.30f
    const val NOTEHEAD_WHOLE_WIDTH = 1.492f
    const val STEM_UP_ANCHOR_X = 1.30f
    const val STEM_UP_ANCHOR_Y = 0.16f
    const val STEM_DOWN_ANCHOR_X = 0.0f
    const val STEM_DOWN_ANCHOR_Y = -0.168f

    const val ACCIDENTAL_SHARP_WIDTH = 0.976f
    const val ACCIDENTAL_FLAT_WIDTH = 0.812f
    const val ACCIDENTAL_NATURAL_WIDTH = 0.684f
}
