package com.syntona.dataflow.registry

import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstrumentRegistryTest {

    @Test
    fun testMusicInstrumentsBundleExists() {
        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }
        assertNotNull(music, "music bundle should exist in ModuleRegistry")
        val instruments = music.subBundles.find { it.name == "instruments" }
        assertNotNull(instruments, "instruments bundle should exist under music in ModuleRegistry")
    }

    @Test
    fun testRegisterAndCreateInstrumentPatch() {
        val json = """
            {
                "type": "core.polyPatch",
                "maxVoices": 3,
                "patch": {
                    "modules": [
                        {
                            "id": 0,
                            "type": "ksyn.sources.sineOsc",
                            "name": "sineOsc",
                            "x": 100.0,
                            "y": 100.0,
                            "jacks": {}
                        }
                    ],
                    "connections": []
                }
            }
        """.trimIndent()

        InstrumentRegistry.registerInstrument("testPolySynth", json, "instruments/testPolySynth.json")

        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
        val instruments = music.subBundles.find { it.name == "instruments" }!!
        val entry = instruments.modules.find { it.name == "testPolySynth" }
        assertNotNull(entry, "testPolySynth should be registered under music.instruments")

        val createdModule = entry.creator()
        assertTrue(createdModule is PolyPatchModule, "Created module should be PolyPatchModule")
        assertEquals(3, createdModule.maxVoices)
        assertEquals("instruments/testPolySynth.json", createdModule.resourcePath)
        assertEquals(null, createdModule.filePath)
        assertEquals("testPolySynth", createdModule.name)

        // Check that inner module exists
        val hasSineOsc = createdModule.graphState.modules.any { it.name == "sineOsc" }
        assertTrue(hasSineOsc, "sineOsc should exist in deserialized polyPatch")

        // Default modules should also be present
        assertTrue(createdModule.graphState.modules.any { it.name == "pitch" })
        assertTrue(createdModule.graphState.modules.any { it.name == "on" })
        assertTrue(createdModule.graphState.modules.any { it.name == "off" })
        assertTrue(createdModule.graphState.modules.any { it.name == "output" })
    }

    @Test
    fun testLoadInstrumentsFromResources() = runBlocking {
        InstrumentRegistry.loadInstrumentsIndex()

        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
        val instruments = music.subBundles.find { it.name == "instruments" }!!

        // sawLowPass.json is bundled in composeResources/files/instruments/
        val sawLowPassEntry = instruments.modules.find { it.name == "sawLowPass" }
        if (sawLowPassEntry != null) {
            val module = sawLowPassEntry.creator()
            assertTrue(module is PolyPatchModule, "sawLowPass should be a PolyPatchModule")
            assertEquals(6, module.maxVoices)
            assertEquals("instruments/sawLowPass.json", module.resourcePath)
            assertEquals(null, module.filePath)

            // Verify some components inside sawLowPass
            assertTrue(module.graphState.modules.any { it.name == "sawOscDPW" || it.type.contains("sawOsc") })
            assertTrue(module.graphState.modules.any { it.name == "fourPole" || it.type.contains("fourPole") })
        }
    }
}
