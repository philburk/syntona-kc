package com.syntona.export

import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.softsynth.ksyn.unitgen.PitchToFrequency
import com.softsynth.ksyn.unitgen.SawtoothOscillatorBL
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PianoRollModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.UnitGeneratorModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceExporterTest {

    @Test
    fun testSanitizeClassName() {
        assertEquals("HeavenlyBell", SourceExporter.sanitizeClassName("HeavenlyBell.kt"))
        assertEquals("HeavenlyBell", SourceExporter.sanitizeClassName("HeavenlyBell.java"))
        assertEquals("HeavenlyBell", SourceExporter.sanitizeClassName("heavenly-bell"))
        assertEquals("MinimalSawVoice", SourceExporter.sanitizeClassName("minimal_saw_voice"))
        assertEquals("Voice123", SourceExporter.sanitizeClassName("123"))
        assertEquals("SyntonaVoice", SourceExporter.sanitizeClassName(""))
    }

    @Test
    fun testSanitizeIdentifier() {
        assertEquals("sawOscBL", SourceExporter.sanitizeIdentifier("sawOscBL"))
        assertEquals("v123", SourceExporter.sanitizeIdentifier("123"))
        assertEquals("inPart0", SourceExporter.sanitizeIdentifier("in-part-0"))
        // Math unit symbolic names
        assertEquals("plus", SourceExporter.sanitizeIdentifier("+~"))
        assertEquals("times", SourceExporter.sanitizeIdentifier("*~"))
        assertEquals("timesPlus", SourceExporter.sanitizeIdentifier("*+~"))
        assertEquals("minus", SourceExporter.sanitizeIdentifier("-~"))
        assertEquals("divide", SourceExporter.sanitizeIdentifier("/~"))
        assertEquals("compare", SourceExporter.sanitizeIdentifier(">~"))
        assertEquals("plus2", SourceExporter.sanitizeIdentifier("+~ 2"))
        assertEquals("timesPlus2", SourceExporter.sanitizeIdentifier("*+~2"))
    }

    @Test
    fun testValidatePatch() {
        val patch = PolyPatchModule()
        // Default polyPatch has voice inputs and default audio output
        val result1 = SourceExporter.validatePatch(patch)
        assertTrue(result1.isExportable)
        assertTrue(result1.unsupportedModules.isEmpty())

        // Add knob and fader (should be ignored without warning)
        patch.graphState.addModule(KnobModule(), androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.addModule(FaderModule(), androidx.compose.ui.geometry.Offset.Zero)

        val result2 = SourceExporter.validatePatch(patch)
        assertEquals(2, result2.ignoredModules.size)
        assertTrue(result2.unsupportedModules.isEmpty())

        // Add a non-KSyn module like PianoRollModule
        patch.graphState.addModule(PianoRollModule(), androidx.compose.ui.geometry.Offset.Zero)
        val result3 = SourceExporter.validatePatch(patch)
        assertEquals(1, result3.unsupportedModules.size)
        assertEquals("pianoRoll", result3.unsupportedModules[0].name)
    }

    @Test
    fun testKSynExportPolyPatch() {
        val polyPatch = PolyPatchModule("minimalSaw")

        val p2fMod = UnitGeneratorModule("pitchToHz~", PitchToFrequency())
        val oscMod = UnitGeneratorModule("sawOscBL", SawtoothOscillatorBL())
        val envMod = UnitGeneratorModule("envDAHDSR", EnvelopeDAHDSR())

        polyPatch.graphState.addModule(p2fMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(oscMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envMod, androidx.compose.ui.geometry.Offset.Zero)

        // Find ports
        val p2fOut = p2fMod.outputs.first { it.name == "out" }
        val oscFreqIn = oscMod.inputs.first { it.name == "frequency" }
        polyPatch.graphState.connect(p2fOut, oscFreqIn)

        val oscOut = oscMod.outputs.first { it.name == "out" }
        val envAmpIn = envMod.inputs.first { it.name == "amplitude" }
        polyPatch.graphState.connect(oscOut, envAmpIn)

        val envOut = envMod.outputs.first { it.name == "out" }
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()
        val outIn = outputMod.inputs.first { it.name == "in" }
        polyPatch.graphState.connect(envOut, outIn)

        val code = KSynSourceExporter.export(polyPatch, "MinimalSawVoice")

        assertTrue(code.contains("class MinimalSawVoice : Circuit(), PitchedVoice {"))
        assertTrue(code.contains("PitchToFrequency()"))
        assertTrue(code.contains("SawtoothOscillatorBL()"))
        assertTrue(code.contains("EnvelopeDAHDSR()"))
        assertTrue(code.contains("pitchToHz.output.connect(sawOscBL.frequency)"))
        assertTrue(code.contains("sawOscBL.output.connect(envDAHDSR.amplitude)"))
        assertTrue(code.contains("override fun noteOn("))
        assertTrue(code.contains("override fun noteOff("))
        assertTrue(code.contains("override fun getOutputPort(): UnitOutputPort"))
        assertTrue(code.contains("envDAHDSR.setupAutoDisable(this)"))
        assertTrue(code.contains("This code was exported from Syntona."))
        assertTrue(code.contains("val presetNames = arrayOf(\"Default\")"))
        assertTrue(code.contains("VoiceDescription(\"MinimalSawVoice\", presetNames)"))
    }

    @Test
    fun testJSynExportPolyPatchBypassesPitchToFrequency() {
        val polyPatch = PolyPatchModule("minimalSaw")

        val p2fMod = UnitGeneratorModule("pitchToHz~", PitchToFrequency())
        val oscMod = UnitGeneratorModule("sawOscBL", SawtoothOscillatorBL())
        val envMod = UnitGeneratorModule("envDAHDSR", EnvelopeDAHDSR())

        polyPatch.graphState.addModule(p2fMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(oscMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envMod, androidx.compose.ui.geometry.Offset.Zero)

        // Connect p2f to osc frequency
        val p2fOut = p2fMod.outputs.first { it.name == "out" }
        val oscFreqIn = oscMod.inputs.first { it.name == "frequency" }
        polyPatch.graphState.connect(p2fOut, oscFreqIn)

        // Connect osc to env amplitude
        val oscOut = oscMod.outputs.first { it.name == "out" }
        val envAmpIn = envMod.inputs.first { it.name == "amplitude" }
        polyPatch.graphState.connect(oscOut, envAmpIn)

        val envOut = envMod.outputs.first { it.name == "out" }
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()
        val outIn = outputMod.inputs.first { it.name == "in" }
        polyPatch.graphState.connect(envOut, outIn)

        val code = JSynSourceExporter.export(polyPatch, "MinimalSawVoice")

        assertTrue(code.contains("public class MinimalSawVoice extends Circuit implements UnitVoice {"))
        // JSyn voice directly binds frequency without PitchToFrequency
        assertFalse(code.contains("PitchToFrequency"))
        assertTrue(code.contains("addPort(frequency = sawOscBL.frequency, \"Frequency\");"))
        assertTrue(code.contains("sawOscBL.output.connect(envDAHDSR.amplitude);"))
        assertTrue(code.contains("public void noteOn(double freq, double ampl, TimeStamp timeStamp)"))
        assertTrue(code.contains("public void noteOff(TimeStamp timeStamp)"))
        assertTrue(code.contains("public UnitOutputPort getOutput()"))
        assertTrue(code.contains("envDAHDSR.setupAutoDisable(this);"))
        assertTrue(code.contains("This code was exported from Syntona."))
        assertTrue(code.contains("\"Default\""))
        assertTrue(code.contains("super(\"MinimalSawVoice\", presetNames);"))
    }

    @Test
    fun testKSynAndJSynExportRegularPatch() {
        val patch = PatchModule("myFilter")

        val inMod = AudioInputModule()
        inMod.name = "audioIn"
        val outMod = AudioOutputModule()
        outMod.name = "audioOut"
        val filterMod = UnitGeneratorModule("lowPass", com.softsynth.ksyn.unitgen.FilterLowPass())

        patch.graphState.addModule(inMod, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.addModule(filterMod, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.addModule(outMod, androidx.compose.ui.geometry.Offset.Zero)

        patch.graphState.connect(inMod.output, filterMod.inputs.first { it.name == "in" })
        patch.graphState.connect(filterMod.outputs.first { it.name == "out" }, outMod.input)

        val ksynCode = KSynSourceExporter.export(patch, "MyFilter")
        assertTrue(ksynCode.contains("class MyFilter : Circuit() {"))
        assertTrue(ksynCode.contains("val audioIn: UnitInputPort"))
        assertTrue(ksynCode.contains("val audioOut: UnitOutputPort"))
        assertTrue(ksynCode.contains("This code was exported from Syntona."))

        val jsynCode = JSynSourceExporter.export(patch, "MyFilter")
        assertTrue(jsynCode.contains("public class MyFilter extends Circuit {"))
        assertTrue(jsynCode.contains("public UnitInputPort audioIn;"))
        assertTrue(jsynCode.contains("public UnitOutputPort audioOut;"))
        assertTrue(jsynCode.contains("This code was exported from Syntona."))
    }

    @Test
    fun testNormalizeJackPortName() {
        assertEquals("Q", SourceExporter.normalizeJackPortName("q"))
        assertEquals("Q", SourceExporter.normalizeJackPortName("Q"))
        assertEquals("inputA", SourceExporter.normalizeJackPortName("inA"))
        assertEquals("inputA", SourceExporter.normalizeJackPortName("inputa"))
        assertEquals("inputB", SourceExporter.normalizeJackPortName("inB"))
        assertEquals("inputB", SourceExporter.normalizeJackPortName("inputb"))
        assertEquals("inputC", SourceExporter.normalizeJackPortName("inC"))
        assertEquals("outputA", SourceExporter.normalizeJackPortName("outA"))
        assertEquals("outputA", SourceExporter.normalizeJackPortName("outputa"))
        assertEquals("input", SourceExporter.normalizeJackPortName("in"))
        assertEquals("output", SourceExporter.normalizeJackPortName("out"))
    }

    @Test
    fun testPitchToFrequencyModuleValidatedAsAudio() {
        val patch = PolyPatchModule("minimalSawWithP2f")
        val p2f = com.syntona.dataflow.modules.PitchToFrequencyModule()
        patch.graphState.addModule(p2f, androidx.compose.ui.geometry.Offset.Zero)

        val result = SourceExporter.validatePatch(patch)
        assertTrue(result.isExportable)
        assertTrue(result.unsupportedModules.isEmpty(), "PitchToFrequencyModule should be considered audio, not unsupported")
    }

    @Test
    fun testKSynExportSawLowPassConnections() {
        val polyPatch = PolyPatchModule("SawLowPass")

        val oscMod = UnitGeneratorModule("sawOscBL", SawtoothOscillatorBL())
        val filterMod = UnitGeneratorModule("fourPole", com.softsynth.ksyn.unitgen.FilterFourPoles())
        val multMod = UnitGeneratorModule("multiply", com.softsynth.ksyn.unitgen.Multiply())
        val envMod = UnitGeneratorModule("ampEnv", EnvelopeDAHDSR())

        polyPatch.graphState.addModule(oscMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(filterMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(multMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envMod, androidx.compose.ui.geometry.Offset.Zero)

        // osc -> filter input
        val oscOut = oscMod.outputs.first { it.name == "out" }
        val filterIn = filterMod.inputs.first { it.name == "in" }
        polyPatch.graphState.connect(oscOut, filterIn)

        // filter -> multiply inputA
        val filterOut = filterMod.outputs.first { it.name == "out" }
        val multInA = multMod.inputs.first { it.name == "inA" }
        polyPatch.graphState.connect(filterOut, multInA)

        // env -> multiply inputB
        val envOut = envMod.outputs.first { it.name == "out" }
        val multInB = multMod.inputs.first { it.name == "inB" }
        polyPatch.graphState.connect(envOut, multInB)

        val code = KSynSourceExporter.export(polyPatch, "SawLowPassVoice")

        assertTrue(code.contains("fourPole.output.connect(multiply.inputA)"))
        assertTrue(code.contains("ampEnv.output.connect(multiply.inputB)"))
        assertTrue(code.contains("fourPole.Q.setup("))
    }

    @Test
    fun testModuleNameCollisions() {
        val polyPatch = PolyPatchModule("collisionTest")

        // Two modules both named "osc"
        val osc1 = UnitGeneratorModule("osc", SawtoothOscillatorBL())
        val osc2 = UnitGeneratorModule("osc", SawtoothOscillatorBL())
        val osc3 = UnitGeneratorModule("osc", SawtoothOscillatorBL())

        polyPatch.graphState.addModule(osc1, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(osc2, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(osc3, androidx.compose.ui.geometry.Offset.Zero)

        val ksynCode = KSynSourceExporter.export(polyPatch, "CollisionVoice")
        assertTrue(ksynCode.contains("private val osc = SawtoothOscillatorBL()"))
        assertTrue(ksynCode.contains("private val osc2 = SawtoothOscillatorBL()"))
        assertTrue(ksynCode.contains("private val osc3 = SawtoothOscillatorBL()"))

        val jsynCode = JSynSourceExporter.export(polyPatch, "CollisionVoice")
        assertTrue(jsynCode.contains("private SawtoothOscillatorBL osc;"))
        assertTrue(jsynCode.contains("private SawtoothOscillatorBL osc2;"))
        assertTrue(jsynCode.contains("private SawtoothOscillatorBL osc3;"))
    }

    @Test
    fun testMathUnitsExport() {
        val polyPatch = PolyPatchModule("mathPatch")

        val addMod = UnitGeneratorModule("+~", com.softsynth.ksyn.unitgen.Add())
        val addMod2 = UnitGeneratorModule("+~", com.softsynth.ksyn.unitgen.Add())
        val mulMod = UnitGeneratorModule("*~", com.softsynth.ksyn.unitgen.Multiply())
        val mulAddMod = UnitGeneratorModule("*+~", com.softsynth.ksyn.unitgen.MultiplyAdd())
        val subMod = UnitGeneratorModule("-~", com.softsynth.ksyn.unitgen.Subtract())
        val divMod = UnitGeneratorModule("/~", com.softsynth.ksyn.unitgen.Divide())
        val cmpMod = UnitGeneratorModule(">~", com.softsynth.ksyn.unitgen.Compare())

        polyPatch.graphState.addModule(addMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(addMod2, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(mulMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(mulAddMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(subMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(divMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(cmpMod, androidx.compose.ui.geometry.Offset.Zero)

        val ksynCode = KSynSourceExporter.export(polyPatch, "MathVoice")
        assertTrue(ksynCode.contains("private val plus = Add()"), "Expected 'plus = Add()' in KSyn:\n$ksynCode")
        assertTrue(ksynCode.contains("private val plus2 = Add()"), "Expected 'plus2 = Add()' in KSyn:\n$ksynCode")
        assertTrue(ksynCode.contains("private val times = Multiply()"), "Expected 'times = Multiply()' in KSyn:\n$ksynCode")
        assertTrue(ksynCode.contains("private val timesPlus = MultiplyAdd()"), "Expected 'timesPlus = MultiplyAdd()' in KSyn:\n$ksynCode")
        assertTrue(ksynCode.contains("private val minus = Subtract()"), "Expected 'minus = Subtract()' in KSyn:\n$ksynCode")
        assertTrue(ksynCode.contains("private val divide = Divide()"), "Expected 'divide = Divide()' in KSyn:\n$ksynCode")
        assertTrue(ksynCode.contains("private val compare = Compare()"), "Expected 'compare = Compare()' in KSyn:\n$ksynCode")

        val jsynCode = JSynSourceExporter.export(polyPatch, "MathVoice")
        assertTrue(jsynCode.contains("private Add plus;"), "Expected 'Add plus;' in JSyn:\n$jsynCode")
        assertTrue(jsynCode.contains("private Add plus2;"), "Expected 'Add plus2;' in JSyn:\n$jsynCode")
        assertTrue(jsynCode.contains("private Multiply times;"), "Expected 'Multiply times;' in JSyn:\n$jsynCode")
        assertTrue(jsynCode.contains("private MultiplyAdd timesPlus;"), "Expected 'MultiplyAdd timesPlus;' in JSyn:\n$jsynCode")
        assertTrue(jsynCode.contains("private Subtract minus;"), "Expected 'Subtract minus;' in JSyn:\n$jsynCode")
        assertTrue(jsynCode.contains("private Divide divide;"), "Expected 'Divide divide;' in JSyn:\n$jsynCode")
        assertTrue(jsynCode.contains("private Compare compare;"), "Expected 'Compare compare;' in JSyn:\n$jsynCode")
    }

    @Test
    fun testMultipleEnvelopesAndCustomAmplitudeRouting() {
        val polyPatch = PolyPatchModule("sawLowPass")

        val oscMod = UnitGeneratorModule("sawOscBL", SawtoothOscillatorBL())
        val envMod1 = UnitGeneratorModule("ampEnv", EnvelopeDAHDSR())
        val envMod2 = UnitGeneratorModule("ampEnv2", EnvelopeDAHDSR())
        val multMod1 = UnitGeneratorModule("multiply", com.softsynth.ksyn.unitgen.Multiply())
        val multMod2 = UnitGeneratorModule("multiply2", com.softsynth.ksyn.unitgen.Multiply())

        polyPatch.graphState.addModule(oscMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envMod1, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envMod2, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(multMod1, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(multMod2, androidx.compose.ui.geometry.Offset.Zero)

        // PolyPatch default input modules: on, off, pitch, amplitude, pressure
        val onMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "on" }
        val offMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "off" }
        val ampMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "amplitude" }

        // Wire "on" to both env1 and env2
        polyPatch.graphState.connect(onMod.output, envMod1.inputs.first { it.name == "in" || it.name == "input" })
        polyPatch.graphState.connect(onMod.output, envMod2.inputs.first { it.name == "in" || it.name == "input" })

        // Wire "off" to both env1 and env2
        polyPatch.graphState.connect(offMod.output, envMod1.inputs.first { it.name == "in" || it.name == "input" })
        polyPatch.graphState.connect(offMod.output, envMod2.inputs.first { it.name == "in" || it.name == "input" })

        // Wire "amplitude" to multiply2.inputa
        polyPatch.graphState.connect(ampMod.output, multMod2.inputs.first { it.name.startsWith("inA", ignoreCase = true) })

        val ksynCode = KSynSourceExporter.export(polyPatch, "SawLowPassVoice")
        assertTrue(ksynCode.contains("amplitude = multiply2.inputA"), "KSyn amplitude port should route to multiply2.inputA:\n$ksynCode")
        assertTrue(ksynCode.contains("ampEnv.input.off(timeStamp)"), "KSyn noteOff should trigger ampEnv")
        assertTrue(ksynCode.contains("ampEnv2.input.off(timeStamp)"), "KSyn noteOff should trigger ampEnv2")
        assertTrue(ksynCode.contains("ampEnv.input.on(timeStamp)"), "KSyn noteOn should trigger ampEnv")
        assertTrue(ksynCode.contains("ampEnv2.input.on(timeStamp)"), "KSyn noteOn should trigger ampEnv2")

        val jsynCode = JSynSourceExporter.export(polyPatch, "SawLowPassVoice")
        assertTrue(jsynCode.contains("addPort(amplitude = multiply2.inputA, \"Amplitude\");"), "JSyn amplitude port should route to multiply2.inputA:\n$jsynCode")
        assertTrue(jsynCode.contains("ampEnv.input.off(timeStamp);"), "JSyn noteOff should trigger ampEnv")
        assertTrue(jsynCode.contains("ampEnv2.input.off(timeStamp);"), "JSyn noteOff should trigger ampEnv2")
        assertTrue(jsynCode.contains("ampEnv.input.on(timeStamp);"), "JSyn noteOn should trigger ampEnv")
        assertTrue(jsynCode.contains("ampEnv2.input.on(timeStamp);"), "JSyn noteOn should trigger ampEnv2")
    }

    @Test
    fun testExportWithDirectOnOffJackConnections() {
        val polyPatch = PolyPatchModule("SawVoice")
        val envMod = UnitGeneratorModule("ampEnv", EnvelopeDAHDSR())
        polyPatch.graphState.addModule(envMod, androidx.compose.ui.geometry.Offset.Zero)

        val onMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "on" }
        val offMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "off" }

        polyPatch.graphState.connect(onMod.output, envMod.inputs.first { it.name == "on" })
        polyPatch.graphState.connect(offMod.output, envMod.inputs.first { it.name == "off" })

        val ksynCode = KSynSourceExporter.export(polyPatch, "SawVoice")
        assertTrue(ksynCode.contains("ampEnv.input.on(timeStamp)"), "KSyn noteOn should trigger ampEnv via on jack")
        assertTrue(ksynCode.contains("ampEnv.input.off(timeStamp)"), "KSyn noteOff should trigger ampEnv via off jack")

        val jsynCode = JSynSourceExporter.export(polyPatch, "SawVoice")
        assertTrue(jsynCode.contains("ampEnv.input.on(timeStamp);"), "JSyn noteOn should trigger ampEnv via on jack")
        assertTrue(jsynCode.contains("ampEnv.input.off(timeStamp);"), "JSyn noteOff should trigger ampEnv via off jack")
    }

    @Test
    fun testAutoDisableSelectsClosestEnvelopeToOutput() {
        val polyPatch = PolyPatchModule("multiEnvVoice")

        // Patch layout:
        // osc -> filter -> bigEnv -> AudioOutputModule
        // filter.frequency <- filterEnv
        val osc = UnitGeneratorModule("osc", SawtoothOscillatorBL())
        val filter = UnitGeneratorModule("filter", com.softsynth.ksyn.unitgen.FilterLowPass())
        val filterEnv = UnitGeneratorModule("filterEnv", EnvelopeDAHDSR())
        val bigEnv = UnitGeneratorModule("bigEnv", EnvelopeDAHDSR())
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()

        polyPatch.graphState.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(filter, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(filterEnv, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(bigEnv, androidx.compose.ui.geometry.Offset.Zero)

        // filterEnv -> filter.frequency (distance 3 to output)
        polyPatch.graphState.connect(filterEnv.outputs.first { it.name == "out" }, filter.inputs.first { it.name == "frequency" })
        // osc -> filter.in
        polyPatch.graphState.connect(osc.outputs.first { it.name == "out" }, filter.inputs.first { it.name == "in" })
        // filter -> bigEnv.amplitude (distance 2 to output)
        polyPatch.graphState.connect(filter.outputs.first { it.name == "out" }, bigEnv.inputs.first { it.name == "amplitude" })
        // bigEnv -> outputMod.in (distance 1 to output)
        polyPatch.graphState.connect(bigEnv.outputs.first { it.name == "out" }, outputMod.inputs.first { it.name == "in" })

        val ksynCode = KSynSourceExporter.export(polyPatch, "MultiEnvVoice")
        assertTrue(ksynCode.contains("bigEnv.setupAutoDisable(this)"), "Should choose bigEnv (closest to output):\n$ksynCode")
        assertFalse(ksynCode.contains("filterEnv.setupAutoDisable(this)"), "Should not choose filterEnv")

        val jsynCode = JSynSourceExporter.export(polyPatch, "MultiEnvVoice")
        assertTrue(jsynCode.contains("bigEnv.setupAutoDisable(this);"), "Should choose bigEnv in JSyn:\n$jsynCode")
        assertFalse(jsynCode.contains("filterEnv.setupAutoDisable(this);"), "Should not choose filterEnv in JSyn")
    }

    @Test
    fun testValidatePatchWithEnvSegModule() {
        val patch = PolyPatchModule("envSegPatch")
        patch.graphState.addModule(com.syntona.dataflow.modules.EnvSegModule(), androidx.compose.ui.geometry.Offset.Zero)
        val result = SourceExporter.validatePatch(patch)
        assertTrue(result.isExportable)
        assertTrue(result.unsupportedModules.isEmpty())
        assertTrue(result.audioModules.any { it is com.syntona.dataflow.modules.EnvSegModule })
    }

    @Test
    fun testKSynExportEnvSeg() {
        val polyPatch = PolyPatchModule("EnvSegVoice")

        val p2fMod = UnitGeneratorModule("pitchToHz~", PitchToFrequency())
        val oscMod = UnitGeneratorModule("sawOscBL", SawtoothOscillatorBL())
        val envSegMod = com.syntona.dataflow.modules.EnvSegModule()

        polyPatch.graphState.addModule(p2fMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(oscMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envSegMod, androidx.compose.ui.geometry.Offset.Zero)

        // Wiring
        val p2fOut = p2fMod.outputs.first { it.name == "out" }
        val oscFreqIn = oscMod.inputs.first { it.name == "frequency" }
        polyPatch.graphState.connect(p2fOut, oscFreqIn)

        val oscOut = oscMod.outputs.first { it.name == "out" }
        val envAmpIn = envSegMod.inputs.first { it.name == "amplitude" }
        polyPatch.graphState.connect(oscOut, envAmpIn)

        val envOut = envSegMod.outputs.first { it.name == "out" }
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()
        val outIn = outputMod.inputs.first { it.name == "in" }
        polyPatch.graphState.connect(envOut, outIn)

        val onMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "on" }
        val offMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "off" }
        polyPatch.graphState.connect(onMod.output, envSegMod.onIn)
        polyPatch.graphState.connect(offMod.output, envSegMod.offIn)

        val code = KSynSourceExporter.export(polyPatch, "EnvSegVoice")

        assertTrue(code.contains("import com.softsynth.ksyn.data.SegmentedEnvelope"), "Must import SegmentedEnvelope:\n$code")
        assertTrue(code.contains("val envSegEnvelope = SegmentedEnvelope(doubleArrayOf("), "Must create SegmentedEnvelope in companion object:\n$code")
        assertTrue(code.contains("sustainBegin = 2"), "Must set sustainBegin:\n$code")
        assertTrue(code.contains("sustainEnd = 2"), "Must set sustainEnd:\n$code")
        assertTrue(code.contains("private val envSeg = VariableRateMonoReader()"), "Must declare VariableRateMonoReader:\n$code")
        assertTrue(code.contains("add(envSeg)"), "Must add envSeg to circuit:\n$code")
        assertTrue(code.contains("sawOscBL.output.connect(envSeg.amplitude)"), "Must connect oscillator to envSeg.amplitude:\n$code")
        assertTrue(code.contains("envSeg.rate.setup(0.0, 1.0, 8.0)"), "Must setup envSeg rate:\n$code")
        assertTrue(code.contains("envSeg.amplitude.setup(0.0, 1.0, 1.0)"), "Must setup envSeg amplitude:\n$code")
        assertTrue(code.contains("envSeg.dataQueue.queueOn(envSegEnvelope, timeStamp)"), "Must queueOn envSeg in noteOn:\n$code")
        assertTrue(code.contains("envSeg.dataQueue.queueOff(envSegEnvelope, false, timeStamp)"), "Must queueOff envSeg in noteOff:\n$code")
        assertTrue(code.contains("return envSeg.output"), "Must return envSeg.output in getOutputPort:\n$code")
    }

    @Test
    fun testJSynExportEnvSeg() {
        val polyPatch = PolyPatchModule("EnvSegVoice")

        val oscMod = UnitGeneratorModule("sawOscBL", SawtoothOscillatorBL())
        val envSegMod = com.syntona.dataflow.modules.EnvSegModule()

        polyPatch.graphState.addModule(oscMod, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envSegMod, androidx.compose.ui.geometry.Offset.Zero)

        val oscOut = oscMod.outputs.first { it.name == "out" }
        val envAmpIn = envSegMod.inputs.first { it.name == "amplitude" }
        polyPatch.graphState.connect(oscOut, envAmpIn)

        val envOut = envSegMod.outputs.first { it.name == "out" }
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()
        val outIn = outputMod.inputs.first { it.name == "in" }
        polyPatch.graphState.connect(envOut, outIn)

        val onMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "on" }
        val offMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "off" }
        polyPatch.graphState.connect(onMod.output, envSegMod.onIn)
        polyPatch.graphState.connect(offMod.output, envSegMod.offIn)

        val code = JSynSourceExporter.export(polyPatch, "EnvSegVoice")

        assertTrue(code.contains("import com.jsyn.data.SegmentedEnvelope;"), "Must import SegmentedEnvelope in JSyn:\n$code")
        assertTrue(code.contains("private static final SegmentedEnvelope envSegEnvelope;"), "Must declare static SegmentedEnvelope:\n$code")
        assertTrue(code.contains("envSegEnvelope = new SegmentedEnvelope(envSegEnvelopeData);"), "Must initialize static SegmentedEnvelope:\n$code")
        assertTrue(code.contains("envSegEnvelope.setSustainBegin(2);"), "Must set sustainBegin in JSyn:\n$code")
        assertTrue(code.contains("envSegEnvelope.setSustainEnd(2);"), "Must set sustainEnd in JSyn:\n$code")
        assertTrue(code.contains("private VariableRateMonoReader envSeg;"), "Must declare VariableRateMonoReader in JSyn:\n$code")
        assertTrue(code.contains("add(envSeg = new VariableRateMonoReader());"), "Must instantiate VariableRateMonoReader in JSyn:\n$code")
        assertTrue(code.contains("sawOscBL.output.connect(envSeg.amplitude);"), "Must connect oscillator to envSeg.amplitude in JSyn:\n$code")
        assertTrue(code.contains("envSeg.rate.setup(0.0, 1.0, 8.0);"), "Must setup envSeg rate in JSyn:\n$code")
        assertTrue(code.contains("envSeg.amplitude.setup(0.0, 1.0, 1.0);"), "Must setup envSeg amplitude in JSyn:\n$code")
        assertTrue(code.contains("envSeg.dataQueue.queueOn(envSegEnvelope, timeStamp);"), "Must queueOn in JSyn noteOn:\n$code")
        assertTrue(code.contains("envSeg.dataQueue.queueOff(envSegEnvelope, false, timeStamp);"), "Must queueOff in JSyn noteOff:\n$code")
        assertTrue(code.contains("return envSeg.output;"), "Must return envSeg.output in JSyn getOutput:\n$code")
    }

    @Test
    fun testExportMultipleEnvSegModules() {
        val polyPatch = PolyPatchModule("MultiEnvSegVoice")

        val envSeg1 = com.syntona.dataflow.modules.EnvSegModule().apply { name = "ampEnv" }
        val envSeg2 = com.syntona.dataflow.modules.EnvSegModule().apply { name = "filterEnv" }

        polyPatch.graphState.addModule(envSeg1, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envSeg2, androidx.compose.ui.geometry.Offset.Zero)

        val onMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "on" }
        val offMod = polyPatch.graphState.modules.filterIsInstance<InputModule>().first { it.name == "off" }
        polyPatch.graphState.connect(onMod.output, envSeg1.onIn)
        polyPatch.graphState.connect(onMod.output, envSeg2.onIn)
        polyPatch.graphState.connect(offMod.output, envSeg1.offIn)
        polyPatch.graphState.connect(offMod.output, envSeg2.offIn)

        val ksynCode = KSynSourceExporter.export(polyPatch, "MultiEnvSegVoice")
        assertTrue(ksynCode.contains("val ampEnvEnvelope = SegmentedEnvelope"), "KSyn must create ampEnvEnvelope:\n$ksynCode")
        assertTrue(ksynCode.contains("val filterEnvEnvelope = SegmentedEnvelope"), "KSyn must create filterEnvEnvelope:\n$ksynCode")
        assertTrue(ksynCode.contains("ampEnv.dataQueue.queueOn(ampEnvEnvelope, timeStamp)"), "KSyn noteOn must trigger ampEnv")
        assertTrue(ksynCode.contains("filterEnv.dataQueue.queueOn(filterEnvEnvelope, timeStamp)"), "KSyn noteOn must trigger filterEnv")
        assertTrue(ksynCode.contains("ampEnv.dataQueue.queueOff(ampEnvEnvelope, false, timeStamp)"), "KSyn noteOff must trigger ampEnv")
        assertTrue(ksynCode.contains("filterEnv.dataQueue.queueOff(filterEnvEnvelope, false, timeStamp)"), "KSyn noteOff must trigger filterEnv")

        val jsynCode = JSynSourceExporter.export(polyPatch, "MultiEnvSegVoice")
        assertTrue(jsynCode.contains("private static final SegmentedEnvelope ampEnvEnvelope;"), "JSyn must declare ampEnvEnvelope:\n$jsynCode")
        assertTrue(jsynCode.contains("private static final SegmentedEnvelope filterEnvEnvelope;"), "JSyn must declare filterEnvEnvelope:\n$jsynCode")
        assertTrue(jsynCode.contains("ampEnv.dataQueue.queueOn(ampEnvEnvelope, timeStamp);"), "JSyn noteOn must trigger ampEnv")
        assertTrue(jsynCode.contains("filterEnv.dataQueue.queueOn(filterEnvEnvelope, timeStamp);"), "JSyn noteOn must trigger filterEnv")
        assertTrue(jsynCode.contains("ampEnv.dataQueue.queueOff(ampEnvEnvelope, false, timeStamp);"), "JSyn noteOff must trigger ampEnv")
        assertTrue(jsynCode.contains("filterEnv.dataQueue.queueOff(filterEnvEnvelope, false, timeStamp);"), "JSyn noteOff must trigger filterEnv")
    }
}
