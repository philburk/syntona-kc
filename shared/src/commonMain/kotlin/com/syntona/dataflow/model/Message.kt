package com.syntona.dataflow.model

import com.syntona.core.model.ScoreTime

/**
 * Base type for messages passed between modules in the data flow graph.
 * Messages are immutable and contain a [ScoreTime] for precise scheduling.
 */
sealed class Message {
    abstract val time: ScoreTime

    /** A message carrying a floating-point value (e.g., Frequency, Volume). */
    data class FloatValue(override val time: ScoreTime, val value: Double) : Message()

    /** A message carrying a text string (e.g., Command, File Path). */
    data class TextValue(override val time: ScoreTime, val value: String) : Message()

    /** A message carrying an arbitrary object (e.g., Waveform data, Sysex buffer). */
    data class ObjectValue(override val time: ScoreTime, val value: Any?) : Message()
}
