package com.syntona.dataflow.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Represents a connection point on a [Module].
 */
sealed class Jack {
    abstract val name: String
    abstract val module: Module

    // We use a mutable state to update the UI when a message arrives
    var isHighlighted by mutableStateOf(false)
}

/**
 * An input port that can receive messages.
 * @property isKickable If true, receiving a message on this jack triggers [Module.execute].
 */
class InputJack(
    override val name: String,
    override val module: Module,
    val isKickable: Boolean = false
) : Jack() {
    var isKicked = false
        private set

    var lastMessage: Message? = null
        private set

    fun receive(msg: Message) {
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
}

/**
 * An output port that can send messages to connected [InputJack]s.
 */
class OutputJack(
    override val name: String,
    override val module: Module
) : Jack() {
    private val connections = mutableListOf<InputJack>()

    fun connect(inputJack: InputJack) {
        if (!connections.contains(inputJack)) {
            connections.add(inputJack)
        }
    }

    fun disconnect(inputJack: InputJack) {
        connections.remove(inputJack)
    }

    fun send(msg: Message) {
        connections.forEach { it.receive(msg) }
    }
}
