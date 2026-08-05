package com.syntona.dataflow.editor

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.modules.MultiplyModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JackKickableTest {

    @Test
    fun testKickablePropertyToggling() {
        val multiply = MultiplyModule()
        assertTrue(multiply.inputA.isKickable)
        assertFalse(multiply.inputB.isKickable)

        multiply.inputA.setValue(5.0)
        
        // Sending value to non-kickable inputB should not execute module
        var lastOutputValue = 0.0
        multiply.output.connect(com.syntona.dataflow.model.InputJack("test", multiply))
        multiply.inputB.receive(Message.FloatValue(ScoreTime.Zero, 10.0))

        // Set inputB to be kickable
        multiply.inputB.isKickable = true
        assertTrue(multiply.inputB.isKickable)

        // Sending value to kickable inputB should trigger module execution (5 * 10 = 50)
        multiply.inputB.receive(Message.FloatValue(ScoreTime.Zero, 10.0))
        assertEquals(50.0, multiply.output.getValue())
    }

    @Test
    fun testKickableSerializationAndDeserialization() {
        val graphState = GraphState()
        val multiply = MultiplyModule()
        graphState.addModule(multiply, androidx.compose.ui.geometry.Offset.Zero)

        // Change kickable state of inputB
        multiply.inputB.isKickable = true

        val json = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(json.contains("\"kickable\":true") || json.contains("\"kickable\": true"))

        val newGraphState = GraphState()
        val deserializedModules = GraphSerializer.deserialize(newGraphState, json)
        assertEquals(1, deserializedModules.size)

        val deserializedMultiply = deserializedModules[0] as MultiplyModule
        assertTrue(deserializedMultiply.inputB.isKickable)
    }
}
