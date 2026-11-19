package com.syntona.dataflow.score

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Quantization grid resolution for real-time MIDI recording into a score. */
enum class ScoreQuantizeGrid(
    val label: String,
    val ticks: Int,
    val duration: NoteDuration,
) {
    EIGHTH("1/8", NoteDuration.EIGHTH.ticks, NoteDuration.EIGHTH),
    SIXTEENTH("1/16", NoteDuration.SIXTEENTH.ticks, NoteDuration.SIXTEENTH),
    THIRTY_SECOND("1/32", NoteDuration.THIRTY_SECOND.ticks, NoteDuration.THIRTY_SECOND),
}

/** How newly recorded notes combine with existing notes in a measure during real-time recording. */
enum class ScoreRecordMode(val label: String) {
    REPLACE("Replace"),
    MERGE("Merge"),
}

/** A raw recorded note captured in continuous musical ticks before quantization. */
data class RecordedRawNote(
    val startTick: Double,
    val endTick: Double,
    val midiPitch: Int,
    val amplitude: Double = 0.25,
)

/**
 * Pure rhythm quantization and transcription engine converting real-time MIDI note spans
 * into engraved [ScoreMeasure] notation with standard durations, dotted notes, chords,
 * boundary-aligned rests, ties, and 3:2 triplets.
 */
object ScoreTranscriber {

    /** Standard Middle C split point for two-handed Grand Staff recording (treble >= 60, bass < 60). */
    const val GRAND_STAFF_SPLIT_MIDI = 60

    private data class DurationOption(
        val duration: NoteDuration,
        val isDotted: Boolean,
        val ticks: Int,
    )

    private val STRAIGHT_NOTE_OPTIONS: List<DurationOption> = listOf(
        DurationOption(NoteDuration.WHOLE, false, NoteDuration.WHOLE.totalTicks(false)),
        DurationOption(NoteDuration.HALF, true, NoteDuration.HALF.totalTicks(true)),
        DurationOption(NoteDuration.HALF, false, NoteDuration.HALF.totalTicks(false)),
        DurationOption(NoteDuration.QUARTER, true, NoteDuration.QUARTER.totalTicks(true)),
        DurationOption(NoteDuration.QUARTER, false, NoteDuration.QUARTER.totalTicks(false)),
        DurationOption(NoteDuration.EIGHTH, true, NoteDuration.EIGHTH.totalTicks(true)),
        DurationOption(NoteDuration.EIGHTH, false, NoteDuration.EIGHTH.totalTicks(false)),
        DurationOption(NoteDuration.SIXTEENTH, true, NoteDuration.SIXTEENTH.totalTicks(true)),
        DurationOption(NoteDuration.SIXTEENTH, false, NoteDuration.SIXTEENTH.totalTicks(false)),
        DurationOption(NoteDuration.THIRTY_SECOND, false, NoteDuration.THIRTY_SECOND.totalTicks(false)),
    )

    private val REST_OPTIONS: List<DurationOption> = listOf(
        DurationOption(NoteDuration.WHOLE, false, NoteDuration.WHOLE.ticks),
        DurationOption(NoteDuration.HALF, false, NoteDuration.HALF.ticks),
        DurationOption(NoteDuration.QUARTER, false, NoteDuration.QUARTER.ticks),
        DurationOption(NoteDuration.EIGHTH, false, NoteDuration.EIGHTH.ticks),
        DurationOption(NoteDuration.SIXTEENTH, false, NoteDuration.SIXTEENTH.ticks),
        DurationOption(NoteDuration.THIRTY_SECOND, false, NoteDuration.THIRTY_SECOND.ticks),
    )

    private data class QuantizedChordEvent(
        val onsetTick: Int,
        val endTick: Int,
        val pitches: List<Int>,
        val amplitude: Double,
        val tieOutToNextMeasure: Boolean,
    )

    /**
     * Extracts existing sounding notes from [measure] as continuous [RecordedRawNote] spans
     * starting from [measureStartTick], merging intra-measure ties so [ScoreRecordMode.MERGE]
     * can re-transcribe old and new notes together cleanly.
     */
    fun extractRawNotesFromMeasure(
        measure: ScoreMeasure,
        measureStartTick: Double,
    ): List<RecordedRawNote> {
        val result = mutableListOf<RecordedRawNote>()
        // Track active intra-measure ties: midiPitch -> index in result
        val tiedIndices = mutableMapOf<Int, Int>()
        var running = RationalTicks.ZERO

        for (item in measure.items) {
            val dur = item.exactTicks()
            val start = measureStartTick + running.toDoubleTicks()
            val end = measureStartTick + (running + dur).toDoubleTicks()
            val tiedOut = item.tiedMidiPitches()
            val amp = item.noteAmplitude().coerceIn(0.05, 1.0)
            val currentMidis = item.soundingPitches().map { it.midiPitch }.toSet()

            // Close ties for any pitch not continued in this slot
            tiedIndices.keys.toList().forEach { pitch ->
                if (pitch !in currentMidis) tiedIndices.remove(pitch)
            }

            for (midi in currentMidis) {
                val existingIdx = tiedIndices[midi]
                if (existingIdx != null) {
                    val prev = result[existingIdx]
                    result[existingIdx] = prev.copy(endTick = end)
                    if (midi !in tiedOut) {
                        tiedIndices.remove(midi)
                    }
                } else {
                    val idx = result.size
                    result.add(RecordedRawNote(startTick = start, endTick = end, midiPitch = midi, amplitude = amp))
                    if (midi in tiedOut) {
                        tiedIndices[midi] = idx
                    }
                }
            }
            running += dur
        }
        return result
    }

    /**
     * Transcribes [rawNotes] falling within a single measure `[measureStartTick, measureStartTick + capacity)`
     * into a rhythmically complete [ScoreMeasure].
     *
     * Returns `(transcribedMeasure, carriedNotesForNextMeasure)` where `carriedNotesForNextMeasure`
     * contains any notes whose quantized onset snapped to the next downbeat or whose sustained tail
     * crossed the barline.
     */
    fun transcribeMeasure(
        rawNotes: List<RecordedRawNote>,
        measureNumber: Int,
        measureStartTick: Double,
        timeSignature: TimeSignature,
        keySignature: KeySignature,
        quantizeGrid: ScoreQuantizeGrid = ScoreQuantizeGrid.SIXTEENTH,
        detectTriplets: Boolean = true,
        existingTupletIds: Set<String> = emptySet(),
        existingMeasure: ScoreMeasure? = null,
        allowTieOut: Boolean = true,
    ): Pair<ScoreMeasure, List<RecordedRawNote>> {
        val capacity = timeSignature.measureCapacityTicks.coerceAtLeast(NoteDuration.THIRTY_SECOND.ticks)
        val quarterTicks = NoteDuration.QUARTER.ticks
        val gridTicks = quantizeGrid.ticks
        val beatTicks = beatTicksForTimeSignature(timeSignature)
        val beatCount = ((capacity + beatTicks - 1) / beatTicks).coerceAtLeast(1)

        val carriedToNext = mutableListOf<RecordedRawNote>()
        val inMeasureRaw = mutableListOf<RecordedRawNote>()

        for (note in rawNotes) {
            val relStart = note.startTick - measureStartTick
            val relEnd = (note.endTick - measureStartTick).coerceAtLeast(relStart + gridTicks * 0.5)
            // Notes within half a grid step of the next barline snap onto Beat 1 of the next measure
            if (relStart >= capacity - gridTicks * 0.5) {
                carriedToNext.add(note)
                continue
            }
            if (relEnd <= 0.0) continue
            inMeasureRaw.add(
                note.copy(
                    startTick = measureStartTick + relStart.coerceAtLeast(0.0),
                    endTick = measureStartTick + relEnd,
                )
            )
        }

        if (inMeasureRaw.isEmpty()) {
            val restItems = decomposeRestSpan(0, capacity, beatTicks)
            val measure = buildMeasurePreservingMetadata(measureNumber, restItems, existingMeasure)
            return measure to carriedToNext
        }

        // 1. Classify each beat as straight vs. 3:2 triplet
        val isTripletBeat = BooleanArray(beatCount) { false }
        val tripletStep = beatTicks / 3 // exact 80 ticks when beatTicks == 240 (quarter)
        val canDetectTriplets = detectTriplets &&
            beatTicks == quarterTicks &&
            beatTicks % 3 == 0 &&
            quantizeGrid != ScoreQuantizeGrid.THIRTY_SECOND

        if (canDetectTriplets) {
            val edgeTol = gridTicks * 0.5
            for (b in 0 until beatCount) {
                val bStart = b * beatTicks
                val bEnd = min((b + 1) * beatTicks, capacity)
                if (bEnd - bStart < beatTicks) continue

                val beatNotes = inMeasureRaw.filter { n ->
                    val r = n.startTick - measureStartTick
                    r >= bStart - edgeTol && r < bEnd - edgeTol
                }
                if (beatNotes.isEmpty()) continue

                var straightErr = 0.0
                var tripletErr = 0.0
                var hasInnerTriplet = false
                for (n in beatNotes) {
                    val r = (n.startTick - measureStartTick).coerceIn(bStart.toDouble(), bEnd.toDouble())
                    val local = r - bStart
                    val sSnap = ((local / gridTicks).roundToInt() * gridTicks).coerceIn(0, beatTicks)
                    val tIdx = (local / tripletStep).roundToInt().coerceIn(0, 3)
                    val tSnap = tIdx * tripletStep
                    straightErr += abs(local - sSnap)
                    tripletErr += abs(local - tSnap)
                    if (tIdx == 1 || tIdx == 2) {
                        hasInnerTriplet = true
                    }
                }
                val tripletWins = hasInnerTriplet &&
                    tripletErr < straightErr * 0.65 &&
                    (beatNotes.size >= 2 || (tripletErr < tripletStep * 0.22 && straightErr > gridTicks * 0.35))
                isTripletBeat[b] = tripletWins
            }
        }

        // 2. Quantize every note's onset onto its beat's chosen grid
        data class SnappedNote(
            val onsetTick: Int,
            val rawEndRel: Double,
            val midiPitch: Int,
            val amplitude: Double,
        )

        val snappedByOnset = linkedMapOf<Int, MutableList<SnappedNote>>()
        for (n in inMeasureRaw) {
            val relStart = (n.startTick - measureStartTick).coerceAtLeast(0.0)
            val relEnd = (n.endTick - measureStartTick).coerceAtLeast(relStart + gridTicks * 0.5)
            val bIdx = (relStart / beatTicks).toInt().coerceIn(0, beatCount - 1)
            val bStart = bIdx * beatTicks
            val local = relStart - bStart
            val snappedOnset = if (isTripletBeat[bIdx]) {
                val tIdx = (local / tripletStep).roundToInt().coerceIn(0, 3)
                bStart + tIdx * tripletStep
            } else {
                val gIdx = (relStart / gridTicks).roundToInt()
                gIdx * gridTicks
            }
            if (snappedOnset >= capacity) {
                carriedToNext.add(
                    n.copy(
                        startTick = measureStartTick + capacity,
                        endTick = (measureStartTick + relEnd).coerceAtLeast(measureStartTick + capacity + gridTicks),
                    )
                )
                continue
            }
            snappedByOnset.getOrPut(snappedOnset) { mutableListOf() }
                .add(SnappedNote(snappedOnset, relEnd, n.midiPitch.coerceIn(0, 127), n.amplitude.coerceIn(0.05, 1.0)))
        }

        if (snappedByOnset.isEmpty()) {
            val restItems = decomposeRestSpan(0, capacity, beatTicks)
            val measure = buildMeasurePreservingMetadata(measureNumber, restItems, existingMeasure)
            return measure to carriedToNext
        }

        // 3. Merge simultaneous attacks into chord events and quantize release/duration cleanly
        val sortedOnsets = snappedByOnset.keys.sorted()
        val events = ArrayList<QuantizedChordEvent>(sortedOnsets.size)
        for ((idx, onset) in sortedOnsets.withIndex()) {
            val group = snappedByOnset[onset] ?: continue
            val nextOnset = sortedOnsets.getOrNull(idx + 1) ?: capacity
            val bIdx = (onset / beatTicks).coerceIn(0, beatCount - 1)
            val stepForEnd = if (isTripletBeat[bIdx]) tripletStep else gridTicks
            val pitches = group.map { it.midiPitch }.distinct().sorted()
            val amp = group.maxOf { it.amplitude }
            val maxRawEnd = group.maxOf { it.rawEndRel }

            val crossesBarline = allowTieOut &&
                (idx == sortedOnsets.lastIndex) &&
                (maxRawEnd > capacity + gridTicks * 0.5)
            val clampedRawEnd = min(maxRawEnd, nextOnset.toDouble())
            var snappedEnd = ((clampedRawEnd / stepForEnd).roundToInt() * stepForEnd)
                .coerceAtLeast(onset + stepForEnd)
                .coerceAtMost(nextOnset)
                .coerceAtMost(capacity)

            // Absorb tiny release gaps (< 1 grid step) before the next note or barline tie
            if (nextOnset - snappedEnd in 1 until stepForEnd) {
                snappedEnd = min(nextOnset, capacity)
            }
            if (crossesBarline) {
                snappedEnd = capacity
                for (midi in pitches) {
                    carriedToNext.add(
                        RecordedRawNote(
                            startTick = measureStartTick + capacity,
                            endTick = measureStartTick + maxRawEnd,
                            midiPitch = midi,
                            amplitude = amp,
                        )
                    )
                }
            }

            events.add(
                QuantizedChordEvent(
                    onsetTick = onset,
                    endTick = snappedEnd,
                    pitches = pitches,
                    amplitude = amp,
                    tieOutToNextMeasure = crossesBarline,
                )
            )
        }

        // 4. Emit ScoreItems across triplet beats and straight regions
        val items = mutableListOf<ScoreItem>()
        val usedTupletIds = existingTupletIds.toMutableSet()
        val eventByOnset = events.associateBy { it.onsetTick }
        var cursor = 0

        var beatIdx = 0
        while (cursor < capacity) {
            // Check if cursor starts a triplet beat
            val curBeat = (cursor / beatTicks).coerceIn(0, beatCount - 1)
            val curBeatStart = curBeat * beatTicks
            val curBeatEnd = min(curBeatStart + beatTicks, capacity)
            if (cursor == curBeatStart && curBeatEnd - curBeatStart == beatTicks && isTripletBeat[curBeat]) {
                val groupId = ScoreEditing.nextTupletGroupId(usedTupletIds)
                usedTupletIds.add(groupId)
                val tuplet = ScoreTuplet(
                    groupId = groupId,
                    actualCount = 3,
                    normalCount = 2,
                    baseTicks = NoteDuration.EIGHTH.ticks,
                )
                var slot = 0
                while (slot < 3) {
                    val slotStart = curBeatStart + slot * tripletStep
                    val ev = eventByOnset[slotStart]
                    if (ev != null) {
                        val spansTwoSlots = slot <= 1 &&
                            ev.endTick >= slotStart + tripletStep * 2 &&
                            eventByOnset[slotStart + tripletStep] == null
                        val slotCount = if (spansTwoSlots) 2 else 1
                        val dur = if (spansTwoSlots) NoteDuration.QUARTER else NoteDuration.EIGHTH
                        val slotEnd = slotStart + slotCount * tripletStep
                        val tiedOut = ev.endTick > slotEnd || (ev.tieOutToNextMeasure && slotEnd >= capacity)
                        items.add(
                            buildNoteOrChordItem(
                                midiPitches = ev.pitches,
                                duration = dur,
                                isDotted = false,
                                amplitude = ev.amplitude,
                                isTiedToNext = tiedOut,
                                tuplet = tuplet,
                                keySignature = keySignature,
                            )
                        )
                        slot += slotCount
                    } else {
                        items.add(ScoreItem.Rest(NoteDuration.EIGHTH, tuplet = tuplet))
                        slot += 1
                    }
                }
                cursor = curBeatEnd
                beatIdx = curBeat + 1
                continue
            }

            // Straight region: advance until next triplet beat start or measure end
            var straightEnd = capacity
            for (b in curBeat until beatCount) {
                val bStart = b * beatTicks
                if (bStart > cursor && isTripletBeat[b]) {
                    straightEnd = min(straightEnd, bStart)
                    break
                }
            }

            // Find next event starting in [cursor, straightEnd)
            val nextEvent = events.firstOrNull { it.onsetTick in cursor until straightEnd }
            if (nextEvent == null) {
                items.addAll(decomposeRestSpan(cursor, straightEnd - cursor, beatTicks))
                cursor = straightEnd
                continue
            }

            if (nextEvent.onsetTick > cursor) {
                items.addAll(decomposeRestSpan(cursor, nextEvent.onsetTick - cursor, beatTicks))
                cursor = nextEvent.onsetTick
            }

            val noteEnd = min(nextEvent.endTick, straightEnd)
            val noteSpan = (noteEnd - cursor).coerceAtLeast(NoteDuration.THIRTY_SECOND.ticks)
            val noteParts = decomposeNoteSpan(cursor, noteSpan, beatTicks)
            val continuesPastRegion = nextEvent.endTick > straightEnd || nextEvent.tieOutToNextMeasure
            for ((partIdx, part) in noteParts.withIndex()) {
                val isLastPart = partIdx == noteParts.lastIndex
                val tied = if (isLastPart) continuesPastRegion else true
                items.add(
                    buildNoteOrChordItem(
                        midiPitches = nextEvent.pitches,
                        duration = part.duration,
                        isDotted = part.isDotted,
                        amplitude = nextEvent.amplitude,
                        isTiedToNext = tied,
                        tuplet = null,
                        keySignature = keySignature,
                    )
                )
            }
            cursor = noteEnd
        }

        val measure = buildMeasurePreservingMetadata(measureNumber, items, existingMeasure)
        return measure to carriedToNext
    }

    private fun buildMeasurePreservingMetadata(
        measureNumber: Int,
        items: List<ScoreItem>,
        existing: ScoreMeasure?,
    ): ScoreMeasure = ScoreMeasure(
        number = measureNumber,
        items = items,
        keySignature = existing?.keySignature,
        timeSignature = existing?.timeSignature,
        clefChanges = existing?.clefChanges?.filterKeys { it == 0 }.orEmpty(),
        dynamics = existing?.dynamics?.filterKeys { it == 0 }.orEmpty(),
    )

    private fun buildNoteOrChordItem(
        midiPitches: List<Int>,
        duration: NoteDuration,
        isDotted: Boolean,
        amplitude: Double,
        isTiedToNext: Boolean,
        tuplet: ScoreTuplet?,
        keySignature: KeySignature,
    ): ScoreItem {
        val spelled = midiPitches.map { SpelledPitch.fromMidiPitch(it, keySignature) }
        return if (spelled.size == 1) {
            ScoreItem.Note(
                pitch = spelled[0],
                duration = duration,
                isDotted = isDotted,
                amplitude = amplitude,
                isTiedToNext = isTiedToNext,
                tuplet = tuplet,
            )
        } else {
            ScoreItem.Chord(
                pitches = spelled,
                duration = duration,
                isDotted = isDotted,
                amplitude = amplitude,
                tiedMidiPitches = if (isTiedToNext) midiPitches.toSet() else emptySet(),
                tuplet = tuplet,
            )
        }
    }

    /** Decomposes a note duration `[startTick, startTick + spanTicks)` into minimal standard/dotted parts. */
    private fun decomposeNoteSpan(
        startTick: Int,
        spanTicks: Int,
        beatTicks: Int,
    ): List<DurationOption> {
        // Exact single standard or dotted note value? Use it directly unless it's unrepresentable.
        STRAIGHT_NOTE_OPTIONS.firstOrNull { it.ticks == spanTicks }?.let { return listOf(it) }

        val parts = mutableListOf<DurationOption>()
        var pos = startTick
        var rem = spanTicks
        while (rem >= NoteDuration.THIRTY_SECOND.ticks) {
            // Prefer exact match for remaining span first
            val exact = STRAIGHT_NOTE_OPTIONS.firstOrNull { it.ticks == rem }
            if (exact != null) {
                parts.add(exact)
                break
            }
            // Otherwise pick largest duration aligned to beat boundary when crossing a beat
            val nextBeat = ((pos / beatTicks) + 1) * beatTicks
            val toBeat = nextBeat - pos
            val chosen = if (pos % beatTicks != 0 && toBeat in NoteDuration.THIRTY_SECOND.ticks until rem) {
                STRAIGHT_NOTE_OPTIONS.firstOrNull { it.ticks <= toBeat }
                    ?: STRAIGHT_NOTE_OPTIONS.last()
            } else {
                STRAIGHT_NOTE_OPTIONS.firstOrNull { it.ticks <= rem }
                    ?: STRAIGHT_NOTE_OPTIONS.last()
            }
            parts.add(chosen)
            pos += chosen.ticks
            rem -= chosen.ticks
        }
        return parts
    }

    /** Decomposes a silence span `[startTick, startTick + spanTicks)` into boundary-aligned rests. */
    private fun decomposeRestSpan(
        startTick: Int,
        spanTicks: Int,
        beatTicks: Int,
    ): List<ScoreItem.Rest> {
        val rests = mutableListOf<ScoreItem.Rest>()
        var pos = startTick
        var rem = spanTicks
        val minTick = NoteDuration.THIRTY_SECOND.ticks
        while (rem >= minTick) {
            val chosen = REST_OPTIONS.firstOrNull { opt ->
                opt.ticks <= rem && isRestBoundaryAligned(pos, opt.ticks, beatTicks)
            } ?: REST_OPTIONS.firstOrNull { it.ticks <= rem } ?: REST_OPTIONS.last()
            rests.add(ScoreItem.Rest(chosen.duration))
            pos += chosen.ticks
            rem -= chosen.ticks
        }
        return rests
    }

    private fun isRestBoundaryAligned(pos: Int, restTicks: Int, beatTicks: Int): Boolean {
        if (restTicks >= beatTicks * 2) return pos % (beatTicks * 2) == 0
        if (restTicks >= beatTicks) return pos % beatTicks == 0
        return pos % restTicks == 0
    }

    private fun beatTicksForTimeSignature(ts: TimeSignature): Int = when {
        ts.denominator == 8 && ts.numerator >= 6 && ts.numerator % 3 == 0 -> NoteDuration.EIGHTH.ticks * 3
        ts.denominator == 8 -> NoteDuration.EIGHTH.ticks
        else -> NoteDuration.QUARTER.ticks
    }
}
