package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SequenceModuleTest {

    @Test
    fun testResetDoesNotSendMessageAndResetsImmediately() {
        val seq = SequenceModule(8)

        var stepReceived = false
        var allReceived = false

        val receiverStep = object : Module("stepRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                stepReceived = true
            }
        }

        val receiverAll = object : Module("allRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                allReceived = true
            }
        }

        seq.stepOutputs[0].connect(receiverStep.inJack)
        seq.allOutput.connect(receiverAll.inJack)

        // Advance count to 3
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.increment.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(3, seq.count)

        stepReceived = false
        allReceived = false

        // Kick reset
        seq.reset.receive(Message.FloatValue(ScoreTime.Zero, 1.0))

        assertEquals(0, seq.count, "count should be reset to 0 immediately")
        assertTrue(seq.stepOutputs[0].isHighlighted, "step 0 should be highlighted on reset")
        assertFalse(seq.stepOutputs[3].isHighlighted, "step 3 should no longer be highlighted")
        assertFalse(stepReceived, "reset should not cause step output message to be sent")
        assertFalse(allReceived, "reset should not cause 'all' output message to be sent")
    }

    @Test
    fun testAllOutputJackSendsAllStepMessages() {
        val seq = SequenceModule(8)
        val receivedAllValues = mutableListOf<Double>()

        val receiver = object : Module("allRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedAllValues.add(triggerMessage.value)
                }
            }
        }
        seq.allOutput.connect(receiver.inJack)

        seq.values[0] = "10.0"
        seq.values[1] = "20.0"
        seq.values[2] = "30.0"

        // Increment 3 times (post-increment: fires steps 0, 1, 2)
        seq.kick.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.kick.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        seq.kick.receive(Message.FloatValue(ScoreTime.Zero, 1.0))

        assertEquals(listOf(10.0, 20.0, 30.0), receivedAllValues)
    }

    @Test
    fun testMultiValueListStepFiring() {
        val seq = SequenceModule(8)
        val stepOutputsEmitted = mutableListOf<Double>()
        val allOutputsEmitted = mutableListOf<Double>()

        val stepReceiver = object : Module("stepRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    stepOutputsEmitted.add(triggerMessage.value)
                }
            }
        }

        val allReceiver = object : Module("allRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    allOutputsEmitted.add(triggerMessage.value)
                }
            }
        }

        seq.stepOutputs[1].connect(stepReceiver.inJack)
        seq.allOutput.connect(allReceiver.inJack)

        // Set step 1 to a comma-separated list of values "60,62.5,67"
        seq.values[1] = "60,62.5,67"

        // Kick step 0, advances count to 1
        seq.kick.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        stepOutputsEmitted.clear()
        allOutputsEmitted.clear()

        // Kick step 1
        seq.kick.receive(Message.FloatValue(ScoreTime.Zero, 1.0))

        val expected = listOf(60.0, 62.5, 67.0)
        assertEquals(expected, stepOutputsEmitted, "Step output 1 should emit all 3 values in order")
        assertEquals(expected, allOutputsEmitted, "All output jack should emit all 3 values in order")
    }

    @Test
    fun testCopyPasteAndSerializationPreservesMultiValueLists() {
        val rootGraph = com.syntona.dataflow.editor.GraphState()
        val seq = SequenceModule(8)
        seq.values[0] = "52, 55"
        seq.values[1] = "60, 62.5, 67"
        rootGraph.addModule(seq, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize graph (simulating copy or save)
        val json = com.syntona.dataflow.editor.GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("52, 55"), "Serialized JSON should contain the exact string list '52, 55'")

        // Deserialize graph (simulating paste or open)
        val newGraph = com.syntona.dataflow.editor.GraphState()
        val deserialized = com.syntona.dataflow.editor.GraphSerializer.deserialize(newGraph, json)
        val restoredSeq = deserialized.first { it is SequenceModule } as SequenceModule

        assertEquals("52, 55", restoredSeq.values[0], "Pasted/restored seq8 step 0 should preserve multi-value string '52, 55'")
        assertEquals("60, 62.5, 67", restoredSeq.values[1], "Pasted/restored seq8 step 1 should preserve multi-value string '60, 62.5, 67'")
    }

    @Test
    fun testSeq16ModuleRegistered() {
        val creator = ModuleRegistry.findCreator("seq16")
        assertNotNull(creator, "seq16 should be registered in ModuleRegistry")
        val module = creator()
        assertTrue(module is SequenceModule)
        assertEquals(16, module.numOutputs)
        assertEquals("out", module.mainOutput.name)
        assertEquals("kick", module.kick.name)
        assertEquals("kick", module.increment.name)
        assertTrue(module.kick is com.syntona.dataflow.model.CommandJack)
        assertTrue(module.reset is com.syntona.dataflow.model.CommandJack)
        assertFalse(module.kick.isModuleEditorVisible)
        assertFalse(module.reset.isModuleEditorVisible)
    }

    @Test
    fun testCounterAndRandomCommandJacks() {
        val counter = CounterModule()
        assertTrue(counter.kick is com.syntona.dataflow.model.CommandJack)
        assertTrue(counter.increment is com.syntona.dataflow.model.CommandJack)
        assertTrue(counter.reset is com.syntona.dataflow.model.CommandJack)
        assertFalse(counter.kick.isModuleEditorVisible)
        assertFalse(counter.reset.isModuleEditorVisible)

        val random = RandomModule()
        assertTrue(random.kick is com.syntona.dataflow.model.CommandJack)
        assertFalse(random.kick.isModuleEditorVisible)
    }

    @Test
    fun testLegacyInAndAllConnectionsDeserialization() {
        val legacyJson = """
            {
                "modules": [
                    {"id": 0, "type": "core.basic.seq8", "name": "seq"},
                    {"id": 1, "type": "core.basic.counter", "name": "counter"}
                ],
                "connections": [
                    {"from": 0, "fromJack": "all", "to": 1, "toJack": "inc"},
                    {"from": 1, "fromJack": "out", "to": 0, "toJack": "in"}
                ]
            }
        """.trimIndent()
        val graphState = com.syntona.dataflow.editor.GraphState()
        com.syntona.dataflow.editor.GraphSerializer.deserialize(graphState, legacyJson)
        val seq = graphState.modules.first { it is SequenceModule } as SequenceModule
        assertEquals(2, graphState.connections.size)
        assertEquals(seq.mainOutput, graphState.connections[0].output)
        assertEquals(seq.kick, graphState.connections[1].input)
    }
}
