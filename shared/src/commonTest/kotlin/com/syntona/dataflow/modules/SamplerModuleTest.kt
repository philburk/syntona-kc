package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SamplerModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = SamplerModule()
        assertEquals("sampler", module.name)
        
        // Assert input jack order: kick should be above input
        assertEquals(2, module.inputs.size)
        assertEquals("kick", module.inputs[0].name)
        assertEquals("input", module.inputs[1].name)

        val kickInput = module.inputs[0]
        assertTrue(kickInput.isKickable, "kick input should be kickable")

        val audioInput = module.inputs[1]
        assertTrue(audioInput is AudioInputJack, "input jack should be an AudioInputJack")

        val regularOutput = module.outputs.find { it.name == "output" }
        assertNotNull(regularOutput, "SamplerModule should have an output named 'output'")
        assertTrue(regularOutput is OutputJack, "output jack should be a regular OutputJack")
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("sampler")
        assertNotNull(creator, "SamplerModule creator should be registered in ModuleRegistry under 'sampler'")
        val module = creator()
        assertTrue(module is SamplerModule, "Created module should be an instance of SamplerModule")
    }

    @Test
    fun testSamplerExecution() {
        val graphState = GraphState()
        val sampler = SamplerModule()
        graphState.addModule(sampler, androidx.compose.ui.geometry.Offset.Zero)

        // Capture output sent from the sampler output jack
        var messageReceived: Message? = null
        val receiverInput = object : com.syntona.dataflow.model.Module("testReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                messageReceived = triggerMessage
            }
        }
        graphState.addModule(receiverInput, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(sampler.output, receiverInput.input)

        // Verify no message yet
        assertEquals(null, messageReceived)

        // Set value on sampler audio input
        val audioInput = sampler.inputs.first { it.name == "input" }
        audioInput.setValue(12.34)

        // Trigger sampler kick
        val kickInput = sampler.inputs.first { it.name == "kick" }
        kickInput.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Verify sampled value received
        assertNotNull(messageReceived)
        assertTrue(messageReceived is Message.FloatValue)
        assertEquals(12.34, (messageReceived as Message.FloatValue).value, 0.001)
    }
}
