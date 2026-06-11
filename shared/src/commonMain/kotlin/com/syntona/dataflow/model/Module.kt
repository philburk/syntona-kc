package com.syntona.dataflow.model

/**
 * Base class for all data flow modules.
 */
abstract class Module(val name: String) {
    private val _inputs = mutableListOf<InputJack>()
    val inputs: List<InputJack> get() = _inputs

    private val _outputs = mutableListOf<OutputJack>()
    val outputs: List<OutputJack> get() = _outputs

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

    /**
     * Called when any [InputJack] receives a message.
     * If the jack is kickable, [execute] is called.
     */
    open fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack.isKickable) {
            execute(msg)
        }
    }

    /**
     * Triggered by a kickable input. Subclasses implement their logic here.
     * @param triggerMessage The message that triggered the execution.
     */
    abstract fun execute(triggerMessage: Message)
}
