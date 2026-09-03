package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.BooleanInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvSegModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = EnvSegModule()
        assertEquals("envSeg", module.name)

        assertEquals(5, module.inputs.size)
        assertEquals("on", module.inputs[0].name)
        assertTrue(module.inputs[0] is CommandJack)
        assertEquals("off", module.inputs[1].name)
        assertTrue(module.inputs[1] is CommandJack)
        assertEquals("looping", module.inputs[2].name)
        assertTrue(module.inputs[2] is BooleanInputJack)
        assertFalse(module.inputs[2].isValueDraggable)
        assertFalse(module.inputs[2].isDisplayableValue)
        assertEquals("speed", module.inputs[3].name)
        assertTrue(module.inputs[3] is AudioInputJack)
        assertEquals("amplitude", module.inputs[4].name)
        assertTrue(module.inputs[4] is AudioInputJack)

        assertEquals(1, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
        assertTrue(module.outputs[0] is AudioOutputJack)

        module.teardown()
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("envSeg")
        assertNotNull(creator, "envSeg should be registered in ModuleRegistry")
        val module = creator()
        assertTrue(module is EnvSegModule)
        module.teardown()
    }

    @Test
    fun testCommandsAndLooping() {
        val module = EnvSegModule()
        val now = ScoreTime.Zero

        assertFalse(module.isLooping)
        module.loopInput.isChecked = true
        assertTrue(module.isLooping)

        module.onIn.receive(Message.FloatValue(now, 1.0))
        module.offIn.receive(Message.FloatValue(now, 0.0))

        module.teardown()
    }

    @Test
    fun testSerializationRoundTrip() {
        val graph = GraphState()
        val envSeg = EnvSegModule()
        envSeg.speed.setValue(2.0)
        envSeg.amplitude.setValue(0.75)
        envSeg.loopInput.isChecked = true

        graph.addModule(envSeg, Offset(100f, 100f))

        val json = GraphSerializer.serialize(graph, graph.modules)
        assertTrue(json.contains("ksyn.data.envSeg") || json.contains("envSeg"))
        assertTrue(json.contains("envData"))

        val newGraph = GraphState()
        val restored = GraphSerializer.deserialize(newGraph, json)

        val restoredEnvSeg = restored.find { it is EnvSegModule } as? EnvSegModule
        assertNotNull(restoredEnvSeg)
        assertEquals(2.0, restoredEnvSeg.speed.getValue())
        assertEquals(0.75, restoredEnvSeg.amplitude.getValue())
        assertEquals(1.0, restoredEnvSeg.loopInput.getValue())
        assertTrue(restoredEnvSeg.isLooping)

        restoredEnvSeg.teardown()
        envSeg.teardown()
    }

    @Test
    fun testLegacySegEnvAndMonoRdrMigration() {
        val legacyJson = """
        {
          "modules": [
            {
              "id": 0,
              "type": "ksyn.data.segEnv",
              "name": "segEnv",
              "x": 100.0,
              "y": 150.0,
              "jacks": {},
              "envData": [0.02, 0.9, 0.10, 0.5, 0.50, 0.0],
              "sustainBegin": 2,
              "sustainEnd": 2,
              "releaseBegin": -1,
              "releaseEnd": -1
            },
            {
              "id": 1,
              "type": "ksyn.data.monoRdr",
              "name": "monoRdr",
              "x": 250.0,
              "y": 150.0,
              "jacks": {
                "amplitude": {
                  "v": 0.85,
                  "min": 0.0,
                  "max": 1.0
                },
                "rate": {
                  "v": 1.5,
                  "min": 0.0,
                  "max": 8.0
                }
              }
            },
            {
              "id": 2,
              "type": "core.command",
              "name": "start",
              "x": 20.0,
              "y": 100.0,
              "jacks": {}
            }
          ],
          "connections": [
            {
              "from": 0,
              "fromJack": "output",
              "to": 1,
              "toJack": "data"
            },
            {
              "from": 2,
              "fromJack": "out",
              "to": 0,
              "toJack": "queueOn"
            }
          ]
        }
        """.trimIndent()

        val graph = GraphState()
        val modules = GraphSerializer.deserialize(graph, legacyJson)

        assertEquals(2, modules.size, "Should have 2 modules: start and merged envSeg")
        val envSeg = modules.find { it is EnvSegModule } as? EnvSegModule
        assertNotNull(envSeg, "segEnv + monoRdr should be converted into EnvSegModule")
        assertEquals(0.85, envSeg.amplitude.getValue(), 1e-4)
        assertEquals(1.5, envSeg.speed.getValue(), 1e-4)
        assertEquals(2, envSeg.envelope.sustainBegin)

        // Check connection: start.out -> envSeg.on
        assertEquals(1, graph.connections.size)
        val conn = graph.connections.first()
        assertEquals("on", conn.input.name)
        assertEquals(envSeg, conn.input.module)

        envSeg.teardown()
    }

    @Test
    fun testLegacySegEnvLoopConnectionMigration() {
        val legacyJson = """
        {
          "modules": [
            {
              "id": 0,
              "type": "ksyn.data.segEnv",
              "name": "segEnv",
              "x": 100.0,
              "y": 150.0,
              "jacks": {}
            },
            {
              "id": 1,
              "type": "ksyn.data.monoRdr",
              "name": "monoRdr",
              "x": 250.0,
              "y": 150.0,
              "jacks": {}
            },
            {
              "id": 2,
              "type": "core.command",
              "name": "start",
              "x": 20.0,
              "y": 100.0,
              "jacks": {}
            }
          ],
          "connections": [
            {
              "from": 0,
              "fromJack": "output",
              "to": 1,
              "toJack": "data"
            },
            {
              "from": 2,
              "fromJack": "out",
              "to": 0,
              "toJack": "loop"
            }
          ]
        }
        """.trimIndent()

        val graph = GraphState()
        val modules = GraphSerializer.deserialize(graph, legacyJson)

        val envSeg = modules.find { it is EnvSegModule } as? EnvSegModule
        assertNotNull(envSeg)
        assertTrue(envSeg.isLooping, "loop connection should check the looping checkbox")
        assertTrue(envSeg.loopInput.isChecked)

        // Check connection: start.out -> envSeg.on
        assertEquals(1, graph.connections.size)
        val conn = graph.connections.first()
        assertEquals("on", conn.input.name, "loop connection should connect to on")
        assertEquals(envSeg, conn.input.module)

        envSeg.teardown()
    }

    @Test
    fun testLegacySegEnvQueueOnlyDisablesSustainLoop() {
        val legacyJson = """
        {
          "modules": [
            {
              "id": 0,
              "type": "ksyn.data.segEnv",
              "name": "segEnv",
              "x": 100.0,
              "y": 150.0,
              "jacks": {},
              "envData": [0.02, 0.9, 0.10, 0.5, 0.50, 0.0],
              "sustainBegin": 2,
              "sustainEnd": 2
            },
            {
              "id": 1,
              "type": "ksyn.data.monoRdr",
              "name": "monoRdr",
              "x": 250.0,
              "y": 150.0,
              "jacks": {}
            },
            {
              "id": 2,
              "type": "core.command",
              "name": "start",
              "x": 20.0,
              "y": 100.0,
              "jacks": {}
            }
          ],
          "connections": [
            {
              "from": 0,
              "fromJack": "output",
              "to": 1,
              "toJack": "data"
            },
            {
              "from": 2,
              "fromJack": "out",
              "to": 0,
              "toJack": "queue"
            }
          ]
        }
        """.trimIndent()

        val graph = GraphState()
        val modules = GraphSerializer.deserialize(graph, legacyJson)

        val envSeg = modules.find { it is EnvSegModule } as? EnvSegModule
        assertNotNull(envSeg)
        assertFalse(envSeg.isLooping, "queue-only connection should not check the looping checkbox")
        assertFalse(envSeg.loopInput.isChecked)
        assertEquals(-1, envSeg.envelope.sustainBegin, "sustainBegin should be disabled (-1) for queue-only one-shot")
        assertEquals(-1, envSeg.envelope.sustainEnd, "sustainEnd should be disabled (-1) for queue-only one-shot")

        // Check connection: start.out -> envSeg.on
        assertEquals(1, graph.connections.size)
        val conn = graph.connections.first()
        assertEquals("on", conn.input.name, "queue connection should connect to on")
        assertEquals(envSeg, conn.input.module)

        envSeg.teardown()
    }
}
