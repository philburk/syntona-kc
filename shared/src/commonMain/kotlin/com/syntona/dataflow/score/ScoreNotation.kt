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

    data class Note(
        val pitch: SpelledPitch,
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
)

/** Identifies an item slot by measure index and item index within that measure. */
data class ScoreItemLocation(
    val measureIdx: Int,
    val itemIdx: Int,
)

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
    ): List<ScoreMeasure> {
        val note = itemAt(measures, loc) as? ScoreItem.Note ?: return measures
        if (note.pitch == newPitch) return measures
        return replaceItem(measures, loc, note.copy(pitch = newPitch))
    }

    fun setAccidentalOnSelection(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
        alter: Int,
    ): List<ScoreMeasure> = mapSelectedNotes(measures, selection) { note ->
        if (note.pitch.alter != alter) note.copy(pitch = note.pitch.copy(alter = alter)) else note
    }

    fun toggleStaccatoOnSelection(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
    ): List<ScoreMeasure> {
        val selectedNotes = selection.mapNotNull { itemAt(measures, it) as? ScoreItem.Note }
        if (selectedNotes.isEmpty()) return measures
        val targetStaccato = !selectedNotes.all { it.isStaccato }
        return mapSelectedNotes(measures, selection) { note ->
            if (note.isStaccato != targetStaccato) note.copy(isStaccato = targetStaccato) else note
        }
    }

    private inline fun mapSelectedNotes(
        measures: List<ScoreMeasure>,
        selection: Set<ScoreItemLocation>,
        transform: (ScoreItem.Note) -> ScoreItem.Note,
    ): List<ScoreMeasure> {
        if (selection.isEmpty()) return measures
        var anyChanged = false
        val updated = measures.mapIndexed { mIdx, m ->
            var measureChanged = false
            val newItems = m.items.mapIndexed { iIdx, item ->
                if (item is ScoreItem.Note && ScoreItemLocation(mIdx, iIdx) in selection) {
                    val transformed = transform(item)
                    if (transformed !== item && transformed != item) {
                        measureChanged = true
                        anyChanged = true
                        transformed
                    } else {
                        item
                    }
                } else {
                    item
                }
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
            val indicesToRemove = byMeasure[mIdx]?.map { it.itemIdx }?.toSet() ?: emptySet()
            if (indicesToRemove.isEmpty()) {
                m
            } else {
                m.copy(items = m.items.filterIndexed { idx, _ -> idx !in indicesToRemove })
            }
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
