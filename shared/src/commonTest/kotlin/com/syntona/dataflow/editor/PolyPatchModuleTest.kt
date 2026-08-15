package com.syntona.dataflow.editor

import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.PassThrough
import com.syntona.dataflow.modules.*
import com.syntona.dataflow.model.*
import com.softsynth.ksyn.ports.UnitInputPort
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
        val outJack = sineOscModule.outputs.find { it.name == "output" }
        val inJack = audioOutputModule.inputs.find { it.name == "input" }
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
        assertEquals(4, polyPatch.graphState.modules.size)
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
        polyPatch.initializeVoices(1)

        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        val voice = polyPatch.polyphonicInstrument!!.voices.first() as DynamicPitchedVoice
        val pitchModule = polyPatch.graphState.modules.find { it.name == "pitch" }!!
        val pitchUnit = voice.moduleToUnitMap[pitchModule] as PassThrough

        // Note on with pitch = 60.0, amplitude = 0.8
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.NOTE_ON,
                tag = 0,
                value = 60.0,
                extra = 0.8
            )
        )
        engine.generateNextBuffer()

        assertEquals(60.0, voice.pitch, 1e-4)
        assertEquals(0.0, voice.pitchBend, 1e-4)
        assertEquals(60.0f, pitchUnit.input.get(0), 1e-4f)

        // Send pitch bend of +2.0 semitones
        polyPatch.voiceInputJack.receive(
            Message.VoiceMessage(
                com.syntona.core.model.ScoreTime.Zero,
                Message.VoiceOpcode.PITCH_BEND,
                tag = -1,
                value = 2.0,
                extra = 0.0
            )
        )
        engine.generateNextBuffer()

        assertEquals(60.0, voice.pitch, 1e-4)
        assertEquals(2.0, voice.pitchBend, 1e-4)
        assertEquals(62.0f, pitchUnit.input.get(0), 1e-4f)
    }

    @Test
    fun testEnsureDefaultModulesExistOnDeserialization() {
        val json = """
            {"modules":[{"id":0,"type":"polyPatch","name":"poly1","x":0.0,"y":0.0,"maxVoices":2,"patch":{"modules":[{"id":0,"type":"aOutput","name":"output","x":400.0,"y":250.0}],"connections":[]}}],"connections":[]}
        """.trimIndent()
        val rootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(rootGraph, json)
        val polyPatch = deserialized[0] as PolyPatchModule

        assertEquals(4, polyPatch.graphState.modules.size)
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
        val outJack = triOsc.outputs.find { it.name == "output" }!!
        val outputModule = polyPatch.graphState.modules.find { it.name == "output" }!!
        val inJack = outputModule.inputs.find { it.name == "input" }!!
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
        val pastedOutJack = pastedTriOsc.outputs.find { it.name == "output" }!!
        val parentInJack = parentOutput.inputs.find { it.name == "input" }!!
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

        // Step the sequence by kicking 'in' 3 times to get from 0 to 3
        // Initial state is step 0. So 3 increments should land on step 3.
        seq.increment.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 1.0))

        assertEquals(3, seq.count)

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

        // Verify the connection: template passThrough's output should connect to the instrument's "vibrato" port
        val instrument = polyPatch.polyphonicInstrument
        assertNotNull(instrument)
        val instrumentPort = instrument.getPortByName("vibrato") as? UnitInputPort
        assertNotNull(instrumentPort)

        val outputPart = vibratoIn.passThrough.output.parts[0]
        assertEquals(1, outputPart.connectionCount)
        assertEquals(instrumentPort.parts[0], outputPart.getConnection(0))

        // 2. Rename custom AudioInputModule
        vibratoIn.name = "lfoMod"
        engine.generateNextBuffer()

        // Old port should be gone or disconnected
        val oldInstPort = instrument.getPortByName("vibrato")
        assertTrue(oldInstPort == null)

        val newInstPort = instrument.getPortByName("lfoMod") as? UnitInputPort
        assertNotNull(newInstPort)
        assertEquals(1, outputPart.connectionCount)
        assertEquals(newInstPort.parts[0], outputPart.getConnection(0))

        // 3. Remove custom AudioInputModule
        polyPatch.graphState.removeModulesDirectly(listOf(vibratoIn))
        engine.generateNextBuffer()

        assertEquals(0, outputPart.connectionCount)
        assertTrue(instrument.getPortByName("lfoMod") == null)
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

        assertNotNull(mixer4.getGainJack(0))
        assertNotNull(mixer4.getPanJack(0))
        assertNotNull(mixer4.getInputJack(0))
        assertNotNull(mixer4.inputs.find { it.name == "amplitude" })
        assertEquals(2, mixer4.outputs.size)

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
}

