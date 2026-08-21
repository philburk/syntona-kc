package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.AdoptsRememberedDefaults
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.DataQueueOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Jack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.modules.KickModule
import com.syntona.dataflow.modules.LoadKickModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.KeyModule
import com.syntona.dataflow.modules.KeyMatchModule
import com.syntona.dataflow.modules.KeyNotesModule
import com.syntona.getEventCharCode
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed

/**
 * Manages the UI state of the graph editor, including module positions
 * and anchor points for wires.
 */
class GraphState(private val onModuleAdded: ((Module) -> Unit)? = null) {
    var engine: DataFlowEngine? = null
        set(value) {
            field = value
            if (value != null) {
                for (module in modules) {
                    if (module is EngineAware) {
                        module.setEngine(value)
                    }
                }
            }
        }
    var onChange: (() -> Unit)? = null
    var isDeserializing = false
    var parentPatch: PatchModule? = null

    var onModuleAddedCallback: ((Module) -> Unit)? = null
    var onModuleRemovedCallback: ((Module) -> Unit)? = null
    private val moduleRemovedListeners = mutableListOf<(Module) -> Unit>()

    fun addModuleRemovedListener(listener: (Module) -> Unit) {
        if (!moduleRemovedListeners.contains(listener)) {
            moduleRemovedListeners.add(listener)
        }
    }

    fun removeModuleRemovedListener(listener: (Module) -> Unit) {
        moduleRemovedListeners.remove(listener)
    }

    var onConnectionAddedCallback: ((OutputJack, InputJack) -> Unit)? = null
    var onConnectionRemovedCallback: ((OutputJack, InputJack) -> Unit)? = null

    private fun notifyChanged() { onChange?.invoke() }

    fun markChanged() { notifyChanged() }

    val modules = mutableStateListOf<Module>()
    val modulePositions = mutableStateMapOf<Module, Offset>()
    val moduleSizes = mutableStateMapOf<Module, Offset>()

    // Maps each Jack to its global offset on the canvas for drawing wires
    val jackOffsets = mutableStateMapOf<Jack, Offset>()
    val jackBounds = mutableStateMapOf<Jack, Rect>()
    private val pressedKeyCodes = mutableSetOf<Int>()

    var rootOffset by mutableStateOf(Offset.Zero)
        private set

    fun updateRootOffset(offset: Offset) {
        rootOffset = offset
    }

    fun addModule(module: Module, position: Offset) {
        val isPolyPatchChild = parentPatch is com.syntona.dataflow.modules.PolyPatchModule
        if (isPolyPatchChild && !module.isAllowedInPolyPatch) {
            return
        }
        onModuleAdded?.invoke(module)  // runs before Compose observes the module
        if (module is EngineAware) engine?.let { module.setEngine(it) }
        // After a loaded patch has applied its own values, so an explicit choice still wins.
        if (module is AdoptsRememberedDefaults) module.adoptRememberedDefaults()
        modules.add(module)
        modulePositions[module] = position
        notifyChanged()
        onModuleAddedCallback?.invoke(module)
        if (module is com.syntona.dataflow.modules.PolyPatchModule && !module.isInitialized) {
            com.syntona.dataflow.modules.PolyPatchPrompter.activePromptModule = module
            com.syntona.dataflow.modules.PolyPatchPrompter.activePromptGraphState = this
        }
    }

    // Used by DeleteModulesCommand.undo() to re-add modules without posting to engine
    internal fun addModuleDirectly(module: Module, position: Offset) {
        onModuleAdded?.invoke(module)
        if (module is EngineAware) engine?.let { module.setEngine(it) }
        modules.add(module)
        modulePositions[module] = position
        onModuleAddedCallback?.invoke(module)
    }

    // Used by DeleteModulesCommand.undo() to re-create connections without posting to engine
    internal fun connectDirectly(output: OutputJack, input: InputJack) {
        output.connect(input)
        if (connections.none { it.output == output && it.input == input }) {
            connections.add(UIConnection(output, input))
        }
        if (output !is AudioOutputJack) {
            val currentValue = when (val m = output.module) {
                is com.syntona.dataflow.modules.KnobModule -> m.value
                is com.syntona.dataflow.modules.FaderModule -> m.value
                is com.syntona.dataflow.modules.NumberModule -> m.value
                is com.syntona.dataflow.modules.InputModule -> if (m.hasValue) m.defaultValue else null
                else -> if (output.getValue() != 0.0) output.getValue() else null
            }
            if (currentValue != null) {
                input.receive(Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, currentValue))
            }
        }
        syncRangeFromConnections(output)
        onConnectionAddedCallback?.invoke(output, input)
    }

    // Core deletion logic used by DeleteModulesCommand.execute()
    fun removeModulesDirectly(modulesToRemove: List<Module>) {
        if (modulesToRemove.isEmpty()) return
        modulesToRemove.forEach { module ->
            moduleRemovedListeners.forEach { it(module) }
        }
        modulesToRemove.forEach { module ->
            module.teardown()
            val connsToRemove = connections.filter { it.output.module === module || it.input.module === module }
            connsToRemove.forEach { conn ->
                disconnect(conn.output, conn.input)
            }
            module.inputs.forEach {
                jackOffsets.remove(it)
                jackBounds.remove(it)
            }
            module.outputs.forEach {
                jackOffsets.remove(it)
                jackBounds.remove(it)
            }
            if (editingModule === module) editingModule = null
            modulePositions.remove(module)
            moduleSizes.remove(module)
            modules.remove(module)
            onModuleRemovedCallback?.invoke(module)
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

    fun updateJackOffset(jack: Jack, offset: Offset, size: Size = Size.Zero) {
        jackOffsets[jack] = offset
        if (size != Size.Zero) {
            val left = if (jack is InputJack) offset.x else offset.x - size.width
            val top = offset.y - size.height / 2f
            jackBounds[jack] = Rect(left, top, left + size.width, top + size.height)
        } else {
            jackBounds[jack] = Rect(offset.x - 20f, offset.y - 20f, offset.x + 20f, offset.y + 20f)
        }
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

    fun selectModulesInRect(rectStart: Offset, rectEnd: Offset, density: Float) {
        val left = kotlin.math.min(rectStart.x, rectEnd.x)
        val top = kotlin.math.min(rectStart.y, rectEnd.y)
        val right = kotlin.math.max(rectStart.x, rectEnd.x)
        val bottom = kotlin.math.max(rectStart.y, rectEnd.y)

        clearSelection()

        modules.forEach { module ->
            val posDp = modulePositions[module] ?: Offset.Zero
            val posX = posDp.x * density
            val posY = posDp.y * density
            val size = moduleSizes[module]
            val width = size?.x ?: (150f * density)
            val height = size?.y ?: (250f * density)
            // Check if module rect overlaps selection rect
            if (posX < right && posX + width > left &&
                posY < bottom && posY + height > top) {
                selectModule(module)
            }
        }
    }

    fun clearAllModules() {
        removeModulesDirectly(modules.toList())
    }

    fun replaceModule(oldModule: Module, newModule: Module) {
        val position = modulePositions[oldModule] ?: Offset.Zero
        val size = moduleSizes[oldModule] ?: Offset.Zero

        // Find all connections that involve oldModule
        val oldConns = connections.filter { it.output.module === oldModule || it.input.module === oldModule }

        // Remove oldModule
        removeModulesDirectly(listOf(oldModule))

        // Add newModule
        addModuleDirectly(newModule, position)
        if (size != Offset.Zero) {
            moduleSizes[newModule] = size
        }

        // Reconnect
        oldConns.forEach { conn ->
            if (conn.output.module === oldModule) {
                val newOutput = newModule.outputs.find { it.name == conn.output.name }
                if (newOutput != null) {
                    connectDirectly(newOutput, conn.input)
                }
            } else if (conn.input.module === oldModule) {
                val newInput = newModule.inputs.find { it.name == conn.input.name }
                if (newInput != null) {
                    connectDirectly(conn.output, newInput)
                }
            }
        }

        notifyChanged()
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

    fun onKeyEvent(event: KeyEvent): Boolean {
        val rawCharCode = getEventCharCode(event)
        if (rawCharCode == 0 || rawCharCode == 65535) return false

        val charCode = rawCharCode and 0x7F
        if (charCode == 0) return false

        val isDown = when (event.type) {
            KeyEventType.KeyDown -> true
            KeyEventType.KeyUp -> false
            else -> return false
        }

        return handleKeyStroke(
            charCode,
            isDown,
            event.isShiftPressed,
            isCommandPressed = event.isMetaPressed || event.isCtrlPressed
        )
    }

    /**
     * @param isCommandPressed true when Cmd or Ctrl is held, in which case the keystroke is an
     *   application shortcut rather than a note and is left for the caller to handle.
     */
    fun handleKeyStroke(
        charCode: Int,
        isDown: Boolean,
        isShiftPressed: Boolean = false,
        isCommandPressed: Boolean = false
    ): Boolean {
        // Never treat a command keystroke as a musical key. Without this, Cmd+C is identical to
        // playing the note "C": the graph consumes it and the Copy shortcut never runs. It also
        // used to leave the letter stuck in pressedKeyCodes, because macOS delivers no KeyUp for
        // a letter while Cmd is held, so every later Cmd+C was dropped below as an auto-repeat.
        // That is why copying worked once or twice and then stopped for the rest of the session.
        if (isCommandPressed) return false

        if (isDown) {
            if (pressedKeyCodes.contains(charCode)) {
                return true // Ignore auto-repeat
            }
            pressedKeyCodes.add(charCode)
        } else {
            pressedKeyCodes.remove(charCode)
        }

        val e = engine
        val time = ScoreClock.now()
        var processed = false

        fun dispatch(module: Module) {
            if (module is KeyModule) {
                val jack = if (isDown) module.on else module.off
                val msg = Message.FloatValue(time, charCode.toDouble())
                if (e != null) {
                    e.post(SendOutputCommand(jack, msg))
                } else {
                    jack.send(msg)
                }
                processed = true
            } else if (module is KeyMatchModule) {
                if (charCode.toChar().uppercaseChar() == module.keyChar.uppercaseChar()) {
                    val jack = if (isDown) module.on else module.off
                    val msg = Message.FloatValue(time, charCode.toDouble())
                    if (e != null) {
                        e.post(SendOutputCommand(jack, msg))
                    } else {
                        jack.send(msg)
                    }
                    processed = true
                }
            } else if (module is KeyNotesModule) {
                val char = charCode.toChar().lowercaseChar()
                if (module.handleKeyStroke(char, isDown, isShiftPressed, time, e)) {
                    processed = true
                }
            } else if (module is PatchModule) {
                module.graphState.modules.forEach { dispatch(it) }
            }
        }
        modules.forEach { dispatch(it) }
        return processed
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
        if (output is DataQueueOutputJack && input !is DataQueueInputJack) return
        if (input is DataQueueInputJack && output !is DataQueueOutputJack) return
        output.connect(input)
        if (connections.none { it.output == output && it.input == input }) {
            connections.add(UIConnection(output, input))
        }
        syncRangeFromConnections(output)
        notifyChanged()
        onConnectionAddedCallback?.invoke(output, input)
    }

    fun disconnect(output: OutputJack, input: InputJack) {
        output.disconnect(input)
        connections.removeAll { it.output == output && it.input == input }
        syncRangeFromConnections(output)
        notifyChanged()
        onConnectionRemovedCallback?.invoke(output, input)
    }

    private fun syncRangeFromConnections(output: OutputJack) {
        if (isDeserializing) return
        val connected = connections.filter { it.output === output }
        if (connected.isEmpty()) return

        when (val m = output.module) {
            is FaderModule -> {
                if (connected.size == 1) {
                    val target = connected.first().input
                    m.valueIn.min = target.min
                    m.valueIn.max = target.max
                    val low = minOf(target.min, target.max)
                    val high = maxOf(target.min, target.max)
                    m.setValue(target.getValue().coerceIn(low, high))
                } else {
                    m.valueIn.min = connected.minOf { it.input.min }
                    m.valueIn.max = connected.maxOf { it.input.max }
                }
            }
            is KnobModule -> {
                if (connected.size == 1) {
                    val target = connected.first().input
                    m.valueIn.min = target.min
                    m.valueIn.max = target.max
                    val low = minOf(target.min, target.max)
                    val high = maxOf(target.min, target.max)
                    m.setValue(target.getValue().coerceIn(low, high))
                } else {
                    m.valueIn.min = connected.minOf { it.input.min }
                    m.valueIn.max = connected.maxOf { it.input.max }
                }
            }
            is InputModule -> {
                if (connected.size == 1) {
                    val target = connected.first().input
                    m.defaultMin = target.min
                    m.defaultMax = target.max
                    val low = minOf(target.min, target.max)
                    val high = maxOf(target.min, target.max)
                    m.defaultValue = target.getValue().coerceIn(low, high)
                } else {
                    m.defaultMin = connected.minOf { it.input.min }
                    m.defaultMax = connected.maxOf { it.input.max }
                }
            }
            is AudioInputModule -> {
                if (connected.size == 1) {
                    val target = connected.first().input
                    m.defaultMin = target.min
                    m.defaultMax = target.max
                    val low = minOf(target.min, target.max)
                    val high = maxOf(target.min, target.max)
                    m.defaultValue = target.getValue().coerceIn(low, high)
                } else {
                    m.defaultMin = connected.minOf { it.input.min }
                    m.defaultMax = connected.maxOf { it.input.max }
                }
            }
            else -> return
        }
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

    fun startDraggingFromOutput(output: OutputJack, isShiftPressed: Boolean = false) {
        // Many fan-out from an OutputJack are allowed.
        // If Shift is pressed, disconnect the last (most recent) connection from this OutputJack.
        if (isShiftPressed) {
            val existing = connections.lastOrNull { it.output == output }
            if (existing != null) {
                deleteConnection(existing)
                markChanged()
                FileState.markDirty()
            }
        }
        draggingWireByOutput = output
    }

    fun startDraggingFromInput(input: InputJack) {
        // If an InputJack with a Wire connected is clicked then the last connection should be disconnected from the InputJack
        // and a rubber-band line to the connected OutputJack should be drawn.
        val existing = connections.lastOrNull { it.input == input }
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
        val hit = jackBounds.entries
            .filter { it.key is InputJack }
            .firstOrNull { it.value.inflate(8f).contains(offset) }
            ?.key as? InputJack
        if (hit != null) return hit

        return jackOffsets.entries
            .filter { it.key is InputJack }
            .minByOrNull { (it.value - offset).getDistance() }
            ?.takeIf { (it.value - offset).getDistance() < 30f }
            ?.key as? InputJack
    }
}
