package com.syntona.dataflow.model

/**
 * Base class for all data flow modules.
 */
abstract class Module(val name: String) {
    private val _inputs = mutableListOf<InputJack>()
    val inputs: List<InputJack> get() = _inputs

    private val _outputs = mutableListOf<OutputJack>()
    val outputs: List<OutputJack> get() = _outputs

    private val processingMessages = mutableSetOf<Message>()
    private var safe = true
    protected fun addInput(name: String, isKickable: Boolean = false): InputJack {
        val jack = InputJack(name, this, isKickable)
        _inputs.add(jack)
        return jack
    }

    protected fun addOutput(name: String): OutputJack {
        val jack = OutputJack(name, this)
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

    /**
     * Called when any [InputJack] receives a message.
     * If the jack is kickable, [execute] is called.
     */
    open fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack.isKickable) {
            executeSafely(msg)
        }
    }

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
