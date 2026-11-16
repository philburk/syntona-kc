package com.syntona.dataflow.score

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScoreMusicXmlJvmTest {

    @Test
    fun testExportedDefaultScoreParsesWithStrictXmlDomParser() {
        val module = ScoreModule()
        val xml = ScoreMusicXml.exportModuleToString(module)

        val factory = DocumentBuilderFactory.newInstance().apply {
            // Avoid external DTD network fetches during offline/sandboxed unit tests.
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isValidating = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = doc.documentElement
        assertEquals("score-partwise", root.tagName)
        assertEquals("3.1", root.getAttribute("version"))

        val workTitles = root.getElementsByTagName("work-title")
        assertEquals(1, workTitles.length)
        assertEquals("Untitled", workTitles.item(0).textContent)

        val creators = root.getElementsByTagName("creator")
        assertEquals(1, creators.length)
        assertEquals("Anonymous", creators.item(0).textContent)

        val encodingDates = root.getElementsByTagName("encoding-date")
        assertEquals(1, encodingDates.length)
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(encodingDates.item(0).textContent))

        val parts = root.getElementsByTagName("part")
        assertEquals(module.scoreData.instruments.size, parts.length)
        for (i in 0 until parts.length) {
            val partEl = parts.item(i) as Element
            val measures = partEl.getElementsByTagName("measure")
            assertEquals(module.scoreData.maxMeasureCount(), measures.length)
        }
    }

    @Test
    fun testReferenceNotionFixtureLoadsFromTestResources() {
        val stream = this::class.java.getResourceAsStream("/musicxml/ScoreTest_01.xml")
        assertNotNull(stream, "Expected /musicxml/ScoreTest_01.xml in jvmTest resources")
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isValidating = false
        }
        val doc = factory.newDocumentBuilder().parse(stream)
        assertEquals("score-partwise", doc.documentElement.tagName)
        assertEquals("ScoreTest_01", doc.getElementsByTagName("work-title").item(0).textContent)
    }

    @Test
    fun testImportNotionScoreTest01AndRoundTripExportImport() {
        val rawXml = this::class.java.getResourceAsStream("/musicxml/ScoreTest_01.xml")
            ?.bufferedReader()
            ?.use { it.readText() }
        assertNotNull(rawXml, "Expected /musicxml/ScoreTest_01.xml in jvmTest resources")

        val imported = ScoreMusicXml.importFromString(rawXml)
        assertEquals("ScoreTest_01", imported.metadata.title)
        assertEquals("Phil Burk", imported.metadata.composer)
        assertEquals(KeySignature.D_MAJOR, imported.keySignature)
        assertEquals(TimeSignature.COMMON_TIME, imported.timeSignature)
        assertEquals(90.0, imported.tempoBpm)
        assertEquals(2, imported.instruments.size)

        val violin = imported.instruments[0]
        val piano = imported.instruments[1]
        assertEquals("Violin", violin.name)
        assertEquals(1, violin.staffCount)
        assertEquals(Clef.TREBLE, violin.clef)
        assertEquals("Piano", piano.name)
        assertEquals(2, piano.staffCount)
        assertEquals(Clef.TREBLE, piano.clef)
        assertEquals(Clef.BASS, piano.lowerClef)
        assertEquals(33, violin.measures.size)
        assertEquals(33, piano.measures.size)
        assertEquals(33, piano.lowerMeasures.size)

        // Check M1 Violin: 2 quarters + 4 staccato eighths
        assertEquals(6, violin.measures[0].items.size)
        assertTrue(violin.measures[0].items.drop(2).all { it.noteStaccato() })

        // Check M2 Violin: 5:4 quintuplet + 2 quarters; Piano lower has cross-bar tie at end
        assertEquals(7, violin.measures[1].items.size)
        assertTrue(violin.measures[1].items.take(5).all {
            it.tuplet?.actualCount == 5 && it.tuplet?.normalCount == 4
        })
        assertTrue((piano.lowerMeasures[1].items.last() as ScoreItem.Note).isTiedToNext)

        // Check M4: time signature changes to 5/4 on ALL staves (Violin, Piano upper & Piano lower), Piano upper has ff dynamic
        assertEquals(TimeSignature.FIVE_FOUR, violin.measures[3].timeSignature)
        assertEquals(TimeSignature.FIVE_FOUR, piano.measures[3].timeSignature)
        assertEquals(TimeSignature.FIVE_FOUR, piano.lowerMeasures[3].timeSignature)
        assertEquals(TimeSignature.COMMON_TIME, violin.measures[4].timeSignature)
        assertEquals(TimeSignature.COMMON_TIME, piano.measures[4].timeSignature)
        assertEquals(TimeSignature.COMMON_TIME, piano.lowerMeasures[4].timeSignature)
        assertEquals(DynamicMark.FF, piano.measures[3].dynamics[0]?.mark)

        // Check M5/M6 Violin: dotted 8th + 16th, mid-bar Bass clef at item 3, Treble return in M6, beam split at item 4
        assertEquals(Clef.BASS, violin.measures[4].clefChanges[3])
        assertEquals(Clef.TREBLE, violin.measures[5].clefChanges[0])
        assertEquals(false, violin.measures[5].beamOverrides[4])

        // Check M7: Key C Major on ALL staves, 5:4 tuplet with rest on item 1, Piano upper pp dynamic, Piano lower whole rest
        assertEquals(KeySignature.C_MAJOR, violin.measures[6].keySignature)
        assertEquals(KeySignature.C_MAJOR, piano.measures[6].keySignature)
        assertEquals(KeySignature.C_MAJOR, piano.lowerMeasures[6].keySignature)
        assertTrue(violin.measures[6].items[1] is ScoreItem.Rest)
        assertEquals(DynamicMark.PP, piano.measures[6].dynamics[0]?.mark)
        assertTrue(piano.lowerMeasures[6].items.single() is ScoreItem.Rest)

        // Measures 9..33 in Notion (print-object='no') import as empty measures
        for (mIdx in 8 until 33) {
            assertTrue(violin.measures[mIdx].items.isEmpty(), "Expected Violin bar ${mIdx + 1} empty")
            assertTrue(piano.measures[mIdx].items.isEmpty(), "Expected Piano upper bar ${mIdx + 1} empty")
        }

        // Round-trip imported Notion score: Export -> Import and confirm 100% consistency
        val scoreData = ScoreData().apply {
            keySignature = imported.keySignature
            timeSignature = imported.timeSignature
            replaceAllInstruments(imported.instruments)
        }
        val exported = ScoreMusicXml.exportToString(
            scoreData = scoreData,
            tempoBpm = imported.tempoBpm ?: 90.0,
            metadata = imported.metadata.copy(date = "2026-11-16"),
        )
        val roundTripped = ScoreMusicXml.importFromString(exported)
        assertEquals(imported.keySignature, roundTripped.keySignature)
        assertEquals(imported.timeSignature, roundTripped.timeSignature)
        assertEquals(imported.tempoBpm, roundTripped.tempoBpm)
        assertEquals(imported.instruments.size, roundTripped.instruments.size)
        for (iIdx in imported.instruments.indices) {
            val before = imported.instruments[iIdx]
            val after = roundTripped.instruments[iIdx]
            assertEquals(before.name, after.name)
            assertEquals(before.staffCount, after.staffCount)
            for (sIdx in 0 until before.staffCount) {
                val mBefore = before.measuresForStaff(sIdx)
                val mAfter = after.measuresForStaff(sIdx)
                assertEquals(mBefore.size, mAfter.size)
                for (mIdx in mBefore.indices) {
                    assertEquals(mBefore[mIdx].items.size, mAfter[mIdx].items.size, "inst=$iIdx s=$sIdx m=$mIdx")
                    assertEquals(mBefore[mIdx].dynamics, mAfter[mIdx].dynamics, "dyn inst=$iIdx s=$sIdx m=$mIdx")
                    assertEquals(mBefore[mIdx].clefChanges, mAfter[mIdx].clefChanges, "clef inst=$iIdx s=$sIdx m=$mIdx")
                }
            }
        }
    }
}
