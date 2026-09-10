package com.syntona.dataflow.modules

import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplePlayerModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = SamplePlayerModule()
        assertEquals("samplePlayer", module.name)

        assertEquals(6, module.inputs.size)
        assertEquals("on", module.inputs[0].name)
        assertTrue(module.inputs[0] is CommandJack)
        assertEquals("off", module.inputs[1].name)
        assertTrue(module.inputs[1] is CommandJack)
        assertEquals("stop", module.inputs[2].name)
        assertTrue(module.inputs[2] is CommandJack)
        assertEquals("looping", module.inputs[3].name)
        assertTrue(module.inputs[3] is com.syntona.dataflow.model.BooleanInputJack)
        assertEquals("speed", module.inputs[4].name)
        assertTrue(module.inputs[4] is AudioInputJack)
        assertEquals("amplitude", module.inputs[5].name)
        assertTrue(module.inputs[5] is AudioInputJack)

        assertEquals(1, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
        assertTrue(module.outputs[0] is AudioOutputJack)

        module.teardown()
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("samplePlayer")
        assertNotNull(creator, "samplePlayer should be registered")
        val module = creator()
        assertTrue(module is SamplePlayerModule)
        module.teardown()
    }

    @Test
    fun testCommandsDoNotCrash() {
        val module = SamplePlayerModule()
        val now = ScoreTime.Zero

        module.loopInput.isChecked = true
        module.onIn.receive(Message.FloatValue(now, 1.0))
        module.offIn.receive(Message.FloatValue(now, 0.0))
        module.stopIn.receive(Message.FloatValue(now, 0.0))

        module.teardown()
    }

    @Test
    fun testFileNameExtraction() {
        val module = SamplePlayerModule()
        assertNull(module.fileName)

        module.filePath = ""
        assertNull(module.fileName)

        module.filePath = "   "
        assertNull(module.fileName)

        module.filePath = "/Users/phil/Music/snare.wav"
        assertEquals("snare.wav", module.fileName)

        module.filePath = "C:\\Sounds\\Samples\\kick.wav"
        assertEquals("kick.wav", module.fileName)

        module.filePath = "samples/cymbal.wav"
        assertEquals("cymbal.wav", module.fileName)

        module.filePath = "tom.wav"
        assertEquals("tom.wav", module.fileName)

        module.teardown()
    }
}
