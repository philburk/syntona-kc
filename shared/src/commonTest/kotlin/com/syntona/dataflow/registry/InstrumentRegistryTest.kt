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
        assertTrue(instruments.modules.isNotEmpty())
    }

    @Test
    fun testTwoOpFMLoadingAndValuePropagation() = runBlocking {
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        if (!engine.isRunning) engine.start()
        try {
            InstrumentRegistry.loadInstrumentsIndex()

            val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
            val instruments = music.subBundles.find { it.name == "instruments" }!!
            val twoOpEntry = instruments.modules.find { it.name == "twoOpFM" }
            assertNotNull(twoOpEntry, "twoOpFM should be registered under music.instruments")

            val twoOpFM = twoOpEntry.creator() as PolyPatchModule
            assertTrue(twoOpFM.isInitialized)

            // Check template mcRatio knob value
            val mcRatioKnob = twoOpFM.graphState.modules.find { it.name == "mcRatio" } as? com.syntona.dataflow.modules.KnobModule
            assertNotNull(mcRatioKnob)
            assertEquals(2.0077, mcRatioKnob.value, 1e-3)

            // Check voice 0 mcRatio and a* multiplier inputb
            val voice0 = twoOpFM.voices[0]
            val voiceMcRatio = voice0.templateToVoiceMap[mcRatioKnob] as? com.syntona.dataflow.modules.KnobModule
            assertNotNull(voiceMcRatio)
            assertEquals(2.0077, voiceMcRatio.value, 1e-3)

            // Check connection from mcRatio knob to target module in graph
            val mcRatioConn = twoOpFM.graphState.connections.find { it.output.module === mcRatioKnob }
            assertNotNull(mcRatioConn, "Connection from mcRatio should exist in twoOpFM")
            val targetMod = mcRatioConn.input.module
            val voiceTargetMod = voice0.templateToVoiceMap[targetMod]
            assertNotNull(voiceTargetMod, "Voice target module should exist in voice0")
            val voiceTargetInJack = voiceTargetMod.inputs.find { it.name == mcRatioConn.input.name }
            assertNotNull(voiceTargetInJack)
            assertEquals(2.0077, voiceTargetInJack.getValue(), 1e-3)

            // Send a NoteOn message and verify voice 0 handles it
            twoOpFM.voiceInputJack.receive(
                com.syntona.dataflow.model.Message.VoiceMessage(
                    com.syntona.core.model.ScoreTime.Zero,
                    com.syntona.dataflow.model.Message.VoiceOpcode.NOTE_ON,
                    tag = 10,
                    value = 60.0,
                    extra = 0.8
                )
            )
            engine.generateNextBuffer()
        } finally {
            if (engine.isRunning) engine.stop()
        }
    }
}
