package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.IntegerInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.*

class TableModuleTest {

    @Test
    fun testTableRegistrationAndProperties() {
        for (n in listOf(4, 8, 16)) {
            val typeName = "table$n"
            val creator = ModuleRegistry.findCreator(typeName)
            assertNotNull(creator, "$typeName should be registered in ModuleRegistry")
            val module = creator()
            assertTrue(module is TableModule, "Creator should produce TableModule")
            assertEquals(n, module.numOutputs)
            assertEquals("music.compose.$typeName", ModuleRegistry.getBundlePath(typeName))

            // Check index jack
            val indexJack = module.indexIn
            assertTrue(indexJack.isKickable, "indexIn should be kickable")
            assertIs<IntegerInputJack>(indexJack, "indexIn should be an IntegerInputJack")
            assertTrue(indexJack.isInteger, "indexIn should be marked as integer")
            assertEquals(0.0, indexJack.min)
            assertEquals((n - 1).toDouble(), indexJack.max)

            // Check out jack
            assertEquals("out", module.out.name)
            assertEquals("out", module.mainOutput.name)

            // Verify no reset jack
            assertNull(module.inputs.find { it.name == "reset" }, "TableModule should have no reset jack")
        }
    }

    @Test
    fun testIndexedLookup() {
        val table = Table8Module()
        val receivedOut = mutableListOf<Double>()
        val receivedStep3 = mutableListOf<Double>()

        val outReceiver = object : Module("outRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedOut.add(triggerMessage.value)
                }
            }
        }
        val step3Receiver = object : Module("step3Rec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedStep3.add(triggerMessage.value)
                }
            }
        }

        table.out.connect(outReceiver.inJack)
        table.stepOutputs[3].connect(step3Receiver.inJack)

        table.values[0] = "10.0"
        table.values[3] = "42.0"
        table.values[5] = "99.0"

        // Send index 3
        table.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 3.0))

        assertEquals(listOf(42.0), receivedOut, "out should emit value for index 3")
        assertEquals(listOf(42.0), receivedStep3, "stepOutput 3 should emit value for index 3")
        assertTrue(table.stepOutputs[3].isHighlighted, "step 3 should be highlighted")
        assertFalse(table.stepOutputs[0].isHighlighted, "step 0 should not be highlighted")

        // Send index 0
        table.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(listOf(42.0, 10.0), receivedOut, "out should emit value for index 0")
        assertTrue(table.stepOutputs[0].isHighlighted, "step 0 should be highlighted")
        assertFalse(table.stepOutputs[3].isHighlighted, "step 3 should no longer be highlighted")
    }

    @Test
    fun testMultiValueListFiring() {
        val table = Table4Module()
        val receivedOut = mutableListOf<Double>()

        val outReceiver = object : Module("outRec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedOut.add(triggerMessage.value)
                }
            }
        }
        table.out.connect(outReceiver.inJack)

        table.values[2] = "60, 64, 67"
        table.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 2.0))

        assertEquals(listOf(60.0, 64.0, 67.0), receivedOut, "Table should emit multi-value chords in order")
    }

    @Test
    fun testStepInputsDynamicUpdate() {
        val table = Table4Module()
        assertEquals("1.0", table.values[1])

        table.stepInputs[1].receive(Message.FloatValue(ScoreTime.Zero, 88.5))
        assertEquals("88.5", table.values[1], "stepInput message should dynamically update value")

        table.stepInputs[1].receive(Message.TextValue(ScoreTime.Zero, "10, 20, 30"))
        assertEquals("10, 20, 30", table.values[1], "stepInput text message should dynamically update value")
    }

    @Test
    fun testSerializationRoundTrip() {
        val state = GraphState()
        val table = Table8Module()
        table.values[0] = "55.0"
        table.values[1] = "60, 65, 70"
        table.indexIn.setValue(2.0)
        state.addModule(table, Offset.Zero)

        val json = GraphSerializer.serialize(state, state.modules.toList())
        assertTrue(json.contains("music.compose.table8"), "JSON should contain bundle path music.compose.table8")
        assertTrue(json.contains("60, 65, 70"), "JSON should contain custom step values")

        val restoredState = GraphState()
        GraphSerializer.deserialize(restoredState, json)

        val restored = restoredState.modules.firstOrNull { it is Table8Module } as? Table8Module
        assertNotNull(restored, "Restored state should contain Table8Module")
        assertEquals("55.0", restored.values[0])
        assertEquals("60, 65, 70", restored.values[1])
        assertEquals(2.0, restored.indexIn.getValue())
        assertTrue(restored.indexIn.isInteger, "Restored index jack should retain isInteger")
    }

    @Test
    fun testStepperDrivingTable() {
        val stepper = StepperModule()
        val table = Table4Module()

        table.values[0] = "100.0"
        table.values[1] = "200.0"
        table.values[2] = "300.0"
        table.values[3] = "400.0"

        val received = mutableListOf<Double>()
        val receiver = object : Module("rec") {
            val inJack = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    received.add(triggerMessage.value)
                }
            }
        }

        stepper.indexOut.connect(table.indexIn)
        table.out.connect(receiver.inJack)

        // Start stepper
        stepper.start.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(listOf(100.0), received, "Start on stepper should drive table step 0")

        // Kick 1
        stepper.kick.receive(Message.FloatValue(ScoreTime(1_000_000_000L), 1.0))
        assertEquals(listOf(100.0, 200.0), received, "Kick 1 should drive table step 1")

        // Kick 2
        stepper.kick.receive(Message.FloatValue(ScoreTime(2_000_000_000L), 1.0))
        assertEquals(listOf(100.0, 200.0, 300.0), received, "Kick 2 should drive table step 2")

        // Kick 3
        stepper.kick.receive(Message.FloatValue(ScoreTime(3_000_000_000L), 1.0))
        assertEquals(listOf(100.0, 200.0, 300.0, 400.0), received, "Kick 3 should drive table step 3")
    }
}
