package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.instruments.BowedStringVoice
import com.softsynth.ksyn.instruments.PanningSaw
import com.softsynth.ksyn.unitgen.LineOut
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.VoiceInputJack
import com.syntona.dataflow.registry.InstrumentRegistry
import com.syntona.dataflow.registry.ModuleRegistry
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KSynInstrumentModuleTest {

    private val engine get() = SynthKSyn.synth as SynthesisEngine

    @BeforeTest
    fun setUp() {
        if (!engine.isRunning) engine.start()
    }

    @AfterTest
    fun tearDown() {
        if (engine.isRunning) engine.stop()
    }

    @Test
    fun testBowedStringRegisteredInMusicInstruments() = runBlocking {
        val creator = ModuleRegistry.findCreator("music.instruments.bowedString")
            ?: ModuleRegistry.findCreator("bowedString")
        assertNotNull(creator, "bowedString should be registered in ModuleRegistry")

        val module = assertIs<KSynInstrumentModule>(creator())
        try {
            assertEquals("bowedString", module.name)
            assertFalse(module.isAllowedInPolyPatch)
            assertEquals(listOf("Violin", "Viola", "Cello", "DoubleBass"), module.presetNames)
            assertEquals("Violin", module.presetName)
            assertEquals(0, module.presetIndex)

            // Also verify loadInstrumentsIndex() preserves bowedString in music.instruments
            InstrumentRegistry.loadInstrumentsIndex()
            val music = ModuleRegistry.root.subBundles.find { it.name == "music" }!!
            val instruments = music.subBundles.find { it.name == "instruments" }!!
            assertTrue(
                instruments.modules.any { it.name == "bowedString" },
                "bowedString should remain in music.instruments after loadInstrumentsIndex()"
            )
        } finally {
            module.teardown()
        }
    }

    @Test
    fun testBowedStringJacksAndPresets() {
        val module = KSynInstrumentModule("bowedString", BowedStringVoice.getVoiceDescription())
        try {
            val firstInput = module.inputs.first()
            assertIs<VoiceInputJack>(firstInput)
            assertEquals("input", firstInput.name)
            assertTrue(firstInput.isKickable)

            val inputNames = module.inputs.map { it.name }
            assertTrue("pitchOffset" in inputNames)
            assertTrue("amplitude" in inputNames)
            assertTrue("bowPosition" in inputNames)
            assertTrue("decay" in inputNames)
            assertTrue("brightness" in inputNames)
            assertTrue("bodySize" in inputNames)
            assertTrue("resonance" in inputNames)
            assertTrue("bodyMix" in inputNames)

            assertEquals(listOf("output"), module.outputs.map { it.name })
            assertIs<AudioOutputJack>(module.outputs.single())

            val bodySizeJack = assertIs<AudioInputJack>(module.inputs.first { it.name == "bodySize" })
            val decayJack = assertIs<AudioInputJack>(module.inputs.first { it.name == "decay" })

            // Default preset 0 = Violin (bodySize = 1.0, decay = 3.0)
            assertEquals(1.0, bodySizeJack.getValue(), 1e-4)
            assertEquals(3.0, decayJack.getValue(), 1e-4)

            // Switch to Cello (bodySize = 2.5, decay = 4.0)
            module.presetName = "Cello"
            assertEquals(2, module.presetIndex)
            assertEquals("Cello", module.presetName)
            assertEquals(2.5, bodySizeJack.getValue(), 1e-4)
            assertEquals(4.0, decayJack.getValue(), 1e-4)

            // Switch to DoubleBass (bodySize = 4.6, decay = 4.5)
            module.presetName = "DoubleBass"
            assertEquals(3, module.presetIndex)
            assertEquals(4.6, bodySizeJack.getValue(), 1e-4)
            assertEquals(4.5, decayJack.getValue(), 1e-4)
        } finally {
            module.teardown()
        }
    }

    @Test
    fun testStereoKSynInstrumentExposesOut0AndOut1() {
        val module = KSynInstrumentModule("panningSaw", PanningSaw.getVoiceDescription())
        try {
            assertEquals(listOf("out0", "out1"), module.outputs.map { it.name })
        } finally {
            module.teardown()
        }
    }

    @Test
    fun testPresetAndCustomJackValueSurviveSaveAndLoad() {
        val state = GraphState()
        val module = KSynInstrumentModule("bowedString", BowedStringVoice.getVoiceDescription())
        module.presetName = "Viola"
        val decayJack = module.inputs.first { it.name == "decay" }
        // Override Viola's default decay (3.2) to 5.5
        decayJack.setValue(5.5)
        state.addModule(module, Offset.Zero)

        val json = GraphSerializer.serialize(state, state.modules.toList())
        state.clearAllModules()

        val loadedState = GraphState()
        try {
            GraphSerializer.deserialize(loadedState, json)
            val loaded = assertIs<KSynInstrumentModule>(loadedState.modules.single())
            assertEquals("Viola", loaded.presetName)
            assertEquals(1, loaded.presetIndex)
            val loadedBodySize = loaded.inputs.first { it.name == "bodySize" }.getValue()
            val loadedDecay = loaded.inputs.first { it.name == "decay" }.getValue()
            assertEquals(1.25, loadedBodySize, 1e-4, "Preset bodySize for Viola should be loaded")
            assertEquals(5.5, loadedDecay, 1e-4, "Custom jack override on decay should be preserved after preset load")
        } finally {
            loadedState.clearAllModules()
        }
    }

    @Test
    fun testNoteOnProducesAudioOutput() = runBlocking {
        val rootGraph = GraphState()
        val bowedString = KSynInstrumentModule("bowedString", BowedStringVoice.getVoiceDescription())
        val lineOut = UnitGeneratorModule("lineOut", LineOut())
        try {
            rootGraph.addModule(bowedString, Offset.Zero)
            rootGraph.addModule(lineOut, Offset(200f, 0f))

            val outJack = bowedString.outputs.first { it.name == "output" } as AudioOutputJack
            val inJack = lineOut.inputs.first { it.name == "in0" } as AudioInputJack
            rootGraph.connect(outJack, inJack)

            engine.generateNextBuffer()
            assertEquals(0.0f, bowedString.instrument.getOutputPort().get(0), 1e-4f)

            bowedString.voiceInputJack.receive(
                Message.VoiceMessage(
                    ScoreTime.Zero,
                    Message.VoiceOpcode.NOTE_ON,
                    tag = 60,
                    value = 60.0,
                    extra = 0.8
                )
            )

            var maxSample = 0.0f
            repeat(20) {
                engine.generateNextBuffer()
                val sample = abs(bowedString.instrument.getOutputPort().get(0))
                if (sample > maxSample) maxSample = sample
            }
            assertTrue(maxSample > 1e-3f, "bowedString should generate audio after NOTE_ON (maxSample=$maxSample)")
        } finally {
            rootGraph.clearAllModules()
        }
    }
}
