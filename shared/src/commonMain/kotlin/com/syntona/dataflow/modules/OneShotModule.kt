package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class OneShotModule : Module("oneShot") {
    val armInput = addCommandInput("arm")
    val kickInput = addInput("in", isKickable = true)
    val output = addOutput("out")

    var isArmed by mutableStateOf(false)

    override fun execute(triggerMessage: Message) {
        if (armInput.isKicked) {
            isArmed = !isArmed
            armInput.isHighlighted = isArmed
        } else if (kickInput.isKicked) {
            if (isArmed) {
                isArmed = false
                armInput.isHighlighted = false
                output.send(triggerMessage)
            }
        }
    }
}
