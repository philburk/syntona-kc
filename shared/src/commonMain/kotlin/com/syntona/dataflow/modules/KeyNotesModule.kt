package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class KeyNotesModule : Module("keyNotes"), PlayableKeys {
    override val amplitudeIn = addInput("amplitude").also {
        it.min = 0.0
        it.max = 1.0
    }
    override val octaveIn = addInput("octave").also {
        it.min = MIN_KEY_OCTAVE
        it.max = MAX_KEY_OCTAVE
    }
    val output = addVoiceOutput("out")

    var bottomOctaveKeys by mutableStateOf("zsxdcvgbhnjm,")
    var topOctaveKeys by mutableStateOf("q2w3er5t6y7u8io")

    val activeNotes = mutableMapOf<Char, Int>()

    init {
        amplitudeIn.setValue(DEFAULT_KEY_AMPLITUDE)
        octaveIn.setValue(DEFAULT_KEY_OCTAVE.toDouble())
    }

    override fun teardown() {
        activeNotes.clear()
        super.teardown()
    }

    override fun execute(triggerMessage: Message) {
        // Triggered by global key events, no execution on graph kicks
    }

    fun handleKeyStroke(
        char: Char,
        isDown: Boolean,
        isShiftPressed: Boolean,
        time: ScoreTime,
        engine: com.syntona.dataflow.engine.DataFlowEngine?
    ): Boolean {
        var processed = false
        if (isDown) {
            if (!activeNotes.containsKey(char)) {
                var pitchOffset = -1
                var found = false
                val bottomIndex = bottomOctaveKeys.indexOf(char)
                if (bottomIndex >= 0) {
                    pitchOffset = bottomIndex
                    found = true
                } else {
                    val topIndex = topOctaveKeys.indexOf(char)
                    if (topIndex >= 0) {
                        pitchOffset = 12 + topIndex
                        found = true
                    }
                }
                if (found) {
                    var noteNumber = basePitch() + pitchOffset
                    if (isShiftPressed) {
                        noteNumber += 24
                    }
                    // Remembered so that releasing the key turns off the note that was struck,
                    // even if the octave jack has moved in the meantime.
                    activeNotes[char] = noteNumber
                    val msg = Message.VoiceMessage(
                        time = time,
                        opcode = Message.VoiceOpcode.NOTE_ON,
                        tag = noteNumber,
                        value = noteNumber.toDouble(),
                        extra = currentAmplitude()
                    )
                    if (engine != null) {
                        engine.post(com.syntona.dataflow.engine.SendOutputCommand(output, msg))
                    } else {
                        output.send(msg)
                    }
                    processed = true
                }
            } else {
                processed = true
            }
        } else {
            val noteNumber = activeNotes.remove(char)
            if (noteNumber != null) {
                val msg = Message.VoiceMessage(
                    time = time,
                    opcode = Message.VoiceOpcode.NOTE_OFF,
                    tag = noteNumber,
                    value = noteNumber.toDouble(),
                    extra = currentAmplitude()
                )
                if (engine != null) {
                    engine.post(com.syntona.dataflow.engine.SendOutputCommand(output, msg))
                } else {
                    output.send(msg)
                }
                processed = true
            }
        }
        return processed
    }
}
