package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.modules.AddModule
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.MultiplyModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatchExtractorTest {

    @Test
    fun testExtractEmptySelectionReturnsNull() {
        val graphState = GraphState()
        val result = PatchExtractor.extractToPatch(graphState, emptyList())
        assertNull(result)
        assertEquals(0, graphState.modules.size)
    }

    @Test
    fun testExtractInternalConnectionsOnly() {
        val graphState = GraphState()
        val add = AddModule()
        val mult = MultiplyModule()

        graphState.addModule(add, Offset(100f, 100f))
        graphState.addModule(mult, Offset(250f, 100f))
        graphState.connect(add.output, mult.inputA)

        assertEquals(2, graphState.modules.size)
        assertEquals(1, graphState.connections.size)

        val patch = PatchExtractor.extractToPatch(graphState, listOf(add, mult))
        assertNotNull(patch)

        // Parent graph should now contain only the new patch module
        assertEquals(1, graphState.modules.size)
        assertEquals(patch, graphState.modules.first())
        assertEquals(0, graphState.connections.size)

        // Inside the patch
        val innerModules = patch.graphState.modules
        assertEquals(2, innerModules.size)
        val innerAdd = innerModules.find { it is AddModule } as AddModule
        val innerMult = innerModules.find { it is MultiplyModule } as MultiplyModule
        assertNotNull(innerAdd)
        assertNotNull(innerMult)

        // Internal connection should be preserved
        assertEquals(1, patch.graphState.connections.size)
        val conn = patch.graphState.connections.first()
        assertEquals(innerAdd.output, conn.output)
        assertEquals(innerMult.inputA, conn.input)

        // No outer jacks needed
        assertEquals(0, patch.inputs.size)
        assertEquals(0, patch.outputs.size)
    }

    @Test
    fun testExtractWithExternalControlConnections() {
        val graphState = GraphState()
        val knob = KnobModule()
        val add = AddModule()
        val mult = MultiplyModule()
        val fader = FaderModule()

        graphState.addModule(knob, Offset(50f, 100f))
        graphState.addModule(add, Offset(200f, 100f))
        graphState.addModule(mult, Offset(350f, 100f))
        graphState.addModule(fader, Offset(500f, 100f))

        // Wire: knob.output -> add.inputA -> mult.inputA -> fader.valueIn
        graphState.connect(knob.output, add.inputA)
        graphState.connect(add.output, mult.inputA)
        graphState.connect(mult.output, fader.valueIn)

        assertEquals(4, graphState.modules.size)
        assertEquals(3, graphState.connections.size)

        val patch = PatchExtractor.extractToPatch(graphState, listOf(add, mult))
        assertNotNull(patch)

        // Parent graph: knob, patch, fader
        assertEquals(3, graphState.modules.size)
        assertEquals(2, graphState.connections.size)

        // Parent connections: knob.output -> patch.in, patch.out -> fader.valueIn
        assertEquals(1, patch.inputs.size)
        assertEquals(1, patch.outputs.size)
        val patchInputJack = patch.inputs.first()
        val patchOutputJack = patch.outputs.first()

        assertTrue(graphState.connections.any { it.output == knob.output && it.input == patchInputJack })
        assertTrue(graphState.connections.any { it.output == patchOutputJack && it.input == fader.valueIn })

        // Inside the patch: InputModule, AddModule, MultiplyModule, OutputModule
        val innerModules = patch.graphState.modules
        assertEquals(4, innerModules.size)

        val inMod = innerModules.find { it is InputModule } as InputModule
        val outMod = innerModules.find { it is OutputModule } as OutputModule
        val innerAdd = innerModules.find { it is AddModule } as AddModule
        val innerMult = innerModules.find { it is MultiplyModule } as MultiplyModule

        assertNotNull(inMod)
        assertNotNull(outMod)
        assertNotNull(innerAdd)
        assertNotNull(innerMult)

        assertEquals(3, patch.graphState.connections.size)
        assertTrue(patch.graphState.connections.any { it.output == inMod.output && it.input == innerAdd.inputA })
        assertTrue(patch.graphState.connections.any { it.output == innerAdd.output && it.input == innerMult.inputA })
        assertTrue(patch.graphState.connections.any { it.output == innerMult.output && it.input == outMod.input })
    }

    @Test
    fun testExtractWithExternalAudioConnections() {
        val graphState = GraphState()
        val srcAudioIn = AudioInputModule()
        val sineOsc = com.syntona.dataflow.registry.ModuleRegistry.findCreator("sineOsc")!!.invoke()
        val filter = com.syntona.dataflow.registry.ModuleRegistry.findCreator("fourPole")!!.invoke()
        val dstAudioOut = AudioOutputModule()

        graphState.addModule(srcAudioIn, Offset(50f, 100f))
        graphState.addModule(sineOsc, Offset(200f, 100f))
        graphState.addModule(filter, Offset(350f, 100f))
        graphState.addModule(dstAudioOut, Offset(500f, 100f))

        // Wire audio: srcAudioIn.output -> filter.input0 (freq/input), sineOsc.output -> filter.input, filter.output -> dstAudioOut.input
        val filterIn = filter.inputs.first()
        val filterOut = filter.outputs.first()
        val sineOut = sineOsc.outputs.first()

        graphState.connect(srcAudioIn.output, filterIn)
        graphState.connect(sineOut, filterIn)
        graphState.connect(filterOut, dstAudioOut.input)

        val patch = PatchExtractor.extractToPatch(graphState, listOf(sineOsc, filter))
        assertNotNull(patch)

        // Parent graph: srcAudioIn, patch, dstAudioOut
        assertEquals(3, graphState.modules.size)
        assertEquals(1, patch.inputs.size)
        assertEquals(1, patch.outputs.size)

        val patchAudioIn = patch.inputs.first()
        val patchAudioOut = patch.outputs.first()

        assertTrue(graphState.connections.any { it.output == srcAudioIn.output && it.input == patchAudioIn })
        assertTrue(graphState.connections.any { it.output == patchAudioOut && it.input == dstAudioOut.input })

        // Inside patch: AudioInputModule, sineOsc, filter, AudioOutputModule
        assertEquals(4, patch.graphState.modules.size)
        assertEquals(3, patch.graphState.connections.size)
    }

    @Test
    fun testMultipleIncomingToSameJackShareSingleInputModule() {
        val graphState = GraphState()
        val knob1 = KnobModule()
        val knob2 = KnobModule()
        val add = AddModule()

        graphState.addModule(knob1, Offset(50f, 50f))
        graphState.addModule(knob2, Offset(50f, 150f))
        graphState.addModule(add, Offset(200f, 100f))

        graphState.connect(knob1.output, add.inputA)
        graphState.connect(knob2.output, add.inputA)

        val patch = PatchExtractor.extractToPatch(graphState, listOf(add))
        assertNotNull(patch)

        // Single input jack on patch
        assertEquals(1, patch.inputs.size)
        val patchIn = patch.inputs.first()

        // Both knobs connect to the same patch input jack
        assertEquals(2, graphState.connections.size)
        assertTrue(graphState.connections.any { it.output == knob1.output && it.input == patchIn })
        assertTrue(graphState.connections.any { it.output == knob2.output && it.input == patchIn })

        // Inside the patch, exactly one InputModule exists
        val inputModules = patch.graphState.modules.filterIsInstance<InputModule>()
        assertEquals(1, inputModules.size)
    }

    @Test
    fun testEditActionsCanMakePatchAndMakePatch() {
        val graphState = GraphState()
        val mdi = MdiState()
        mdi.activeGraphState = graphState

        assertFalse(EditActions.canMakePatch(mdi))
        assertNull(EditActions.makePatch(mdi))

        val add = AddModule()
        graphState.addModule(add, Offset(100f, 100f))

        assertFalse(EditActions.canMakePatch(mdi))

        graphState.selectModule(add)
        assertTrue(EditActions.canMakePatch(mdi))

        val patch = EditActions.makePatch(mdi)
        assertNotNull(patch)
        assertEquals(1, graphState.modules.size)
        assertEquals(patch, graphState.modules.first())
        assertTrue(graphState.selectedModules.contains(patch))
    }

    @Test
    fun testExtractSequenceControlToAudioFrequencyPort() {
        val graphState = GraphState()
        val knob = KnobModule() // Represents sequence or control message source (OutputJack)
        val sineOsc = com.syntona.dataflow.registry.ModuleRegistry.findCreator("sineOsc")!!.invoke()
        val filter = com.syntona.dataflow.registry.ModuleRegistry.findCreator("fourPole")!!.invoke()
        val dstAudioOut = AudioOutputModule()

        graphState.addModule(knob, Offset(50f, 100f))
        graphState.addModule(sineOsc, Offset(200f, 100f))
        graphState.addModule(filter, Offset(350f, 100f))
        graphState.addModule(dstAudioOut, Offset(500f, 100f))

        // Wire control to audio input: knob.output (OutputJack) -> sineOsc.frequency (AudioInputJack)
        val freqJack = sineOsc.inputs.find { it.name == "frequency" }!!
        val filterIn = filter.inputs.first()
        val filterOut = filter.outputs.first()
        val sineOut = sineOsc.outputs.first()

        graphState.connect(knob.output, freqJack)
        graphState.connect(sineOut, filterIn)
        graphState.connect(filterOut, dstAudioOut.input)

        assertEquals(4, graphState.modules.size)
        assertEquals(3, graphState.connections.size)

        // Select the ksyn audio units
        val patch = PatchExtractor.extractToPatch(graphState, listOf(sineOsc, filter))
        assertNotNull(patch)

        // Parent graph: knob, patch, dstAudioOut
        assertEquals(3, graphState.modules.size)
        assertEquals(2, graphState.connections.size)

        // Inside the patch: InputModule (frequency), sineOsc, filter, AudioOutputModule (out)
        assertEquals(4, patch.graphState.modules.size)
        val inMod = patch.graphState.modules.find { it is InputModule } as? InputModule
        assertNotNull(inMod, "Inside patch must have an InputModule for frequency control")
        assertEquals("frequency", inMod.name)
        val outMod = patch.graphState.modules.find { it is AudioOutputModule } as? AudioOutputModule
        assertNotNull(outMod, "Inside patch must have an AudioOutputModule for audio output")
        assertEquals("out", outMod.name)
        assertEquals(3, patch.graphState.connections.size)

        // Verify parent connection from knob to patch's new frequency input exists
        val patchFreqInput = patch.inputs.find { it.name == "frequency" }
        assertNotNull(patchFreqInput, "Patch must have a frequency input jack")
        assertTrue(patchFreqInput !is AudioInputJack, "Input for message control should be a standard InputJack")
        assertTrue(graphState.connections.any { it.output == knob.output && it.input == patchFreqInput }, "knob must connect to patch.frequency in parent")

        // Verify parent connection from patch output to dstAudioOut exists
        val patchAudioOut = patch.outputs.first()
        assertTrue(patchAudioOut is AudioOutputJack)
        assertEquals("out", patchAudioOut.name)
        assertTrue(graphState.connections.any { it.output == patchAudioOut && it.input == dstAudioOut.input })
    }
}
