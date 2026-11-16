package com.syntona.util.musicxml

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicXmlWriterTest {

    @Test
    fun testWriteEscapesMetadataAndPreservesElementOrder() {
        val score = MusicXmlScore(
            metadata = MusicXmlMetadata(
                title = "Prelude & Fugue <1>",
                composer = "A. \"Composer\"",
                date = "2026-11-16",
                software = "Syntona",
            ),
            parts = listOf(
                MusicXmlPart(
                    id = "P1",
                    name = "Violin & Strings",
                    midiChannel = 1,
                    staves = 1,
                    measures = listOf(
                        MusicXmlMeasure(
                            number = 1,
                            attributes = MusicXmlMeasureAttributes(
                                divisions = 4,
                                key = MusicXmlKey(fifths = 2, mode = "major"),
                                time = MusicXmlTime(beats = 4, beatType = 4),
                                staves = 1,
                                clefs = listOf(MusicXmlClef(number = 1, sign = "G", line = 2)),
                            ),
                            elements = listOf(
                                MusicXmlMeasureElement.TempoDirection(bpm = 96),
                                MusicXmlMeasureElement.DynamicDirection(
                                    dynamic = MusicXmlDynamic.MF,
                                    placement = MusicXmlPlacement.BELOW,
                                    staff = 1,
                                ),
                                MusicXmlMeasureElement.Note(
                                    pitch = MusicXmlPitch(step = 'C', alter = 1, octave = 5),
                                    duration = 6,
                                    ties = listOf(MusicXmlTieType.STOP, MusicXmlTieType.START),
                                    voice = 1,
                                    type = MusicXmlNoteType.QUARTER,
                                    dots = 1,
                                    accidental = MusicXmlAccidental.SHARP,
                                    timeModification = MusicXmlTimeModification(3, 2),
                                    staff = 1,
                                    beams = mapOf(1 to MusicXmlBeamType.BEGIN, 2 to MusicXmlBeamType.FORWARD_HOOK),
                                    tiedNotations = listOf(MusicXmlTieType.STOP, MusicXmlTieType.START),
                                    tupletNotations = listOf(
                                        MusicXmlTupletNotation(MusicXmlTieType.START, number = 1, bracket = true)
                                    ),
                                    isStaccato = true,
                                ),
                            ),
                            barlineRight = MusicXmlBarStyle.LIGHT_HEAVY,
                        )
                    ),
                )
            ),
        )

        val xml = MusicXmlWriter.write(score)
        assertContains(xml, "<work-title>Prelude &amp; Fugue &lt;1&gt;</work-title>")
        assertContains(xml, """<creator type="composer">A. "Composer"</creator>""")
        assertContains(xml, "<encoding-date>2026-11-16</encoding-date>")
        assertContains(xml, "<part-name>Violin &amp; Strings</part-name>")
        assertContains(xml, """<direction placement="above">""")
        assertContains(xml, """<sound tempo="96"/>""")
        assertContains(xml, """<direction placement="below">""")
        assertContains(xml, "<mf/>")

        // Verify strict DTD/XSD ordering inside <note>
        val noteBlock = xml.substringAfter("<note>").substringBefore("</note>")
        val expectedOrder = listOf(
            "<pitch>",
            "<duration>6</duration>",
            """<tie type="stop"/>""",
            """<tie type="start"/>""",
            "<voice>1</voice>",
            "<type>quarter</type>",
            "<dot/>",
            "<accidental>sharp</accidental>",
            "<time-modification>",
            "<staff>1</staff>",
            """<beam number="1">begin</beam>""",
            """<beam number="2">forward hook</beam>""",
            "<notations>",
        )
        var cursor = -1
        for (tag in expectedOrder) {
            val next = noteBlock.indexOf(tag)
            assertTrue(next > cursor, "Expected $tag after index $cursor in:\n$noteBlock")
            cursor = next
        }
        assertContains(xml, """<barline location="right">""")
        assertContains(xml, "<bar-style>light-heavy</bar-style>")

        // Round-trip through MusicXmlReader recovers exact model structure
        val parsed = MusicXmlReader.read(xml)
        assertEquals(score, parsed)
    }

    @Test
    fun testReaderHandlesSingleQuotesCommentsGraceNotesAndEntities() {
        val xml = """
            <?xml version='1.0' encoding='UTF-8'?>
            <!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 3.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
            <score-partwise version='3.0'>
                <work><work-title>Café &amp; Tea &#x266B;</work-title></work>
                <part-list>
                    <score-part id='P1'><part-name>Flute</part-name></score-part>
                </part-list>
                <part id='P1'>
                    <!-- comment -->
                    <measure number='1'>
                        <attributes>
                            <divisions>4</divisions>
                            <key><fifths>-1</fifths><mode>major</mode></key>
                            <time><beats>3</beats><beat-type>4</beat-type></time>
                            <clef number='1'><sign>G</sign><line>2</line></clef>
                        </attributes>
                        <note><grace/><pitch><step>E</step><octave>5</octave></pitch><type>16th</type></note>
                        <note>
                            <pitch><step>F</step><alter>0.0</alter><octave>5</octave></pitch>
                            <duration>12</duration>
                            <voice>1</voice>
                            <type>half</type>
                            <dot/>
                            <notations><fermata/><slur type='start'/></notations>
                        </note>
                    </measure>
                </part>
            </score-partwise>
        """.trimIndent()
        val parsed = MusicXmlReader.read(xml)
        assertEquals("Café & Tea ♫", parsed.metadata.title)
        assertEquals("Anonymous", parsed.metadata.composer)
        assertEquals(1, parsed.parts.size)
        val notes = parsed.parts[0].measures[0].elements.filterIsInstance<MusicXmlMeasureElement.Note>()
        assertEquals(1, notes.size) // grace note skipped cleanly
        assertEquals(MusicXmlPitch('F', 0, 5), notes[0].pitch)
        assertEquals(12, notes[0].duration)
        assertEquals(1, notes[0].dots)
    }
}
