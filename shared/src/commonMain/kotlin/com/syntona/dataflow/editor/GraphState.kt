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
class GraphState {
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

    fun connect(output: OutputJack, input: InputJack) {
        output.connect(input)
        connections.add(UIConnection(output, input))
    }
}
