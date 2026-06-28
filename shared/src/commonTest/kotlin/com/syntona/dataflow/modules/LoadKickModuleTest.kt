package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoadKickModuleTest {

    @Test
    fun testModuleCreationAndOutput() {
        val module = LoadKickModule()
        assertEquals("loadKick", module.name)
        val kickOutput = module.outputs.find { it.name == "kick" }
        assertNotNull(kickOutput, "LoadKickModule should have an output named 'kick'")
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("loadKick")
        assertNotNull(creator, "LoadKickModule creator should be registered in ModuleRegistry under 'loadKick'")
        val module = creator()
        assertTrue(module is LoadKickModule, "Created module should be an instance of LoadKickModule")
    }

    @Test
    fun testKickLoadKickModules() {
        val graphState = GraphState()
        val loadKick = LoadKickModule()
        graphState.addModule(loadKick, androidx.compose.ui.geometry.Offset.Zero)

        // Capture output sent from the kick output jack
        var messageReceived: Message? = null
        val receiverInput = object : com.syntona.dataflow.model.Module("testReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                messageReceived = triggerMessage
            }
        }
        graphState.addModule(receiverInput, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(loadKick.output, receiverInput.input)

        // Verify no message yet
        assertEquals(null, messageReceived)

        // Trigger kickLoadKickModules
        graphState.kickLoadKickModules()

        // Verify kick received
        assertNotNull(messageReceived)
        assertTrue(messageReceived is Message.FloatValue)
        assertEquals(0.0, (messageReceived as Message.FloatValue).value)
    }

    @Test
    fun testKickLoadKickModulesNested() {
        val rootGraphState = GraphState()
        
        // Add a PatchModule (nested graph)
        val patchModule = PatchModule("testPatch")
        rootGraphState.addModule(patchModule, androidx.compose.ui.geometry.Offset.Zero)

        // Add a LoadKickModule inside the patch module
        val nestedLoadKick = LoadKickModule()
        patchModule.graphState.addModule(nestedLoadKick, androidx.compose.ui.geometry.Offset.Zero)

        // Add a receiver inside the patch module
        var messageReceived: Message? = null
        val receiverInput = object : com.syntona.dataflow.model.Module("testReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                messageReceived = triggerMessage
            }
        }
        patchModule.graphState.addModule(receiverInput, androidx.compose.ui.geometry.Offset.Zero)
        patchModule.graphState.connect(nestedLoadKick.output, receiverInput.input)

        // Verify no message yet
        assertEquals(null, messageReceived)

        // Trigger kickLoadKickModules on root graph state
        rootGraphState.kickLoadKickModules()

        // Verify the nested load kick was triggered
        assertNotNull(messageReceived)
        assertTrue(messageReceived is Message.FloatValue)
        assertEquals(0.0, (messageReceived as Message.FloatValue).value)
    }
}
