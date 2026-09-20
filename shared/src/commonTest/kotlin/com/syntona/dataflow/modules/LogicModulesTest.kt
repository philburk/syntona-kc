package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.*

class LogicModulesTest {

    private class MessageReceiver(name: String = "receiver") : Module(name) {
        val inJack = addInput("in", isKickable = true)
        val received = mutableListOf<Message>()

        override fun execute(triggerMessage: Message) {
            received.add(triggerMessage)
        }
    }

    @Test
    fun testModuleRegistration() {
        val expectedModules = listOf(
            "and", "or", "xor", "not", "toggle", "pass",
            "branch4", "branch8", "branch16",
            "select4", "select8", "select16"
        )
        for (name in expectedModules) {
            val creator = ModuleRegistry.findCreator(name)
            assertNotNull(creator, "$name should be registered in ModuleRegistry")
            val module = creator()
            assertNotNull(module)
            assertEquals("core.logic.$name", ModuleRegistry.getBundlePath(name))
        }

        // Verify legacy aliases resolve
        listOf("gate", "choose4", "choose8", "choose16").forEach { legacyName ->
            assertNotNull(ModuleRegistry.findCreator(legacyName), "Legacy $legacyName should resolve")
        }
    }

    @Test
    fun testAndModuleTruthTableAndKickability() {
        val andMod = AndModule()
        val receiver = MessageReceiver()
        andMod.output.connect(receiver.inJack)

        // 0 AND 0 -> 0
        andMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        receiver.received.clear()
        andMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(1, receiver.received.size)
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)

        // 0 AND 1 -> 0 (triggered via inputB)
        receiver.received.clear()
        andMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(1, receiver.received.size)
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)

        // 1 AND 1 -> 1 (triggered via inputA)
        receiver.received.clear()
        andMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(1, receiver.received.size)
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)

        // 1 AND 0 -> 0 (triggered via inputB)
        receiver.received.clear()
        andMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(1, receiver.received.size)
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)

        // Edge case: negative numbers are FALSE (<= 0), positive floats are TRUE (> 0)
        receiver.received.clear()
        andMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, -5.0))
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)

        receiver.received.clear()
        andMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        andMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 0.001))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)
    }

    @Test
    fun testOrModuleTruthTable() {
        val orMod = OrModule()
        val receiver = MessageReceiver()
        orMod.output.connect(receiver.inJack)

        // 0 OR 0 -> 0
        orMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        receiver.received.clear()
        orMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)

        // 0 OR 1 -> 1
        receiver.received.clear()
        orMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)

        // 1 OR 0 -> 1
        receiver.received.clear()
        orMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        orMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)

        // 1 OR 1 -> 1
        receiver.received.clear()
        orMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)
    }

    @Test
    fun testXorModuleTruthTable() {
        val xorMod = XorModule()
        val receiver = MessageReceiver()
        xorMod.output.connect(receiver.inJack)

        // 0 XOR 0 -> 0
        xorMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        receiver.received.clear()
        xorMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)

        // 0 XOR 1 -> 1
        receiver.received.clear()
        xorMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)

        // 1 XOR 0 -> 1
        receiver.received.clear()
        xorMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        xorMod.inputA.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)

        // 1 XOR 1 -> 0
        receiver.received.clear()
        xorMod.inputB.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)
    }

    @Test
    fun testNotModule() {
        val notMod = NotModule()
        val receiver = MessageReceiver()
        notMod.output.connect(receiver.inJack)

        // NOT 0 -> 1
        notMod.input.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)

        // NOT 1 -> 0
        notMod.input.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)

        // NOT -10 -> 1 (<= 0 is false)
        notMod.input.receive(Message.FloatValue(ScoreTime.Zero, -10.0))
        assertEquals(1.0, (receiver.received.last() as Message.FloatValue).value)

        // NOT 0.05 -> 0 (> 0 is true)
        notMod.input.receive(Message.FloatValue(ScoreTime.Zero, 0.05))
        assertEquals(0.0, (receiver.received.last() as Message.FloatValue).value)
    }

    @Test
    fun testBranchModuleRouting() {
        val branch = Branch4Module()
        val receivers = List(4) { MessageReceiver("rec$it") }
        for (i in 0 until 4) {
            branch.stepOutputs[i].connect(receivers[i].inJack)
        }

        // Updating index should NOT trigger outputs
        branch.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 2.0))
        receivers.forEach { assertEquals(0, it.received.size, "Index change must not trigger output") }

        // Send to in -> should route to out2
        val msg1: Message = Message.FloatValue(ScoreTime(100L), 99.0)
        branch.input.receive(msg1)
        assertEquals(0, receivers[0].received.size)
        assertEquals(0, receivers[1].received.size)
        assertEquals(listOf<Message>(msg1), receivers[2].received)
        assertEquals(0, receivers[3].received.size)

        // Change index to 0
        branch.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        val msg2: Message = Message.TextValue(ScoreTime(200L), "synth-lead")
        branch.input.receive(msg2)
        assertEquals(listOf<Message>(msg2), receivers[0].received)
        assertEquals(1, receivers[2].received.size)

        // Out of bounds: index 4 (>= 4) -> message dropped
        branch.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 4.0))
        branch.input.receive(Message.FloatValue(ScoreTime(300L), 1.0))
        receivers.forEach {
            if (it == receivers[0] || it == receivers[2]) assertEquals(1, it.received.size)
            else assertEquals(0, it.received.size)
        }
    }

    @Test
    fun testSelectModuleSelection() {
        val select = Select4Module()
        val receiver = MessageReceiver()
        select.output.connect(receiver.inJack)

        // Select index 2
        select.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 2.0))
        assertEquals(0, receiver.received.size, "Index change must not trigger output")

        // Send on unselected inputs: in0, in1, in3 -> all dropped
        select.stepInputs[0].receive(Message.FloatValue(ScoreTime.Zero, 10.0))
        select.stepInputs[1].receive(Message.FloatValue(ScoreTime.Zero, 20.0))
        select.stepInputs[3].receive(Message.FloatValue(ScoreTime.Zero, 30.0))
        assertEquals(0, receiver.received.size, "Unselected inputs must be dropped")

        // Send on selected input in2 -> forwarded
        val msg: Message = Message.FloatValue(ScoreTime(500L), 42.0)
        select.stepInputs[2].receive(msg)
        assertEquals(listOf<Message>(msg), receiver.received, "Selected input must be forwarded")

        // Switch to index 0
        select.indexIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        val msg0: Message = Message.TextValue(ScoreTime(600L), "selected")
        select.stepInputs[0].receive(msg0)
        assertEquals(listOf<Message>(msg, msg0), receiver.received)
    }

    @Test
    fun testPassModulePassAndDrop() {
        val pass = PassModule()
        val receiver = MessageReceiver()
        pass.output.connect(receiver.inJack)

        assertEquals("enabled", pass.enabled.name)
        assertIs<com.syntona.dataflow.model.BooleanInputJack>(pass.enabled)
        assertFalse(pass.enabled.isValueDraggable)
        assertFalse(pass.enabled.isDisplayableValue)
        assertFalse(pass.enabled.isModuleEditorVisible)
        assertTrue(pass.enabled.isChecked)

        // Initial state is open (enabled = 1.0)
        val msg1: Message = Message.FloatValue(ScoreTime(100L), 77.0)
        pass.input.receive(msg1)
        assertEquals(listOf<Message>(msg1), receiver.received, "Pass should be open by default")

        // Close pass (enabled = 0.0)
        pass.enabled.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(1, receiver.received.size, "Updating enabled should not trigger output")
        assertFalse(pass.enabled.isChecked)

        // Send message when closed -> dropped
        pass.input.receive(Message.FloatValue(ScoreTime(200L), 88.0))
        assertEquals(1, receiver.received.size, "Message should be dropped when pass is closed")

        // Re-open pass (enabled = 0.5 > 0)
        pass.enabled.receive(Message.FloatValue(ScoreTime.Zero, 0.5))
        assertTrue(pass.enabled.isChecked)
        val msg2: Message = Message.FloatValue(ScoreTime(300L), 99.0)
        pass.input.receive(msg2)
        assertEquals(listOf<Message>(msg1, msg2), receiver.received, "Message should pass when pass is re-opened")
    }

    @Test
    fun testSerializationRoundTrip() {
        val graph = GraphState()
        val andMod = AndModule()
        val branch = Branch4Module()
        val select = Select4Module()
        val pass = PassModule()

        branch.indexIn.setValue(2.0)
        select.indexIn.setValue(1.0)
        pass.enabled.setValue(0.0)

        graph.addModule(andMod, Offset.Zero)
        graph.addModule(branch, Offset(100f, 100f))
        graph.addModule(select, Offset(200f, 200f))
        graph.addModule(pass, Offset(300f, 300f))

        val json = GraphSerializer.serialize(graph, graph.modules)
        assertTrue(json.contains("core.logic.and"))
        assertTrue(json.contains("core.logic.branch4"))
        assertTrue(json.contains("core.logic.select4"))
        assertTrue(json.contains("core.logic.pass"))

        val newGraph = GraphState()
        val restored = GraphSerializer.deserialize(newGraph, json)

        val restoredBranch = restored.find { it is Branch4Module } as Branch4Module
        val restoredSelect = restored.find { it is Select4Module } as Select4Module
        val restoredPass = restored.find { it is PassModule } as PassModule

        assertEquals(2.0, restoredBranch.indexIn.getValue())
        assertEquals(1.0, restoredSelect.indexIn.getValue())
        assertEquals(0.0, restoredPass.enabled.getValue())
    }

    @Test
    fun testToggleModule() {
        val toggle = ToggleModule()
        assertEquals("toggle", toggle.name)
        assertEquals(2, toggle.inputs.size)
        assertEquals("reset", toggle.inputs[0].name)
        assertIs<com.syntona.dataflow.model.CommandJack>(toggle.reset, "reset should be a CommandJack")
        assertTrue(toggle.inputs[0].isKickable, "reset should be kickable")
        assertFalse(toggle.reset.isModuleEditorVisible, "reset should have isModuleEditorVisible=false")
        assertEquals("kick", toggle.inputs[1].name)
        assertIs<com.syntona.dataflow.model.CommandJack>(toggle.kick, "kick should be a CommandJack")
        assertTrue(toggle.inputs[1].isKickable, "kick should be kickable")
        assertFalse(toggle.kick.isModuleEditorVisible, "kick should have isModuleEditorVisible=false")

        assertEquals(2, toggle.outputs.size)
        assertEquals("out0", toggle.outputs[0].name)
        assertEquals("out1", toggle.outputs[1].name)

        val rec0 = MessageReceiver("rec0")
        val rec1 = MessageReceiver("rec1")
        toggle.out0.connect(rec0.inJack)
        toggle.out1.connect(rec1.inJack)

        // Initial state
        assertEquals(0, toggle.state)
        assertTrue(toggle.out0.isHighlighted)
        assertFalse(toggle.out1.isHighlighted)

        // Kick 1 -> should fire out0 with TRUE (1.0)
        toggle.kick.receive(Message.FloatValue(ScoreTime(100L), 0.0))
        assertEquals(1, rec0.received.size)
        assertEquals(0, rec1.received.size)
        assertEquals(1.0, (rec0.received.last() as Message.FloatValue).value)
        assertEquals(ScoreTime(100L), rec0.received.last().time)
        assertEquals(1, toggle.state)
        assertTrue(toggle.out0.isHighlighted)
        assertFalse(toggle.out1.isHighlighted)

        // Kick 2 -> should fire out1 with TRUE (1.0)
        rec0.received.clear()
        rec1.received.clear()
        toggle.kick.receive(Message.FloatValue(ScoreTime(200L), 0.0))
        assertEquals(0, rec0.received.size)
        assertEquals(1, rec1.received.size)
        assertEquals(1.0, (rec1.received.last() as Message.FloatValue).value)
        assertEquals(ScoreTime(200L), rec1.received.last().time)
        assertEquals(0, toggle.state)
        assertFalse(toggle.out0.isHighlighted)
        assertTrue(toggle.out1.isHighlighted)

        // Kick 3 -> should fire out0 with TRUE (1.0)
        rec0.received.clear()
        rec1.received.clear()
        toggle.kick.receive(Message.FloatValue(ScoreTime(300L), 0.0))
        assertEquals(1, rec0.received.size)
        assertEquals(0, rec1.received.size)
        assertEquals(1.0, (rec0.received.last() as Message.FloatValue).value)
        assertEquals(1, toggle.state)

        // Reset -> should reset state back to 0 without emitting
        rec0.received.clear()
        rec1.received.clear()
        toggle.reset.receive(Message.FloatValue(ScoreTime(400L), 0.0))
        assertEquals(0, rec0.received.size)
        assertEquals(0, rec1.received.size)
        assertEquals(0, toggle.state)
        assertTrue(toggle.out0.isHighlighted)
        assertFalse(toggle.out1.isHighlighted)

        // Kick after Reset -> should fire out0 again
        toggle.kick.receive(Message.FloatValue(ScoreTime(500L), 0.0))
        assertEquals(1, rec0.received.size)
        assertEquals(0, rec1.received.size)
        assertEquals(1.0, (rec0.received.last() as Message.FloatValue).value)
        assertEquals(1, toggle.state)
    }
}
