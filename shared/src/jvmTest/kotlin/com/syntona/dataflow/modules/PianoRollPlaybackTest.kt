package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.KickJackCommand
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end playback test running a real [DataFlowEngine] coroutine loop.
 * JVM-only because it relies on real wall-clock scheduling and Thread.sleep.
 */
class PianoRollPlaybackTest {

    private class ProbeModule : Module("probe") {
        val input = addVoiceInput("in", isKickable = true)
        val received = mutableListOf<Message.VoiceMessage>()
        override fun execute(triggerMessage: Message) {
            (triggerMessage as? Message.VoiceMessage)?.let { received.add(it) }
        }
    }

    private fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            Thread.sleep(10)
        }
    }

    @Test
    fun testPlaybackSendsOrderedNoteOnOffPairs() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val engine = DataFlowEngine(scope)
            val module = PianoRollModule()
            module.noteOutputMode = PianoRollModule.NoteOutputMode.NOTE_ON_OFF
            module.setEngine(engine)
            val probe = ProbeModule()
            module.output.connect(probe.input)

            // Two notes at 240 BPM-ish speeds: keep everything short.
            module.tempo.setValue(120.0) // nsPerTick ≈ 2.08ms; a 16th (60 ticks) ≈ 125ms
            module.notes.add(PianoRollNote(id = 0, startTicks = 0, durationTicks = 60, rootPitch = 60.0))
            module.notes.add(
                PianoRollNote(id = 1, startTicks = 60, durationTicks = 60, parentId = 0, ratio = Ratio(3, 2))
            )

            engine.post(KickJackCommand(module.start, ScoreClock.now()))
            waitUntil { probe.received.size >= 4 && !module.isPlaying }

            assertEquals(4, probe.received.size, "expected NOTE_ON/OFF for two notes, got ${probe.received}")
            assertFalse(module.isPlaying, "playback should stop after the last note")

            val (on0, off0) = probe.received.filter { it.tag == 0 }.let { it[0] to it[1] }
            assertEquals(Message.VoiceOpcode.NOTE_ON, on0.opcode)
            assertEquals(Message.VoiceOpcode.NOTE_OFF, off0.opcode)
            assertEquals(60.0, on0.value)

            val (on1, off1) = probe.received.filter { it.tag == 1 }.let { it[0] to it[1] }
            assertEquals(Message.VoiceOpcode.NOTE_ON, on1.opcode)
            assertEquals(Message.VoiceOpcode.NOTE_OFF, off1.opcode)
            assertTrue(kotlin.math.abs(on1.value - 67.02) < 0.01, "chained note should sound at ~67.02, was ${on1.value}")

            // Ordering: first note's ON precedes second note's ON, which precedes its own OFF.
            assertTrue(probe.received.indexOf(on0) < probe.received.indexOf(on1))
            assertTrue(probe.received.indexOf(on1) < probe.received.indexOf(off1))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun testDefaultPlaybackSendsUnifiedNoteMessages() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val engine = DataFlowEngine(scope)
            val module = PianoRollModule()
            module.setEngine(engine)
            val probe = ProbeModule()
            module.output.connect(probe.input)

            assertEquals(PianoRollModule.NoteOutputMode.NOTE, module.noteOutputMode)
            module.tempo.setValue(120.0) // 60 ticks = 0.125s
            module.notes.add(PianoRollNote(id = 0, startTicks = 0, durationTicks = 60, rootPitch = 60.0, amplitude = 0.75))
            module.notes.add(
                PianoRollNote(id = 1, startTicks = 60, durationTicks = 120, parentId = 0, ratio = Ratio(3, 2), amplitude = 0.35)
            )

            engine.post(KickJackCommand(module.start, ScoreClock.now()))
            waitUntil { probe.received.size >= 2 && !module.isPlaying }

            assertFalse(module.isPlaying, "playback should stop after last note finishes")
            assertEquals(2, probe.received.size, "NOTE mode should emit only NOTE messages when playing to completion")

            val note0 = probe.received[0]
            assertEquals(Message.VoiceOpcode.NOTE, note0.opcode)
            assertEquals(0, note0.tag)
            assertEquals(60.0, note0.value)
            assertEquals(0.75, note0.extra, 1e-6)
            assertTrue(kotlin.math.abs(note0.duration - 0.125) < 0.002, "expected ~0.125s duration, got ${note0.duration}")

            val note1 = probe.received[1]
            assertEquals(Message.VoiceOpcode.NOTE, note1.opcode)
            assertEquals(1, note1.tag)
            assertTrue(kotlin.math.abs(note1.value - 67.02) < 0.01)
            assertEquals(0.35, note1.extra, 1e-6)
            assertTrue(kotlin.math.abs(note1.duration - 0.250) < 0.002, "expected ~0.250s duration, got ${note1.duration}")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun testStopFlushesSoundingNotes() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val engine = DataFlowEngine(scope)
            val module = PianoRollModule()
            module.setEngine(engine)
            val probe = ProbeModule()
            module.output.connect(probe.input)

            // One long note (4 beats ≈ 2s at 120 BPM) that we stop early.
            module.notes.add(PianoRollNote(id = 0, startTicks = 0, durationTicks = 960, rootPitch = 60.0))

            engine.post(KickJackCommand(module.start, ScoreClock.now()))
            waitUntil {
                probe.received.any {
                    it.opcode == Message.VoiceOpcode.NOTE || it.opcode == Message.VoiceOpcode.NOTE_ON
                }
            }
            assertTrue(module.isPlaying)

            engine.post(KickJackCommand(module.stop, ScoreClock.now()))
            waitUntil { !module.isPlaying }

            assertFalse(module.isPlaying)
            waitUntil(500) { probe.received.size >= 2 }
            val last = probe.received.last()
            assertEquals(Message.VoiceOpcode.NOTE_OFF, last.opcode, "stop must flush a NOTE_OFF for the sounding note")
            assertEquals(0, last.tag)
            // No further messages: the scheduled OFF was cancelled.
            val count = probe.received.size
            Thread.sleep(300)
            assertEquals(count, probe.received.size, "cancelled events must not fire after stop")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun testPlayStartMarkerSkipsEarlierNotes() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val engine = DataFlowEngine(scope)
            val module = PianoRollModule()
            module.setEngine(engine)
            val probe = ProbeModule()
            module.output.connect(probe.input)

            module.notes.add(PianoRollNote(id = 0, startTicks = 0, durationTicks = 60, rootPitch = 60.0))
            module.notes.add(PianoRollNote(id = 1, startTicks = 240, durationTicks = 60, rootPitch = 64.0))
            module.playStartTicks = 120 // past the end of note 0

            engine.post(KickJackCommand(module.start, ScoreClock.now()))
            waitUntil { !module.isPlaying && probe.received.isNotEmpty() }

            assertTrue(probe.received.all { it.tag == 1 }, "only the note after the marker should play, got ${probe.received}")
            assertEquals(1, probe.received.size)
            assertEquals(Message.VoiceOpcode.NOTE, probe.received[0].opcode)
        } finally {
            scope.cancel()
        }
    }
}
