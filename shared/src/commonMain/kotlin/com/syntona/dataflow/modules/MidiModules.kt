/*
 * Copyright 2026 Phil Burk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.softsynth.midibridge.MidiClock
import com.softsynth.midibridge.MidiDestination
import com.softsynth.midibridge.MidiEvent
import com.softsynth.midibridge.Midi1Messages
import com.softsynth.midibridge.controller
import com.softsynth.midibridge.controllerValue7
import com.softsynth.midibridge.data1
import com.softsynth.midibridge.data2
import com.softsynth.midibridge.isNoteOff
import com.softsynth.midibridge.isNoteOn
import com.softsynth.midibridge.messageStatus
import com.softsynth.midibridge.note
import com.softsynth.midibridge.pitchBendFraction
import com.softsynth.midibridge.velocityFraction
import com.softsynth.midibridge.midi1.Midi1
import com.softsynth.midibridge.ump.Ump
import com.syntona.core.midi.MidiDeviceService
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.round

/**
 * Semitones spanned by a MIDI 1.0 pitch bend at either extreme, at the default bend sensitivity.
 * VoiceMessage carries a bend in semitones, so a conversion factor is unavoidable, and every
 * synthesizer powers up at +/- 2 semitones unless an RPN says otherwise.
 */
private const val PITCH_BEND_SEMITONES = 2.0

/** Largest value a MIDI 1.0 data byte can hold. */
private const val MIDI_7BIT_MAX = 127

/** How long the input poller idles when no MIDI is waiting. */
private const val POLL_INTERVAL_MS = 2L

/** Rounds a fractional pitch to the nearest MIDI 1.0 note number. */
private fun toMidiNote(pitch: Double): Int = round(pitch).toInt().coerceIn(0, MIDI_7BIT_MAX)

/** Converts a 0.0 to 1.0 fraction to a MIDI 1.0 data byte. */
private fun toMidi7Bit(fraction: Double): Int =
    round(fraction * MIDI_7BIT_MAX).toInt().coerceIn(0, MIDI_7BIT_MAX)

/** Converts a MIDI 1.0 data byte to a 0.0 to 1.0 fraction. */
private fun fromMidi7Bit(value: Int): Double = value.toDouble() / MIDI_7BIT_MAX

/**
 * Receives MIDI from an external device and emits [Message.VoiceMessage]s.
 *
 * The device is chosen by name in the module editor. Polling runs in its own coroutine rather
 * than on the data flow engine, and each converted message is posted back to the engine as a
 * command so that the graph is only ever touched from the engine coroutine.
 */
class MidiInModule : Module("midiIn"), EngineAware {
    val output = addVoiceOutput("out")

    /** Name of the source device to listen to, or empty for none. */
    var deviceName: String by mutableStateOf("")
        private set

    private var engine: DataFlowEngine? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollJob: Job? = null

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    fun selectDevice(name: String) {
        if (name == deviceName) return
        deviceName = name
        restartPolling()
    }

    override fun teardown() {
        pollJob?.cancel()
        pollJob = null
        scope.cancel()
    }

    /** Nothing kicks a midiIn; it is driven by the device. */
    override fun execute(triggerMessage: Message) {}

    private fun restartPolling() {
        pollJob?.cancel()
        val name = deviceName
        if (name.isEmpty()) {
            pollJob = null
            return
        }
        pollJob = scope.launch {
            val source = MidiDeviceService.openSource(name) ?: return@launch
            try {
                val event = MidiEvent()
                while (isActive && source.isOpen) {
                    var delivered = false
                    while (source.next(event)) {
                        delivered = true
                        toVoiceMessage(event, ScoreClock.now())?.let { emit(it) }
                    }
                    if (!delivered) delay(POLL_INTERVAL_MS)
                }
            } finally {
                source.close()
            }
        }
    }

    private fun emit(message: Message.VoiceMessage) {
        val e = engine
        if (e != null) {
            e.post(SendOutputCommand(output, message))
        } else {
            Snapshot.withMutableSnapshot { output.send(message) }
        }
    }
}

/**
 * Converts an incoming MIDI 1.0 channel voice message to a [Message.VoiceMessage], or returns
 * null for anything with no VoiceMessage equivalent, such as clock and program change.
 *
 * Channel is discarded: VoiceMessage has no channel field, and its tag identifies a note rather
 * than a MIDI channel.
 */
internal fun toVoiceMessage(event: MidiEvent, time: ScoreTime): Message.VoiceMessage? {
    if (event.messageType != Ump.MT_MIDI1_CHANNEL_VOICE) return null
    return when {
        event.isNoteOn -> Message.VoiceMessage(
            time, Message.VoiceOpcode.NOTE_ON,
            tag = event.note,
            value = event.note.toDouble(),
            extra = event.velocityFraction.toDouble()
        )
        event.isNoteOff -> Message.VoiceMessage(
            time, Message.VoiceOpcode.NOTE_OFF,
            tag = event.note,
            value = event.note.toDouble(),
            extra = 0.0
        )
        event.messageStatus == Midi1.CONTROL_CHANGE -> Message.VoiceMessage(
            time, Message.VoiceOpcode.CONTROL_CHANGE,
            tag = -1,
            value = fromMidi7Bit(event.controllerValue7),
            extra = event.controller.toDouble()
        )
        event.messageStatus == Midi1.POLY_PRESSURE -> Message.VoiceMessage(
            time, Message.VoiceOpcode.PRESSURE,
            tag = event.note,
            value = fromMidi7Bit(event.data2),
            extra = 0.0
        )
        event.messageStatus == Midi1.CHANNEL_PRESSURE -> Message.VoiceMessage(
            time, Message.VoiceOpcode.PRESSURE,
            tag = -1,
            value = fromMidi7Bit(event.data1),
            extra = 0.0
        )
        event.messageStatus == Midi1.PITCH_BEND -> Message.VoiceMessage(
            time, Message.VoiceOpcode.PITCH_BEND,
            tag = -1,
            value = event.pitchBendFraction.toDouble() * PITCH_BEND_SEMITONES,
            extra = 0.0
        )
        else -> null
    }
}

/**
 * Sends [Message.VoiceMessage]s to an external MIDI device as MIDI 1.0.
 *
 * The device is chosen by name in the module editor. Fractional pitches are rounded to the
 * nearest MIDI note number, since MIDI 1.0 has nowhere to put the fraction.
 */
class MidiOutModule : Module("midiOut") {
    val input = addVoiceInput("in")

    /** Name of the destination device to send to, or empty for none. */
    var deviceName: String by mutableStateOf("")
        private set

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var destination: MidiDestination? = null
    private var openJob: Job? = null

    fun selectDevice(name: String) {
        if (name == deviceName) return
        deviceName = name
        reopenDestination()
    }

    override fun teardown() {
        openJob?.cancel()
        openJob = null
        destination?.close()
        destination = null
        scope.cancel()
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === input && msg is Message.VoiceMessage) {
            sendVoiceMessage(msg)
        }
        super.onMessageReceived(jack, msg)
    }

    /** Nothing kicks a midiOut; it forwards whatever arrives on [input]. */
    override fun execute(triggerMessage: Message) {}

    private fun reopenDestination() {
        openJob?.cancel()
        destination?.close()
        destination = null
        val name = deviceName
        if (name.isEmpty()) {
            openJob = null
            return
        }
        openJob = scope.launch {
            destination = MidiDeviceService.openDestination(name)
        }
    }

    /**
     * Sends now rather than at [Message.time]. ScoreTime counts from an app level mark while
     * MIDI timestamps are on [MidiClock]'s timeline, and there is no mapping between the two
     * yet - the same limitation PolyPatchModule has when it plays a VoiceMessage.
     */
    private fun sendVoiceMessage(msg: Message.VoiceMessage) {
        val dest = destination ?: return
        dest.send(toMidi1Word(msg), MidiClock.nowNanos())
    }
}

/**
 * Converts a [Message.VoiceMessage] to the single Universal MIDI Packet word that carries it as
 * a MIDI 1.0 channel voice message, on channel 0.
 *
 * Pitch arrives in fractional semitones and leaves as a note number, rounded to the nearest
 * semitone because MIDI 1.0 has nowhere to put the fraction. Controller and pressure values are
 * treated as 0.0 to 1.0 fractions, matching the unit generator ports PolyPatchModule drives with
 * them, and a bend in semitones is scaled by the default +/- 2 semitone bend sensitivity.
 */
internal fun toMidi1Word(msg: Message.VoiceMessage): Int = when (msg.opcode) {
    Message.VoiceOpcode.NOTE_ON -> {
        val velocity = toMidi7Bit(msg.extra).coerceAtLeast(1) // 0 would mean note off
        Midi1Messages.noteOn(toMidiNote(msg.value), velocity)
    }
    Message.VoiceOpcode.NOTE_OFF -> Midi1Messages.noteOff(toMidiNote(msg.value))
    Message.VoiceOpcode.CONTROL_CHANGE ->
        Midi1Messages.controlChange(
            msg.extra.toInt().coerceIn(0, MIDI_7BIT_MAX),
            toMidi7Bit(msg.value)
        )
    Message.VoiceOpcode.PRESSURE -> {
        val amount = toMidi7Bit(msg.value)
        if (msg.tag >= 0) {
            Midi1Messages.polyPressure(msg.tag.coerceIn(0, MIDI_7BIT_MAX), amount)
        } else {
            Midi1Messages.channelPressure(amount)
        }
    }
    Message.VoiceOpcode.PITCH_BEND ->
        Midi1Messages.pitchBendFraction((msg.value / PITCH_BEND_SEMITONES).toFloat())
}
