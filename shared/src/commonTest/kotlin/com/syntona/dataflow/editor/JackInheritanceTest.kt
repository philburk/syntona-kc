package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.unitgen.SineOscillator
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.ClockModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.UnitGeneratorModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JackInheritanceTest {

    @Test
    fun testKnobInheritsMinMaxAndValueOnFirstConnection() {
        val rootGraph = GraphState()
        val knob = KnobModule()
        rootGraph.addModule(knob, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val outJack = knob.outputs.find { it.name == "out" }!!
        val freqJack = osc.inputs.find { it.name == "frequency" }!!

        assertEquals(40.0, freqJack.min)
        assertEquals(8000.0, freqJack.max)
        assertEquals(440.0, freqJack.getValue())

        rootGraph.connect(outJack, freqJack)

        assertEquals(40.0, knob.valueIn.min)
        assertEquals(8000.0, knob.valueIn.max)
        assertEquals(440.0, knob.value)
        assertEquals(440.0, freqJack.getValue())
    }

    @Test
    fun testKnobPreservesValueOnSecondConnection() {
        val rootGraph = GraphState()
        val knob = KnobModule()
        rootGraph.addModule(knob, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val clock = ClockModule()
        rootGraph.addModule(clock, Offset.Zero)

        val outJack = knob.outputs.find { it.name == "out" }!!
        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        val rateJack = clock.inputs.find { it.name == "rate(hz)" }!!

        rootGraph.connect(outJack, freqJack)
        assertEquals(440.0, knob.value)

        // User changes value
        knob.setValue(1200.0)
        assertEquals(1200.0, knob.value)

        // Connect to second input with different range and default
        rootGraph.connect(outJack, rateJack)

        // Range expands to cover both
        assertEquals(minOf(freqJack.min, rateJack.min), knob.valueIn.min)
        assertEquals(maxOf(freqJack.max, rateJack.max), knob.valueIn.max)
        // Value must NOT be overwritten on second connection
        assertEquals(1200.0, knob.value)
    }

    @Test
    fun testFaderInheritsMinMaxAndValueOnFirstConnection() {
        val rootGraph = GraphState()
        val fader = FaderModule()
        rootGraph.addModule(fader, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val outJack = fader.outputs.find { it.name == "out" }!!
        val freqJack = osc.inputs.find { it.name == "frequency" }!!

        rootGraph.connect(outJack, freqJack)

        assertEquals(40.0, fader.valueIn.min)
        assertEquals(8000.0, fader.valueIn.max)
        assertEquals(440.0, fader.value)
    }

    @Test
    fun testInputModuleInheritsMinMaxAndValueOnFirstConnection() {
        val patch = PatchModule("testPatch")
        val inMod = InputModule()
        patch.graphState.addModule(inMod, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)

        val inModOut = inMod.output
        val freqJack = osc.inputs.find { it.name == "frequency" }!!

        patch.graphState.connect(inModOut, freqJack)

        assertEquals(40.0, inMod.defaultMin)
        assertEquals(8000.0, inMod.defaultMax)
        assertEquals(440.0, inMod.defaultValue)

        val patchJack = inMod.patchJack
        assertNotNull(patchJack)
        assertEquals(40.0, patchJack.min)
        assertEquals(8000.0, patchJack.max)
        assertEquals(440.0, patchJack.getValue())
    }

    @Test
    fun testKnobConnectingToPatchInputJackInheritsInheritedValues() {
        val rootGraph = GraphState()
        val patch = PatchModule("filterPatch")
        rootGraph.addModule(patch, Offset.Zero)

        val inMod = InputModule()
        patch.graphState.addModule(inMod, Offset.Zero)
        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)
        patch.graphState.connect(inMod.output, osc.inputs.find { it.name == "frequency" }!!)

        val knob = KnobModule()
        rootGraph.addModule(knob, Offset.Zero)

        val patchInputJack = patch.inputs.find { it === inMod.patchJack }
        assertNotNull(patchInputJack)
        assertEquals(40.0, patchInputJack.min)
        assertEquals(8000.0, patchInputJack.max)
        assertEquals(440.0, patchInputJack.getValue())

        rootGraph.connect(knob.outputs.first { it.name == "out" }, patchInputJack)

        assertEquals(40.0, knob.valueIn.min)
        assertEquals(8000.0, knob.valueIn.max)
        assertEquals(440.0, knob.value)
    }

    @Test
    fun testAudioInputModuleInheritsMinMaxAndValue() {
        val patch = PatchModule("audioPatch")
        val aInMod = AudioInputModule()
        patch.graphState.addModule(aInMod, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)

        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        patch.graphState.connect(aInMod.output, freqJack)

        assertEquals(40.0, aInMod.defaultMin)
        assertEquals(8000.0, aInMod.defaultMax)
        assertEquals(440.0, aInMod.defaultValue)

        val patchJack = aInMod.patchJack
        assertNotNull(patchJack)
        assertEquals(40.0, patchJack.min)
        assertEquals(8000.0, patchJack.max)
        assertEquals(440.0, patchJack.getValue())
    }

    @Test
    fun testDisconnectRecomputesMinMaxWhenOtherConnectionsRemain() {
        val rootGraph = GraphState()
        val knob = KnobModule()
        rootGraph.addModule(knob, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val clock = ClockModule()
        rootGraph.addModule(clock, Offset.Zero)

        val outJack = knob.outputs.find { it.name == "out" }!!
        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        val rateJack = clock.inputs.find { it.name == "rate(hz)" }!!

        rootGraph.connect(outJack, freqJack)
        rootGraph.connect(outJack, rateJack)

        assertEquals(minOf(freqJack.min, rateJack.min), knob.valueIn.min)
        assertEquals(maxOf(freqJack.max, rateJack.max), knob.valueIn.max)

        rootGraph.disconnect(outJack, rateJack)

        assertEquals(freqJack.min, knob.valueIn.min)
        assertEquals(freqJack.max, knob.valueIn.max)
    }

    @Test
    fun testInputModuleInheritsIntegerOnConnection() {
        val patch = PatchModule("testPatch")
        val inMod = InputModule()
        patch.graphState.addModule(inMod, Offset.Zero)

        val keyboard = com.syntona.dataflow.modules.Keyboard25Module()
        patch.graphState.addModule(keyboard, Offset.Zero)

        assertEquals(false, inMod.defaultIsInteger)
        assertEquals(false, inMod.patchJack?.isInteger)

        patch.graphState.connect(inMod.output, keyboard.octaveIn)

        assertEquals(true, inMod.defaultIsInteger)
        assertEquals(true, inMod.patchJack?.isInteger)
    }
}
