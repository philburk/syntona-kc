package com.syntona.dataflow.modules

import com.syntona.SYNTONA_FILE_SUFFIX
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkModuleTest {

    @Test
    fun testLinkModuleTriggerLoad() {
        val link = LinkModule()
        link.filePath = "tutorials/01_basics/01_saw_env.json"
        link.buttonText = "Next Tutorial"

        var loadedPath: String? = null
        LinkModule.onTriggerLoad = { path ->
            loadedPath = path
        }

        link.triggerLoad()
        assertEquals("tutorials/01_basics/01_saw_env.json", loadedPath)

        // Test execution triggers load
        loadedPath = null
        link.execute(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals("tutorials/01_basics/01_saw_env.json", loadedPath)
    }

    @Test
    fun testFileIndexFilteringIncludesJsonAndSntn() {
        val mockFiles = listOf(
            "files_index.txt",
            "tutorials/01_basics/01_saw_env.json",
            "help/music/compose/scale.json",
            "examples/drum_rhythm.json",
            "custom/mypatch$SYNTONA_FILE_SUFFIX",
            "images/preview.png"
        )

        val filtered = mockFiles.filter { it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX) }.sorted()
        assertEquals(4, filtered.size)
        assertTrue(filtered.contains("tutorials/01_basics/01_saw_env.json"))
        assertTrue(filtered.contains("help/music/compose/scale.json"))
        assertTrue(filtered.contains("examples/drum_rhythm.json"))
        assertTrue(filtered.contains("custom/mypatch$SYNTONA_FILE_SUFFIX"))
    }
}
