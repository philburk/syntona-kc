package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.UniversalInputJack
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimeModulesTest {

    @Test
    fun testGrabTimeJacks() {
        val module = GrabTimeModule()
        assertEquals("grabTime", module.name)
        assertEquals(1, module.inputs.size)
        assertEquals("in", module.inputs[0].name)
        assertTrue(module.inputs[0] is UniversalInputJack)
        assertTrue(module.inputs[0].isKickable)

        assertEquals(1, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
    }

    @Test
    fun testGrabTimeExtractsTimestampFromFloatMessage() {
        val module = GrabTimeModule()
        var receivedMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                receivedMessage = msg
            }
        }
        module.output.connect(receiver)

        val testTime = ScoreTime.fromSeconds(4.25)
        module.input.receive(Message.FloatValue(testTime, 100.0))

        assertNotNull(receivedMessage)
        assertTrue(receivedMessage is Message.FloatValue)
        val floatMsg = receivedMessage as Message.FloatValue
        assertEquals(testTime, floatMsg.time)
        assertEquals(4.25, floatMsg.value)
    }

    @Test
    fun testGrabTimeExtractsTimestampFromTextMessage() {
        val module = GrabTimeModule()
        var receivedMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                receivedMessage = msg
            }
        }
        module.output.connect(receiver)

        val testTime = ScoreTime.fromSeconds(12.75)
        module.input.receive(Message.TextValue(testTime, "sampleText"))

        assertNotNull(receivedMessage)
        assertTrue(receivedMessage is Message.FloatValue)
        val floatMsg = receivedMessage as Message.FloatValue
        assertEquals(testTime, floatMsg.time)
        assertEquals(12.75, floatMsg.value)
    }

    @Test
    fun testGrabTimeExtractsTimestampFromVoiceMessage() {
        val module = GrabTimeModule()
        var receivedMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                receivedMessage = msg
            }
        }
        module.output.connect(receiver)

        val testTime = ScoreTime.fromSeconds(0.5)
        module.input.receive(
            Message.VoiceMessage(
                time = testTime,
                opcode = Message.VoiceOpcode.NOTE_ON,
                tag = 60,
                value = 60.0,
                extra = 0.8
            )
        )

        assertNotNull(receivedMessage)
        assertTrue(receivedMessage is Message.FloatValue)
        val floatMsg = receivedMessage as Message.FloatValue
        assertEquals(testTime, floatMsg.time)
        assertEquals(0.5, floatMsg.value)
    }

    @Test
    fun testTimeNowJacks() {
        val module = TimeNowModule()
        assertEquals("timeNow", module.name)
        assertEquals(1, module.inputs.size)
        assertEquals("kick", module.inputs[0].name)
        assertTrue(module.inputs[0].isKickable)

        assertEquals(1, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
    }

    @Test
    fun testTimeNowOutputsCurrentTimeOnKick() {
        val module = TimeNowModule()
        var receivedMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                receivedMessage = msg
            }
        }
        module.output.connect(receiver)

        val beforeTime = ScoreClock.now()
        module.kick.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        val afterTime = ScoreClock.now()

        assertNotNull(receivedMessage)
        assertTrue(receivedMessage is Message.FloatValue)
        val floatMsg = receivedMessage as Message.FloatValue
        assertTrue(floatMsg.time >= beforeTime, "Output timestamp should be >= beforeTime")
        assertTrue(floatMsg.time <= afterTime, "Output timestamp should be <= afterTime")
        assertEquals(floatMsg.time.seconds, floatMsg.value, "Output value should equal time in seconds")
    }

    @Test
    fun testRegistryLookup() {
        val grabTimeCreator = ModuleRegistry.findCreator("grabTime")
        assertNotNull(grabTimeCreator)
        assertTrue(grabTimeCreator() is GrabTimeModule)

        val timeNowCreator = ModuleRegistry.findCreator("timeNow")
        assertNotNull(timeNowCreator)
        assertTrue(timeNowCreator() is TimeNowModule)
    }
}
