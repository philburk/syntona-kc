package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

const val DEFAULT_ROW1_KEYS = "12345678"
const val DEFAULT_ROW2_KEYS = "qwertyui"
const val DEFAULT_ROW3_KEYS = "asdfghjk"
const val DEFAULT_ROW4_KEYS = "zxcvbnm,"

const val DEFAULT_KEY_OFFSET = 36
const val MIN_KEY_OFFSET = 0.0
const val MAX_KEY_OFFSET = 80.0

private val DIATONIC_SCALE = intArrayOf(0, 2, 4, 5, 7, 9, 11)

fun diatonicPitchOffset(index: Int): Int {
    if (index < 0) return 0
    return (index / 7) * 12 + DIATONIC_SCALE[index % 7]
}

class KeyNotesModule : Module("keyNotes") {
    val amplitudeIn = addInput("amplitude").also {
        it.min = 0.0
        it.max = 1.0
    }
    val offsetIn = addIntegerInput("offset").also {
        it.min = MIN_KEY_OFFSET
        it.max = MAX_KEY_OFFSET
    }
    val output = addVoiceOutput("out")

    var row1Keys by mutableStateOf(DEFAULT_ROW1_KEYS)
    var row2Keys by mutableStateOf(DEFAULT_ROW2_KEYS)
    var row3Keys by mutableStateOf(DEFAULT_ROW3_KEYS)
    var row4Keys by mutableStateOf(DEFAULT_ROW4_KEYS)

    val activeNotes = mutableMapOf<Char, Int>()

    init {
        amplitudeIn.setValue(DEFAULT_KEY_AMPLITUDE)
        offsetIn.setValue(DEFAULT_KEY_OFFSET.toDouble())
    }

    fun currentAmplitude(): Double = amplitudeIn.getClippedDoubleValue()

    fun basePitch(): Int = offsetIn.getClippedIntValue()

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
                val rows = listOf(row4Keys, row3Keys, row2Keys, row1Keys)
                for (octaveIndex in rows.indices) {
                    val keyIndex = rows[octaveIndex].indexOf(char)
                    if (keyIndex >= 0) {
                        pitchOffset = octaveIndex * 12 + diatonicPitchOffset(keyIndex)
                        found = true
                        break
                    }
                }
                if (found) {
                    var noteNumber = basePitch() + pitchOffset
                    if (isShiftPressed) {
                        noteNumber += 24
                    }
                    if (noteNumber in 0..127) {
                        // Remembered so that releasing the key turns off the note that was struck,
                        // even if the offset jack has moved in the meantime.
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
