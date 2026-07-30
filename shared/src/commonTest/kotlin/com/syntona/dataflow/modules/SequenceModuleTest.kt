package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SequenceModuleTest {

    @Test
    fun testResetDoesNotSendMessageAndResetsImmediately() {
        val seq = SequenceModule(8)

        var stepReceived = false
        var allReceived = false

        val receiverStep = object : Module("stepRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                stepReceived = true
            }
        }

        val receiverAll = object : Module("allRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                allReceived = true
            }
        }

        seq.stepOutputs[0].connect(receiverStep.inJack)
        seq.allOutput.connect(receiverAll.inJack)

        // Advance count to 3
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(3, seq.count)

        stepReceived = false
        allReceived = false

        // Kick reset
        seq.reset.receive(Message.FloatValue(ScoreTime.Zero, 1.0))

        assertEquals(0, seq.count, "count should be reset to 0 immediately")
        assertTrue(seq.stepOutputs[0].isHighlighted, "step 0 should be highlighted on reset")
        assertFalse(seq.stepOutputs[3].isHighlighted, "step 3 should no longer be highlighted")
        assertFalse(stepReceived, "reset should not cause step output message to be sent")
        assertFalse(allReceived, "reset should not cause 'all' output message to be sent")
    }

    @Test
    fun testAllOutputJackSendsAllStepMessages() {
        val seq = SequenceModule(8)
        val receivedAllValues = mutableListOf<Double>()

        val receiver = object : Module("allRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedAllValues.add(triggerMessage.value)
                }
            }
        }
        seq.allOutput.connect(receiver.inJack)

        seq.values[0] = 10.0
        seq.values[1] = 20.0
        seq.values[2] = 30.0

        // Increment 3 times
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))

        assertEquals(listOf(20.0, 30.0, 1.0), receivedAllValues)
    }

    @Test
    fun testSeq16ModuleRegistered() {
        val creator = ModuleRegistry.findCreator("seq16")
        assertNotNull(creator, "seq16 should be registered in ModuleRegistry")
        val module = creator()
        assertTrue(module is SequenceModule)
        assertEquals(16, module.numOutputs)
        assertEquals("all", module.allOutput.name)
    }
}
