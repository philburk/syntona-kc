package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArithmeticMathTest {

    @Test
    fun testPitchToFrequencyExecution() {
        val p2f = PitchToFrequencyModule()
        var resultFreq = 0.0

        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    resultFreq = triggerMessage.value
                }
            }
        }

        val graph = GraphState()
        graph.addModule(p2f, androidx.compose.ui.geometry.Offset.Zero)
        graph.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graph.connect(p2f.output, receiver.inJack)

        // Concert A (69.0) should convert to 440.0 Hz
        p2f.input.receive(Message.FloatValue(ScoreTime.Zero, 69.0))
        assertEquals(440.0, resultFreq, 1e-4)

        // Middle C (60.0) should convert to ~261.6255 Hz
        p2f.input.receive(Message.FloatValue(ScoreTime.Zero, 60.0))
        assertTrue(abs(resultFreq - 261.6255) < 0.01, "Middle C should convert to ~261.63 Hz but was $resultFreq")

        // Concert A - 1 octave (57.0) should convert to 220.0 Hz
        p2f.input.receive(Message.FloatValue(ScoreTime.Zero, 57.0))
        assertEquals(220.0, resultFreq, 1e-4)
    }

    @Test
    fun testModuleRegistryRegistration() {
        // core.math.pitchToHz should exist
        val p2fCreator = ModuleRegistry.findCreator("pitchToHz")
        assertNotNull(p2fCreator, "core.math.pitchToHz should be registered")
        val p2fMod = p2fCreator()
        assertTrue(p2fMod is PitchToFrequencyModule, "pitchToHz should be an instance of PitchToFrequencyModule")

        // Legacy p2f should also resolve
        val legacyCreator = ModuleRegistry.findCreator("p2f")
        assertNotNull(legacyCreator, "legacy p2f should be resolved")

        // ksyn.math.pitchToHz~ should exist
        val ap2fCreator = ModuleRegistry.findCreator("pitchToHz~")
        assertNotNull(ap2fCreator, "ksyn.math.pitchToHz~ should be registered")
        val ap2fMod = ap2fCreator()
        assertTrue(ap2fMod is UnitGeneratorModule, "pitchToHz~ should be a UnitGeneratorModule")
        assertTrue(ap2fMod.unit is com.softsynth.ksyn.unitgen.PitchToFrequency)

        // Legacy ap2f should also resolve
        val legacyAp2fCreator = ModuleRegistry.findCreator("ap2f")
        assertNotNull(legacyAp2fCreator, "legacy ap2f should be resolved")

        // ksyn.control.p2f should NOT be a UnitGeneratorModule registered under ksyn.control
        val root = ModuleRegistry.root
        val ksynBundle = root.subBundles.find { it.name == "ksyn" }
        val controlBundle = ksynBundle?.subBundles?.find { it.name == "control" }
        assertNull(controlBundle?.modules?.find { it.name == "p2f" }, "p2f should no longer be in ksyn.control")

        val mathBundle = ksynBundle?.subBundles?.find { it.name == "math" }
        assertNotNull(mathBundle?.modules?.find { it.name == "pitchToHz~" }, "pitchToHz~ should be in ksyn.math")
    }
}
