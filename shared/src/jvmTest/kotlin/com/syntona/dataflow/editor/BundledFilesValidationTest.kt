package com.syntona.dataflow.editor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BundledFilesValidationTest {

    @Test
    fun testAllBundledJsonPatchesDeserializeCleanly() {
        val candidates = listOf(
            File("src/commonMain/composeResources/files"),
            File("shared/src/commonMain/composeResources/files"),
            File("../shared/src/commonMain/composeResources/files")
        )
        val filesDir = candidates.find { it.exists() && it.isDirectory }
        assertTrue(filesDir != null, "Could not find composeResources/files directory")

        val jsonFiles = filesDir.walkTopDown().filter { it.extension == "json" }.toList()
        assertTrue(jsonFiles.isNotEmpty(), "Found no JSON files to test")

        for (file in jsonFiles) {
            val json = file.readText()
            val graphState = GraphState()
            val problems = mutableListOf<String>()
            val modules = GraphSerializer.deserialize(graphState, json, problems = problems)
            assertTrue(
                problems.isEmpty(),
                "File ${file.name} had deserialization problems: ${problems.joinToString("; ")}"
            )
            assertTrue(modules.isNotEmpty(), "File ${file.name} loaded 0 modules")
        }
    }

    @Test
    fun testDrumRhythmLegacyModuleRenamingOnLoad() {
        val candidates = listOf(
            File("src/commonMain/composeResources/files/examples/drum_rhythm.json"),
            File("shared/src/commonMain/composeResources/files/examples/drum_rhythm.json"),
            File("../shared/src/commonMain/composeResources/files/examples/drum_rhythm.json")
        )
        val file = candidates.find { it.exists() && it.isFile }
        assertTrue(file != null, "Could not find drum_rhythm.json")

        val graphState = GraphState()
        val problems = mutableListOf<String>()
        val modules = GraphSerializer.deserialize(graphState, file.readText(), problems = problems)
        assertTrue(problems.isEmpty(), "Problems: ${problems.joinToString("; ")}")

        val onLoadMod = modules.find { it is com.syntona.dataflow.modules.OnLoadModule }
        assertTrue(onLoadMod != null, "Should have loaded an OnLoadModule")
        kotlin.test.assertEquals("onLoad", onLoadMod.name, "Legacy 'loadKick' should have been renamed to 'onLoad'")

        val ckDivMod = modules.find { it is com.syntona.dataflow.modules.ClockDividerModule } as? com.syntona.dataflow.modules.ClockDividerModule
        assertTrue(ckDivMod != null, "Should have loaded a ClockDividerModule")
        kotlin.test.assertEquals("clockDiv", ckDivMod.name, "Legacy 'ckDiv' should have been renamed to 'clockDiv'")
        kotlin.test.assertEquals(2.0, ckDivMod.divisor.getValue(), "Legacy jack 'N' value should be applied to 'divisor'")
    }
}
