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
) {
    TREBLE(
        glyph = SmuflGlyphs.G_CLEF,
        glyphLineFromBottom = 1, // G4 sits on 2nd line from bottom
        bottomLineDiatonicStep = 4 * 7 + NoteLetter.E.ordinal, // E4
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
        updated[measureIdx] = m.copy(items = newItems)
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
            for ((iIdx, item) in m.items.withIndex()) {
                val locs = byItem[iIdx]
                if (locs == null) {
                    nextItems.add(item)
                } else if (item is ScoreItem.Chord && locs.none { it.chordNoteIdx == null }) {
                    val removeSet = locs.mapNotNull { it.chordNoteIdx }.toSet()
                    val remaining = item.pitches.filterIndexed { pIdx, _ -> pIdx !in removeSet }
                    itemFromPitches(
                        pitches = remaining,
                        duration = item.duration,
                        isDotted = item.isDotted,
                        amplitude = item.amplitude,
                        isStaccato = item.isStaccato,
                    )?.let { nextItems.add(it) }
                }
            }
            m.copy(items = nextItems)
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
     * Computes ordered `(staffStep, accidental)` glyphs for a mid-score key change from
     * [prevKey] to [newKey], including cancellation naturals where appropriate.
     */
    fun midMeasureKeyGlyphs(prevKey: KeySignature, newKey: KeySignature): List<Pair<Int, Accidental>> {
        if (newKey == prevKey) return emptyList()
        val prevSteps = prevKey.trebleStaffSteps()
        val newSteps = newKey.trebleStaffSteps()
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
