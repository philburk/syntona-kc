package com.syntona.dataflow.model

import com.syntona.core.model.ScoreTime

/**
 * Base type for messages passed between modules in the data flow graph.
 * Messages are immutable and contain a [ScoreTime] for precise scheduling.
 */
sealed class Message {
    abstract val time: ScoreTime

    /** A message carrying a floating-point value (e.g., Frequency, Volume). */
    data class FloatValue(override val time: ScoreTime, val value: Double) : Message() {
        override fun toString(): String = formatVal(value)
    }

    /** A message carrying a text string (e.g., Command, File Path). */
    data class TextValue(override val time: ScoreTime, val value: String) : Message() {
        override fun toString(): String = value
    }

    /** A message carrying an arbitrary object (e.g., Waveform data, Sysex buffer). */
    data class ObjectValue(override val time: ScoreTime, val value: Any?) : Message() {
        override fun toString(): String = value?.toString() ?: "null"
    }

    enum class DataQueueOpcode {
        QUEUE, QUEUE_ON, QUEUE_OFF
    }

    /** A message carrying a data object and an opcode for a DataQueue. */
    data class DataQueueMessage(
        override val time: ScoreTime,
        val data: Any,
        val opcode: DataQueueOpcode
    ) : Message() {
        override fun toString(): String = "$opcode data=$data"
    }

    /** Commands for [VoiceMessage], modeled after MIDI Channel Voice Messages. */
    enum class VoiceOpcode {
        NOTE_ON, NOTE_OFF, CONTROL_CHANGE, PRESSURE, PITCH_BEND
    }

    /**
     * A compound message for note and controller events. The meaning of [tag], [value], and
     * [extra] depends on [opcode]:
     *
     * - NOTE_ON / NOTE_OFF: tag = noteNumber, value = pitch in fractional semitones,
     *   extra = amplitude (0.0 to 1.0)
     * - CONTROL_CHANGE: tag = noteNumber, or -1 for all notes; value = controller value;
     *   extra = which controller to change
     * - PRESSURE: tag = noteNumber, or -1 for all notes; value = pressure value; extra ignored
     * - PITCH_BEND: tag = noteNumber, or -1 for all notes; value = offset in semitones;
     *   extra ignored
     */
    data class VoiceMessage(
        override val time: ScoreTime,
        val opcode: VoiceOpcode,
        val tag: Int,
        val value: Double,
        val extra: Double
    ) : Message() {
        override fun toString(): String = "$opcode t=$tag v=${formatVal(value)} x=${formatVal(extra)}"
    }
}

private fun formatVal(v: Double): String {
    if (!v.isFinite()) return v.toString()
    if (v == 0.0) return "0"
    val longVal = v.toLong()
    if (longVal.toDouble() == v) return longVal.toString()
    val factor = 1000.0
    val rounded = kotlin.math.round(v * factor) / factor
    return rounded.toString().removeSuffix(".0")
}
