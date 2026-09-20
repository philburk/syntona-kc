package com.syntona.dataflow.registry

import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.UnitGeneratorModule
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstrumentRegistryTest {

    @AfterTest
    fun tearDown() {
        InstrumentRegistry.unregisterInstrument("testPolySynth")
        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }
        val instruments = music?.subBundles?.find { it.name == "instruments" }
        instruments?.modules?.clear()
        InstrumentRegistry.instrumentJsonCache.clear()
    }

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

        try {
            InstrumentRegistry.registerInstrument(
                "testPolySynth",
                json,
                "instruments/testPolySynth.json",
                description = "test polyphonic synthesizer instrument"
            )

            val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
            val instruments = music.subBundles.find { it.name == "instruments" }!!
            val entry = instruments.modules.find { it.name == "testPolySynth" }
            assertNotNull(entry, "testPolySynth should be registered under music.instruments")
            assertEquals("test polyphonic synthesizer instrument", entry.description)

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
        } finally {
            InstrumentRegistry.unregisterInstrument("testPolySynth")
        }
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

    @Test
    fun testMinimalSawStartsOff() = runBlocking {
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        if (!engine.isRunning) engine.start()
        try {
            InstrumentRegistry.loadInstrumentsIndex()
            val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
            val instruments = music.subBundles.find { it.name == "instruments" }!!
            val entry = instruments.modules.find { it.name == "minimalSaw" }
            assertNotNull(entry, "minimalSaw should be registered")

            val minimalSaw = entry.creator() as PolyPatchModule
            assertTrue(minimalSaw.isInitialized)

            // Check the envDAHDSR in voice 0
            val voice0 = minimalSaw.voices[0]
            val dahdsrTemplate = minimalSaw.graphState.modules.find { it.name == "envDAHDSR" } as? UnitGeneratorModule
            assertNotNull(dahdsrTemplate, "envDAHDSR should be in template")
            val dahdsrVoice = voice0.templateToVoiceMap[dahdsrTemplate] as? UnitGeneratorModule
            assertNotNull(dahdsrVoice, "envDAHDSR should be in voice0")
            val dahdsrUnit = dahdsrVoice.unit as com.softsynth.ksyn.unitgen.EnvelopeDAHDSR

            engine.generateNextBuffer()

            val rootGraph = com.syntona.dataflow.editor.GraphState()
            rootGraph.addModule(minimalSaw, androidx.compose.ui.geometry.Offset.Zero)

            val lineOut = UnitGeneratorModule("lineOut", com.softsynth.ksyn.unitgen.LineOut())
            rootGraph.addModule(lineOut, androidx.compose.ui.geometry.Offset(200f, 0f))

            val sawOutJack = minimalSaw.outputs.find { it.name == "output" } as AudioOutputJack
            val lineInJack = lineOut.inputs.find { it.name == "in0" } as AudioInputJack
            rootGraph.connect(sawOutJack, lineInJack)

            engine.generateNextBuffer()
            val outVal = minimalSaw.polyphonicInstrument!!.getOutputPort().get(0)

            assertEquals(0.0f, dahdsrUnit.output.get(0), 1e-4f, "DAHDSR envelope output should be 0 before any noteOn")
            assertEquals(0.0f, outVal, 1e-4f, "minimalSaw output should be 0 before any noteOn")

            // Now play a note and verify it produces sound
            minimalSaw.voiceInputJack.receive(
                com.syntona.dataflow.model.Message.VoiceMessage(
                    com.syntona.core.model.ScoreTime.Zero,
                    com.syntona.dataflow.model.Message.VoiceOpcode.NOTE_ON,
                    tag = 10,
                    value = 60.0,
                    extra = 0.8
                )
            )
            engine.generateNextBuffer()
            val noteOnOut = minimalSaw.polyphonicInstrument!!.getOutputPort().get(0)
            assertTrue(kotlin.math.abs(noteOnOut) > 0.01f || kotlin.math.abs(dahdsrUnit.output.get(0)) > 0.01f, "minimalSaw should make sound after noteOn")
        } finally {
            if (engine.isRunning) engine.stop()
        }
    }

    @Test
    fun testSawTremoloEnvelopeSharedWithVoices() = runBlocking {
        InstrumentRegistry.loadInstrumentsIndex()
        val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
        val instruments = music.subBundles.find { it.name == "instruments" }!!
        val entry = instruments.modules.find { it.name == "sawTremolo" }
        assertNotNull(entry, "sawTremolo should be registered")

        val sawTremolo = entry.creator() as PolyPatchModule
        assertTrue(sawTremolo.isInitialized)

        val templateEnvSeg = sawTremolo.graphState.modules.find { it is com.syntona.dataflow.modules.EnvSegModule } as? com.syntona.dataflow.modules.EnvSegModule
        assertNotNull(templateEnvSeg, "template should have EnvSegModule")
        assertEquals(2, templateEnvSeg.envelope.sustainBegin)
        assertEquals(5, templateEnvSeg.envelope.sustainEnd)

        for (voice in sawTremolo.voices) {
            val voiceEnvSeg = voice.templateToVoiceMap[templateEnvSeg] as? com.syntona.dataflow.modules.EnvSegModule
            assertNotNull(voiceEnvSeg, "voice should have EnvSegModule")
            assertTrue(templateEnvSeg.envelope === voiceEnvSeg.envelope, "voice EnvSegModule must share envelope by reference")
            assertEquals(2, voiceEnvSeg.envelope.sustainBegin)
            assertEquals(5, voiceEnvSeg.envelope.sustainEnd)
        }
    }
}
