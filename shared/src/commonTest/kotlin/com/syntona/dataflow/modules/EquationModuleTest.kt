package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.core.model.ScoreTime
import com.syntona.core.model.ScoreClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EquationModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        // f(x)
        val fx = EquationModule("f(x)")
        assertEquals("f(x)", fx.name)
        assertNotNull(fx.inputX)
        assertTrue(fx.inputX.isKickable)
        assertEquals(null, fx.inputY)
        assertEquals(null, fx.inputZ)
        assertNotNull(fx.output)

        // f(x,y)
        val fxy = EquationModule("f(x,y)")
        assertEquals("f(x,y)", fxy.name)
        assertNotNull(fxy.inputX)
        assertTrue(fxy.inputX.isKickable)
        assertNotNull(fxy.inputY)
        assertEquals(false, fxy.inputY!!.isKickable)
        assertEquals(null, fxy.inputZ)
        assertNotNull(fxy.output)

        // f(x,y,z)
        val fxyz = EquationModule("f(x,y,z)")
        assertEquals("f(x,y,z)", fxyz.name)
        assertNotNull(fxyz.inputX)
        assertTrue(fxyz.inputX.isKickable)
        assertNotNull(fxyz.inputY)
        assertEquals(false, fxyz.inputY!!.isKickable)
        assertNotNull(fxyz.inputZ)
        assertEquals(false, fxyz.inputZ!!.isKickable)
        assertNotNull(fxyz.output)
    }

    @Test
    fun testModuleRegistryCreator() {
        for (name in listOf("f(x)", "f(x,y)", "f(x,y,z)")) {
            val creator = ModuleRegistry.findCreator(name)
            assertNotNull(creator, "EquationModule creator should be registered in ModuleRegistry under '$name'")
            val module = creator()
            assertTrue(module is EquationModule, "Created module should be an instance of EquationModule")
            assertEquals(name, module.name)
        }
    }

    @Test
    fun testEvaluation() {
        val graphState = GraphState()
        val module = EquationModule("f(x,y,z)")
        module.compile("x * y + z")
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        var receivedVal: Double? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedVal = triggerMessage.value
                }
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(module.output, receiver.input)

        // Set inputs
        module.inputX.setValue(4.0)
        module.inputY!!.setValue(3.0)
        module.inputZ!!.setValue(5.0)

        // Kick the module (inputX is kickable, which triggers execute)
        module.onMessageReceived(module.inputX, Message.FloatValue(ScoreClock.now(), 4.0))

        assertEquals(17.0, receivedVal, "4.0 * 3.0 + 5.0 should be 17.0")
    }

    @Test
    fun testSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = EquationModule("f(x,y)")
        module.compile("sin(x) * y")
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"equation\": \"sin(x) * y\"") || jsonStr.contains("\"equation\":\"sin(x) * y\""), "Serialized string should contain equation value")

        // Deserialize
        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserializedModule = newGraphState.modules.find { it.name == "f(x,y)" } as? EquationModule
        assertNotNull(deserializedModule)
        assertEquals("sin(x) * y", deserializedModule.equation, "Deserialized equation should match original")
    }

    @Test
    fun testCompileError() {
        val module = EquationModule("f(x)")
        assertEquals(null, module.errorMessage)

        val success = module.compile("sizzzn(x)")
        kotlin.test.assertFalse(success, "Compilation of invalid equation should fail")
        assertNotNull(module.errorMessage, "errorMessage should be populated when compilation fails")
        assertTrue(module.errorMessage!!.contains("Unexpected character") || module.errorMessage!!.contains("Unexpected characters") || module.errorMessage!!.isNotEmpty())
    }
}
