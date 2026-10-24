package com.syntona.dataflow.modules

import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.SequentialDataOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloatSampleModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = FloatSampleModule()
        assertEquals("sample", module.name)

        assertEquals(1, module.inputs.size)
        assertEquals("send", module.inputs[0].name)
        assertTrue(module.inputs[0] is CommandJack)

        assertEquals(2, module.outputs.size)
        assertEquals("data", module.outputs[0].name)
        assertTrue(module.outputs[0] is SequentialDataOutputJack)
        assertEquals("durationMs", module.outputs[1].name)
    }

    @Test
    fun testSampleIsMonoAndHoldsEightSeconds() {
        val module = FloatSampleModule()
        assertEquals(1, module.sample.channelsPerFrame)
        assertEquals(SynthKSyn.synth.frameRate * 8, module.sample.numFrames)
        // Mono, so the backing buffer has exactly one value per frame.
        assertEquals(module.sample.numFrames, module.sample.buffer.size)
    }

    @Test
    fun testSampleFrameRateMatchesAllocation() {
        val module = FloatSampleModule()
        assertEquals(SynthKSyn.synth.frameRate.toDouble(), module.sample.frameRate)
    }

    @Test
    fun testSendJackEmitsSampleReference() {
        val module = FloatSampleModule()
        val received = mutableListOf<Message.SequentialDataMessage>()

        val collector = object : Module("collector") {
            val sink = addInput("sink")
            override fun onMessageReceived(jack: InputJack, msg: Message) {
                if (msg is Message.SequentialDataMessage) received.add(msg)
            }
            override fun execute(triggerMessage: Message) {}
        }
        module.dataOutput.connect(collector.sink)

        // Trigger send input
        module.sendIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        assertEquals(1, received.size)
        assertTrue(received[0].data === module.sample, "should emit this module's own sample")
    }

    @Test
    fun testSendJackEmitsDurationMs() {
        val module = FloatSampleModule()
        var receivedDuration: Double? = null

        val collector = object : Module("collector") {
            val sink = addInput("sink")
            override fun onMessageReceived(jack: InputJack, msg: Message) {
                if (msg is Message.FloatValue) receivedDuration = msg.value
            }
            override fun execute(triggerMessage: Message) {}
        }
        module.durationOutput.connect(collector.sink)

        // Trigger send input
        module.sendIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        assertEquals(8000.0, receivedDuration)
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("sample")
        assertNotNull(creator, "sample should be registered")
        assertTrue(creator() is FloatSampleModule)
    }

    @Test
    fun testFileNameExtraction() {
        val module = FloatSampleModule()
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
    }
}
