package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that flashes briefly when kicked.
 * It has only one input called "flash".
 */
class FlashModule : Module("flash") {
    val flashInput = addInput("flash", isKickable = true)

    var isOn by mutableStateOf(false)
    var triggerCount by mutableStateOf(0)
        private set

    override fun execute(triggerMessage: Message) {
        if (flashInput.isKicked) {
            isOn = true
            triggerCount++
        }
    }
}
