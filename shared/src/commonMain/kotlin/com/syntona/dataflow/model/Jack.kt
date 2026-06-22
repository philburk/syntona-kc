package com.syntona.dataflow.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime

/**
 * Represents a connection point on a [Module].
 */
sealed class Jack {
    abstract val name: String
    abstract val module: Module

    // We use a mutable state to update the UI when a message arrives
    var isHighlighted by mutableStateOf(false)

    /** Returns the current scalar value of this jack for display. */
    abstract fun getValue(): Double
}

/**
 * An input port that can receive messages.
 * @property isKickable If true, receiving a message on this jack triggers [Module.execute].
 */
open class InputJack(
    override val name: String,
    override val module: Module,
    val isKickable: Boolean = false
) : Jack() {
    var isKicked = false
        private set

    var lastMessage: Message? = null
        private set

    open fun receive(msg: Message) {
        lastMessage = msg
        isKicked = true
        module.onMessageReceived(this, msg)
        isKicked = false
    }

    /**
     * Convenience to get the current numeric value, or a default if not set or not a FloatValue.
     */
    fun getDoubleValue(default: Double = 0.0): Double {
        val msg = lastMessage
        return if (msg is Message.FloatValue) msg.value else default
    }

    open var min: Double by mutableStateOf(0.0)
    open var max: Double by mutableStateOf(100.0)

    override fun getValue(): Double = getDoubleValue()

    /** Set the value of this jack by delivering a FloatValue message. */
    open fun setValue(value: Double) {
        receive(Message.FloatValue(ScoreTime.Zero, value))
    }
}

/**
 * An input jack rendered as a clickable Button in the UI rather than a value-draggable label.
 * Clicking the button sends a kick message to this jack's module.
 */
class CommandJack(name: String, module: Module) : InputJack(name, module, isKickable = true)

/**
 * An output port that can send messages to connected [InputJack]s.
 */
open class OutputJack(
    override val name: String,
    override val module: Module
) : Jack() {
    private val connections = mutableListOf<InputJack>()

    private var lastSentValue: Double = 0.0

    override fun getValue(): Double = lastSentValue

    open fun connect(inputJack: InputJack) {
        if (!connections.contains(inputJack)) {
            connections.add(inputJack)
        }
    }

    open fun disconnect(inputJack: InputJack) {
        connections.remove(inputJack)
    }

    fun send(msg: Message) {
        if (msg is Message.FloatValue) lastSentValue = msg.value
        connections.forEach { it.receive(msg) }
    }
}
