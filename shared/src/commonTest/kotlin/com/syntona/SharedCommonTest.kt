package com.syntona

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.ModModule
import com.syntona.core.model.ScoreTime

class SharedCommonTest {

    @Test
    fun testModModule() {
        val graphState = GraphState()
        val mod = ModModule()
        graphState.addModule(mod, androidx.compose.ui.geometry.Offset.Zero)

        var messageReceived: Message? = null
        val receiver = object : Module("testReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                messageReceived = triggerMessage
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(mod.output, receiver.input)

        // Test 5 % 3 = 2
        mod.inputA.setValue(5.0)
        mod.inputB.setValue(3.0)
        mod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 5.0))

        assertNotNull(messageReceived)
        assertEquals(2.0, (messageReceived as Message.FloatValue).value)

        // Test 5.5 % 2 = 1.5
        messageReceived = null
        mod.inputA.setValue(5.5)
        mod.inputB.setValue(2.0)
        mod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 5.5))

        assertNotNull(messageReceived)
        assertEquals(1.5, (messageReceived as Message.FloatValue).value)
    }
}
