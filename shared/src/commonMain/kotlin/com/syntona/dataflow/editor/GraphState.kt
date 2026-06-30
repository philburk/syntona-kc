package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Jack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.modules.KickModule
import com.syntona.dataflow.modules.LoadKickModule
import com.syntona.dataflow.modules.PatchModule

/**
 * Manages the UI state of the graph editor, including module positions
 * and anchor points for wires.
 */
class GraphState(private val onModuleAdded: ((Module) -> Unit)? = null) {
    var engine: DataFlowEngine? = null
    var onChange: (() -> Unit)? = null

    private fun notifyChanged() { onChange?.invoke() }

    fun markChanged() { notifyChanged() }

    val modules = mutableStateListOf<Module>()
    val modulePositions = mutableStateMapOf<Module, Offset>()
    val moduleSizes = mutableStateMapOf<Module, Offset>()

    // Maps each Jack to its global offset on the canvas for drawing wires
    val jackOffsets = mutableStateMapOf<Jack, Offset>()

    var rootOffset by mutableStateOf(Offset.Zero)
        private set

    fun updateRootOffset(offset: Offset) {
        rootOffset = offset
    }

    fun addModule(module: Module, position: Offset) {
        onModuleAdded?.invoke(module)  // runs before Compose observes the module
        if (module is EngineAware) engine?.let { module.setEngine(it) }
        modules.add(module)
        modulePositions[module] = position
        notifyChanged()
    }

    // Used by DeleteModulesCommand.undo() to re-add modules without posting to engine
    internal fun addModuleDirectly(module: Module, position: Offset) {
        onModuleAdded?.invoke(module)
        if (module is EngineAware) engine?.let { module.setEngine(it) }
        modules.add(module)
        modulePositions[module] = position
    }

    // Used by DeleteModulesCommand.undo() to re-create connections without posting to engine
    internal fun connectDirectly(output: OutputJack, input: InputJack) {
        output.connect(input)
        if (connections.none { it.output == output && it.input == input }) {
            connections.add(UIConnection(output, input))
        }
        syncRangeFromConnections(output)
    }

    // Core deletion logic used by DeleteModulesCommand.execute()
    internal fun removeModulesDirectly(modulesToRemove: List<Module>) {
        modulesToRemove.forEach { module ->
            module.teardown()
            connections
                .filter { it.output.module === module || it.input.module === module }
                .forEach { it.output.disconnect(it.input) }
            connections.removeAll { it.output.module === module || it.input.module === module }
            module.inputs.forEach { jackOffsets.remove(it) }
            module.outputs.forEach { jackOffsets.remove(it) }
            if (editingModule === module) editingModule = null
            modulePositions.remove(module)
            moduleSizes.remove(module)
            modules.remove(module)
        }
        selectedModules.removeAll(modulesToRemove)
        notifyChanged()
    }

    fun updateModulePosition(module: Module, delta: Offset) {
        modulePositions[module] = (modulePositions[module] ?: Offset.Zero) + delta
        notifyChanged()
    }

    fun updateModuleSize(module: Module, width: Float, height: Float) {
        moduleSizes[module] = Offset(width, height)
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

    // Selection state
    val selectedModules = mutableStateListOf<Module>()
    var selectionRectStart by mutableStateOf<Offset?>(null)
    var selectionRectEnd by mutableStateOf<Offset?>(null)

    fun selectModule(module: Module) {
        if (!selectedModules.contains(module)) {
            selectedModules.add(module)
        }
    }

    fun deselectModule(module: Module) {
        selectedModules.remove(module)
    }

    fun clearSelection() {
        selectedModules.clear()
    }

    fun toggleModuleSelection(module: Module) {
        if (selectedModules.contains(module)) {
            deselectModule(module)
        } else {
            selectModule(module)
        }
    }

    fun selectModulesInRect(rectStart: Offset, rectEnd: Offset) {
        val left = kotlin.math.min(rectStart.x, rectEnd.x)
        val top = kotlin.math.min(rectStart.y, rectEnd.y)
        val right = kotlin.math.max(rectStart.x, rectEnd.x)
        val bottom = kotlin.math.max(rectStart.y, rectEnd.y)

        clearSelection()

        modules.forEach { module ->
            val pos = modulePositions[module] ?: Offset.Zero
            val size = moduleSizes[module]
            val width = size?.x ?: 150f
            val height = size?.y ?: 250f
            // Check if module rect overlaps selection rect
            if (pos.x < right && pos.x + width > left &&
                pos.y < bottom && pos.y + height > top) {
                selectModule(module)
            }
        }
    }

    fun clearAllModules() {
        removeModulesDirectly(modules.toList())
    }

    fun kickLoadKickModules() {
        val e = engine
        val time = ScoreClock.now()
        fun kickInModule(module: Module) {
            if (module is LoadKickModule) {
                if (e != null) {
                    e.post(SendOutputCommand(
                        module.output,
                        Message.FloatValue(time, 0.0)
                    ))
                } else {
                    module.kick(time)
                }
            } else if (module is PatchModule) {
                module.graphState.modules.forEach { kickInModule(it) }
            }
        }
        modules.forEach { kickInModule(it) }
    }

    fun kickModule(module: Module) {
        val e = engine
        val time = ScoreClock.now()
        when (module) {
            is KickModule -> {
                if (e != null) {
                    e.post(SendOutputCommand(module.output, Message.FloatValue(time, 0.0)))
                } else {
                    module.kick(time)
                }
            }
            is LoadKickModule -> {
                if (e != null) {
                    e.post(SendOutputCommand(module.output, Message.FloatValue(time, 0.0)))
                } else {
                    module.kick(time)
                }
            }
        }
    }

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
        // Audio-rate outputs carry no messages — block connection to non-audio inputs
        if (output is AudioOutputJack && input !is AudioInputJack) return
        output.connect(input)
        if (connections.none { it.output == output && it.input == input }) {
            connections.add(UIConnection(output, input))
        }
        syncRangeFromConnections(output)
        notifyChanged()
    }

    fun disconnect(output: OutputJack, input: InputJack) {
        output.disconnect(input)
        connections.removeAll { it.output == output && it.input == input }
        syncRangeFromConnections(output)
        notifyChanged()
    }

    private fun syncRangeFromConnections(output: OutputJack) {
        val valueIn = when (val m = output.module) {
            is FaderModule -> m.valueIn
            is KnobModule -> m.valueIn
            else -> return
        }
        val connected = connections.filter { it.output === output }
        if (connected.isEmpty()) return
        valueIn.min = connected.minOf { it.input.min }
        valueIn.max = connected.maxOf { it.input.max }
    }

    fun deleteConnection(connection: UIConnection) {
        disconnect(connection.output, connection.input)
    }

    fun deleteSelectedModules() {
        val toDelete = selectedModules.toList()
        if (toDelete.isEmpty()) return
        val e = engine
        if (e != null) {
            val savedPositions = toDelete.associateWith { modulePositions[it] ?: Offset.Zero }
            val savedConnections = connections.filter { conn ->
                toDelete.any { it === conn.output.module || it === conn.input.module }
            }
            e.post(DeleteModulesCommand(this, toDelete, savedPositions, savedConnections))
        } else {
            removeModulesDirectly(toDelete)
        }
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
