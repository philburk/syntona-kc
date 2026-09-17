package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.editor.DeleteModulesCommand
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.readFileDirect
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.model.VoiceInputJack
import com.syntona.dataflow.model.VoiceOutputJack
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Module
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTrackTest {

    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, () -> Unit>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            scheduled.add(time to action)
            return ScheduledEvent(action)
        }
    }

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

    @Test
    fun testPatchTrackOutputsAndIntrinsicCommands() {
        val timeline = TimelineModule()
        val patchTrack = timeline.addTrack(TrackType.PATCH, "SynthTrack") as PatchTrack

        // Verify intrinsic command modules
        assertNotNull(patchTrack.startCommandModule)
        assertNotNull(patchTrack.stopCommandModule)
        assertEquals("start", patchTrack.startCommandModule.name)
        assertEquals("stop", patchTrack.stopCommandModule.name)
        assertFalse(patchTrack.startCommandModule.isDeletable)
        assertFalse(patchTrack.stopCommandModule.isDeletable)
        assertFalse(patchTrack.patch.canDeleteChildModule(patchTrack.startCommandModule))
        assertFalse(patchTrack.patch.canDeleteChildModule(patchTrack.stopCommandModule))

        // Initially no outputs
        assertEquals(0, patchTrack.outputJacks.size)
        assertNull(patchTrack.outputJack)

        // Add an AudioOutputModule inside the patch
        val audioOut = AudioOutputModule()
        patchTrack.patch.graphState.addModule(audioOut, Offset(200f, 100f))

        // Should mirror to timeline output
        assertEquals(1, patchTrack.outputJacks.size)
        assertNotNull(patchTrack.outputJack)
        assertTrue(patchTrack.outputJack is AudioOutputJack)
        assertTrue(timeline.outputs.contains(patchTrack.outputJack))

        // Add a standard OutputModule inside the patch
        val msgOut = OutputModule()
        patchTrack.patch.graphState.addModule(msgOut, Offset(200f, 200f))
        assertEquals(2, patchTrack.outputJacks.size)

        // Remove AudioOutputModule
        patchTrack.patch.graphState.removeModulesDirectly(listOf(audioOut))
        assertEquals(1, patchTrack.outputJacks.size)
        assertEquals(msgOut.name, patchTrack.outputJacks[0].name.substringAfter("_", patchTrack.outputJacks[0].name))
    }

    @Test
    fun testPatchTrackPlaybackStateAndMute() {
        val timeline = TimelineModule()
        val patchTrack = timeline.addTrack(TrackType.PATCH) as PatchTrack
        assertFalse(patchTrack.isPatchRunning)

        patchTrack.startPatch()
        assertTrue(patchTrack.isPatchRunning)

        patchTrack.stopPatch()
        assertFalse(patchTrack.isPatchRunning)

        // Test audible changes during playback
        patchTrack.addSection(start = 0.0, duration = 4.0)
        timeline.seekTime = 2.0
        // Playback not started yet -> handleAudibleChanged stops patch
        patchTrack.handleAudibleChanged()
        assertFalse(patchTrack.isPatchRunning)
    }

    @Test
    fun testMessageTrackDataAndRecording() {
        val timeline = TimelineModule()
        val msgTrack = timeline.addTrack(TrackType.MESSAGE) as MessageTrack

        val sec = assertNotNull(msgTrack.addSection(start = 0.0, duration = 4.0))
        assertNotNull(sec.data)
        val data = sec.data as MessageSectionData
        assertEquals(0, data.events.size)

        // Add events
        val ev1 = data.addEvent(0.5, 440.0)
        val ev2 = data.addEvent(1.5, 880.0)
        assertEquals(2, data.events.size)
        assertEquals(0.5, data.events[0].time)
        assertEquals(440.0, data.events[0].value)
        assertEquals(1.5, data.events[1].time)
        assertEquals(880.0, data.events[1].value)

        // Remove event
        data.removeEvent(ev1)
        assertEquals(1, data.events.size)
        assertEquals(880.0, data.events[0].value)

        // Test recording message when armed and recording
        msgTrack.isRecordArmed = true
        timeline.startRecording(ScoreTime.Zero)
        timeline.seekTime = 2.0 // relative time in section is 2.0
        msgTrack.onMessageReceived(msgTrack.inputJack, Message.FloatValue(ScoreTime.Zero, 550.0))
        assertEquals(2, data.events.size)
        val recEvent = data.events.first { kotlin.math.abs(it.time - 2.0) < 0.01 }
        assertEquals(550.0, recEvent.value)
    }

    @Test
    fun testPhase2SerializationRoundTrip() {
        val graphState = GraphState()
        val timeline = TimelineModule()
        graphState.addModule(timeline, Offset.Zero)

        // 1. Setup Patch Track with AudioOutputModule
        val patchTrack = timeline.addTrack(TrackType.PATCH, "InnerSynth") as PatchTrack
        val audioOut = AudioOutputModule()
        audioOut.name = "mainOut"
        patchTrack.patch.graphState.addModule(audioOut, Offset(100f, 100f))
        assertEquals(1, patchTrack.outputJacks.size)

        // 2. Setup Message Track with discrete unquantized events
        val msgTrack = timeline.addTrack(TrackType.MESSAGE, "ModTrack") as MessageTrack
        val sec = msgTrack.addSectionDirect(id = 5, start = 0.0, duration = 2.0, repeats = 1, name = "FilterLFO")
        val msgData = MessageSectionData(
            initialEvents = listOf(
                TrackMessageEvent(0.25, 100.0),
                TrackMessageEvent(1.15, 500.0),
                TrackMessageEvent(1.85, 800.0)
            ),
            initialMin = 50.0,
            initialMax = 1000.0
        )
        sec.data = msgData

        val json = GraphSerializer.serialize(graphState, graphState.modules)

        // Deserialize
        val reloadedState = GraphState()
        GraphSerializer.deserialize(reloadedState, json)
        val loadedTimeline = reloadedState.modules.filterIsInstance<TimelineModule>().first()

        assertEquals(2, loadedTimeline.tracks.size)

        // Verify loaded PatchTrack
        val loadedPatch = loadedTimeline.tracks[0] as PatchTrack
        assertEquals("InnerSynth", loadedPatch.name)
        assertTrue(loadedPatch.patch.graphState.modules.any { it is AudioOutputModule })
        assertEquals(1, loadedPatch.outputJacks.size)

        // Verify loaded MessageTrack
        val loadedMsg = loadedTimeline.tracks[1] as MessageTrack
        assertEquals("ModTrack", loadedMsg.name)
        assertEquals(1, loadedMsg.sections.size)
        val loadedSec = loadedMsg.sections[0]
        assertEquals(5, loadedSec.id)
        val loadedData = loadedSec.data as MessageSectionData
        assertEquals(3, loadedData.events.size)
        assertEquals(50.0, loadedData.min)
        assertEquals(1000.0, loadedData.max)
        assertEquals(0.25, loadedData.events[0].time, 0.001)
        assertEquals(100.0, loadedData.events[0].value, 0.001)
        assertEquals(1.15, loadedData.events[1].time, 0.001)
        assertEquals(500.0, loadedData.events[1].value, 0.001)
        assertEquals(1.85, loadedData.events[2].time, 0.001)
        assertEquals(800.0, loadedData.events[2].value, 0.001)
    }

    @Test
    fun testRecordJackHighlighting() {
        val timeline = TimelineModule()
        timeline.addTrack(TrackType.MESSAGE)
        val rJack = assertNotNull(timeline.recordJack)
        assertFalse(rJack.isHighlighted)
        assertFalse(timeline.isRecording)

        timeline.startRecording(ScoreTime.Zero)
        assertTrue(timeline.isRecording)
        assertTrue(rJack.isHighlighted)

        timeline.stopRecording(ScoreTime.Zero)
        assertFalse(timeline.isRecording)
        assertFalse(rJack.isHighlighted)
    }

    @Test
    fun testTimelineDeletionAndUndoPreservesPatchContentsAndSections() {
        val graphState = GraphState()
        val timeline = TimelineModule()
        graphState.addModule(timeline, Offset.Zero)

        val patchTrack = timeline.addTrack(TrackType.PATCH, "MyPatchTrack") as PatchTrack
        val audioOut = AudioOutputModule()
        audioOut.name = "audioOut1"
        patchTrack.patch.graphState.addModule(audioOut, Offset(100f, 100f))
        val sec = assertNotNull(patchTrack.addSection(start = 2.0, duration = 4.0))

        assertEquals(1, patchTrack.outputJacks.size)
        assertEquals(1, patchTrack.sections.size)
        assertTrue(patchTrack.patch.graphState.modules.contains(audioOut))

        // Select and delete timeline module
        graphState.selectModule(timeline)
        val savedPositions: Map<com.syntona.dataflow.model.Module, Offset> = mapOf(timeline to Offset.Zero)
        val deleteCmd = DeleteModulesCommand(graphState, listOf(timeline), savedPositions, emptyList())
        deleteCmd.execute()

        // Timeline is removed from canvas
        assertFalse(graphState.modules.contains(timeline))

        // Undo deletion
        deleteCmd.undo()
        assertTrue(graphState.modules.contains(timeline))

        // Check that patch track, inner modules, and sections are completely intact
        assertEquals(1, timeline.tracks.size)
        val restoredPatchTrack = timeline.tracks[0] as PatchTrack
        assertEquals("MyPatchTrack", restoredPatchTrack.name)
        assertTrue(restoredPatchTrack.patch.graphState.modules.contains(audioOut))
        assertEquals(1, restoredPatchTrack.outputJacks.size)
        assertEquals(1, restoredPatchTrack.sections.size)
        assertEquals(sec.start, restoredPatchTrack.sections[0].start)
        assertEquals(sec.duration, restoredPatchTrack.sections[0].duration)
    }

    @Test
    fun testStrictNonOverlappingSections() {
        val timeline = TimelineModule()
        timeline.maxTime = 20.0
        val track = timeline.addTrack(TrackType.PATCH) as PatchTrack

        // 1. Add section A at [2.0, 6.0)
        val secA = assertNotNull(track.addSection(start = 2.0, duration = 4.0))
        assertEquals(2.0, secA.start)
        assertEquals(4.0, secA.duration)
        assertEquals(6.0, secA.end)

        // 2. Attempt to add overlapping section inside A:
        assertFalse(track.canAddSectionAt(2.0))
        assertFalse(track.canAddSectionAt(3.5))
        assertFalse(track.canAddSectionAt(5.99))
        assertNull(track.addSection(start = 3.0, duration = 2.0))

        // 3. Add section B at [10.0, 14.0)
        val secB = assertNotNull(track.addSection(start = 10.0, duration = 4.0))
        assertEquals(10.0, secB.start)
        assertEquals(14.0, secB.end)

        // 4. Test move bounds for A between 0.0 and B (start 10.0):
        // secA totalDuration is 4.0, so max start is 10.0 - 4.0 = 6.0
        val boundsA = track.getMoveBounds(secA, timeline.maxTime)
        assertEquals(0.0, boundsA.start)
        assertEquals(6.0, boundsA.endInclusive)

        // 5. Test max duration for A before colliding with B:
        // space = 10.0 - 2.0 = 8.0
        val maxDurA = track.getMaxDuration(secA, timeline.maxTime)
        assertEquals(8.0, maxDurA)

        // 6. Test max repeats for A (start=2.0, dur=4.0):
        // space = 8.0, dur = 4.0 -> max 2 repeats
        val maxRepsA = track.getMaxRepeats(secA, timeline.maxTime)
        assertEquals(2, maxRepsA)

        // 7. Add section C between A and B with desired duration 6.0 at start 7.0:
        // Space between 7.0 and B (10.0) is only 3.0.
        // It should clamp duration to 3.0!
        val secC = assertNotNull(track.addSection(start = 7.0, duration = 6.0))
        assertEquals(7.0, secC.start)
        assertEquals(3.0, secC.duration)
        assertEquals(10.0, secC.end)
    }

    @Test
    fun testSectionSelectionAndDeletion() {
        val graphState = GraphState()
        val timeline = TimelineModule()
        graphState.addModule(timeline, Offset.Zero)
        val track = timeline.addTrack(TrackType.MESSAGE) as MessageTrack
        val sec = assertNotNull(track.addSection(start = 1.0, duration = 3.0))

        assertEquals(1, track.sections.size)
        assertNull(graphState.selectedSectionInfo)

        // Select section
        graphState.selectTrackSection(timeline, track, sec)
        assertNotNull(graphState.selectedSectionInfo)
        assertEquals(sec, graphState.selectedSectionInfo?.third)
        assertTrue(graphState.selectedModules.isEmpty())

        // Delete selected section
        graphState.deleteSelected()
        assertTrue(track.sections.isEmpty())
        assertNull(graphState.selectedSectionInfo)
        // Timeline module must NOT have been deleted
        assertTrue(graphState.modules.contains(timeline))
    }

    @Test
    fun testTimelineModuleGetCurrentTime() {
        val testEngine = TestEngine()
        val timeline = TimelineModule()
        timeline.setEngine(testEngine)
        timeline.loopMode = false
        timeline.maxTime = 16.0
        timeline.seekTime = 2.5
        assertEquals(2.5, timeline.getCurrentTime())

        // Start playback at t = 0ns with startPos = 1.0
        val t0 = ScoreTime(0L)
        timeline.startPlayback(t0, 1.0)
        assertTrue(timeline.isPlaying)

        // At t = 0s -> pos = 1.0
        assertEquals(1.0, timeline.getCurrentTime(t0))

        // At t = 2.5s (2_500_000_000 ns) -> pos = 3.5
        val t1 = ScoreTime(2_500_000_000L)
        assertEquals(3.5, timeline.getCurrentTime(t1))

        // Stop playback at t1 -> seekTime should update to 3.5
        timeline.stopPlayback(t1)
        assertFalse(timeline.isPlaying)
        assertEquals(3.5, timeline.seekTime)
        assertEquals(3.5, timeline.getCurrentTime())

        // Test BeatLineModule at 120 bpm (2 beats / sec)
        val beatLine = BeatLineModule()
        beatLine.setEngine(testEngine)
        beatLine.loopMode = false
        beatLine.maxTime = 16.0
        beatLine.tempo.setValue(120.0)
        beatLine.startPlayback(t0, 0.0)
        // At 1.0 second elapsed, should be 2.0 beats
        val t1Sec = ScoreTime(1_000_000_000L)
        assertEquals(2.0, beatLine.getCurrentTime(t1Sec), 0.001)

        // At 2.0 seconds elapsed, should be 4.0 beats
        val t2Sec = ScoreTime(2_000_000_000L)
        assertEquals(4.0, beatLine.getCurrentTime(t2Sec), 0.001)
        beatLine.stopPlayback(t2Sec)
    }

    @Test
    fun testMessageTrackRecordingAndPlaybackSimulation() {
        val testEngine = TestEngine()
        val timeline = TimelineModule()
        timeline.maxTime = 16.0
        timeline.setEngine(testEngine)
        val track = timeline.addTrack(TrackType.MESSAGE) as MessageTrack
        track.isRecordArmed = true

        // Start recording at t = 0s
        val t0 = ScoreTime(0L)
        timeline.startRecording(t0)
        assertTrue(timeline.isRecording)
        assertTrue(timeline.isPlaying)

        // Feed unquantized / arbitrary arrival time messages
        // t = 0.0s -> 0.2
        // t = 1.15s -> 0.5
        // t = 2.48s -> 0.8
        // t = 3.91s -> 1.0
        track.inputJack.receive(Message.FloatValue(t0, 0.2))

        val t1 = ScoreTime(1_150_000_000L)
        track.inputJack.receive(Message.FloatValue(t1, 0.5))

        val t2 = ScoreTime(2_480_000_000L)
        track.inputJack.receive(Message.FloatValue(t2, 0.8))

        val t3 = ScoreTime(3_910_000_000L)
        track.inputJack.receive(Message.FloatValue(t3, 1.0))

        // Stop recording at t = 4.0s
        val t4 = ScoreTime(4_000_000_000L)
        timeline.stopRecording(t4)
        timeline.stopPlayback(t4)

        assertFalse(timeline.isRecording)
        assertFalse(timeline.isPlaying)

        // Verify section data recorded exactly the discrete events at unquantized times
        assertEquals(1, track.sections.size)
        val section = track.sections[0]
        val data = assertNotNull(section.data as? MessageSectionData)
        assertEquals(4, data.events.size)

        assertEquals(0.0, data.events[0].time, 0.001)
        assertEquals(0.2, data.events[0].value, 0.001)

        assertEquals(1.15, data.events[1].time, 0.001)
        assertEquals(0.5, data.events[1].value, 0.001)

        assertEquals(2.48, data.events[2].time, 0.001)
        assertEquals(0.8, data.events[2].value, 0.001)

        assertEquals(3.91, data.events[3].time, 0.001)
        assertEquals(1.0, data.events[3].value, 0.001)

        // Test PLAYBACK simulation:
        testEngine.scheduled.clear()
        val receivedOutputs = mutableListOf<Double>()
        val receiver = object : Module("receiver") {
            val input = addInput("in")
            override fun execute(triggerMessage: Message) {}
            override fun onMessageReceived(jack: InputJack, msg: Message) {
                if (msg is Message.FloatValue) {
                    receivedOutputs.add(msg.value)
                }
            }
        }
        track.outputJack.connect(receiver.input)

        // Start playback from 0.0 to 4.0s
        timeline.startPlayback(ScoreTime(0L), 0.0)

        // Find discrete playback events scheduled before cycle end
        val playbackEvents = testEngine.scheduled.filter { it.first.nanoseconds < 4_000_000_000L }
        assertEquals(4, playbackEvents.size)

        // Execute scheduled events in order
        playbackEvents.forEach { (_, action) -> action() }

        assertEquals(4, receivedOutputs.size)
        assertEquals(0.2, receivedOutputs[0], 0.001)
        assertEquals(0.5, receivedOutputs[1], 0.001)
        assertEquals(0.8, receivedOutputs[2], 0.001)
        assertEquals(1.0, receivedOutputs[3], 0.001)
    }

    @Test
    fun testMessageTrackDynamicDurationExpansion() {
        val testEngine = TestEngine()
        val timeline = TimelineModule()
        timeline.loopMode = false
        timeline.maxTime = 20.0
        timeline.setEngine(testEngine)
        val track = timeline.addTrack(TrackType.MESSAGE) as MessageTrack
        track.isRecordArmed = true

        val t0 = ScoreTime(0L)
        timeline.startRecording(t0)

        // Start recording at t = 0s
        track.inputJack.receive(Message.FloatValue(t0, 0.3))

        // Move fader past initial 4.0s duration, at t = 5.25s
        val t5 = ScoreTime(5_250_000_000L)
        track.inputJack.receive(Message.FloatValue(t5, 0.7))

        val t6 = ScoreTime(6_000_000_000L)
        timeline.stopRecording(t6)
        timeline.stopPlayback(t6)

        val section = track.sections[0]
        val data = assertNotNull(section.data as? MessageSectionData)

        // Verify section duration expanded to encompass 5.25s
        assertTrue(section.duration >= 5.25, "Section duration should have expanded beyond 4.0, was ${section.duration}")
        assertEquals(2, data.events.size)
        assertEquals(0.0, data.events[0].time, 0.001)
        assertEquals(0.3, data.events[0].value, 0.001)
        assertEquals(5.25, data.events[1].time, 0.001)
        assertEquals(0.7, data.events[1].value, 0.001)
    }

    @Test
    fun testTrackSectionDuplication() {
        val timeline = TimelineModule()
        timeline.maxTime = 20.0
        val track = timeline.addTrack(TrackType.MESSAGE) as MessageTrack
        val origSec = track.addSection(start = 0.0, duration = 4.0, repeats = 2, name = "Pattern")
        assertNotNull(origSec)
        val origData = origSec.data as MessageSectionData
        origData.addEvent(1.0, 440.0)
        origData.addEvent(2.5, 880.0)

        // Duplicate to start = 8.0
        val copySec = track.duplicateSection(origSec, 8.0)
        assertNotNull(copySec)
        assertEquals(8.0, copySec.start)
        assertEquals(4.0, copySec.duration)
        assertEquals(2, copySec.repeats)
        assertEquals("Pattern", copySec.name)

        val copyData = assertNotNull(copySec.data as? MessageSectionData)
        assertEquals(2, copyData.events.size)
        assertEquals(1.0, copyData.events[0].time)
        assertEquals(440.0, copyData.events[0].value)

        // Verify deep copy: mutating copy does not affect original
        copyData.addEvent(3.0, 999.0)
        assertEquals(3, copyData.events.size)
        assertEquals(2, origData.events.size)

        // Attempting to duplicate over an existing section should fail
        val overlapping = track.duplicateSection(origSec, 2.0)
        assertNull(overlapping)
    }

    @Test
    fun testPatchTrackDeserializationAndStartStopDispatch() {
        val rootGraph = GraphState()
        val timeline = TimelineModule()
        timeline.loopMode = false
        timeline.maxTime = 16.0
        rootGraph.addModule(timeline, Offset(100f, 100f))

        val patchTrack = timeline.addTrack(TrackType.PATCH, "Synth") as PatchTrack
        patchTrack.addSection(start = 2.0, duration = 4.0, repeats = 1)

        val envCreator = ModuleRegistry.findCreator("ksyn.control.envDAHDSR") ?: ModuleRegistry.findCreator("envDAHDSR")
        assertNotNull(envCreator, "envDAHDSR should be available in ModuleRegistry")
        val envMod = envCreator()
        patchTrack.patch.graphState.addModule(envMod, Offset(200f, 100f))

        val startMod = patchTrack.startCommandModule
        val stopMod = patchTrack.stopCommandModule
        val onJack = envMod.inputs.find { it.name == "on" }!!
        val offJack = envMod.inputs.find { it.name == "off" }!!

        patchTrack.patch.graphState.connect(startMod.output, onJack)
        patchTrack.patch.graphState.connect(stopMod.output, offJack)

        assertTrue(patchTrack.patch.graphState.connections.any { it.output === startMod.output && it.input === onJack })
        assertTrue(patchTrack.patch.graphState.connections.any { it.output === stopMod.output && it.input === offJack })

        // Serialize the root graph
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules.toList())

        // Deserialize into a new graph
        val newGraph = GraphState()
        GraphSerializer.deserialize(newGraph, json)

        val newTimeline = newGraph.modules.filterIsInstance<TimelineModule>().first()
        val newPatchTrack = newTimeline.tracks.filterIsInstance<PatchTrack>().first()

        // 1. Verify no duplicate start/stop modules exist
        val commandMods = newPatchTrack.patch.graphState.modules.filterIsInstance<CommandModule>()
        assertEquals(2, commandMods.size, "Should have exactly 2 command modules (start and stop), not duplicates")
        assertEquals(1, commandMods.count { it.name == "start" })
        assertEquals(1, commandMods.count { it.name == "stop" })

        // 2. Verify startCommandModule and stopCommandModule dynamically resolve to the connected modules
        val loadedStartMod = newPatchTrack.startCommandModule
        val loadedStopMod = newPatchTrack.stopCommandModule
        val loadedEnvMod = newPatchTrack.patch.graphState.modules.find { it.name.contains("envDAHDSR") || it.type.contains("envDAHDSR") }!!
        val loadedOnJack = loadedEnvMod.inputs.find { it.name == "on" }!!
        val loadedOffJack = loadedEnvMod.inputs.find { it.name == "off" }!!

        assertTrue(newPatchTrack.patch.graphState.connections.any { it.output === loadedStartMod.output && it.input === loadedOnJack }, "startCommandModule output must be connected to env on")
        assertTrue(newPatchTrack.patch.graphState.connections.any { it.output === loadedStopMod.output && it.input === loadedOffJack }, "stopCommandModule output must be connected to env off")

        // 3. Test playback scheduling and execution
        val engine = TestEngine()
        newTimeline.setEngine(engine)
        newTimeline.startPlayback(ScoreTime(1_000_000_000L), 0.0)

        assertFalse(newPatchTrack.isPatchRunning)

        val startSecEvent = engine.scheduled.find { (time, _) ->
            val delaySec = (time.nanoseconds - 1_000_000_000L) / 1_000_000_000.0
            kotlin.math.abs(delaySec - 2.0) < 0.001
        }
        assertNotNull(startSecEvent, "Should schedule patch start at 2.0s")
        startSecEvent.second()
        assertTrue(newPatchTrack.isPatchRunning, "Patch should be running after start event fires")

        val stopSecEvent = engine.scheduled.find { (time, _) ->
            val delaySec = (time.nanoseconds - 1_000_000_000L) / 1_000_000_000.0
            kotlin.math.abs(delaySec - 6.0) < 0.001
        }
        assertNotNull(stopSecEvent, "Should schedule patch stop at 6.0s")
        stopSecEvent.second()
        assertFalse(newPatchTrack.isPatchRunning, "Patch should be stopped after stop event fires")
    }

    @Test
    fun testMultitrackTestFileDeserializationAndPlayback() {
        val json = readFileDirect("/Users/phil/Documents/Syntona/multitrack_test.sntn")
        if (json == null) {
            println("multitrack_test.sntn not found on disk, skipping file test")
            return
        }

        val graph = GraphState()
        GraphSerializer.deserialize(graph, json)

        val beatLine = graph.modules.filterIsInstance<TimelineModule>().firstOrNull()
        assertNotNull(beatLine, "beatLine module should be present")

        val patchTrack = beatLine.tracks.filterIsInstance<PatchTrack>().firstOrNull()
        assertNotNull(patchTrack, "PatchTrack should be present")
        assertEquals("Patch 2", patchTrack.name)

        // Modules in Track 2 inner patch: start, stop, sineOsc, envDAHDSR, aOut1 = 5 modules
        val innerModules = patchTrack.patch.graphState.modules
        assertEquals(5, innerModules.size, "Should have exactly 5 modules inside Track 2 patch (no duplicates)")

        val startMod = patchTrack.startCommandModule
        val stopMod = patchTrack.stopCommandModule
        assertEquals("start", startMod.name)
        assertEquals("stop", stopMod.name)

        val envMod = innerModules.find { it.name == "envDAHDSR" }
        assertNotNull(envMod, "envDAHDSR module must exist")
        val onJack = envMod.inputs.find { it.name == "on" }!!
        val offJack = envMod.inputs.find { it.name == "off" }!!

        assertTrue(patchTrack.patch.graphState.connections.any { it.output === startMod.output && it.input === onJack }, "startCommandModule must be connected to envDAHDSR on")
        assertTrue(patchTrack.patch.graphState.connections.any { it.output === stopMod.output && it.input === offJack }, "stopCommandModule must be connected to envDAHDSR off")

        assertTrue(patchTrack.sections.isNotEmpty(), "Sections should be present in multitrack_test.sntn")

        // Test playback dispatch
        val engine = TestEngine()
        beatLine.setEngine(engine)
        val baseNow = ScoreTime(1_000_000_000L)
        beatLine.startPlayback(baseNow, 0.0)

        assertFalse(patchTrack.isPatchRunning)

        val s0 = patchTrack.sections[0]
        val s0Start = beatLine.unitsToSeconds(s0.start)
        val s0Stop = beatLine.unitsToSeconds(s0.start + s0.totalDuration)
        val sCycleEnd = beatLine.unitsToSeconds(if (beatLine.loopMode) beatLine.loopEnd else beatLine.maxTime)

        val start0 = engine.scheduled.find {
            val s = (it.first.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
            kotlin.math.abs(s - s0Start) < 0.001
        }
        assertNotNull(start0, "Should schedule section 0 start at $s0Start seconds")
        start0.second()
        assertTrue(patchTrack.isPatchRunning)

        val stop0 = engine.scheduled.find {
            val s = (it.first.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
            kotlin.math.abs(s - s0Stop) < 0.001
        }
        assertNotNull(stop0, "Should schedule section 0 stop at $s0Stop seconds")
        stop0.second()
        assertFalse(patchTrack.isPatchRunning)

        // At cycle end: stopPlayback called
        val cycleEnd = engine.scheduled.find {
            val s = (it.first.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
            kotlin.math.abs(s - sCycleEnd) < 0.001
        }
        assertNotNull(cycleEnd, "Should schedule cycle end at $sCycleEnd seconds")
        cycleEnd.second()
        assertFalse(beatLine.isPlaying)
    }

    @Test
    fun testTrackMessageEventAndSectionDataEditing() {
        val event = TrackMessageEvent(0.5, 42.0)
        val (t, v) = event
        assertEquals(0.5, t)
        assertEquals(42.0, v)

        // Mutating fields
        event.time = 0.75
        event.value = 84.0
        assertEquals(0.75, event.time)
        assertEquals(84.0, event.value)

        // Section data add, move, remove
        val data = MessageSectionData(initialMin = 0.0, initialMax = 100.0)
        val ev1 = data.addEvent(0.2, 10.0)
        val ev2 = data.addEvent(0.8, 50.0)
        assertEquals(2, data.events.size)

        // Move event
        ev1.time = 0.3
        ev1.value = 15.0
        assertEquals(0.3, data.events[0].time)
        assertEquals(15.0, data.events[0].value)

        // Remove event
        data.removeEvent(ev1)
        assertEquals(1, data.events.size)
        assertEquals(ev2, data.events[0])
    }

    @Test
    fun testSubdivisionSnappingSmallIncrements() {
        // 1/16 subdivision = 0.0625
        val sub16 = 1.0 / 16.0
        val snapped1 = TimelineBeatUtils.snapToSubdivision(0.07, sub16)
        assertEquals(0.0625, snapped1, 0.0001)

        val snapped2 = TimelineBeatUtils.snapToSubdivision(0.12, sub16)
        assertEquals(0.125, snapped2, 0.0001)

        // 1/32 subdivision = 0.03125
        val sub32 = 1.0 / 32.0
        val snapped3 = TimelineBeatUtils.snapToSubdivision(0.035, sub32)
        assertEquals(0.03125, snapped3, 0.0001)
    }

    @Test
    fun testPatchTrackRepeatsSchedulingNoConflictingStop() {
        val rootGraph = GraphState()
        val timeline = TimelineModule()
        timeline.loopMode = false
        timeline.maxTime = 20.0
        rootGraph.addModule(timeline, Offset.Zero)

        val patchTrack = PatchTrack(1, "Synth", timeline)
        timeline.tracks.add(patchTrack)

        // Add section with 3 repeats: [2..6), [6..10), [10..14)
        patchTrack.addSection(start = 2.0, duration = 4.0, repeats = 3)

        val engine = TestEngine()
        timeline.setEngine(engine)
        val baseNow = ScoreTime(1_000_000_000L)
        timeline.startPlayback(baseNow, 0.0)

        // Converts time to seconds relative to baseNow
        val eventsAtTime = { sec: Double ->
            engine.scheduled.filter { (time, _) ->
                val s = (time.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
                kotlin.math.abs(s - sec) < 0.001
            }
        }

        // At 2.0s: Repeat 0 starts -> exactly 1 event (startPatch)
        val eventsAt2 = eventsAtTime(timeline.unitsToSeconds(2.0))
        assertEquals(1, eventsAt2.size, "Should have 1 event at repeat 0 start (2.0s)")

        // At 6.0s: Repeat 1 starts -> exactly 1 event (startPatch, NO stopPatch)
        val eventsAt6 = eventsAtTime(timeline.unitsToSeconds(6.0))
        assertEquals(1, eventsAt6.size, "Should have exactly 1 event at repeat 1 start (6.0s), no conflicting stopPatch")

        // At 10.0s: Repeat 2 starts -> exactly 1 event (startPatch, NO stopPatch)
        val eventsAt10 = eventsAtTime(timeline.unitsToSeconds(10.0))
        assertEquals(1, eventsAt10.size, "Should have exactly 1 event at repeat 2 start (10.0s), no conflicting stopPatch")

        // At 14.0s: Repeat 2 ends -> exactly 1 event (stopPatch)
        val eventsAt14 = eventsAtTime(timeline.unitsToSeconds(14.0))
        assertEquals(1, eventsAt14.size, "Should have exactly 1 event at repeat 2 end (14.0s) for stopPatch")

        // Fire events in sequence and verify patch state
        assertFalse(patchTrack.isPatchRunning)
        eventsAt2[0].second()
        assertTrue(patchTrack.isPatchRunning, "Patch should be running during repeat 0")

        eventsAt6[0].second()
        assertTrue(patchTrack.isPatchRunning, "Patch should remain running during repeat 1")

        eventsAt10[0].second()
        assertTrue(patchTrack.isPatchRunning, "Patch should remain running during repeat 2")

        eventsAt14[0].second()
        assertFalse(patchTrack.isPatchRunning, "Patch should stop after final repeat ends")
    }

    @Test
    fun testScheduledQueueFifoOrderingForEqualTimestamps() {
        val queue = com.syntona.core.model.ScheduledQueue<String>()
        val time = ScoreTime(100_000_000L)

        queue.add(time, "First")
        queue.add(time, "Second")
        queue.add(time, "Third")

        assertEquals("First", queue.removeNext(time))
        assertEquals("Second", queue.removeNext(time))
        assertEquals("Third", queue.removeNext(time))
        assertNull(queue.removeNext(time))
    }
}

