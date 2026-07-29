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
        assertEquals(8.0, stepsPerCycleInput.getValue())

        val densityInput = module.inputs.find { it.name == "density" }
        assertNotNull(densityInput)
        assertEquals(0.0, densityInput.min)
        assertEquals(1.0, densityInput.max)
        assertEquals(0.5, densityInput.getValue())

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
}
