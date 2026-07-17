package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that lights up with a bright yellow circle next to its input jacks.
 * It has "on" and "off" inputs.
 */
class GlowModule : Module("glow") {
    val onInput = addInput("on", isKickable = true)
    val offInput = addInput("off", isKickable = true)

    var isOn by mutableStateOf(false)

    override fun execute(triggerMessage: Message) {
        if (onInput.isKicked) {
            isOn = true
        } else if (offInput.isKicked) {
            isOn = false
        }
    }
}
