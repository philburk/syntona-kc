package com.syntona.dataflow.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime

/**
 * Represents a connection point on a [Module].
 */
sealed class Jack {
    abstract var name: String
    abstract val module: Module

    // We use a mutable state to update the UI when a message arrives
    var isHighlighted by mutableStateOf(false)

    /** Returns the current scalar value of this jack for display. */
    abstract fun getValue(): Double

    /** Whether this jack treats values as discrete integers. */
    abstract var isInteger: Boolean

    /** Whether this jack represents a displayable numeric scalar value. */
    open val isDisplayableValue: Boolean get() = true

    /** Whether an unconnected input jack can have its numeric value adjusted by click-dragging. */
    open val isValueDraggable: Boolean get() = false

    /** Whether this jack appears as a customizable row in ModuleEditor. */
    open val isModuleEditorVisible: Boolean get() = false
}

/**
 * An input port that can receive messages.
 * @property isKickable If true, receiving a message on this jack triggers [Module.execute].
 * @property isInteger If true, values on this jack are treated as discrete integers.
 */
open class InputJack(
    name: String,
    override val module: Module,
    isKickable: Boolean = false,
    isInteger: Boolean = false
) : Jack() {
    override val isValueDraggable: Boolean get() = true
    override val isModuleEditorVisible: Boolean get() = true
    override var name by mutableStateOf(name)
    private var _isKickable by mutableStateOf(isKickable)
    open var isKickable: Boolean
        get() = _isKickable
        set(value) {
            _isKickable = value
            val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
            polyPatch?.syncInputJackKickable(this, value)
        }

    private var _isInteger by mutableStateOf(isInteger)
    override var isInteger: Boolean
        get() = _isInteger
        set(value) {
            _isInteger = value
            val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
            polyPatch?.syncInputJackInteger(this, value)
        }

    var isKicked = false
        private set

    var lastMessage: Message? = null
        private set

    open fun receive(msg: Message) {
        lastMessage = msg
        isKicked = true
        module.onMessageReceived(this, msg)
        isKicked = false
        val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
        if (polyPatch != null && msg is Message.FloatValue) {
            polyPatch.syncInputJackValue(this, msg.value)
        }
    }

    /**
     * Convenience to get the current numeric value, or a default if not set or not a FloatValue.
     */
    fun getDoubleValue(default: Double = 0.0): Double {
        val msg = lastMessage
        return if (msg is Message.FloatValue) msg.value else default
    }

    private var _min by mutableStateOf(0.0)
    open var min: Double
        get() = _min
        set(value) {
            _min = value
            val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
            polyPatch?.syncInputJackMin(this, value)
        }

    private var _max by mutableStateOf(100.0)
    open var max: Double
        get() = _max
        set(value) {
            _max = value
            val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
            polyPatch?.syncInputJackMax(this, value)
        }

    override fun getValue(): Double = getDoubleValue()

    /** Set the value of this jack by delivering a FloatValue message. */
    open fun setValue(value: Double) {
        receive(Message.FloatValue(ScoreTime.Zero, value))
    }

    fun getClippedIntValue(): Int {
        val low = minOf(min.toInt(), max.toInt())
        val high = maxOf(min.toInt(), max.toInt())
        return getDoubleValue().toInt().coerceIn(low, high)
    }
    fun getClippedDoubleValue(): Double {
        val low = minOf(min, max)
        val high = maxOf(min, max)
        return getDoubleValue().coerceIn(low, high)
    }
}

/**
 * An input jack rendered as a clickable Button in the UI rather than a value-draggable label.
 * Clicking the button sends a kick message to this jack's module.
 */
/**
 * A module that shows a short status next to one of its command buttons.
 *
 * For a button whose effect is not visible on the module otherwise: matchControl's learn button
 * shows "?" while it waits and the controller number once it has one, so the assignment can be
 * read off the patch instead of hunting for it in the editor.
 */
interface CommandJackBadge {
    /** Text to show beside [jack], or null for no badge. */
    fun badgeFor(jack: CommandJack): String?
}

class CommandJack(name: String, module: Module) : InputJack(name, module, isKickable = true) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false
    override var name: String
        get() = if (module is com.syntona.dataflow.modules.LinkModule) module.buttonText else super.name
        set(value) { super.name = value }
}

/**
 * An [InputJack] that treats values as discrete integers.
 */
open class IntegerInputJack(
    name: String,
    module: Module,
    isKickable: Boolean = false
) : InputJack(name, module, isKickable = isKickable, isInteger = true)

/**
 * An [InputJack] that represents a discrete 0 or 1 boolean state.
 * Rendered with an inline interactive Checkbox in the graph canvas when unconnected.
 * Not editable via mouse drag and excluded from ModuleEditor.
 */
open class BooleanInputJack(
    name: String,
    module: Module,
    isKickable: Boolean = false
) : IntegerInputJack(name, module, isKickable = isKickable) {
    init {
        min = 0.0
        max = 1.0
    }

    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false

    private var _isChecked by mutableStateOf(false)
    var isChecked: Boolean
        get() = _isChecked
        set(value) {
            _isChecked = value
            val doubleVal = if (value) 1.0 else 0.0
            if (getDoubleValue() != doubleVal) {
                setValue(doubleVal)
            }
        }

    override fun receive(msg: Message) {
        if (msg is Message.FloatValue) {
            _isChecked = msg.value > 0.0
        }
        super.receive(msg)
    }
}

/**
 * An output port that can send messages to connected [InputJack]s.
 */
open class OutputJack(
    name: String,
    override val module: Module,
    isInteger: Boolean = false
) : Jack() {
    override var name by mutableStateOf(name)
    override var isInteger by mutableStateOf(isInteger)
    private val connections = mutableListOf<InputJack>()

    private var lastSentValue: Double by mutableStateOf(0.0)

    override fun getValue(): Double = lastSentValue

    open fun connect(inputJack: InputJack) {
        if (!connections.contains(inputJack)) {
            connections.add(inputJack)
        }
    }

    open fun disconnect(inputJack: InputJack) {
        connections.remove(inputJack)
    }

    open fun send(msg: Message) {
        if (msg is Message.FloatValue) lastSentValue = msg.value
        connections.forEach { it.receive(msg) }
    }
}

/**
 * An output jack rendered as a clickable Button in the UI rather than a label.
 * Clicking the button sends a kick message from this jack.
 */
class CommandOutputJack(name: String, module: Module) : OutputJack(name, module) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false
}

class VoiceInputJack(name: String, module: Module, isKickable: Boolean = false) : InputJack(name, module, isKickable) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false
}

class VoiceOutputJack(name: String, module: Module) : OutputJack(name, module) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false
}

/**
 * An input port that accepts any message type (e.g. VoiceMessage, FloatValue, DataQueueMessage).
 */
class UniversalInputJack(name: String, module: Module, isKickable: Boolean = false) : InputJack(name, module, isKickable) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false
}

/**
 * An output port that can send any message type (e.g. from pass-through or delay modules).
 */
class UniversalOutputJack(name: String, module: Module) : OutputJack(name, module) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false
}

