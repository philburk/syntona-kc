package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Order4ModuleTest {

    /** Records which output a message arrived from, in the order the messages arrive. */
    private class OrderSpy(private val log: MutableList<Pair<String, Message>>) {
        fun jackFor(label: String, owner: Module) = object : InputJack(label, owner) {
            override fun receive(msg: Message) {
                log.add(label to msg)
            }
        }
    }

    @Test
    fun testJacksReadFromOut4DownToOut1() {
        val order = Order4Module()
        assertEquals(
            listOf("out4", "out3", "out2", "out1"),
            order.outputs.map { it.name },
            "Jacks are listed top to bottom, so out1 is the lowest"
        )
    }

    @Test
    fun testMessageGoesOutTheBottomJackFirst() {
        val order = Order4Module()
        val log = mutableListOf<Pair<String, Message>>()
        val spy = OrderSpy(log)
        order.out1.connect(spy.jackFor("out1", order))
        order.out2.connect(spy.jackFor("out2", order))
        order.out3.connect(spy.jackFor("out3", order))
        order.out4.connect(spy.jackFor("out4", order))

        val msg = Message.FloatValue(ScoreTime(100L), 42.0)
        order.input.receive(msg)

        assertEquals(listOf("out1", "out2", "out3", "out4"), log.map { it.first })
        assertTrue(log.all { it.second === msg }, "The same message is passed along unchanged")
    }

    @Test
    fun testVoiceMessagesArePassedAlongToo() {
        val order = Order4Module()
        val log = mutableListOf<Pair<String, Message>>()
        val spy = OrderSpy(log)
        order.out1.connect(spy.jackFor("out1", order))
        order.out4.connect(spy.jackFor("out4", order))

        val note = Message.VoiceMessage(
            ScoreTime(200L),
            Message.VoiceOpcode.NOTE_ON,
            tag = 7,
            value = 60.0,
            extra = 0.8
        )
        order.input.receive(note)

        assertEquals(listOf("out1", "out4"), log.map { it.first })
        log.forEach { (_, msg) ->
            val voice = assertIs<Message.VoiceMessage>(msg)
            assertEquals(Message.VoiceOpcode.NOTE_ON, voice.opcode)
            assertEquals(7, voice.tag)
            assertEquals(60.0, voice.value)
        }
    }

    @Test
    fun testRegisteredInCoreBasicWithDescription() {
        val entry = ModuleRegistry.findEntry("order4")
        assertNotNull(entry, "order4 should be registered")
        assertIs<Order4Module>(entry.creator())
        assertNotNull(ModuleRegistry.moduleDescriptions["order4"], "every module needs a help entry")
        assertEquals("core.basic", ModuleRegistry.getBundlePath("order4")?.substringBeforeLast("."))
    }
}
