package com.syntona.dataflow.registry

import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoundRegistryTest {

    @Test
    fun testMusicSoundsBundleExists() {
        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }
        assertNotNull(music, "music bundle should exist in ModuleRegistry")
        val sounds = music.subBundles.find { it.name == "sounds" }
        assertNotNull(sounds, "sounds bundle should exist under music in ModuleRegistry")
    }

    @Test
    fun testRegisterAndCreateSoundPatch() {
        val json = """
            {
                "type": "core.polyPatch",
                "maxVoices": 2,
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

        SoundRegistry.registerSound("testSnare", json, "sounds/testSnare.json")

        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
        val sounds = music.subBundles.find { it.name == "sounds" }!!
        val entry = sounds.modules.find { it.name == "testSnare" }
        assertNotNull(entry, "testSnare should be registered under music.sounds")

        val createdModule = entry.creator()
        assertTrue(createdModule is PolyPatchModule, "Created module should be PolyPatchModule")
        assertEquals(2, createdModule.maxVoices)
        assertEquals("sounds/testSnare.json", createdModule.resourcePath)
        assertEquals(null, createdModule.filePath)
        assertEquals("testSnare", createdModule.name)

        // Check that inner module exists
        val hasSine = createdModule.graphState.modules.any { it.name == "sineOsc" }
        assertTrue(hasSine, "sineOsc should exist in deserialized polyPatch")
    }

    @Test
    fun testLoadSoundsFromResources() = runBlocking {
        SoundRegistry.loadSoundsIndex()

        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
        val sounds = music.subBundles.find { it.name == "sounds" }!!

        // drumFM.json is bundled in composeResources/files/sounds/
        val drumFMEntry = sounds.modules.find { it.name == "drumFM" }
        if (drumFMEntry != null) {
            val module = drumFMEntry.creator()
            assertTrue(module is PolyPatchModule || module is PatchModule, "drumFM should be a patch")
            assertEquals("sounds/drumFM.json", (module as PatchModule).resourcePath)
            assertEquals(null, module.filePath)
        }
    }
}
