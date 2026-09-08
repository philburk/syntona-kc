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

    @Test
    fun testMinModuleExecution() {
        val minMod = MinModule()
        var received = 0.0

        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    received = triggerMessage.value
                }
            }
        }

        val graph = GraphState()
        graph.addModule(minMod, androidx.compose.ui.geometry.Offset.Zero)
        graph.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graph.connect(minMod.output, receiver.inJack)

        // Set B = 5.0, send A = 3.0 -> min is 3.0
        minMod.inputB.setValue(5.0)
        minMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 3.0))
        assertEquals(3.0, received, 1e-6)

        // Set B = 5.0, send A = 8.0 -> min is 5.0
        minMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 8.0))
        assertEquals(5.0, received, 1e-6)

        // Negative numbers: B = -2.0, send A = -10.0 -> min is -10.0
        minMod.inputB.setValue(-2.0)
        minMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, -10.0))
        assertEquals(-10.0, received, 1e-6)
    }

    @Test
    fun testMaxModuleExecution() {
        val maxMod = MaxModule()
        var received = 0.0

        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    received = triggerMessage.value
                }
            }
        }

        val graph = GraphState()
        graph.addModule(maxMod, androidx.compose.ui.geometry.Offset.Zero)
        graph.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graph.connect(maxMod.output, receiver.inJack)

        // Set B = 5.0, send A = 3.0 -> max is 5.0
        maxMod.inputB.setValue(5.0)
        maxMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 3.0))
        assertEquals(5.0, received, 1e-6)

        // Set B = 5.0, send A = 8.0 -> max is 8.0
        maxMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 8.0))
        assertEquals(8.0, received, 1e-6)

        // Negative numbers: B = -2.0, send A = -10.0 -> max is -2.0
        maxMod.inputB.setValue(-2.0)
        maxMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, -10.0))
        assertEquals(-2.0, received, 1e-6)
    }

    @Test
    fun testAbsModuleExecution() {
        val absMod = AbsModule()
        var received = 0.0

        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    received = triggerMessage.value
                }
            }
        }

        val graph = GraphState()
        graph.addModule(absMod, androidx.compose.ui.geometry.Offset.Zero)
        graph.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graph.connect(absMod.output, receiver.inJack)

        absMod.input.receive(Message.FloatValue(ScoreTime.Zero, -7.5))
        assertEquals(7.5, received, 1e-6)

        absMod.input.receive(Message.FloatValue(ScoreTime.Zero, 4.2))
        assertEquals(4.2, received, 1e-6)

        absMod.input.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(0.0, received, 1e-6)
    }

    @Test
    fun testMinMaxAbsRegistryResolution() {
        // Short names
        val minCreator = ModuleRegistry.findCreator("min")
        assertNotNull(minCreator)
        assertTrue(minCreator() is MinModule)

        val maxCreator = ModuleRegistry.findCreator("max")
        assertNotNull(maxCreator)
        assertTrue(maxCreator() is MaxModule)

        val absCreator = ModuleRegistry.findCreator("abs")
        assertNotNull(absCreator)
        assertTrue(absCreator() is AbsModule)

        // Exact path
        assertEquals("core.math.min", ModuleRegistry.getBundlePath("min"))
        assertEquals("core.math.max", ModuleRegistry.getBundlePath("max"))
        assertEquals("core.math.abs", ModuleRegistry.getBundlePath("abs"))

        assertTrue(ModuleRegistry.findCreator("core.math.min")?.invoke() is MinModule)
        assertTrue(ModuleRegistry.findCreator("core.math.max")?.invoke() is MaxModule)
        assertTrue(ModuleRegistry.findCreator("core.math.abs")?.invoke() is AbsModule)

        // Legacy / alias basic.math prefix
        assertTrue(ModuleRegistry.findCreator("basic.math.min")?.invoke() is MinModule)
        assertTrue(ModuleRegistry.findCreator("basic.math.max")?.invoke() is MaxModule)
        assertTrue(ModuleRegistry.findCreator("basic.math.abs")?.invoke() is AbsModule)
    }
}
