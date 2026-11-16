package com.syntona.util.musicxml

import kotlin.test.Test
import kotlin.test.assertContains
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
    }
}
