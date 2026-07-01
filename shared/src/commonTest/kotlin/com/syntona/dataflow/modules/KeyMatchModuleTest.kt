package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyMatchModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = KeyMatchModule()
        assertEquals("keyMatch", module.name)
        assertEquals('X', module.keyChar)
        
        val onOutput = module.outputs.find { it.name == "on" }
        assertNotNull(onOutput, "KeyMatchModule should have an output named 'on'")

        val offOutput = module.outputs.find { it.name == "off" }
        assertNotNull(offOutput, "KeyMatchModule should have an output named 'off'")
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("keyMatch")
        assertNotNull(creator, "KeyMatchModule creator should be registered in ModuleRegistry under 'keyMatch'")
        val module = creator()
        assertTrue(module is KeyMatchModule, "Created module should be an instance of KeyMatchModule")
    }

    @Test
    fun testCharacterMatching() {
        val graphState = GraphState()
        val keyMatchModule = KeyMatchModule()
        keyMatchModule.keyChar = 'a' // Match character 'a'
        graphState.addModule(keyMatchModule, androidx.compose.ui.geometry.Offset.Zero)

        var onReceivedVal: Double? = null
        val onReceiver = object : com.syntona.dataflow.model.Module("onReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    onReceivedVal = triggerMessage.value
                }
            }
        }
        graphState.addModule(onReceiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyMatchModule.on, onReceiver.input)

        // 1. Dispatch key stroke for 'b' (ASCII 98) - should be ignored
        val processedIgnored = graphState.handleKeyStroke(98, isDown = true)
        assertFalse(processedIgnored, "Key stroke for un-matched char should be ignored")
        assertEquals(null, onReceivedVal)

        // 2. Dispatch key stroke for 'a' (ASCII 97) - should trigger output
        val processedMatch = graphState.handleKeyStroke(97, isDown = true)
        assertTrue(processedMatch, "Key stroke for matched char should be processed")
        assertEquals(97.0, onReceivedVal)
    }

    @Test
    fun testSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = KeyMatchModule()
        module.keyChar = 'z'
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"keyChar\":\"z\""), "Serialized string should contain keyChar value")

        // Deserialize
        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserializedModule = newGraphState.modules.find { it.name == "keyMatch" } as? KeyMatchModule
        assertNotNull(deserializedModule)
        assertEquals('z', deserializedModule.keyChar, "Deserialized keyChar should match original 'z'")
    }
}
