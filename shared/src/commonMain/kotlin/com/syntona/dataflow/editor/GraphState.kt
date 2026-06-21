package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Jack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack

/**
 * Manages the UI state of the graph editor, including module positions
 * and anchor points for wires.
 */
class GraphState(private val onModuleAdded: ((Module) -> Unit)? = null) {
    val modules = mutableStateListOf<Module>()
    val modulePositions = mutableStateMapOf<Module, Offset>()

    // Maps each Jack to its global offset on the canvas for drawing wires
    val jackOffsets = mutableStateMapOf<Jack, Offset>()

    var rootOffset by mutableStateOf(Offset.Zero)
        private set

    fun updateRootOffset(offset: Offset) {
        rootOffset = offset
    }

    fun addModule(module: Module, position: Offset) {
        modules.add(module)
        modulePositions[module] = position
        onModuleAdded?.invoke(module)
    }

    fun updateModulePosition(module: Module, delta: Offset) {
        modulePositions[module] = (modulePositions[module] ?: Offset.Zero) + delta
    }

    fun updateJackOffset(jack: Jack, offset: Offset) {
        jackOffsets[jack] = offset
    }

    /**
     * Represents a connection between an OutputJack and an InputJack for UI rendering.
     */
    data class UIConnection(val output: OutputJack, val input: InputJack)

    val connections = mutableStateListOf<UIConnection>()

    // Current state of a wire being dragged
    var draggingWireByOutput by mutableStateOf<OutputJack?>(null)
    var draggingWireToInput by mutableStateOf<InputJack?>(null)
    var dragPointerPosition by mutableStateOf(Offset.Zero)

    // Current state of a module being dragged from the tree
    var draggedModuleCreator by mutableStateOf<(() -> Module)?>(null)
    var draggedModulePosition by mutableStateOf(Offset.Zero)

    // Module currently open in the editor panel
    var editingModule by mutableStateOf<Module?>(null)

    fun startDraggingFromTree(creator: () -> Module, position: Offset) {
        draggedModuleCreator = creator
        draggedModulePosition = position
    }

    fun updateDraggedModulePosition(position: Offset) {
        draggedModulePosition = position
    }

    fun dropModuleFromTree(position: Offset) {
        val creator = draggedModuleCreator
        if (creator != null) {
            addModule(creator(), position - rootOffset)
        }
        draggedModuleCreator = null
    }

    fun connect(output: OutputJack, input: InputJack) {
        output.connect(input)
        if (connections.none { it.output == output && it.input == input }) {
            connections.add(UIConnection(output, input))
        }
    }

    fun disconnect(output: OutputJack, input: InputJack) {
        output.disconnect(input)
        connections.removeAll { it.output == output && it.input == input }
    }

    fun deleteConnection(connection: UIConnection) {
        disconnect(connection.output, connection.input)
    }

    fun startDraggingFromOutput(output: OutputJack) {
        // Many fan-out from an OutputJack are now allowed.
        // Clicking an OutputJack should NOT disconnect any existing wires.
        draggingWireByOutput = output
    }

    fun startDraggingFromInput(input: InputJack) {
        // If an InputJack with a Wire connected is clicked then the first connection should be disconnected from the InputJack
        // and a rubber-band line to the connected OutputJack should be drawn.
        val existing = connections.firstOrNull { it.input == input }
        if (existing != null) {
            val output = existing.output
            deleteConnection(existing)
            draggingWireByOutput = output
        }
    }

    fun onPointerMove(position: Offset) {
        dragPointerPosition = position
    }

    fun onPointerUp(inputJack: InputJack?) {
        val output = draggingWireByOutput
        if (output != null) {
            // Hit test: are we over an input jack?
            val targetInput = inputJack ?: findInputJackAt(dragPointerPosition)
            if (targetInput != null) {
                connect(output, targetInput)
            }
        }
        draggingWireByOutput = null
    }

    private fun findInputJackAt(offset: Offset): InputJack? {
        return jackOffsets.entries
            .filter { it.key is InputJack }
            .minByOrNull { (it.value - offset).getDistance() }
            ?.takeIf { (it.value - offset).getDistance() < 30f }
            ?.key as? InputJack
    }
}
