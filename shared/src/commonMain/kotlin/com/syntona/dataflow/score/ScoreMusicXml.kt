package com.syntona.dataflow.score

import com.syntona.currentIsoDate
import com.syntona.dataflow.modules.PianoRollTiming
import com.syntona.util.musicxml.MusicXmlAccidental
import com.syntona.util.musicxml.MusicXmlBarStyle
import com.syntona.util.musicxml.MusicXmlBeamType
import com.syntona.util.musicxml.MusicXmlClef
import com.syntona.util.musicxml.MusicXmlDynamic
import com.syntona.util.musicxml.MusicXmlKey
import com.syntona.util.musicxml.MusicXmlMeasure
import com.syntona.util.musicxml.MusicXmlMeasureAttributes
import com.syntona.util.musicxml.MusicXmlMeasureElement
import com.syntona.util.musicxml.MusicXmlMetadata
import com.syntona.util.musicxml.MusicXmlNoteType
import com.syntona.util.musicxml.MusicXmlPart
import com.syntona.util.musicxml.MusicXmlPitch
import com.syntona.util.musicxml.MusicXmlPlacement
import com.syntona.util.musicxml.MusicXmlScore
import com.syntona.util.musicxml.MusicXmlTieType
import com.syntona.util.musicxml.MusicXmlTime
import com.syntona.util.musicxml.MusicXmlTimeModification
import com.syntona.util.musicxml.MusicXmlTupletNotation
import com.syntona.util.musicxml.MusicXmlWriter
import kotlin.math.roundToInt

/**
 * Adapter translating Syntona's [ScoreData] / [ScoreModule] notation model into
 * [MusicXmlScore] and serialized MusicXML 3.1 text via [com.syntona.util.musicxml.MusicXmlWriter].
 */
object ScoreMusicXml {
    const val DEFAULT_TITLE = "Untitled"
    const val DEFAULT_COMPOSER = "Anonymous"
    private const val SOFTWARE_NAME = "Syntona"

    /** Returns default MusicXML metadata (`Title = "Untitled"`, `Composer = "Anonymous"`, `Date = today`). */
    fun defaultMetadata(
        title: String = DEFAULT_TITLE,
        composer: String = DEFAULT_COMPOSER,
        date: String = currentIsoDate(),
    ): MusicXmlMetadata = MusicXmlMetadata(
        title = title,
        composer = composer,
        date = date,
        software = SOFTWARE_NAME,
    )

    /** Exports [module]'s score and current tempo setting to a MusicXML 3.1 string. */
    fun exportModuleToString(
        module: ScoreModule,
        metadata: MusicXmlMetadata = defaultMetadata(),
    ): String = exportToString(
        scoreData = module.scoreData,
        tempoBpm = module.tempo.getValue(),
        metadata = metadata,
    )

    /** Exports [scoreData] to a MusicXML 3.1 `<score-partwise>` XML string. */
    fun exportToString(
        scoreData: ScoreData,
        tempoBpm: Double = PianoRollTiming.DEFAULT_TEMPO,
        metadata: MusicXmlMetadata = defaultMetadata(),
    ): String = MusicXmlWriter.write(toMusicXmlScore(scoreData, tempoBpm, metadata))

    /** Converts [scoreData] and [tempoBpm] into a standalone [MusicXmlScore] document model. */
    fun toMusicXmlScore(
        scoreData: ScoreData,
        tempoBpm: Double = PianoRollTiming.DEFAULT_TEMPO,
        metadata: MusicXmlMetadata = defaultMetadata(),
    ): MusicXmlScore {
        val measureCount = scoreData.maxMeasureCount().coerceAtLeast(1)
        val primaryMeasures = scoreData.measures.ifEmpty {
            scoreData.instruments.firstOrNull()?.measures.orEmpty()
        }
        val roundedTempo = tempoBpm.roundToInt().coerceAtLeast(1)

        val parts = scoreData.instruments.mapIndexed { instIdx, instrument ->
            buildPart(
                instrument = instrument,
                partId = "P${instIdx + 1}",
                midiChannel = (instIdx % 16) + 1,
                measureCount = measureCount,
                primaryMeasures = primaryMeasures,
                initialKey = scoreData.keySignature,
                initialTime = scoreData.timeSignature,
                includeTempo = instIdx == 0,
                tempoBpm = roundedTempo,
            )
        }

        return MusicXmlScore(metadata = metadata, parts = parts)
    }

    private fun buildPart(
        instrument: ScoreInstrumentPart,
        partId: String,
        midiChannel: Int,
        measureCount: Int,
        primaryMeasures: List<ScoreMeasure>,
        initialKey: KeySignature,
        initialTime: TimeSignature,
        includeTempo: Boolean,
        tempoBpm: Int,
    ): MusicXmlPart {
        val staffCount = instrument.staffCount.coerceIn(1, 2)
        val staffMeasuresList = List(staffCount) { sIdx ->
            ScoreEditing.padMeasuresToCount(instrument.measuresForStaff(sIdx), measureCount)
        }
        val runningClefs = MutableList(staffCount) { sIdx -> instrument.clefForStaff(sIdx) }

        var prevDivisions: Int? = null
        var prevKey: KeySignature? = null
        var prevTime: TimeSignature? = null

        val xmlMeasures = mutableListOf<MusicXmlMeasure>()
        for (mIdx in 0 until measureCount) {
            val effectiveKey = ScoreEditing.effectiveKeyAtMeasure(primaryMeasures, initialKey, mIdx)
            val effectiveTime = ScoreEditing.effectiveTimeAtMeasure(primaryMeasures, initialTime, mIdx)
            val divisions = computeMeasureDivisions(
                effectiveTime = effectiveTime,
                measuresAcrossStaves = staffMeasuresList.map { it[mIdx] },
            )

            val openingAttributes = MusicXmlMeasureAttributes(
                divisions = divisions.takeIf { it != prevDivisions },
                key = if (mIdx == 0 || effectiveKey != prevKey) {
                    MusicXmlKey(fifths = effectiveKey.fifths, mode = "major")
                } else {
                    null
                },
                time = if (mIdx == 0 || effectiveTime != prevTime) {
                    MusicXmlTime(beats = effectiveTime.numerator, beatType = effectiveTime.denominator)
                } else {
                    null
                },
                staves = if (mIdx == 0) staffCount else null,
                clefs = if (mIdx == 0) {
                    List(staffCount) { sIdx -> toMusicXmlClef(runningClefs[sIdx], sIdx + 1) }
                } else {
                    emptyList()
                },
            ).takeIf { attrs ->
                attrs.divisions != null ||
                    attrs.key != null ||
                    attrs.time != null ||
                    attrs.staves != null ||
                    attrs.clefs.isNotEmpty()
            }

            prevDivisions = divisions
            prevKey = effectiveKey
            prevTime = effectiveTime

            val elements = mutableListOf<MusicXmlMeasureElement>()
            if (mIdx == 0 && includeTempo) {
                elements.add(MusicXmlMeasureElement.TempoDirection(bpm = tempoBpm))
            }

            var staff0Duration = 0
            for (sIdx in 0 until staffCount) {
                if (sIdx > 0 && staff0Duration > 0) {
                    elements.add(MusicXmlMeasureElement.Backup(duration = staff0Duration))
                }
                val staffNumber = sIdx + 1
                val voice = if (sIdx == 0) 1 else 5
                val staffMeasures = staffMeasuresList[sIdx]
                val staffResult = buildStaffElements(
                    staffMeasures = staffMeasures,
                    measureIdx = mIdx,
                    openingClef = runningClefs[sIdx],
                    effectiveKey = effectiveKey,
                    effectiveTime = effectiveTime,
                    divisions = divisions,
                    voice = voice,
                    staffNumber = staffNumber,
                )
                runningClefs[sIdx] = staffResult.endingClef
                elements.addAll(staffResult.elements)
                if (sIdx == 0) {
                    staff0Duration = staffResult.totalDuration
                }
            }

            xmlMeasures.add(
                MusicXmlMeasure(
                    number = mIdx + 1,
                    attributes = openingAttributes,
                    elements = elements,
                    barlineRight = if (mIdx == measureCount - 1) MusicXmlBarStyle.LIGHT_HEAVY else null,
                )
            )
        }

        return MusicXmlPart(
            id = partId,
            name = instrument.name,
            midiChannel = midiChannel,
            staves = staffCount,
            measures = xmlMeasures,
        )
    }

    private data class StaffElementsResult(
        val elements: List<MusicXmlMeasureElement>,
        val totalDuration: Int,
        val endingClef: Clef,
    )

    private fun buildStaffElements(
        staffMeasures: List<ScoreMeasure>,
        measureIdx: Int,
        openingClef: Clef,
        effectiveKey: KeySignature,
        effectiveTime: TimeSignature,
        divisions: Int,
        voice: Int,
        staffNumber: Int,
    ): StaffElementsResult {
        val measure = staffMeasures[measureIdx]
        val elements = mutableListOf<MusicXmlMeasureElement>()
        var runningClef = openingClef
        val activeAlters = mutableMapOf<Int, Int>()

        val beamsByItem = computeBeamsByItem(
            measure = measure,
            measureIdx = measureIdx,
            openingClef = openingClef,
            timeSignature = effectiveTime,
        )
        val tupletGroups = ScoreEditing.computeTupletGroups(measure, measureIdx, openingClef, effectiveTime)
        val tupletStarts = mutableMapOf<Int, MusicXmlTupletNotation>()
        val tupletStops = mutableMapOf<Int, MusicXmlTupletNotation>()
        for (group in tupletGroups) {
            if (group.itemIndices.isEmpty()) continue
            val firstIdx = group.itemIndices.first()
            val lastIdx = group.itemIndices.last()
            val showNumber = if (group.displayText.contains(':')) "both" else null
            tupletStarts[firstIdx] = MusicXmlTupletNotation(
                type = MusicXmlTieType.START,
                number = 1,
                bracket = group.drawBracket,
                showNumber = showNumber,
            )
            tupletStops[lastIdx] = MusicXmlTupletNotation(
                type = MusicXmlTieType.STOP,
                number = 1,
            )
        }

        if (measure.items.isEmpty()) {
            measure.clefChanges[0]?.let { newClef ->
                if (measureIdx > 0 && newClef != runningClef) {
                    elements.add(
                        MusicXmlMeasureElement.AttributesChange(
                            MusicXmlMeasureAttributes(clefs = listOf(toMusicXmlClef(newClef, staffNumber)))
                        )
                    )
                }
                runningClef = newClef
            }
            measure.dynamics.keys.sorted().forEach { dynKey ->
                measure.dynamics[dynKey]?.let { dyn ->
                    elements.add(toDynamicDirection(dyn, staffNumber))
                }
            }
            val measureRestDivs = toDivisions(measureQuarterFraction(effectiveTime), divisions)
            elements.add(
                MusicXmlMeasureElement.Note(
                    isChord = false,
                    pitch = null,
                    isMeasureRest = true,
                    duration = measureRestDivs,
                    voice = voice,
                    type = null,
                    staff = staffNumber,
                )
            )
            for (k in measure.clefChanges.keys.sorted()) {
                if (k > 0) {
                    val newClef = measure.clefChanges.getValue(k)
                    if (newClef != runningClef) {
                        elements.add(
                            MusicXmlMeasureElement.AttributesChange(
                                MusicXmlMeasureAttributes(clefs = listOf(toMusicXmlClef(newClef, staffNumber)))
                            )
                        )
                    }
                    runningClef = newClef
                }
            }
            return StaffElementsResult(elements, measureRestDivs, runningClef)
        }

        var totalDivisions = 0
        for (itemIdx in measure.items.indices) {
            measure.clefChanges[itemIdx]?.let { newClef ->
                if ((measureIdx > 0 || itemIdx > 0) && newClef != runningClef) {
                    elements.add(
                        MusicXmlMeasureElement.AttributesChange(
                            MusicXmlMeasureAttributes(clefs = listOf(toMusicXmlClef(newClef, staffNumber)))
                        )
                    )
                }
                runningClef = newClef
            }

            measure.dynamics[itemIdx]?.let { dyn ->
                elements.add(toDynamicDirection(dyn, staffNumber))
            }

            val item = measure.items[itemIdx]
            val itemDivs = toDivisions(itemQuarterFraction(item), divisions)
            totalDivisions += itemDivs

            val noteType = toMusicXmlNoteType(item.duration)
            val dots = if (item.isDotted) 1 else 0
            val timeMod = item.tuplet?.let { MusicXmlTimeModification(it.actualCount, it.normalCount) }
            val tupletNotationList = buildList {
                tupletStarts[itemIdx]?.let { add(it) }
                tupletStops[itemIdx]?.let { add(it) }
            }

            val (incomingTiedMidis, tiedFromPrevMeasure) = incomingTiedPitchesAt(
                staffMeasures = staffMeasures,
                measureIdx = measureIdx,
                itemIdx = itemIdx,
            )

            when (item) {
                is ScoreItem.Rest -> {
                    elements.add(
                        MusicXmlMeasureElement.Note(
                            isChord = false,
                            pitch = null,
                            isMeasureRest = false,
                            duration = itemDivs,
                            voice = voice,
                            type = noteType,
                            dots = dots,
                            timeModification = timeMod,
                            staff = staffNumber,
                            tupletNotations = tupletNotationList,
                        )
                    )
                }

                is ScoreItem.Note, is ScoreItem.Chord -> {
                    val pitches = item.soundingPitches()
                    val itemBeams = beamsByItem[itemIdx].orEmpty()
                    val isStaccato = item.noteStaccato()
                    for ((pIdx, pitch) in pitches.withIndex()) {
                        val isPrimary = pIdx == 0
                        val hasIncomingTie = pitch.midiPitch in incomingTiedMidis
                        val hasOutgoingTie = pitch.midiPitch in item.tiedMidiPitches() &&
                            ScoreEditing.hasActiveTieForPitch(staffMeasures, measureIdx, itemIdx, pitch.midiPitch)
                        val tieList = buildList {
                            if (hasIncomingTie) add(MusicXmlTieType.STOP)
                            if (hasOutgoingTie) add(MusicXmlTieType.START)
                        }

                        val keyDefault = effectiveKey.defaultAlter(pitch.letter)
                        val dStep = pitch.diatonicStep
                        val currentAlter = activeAlters[dStep] ?: keyDefault
                        val displayedAccidental = if (tiedFromPrevMeasure && hasIncomingTie) {
                            null
                        } else if (pitch.alter != currentAlter) {
                            activeAlters[dStep] = pitch.alter
                            toMusicXmlAccidental(pitch.alter)
                        } else {
                            null
                        }

                        elements.add(
                            MusicXmlMeasureElement.Note(
                                isChord = !isPrimary,
                                pitch = MusicXmlPitch(
                                    step = pitch.letter.display[0],
                                    alter = pitch.alter,
                                    octave = pitch.octave,
                                ),
                                isMeasureRest = false,
                                duration = itemDivs,
                                ties = tieList,
                                voice = voice,
                                type = noteType,
                                dots = dots,
                                accidental = displayedAccidental,
                                timeModification = timeMod,
                                staff = staffNumber,
                                beams = if (isPrimary) itemBeams else emptyMap(),
                                tiedNotations = tieList,
                                tupletNotations = if (isPrimary) tupletNotationList else emptyList(),
                                isStaccato = isPrimary && isStaccato,
                            )
                        )
                    }
                }
            }
        }

        // Trailing clefs or dynamics placed past the last item slot
        for (k in measure.clefChanges.keys.sorted()) {
            if (k >= measure.items.size) {
                val newClef = measure.clefChanges.getValue(k)
                if (newClef != runningClef) {
                    elements.add(
                        MusicXmlMeasureElement.AttributesChange(
                            MusicXmlMeasureAttributes(clefs = listOf(toMusicXmlClef(newClef, staffNumber)))
                        )
                    )
                }
                runningClef = newClef
            }
        }
        for (k in measure.dynamics.keys.sorted()) {
            if (k >= measure.items.size) {
                elements.add(toDynamicDirection(measure.dynamics.getValue(k), staffNumber))
            }
        }

        return StaffElementsResult(elements, totalDivisions, runningClef)
    }

    private fun incomingTiedPitchesAt(
        staffMeasures: List<ScoreMeasure>,
        measureIdx: Int,
        itemIdx: Int,
    ): Pair<Set<Int>, Boolean> {
        val currentItem = staffMeasures.getOrNull(measureIdx)?.items?.getOrNull(itemIdx)
            ?: return emptySet<Int>() to false
        val currentMidis = currentItem.soundingPitches().map { it.midiPitch }.toSet()
        if (currentMidis.isEmpty()) return emptySet<Int>() to false

        val (prevItem, fromPrevMeasure) = if (itemIdx > 0) {
            staffMeasures[measureIdx].items.getOrNull(itemIdx - 1) to false
        } else if (measureIdx > 0) {
            staffMeasures[measureIdx - 1].items.lastOrNull() to true
        } else {
            null to false
        }
        if (prevItem == null) return emptySet<Int>() to false
        val activeIncoming = prevItem.tiedMidiPitches().intersect(currentMidis)
        return activeIncoming to fromPrevMeasure
    }

    private fun computeBeamsByItem(
        measure: ScoreMeasure,
        measureIdx: Int,
        openingClef: Clef,
        timeSignature: TimeSignature,
    ): Map<Int, Map<Int, MusicXmlBeamType>> {
        val groups = ScoreEditing.computeBeamGroups(measure, measureIdx, openingClef, timeSignature)
        if (groups.isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, MutableMap<Int, MusicXmlBeamType>>()
        for (group in groups) {
            val indices = group.itemIndices
            val maxFlags = indices.maxOf { measure.items[it].duration.flagCount }
            for (level in 1..maxFlags) {
                for (k in indices.indices) {
                    val idx = indices[k]
                    if (measure.items[idx].duration.flagCount < level) continue
                    val hasPrev = k > 0 && measure.items[indices[k - 1]].duration.flagCount >= level
                    val hasNext = k < indices.lastIndex && measure.items[indices[k + 1]].duration.flagCount >= level
                    val beamType = when {
                        !hasPrev && hasNext -> MusicXmlBeamType.BEGIN
                        hasPrev && hasNext -> MusicXmlBeamType.CONTINUE
                        hasPrev && !hasNext -> MusicXmlBeamType.END
                        k == 0 -> MusicXmlBeamType.FORWARD_HOOK
                        else -> MusicXmlBeamType.BACKWARD_HOOK
                    }
                    result.getOrPut(idx) { mutableMapOf() }[level] = beamType
                }
            }
        }
        return result
    }

    private fun computeMeasureDivisions(
        effectiveTime: TimeSignature,
        measuresAcrossStaves: List<ScoreMeasure>,
    ): Int {
        var divisions = measureQuarterFraction(effectiveTime).den
        for (m in measuresAcrossStaves) {
            for (item in m.items) {
                val frac = itemQuarterFraction(item)
                divisions = lcm(divisions, frac.den)
            }
        }
        return divisions.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun measureQuarterFraction(time: TimeSignature): RationalTicks =
        RationalTicks.simplified(time.numerator.toLong() * 4L, time.denominator.toLong())

    private fun itemQuarterFraction(item: ScoreItem): RationalTicks {
        val exact = item.exactTicks()
        return RationalTicks.simplified(exact.num, exact.den * PianoRollTiming.TPB.toLong())
    }

    private fun toDivisions(quarterFraction: RationalTicks, divisions: Int): Int {
        val raw = quarterFraction.num * divisions.toLong()
        return (raw / quarterFraction.den).toInt().coerceAtLeast(1)
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = kotlin.math.abs(a)
        var y = kotlin.math.abs(b)
        while (y != 0L) {
            val r = x % y
            x = y
            y = r
        }
        return if (x == 0L) 1L else x
    }

    private fun lcm(a: Long, b: Long): Long {
        val safeA = a.coerceAtLeast(1L)
        val safeB = b.coerceAtLeast(1L)
        return (safeA / gcd(safeA, safeB)) * safeB
    }

    private fun toMusicXmlClef(clef: Clef, number: Int): MusicXmlClef = when (clef) {
        Clef.TREBLE -> MusicXmlClef(number = number, sign = "G", line = 2)
        Clef.BASS -> MusicXmlClef(number = number, sign = "F", line = 4)
        Clef.ALTO -> MusicXmlClef(number = number, sign = "C", line = 3)
        Clef.TENOR -> MusicXmlClef(number = number, sign = "C", line = 4)
        Clef.TREBLE_8VB -> MusicXmlClef(number = number, sign = "G", line = 2, octaveChange = -1)
        Clef.TREBLE_8VA -> MusicXmlClef(number = number, sign = "G", line = 2, octaveChange = 1)
        Clef.BASS_8VB -> MusicXmlClef(number = number, sign = "F", line = 4, octaveChange = -1)
        Clef.BASS_8VA -> MusicXmlClef(number = number, sign = "F", line = 4, octaveChange = 1)
    }

    private fun toMusicXmlNoteType(duration: NoteDuration): MusicXmlNoteType = when (duration) {
        NoteDuration.WHOLE -> MusicXmlNoteType.WHOLE
        NoteDuration.HALF -> MusicXmlNoteType.HALF
        NoteDuration.QUARTER -> MusicXmlNoteType.QUARTER
        NoteDuration.EIGHTH -> MusicXmlNoteType.EIGHTH
        NoteDuration.SIXTEENTH -> MusicXmlNoteType.SIXTEENTH
        NoteDuration.THIRTY_SECOND -> MusicXmlNoteType.THIRTY_SECOND
    }

    private fun toMusicXmlAccidental(alter: Int): MusicXmlAccidental = when (alter) {
        -2 -> MusicXmlAccidental.FLAT_FLAT
        -1 -> MusicXmlAccidental.FLAT
        1 -> MusicXmlAccidental.SHARP
        2 -> MusicXmlAccidental.DOUBLE_SHARP
        else -> MusicXmlAccidental.NATURAL
    }

    private fun toDynamicDirection(
        dyn: ScoreDynamicMarking,
        staffNumber: Int,
    ): MusicXmlMeasureElement.DynamicDirection {
        val xmlDyn = when (dyn.mark) {
            DynamicMark.PPPP -> MusicXmlDynamic.PPPP
            DynamicMark.PPP -> MusicXmlDynamic.PPP
            DynamicMark.PP -> MusicXmlDynamic.PP
            DynamicMark.P -> MusicXmlDynamic.P
            DynamicMark.MP -> MusicXmlDynamic.MP
            DynamicMark.MF -> MusicXmlDynamic.MF
            DynamicMark.F -> MusicXmlDynamic.F
            DynamicMark.FF -> MusicXmlDynamic.FF
            DynamicMark.FFF -> MusicXmlDynamic.FFF
            DynamicMark.FFFF -> MusicXmlDynamic.FFFF
            DynamicMark.FP -> MusicXmlDynamic.FP
            DynamicMark.SF -> MusicXmlDynamic.SF
            DynamicMark.SFZ -> MusicXmlDynamic.SFZ
        }
        val placement = when (dyn.placement) {
            DynamicPlacement.ABOVE -> MusicXmlPlacement.ABOVE
            DynamicPlacement.BELOW -> MusicXmlPlacement.BELOW
        }
        return MusicXmlMeasureElement.DynamicDirection(
            dynamic = xmlDyn,
            placement = placement,
            staff = staffNumber,
        )
    }
}
