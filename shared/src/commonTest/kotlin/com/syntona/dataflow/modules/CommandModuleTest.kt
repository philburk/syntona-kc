package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = CommandModule()
        assertEquals("command", module.name)
        assertEquals(0, module.inputs.size)
        assertEquals(1, module.outputs.size)
        assertEquals("output", module.outputs[0].name)
    }

    @Test
    fun testDefaultCommandJackNamingOnPatch() {
        val patch = PatchModule()
        val cmd1 = CommandModule()
        val cmd2 = CommandModule()

        patch.graphState.addModule(cmd1, Offset.Zero)
        patch.graphState.addModule(cmd2, Offset.Zero)

        assertEquals("cmd1", cmd1.name)
        assertEquals("cmd2", cmd2.name)

        assertEquals(2, patch.inputs.size)
        assertTrue(patch.inputs[0] is CommandJack)
        assertTrue(patch.inputs[1] is CommandJack)
        assertEquals("cmd1", patch.inputs[0].name)
        assertEquals("cmd2", patch.inputs[1].name)
    }

    @Test
    fun testRenamingPropagatesToPatchJack() {
        val patch = PatchModule()
        val cmd = CommandModule()
        patch.graphState.addModule(cmd, Offset.Zero)

        cmd.name = "start"
        assertEquals("start", cmd.name)
        val patchJack = patch.inputs.find { it === cmd.patchJack }
        assertNotNull(patchJack)
        assertTrue(patchJack is CommandJack)
        assertEquals("start", patchJack.name)
    }

    @Test
    fun testKickingPatchCommandJackRoutesThroughCommandModuleOutput() {
        val patch = PatchModule()
        val cmd = CommandModule()
        patch.graphState.addModule(cmd, Offset.Zero)
        cmd.name = "trigger"

        val counter = CounterModule()
        patch.graphState.addModule(counter, Offset.Zero)
        patch.graphState.connect(cmd.output, counter.increment)

        val patchJack = cmd.patchJack as CommandJack
        patchJack.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        assertEquals(1.0, counter.output.getValue())
    }

    @Test
    fun testRemovingCommandModuleCleansUpPatchJack() {
        val patch = PatchModule()
        val cmd = CommandModule()
        patch.graphState.addModule(cmd, Offset.Zero)
        assertEquals(1, patch.inputs.size)

        patch.graphState.removeModulesDirectly(listOf(cmd))
        assertEquals(0, patch.inputs.size)
    }

    @Test
    fun testSerializationAndDeserialization() {
        val rootGraph = GraphState()
        val patch = PatchModule()
        rootGraph.addModule(patch, Offset.Zero)

        val cmd = CommandModule()
        patch.graphState.addModule(cmd, Offset.Zero)
        cmd.name = "reset"

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"type\":\"core.command\"") || json.contains("\"type\": \"core.command\"") || json.contains("\"type\":\"command\"") || json.contains("\"type\": \"command\""))
        assertTrue(json.contains("\"name\":\"reset\"") || json.contains("\"name\": \"reset\""))

        val newRootGraph = GraphState()
        val deserializedModules = GraphSerializer.deserialize(newRootGraph, json)
        assertEquals(1, deserializedModules.size)

        val newPatch = deserializedModules[0] as PatchModule
        assertEquals(1, newPatch.inputs.size)
        assertTrue(newPatch.inputs[0] is CommandJack)
        assertEquals("reset", newPatch.inputs[0].name)

        val deserializedCmd = newPatch.graphState.modules.first() as CommandModule
        assertEquals("reset", deserializedCmd.name)
    }

    @Test
    fun testPolyPatchModuleWithCommandModule() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(4)

        val cmd = CommandModule()
        cmd.name = "kickAll"
        polyPatch.graphState.addModule(cmd, Offset.Zero)

        val patchJack = polyPatch.inputs.find { it.name == "kickAll" }
        assertNotNull(patchJack)
        assertTrue(patchJack is CommandJack)

        var voiceKickedCount = 0
        for (voice in polyPatch.voices) {
            val voiceCmd = voice.templateToVoiceMap[cmd] as? CommandModule
            assertNotNull(voiceCmd)
            voiceCmd.output.connect(object : com.syntona.dataflow.model.InputJack("test", voiceCmd) {
                override fun receive(msg: Message) {
                    voiceKickedCount++
                }
            })
        }

        patchJack.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(4, voiceKickedCount)
    }

    @Test
    fun testRegisteredInModuleRegistry() {
        val creator = ModuleRegistry.findCreator("command")
        assertNotNull(creator, "command module should be registered in ModuleRegistry")
        val instance = creator()
        assertTrue(instance is CommandModule)
        assertEquals("core.command", ModuleRegistry.getBundlePath("command"))
    }
}
