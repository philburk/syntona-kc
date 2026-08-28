package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

abstract class KeyboardModule(type: String, val numNotes: Int) : Module(type), PlayableKeys {
    override val amplitudeIn = addInput("amplitude").also {
        it.min = 0.0
        it.max = 1.0
    }
    override val octaveIn = addIntegerInput("octave").also {
        it.min = MIN_KEY_OCTAVE
        it.max = MAX_KEY_OCTAVE
    }
    val output = addVoiceOutput("out")
    // Roomy enough to play: the keys get the full width, below the row of jacks.
    var width by mutableStateOf(if (numNotes == 13) 420f else 640f)
    var height by mutableStateOf(220f)

    /**
     * Mirrors [octaveIn] as Compose state. Jack values are plain fields, so without this the
     * drawn keys would keep sending their old pitches until something else redrew the node.
     */
    var octave by mutableStateOf(DEFAULT_KEY_OCTAVE)
        private set

    init {
        amplitudeIn.setValue(DEFAULT_KEY_AMPLITUDE)
        octaveIn.setValue(DEFAULT_KEY_OCTAVE.toDouble())
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === octaveIn) octave = octaveIn.getClippedIntValue()
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {
        // Keyboard inputs do not execute based on input jack events.
    }
}

class Keyboard13Module : KeyboardModule("keyboard13", 13)
class Keyboard25Module : KeyboardModule("keyboard25", 25)
