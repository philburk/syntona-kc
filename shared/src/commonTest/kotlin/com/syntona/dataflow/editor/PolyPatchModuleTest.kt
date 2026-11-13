package com.syntona.dataflow.editor

import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.PassThrough
import com.syntona.dataflow.modules.*
import com.syntona.dataflow.model.*
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import kotlin.math.pow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel

class PolyPatchModuleTest {

    @BeforeTest
    fun setUp() {
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        if (!engine.isRunning) {
            engine.start()
        }
    }

    @AfterTest
    fun tearDown() {
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        if (engine.isRunning) {
            engine.stop()
        }
    }

    @Test
    fun testPolyPatchInitializationAndVoiceSync() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(4)
        assertEquals(4, polyPatch.maxVoices)
        assertNotNull(polyPatch.polyphonicInstrument)

        // Add a unit generator module
        val sineOscModule = UnitGeneratorModule("sineOsc", SineOscillator())
        polyPatch.graphState.addModule(sineOscModule, androidx.compose.ui.geometry.Offset.Zero)

        // Add aOutput module
        val audioOutputModule = AudioOutputModule()
        polyPatch.graphState.addModule(audioOutputModule, androidx.compose.ui.geometry.Offset.Zero)

        // Connect them
        val outJack = sineOscModule.outputs.find { it.name == "out" }
        val inJack = audioOutputModule.inputs.find { it.name == "in" }
        assertNotNull(outJack)
        assertNotNull(inJack)
        polyPatch.graphState.connect(outJack, inJack)

        // Remove module to verify no throw/errors
        polyPatch.graphState.removeModulesDirectly(listOf(sineOscModule))
    }

    @Test
    fun testPolyPatchSerialization() {
        val rootGraph = GraphState()
        val polyPatch = PolyPatchModule()
        rootGraph.addModule(polyPatch, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.initializeVoices(3)

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"maxVoices\": 3"))

        val newRootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newRootGraph, json)
        assertEquals(1, deserialized.size)

        val newPolyPatch = deserialized[0] as PolyPatchModule
        assertEquals(3, newPolyPatch.maxVoices)
        assertTrue(newPolyPatch.isInitialized)
    }

    @Test
    fun testPolyPatchDeserializationWithoutMaxVoices() {
        val json = """
            {"modules":[{"id":1,"type":"polyPatch","name":"poly1","x":0.0,"y":0.0}],"connections":[]}
        """.trimIndent()
        val rootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(rootGraph, json)
        assertEquals(1, deserialized.size)
        val polyPatch = deserialized[0] as PolyPatchModule
        assertTrue(!polyPatch.isInitialized)
    }

    @Test
    fun testJackValuePropagationToVoices() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val sineOscModule = UnitGeneratorModule("sineOsc", SineOscillator())
        polyPatch.graphState.addModule(sineOscModule, androidx.compose.ui.geometry.Offset.Zero)

        val freqJack = sineOscModule.inputs.find { it.name == "frequency" }
        assertNotNull(freqJack)

        // Set value on the editor module's jack
        freqJack.setValue(880.0)
    }

    @Test
    fun testDefaultOutputModule() {
        val polyPatch = PolyPatchModule()
        assertEquals(7, polyPatch.graphState.modules.size)
        val defaultOutput = polyPatch.graphState.modules.find { it is AudioOutputModule }
        assertNotNull(defaultOutput)
        assertEquals("output", defaultOutput.name)

        // Parent PolyPatchModule should NOT have any audio output jacks from defaultOutput
        assertTrue(polyPatch.outputs.isEmpty())

        polyPatch.initializeVoices(2)
        // Only the mixed output jack should exist on parent
        assertEquals(1, polyPatch.outputs.size)
        assertEquals("output", polyPatch.outputs[0].name)
    }

    @Test
    fun testCannotDeleteBuiltInModules() {
        val polyPatch = PolyPatchModule()
        assertEquals(7, polyPatch.graphState.modules.size)

        // All 7 built-in modules must be intrinsic and non-deletable
        for (m in polyPatch.graphState.modules) {
            assertTrue(m.isIntrinsic, "Module ${m.name} should be marked isIntrinsic")
            assertFalse(polyPatch.canDeleteChildModule(m), "Module ${m.name} should not be deletable by parent")
            assertFalse(polyPatch.graphState.canDeleteModule(m), "GraphState should report module ${m.name} not deletable")
        }

        // Test deleting single built-in module: "output"
        val outputMod = polyPatch.graphState.modules.find { it.name == "output" }!!
        polyPatch.graphState.selectModule(outputMod)
        polyPatch.graphState.deleteSelectedModules()
        assertTrue(polyPatch.graphState.modules.contains(outputMod), "Output module must not be deleted")
        assertEquals(7, polyPatch.graphState.modules.size)

        // Test deleting single built-in module: "on"
        val onMod = polyPatch.graphState.modules.find { it.name == "on" }!!
        polyPatch.graphState.clearSelection()
        polyPatch.graphState.selectModule(onMod)
        polyPatch.graphState.deleteSelectedModules()
        assertTrue(polyPatch.graphState.modules.contains(onMod), "On module must not be deleted")
        assertEquals(7, polyPatch.graphState.modules.size)

        // Test selecting all built-in modules and trying to delete them
        polyPatch.graphState.clearSelection()
        for (m in polyPatch.graphState.modules) {
            polyPatch.graphState.selectModule(m)
        }
        polyPatch.graphState.deleteSelectedModules()
        assertEquals(7, polyPatch.graphState.modules.size, "No built-in modules should be deleted")
    }

    @Test
    fun testDeleteUserAddedModuleInPolyPatch() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val sineOsc = UnitGeneratorModule("sineOsc", SineOscillator())
        polyPatch.graphState.addModule(sineOsc, androidx.compose.ui.geometry.Offset.Zero)
        assertEquals(8, polyPatch.graphState.modules.size)
        assertTrue(polyPatch.canDeleteChildModule(sineOsc))
        assertTrue(polyPatch.graphState.canDeleteModule(sineOsc))

        polyPatch.graphState.selectModule(sineOsc)
        polyPatch.graphState.deleteSelectedModules()
        assertEquals(7, polyPatch.graphState.modules.size)
        assertFalse(polyPatch.graphState.modules.contains(sineOsc))
    }

    @Test
    fun testDeleteMixedSelectionInPolyPatch() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val sineOsc = UnitGeneratorModule("sineOsc", SineOscillator())
        polyPatch.graphState.addModule(sineOsc, androidx.compose.ui.geometry.Offset.Zero)
        val outputMod = polyPatch.graphState.modules.find { it.name == "output" }!!

        // Connect oscillator to output
        val oscOut = sineOsc.outputs.find { it.name == "out" }!!
        val outIn = outputMod.inputs.find { it.name == "in" }!!
        polyPatch.graphState.connect(oscOut, outIn)
        assertEquals(1, polyPatch.graphState.connections.size)

        // Select both sineOsc and outputMod
        polyPatch.graphState.selectModule(sineOsc)
        polyPatch.graphState.selectModule(outputMod)

        polyPatch.graphState.deleteSelectedModules()

        // Oscillator should be deleted and connection removed, but outputMod should remain
        assertEquals(7, polyPatch.graphState.modules.size)
        assertFalse(polyPatch.graphState.modules.contains(sineOsc))
        assertTrue(polyPatch.graphState.modules.contains(outputMod))
        assertEquals(0, polyPatch.graphState.connections.size)
    }

    @Test
    fun testAuxiliaryOutputModule() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        // Adding an auxiliary AudioOutputModule
        val auxOutput = AudioOutputModule()
        polyPatch.graphState.addModule(auxOutput, androidx.compose.ui.geometry.Offset.Zero)

        // The auxiliary module should be named "aOut1" by default, and a parent jack should be created
        assertEquals("aOut1", auxOutput.name)
        assertEquals(2, polyPatch.outputs.size)
        assertEquals("output", polyPatch.outputs[0].name)
        assertEquals("aOut1", polyPatch.outputs[1].name)

        // Renaming the auxiliary output should update the parent jack name
        auxOutput.name = "submix"
        assertEquals("submix", auxOutput.name)
        assertEquals("submix", polyPatch.outputs[1].name)

        // Removing the auxiliary module should remove the parent jack
        polyPatch.graphState.removeModulesDirectly(listOf(auxOutput))
        assertEquals(1, polyPatch.outputs.size)
        assertEquals("output", polyPatch.outputs[0].name)
    }

    @Test
    fun testPitchBendVoiceSumming() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        val voice0 = polyPatch.voices[0]
        val voice1 = polyPatch.voices[1]
        val pitchModule = polyPatch.graphState.modules.find { it.name == "pitch" }!!
        val pitchIn0 = voice0.templateToVoiceMap[pitchModule] as InputModule
        val pitchIn1 = voice1.templateToVoiceMap[pitchModule] as InputModule

        // Note on for voice 0 (tag = 10, pitch = 60.0)
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 0.8
            )
        )
        // Note on for voice 1 (tag = 20, pitch = 64.0)
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 20,
                value = 64.0,
                extra = 0.8
            )
        )
        engine.generateNextBuffer()

        assertEquals(60.0, voice0.currentPitch, 1e-4)
        assertEquals(0.0, voice0.pitchBend, 1e-4)
        assertEquals(60.0, pitchIn0.output.getValue(), 1e-4)

        assertEquals(64.0, voice1.currentPitch, 1e-4)
        assertEquals(0.0, voice1.pitchBend, 1e-4)
        assertEquals(64.0, pitchIn1.output.getValue(), 1e-4)

        // 1. Send per-note pitch bend of +2.0 semitones ONLY to tag = 10
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.PITCH_BEND,
                tag = 10,
                value = 2.0,
                extra = 0.0
            )
        )
        engine.generateNextBuffer()

        // Voice 0 (tag 10) should have bend = 2.0, pitch = 62.0
        assertEquals(60.0, voice0.currentPitch, 1e-4)
        assertEquals(2.0, voice0.pitchBend, 1e-4)
        assertEquals(62.0, pitchIn0.output.getValue(), 1e-4)

        // Voice 1 (tag 20) should remain bend = 0.0, pitch = 64.0
        assertEquals(64.0, voice1.currentPitch, 1e-4)
        assertEquals(0.0, voice1.pitchBend, 1e-4)
        assertEquals(64.0, pitchIn1.output.getValue(), 1e-4)

        // 2. Send broadcast pitch bend of -1.5 semitones to tag = -1 (all voices)
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.PITCH_BEND,
                tag = -1,
                value = -1.5,
                extra = 0.0
            )
        )
        engine.generateNextBuffer()

        // Both voices should now have bend = -1.5
        assertEquals(60.0, voice0.currentPitch, 1e-4)
        assertEquals(-1.5, voice0.pitchBend, 1e-4)
        assertEquals(58.5, pitchIn0.output.getValue(), 1e-4)

        assertEquals(64.0, voice1.currentPitch, 1e-4)
        assertEquals(-1.5, voice1.pitchBend, 1e-4)
        assertEquals(62.5, pitchIn1.output.getValue(), 1e-4)
    }

    @Test
    fun testOnOffSignalAndEnvelopeExplicitTriggering() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(1)
        polyPatch.polyphonicInstrument!!.start()

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        val voice0 = polyPatch.voices[0]
        val onModule = polyPatch.graphState.modules.find { it.name == "on" }!!
        val offModule = polyPatch.graphState.modules.find { it.name == "off" }!!
        val outputModule = polyPatch.graphState.modules.find { it.name == "output" }!!

        // Add envDAHDSR module
        val dahdsrModule = UnitGeneratorModule("envDAHDSR", com.softsynth.ksyn.unitgen.EnvelopeDAHDSR())
        polyPatch.graphState.addModule(dahdsrModule, androidx.compose.ui.geometry.Offset.Zero)
        val dahdsrVoiceMod = voice0.templateToVoiceMap[dahdsrModule] as UnitGeneratorModule
        val dahdsrUnit0 = dahdsrVoiceMod.unit as com.softsynth.ksyn.unitgen.EnvelopeDAHDSR

        // Connect DAHDSR output to main output so it is pulled by the synthesis engine
        val dahdsrOutJack = dahdsrModule.outputs.find { it.name == "out" }!!
        val patchOutInJack = outputModule.inputs.find { it.name == "in" }!!
        polyPatch.graphState.connect(dahdsrOutJack, patchOutInJack)
        engine.generateNextBuffer()

        // 1. Without connecting on/off, noteOn should NOT trigger DAHDSR
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        engine.generateNextBuffer()
        assertEquals(0.0f, dahdsrUnit0.output.get(0), 1e-4f)

        // 2. Connect on.out and off.out to DAHDSR.in
        val onOutJack = onModule.outputs.find { it.name == "out" }!!
        val offOutJack = offModule.outputs.find { it.name == "out" }!!
        val dahdsrInJack = dahdsrModule.inputs.find { it.name == "in" }!!
        polyPatch.graphState.connect(onOutJack, dahdsrInJack)
        polyPatch.graphState.connect(offOutJack, dahdsrInJack)
        engine.generateNextBuffer()

        // 3. Now noteOn SHOULD trigger DAHDSR via on = 1.0
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        // Run a buffer to generate envelope output
        engine.generateNextBuffer()
        polyPatch.polyphonicInstrument!!.getOutputPort().unitGenerator?.pullData(engine.frameCount)
        assertTrue(dahdsrUnit0.output.getValues().any { it > 0.0f }, "DAHDSR envelope output should be > 0 after noteOn with on connected")

        // 4. Note off should release DAHDSR via off = 0.0
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_OFF,
                tag = 10,
                value = 60.0,
                extra = 0.0
            )
        )
        engine.generateNextBuffer()
    }

    /** Like playGrainFarm.sntn: delete a polyPatch, undo, then play a note. */
    @Test
    fun testPolyPatchStillSoundsAfterUndoDelete() = kotlinx.coroutines.runBlocking {
        val root = GraphState()
        val polyPatch = PolyPatchModule()
        root.addModule(polyPatch, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.initializeVoices(1)
        val sine = UnitGeneratorModule("sineOsc", SineOscillator())
        polyPatch.graphState.addModule(sine, androidx.compose.ui.geometry.Offset.Zero)
        val outputModule = polyPatch.graphState.modules.first { it.name == "output" }
        polyPatch.graphState.connect(sine.outputs.first { it.name == "out" }, outputModule.inputs.first { it.name == "in" })

        val delete = DeleteModulesCommand(root, listOf(polyPatch), mapOf(polyPatch to androidx.compose.ui.geometry.Offset.Zero), emptyList())
        delete.execute()
        assertFalse(root.modules.contains(polyPatch))
        delete.undo()
        assertTrue(root.modules.contains(polyPatch))

        val instrument = assertNotNull(polyPatch.polyphonicInstrument, "undo must restore a working instrument")
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(com.syntona.core.model.ScoreTime.Zero, Message.VoiceOpcode.NOTE_ON, tag = 1, value = 69.0, extra = 1.0)
        )
        engine.generateNextBuffer()
        instrument.getOutputPort().unitGenerator?.pullData(engine.frameCount)
        assertTrue(instrument.getOutputPort().getValues().any { it != 0.0f }, "polyPatch should sound after undoing its deletion")

        // Once the delete can no longer be undone, the polyPatch is torn down.
        delete.execute()
        delete.discard()
        assertEquals(null, polyPatch.polyphonicInstrument)
    }

    @Test
    fun testOnOffDirectToEnvelopeOnOffJacks() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(1)
        polyPatch.polyphonicInstrument!!.start()

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        val voice0 = polyPatch.voices[0]
        val onModule = polyPatch.graphState.modules.find { it.name == "on" }!!
        val offModule = polyPatch.graphState.modules.find { it.name == "off" }!!
        val outputModule = polyPatch.graphState.modules.find { it.name == "output" }!!

        // Add envDAHDSR module
        val dahdsrModule = UnitGeneratorModule("envDAHDSR", com.softsynth.ksyn.unitgen.EnvelopeDAHDSR())
        polyPatch.graphState.addModule(dahdsrModule, androidx.compose.ui.geometry.Offset.Zero)
        val dahdsrVoiceMod = voice0.templateToVoiceMap[dahdsrModule] as UnitGeneratorModule
        val dahdsrUnit0 = dahdsrVoiceMod.unit as com.softsynth.ksyn.unitgen.EnvelopeDAHDSR

        // Connect DAHDSR output to main output
        val dahdsrOutJack = dahdsrModule.outputs.find { it.name == "out" }!!
        val patchOutInJack = outputModule.inputs.find { it.name == "in" }!!
        polyPatch.graphState.connect(dahdsrOutJack, patchOutInJack)
        engine.generateNextBuffer()

        // Connect on.out -> envDAHDSR.on and off.out -> envDAHDSR.off directly
        val onOutJack = onModule.outputs.find { it.name == "out" }!!
        val offOutJack = offModule.outputs.find { it.name == "out" }!!
        val dahdsrOnJack = dahdsrModule.inputs.find { it.name == "on" }!!
        val dahdsrOffJack = dahdsrModule.inputs.find { it.name == "off" }!!
        polyPatch.graphState.connect(onOutJack, dahdsrOnJack)
        polyPatch.graphState.connect(offOutJack, dahdsrOffJack)
        engine.generateNextBuffer()

        // NoteOn should trigger DAHDSR via on jack
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        engine.generateNextBuffer()
        polyPatch.polyphonicInstrument!!.getOutputPort().unitGenerator?.pullData(engine.frameCount)
        assertTrue(dahdsrUnit0.output.getValues().any { it > 0.0f }, "DAHDSR envelope output should be > 0 after noteOn with on jack connected")

        // NoteOff should release DAHDSR via off jack
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_OFF,
                tag = 10,
                value = 60.0,
                extra = 0.0
            )
        )
        engine.generateNextBuffer()
    }

    @Test
    fun testEnvelopeStartsOffWhenOffConnectedBeforeOn() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(1)
        polyPatch.polyphonicInstrument!!.start()

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        val voice0 = polyPatch.voices[0]
        val onModule = polyPatch.graphState.modules.find { it.name == "on" }!!
        val offModule = polyPatch.graphState.modules.find { it.name == "off" }!!
        val outputModule = polyPatch.graphState.modules.find { it.name == "output" }!!

        // Add envDAHDSR module
        val dahdsrModule = UnitGeneratorModule("envDAHDSR", com.softsynth.ksyn.unitgen.EnvelopeDAHDSR())
        polyPatch.graphState.addModule(dahdsrModule, androidx.compose.ui.geometry.Offset.Zero)
        val dahdsrVoiceMod = voice0.templateToVoiceMap[dahdsrModule] as UnitGeneratorModule
        val dahdsrUnit0 = dahdsrVoiceMod.unit as com.softsynth.ksyn.unitgen.EnvelopeDAHDSR

        // Connect DAHDSR output to main output
        val dahdsrOutJack = dahdsrModule.outputs.find { it.name == "out" }!!
        val patchOutInJack = outputModule.inputs.find { it.name == "in" }!!
        polyPatch.graphState.connect(dahdsrOutJack, patchOutInJack)

        // Connect off FIRST, then on SECOND (matching minimalSaw connection order)
        val onOutJack = onModule.outputs.find { it.name == "out" }!!
        val offOutJack = offModule.outputs.find { it.name == "out" }!!
        val dahdsrOnJack = dahdsrModule.inputs.find { it.name == "on" }!!
        val dahdsrOffJack = dahdsrModule.inputs.find { it.name == "off" }!!
        polyPatch.graphState.connect(offOutJack, dahdsrOffJack)
        polyPatch.graphState.connect(onOutJack, dahdsrOnJack)
        engine.generateNextBuffer()

        // Prior to the fix, connecting on second triggered the envelope ON. Verify it starts OFF.
        polyPatch.polyphonicInstrument!!.getOutputPort().unitGenerator?.pullData(engine.frameCount)
        assertEquals(0.0f, dahdsrUnit0.output.get(0), 1e-4f, "DAHDSR envelope output should be 0 before any noteOn")
    }

    @Test
    fun testEnsureDefaultModulesExistOnDeserialization() {
        val json = """
            {"modules":[{"id":0,"type":"polyPatch","name":"poly1","x":0.0,"y":0.0,"maxVoices":2,"patch":{"modules":[{"id":0,"type":"aOutput","name":"output","x":400.0,"y":200.0}],"connections":[]}}],"connections":[]}
        """.trimIndent()
        val rootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(rootGraph, json)
        val polyPatch = deserialized[0] as PolyPatchModule

        assertEquals(7, polyPatch.graphState.modules.size)
        assertTrue(polyPatch.graphState.modules.any { it.name == "on" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "off" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "pitch" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "amplitude" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "pressure" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "duration" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "output" })

        // Check intrinsic flags and non-deletability
        for (m in polyPatch.graphState.modules) {
            assertTrue(m.isIntrinsic, "Deserialized module ${m.name} should be isIntrinsic")
            assertFalse(polyPatch.graphState.canDeleteModule(m), "Deserialized module ${m.name} should not be deletable")
        }
        val outputMod = polyPatch.graphState.modules.find { it.name == "output" }!!
        polyPatch.graphState.selectModule(outputMod)
        polyPatch.graphState.deleteSelectedModules()
        assertEquals(7, polyPatch.graphState.modules.size, "Deserialized output module must not be deleted")
    }

    @Test
    fun testKnobRangePreservationOnDeserialization() {
        val rootGraph = GraphState()
        val knob = KnobModule()
        rootGraph.addModule(knob, androidx.compose.ui.geometry.Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)

        // Connect them first (triggering the interactive inheritance of the oscillator's 40-8000 range)
        val outJack = knob.outputs.find { it.name == "out" }!!
        val inJack = osc.inputs.find { it.name == "frequency" }!!
        rootGraph.connect(outJack, inJack)

        // Then override the range on the knob (user edits range after connecting)
        knob.valueIn.min = -2.0
        knob.valueIn.max = 2.0

        // Save the graph
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)

        // Deserialise the graph
        val newRootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newRootGraph, json)
        val newKnob = deserialized.find { it is KnobModule } as KnobModule

        // Range must be preserved as -2.0 to 2.0 (NOT inherited from the connected oscillator's frequency range)
        assertEquals(-2.0, newKnob.valueIn.min)
        assertEquals(2.0, newKnob.valueIn.max)
    }

    @Test
    fun testCutTriOscFromPolyPatchAndPasteToParent() = kotlinx.coroutines.runBlocking {
        val rootGraph = GraphState()
        val polyPatch = PolyPatchModule()
        rootGraph.addModule(polyPatch, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.initializeVoices(1)

        // Add a triOsc inside the polyPatch
        val triOsc = UnitGeneratorModule("triOsc", com.softsynth.ksyn.unitgen.TriangleOscillator())
        polyPatch.graphState.addModule(triOsc, androidx.compose.ui.geometry.Offset.Zero)

        // Connect the triOsc output to output in the polyPatch
        val outJack = triOsc.outputs.find { it.name == "out" }!!
        val outputModule = polyPatch.graphState.modules.find { it.name == "output" }!!
        val inJack = outputModule.inputs.find { it.name == "in" }!!
        polyPatch.graphState.connect(outJack, inJack)

        // Run synthesis engine to start pulling data
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        // Cut the triOsc (remove from polyPatch)
        polyPatch.graphState.removeModulesDirectly(listOf(triOsc))

        // Trigger buffer generation immediately after removal (checks for race condition / NPE)
        engine.generateNextBuffer()

        // Paste it to the parent patch (instantiate a new triOsc module in rootGraph)
        val pastedTriOsc = UnitGeneratorModule("triOsc", com.softsynth.ksyn.unitgen.TriangleOscillator())
        rootGraph.addModule(pastedTriOsc, androidx.compose.ui.geometry.Offset.Zero)

        // Connect it in the parent patch
        val parentOutput = AudioOutputModule()
        rootGraph.addModule(parentOutput, androidx.compose.ui.geometry.Offset.Zero)
        val pastedOutJack = pastedTriOsc.outputs.find { it.name == "out" }!!
        val parentInJack = parentOutput.inputs.find { it.name == "in" }!!
        rootGraph.connect(pastedOutJack, parentInJack)

        // Run buffer generation to ensure no crashes or NPEs
        engine.generateNextBuffer()
    }

    @Test
    fun testWindowStateSaveAndRestore() {
        val rootGraph = GraphState()
        val childPatch = PatchModule()
        childPatch.name = "subPatch"
        childPatch.isWindowOpen = true
        rootGraph.addModule(childPatch, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"windowOpen\": true"))

        // Deserialize
        val newRootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newRootGraph, json)
        val restoredChild = deserialized.find { it.name == "subPatch" } as PatchModule
        assertTrue(restoredChild.isWindowOpen)

        // Now test window closed state
        restoredChild.isWindowOpen = false
        val jsonClosed = GraphSerializer.serialize(newRootGraph, newRootGraph.modules)
        assertTrue(jsonClosed.contains("\"windowOpen\": false"))

        val finalRootGraph = GraphState()
        val finalDeserialized = GraphSerializer.deserialize(finalRootGraph, jsonClosed)
        val finalChild = finalDeserialized.find { it.name == "subPatch" } as PatchModule
        kotlin.test.assertFalse(finalChild.isWindowOpen)
    }

    @Test
    fun testSequenceModuleInputsAndSerialization() {
        val rootGraph = GraphState()
        val seq = SequenceModule(8)
        rootGraph.addModule(seq, androidx.compose.ui.geometry.Offset.Zero)

        // Verify initial state
        assertEquals("1.0", seq.values[3])

        // Programmatically send 42.0 to step 3 input
        seq.stepInputs[3].receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 42.0))
        assertEquals("42.0", seq.values[3])

        // Step the sequence by kicking 4 times to fire steps 0, 1, 2, 3 (post-increment starting at 0)
        seq.kick.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))
        seq.kick.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))
        seq.kick.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))
        seq.kick.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))

        assertEquals(4, seq.count)

        // Output of step 3 should have received the value 42.0
        assertEquals(42.0, seq.stepOutputs[3].getValue())

        // Serialize
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"in3\":") || json.contains("stepValues"))
        assertTrue(json.contains("42"))

        // Deserialize and check restored value
        val newRootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newRootGraph, json)
        val restoredSeq = deserialized.find { it is SequenceModule } as SequenceModule
        assertEquals("42.0", restoredSeq.values[3])
    }

    @Test
    fun testModuloSelectModuleBehaviorAndSerialization() {
        val rootGraph = GraphState()
        val mod = ModuloSelectModule(8)
        rootGraph.addModule(mod, androidx.compose.ui.geometry.Offset.Zero)

        // Verify only 1 input ("in") and it is an IntegerInputJack
        assertEquals(1, mod.inputs.size)
        assertEquals("in", mod.inputs[0].name)
        assertIs<com.syntona.dataflow.model.IntegerInputJack>(mod.input)
        assertTrue(mod.input.isInteger)

        // Initial highlighted jack is output 0
        assertTrue(mod.stepOutputs[0].isHighlighted)

        // Send value 10.0 to 'in'
        mod.input.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 10.0))

        // Modulo 8 of 10 is 2
        assertEquals(2.0, mod.stepOutputs[2].getValue())
        assertTrue(mod.stepOutputs[2].isHighlighted)
        kotlin.test.assertFalse(mod.stepOutputs[0].isHighlighted)

        // Serialize
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)

        // Deserialize and check restored type
        val newRootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newRootGraph, json)
        val restoredMod = deserialized.find { it is ModuloSelectModule } as ModuloSelectModule
        assertEquals(8, restoredMod.numOutputs)
    }

    @Test
    fun testShowVoiceToShowManyMapping() {
        val rootGraph = GraphState()
        val json = """
            {"modules":[{"id":0,"type":"showVoice","name":"monitor","x":10.0,"y":20.0,"jacks":{}}],"connections":[]}
        """.trimIndent()

        val deserialized = GraphSerializer.deserialize(rootGraph, json)
        assertEquals(1, deserialized.size)
        val module = deserialized[0]
        assertTrue(module is ShowManyModule)
        assertEquals("monitor", module.name)
    }

    @Test
    fun testCustomAudioInputConnectionToVoices() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        // 1. Add custom AudioInputModule inside polyPatch
        val vibratoIn = AudioInputModule()
        vibratoIn.name = "vibrato"
        polyPatch.graphState.addModule(vibratoIn, androidx.compose.ui.geometry.Offset.Zero)

        // Run engine buffer to process connection commands queued on synth thread
        engine.generateNextBuffer()

        // Verify the connection: template passThrough's output should connect to each voice's "vibrato" input
        val voice0 = polyPatch.voices[0]
        val voice1 = polyPatch.voices[1]
        val voiceVibrato0 = voice0.templateToVoiceMap[vibratoIn] as AudioInputModule
        val voiceVibrato1 = voice1.templateToVoiceMap[vibratoIn] as AudioInputModule

        val outputPart = vibratoIn.passThrough.output.parts[0]
        assertEquals(2, outputPart.connectionCount)
        assertTrue(outputPart.getConnection(0) === voiceVibrato0.passThrough.input.parts[0] || outputPart.getConnection(1) === voiceVibrato0.passThrough.input.parts[0])
        assertTrue(outputPart.getConnection(0) === voiceVibrato1.passThrough.input.parts[0] || outputPart.getConnection(1) === voiceVibrato1.passThrough.input.parts[0])

        // 2. Rename custom AudioInputModule
        vibratoIn.name = "lfoMod"
        engine.generateNextBuffer()
        assertEquals("lfoMod", voiceVibrato0.name)
        assertEquals("lfoMod", voiceVibrato1.name)

        // 3. Remove custom AudioInputModule
        polyPatch.graphState.removeModulesDirectly(listOf(vibratoIn))
        engine.generateNextBuffer()

        assertEquals(0, outputPart.connectionCount)
    }

    @Test
    fun testStartDraggingFromInputChoosesLastConnection() {
        val graphState = GraphState()
        val mod1 = CounterModule()
        val mod2 = CounterModule()
        val mod3 = CounterModule()
        graphState.addModule(mod1, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(mod2, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(mod3, androidx.compose.ui.geometry.Offset.Zero)

        // Connect mod1.output to mod3.reset
        graphState.connect(mod1.output, mod3.reset)
        // Connect mod2.output to mod3.reset
        graphState.connect(mod2.output, mod3.reset)

        assertEquals(2, graphState.connections.size)
        assertEquals(mod1.output, graphState.connections[0].output)
        assertEquals(mod2.output, graphState.connections[1].output)

        // Start dragging from input
        graphState.startDraggingFromInput(mod3.reset)

        // The last connection (from mod2.output) should have been deleted
        assertEquals(1, graphState.connections.size)
        assertEquals(mod1.output, graphState.connections[0].output)
        assertEquals(mod2.output, graphState.draggingWireByOutput)
    }

    @Test
    fun testStartDraggingFromOutputWithShiftDisconnectsLastConnection() {
        val graphState = GraphState()
        val mod1 = CounterModule()
        val mod2 = CounterModule()
        val mod3 = CounterModule()

        graphState.addModule(mod1, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(mod2, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(mod3, androidx.compose.ui.geometry.Offset.Zero)

        graphState.connect(mod1.output, mod2.reset)
        graphState.connect(mod1.output, mod3.reset)

        assertEquals(2, graphState.connections.size)
        assertEquals(mod2.reset, graphState.connections[0].input)
        assertEquals(mod3.reset, graphState.connections[1].input)

        graphState.startDraggingFromOutput(mod1.output, isShiftPressed = true)

        assertEquals(1, graphState.connections.size)
        assertEquals(mod2.reset, graphState.connections[0].input)
        assertEquals(mod1.output, graphState.draggingWireByOutput)
    }

    @Test
    fun testInputJackDropTargetBounds() {
        val graphState = GraphState()
        val mod1 = CounterModule()
        val mod2 = CounterModule()
        graphState.addModule(mod1, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(mod2, androidx.compose.ui.geometry.Offset.Zero)

        // Set the jack offset and bounds size (e.g. 100 x 30) for mod2's reset jack
        val offset = androidx.compose.ui.geometry.Offset(150f, 100f)
        val size = androidx.compose.ui.geometry.Size(100f, 30f)
        graphState.updateJackOffset(mod2.reset, offset, size)

        // Start dragging from mod1's output jack
        graphState.startDraggingFromOutput(mod1.output)

        // Drop on pointer position within the input jack's bounding box
        // This is 50 pixels away on the x-axis, but inside the 100x30 bounding box
        graphState.onPointerMove(androidx.compose.ui.geometry.Offset(200f, 105f))
        graphState.onPointerUp(null)

        // Verify that the connection is successfully made
        assertEquals(1, graphState.connections.size)
        assertEquals(mod2.reset, graphState.connections[0].input)
        assertEquals(mod1.output, graphState.connections[0].output)
    }

    @Test
    fun testMixerModules() {
        val rootGraph = PatchModule()
        val mixer8 = Mixer8x2Module()
        val mixer4 = Mixer4x2Module()

        // Verify ports
        assertNotNull(mixer8.getGainJack(0))
        assertNotNull(mixer8.getPanJack(0))
        assertNotNull(mixer8.getInputJack(0))
        assertNotNull(mixer8.inputs.find { it.name == "amplitude" })
        assertEquals(2, mixer8.outputs.size)
        assertNotNull(mixer8.getOutputJack(0))
        assertNotNull(mixer8.getOutputJack(1))
        assertEquals("out0", mixer8.getOutputJack(0)?.name)
        assertEquals("out1", mixer8.getOutputJack(1)?.name)

        assertNotNull(mixer4.getGainJack(0))
        assertNotNull(mixer4.getPanJack(0))
        assertNotNull(mixer4.getInputJack(0))
        assertNotNull(mixer4.inputs.find { it.name == "amplitude" })
        assertEquals(2, mixer4.outputs.size)
        assertNotNull(mixer4.getOutputJack(0))
        assertNotNull(mixer4.getOutputJack(1))
        assertEquals("out0", mixer4.getOutputJack(0)?.name)
        assertEquals("out1", mixer4.getOutputJack(1)?.name)

        // Verify volume/pan helpers
        val gainJack = mixer8.getGainJack(0)!!
        val panJack = mixer8.getPanJack(0)!!

        // Set raw volume knob position to 0.5f and verify gain calculations
        val gainVal = 50.0.pow(0.5) - 1.0
        val scaledGainVal = gainVal / 49.0
        gainJack.setValue(scaledGainVal)
        assertEquals(0.5f, mixer8.getVolumeKnobValue(0), 1e-4f)

        // Set pan knob to -0.5f
        panJack.setValue(-0.5)
        assertEquals(-0.5f, mixer8.getPanKnobValue(0), 1e-4f)

        // Verify serialization
        rootGraph.graphState.addModule(mixer8, androidx.compose.ui.geometry.Offset.Zero)
        val json = GraphSerializer.serialize(rootGraph.graphState, listOf(mixer8))

        // Remove and deserialize
        rootGraph.graphState.removeModulesDirectly(listOf(mixer8))
        val deserialized = GraphSerializer.deserialize(rootGraph.graphState, json)
        assertEquals(1, deserialized.size)
        val newMixer = deserialized[0] as Mixer8x2Module
        assertEquals(scaledGainVal, newMixer.getGainJack(0)!!.getValue(), 1e-4)
        assertEquals(-0.5, newMixer.getPanJack(0)!!.getValue(), 1e-4)
    }

    @Test
    fun testMixerMuteAndSolo() {
        val mixer = com.syntona.dataflow.modules.Mixer4x2Module()
        val gain0 = (50.0.pow(0.8) - 1.0) / 49.0
        val gain1 = (50.0.pow(0.6) - 1.0) / 49.0
        val gain2 = (50.0.pow(0.4) - 1.0) / 49.0
        mixer.getGainJack(0)!!.setValue(gain0)
        mixer.getGainJack(1)!!.setValue(gain1)
        mixer.getGainJack(2)!!.setValue(gain2)

        // Initially all channels are audible
        for (i in 0 until 4) {
            kotlin.test.assertTrue(mixer.isChannelAudible(i))
        }
        assertEquals(gain0, mixer.getGainJack(0)!!.getValue(), 1e-4)
        assertEquals(gain1, mixer.getGainJack(1)!!.getValue(), 1e-4)

        // Mute channel 0: effective gain is 0, but knob position and channelGain remain intact
        mixer.setChannelMuted(0, true)
        kotlin.test.assertTrue(mixer.isChannelMuted(0))
        kotlin.test.assertFalse(mixer.isChannelAudible(0))
        assertEquals(0.0, mixer.getGainJack(0)!!.getValue(), 1e-4)
        assertEquals(gain0, mixer.getChannelGain(0), 1e-4)
        assertEquals(0.8f, mixer.getVolumeKnobValue(0), 1e-4f)
        assertEquals(gain1, mixer.getGainJack(1)!!.getValue(), 1e-4)

        // Unmute channel 0: effective gain is restored
        mixer.setChannelMuted(0, false)
        kotlin.test.assertTrue(mixer.isChannelAudible(0))
        assertEquals(gain0, mixer.getGainJack(0)!!.getValue(), 1e-4)

        // Solo channel 1: only channel 1 is audible; channels 0, 2, 3 are silenced
        mixer.setChannelSoloed(1, true)
        kotlin.test.assertFalse(mixer.isChannelAudible(0))
        kotlin.test.assertTrue(mixer.isChannelAudible(1))
        kotlin.test.assertFalse(mixer.isChannelAudible(2))
        kotlin.test.assertFalse(mixer.isChannelAudible(3))
        assertEquals(0.0, mixer.getGainJack(0)!!.getValue(), 1e-4)
        assertEquals(gain1, mixer.getGainJack(1)!!.getValue(), 1e-4)
        assertEquals(0.0, mixer.getGainJack(2)!!.getValue(), 1e-4)

        // Round-trip save and load while channel 1 is soloed and channel 2 is muted
        mixer.setChannelMuted(2, true)
        val state = GraphState()
        state.addModule(mixer, androidx.compose.ui.geometry.Offset.Zero)
        val json = GraphSerializer.serialize(state, listOf(mixer))
        val afterState = GraphState()
        val loaded = GraphSerializer.deserialize(afterState, json)
        val loadedMixer = loaded[0] as com.syntona.dataflow.modules.Mixer4x2Module

        kotlin.test.assertFalse(loadedMixer.isChannelMuted(0))
        kotlin.test.assertTrue(loadedMixer.isChannelSoloed(1))
        kotlin.test.assertTrue(loadedMixer.isChannelMuted(2))
        assertEquals(gain0, loadedMixer.getChannelGain(0), 1e-4)
        assertEquals(gain1, loadedMixer.getChannelGain(1), 1e-4)
        assertEquals(gain2, loadedMixer.getChannelGain(2), 1e-4)
        assertEquals(0.0, loadedMixer.getGainJack(0)!!.getValue(), 1e-4)
        assertEquals(gain1, loadedMixer.getGainJack(1)!!.getValue(), 1e-4)

        // Unsolo channel 1 on loaded mixer: channel 0 becomes audible with its saved gain0, channel 2 stays muted
        loadedMixer.setChannelSoloed(1, false)
        assertEquals(gain0, loadedMixer.getGainJack(0)!!.getValue(), 1e-4)
        assertEquals(0.0, loadedMixer.getGainJack(2)!!.getValue(), 1e-4)
    }

    @Test
    fun testSegEnvAndMonoRdrInPolyPatch() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        // 1. Add envSeg inside polyPatch
        val envSeg = EnvSegModule()
        polyPatch.graphState.addModule(envSeg, androidx.compose.ui.geometry.Offset.Zero)

        // 2. Wire on -> envSeg.on, off -> envSeg.off
        val onModule = polyPatch.graphState.modules.find { it.name == "on" }!!
        val offModule = polyPatch.graphState.modules.find { it.name == "off" }!!
        val onOut = onModule.outputs.find { it.name == "out" }!!
        val offOut = offModule.outputs.find { it.name == "out" }!!

        polyPatch.graphState.connect(onOut, envSeg.onIn)
        polyPatch.graphState.connect(offOut, envSeg.offIn)

        // Connect envSeg output to main output so it is pulled during buffer generation
        val outputModule = polyPatch.graphState.modules.find { it.name == "output" }!!
        val patchOutIn = outputModule.inputs.find { it.name == "in" }!!
        polyPatch.graphState.connect(envSeg.output, patchOutIn)

        // 3. Send NoteOn for tag = 10
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )

        // Process buffers
        engine.generateNextBuffer()
        polyPatch.polyphonicInstrument!!.getOutputPort().unitGenerator?.pullData(engine.frameCount)

        // Verify that Voice 0's envSeg receives the queued envelope
        val voice0 = polyPatch.voices[0]
        val voiceEnvSeg = voice0.templateToVoiceMap[envSeg] as EnvSegModule
        val rdrUnit = voiceEnvSeg.unit

        assertTrue(rdrUnit.output.getValues().any { it > 0.0f }, "envSeg should produce envelope values > 0 after NOTE_ON triggered envSeg")

        // 4. Send NoteOff for tag = 10
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_OFF,
                tag = 10,
                value = 60.0,
                extra = 0.0
            )
        )
    }

    @Test
    fun testEquationModuleInPolyPatch() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        // Add f(x) module inside polyPatch
        val fx = EquationModule("f(x)")
        fx.compile("x + 12")
        polyPatch.graphState.addModule(fx, androidx.compose.ui.geometry.Offset.Zero)

        // Connect pitch.output -> f(x).x
        val pitchModule = polyPatch.graphState.modules.find { it.name == "pitch" }!!
        val pitchOut = pitchModule.outputs.find { it.name == "out" }!!
        val fxIn = fx.inputs.find { it.name == "x" }!!
        polyPatch.graphState.connect(pitchOut, fxIn)

        // Send NOTE_ON with pitch 60.0
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        engine.generateNextBuffer()

        val voice0 = polyPatch.voices[0]
        val voiceFx = voice0.templateToVoiceMap[fx] as EquationModule
        assertEquals(72.0, voiceFx.output.getValue(), 1e-4)
    }

    @Test
    fun testDisallowedModuleDropInPolyPatch() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        // 1. Nested polyPatch should be disallowed
        val nestedPoly = PolyPatchModule("nested")
        polyPatch.graphState.addModule(nestedPoly, androidx.compose.ui.geometry.Offset.Zero)
        assertTrue(polyPatch.graphState.modules.none { it === nestedPoly })

        // 2. MidiInModule should be disallowed
        val midiIn = MidiInModule()
        polyPatch.graphState.addModule(midiIn, androidx.compose.ui.geometry.Offset.Zero)
        assertTrue(polyPatch.graphState.modules.none { it === midiIn })

        // 3. ScopeModule should be disallowed
        val scope = ScopeModule()
        polyPatch.graphState.addModule(scope, androidx.compose.ui.geometry.Offset.Zero)
        assertTrue(polyPatch.graphState.modules.none { it === scope })
    }

    @Test
    fun testSharedDataReference() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val envSeg = EnvSegModule()
        val sample = FloatSampleModule()

        polyPatch.graphState.addModule(envSeg, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(sample, androidx.compose.ui.geometry.Offset.Zero)

        val voice0 = polyPatch.voices[0]
        val voice1 = polyPatch.voices[1]

        val voiceEnvSeg0 = voice0.templateToVoiceMap[envSeg] as EnvSegModule
        val voiceEnvSeg1 = voice1.templateToVoiceMap[envSeg] as EnvSegModule

        val voiceSample0 = voice0.templateToVoiceMap[sample] as FloatSampleModule
        val voiceSample1 = voice1.templateToVoiceMap[sample] as FloatSampleModule

        // Verify exact object reference identity (shared memory)
        assertTrue(envSeg.envelope === voiceEnvSeg0.envelope)
        assertTrue(envSeg.envelope === voiceEnvSeg1.envelope)

        assertTrue(sample.sample === voiceSample0.sample)
        assertTrue(sample.sample === voiceSample1.sample)
    }

    @Test
    fun testIsValueAddedPropagationToVoices() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        // 1. Add ap2f and triOsc inside polyPatch
        val ap2f = UnitGeneratorModule("ap2f", com.softsynth.ksyn.unitgen.PitchToFrequency())
        val triOsc = UnitGeneratorModule("triOsc", com.softsynth.ksyn.unitgen.TriangleOscillator())
        polyPatch.graphState.addModule(ap2f, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(triOsc, androidx.compose.ui.geometry.Offset.Zero)

        val ap2fInJack = ap2f.inputs.find { it.name == "in" } as AudioInputJack
        val triOutJack = triOsc.outputs.find { it.name == "out" }!!
        val pitchMod = polyPatch.graphState.modules.find { it.name == "pitch" }!!
        val pitchOutJack = pitchMod.outputs.find { it.name == "out" }!!

        // Wire pitch -> ap2f.in and triOsc -> ap2f.in
        polyPatch.graphState.connect(pitchOutJack, ap2fInJack)
        polyPatch.graphState.connect(triOutJack, ap2fInJack)

        val voice0 = polyPatch.voices[0]
        val voice1 = polyPatch.voices[1]
        val voiceAP2F0 = voice0.templateToVoiceMap[ap2f] as UnitGeneratorModule
        val voiceAP2F1 = voice1.templateToVoiceMap[ap2f] as UnitGeneratorModule
        val voiceAP2FInJack0 = voiceAP2F0.inputs.find { it.name == "in" } as AudioInputJack
        val voiceAP2FInJack1 = voiceAP2F1.inputs.find { it.name == "in" } as AudioInputJack

        // 2. Initially isValueAdded is false
        assertEquals(false, voiceAP2FInJack0.isValueAdded)
        assertEquals(false, voiceAP2FInJack1.isValueAdded)

        // 3. Set isValueAdded = true on the template jack
        ap2fInJack.isValueAdded = true

        // Verify it synced to both hidden voice jacks
        assertEquals(true, voiceAP2FInJack0.isValueAdded)
        assertEquals(true, voiceAP2FInJack1.isValueAdded)

        // 4. Send NoteOn with pitch = 60.0 (Concert Middle C, ~261.63 Hz)
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        engine.generateNextBuffer()

        // Verify that the ap2f unit outputs frequencies in the ~261 Hz range (pitch 60 + vibrato modulation)
        val ap2fUnit0 = voiceAP2F0.unit as com.softsynth.ksyn.unitgen.PitchToFrequency
        ap2fUnit0.pullData(engine.frameCount)
        val outFreqs = ap2fUnit0.output.getValues()
        assertTrue(outFreqs.all { it > 200.0f && it < 350.0f }, "ap2f output frequencies should be in the ~261 Hz range with pitch 60 added to triOsc")
    }

    @Test
    fun testPolyPatchVoiceCircuitAutoDisableLifecycle() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule("autoDisableTest")
        polyPatch.initializeVoices(2)

        val osc = UnitGeneratorModule("sawOscBL", com.softsynth.ksyn.unitgen.SawtoothOscillatorBL()).apply { name = "osc" }
        val env = UnitGeneratorModule("envDAHDSR", com.softsynth.ksyn.unitgen.EnvelopeDAHDSR()).apply {
            name = "ampEnv"
            autoDisableVoice = AutoDisableVoiceMode.YES
        }
        val out = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()
        val onIn = polyPatch.graphState.modules.first { it is InputModule && it.name == "on" }

        polyPatch.graphState.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(env, androidx.compose.ui.geometry.Offset.Zero)

        // Connect osc -> env.amplitude -> out.in
        polyPatch.graphState.connect(osc.outputs.first { it.name == "out" }, env.inputs.first { it.name == "amplitude" })
        polyPatch.graphState.connect(env.outputs.first { it.name == "out" }, out.inputs.first { it.name == "in" })
        polyPatch.graphState.connect(onIn.outputs.first { it.name == "out" }, env.inputs.first { it.name == "on" })

        val voice0 = polyPatch.voices[0]
        val voice1 = polyPatch.voices[1]

        // AutoDisable should have automatically wired to both voice circuits and disabled them while idle
        assertEquals(false, voice0.voiceCircuit.isEnabled, "Voice 0 circuit should start disabled when idle")
        assertEquals(false, voice1.voiceCircuit.isEnabled, "Voice 1 circuit should start disabled when idle")

        // Trigger note on voice 0
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        engine.generateNextBuffer()

        // The allocated voice circuit should wake up (isEnabled = true)
        val activeVoice = if (voice0.voiceCircuit.isEnabled) voice0 else voice1
        assertTrue(activeVoice.voiceCircuit.isEnabled, "Active voice circuit should be enabled after noteOn")

        // Explicitly set AutoDisableVoiceMode.NO on the envelope
        polyPatch.syncAutoDisableVoice(env, AutoDisableVoiceMode.NO)

        // Both voice circuits should now be enabled because no envelope is auto-disabling them
        assertTrue(voice0.voiceCircuit.isEnabled, "Voice 0 circuit should be enabled when auto-disable is NO")
        assertTrue(voice1.voiceCircuit.isEnabled, "Voice 1 circuit should be enabled when auto-disable is NO")

        // Explicitly set AutoDisableVoiceMode.YES
        polyPatch.syncAutoDisableVoice(env, AutoDisableVoiceMode.YES)
        assertEquals(false, voice0.voiceCircuit.isEnabled, "Voice 0 circuit should be auto-disabled again when set to YES")
        assertEquals(false, voice1.voiceCircuit.isEnabled, "Voice 1 circuit should be auto-disabled again when set to YES")
    }

    @Test
    fun testPolyPatchVoiceCircuitAutoDisableLifecycleWithEnvSeg() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule("autoDisableEnvSegTest")
        polyPatch.initializeVoices(2)
        polyPatch.polyphonicInstrument!!.start()

        val osc = UnitGeneratorModule("sawOscBL", com.softsynth.ksyn.unitgen.SawtoothOscillatorBL()).apply { name = "osc" }
        val env = EnvSegModule().apply {
            name = "envSeg"
            autoDisableVoice = AutoDisableVoiceMode.YES
        }
        val out = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()
        val onIn = polyPatch.graphState.modules.first { it is InputModule && it.name == "on" }
        val offIn = polyPatch.graphState.modules.first { it is InputModule && it.name == "off" }

        polyPatch.graphState.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(env, androidx.compose.ui.geometry.Offset.Zero)

        // Connect osc -> env.amplitude -> out.in
        polyPatch.graphState.connect(osc.outputs.first { it.name == "out" }, env.inputs.first { it.name == "amplitude" })
        polyPatch.graphState.connect(env.outputs.first { it.name == "out" }, out.inputs.first { it.name == "in" })
        polyPatch.graphState.connect(onIn.outputs.first { it.name == "out" }, env.inputs.first { it.name == "on" })
        polyPatch.graphState.connect(offIn.outputs.first { it.name == "out" }, env.inputs.first { it.name == "off" })

        val voice0 = polyPatch.voices[0]
        val voice1 = polyPatch.voices[1]

        // AutoDisable should have automatically wired to both voice circuits and disabled them while idle
        assertEquals(false, voice0.voiceCircuit.isEnabled, "Voice 0 circuit should start disabled when idle")
        assertEquals(false, voice1.voiceCircuit.isEnabled, "Voice 1 circuit should start disabled when idle")

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine

        // Trigger noteOn
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        engine.generateNextBuffer()

        val activeVoice = if (voice0.voiceCircuit.isEnabled) voice0 else voice1
        assertTrue(activeVoice.voiceCircuit.isEnabled, "Active voice circuit should be enabled after noteOn")

        // Trigger noteOff
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_OFF,
                tag = 10,
                value = 60.0,
                extra = 0.0
            )
        )
        // Run enough buffers for the release segment to finish (0.5s release -> at 44100Hz/64 ~ 350-500 buffers)
        for (i in 0 until 600) {
            engine.generateNextBuffer()
        }

        // Voice circuit should now be disabled after release completes!
        assertEquals(false, activeVoice.voiceCircuit.isEnabled, "Active voice circuit should be disabled after envelope release completes")
    }

    @Test
    fun testPolyPatchStereoInitializationAndIntrinsicModules() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(4, isStereo = true)
        assertEquals(4, polyPatch.maxVoices)
        assertTrue(polyPatch.isStereo)
        assertNotNull(polyPatch.polyphonicInstrument)

        // Outer outputs: should have "out0" and "out1", NOT "output"
        assertNotNull(polyPatch.outputs.find { it.name == "out0" }, "Should have out0 jack")
        assertNotNull(polyPatch.outputs.find { it.name == "out1" }, "Should have out1 jack")
        assertTrue(polyPatch.outputs.none { it.name == "output" }, "Should not have mono output jack")

        // Inside modules: should have intrinsic AudioOutput2Module named "output"
        val stereoOut = polyPatch.graphState.modules.filterIsInstance<AudioOutput2Module>().firstOrNull()
        assertNotNull(stereoOut, "Should have AudioOutput2Module inside")
        assertEquals("output", stereoOut.name)
        assertNotNull(stereoOut.inputs.find { it.name == "in0" })
        assertNotNull(stereoOut.inputs.find { it.name == "in1" })
        assertTrue(polyPatch.graphState.modules.none { it is AudioOutputModule }, "Should not have AudioOutputModule")

        // Cannot delete intrinsic stereo output
        assertTrue(stereoOut.isIntrinsic)
        assertTrue(polyPatch.isModuleIntrinsic(stereoOut))
        assertFalse(polyPatch.canDeleteChildModule(stereoOut))
        assertFalse(polyPatch.graphState.canDeleteModule(stereoOut))
        polyPatch.graphState.selectModule(stereoOut)
        polyPatch.graphState.deleteSelectedModules()
        assertTrue(polyPatch.graphState.modules.contains(stereoOut), "Intrinsic AudioOutput2Module cannot be deleted")
    }

    @Test
    fun testPolyPatchStereoVoiceRouting() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2, isStereo = true)

        val osc0 = UnitGeneratorModule("sineOsc0", SineOscillator())
        val osc1 = UnitGeneratorModule("sineOsc1", SineOscillator())
        polyPatch.graphState.addModule(osc0, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(osc1, androidx.compose.ui.geometry.Offset.Zero)

        val stereoOut = polyPatch.graphState.modules.filterIsInstance<AudioOutput2Module>().first()
        val in0 = stereoOut.inputs.first { it.name == "in0" }
        val in1 = stereoOut.inputs.first { it.name == "in1" }

        polyPatch.graphState.connect(osc0.outputs.first { it.name == "out" }, in0)
        polyPatch.graphState.connect(osc1.outputs.first { it.name == "out" }, in1)

        assertEquals(2, polyPatch.voices.size)
        // Check that voice twoInDualOut exists
        for (voice in polyPatch.voices) {
            val voiceStereoOut = voice.templateToVoiceMap[stereoOut] as? AudioOutput2Module
            assertNotNull(voiceStereoOut)
            assertNotNull(voiceStereoOut.twoInDualOut)
        }

        // Verify removing module doesn't crash
        polyPatch.graphState.removeModulesDirectly(listOf(osc0))
    }

    @Test
    fun testPolyPatchStereoSerialization() {
        val rootGraph = GraphState()
        val polyPatch = PolyPatchModule()
        rootGraph.addModule(polyPatch, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.initializeVoices(3, isStereo = true)

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"isStereo\": true"))

        val newRootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newRootGraph, json)
        assertEquals(1, deserialized.size)

        val newPolyPatch = deserialized[0] as PolyPatchModule
        assertEquals(3, newPolyPatch.maxVoices)
        assertTrue(newPolyPatch.isInitialized)
        assertTrue(newPolyPatch.isStereo)
        assertNotNull(newPolyPatch.outputs.find { it.name == "out0" })
        assertNotNull(newPolyPatch.outputs.find { it.name == "out1" })
        assertTrue(newPolyPatch.outputs.none { it.name == "output" })

        val stereoOut = newPolyPatch.graphState.modules.filterIsInstance<AudioOutput2Module>().firstOrNull()
        assertNotNull(stereoOut)
        assertEquals("output", stereoOut.name)
    }

    @Test
    fun testLoadStereoPatchExample() = kotlinx.coroutines.runBlocking {
        val rootGraph = GraphState()
        try {
            checkStereoPatchExample(rootGraph)
        } finally {
            // Stop the patch's LineOut so it does not render in later tests.
            rootGraph.clearAllModules()
        }
    }

    private suspend fun checkStereoPatchExample(rootGraph: GraphState) {
        val resourceFile = listOf(
            java.io.File("src/commonMain/composeResources/files/examples/stereoPatch.json"),
            java.io.File("shared/src/commonMain/composeResources/files/examples/stereoPatch.json"),
            java.io.File("../shared/src/commonMain/composeResources/files/examples/stereoPatch.json")
        ).firstOrNull { it.exists() }
        assertNotNull(resourceFile, "Resource file stereoPatch.json should exist")
        val json = resourceFile.readText()
        val deserialized = GraphSerializer.deserialize(rootGraph, json)
        val polyPatch = deserialized.filterIsInstance<PolyPatchModule>().first()
        assertTrue(polyPatch.isStereo)
        assertTrue(polyPatch.isInitialized)

        val out0 = polyPatch.outputs.find { it.name == "out0" } as? AudioOutputJack
        val out1 = polyPatch.outputs.find { it.name == "out1" } as? AudioOutputJack
        assertNotNull(out0)
        assertNotNull(out1)

        val currentMixer0 = polyPatch.auxMixers["out0"]
        val currentMixer1 = polyPatch.auxMixers["out1"]
        println("currentMixer0 output: ${currentMixer0?.output}, out0 unitOutputPort: ${out0.unitOutputPort}")
        println("Do they match? ${currentMixer0?.output === out0.unitOutputPort}")

        val lineOut = deserialized.find { it.name == "lineOut" }
        assertNotNull(lineOut)

        // Check if polyPatch out0 and out1 are connected to lineOut
        val rootConns = rootGraph.connections.filter { it.output.module === polyPatch }
        println("Root conns from polyPatch: ${rootConns.map { "${it.output.name} -> ${it.input.module.name}.${it.input.name}" }}")

        // Check inside polyPatch
        val stereoOut = polyPatch.graphState.modules.filterIsInstance<AudioOutput2Module>().firstOrNull()
        assertNotNull(stereoOut)
        val innerConns = polyPatch.graphState.connections.filter { it.input.module === stereoOut }
        println("Inner conns to stereoOut: ${innerConns.map { "${it.output.module.name}.${it.output.name} -> ${it.input.name}" }}")

        // Check voice routing
        for ((idx, voice) in polyPatch.voices.withIndex()) {
            val voiceStereoOut = voice.templateToVoiceMap[stereoOut] as? AudioOutput2Module
            println("Voice $idx stereoOut: $voiceStereoOut, twoInDualOut: ${voiceStereoOut?.twoInDualOut}")
            val voiceConns = voice.graphState.connections.filter { it.input.module === voiceStereoOut }
            println("Voice $idx conns to stereoOut: ${voiceConns.size}")
        }

        // Test audio synthesis
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        // Trigger a note
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 10,
                value = 60.0,
                extra = 1.0
            )
        )
        val voice0 = polyPatch.voices[0]
        println("Voice 0 circuit units: ${voice0.voiceCircuit.getUnits().map { it::class.simpleName }}")
        println("Voice 0 circuit isEnabled: ${voice0.voiceCircuit.isEnabled}")
        println("Voice 0 conns: ${voice0.graphState.connections.map { "${it.output.module.name}.${it.output.name} -> ${it.input.module.name}.${it.input.name}" }}")

        val vStereoOut = voice0.templateToVoiceMap[stereoOut] as AudioOutput2Module
        val conn0 = voice0.graphState.connections.find { it.input.name == "in0" }
        println("conn0 input module: ${conn0?.input?.module}")
        println("vStereoOut: $vStereoOut")
        println("Are they same instance? ${conn0?.input?.module === vStereoOut}")
        println("conn0 input jack: ${conn0?.input}, vStereoOut.in0: ${vStereoOut.in0}, same jack? ${conn0?.input === vStereoOut.in0}")
        for (i in 0 until 10) {
            engine.generateNextBuffer()
        }

        val activeVoice = polyPatch.voices.find { it.voiceCircuit.isEnabled } ?: voice0
        val vActiveStereoOut = activeVoice.templateToVoiceMap[stereoOut] as AudioOutput2Module
        val voiceOut0 = vActiveStereoOut.twoInDualOut.output.getValues(0)
        val voiceOut1 = vActiveStereoOut.twoInDualOut.output.getValues(1)
        assertTrue(voiceOut0.any { it != 0f }, "Active voice output 0 should generate signal")
        assertTrue(voiceOut1.any { it != 0f }, "Active voice output 1 should generate signal")

        assertSame(currentMixer0?.output, out0.unitOutputPort, "out0 port should point to active mixer0")
        assertSame(currentMixer1?.output, out1.unitOutputPort, "out1 port should point to active mixer1")

        val buffer0 = out0.unitOutputPort.getValues(out0.partIndex)
        val maxVal0 = buffer0.maxOrNull() ?: 0f
        val minVal0 = buffer0.minOrNull() ?: 0f
        assertTrue(maxVal0 != 0f || minVal0 != 0f, "out0 should produce audio signal")

        val buffer1 = out1.unitOutputPort.getValues(out1.partIndex)
        val maxVal1 = buffer1.maxOrNull() ?: 0f
        val minVal1 = buffer1.minOrNull() ?: 0f
        assertTrue(maxVal1 != 0f || minVal1 != 0f, "out1 should produce audio signal")
    }

    @Test
    fun testTeardownSubpatchWithPolyPatchAndOscillatorDoesNotCrashAudio() = kotlinx.coroutines.runBlocking {
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        try {
            val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
            val rootGs = GraphState()
            rootGs.engine = com.syntona.dataflow.engine.DataFlowEngine(testScope)

            // Create subpatch containing a PolyPatch and LineOut
            val subpatch = PatchModule("subpatch")
            val polyPatch = PolyPatchModule("poly")
            polyPatch.initializeVoices(2)

            val osc = UnitGeneratorModule("sineOsc", SineOscillator())
            polyPatch.graphState.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)
            val audioOut = AudioOutputModule()
            polyPatch.graphState.addModule(audioOut, androidx.compose.ui.geometry.Offset.Zero)

            val oscOut = osc.outputs.first { it.name == "out" }
            val audioIn = audioOut.inputs.first { it.name == "in" }
            polyPatch.graphState.connect(oscOut, audioIn)

            subpatch.graphState.addModule(polyPatch, androidx.compose.ui.geometry.Offset.Zero)
            val lineOutMod = UnitGeneratorModule("lineOut", com.softsynth.ksyn.unitgen.LineOut())
            subpatch.graphState.addModule(lineOutMod, androidx.compose.ui.geometry.Offset.Zero)

            val polyOutJack = polyPatch.outputs.first { it.name == "output" }
            val lineInJack = lineOutMod.inputs.first { it.name == "in0" }
            subpatch.graphState.connect(polyOutJack, lineInJack)

            rootGs.addModule(subpatch, androidx.compose.ui.geometry.Offset(100f, 100f))

            for (i in 0 until 5) {
                engine.generateNextBuffer()
            }

            // Teardown / clear all modules from root
            rootGs.clearAllModules()

            // Subsequent buffers must not crash due to null engine in dangling oscillator
            for (i in 0 until 10) {
                engine.generateNextBuffer()
            }
        } finally {
            testScope.cancel()
        }
    }

    /** A one-second sample of constant 0.5, unlike the silent default buffer. */
    private fun testSample() = com.softsynth.ksyn.data.FloatSample(44100).apply {
        frameRate = 44100.0
        write(0, FloatArray(44100) { 0.5f }, 0, 44100)
    }

    @Test
    fun testSampleModuleUnitsRunInTheVoiceCircuit() {
        // A voice only generates units in its circuit; any other unit is silent.
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val player = SamplePlayerModule()
        val reader = SampleReaderModule()
        val writer = SampleWriterModule()
        listOf(player, reader, writer).forEach {
            polyPatch.graphState.addModule(it, androidx.compose.ui.geometry.Offset.Zero)
        }

        for (voice in polyPatch.voices) {
            val voicePlayer = assertIs<SamplePlayerModule>(voice.templateToVoiceMap[player])
            val voiceReader = assertIs<SampleReaderModule>(voice.templateToVoiceMap[reader])
            val voiceWriter = assertIs<SampleWriterModule>(voice.templateToVoiceMap[writer])
            for (unit in listOf(
                voicePlayer.unit, voicePlayer.rateMultiplier,
                voiceReader.unit, voiceReader.rateMultiplier,
                voiceWriter.unit
            )) {
                assertSame(voice.voiceCircuit, unit.circuit, "${unit::class.simpleName} is not in the voice circuit")
            }
        }
    }

    @Test
    fun testVoiceSampleReadersGetTheRealSample() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val sample = FloatSampleModule()
        sample.sample = testSample()
        val reader = SampleReaderModule()
        polyPatch.graphState.addModule(sample, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(reader, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.connect(sample.dataOutput, reader.dataInput)

        fun voiceReaderData() = polyPatch.voices.map {
            assertIs<SampleReaderModule>(it.templateToVoiceMap[reader]).dataInput.currentData
        }
        // Each voice's sample module used to hand its reader the silent default buffer.
        voiceReaderData().forEach { assertSame(sample.sample, it) }

        // Loading another sample later reaches the voices too.
        val replacement = testSample()
        sample.sample = replacement
        sample.sendSample()
        voiceReaderData().forEach { assertSame(replacement, it) }
    }

    @Test
    fun testVoiceSamplePlayersFollowTheTemplateSample() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val player = SamplePlayerModule()
        player.sample = testSample()
        polyPatch.graphState.addModule(player, androidx.compose.ui.geometry.Offset.Zero)

        val replacement = testSample()
        player.sample = replacement
        for (voice in polyPatch.voices) {
            assertSame(replacement, assertIs<SamplePlayerModule>(voice.templateToVoiceMap[player]).sample)
        }
    }

    @Test
    fun testDelayInVoicesHasABuffer() = kotlinx.coroutines.runBlocking {
        // Voices are rebuilt from ModuleRegistry.ksynFactories, not copied from the template, so
        // a delay whose buffer is only allocated by the palette entry was silent in every voice.
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val template = com.syntona.dataflow.registry.ModuleRegistry.findCreator("delay~")!!.invoke()
        polyPatch.graphState.addModule(template, androidx.compose.ui.geometry.Offset.Zero)

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        val delays = polyPatch.voices.map { voice ->
            val voiceModule = assertIs<UnitGeneratorModule>(voice.templateToVoiceMap[template])
            assertIs<com.softsynth.ksyn.unitgen.InterpolatingDelay>(voiceModule.unit).also {
                it.input.set(1.0)
                it.delay.set(0.0)
            }
        }
        // Port values set while the engine runs are applied on the audio thread.
        engine.generateNextBuffer()

        for (delay in delays) {
            // With zero delay an allocated delay line passes its input straight through;
            // an unallocated one returns without writing anything.
            delay.generate()
            assertEquals(1.0f, delay.output.getValues()[0], "delay in a voice produced no output")
        }
    }

    @Test
    fun testNoteCommandDurationAndDispatchOrder() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        val durTemplate = polyPatch.graphState.modules.find { it.name == "duration" }
        assertNotNull(durTemplate, "polyPatch should have an intrinsic 'duration' input module")
        assertTrue(polyPatch.isModuleIntrinsic(durTemplate))

        polyPatch.initializeVoices(1)
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        val voice0 = polyPatch.voices[0]
        val log = mutableListOf<Pair<String, Double>>()

        for (inputName in listOf("duration", "amplitude", "pitch", "on", "off")) {
            val voiceInMod = voice0.findVoiceSpecialInput(inputName) as InputModule
            val outJack = voiceInMod.outputs.first { it.name == "out" }
            val recorder = object : Module("rec_$inputName") {
                val input = addInput("in", isKickable = true)
                override fun execute(triggerMessage: Message) {
                    if (triggerMessage is Message.FloatValue) {
                        log.add(inputName to triggerMessage.value)
                    }
                }
            }
            outJack.connect(recorder.input)
        }

        // 1. Plain NOTE_ON should dispatch duration = 1.0s, then amplitude, pitch, on
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 60,
                value = 60.0,
                extra = 0.5
            )
        )
        engine.generateNextBuffer()
        assertEquals(
            listOf("duration" to 1.0, "amplitude" to 0.5, "pitch" to 60.0, "on" to 1.0),
            log,
            "NOTE_ON should dispatch duration(1.0), amplitude, pitch before on"
        )

        // 2. NOTE with duration = 0.15s should dispatch duration = 0.15s before on, and schedule noteOff
        log.clear()
        var scheduledTime: com.syntona.core.model.ScoreTime? = null
        var scheduledAction: (() -> Unit)? = null
        val fakeEngine = object : com.syntona.dataflow.engine.DataFlowEngine(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        ) {
            override fun schedule(
                time: com.syntona.core.model.ScoreTime,
                action: () -> Unit
            ): com.syntona.dataflow.engine.ScheduledEvent {
                scheduledTime = time
                scheduledAction = action
                return com.syntona.dataflow.engine.ScheduledEvent(action)
            }
        }
        polyPatch.setEngine(fakeEngine)

        val startTime = com.syntona.core.model.ScoreTime.fromSeconds(2.0)
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                startTime,
                Message.VoiceOpcode.NOTE,
                tag = 64,
                value = 64.0,
                extra = 0.8,
                duration = 0.15
            )
        )
        engine.generateNextBuffer()

        assertEquals(
            listOf("duration" to 0.15, "amplitude" to 0.8, "pitch" to 64.0, "on" to 1.0),
            log,
            "NOTE should dispatch duration(0.15), amplitude, pitch before on"
        )
        assertNotNull(scheduledTime, "NOTE should schedule a noteOff on the DataFlowEngine")
        assertEquals(2.15, scheduledTime.seconds, 1e-6)

        // Firing the scheduled event triggers noteOff ("off" = 0.0)
        log.clear()
        scheduledAction!!.invoke()
        engine.generateNextBuffer()
        assertEquals(listOf("off" to 0.0), log)
    }

    @Test
    fun testNoteCancelsStaleScheduledNoteOffOnEarlyOffAndRetrigger() = kotlinx.coroutines.runBlocking {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        val scheduledEvents = mutableListOf<com.syntona.dataflow.engine.ScheduledEvent>()
        val fakeEngine = object : com.syntona.dataflow.engine.DataFlowEngine(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        ) {
            override fun schedule(
                time: com.syntona.core.model.ScoreTime,
                action: () -> Unit
            ): com.syntona.dataflow.engine.ScheduledEvent {
                val ev = com.syntona.dataflow.engine.ScheduledEvent(action)
                scheduledEvents.add(ev)
                return ev
            }
        }
        polyPatch.setEngine(fakeEngine)

        // 1. Send NOTE(tag=60), then cancel early with NOTE_OFF(tag=60)
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.fromSeconds(1.0),
                Message.VoiceOpcode.NOTE,
                tag = 60,
                value = 60.0,
                extra = 0.6,
                duration = 0.5
            )
        )
        assertEquals(1, scheduledEvents.size)
        val firstEvent = scheduledEvents[0]
        assertFalse(firstEvent.isCancelled)

        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.fromSeconds(1.1),
                Message.VoiceOpcode.NOTE_OFF,
                tag = 60,
                value = 60.0,
                extra = 0.0
            )
        )
        assertTrue(firstEvent.isCancelled, "NOTE_OFF should cancel pending scheduled noteOff for the same tag")

        // 2. Re-triggering NOTE with the same tag should cancel the previous NOTE's scheduled noteOff
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.fromSeconds(2.0),
                Message.VoiceOpcode.NOTE,
                tag = 60,
                value = 60.0,
                extra = 0.7,
                duration = 0.5
            )
        )
        assertEquals(2, scheduledEvents.size)
        val secondEvent = scheduledEvents[1]
        assertFalse(secondEvent.isCancelled)

        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.fromSeconds(2.1),
                Message.VoiceOpcode.NOTE,
                tag = 60,
                value = 60.0,
                extra = 0.8,
                duration = 0.5
            )
        )
        assertEquals(3, scheduledEvents.size)
        assertTrue(secondEvent.isCancelled, "Re-triggering NOTE with the same tag must cancel prior scheduled noteOff")
        assertFalse(scheduledEvents[2].isCancelled)

        // 3. teardown cancels any remaining pending scheduled noteOff events
        polyPatch.teardown()
        assertTrue(scheduledEvents[2].isCancelled, "teardown must cancel pending scheduled noteOff events")
    }
}

