/*
 * Copyright (C) 2026 Phil Burk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.softsynth.midibridge.MidiClock
import com.softsynth.midibridge.MidiDestination
import com.softsynth.midibridge.MidiDirection
import com.softsynth.midibridge.MidiEvent
import com.softsynth.midibridge.Midi1Messages
import com.softsynth.midibridge.channel
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
import com.syntona.dataflow.model.AdoptsRememberedDefaults
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round
import kotlin.math.sqrt

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

/** How long to wait before trying again for a device that is not attached yet. */
private const val RETRY_INTERVAL_MS = 200L

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
class MidiInModule : Module("midiIn"), EngineAware, AdoptsRememberedDefaults {
    override val isAllowedInPolyPatch: Boolean
        get() = false

    val output = addVoiceOutput("out")

    /** Name of the source device to listen to, or empty for none. */
    var deviceName: String by mutableStateOf("")
        private set

    /** Filter channel: -1 for ALL channels, or 1..16 for a specific channel. */
    var channelFilter: Int by mutableStateOf(-1)

    fun selectChannelFilter(ch: Int) {
        channelFilter = if (ch in 1..16) ch else -1
    }

    private var engine: DataFlowEngine? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var unsubscribe: (() -> Unit)? = null
    private var subscribeJob: Job? = null

    /**
     * Bumped by every device change and by teardown. Subscribing suspends, so a subscription can
     * finish after the device it was for has already been switched away from; the generation it
     * was started in is what tells it whether it is still wanted.
     */
    private var subscriptionGeneration = 0

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    private var rawSavedName: String? = null

    /**
     * The user picked this device, so it becomes the one remembered for next time.
     *
     * Only a deliberate choice is remembered. Opening a patch that happens to name a device is
     * not the user saying "this is my keyboard now", and letting it overwrite the memory would
     * mean loading someone else's patch quietly retargeted every patch opened afterwards.
     */
    fun selectDevice(name: String) {
        rawSavedName = null
        applyDevice(name)
        MidiDeviceService.rememberDevice(MidiDirection.SOURCE, name)
    }

    /** Points the module at a device without treating it as a choice worth remembering. */
    private fun applyDevice(name: String) {
        if (name == deviceName) return
        deviceName = name
        restartPolling()
    }

    /**
     * Applies the device a saved patch specified, falling back to the one last chosen when it
     * specified none. A patch shared by someone else, or saved before anything was plugged in,
     * then plays from whatever keyboard this machine uses without being told again.
     */
    fun loadDevice(savedName: String) {
        rawSavedName = savedName
        applyDevice(MidiDeviceService.resolveLoadedDevice(MidiDirection.SOURCE, savedName))
    }

    /** A module dropped into a patch starts on the keyboard already in use, not on None. */
    override fun adoptRememberedDefaults() {
        if (deviceName.isEmpty()) {
            applyDevice(MidiDeviceService.lastUsedDevice(MidiDirection.SOURCE))
        }
    }

    override fun teardown() {
        subscriptionGeneration++
        subscribeJob?.cancel()
        subscribeJob = null
        unsubscribe?.invoke()
        unsubscribe = null
        scope.cancel()
    }

    /** Nothing kicks a midiIn; it is driven by the device. */
    override fun execute(triggerMessage: Message) {}

    private fun restartPolling() {
        val generation = ++subscriptionGeneration
        subscribeJob?.cancel()
        unsubscribe?.invoke()
        unsubscribe = null
        val name = deviceName
        if (name.isEmpty() && rawSavedName == null) {
            subscribeJob = null
            return
        }

        subscribeJob = scope.launch {
            MidiDeviceService.open()

            val saved = rawSavedName
            if (saved != null) {
                val reResolved = MidiDeviceService.resolveLoadedDevice(MidiDirection.SOURCE, saved)
                rawSavedName = null
                if (reResolved != deviceName) {
                    deviceName = reResolved
                }
            }

            if (deviceName.isEmpty()) return@launch

            val cancelSubscription = MidiDeviceService.subscribeSource(deviceName) { event ->
                if (channelFilter == -1 || event.channel == (channelFilter - 1)) {
                    toVoiceMessage(event, ScoreClock.now())?.let { emit(it) }
                }
            } ?: return@launch

            // Handing the subscription over must not be interrupted: a cancellation landing
            // between subscribing and storing the handle would strand a listener that keeps
            // feeding this module from a device it no longer listens to, with no way to stop it.
            withContext(NonCancellable) {
                if (generation == subscriptionGeneration) {
                    unsubscribe = cancelSubscription
                } else {
                    cancelSubscription()
                }
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
 * null for anything with no VoiceMessage equivalent, such as clock.
 */
internal fun toVoiceMessage(event: MidiEvent, time: ScoreTime): Message.VoiceMessage? {
    if (event.messageType != Ump.MT_MIDI1_CHANNEL_VOICE) return null
    return when {
        event.isNoteOn -> {
            val fraction = event.velocityFraction.toDouble()
            Message.VoiceMessage(
                time, Message.VoiceOpcode.NOTE_ON,
                tag = event.note,
                value = event.note.toDouble(),
                extra = fraction * fraction // quadratic velocity curve
            )
        }
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
        event.messageStatus == Midi1.PROGRAM_CHANGE -> Message.VoiceMessage(
            time, Message.VoiceOpcode.PROGRAM_CHANGE,
            tag = -1,
            value = event.data1.toDouble(),
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
class MidiOutModule : Module("midiOut"), AdoptsRememberedDefaults {
    override val isAllowedInPolyPatch: Boolean
        get() = false

    val input = addVoiceInput("in")
    val channelIn = addIntegerInput("channel").also {
        it.min = 1.0
        it.max = 16.0
    }

    /** Name of the destination device to send to, or empty for none. */
    var deviceName: String by mutableStateOf("")
        private set

    /** Target MIDI channel (1..16, 1-indexed, default 1). */
    var channel: Int by mutableStateOf(1)

    init {
        channelIn.setValue(1.0)
    }

    /**
     * The jack is written too, not just [channel]. They are two views of one setting, and the
     * loader restores module properties before jack values - so a menu choice that only touched
     * [channel] would be overwritten by the jack's stale value on the next load.
     */
    fun selectChannel(ch: Int) {
        val clamped = ch.coerceIn(1, 16)
        channel = clamped
        if (channelIn.getDoubleValue() != clamped.toDouble()) {
            channelIn.setValue(clamped.toDouble())
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lease: MidiDeviceService.DestinationLease? = null
    private var openJob: Job? = null

    private val destination: MidiDestination? get() = lease?.destination

    private var rawSavedName: String? = null

    /**
     * The user picked this device, so it becomes the one remembered for next time.
     * See [MidiInModule.selectDevice] for why loading does not do the same.
     */
    fun selectDevice(name: String) {
        rawSavedName = null
        applyDevice(name)
        MidiDeviceService.rememberDevice(MidiDirection.DESTINATION, name)
    }

    /** Points the module at a device without treating it as a choice worth remembering. */
    private fun applyDevice(name: String) {
        if (name == deviceName) return
        deviceName = name
        reopenDestination()
    }

    /**
     * Applies the device a saved patch specified, falling back to the one last chosen when it
     * specified none. See [MidiInModule.loadDevice].
     */
    fun loadDevice(savedName: String) {
        rawSavedName = savedName
        applyDevice(MidiDeviceService.resolveLoadedDevice(MidiDirection.DESTINATION, savedName))
    }

    /** A module dropped into a patch starts on the synth already in use, not on None. */
    override fun adoptRememberedDefaults() {
        if (deviceName.isEmpty()) {
            applyDevice(MidiDeviceService.lastUsedDevice(MidiDirection.DESTINATION))
        }
    }

    override fun teardown() {
        openJob?.cancel()
        openJob = null
        releaseDestination()
        scope.cancel()
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === channelIn && msg is Message.FloatValue) {
            val ch = msg.value.toInt().coerceIn(1, 16)
            if (channel != ch) {
                channel = ch
            }
        }
        if (jack === input && msg is Message.VoiceMessage) {
            sendVoiceMessage(msg)
        }
        super.onMessageReceived(jack, msg)
    }

    /** Nothing kicks a midiOut; it forwards whatever arrives on [input]. */
    override fun execute(triggerMessage: Message) {}

    private fun releaseDestination() {
        lease?.release()
        lease = null
    }

    private fun reopenDestination() {
        openJob?.cancel()
        releaseDestination()
        val name = deviceName
        if (name.isEmpty() && rawSavedName == null) {
            openJob = null
            return
        }
        openJob = scope.launch {
            MidiDeviceService.open()

            val saved = rawSavedName
            if (saved != null) {
                val reResolved = MidiDeviceService.resolveLoadedDevice(MidiDirection.DESTINATION, saved)
                rawSavedName = null
                if (reResolved != deviceName) {
                    deviceName = reResolved
                }
            }

            if (deviceName.isEmpty()) return@launch

            while (isActive) {
                val acquired = MidiDeviceService.acquireDestination(deviceName)
                if (acquired != null) {
                    // The device may have been changed again while this one was opening. Giving
                    // the lease straight back is what keeps that race from leaking a port.
                    if (isActive && deviceName == name) {
                        lease = acquired
                    } else {
                        acquired.release()
                    }
                    return@launch
                }
                delay(RETRY_INTERVAL_MS)
            }
        }
    }

    /**
     * Sends now rather than at [Message.time]. ScoreTime counts from an app level mark while
     * MIDI timestamps are on [MidiClock]'s timeline, and there is no mapping between the two
     * yet - the same limitation PolyPatchModule has when it plays a VoiceMessage.
     */
    private fun sendVoiceMessage(msg: Message.VoiceMessage) {
        val dest = destination ?: return
        val chZeroBased = (channel - 1).coerceIn(0, 15)
        dest.send(toMidi1Word(msg, chZeroBased), MidiClock.nowNanos())
    }
}

/**
 * Converts a [Message.VoiceMessage] to the single Universal MIDI Packet word that carries it as
 * a MIDI 1.0 channel voice message, on the specified 0-based channel.
 */
internal fun toMidi1Word(msg: Message.VoiceMessage, channelZeroBased: Int = 0): Int {
    val ch = channelZeroBased.coerceIn(0, 15)
    return when (msg.opcode) {
        Message.VoiceOpcode.NOTE_ON -> {
            val fraction = sqrt(msg.extra.coerceAtLeast(0.0))
            val velocity = toMidi7Bit(fraction).coerceAtLeast(1) // 0 would mean note off
            Midi1Messages.noteOn(toMidiNote(msg.value), velocity, ch)
        }
        // noteOff takes a velocity before the channel, so ch must be named or it lands in it.
        Message.VoiceOpcode.NOTE_OFF -> Midi1Messages.noteOff(toMidiNote(msg.value), channel = ch)
        Message.VoiceOpcode.CONTROL_CHANGE ->
            Midi1Messages.controlChange(
                msg.extra.toInt().coerceIn(0, MIDI_7BIT_MAX),
                toMidi7Bit(msg.value),
                ch
            )
        Message.VoiceOpcode.PRESSURE -> {
            val amount = toMidi7Bit(msg.value)
            if (msg.tag >= 0) {
                Midi1Messages.polyPressure(msg.tag.coerceIn(0, MIDI_7BIT_MAX), amount, ch)
            } else {
                Midi1Messages.channelPressure(amount, ch)
            }
        }
        Message.VoiceOpcode.PITCH_BEND ->
            Midi1Messages.pitchBendFraction((msg.value / PITCH_BEND_SEMITONES).toFloat(), ch)
        Message.VoiceOpcode.PROGRAM_CHANGE ->
            Midi1Messages.programChange(msg.value.toInt().coerceIn(0, MIDI_7BIT_MAX), ch)
    }
}
