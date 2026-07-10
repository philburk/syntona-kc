package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

abstract class KeyboardModule(type: String, val numNotes: Int) : Module(type) {
    val output = addVoiceOutput("out")
    var width by mutableStateOf(if (numNotes == 13) 240f else 400f)
    var height by mutableStateOf(150f)

    override fun execute(triggerMessage: Message) {
        // Keyboard inputs do not execute based on input jack events.
    }
}

class Keyboard13Module : KeyboardModule("keyboard13", 13)
class Keyboard25Module : KeyboardModule("keyboard25", 25)
