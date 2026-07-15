package com.syntona.dataflow.editor

import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.CounterModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.model.Module
import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.registry.ModuleRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PatchFileReferenceTest {

    @Test
    fun testChangeTrackingAndPropagating() {
        val root = PatchModule("root")
        val child = PatchModule("child")
        root.graphState.addModule(child, Offset.Zero)

        // Reset modified flags (since adding child module modifies root)
        root.isModified = false
        child.isModified = false

        assertFalse(root.isModified)
        assertFalse(child.isModified)

        // Add a module inside child to trigger a change
        val counter = CounterModule()
        child.graphState.addModule(counter, Offset.Zero)

        // Both child and root should be marked modified/dirty
        assertTrue(child.isModified)
        assertTrue(root.isModified)
    }

    @Test
    fun testFindModifiedReferencedPatches() {
        val root = PatchModule("root")
        val child1 = PatchModule("child1")
        val child2 = PatchModule("child2")
        val grandChild = PatchModule("grandChild")

        root.graphState.addModule(child1, Offset.Zero)
        root.graphState.addModule(child2, Offset.Zero)
        child1.graphState.addModule(grandChild, Offset.Zero)

        // Give them file paths, but keep them unmodified initially
        child1.filePath = "/path/to/child1.json"
        child2.filePath = "/path/to/child2.json"
        grandChild.filePath = "/path/to/grandChild.json"

        child1.isModified = false
        child2.isModified = false
        grandChild.isModified = false

        assertEquals(0, root.findModifiedReferencedPatches().size)

        // Modify grandChild
        val counter = CounterModule()
        grandChild.graphState.addModule(counter, Offset.Zero)

        // Both grandChild and child1 are modified (propagation). Both are referenced.
        val modified = root.findModifiedReferencedPatches()
        assertEquals(2, modified.size)
        assertTrue(modified.contains(child1))
        assertTrue(modified.contains(grandChild))
    }

    @Test
    fun testSerializationOfReferencedPatches() {
        val root = PatchModule("root")
        val child = PatchModule("child")
        root.graphState.addModule(child, Offset.Zero)

        val counter = CounterModule()
        child.graphState.addModule(counter, Offset.Zero)

        child.filePath = "/path/to/child.json"

        // Scenario A: referenced but modified (e.g. before save) -> serializes both path and patch content
        child.isModified = true
        val jsonModified = GraphSerializer.serialize(root.graphState, root.graphState.modules)
        assertTrue(jsonModified.contains("\"filePath\": \"/path/to/child.json\""))
        assertTrue(jsonModified.contains("\"patch\":"))

        // Scenario B: referenced and NOT modified -> serializes only path reference
        child.isModified = false
        val jsonUnmodified = GraphSerializer.serialize(root.graphState, root.graphState.modules)
        assertTrue(jsonUnmodified.contains("\"filePath\": \"/path/to/child.json\""))
        assertFalse(jsonUnmodified.contains("\"patch\":"))
    }

    @Test
    fun testDeserializationFallbackAndDirectLoad() {
        // Create a temporary file on the local file system for JVM test
        val childContent = """
            {"modules":[{"id":0,"type":"counter","name":"myCounter","x":0.0,"y":0.0}],"connections":[]}
        """.trimIndent()
        val tempFile = File.createTempFile("child_patch_test", ".json")
        tempFile.writeText(childContent)
        tempFile.deleteOnExit()

        val rootJson = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "patch",
                  "name": "mySubPatch",
                  "x": 10.0,
                  "y": 20.0,
                  "filePath": "${tempFile.absolutePath.replace("\\", "\\\\")}"
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val rootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(rootGraph, rootJson)
        assertEquals(1, deserialized.size)

        val subPatch = deserialized[0] as PatchModule
        assertEquals(tempFile.absolutePath, subPatch.filePath)
        assertFalse(subPatch.isModified)

        // Verify that the child patch's contents were loaded successfully from the file
        assertEquals(1, subPatch.graphState.modules.size)
        assertEquals("myCounter", subPatch.graphState.modules[0].name)
    }

    @Test
    fun testReplaceModulePreservesConnections() {
        val graph = GraphState()
        val counter1 = CounterModule()
        val oldPatch = PatchModule("oldPatch")
        val counter2 = CounterModule()

        graph.addModule(counter1, Offset(10f, 20f))
        graph.addModule(oldPatch, Offset(100f, 200f))
        graph.addModule(counter2, Offset(300f, 400f))

        // Add child Input/Output modules to oldPatch to automatically create jacks
        val inputModule1 = InputModule()
        inputModule1.name = "in1"
        oldPatch.graphState.addModule(inputModule1, Offset.Zero)
        val patchIn = oldPatch.inputs.find { it.name == "in1" }!!

        val outputModule1 = OutputModule()
        outputModule1.name = "out1"
        oldPatch.graphState.addModule(outputModule1, Offset.Zero)
        val patchOut = oldPatch.outputs.find { it.name == "out1" }!!

        // Create connections
        val outJack = counter1.output
        val inJack = counter2.increment

        graph.connect(outJack, patchIn)
        graph.connect(patchOut, inJack)

        assertEquals(2, graph.connections.size)

        // Replaced module
        val newPatch = PolyPatchModule("newPatch")

        val inputModule2 = InputModule()
        inputModule2.name = "in1"
        newPatch.graphState.addModule(inputModule2, Offset.Zero)

        val outputModule2 = OutputModule()
        outputModule2.name = "out1"
        newPatch.graphState.addModule(outputModule2, Offset.Zero)

        // Replace oldPatch with newPatch
        graph.replaceModule(oldPatch, newPatch)

        // Verify that newPatch is in graph, and has replaced oldPatch at the correct coordinates
        assertTrue(graph.modules.contains(newPatch))
        assertFalse(graph.modules.contains(oldPatch))
        assertEquals(Offset(100f, 200f), graph.modulePositions[newPatch])

        // Verify connections mapped successfully
        assertEquals(2, graph.connections.size)
        val conn1 = graph.connections.find { it.output == outJack }
        assertNotNull(conn1)
        assertEquals(newPatch.inputs.find { it.name == "in1" }, conn1.input)

        val conn2 = graph.connections.find { it.input == inJack }
        assertNotNull(conn2)
        assertEquals(newPatch.outputs.find { it.name == "out1" }, conn2.output)
    }

    @Test
    fun testMdiActiveWindowTracking() {
        val mdiState = MdiState()
        val patch1 = PatchModule("patch1")
        val patch2 = PatchModule("patch2")

        val window1 = MdiWindowInfo(
            id = patch1,
            title = patch1.name,
            graphState = patch1.graphState,
            content = {}
        )
        val window2 = MdiWindowInfo(
            id = patch2,
            title = patch2.name,
            graphState = patch2.graphState,
            content = {}
        )

        mdiState.open(window1)
        assertEquals(patch1.graphState, mdiState.activeGraphState)

        mdiState.open(window2)
        assertEquals(patch2.graphState, mdiState.activeGraphState)

        mdiState.bringToFront(window1)
        assertEquals(patch1.graphState, mdiState.activeGraphState)
    }

    @Test
    fun testDeserializeWrappedPatchFile() {
        val json = """
            {
              "type": "patch",
              "patch": {
                "modules": [
                  {"id": 0, "type": "counter", "name": "myCounter", "x": 0.0, "y": 0.0}
                ],
                "connections": []
              }
            }
        """.trimIndent()

        val graph = GraphState()
        val deserialized = GraphSerializer.deserialize(graph, json)
        assertEquals(1, deserialized.size)
        assertEquals("myCounter", deserialized[0].name)
    }

    @Test
    fun testClearModifiedRecursively() {
        val root = PatchModule("root")
        val child = PatchModule("child")
        val grandchild = PatchModule("grandchild")

        root.graphState.addModule(child, Offset.Zero)
        child.graphState.addModule(grandchild, Offset.Zero)

        grandchild.isModified = true
        child.isModified = true
        root.isModified = true

        assertTrue(grandchild.isModified)
        assertTrue(child.isModified)
        assertTrue(root.isModified)

        root.clearModifiedRecursively()

        assertFalse(grandchild.isModified)
        assertFalse(child.isModified)
        assertFalse(root.isModified)
    }

    @Test
    fun testTooltipTextGeneration() {
        val root = PatchModule("root")
        val text = getModuleTooltipText(root)
        assertEquals("core.patch", text)

        val poly = PolyPatchModule("poly")
        val polyText = getModuleTooltipText(poly)
        assertEquals("core.polyPatch", polyText)

        val clockEntry = ModuleRegistry.root.subBundles.find { it.name == "core" }
            ?.subBundles?.find { it.name == "basic" }
            ?.modules?.find { it.name == "clock" }
        assertNotNull(clockEntry)
        val entryText = getModuleEntryTooltipText(clockEntry!!)
        assertEquals("core.basic.clock", entryText)
    }

    @Test
    fun testDoubleToNumberDeserializationMapping() {
        val json = """
            {
                "modules": [
                    {
                        "id": 0,
                        "type": "double",
                        "name": "myDouble",
                        "x": 10.0,
                        "y": 20.0,
                        "jacks": {
                            "in": 42.5
                        }
                    }
                ],
                "connections": []
            }
        """.trimIndent()
        val graph = GraphState()
        val deserialized = GraphSerializer.deserialize(graph, json)
        assertEquals(1, deserialized.size)
        val numModule = deserialized[0]
        assertTrue(numModule is com.syntona.dataflow.modules.NumberModule)
        assertEquals("number", numModule.type)
        assertEquals("myDouble", numModule.name)
        assertEquals(42.5, numModule.value)
    }
}
