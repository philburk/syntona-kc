package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.unitgen.SineOscillator
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.ClockModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.MatchControl4Module
import com.syntona.dataflow.modules.MatchControl8Module
import com.syntona.dataflow.modules.MatchControlModule
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
        assertEquals(0.5, knob.valueIn.getValue())

        rootGraph.connect(outJack, freqJack)

        assertEquals(40.0, knob.valueIn.min)
        assertEquals(8000.0, knob.valueIn.max)
        assertEquals(440.0, knob.value)
        assertEquals(440.0, knob.valueIn.getValue())
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
        val rateJack = clock.inputs.find { it.name == "rateHz" }!!

        rootGraph.connect(outJack, freqJack)
        assertEquals(440.0, knob.value)

        // User changes value
        knob.setValue(1200.0)
        assertEquals(1200.0, knob.value)
        assertEquals(1200.0, knob.valueIn.getValue())

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

        assertEquals(0.0, fader.valueIn.getValue())

        rootGraph.connect(outJack, freqJack)

        assertEquals(40.0, fader.valueIn.min)
        assertEquals(8000.0, fader.valueIn.max)
        assertEquals(440.0, fader.value)
        assertEquals(440.0, fader.valueIn.getValue())
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
    fun testInputModuleInheritsNameOfFirstConnectedJack() {
        val patch = PatchModule("testPatch")
        val inMod = InputModule()
        patch.graphState.addModule(inMod, Offset.Zero)
        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)
        assertEquals("in1", inMod.name)

        patch.graphState.connect(inMod.output, osc.inputs.find { it.name == "frequency" }!!)
        assertEquals("frequency", inMod.name)
        assertEquals("frequency", inMod.patchJack?.name)

        // Only the first connection names it
        patch.graphState.connect(inMod.output, osc.inputs.find { it.name == "amplitude" }!!)
        assertEquals("frequency", inMod.name)

        // A second input fed to a jack of the same name gets a unique name
        val inMod2 = InputModule()
        patch.graphState.addModule(inMod2, Offset.Zero)
        val osc2 = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc2, Offset.Zero)
        patch.graphState.connect(inMod2.output, osc2.inputs.find { it.name == "frequency" }!!)
        assertEquals("frequency2", inMod2.name)
    }

    @Test
    fun testInputModuleKeepsNameTheUserGaveIt() {
        val patch = PatchModule("testPatch")
        val inMod = InputModule()
        patch.graphState.addModule(inMod, Offset.Zero)
        inMod.name = "cutoff"
        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)

        patch.graphState.connect(inMod.output, osc.inputs.find { it.name == "frequency" }!!)
        assertEquals("cutoff", inMod.name)
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
    fun testAudioInputModuleInheritsNameOfFirstConnectedJack() {
        val patch = PatchModule("audioPatch")
        val aInMod = AudioInputModule()
        patch.graphState.addModule(aInMod, Offset.Zero)
        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)
        assertEquals("aIn1", aInMod.name)

        patch.graphState.connect(aInMod.output, osc.inputs.find { it.name == "frequency" }!!)
        assertEquals("frequency", aInMod.name)
        assertEquals("frequency", aInMod.patchJack?.name)

        patch.graphState.connect(aInMod.output, osc.inputs.find { it.name == "amplitude" }!!)
        assertEquals("frequency", aInMod.name)
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
        val rateJack = clock.inputs.find { it.name == "rateHz" }!!

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

    @Test
    fun testKnobInheritsIntExpAndNameOnFirstConnection() {
        val rootGraph = GraphState()
        val knob = KnobModule()
        assertEquals("knob", knob.name)
        rootGraph.addModule(knob, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        assertEquals(true, freqJack.isExponential, "frequency jack should default to exponential")
        freqJack.isInteger = true

        val outJack = knob.outputs.find { it.name == "out" }!!
        rootGraph.connect(outJack, freqJack)

        assertEquals("frequency", knob.name)
        assertEquals(true, knob.valueIn.isInteger)
        assertEquals(true, knob.isInteger)
        assertEquals(true, knob.valueIn.isExponential)
        assertEquals(true, knob.isExponential)
    }

    @Test
    fun testFaderInheritsIntExpAndNameOnFirstConnection() {
        val rootGraph = GraphState()
        val fader = FaderModule()
        assertEquals("fader", fader.name)
        rootGraph.addModule(fader, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val ampJack = osc.inputs.find { it.name == "amplitude" }!!
        assertEquals(true, ampJack.isExponential, "amplitude jack should default to exponential")
        ampJack.isInteger = false

        val outJack = fader.outputs.find { it.name == "out" }!!
        rootGraph.connect(outJack, ampJack)

        assertEquals("amplitude", fader.name)
        assertEquals(false, fader.valueIn.isInteger)
        assertEquals(true, fader.valueIn.isExponential)
        assertEquals(true, fader.isExponential)
    }

    @Test
    fun testKnobPreservesNameOnSecondConnection() {
        val rootGraph = GraphState()
        val knob = KnobModule()
        rootGraph.addModule(knob, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)
        val clock = ClockModule()
        rootGraph.addModule(clock, Offset.Zero)

        val outJack = knob.outputs.find { it.name == "out" }!!
        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        val rateJack = clock.inputs.find { it.name == "rateHz" }!!

        rootGraph.connect(outJack, freqJack)
        assertEquals("frequency", knob.name)

        rootGraph.connect(outJack, rateJack)
        assertEquals("frequency", knob.name)
    }

    @Test
    fun testInputModuleInheritsExponentialOnConnection() {
        val patch = PatchModule("testPatch")
        val inMod = InputModule()
        patch.graphState.addModule(inMod, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)
        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        assertEquals(true, freqJack.isExponential)

        assertEquals(false, inMod.defaultIsExponential)
        assertEquals(false, inMod.patchJack?.isExponential)

        patch.graphState.connect(inMod.output, freqJack)

        assertEquals(true, inMod.defaultIsExponential)
        assertEquals(true, inMod.patchJack?.isExponential)
    }

    @Test
    fun testJackDefaultExponentialNames() {
        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        val ampJack = osc.inputs.find { it.name == "amplitude" }!!
        assertEquals(true, freqJack.isExponential)
        assertEquals(true, ampJack.isExponential)

        val clock = ClockModule()
        val rateJack = clock.inputs.find { it.name == "rateHz" }!!
        assertEquals(true, rateJack.isExponential, "rateHz should default to exponential")

        val addTime = com.syntona.dataflow.modules.AddTimeModule()
        assertEquals(true, addTime.delayMs.isExponential, "delayMs should default to exponential")

        val beatClock = com.syntona.dataflow.modules.BeatClockModule()
        assertEquals(false, beatClock.tempo.isExponential, "tempo should default to linear")

        val packNote = com.syntona.dataflow.modules.PackNoteModule()
        assertEquals(false, packNote.pitchIn.isExponential, "pitch should default to linear")
        assertEquals(true, packNote.durationIn.isExponential, "duration should default to exponential")
    }

    @Test
    fun testGraphSerializationPreservesExponentialOverride() {
        val patch = PatchModule("testPatch")
        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        patch.graphState.addModule(osc, Offset.Zero)
        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        assertEquals(true, freqJack.isExponential)

        // User explicitly toggles frequency to linear
        freqJack.isExponential = false

        val json = GraphSerializer.serialize(patch.graphState, listOf(osc))
        // Verify exponential:false is recorded for the override
        kotlin.test.assertTrue(json.contains("\"exponential\": false") || json.contains("\"exponential\":false"), "JSON should record override: $json")

        val newPatch = PatchModule("loadedPatch")
        GraphSerializer.deserialize(newPatch.graphState, json)
        val loadedOsc = newPatch.graphState.modules.first { it.name == "sineOsc" }
        val loadedFreqJack = loadedOsc.inputs.first { it.name == "frequency" }
        assertEquals(false, loadedFreqJack.isExponential, "Overridden false must be preserved on deserialization")
    }

    @Test
    fun testEnvelopeAndRateJacksDefaultToExponential() {
        val env = UnitGeneratorModule("env", com.softsynth.ksyn.unitgen.EnvelopeDAHDSR())
        val attack = env.inputs.first { it.name == "attack" }
        val decay = env.inputs.first { it.name == "decay" }
        val release = env.inputs.first { it.name == "release" }
        val hold = env.inputs.first { it.name == "hold" }
        val delay = env.inputs.first { it.name == "delay" }
        val sustain = env.inputs.first { it.name == "sustain" }

        assertEquals(true, attack.isExponential, "attack should be exponential")
        assertEquals(true, decay.isExponential, "decay should be exponential")
        assertEquals(true, release.isExponential, "release should be exponential")
        assertEquals(true, hold.isExponential, "hold should be exponential")
        assertEquals(true, delay.isExponential, "delay should be exponential")
        // sustain is level/amplitude ratio (0.0 to 1.0)
        assertEquals(false, sustain.isExponential, "sustain is level, should default linear")
    }

    @Test
    fun testKnobInheritsRateHzExpAndName() {
        val rootGraph = GraphState()
        val knob = KnobModule()
        rootGraph.addModule(knob, Offset.Zero)

        val clock = ClockModule()
        rootGraph.addModule(clock, Offset.Zero)

        val outJack = knob.outputs.find { it.name == "out" }!!
        val rateJack = clock.inputs.find { it.name == "rateHz" }!!

        rootGraph.connect(outJack, rateJack)

        assertEquals("rateHz", knob.name)
        assertEquals(true, knob.valueIn.isExponential)
        assertEquals(true, knob.isExponential)
    }

    @Test
    fun testMatchControlInheritsRangeAndValueOnFirstConnection() {
        val rootGraph = GraphState()
        val matchControl = MatchControlModule()
        rootGraph.addModule(matchControl, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        rootGraph.connect(matchControl.output, freqJack)

        assertEquals(40.0, matchControl.valueIn.min)
        assertEquals(8000.0, matchControl.valueIn.max)
        assertEquals(true, matchControl.valueIn.isExponential)
        assertEquals(false, matchControl.valueIn.isInteger)
        assertEquals(440.0, matchControl.currentValue)
        assertEquals(440.0, matchControl.valueIn.getValue())
    }

    @Test
    fun testMatchControl4And8InheritRangeAndValuePerOutputOnFirstConnection() {
        val rootGraph = GraphState()
        val match4 = MatchControl4Module()
        val match8 = MatchControl8Module()
        rootGraph.addModule(match4, Offset.Zero)
        rootGraph.addModule(match8, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)
        val clock = ClockModule()
        rootGraph.addModule(clock, Offset.Zero)

        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        val ampJack = osc.inputs.find { it.name == "amplitude" }!!
        val rateJack = clock.inputs.find { it.name == "rateHz" }!!

        rootGraph.connect(match4.stepOutputs[0], freqJack)
        rootGraph.connect(match4.stepOutputs[1], ampJack)
        rootGraph.connect(match8.stepOutputs[7], rateJack)

        assertEquals(40.0, match4.stepInputs[0].min)
        assertEquals(8000.0, match4.stepInputs[0].max)
        assertEquals(true, match4.stepInputs[0].isExponential)
        assertEquals(440.0, match4.currentValues[0])
        assertEquals(440.0, match4.stepInputs[0].getValue())

        assertEquals(ampJack.min, match4.stepInputs[1].min)
        assertEquals(ampJack.max, match4.stepInputs[1].max)
        assertEquals(ampJack.isExponential, match4.stepInputs[1].isExponential)
        assertEquals(ampJack.getValue(), match4.currentValues[1])
        assertEquals(ampJack.getValue(), match4.stepInputs[1].getValue())

        assertEquals(rateJack.min, match8.stepInputs[7].min)
        assertEquals(rateJack.max, match8.stepInputs[7].max)
        assertEquals(rateJack.isExponential, match8.stepInputs[7].isExponential)
        assertEquals(rateJack.getValue(), match8.currentValues[7])
        assertEquals(rateJack.getValue(), match8.stepInputs[7].getValue())
    }
}
