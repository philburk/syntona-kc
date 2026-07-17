package com.syntona.dataflow.modules

import androidx.compose.runtime.mutableStateListOf
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlin.math.round

/**
 * Composes a [Message.VoiceMessage] from four inputs: opcode, tag, value, extra.
 * Receiving a message on [tagIn] (kickable) sends the composed message from [output].
 */
class PackVoiceModule : Module("packVoice") {
    val opcodeIn = addInput("opcode").also {
        it.min = 0.0
        it.max = (Message.VoiceOpcode.entries.size - 1).toDouble()
    }
    val tagIn = addInput("tag", isKickable = true)
    val valueIn = addInput("value")
    val extraIn = addInput("extra")
    val output = addVoiceOutput("out")

    init {
        opcodeIn.setValue(Message.VoiceOpcode.NOTE_ON.ordinal.toDouble())
    }

    var opcode: Message.VoiceOpcode
        get() = Message.VoiceOpcode.entries.getOrElse(opcodeIn.getDoubleValue().toInt()) {
            Message.VoiceOpcode.NOTE_ON
        }
        set(newOpcode) { opcodeIn.setValue(newOpcode.ordinal.toDouble()) }

    override fun execute(triggerMessage: Message) {
        output.send(
            Message.VoiceMessage(
                time = triggerMessage.time,
                opcode = opcode,
                tag = tagIn.getDoubleValue().toInt(),
                value = valueIn.getDoubleValue(),
                extra = extraIn.getDoubleValue()
            )
        )
    }
}

/**
 * Composes a timed note from four inputs: pitch, tag, amplitude, duration. Receiving a message
 * on [pitchIn] (kickable) sends a NOTE_ON immediately, followed by a matching NOTE_OFF scheduled
 * [durationIn] milliseconds later, using the same engine-scheduling technique as [AddTimeModule].
 *
 * If [tagIn] is left at its default of -1, the tag is resolved to `floor(pitch)` so untagged
 * notes still pair their NOTE_ON/NOTE_OFF by pitch.
 */
class PackNoteModule : Module("packNote"), EngineAware {
    val pitchIn = addInput("pitch", isKickable = true)
    val tagIn = addInput("tag").also {
        it.min = -1.0
        it.max = 127.0
    }
    val amplitudeIn = addInput("amplitude").also {
        it.min = 0.0
        it.max = 2.0
    }
    val durationIn = addInput("duration").also {
        it.min = 0.0
        it.max = 1000.0
    }
    val output = addVoiceOutput("out")

    private var engine: DataFlowEngine? = null

    init {
        tagIn.setValue(-1.0)
        amplitudeIn.setValue(0.2)
        durationIn.setValue(20.0) // millis
    }

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun execute(triggerMessage: Message) {
        val pitch = pitchIn.getDoubleValue()
        val amplitude = amplitudeIn.getDoubleValue()
        val rawTag = tagIn.getDoubleValue().toInt()
        val tag = if (rawTag == -1) round(pitch).toInt() else rawTag

        output.send(Message.VoiceMessage(triggerMessage.time, Message.VoiceOpcode.NOTE_ON, tag, pitch, amplitude))

        val durationNs = (durationIn.getDoubleValue() * 1_000_000.0).toLong().coerceAtLeast(0L)
        val fireTime = triggerMessage.time + ScoreTime(durationNs)
        val noteOff = Message.VoiceMessage(fireTime, Message.VoiceOpcode.NOTE_OFF, tag, pitch, amplitude)
        val e = engine
        if (e != null) {
            e.schedule(fireTime) { output.send(noteOff) }
        } else {
            output.send(noteOff)
        }
    }
}

/**
 * Generates a PITCH_BEND VoiceMessage with bned and tag.
 * Receiving a message on [bend] (kickable) sends a PITCH_BEND immediately.
 */
class PackBendModule : Module("packBend"), EngineAware {
    val bendIn = addInput("bend", isKickable = true).also {
        it.min = -2.0
        it.max = 2.0
    }
    val tagIn = addInput("tag").also {
        it.min = -1.0
        it.max = 127.0
    }
    val output = addVoiceOutput("out")

    private var engine: DataFlowEngine? = null

    init {
        tagIn.setValue(-1.0)
        bendIn.setValue(0.0)
    }

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun execute(triggerMessage: Message) {
        val bend = bendIn.getDoubleValue()
        val tag = tagIn.getDoubleValue().toInt()
        output.send(Message.VoiceMessage(triggerMessage.time,
            Message.VoiceOpcode.PITCH_BEND,
            tag, bend, 0.0))
    }
}
