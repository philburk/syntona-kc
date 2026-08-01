package com.syntona.dataflow.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpRegistryTest {

    @Test
    fun testHelpFilePathResolution() {
        HelpRegistry.setHelpFiles(
            setOf(
                "help/music/input/keyboard13.sntn",
                "help/music/compose/scale.sntn",
                "help/music/input/pianoRoll.sntn"
            )
        )

        assertTrue(HelpRegistry.hasHelpFile("keyboard13"))
        assertEquals("help/music/input/keyboard13.sntn", HelpRegistry.getHelpFilePath("keyboard13"))

        assertTrue(HelpRegistry.hasHelpFile("scale"))
        assertEquals("help/music/compose/scale.sntn", HelpRegistry.getHelpFilePath("scale"))

        assertTrue(HelpRegistry.hasHelpFile("pianoRoll"))
        assertEquals("help/music/input/pianoRoll.sntn", HelpRegistry.getHelpFilePath("pianoRoll"))

        assertFalse(HelpRegistry.hasHelpFile("nonExistentModule"))
        assertEquals(null, HelpRegistry.getHelpFilePath("nonExistentModule"))
    }
}
