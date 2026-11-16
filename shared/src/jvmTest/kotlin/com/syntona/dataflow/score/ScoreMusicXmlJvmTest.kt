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
}
