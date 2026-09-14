package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.CommandJackBadge
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
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
    val tagIn = addIntegerInput("tag").also {
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
    val tagIn = addIntegerInput("tag").also {
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

/**
 * Generates a CONTROL_CHANGE VoiceMessage with value and index.
 * Receiving a message on [valueIn] (kickable) sends a CONTROL_CHANGE immediately.
 *
 * The message's tag is always -1, addressing every note. Per-note controllers are a MIDI 2.0
 * idea and there is nothing behind them here yet, so no tag is offered. The note modules keep
 * their tag because internal intonation depends on it.
 */
class PackControlModule : Module("packControl") {
    val valueIn = addInput("value", isKickable = true)
    val indexIn = addIntegerInput("index").also {
        it.min = 0.0
        it.max = 127.0
    }
    val output = addVoiceOutput("out")

    init {
        valueIn.setValue(0.0)
        indexIn.setValue(0.0)
    }

    override fun execute(triggerMessage: Message) {
        val value = valueIn.getDoubleValue()
        val index = indexIn.getDoubleValue()
        output.send(
            Message.VoiceMessage(
                triggerMessage.time,
                Message.VoiceOpcode.CONTROL_CHANGE,
                tag = -1,
                value = value,
                extra = index
            )
        )
    }
}

/**
 * Generates a PROGRAM_CHANGE VoiceMessage with a program number.
 * Receiving a message on [programIn] (kickable) sends a PROGRAM_CHANGE immediately.
 *
 * The message's tag is always -1: a program change applies to a whole channel, so there is
 * nothing per-note to address until MIDI 2.0 arrives.
 */
class PackProgramModule : Module("packProgram") {
    val programIn = addIntegerInput("program", isKickable = true).also {
        it.min = 0.0
        it.max = 127.0
    }
    val output = addVoiceOutput("out")

    init {
        programIn.setValue(0.0)
    }

    override fun execute(triggerMessage: Message) {
        val program = programIn.getDoubleValue()
        output.send(
            Message.VoiceMessage(
                triggerMessage.time,
                Message.VoiceOpcode.PROGRAM_CHANGE,
                tag = -1,
                value = program,
                extra = 0.0
            )
        )
    }
}

/**
 * Unpacks NOTE_ON VoiceMessages into pitch, tag, and amplitude outputs.
 * Ignores messages that are not NOTE_ON.
 * Outputs fire in order from bottom to top: amplitude, tag, pitch.
 */
class UnpackNoteOnModule : Module("unpackNoteOn") {
    val input = addVoiceInput("in", isKickable = true)
    val pitch = addOutput("pitch")
    val tag = addOutput("tag")
    val amplitude = addOutput("amplitude")

    override fun execute(triggerMessage: Message) {
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode != Message.VoiceOpcode.NOTE_ON) return

        amplitude.send(Message.FloatValue(msg.time, msg.extra))
        tag.send(Message.FloatValue(msg.time, msg.tag.toDouble()))
        pitch.send(Message.FloatValue(msg.time, msg.value))
    }
}

/**
 * Unpacks NOTE_OFF VoiceMessages into pitch, tag, and amplitude outputs.
 * Ignores messages that are not NOTE_OFF.
 * Outputs fire in order from bottom to top: amplitude, tag, pitch.
 */
class UnpackNoteOffModule : Module("unpackNoteOff") {
    val input = addVoiceInput("in", isKickable = true)
    val pitch = addOutput("pitch")
    val tag = addOutput("tag")
    val amplitude = addOutput("amplitude")

    override fun execute(triggerMessage: Message) {
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode != Message.VoiceOpcode.NOTE_OFF) return

        amplitude.send(Message.FloatValue(msg.time, msg.extra))
        tag.send(Message.FloatValue(msg.time, msg.tag.toDouble()))
        pitch.send(Message.FloatValue(msg.time, msg.value))
    }
}

/**
 * Unpacks CONTROL_CHANGE VoiceMessages into value and index outputs.
 * Ignores messages that are not CONTROL_CHANGE.
 * Outputs fire in order from bottom to top: index, value.
 *
 * The message's tag is dropped rather than given a jack, since per-note controllers are a
 * MIDI 2.0 feature this does not support yet.
 */
class UnpackControlModule : Module("unpackControl") {
    val input = addVoiceInput("in", isKickable = true)
    val value = addOutput("value")
    val index = addOutput("index")

    override fun execute(triggerMessage: Message) {
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode != Message.VoiceOpcode.CONTROL_CHANGE) return

        index.send(Message.FloatValue(msg.time, msg.extra))
        value.send(Message.FloatValue(msg.time, msg.value))
    }
}

/**
 * Unpacks PITCH_BEND VoiceMessages into bend and tag outputs.
 * Ignores messages that are not PITCH_BEND.
 * Outputs fire in order from bottom to top: tag, bend.
 */
class UnpackBendModule : Module("unpackBend") {
    val input = addVoiceInput("in", isKickable = true)
    val bend = addOutput("bend")
    val tag = addOutput("tag")

    override fun execute(triggerMessage: Message) {
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode != Message.VoiceOpcode.PITCH_BEND) return

        tag.send(Message.FloatValue(msg.time, msg.tag.toDouble()))
        bend.send(Message.FloatValue(msg.time, msg.value))
    }
}

/**
 * Unpacks PROGRAM_CHANGE VoiceMessages into a program output.
 * Ignores messages that are not PROGRAM_CHANGE.
 *
 * The message's tag is dropped: a program change applies to a whole channel.
 */
class UnpackProgramModule : Module("unpackProgram") {
    val input = addVoiceInput("in", isKickable = true)
    val program = addOutput("program")

    override fun execute(triggerMessage: Message) {
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode != Message.VoiceOpcode.PROGRAM_CHANGE) return

        program.send(Message.FloatValue(msg.time, msg.value))
    }
}

/** Value of a match module's selector jack meaning "not assigned yet; learn from the next one". */
const val MATCH_ARMED = -1.0

/**
 * Listens for one voice message out of the stream and passes on a single number from it.
 *
 * Where the unpack modules forward whatever arrives and leave the sorting to you, these pick one
 * controller or one note and emit only that, so a knob or a drum pad can be wired straight to
 * what it drives.
 *
 * Which one it listens for is [matchIn]. Setting that jack to [MATCH_ARMED] arms the module: the
 * next matching message claims it, and its payload is passed on in the same breath, so the
 * control you reached for is already live by the time you let go. The [learn] button does
 * exactly that - it is the arming gesture made visible, rather than asking anyone to remember
 * that -1 is a magic number.
 *
 * A new module starts armed, since the first thing you want is to tell it which control it is.
 */
abstract class MatchVoiceModule(
    type: String,
    selectorName: String,
    payloadName: String,
    private val opcode: Message.VoiceOpcode
) : Module(type), CommandJackBadge {
    val input = addVoiceInput("in", isKickable = true)
    val learn = addCommandInput("learn")
    val matchIn = addIntegerInput(selectorName).also {
        it.min = MATCH_ARMED
        it.max = 127.0
    }
    val output = addOutput(payloadName)
    val unmatched = addVoiceOutput("unmatched")

    /**
     * Mirrors [matchIn] as Compose state. Jack values are plain fields, so the badge on the node
     * would keep showing "?" after something was learned without this.
     */
    var matchNumber: Int by mutableStateOf(MATCH_ARMED.toInt())
        private set

    init {
        matchIn.setValue(MATCH_ARMED)
        syncIndicators()
    }

    /** Which field of an incoming message says which control it came from. */
    protected abstract fun identifierOf(msg: Message.VoiceMessage): Double

    /** Which field is passed on when a message matches. */
    protected abstract fun payloadOf(msg: Message.VoiceMessage): Double

    /**
     * Whether this message belongs to the matched target (and should therefore be filtered from [unmatched]).
     * By default, checks if [msg.opcode] == [opcode] and [identifierOf] matches [matchNumber].
     */
    protected open fun isTarget(msg: Message.VoiceMessage): Boolean {
        if (msg.opcode != opcode) return false
        val incoming = round(identifierOf(msg)).toInt()
        return incoming == matchNumber
    }

    /** True while waiting to be told what to follow. */
    val isArmed: Boolean get() = matchNumber < 0

    /** Arms the module, so the next matching message claims it. */
    fun arm() {
        matchIn.setValue(MATCH_ARMED)
    }

    /** Shows what the learn button is waiting for, or what it caught. */
    override fun badgeFor(jack: CommandJack): String? =
        if (jack !== learn) null else if (isArmed) "?" else matchNumber.toString()

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        // The selector can also be set by a wire or by the editor, so the button follows the
        // jack rather than the other way round.
        if (jack === matchIn) syncIndicators()
    }

    override fun execute(triggerMessage: Message) {
        if (learn.isKicked) {
            arm()
            return
        }
        val msg = triggerMessage as? Message.VoiceMessage ?: return

        if (isArmed) {
            if (msg.opcode == opcode) {
                val incoming = round(identifierOf(msg)).toInt()
                matchIn.setValue(incoming.toDouble().coerceIn(0.0, matchIn.max))
                output.send(Message.FloatValue(msg.time, payloadOf(msg)))
                return
            } else {
                unmatched.send(msg)
                return
            }
        }

        if (isTarget(msg)) {
            if (msg.opcode == opcode) {
                output.send(Message.FloatValue(msg.time, payloadOf(msg)))
            }
        } else {
            unmatched.send(msg)
        }
    }

    /**
     * Brings the node's indicators into line with the jack: the learn button turns orange while
     * armed, and the badge beside it shows "?" or the number it is following.
     */
    private fun syncIndicators() {
        matchNumber = round(matchIn.getDoubleValue()).toInt()
        learn.isHighlighted = isArmed
    }
}

/**
 * Follows one MIDI controller and emits its value, ignoring every other controller.
 *
 * The controller number travels in a CONTROL_CHANGE's extra field, and its value in the value
 * field. Wire the output to a knob's parameter and turn the hardware knob to teach it.
 */
class MatchControlModule : MatchVoiceModule(
    type = "matchControl",
    selectorName = "index",
    payloadName = "value",
    opcode = Message.VoiceOpcode.CONTROL_CHANGE
) {
    /**
     * Mirrors the output payload as Compose state so the graphic display on the node
     * repaints smoothly when controller values stream in.
     */
    var currentValue by mutableStateOf(0.0)
        private set

    override fun identifierOf(msg: Message.VoiceMessage): Double = msg.extra

    override fun payloadOf(msg: Message.VoiceMessage): Double = msg.value

    override fun execute(triggerMessage: Message) {
        super.execute(triggerMessage)
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode != Message.VoiceOpcode.CONTROL_CHANGE) return
        val incoming = round(identifierOf(msg)).toInt()
        if (incoming == matchNumber) {
            currentValue = payloadOf(msg)
        }
    }
}

/**
 * Follows sequential MIDI controllers and emits their values on separate output jacks.
 *
 * It starts armed (learn button highlighted, matchNumber = -1). When the first [CONTROL_CHANGE] arrives
 * while armed, it assigns that controller's index to the first value output jack, and assigns
 * sequential indices to the remaining output value jacks.
 *
 * Incoming control change values for each matched controller are emitted from the corresponding
 * output jack, and unhandled voice messages pass out [unmatched].
 */
abstract class MatchControlSequentialModule(
    typeName: String,
    val numOutputs: Int
) : Module(typeName), CommandJackBadge {
    val input = addVoiceInput("in", isKickable = true)
    val learn = addCommandInput("learn")
    val matchIn = addIntegerInput("index").also {
        it.min = MATCH_ARMED
        it.max = 127.0
    }
    val stepOutputs: List<OutputJack> = List(numOutputs) { index ->
        addOutput("value$index")
    }
    val unmatched = addVoiceOutput("unmatched")

    val currentValues = mutableStateListOf<Double>().apply {
        repeat(numOutputs) { add(0.0) }
    }

    /** Alias for [stepOutputs]. */
    val valueOutputs: List<OutputJack> get() = stepOutputs

    fun getValue(index: Int): Double = currentValues.getOrElse(index) { 0.0 }
    fun getValueJack(index: Int): OutputJack = stepOutputs[index]
    fun getOutputJack(index: Int): OutputJack = stepOutputs[index]

    // Convenience accessors
    val value0: OutputJack get() = stepOutputs[0]
    val out0: OutputJack get() = stepOutputs[0]
    val value1: OutputJack? get() = stepOutputs.getOrNull(1)
    val out1: OutputJack? get() = stepOutputs.getOrNull(1)
    val value2: OutputJack? get() = stepOutputs.getOrNull(2)
    val out2: OutputJack? get() = stepOutputs.getOrNull(2)
    val value3: OutputJack? get() = stepOutputs.getOrNull(3)
    val out3: OutputJack? get() = stepOutputs.getOrNull(3)
    val value4: OutputJack? get() = stepOutputs.getOrNull(4)
    val out4: OutputJack? get() = stepOutputs.getOrNull(4)
    val value5: OutputJack? get() = stepOutputs.getOrNull(5)
    val out5: OutputJack? get() = stepOutputs.getOrNull(5)
    val value6: OutputJack? get() = stepOutputs.getOrNull(6)
    val out6: OutputJack? get() = stepOutputs.getOrNull(6)
    val value7: OutputJack? get() = stepOutputs.getOrNull(7)
    val out7: OutputJack? get() = stepOutputs.getOrNull(7)

    var matchNumber: Int by mutableStateOf(MATCH_ARMED.toInt())
        private set

    init {
        matchIn.setValue(MATCH_ARMED)
        syncIndicators()
    }

    val isArmed: Boolean get() = matchNumber < 0

    fun arm() {
        matchIn.setValue(MATCH_ARMED)
    }

    fun indexFor(outputIndex: Int): Int =
        if (isArmed || outputIndex !in 0 until numOutputs) -1 else matchNumber + outputIndex

    override fun badgeFor(jack: CommandJack): String? =
        if (jack !== learn) null else if (isArmed) "?" else matchNumber.toString()

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        if (jack === matchIn) syncIndicators()
    }

    private fun syncIndicators() {
        matchNumber = round(matchIn.getDoubleValue()).toInt()
        learn.isHighlighted = isArmed
    }

    override fun execute(triggerMessage: Message) {
        if (learn.isKicked) {
            arm()
            return
        }
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode != Message.VoiceOpcode.CONTROL_CHANGE) {
            unmatched.send(msg)
            return
        }

        val incoming = round(msg.extra).toInt()

        if (isArmed) {
            matchIn.setValue(incoming.toDouble().coerceIn(0.0, matchIn.max))
            stepOutputs[0].send(Message.FloatValue(msg.time, msg.value))
            currentValues[0] = msg.value
            return
        }

        val offset = incoming - matchNumber
        if (offset in 0 until numOutputs) {
            stepOutputs[offset].send(Message.FloatValue(msg.time, msg.value))
            currentValues[offset] = msg.value
        } else {
            unmatched.send(msg)
        }
    }
}

/**
 * Matches 4 sequential MIDI controllers and emits their values on separate output jacks.
 */
class MatchControl4Module : MatchControlSequentialModule("matchControl4", 4)

/**
 * Matches 8 sequential MIDI controllers and emits their values on separate output jacks.
 */
class MatchControl8Module : MatchControlSequentialModule("matchControl8", 8)

/**
 * Follows one note and emits its amplitude on every strike, ignoring every other note.
 * Any notes that do not match are forwarded out the [unmatched] output.
 *
 * For triggering from a drum pad or one key of a keyboard: match on the note, and the amplitude
 * carries how hard it was hit, so a trigger arrives with its dynamics attached. Both NOTE_ON
 * and NOTE_OFF for the matched note are filtered from [unmatched], allowing remaining notes
 * to pass through cleanly to a downstream polyPatch or voice.
 */
class MatchNoteOnModule : MatchVoiceModule(
    type = "matchNoteOn",
    selectorName = "tag",
    payloadName = "amplitude",
    opcode = Message.VoiceOpcode.NOTE_ON
) {
    var triggerCount by mutableStateOf(0)
        private set

    override fun identifierOf(msg: Message.VoiceMessage): Double = msg.tag.toDouble()

    override fun payloadOf(msg: Message.VoiceMessage): Double = msg.extra

    override fun isTarget(msg: Message.VoiceMessage): Boolean {
        if (msg.opcode != Message.VoiceOpcode.NOTE_ON && msg.opcode != Message.VoiceOpcode.NOTE_OFF) {
            return false
        }
        val incoming = round(identifierOf(msg)).toInt()
        return incoming == matchNumber
    }

    override fun execute(triggerMessage: Message) {
        val wasArmed = isArmed
        super.execute(triggerMessage)
        val msg = triggerMessage as? Message.VoiceMessage ?: return
        if (msg.opcode == Message.VoiceOpcode.NOTE_ON) {
            if (wasArmed || isTarget(msg)) {
                triggerCount++
            }
        }
    }
}

/**
 * Follows sequential MIDI notes and emits their amplitude/velocity on separate output jacks.
 *
 * It starts armed (learn button highlighted, matchNumber = -1). When the first [Message.VoiceOpcode.NOTE_ON] arrives
 * while armed, it assigns that note's tag to the first amplitude output jack, and assigns
 * sequential note numbers to the remaining output jacks.
 *
 * Both [Message.VoiceOpcode.NOTE_ON] and [Message.VoiceOpcode.NOTE_OFF] for any matched notes are filtered from [unmatched], allowing
 * remaining notes to pass through cleanly to downstream polyPatches or voices.
 */
abstract class MatchNoteOnSequentialModule(
    typeName: String,
    val numOutputs: Int
) : Module(typeName), CommandJackBadge {
    val input = addVoiceInput("in", isKickable = true)
    val learn = addCommandInput("learn")
    val matchIn = addIntegerInput("tag").also {
        it.min = MATCH_ARMED
        it.max = 127.0
    }
    val stepOutputs: List<OutputJack> = List(numOutputs) { index ->
        addOutput("amplitude$index")
    }
    val unmatched = addVoiceOutput("unmatched")

    val currentAmplitudes = mutableStateListOf<Double>().apply {
        repeat(numOutputs) { add(0.0) }
    }

    val triggerCounts = mutableStateListOf<Int>().apply {
        repeat(numOutputs) { add(0) }
    }

    /** Alias for [stepOutputs]. */
    val amplitudeOutputs: List<OutputJack> get() = stepOutputs

    fun getAmplitude(index: Int): Double = currentAmplitudes.getOrElse(index) { 0.0 }
    fun getTriggerCount(index: Int): Int = triggerCounts.getOrElse(index) { 0 }
    fun getAmplitudeJack(index: Int): OutputJack = stepOutputs[index]
    fun getOutputJack(index: Int): OutputJack = stepOutputs[index]

    // Convenience accessors
    val amplitude0: OutputJack get() = stepOutputs[0]
    val amplitude1: OutputJack? get() = stepOutputs.getOrNull(1)
    val amplitude2: OutputJack? get() = stepOutputs.getOrNull(2)
    val amplitude3: OutputJack? get() = stepOutputs.getOrNull(3)
    val amplitude4: OutputJack? get() = stepOutputs.getOrNull(4)
    val amplitude5: OutputJack? get() = stepOutputs.getOrNull(5)
    val amplitude6: OutputJack? get() = stepOutputs.getOrNull(6)
    val amplitude7: OutputJack? get() = stepOutputs.getOrNull(7)

    var matchNumber: Int by mutableStateOf(MATCH_ARMED.toInt())
        private set

    init {
        matchIn.setValue(MATCH_ARMED)
        syncIndicators()
    }

    val isArmed: Boolean get() = matchNumber < 0

    fun arm() {
        matchIn.setValue(MATCH_ARMED)
    }

    fun indexFor(outputIndex: Int): Int =
        if (isArmed || outputIndex !in 0 until numOutputs) -1 else matchNumber + outputIndex

    override fun badgeFor(jack: CommandJack): String? =
        if (jack !== learn) null else if (isArmed) "?" else matchNumber.toString()

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        if (jack === matchIn) syncIndicators()
    }

    private fun syncIndicators() {
        matchNumber = round(matchIn.getDoubleValue()).toInt()
        learn.isHighlighted = isArmed
    }

    override fun execute(triggerMessage: Message) {
        if (learn.isKicked) {
            arm()
            return
        }
        val msg = triggerMessage as? Message.VoiceMessage ?: return

        if (isArmed) {
            if (msg.opcode == Message.VoiceOpcode.NOTE_ON) {
                val incoming = msg.tag
                matchIn.setValue(incoming.toDouble().coerceIn(0.0, matchIn.max))
                stepOutputs[0].send(Message.FloatValue(msg.time, msg.extra))
                currentAmplitudes[0] = msg.extra
                triggerCounts[0]++
                return
            } else {
                unmatched.send(msg)
                return
            }
        }

        val isNote = msg.opcode == Message.VoiceOpcode.NOTE_ON || msg.opcode == Message.VoiceOpcode.NOTE_OFF
        val offset = msg.tag - matchNumber
        val isMatched = isNote && (offset in 0 until numOutputs)

        if (isMatched) {
            if (msg.opcode == Message.VoiceOpcode.NOTE_ON) {
                stepOutputs[offset].send(Message.FloatValue(msg.time, msg.extra))
                currentAmplitudes[offset] = msg.extra
                triggerCounts[offset]++
            }
        } else {
            unmatched.send(msg)
        }
    }
}

/**
 * Matches 4 sequential MIDI notes and emits their amplitude/velocity on separate output jacks.
 */
class MatchNoteOn4Module : MatchNoteOnSequentialModule("matchNoteOn4", 4)

/**
 * Matches 8 sequential MIDI notes and emits their amplitude/velocity on separate output jacks.
 */
class MatchNoteOn8Module : MatchNoteOnSequentialModule("matchNoteOn8", 8)

