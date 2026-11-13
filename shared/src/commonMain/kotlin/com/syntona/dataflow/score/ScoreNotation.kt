package com.syntona.dataflow.score

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.modules.PianoRollTiming

enum class NoteLetter(val semitoneOffset: Int, val display: String) {
    C(0, "C"),
    D(2, "D"),
    E(4, "E"),
    F(5, "F"),
    G(7, "G"),
    A(9, "A"),
    B(11, "B"),
}

enum class Accidental(val alter: Int, val glyph: String, val symbol: String) {
    FLAT(-1, SmuflGlyphs.ACCIDENTAL_FLAT, "b"),
    NATURAL(0, SmuflGlyphs.ACCIDENTAL_NATURAL, "n"),
    SHARP(1, SmuflGlyphs.ACCIDENTAL_SHARP, "#"),
}

/** Diatonic pitch spelling separating letter+octave from chromatic alteration. */
data class SpelledPitch(
    val letter: NoteLetter,
    val alter: Int = 0,
    val octave: Int = 4,
) {
    val midiPitch: Int get() = (octave + 1) * 12 + letter.semitoneOffset + alter

    /** Absolute diatonic step (`C4` = 28). */
    val diatonicStep: Int get() = octave * 7 + letter.ordinal

    fun displayName(): String {
        val suffix = when (alter) {
            1 -> "#"
            -1 -> "b"
            else -> ""
        }
        return "${letter.display}$suffix$octave"
    }
}

enum class Clef(
    val glyph: String,
    /** Line index from bottom (0 = bottom line, 4 = top line) where the clef glyph anchor sits. */
    val glyphLineFromBottom: Int,
    /** Diatonic step of the bottom staff line (`line 0`). */
    val bottomLineDiatonicStep: Int,
) {
    TREBLE(
        glyph = SmuflGlyphs.G_CLEF,
        glyphLineFromBottom = 1, // G4 sits on 2nd line from bottom
        bottomLineDiatonicStep = 4 * 7 + NoteLetter.E.ordinal, // E4
    );

    /** Staff step offset relative to the bottom line (`0` = bottom line, `4` = middle line `B4`, `8` = top line `F5`). */
    fun staffStepFromBottom(pitch: SpelledPitch): Int = pitch.diatonicStep - bottomLineDiatonicStep
}

enum class KeySignature(val fifths: Int, val label: String) {
    C_FLAT(-7, "Cb Maj (7b)"),
    G_FLAT(-6, "Gb Maj (6b)"),
    D_FLAT(-5, "Db Maj (5b)"),
    A_FLAT(-4, "Ab Maj (4b)"),
    E_FLAT(-3, "Eb Maj (3b)"),
    B_FLAT(-2, "Bb Maj (2b)"),
    F_MAJOR(-1, "F Maj (1b)"),
    C_MAJOR(0, "C Maj (0)"),
    G_MAJOR(1, "G Maj (1#)"),
    D_MAJOR(2, "D Maj (2#)"),
    A_MAJOR(3, "A Maj (3#)"),
    E_MAJOR(4, "E Maj (4#)"),
    B_MAJOR(5, "B Maj (5#)"),
    F_SHARP(6, "F# Maj (6#)"),
    C_SHARP(7, "C# Maj (7#)");

    /** Default chromatic alteration implied by this key signature for [letter]. */
    fun defaultAlter(letter: NoteLetter): Int {
        if (fifths > 0) {
            for (i in 0 until fifths) {
                if (SHARP_ORDER[i] == letter) return 1
            }
        } else if (fifths < 0) {
            for (i in 0 until -fifths) {
                if (FLAT_ORDER[i] == letter) return -1
            }
        }
        return 0
    }

    /** Ordered staff steps (from bottom line E4 = 0) for key signature glyphs in treble clef. */
    fun trebleStaffSteps(): List<Int> {
        return if (fifths > 0) {
            TREBLE_SHARP_STEPS.take(fifths)
        } else if (fifths < 0) {
            TREBLE_FLAT_STEPS.take(-fifths)
        } else {
            emptyList()
        }
    }

    companion object {
        private val SHARP_ORDER = listOf(
            NoteLetter.F, NoteLetter.C, NoteLetter.G, NoteLetter.D,
            NoteLetter.A, NoteLetter.E, NoteLetter.B
        )
        private val FLAT_ORDER = SHARP_ORDER.reversed()

        // Standard treble-clef sharp positions (F5, C5, G5, D5, A4, E5, B4) relative to bottom line E4
        private val TREBLE_SHARP_STEPS = listOf(8, 5, 9, 6, 3, 7, 4)

        // Standard treble-clef flat positions (B4, E5, A4, D5, G4, C5, F4) relative to bottom line E4
        private val TREBLE_FLAT_STEPS = listOf(4, 7, 3, 6, 2, 5, 1)

        val COMMON_PHASE1_PRESETS = listOf(
            F_MAJOR, C_MAJOR, G_MAJOR, D_MAJOR, A_MAJOR, B_FLAT, E_FLAT
        )
    }
}

data class TimeSignature(val numerator: Int = 4, val denominator: Int = 4) {
    val label: String get() = "$numerator/$denominator"

    companion object {
        val COMMON_TIME = TimeSignature(4, 4)
        val THREE_FOUR = TimeSignature(3, 4)
        val TWO_FOUR = TimeSignature(2, 4)
        val SIX_EIGHT = TimeSignature(6, 8)
        val PRESETS = listOf(COMMON_TIME, THREE_FOUR, TWO_FOUR, SIX_EIGHT)
    }
}

enum class NoteDuration(
    val ticks: Int,
    val noteheadGlyph: String,
    val restGlyph: String,
    val hasStem: Boolean,
    val flagCount: Int,
) {
    WHOLE(PianoRollTiming.TPB * 4, SmuflGlyphs.NOTEHEAD_WHOLE, SmuflGlyphs.REST_WHOLE, false, 0),
    HALF(PianoRollTiming.TPB * 2, SmuflGlyphs.NOTEHEAD_HALF, SmuflGlyphs.REST_HALF, true, 0),
    QUARTER(PianoRollTiming.TPB, SmuflGlyphs.NOTEHEAD_BLACK, SmuflGlyphs.REST_QUARTER, true, 0),
    EIGHTH(PianoRollTiming.TPB / 2, SmuflGlyphs.NOTEHEAD_BLACK, SmuflGlyphs.REST_8TH, true, 1),
    SIXTEENTH(PianoRollTiming.TPB / 4, SmuflGlyphs.NOTEHEAD_BLACK, SmuflGlyphs.REST_16TH, true, 2);

    fun totalTicks(isDotted: Boolean): Int = if (isDotted) ticks + ticks / 2 else ticks
}

sealed interface ScoreItem {
    val duration: NoteDuration
    val isDotted: Boolean

    val totalTicks: Int get() = duration.totalTicks(isDotted)

    data class Note(
        val pitch: SpelledPitch,
        override val duration: NoteDuration,
        override val isDotted: Boolean = false,
        val amplitude: Double = 0.25,
    ) : ScoreItem

    data class Rest(
        override val duration: NoteDuration,
        override val isDotted: Boolean = false,
    ) : ScoreItem
}

data class ScoreMeasure(
    val number: Int,
    val items: List<ScoreItem>,
)

/** Observable score document for Phase 1 single-stave notation rendering & playback. */
class ScoreData {
    var clef by mutableStateOf(Clef.TREBLE)
    var keySignature by mutableStateOf(KeySignature.D_MAJOR)
    var timeSignature by mutableStateOf(TimeSignature.COMMON_TIME)
    val measures = mutableStateListOf<ScoreMeasure>()

    init {
        loadDefaultSample()
    }

    fun loadDefaultSample() {
        clef = Clef.TREBLE
        keySignature = KeySignature.D_MAJOR
        timeSignature = TimeSignature.COMMON_TIME
        measures.clear()
        measures.addAll(defaultPhase1Measures())
    }

    companion object {
        /** Sample stave demonstrating key signature, time signature, and all common note & rest durations. */
        fun defaultPhase1Measures(): List<ScoreMeasure> = listOf(
            // Measure 1 (4/4): Quarter, Quarter, Eighth, Eighth, Quarter (shows D major F# in key sig & C# next bar)
            ScoreMeasure(
                number = 1,
                items = listOf(
                    ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER),
                    ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER),
                    ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 4), NoteDuration.EIGHTH),
                    ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.EIGHTH),
                    ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.QUARTER),
                ),
            ),
            // Measure 2 (4/4): Half (middle line B4, stem down), Quarter with inline accidental (G#4), Quarter Rest
            ScoreMeasure(
                number = 2,
                items = listOf(
                    ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.HALF),
                    ScoreItem.Note(SpelledPitch(NoteLetter.G, 1, 4), NoteDuration.QUARTER),
                    ScoreItem.Rest(NoteDuration.QUARTER),
                ),
            ),
            // Measure 3 (4/4): Dotted Quarter (C#5), Eighth (D5), Eighth on upper ledger (A5), Eighth Rest, 2x 16th + 16th Rest
            ScoreMeasure(
                number = 3,
                items = listOf(
                    ScoreItem.Note(SpelledPitch(NoteLetter.C, 1, 5), NoteDuration.QUARTER, isDotted = true),
                    ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.EIGHTH),
                    ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 5), NoteDuration.EIGHTH),
                    ScoreItem.Rest(NoteDuration.EIGHTH),
                    ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 5), NoteDuration.SIXTEENTH),
                    ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.SIXTEENTH),
                    ScoreItem.Note(SpelledPitch(NoteLetter.C, 1, 5), NoteDuration.SIXTEENTH),
                    ScoreItem.Rest(NoteDuration.SIXTEENTH),
                ),
            ),
            // Measure 4 (4/4): Middle C ledger line Half Note + Half Rest
            ScoreMeasure(
                number = 4,
                items = listOf(
                    ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.HALF),
                    ScoreItem.Rest(NoteDuration.HALF),
                ),
            ),
            // Measure 5 (4/4): Whole Note
            ScoreMeasure(
                number = 5,
                items = listOf(
                    ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.WHOLE),
                ),
            ),
            // Measure 6 (4/4): Whole Rest
            ScoreMeasure(
                number = 6,
                items = listOf(
                    ScoreItem.Rest(NoteDuration.WHOLE),
                ),
            ),
        )
    }
}
