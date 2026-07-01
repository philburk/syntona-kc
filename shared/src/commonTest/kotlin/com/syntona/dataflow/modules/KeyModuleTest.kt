package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = KeyModule()
        assertEquals("key", module.name)
        
        val onOutput = module.outputs.find { it.name == "on" }
        assertNotNull(onOutput, "KeyModule should have an output named 'on'")

        val offOutput = module.outputs.find { it.name == "off" }
        assertNotNull(offOutput, "KeyModule should have an output named 'off'")
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("key")
        assertNotNull(creator, "KeyModule creator should be registered in ModuleRegistry under 'key'")
        val module = creator()
        assertTrue(module is KeyModule, "Created module should be an instance of KeyModule")
    }



    @Test
    fun testKeyStrokesAndAutoRepeat() {
        val graphState = GraphState()
        val keyModule = KeyModule()
        graphState.addModule(keyModule, androidx.compose.ui.geometry.Offset.Zero)

        // Receivers to capture events
        var onReceivedVal: Double? = null
        var onCount = 0
        val onReceiver = object : com.syntona.dataflow.model.Module("onReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    onReceivedVal = triggerMessage.value
                    onCount++
                }
            }
        }
        graphState.addModule(onReceiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyModule.on, onReceiver.input)

        var offReceivedVal: Double? = null
        var offCount = 0
        val offReceiver = object : com.syntona.dataflow.model.Module("offReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    offReceivedVal = triggerMessage.value
                    offCount++
                }
            }
        }
        graphState.addModule(offReceiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(keyModule.off, offReceiver.input)

        // 1. Dispatch initial key down for 'A' (ASCII 65)
        val processedDown = graphState.handleKeyStroke(65, isDown = true)
        assertTrue(processedDown)
        assertEquals(65.0, onReceivedVal)
        assertEquals(1, onCount)
        assertEquals(null, offReceivedVal)
        assertEquals(0, offCount)

        // 2. Dispatch repeated key down for 'A' (simulate auto-repeat)
        val processedDownRepeat = graphState.handleKeyStroke(65, isDown = true)
        assertTrue(processedDownRepeat)
        assertEquals(1, onCount, "Auto-repeat key down should be ignored (should not send another message)")
        assertEquals(0, offCount)

        // 3. Dispatch key up for 'A'
        val processedUp = graphState.handleKeyStroke(65, isDown = false)
        assertTrue(processedUp)
        assertEquals(65.0, offReceivedVal)
        assertEquals(1, onCount)
        assertEquals(1, offCount)

        // 4. Dispatch key down for 'A' again (should be allowed now that key was released)
        val processedDown2 = graphState.handleKeyStroke(65, isDown = true)
        assertTrue(processedDown2)
        assertEquals(2, onCount)
    }
}
