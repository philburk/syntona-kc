package com.syntona.dataflow.editor

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every bundled example still loads.
 *
 * These files are edited by hand often enough - renamed, pruned, hand-merged - and a patch that
 * fails to load is only noticed when someone opens it from the Help menu. Deserializing them all
 * here catches a broken one at build time instead.
 */
class BundledExamplesTest {

    private val examplesDir = File("src/commonMain/composeResources/files/examples")

    @AfterTest
    fun clearProblems() {
        FileState.loadingProblemsReport = null
    }

    private fun exampleFiles(): List<File> =
        examplesDir.listFiles { f: File -> f.name.endsWith(".json") }?.sortedBy { it.name }
            ?: emptyList()

    @Test
    fun everyBundledExampleDeserializesWithoutProblems() {
        val files = exampleFiles()
        assertTrue(files.size > 10, "expected the examples folder, found ${files.size} files")

        val broken = mutableListOf<String>()
        for (file in files) {
            FileState.loadingProblemsReport = null
            val state = GraphState()
            val modules = GraphSerializer.deserialize(state, file.readText())
            val problems = FileState.loadingProblemsReport
            if (problems != null) broken.add("${file.name}: $problems")
            if (modules.isEmpty()) broken.add("${file.name}: loaded no modules")
            // Every unit generator these patches create lives in the one shared synth. Without
            // tearing them down, loading the whole examples folder leaves the engine unusable
            // for any test that runs afterwards.
            state.removeModulesDirectly(state.modules.toList())
        }
        assertTrue(broken.isEmpty(), "examples failed to load:\n${broken.joinToString("\n")}")
    }

    /**
     * polyPatch no longer creates a pitchBend input, and the bundled examples were pruned to
     * match. A bend reaches the voice through pitch, so the module was clutter.
     */
    @Test
    fun noExampleCarriesAPitchBendInput() {
        val withPitchBend = exampleFiles().filter { file ->
            val text = file.readText()
            // The aInput specifically. A knob named pitchBend driving packBend is the intended
            // way to bend, and poly_patch.json still has one.
            Regex("\"type\": \"ksyn\\.aInput\",\\s*\"name\": \"pitchBend\"").containsMatchIn(text)
        }
        assertEquals(emptyList(), withPitchBend.map { it.name })
    }
}
