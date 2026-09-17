package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.model.VoiceInputJack
import com.syntona.dataflow.model.VoiceOutputJack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTrackTest {

    @Test
    fun testTrackAdditionAndJackCreation() {
        val timeline = TimelineModule()
        assertEquals(0, timeline.tracks.size)
        assertNull(timeline.recordJack)

        // 1. Add Patch Track
        val patchTrack = timeline.addTrack(TrackType.PATCH)
        assertEquals(1, timeline.tracks.size)
        assertTrue(patchTrack is PatchTrack)
        assertEquals("Patch 1", patchTrack.name)
        assertNull(patchTrack.inputJack)
        assertNull(patchTrack.outputJack)
        assertNull(timeline.recordJack) // Patch tracks are not recordable

        // 2. Add Message Track
        val msgTrack = timeline.addTrack(TrackType.MESSAGE)
        assertEquals(2, timeline.tracks.size)
        assertTrue(msgTrack is MessageTrack)
        assertEquals("Message 2", msgTrack.name)
        assertNotNull(msgTrack.inputJack)
        assertNotNull(msgTrack.outputJack)
        assertTrue(msgTrack.inputJack is InputJack)
        assertTrue(msgTrack.outputJack is OutputJack)
        assertEquals("in2", msgTrack.inputJack?.name)
        assertEquals("out2", msgTrack.outputJack?.name)
        assertTrue(timeline.inputs.contains(msgTrack.inputJack))
        assertTrue(timeline.outputs.contains(msgTrack.outputJack))
        assertNotNull(timeline.recordJack) // Message tracks are recordable

        // 3. Add MIDI Track
        val midiTrack = timeline.addTrack(TrackType.MIDI)
        assertTrue(midiTrack is MidiTrack)
        assertTrue(midiTrack.inputJack is VoiceInputJack)
        assertTrue(midiTrack.outputJack is VoiceOutputJack)
        assertEquals("in3", midiTrack.inputJack.name)
        assertEquals("out3", midiTrack.outputJack.name)

        // 4. Add Audio Track
        val audioTrack = timeline.addTrack(TrackType.AUDIO)
        assertTrue(audioTrack is AudioTrack)
        assertTrue(audioTrack.inputJack is AudioInputJack)
        assertTrue(audioTrack.outputJack is AudioOutputJack)
        assertEquals("in4", audioTrack.inputJack.name)
        assertEquals("out4", audioTrack.outputJack.name)

        // 5. Remove Message Track and verify jacks are cleaned up
        timeline.removeTrack(msgTrack)
        assertEquals(3, timeline.tracks.size)
        assertFalse(timeline.inputs.contains(msgTrack.inputJack))
        assertFalse(timeline.outputs.contains(msgTrack.outputJack))
    }

    @Test
    fun testRecordJackLifecycle() {
        val timeline = TimelineModule()
        assertNull(timeline.recordJack)

        val patchTrack = timeline.addTrack(TrackType.PATCH)
        assertNull(timeline.recordJack)

        val midiTrack = timeline.addTrack(TrackType.MIDI)
        assertNotNull(timeline.recordJack)
        assertEquals("record", timeline.recordJack?.name)

        val audioTrack = timeline.addTrack(TrackType.AUDIO)
        assertNotNull(timeline.recordJack)

        // Remove audio track - midi track is still recordable
        timeline.removeTrack(audioTrack)
        assertNotNull(timeline.recordJack)

        // Remove midi track - only patch track remains
        timeline.removeTrack(midiTrack)
        assertNull(timeline.recordJack)
        assertFalse(timeline.inputs.any { it.name == "record" })
    }

    @Test
    fun testTrackSectionCalculations() {
        val section = TrackSection(
            id = 1,
            initialStart = 2.0,
            initialDuration = 4.0,
            initialRepeats = 3,
            initialName = "Chorus"
        )
        assertEquals(12.0, section.totalDuration)
        assertEquals(14.0, section.end)

        section.repeats = 1
        assertEquals(4.0, section.totalDuration)
        assertEquals(6.0, section.end)

        section.duration = 2.5
        assertEquals(2.5, section.totalDuration)
        assertEquals(4.5, section.end)
    }

    @Test
    fun testBeatSnapping() {
        val subdivision = 0.25 // 1/4 beat
        val snapped1 = TimelineBeatUtils.snapToSubdivision(1.23, subdivision)
        assertEquals(1.25, snapped1)

        val snapped2 = TimelineBeatUtils.snapToSubdivision(1.12, subdivision)
        assertEquals(1.0, snapped2)

        val snapped3 = TimelineBeatUtils.snapToSubdivision(2.38, subdivision)
        assertEquals(2.5, snapped3)

        // Subdivision 1/8 beat (0.125)
        val snapped4 = TimelineBeatUtils.snapToSubdivision(1.36, 0.125)
        assertEquals(1.375, snapped4)
    }

    @Test
    fun testBeatLineTempoLockWithAudioTrack() {
        val beatLine = BeatLineModule()
        assertFalse(beatLine.isTempoLocked)

        // Audio track with no sections does not lock tempo
        val audioTrack = beatLine.addTrack(TrackType.AUDIO) as AudioTrack
        assertFalse(beatLine.isTempoLocked)

        // Adding a section locks the tempo jack
        audioTrack.addSection(start = 0.0, duration = 4.0)
        assertTrue(beatLine.isTempoLocked)
        assertEquals(120.0, beatLine.lockedTempoBpm)

        // Changing tempo jack value while locked is ignored
        beatLine.tempo.setValue(140.0)
        assertEquals(120.0, beatLine.tempo.getValue())

        // Removing the section unlocks the tempo
        audioTrack.removeSection(audioTrack.sections.first())
        assertFalse(beatLine.isTempoLocked)

        // Once unlocked, tempo can be changed
        beatLine.tempo.setValue(140.0)
        assertEquals(140.0, beatLine.tempo.getValue())
    }

    @Test
    fun testSerializationRoundTrip() {
        val graphState = GraphState()
        val timeline = TimelineModule()
        graphState.addModule(timeline, Offset.Zero)

        val patchTrack = timeline.addTrack(TrackType.PATCH, "MyPatchTrack")
        patchTrack.isMuted = true

        val audioTrack = timeline.addTrack(TrackType.AUDIO, "VocalTrack") as AudioTrack
        audioTrack.isSoloed = true
        audioTrack.isRecordArmed = true
        audioTrack.isInputMonitored = true
        audioTrack.addSectionDirect(id = 10, start = 1.5, duration = 3.0, repeats = 2, name = "Take 1")

        val json = GraphSerializer.serialize(graphState, graphState.modules)

        // Deserialize
        val reloadedState = GraphState()
        GraphSerializer.deserialize(reloadedState, json)
        val loadedTimeline = reloadedState.modules.filterIsInstance<TimelineModule>().firstOrNull()
        assertNotNull(loadedTimeline)

        assertEquals(2, loadedTimeline.tracks.size)

        val loadedPatch = loadedTimeline.tracks[0]
        assertEquals(TrackType.PATCH, loadedPatch.type)
        assertEquals("MyPatchTrack", loadedPatch.name)
        assertTrue(loadedPatch.isMuted)
        assertFalse(loadedPatch.isSoloed)

        val loadedAudio = loadedTimeline.tracks[1]
        assertEquals(TrackType.AUDIO, loadedAudio.type)
        assertEquals("VocalTrack", loadedAudio.name)
        assertTrue(loadedAudio.isSoloed)
        assertTrue(loadedAudio.isRecordArmed)
        assertTrue(loadedAudio.isInputMonitored)
        assertNotNull(loadedAudio.inputJack)
        assertNotNull(loadedAudio.outputJack)
        assertEquals("in2", loadedAudio.inputJack?.name)
        assertEquals("out2", loadedAudio.outputJack?.name)

        assertEquals(1, loadedAudio.sections.size)
        val sec = loadedAudio.sections[0]
        assertEquals(10, sec.id)
        assertEquals(1.5, sec.start)
        assertEquals(3.0, sec.duration)
        assertEquals(2, sec.repeats)
        assertEquals("Take 1", sec.name)
    }
}
