package com.syntona.dataflow.modules

import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.DataQueueOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FloatSampleModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = FloatSampleModule()
        assertEquals("sample", module.name)

        assertEquals(4, module.inputs.size)
        assertEquals("queueOn", module.inputs[0].name)
        assertEquals("queueOff", module.inputs[1].name)
        assertEquals("queue", module.inputs[2].name)
        assertEquals("queueLoop", module.inputs[3].name)

        assertEquals(1, module.outputs.size)
        assertEquals("output", module.outputs[0].name)
        assertTrue(module.outputs[0] is DataQueueOutputJack)
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
    fun testQueueInputsSendSampleWithMatchingOpcode() {
        val module = FloatSampleModule()
        val received = mutableListOf<Message.DataQueueMessage>()

        val collector = object : Module("collector") {
            val sink = addInput("sink")
            override fun onMessageReceived(jack: InputJack, msg: Message) {
                if (msg is Message.DataQueueMessage) received.add(msg)
            }
            override fun execute(triggerMessage: Message) {}
        }
        module.outputs[0].connect(collector.sink)

        // Kicking each command input in declared order should emit the matching opcode.
        module.inputs.forEach { it.receive(Message.FloatValue(ScoreTime.Zero, 0.0)) }

        assertEquals(
            listOf(
                Message.DataQueueOpcode.QUEUE_ON,
                Message.DataQueueOpcode.QUEUE_OFF,
                Message.DataQueueOpcode.QUEUE,
                Message.DataQueueOpcode.QUEUE_LOOP
            ),
            received.map { it.opcode }
        )
        received.forEach { assertTrue(it.data === module.sample, "should queue this module's own sample") }
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("sample")
        assertNotNull(creator, "sample should be registered")
        assertTrue(creator() is FloatSampleModule)
    }
}
