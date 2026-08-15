package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowManyModuleTest {

    @Test
    fun testShowManyAppendsAndCapsMessages() {
        val module = ShowManyModule()
        assertEquals(0, module.messages.size)

        // Send 10 messages
        for (i in 0 until 10) {
            module.input.receive(Message.FloatValue(ScoreTime.Zero, i.toDouble()))
        }
        assertEquals(10, module.messages.size)

        // Flood with 100 messages
        for (i in 10 until 110) {
            module.input.receive(
                Message.VoiceMessage(
                    ScoreTime.Zero,
                    Message.VoiceOpcode.PITCH_BEND,
                    tag = -1,
                    value = i.toDouble(),
                    extra = 0.0
                )
            )
        }

        // Must never exceed capacity (30)
        assertEquals(module.capacity, module.messages.size)
        assertTrue(module.messages.last().contains("v=109"))
    }
}
