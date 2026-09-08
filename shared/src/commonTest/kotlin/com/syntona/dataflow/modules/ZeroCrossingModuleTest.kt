package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ZeroCrossingModuleTest {

    @Test
    fun testModuleCreationAndJacks() {
        val module = ZeroCrossingModule()
        assertEquals("zeroCrossing", module.name)
        assertFalse(module.isAllowedInPolyPatch, "zeroCrossing module should not be in polyPatch")

        assertEquals(4, module.inputs.size)
        assertEquals("in", module.inputs[0].name)
        assertTrue(module.inputs[0] is AudioInputJack)

        assertEquals("hysteresis", module.inputs[1].name)
        assertTrue(module.inputs[1] is AudioInputJack)
        assertEquals(0.0, module.hysteresis.min)
        assertEquals(1.0, module.hysteresis.max)
        assertEquals(0.01, module.hysteresis.getDoubleValue())

        assertEquals("holdOffMs", module.inputs[2].name)
        assertTrue(module.inputs[2] is AudioInputJack)
        assertEquals(10.0, module.holdOffMs.min)
        assertEquals(1000.0, module.holdOffMs.max)
        assertEquals(40.0, module.holdOffMs.getDoubleValue())

        assertEquals("reset", module.inputs[3].name)
        assertTrue(module.inputs[3] is CommandJack)

        assertEquals(2, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
        assertTrue(module.outputs[0] is AudioOutputJack)

        assertEquals("trigger", module.outputs[1].name)
        assertTrue(module.outputs[1] is OutputJack)

        module.teardown()
    }

    @Test
    fun testModuleRegistryResolution() {
        val entry = ModuleRegistry.findEntry("zeroCrossing")
        assertNotNull(entry, "zeroCrossing must be registered in ModuleRegistry")
        val created = entry.creator()
        assertTrue(created is ZeroCrossingModule)
        created.teardown()
    }

    @Test
    fun testResetCommand() {
        val module = ZeroCrossingModule()
        // Simulate a count on the detector
        val block = floatArrayOf(-0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val inSamples = module.detector.input.getValues()
        for (i in block.indices) inSamples[i] = block[i]
        module.detector.generate()
        assertEquals(1, module.detector.count)

        // Kick reset
        module.resetIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(0, module.detector.count)

        module.teardown()
    }
}
