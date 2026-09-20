package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GrabAudioModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = GrabAudioModule()
        assertEquals("grabAudio", module.name)

        // Assert input jack order: kick should be above in
        assertEquals(2, module.inputs.size)
        assertEquals("kick", module.inputs[0].name)
        assertEquals("in", module.inputs[1].name)

        val kickInput = module.inputs[0]
        assertTrue(kickInput.isKickable, "kick input should be kickable")

        val audioInput = module.inputs[1]
        assertTrue(audioInput is AudioInputJack, "input jack should be an AudioInputJack")

        val regularOutput = module.outputs.find { it.name == "out" }
        assertNotNull(regularOutput, "GrabAudioModule should have an output named 'out'")
        assertIs<OutputJack>(regularOutput, "output jack should be a regular OutputJack")
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("grabAudio")
        assertNotNull(creator, "GrabAudioModule creator should be registered in ModuleRegistry under 'grabAudio'")
        val module = creator()
        assertTrue(module is GrabAudioModule, "Created module should be an instance of GrabAudioModule")
    }

    @Test
    fun testGrabAudioExecution() {
        val graphState = GraphState()
        val grabAudio = GrabAudioModule()
        graphState.addModule(grabAudio, androidx.compose.ui.geometry.Offset.Zero)

        // Capture output sent from the grabAudio output jack
        var messageReceived: Message? = null
        val receiverInput = object : com.syntona.dataflow.model.Module("testReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                messageReceived = triggerMessage
            }
        }
        graphState.addModule(receiverInput, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(grabAudio.output, receiverInput.input)

        // Verify no message yet
        assertEquals(null, messageReceived)

        // Set value on grabAudio audio input
        val audioInput = grabAudio.inputs.first { it.name == "in" }
        audioInput.setValue(12.34)

        // Trigger grabAudio kick
        val kickInput = grabAudio.inputs.first { it.name == "kick" }
        kickInput.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Verify sampled value received
        assertNotNull(messageReceived)
        assertTrue(messageReceived is Message.FloatValue)
        assertEquals(12.34, (messageReceived as Message.FloatValue).value, 0.001)
    }
}
