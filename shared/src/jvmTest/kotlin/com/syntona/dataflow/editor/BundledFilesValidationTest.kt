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
}
