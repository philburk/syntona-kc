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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals(6, polyPatch.graphState.modules.size)
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

    @Test
    fun testEnsureDefaultModulesExistOnDeserialization() {
        val json = """
            {"modules":[{"id":0,"type":"polyPatch","name":"poly1","x":0.0,"y":0.0,"maxVoices":2,"patch":{"modules":[{"id":0,"type":"aOutput","name":"output","x":400.0,"y":200.0}],"connections":[]}}],"connections":[]}
        """.trimIndent()
        val rootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(rootGraph, json)
        val polyPatch = deserialized[0] as PolyPatchModule

        assertEquals(6, polyPatch.graphState.modules.size)
        assertTrue(polyPatch.graphState.modules.any { it.name == "on" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "off" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "pitch" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "amplitude" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "pressure" })
        assertTrue(polyPatch.graphState.modules.any { it.name == "output" })
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

        // Initial highlighted jack is output 0
        assertTrue(mod.stepOutputs[0].isHighlighted)

        // Send value 10.5 to 'in'
        mod.increment.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 10.5))

        // Modulo 8 of 10.5 is 2.5, which floors to index 2
        assertEquals(2.5, mod.stepOutputs[2].getValue())
        assertTrue(mod.stepOutputs[2].isHighlighted)
        kotlin.test.assertFalse(mod.stepOutputs[0].isHighlighted)

        // Send reset signal
        mod.reset.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))
        assertEquals(0.0, mod.stepOutputs[0].getValue())
        assertTrue(mod.stepOutputs[0].isHighlighted)
        kotlin.test.assertFalse(mod.stepOutputs[2].isHighlighted)

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
}

