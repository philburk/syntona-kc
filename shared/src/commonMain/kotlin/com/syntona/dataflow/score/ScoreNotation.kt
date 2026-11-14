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

    fun pitchClassName(): String {
        val suffix = when (alter) {
            1 -> "#"
            -1 -> "b"
            else -> ""
        }
        return "${letter.display}$suffix"
    }

    fun displayName(): String = "${pitchClassName()}$octave"

    /** Formats pitch name and MIDI number, e.g. `"C#4, 61"`. */
    fun pitchDisplay(): String = "${displayName()}, $midiPitch"

    companion object {
        private val NATURAL_BY_PC = mapOf(
            0 to NoteLetter.C,
            2 to NoteLetter.D,
            4 to NoteLetter.E,
            5 to NoteLetter.F,
            7 to NoteLetter.G,
            9 to NoteLetter.A,
            11 to NoteLetter.B,
        )
        private val SHARP_PC_LETTERS = mapOf(
            1 to NoteLetter.C,
            3 to NoteLetter.D,
            6 to NoteLetter.F,
            8 to NoteLetter.G,
            10 to NoteLetter.A,
        )
        private val FLAT_PC_LETTERS = mapOf(
            1 to NoteLetter.D,
            3 to NoteLetter.E,
            6 to NoteLetter.G,
            8 to NoteLetter.A,
            10 to NoteLetter.B,
        )

        /**
         * Maps an incoming MIDI pitch number (`0..127`) to a [SpelledPitch] guided by [keySignature].
         * Prefers pitches in the key's diatonic scale first; falls back to naturals for white keys
         * and sharps/flats matching the key signature direction for chromatic tones.
         */
        fun fromMidiPitch(midiPitch: Int, keySignature: KeySignature): SpelledPitch {
            val clamped = midiPitch.coerceIn(0, 127)
            val baseOctave = clamped / 12 - 1

            for (oct in (baseOctave - 1)..(baseOctave + 1)) {
                for (letter in NoteLetter.entries) {
                    val alter = keySignature.defaultAlter(letter)
                    val candidateMidi = (oct + 1) * 12 + letter.semitoneOffset + alter
                    if (candidateMidi == clamped) {
                        return SpelledPitch(letter = letter, alter = alter, octave = oct)
                    }
                }
            }

            val pc = clamped.mod(12)
            NATURAL_BY_PC[pc]?.let { natural ->
                return SpelledPitch(letter = natural, alter = 0, octave = baseOctave)
            }
            return if (keySignature.fifths < 0) {
                SpelledPitch(letter = FLAT_PC_LETTERS.getValue(pc), alter = -1, octave = baseOctave)
            } else {
                SpelledPitch(letter = SHARP_PC_LETTERS.getValue(pc), alter = 1, octave = baseOctave)
            }
        }
    }
}

enum class Clef(
    val glyph: String,
    /** Line index from bottom (0 = bottom line, 4 = top line) where the clef glyph anchor sits. */
    val glyphLineFromBottom: Int,
    /** Diatonic step of the bottom staff line (`line 0`). */
    val bottomLineDiatonicStep: Int,
    val label: String,
) {
    TREBLE(
        glyph = SmuflGlyphs.G_CLEF,
        glyphLineFromBottom = 1, // G4 sits on 2nd line from bottom
        bottomLineDiatonicStep = 4 * 7 + NoteLetter.E.ordinal, // E4
        label = "Treble",
    ),
    BASS(
        glyph = SmuflGlyphs.F_CLEF,
        glyphLineFromBottom = 3, // F3 sits on 4th line from bottom
        bottomLineDiatonicStep = 2 * 7 + NoteLetter.G.ordinal, // G2
        label = "Bass",
    ),
    ALTO(
        glyph = SmuflGlyphs.C_CLEF,
        glyphLineFromBottom = 2, // C4 sits on 3rd (middle) line
        bottomLineDiatonicStep = 3 * 7 + NoteLetter.F.ordinal, // F3
        label = "Alto",
    ),
    TENOR(
        glyph = SmuflGlyphs.C_CLEF,
        glyphLineFromBottom = 3, // C4 sits on 4th line from bottom
        bottomLineDiatonicStep = 3 * 7 + NoteLetter.D.ordinal, // D3
        label = "Tenor",
    ),
    TREBLE_8VB(
        glyph = SmuflGlyphs.G_CLEF_8VB,
        glyphLineFromBottom = 1, // G3 sits on 2nd line from bottom
        bottomLineDiatonicStep = 3 * 7 + NoteLetter.E.ordinal, // E3
        label = "Treble 8vb",
    ),
    TREBLE_8VA(
        glyph = SmuflGlyphs.G_CLEF_8VA,
        glyphLineFromBottom = 1, // G5 sits on 2nd line from bottom
        bottomLineDiatonicStep = 5 * 7 + NoteLetter.E.ordinal, // E5
        label = "Treble 8va",
    ),
    BASS_8VB(
        glyph = SmuflGlyphs.F_CLEF_8VB,
        glyphLineFromBottom = 3, // F2 sits on 4th line from bottom
        bottomLineDiatonicStep = 1 * 7 + NoteLetter.G.ordinal, // G1
        label = "Bass 8vb",
    ),
    BASS_8VA(
        glyph = SmuflGlyphs.F_CLEF_8VA,
        glyphLineFromBottom = 3, // F4 sits on 4th line from bottom
        bottomLineDiatonicStep = 3 * 7 + NoteLetter.G.ordinal, // G3
        label = "Bass 8va",
    );

    /** Staff step offset relative to the bottom line (`0` = bottom line, `4` = middle line `B4`, `8` = top line `F5`). */
    fun staffStepFromBottom(pitch: SpelledPitch): Int = pitch.diatonicStep - bottomLineDiatonicStep

    /** Converts a staff step offset relative to the bottom line into a [SpelledPitch]. */
    fun pitchForStaffStep(
        stepFromBottom: Int,
        keySignature: KeySignature,
        alterOverride: Int? = null,
    ): SpelledPitch {
        val diatonicStep = bottomLineDiatonicStep + stepFromBottom
        val octave = diatonicStep.floorDiv(7)
        val letter = NoteLetter.entries[diatonicStep.mod(7)]
        val alter = alterOverride ?: keySignature.defaultAlter(letter)
        return SpelledPitch(letter = letter, alter = alter, octave = octave)
    }
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

    /** Ordered staff steps (from bottom line = 0) for key signature glyphs in [clef]. */
    fun staffSteps(clef: Clef): List<Int> {
        val (sharps, flats) = when (clef) {
            Clef.TREBLE, Clef.TREBLE_8VB, Clef.TREBLE_8VA -> TREBLE_SHARP_STEPS to TREBLE_FLAT_STEPS
            Clef.BASS, Clef.BASS_8VB, Clef.BASS_8VA -> BASS_SHARP_STEPS to BASS_FLAT_STEPS
            Clef.ALTO -> ALTO_SHARP_STEPS to ALTO_FLAT_STEPS
            Clef.TENOR -> TENOR_SHARP_STEPS to TENOR_FLAT_STEPS
        }
        return if (fifths > 0) {
            sharps.take(fifths)
        } else if (fifths < 0) {
            flats.take(-fifths)
        } else {
            emptyList()
        }
    }

    /** Ordered staff steps (from bottom line E4 = 0) for key signature glyphs in treble clef. */
    fun trebleStaffSteps(): List<Int> = staffSteps(Clef.TREBLE)

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

        // Standard bass-clef positions relative to bottom line G2
        private val BASS_SHARP_STEPS = listOf(6, 3, 7, 4, 1, 5, 2)
        private val BASS_FLAT_STEPS = listOf(2, 5, 1, 4, 0, 3, -1)

        // Standard alto-clef positions relative to bottom line F3
        private val ALTO_SHARP_STEPS = listOf(7, 4, 8, 5, 2, 6, 3)
        private val ALTO_FLAT_STEPS = listOf(3, 6, 2, 5, 1, 4, 0)

        // Standard tenor-clef positions relative to bottom line D3
        private val TENOR_SHARP_STEPS = listOf(2, 6, 3, 7, 4, 8, 5)
        private val TENOR_FLAT_STEPS = listOf(5, 8, 4, 7, 3, 6, 2)

        val COMMON_PHASE1_PRESETS = listOf(
            F_MAJOR, C_MAJOR, G_MAJOR, D_MAJOR, A_MAJOR, B_FLAT, E_FLAT
        )
    }
}

data class TimeSignature(val numerator: Int = 4, val denominator: Int = 4) {
    val label: String get() = "$numerator/$denominator"

    /** Total ticks allowed in one measure for this time signature. */
    val measureCapacityTicks: Int get() = (numerator * (PianoRollTiming.TPB * 4)) / denominator

    companion object {
        val VALID_DENOMINATORS = setOf(1, 2, 4, 8, 16, 32)
        const val MIN_NUMERATOR = 1
        const val MAX_NUMERATOR = 32

        val COMMON_TIME = TimeSignature(4, 4)
        val THREE_FOUR = TimeSignature(3, 4)
        val TWO_FOUR = TimeSignature(2, 4)
        val TWO_TWO = TimeSignature(2, 2)
        val FIVE_FOUR = TimeSignature(5, 4)
        val THREE_EIGHT = TimeSignature(3, 8)
        val SIX_EIGHT = TimeSignature(6, 8)
        val NINE_EIGHT = TimeSignature(9, 8)
        val TWELVE_EIGHT = TimeSignature(12, 8)
        val PRESETS = listOf(
            TWO_FOUR,
            THREE_FOUR,
            COMMON_TIME,
            FIVE_FOUR,
            TWO_TWO,
            THREE_EIGHT,
            SIX_EIGHT,
            NINE_EIGHT,
            TWELVE_EIGHT,
        )

        fun isValid(numerator: Int, denominator: Int): Boolean =
            numerator in MIN_NUMERATOR..MAX_NUMERATOR && denominator in VALID_DENOMINATORS

        /** Parses strings such as `"3/4"` or `"12/8"`; returns `null` when invalid. */
        fun parse(text: String): TimeSignature? {
            val parts = text.trim().split('/')
            if (parts.size != 2) return null
            val num = parts[0].trim().toIntOrNull() ?: return null
            val den = parts[1].trim().toIntOrNull() ?: return null
            return if (isValid(num, den)) TimeSignature(num, den) else null
        }
    }
}

enum class NoteDuration(
    val ticks: Int,
    val noteheadGlyph: String,
    val noteSymbolGlyph: String,
    val restGlyph: String,
    val hasStem: Boolean,
    val flagCount: Int,
    val shortcutChar: Char,
    val displayLabel: String,
) {
    WHOLE(
        ticks = PianoRollTiming.TPB * 4,
        noteheadGlyph = SmuflGlyphs.NOTEHEAD_WHOLE,
        noteSymbolGlyph = SmuflGlyphs.NOTE_WHOLE,
        restGlyph = SmuflGlyphs.REST_WHOLE,
        hasStem = false,
        flagCount = 0,
        shortcutChar = 'w',
        displayLabel = "Whole",
    ),
    HALF(
        ticks = PianoRollTiming.TPB * 2,
        noteheadGlyph = SmuflGlyphs.NOTEHEAD_HALF,
        noteSymbolGlyph = SmuflGlyphs.NOTE_HALF_UP,
        restGlyph = SmuflGlyphs.REST_HALF,
        hasStem = true,
        flagCount = 0,
        shortcutChar = 'h',
        displayLabel = "Half",
    ),
    QUARTER(
        ticks = PianoRollTiming.TPB,
        noteheadGlyph = SmuflGlyphs.NOTEHEAD_BLACK,
        noteSymbolGlyph = SmuflGlyphs.NOTE_QUARTER_UP,
        restGlyph = SmuflGlyphs.REST_QUARTER,
        hasStem = true,
        flagCount = 0,
        shortcutChar = 'q',
        displayLabel = "Quarter",
    ),
    EIGHTH(
        ticks = PianoRollTiming.TPB / 2,
        noteheadGlyph = SmuflGlyphs.NOTEHEAD_BLACK,
        noteSymbolGlyph = SmuflGlyphs.NOTE_8TH_UP,
        restGlyph = SmuflGlyphs.REST_8TH,
        hasStem = true,
        flagCount = 1,
        shortcutChar = 'e',
        displayLabel = "Eighth",
    ),
    SIXTEENTH(
        ticks = PianoRollTiming.TPB / 4,
        noteheadGlyph = SmuflGlyphs.NOTEHEAD_BLACK,
        noteSymbolGlyph = SmuflGlyphs.NOTE_16TH_UP,
        restGlyph = SmuflGlyphs.REST_16TH,
        hasStem = true,
        flagCount = 2,
        shortcutChar = 's',
        displayLabel = "16th",
    ),
    THIRTY_SECOND(
        ticks = PianoRollTiming.TPB / 8,
        noteheadGlyph = SmuflGlyphs.NOTEHEAD_BLACK,
        noteSymbolGlyph = SmuflGlyphs.NOTE_32ND_UP,
        restGlyph = SmuflGlyphs.REST_32ND,
        hasStem = true,
        flagCount = 3,
        shortcutChar = 't',
        displayLabel = "32nd",
    );

    fun totalTicks(isDotted: Boolean): Int = if (isDotted) ticks + ticks / 2 else ticks
}

sealed interface ScoreItem {
    val duration: NoteDuration
    val isDotted: Boolean

    val totalTicks: Int get() = duration.totalTicks(isDotted)

    fun soundingPitches(): List<SpelledPitch> = when (this) {
        is Note -> listOf(pitch)
        is Chord -> pitches
        is Rest -> emptyList()
    }

    fun noteAmplitude(): Double = when (this) {
        is Note -> amplitude
        is Chord -> amplitude
        is Rest -> 0.0
    }

    fun noteStaccato(): Boolean = when (this) {
        is Note -> isStaccato
        is Chord -> isStaccato
        is Rest -> false
    }

    data class Note(
        val pitch: SpelledPitch,
        override val duration: NoteDuration,
        override val isDotted: Boolean = false,
        val amplitude: Double = 0.25,
        val isStaccato: Boolean = false,
    ) : ScoreItem

    /** Vertically aligned chord of two or more simultaneous pitches sharing one stem/duration. */
    data class Chord(
        val pitches: List<SpelledPitch>,
        override val duration: NoteDuration,
        override val isDotted: Boolean = false,
        val amplitude: Double = 0.25,
        val isStaccato: Boolean = false,
    ) : ScoreItem

    data class Rest(
        override val duration: NoteDuration,
        override val isDotted: Boolean = false,
    ) : ScoreItem
}

data class ScoreMeasure(
    val number: Int,
    val items: List<ScoreItem>,
    val keySignature: KeySignature? = null,
    val timeSignature: TimeSignature? = null,
    val clefChanges: Map<Int, Clef> = emptyMap(),
)

/** Identifies the opening clef (`0, 0`) or a mid-score clef change before `(measureIdx, itemIdx)`. */
data class ScoreClefLocation(
    val measureIdx: Int,
    val itemIdx: Int,
)

/**
 * Identifies an item slot or an individual notehead (`chordNoteIdx != null`) within a chord.
 */
data class ScoreItemLocation(
    val measureIdx: Int,
    val itemIdx: Int,
    val chordNoteIdx: Int? = null,
) {
    fun slotLocation(): ScoreItemLocation = if (chordNoteIdx == null) this else copy(chordNoteIdx = null)
}

/** Pure immutable helpers for editing [ScoreMeasure] lists. */
object ScoreEditing {
    fun renumber(measures: List<ScoreMeasure>): List<ScoreMeasure> {
        val nonEmptyOrSingle = if (measures.isEmpty()) listOf(ScoreMeasure(1, emptyList())) else measures
        return nonEmptyOrSingle.mapIndexed { idx, m ->
            if (m.number == idx + 1) m else m.copy(number = idx + 1)
        }
    }

    fun itemAt(measures: List<ScoreMeasure>, loc: ScoreItemLocation): ScoreItem? =
        measures.getOrNull(loc.measureIdx)?.items?.getOrNull(loc.itemIdx)

    fun pitchAt(measures: List<ScoreMeasure>, loc: ScoreItemLocation): SpelledPitch? = when (val item = itemAt(measures, loc)) {
        is ScoreItem.Note -> item.pitch
        is ScoreItem.Chord -> loc.chordNoteIdx?.let { item.pitches.getOrNull(it) }
        else -> null
    }

    /** Sorts chord pitches low-to-high by diatonic step then MIDI pitch, deduplicating identical steps. */
    fun normalizeChordPitches(pitches: List<SpelledPitch>): List<SpelledPitch> {
        val byStep = LinkedHashMap<Int, SpelledPitch>()
        for (p in pitches) {
            byStep[p.diatonicStep] = p
        }
        return byStep.values.sortedWith(compareBy<SpelledPitch> { it.diatonicStep }.thenBy { it.midiPitch })
    }

    fun itemFromPitches(
        pitches: List<SpelledPitch>,
        duration: NoteDuration,
        isDotted: Boolean = false,
        amplitude: Double = 0.25,
        isStaccato: Boolean = false,
    ): ScoreItem? {
        val sorted = normalizeChordPitches(pitches)
        return when {
            sorted.isEmpty() -> null
            sorted.size == 1 -> ScoreItem.Note(
                pitch = sorted.first(),
                duration = duration,
                isDotted = isDotted,
                amplitude = amplitude,
                isStaccato = isStaccato,
            )
            else -> ScoreItem.Chord(
                pitches = sorted,
                duration = duration,
                isDotted = isDotted,
                amplitude = amplitude,
                isStaccato = isStaccato,
            )
        }
    }

    /**
     * Adds [pitch] vertically aligned to the existing [ScoreItem.Note] or [ScoreItem.Chord] at
     * [slotLoc], returning the updated measures and the location of the added notehead.
     */
    fun addPitchToSlot(
        measures: List<ScoreMeasure>,
        slotLoc: ScoreItemLocation,
        pitch: SpelledPitch,
    ): Pair<List<ScoreMeasure>, ScoreItemLocation> {
        val slot = slotLoc.slotLocation()
        val item = itemAt(measures, slot) ?: return measures to slotLoc
        val (existingPitches, dur, dotted, amp, stacc) = when (item) {
            is ScoreItem.Note -> Tuple5(listOf(item.pitch), item.duration, item.isDotted, item.amplitude, item.isStaccato)
            is ScoreItem.Chord -> Tuple5(item.pitches, item.duration, item.isDotted, item.amplitude, item.isStaccato)
            is ScoreItem.Rest -> return measures to slotLoc
        }
        val combined = normalizeChordPitches(existingPitches + pitch)
        val newItem = itemFromPitches(combined, dur, dotted, amp, stacc) ?: return measures to slotLoc
        val newMeasures = replaceItem(measures, slot, newItem)
        val newLoc = if (newItem is ScoreItem.Chord) {
            val idx = newItem.pitches.indexOfFirst { it.diatonicStep == pitch.diatonicStep }
                .takeIf { it >= 0 }
            slot.copy(chordNoteIdx = idx)
        } else {
            slot
        }
        return newMeasures to newLoc
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    fun insertItem(
        measures: List<ScoreMeasure>,
        measureIdx: Int,
        itemIdx: Int,
        item: ScoreItem,
        appendNewMeasure: Boolean = false,
        shiftClefAtIndex: Boolean = false,
    ): Pair<List<ScoreMeasure>, ScoreItemLocation> {
        val updated = measures.toMutableList()
        if (appendNewMeasure || updated.isEmpty() || measureIdx >= updated.size) {
            val newMIdx = updated.size
            updated.add(ScoreMeasure(newMIdx + 1, listOf(item)))
            return renumber(updated) to ScoreItemLocation(newMIdx, 0)
        }
        val m = updated[measureIdx]
        val clampedIdx = itemIdx.coerceIn(0, m.items.size)
        val newItems = m.items.toMutableList().apply { add(clampedIdx, item) }
        val updatedClefs = if (m.clefChanges.isEmpty()) {
            m.clefChanges
        } else {
            buildMap {
                for ((k, clef) in m.clefChanges) {
                    val newK = if (k > clampedIdx || (shiftClefAtIndex && k > 0 && k == clampedIdx)) {
                        k + 1
                    } else {
                        k
                    }
                    put(newK, clef)
                }
            }
        }
        updated[measureIdx] = m.copy(items = newItems, clefChanges = updatedClefs)
        return renumber(updated) to ScoreItemLocation(measureIdx, clampedIdx)
    }

    fun replaceItem(
        measures: List<ScoreMeasure>,
        loc: ScoreItemLocation,
        item: ScoreItem,
    ): List<ScoreMeasure> {
        val m = measures.getOrNull(loc.measureIdx) ?: return measures
        if (loc.itemIdx !in m.items.indices) return measures
        val newItems = m.items.toMutableList().apply { set(loc.itemIdx, item) }
        return measures.toMutableList().apply { set(loc.measureIdx, m.copy(items = newItems)) }
    }

    fun setNotePitch(
        measures: List<ScoreMeasure>,
        loc: ScoreItemLocation,
        newPitch: SpelledPitch,
    ): List<ScoreMeasure> = setNotePitchAndTrackLocation(measures, loc, newPitch).first

    /**
     * Updates the pitch at [loc] (either a single [ScoreItem.Note] or an individual notehead
     * inside a [ScoreItem.Chord]) and returns the updated measures paired with the note's
     * post-sort [ScoreItemLocation].
     */
    fun setNotePitchAndTrackLocation(
        measures: List<ScoreMeasure>,
        loc: ScoreItemLocation,
        newPitch: SpelledPitch,
    ): Pair<List<ScoreMeasure>, ScoreItemLocation> {
        val slot = loc.slotLocation()
        return when (val item = itemAt(measures, slot)) {
            is ScoreItem.Note -> {
                if (item.pitch == newPitch) measures to slot
                else replaceItem(measures, slot, item.copy(pitch = newPitch)) to slot
            }
            is ScoreItem.Chord -> {
                val noteIdx = loc.chordNoteIdx ?: return measures to loc
                if (noteIdx !in item.pitches.indices) return measures to loc
                if (item.pitches[noteIdx] == newPitch) return measures to loc
                val rawList = item.pitches.toMutableList()
                rawList[noteIdx] = newPitch
                // Avoid collapsing another existing notehead while dragging past it cleanly: keep distinct steps
                val normalized = if (rawList.count { it.diatonicStep == newPitch.diatonicStep } > 1) {
                    val others = item.pitches.filterIndexed { idx, _ -> idx != noteIdx }
                    normalizeChordPitches(others + newPitch)
                } else {
                    rawList.sortedWith(compareBy<SpelledPitch> { it.diatonicStep }.thenBy { it.midiPitch })
                }
                val newItem = itemFromPitches(
                    normalized,
                    item.duration,
                    item.isDotted,
                    item.amplitude,
                    item.isStaccato,
                ) ?: return measures to loc
                val nextMeasures = replaceItem(measures, slot, newItem)
                val nextLoc = if (newItem is ScoreItem.Chord) {
                    val newIdx = newItem.pitches.indexOfFirst { it == newPitch }
                        .takeIf { it >= 0 } ?: newItem.pitches.indexOfFirst { it.diatonicStep == newPitch.diatonicStep }
                        .coerceAtLeast(0)
                    slot.copy(chordNoteIdx = newIdx)
                } else {
                    slot
                }
                nextMeasures to nextLoc
            }
            else -> measures to loc
        }
    }

    fun setAccidentalOnSelection(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
        alter: Int,
    ): List<ScoreMeasure> {
        if (selection.isEmpty()) return measures
        val bySlot = selection.groupBy { it.slotLocation() }
        var anyChanged = false
        val updated = measures.mapIndexed { mIdx, m ->
            var measureChanged = false
            val newItems = m.items.mapIndexed { iIdx, item ->
                val locs = bySlot[ScoreItemLocation(mIdx, iIdx)] ?: return@mapIndexed item
                when (item) {
                    is ScoreItem.Note -> if (item.pitch.alter != alter) {
                        measureChanged = true
                        anyChanged = true
                        item.copy(pitch = item.pitch.copy(alter = alter))
                    } else {
                        item
                    }
                    is ScoreItem.Chord -> {
                        val applyAll = locs.any { it.chordNoteIdx == null }
                        val targetIndices = if (applyAll) item.pitches.indices.toSet() else locs.mapNotNull { it.chordNoteIdx }.toSet()
                        var itemChanged = false
                        val nextPitches = item.pitches.mapIndexed { pIdx, p ->
                            if (pIdx in targetIndices && p.alter != alter) {
                                itemChanged = true
                                p.copy(alter = alter)
                            } else {
                                p
                            }
                        }
                        if (itemChanged) {
                            measureChanged = true
                            anyChanged = true
                            item.copy(pitches = nextPitches)
                        } else {
                            item
                        }
                    }
                    is ScoreItem.Rest -> item
                }
            }
            if (measureChanged) m.copy(items = newItems) else m
        }
        return if (anyChanged) updated else measures
    }

    /**
     * Updates the duration and augmentation dot on every selected note, chord, or rest.
     */
    fun setDurationOnSelection(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
        duration: NoteDuration,
        isDotted: Boolean,
    ): List<ScoreMeasure> {
        if (selection.isEmpty()) return measures
        val slots = selection.map { it.slotLocation() }.toSet()
        var anyChanged = false
        val updated = measures.mapIndexed { mIdx, m ->
            var measureChanged = false
            val newItems = m.items.mapIndexed { sIdx, item ->
                if (ScoreItemLocation(mIdx, sIdx) !in slots) {
                    item
                } else {
                    if (item.duration != duration || item.isDotted != isDotted) {
                        measureChanged = true
                        anyChanged = true
                        when (item) {
                            is ScoreItem.Note -> item.copy(duration = duration, isDotted = isDotted)
                            is ScoreItem.Chord -> item.copy(duration = duration, isDotted = isDotted)
                            is ScoreItem.Rest -> item.copy(duration = duration, isDotted = isDotted)
                        }
                    } else {
                        item
                    }
                }
            }
            if (measureChanged) m.copy(items = newItems) else m
        }
        return if (anyChanged) updated else measures
    }

    fun toggleStaccatoOnSelection(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
    ): List<ScoreMeasure> {
        if (selection.isEmpty()) return measures
        val slots = selection.map { it.slotLocation() }.toSet()
        val selectedSounding = slots.mapNotNull { slot ->
            when (val item = itemAt(measures, slot)) {
                is ScoreItem.Note -> item.isStaccato
                is ScoreItem.Chord -> item.isStaccato
                else -> null
            }
        }
        if (selectedSounding.isEmpty()) return measures
        val targetStaccato = !selectedSounding.all { it }
        var anyChanged = false
        val updated = measures.mapIndexed { mIdx, m ->
            var measureChanged = false
            val newItems = m.items.mapIndexed { iIdx, item ->
                if (ScoreItemLocation(mIdx, iIdx) !in slots) return@mapIndexed item
                val next = when (item) {
                    is ScoreItem.Note -> if (item.isStaccato != targetStaccato) item.copy(isStaccato = targetStaccato) else item
                    is ScoreItem.Chord -> if (item.isStaccato != targetStaccato) item.copy(isStaccato = targetStaccato) else item
                    is ScoreItem.Rest -> item
                }
                if (next !== item) {
                    measureChanged = true
                    anyChanged = true
                }
                next
            }
            if (measureChanged) m.copy(items = newItems) else m
        }
        return if (anyChanged) updated else measures
    }

    fun deleteItems(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
    ): List<ScoreMeasure> {
        if (selection.isEmpty()) return measures
        val byMeasure = selection.groupBy { it.measureIdx }
        val updated = measures.mapIndexed { mIdx, m ->
            val measureSelections = byMeasure[mIdx] ?: return@mapIndexed m
            val byItem = measureSelections.groupBy { it.itemIdx }
            val nextItems = ArrayList<ScoreItem>(m.items.size)
            val removedIndices = mutableListOf<Int>()
            for ((iIdx, item) in m.items.withIndex()) {
                val locs = byItem[iIdx]
                if (locs == null) {
                    nextItems.add(item)
                } else if (item is ScoreItem.Chord && locs.none { it.chordNoteIdx == null }) {
                    val removeSet = locs.mapNotNull { it.chordNoteIdx }.toSet()
                    val remaining = item.pitches.filterIndexed { pIdx, _ -> pIdx !in removeSet }
                    val reduced = itemFromPitches(
                        pitches = remaining,
                        duration = item.duration,
                        isDotted = item.isDotted,
                        amplitude = item.amplitude,
                        isStaccato = item.isStaccato,
                    )
                    if (reduced != null) {
                        nextItems.add(reduced)
                    } else {
                        removedIndices.add(iIdx)
                    }
                } else {
                    removedIndices.add(iIdx)
                }
            }
            val nextClefs = if (m.clefChanges.isEmpty() || removedIndices.isEmpty()) {
                m.clefChanges
            } else {
                buildMap {
                    for ((k, clef) in m.clefChanges.entries.sortedBy { it.key }) {
                        if (k == 0) {
                            put(0, clef)
                        } else {
                            val removedBefore = removedIndices.count { it < k }
                            val newK = (k - removedBefore).coerceAtLeast(0)
                            put(newK, clef)
                        }
                    }
                }
            }
            m.copy(items = nextItems, clefChanges = nextClefs)
        }
        return renumber(updated)
    }

    fun appendEmptyMeasure(measures: List<ScoreMeasure>): List<ScoreMeasure> =
        renumber(measures + ScoreMeasure(measures.size + 1, emptyList()))

    fun clearToSingleEmptyMeasure(): List<ScoreMeasure> =
        listOf(ScoreMeasure(1, emptyList()))

    /** Default cursor placement when step record turns on with no cursor placed yet. */
    fun defaultStepCursor(measures: List<ScoreMeasure>): ScoreCursorPosition {
        if (measures.isEmpty()) return ScoreCursorPosition(0, 0)
        val lastIdx = measures.lastIndex
        return ScoreCursorPosition(lastIdx, measures[lastIdx].items.size)
    }

    /**
     * Inserts [item] at [cursor] and advances the cursor cleanly.
     * When appending reaches or exceeds [measureCapacityTicks], the cursor advances to the
     * beginning of the next measure (appending a new empty measure if needed).
     */
    fun stepInsertItem(
        measures: List<ScoreMeasure>,
        cursor: ScoreCursorPosition,
        item: ScoreItem,
        measureCapacityTicks: Int,
    ): ScoreStepInsertResult {
        val normalized = renumber(measures)
        val mIdx = cursor.measureIdx.coerceIn(0, normalized.lastIndex)
        val iIdx = cursor.itemIdx.coerceIn(0, normalized[mIdx].items.size)
        val (insertedMeasures, loc) = insertItem(normalized, mIdx, iIdx, item)
        val measure = insertedMeasures[loc.measureIdx]
        val measureTicks = measure.items.sumOf { it.totalTicks }
        val atEndOfMeasure = loc.itemIdx == measure.items.lastIndex

        return if (atEndOfMeasure && measureTicks >= measureCapacityTicks) {
            val nextMeasureIdx = loc.measureIdx + 1
            val finalMeasures = if (nextMeasureIdx >= insertedMeasures.size) {
                appendEmptyMeasure(insertedMeasures)
            } else {
                insertedMeasures
            }
            ScoreStepInsertResult(
                measures = finalMeasures,
                insertedLocation = loc,
                nextCursor = ScoreCursorPosition(nextMeasureIdx, 0),
            )
        } else {
            ScoreStepInsertResult(
                measures = insertedMeasures,
                insertedLocation = loc,
                nextCursor = ScoreCursorPosition(loc.measureIdx, loc.itemIdx + 1),
            )
        }
    }

    /** Returns the [KeySignature] in effect during [measureIdx] (`0`-based). */
    fun effectiveKeyAtMeasure(
        measures: List<ScoreMeasure>,
        initialKey: KeySignature,
        measureIdx: Int,
    ): KeySignature {
        var effective = measures.firstOrNull()?.keySignature ?: initialKey
        val limit = measureIdx.coerceAtMost(measures.lastIndex)
        for (i in 1..limit) {
            measures[i].keySignature?.let { effective = it }
        }
        return effective
    }

    /**
     * Applies [newKey] to the first measure in [selectedMeasureIndices] and clears explicit
     * key signatures from any later selected measures, without altering any note pitch values.
     */
    fun setKeySignatureOnMeasures(
        initialKey: KeySignature,
        measures: List<ScoreMeasure>,
        selectedMeasureIndices: Set<Int>,
        newKey: KeySignature,
    ): Pair<KeySignature, List<ScoreMeasure>> {
        val normalized = renumber(measures)
        val sortedSelection = selectedMeasureIndices.filter { it in normalized.indices }.sorted()
        if (sortedSelection.isEmpty()) return initialKey to normalized

        val firstIdx = sortedSelection.first()
        val laterIndices = sortedSelection.drop(1).toSet()
        val nextInitialKey = if (firstIdx == 0) newKey else initialKey
        val updated = normalized.mapIndexed { idx, measure ->
            when {
                idx == firstIdx -> measure.copy(keySignature = if (idx == 0) null else newKey)
                idx in laterIndices -> measure.copy(keySignature = null)
                else -> measure
            }
        }
        return nextInitialKey to updated
    }

    /** Returns the [TimeSignature] in effect during [measureIdx] (`0`-based). */
    fun effectiveTimeAtMeasure(
        measures: List<ScoreMeasure>,
        initialTime: TimeSignature,
        measureIdx: Int,
    ): TimeSignature {
        var effective = measures.firstOrNull()?.timeSignature ?: initialTime
        val limit = measureIdx.coerceAtMost(measures.lastIndex)
        for (i in 1..limit) {
            measures[i].timeSignature?.let { effective = it }
        }
        return effective
    }

    /**
     * Inserts, updates, or removes (`newTime == null`) the time signature on the first measure in
     * [selectedMeasureIndices]. Measure 0's opening time signature cannot be removed.
     */
    fun setTimeSignatureOnMeasures(
        initialTime: TimeSignature,
        measures: List<ScoreMeasure>,
        selectedMeasureIndices: Set<Int>,
        newTime: TimeSignature?,
    ): Pair<TimeSignature, List<ScoreMeasure>> {
        val normalized = renumber(measures)
        val firstIdx = selectedMeasureIndices.filter { it in normalized.indices }.minOrNull()
            ?: return initialTime to normalized
        if (newTime == null && firstIdx == 0) {
            return initialTime to normalized
        }
        val nextInitialTime = if (firstIdx == 0 && newTime != null) newTime else initialTime
        val updated = normalized.mapIndexed { idx, measure ->
            if (idx == firstIdx) {
                measure.copy(timeSignature = if (idx == 0) null else newTime)
            } else {
                measure
            }
        }
        return nextInitialTime to updated
    }

    /**
     * Returns the [Clef] in effect at `(measureIdx, itemIdx)`.
     * [itemIdx] `0` includes a beat-1 clef change on [measureIdx]; `Int.MAX_VALUE` returns the clef
     * in effect at the end of [measureIdx].
     */
    fun effectiveClefAt(
        measures: List<ScoreMeasure>,
        initialClef: Clef,
        measureIdx: Int,
        itemIdx: Int = Int.MAX_VALUE,
    ): Clef {
        var effective = measures.firstOrNull()?.clefChanges?.get(0) ?: initialClef
        if (measures.isEmpty()) return effective
        val limitM = measureIdx.coerceIn(0, measures.lastIndex)
        for (m in 0..limitM) {
            val measure = measures[m]
            val limitI = if (m < limitM) Int.MAX_VALUE else itemIdx
            for ((idx, clef) in measure.clefChanges.entries.sortedBy { it.key }) {
                if (m == 0 && idx == 0) continue
                if (idx <= limitI) {
                    effective = clef
                }
            }
        }
        return effective
    }

    /** Returns `true` if [measures] has an explicit mid-score clef change stored at [location]. */
    fun hasExplicitClefChangeAt(
        measures: List<ScoreMeasure>,
        location: ScoreClefLocation,
    ): Boolean = measures.getOrNull(location.measureIdx)?.clefChanges?.containsKey(location.itemIdx) == true

    /**
     * Sets or removes (`newClef == null`) a clef change at [location] (`(0, 0)` sets opening clef).
     * Existing note pitches are preserved unchanged.
     */
    fun setClefChange(
        initialClef: Clef,
        measures: List<ScoreMeasure>,
        location: ScoreClefLocation,
        newClef: Clef?,
    ): Pair<Clef, List<ScoreMeasure>> {
        val normalized = renumber(measures)
        if (location.measureIdx !in normalized.indices) return initialClef to normalized
        if (location.measureIdx == 0 && location.itemIdx <= 0) {
            if (newClef == null) return initialClef to normalized
            val m0 = normalized[0]
            val nextMeasures = if (0 in m0.clefChanges) {
                val updatedClefs = m0.clefChanges.toMutableMap().apply { remove(0) }
                normalized.toMutableList().apply { set(0, m0.copy(clefChanges = updatedClefs)) }
            } else {
                normalized
            }
            return newClef to nextMeasures
        }
        val m = normalized[location.measureIdx]
        val clampedItemIdx = location.itemIdx.coerceIn(0, m.items.size)
        val prevEffective = if (clampedItemIdx == 0) {
            effectiveClefAt(normalized, initialClef, location.measureIdx - 1, Int.MAX_VALUE)
        } else {
            effectiveClefAt(normalized, initialClef, location.measureIdx, clampedItemIdx - 1)
        }
        val nextClefs = m.clefChanges.toMutableMap()
        if (newClef == null || newClef == prevEffective) {
            nextClefs.remove(clampedItemIdx)
        } else {
            nextClefs[clampedItemIdx] = newClef
        }
        if (nextClefs == m.clefChanges) return initialClef to normalized
        val nextMeasures = normalized.toMutableList().apply {
            set(location.measureIdx, m.copy(clefChanges = nextClefs))
        }
        return initialClef to nextMeasures
    }

    /**
     * Captures the currently selected notes, chords, and rests in score order along with each
     * item's measure offset relative to the first selected measure (and any explicit clef at that
     * slot) so pasting preserves bar boundaries and mid-score clefs.
     * When only specific noteheads of a [ScoreItem.Chord] are selected, copies only
     * those pitches rather than the entire chord.
     */
    fun copySelectedEntries(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
    ): List<ScoreClipboardItem> {
        if (selection.isEmpty()) return emptyList()
        val bySlot = selection.groupBy { it.slotLocation() }
        val sortedSlots = bySlot.keys.sortedWith(compareBy<ScoreItemLocation> { it.measureIdx }.thenBy { it.itemIdx })
        val firstMeasureIdx = sortedSlots.firstOrNull { itemAt(measures, it) != null }?.measureIdx ?: return emptyList()
        val copied = mutableListOf<ScoreClipboardItem>()
        for (slot in sortedSlots) {
            val item = itemAt(measures, slot) ?: continue
            val relMeasure = slot.measureIdx - firstMeasureIdx
            val clefBefore = measures
                .getOrNull(slot.measureIdx)
                ?.clefChanges
                ?.get(slot.itemIdx)
                ?.takeIf { slot.itemIdx > 0 || slot.measureIdx > 0 }
            val locs = bySlot.getValue(slot)
            if (item is ScoreItem.Chord && locs.none { it.chordNoteIdx == null }) {
                val selectedIndices = locs
                    .mapNotNull { it.chordNoteIdx }
                    .filter { it in item.pitches.indices }
                    .distinct()
                    .sorted()
                val pitches = selectedIndices.map { item.pitches[it] }
                itemFromPitches(
                    pitches = pitches,
                    duration = item.duration,
                    isDotted = item.isDotted,
                    amplitude = item.amplitude,
                    isStaccato = item.isStaccato,
                )?.let { copied.add(ScoreClipboardItem(it, relMeasure, clefBefore)) }
            } else {
                copied.add(ScoreClipboardItem(item, relMeasure, clefBefore))
            }
        }
        return copied
    }

    /**
     * Builds a typed [ScoreClipboardContent] snapshot from the active editor selection:
     * - Highlighted measures ([selectedMeasures] non-empty) capture every measure's items,
     *   effective opening key/time/clef, and mid-measure/trailing clefs.
     * - Item selections ([selectedLocations] non-empty) capture items + measure offsets + attached clefs.
     * - Standalone clef selection ([selectedClefChange] non-null) captures [ScoreClipboardContent.ClefOnly].
     */
    fun copySelectionContent(
        measures: List<ScoreMeasure>,
        initialClef: Clef,
        initialKeySignature: KeySignature,
        initialTimeSignature: TimeSignature,
        selectedLocations: Set<ScoreItemLocation> = emptySet(),
        selectedMeasures: Set<Int> = emptySet(),
        selectedClefChange: ScoreClefLocation? = null,
    ): ScoreClipboardContent? {
        val validMeasures = selectedMeasures.filter { it in measures.indices }.sorted()
        if (validMeasures.isNotEmpty()) {
            val range = validMeasures.min()..validMeasures.max()
            val copiedMeasures = range.map { mIdx ->
                val m = measures[mIdx]
                ScoreClipboardMeasure(
                    measure = m.copy(
                        items = m.items.toList(),
                        clefChanges = m.clefChanges.toMap(),
                    ),
                    openingKey = effectiveKeyAtMeasure(measures, initialKeySignature, mIdx),
                    openingTime = effectiveTimeAtMeasure(measures, initialTimeSignature, mIdx),
                    openingClef = effectiveClefAt(measures, initialClef, mIdx, itemIdx = 0),
                )
            }
            return ScoreClipboardContent.MeasureRange(copiedMeasures)
        }
        if (selectedLocations.isNotEmpty()) {
            val entries = copySelectedEntries(measures, selectedLocations)
            if (entries.isNotEmpty()) {
                return ScoreClipboardContent.ItemSelection(entries)
            }
        }
        if (selectedClefChange != null) {
            val clef = effectiveClefAt(measures, initialClef, selectedClefChange.measureIdx, selectedClefChange.itemIdx)
            return ScoreClipboardContent.ClefOnly(clef)
        }
        return null
    }

    /** Convenience helper returning just the copied [ScoreItem]s without measure offsets. */
    fun copySelectedItems(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
    ): List<ScoreItem> = copySelectedEntries(measures, selection).map { it.item }

    /**
     * Inserts flat [itemsToPaste] into [measures] (treating all items as contiguous from the
     * target slot while wrapping across full measures per time signature).
     */
    fun pasteItems(
        measures: List<ScoreMeasure>,
        itemsToPaste: List<ScoreItem>,
        initialTimeSignature: TimeSignature,
        selectedLocations: Set<ScoreItemLocation> = emptySet(),
        cursorPosition: ScoreCursorPosition? = null,
        selectedMeasures: Set<Int> = emptySet(),
        appendMeasureOnFull: Boolean = false,
    ): ScorePasteResult? = pasteClipboardItems(
        measures = measures,
        entries = itemsToPaste.map { ScoreClipboardItem(it, 0) },
        initialTimeSignature = initialTimeSignature,
        selectedLocations = selectedLocations,
        cursorPosition = cursorPosition,
        selectedMeasures = selectedMeasures,
        appendMeasureOnFull = appendMeasureOnFull,
    )

    /**
     * Inserts measure-aware [entries] into [measures] starting at the active [cursorPosition],
     * after the last item in [selectedLocations], into [selectedMeasures], or at the end of
     * existing content (falling back to measure 1 beat 1 if all measures are empty).
     * Measure jumps in [entries] advance to corresponding target measures so incomplete measures
     * (missing rests) preserve their bar alignment and timing, while contiguous items that exceed
     * a measure's capacity still wrap across barlines according to each measure's time signature.
     */
    fun pasteClipboardItems(
        measures: List<ScoreMeasure>,
        entries: List<ScoreClipboardItem>,
        initialTimeSignature: TimeSignature,
        selectedLocations: Set<ScoreItemLocation> = emptySet(),
        cursorPosition: ScoreCursorPosition? = null,
        selectedMeasures: Set<Int> = emptySet(),
        appendMeasureOnFull: Boolean = false,
        initialClef: Clef = Clef.TREBLE,
    ): ScorePasteResult? {
        if (entries.isEmpty()) return null
        var current = renumber(measures)
        var afterClef = initialClef
        val (startMIdx, startIIdx) = when {
            cursorPosition != null -> {
                if (current.isEmpty()) {
                    0 to 0
                } else {
                    val m = cursorPosition.measureIdx.coerceIn(0, current.lastIndex)
                    m to cursorPosition.itemIdx.coerceIn(0, current[m].items.size)
                }
            }
            selectedLocations.isNotEmpty() -> {
                val lastSel = selectedLocations
                    .map { it.slotLocation() }
                    .maxWith(compareBy<ScoreItemLocation> { it.measureIdx }.thenBy { it.itemIdx })
                if (current.isEmpty()) {
                    0 to 0
                } else {
                    val m = lastSel.measureIdx.coerceIn(0, current.lastIndex)
                    val i = (lastSel.itemIdx + 1).coerceIn(0, current[m].items.size)
                    m to i
                }
            }
            selectedMeasures.isNotEmpty() -> {
                if (current.isEmpty()) {
                    0 to 0
                } else {
                    val m = selectedMeasures.min().coerceIn(0, current.lastIndex)
                    m to current[m].items.size
                }
            }
            else -> {
                val lastNonEmpty = current.indexOfLast { it.items.isNotEmpty() }
                if (lastNonEmpty >= 0) {
                    lastNonEmpty to current[lastNonEmpty].items.size
                } else {
                    0 to 0
                }
            }
        }

        var mIdx = startMIdx
        var iIdx = startIIdx
        var prevRelMeasure = entries.first().relativeMeasureOffset
        val pastedLocs = linkedSetOf<ScoreItemLocation>()
        var lastInsertedLoc = ScoreItemLocation(0, 0)

        for (entry in entries) {
            val barJump = (entry.relativeMeasureOffset - prevRelMeasure).coerceAtLeast(0)
            if (barJump > 0) {
                mIdx += barJump
                iIdx = 0
                prevRelMeasure = entry.relativeMeasureOffset
            }
            while (mIdx >= current.size) {
                current = appendEmptyMeasure(current)
                iIdx = 0
            }
            val curMeasure = current[mIdx]
            if (iIdx >= curMeasure.items.size && curMeasure.items.isNotEmpty()) {
                val capTicks = effectiveTimeAtMeasure(current, initialTimeSignature, mIdx).measureCapacityTicks
                val curTicks = curMeasure.items.sumOf { it.totalTicks }
                if (curTicks + entry.item.totalTicks > capTicks) {
                    mIdx++
                    iIdx = 0
                    if (mIdx >= current.size) {
                        current = appendEmptyMeasure(current)
                    }
                }
            }
            val (next, loc) = insertItem(
                current,
                mIdx,
                iIdx,
                entry.item,
                appendNewMeasure = mIdx >= current.size,
                shiftClefAtIndex = true,
            )
            current = next
            if (entry.clefBefore != null && (loc.measureIdx > 0 || loc.itemIdx > 0)) {
                val (updatedClef, updatedMeasures) = setClefChange(
                    afterClef,
                    current,
                    ScoreClefLocation(loc.measureIdx, loc.itemIdx),
                    entry.clefBefore,
                )
                afterClef = updatedClef
                current = updatedMeasures
            }
            pastedLocs.add(loc)
            lastInsertedLoc = loc
            mIdx = loc.measureIdx
            iIdx = loc.itemIdx + 1
        }

        val finalMeasure = current[lastInsertedLoc.measureIdx]
        val finalCap = effectiveTimeAtMeasure(current, initialTimeSignature, lastInsertedLoc.measureIdx).measureCapacityTicks
        val atEndOfMeasure = lastInsertedLoc.itemIdx == finalMeasure.items.lastIndex
        val nextCursor = if (atEndOfMeasure && finalMeasure.items.sumOf { it.totalTicks } >= finalCap) {
            val nextM = lastInsertedLoc.measureIdx + 1
            if (nextM < current.size) {
                ScoreCursorPosition(nextM, 0)
            } else if (appendMeasureOnFull) {
                current = appendEmptyMeasure(current)
                ScoreCursorPosition(nextM, 0)
            } else {
                ScoreCursorPosition(lastInsertedLoc.measureIdx, finalMeasure.items.size)
            }
        } else {
            ScoreCursorPosition(lastInsertedLoc.measureIdx, lastInsertedLoc.itemIdx + 1)
        }

        return ScorePasteResult(
            measures = current,
            pastedLocations = pastedLocs,
            nextCursor = nextCursor,
            initialClef = afterClef,
        )
    }

    /**
     * Pastes any [ScoreClipboardContent] (whole [ScoreClipboardContent.MeasureRange] with key
     * signatures, time signatures, clefs, items, and empty bars, or [ScoreClipboardContent.ItemSelection])
     * into the score document starting at the active cursor or selection.
     * Standalone [ScoreClipboardContent.ClefOnly] is handled by [setClefChange] at the target clef location.
     */
    fun pasteClipboardContent(
        measures: List<ScoreMeasure>,
        content: ScoreClipboardContent,
        initialClef: Clef,
        initialKeySignature: KeySignature,
        initialTimeSignature: TimeSignature,
        selectedLocations: Set<ScoreItemLocation> = emptySet(),
        cursorPosition: ScoreCursorPosition? = null,
        selectedMeasures: Set<Int> = emptySet(),
        appendMeasureOnFull: Boolean = false,
    ): ScorePasteResult? = when (content) {
        is ScoreClipboardContent.MeasureRange -> pasteMeasureRange(
            measures = measures,
            copiedMeasures = content.measures,
            initialClef = initialClef,
            initialKeySignature = initialKeySignature,
            initialTimeSignature = initialTimeSignature,
            selectedLocations = selectedLocations,
            cursorPosition = cursorPosition,
            selectedMeasures = selectedMeasures,
            appendMeasureOnFull = appendMeasureOnFull,
        )
        is ScoreClipboardContent.ItemSelection -> pasteClipboardItems(
            measures = measures,
            entries = content.items,
            initialTimeSignature = initialTimeSignature,
            selectedLocations = selectedLocations,
            cursorPosition = cursorPosition,
            selectedMeasures = selectedMeasures,
            appendMeasureOnFull = appendMeasureOnFull,
            initialClef = initialClef,
        )?.copy(
            initialKeySignature = initialKeySignature,
            initialTimeSignature = initialTimeSignature,
        )
        is ScoreClipboardContent.ClefOnly -> null
    }

    private fun pasteMeasureRange(
        measures: List<ScoreMeasure>,
        copiedMeasures: List<ScoreClipboardMeasure>,
        initialClef: Clef,
        initialKeySignature: KeySignature,
        initialTimeSignature: TimeSignature,
        selectedLocations: Set<ScoreItemLocation>,
        cursorPosition: ScoreCursorPosition?,
        selectedMeasures: Set<Int>,
        appendMeasureOnFull: Boolean,
    ): ScorePasteResult? {
        if (copiedMeasures.isEmpty()) return null
        var current = renumber(measures)
        var afterClef = initialClef
        var afterKey = initialKeySignature
        var afterTime = initialTimeSignature

        val (startMIdx, startIIdx) = when {
            cursorPosition != null -> {
                if (current.isEmpty()) {
                    0 to 0
                } else {
                    val m = cursorPosition.measureIdx.coerceIn(0, current.lastIndex)
                    val i = cursorPosition.itemIdx.coerceIn(0, current[m].items.size)
                    // When the cursor sits after the last item of a non-empty bar (or past the final
                    // barline), start the pasted measure range on the following measure so copied bar 1
                    // retains its key/time/clef headers and new trailing bars are appended automatically.
                    if (i > 0 && i == current[m].items.size) (m + 1) to 0 else m to i
                }
            }
            selectedLocations.isNotEmpty() -> {
                if (current.isEmpty()) {
                    0 to 0
                } else {
                    val lastM = maxOf(
                        selectedLocations.maxOf { it.measureIdx },
                        selectedMeasures.maxOrNull() ?: -1,
                    ).coerceIn(0, current.lastIndex)
                    (lastM + 1) to 0
                }
            }
            selectedMeasures.isNotEmpty() -> {
                selectedMeasures.min().coerceAtLeast(0) to 0
            }
            else -> {
                val lastNonEmpty = current.indexOfLast {
                    it.items.isNotEmpty() ||
                        it.keySignature != null ||
                        it.timeSignature != null ||
                        it.clefChanges.isNotEmpty()
                }
                if (lastNonEmpty >= 0) (lastNonEmpty + 1) to 0 else 0 to 0
            }
        }

        val pastedLocs = linkedSetOf<ScoreItemLocation>()
        val pastedMeasureSet = linkedSetOf<Int>()

        for ((offset, cm) in copiedMeasures.withIndex()) {
            val targetMIdx = startMIdx + offset
            while (targetMIdx >= current.size) {
                current = appendEmptyMeasure(current)
            }
            val insertIIdx = if (offset == 0) startIIdx else 0
            val dest = current[targetMIdx]
            val insertedCount = cm.measure.items.size
            val beforeItems = dest.items.take(insertIIdx)
            val afterItems = dest.items.drop(insertIIdx)
            val mergedItems = beforeItems + cm.measure.items + afterItems
            for (i in 0 until insertedCount) {
                pastedLocs.add(ScoreItemLocation(targetMIdx, insertIIdx + i))
            }

            val mergedClefs = mutableMapOf<Int, Clef>()
            for ((k, clef) in dest.clefChanges) {
                when {
                    k < insertIIdx -> mergedClefs[k] = clef
                    k > 0 || insertIIdx > 0 -> mergedClefs[k + insertedCount] = clef
                }
            }
            for ((k, clef) in cm.measure.clefChanges) {
                if (k > 0) {
                    mergedClefs[insertIIdx + k] = clef
                }
            }

            val mergedKey: KeySignature?
            val mergedTime: TimeSignature?
            if (insertIIdx == 0) {
                if (targetMIdx == 0) {
                    afterKey = cm.openingKey
                    afterTime = cm.openingTime
                    afterClef = cm.openingClef
                    mergedKey = null
                    mergedTime = null
                    mergedClefs.remove(0)
                } else {
                    val prevKey = effectiveKeyAtMeasure(current, afterKey, targetMIdx - 1)
                    val prevTime = effectiveTimeAtMeasure(current, afterTime, targetMIdx - 1)
                    val prevClef = effectiveClefAt(current, afterClef, targetMIdx - 1, Int.MAX_VALUE)
                    mergedKey = cm.openingKey.takeIf { it != prevKey }
                    mergedTime = cm.openingTime.takeIf { it != prevTime }
                    if (cm.openingClef != prevClef) {
                        mergedClefs[0] = cm.openingClef
                    } else {
                        mergedClefs.remove(0)
                    }
                }
            } else {
                mergedKey = dest.keySignature
                mergedTime = dest.timeSignature
                val prevClef = effectiveClefAt(current, afterClef, targetMIdx, insertIIdx - 1)
                if (cm.openingClef != prevClef) {
                    mergedClefs[insertIIdx] = cm.openingClef
                }
            }

            current = current.toMutableList().apply {
                set(
                    targetMIdx,
                    dest.copy(
                        items = mergedItems,
                        keySignature = mergedKey,
                        timeSignature = mergedTime,
                        clefChanges = mergedClefs,
                    )
                )
            }
            pastedMeasureSet.add(targetMIdx)
        }

        current = renumber(current)
        val lastM = startMIdx + copiedMeasures.lastIndex
        val lastCopiedCount = copiedMeasures.last().measure.items.size
        val nextCursor = if (lastCopiedCount == 0) {
            ScoreCursorPosition(lastM, 0)
        } else {
            val lastItemIdx = if (copiedMeasures.size == 1) startIIdx + lastCopiedCount - 1 else lastCopiedCount - 1
            val finalMeasure = current[lastM]
            val finalCap = effectiveTimeAtMeasure(current, afterTime, lastM).measureCapacityTicks
            val atEndOfMeasure = lastItemIdx == finalMeasure.items.lastIndex
            if (atEndOfMeasure && finalMeasure.items.sumOf { it.totalTicks } >= finalCap) {
                val nextM = lastM + 1
                if (nextM < current.size) {
                    ScoreCursorPosition(nextM, 0)
                } else if (appendMeasureOnFull) {
                    current = appendEmptyMeasure(current)
                    ScoreCursorPosition(nextM, 0)
                } else {
                    ScoreCursorPosition(lastM, finalMeasure.items.size)
                }
            } else {
                ScoreCursorPosition(lastM, lastItemIdx + 1)
            }
        }

        return ScorePasteResult(
            measures = current,
            pastedLocations = pastedLocs,
            nextCursor = nextCursor,
            initialClef = afterClef,
            initialKeySignature = afterKey,
            initialTimeSignature = afterTime,
            pastedMeasures = pastedMeasureSet,
        )
    }

    /**
     * Resolves the target [ScoreClefLocation] from editor selection/cursor state.
     * Priority: explicit selected clef change, first selected item, active insertion cursor
     * (so a mid-measure cursor is not shadowed by single-measure click highlight), selected
     * measure beat 1, or the opening `(0, 0)` clef.
     */
    fun resolveTargetClefLocation(
        selectedClefChange: ScoreClefLocation?,
        selectedLocations: Set<ScoreItemLocation>,
        cursorPosition: ScoreCursorPosition?,
        selectedMeasures: Set<Int>,
    ): ScoreClefLocation {
        selectedClefChange?.let { return it }
        if (selectedLocations.isNotEmpty()) {
            val first = selectedLocations
                .map { it.slotLocation() }
                .minWith(compareBy<ScoreItemLocation> { it.measureIdx }.thenBy { it.itemIdx })
            return ScoreClefLocation(first.measureIdx, first.itemIdx)
        }
        cursorPosition?.let { return ScoreClefLocation(it.measureIdx, it.itemIdx) }
        if (selectedMeasures.isNotEmpty()) {
            return ScoreClefLocation(selectedMeasures.min(), 0)
        }
        return ScoreClefLocation(0, 0)
    }

    /**
     * Computes ordered `(staffStep, accidental)` glyphs for a mid-score key change from
     * [prevKey] to [newKey] in [clef], including cancellation naturals where appropriate.
     */
    fun midMeasureKeyGlyphs(
        prevKey: KeySignature,
        newKey: KeySignature,
        clef: Clef = Clef.TREBLE,
    ): List<Pair<Int, Accidental>> {
        if (newKey == prevKey) return emptyList()
        val prevSteps = prevKey.staffSteps(clef)
        val newSteps = newKey.staffSteps(clef)
        if (newKey.fifths == 0) {
            return prevSteps.map { it to Accidental.NATURAL }
        }
        val newAcc = if (newKey.fifths > 0) Accidental.SHARP else Accidental.FLAT
        if (prevKey.fifths == 0) {
            return newSteps.map { it to newAcc }
        }
        val sameSide = (prevKey.fifths > 0) == (newKey.fifths > 0)
        return if (sameSide) {
            val prevCount = kotlin.math.abs(prevKey.fifths)
            val newCount = kotlin.math.abs(newKey.fifths)
            if (newCount >= prevCount) {
                newSteps.map { it to newAcc }
            } else {
                newSteps.map { it to newAcc } + prevSteps.drop(newCount).map { it to Accidental.NATURAL }
            }
        } else {
            prevSteps.map { it to Accidental.NATURAL } + newSteps.map { it to newAcc }
        }
    }
}

/** Insertion cursor identifying a measure slot (`itemIdx` in `0..measure.items.size`). */
data class ScoreCursorPosition(
    val measureIdx: Int,
    val itemIdx: Int,
)

/** Result of [ScoreEditing.stepInsertItem]. */
data class ScoreStepInsertResult(
    val measures: List<ScoreMeasure>,
    val insertedLocation: ScoreItemLocation,
    val nextCursor: ScoreCursorPosition,
)

/** Single entry stored in [ScoreClipboard], retaining its relative measure offset and optional clef before it. */
data class ScoreClipboardItem(
    val item: ScoreItem,
    val relativeMeasureOffset: Int = 0,
    val clefBefore: Clef? = null,
)

/** Snapshot of a single copied measure, including its effective opening key/time/clef and internal clefs. */
data class ScoreClipboardMeasure(
    val measure: ScoreMeasure,
    val openingKey: KeySignature,
    val openingTime: TimeSignature,
    val openingClef: Clef,
)

/** Typed payload stored in [ScoreClipboard] covering whole measures, selected items, or a standalone clef. */
sealed interface ScoreClipboardContent {
    data class MeasureRange(val measures: List<ScoreClipboardMeasure>) : ScoreClipboardContent
    data class ItemSelection(val items: List<ScoreClipboardItem>) : ScoreClipboardContent
    data class ClefOnly(val clef: Clef) : ScoreClipboardContent
}

/** Result of [ScoreEditing.pasteItems], [ScoreEditing.pasteClipboardItems], or [ScoreEditing.pasteClipboardContent]. */
data class ScorePasteResult(
    val measures: List<ScoreMeasure>,
    val pastedLocations: Set<ScoreItemLocation>,
    val nextCursor: ScoreCursorPosition,
    val initialClef: Clef? = null,
    val initialKeySignature: KeySignature? = null,
    val initialTimeSignature: TimeSignature? = null,
    val pastedMeasures: Set<Int> = emptySet(),
)

/** Shared clipboard across all [ScoreModule] editors so notes, measures, clefs, and signatures can be copied and pasted between scores. */
internal object ScoreClipboard {
    var content by mutableStateOf<ScoreClipboardContent?>(null)
        private set

    val items: List<ScoreClipboardItem>
        get() = when (val c = content) {
            is ScoreClipboardContent.ItemSelection -> c.items
            is ScoreClipboardContent.MeasureRange -> c.measures.flatMapIndexed { idx, cm ->
                cm.measure.items.map { ScoreClipboardItem(it, idx) }
            }
            is ScoreClipboardContent.ClefOnly, null -> emptyList()
        }

    val isNotEmpty: Boolean
        get() = when (val c = content) {
            is ScoreClipboardContent.MeasureRange -> c.measures.isNotEmpty()
            is ScoreClipboardContent.ItemSelection -> c.items.isNotEmpty()
            is ScoreClipboardContent.ClefOnly -> true
            null -> false
        }

    fun copy(copied: List<ScoreClipboardItem>) {
        if (copied.isNotEmpty()) {
            content = ScoreClipboardContent.ItemSelection(copied.toList())
        }
    }

    fun copyContent(newContent: ScoreClipboardContent?) {
        if (newContent == null) return
        val nonEmpty = when (newContent) {
            is ScoreClipboardContent.MeasureRange -> newContent.measures.isNotEmpty()
            is ScoreClipboardContent.ItemSelection -> newContent.items.isNotEmpty()
            is ScoreClipboardContent.ClefOnly -> true
        }
        if (nonEmpty) {
            content = newContent
        }
    }

    fun clear() {
        content = null
    }
}

/** Observable score document for Phase 1 single-stave notation rendering & playback. */
class ScoreData {
    var clef by mutableStateOf(Clef.TREBLE)
    var keySignature by mutableStateOf(KeySignature.D_MAJOR)
    var timeSignature by mutableStateOf(TimeSignature.COMMON_TIME)
    val measures = mutableStateListOf<ScoreMeasure>()

    init {
        loadDefaultSample()
    }

    fun replaceMeasures(newMeasures: List<ScoreMeasure>) {
        val normalized = ScoreEditing.renumber(newMeasures)
        measures.clear()
        measures.addAll(normalized)
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

/**
 * Recognizes standard chord symbols (e.g. `"C#m7"`, `"G7"`, `"Cmaj7"`, `"F/C"`) from a set
 * of vertical chord pitches and the active [KeySignature].
 */
object ScoreChordAnalyzer {
    private data class ChordTemplate(
        val intervals: Set<Int>,
        val suffix: String,
        val priority: Int,
    )

    private val TEMPLATES = listOf(
        // Triads
        ChordTemplate(setOf(0, 4, 7), "", 100),
        ChordTemplate(setOf(0, 3, 7), "m", 98),
        ChordTemplate(setOf(0, 3, 6), "dim", 88),
        ChordTemplate(setOf(0, 4, 8), "aug", 82),
        ChordTemplate(setOf(0, 2, 7), "sus2", 78),
        ChordTemplate(setOf(0, 5, 7), "sus4", 80),
        ChordTemplate(setOf(0, 4, 6), "(b5)", 65),
        // Sixths & Sevenths
        ChordTemplate(setOf(0, 4, 7, 10), "7", 99),
        ChordTemplate(setOf(0, 4, 7, 11), "maj7", 97),
        ChordTemplate(setOf(0, 3, 7, 10), "m7", 96),
        ChordTemplate(setOf(0, 3, 7, 11), "m(maj7)", 85),
        ChordTemplate(setOf(0, 3, 6, 10), "m7b5", 89),
        ChordTemplate(setOf(0, 3, 6, 9), "dim7", 84),
        ChordTemplate(setOf(0, 4, 7, 9), "6", 76),
        ChordTemplate(setOf(0, 3, 7, 9), "m6", 74),
        ChordTemplate(setOf(0, 5, 7, 10), "7sus4", 82),
        ChordTemplate(setOf(0, 4, 8, 10), "aug7", 75),
        ChordTemplate(setOf(0, 4, 8, 11), "aug(maj7)", 72),
        ChordTemplate(setOf(0, 2, 4, 7), "add9", 79),
        ChordTemplate(setOf(0, 2, 3, 7), "m(add9)", 77),
        // Extended & omitted fifths / dyads
        ChordTemplate(setOf(0, 2, 4, 7, 10), "9", 90),
        ChordTemplate(setOf(0, 2, 4, 7, 11), "maj9", 88),
        ChordTemplate(setOf(0, 2, 3, 7, 10), "m9", 87),
        ChordTemplate(setOf(0, 4, 10), "7(no5)", 70),
        ChordTemplate(setOf(0, 4, 11), "maj7(no5)", 68),
        ChordTemplate(setOf(0, 3, 10), "m7(no5)", 68),
        ChordTemplate(setOf(0, 7), "5", 72),
    )

    fun recognizeChordName(pitches: List<SpelledPitch>, keySignature: KeySignature): String? {
        if (pitches.size < 2) return null
        val sortedByMidi = pitches.sortedBy { it.midiPitch }
        val bassPitch = sortedByMidi.first()
        val bassPc = bassPitch.midiPitch.mod(12)
        val pcSet = sortedByMidi.map { it.midiPitch.mod(12) }.toSet()
        if (pcSet.size < 2) return null

        val keyScale = NoteLetter.entries.associateBy(
            { (it.semitoneOffset + keySignature.defaultAlter(it)).mod(12) },
            { SpelledPitch(it, keySignature.defaultAlter(it), 4).pitchClassName() },
        )

        fun spellPitchClass(pc: Int): String {
            keyScale[pc]?.let { return it }
            sortedByMidi.firstOrNull { it.midiPitch.mod(12) == pc }?.let { return it.pitchClassName() }
            return SpelledPitch.fromMidiPitch(60 + pc, keySignature).pitchClassName()
        }

        var bestName: String? = null
        var bestScore = Int.MIN_VALUE

        for (candidateRoot in pcSet) {
            val intervals = pcSet.map { (it - candidateRoot).mod(12) }.toSet()
            val template = TEMPLATES.firstOrNull { it.intervals == intervals } ?: continue
            var score = template.priority
            if (candidateRoot == bassPc) {
                score += 40
            }
            if (candidateRoot in keyScale) {
                score += 18
            }
            val matchingNote = sortedByMidi.firstOrNull { it.midiPitch.mod(12) == candidateRoot }
            if (matchingNote != null && keyScale[candidateRoot] == matchingNote.pitchClassName()) {
                score += 8
            }
            if (score > bestScore) {
                bestScore = score
                val rootName = spellPitchClass(candidateRoot)
                bestName = if (candidateRoot == bassPc) {
                    "$rootName${template.suffix}"
                } else {
                    "$rootName${template.suffix}/${spellPitchClass(bassPc)}"
                }
            }
        }
        return bestName
    }
}
