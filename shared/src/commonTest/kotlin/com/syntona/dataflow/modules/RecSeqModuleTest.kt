package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecSeqModuleTest {

    @Test
    fun testModuleCreationAndJacks() {
        val module = RecSeqModule()
        assertEquals("recSeq", module.name)

        val stepInput = module.inputs.find { it.name == "step" }
        assertNotNull(stepInput)
        assertTrue(stepInput.isKickable)

        val stepsPerCycleInput = module.inputs.find { it.name == "stepsPerCycle" }
        assertNotNull(stepsPerCycleInput)
        assertEquals(2.0, stepsPerCycleInput.min)
        assertEquals(32.0, stepsPerCycleInput.max)
        assertTrue(stepsPerCycleInput.getDoubleValue() == 8.0, "stepsPerCycle expected 8.0 but got ${stepsPerCycleInput.getDoubleValue()}")

        val densityInput = module.inputs.find { it.name == "density" }
        assertNotNull(densityInput)
        assertEquals(0.0, densityInput.min)
        assertEquals(1.0, densityInput.max)
        assertTrue(densityInput.getDoubleValue() == 0.5, "density expected 0.5 but got ${densityInput.getDoubleValue()}")

        val outputJack = module.outputs.find { it.name == "output" }
        assertNotNull(outputJack)
    }

    @Test
    fun testModuleRegistryRegistration() {
        val creatorByShortName = ModuleRegistry.findCreator("recSeq")
        assertNotNull(creatorByShortName)
        val moduleFromShortName = creatorByShortName()
        assertTrue(moduleFromShortName is RecSeqModule)

        val creatorByPath = ModuleRegistry.findCreator("music.compose.recSeq")
        assertNotNull(creatorByPath)
        val moduleFromPath = creatorByPath()
        assertTrue(moduleFromPath is RecSeqModule)
    }

    @Test
    fun testExecutionOutputsValue() {
        val module = RecSeqModule()
        val stepInput = module.inputs.first { it.name == "step" }
        val outputJack = module.outputs.first { it.name == "output" }

        var receivedMessage: Message? = null
        val receiver = object : InputJack("testRecv", module) {
            override fun receive(msg: Message) {
                receivedMessage = msg
                super.receive(msg)
            }
        }
        outputJack.connect(receiver)

        stepInput.receive(Message.FloatValue(ScoreTime(100), 0.0))

        assertNotNull(receivedMessage)
        assertTrue(receivedMessage is Message.FloatValue)
        val floatVal = (receivedMessage as Message.FloatValue).value
        // RecursiveSequence next() returns an Int (e.g. note value or -1)
        assertTrue(floatVal == floatVal.toInt().toDouble())
    }

    @Test
    fun testZeroDensitySendsNoMessagesOrRests() {
        val module = RecSeqModule()
        val stepInput = module.inputs.first { it.name == "step" }
        val densityInput = module.inputs.first { it.name == "density" }
        val outputJack = module.outputs.first { it.name == "output" }

        densityInput.setValue(0.0)

        val receivedMessages = mutableListOf<Message>()
        val receiver = object : InputJack("testRecv", module) {
            override fun receive(msg: Message) {
                receivedMessages.add(msg)
                super.receive(msg)
            }
        }
        outputJack.connect(receiver)

        // Trigger steps 0 through 32 with density set to 0.0
        for (s in 0..32) {
            stepInput.receive(Message.FloatValue(ScoreTime(s.toLong() * 100), s.toDouble()))
        }

        // Verify no -1 values (or any rest messages) were sent
        for (msg in receivedMessages) {
            assertTrue(msg is Message.FloatValue)
            val valDouble = (msg as Message.FloatValue).value
            assertTrue(valDouble >= 0.0, "No negative/rest values (such as -1) should be emitted. Got: $valDouble")
        }
        assertEquals(0, receivedMessages.size, "Density 0.0 should produce rests for all steps, so no messages should be sent")
    }
}
