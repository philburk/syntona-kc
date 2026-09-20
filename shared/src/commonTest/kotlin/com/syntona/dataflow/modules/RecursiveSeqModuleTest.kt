package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecursiveSeqModuleTest {

    @Test
    fun testModuleCreationAndJacks() {
        val module = RecursiveSeqModule()
        assertEquals("recursiveSeq", module.name)

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

        val outputJack = module.outputs.find { it.name == "out" }
        assertNotNull(outputJack)
    }

    @Test
    fun testModuleRegistryRegistration() {
        val creatorByShortName = ModuleRegistry.findCreator("recursiveSeq")
        assertNotNull(creatorByShortName)
        val moduleFromShortName = creatorByShortName()
        assertTrue(moduleFromShortName is RecursiveSeqModule)

        val creatorByPath = ModuleRegistry.findCreator("music.compose.recursiveSeq")
        assertNotNull(creatorByPath)
        val moduleFromPath = creatorByPath()
        assertTrue(moduleFromPath is RecursiveSeqModule)

        // Verify legacy recSeq alias
        val legacyCreator = ModuleRegistry.findCreator("recSeq")
        assertNotNull(legacyCreator)
        val legacyMod = legacyCreator()
        assertTrue(legacyMod is RecursiveSeqModule)
    }

    @Test
    fun testExecutionOutputsValue() {
        val module = RecursiveSeqModule()
        val stepInput = module.inputs.first { it.name == "step" }
        val densityInput = module.inputs.first { it.name == "density" }
        val outputJack = module.outputs.first { it.name == "out" }

        densityInput.setValue(1.0)

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
        val module = RecursiveSeqModule()
        val stepInput = module.inputs.first { it.name == "step" }
        val densityInput = module.inputs.first { it.name == "density" }
        val outputJack = module.outputs.first { it.name == "out" }

        densityInput.setValue(0.0)

        val receivedMessages = mutableListOf<Message>()
        val receiver = object : InputJack("testRecv", module) {
            override fun receive(msg: Message) {
                receivedMessages.add(msg)
                super.receive(msg)
            }
        }
        outputJack.connect(receiver)

        // Trigger steps with density set to 0.0
        for (s in 0..64) {
            stepInput.receive(Message.FloatValue(ScoreTime(s.toLong() * 100), s.toDouble()))
        }

        // 1) Verify no -1 values (or any negative rest values) were EVER sent
        for (msg in receivedMessages) {
            assertTrue(msg is Message.FloatValue)
            val valDouble = msg.value
            assertTrue(valDouble >= 0.0, "No negative/rest values (such as -1) should be emitted. Got: $valDouble")
        }

        // 2) Once settled at density 0.0, no further messages should be sent
        receivedMessages.clear()
        for (s in 65..128) {
            stepInput.receive(Message.FloatValue(ScoreTime(s.toLong() * 100), s.toDouble()))
        }
        assertEquals(0, receivedMessages.size, "With density 0.0, rests should be produced for all steps and no messages sent")
    }
}
