package com.syntona.dataflow.modules

import com.softsynth.midibridge.Midi1Messages
import com.softsynth.midibridge.MidiEvent
import com.softsynth.midibridge.controller
import com.softsynth.midibridge.controllerValue7
import com.softsynth.midibridge.note
import com.softsynth.midibridge.pitchBend14
import com.softsynth.midibridge.velocity7
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conversion between [Message.VoiceMessage] and MIDI 1.0, as used by midiIn and midiOut.
 * JVM-only because midi-bridge is not on the common metadata classpath.
 */
class MidiConversionTest {

    private val time = ScoreTime(0L)

    private fun eventOf(word: Int) = MidiEvent().also { it.set(0L, word) }

    private fun voiceMessageOf(word: Int) = toVoiceMessage(eventOf(word), time)

    @Test
    fun noteOnBecomesNoteOnWithVelocityAsAmplitude() {
        val msg = voiceMessageOf(Midi1Messages.noteOn(60, 127))!!
        assertEquals(Message.VoiceOpcode.NOTE_ON, msg.opcode)
        assertEquals(60, msg.tag)
        assertEquals(60.0, msg.value)
        assertEquals(1.0, msg.extra, 0.001)
    }

    @Test
    fun noteOnAppliesQuadraticVelocityCurve() {
        val msg = voiceMessageOf(Midi1Messages.noteOn(60, 64))!!
        val linearFraction = 64.0 / 127.0
        val expectedAmplitude = linearFraction * linearFraction
        assertEquals(expectedAmplitude, msg.extra, 0.001)
    }

    @Test
    fun noteOnWithZeroVelocityBecomesNoteOff() {
        val msg = voiceMessageOf(Midi1Messages.noteOn(60, 0))!!
        assertEquals(Message.VoiceOpcode.NOTE_OFF, msg.opcode)
        assertEquals(60, msg.tag)
    }

    @Test
    fun controlChangeCarriesControllerInExtraAndValueAsFraction() {
        val msg = voiceMessageOf(Midi1Messages.controlChange(74, 127))!!
        assertEquals(Message.VoiceOpcode.CONTROL_CHANGE, msg.opcode)
        assertEquals(-1, msg.tag)
        assertEquals(74.0, msg.extra)
        assertEquals(1.0, msg.value, 0.001)
    }

    @Test
    fun polyPressureKeepsItsNoteAndChannelPressureDoesNot() {
        val poly = voiceMessageOf(Midi1Messages.polyPressure(64, 64))!!
        assertEquals(Message.VoiceOpcode.PRESSURE, poly.opcode)
        assertEquals(64, poly.tag)
        assertEquals(0.5, poly.value, 0.01)

        val channel = voiceMessageOf(Midi1Messages.channelPressure(64))!!
        assertEquals(Message.VoiceOpcode.PRESSURE, channel.opcode)
        assertEquals(-1, channel.tag)
    }

    @Test
    fun pitchBendArrivesInSemitones() {
        val up = voiceMessageOf(Midi1Messages.pitchBendFraction(1f))!!
        assertEquals(Message.VoiceOpcode.PITCH_BEND, up.opcode)
        assertEquals(2.0, up.value, 0.01)

        val center = voiceMessageOf(Midi1Messages.pitchBend(Midi1Messages.PITCH_BEND_CENTER))!!
        assertEquals(0.0, center.value, 0.01)
    }

    @Test
    fun programChangeBecomesVoiceMessage() {
        val msg = voiceMessageOf(Midi1Messages.programChange(5))!!
        assertEquals(Message.VoiceOpcode.PROGRAM_CHANGE, msg.opcode)
        assertEquals(5.0, msg.value)
    }

    @Test
    fun messagesWithNoVoiceEquivalentAreDropped() {
        assertNull(voiceMessageOf(Midi1Messages.timingClock()))
    }

    @Test
    fun fractionalPitchIsRoundedToNearestNote() {
        val sharp = Message.VoiceMessage(time, Message.VoiceOpcode.NOTE_ON, 60, 60.6, 1.0)
        assertEquals(61, eventOf(toMidi1Word(sharp)).note)

        val flat = Message.VoiceMessage(time, Message.VoiceOpcode.NOTE_ON, 60, 60.4, 1.0)
        assertEquals(60, eventOf(toMidi1Word(flat)).note)
    }

    @Test
    fun noteOnNeverSendsZeroVelocity() {
        val silent = Message.VoiceMessage(time, Message.VoiceOpcode.NOTE_ON, 60, 60.0, 0.0)
        assertEquals(1, eventOf(toMidi1Word(silent)).velocity7)
    }

    @Test
    fun controlChangeSendsExtraAsControllerNumber() {
        val cc = Message.VoiceMessage(time, Message.VoiceOpcode.CONTROL_CHANGE, -1, 0.5, 74.0)
        val event = eventOf(toMidi1Word(cc))
        assertEquals(74, event.controller)
        assertEquals(64, event.controllerValue7)
    }

    @Test
    fun semitoneBendSendsHalfOfFullScaleDeflection() {
        val bend = Message.VoiceMessage(time, Message.VoiceOpcode.PITCH_BEND, -1, 1.0, 0.0)
        val event = eventOf(toMidi1Word(bend))
        val expected = Midi1Messages.PITCH_BEND_CENTER + Midi1Messages.PITCH_BEND_CENTER / 2
        assertTrue(abs(event.pitchBend14 - expected) <= 1, "was ${event.pitchBend14}")
    }

    @Test
    fun noteSurvivesARoundTrip() {
        val sent = Message.VoiceMessage(time, Message.VoiceOpcode.NOTE_ON, 60, 60.0, 0.5)
        val received = voiceMessageOf(toMidi1Word(sent))!!
        assertEquals(sent.opcode, received.opcode)
        assertEquals(sent.tag, received.tag)
        assertEquals(sent.value, received.value)
        assertEquals(sent.extra, received.extra, 0.01)
    }
}
