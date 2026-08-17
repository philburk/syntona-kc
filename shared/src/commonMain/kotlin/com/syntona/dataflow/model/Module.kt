package com.syntona.dataflow.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A module that fills in a remembered choice when it is placed in a patch.
 *
 * Applied by [com.syntona.dataflow.editor.GraphState.addModule], which covers both dropping a
 * module from the tree and loading a patch, and deliberately not by the undo path, where the
 * module already carries the state it had when it was deleted.
 *
 * Only for settings the module has none of yet: the implementation is expected to leave an
 * explicit choice alone, since loading applies a patch's own values before this runs.
 */
interface AdoptsRememberedDefaults {
    fun adoptRememberedDefaults()
}

/**
 * Base class for all data flow modules.
 */
abstract class Module(val type: String) {
    open var name by mutableStateOf(type)
    var parentPatch: com.syntona.dataflow.modules.PatchModule? = null

    /**
     * Whether this module is allowed inside a [com.syntona.dataflow.modules.PolyPatchModule].
     * Defaults to true. Override to return false for global hardware or incompatible modules.
     */
    open val isAllowedInPolyPatch: Boolean
        get() = true

    private val _inputs = mutableStateListOf<InputJack>()
    val inputs: List<InputJack> get() = _inputs

    private val _outputs = mutableStateListOf<OutputJack>()
    val outputs: List<OutputJack> get() = _outputs

    private val processingMessages = mutableSetOf<Message>()
    private var safe = true
    protected fun addInput(name: String, isKickable: Boolean = false): InputJack {
        val jack = InputJack(name, this, isKickable)
        _inputs.add(jack)
        return jack
    }

    protected fun addCommandInput(name: String): CommandJack {
        val jack = CommandJack(name, this)
        _inputs.add(jack)
        return jack
    }

    protected fun addOutput(name: String): OutputJack {
        val jack = OutputJack(name, this)
        _outputs.add(jack)
        return jack
    }

    protected fun removeInput(jack: InputJack) {
        _inputs.remove(jack)
    }

    protected fun removeOutput(jack: OutputJack) {
        _outputs.remove(jack)
    }

    protected fun addCommandOutput(name: String): CommandOutputJack {
        val jack = CommandOutputJack(name, this)
        _outputs.add(jack)
        return jack
    }

    protected fun addAudioInput(name: String, port: com.softsynth.ksyn.ports.UnitInputPort, partIndex: Int = 0): AudioInputJack {
        val jack = AudioInputJack(name, this, port, partIndex)
        _inputs.add(jack)
        return jack
    }

    protected fun addAudioOutput(name: String, port: com.softsynth.ksyn.ports.UnitOutputPort, partIndex: Int = 0): AudioOutputJack {
        val jack = AudioOutputJack(name, this, port, partIndex)
        _outputs.add(jack)
        return jack
    }

    protected fun addDataQueueInput(name: String, port: com.softsynth.ksyn.ports.UnitDataQueuePort): DataQueueInputJack {
        val jack = DataQueueInputJack(name, this, port)
        _inputs.add(jack)
        return jack
    }

    protected fun addDataQueueOutput(name: String): DataQueueOutputJack {
        val jack = DataQueueOutputJack(name, this)
        _outputs.add(jack)
        return jack
    }

    protected fun addVoiceInput(name: String, isKickable: Boolean = false): VoiceInputJack {
        val jack = VoiceInputJack(name, this, isKickable)
        _inputs.add(jack)
        return jack
    }

    protected fun addVoiceOutput(name: String): VoiceOutputJack {
        val jack = VoiceOutputJack(name, this)
        _outputs.add(jack)
        return jack
    }

    /**
     * Called when any [InputJack] receives a message.
     * If the jack is kickable, [execute] is called.
     */
    open fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack.isKickable) {
            executeSafely(msg)
        }
    }

    /** Called when this module is removed from a graph. Subclasses override to cancel pending work. */
    open fun teardown() {}

    fun executeSafely(triggerMessage: Message) {
        // prevent recursion in cyclic graphs
        if (safe) {
            safe = false
            execute(triggerMessage)
            safe = true
        }
    }
    /**
     * Triggered by a kickable input. Subclasses implement their logic here.
     * @param triggerMessage The message that triggered the execution.
     */
    abstract fun execute(triggerMessage: Message)
}

fun cleanKotlinIdentifier(input: String): String {
    val alphaNumeric = input.filter { it.isLetterOrDigit() || it == '_' }
    return alphaNumeric.dropWhile { it.isDigit() }
}
