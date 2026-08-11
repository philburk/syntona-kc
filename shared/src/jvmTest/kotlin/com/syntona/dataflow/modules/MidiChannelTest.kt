package com.syntona.dataflow.modules

import com.softsynth.midibridge.Midi1Messages
import com.softsynth.midibridge.MidiEvent
import com.softsynth.midibridge.channel
import com.softsynth.midibridge.note
import com.softsynth.midibridge.velocity7
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals

class MidiChannelTest {

    private val time = ScoreTime(0L)

    private fun eventOf(word: Int) = MidiEvent().also { it.set(0L, word) }

    @Test
    fun toMidi1WordEncodesSpecifiedChannel() {
        val msg = Message.VoiceMessage(time, Message.VoiceOpcode.NOTE_ON, 60, 60.0, 0.8)

        // Channel 1 (human) -> 0x0 (0-indexed)
        val wordCh0 = toMidi1Word(msg, channelZeroBased = 0)
        assertEquals(0, eventOf(wordCh0).channel)
        assertEquals(60, eventOf(wordCh0).note)

        // Channel 10 (human) -> 0x9 (0-indexed)
        val wordCh9 = toMidi1Word(msg, channelZeroBased = 9)
        assertEquals(9, eventOf(wordCh9).channel)
        assertEquals(60, eventOf(wordCh9).note)

        // Channel 16 (human) -> 0xF (0-indexed)
        val wordCh15 = toMidi1Word(msg, channelZeroBased = 15)
        assertEquals(15, eventOf(wordCh15).channel)
    }

    @Test
    fun everyOpcodeEncodesTheChannel() {
        // NOTE_ON was the only opcode covered, which let noteOff pass its channel as a velocity.
        for (opcode in Message.VoiceOpcode.entries) {
            val msg = Message.VoiceMessage(time, opcode, 60, 60.0, 0.8)
            val event = eventOf(toMidi1Word(msg, channelZeroBased = 9))
            assertEquals(9, event.channel, "$opcode did not encode the channel")
        }
    }

    @Test
    fun noteOffKeepsItsDefaultReleaseVelocity() {
        val msg = Message.VoiceMessage(time, Message.VoiceOpcode.NOTE_OFF, 60, 60.0, 0.0)
        assertEquals(64, eventOf(toMidi1Word(msg, channelZeroBased = 9)).velocity7)
    }

    @Test
    fun midiInChannelFilterDefaultsToAll() {
        val midiIn = MidiInModule()
        assertEquals(-1, midiIn.channelFilter)

        midiIn.selectChannelFilter(5)
        assertEquals(5, midiIn.channelFilter)

        // Reset to ALL when invalid or negative
        midiIn.selectChannelFilter(-1)
        assertEquals(-1, midiIn.channelFilter)
    }

    @Test
    fun midiOutChannelDefaultsTo1AndUpdatesViaJackMessageWithoutOverflow() {
        val midiOut = MidiOutModule()
        assertEquals(1, midiOut.channel)

        // Select channel via UI / method call
        midiOut.selectChannel(10)
        assertEquals(10, midiOut.channel)

        // Receive message on channelIn jack (precedence rule) without recursion/StackOverflowError
        midiOut.onMessageReceived(midiOut.channelIn, Message.FloatValue(time, 4.0))
        assertEquals(4, midiOut.channel)
    }

    @Test
    fun midiOutChannelSurvivesSaveAndReload() {
        val state = GraphState()
        val midiOut = MidiOutModule()
        midiOut.selectChannel(10)
        state.addModule(midiOut, androidx.compose.ui.geometry.Offset.Zero)

        val reloaded = GraphState()
        GraphSerializer.deserialize(reloaded, GraphSerializer.serialize(state, state.modules))
        val loaded = reloaded.modules.first { it is MidiOutModule } as MidiOutModule
        assertEquals(10, loaded.channel)
    }
}
