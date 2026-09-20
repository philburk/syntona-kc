package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.editor.AddSectionCommand
import com.syntona.dataflow.editor.DeleteModulesCommand
import com.syntona.dataflow.editor.DeleteSectionCommand
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
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
import com.syntona.util.SegmentedFloatArray
import com.softsynth.ksyn.Synthesizer
import com.syntona.core.audio.writeWavFile
import com.syntona.dataflow.editor.CopyPasteService
import com.syntona.dataflow.editor.readBinaryFileDirect
import com.syntona.util.resolvePath
import com.softsynth.ksyn.util.SampleLoader
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTrackTest {

    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, () -> Unit>>()
        private val events = mutableListOf<Pair<ScoreTime, ScheduledEvent>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            scheduled.add(time to action)
            return ScheduledEvent(action).also { events.add(time to it) }
        }

        /** Fires events due by [time] in time order, skipping cancelled ones, like the real engine. */
        fun fireUntil(time: ScoreTime) {
            while (true) {
                val next = events
                    .filter { !it.second.isCancelled && it.first.nanoseconds <= time.nanoseconds }
                    .minByOrNull { it.first.nanoseconds } ?: break
                events.remove(next)
                next.second.fire()
            }
        }
    }

    private fun ScoreTime.plusSeconds(seconds: Double) = this + ScoreTime((seconds * 1_000_000_000.0).toLong())

    @Test
    fun testTrackAdditionAndJackCreation() {
        val timeline = TimelineModule()
        assertEquals(0, timeline.tracks.size)

        // 1. Add Patch Track
        val patchTrack = timeline.addTrack(TrackType.PATCH)
        assertEquals(1, timeline.tracks.size)
        assertTrue(patchTrack is PatchTrack)
        assertEquals("Patch 1", patchTrack.name)
        assertNull(patchTrack.inputJack)
        assertNull(patchTrack.outputJack)

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
        timeline.teardown()
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
        beatLine.teardown()
    }

    @Test
    fun testSerializationRoundTrip() {
        val graphState = GraphState()
        val timeline = TimelineModule()
        graphState.addModule(timeline, Offset.Zero)

        val patchTrack = timeline.addTrack(TrackType.PATCH, "MyPatchTrack")
        patchTrack.isMuted = true
        patchTrack.isSoloed = false

        val audioTrack = timeline.addTrack(TrackType.AUDIO, "VocalTrack") as AudioTrack
        audioTrack.isSoloed = true
        audioTrack.isRecordArmed = true
        audioTrack.isInputMonitored = true
        audioTrack.addSectionDirect(id = 10, start = 1.5, duration = 3.0, repeats = 2, name = "Take 1")

        val json = GraphSerializer.serialize(graphState, graphState.modules)

        val loadedState = GraphState()
        GraphSerializer.deserialize(loadedState, json)

        val loadedTimeline = loadedState.modules.filterIsInstance<TimelineModule>().first()
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

        timeline.teardown()
        loadedTimeline.teardown()
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
        // Playback not started yet -> onAudibilityChanged stops patch
        patchTrack.onAudibilityChanged()
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
    fun testRecordingStateLifecycle() {
        val timeline = TimelineModule()
        timeline.addTrack(TrackType.MESSAGE)
        assertFalse(timeline.isRecording)

        timeline.startRecording(ScoreTime.Zero)
        assertTrue(timeline.isRecording)

        timeline.stopRecording(ScoreTime.Zero)
        assertFalse(timeline.isRecording)
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

    @Test
    fun testMidiSectionDataAndNotes() {
        val secData = MidiSectionData()
        assertEquals(0, secData.pianoRoll.notes.size)

        val n1 = secData.addNote(startTicks = 0, durationTicks = 240, pitch = 60.0)
        assertEquals(0, n1.startTicks)
        assertEquals(240, n1.durationTicks)
        assertEquals(60.0, n1.rootPitch)
        assertEquals(60.0, secData.pianoRoll.pitchOf(n1))

        // Add dependent note with ratio 3/2 (perfect fifth up: ~7.02 semitones)
        val nid = secData.pianoRoll.allocateNoteId()
        val n2 = PianoRollNote(
            id = nid,
            startTicks = 240,
            durationTicks = 240,
            parentId = n1.id,
            ratio = Ratio(3, 2),
            rootPitch = 60.0
        )
        secData.pianoRoll.notes.add(n2)
        assertEquals(2, secData.pianoRoll.notes.size)
        val p2 = secData.pianoRoll.pitchOf(n2)
        assertTrue(p2 > 67.0 && p2 < 67.03, "Note 2 should be at ~67.02 semitones, was $p2")

        // Deep copy
        val copied = secData.copy()
        assertEquals(2, copied.pianoRoll.notes.size)
        assertEquals(secData.pianoRoll.notes[0].startTicks, copied.pianoRoll.notes[0].startTicks)
        assertEquals(secData.pianoRoll.notes[1].ratio, copied.pianoRoll.notes[1].ratio)
    }

    @Test
    fun testMidiTrackRecordingNotePairing() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        midiTrack.isRecordArmed = true

        val engine = TestEngine()
        beatLine.setEngine(engine)

        val startNow = ScoreTime(1_000_000_000L)
        beatLine.startRecording(startNow)
        assertTrue(beatLine.isRecording)

        // Note On at 1.0 beat (ScoreTime + 0.5s at 120bpm)
        val onTime = startNow + ScoreTime(500_000_000L)
        val noteOnMsg = Message.VoiceMessage(
            onTime,
            Message.VoiceOpcode.NOTE_ON,
            tag = 60,
            value = 60.0,
            extra = 0.8
        )
        midiTrack.onMessageReceived(midiTrack.inputJack, noteOnMsg)

        // Note Off at 2.0 beats (ScoreTime + 1.0s)
        val offTime = startNow + ScoreTime(1_000_000_000L)
        val noteOffMsg = Message.VoiceMessage(
            offTime,
            Message.VoiceOpcode.NOTE_OFF,
            tag = 60,
            value = 60.0,
            extra = 0.0
        )
        midiTrack.onMessageReceived(midiTrack.inputJack, noteOffMsg)

        beatLine.stopRecording(startNow + ScoreTime(1_500_000_000L))
        assertFalse(beatLine.isRecording)

        // Verify section created and recorded note
        assertTrue(midiTrack.sections.isNotEmpty(), "Section should be created on recording")
        val sec = midiTrack.sections.first()
        val secData = sec.data as? MidiSectionData
        assertNotNull(secData, "Section should have MidiSectionData")
        assertEquals(1, secData.pianoRoll.notes.size)

        val recordedNote = secData.pianoRoll.notes.first()
        assertEquals(60.0, recordedNote.rootPitch)
        // 1 beat duration = 240 ticks
        assertEquals(240, recordedNote.durationTicks)
    }

    @Test
    fun testMidiTrackDynamicSectionExpansion() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sec = midiTrack.addSection(start = 0.0, duration = 2.0)
        assertNotNull(sec)
        assertEquals(2.0, sec.duration)

        midiTrack.isRecordArmed = true
        val engine = TestEngine()
        beatLine.setEngine(engine)

        val startNow = ScoreTime(1_000_000_000L)
        beatLine.startRecording(startNow)

        // Note On at 0.5 beats
        val onTime = startNow + ScoreTime(250_000_000L)
        midiTrack.onMessageReceived(
            midiTrack.inputJack,
            Message.VoiceMessage(onTime, Message.VoiceOpcode.NOTE_ON, tag = 64, value = 64.0, extra = 0.7)
        )

        // Note Off at 3.5 beats (exceeding initial 2.0 duration)
        val offTime = startNow + ScoreTime(1_750_000_000L)
        midiTrack.onMessageReceived(
            midiTrack.inputJack,
            Message.VoiceMessage(offTime, Message.VoiceOpcode.NOTE_OFF, tag = 64, value = 64.0, extra = 0.0)
        )

        beatLine.stopRecording(offTime)

        // Duration should have expanded to hold the note
        assertTrue(sec.duration >= 3.5, "Section duration should expand, was ${sec.duration}")
    }

    @Test
    fun testMidiTrackPlaybackSchedulingAndChase() {
        val beatLine = BeatLineModule()
        beatLine.loopMode = false
        beatLine.maxTime = 16.0
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sec = midiTrack.addSection(start = 1.0, duration = 4.0)!!
        val secData = sec.data as MidiSectionData
        secData.addNote(startTicks = 0, durationTicks = 240, pitch = 60.0) // 1.0 to 2.0 beats
        secData.addNote(startTicks = 480, durationTicks = 240, pitch = 64.0) // 3.0 to 4.0 beats

        val engine = TestEngine()
        beatLine.setEngine(engine)

        val sentMessages = mutableListOf<Message.VoiceMessage>()
        val spyJack = object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sentMessages.add(msg)
            }
        }
        midiTrack.outputJack.connect(spyJack)

        val baseNow = ScoreTime(1_000_000_000L)
        beatLine.startPlayback(baseNow, 0.0)

        // Verify scheduled events
        assertTrue(engine.scheduled.isNotEmpty(), "Events should be scheduled for playback")

        // 1. Note 1 ON at 1.0 beat (0.5s at 120bpm)
        val on1 = engine.scheduled.find {
            val s = (it.first.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
            kotlin.math.abs(s - 0.5) < 0.001
        }
        assertNotNull(on1, "Should schedule Note 1 ON at 0.5s")
        on1.second()

        assertEquals(1, sentMessages.size)
        assertEquals(Message.VoiceOpcode.NOTE_ON, sentMessages[0].opcode)
        assertEquals(60.0, sentMessages[0].value)

        // 2. Note 1 OFF at 2.0 beats (1.0s at 120bpm)
        val off1 = engine.scheduled.find {
            val s = (it.first.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
            kotlin.math.abs(s - 1.0) < 0.001
        }
        assertNotNull(off1, "Should schedule Note 1 OFF at 1.0s")
        off1.second()

        assertEquals(2, sentMessages.size)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, sentMessages[1].opcode)
        assertEquals(sentMessages[0].tag, sentMessages[1].tag, "NOTE_ON and NOTE_OFF should have matching voice tags")

        // Test Chase Notes: start playback in the middle of Note 1 (at 1.5 beats)
        sentMessages.clear()
        engine.scheduled.clear()
        beatLine.startPlayback(baseNow, 1.5)

        // Note 1 should have fired NOTE_ON immediately at baseNow
        assertEquals(1, sentMessages.size, "Chase note should fire NOTE_ON immediately")
        assertEquals(Message.VoiceOpcode.NOTE_ON, sentMessages[0].opcode)
        assertEquals(60.0, sentMessages[0].value)
    }

    @Test
    fun testMidiTrackPlaybackIgnoresNotesBeyondShortenedSection() {
        val beatLine = BeatLineModule()
        beatLine.loopMode = false
        beatLine.maxTime = 16.0
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sec = midiTrack.addSection(start = 0.0, duration = 4.0)!!
        val secData = sec.data as MidiSectionData
        secData.addNote(startTicks = 0, durationTicks = 240, pitch = 60.0) // 0.0 to 1.0 beats
        secData.addNote(startTicks = 720, durationTicks = 240, pitch = 64.0) // 3.0 to 4.0 beats
        sec.duration = 2.0 // Second note now starts past the end of the section

        val engine = TestEngine()
        beatLine.setEngine(engine)

        val sentMessages = mutableListOf<Message.VoiceMessage>()
        val spyJack = object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sentMessages.add(msg)
            }
        }
        midiTrack.outputJack.connect(spyJack)

        val baseNow = ScoreTime(1_000_000_000L)
        beatLine.startPlayback(baseNow, 0.0)
        engine.scheduled.sortedBy { it.first.nanoseconds }.forEach { it.second() }

        val notes = sentMessages.filter {
            it.opcode == Message.VoiceOpcode.NOTE_ON || it.opcode == Message.VoiceOpcode.NOTE_OFF
        }
        assertEquals(2, notes.size, "Only the note inside the section should play")
        assertTrue(notes.all { it.value == 60.0 })
        assertEquals(Message.VoiceOpcode.NOTE_ON, notes[0].opcode)
        assertEquals(Message.VoiceOpcode.NOTE_OFF, notes[1].opcode)
    }

    @Test
    fun testMessageTrackPlaybackIgnoresEventsBeyondShortenedSection() {
        val testEngine = TestEngine()
        val timeline = TimelineModule()
        timeline.loopMode = false
        timeline.maxTime = 16.0
        timeline.setEngine(testEngine)
        val track = timeline.addTrack(TrackType.MESSAGE) as MessageTrack
        val sec = track.addSection(start = 0.0, duration = 4.0)!!
        val data = sec.data as MessageSectionData
        data.addEvent(1.0, 0.25)
        data.addEvent(3.0, 0.75)
        sec.duration = 2.0 // Second event now lies past the end of each repeat
        sec.repeats = 2

        val received = mutableListOf<Double>()
        val spyJack = object : InputJack("spy", timeline) {
            override fun receive(msg: Message) {
                if (msg is Message.FloatValue) received.add(msg.value)
            }
        }
        track.outputJack.connect(spyJack)

        val baseNow = ScoreTime(0L)
        timeline.startPlayback(baseNow, 0.0)
        val trackEvents = testEngine.scheduled
            .map { (it.first.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0 }
            .filter { it < timeline.maxTime - 0.001 } // Exclude the cycle end event
            .sorted()
        assertEquals(listOf(1.0, 3.0), trackEvents, "Only the 1.0 event should play, once per repeat")

        testEngine.scheduled.sortedBy { it.first.nanoseconds }.forEach { it.second() }
        assertEquals(listOf(0.25, 0.25), received)
    }

    @Test
    fun testMidiTrackPlaybackTagsAreUniqueAcrossTracksAndRepeats() {
        val beatLine = BeatLineModule()
        beatLine.loopMode = false
        beatLine.maxTime = 16.0
        val engine = TestEngine()
        beatLine.setEngine(engine)

        // Two tracks with identical section and note ids, as when both feed the same synth
        val tracks = List(2) { beatLine.addTrack(TrackType.MIDI) as MidiTrack }
        val sentByTrack = tracks.associateWith { mutableListOf<Message.VoiceMessage>() }
        tracks.forEach { track ->
            val sec = track.addSectionDirect(id = 0, start = 0.0, duration = 1.0, repeats = 2)
            (sec.data as MidiSectionData).addNote(startTicks = 0, durationTicks = 240, pitch = 60.0) // Fills each repeat
            val spy = object : InputJack("spy${track.id}", beatLine) {
                override fun receive(msg: Message) {
                    if (msg is Message.VoiceMessage) sentByTrack.getValue(track).add(msg)
                }
            }
            track.outputJack.connect(spy)
        }

        beatLine.startPlayback(ScoreTime.Zero, 0.0)
        engine.scheduled.sortedBy { it.first.nanoseconds }.forEach { it.second() }

        val allOnTags = mutableListOf<Int>()
        sentByTrack.values.forEach { sent ->
            val ons = sent.filter { it.opcode == Message.VoiceOpcode.NOTE_ON }
            val offs = sent.filter { it.opcode == Message.VoiceOpcode.NOTE_OFF }
            assertEquals(2, ons.size, "One NOTE_ON per repeat")
            assertEquals(ons.map { it.tag }.toSet(), offs.map { it.tag }.toSet(), "Each NOTE_OFF matches a NOTE_ON")
            allOnTags.addAll(ons.map { it.tag })
        }
        assertEquals(4, allOnTags.toSet().size, "Tags must differ across tracks and repeats")
        assertTrue(allOnTags.all { it > 127 }, "Tags must not collide with MIDI note numbers")
    }

    @Test
    fun testSoloAppliesAcrossTrackTypes() {
        val beatLine = BeatLineModule()
        beatLine.loopMode = false
        val engine = TestEngine()
        beatLine.setEngine(engine)

        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sec = midiTrack.addSection(start = 0.0, duration = 4.0)!!
        (sec.data as MidiSectionData).addNote(startTicks = 0, durationTicks = 960, pitch = 60.0)
        val audioTrack = beatLine.addTrack(TrackType.AUDIO) as AudioTrack

        val sent = mutableListOf<Message.VoiceMessage>()
        midiTrack.outputJack.connect(object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sent.add(msg)
            }
        })

        // Soloing the MIDI track silences the audio track
        assertTrue(audioTrack.unit.isAudible)
        midiTrack.isSoloed = true
        assertFalse(audioTrack.unit.isAudible, "Audio track should be silenced when a MIDI track is soloed")
        midiTrack.isSoloed = false
        assertTrue(audioTrack.unit.isAudible)

        // Soloing the audio track ends the MIDI note that is sounding
        beatLine.startPlayback(ScoreTime.Zero, 1.0) // Chases the note
        assertEquals(Message.VoiceOpcode.NOTE_ON, sent.last().opcode)
        audioTrack.isSoloed = true
        assertEquals(Message.VoiceOpcode.NOTE_OFF, sent.last().opcode, "MIDI note should end when an audio track is soloed")

        beatLine.stopPlayback(ScoreTime.Zero)
        audioTrack.teardown()
    }

    @Test
    fun testPatchTrackNotRestartedWhenAnotherTrackIsSoloed() {
        val timeline = TimelineModule()
        timeline.loopMode = false
        val engine = TestEngine()
        timeline.setEngine(engine)

        val patchTrack = timeline.addTrack(TrackType.PATCH) as PatchTrack
        patchTrack.addSection(start = 0.0, duration = 4.0)
        val otherTrack = timeline.addTrack(TrackType.PATCH) as PatchTrack

        var startCount = 0
        patchTrack.startCommandModule.output.connect(object : InputJack("spy", timeline) {
            override fun receive(msg: Message) {
                startCount++
            }
        })

        timeline.startPlayback(ScoreClock.now(), 1.0)
        assertTrue(patchTrack.isPatchRunning)
        assertEquals(1, startCount)

        // Toggling solo on another track leaves this patch audible, so it must not restart
        patchTrack.isSoloed = true
        otherTrack.isSoloed = true
        otherTrack.isSoloed = false
        assertTrue(patchTrack.isPatchRunning)
        assertEquals(1, startCount, "A running patch should not receive another start")

        // Soloing only the other track stops this patch; unsoloing starts it again
        patchTrack.isSoloed = false
        otherTrack.isSoloed = true
        assertFalse(patchTrack.isPatchRunning)
        otherTrack.isSoloed = false
        assertTrue(patchTrack.isPatchRunning)
        assertEquals(2, startCount)

        timeline.stopPlayback(ScoreClock.now())
    }

    @Test
    fun testMidiTrackMutePassesNoteOffs() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        midiTrack.isInputMonitored = true
        val sec = midiTrack.addSection(start = 0.0, duration = 4.0)!!
        val bridge = assertNotNull((sec.data as MidiSectionData).bridgeJack)

        val sent = mutableListOf<Message.VoiceMessage>()
        midiTrack.outputJack.connect(object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sent.add(msg)
            }
        })
        fun voice(opcode: Message.VoiceOpcode, amplitude: Double = 0.5) =
            Message.VoiceMessage(ScoreTime.Zero, opcode, tag = 60, value = 60.0, extra = amplitude)

        // A monitored key is held down, then the track is muted before release
        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_ON))
        midiTrack.isMuted = true
        sent.clear()

        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_ON))
        assertTrue(sent.isEmpty(), "Muted track should block NOTE_ON")
        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_OFF))
        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_ON, amplitude = 0.0))
        assertEquals(2, sent.size, "Muted track should pass monitored note-offs")

        // Same rule for notes played from the piano roll editor
        sent.clear()
        bridge.receive(voice(Message.VoiceOpcode.NOTE_ON))
        bridge.receive(voice(Message.VoiceOpcode.NOTE_OFF))
        assertEquals(listOf(Message.VoiceOpcode.NOTE_OFF), sent.map { it.opcode })
    }

    @Test
    fun testMidiTrackMonitorOffPassesNoteOffForHeldKey() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sent = mutableListOf<Message.VoiceMessage>()
        midiTrack.outputJack.connect(object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sent.add(msg)
            }
        })
        fun voice(opcode: Message.VoiceOpcode, tag: Int) =
            Message.VoiceMessage(ScoreTime.Zero, opcode, tag = tag, value = tag.toDouble(), extra = 0.5)

        // Key 60 is held while monitoring is turned off
        midiTrack.isInputMonitored = true
        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_ON, 60))
        midiTrack.isInputMonitored = false
        sent.clear()

        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_ON, 64))
        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_OFF, 64))
        assertTrue(sent.isEmpty(), "Unmonitored notes should not pass")

        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_OFF, 60))
        assertEquals(listOf(60), sent.map { it.tag }, "Held key's NOTE_OFF should still pass")

        // Only once: a repeated NOTE_OFF for the same key is blocked again
        midiTrack.inputJack.receive(voice(Message.VoiceOpcode.NOTE_OFF, 60))
        assertEquals(1, sent.size)
    }

    @Test
    fun testAddSectionCommandCreatesSectionAndSupportsUndoRedo() {
        val timeline = TimelineModule()
        val track = timeline.addTrack(TrackType.MESSAGE)
        val added = mutableListOf<TrackSection>()

        val cmd = AddSectionCommand(track, { track.addSection(1.0, 2.0) }) { added.add(it) }
        assertFalse(cmd.isUndoable, "Nothing to undo before it runs")
        cmd.execute()
        val sec = assertNotNull(cmd.section)
        assertTrue(cmd.isUndoable)
        assertEquals(listOf(sec), track.sections.toList())
        assertEquals(listOf(sec), added)

        cmd.undo()
        assertTrue(track.sections.isEmpty())
        cmd.execute() // Redo restores the same section without creating a new one
        assertEquals(listOf(sec), track.sections.toList())
        assertEquals(1, added.size)

        // No room: nothing is added and the command is not recorded for undo
        val blocked = AddSectionCommand(track, { track.addSection(1.5, 2.0) })
        blocked.execute()
        assertNull(blocked.section)
        assertFalse(blocked.isUndoable)
        assertEquals(1, track.sections.size)
    }

    @Test
    fun testDeleteSectionCommandUndoRestoresOrder() {
        val timeline = TimelineModule()
        val track = timeline.addTrack(TrackType.MESSAGE)
        val first = track.addSection(0.0, 1.0)!!
        val middle = track.addSection(2.0, 1.0)!!
        val last = track.addSection(4.0, 1.0)!!

        val cmd = DeleteSectionCommand(track, middle)
        cmd.execute()
        assertEquals(listOf(first, last), track.sections.toList())
        cmd.undo()
        assertEquals(listOf(first, middle, last), track.sections.toList())
    }

    @Test
    fun testMessageSectionInsertEventKeepsObjectAndWidensRange() {
        val data = MessageSectionData(initialMin = 0.0, initialMax = 1.0)
        data.addEvent(1.0, 0.5)
        data.addEvent(3.0, 0.5)

        val ev = TrackMessageEvent(2.0, 5.0)
        data.insertEvent(ev)
        assertTrue(data.events[1] === ev, "Inserted in time order as the same object")
        assertEquals(5.0, data.max)
        assertEquals(5.0, ev.value, "Value is not clamped")
    }

    @Test
    fun testRunOnEngineRunsDirectlyWithoutEngine() {
        val timeline = TimelineModule()
        var ran = false
        timeline.runOnEngine { ran = true }
        assertTrue(ran)
    }

    // Section edit tests below share one layout at 120 BPM, playing from beat 1:
    // A (beats 0-4) sounds under the playhead and is deleted while sounding,
    // B (beat 8) is deleted before it starts, and C (beat 12) is moved to beat 5,
    // so it starts at 2.0 s instead of 5.5 s. Editing B and C must not restart A.

    private fun beatLineForSectionEdits(engine: TestEngine) = BeatLineModule().apply {
        loopMode = false
        maxTime = 16.0
        setEngine(engine)
    }

    @Test
    fun testMidiTrackSectionEditsDuringPlayback() {
        val engine = TestEngine()
        val beatLine = beatLineForSectionEdits(engine)
        val track = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        fun sectionWithNote(start: Double, duration: Double, pitch: Double) =
            track.addSection(start, duration)!!.also {
                (it.data as MidiSectionData).addNote(startTicks = 0, durationTicks = timelineTicks(duration), pitch = pitch)
            }
        val a = sectionWithNote(0.0, 4.0, 60.0)
        val b = sectionWithNote(8.0, 2.0, 67.0)
        val c = sectionWithNote(12.0, 2.0, 72.0)

        val sent = mutableListOf<Message.VoiceMessage>()
        track.outputJack.connect(object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sent.add(msg)
            }
        })
        fun count(opcode: Message.VoiceOpcode, pitch: Double) = sent.count { it.opcode == opcode && it.value == pitch }

        val base = ScoreClock.now()
        beatLine.startPlayback(base, 1.0)
        assertEquals(1, count(Message.VoiceOpcode.NOTE_ON, 60.0), "Note in A is chased")

        track.removeSection(b)
        track.moveSection(c, 5.0)
        assertEquals(1, count(Message.VoiceOpcode.NOTE_ON, 60.0), "Sounding note must not be retriggered")
        assertEquals(0, count(Message.VoiceOpcode.NOTE_OFF, 60.0))

        track.removeSection(a)
        assertEquals(1, count(Message.VoiceOpcode.NOTE_OFF, 60.0), "Deleting A ends its sounding note")

        engine.fireUntil(base.plusSeconds(1.9))
        assertEquals(0, count(Message.VoiceOpcode.NOTE_ON, 72.0))
        engine.fireUntil(base.plusSeconds(2.1))
        assertEquals(1, count(Message.VoiceOpcode.NOTE_ON, 72.0), "Moved section C plays at its new time")

        engine.fireUntil(base.plusSeconds(7.0))
        assertEquals(0, count(Message.VoiceOpcode.NOTE_ON, 67.0), "Deleted section B never plays")
        assertEquals(count(Message.VoiceOpcode.NOTE_ON, 72.0), count(Message.VoiceOpcode.NOTE_OFF, 72.0))
        beatLine.stopPlayback(ScoreClock.now())
    }

    private fun timelineTicks(beats: Double) = (beats * PianoRollTiming.TPB).toInt()

    @Test
    fun testMessageTrackSectionEditsDuringPlayback() {
        val engine = TestEngine()
        val beatLine = beatLineForSectionEdits(engine)
        val track = beatLine.addTrack(TrackType.MESSAGE) as MessageTrack
        fun sectionWithEvent(start: Double, duration: Double, time: Double, value: Double) =
            track.addSection(start, duration)!!.also { (it.data as MessageSectionData).addEvent(time, value) }
        val a = sectionWithEvent(0.0, 4.0, time = 2.0, value = 1.0) // Pending at 0.5 s
        val b = sectionWithEvent(8.0, 2.0, time = 0.0, value = 2.0)
        val c = sectionWithEvent(12.0, 2.0, time = 0.0, value = 3.0)

        val received = mutableListOf<Message.FloatValue>()
        track.outputJack.connect(object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.FloatValue) received.add(msg)
            }
        })

        val base = ScoreClock.now()
        beatLine.startPlayback(base, 1.0)
        track.removeSection(b)
        track.moveSection(c, 5.0)
        track.removeSection(a)

        engine.fireUntil(base.plusSeconds(7.0))
        assertEquals(listOf(3.0), received.map { it.value }, "Only moved section C plays")
        val cSeconds = (received[0].time.nanoseconds - base.nanoseconds) / 1_000_000_000.0
        assertEquals(2.0, cSeconds, 0.1)
        beatLine.stopPlayback(ScoreClock.now())
    }

    @Test
    fun testPatchTrackSectionEditsDuringPlayback() {
        val engine = TestEngine()
        val beatLine = beatLineForSectionEdits(engine)
        val track = beatLine.addTrack(TrackType.PATCH) as PatchTrack
        val a = track.addSection(0.0, 4.0)!!
        val b = track.addSection(8.0, 2.0)!!
        val c = track.addSection(12.0, 2.0)!!

        var starts = 0
        var stops = 0
        track.startCommandModule.output.connect(object : InputJack("startSpy", beatLine) {
            override fun receive(msg: Message) { starts++ }
        })
        track.stopCommandModule.output.connect(object : InputJack("stopSpy", beatLine) {
            override fun receive(msg: Message) { stops++ }
        })

        val base = ScoreClock.now()
        beatLine.startPlayback(base, 1.0)
        assertTrue(track.isPatchRunning)
        assertEquals(1, starts)

        track.removeSection(b)
        track.moveSection(c, 5.0)
        assertTrue(track.isPatchRunning)
        assertEquals(1, starts, "Running patch must not be restarted")

        track.removeSection(a)
        assertFalse(track.isPatchRunning, "Deleting A stops its patch")
        assertEquals(1, stops)

        engine.fireUntil(base.plusSeconds(1.9))
        assertEquals(1, starts)
        engine.fireUntil(base.plusSeconds(2.1))
        assertEquals(2, starts, "Moved section C starts at its new time")

        engine.fireUntil(base.plusSeconds(7.0))
        assertEquals(2, starts, "Deleted section B never starts")
        assertEquals(2, stops, "C stops at its new end")
        beatLine.stopPlayback(ScoreClock.now())
    }

    @Test
    fun testAudioTrackSectionEditsDuringPlayback() {
        val engine = TestEngine()
        val beatLine = beatLineForSectionEdits(engine)
        val track = beatLine.addTrack(TrackType.AUDIO) as AudioTrack
        val unit = track.unit
        val frameRate = if (unit.frameRate > 0.0) unit.frameRate else SynthKSyn.synth.frameRate.toDouble()
        val block = Synthesizer.FRAMES_PER_BLOCK

        fun sectionWithAudio(start: Double, duration: Double, value: (Int) -> Float): TrackSection {
            val frames = (beatLine.unitsToSeconds(duration) * frameRate).toInt()
            val rec = SegmentedFloatArray(unit.segmentSize, unit.poolCapacity)
            var remaining = frames
            while (remaining > 0) {
                val chunk = minOf(remaining, rec.segmentSize)
                rec.extend(chunk)
                remaining -= chunk
            }
            for (i in 0 until frames) rec[i] = value(i)
            return track.addSection(start, duration)!!.also { (it.data as AudioSectionData).recording = rec }
        }
        val a = sectionWithAudio(0.0, 4.0) { (it + 1).toFloat() } // Ramp shows the playback position
        val b = sectionWithAudio(8.0, 2.0) { 0.25f }
        val c = sectionWithAudio(12.0, 2.0) { 0.125f }
        val outputs = unit.output.getValues()

        val base = ScoreClock.now()
        beatLine.startPlayback(base, 1.0)
        // Advance A far more than the real time the test takes, so a restart from the playhead would show
        repeat(1000) { unit.generate() }
        val expectedNext = outputs[block - 1] + 1f

        track.removeSection(b)
        track.moveSection(c, 5.0)
        unit.generate()
        assertEquals(expectedNext, outputs[0], "A keeps playing from where it was")

        track.removeSection(a)
        unit.generate()
        assertEquals(0f, outputs[0], "Deleting A stops its audio")

        engine.fireUntil(base.plusSeconds(1.9))
        unit.generate()
        assertEquals(0f, outputs[0])
        engine.fireUntil(base.plusSeconds(2.1))
        unit.generate()
        assertEquals(0.125f, outputs[0], 1e-6f, "Moved section C plays at its new time")

        engine.fireUntil(base.plusSeconds(7.0))
        unit.generate()
        assertEquals(0.125f, outputs[0], 1e-6f, "Deleted section B never plays")

        beatLine.stopPlayback(ScoreClock.now())
        track.teardown()
    }

    /** Records [frames] frames of [value] into [track], which must already be recording. */
    private fun recordConstant(track: AudioTrack, frames: Int, value: Float) {
        val inputs = track.unit.input.getValues()
        repeat(frames / Synthesizer.FRAMES_PER_BLOCK) {
            inputs.fill(value, 0, Synthesizer.FRAMES_PER_BLOCK)
            track.unit.generate()
        }
    }

    private fun AudioTrack.frameRateForTest(): Double =
        if (unit.frameRate > 0.0) unit.frameRate else SynthKSyn.synth.frameRate.toDouble()

    @Test
    fun testAudioTrackPunchInKeepsAudioAroundTheTake() {
        val timeline = TimelineModule()
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack
        val frameRate = track.frameRateForTest()
        val sec = track.addSectionDirect(id = 1, start = 0.0, duration = 2.0)
        val baseFrames = (2.0 * frameRate).toInt()
        val base = SegmentedFloatArray(track.unit.segmentSize, track.unit.poolCapacity)
        var remaining = baseFrames
        while (remaining > 0) {
            val chunk = minOf(remaining, base.segmentSize)
            base.extend(chunk)
            remaining -= chunk
        }
        base.write(0, FloatArray(baseFrames) { 0.25f })
        (sec.data as AudioSectionData).recording = base

        track.onRecordStart(ScoreTime.Zero, 0.5) // Half a second into the section
        assertTrue((sec.data as AudioSectionData).recording === base, "Old audio stays in place while recording")
        recordConstant(track, 64, 0.9f)
        track.finishRecording(sec, latencyFrames = 0, stopUnits = 0.51)

        val result = assertNotNull((sec.data as AudioSectionData).recording)
        val punchIn = (0.5 * frameRate).roundToInt()
        assertEquals(baseFrames, result.length, "Audio after the take is kept")
        assertEquals(0.25f, result[punchIn - 1], "Audio before the punch-in is kept")
        assertEquals(0.9f, result[punchIn], "Take starts where recording started")
        assertEquals(0.9f, result[punchIn + 63])
        assertEquals(0.25f, result[punchIn + 64], "Audio after the punch-out is kept")
        assertEquals(2.0, sec.duration, "Punching in does not shorten the section")
        track.teardown()
    }

    @Test
    fun testAudioTrackRecordingIntoEmptySectionStartsAtRecordPosition() {
        val timeline = TimelineModule()
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack
        val frameRate = track.frameRateForTest()
        val sec = track.addSectionDirect(id = 1, start = 1.0, duration = 4.0)

        track.onRecordStart(ScoreTime.Zero, 2.0) // One second into the section
        recordConstant(track, 64, 0.9f)
        track.finishRecording(sec, latencyFrames = 0, stopUnits = 2.01)

        val result = assertNotNull((sec.data as AudioSectionData).recording)
        val punchIn = (1.0 * frameRate).roundToInt()
        assertEquals(punchIn + 64, result.length)
        assertEquals(0f, result[punchIn - 1], "Silence before the take")
        assertEquals(0.9f, result[punchIn], "Take lines up with where recording started")
        assertEquals(1.0, sec.start)
        assertEquals(4.0, sec.duration)
        track.teardown()
    }

    @Test
    fun testAudioThumbnailPublishesCountAndValuesTogether() {
        val rec = SegmentedFloatArray()
        rec.extend(256)
        rec.write(0, FloatArray(256) { 0.5f })
        val thumb = AudioThumbnail()
        thumb.updateFrom(rec)
        val before = thumb.blocks

        // Same length, different samples, as after punching in
        val punched = SegmentedFloatArray()
        punched.extend(256)
        punched.write(0, FloatArray(256) { 0.9f })
        thumb.clear()
        thumb.updateFrom(punched)

        assertEquals(before.count, thumb.blocks.count, "Block count alone cannot show the change")
        assertTrue(thumb.blocks !== before, "Views must see a new object so they redraw")
        // A view reads one object, so its count can never exceed its arrays
        assertTrue(thumb.blocks.count <= thumb.blocks.minValues.size)
        assertTrue(thumb.blocks.count <= thumb.blocks.maxValues.size)
        assertEquals(0.9f, thumb.blocks.maxValues[0], 1e-6f)
    }

    @Test
    fun testAudioThumbnailClearThenRefillNeverExposesAnEmptyArrayWithBlocks() {
        val rec = SegmentedFloatArray()
        rec.extend(1024)
        rec.write(0, FloatArray(1024) { 0.4f })
        val thumb = AudioThumbnail()
        thumb.updateFrom(rec)

        // Mid-rebuild, as finishRecording does: whatever a draw reads must be self consistent
        thumb.clear()
        val duringRebuild = thumb.blocks
        assertEquals(0, duringRebuild.count)
        assertTrue(duringRebuild.count <= duringRebuild.minValues.size)

        thumb.updateFrom(rec)
        val after = thumb.blocks
        assertTrue(after.count > 0)
        assertTrue(after.count <= after.minValues.size, "Count must fit its own arrays")
    }

    @Test
    fun testAudioTrackPunchInSilencesTheSectionWhileRecording() {
        val engine = TestEngine()
        val timeline = TimelineModule()
        timeline.loopMode = false
        timeline.maxTime = 16.0
        timeline.setEngine(engine)
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack
        val frameRate = track.frameRateForTest()
        val sec = track.addSectionDirect(id = 1, start = 0.0, duration = 2.0)
        val baseFrames = (2.0 * frameRate).toInt()
        val base = SegmentedFloatArray(track.unit.segmentSize, track.unit.poolCapacity)
        var remaining = baseFrames
        while (remaining > 0) {
            val chunk = minOf(remaining, base.segmentSize)
            base.extend(chunk)
            remaining -= chunk
        }
        base.write(0, FloatArray(baseFrames) { 0.25f })
        (sec.data as AudioSectionData).recording = base
        val outputs = track.unit.output.getValues()

        val now = ScoreClock.now()
        timeline.startPlayback(now, 0.0)
        track.unit.generate()
        assertEquals(0.25f, outputs[0], "Section plays before recording")

        track.onRecordStart(ScoreClock.now(), 0.5)
        track.unit.generate()
        assertEquals(0f, outputs[0], "Old audio is silent under the take")

        recordConstant(track, 64, 0.9f)
        track.finishRecording(sec, latencyFrames = 0, stopUnits = 0.51)
        track.unit.generate()
        assertTrue(outputs[0] != 0f, "Section plays again once the take is written")

        timeline.stopPlayback(ScoreClock.now())
        track.teardown()
    }

    /** Builds a timeline with one audio track holding one section of [frames] frames of [value]. */
    private fun timelineWithAudio(graph: GraphState, frames: Int, value: Float): TimelineModule {
        val timeline = TimelineModule()
        graph.addModule(timeline, Offset.Zero)
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack
        val sec = track.addSectionDirect(id = 1, start = 0.0, duration = 1.0)
        val rec = SegmentedFloatArray(track.unit.segmentSize, track.unit.poolCapacity)
        rec.extend(frames)
        rec.write(0, FloatArray(frames) { value })
        (sec.data as AudioSectionData).apply {
            recording = rec
            needsWavWrite = true
        }
        return timeline
    }

    private fun wavFileNames(json: String): List<String> =
        Regex("\"wavFile\"\\s*:\\s*\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()

    @Test
    fun testTwoTimelinesInOneDocumentGetSeparateWavFiles() {
        val graph = GraphState()
        timelineWithAudio(graph, frames = 64, value = 0.25f)
        timelineWithAudio(graph, frames = 64, value = 0.5f)

        val basePath = "build/tmp_two_timelines.sntn"
        val names = wavFileNames(GraphSerializer.serialize(graph, graph.modules, basePath = basePath))
        assertEquals(2, names.size)
        assertEquals(names.toSet().size, names.size, "Each section needs a file of its own")
        names.forEach { assertTrue(it.startsWith("audio/tmp_two_timelines/"), "Unexpected path $it") }
        graph.modules.filterIsInstance<TimelineModule>().forEach { timeline ->
            timeline.tracks.forEach { (it as AudioTrack).teardown() }
        }
    }

    @Test
    fun testSavingTwiceKeepsWavNamesAndDoesNotRewriteFiles() {
        val graph = GraphState()
        val timeline = timelineWithAudio(graph, frames = 64, value = 0.25f)

        val basePath = "build/tmp_rewrite_check.sntn"
        val firstNames = wavFileNames(GraphSerializer.serialize(graph, graph.modules, basePath = basePath))
        assertEquals(1, firstNames.size)
        val wavPath = resolvePath(firstNames[0], basePath)
        assertNotNull(readBinaryFileDirect(wavPath), "First save writes the audio")

        // Replace the file with a shorter one, which a second save would overwrite
        writeWavFile(wavPath, FloatArray(8) { 0.5f }, 48000, 1)
        val secondNames = wavFileNames(GraphSerializer.serialize(graph, graph.modules, basePath = basePath))
        assertEquals(firstNames, secondNames, "Names stay the same across saves")
        val bytes = assertNotNull(readBinaryFileDirect(wavPath))
        assertEquals(8, SampleLoader.loadFloatSample(bytes).numFrames, "Unchanged audio is not written again")

        timeline.tracks.forEach { (it as AudioTrack).teardown() }
    }

    @Test
    fun testSerializingWithoutAudioWritesNoWavFiles() {
        val graph = GraphState()
        val timeline = timelineWithAudio(graph, frames = 64, value = 0.25f)
        val data = (timeline.tracks.first().sections.first().data as AudioSectionData)

        val basePath = "build/tmp_no_audio_write.sntn"
        val json = GraphSerializer.serialize(graph, graph.modules, basePath = basePath, writeAudioFiles = false)

        assertTrue(wavFileNames(json).isEmpty(), "No file is named")
        assertNull(data.wavFileName)
        assertNull(data.savedWavPath, "No audio is written")

        // The clipboard serializes this way, so copying never writes audio files
        CopyPasteService.copy(graph, graph.modules.toList())
        assertNull(data.savedWavPath)
        CopyPasteService.clear()
        timeline.tracks.forEach { (it as AudioTrack).teardown() }
    }

    @Test
    fun testSectionsSharingAWavNameAreGivenSeparateFiles() {
        val graph = GraphState()
        val first = timelineWithAudio(graph, frames = 64, value = 0.25f)
        val second = timelineWithAudio(graph, frames = 64, value = 0.5f)
        // As after pasting a timeline, both sections claim one file
        val shared = "audio/tmp_shared_name/take_1.wav"
        (first.tracks.first().sections.first().data as AudioSectionData).wavFileName = shared
        (second.tracks.first().sections.first().data as AudioSectionData).wavFileName = shared

        val basePath = "build/tmp_shared_name.sntn"
        val names = wavFileNames(GraphSerializer.serialize(graph, graph.modules, basePath = basePath))
        assertEquals(2, names.size)
        assertEquals(names.toSet().size, names.size, "The second section is given a file of its own")
        assertTrue(names.contains(shared), "The first section keeps the name it had")

        listOf(first, second).forEach { t -> t.tracks.forEach { (it as AudioTrack).teardown() } }
    }

    @Test
    fun testFinishRecordingWithNoRoomKeepsSectionInsideTheGap() {
        val timeline = TimelineModule()
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack
        // A section with almost no room before the next one, as when recording at the end
        val sec = track.addSectionDirect(id = 1, start = 0.0, duration = 0.02)
        track.addSectionDirect(id = 2, start = 0.02, duration = 1.0)

        track.onRecordStart(ScoreTime.Zero, 0.0)
        recordConstant(track, 64, 0.5f)
        track.finishRecording(sec, latencyFrames = 0, stopUnits = 0.01)

        assertTrue(sec.duration > 0.0, "Section keeps a usable duration")
        assertTrue(sec.duration <= 0.02 + 1e-9, "Section must not overlap the next one")
        track.teardown()
    }

    @Test
    fun testFinishRecordingANewSectionWithNoRoomLeftDoesNotThrow() {
        val timeline = TimelineModule()
        timeline.maxTime = 16.0
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack

        track.onRecordStart(ScoreTime.Zero, 5.0) // Creates a section
        val sec = track.sections.single()
        recordConstant(track, 64, 0.5f)
        // The room ahead disappears while recording, e.g. the song end moves in
        timeline.maxTime = 5.0
        track.finishRecording(sec, latencyFrames = 0, stopUnits = 5.01)

        assertTrue(sec.duration > 0.0, "Section keeps a usable duration")
        track.teardown()
    }

    @Test
    fun testMessageTrackRecordStopWithNoRoomKeepsSectionInsideTheGap() {
        val timeline = TimelineModule()
        val track = timeline.addTrack(TrackType.MESSAGE) as MessageTrack
        val sec = track.addSectionDirect(id = 1, start = 0.0, duration = 0.02)
        track.addSectionDirect(id = 2, start = 0.02, duration = 1.0)

        track.onRecordStart(ScoreTime.Zero, 0.0)
        track.onRecordStop(ScoreTime.Zero, 1.0) // Recorded past the room available

        assertTrue(sec.duration <= 0.02 + 1e-9, "Section must not overlap the next one")
    }

    @Test
    fun testSectionsCannotStartAtOrAfterTheEndOfTheTimeline() {
        val timeline = TimelineModule()
        timeline.maxTime = 8.0
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack

        assertFalse(track.canAddSectionAt(8.0))
        assertFalse(track.canAddSectionAt(9.0))
        assertTrue(track.canAddSectionAt(7.0))
        assertNull(track.addSection(8.0, 2.0), "No section is added at the end")

        // Recording at the end adds nothing rather than an unusable section
        track.onRecordStart(ScoreTime.Zero, 8.0)
        assertTrue(track.sections.isEmpty())
        track.teardown()
    }

    /** An audio track with one section holding [frames] frames of [value], ready to duplicate. */
    private fun audioTrackWithClip(timeline: TimelineModule, frames: Int, value: Float): AudioTrack {
        val track = timeline.addTrack(TrackType.AUDIO) as AudioTrack
        val sec = track.addSectionDirect(id = 1, start = 0.0, duration = 1.0)
        val rec = SegmentedFloatArray(track.unit.segmentSize, track.unit.poolCapacity)
        rec.extend(frames)
        rec.write(0, FloatArray(frames) { value })
        (sec.data as AudioSectionData).apply {
            recording = rec
            needsWavWrite = true
        }
        return track
    }

    @Test
    fun testDuplicatingAnAudioSectionSharesItsClip() {
        val timeline = TimelineModule()
        val track = audioTrackWithClip(timeline, frames = 64, value = 0.25f)
        val original = track.sections.single()

        val copy = assertNotNull(track.duplicateSection(original, 2.0))
        val clip = assertNotNull(copy.data as? AudioSectionData)
        assertTrue(clip === original.data, "The copy shares the clip instead of duplicating samples")
        assertFalse(clip.isAliasFor(original), "The original owns the clip")
        assertTrue(clip.isAliasFor(copy), "The copy is an alias")
        track.teardown()
    }

    @Test
    fun testDeletingTheOwnerPassesTheClipToTheNextSection() {
        val timeline = TimelineModule()
        val track = audioTrackWithClip(timeline, frames = 64, value = 0.25f)
        val original = track.sections.single()
        val copy = assertNotNull(track.duplicateSection(original, 2.0))
        val second = assertNotNull(track.duplicateSection(original, 4.0))
        val clip = original.data as AudioSectionData

        track.removeSection(original)
        assertFalse(clip.isAliasFor(copy), "The next section becomes the owner")
        assertTrue(clip.isAliasFor(second))

        track.removeSection(second)
        track.removeSection(copy)
        assertNull(clip.ownerSection, "Nothing shares the clip any more")
        track.teardown()
    }

    @Test
    fun testRecordingIsRefusedOnAnAliasButAllowedOnTheOwner() {
        val timeline = TimelineModule()
        val track = audioTrackWithClip(timeline, frames = 64, value = 0.25f)
        val original = track.sections.single()
        val copy = assertNotNull(track.duplicateSection(original, 2.0))

        // Recording into the alias is refused and changes nothing
        track.onRecordStart(ScoreTime.Zero, 2.5)
        recordConstant(track, 64, 0.9f)
        track.finishRecording(copy, latencyFrames = 0, stopUnits = 2.6)
        val clip = original.data as AudioSectionData
        assertEquals(0.25f, assertNotNull(clip.recording)[0], "The shared audio is untouched")
        assertEquals(64, assertNotNull(clip.recording).length, "No take was punched into the shared audio")

        // Recording into the owner is allowed, and both sections hear the new take
        track.onRecordStart(ScoreTime.Zero, 0.0)
        recordConstant(track, 64, 0.9f)
        track.finishRecording(original, latencyFrames = 0, stopUnits = 0.1)
        assertEquals(0.9f, assertNotNull(clip.recording)[0], "The owner's take replaces the clip")
        assertTrue(copy.data === clip, "The alias still shares the clip")
        track.teardown()
    }

    @Test
    fun testSharedClipIsSavedOnceAndStillSharedAfterReload() {
        val graph = GraphState()
        val timeline = TimelineModule()
        graph.addModule(timeline, Offset.Zero)
        val track = audioTrackWithClip(timeline, frames = 64, value = 0.25f)
        val original = track.sections.single()
        assertNotNull(track.duplicateSection(original, 2.0))

        val basePath = "build/tmp_shared_clip.sntn"
        val json = GraphSerializer.serialize(graph, graph.modules, basePath = basePath)
        assertEquals(1, wavFileNames(json).toSet().size, "One file for the shared clip")
        assertEquals(2, wavFileNames(json).size, "Both sections name it")

        val loadedGraph = GraphState()
        GraphSerializer.deserialize(loadedGraph, json, basePath = basePath)
        val loadedTrack = loadedGraph.modules.filterIsInstance<TimelineModule>().first().tracks.first() as AudioTrack
        val (loadedOriginal, loadedCopy) = loadedTrack.sections.sortedBy { it.start }
        val loadedClip = assertNotNull(loadedOriginal.data as? AudioSectionData)
        assertTrue(loadedCopy.data === loadedClip, "Sharing survives a reload")
        assertFalse(loadedClip.isAliasFor(loadedOriginal))
        assertTrue(loadedClip.isAliasFor(loadedCopy))

        track.teardown()
        loadedTrack.teardown()
    }

    @Test
    fun testMidiTrackMuteSilencesSoundingNotes() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sec = midiTrack.addSection(start = 0.0, duration = 4.0)!!
        val secData = sec.data as MidiSectionData
        secData.addNote(startTicks = 0, durationTicks = 480, pitch = 62.0)

        val engine = TestEngine()
        beatLine.setEngine(engine)

        val sentMessages = mutableListOf<Message.VoiceMessage>()
        val spyJack = object : InputJack("spy", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sentMessages.add(msg)
            }
        }
        midiTrack.outputJack.connect(spyJack)

        val baseNow = ScoreTime(1_000_000_000L)
        beatLine.startPlayback(baseNow, 0.0)

        // Fire the scheduled NOTE_ON at 0.0s
        val on1 = engine.scheduled.find {
            val s = (it.first.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
            kotlin.math.abs(s - 0.0) < 0.001
        }
        assertNotNull(on1, "Should schedule NOTE_ON at 0.0s")
        on1.second()

        // Note started and sounding
        assertEquals(1, sentMessages.size)
        assertEquals(Message.VoiceOpcode.NOTE_ON, sentMessages[0].opcode)

        // Mute the track
        midiTrack.isMuted = true

        // Should immediately send NOTE_OFF
        assertEquals(2, sentMessages.size, "Muting should immediately send NOTE_OFF")
        assertEquals(Message.VoiceOpcode.NOTE_OFF, sentMessages[1].opcode)
        assertEquals(sentMessages[0].tag, sentMessages[1].tag)
    }

    @Test
    fun testMidiTrackSerializationRoundTrip() {
        val rootGraph = GraphState()
        val beatLine = BeatLineModule()
        rootGraph.addModule(beatLine, Offset.Zero)

        val midiTrack = beatLine.addTrack(TrackType.MIDI, "Bass") as MidiTrack
        val sec = midiTrack.addSection(start = 1.0, duration = 4.0, repeats = 2, name = "Verse")!!
        val secData = sec.data as MidiSectionData
        val n1 = secData.addNote(startTicks = 0, durationTicks = 240, pitch = 48.0)
        val nid = secData.pianoRoll.allocateNoteId()
        secData.pianoRoll.notes.add(
            PianoRollNote(
                id = nid,
                startTicks = 240,
                durationTicks = 240,
                parentId = n1.id,
                ratio = Ratio(5, 4),
                rootPitch = 48.0
            )
        )

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"Bass\""))
        assertTrue(json.contains("\"notes\""))
        assertTrue(json.contains("48"))

        // Deserialize
        val newGraph = GraphState()
        GraphSerializer.deserialize(newGraph, json)

        val loadedBeatLine = newGraph.modules.filterIsInstance<BeatLineModule>().firstOrNull()
        assertNotNull(loadedBeatLine)
        assertEquals(1, loadedBeatLine.tracks.size)

        val loadedTrack = loadedBeatLine.tracks.first() as? MidiTrack
        assertNotNull(loadedTrack)
        assertEquals("Bass", loadedTrack.name)
        assertEquals(1, loadedTrack.sections.size)

        val loadedSec = loadedTrack.sections.first()
        assertEquals(1.0, loadedSec.start)
        assertEquals(4.0, loadedSec.duration)
        assertEquals(2, loadedSec.repeats)
        assertEquals("Verse", loadedSec.name)

        val loadedData = loadedSec.data as? MidiSectionData
        assertNotNull(loadedData)
        assertEquals(2, loadedData.pianoRoll.notes.size)

        val loadedN1 = loadedData.pianoRoll.notes[0]
        assertEquals(0, loadedN1.startTicks)
        assertEquals(240, loadedN1.durationTicks)
        assertEquals(48.0, loadedN1.rootPitch)

        val loadedN2 = loadedData.pianoRoll.notes[1]
        assertEquals(240, loadedN2.startTicks)
        assertEquals(Ratio(5, 4), loadedN2.ratio)
    }

    @Test
    fun testMidiTrackPitchBendAndCCRecording() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sec = midiTrack.addSection(start = 0.0, duration = 4.0)!!
        midiTrack.isRecordArmed = true
        val engine = TestEngine()
        beatLine.setEngine(engine)

        val startNow = ScoreTime(1_000_000_000L)
        beatLine.startRecording(startNow)

        // Note ON at 0.5s (1.0 beat at 120 bpm)
        val tNoteOn = startNow + ScoreTime(500_000_000L)
        midiTrack.onMessageReceived(
            midiTrack.inputJack,
            Message.VoiceMessage(tNoteOn, Message.VoiceOpcode.NOTE_ON, tag = 60, value = 60.0, extra = 0.8)
        )

        // Pitch Bend +1.0 semitones at 0.75s (1.5 beats)
        val tBend1 = startNow + ScoreTime(750_000_000L)
        midiTrack.onMessageReceived(
            midiTrack.inputJack,
            Message.VoiceMessage(tBend1, Message.VoiceOpcode.PITCH_BEND, tag = -1, value = 1.0, extra = 0.0)
        )

        // CC 1 (mod wheel) = 0.7 at 1.0s (2.0 beats)
        val tCC = startNow + ScoreTime(1_000_000_000L)
        midiTrack.onMessageReceived(
            midiTrack.inputJack,
            Message.VoiceMessage(tCC, Message.VoiceOpcode.CONTROL_CHANGE, tag = -1, value = 0.7, extra = 1.0)
        )

        // Pitch Bend back to 0.0 at 1.25s (2.5 beats)
        val tBend2 = startNow + ScoreTime(1_250_000_000L)
        midiTrack.onMessageReceived(
            midiTrack.inputJack,
            Message.VoiceMessage(tBend2, Message.VoiceOpcode.PITCH_BEND, tag = -1, value = 0.0, extra = 0.0)
        )

        // Note OFF at 1.5s (3.0 beats)
        val tNoteOff = startNow + ScoreTime(1_500_000_000L)
        midiTrack.onMessageReceived(
            midiTrack.inputJack,
            Message.VoiceMessage(tNoteOff, Message.VoiceOpcode.NOTE_OFF, tag = 60, value = 60.0, extra = 0.0)
        )

        beatLine.stopRecording(tNoteOff)

        val secData = sec.data as MidiSectionData
        // Check that note was recorded in pianoRoll
        assertEquals(1, secData.pianoRoll.notes.size)

        // Check that voice events were recorded in voiceEvents
        assertEquals(3, secData.voiceEvents.size)

        val e0 = secData.voiceEvents[0]
        assertEquals(Message.VoiceOpcode.PITCH_BEND, e0.opcode)
        assertEquals(1.0, e0.value)
        assertEquals(1.5, e0.time, 0.01)

        val e1 = secData.voiceEvents[1]
        assertEquals(Message.VoiceOpcode.CONTROL_CHANGE, e1.opcode)
        assertEquals(0.7, e1.value)
        assertEquals(1.0, e1.extra)
        assertEquals(2.0, e1.time, 0.01)

        val e2 = secData.voiceEvents[2]
        assertEquals(Message.VoiceOpcode.PITCH_BEND, e2.opcode)
        assertEquals(0.0, e2.value)
        assertEquals(2.5, e2.time, 0.01)
    }

    @Test
    fun testMidiTrackPitchBendPlayback() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sec = midiTrack.addSection(start = 0.0, duration = 4.0)!!
        val secData = sec.data as MidiSectionData
        secData.addVoiceEvent(time = 1.0, opcode = Message.VoiceOpcode.PITCH_BEND, tag = -1, value = 1.5, extra = 0.0)
        secData.addVoiceEvent(time = 2.0, opcode = Message.VoiceOpcode.PITCH_BEND, tag = -1, value = 0.0, extra = 0.0)

        val sentMessages = mutableListOf<Message.VoiceMessage>()
        val sink = object : InputJack("sink", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sentMessages.add(msg)
            }
        }
        midiTrack.outputJack.connect(sink)

        val engine = TestEngine()
        beatLine.setEngine(engine)
        val baseNow = ScoreTime(1_000_000_000L)
        beatLine.startPlayback(baseNow, 0.0)

        // 1.0 beat at 120 bpm = 0.5s delay
        val bend1Event = engine.scheduled.find { (time, _) ->
            val delaySec = (time.nanoseconds - baseNow.nanoseconds) / 1_000_000_000.0
            kotlin.math.abs(delaySec - 0.5) < 0.001
        }
        assertNotNull(bend1Event, "Should schedule pitch bend event at 0.5s")
        bend1Event.second()

        val bendMsg = sentMessages.find { it.opcode == Message.VoiceOpcode.PITCH_BEND && it.value == 1.5 }
        assertNotNull(bendMsg, "Should send pitch bend message with value 1.5")
    }

    @Test
    fun testMidiTrackPitchBendResetOnStopAndMute() {
        val beatLine = BeatLineModule()
        val midiTrack = beatLine.addTrack(TrackType.MIDI) as MidiTrack
        val sentMessages = mutableListOf<Message.VoiceMessage>()
        val sink = object : InputJack("sink", beatLine) {
            override fun receive(msg: Message) {
                if (msg is Message.VoiceMessage) sentMessages.add(msg)
            }
        }
        midiTrack.outputJack.connect(sink)

        midiTrack.lastSentPitchBend = 1.5
        sentMessages.clear()
        midiTrack.onPlaybackStop()

        val resetMsg = sentMessages.find { it.opcode == Message.VoiceOpcode.PITCH_BEND && it.value == 0.0 }
        assertNotNull(resetMsg, "onPlaybackStop should send neutral pitch bend (value = 0.0)")

        midiTrack.lastSentPitchBend = 1.5
        sentMessages.clear()
        midiTrack.isMuted = true
        val muteResetMsg = sentMessages.find { it.opcode == Message.VoiceOpcode.PITCH_BEND && it.value == 0.0 }
        assertNotNull(muteResetMsg, "Muting track should send neutral pitch bend (value = 0.0)")
    }

    @Test
    fun testMidiTrackVoiceEventsSerialization() {
        val rootGraph = GraphState()
        val beatLine = BeatLineModule()
        rootGraph.addModule(beatLine, Offset.Zero)

        val midiTrack = beatLine.addTrack(TrackType.MIDI, "SynthLead") as MidiTrack
        val sec = midiTrack.addSection(start = 0.0, duration = 4.0, repeats = 1)!!
        val secData = sec.data as MidiSectionData
        secData.addVoiceEvent(time = 1.0, opcode = Message.VoiceOpcode.PITCH_BEND, tag = -1, value = 2.0, extra = 0.0)
        secData.addVoiceEvent(time = 2.0, opcode = Message.VoiceOpcode.CONTROL_CHANGE, tag = -1, value = 0.8, extra = 74.0)

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"voiceEvents\""))
        assertTrue(json.contains("\"PITCH_BEND\""))
        assertTrue(json.contains("\"CONTROL_CHANGE\""))

        val newGraph = GraphState()
        GraphSerializer.deserialize(newGraph, json)

        val loadedBeatLine = newGraph.modules.filterIsInstance<BeatLineModule>().first()
        val loadedTrack = loadedBeatLine.tracks.first() as MidiTrack
        val loadedSec = loadedTrack.sections.first()
        val loadedData = loadedSec.data as MidiSectionData

        assertEquals(2, loadedData.voiceEvents.size)
        assertEquals(Message.VoiceOpcode.PITCH_BEND, loadedData.voiceEvents[0].opcode)
        assertEquals(2.0, loadedData.voiceEvents[0].value)
        assertEquals(1.0, loadedData.voiceEvents[0].time, 0.001)

        assertEquals(Message.VoiceOpcode.CONTROL_CHANGE, loadedData.voiceEvents[1].opcode)
        assertEquals(0.8, loadedData.voiceEvents[1].value)
        assertEquals(74.0, loadedData.voiceEvents[1].extra)
        assertEquals(2.0, loadedData.voiceEvents[1].time, 0.001)
    }

    @Test
    fun testAudioTrackCreationAndMonitoring() {
        val timeline = TimelineModule()
        val audioTrack = timeline.addTrack(TrackType.AUDIO, "Audio1") as AudioTrack
        assertEquals("Audio1", audioTrack.name)
        assertTrue(audioTrack.inputJack is AudioInputJack)
        assertTrue(audioTrack.outputJack is AudioOutputJack)
        assertFalse(audioTrack.isMuted)
        assertFalse(audioTrack.isRecordArmed)
        assertFalse(audioTrack.isInputMonitored)
        assertTrue(audioTrack.unit.isAudible)
        assertFalse(audioTrack.unit.isInputMonitored)

        // Muting updates unit
        audioTrack.isMuted = true
        assertFalse(audioTrack.unit.isAudible)
        audioTrack.isMuted = false
        assertTrue(audioTrack.unit.isAudible)

        // Monitoring
        audioTrack.isInputMonitored = true
        assertTrue(audioTrack.unit.isInputMonitored)

        // In generate(), monitored input passes to output
        val inputs = audioTrack.unit.input.getValues()
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            inputs[i] = (i + 1) * 0.1f
        }
        audioTrack.unit.generate()
        val outputs = audioTrack.unit.output.getValues()
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            assertEquals((i + 1) * 0.1f, outputs[i], 1e-5f)
        }

        audioTrack.teardown()
    }

    @Test
    fun testAudioTrackRecordingAndThumbnail() {
        val timeline = TimelineModule()
        val audioTrack = timeline.addTrack(TrackType.AUDIO, "Vocals") as AudioTrack
        audioTrack.isRecordArmed = true

        audioTrack.onRecordStart(ScoreTime.Zero, 0.0)
        assertEquals(1, audioTrack.sections.size)
        val sec = audioTrack.sections.first()
        val audioData = sec.data as AudioSectionData
        assertNotNull(audioData.recording)

        // Write 64 frames (multiple synthesis blocks)
        val numBlocks = 64 / Synthesizer.FRAMES_PER_BLOCK
        val inputs = audioTrack.unit.input.getValues()
        for (b in 0 until numBlocks) {
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                inputs[i] = if ((b * Synthesizer.FRAMES_PER_BLOCK + i) % 2 == 0) 0.5f else -0.3f
            }
            audioTrack.unit.generate()
        }

        assertEquals(64, audioTrack.unit.recordCursor)

        audioTrack.finishRecording(sec, latencyFrames = 0, stopUnits = 1.0)
        assertFalse(audioTrack.unit.isRecording)
        assertEquals(64, audioData.recording!!.length)
        assertEquals(0.5f, audioData.recording!![0], 1e-5f)
        assertEquals(-0.3f, audioData.recording!![1], 1e-5f)

        // Thumbnail
        assertTrue(audioData.thumbnail.blockCount > 0)
        assertEquals(-0.3f, audioData.thumbnail.minValues[0], 1e-5f)
        assertEquals(0.5f, audioData.thumbnail.maxValues[0], 1e-5f)

        audioTrack.teardown()
    }

    @Test
    fun testAudioTrackLatencyCompensation() {
        val timeline = TimelineModule()
        val audioTrack = timeline.addTrack(TrackType.AUDIO, "Guitar") as AudioTrack
        audioTrack.isRecordArmed = true
        audioTrack.latencyFramesOverride = 16 // 16 frames latency

        audioTrack.onRecordStart(ScoreTime.Zero, 0.0)
        val sec = audioTrack.sections.first()

        // Write 32 frames ramp
        val inputs = audioTrack.unit.input.getValues()
        val totalFrames = 32
        val blocks = totalFrames / Synthesizer.FRAMES_PER_BLOCK
        var frame = 0
        for (b in 0 until blocks) {
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                inputs[i] = frame.toFloat() / totalFrames
                frame++
            }
            audioTrack.unit.generate()
        }

        assertEquals(32, audioTrack.unit.recordCursor)

        // Finish with 16 frames of latency compensation
        audioTrack.finishRecording(sec, latencyFrames = 16, stopUnits = 1.0)

        val audioData = sec.data as AudioSectionData
        val trimmed = audioData.recording!!
        // Trimmed length should be 32 - 16 = 16
        assertEquals(16, trimmed.length)
        // Frame 0 of trimmed should be frame 16 of the original recording (16.0 / 32.0 = 0.5)
        assertEquals(0.5f, trimmed[0], 1e-5f)
        assertEquals(31f / 32f, trimmed[15], 1e-5f)

        audioTrack.teardown()
    }

    @Test
    fun testAudioTrackPlayback() {
        val timeline = TimelineModule()
        val engine = TestEngine()
        timeline.setEngine(engine)

        val audioTrack = timeline.addTrack(TrackType.AUDIO, "Lead") as AudioTrack
        val sec = audioTrack.addSectionDirect(id = 1, start = 0.0, duration = 1.0, repeats = 2)
        val audioData = sec.data as AudioSectionData

        // Fill recording with 32 frames of test tone (0.75f)
        val rec = SegmentedFloatArray(audioTrack.unit.segmentSize, audioTrack.unit.poolCapacity)
        rec.extend(32)
        for (i in 0 until 32) rec[i] = 0.75f
        audioData.recording = rec
        audioData.updateThumbnail()

        // Start playback at 0.0
        audioTrack.onPlaybackStart(ScoreTime.Zero, 0.0, 5.0, engine)

        // Generate a block (8 frames)
        audioTrack.unit.generate()
        val outputs = audioTrack.unit.output.getValues()
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            assertEquals(0.75f, outputs[i], 1e-5f)
        }

        // Mute track: output becomes 0f
        audioTrack.isMuted = true
        audioTrack.unit.generate()
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            assertEquals(0f, outputs[i], 1e-5f)
        }

        audioTrack.teardown()
    }

    @Test
    fun testAudioTrackPlaybackStaysInSyncWhileMuted() {
        val timeline = TimelineModule()
        val engine = TestEngine()
        timeline.setEngine(engine)

        val audioTrack = timeline.addTrack(TrackType.AUDIO, "Lead") as AudioTrack
        val sec = audioTrack.addSectionDirect(id = 1, start = 0.0, duration = 1.0)
        val audioData = sec.data as AudioSectionData

        // Ramp so each frame's value identifies its position in the recording
        val block = Synthesizer.FRAMES_PER_BLOCK
        val numFrames = block * 4
        val rec = SegmentedFloatArray(audioTrack.unit.segmentSize, audioTrack.unit.poolCapacity)
        rec.extend(numFrames)
        for (i in 0 until numFrames) rec[i] = (i + 1).toFloat()
        audioData.recording = rec

        audioTrack.onPlaybackStart(ScoreTime.Zero, 0.0, 5.0, engine)
        val outputs = audioTrack.unit.output.getValues()

        // Block 0 is audible
        audioTrack.unit.generate()
        for (i in 0 until block) assertEquals((i + 1).toFloat(), outputs[i], 1e-5f)

        // Block 1 is muted
        audioTrack.isMuted = true
        audioTrack.unit.generate()
        for (i in 0 until block) assertEquals(0f, outputs[i], 1e-5f)

        // Block 2 resumes where the timeline is, not where the mute began
        audioTrack.isMuted = false
        audioTrack.unit.generate()
        for (i in 0 until block) {
            assertEquals((2 * block + i + 1).toFloat(), outputs[i], 1e-5f, "Frame $i after unmute")
        }

        audioTrack.teardown()
    }

    @Test
    fun testAudioTrackWavSerializationRoundTrip() {
        val rootGraph = GraphState()
        val timeline = TimelineModule()
        rootGraph.addModule(timeline, Offset.Zero)

        val audioTrack = timeline.addTrack(TrackType.AUDIO, "VocalTake") as AudioTrack
        val sec = audioTrack.addSectionDirect(id = 1, start = 1.0, duration = 2.0, repeats = 1)
        val audioData = sec.data as AudioSectionData

        val rec = SegmentedFloatArray(audioTrack.unit.segmentSize, audioTrack.unit.poolCapacity)
        rec.extend(64)
        for (i in 0 until 64) {
            rec[i] = (i - 32) / 32f
        }
        audioData.recording = rec
        audioData.updateThumbnail()

        val fakeBasePath = "build/tmp_audio_test_patch.sntn"
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules, basePath = fakeBasePath)
        assertTrue(json.contains("\"wavFile\""), "Serialized JSON should contain wavFile attribute")
        assertTrue(
            json.contains("audio/tmp_audio_test_patch/take_"),
            "Wav file path should point to this document's folder under audio/"
        )

        // Deserialize
        val loadedGraph = GraphState()
        GraphSerializer.deserialize(loadedGraph, json, basePath = fakeBasePath)

        val loadedTimeline = loadedGraph.modules.filterIsInstance<TimelineModule>().first()
        val loadedTrack = loadedTimeline.tracks.first() as AudioTrack
        assertEquals("VocalTake", loadedTrack.name)
        assertEquals(1, loadedTrack.sections.size)

        val loadedSec = loadedTrack.sections.first()
        assertEquals(1.0, loadedSec.start, 0.001)
        assertEquals(2.0, loadedSec.duration, 0.001)

        val loadedAudioData = loadedSec.data as AudioSectionData
        assertNotNull(loadedAudioData.recording, "Loaded audio recording should not be null")
        assertEquals(64, loadedAudioData.recording!!.length)
        assertEquals(-1.0f, loadedAudioData.recording!![0], 1e-2f)
        assertTrue(loadedAudioData.thumbnail.blockCount > 0, "Loaded audio thumbnail should be generated")

        audioTrack.teardown()
        loadedTrack.teardown()
    }

    @Test
    fun testAudioTrackRecordingSaveReloadThumbnail() {
        val rootGraph = GraphState()
        val beatLine = BeatLineModule()
        rootGraph.addModule(beatLine, Offset.Zero)

        val audioTrack = beatLine.addTrack(TrackType.AUDIO, "Audio 4") as AudioTrack
        val sec = audioTrack.addSectionDirect(id = 2, start = 0.0, duration = 24.0, repeats = 1, name = "Sec 3")
        val audioData = sec.data as AudioSectionData

        val frameRate = 48000
        val totalFrames = 24 * (60.0 / 120.0) * frameRate // 12 seconds = 576000 frames
        val framesInt = totalFrames.toInt()

        val rec = SegmentedFloatArray(audioTrack.unit.segmentSize, audioTrack.unit.poolCapacity).apply {
            this.frameRate = frameRate.toDouble()
        }
        var rem = framesInt
        while (rem > 0) {
            val chunk = minOf(rem, rec.segmentSize)
            rec.extend(chunk)
            rem -= chunk
        }
        val samples = FloatArray(framesInt)
        // Put a peak every 2 seconds
        for (i in 0 until framesInt) {
            val secTime = i.toDouble() / frameRate
            if ((secTime % 2.0) < 0.1) {
                samples[i] = 0.8f
            }
        }
        rec.write(0, samples)
        audioData.recording = rec
        audioData.updateThumbnail()

        val originalBlockCount = audioData.thumbnail.blockCount
        val originalMaxAtMid = audioData.thumbnail.maxValues[originalBlockCount / 2]
        val originalMaxAtEnd = audioData.thumbnail.maxValues[originalBlockCount - 10]

        val fakeBasePath = "build/tmp_audio_test_patch_24beats.sntn"
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules, basePath = fakeBasePath)

        val loadedGraph = GraphState()
        GraphSerializer.deserialize(loadedGraph, json, basePath = fakeBasePath)

        val loadedBeatLine = loadedGraph.modules.filterIsInstance<BeatLineModule>().first()
        val loadedTrack = loadedBeatLine.tracks.first() as AudioTrack
        val loadedSec = loadedTrack.sections.first()
        val loadedAudioData = loadedSec.data as AudioSectionData

        assertEquals(framesInt, loadedAudioData.recording!!.length, "Recording length should match")
        assertEquals(originalBlockCount, loadedAudioData.thumbnail.blockCount, "Block count should match")
        // Check if second half of thumbnail has data
        val loadedMaxAtMid = loadedAudioData.thumbnail.maxValues[loadedAudioData.thumbnail.blockCount / 2]
        val loadedMaxAtEnd = loadedAudioData.thumbnail.maxValues[loadedAudioData.thumbnail.blockCount - 10]
        println("Original blocks: $originalBlockCount, loaded blocks: ${loadedAudioData.thumbnail.blockCount}")
        println("Original mid: $originalMaxAtMid, end: $originalMaxAtEnd")
        println("Loaded mid: $loadedMaxAtMid, end: $loadedMaxAtEnd")

        audioTrack.teardown()
        loadedTrack.teardown()
    }

    @Test
    fun testAudioTrackStopRecordingViaStopPlayback() {
        val beatLine = BeatLineModule()
        val engine = TestEngine()
        beatLine.setEngine(engine)

        val audioTrack = beatLine.addTrack(TrackType.AUDIO, "Audio 4") as AudioTrack
        audioTrack.isRecordArmed = true
        audioTrack.latencyFramesOverride = 1000
        audioTrack.setEngine(engine)

        // Start recording at 0.0
        beatLine.startRecording(ScoreTime.Zero)
        assertTrue(beatLine.isRecording)
        assertTrue(audioTrack.unit.isRecording)

        // Generate some frames
        val inputs = audioTrack.unit.input.getValues()
        for (i in 0 until 64) {
            inputs[i % Synthesizer.FRAMES_PER_BLOCK] = 0.5f
            if ((i + 1) % Synthesizer.FRAMES_PER_BLOCK == 0) {
                audioTrack.unit.generate()
            }
        }

        // Now stop playback (e.g. user clicked Stop button or transport hit cycleEnd)
        beatLine.stopPlayback(ScoreTime(1_000_000_000L))

        // Flush pending record stop
        audioTrack.flushPendingRecordStop()

        println("After flushPendingRecordStop: unit.isRecording = ${audioTrack.unit.isRecording}")
        assertFalse(audioTrack.unit.isRecording, "unit.isRecording should be false after flushPendingRecordStop")

        audioTrack.teardown()
    }
}

