package com.syntona.dataflow.score

import com.softsynth.ksyn.voices.Articulation
import com.syntona.util.musicxml.MusicXmlAccidental
import com.syntona.util.musicxml.MusicXmlBeamType
import com.syntona.util.musicxml.MusicXmlDynamic
import com.syntona.util.musicxml.MusicXmlMeasureElement
import com.syntona.util.musicxml.MusicXmlNoteType
import com.syntona.util.musicxml.MusicXmlPlacement
import com.syntona.util.musicxml.MusicXmlTieType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScoreMusicXmlTest {

    @Test
    fun testDefaultMetadataAndTempoFromModule() {
        val module = ScoreModule()
        module.tempo.setValue(90.0)

        val meta = ScoreMusicXml.defaultMetadata()
        assertEquals("Untitled", meta.title)
        assertEquals("Anonymous", meta.composer)
        assertTrue(
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(meta.date.orEmpty()),
            "Expected YYYY-MM-DD date, got ${meta.date}",
        )

        val xml = ScoreMusicXml.exportModuleToString(module)
        assertContains(xml, "<work-title>Untitled</work-title>")
        assertContains(xml, """<creator type="composer">Anonymous</creator>""")
        assertContains(xml, "<encoding-date>${meta.date}</encoding-date>")
        assertContains(xml, "<per-minute>90</per-minute>")
        assertContains(xml, """<sound tempo="90"/>""")
    }

    @Test
    fun testExportMultiInstrumentGrandStaffTupletsTiesClefAndDynamics() {
        val score = ScoreData().apply {
            keySignature = KeySignature.D_MAJOR
            timeSignature = TimeSignature.COMMON_TIME
            instruments.clear()

            val quintuplet = ScoreTuplet("t1", actualCount = 5, normalCount = 4)
            val triplet = ScoreTuplet("t2", actualCount = 3, normalCount = 2)

            val violinMeasures = listOf(
                // M1: quarter A4, quarter D5, beamed 8th staccato A4 D5 C#5 E5
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.EIGHTH, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 1, 5), NoteDuration.EIGHTH, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 5), NoteDuration.QUARTER),
                    ),
                ),
                // M2: 5:4 tuplet with a rest + 2 quarters
                ScoreMeasure(
                    number = 2,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Rest(NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.QUARTER),
                    ),
                ),
                // M3: Bb4 (flat), Bb4 (no accidental repeat), C5 (natural), quarter
                ScoreMeasure(
                    number = 3,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, -1, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, -1, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 5), NoteDuration.QUARTER),
                    ),
                ),
                // M4: 5/4 + Key C Maj + dotted 8th + 16th + mid-measure bass clef change
                ScoreMeasure(
                    number = 4,
                    keySignature = KeySignature.C_MAJOR,
                    timeSignature = TimeSignature.FIVE_FOUR,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.EIGHTH, isDotted = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.SIXTEENTH),
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 3), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 3), NoteDuration.QUARTER),
                    ),
                    clefChanges = mapOf(2 to Clef.BASS),
                ),
            )

            val pianoUpper = listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Chord(
                            listOf(SpelledPitch(NoteLetter.C, 1, 5), SpelledPitch(NoteLetter.E, 0, 5)),
                            NoteDuration.HALF,
                        ),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.HALF),
                    ),
                    dynamics = mapOf(0 to ScoreDynamicMarking(DynamicMark.FF, DynamicPlacement.BELOW)),
                ),
                ScoreMeasure(
                    number = 2,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.EIGHTH, tuplet = triplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.EIGHTH, tuplet = triplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 5), NoteDuration.EIGHTH, tuplet = triplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 5), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.QUARTER),
                    ),
                ),
                ScoreMeasure(
                    number = 3,
                    items = listOf(ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.WHOLE)),
                ),
                ScoreMeasure(
                    number = 4,
                    keySignature = KeySignature.C_MAJOR,
                    timeSignature = TimeSignature.FIVE_FOUR,
                    items = emptyList(),
                ),
            )

            val pianoLower = listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.HALF, isTiedToNext = true),
                    ),
                ),
                ScoreMeasure(
                    number = 2,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 3), NoteDuration.HALF, isDotted = true),
                    ),
                ),
                ScoreMeasure(
                    number = 3,
                    items = emptyList(),
                ),
                ScoreMeasure(
                    number = 4,
                    keySignature = KeySignature.C_MAJOR,
                    timeSignature = TimeSignature.FIVE_FOUR,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 3), NoteDuration.WHOLE),
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 2), NoteDuration.QUARTER),
                    ),
                    dynamics = mapOf(0 to ScoreDynamicMarking(DynamicMark.PP, DynamicPlacement.ABOVE)),
                ),
            )

            instruments.add(
                ScoreInstrumentPart(
                    id = "inst-1",
                    name = "Violin",
                    staffCount = 1,
                    clef = Clef.TREBLE,
                    measures = violinMeasures,
                )
            )
            instruments.add(
                ScoreInstrumentPart(
                    id = "inst-2",
                    name = "Piano",
                    staffCount = 2,
                    clef = Clef.TREBLE,
                    lowerClef = Clef.BASS,
                    measures = pianoUpper,
                    lowerMeasures = pianoLower,
                )
            )
            replaceMeasures(violinMeasures)
        }

        val xmlScore = ScoreMusicXml.toMusicXmlScore(
            scoreData = score,
            tempoBpm = 108.0,
            metadata = ScoreMusicXml.defaultMetadata(date = "2026-11-16"),
        )
        assertEquals(2, xmlScore.parts.size)

        val violinPart = xmlScore.parts[0]
        assertEquals("P1", violinPart.id)
        assertEquals(4, violinPart.measures.size)

        // Check M1 beams & staccato + non-accidental C# in D major
        val vM1Notes = violinPart.measures[0].elements.filterIsInstance<MusicXmlMeasureElement.Note>()
        assertEquals(5, vM1Notes.size)
        assertEquals(MusicXmlBeamType.BEGIN, vM1Notes[2].beams[1])
        assertEquals(MusicXmlBeamType.END, vM1Notes[3].beams[1])
        assertTrue(vM1Notes[2].isStaccato)
        assertEquals(null, vM1Notes[3].accidental) // C#5 is diatonic in D major

        // Check M2 5:4 tuplet spans rest cleanly with exact divisions
        val vM2Notes = violinPart.measures[1].elements.filterIsInstance<MusicXmlMeasureElement.Note>()
        assertNotNull(vM2Notes[0].tupletNotations.firstOrNull { it.type == MusicXmlTieType.START })
        assertNotNull(vM2Notes[4].tupletNotations.firstOrNull { it.type == MusicXmlTieType.STOP })
        assertTrue(vM2Notes[1].pitch == null && !vM2Notes[1].isMeasureRest)
        val m2Divisions = violinPart.measures[1].attributes?.divisions
        assertNotNull(m2Divisions)
        assertEquals(4 * m2Divisions, vM2Notes.sumOf { it.duration })

        // Check M3 accidentals: Bb -> flat, second Bb -> null, C5 -> natural
        val vM3Notes = violinPart.measures[2].elements.filterIsInstance<MusicXmlMeasureElement.Note>()
        assertEquals(MusicXmlAccidental.FLAT, vM3Notes[1].accidental)
        assertEquals(null, vM3Notes[2].accidental)
        assertEquals(MusicXmlAccidental.NATURAL, vM3Notes[3].accidental)

        // Check M4 key/time change + dotted 8th + 16th hook + mid-bar bass clef change
        val vM4 = violinPart.measures[3]
        assertEquals(0, vM4.attributes?.key?.fifths)
        assertEquals(5, vM4.attributes?.time?.beats)
        assertEquals(4, vM4.attributes?.time?.beatType)
        val clefChange = vM4.elements.filterIsInstance<MusicXmlMeasureElement.AttributesChange>().single()
        assertEquals("F", clefChange.attributes.clefs.single().sign)
        val vM4Notes = vM4.elements.filterIsInstance<MusicXmlMeasureElement.Note>()
        assertEquals(MusicXmlBeamType.BEGIN, vM4Notes[0].beams[1])
        assertEquals(MusicXmlBeamType.END, vM4Notes[1].beams[1])
        assertEquals(MusicXmlBeamType.BACKWARD_HOOK, vM4Notes[1].beams[2])

        // Check Piano grand staff: both staves present, backup matches staff 1 duration, cross-bar tie, empty bar rest, dynamics
        val pianoPart = xmlScore.parts[1]
        assertEquals(2, pianoPart.staves)
        val pM1 = pianoPart.measures[0]
        assertEquals(2, pM1.attributes?.staves)
        val pM1Dyn = pM1.elements.filterIsInstance<MusicXmlMeasureElement.DynamicDirection>().single()
        assertEquals(MusicXmlDynamic.FF, pM1Dyn.dynamic)
        assertEquals(MusicXmlPlacement.BELOW, pM1Dyn.placement)
        val pM1Notes = pM1.elements.filterIsInstance<MusicXmlMeasureElement.Note>()
        assertFalse(pM1Notes[0].isChord)
        assertTrue(pM1Notes[1].isChord)
        val pM1Backup = pM1.elements.filterIsInstance<MusicXmlMeasureElement.Backup>().single()
        assertEquals(pM1Notes.filter { !it.isChord && it.staff == 1 }.sumOf { it.duration }, pM1Backup.duration)

        // Cross-barline tie from M1 last lower note to M2 first lower note
        val m1LastLower = pM1Notes.last { it.staff == 2 }
        assertEquals(listOf(MusicXmlTieType.START), m1LastLower.ties)
        val pM2LowerFirst = pianoPart.measures[1].elements.filterIsInstance<MusicXmlMeasureElement.Note>().first { it.staff == 2 }
        assertEquals(listOf(MusicXmlTieType.STOP), pM2LowerFirst.ties)

        // M3 empty lower staff emits measure rest; M4 empty upper staff emits measure rest + backup before staff 2
        val pM3LowerRest = pianoPart.measures[2].elements.filterIsInstance<MusicXmlMeasureElement.Note>().single { it.staff == 2 }
        assertTrue(pM3LowerRest.isMeasureRest)
        val pM4UpperRest = pianoPart.measures[3].elements.filterIsInstance<MusicXmlMeasureElement.Note>().single { it.staff == 1 }
        assertTrue(pM4UpperRest.isMeasureRest)
        val pM4Dyn = pianoPart.measures[3].elements.filterIsInstance<MusicXmlMeasureElement.DynamicDirection>().single()
        assertEquals(MusicXmlDynamic.PP, pM4Dyn.dynamic)
        assertEquals(MusicXmlPlacement.ABOVE, pM4Dyn.placement)
        assertEquals(2, pM4Dyn.staff)

        // Serialized string is valid MusicXML with both parts and final double barline
        val xmlString = ScoreMusicXml.exportToString(score, 108.0, ScoreMusicXml.defaultMetadata(date = "2026-11-16"))
        assertContains(xmlString, """<score-partwise version="3.1">""")
        assertContains(xmlString, """<part id="P1">""")
        assertContains(xmlString, """<part id="P2">""")
        assertContains(xmlString, """<bar-style>light-heavy</bar-style>""")
    }

    @Test
    fun testExport8vbClefEmitsClefOctaveChange() {
        val score = ScoreData().apply {
            instruments.clear()
            instruments.add(
                ScoreInstrumentPart(
                    id = "inst-1",
                    name = "Guitar",
                    staffCount = 1,
                    clef = Clef.TREBLE_8VB,
                    measures = listOf(
                        ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 3), NoteDuration.WHOLE)))
                    ),
                )
            )
            replaceMeasures(instruments[0].measures)
        }
        val xml = ScoreMusicXml.exportToString(score)
        assertContains(xml, "<sign>G</sign>")
        assertContains(xml, "<line>2</line>")
        assertContains(xml, "<clef-octave-change>-1</clef-octave-change>")
    }

    @Test
    fun testRoundTripExportAndImportPreservesMultiInstrumentScore() {
        val score = ScoreData().apply {
            keySignature = KeySignature.D_MAJOR
            timeSignature = TimeSignature.COMMON_TIME
            instruments.clear()

            val quintuplet = ScoreTuplet("t1", actualCount = 5, normalCount = 4)
            val triplet = ScoreTuplet("t2", actualCount = 3, normalCount = 2)

            val violinMeasures = listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.EIGHTH, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 1, 5), NoteDuration.EIGHTH, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 5), NoteDuration.QUARTER),
                    ),
                ),
                ScoreMeasure(
                    number = 2,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Rest(NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.EIGHTH, tuplet = quintuplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.QUARTER),
                    ),
                ),
                ScoreMeasure(
                    number = 3,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.EIGHTH),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, -1, 4), NoteDuration.EIGHTH),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, -1, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 5), NoteDuration.HALF),
                    ),
                    // Manual beam break between index 0 and 1
                    beamOverrides = mapOf(0 to false),
                ),
                ScoreMeasure(
                    number = 4,
                    keySignature = KeySignature.C_MAJOR,
                    timeSignature = TimeSignature.FIVE_FOUR,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.EIGHTH, isDotted = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.SIXTEENTH),
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 3), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 3), NoteDuration.QUARTER),
                    ),
                    clefChanges = mapOf(2 to Clef.BASS),
                ),
            )

            val pianoUpper = listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Chord(
                            listOf(SpelledPitch(NoteLetter.C, 1, 5), SpelledPitch(NoteLetter.E, 0, 5)),
                            NoteDuration.HALF,
                        ),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.HALF),
                    ),
                    dynamics = mapOf(0 to ScoreDynamicMarking(DynamicMark.FF, DynamicPlacement.BELOW)),
                ),
                ScoreMeasure(
                    number = 2,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.EIGHTH, tuplet = triplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.EIGHTH, tuplet = triplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 5), NoteDuration.EIGHTH, tuplet = triplet),
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 5), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.QUARTER),
                    ),
                ),
                ScoreMeasure(
                    number = 3,
                    items = listOf(ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.WHOLE)),
                ),
                ScoreMeasure(
                    number = 4,
                    keySignature = KeySignature.C_MAJOR,
                    timeSignature = TimeSignature.FIVE_FOUR,
                    items = emptyList(),
                ),
            )

            val pianoLower = listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.HALF, isTiedToNext = true),
                    ),
                ),
                ScoreMeasure(
                    number = 2,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 3), NoteDuration.HALF, isDotted = true),
                    ),
                ),
                ScoreMeasure(
                    number = 3,
                    items = emptyList(),
                ),
                ScoreMeasure(
                    number = 4,
                    keySignature = KeySignature.C_MAJOR,
                    timeSignature = TimeSignature.FIVE_FOUR,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 3), NoteDuration.WHOLE),
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 2), NoteDuration.QUARTER),
                    ),
                    dynamics = mapOf(0 to ScoreDynamicMarking(DynamicMark.PP, DynamicPlacement.ABOVE)),
                ),
            )

            instruments.add(
                ScoreInstrumentPart(
                    id = "inst-1",
                    name = "Violin",
                    staffCount = 1,
                    clef = Clef.TREBLE,
                    measures = violinMeasures,
                )
            )
            instruments.add(
                ScoreInstrumentPart(
                    id = "inst-2",
                    name = "Piano",
                    staffCount = 2,
                    clef = Clef.TREBLE,
                    lowerClef = Clef.BASS,
                    measures = pianoUpper,
                    lowerMeasures = pianoLower,
                )
            )
            replaceMeasures(violinMeasures)
        }

        val fixedMeta = ScoreMusicXml.defaultMetadata(title = "RoundTrip", composer = "Tester", date = "2026-11-16")
        val exportedXml = ScoreMusicXml.exportToString(score, tempoBpm = 108.0, metadata = fixedMeta)
        val imported = ScoreMusicXml.importFromString(exportedXml)

        assertEquals(KeySignature.D_MAJOR, imported.keySignature)
        assertEquals(TimeSignature.COMMON_TIME, imported.timeSignature)
        assertEquals(108.0, imported.tempoBpm)
        assertEquals(2, imported.instruments.size)

        // Compare both instruments across all staves & measures (tuplet groupIds normalized)
        for (iIdx in score.instruments.indices) {
            val orig = score.instruments[iIdx]
            val imp = imported.instruments[iIdx]
            assertEquals(orig.name, imp.name)
            assertEquals(orig.staffCount, imp.staffCount)
            assertEquals(orig.clef, imp.clef)
            assertEquals(orig.lowerClef, imp.lowerClef)
            for (sIdx in 0 until orig.staffCount) {
                val origStaff = orig.measuresForStaff(sIdx)
                val impStaff = imp.measuresForStaff(sIdx)
                assertEquals(origStaff.size, impStaff.size, "Measure count mismatch inst=$iIdx staff=$sIdx")
                for (mIdx in origStaff.indices) {
                    assertMeasuresMusicallyEqual(origStaff[mIdx], impStaff[mIdx], "inst=$iIdx staff=$sIdx m=$mIdx")
                }
            }
        }

        // Verify re-export idempotence
        val reimportedScore = ScoreData().apply {
            keySignature = imported.keySignature
            timeSignature = imported.timeSignature
            replaceAllInstruments(imported.instruments)
        }
        val reExportedXml = ScoreMusicXml.exportToString(reimportedScore, tempoBpm = 108.0, metadata = fixedMeta)
        assertEquals(exportedXml, reExportedXml)
    }

    @Test
    fun testImportIgnoresUnsupportedFeaturesAndUndoRestoresScore() {
        val module = ScoreModule()
        val originalInstCount = module.scoreData.instruments.size
        val originalKey = module.scoreData.keySignature
        val originalTempo = module.tempo.getValue()

        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="3.1">
              <part-list>
                <score-part id="P1"><part-name>Violin</part-name></score-part>
                <score-part id="P2"><part-name>Custom Synth</part-name></score-part>
              </part-list>
              <part id="P1">
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>1</fifths><mode>major</mode></key>
                    <time><beats>3</beats><beat-type>4</beat-type></time>
                    <clef><sign>G</sign><line>2</line></clef>
                  </attributes>
                  <direction placement="above">
                    <direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>132</per-minute></metronome></direction-type>
                    <sound tempo="132"/>
                  </direction>
                  <note>
                    <pitch><step>G</step><octave>4</octave></pitch>
                    <duration>4</duration><voice>1</voice><type>quarter</type>
                    <lyric><text>la</text></lyric>
                  </note>
                  <note>
                    <pitch><step>B</step><octave>4</octave></pitch>
                    <duration>8</duration><voice>1</voice><type>half</type>
                  </note>
                  <backup><duration>12</duration></backup>
                  <note>
                    <pitch><step>D</step><octave>4</octave></pitch>
                    <duration>12</duration><voice>2</voice><type>half</type><dot/>
                  </note>
                </measure>
              </part>
              <part id="P2">
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>1</fifths></key>
                    <time><beats>3</beats><beat-type>4</beat-type></time>
                    <clef><sign>G</sign><line>2</line><clef-octave-change>-1</clef-octave-change></clef>
                  </attributes>
                  <note>
                    <pitch><step>D</step><octave>3</octave></pitch>
                    <duration>12</duration><voice>1</voice><type>half</type><dot/>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()

        val imported = ScoreMusicXml.importFromString(xml)
        val cmd = ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = module.scoreData.instruments.toList(),
            afterInstruments = imported.instruments,
            beforeKeySignature = module.scoreData.keySignature,
            afterKeySignature = imported.keySignature,
            beforeTimeSignature = module.scoreData.timeSignature,
            afterTimeSignature = imported.timeSignature,
            beforeTempo = module.tempo.getValue(),
            afterTempo = imported.tempoBpm,
        )
        cmd.execute()

        assertEquals(KeySignature.G_MAJOR, module.scoreData.keySignature)
        assertEquals(TimeSignature.THREE_FOUR, module.scoreData.timeSignature)
        assertEquals(132.0, module.tempo.getValue())
        assertEquals(2, module.scoreData.instruments.size)
        assertEquals(Clef.TREBLE_8VB, module.scoreData.instruments[1].clef)
        // Secondary voice 2 ignored cleanly; primary voice 1 retained
        assertEquals(2, module.scoreData.instruments[0].measures[0].items.size)

        cmd.undo()
        assertEquals(originalKey, module.scoreData.keySignature)
        assertEquals(originalInstCount, module.scoreData.instruments.size)
        assertEquals(originalTempo, module.tempo.getValue())
    }

    @Test
    fun testPhase26ArticulationsAndTechniquesRoundTrip() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)

        val measures = listOf(
            ScoreMeasure(
                number = 1,
                items = listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER, isStaccato = true),
                    ScoreItem.Note(d4, NoteDuration.QUARTER, articulation = Articulation.STACCATISSIMO),
                    ScoreItem.Note(e4, NoteDuration.QUARTER, articulation = Articulation.TENUTO),
                    ScoreItem.Chord(listOf(c4, g4), NoteDuration.QUARTER, articulation = Articulation.MARCATO),
                ),
                techniques = mapOf(
                    0 to ScoreTechniqueMarking(ScoreTechniqueMark.PIZZICATO, DynamicPlacement.ABOVE),
                    2 to ScoreTechniqueMarking(ScoreTechniqueMark.ARCO, DynamicPlacement.ABOVE),
                ),
            ),
            ScoreMeasure(
                number = 2,
                items = listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER),
                    ScoreItem.Note(d4, NoteDuration.QUARTER),
                    ScoreItem.Note(e4, NoteDuration.QUARTER),
                    ScoreItem.Note(g4, NoteDuration.QUARTER),
                ),
                techniques = mapOf(
                    0 to ScoreTechniqueMarking(ScoreTechniqueMark.CON_SORDINO, DynamicPlacement.ABOVE),
                    1 to ScoreTechniqueMarking(ScoreTechniqueMark.SENZA_SORDINO, DynamicPlacement.ABOVE),
                    2 to ScoreTechniqueMarking(ScoreTechniqueMark.SON_FILE, DynamicPlacement.ABOVE),
                    3 to ScoreTechniqueMarking(ScoreTechniqueMark.ORDINARIO, DynamicPlacement.ABOVE),
                ),
            ),
        )

        val score = ScoreData().apply {
            replaceAllInstruments(
                listOf(
                    ScoreInstrumentPart(
                        id = "inst-1",
                        name = "Violin",
                        instrumentType = InstrumentType.BOWED_STRINGS,
                        staffCount = 1,
                        clef = Clef.TREBLE,
                        measures = measures,
                    )
                )
            )
        }

        val meta = ScoreMusicXml.defaultMetadata(title = "Articulations", composer = "Tester", date = "2026-11-24")
        val xml = ScoreMusicXml.exportToString(score, tempoBpm = 120.0, metadata = meta)

        assertContains(xml, "<staccato/>")
        assertContains(xml, "<staccatissimo/>")
        assertContains(xml, "<tenuto/>")
        assertContains(xml, "<strong-accent/>")
        assertContains(xml, """<words font-style="italic">pizz.</words>""")
        assertContains(xml, """<words font-style="italic">arco</words>""")
        assertContains(xml, """<words font-style="italic">con sord.</words>""")
        assertContains(xml, """<words font-style="italic">senza sord.</words>""")
        assertContains(xml, """<words font-style="italic">son filé</words>""")
        assertContains(xml, """<words font-style="italic">ordinario</words>""")

        val imported = ScoreMusicXml.importFromString(xml)
        val impMeasures = imported.instruments.single().measures
        assertEquals(2, impMeasures.size)
        assertMeasuresMusicallyEqual(measures[0], impMeasures[0], "m1")
        assertMeasuresMusicallyEqual(measures[1], impMeasures[1], "m2")

        // Verify re-export idempotence
        val reimportedScore = ScoreData().apply {
            replaceAllInstruments(imported.instruments)
        }
        val reExportedXml = ScoreMusicXml.exportToString(reimportedScore, tempoBpm = 120.0, metadata = meta)
        assertEquals(xml, reExportedXml)
    }

    @Test
    fun testImportExternalArticulationAndTechniqueVariants() {
        val externalXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="3.1">
              <part-list>
                <score-part id="P1"><part-name>Cello</part-name></score-part>
              </part-list>
              <part id="P1">
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <clef><sign>F</sign><line>4</line></clef>
                  </attributes>
                  <direction placement="above">
                    <direction-type><words>Allegro</words></direction-type>
                  </direction>
                  <direction placement="above">
                    <direction-type><words>pizz</words></direction-type>
                  </direction>
                  <note>
                    <pitch><step>C</step><octave>3</octave></pitch>
                    <duration>4</duration><voice>1</voice><type>quarter</type>
                    <notations><articulations><staccatissimo/></articulations></notations>
                  </note>
                  <direction placement="above">
                    <direction-type><words>con sordino</words></direction-type>
                  </direction>
                  <note>
                    <pitch><step>D</step><octave>3</octave></pitch>
                    <duration>4</duration><voice>1</voice><type>quarter</type>
                    <notations><articulations><spiccato/></articulations></notations>
                  </note>
                  <direction placement="above">
                    <direction-type><words>son file</words></direction-type>
                  </direction>
                  <note>
                    <pitch><step>E</step><octave>3</octave></pitch>
                    <duration>4</duration><voice>1</voice><type>quarter</type>
                  </note>
                  <direction placement="above">
                    <direction-type><words>ord.</words></direction-type>
                  </direction>
                  <note>
                    <pitch><step>F</step><octave>3</octave></pitch>
                    <duration>4</duration><voice>1</voice><type>quarter</type>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()

        val imported = ScoreMusicXml.importFromString(externalXml)
        val m1 = imported.instruments.single().measures.single()
        assertEquals(Articulation.STACCATISSIMO, m1.items[0].noteArticulation())
        assertEquals(Articulation.STACCATISSIMO, m1.items[1].noteArticulation())
        assertEquals(ScoreTechniqueMark.PIZZICATO, m1.techniques[0]?.mark)
        assertEquals(ScoreTechniqueMark.CON_SORDINO, m1.techniques[1]?.mark)
        assertEquals(ScoreTechniqueMark.SON_FILE, m1.techniques[2]?.mark)
        assertEquals(ScoreTechniqueMark.ORDINARIO, m1.techniques[3]?.mark)
    }

    private fun assertMeasuresMusicallyEqual(
        expected: ScoreMeasure,
        actual: ScoreMeasure,
        context: String,
    ) {
        assertEquals(expected.number, actual.number, "$context number")
        assertEquals(expected.keySignature, actual.keySignature, "$context keySignature")
        assertEquals(expected.timeSignature, actual.timeSignature, "$context timeSignature")
        assertEquals(expected.clefChanges, actual.clefChanges, "$context clefChanges")
        assertEquals(expected.dynamics, actual.dynamics, "$context dynamics")
        assertEquals(expected.techniques, actual.techniques, "$context techniques")
        assertEquals(expected.beamOverrides, actual.beamOverrides, "$context beamOverrides")
        assertEquals(expected.items.size, actual.items.size, "$context item count")

        val tupletIdMap = mutableMapOf<String, String>()
        for (idx in expected.items.indices) {
            val eItem = expected.items[idx]
            val aItem = actual.items[idx]
            val normalizedActual = when {
                eItem.tuplet == null && aItem.tuplet == null -> aItem
                eItem.tuplet != null && aItem.tuplet != null -> {
                    val mappedId = tupletIdMap.getOrPut(aItem.tuplet!!.groupId) { eItem.tuplet!!.groupId }
                    ScoreEditing.withTuplet(
                        aItem,
                        aItem.tuplet!!.copy(groupId = mappedId),
                    )
                }
                else -> aItem
            }
            assertEquals(eItem, normalizedActual, "$context item[$idx]")
        }
    }
}
