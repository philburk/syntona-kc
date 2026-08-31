package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClockDividerModuleTest {

    @Test
    fun testModuleRegistration() {
        val creator = ModuleRegistry.findCreator("clockDiv")
        assertNotNull(creator, "clockDiv should be registered in ModuleRegistry")
        val module = creator()
        assertTrue(module is ClockDividerModule, "Creator should return ClockDividerModule")
        assertEquals("core.basic.clockDiv", ModuleRegistry.getBundlePath("clockDiv"))

        // Legacy alias
        val legacyCreator = ModuleRegistry.findCreator("ckDiv")
        assertNotNull(legacyCreator, "ckDiv should resolve via legacy mapping")
    }

    @Test
    fun testDivideByTwo() {
        val ckDiv = ClockDividerModule()
        val receivedValues = mutableListOf<Double>()

        val receiver = object : Module("receiver") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedValues.add(triggerMessage.value)
                }
            }
        }

        ckDiv.output.connect(receiver.inJack)

        // Feed clock ticks: 0, 1, 2, 3, 4, 5
        for (tick in 0..5) {
            ckDiv.input.receive(Message.FloatValue(ScoreTime(tick.toLong()), tick.toDouble()))
        }

        // Expected outputs: 0 / 2 = 0.0, 2 / 2 = 1.0, 4 / 2 = 2.0
        assertEquals(listOf(0.0, 1.0, 2.0), receivedValues)
    }

    @Test
    fun testDivideByFour() {
        val ckDiv = ClockDividerModule()
        ckDiv.divisor.setValue(4.0)

        val receivedValues = mutableListOf<Double>()
        val receiver = object : Module("receiver") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedValues.add(triggerMessage.value)
                }
            }
        }

        ckDiv.output.connect(receiver.inJack)

        for (tick in 0..12) {
            ckDiv.input.receive(Message.FloatValue(ScoreTime(tick.toLong()), tick.toDouble()))
        }

        // Expected outputs: 0 -> 0.0, 4 -> 1.0, 8 -> 2.0, 12 -> 3.0
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), receivedValues)
    }

    @Test
    fun testDynamicDivisorChange() {
        val ckDiv = ClockDividerModule()
        val receivedValues = mutableListOf<Double>()

        val receiver = object : Module("receiver") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedValues.add(triggerMessage.value)
                }
            }
        }

        ckDiv.output.connect(receiver.inJack)

        // Ticks 0, 1, 2 with N = 2
        ckDiv.input.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        ckDiv.input.receive(Message.FloatValue(ScoreTime(1), 1.0))
        ckDiv.input.receive(Message.FloatValue(ScoreTime(2), 2.0))

        assertEquals(listOf(0.0, 1.0), receivedValues)

        // Change N to 3
        ckDiv.divisor.receive(Message.FloatValue(ScoreTime(3), 3.0))

        ckDiv.input.receive(Message.FloatValue(ScoreTime(3), 3.0))
        ckDiv.input.receive(Message.FloatValue(ScoreTime(4), 4.0))
        ckDiv.input.receive(Message.FloatValue(ScoreTime(5), 5.0))
        ckDiv.input.receive(Message.FloatValue(ScoreTime(6), 6.0))

        // 3 / 3 = 1.0, 6 / 3 = 2.0
        assertEquals(listOf(0.0, 1.0, 1.0, 2.0), receivedValues)
    }

    @Test
    fun testDivisorJackProperties() {
        val ckDiv = ClockDividerModule()
        assertEquals("divisor", ckDiv.divisor.name)
        assertEquals("divisor", ckDiv.n.name)
        assertTrue(ckDiv.divisor.isInteger)
        assertTrue(ckDiv.divisor is com.syntona.dataflow.model.IntegerInputJack)
        assertEquals(1.0, ckDiv.divisor.min)
        assertEquals(128.0, ckDiv.divisor.max)
        assertEquals(2.0, ckDiv.divisor.getValue())
    }

    @Test
    fun testLegacyNJackDeserializationAndConnection() {
        val legacyJson = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "core.basic.clockDiv",
                  "name": "clockDiv",
                  "jacks": {
                    "N": {
                      "v": 8.0,
                      "min": 1.0,
                      "max": 128.0
                    }
                  }
                },
                {
                  "id": 1,
                  "type": "core.input.number",
                  "name": "number"
                }
              ],
              "connections": [
                {
                  "from": 1,
                  "fromJack": "out",
                  "to": 0,
                  "toJack": "N"
                }
              ]
            }
        """.trimIndent()

        val graphState = com.syntona.dataflow.editor.GraphState()
        val deserialized = com.syntona.dataflow.editor.GraphSerializer.deserialize(graphState, legacyJson)
        assertEquals(2, deserialized.size)
        val ckDiv = deserialized[0] as ClockDividerModule
        assertEquals("divisor", ckDiv.divisor.name)
        assertEquals(8.0, ckDiv.divisor.getValue())
        assertTrue(ckDiv.divisor.isInteger)
        assertTrue(graphState.connections.any { it.input === ckDiv.divisor }, "Connection to legacy 'N' should connect to 'divisor'")
    }
}

