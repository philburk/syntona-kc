package com.syntona.dataflow.score

import com.softsynth.ksyn.voices.Articulation
import com.syntona.currentIsoDate
import com.syntona.dataflow.modules.PianoRollTiming
import com.syntona.util.musicxml.MusicXmlAccidental
import com.syntona.util.musicxml.MusicXmlArticulation
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
import com.syntona.util.musicxml.MusicXmlReader
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
            measure.techniques.keys.sorted().forEach { techKey ->
                measure.techniques[techKey]?.let { tech ->
                    elements.add(toWordsDirection(tech, staffNumber))
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
            measure.techniques[itemIdx]?.let { tech ->
                elements.add(toWordsDirection(tech, staffNumber))
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
                    val itemArt = item.noteArticulation()
                    val xmlArticulations = toMusicXmlArticulations(itemArt)
                    val isStaccato = itemArt == Articulation.STACCATO
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
                                articulations = if (isPrimary) xmlArticulations else emptyList(),
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
        for (k in measure.techniques.keys.sorted()) {
            if (k >= measure.items.size) {
                elements.add(toWordsDirection(measure.techniques.getValue(k), staffNumber))
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

    private fun toWordsDirection(
        tech: ScoreTechniqueMarking,
        staffNumber: Int,
    ): MusicXmlMeasureElement.WordsDirection {
        val placement = when (tech.placement) {
            DynamicPlacement.ABOVE -> MusicXmlPlacement.ABOVE
            DynamicPlacement.BELOW -> MusicXmlPlacement.BELOW
        }
        return MusicXmlMeasureElement.WordsDirection(
            text = tech.mark.displayText,
            placement = placement,
            staff = staffNumber,
            italic = true,
        )
    }

    private fun toMusicXmlArticulations(articulation: Articulation): List<MusicXmlArticulation> = when (articulation) {
        Articulation.STACCATO -> listOf(MusicXmlArticulation.STACCATO)
        Articulation.STACCATISSIMO -> listOf(MusicXmlArticulation.STACCATISSIMO)
        Articulation.TENUTO -> listOf(MusicXmlArticulation.TENUTO)
        Articulation.MARCATO -> listOf(MusicXmlArticulation.STRONG_ACCENT)
        else -> emptyList()
    }

    /** Score content imported from a MusicXML `<score-partwise>` document. */
    data class ImportResult(
        val metadata: MusicXmlMetadata,
        val keySignature: KeySignature,
        val timeSignature: TimeSignature,
        val tempoBpm: Double?,
        val instruments: List<ScoreInstrumentPart>,
    )

    /** Parses a MusicXML `<score-partwise>` XML string into [ImportResult]. */
    fun importFromString(xml: String): ImportResult =
        fromMusicXmlScore(MusicXmlReader.read(xml))

    /** Converts a parsed [MusicXmlScore] into [ImportResult], ignoring unsupported MusicXML features. */
    fun fromMusicXmlScore(xmlScore: MusicXmlScore): ImportResult {
        if (xmlScore.parts.isEmpty()) {
            throw IllegalArgumentException("MusicXML document contains no <part> elements")
        }

        val measureCount = xmlScore.parts.maxOfOrNull { it.measures.size }?.coerceAtLeast(1) ?: 1
        val tempoBpm = extractOpeningTempo(xmlScore)
        val scoreHasBeamTags = xmlScore.parts.any { part ->
            part.measures.any { m ->
                m.elements.any { el -> el is MusicXmlMeasureElement.Note && el.beams.isNotEmpty() }
            }
        }

        // First collect measure-level key and time signatures across parts so global score timeline matches.
        val measureKeys = Array<KeySignature?>(measureCount) { null }
        val measureTimes = Array<TimeSignature?>(measureCount) { null }
        for (part in xmlScore.parts) {
            for (mIdx in part.measures.indices) {
                val attrs = part.measures[mIdx].attributes ?: continue
                if (measureKeys[mIdx] == null) {
                    attrs.key?.let { measureKeys[mIdx] = mapKeySignature(it) }
                }
                if (measureTimes[mIdx] == null) {
                    attrs.time?.let { measureTimes[mIdx] = mapTimeSignature(it) }
                }
            }
        }

        val initialKey = measureKeys[0] ?: KeySignature.C_MAJOR
        val initialTime = measureTimes[0] ?: TimeSignature.COMMON_TIME
        val effectiveKeys = Array(measureCount) { initialKey }
        val effectiveTimes = Array(measureCount) { initialTime }
        var runningKey = initialKey
        var runningTime = initialTime
        for (mIdx in 0 until measureCount) {
            if (mIdx > 0) {
                measureKeys[mIdx]?.let { runningKey = it }
                measureTimes[mIdx]?.let { runningTime = it }
            }
            effectiveKeys[mIdx] = runningKey
            effectiveTimes[mIdx] = runningTime
        }

        val usedJackNames = mutableSetOf<String>()
        val importedParts = xmlScore.parts.mapIndexed { instIdx, part ->
            importPart(
                part = part,
                instIdx = instIdx,
                measureCount = measureCount,
                effectiveKeys = effectiveKeys,
                effectiveTimes = effectiveTimes,
                scoreHasBeamTags = scoreHasBeamTags,
                usedJackNames = usedJackNames,
            )
        }

        // Propagate measure-level key and time signature changes to all staves of all instruments
        // so ScoreRenderer displays and validates meter/key consistently across the grand staff and parts.
        val deltaKeys = arrayOfNulls<KeySignature>(measureCount)
        val deltaTimes = arrayOfNulls<TimeSignature>(measureCount)
        var prevK = initialKey
        var prevT = initialTime
        for (mIdx in 1 until measureCount) {
            val effK = effectiveKeys[mIdx]
            val effT = effectiveTimes[mIdx]
            deltaKeys[mIdx] = effK.takeIf { it != prevK }
            deltaTimes[mIdx] = effT.takeIf { it != prevT }
            prevK = effK
            prevT = effT
        }
        val finalInstruments = importedParts.map { part ->
            var updated = part.withStaffMeasures(
                0,
                part.measures.mapIndexed { mIdx, m ->
                    m.copy(keySignature = deltaKeys[mIdx], timeSignature = deltaTimes[mIdx])
                },
            )
            if (part.staffCount >= 2) {
                updated = updated.withStaffMeasures(
                    1,
                    part.lowerMeasures.mapIndexed { mIdx, m ->
                        m.copy(keySignature = deltaKeys[mIdx], timeSignature = deltaTimes[mIdx])
                    },
                )
            }
            updated
        }

        return ImportResult(
            metadata = xmlScore.metadata,
            keySignature = initialKey,
            timeSignature = initialTime,
            tempoBpm = tempoBpm,
            instruments = finalInstruments,
        )
    }

    private fun extractOpeningTempo(xmlScore: MusicXmlScore): Double? {
        for (part in xmlScore.parts) {
            val firstMeasure = part.measures.firstOrNull() ?: continue
            val tempoDir = firstMeasure.elements
                .filterIsInstance<MusicXmlMeasureElement.TempoDirection>()
                .firstOrNull()
            if (tempoDir != null) {
                val multiplier = when (tempoDir.beatUnit) {
                    MusicXmlNoteType.WHOLE -> 4.0
                    MusicXmlNoteType.HALF -> 2.0
                    MusicXmlNoteType.QUARTER -> 1.0
                    MusicXmlNoteType.EIGHTH -> 0.5
                    MusicXmlNoteType.SIXTEENTH -> 0.25
                    MusicXmlNoteType.THIRTY_SECOND -> 0.125
                }
                return (tempoDir.bpm * multiplier).coerceIn(
                    PianoRollTiming.MIN_TEMPO,
                    PianoRollTiming.MAX_TEMPO,
                )
            }
        }
        return null
    }

    private data class RawSlot(
        val startTick: Int,
        val staffIdx: Int,
        val voice: Int,
        val primary: MusicXmlMeasureElement.Note,
        val chordNotes: List<MusicXmlMeasureElement.Note>,
        val divisions: Int,
        val dynamics: List<ScoreDynamicMarking>,
        val techniques: List<ScoreTechniqueMarking> = emptyList(),
    )

    private data class RawClefEvent(
        val startTick: Int,
        val staffIdx: Int,
        val clef: Clef,
    )

    private data class ParsedItemInfo(
        val item: ScoreItem,
        val startTick: Int,
        val beamLinkedToNext: Boolean?,
        val outgoingTies: Set<Int>,
    )

    private fun importPart(
        part: MusicXmlPart,
        instIdx: Int,
        measureCount: Int,
        effectiveKeys: Array<KeySignature>,
        effectiveTimes: Array<TimeSignature>,
        scoreHasBeamTags: Boolean,
        usedJackNames: MutableSet<String>,
    ): ScoreInstrumentPart {
        val staffCount = part.staves.coerceIn(1, 2)
        val firstAttrs = part.measures.firstOrNull()?.attributes
        val openingClef0 = firstAttrs?.clefs
            ?.firstOrNull { it.number == null || it.number == 1 }
            ?.let(::mapClef)
            ?: Clef.TREBLE
        val openingClef1 = firstAttrs?.clefs
            ?.firstOrNull { it.number == 2 }
            ?.let(::mapClef)
            ?: Clef.BASS

        val rawMeasuresUpper = MutableList(measureCount) { ScoreMeasure(it + 1, emptyList()) }
        val rawMeasuresLower = MutableList(measureCount) { ScoreMeasure(it + 1, emptyList()) }
        val outgoingTiesUpper = Array<List<Set<Int>>>(measureCount) { emptyList() }
        val outgoingTiesLower = Array<List<Set<Int>>>(measureCount) { emptyList() }

        var runningDivisions = 1
        val runningClefs = mutableListOf(openingClef0, openingClef1)
        val tupletUsedIds = mutableSetOf<String>()

        for (mIdx in 0 until measureCount) {
            val xmlMeasure = part.measures.getOrNull(mIdx)
            if (xmlMeasure == null) {
                rawMeasuresUpper[mIdx] = ScoreMeasure(mIdx + 1, emptyList())
                rawMeasuresLower[mIdx] = ScoreMeasure(mIdx + 1, emptyList())
                continue
            }

            xmlMeasure.attributes?.divisions?.takeIf { it > 0 }?.let { runningDivisions = it }

            val clefEvents = mutableListOf<RawClefEvent>()
            if (mIdx > 0) {
                xmlMeasure.attributes?.clefs?.forEach { xmlClef ->
                    val sIdx = ((xmlClef.number ?: 1) - 1).coerceIn(0, staffCount - 1)
                    clefEvents.add(RawClefEvent(startTick = 0, staffIdx = sIdx, clef = mapClef(xmlClef)))
                }
            }

            val slotsByStaff = Array(staffCount) { mutableListOf<RawSlot>() }
            val pendingDynByStaff = linkedMapOf<Int, MutableList<ScoreDynamicMarking>>()
            val unstaffedDyn = mutableListOf<ScoreDynamicMarking>()
            val pendingTechByStaff = linkedMapOf<Int, MutableList<ScoreTechniqueMarking>>()
            val unstaffedTech = mutableListOf<ScoreTechniqueMarking>()
            var cursorDivs = 0
            var lastPrimarySlot: RawSlot? = null
            var lastStaffIdx = 0

            for (el in xmlMeasure.elements) {
                when (el) {
                    is MusicXmlMeasureElement.AttributesChange -> {
                        el.attributes.divisions?.takeIf { it > 0 }?.let { runningDivisions = it }
                        el.attributes.clefs.forEach { xmlClef ->
                            val sIdx = if (xmlClef.number != null) {
                                (xmlClef.number - 1).coerceIn(0, staffCount - 1)
                            } else {
                                lastStaffIdx.coerceIn(0, staffCount - 1)
                            }
                            clefEvents.add(
                                RawClefEvent(
                                    startTick = cursorDivs,
                                    staffIdx = sIdx,
                                    clef = mapClef(xmlClef),
                                )
                            )
                        }
                    }

                    is MusicXmlMeasureElement.DynamicDirection -> {
                        val marking = toScoreDynamic(el.dynamic, el.placement)
                        val targetStaff = el.staff?.let { (it - 1).coerceIn(0, staffCount - 1) }
                        if (targetStaff != null) {
                            pendingDynByStaff.getOrPut(targetStaff) { mutableListOf() }.add(marking)
                        } else {
                            unstaffedDyn.add(marking)
                        }
                    }

                    is MusicXmlMeasureElement.WordsDirection -> {
                        val marking = parseScoreTechnique(el.text, el.placement)
                        if (marking != null) {
                            val targetStaff = el.staff?.let { (it - 1).coerceIn(0, staffCount - 1) }
                            if (targetStaff != null) {
                                pendingTechByStaff.getOrPut(targetStaff) { mutableListOf() }.add(marking)
                            } else {
                                unstaffedTech.add(marking)
                            }
                        }
                    }

                    is MusicXmlMeasureElement.TempoDirection -> {
                        // Opening tempo already handled at score level.
                    }

                    is MusicXmlMeasureElement.Backup -> {
                        cursorDivs = (cursorDivs - el.duration).coerceAtLeast(0)
                        lastPrimarySlot = null
                    }

                    is MusicXmlMeasureElement.Forward -> {
                        cursorDivs += el.duration
                        lastPrimarySlot = null
                        el.staff?.let { lastStaffIdx = (it - 1).coerceIn(0, staffCount - 1) }
                    }

                    is MusicXmlMeasureElement.Note -> {
                        val noteStaff = ((el.staff ?: 1) - 1)
                        if (el.isChord) {
                            val prev = lastPrimarySlot
                            if (prev != null) {
                                val updatedChord = prev.chordNotes + el
                                val idxInList = slotsByStaff[prev.staffIdx].lastIndex
                                if (idxInList >= 0) {
                                    val updated = prev.copy(chordNotes = updatedChord)
                                    slotsByStaff[prev.staffIdx][idxInList] = updated
                                    lastPrimarySlot = updated
                                }
                            }
                        } else {
                            val startTick = cursorDivs
                            cursorDivs += el.duration.coerceAtLeast(0)
                            if (noteStaff in 0 until staffCount) {
                                lastStaffIdx = noteStaff
                                val attachedDyns = mutableListOf<ScoreDynamicMarking>()
                                pendingDynByStaff.remove(noteStaff)?.let { attachedDyns.addAll(it) }
                                if (unstaffedDyn.isNotEmpty()) {
                                    attachedDyns.addAll(unstaffedDyn)
                                    unstaffedDyn.clear()
                                }
                                for (inlineDyn in el.inlineDynamics) {
                                    attachedDyns.add(
                                        toScoreDynamic(
                                            inlineDyn.dynamic,
                                            inlineDyn.placement ?: MusicXmlPlacement.BELOW,
                                        )
                                    )
                                }
                                val attachedTechs = mutableListOf<ScoreTechniqueMarking>()
                                pendingTechByStaff.remove(noteStaff)?.let { attachedTechs.addAll(it) }
                                if (unstaffedTech.isNotEmpty()) {
                                    attachedTechs.addAll(unstaffedTech)
                                    unstaffedTech.clear()
                                }
                                val slot = RawSlot(
                                    startTick = startTick,
                                    staffIdx = noteStaff,
                                    voice = el.voice,
                                    primary = el,
                                    chordNotes = emptyList(),
                                    divisions = runningDivisions,
                                    dynamics = attachedDyns,
                                    techniques = attachedTechs,
                                )
                                slotsByStaff[noteStaff].add(slot)
                                lastPrimarySlot = slot
                            } else {
                                lastPrimarySlot = null
                            }
                        }
                    }
                }
            }

            for (sIdx in 0 until staffCount) {
                val built = buildImportedStaffMeasure(
                    measureNumber = mIdx + 1,
                    rawSlots = slotsByStaff[sIdx],
                    clefEvents = clefEvents.filter { it.staffIdx == sIdx },
                    openingClef = runningClefs[sIdx],
                    effectiveKey = effectiveKeys[mIdx],
                    effectiveTime = effectiveTimes[mIdx],
                    scoreHasBeamTags = scoreHasBeamTags,
                    tupletUsedIds = tupletUsedIds,
                    trailingDyns = pendingDynByStaff[sIdx].orEmpty() +
                        if (sIdx == 0) unstaffedDyn else emptyList(),
                    trailingTechs = pendingTechByStaff[sIdx].orEmpty() +
                        if (sIdx == 0) unstaffedTech else emptyList(),
                )
                runningClefs[sIdx] = built.endingClef
                if (sIdx == 0) {
                    rawMeasuresUpper[mIdx] = built.measure
                    outgoingTiesUpper[mIdx] = built.outgoingTiesByItem
                } else {
                    rawMeasuresLower[mIdx] = built.measure
                    outgoingTiesLower[mIdx] = built.outgoingTiesByItem
                }
            }
        }

        val cleanedUpper = pruneOrphanedOutgoingTies(rawMeasuresUpper, outgoingTiesUpper)
        val cleanedLower = if (staffCount >= 2) {
            pruneOrphanedOutgoingTies(rawMeasuresLower, outgoingTiesLower)
        } else {
            listOf(ScoreMeasure(1, emptyList()))
        }

        val resolvedName = part.name.trim().ifEmpty { "Instrument ${instIdx + 1}" }
        val profile = resolveInstrumentProfile(resolvedName, staffCount)
        val jackName = ScoreInstrumentPart.uniqueJackName(resolvedName, usedJackNames)
        usedJackNames.add(jackName)

        return ScoreInstrumentPart(
            id = "inst-${instIdx + 1}",
            name = resolvedName,
            instrumentType = profile.type,
            minMidiPitch = profile.minMidi,
            maxMidiPitch = profile.maxMidi,
            staffCount = staffCount,
            clef = openingClef0,
            lowerClef = openingClef1,
            jackName = jackName,
            measures = ScoreEditing.renumber(cleanedUpper),
            lowerMeasures = if (staffCount >= 2) ScoreEditing.renumber(cleanedLower) else listOf(ScoreMeasure(1, emptyList())),
        )
    }

    private data class BuiltImportedMeasure(
        val measure: ScoreMeasure,
        val outgoingTiesByItem: List<Set<Int>>,
        val endingClef: Clef,
    )

    private fun buildImportedStaffMeasure(
        measureNumber: Int,
        rawSlots: List<RawSlot>,
        clefEvents: List<RawClefEvent>,
        openingClef: Clef,
        effectiveKey: KeySignature,
        effectiveTime: TimeSignature,
        scoreHasBeamTags: Boolean,
        tupletUsedIds: MutableSet<String>,
        trailingDyns: List<ScoreDynamicMarking>,
        trailingTechs: List<ScoreTechniqueMarking> = emptyList(),
    ): BuiltImportedMeasure {
        // Pick the primary voice on this staff to keep Syntona's single-voice-per-staff model clean.
        val primaryVoice = rawSlots.minOfOrNull { it.voice }
        val voiceSlots = if (primaryVoice == null) emptyList() else rawSlots.filter { it.voice == primaryVoice }

        val isInvisibleOrMeasureRestOnly = voiceSlots.size == 1 &&
            voiceSlots[0].chordNotes.isEmpty() &&
            voiceSlots[0].primary.pitch == null &&
            (voiceSlots[0].primary.isMeasureRest || !voiceSlots[0].primary.printObject)

        val effectiveSlots = if (isInvisibleOrMeasureRestOnly) emptyList() else voiceSlots

        // Resolve tuplet groupings across the measure's slots.
        val tupletAssignments = arrayOfNulls<ScoreTuplet>(effectiveSlots.size)
        var sIdx = 0
        while (sIdx < effectiveSlots.size) {
            val tm = effectiveSlots[sIdx].primary.timeModification
            if (tm == null) {
                sIdx++
                continue
            }
            val groupIndices = mutableListOf<Int>()
            while (sIdx < effectiveSlots.size) {
                val curr = effectiveSlots[sIdx].primary
                val currTm = curr.timeModification ?: break
                if (currTm.actualNotes != tm.actualNotes || currTm.normalNotes != tm.normalNotes) break
                val startsNew = curr.tupletNotations.any { it.type == MusicXmlTieType.START }
                if (groupIndices.isNotEmpty() && startsNew) break
                groupIndices.add(sIdx)
                sIdx++
                if (curr.tupletNotations.any { it.type == MusicXmlTieType.STOP }) break
                if (curr.tupletNotations.none { it.type == MusicXmlTieType.START || it.type == MusicXmlTieType.STOP } &&
                    groupIndices.size >= tm.actualNotes
                ) {
                    // Without explicit <tuplet> markers, split consecutive runs by actualNotes count
                    // unless unscaled durations haven't filled a group yet.
                    break
                }
            }
            val unscaledDurs = groupIndices.map { i ->
                val (dur, dotted) = mapDuration(effectiveSlots[i].primary, effectiveSlots[i].divisions)
                dur.totalTicks(dotted)
            }
            val unscaledSum = unscaledDurs.sum()
            val allUniform = unscaledDurs.distinct().size <= 1
            val minDur = unscaledDurs.minOrNull() ?: 0
            val inferredBaseTicks = when {
                groupIndices.size < tm.actualNotes && allUniform -> unscaledDurs.firstOrNull() ?: 0
                groupIndices.size < tm.actualNotes && minDur > 0 && unscaledSum == tm.actualNotes * minDur -> minDur
                else -> 0
            }
            val groupId = ScoreEditing.nextTupletGroupId(tupletUsedIds)
            val scoreTuplet = ScoreTuplet(
                groupId = groupId,
                actualCount = tm.actualNotes,
                normalCount = tm.normalNotes,
                baseTicks = inferredBaseTicks,
            )
            for (idx in groupIndices) {
                tupletAssignments[idx] = scoreTuplet
            }
        }

        val parsedItems = mutableListOf<ParsedItemInfo>()
        val dynamicsMap = mutableMapOf<Int, ScoreDynamicMarking>()
        val techniquesMap = mutableMapOf<Int, ScoreTechniqueMarking>()

        for ((idx, slot) in effectiveSlots.withIndex()) {
            val (dur, dotted) = mapDuration(slot.primary, slot.divisions)
            val tuplet = tupletAssignments[idx]
            val noteArticulation = fromMusicXmlArticulations(slot.primary.articulations, slot.primary.isStaccato)
            val staccato = noteArticulation == Articulation.STACCATO

            val allXmlNotes = listOf(slot.primary) + slot.chordNotes
            val isRest = slot.primary.pitch == null && slot.chordNotes.isEmpty()

            val outgoing = mutableSetOf<Int>()
            val scoreItem: ScoreItem = if (isRest) {
                ScoreItem.Rest(duration = dur, isDotted = dotted, tuplet = tuplet)
            } else {
                val mappedPitches = allXmlNotes.mapNotNull { xmlNote ->
                    val xp = xmlNote.pitch ?: return@mapNotNull null
                    val spelled = mapPitch(xp, effectiveKey)
                    val hasTieStart = MusicXmlTieType.START in xmlNote.ties ||
                        MusicXmlTieType.START in xmlNote.tiedNotations
                    if (hasTieStart) {
                        outgoing.add(spelled.midiPitch)
                    }
                    spelled
                }
                val sortedUnique = mappedPitches
                    .distinctBy { it.diatonicStep to it.midiPitch }
                    .sortedWith(compareBy<SpelledPitch> { it.diatonicStep }.thenBy { it.midiPitch })
                when (sortedUnique.size) {
                    0 -> ScoreItem.Rest(duration = dur, isDotted = dotted, tuplet = tuplet)
                    1 -> ScoreItem.Note(
                        pitch = sortedUnique[0],
                        duration = dur,
                        isDotted = dotted,
                        isStaccato = staccato,
                        isTiedToNext = sortedUnique[0].midiPitch in outgoing,
                        tuplet = tuplet,
                        articulation = noteArticulation,
                    )
                    else -> ScoreItem.Chord(
                        pitches = sortedUnique,
                        duration = dur,
                        isDotted = dotted,
                        isStaccato = staccato,
                        tiedMidiPitches = outgoing,
                        tuplet = tuplet,
                        articulation = noteArticulation,
                    )
                }
            }

            if (slot.dynamics.isNotEmpty()) {
                dynamicsMap[idx] = slot.dynamics.last()
            }
            if (slot.techniques.isNotEmpty()) {
                techniquesMap[idx] = slot.techniques.last()
            }
            val level1Beam = slot.primary.beams[1]
            val linkedToNext = when (level1Beam) {
                MusicXmlBeamType.BEGIN, MusicXmlBeamType.CONTINUE -> true
                else -> false
            }
            parsedItems.add(
                ParsedItemInfo(
                    item = scoreItem,
                    startTick = slot.startTick,
                    beamLinkedToNext = if (scoreHasBeamTags) linkedToNext else null,
                    outgoingTies = outgoing,
                )
            )
        }

        // Preserve dynamic and technique carried on an empty/measure-rest slot (place at slot 0).
        if (effectiveSlots.isEmpty() && voiceSlots.isNotEmpty()) {
            voiceSlots.firstOrNull { it.dynamics.isNotEmpty() }?.dynamics?.lastOrNull()?.let { dyn ->
                dynamicsMap[0] = dyn
            }
            voiceSlots.firstOrNull { it.techniques.isNotEmpty() }?.techniques?.lastOrNull()?.let { tech ->
                techniquesMap[0] = tech
            }
        }
        if (trailingDyns.isNotEmpty() && parsedItems.isNotEmpty()) {
            val lastIdx = parsedItems.lastIndex
            if (lastIdx !in dynamicsMap) {
                dynamicsMap[lastIdx] = trailingDyns.last()
            }
        }
        if (trailingTechs.isNotEmpty() && parsedItems.isNotEmpty()) {
            val lastIdx = parsedItems.lastIndex
            if (lastIdx !in techniquesMap) {
                techniquesMap[lastIdx] = trailingTechs.last()
            }
        }

        // Map clef changes onto item indices in this measure.
        val clefChangesMap = mutableMapOf<Int, Clef>()
        var runningClef = openingClef
        for (ev in clefEvents.sortedBy { it.startTick }) {
            val targetIdx = parsedItems.indexOfFirst { it.startTick >= ev.startTick }
                .let { if (it < 0) parsedItems.size else it }
            if (ev.clef != runningClef) {
                clefChangesMap[targetIdx] = ev.clef
                runningClef = ev.clef
            }
        }

        val itemsList = parsedItems.map { it.item }
        val baseMeasure = ScoreMeasure(
            number = measureNumber,
            items = itemsList,
            clefChanges = clefChangesMap,
            dynamics = dynamicsMap,
            techniques = techniquesMap,
        )

        val beamOverrides = mutableMapOf<Int, Boolean>()
        if (scoreHasBeamTags && itemsList.size >= 2) {
            for (i in 0 until itemsList.lastIndex) {
                val left = itemsList[i]
                val right = itemsList[i + 1]
                if (!ScoreEditing.isBeamableItem(left) || !ScoreEditing.isBeamableItem(right)) continue
                val xmlLinked = parsedItems[i].beamLinkedToNext ?: continue
                val auto = ScoreEditing.autoBeamedAfter(baseMeasure, openingClef, effectiveTime, i)
                if (xmlLinked != auto) {
                    beamOverrides[i] = xmlLinked
                }
            }
        }

        return BuiltImportedMeasure(
            measure = if (beamOverrides.isEmpty()) baseMeasure else baseMeasure.copy(beamOverrides = beamOverrides),
            outgoingTiesByItem = parsedItems.map { it.outgoingTies },
            endingClef = runningClef,
        )
    }

    private fun pruneOrphanedOutgoingTies(
        measures: List<ScoreMeasure>,
        outgoingByMeasure: Array<List<Set<Int>>>,
    ): List<ScoreMeasure> {
        return measures.mapIndexed { mIdx, m ->
            val updatedItems = m.items.mapIndexed { iIdx, item ->
                val wanted = outgoingByMeasure[mIdx].getOrNull(iIdx).orEmpty()
                if (wanted.isEmpty()) {
                    item
                } else {
                    val nextLoc = ScoreEditing.nextItemLocation(measures, mIdx, iIdx)
                    val nextMidis = nextLoc?.let { ScoreEditing.itemAt(measures, it) }
                        ?.soundingPitches()?.map { it.midiPitch }?.toSet()
                        ?: emptySet()
                    val validTies = wanted.intersect(nextMidis)
                    when (item) {
                        is ScoreItem.Note -> item.copy(isTiedToNext = item.pitch.midiPitch in validTies)
                        is ScoreItem.Chord -> item.copy(tiedMidiPitches = validTies)
                        is ScoreItem.Rest -> item
                    }
                }
            }
            if (updatedItems == m.items) m else m.copy(items = updatedItems)
        }
    }

    private fun mapKeySignature(key: MusicXmlKey): KeySignature {
        val clamped = key.fifths.coerceIn(-7, 7)
        return KeySignature.entries.firstOrNull { it.fifths == clamped } ?: KeySignature.C_MAJOR
    }

    private fun mapTimeSignature(time: MusicXmlTime): TimeSignature {
        val num = time.beats.coerceIn(TimeSignature.MIN_NUMERATOR, TimeSignature.MAX_NUMERATOR)
        val den = if (time.beatType in TimeSignature.VALID_DENOMINATORS) {
            time.beatType
        } else {
            TimeSignature.VALID_DENOMINATORS.minByOrNull { kotlin.math.abs(it - time.beatType) } ?: 4
        }
        return TimeSignature(num, den)
    }

    private fun mapClef(xmlClef: MusicXmlClef): Clef {
        val sign = xmlClef.sign.uppercase()
        val oct = xmlClef.octaveChange ?: 0
        return when (sign) {
            "G" -> when {
                oct < 0 -> Clef.TREBLE_8VB
                oct > 0 -> Clef.TREBLE_8VA
                else -> Clef.TREBLE
            }
            "F" -> when {
                oct < 0 -> Clef.BASS_8VB
                oct > 0 -> Clef.BASS_8VA
                else -> Clef.BASS
            }
            "C" -> if (xmlClef.line >= 4) Clef.TENOR else Clef.ALTO
            else -> Clef.TREBLE
        }
    }

    private fun mapDuration(note: MusicXmlMeasureElement.Note, divisions: Int): Pair<NoteDuration, Boolean> {
        val dotted = note.dots >= 1
        note.type?.let { t ->
            val dur = when (t) {
                MusicXmlNoteType.WHOLE -> NoteDuration.WHOLE
                MusicXmlNoteType.HALF -> NoteDuration.HALF
                MusicXmlNoteType.QUARTER -> NoteDuration.QUARTER
                MusicXmlNoteType.EIGHTH -> NoteDuration.EIGHTH
                MusicXmlNoteType.SIXTEENTH -> NoteDuration.SIXTEENTH
                MusicXmlNoteType.THIRTY_SECOND -> NoteDuration.THIRTY_SECOND
            }
            return dur to dotted
        }

        val safeDivs = divisions.coerceAtLeast(1).toDouble()
        val tm = note.timeModification
        val unscaledQuarters = if (tm != null) {
            (note.duration.toDouble() / safeDivs) * (tm.actualNotes.toDouble() / tm.normalNotes.toDouble())
        } else {
            note.duration.toDouble() / safeDivs
        }
        val candidates = NoteDuration.entries.flatMap { d ->
            val baseQuarters = d.ticks.toDouble() / PianoRollTiming.TPB.toDouble()
            listOf(
                Triple(d, false, baseQuarters),
                Triple(d, true, baseQuarters * 1.5),
            )
        }
        val best = candidates.minByOrNull { kotlin.math.abs(it.third - unscaledQuarters) }
        return (best?.first ?: NoteDuration.QUARTER) to (best?.second ?: dotted)
    }

    private fun mapPitch(pitch: MusicXmlPitch, activeKey: KeySignature): SpelledPitch {
        val letter = when (pitch.step.uppercaseChar()) {
            'C' -> NoteLetter.C
            'D' -> NoteLetter.D
            'E' -> NoteLetter.E
            'F' -> NoteLetter.F
            'G' -> NoteLetter.G
            'A' -> NoteLetter.A
            'B' -> NoteLetter.B
            else -> NoteLetter.C
        }
        return if (pitch.alter in -1..1) {
            SpelledPitch(letter = letter, alter = pitch.alter, octave = pitch.octave)
        } else {
            val midi = ((pitch.octave + 1) * 12 + letter.semitoneOffset + pitch.alter).coerceIn(0, 127)
            SpelledPitch.fromMidiPitch(midi, activeKey)
        }
    }

    private fun toScoreDynamic(
        dyn: MusicXmlDynamic,
        placement: MusicXmlPlacement,
    ): ScoreDynamicMarking {
        val mark = when (dyn) {
            MusicXmlDynamic.PPPP -> DynamicMark.PPPP
            MusicXmlDynamic.PPP -> DynamicMark.PPP
            MusicXmlDynamic.PP -> DynamicMark.PP
            MusicXmlDynamic.P -> DynamicMark.P
            MusicXmlDynamic.MP -> DynamicMark.MP
            MusicXmlDynamic.MF -> DynamicMark.MF
            MusicXmlDynamic.F -> DynamicMark.F
            MusicXmlDynamic.FF -> DynamicMark.FF
            MusicXmlDynamic.FFF -> DynamicMark.FFF
            MusicXmlDynamic.FFFF -> DynamicMark.FFFF
            MusicXmlDynamic.FP -> DynamicMark.FP
            MusicXmlDynamic.SF -> DynamicMark.SF
            MusicXmlDynamic.SFZ -> DynamicMark.SFZ
        }
        val scorePlacement = when (placement) {
            MusicXmlPlacement.ABOVE -> DynamicPlacement.ABOVE
            MusicXmlPlacement.BELOW -> DynamicPlacement.BELOW
        }
        return ScoreDynamicMarking(mark = mark, placement = scorePlacement)
    }

    private fun fromMusicXmlArticulations(
        articulations: List<MusicXmlArticulation>,
        isStaccato: Boolean,
    ): Articulation {
        val first = articulations.firstOrNull()
        return when (first) {
            MusicXmlArticulation.STACCATO -> Articulation.STACCATO
            MusicXmlArticulation.STACCATISSIMO -> Articulation.STACCATISSIMO
            MusicXmlArticulation.TENUTO -> Articulation.TENUTO
            MusicXmlArticulation.STRONG_ACCENT -> Articulation.MARCATO
            null -> if (isStaccato) Articulation.STACCATO else Articulation.NORMAL
        }
    }

    private fun parseScoreTechnique(
        rawText: String,
        placement: MusicXmlPlacement,
    ): ScoreTechniqueMarking? {
        val norm = rawText.trim().lowercase().replace(Regex("\\s+"), " ")
        val mark = when (norm) {
            "pizz.", "pizz", "pizzicato" -> ScoreTechniqueMark.PIZZICATO
            "arco" -> ScoreTechniqueMark.ARCO
            "con sord.", "con sord", "con sordino", "mute", "muted" -> ScoreTechniqueMark.CON_SORDINO
            "senza sord.", "senza sord", "senza sordino", "unmute", "open" -> ScoreTechniqueMark.SENZA_SORDINO
            "son filé", "son file" -> ScoreTechniqueMark.SON_FILE
            "ordinario", "ord.", "ord" -> ScoreTechniqueMark.ORDINARIO
            else -> return null
        }
        val scorePlacement = when (placement) {
            MusicXmlPlacement.ABOVE -> DynamicPlacement.ABOVE
            MusicXmlPlacement.BELOW -> DynamicPlacement.BELOW
        }
        return ScoreTechniqueMarking(mark = mark, placement = scorePlacement)
    }

    private data class InstrumentProfile(
        val type: InstrumentType,
        val minMidi: Int,
        val maxMidi: Int,
    )

    private fun resolveInstrumentProfile(name: String, staffCount: Int): InstrumentProfile {
        val preset = InstrumentPreset.entries.firstOrNull {
            name.contains(it.displayName, ignoreCase = true)
        }
        if (preset != null) {
            return InstrumentProfile(preset.instrumentType, preset.minMidiPitch, preset.maxMidiPitch)
        }
        val inferredType = when {
            staffCount >= 2 -> InstrumentType.KEYBOARDS
            name.contains("guitar", ignoreCase = true) ||
                name.contains("harp", ignoreCase = true) -> InstrumentType.PLUCKED_STRINGS
            name.contains("flute", ignoreCase = true) ||
                name.contains("clarinet", ignoreCase = true) ||
                name.contains("oboe", ignoreCase = true) ||
                name.contains("sax", ignoreCase = true) -> InstrumentType.WOODWIND
            name.contains("horn", ignoreCase = true) ||
                name.contains("trombone", ignoreCase = true) ||
                name.contains("tuba", ignoreCase = true) -> InstrumentType.BRASS
            else -> InstrumentType.KEYBOARDS
        }
        return InstrumentProfile(
            inferredType,
            ScoreInstrumentPart.DEFAULT_MIN_MIDI,
            ScoreInstrumentPart.DEFAULT_MAX_MIDI,
        )
    }
}
