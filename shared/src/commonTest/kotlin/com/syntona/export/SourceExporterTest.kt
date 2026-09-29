package com.syntona.export

import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.softsynth.ksyn.unitgen.PitchToFrequency
import com.softsynth.ksyn.unitgen.SawtoothOscillatorBL
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AutoDisableVoiceMode
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.AudioOutput2Module
import com.syntona.dataflow.modules.ChebyshevOscModule
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
        val envMod = UnitGeneratorModule("envDAHDSR", EnvelopeDAHDSR()).apply {
            autoDisableVoice = AutoDisableVoiceMode.YES
        }

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
        assertTrue(code.contains("fun getVoiceDescription(): VoiceDescription"))
        assertTrue(code.contains("return MyVoiceDescription"))
        assertTrue(code.contains("private object MyVoiceDescription : VoiceDescription(\"MinimalSawVoice\", presetNames)"))
        assertTrue(code.contains("get() = \"com.softsynth.ksyn.instruments.MinimalSawVoice\""))
        assertTrue(code.contains("override fun createPitchedVoice(): PitchedVoice"))
        assertTrue(code.contains("return MinimalSawVoice()"))
        assertTrue(code.contains("override fun getTags(presetIndex: Int): Array<String>"))
        assertFalse(code.contains("override fun getDescription()"))
        assertFalse(code.contains("override fun getPresetNames()"))
    }

    @Test
    fun testJSynExportPolyPatchBypassesPitchToFrequency() {
        val polyPatch = PolyPatchModule("minimalSaw")

        val p2fMod = UnitGeneratorModule("pitchToHz~", PitchToFrequency())
        val oscMod = UnitGeneratorModule("sawOscBL", SawtoothOscillatorBL())
        val envMod = UnitGeneratorModule("envDAHDSR", EnvelopeDAHDSR()).apply {
            autoDisableVoice = AutoDisableVoiceMode.YES
        }

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
        val filterEnv = UnitGeneratorModule("filterEnv", EnvelopeDAHDSR()).apply {
            autoDisableVoice = AutoDisableVoiceMode.AUTO
        }
        val bigEnv = UnitGeneratorModule("bigEnv", EnvelopeDAHDSR()).apply {
            autoDisableVoice = AutoDisableVoiceMode.AUTO
        }
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
    fun testAutoDisablePrefersAmplitudePortOverCloserFilterCutoff() {
        val polyPatch = PolyPatchModule("filterAfterAmpVoice")

        // Topology: osc -> ampEnv (VCA) -> filter -> outputMod
        // filter.frequency <- filterEnv
        // Here, filter is 1 hop from output, so filterEnv is 2 hops from output.
        // ampEnv is 2 hops from filter (3 hops from output).
        // ampEnv modulates amplitude; filterEnv modulates frequency.
        // The heuristic MUST choose ampEnv!
        val osc = UnitGeneratorModule("osc", SawtoothOscillatorBL())
        val ampEnv = UnitGeneratorModule("myEnvelope", EnvelopeDAHDSR()).apply {
            autoDisableVoice = AutoDisableVoiceMode.AUTO
        }
        val filter = UnitGeneratorModule("filter", com.softsynth.ksyn.unitgen.FilterLowPass())
        val filterEnv = UnitGeneratorModule("cutoffModEnv", EnvelopeDAHDSR()).apply {
            autoDisableVoice = AutoDisableVoiceMode.AUTO
        }
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()

        polyPatch.graphState.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(ampEnv, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(filter, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(filterEnv, androidx.compose.ui.geometry.Offset.Zero)

        // osc -> ampEnv.amplitude
        polyPatch.graphState.connect(osc.outputs.first { it.name == "out" }, ampEnv.inputs.first { it.name == "amplitude" })
        // ampEnv -> filter.in
        polyPatch.graphState.connect(ampEnv.outputs.first { it.name == "out" }, filter.inputs.first { it.name == "in" })
        // filterEnv -> filter.frequency
        polyPatch.graphState.connect(filterEnv.outputs.first { it.name == "out" }, filter.inputs.first { it.name == "frequency" })
        // filter -> outputMod.in
        polyPatch.graphState.connect(filter.outputs.first { it.name == "out" }, outputMod.inputs.first { it.name == "in" })

        val ksynCode = KSynSourceExporter.export(polyPatch, "FilterAfterAmpVoice")
        assertTrue(ksynCode.contains("myEnvelope.setupAutoDisable(this)"), "Should choose myEnvelope (amplitude port):\n$ksynCode")
        assertFalse(ksynCode.contains("cutoffModEnv.setupAutoDisable(this)"), "Should not choose cutoffModEnv")
    }

    @Test
    fun testAutoDisableExplicitYesAndNoModes() {
        val polyPatch = PolyPatchModule("explicitModesVoice")

        val osc = UnitGeneratorModule("osc", SawtoothOscillatorBL())
        val envA = UnitGeneratorModule("envA", EnvelopeDAHDSR())
        val envB = UnitGeneratorModule("envB", EnvelopeDAHDSR())
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()

        polyPatch.graphState.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envA, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envB, androidx.compose.ui.geometry.Offset.Zero)

        // envA is connected directly to output
        polyPatch.graphState.connect(envA.outputs.first { it.name == "out" }, outputMod.inputs.first { it.name == "in" })

        // 1. Explicit NO on envA forces it to be excluded even though it connects to output
        envA.autoDisableVoice = com.syntona.dataflow.model.AutoDisableVoiceMode.NO
        envB.autoDisableVoice = com.syntona.dataflow.model.AutoDisableVoiceMode.YES

        val ksynCode1 = KSynSourceExporter.export(polyPatch, "ExplicitVoice1")
        assertTrue(ksynCode1.contains("envB.setupAutoDisable(this)"), "Should choose envB due to explicit YES:\n$ksynCode1")
        assertFalse(ksynCode1.contains("envA.setupAutoDisable(this)"), "Should not choose envA due to explicit NO")

        // 2. Both set to NO -> no auto-disable line generated
        envB.autoDisableVoice = com.syntona.dataflow.model.AutoDisableVoiceMode.NO
        val ksynCode2 = KSynSourceExporter.export(polyPatch, "ExplicitVoice2")
        assertFalse(ksynCode2.contains("setupAutoDisable"), "Neither should be chosen when both are NO:\n$ksynCode2")
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

    @Test
    fun testAutoDisableWhenEnvSegAndEnvDAHDSRBothInAutoMode() {
        val polyPatch = PolyPatchModule("EnvSegAndDAHDSRVoice")

        val osc = UnitGeneratorModule("osc", SawtoothOscillatorBL())
        val envSeg = com.syntona.dataflow.modules.EnvSegModule().apply { name = "ampEnv" }
        val filter = UnitGeneratorModule("filter", com.softsynth.ksyn.unitgen.FilterLowPass())
        val filterEnv = UnitGeneratorModule("filterEnv", EnvelopeDAHDSR())
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()

        polyPatch.graphState.addModule(osc, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(envSeg, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(filter, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(filterEnv, androidx.compose.ui.geometry.Offset.Zero)

        // osc -> envSeg.amplitude -> filter.in -> outputMod.in
        polyPatch.graphState.connect(osc.outputs.first { it.name == "out" }, envSeg.inputs.first { it.name == "amplitude" })
        polyPatch.graphState.connect(envSeg.outputs.first { it.name == "out" }, filter.inputs.first { it.name == "in" })
        polyPatch.graphState.connect(filter.outputs.first { it.name == "out" }, outputMod.inputs.first { it.name == "in" })

        // filterEnv -> filter.frequency
        polyPatch.graphState.connect(filterEnv.outputs.first { it.name == "out" }, filter.inputs.first { it.name == "frequency" })

        // Both envSeg and filterEnv now default to NO, so explicitly set them to AUTO for this test
        envSeg.autoDisableVoice = AutoDisableVoiceMode.AUTO
        filterEnv.autoDisableVoice = AutoDisableVoiceMode.AUTO
        assertEquals(AutoDisableVoiceMode.AUTO, envSeg.autoDisableVoice)
        assertEquals(AutoDisableVoiceMode.AUTO, filterEnv.autoDisableVoice)

        // 1. In Auto mode, KSyn exported code now calls setupAutoDisable(this) directly on ampEnv (VariableRateMonoReader)
        // because KSyn now natively supports setupAutoDisable on SequentialDataReader/VariableRateMonoReader!
        val ksynCode = KSynSourceExporter.export(polyPatch, "EnvSegAndDAHDSRVoice")
        assertTrue(ksynCode.contains("ampEnv.setupAutoDisable(this)"), "KSyn must call setupAutoDisable on ampEnv in Auto mode:\n$ksynCode")
        assertFalse(ksynCode.contains("filterEnv.setupAutoDisable(this)"), "KSyn should not call setupAutoDisable on filterEnv when ampEnv is main amp env:\n$ksynCode")

        // JSyn does not support setupAutoDisable on VariableRateMonoReader, so it falls back to filterEnv
        val jsynCode = JSynSourceExporter.export(polyPatch, "EnvSegAndDAHDSRVoice")
        assertTrue(jsynCode.contains("filterEnv.setupAutoDisable(this);"), "JSyn must fall back to filterEnv in Auto mode:\n$jsynCode")

        // 2. If envSeg is set to NO, KSyn falls back to filterEnv
        envSeg.autoDisableVoice = com.syntona.dataflow.model.AutoDisableVoiceMode.NO
        val ksynCodeEnvSegNo = KSynSourceExporter.export(polyPatch, "EnvSegAndDAHDSRVoice")
        assertTrue(ksynCodeEnvSegNo.contains("filterEnv.setupAutoDisable(this)"), "Should fall back to filterEnv when envSeg is NO:\n$ksynCodeEnvSegNo")

        // 3. If both are set to NO, neither is called
        filterEnv.autoDisableVoice = com.syntona.dataflow.model.AutoDisableVoiceMode.NO
        val ksynCodeNo = KSynSourceExporter.export(polyPatch, "EnvSegAndDAHDSRVoice")
        assertFalse(ksynCodeNo.contains("setupAutoDisable"), "No setupAutoDisable when both are NO:\n$ksynCodeNo")
    }

    @Test
    fun testExportCustomInputPortsSingleConnection() {
        val polyPatch = PolyPatchModule("FMPair")

        val modOsc = UnitGeneratorModule("modOsc", com.softsynth.ksyn.unitgen.SineOscillator())
        val divide = UnitGeneratorModule("divide", com.softsynth.ksyn.unitgen.Divide())
        val fcfmRatioIn = AudioInputModule().apply { name = "fcfmRatio" }
        val modAmpIn = AudioInputModule().apply { name = "modAmp" }

        polyPatch.graphState.addModule(modOsc, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(divide, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(fcfmRatioIn, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(modAmpIn, androidx.compose.ui.geometry.Offset.Zero)

        // Connect fcfmRatio -> divide.inB
        polyPatch.graphState.connect(fcfmRatioIn.output, divide.inputs.first { it.name.startsWith("inB", ignoreCase = true) })
        // Connect modAmp -> modOsc.amplitude
        polyPatch.graphState.connect(modAmpIn.output, modOsc.inputs.first { it.name == "amplitude" })

        val ksynCode = KSynSourceExporter.export(polyPatch, "FMPair")
        assertTrue(ksynCode.contains("val fcfmRatio: UnitInputPort"), "KSyn must declare fcfmRatio port:\n$ksynCode")
        assertTrue(ksynCode.contains("val modAmp: UnitInputPort"), "KSyn must declare modAmp port:\n$ksynCode")
        assertTrue(ksynCode.contains("fcfmRatio = divide.inputB"), "KSyn must set fcfmRatio = divide.inputB:\n$ksynCode")
        assertTrue(ksynCode.contains("addPort(fcfmRatio, \"FcfmRatio\")"), "KSyn must addPort fcfmRatio:\n$ksynCode")
        assertTrue(ksynCode.contains("modAmp = modOsc.amplitude"), "KSyn must set modAmp = modOsc.amplitude:\n$ksynCode")
        assertTrue(ksynCode.contains("addPort(modAmp, \"ModAmp\")"), "KSyn must addPort modAmp:\n$ksynCode")

        val jsynCode = JSynSourceExporter.export(polyPatch, "FMPair")
        assertTrue(jsynCode.contains("public UnitInputPort fcfmRatio;"), "JSyn must declare fcfmRatio port:\n$jsynCode")
        assertTrue(jsynCode.contains("public UnitInputPort modAmp;"), "JSyn must declare modAmp port:\n$jsynCode")
        assertTrue(jsynCode.contains("addPort(fcfmRatio = divide.inputB, \"FcfmRatio\");"), "JSyn must addPort fcfmRatio:\n$jsynCode")
        assertTrue(jsynCode.contains("addPort(modAmp = modOsc.amplitude, \"ModAmp\");"), "JSyn must addPort modAmp:\n$jsynCode")
    }

    @Test
    fun testExportCustomInputPortsFanOutWithPassThrough() {
        val polyPatch = PolyPatchModule("FanOutVoice")

        val osc1 = UnitGeneratorModule("osc1", com.softsynth.ksyn.unitgen.SineOscillator())
        val osc2 = UnitGeneratorModule("osc2", com.softsynth.ksyn.unitgen.SineOscillator())
        val vibratoIn = AudioInputModule().apply { name = "vibrato" }

        polyPatch.graphState.addModule(osc1, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(osc2, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(vibratoIn, androidx.compose.ui.geometry.Offset.Zero)

        // Connect vibratoIn -> osc1.frequency AND osc2.frequency
        polyPatch.graphState.connect(vibratoIn.output, osc1.inputs.first { it.name == "frequency" })
        polyPatch.graphState.connect(vibratoIn.output, osc2.inputs.first { it.name == "frequency" })

        val ksynCode = KSynSourceExporter.export(polyPatch, "FanOutVoice")
        assertTrue(ksynCode.contains("private val vibratoPassThrough = PassThrough()"), "KSyn must declare PassThrough for fan-out:\n$ksynCode")
        assertTrue(ksynCode.contains("val vibrato: UnitInputPort"), "KSyn must declare vibrato port:\n$ksynCode")
        assertTrue(ksynCode.contains("add(vibratoPassThrough)"), "KSyn must add PassThrough to circuit:\n$ksynCode")
        assertTrue(ksynCode.contains("vibratoPassThrough.output.connect(osc1.frequency)"), "KSyn must connect PassThrough to osc1:\n$ksynCode")
        assertTrue(ksynCode.contains("vibratoPassThrough.output.connect(osc2.frequency)"), "KSyn must connect PassThrough to osc2:\n$ksynCode")
        assertTrue(ksynCode.contains("vibrato = vibratoPassThrough.input"), "KSyn must bind port to passThrough.input:\n$ksynCode")
        assertTrue(ksynCode.contains("addPort(vibrato, \"Vibrato\")"), "KSyn must expose vibrato port:\n$ksynCode")

        val jsynCode = JSynSourceExporter.export(polyPatch, "FanOutVoice")
        assertTrue(jsynCode.contains("private PassThrough vibratoPassThrough;"), "JSyn must declare PassThrough for fan-out:\n$jsynCode")
        assertTrue(jsynCode.contains("public UnitInputPort vibrato;"), "JSyn must declare vibrato port:\n$jsynCode")
        assertTrue(jsynCode.contains("add(vibratoPassThrough = new PassThrough());"), "JSyn must add PassThrough in constructor:\n$jsynCode")
        assertTrue(jsynCode.contains("vibratoPassThrough.output.connect(osc1.frequency);"), "JSyn must connect PassThrough to osc1:\n$jsynCode")
        assertTrue(jsynCode.contains("vibratoPassThrough.output.connect(osc2.frequency);"), "JSyn must connect PassThrough to osc2:\n$jsynCode")
        assertTrue(jsynCode.contains("addPort(vibrato = vibratoPassThrough.input, \"Vibrato\");"), "JSyn must expose vibrato port:\n$jsynCode")
    }

    @Test
    fun testExportCustomInputPortsUnconnected() {
        val polyPatch = PolyPatchModule("UnconnectedVoice")
        val extraIn = AudioInputModule().apply { name = "extraIn" }
        polyPatch.graphState.addModule(extraIn, androidx.compose.ui.geometry.Offset.Zero)

        val ksynCode = KSynSourceExporter.export(polyPatch, "UnconnectedVoice")
        assertTrue(ksynCode.contains("val extraIn: UnitInputPort"), "KSyn must declare extraIn port:\n$ksynCode")
        assertTrue(ksynCode.contains("extraIn = UnitInputPort(\"ExtraIn\")"), "KSyn must initialize new UnitInputPort:\n$ksynCode")
        assertTrue(ksynCode.contains("addPort(extraIn, \"ExtraIn\")"), "KSyn must add extraIn port:\n$ksynCode")

        val jsynCode = JSynSourceExporter.export(polyPatch, "UnconnectedVoice")
        assertTrue(jsynCode.contains("public UnitInputPort extraIn;"), "JSyn must declare extraIn port:\n$jsynCode")
        assertTrue(jsynCode.contains("addPort(extraIn = new UnitInputPort(\"ExtraIn\"), \"ExtraIn\");"), "JSyn must add new UnitInputPort:\n$jsynCode")
    }

    @Test
    fun testExportCustomControlInputPorts() {
        val polyPatch = PolyPatchModule("ControlInputVoice")

        val osc = UnitGeneratorModule("osc", SawtoothOscillatorBL())
        val ampEnv = UnitGeneratorModule("ampEnv", EnvelopeDAHDSR())
        val filterEnv = UnitGeneratorModule("filterEnv", EnvelopeDAHDSR())
        val driveIn = InputModule().apply { name = "drive" }
        val releaseIn = InputModule().apply { name = "release" }
        val outputMod = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()

        for (m in listOf(osc, ampEnv, filterEnv, driveIn, releaseIn)) {
            polyPatch.graphState.addModule(m, androidx.compose.ui.geometry.Offset.Zero)
        }
        polyPatch.graphState.connect(osc.outputs.first { it.name == "out" }, ampEnv.inputs.first { it.name == "amplitude" })
        polyPatch.graphState.connect(ampEnv.outputs.first { it.name == "out" }, outputMod.inputs.first { it.name == "in" })
        // drive has one target, release fans out to both envelopes.
        polyPatch.graphState.connect(driveIn.output, filterEnv.inputs.first { it.name == "amplitude" })
        polyPatch.graphState.connect(releaseIn.output, filterEnv.inputs.first { it.name == "release" })
        polyPatch.graphState.connect(releaseIn.output, ampEnv.inputs.first { it.name == "release" })
        // Connecting adopts the target's range, so set the defaults afterwards.
        releaseIn.defaultValue = 2.5

        val ksynCode = KSynSourceExporter.export(polyPatch, "ControlInputVoice")
        assertTrue(ksynCode.contains("val drive: UnitInputPort"), "KSyn must declare drive port:\n$ksynCode")
        assertTrue(ksynCode.contains("drive = filterEnv.amplitude"), "KSyn must bind drive directly:\n$ksynCode")
        assertTrue(ksynCode.contains("addPort(drive, \"Drive\")"), "KSyn must add drive port:\n$ksynCode")
        assertTrue(ksynCode.contains("addPort(release, \"Release\")"), "KSyn must add release port:\n$ksynCode")
        assertTrue(ksynCode.contains("private val releasePassThrough = PassThrough()"), "KSyn must fan out release:\n$ksynCode")
        assertTrue(ksynCode.contains("release = releasePassThrough.input"), "KSyn must bind release to PassThrough:\n$ksynCode")
        assertTrue(ksynCode.contains("releasePassThrough.output.connect(filterEnv.release)"), ksynCode)
        assertTrue(ksynCode.contains("releasePassThrough.output.connect(ampEnv.release)"), ksynCode)
        assertTrue(ksynCode.contains("release.setup(0.0, 2.5, 15.0)"), "KSyn must set release range:\n$ksynCode")
        assertFalse(ksynCode.contains(".export(this"), "KSyn voice must not export envelope ports:\n$ksynCode")

        val jsynCode = JSynSourceExporter.export(polyPatch, "ControlInputVoice")
        assertTrue(jsynCode.contains("public UnitInputPort drive;"), "JSyn must declare drive port:\n$jsynCode")
        assertTrue(jsynCode.contains("addPort(drive = filterEnv.amplitude, \"Drive\");"), "JSyn must add drive port:\n$jsynCode")
        assertTrue(jsynCode.contains("addPort(release = releasePassThrough.input, \"Release\");"), "JSyn must add release port:\n$jsynCode")
        assertTrue(jsynCode.contains("releasePassThrough.output.connect(filterEnv.release);"), jsynCode)
        assertTrue(jsynCode.contains("releasePassThrough.output.connect(ampEnv.release);"), jsynCode)
        assertTrue(jsynCode.contains("release.setup(0.0, 2.5, 15.0);"), "JSyn must set release range:\n$jsynCode")
        assertFalse(jsynCode.contains(".export(this"), "JSyn voice must not export envelope ports:\n$jsynCode")
    }

    @Test
    fun testFindKSynOnlyUnits() {
        val polyPatch = PolyPatchModule("KSynOnlyVoice")
        polyPatch.graphState.addModule(UnitGeneratorModule("saw", SawtoothOscillatorBL()), androidx.compose.ui.geometry.Offset.Zero)
        assertTrue(JSynSourceExporter.findKSynOnlyUnits(polyPatch).isEmpty())

        polyPatch.graphState.addModule(ChebyshevOscModule(), androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(ChebyshevOscModule(), androidx.compose.ui.geometry.Offset.Zero)
        assertEquals(listOf("ChebyshevOscillator"), JSynSourceExporter.findKSynOnlyUnits(polyPatch))

        val jsynWarning = SourceExporter.exportWarningMessage(polyPatch, forJSyn = true)
        assertTrue(jsynWarning?.contains("ChebyshevOscillator is not available in JSyn. It is only in KSyn.") == true, "$jsynWarning")
        assertEquals(null, SourceExporter.exportWarningMessage(polyPatch, forJSyn = false))
    }

    @Test
    fun testExportStereoPolyPatch() {
        val polyPatch = PolyPatchModule("stereoVoice")
        polyPatch.initializeVoices(2, isStereo = true)

        val osc1 = UnitGeneratorModule("osc1", SawtoothOscillatorBL())
        val osc2 = UnitGeneratorModule("osc2", SawtoothOscillatorBL())
        polyPatch.graphState.addModule(osc1, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.graphState.addModule(osc2, androidx.compose.ui.geometry.Offset.Zero)

        val stereoOut = polyPatch.graphState.modules.filterIsInstance<AudioOutput2Module>().first()
        val in0 = stereoOut.inputs.first { it.name == "in0" }
        val in1 = stereoOut.inputs.first { it.name == "in1" }

        polyPatch.graphState.connect(osc1.outputs.first { it.name == "out" }, in0)
        polyPatch.graphState.connect(osc2.outputs.first { it.name == "out" }, in1)

        val ksynCode = KSynSourceExporter.export(polyPatch, "StereoVoice")
        assertTrue(ksynCode.contains("private val twoInDualOut = TwoInDualOut()"), "KSyn must declare TwoInDualOut:\n$ksynCode")
        assertTrue(ksynCode.contains("add(twoInDualOut)"), "KSyn must add TwoInDualOut:\n$ksynCode")
        assertTrue(ksynCode.contains("osc1.output.connect(twoInDualOut.inputA)"), "KSyn must connect in0 to inputA:\n$ksynCode")
        assertTrue(ksynCode.contains("osc2.output.connect(twoInDualOut.inputB)"), "KSyn must connect in1 to inputB:\n$ksynCode")
        assertTrue(ksynCode.contains("override fun getOutputPort(): UnitOutputPort {\n        return twoInDualOut.output\n    }"), "KSyn getOutputPort must return twoInDualOut.output:\n$ksynCode")
        assertTrue(ksynCode.contains("fun getVoiceDescription(): VoiceDescription"))
        assertTrue(ksynCode.contains("private object MyVoiceDescription : VoiceDescription(\"StereoVoice\", presetNames)"))

        val jsynCode = JSynSourceExporter.export(polyPatch, "StereoVoice")
        assertTrue(jsynCode.contains("private TwoInDualOut twoInDualOut;"), "JSyn must declare TwoInDualOut:\n$jsynCode")
        assertTrue(jsynCode.contains("add(twoInDualOut = new TwoInDualOut());"), "JSyn must add TwoInDualOut:\n$jsynCode")
        assertTrue(jsynCode.contains("osc1.output.connect(twoInDualOut.inputA);"), "JSyn must connect in0 to inputA:\n$jsynCode")
        assertTrue(jsynCode.contains("osc2.output.connect(twoInDualOut.inputB);"), "JSyn must connect in1 to inputB:\n$jsynCode")
        assertTrue(jsynCode.contains("public UnitOutputPort getOutput() {\n        return twoInDualOut.output;\n    }"), "JSyn getOutput must return twoInDualOut.output:\n$jsynCode")
    }

    @Test
    fun testExportStereoNonPolyPatch() {
        val patch = PatchModule("stereoPatch")
        val stereoOut = AudioOutput2Module().apply { name = "stereoOut" }
        val osc1 = UnitGeneratorModule("osc1", SawtoothOscillatorBL())
        val osc2 = UnitGeneratorModule("osc2", SawtoothOscillatorBL())
        patch.graphState.addModule(stereoOut, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.addModule(osc1, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.addModule(osc2, androidx.compose.ui.geometry.Offset.Zero)

        patch.graphState.connect(osc1.outputs.first { it.name == "out" }, stereoOut.inputs.first { it.name == "in0" })
        patch.graphState.connect(osc2.outputs.first { it.name == "out" }, stereoOut.inputs.first { it.name == "in1" })

        val ksynCode = KSynSourceExporter.export(patch, "StereoPatch")
        assertTrue(ksynCode.contains("val output: UnitOutputPort"), "KSyn must declare output port:\n$ksynCode")
        assertTrue(ksynCode.contains("output = twoInDualOut.output"), "KSyn must assign output port:\n$ksynCode")
        assertTrue(ksynCode.contains("addPort(output, \"output\")"), "KSyn must add output port:\n$ksynCode")

        val jsynCode = JSynSourceExporter.export(patch, "StereoPatch")
        assertTrue(jsynCode.contains("public UnitOutputPort output;"), "JSyn must declare output port:\n$jsynCode")
        assertTrue(jsynCode.contains("output = twoInDualOut.output;"), "JSyn must assign output port:\n$jsynCode")
        assertTrue(jsynCode.contains("addPort(output, \"output\");"), "JSyn must add output port:\n$jsynCode")
    }

    @Test
    fun testKSynExportKeepsChebyshevOrder() {
        // The order is picked from a menu, not a jack, so it has to be written out explicitly
        // or the exported oscillator would fall back to its default.
        val patch = PatchModule("cheby")
        val oscMod = ChebyshevOscModule()
        oscMod.order = 5
        val outMod = AudioOutputModule()
        outMod.name = "audioOut"
        patch.graphState.addModule(oscMod, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.addModule(outMod, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.connect(oscMod.outputs.first { it.name == "out" }, outMod.input)

        val code = KSynSourceExporter.export(patch, "ChebyVoice")
        assertTrue(code.contains("private val chebyshevOsc = ChebyshevOscillator()"), code)
        assertTrue(code.contains("chebyshevOsc.order = 5"), code)
        assertTrue(code.contains("chebyshevOsc.drive.setup("), code)
    }

    @Test
    fun testExportAllocatesDelayBuffers() {
        // A delay line is silent until allocated, and allocate() resets the delay port's
        // maximum, so it must be written before the port limits.
        val patch = PatchModule("echo")
        val delayMod = com.syntona.dataflow.registry.ModuleRegistry.findCreator("delay~")!!.invoke()
        val outMod = AudioOutputModule()
        outMod.name = "audioOut"
        patch.graphState.addModule(delayMod, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.addModule(outMod, androidx.compose.ui.geometry.Offset.Zero)
        patch.graphState.connect(delayMod.outputs.first { it.name == "out" }, outMod.input)

        val frames = com.syntona.dataflow.registry.ModuleRegistry.DELAY_BUFFER_FRAMES
        for ((language, code) in listOf(
            "KSyn" to KSynSourceExporter.export(patch, "Echo"),
            "JSyn" to JSynSourceExporter.export(patch, "Echo")
        )) {
            val allocateAt = code.indexOf(".allocate($frames)")
            val limitsAt = code.indexOf("// Port limits and default values")
            assertTrue(allocateAt >= 0, "$language export never allocates the delay:\n$code")
            assertTrue(allocateAt < limitsAt, "$language allocates after the port limits:\n$code")
        }
    }
}
