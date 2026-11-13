package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RatioMathTest {

    @Test
    fun testGcdReduction() {
        assertEquals(Ratio(3, 2), Ratio(6, 4).reduced())
        assertEquals(Ratio(1, 1), Ratio(4, 4).reduced())
        assertEquals(Ratio(3, 1), Ratio(6, 2).reduced())
    }

    @Test
    fun testUnison() {
        assertEquals(Ratio(1, 1), RatioMath.closestRatio(0.0))
    }

    @Test
    fun testEveryBaseRatioMapsToItself() {
        for (ratio in RatioMath.BASE_RATIOS) {
            assertEquals(
                ratio.reduced(),
                RatioMath.closestRatio(ratio.semitones),
                "closestRatio should return $ratio at its exact semitone offset"
            )
        }
    }

    @Test
    fun testFiveThirdsIsSupported() {
        assertTrue(RatioMath.BASE_RATIOS.contains(Ratio(5, 3)), "5/3 (just major sixth) must be supported")
        assertEquals(Ratio(5, 3), RatioMath.closestRatio(Ratio(5, 3).semitones))
    }

    @Test
    fun testInverseRatios() {
        // Descending by a fifth: 3/2 becomes 2/3.
        assertEquals(Ratio(2, 3), RatioMath.closestRatio(-Ratio(3, 2).semitones))
        // Descending by a major third: 5/4 becomes 4/5.
        assertEquals(Ratio(4, 5), RatioMath.closestRatio(-Ratio(5, 4).semitones))
    }

    @Test
    fun testOctaveTranspositions() {
        assertEquals(Ratio(2, 1), RatioMath.closestRatio(12.0))
        assertEquals(Ratio(4, 1), RatioMath.closestRatio(24.0))
        assertEquals(Ratio(1, 2), RatioMath.closestRatio(-12.0))
        // A twelfth (octave + fifth, ~19.02 semitones) is 3/1.
        assertEquals(Ratio(3, 1), RatioMath.closestRatio(19.02))
        // An octave below a fifth: descending ~4.98 semitones is 2/3... descending 16.98 is 1/3.
        assertEquals(Ratio(1, 3), RatioMath.closestRatio(-19.02))
    }

    @Test
    fun testNearestSelection() {
        // 7.1 semitones is closest to the fifth (7.02).
        assertEquals(Ratio(3, 2), RatioMath.closestRatio(7.1))
        // 1.0 semitone is closest to 16/15 (1.12).
        assertEquals(Ratio(16, 15), RatioMath.closestRatio(1.0))
    }
}

class PianoRollModuleTest {

    private fun module(): PianoRollModule = PianoRollModule()

    @Test
    fun testModuleCreationAndPorts() {
        val m = module()
        assertEquals("pianoRoll", m.name)
        assertNotNull(m.inputs.find { it.name == "start" }, "should have a 'start' command input")
        assertNotNull(m.inputs.find { it.name == "stop" }, "should have a 'stop' command input")
        val tempoInput = m.inputs.find { it.name == "tempo" }
        assertNotNull(tempoInput, "should have a 'tempo' input")
        assertEquals(120.0, tempoInput.getValue())
        assertEquals(20.0, tempoInput.min)
        assertEquals(300.0, tempoInput.max)
        assertNotNull(m.outputs.find { it.name == "out" }, "should have an 'out' voice output")
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("pianoRoll")
        assertNotNull(creator, "pianoRoll should be registered in ModuleRegistry")
        assertTrue(creator() is PianoRollModule)
        assertEquals("music.input.pianoRoll", ModuleRegistry.getBundlePath("pianoRoll"))
    }

    @Test
    fun testPitchDerivationThroughChain() {
        val m = module()
        val root = PianoRollNote(id = 0, startTicks = 0, durationTicks = 240, rootPitch = 60.0)
        val fifth = PianoRollNote(id = 1, startTicks = 240, durationTicks = 240, parentId = 0, ratio = Ratio(3, 2))
        val thirdOfFifth = PianoRollNote(id = 2, startTicks = 480, durationTicks = 240, parentId = 1, ratio = Ratio(5, 4))
        m.notes.addAll(listOf(root, fifth, thirdOfFifth))

        assertEquals(60.0, m.pitchOf(root))
        assertTrue(abs(m.pitchOf(fifth) - 67.02) < 0.01, "3/2 above 60.0 should be ~67.02, was ${m.pitchOf(fifth)}")
        assertTrue(abs(m.pitchOf(thirdOfFifth) - 70.88) < 0.01, "5/4 above the fifth should be ~70.88")

        // Moving the root moves the whole chain: relative pitches are preserved.
        root.rootPitch = 62.0
        assertTrue(abs(m.pitchOf(thirdOfFifth) - 72.88) < 0.01)
    }

    @Test
    fun testDescendantsAndCycleGuard() {
        val m = module()
        m.notes.addAll(
            listOf(
                PianoRollNote(id = 0, startTicks = 0, durationTicks = 240),
                PianoRollNote(id = 1, startTicks = 0, durationTicks = 240, parentId = 0),
                PianoRollNote(id = 2, startTicks = 0, durationTicks = 240, parentId = 1),
                PianoRollNote(id = 3, startTicks = 0, durationTicks = 240),
            )
        )
        assertEquals(listOf(1, 2), m.descendantsOf(0).map { it.id })
        assertTrue(m.isDescendant(2, 0))
        assertFalse(m.isDescendant(3, 0))
        assertFalse(m.isDescendant(0, 2))
    }

    @Test
    fun testAddAndDeleteCommandSymmetry() {
        val m = module()
        val root = PianoRollNote(id = 0, startTicks = 0, durationTicks = 240, rootPitch = 60.0)
        val child = PianoRollNote(id = 1, startTicks = 240, durationTicks = 240, parentId = 0, ratio = Ratio(3, 2))
        val add = AddNotesCommand(m, listOf(root, child))
        add.execute()
        assertEquals(2, m.notes.size)
        add.undo()
        assertEquals(0, m.notes.size)
        add.execute()

        // Deleting the root cascades to its dependents; undo restores both.
        val delete = DeleteNotesCommand(m, listOf(0))
        assertEquals(setOf(0, 1), delete.deletedIds)
        delete.execute()
        assertEquals(0, m.notes.size)
        delete.undo()
        assertEquals(2, m.notes.size)
        assertEquals(0, m.notes.first { it.id == 1 }.parentId)
    }

    @Test
    fun testChangeNotesCommandSymmetry() {
        val m = module()
        val note = PianoRollNote(id = 0, startTicks = 0, durationTicks = 240, rootPitch = 60.0)
        m.notes.add(note)
        val before = note.captureFields()
        val after = before.copy(startTicks = 480, rootPitch = 64.0)
        val cmd = ChangeNotesCommand(m, listOf(ChangeNotesCommand.Edit(0, before, after)))
        cmd.execute()
        assertEquals(480, note.startTicks)
        assertEquals(64.0, note.rootPitch)
        cmd.undo()
        assertEquals(0, note.startTicks)
        assertEquals(60.0, note.rootPitch)
    }

    @Test
    fun testExecuteWithoutEngineDoesNotCrash() {
        val m = module()
        m.notes.add(PianoRollNote(id = 0, startTicks = 0, durationTicks = 240))
        // No engine attached: kicking start must not throw or mark playing.
        m.start.receive(com.syntona.dataflow.model.Message.FloatValue(com.syntona.core.model.ScoreTime.Zero, 0.0))
        assertFalse(m.isPlaying)
    }

    @Test
    fun testFrequencyToPitch() {
        assertTrue(abs(PianoRollModule.frequencyToPitch(261.63) - 60.0) < 0.01, "C4 = 261.63 Hz should be pitch 60.0")
        assertTrue(abs(PianoRollModule.frequencyToPitch(440.0) - 69.0) < 1e-9, "A4 = 440 Hz should be pitch 69.0")
    }
}

class PianoRollSerializationTest {

    @Test
    fun testRoundTrip() {
        val graphState = GraphState()
        val m = PianoRollModule()
        m.tempo.setValue(90.0)
        m.playStartTicks = 480
        m.windowWidth = 900f
        m.notes.addAll(
            listOf(
                PianoRollNote(id = 0, startTicks = 240, durationTicks = 240, rootPitch = 60.0),
                PianoRollNote(id = 1, startTicks = 480, durationTicks = 120, parentId = 0, ratio = Ratio(3, 2)),
            )
        )
        graphState.addModule(m, Offset.Zero)

        val json = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(json.contains("\"tempo\""), "serialized JSON should contain tempo jack")

        val restoredState = GraphState()
        GraphSerializer.deserialize(restoredState, json)
        val restored = restoredState.modules.filterIsInstance<PianoRollModule>().firstOrNull()
        assertNotNull(restored)
        assertEquals(90.0, restored.tempo.getValue())
        assertEquals(480, restored.playStartTicks)
        assertEquals(900f, restored.windowWidth)
        assertEquals(2, restored.notes.size)

        val root = restored.notes.first { it.id == 0 }
        assertEquals(240, root.startTicks)
        assertEquals(240, root.durationTicks)
        assertTrue(root.isRoot)
        assertEquals(60.0, root.rootPitch)

        val child = restored.notes.first { it.id == 1 }
        assertEquals(0, child.parentId)
        assertEquals(Ratio(3, 2), child.ratio)
        assertEquals(120, child.durationTicks)

        // Fresh ids must not collide with restored ones.
        assertTrue(restored.allocateNoteId() >= 2)
    }

    @Test
    fun testDeserializationWithoutPianoRollKeys() {
        // A minimal older-style entry lacking bpm/notes must load with defaults.
        val json = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "music.input.pianoRoll",
                  "name": "pianoRoll",
                  "x": 0.0,
                  "y": 0.0,
                  "jacks": {}
                }
              ],
              "connections": []
            }
        """.trimIndent()
        val graphState = GraphState()
        GraphSerializer.deserialize(graphState, json)
        val m = graphState.modules.filterIsInstance<PianoRollModule>().firstOrNull()
        assertNotNull(m)
        assertEquals(PianoRollTiming.DEFAULT_TEMPO, m.tempo.getValue())
        assertEquals(PianoRollModule.NoteOutputMode.NOTE, m.noteOutputMode)
        assertEquals(0, m.playStartTicks)
        assertEquals(0, m.notes.size)
        assertNull(m.windowWidth)
    }

    @Test
    fun testLegacyBpmDeserialization() {
        val json = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "music.input.pianoRoll",
                  "name": "pianoRoll",
                  "x": 0.0,
                  "y": 0.0,
                  "jacks": {},
                  "bpm": 85
                }
              ],
              "connections": []
            }
        """.trimIndent()
        val graphState = GraphState()
        GraphSerializer.deserialize(graphState, json)
        val m = graphState.modules.filterIsInstance<PianoRollModule>().firstOrNull()
        assertNotNull(m)
        assertEquals(85.0, m.tempo.getValue())
    }

    @Test
    fun testOrphanedParentBecomesRoot() {
        val json = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "music.input.pianoRoll",
                  "name": "pianoRoll",
                  "x": 0.0,
                  "y": 0.0,
                  "jacks": {},
                  "bpm": 120,
                  "playStart": 0,
                  "notes": [[5, 0, 240, 99, 3, 2, 60.0]]
                }
              ],
              "connections": []
            }
        """.trimIndent()
        val graphState = GraphState()
        GraphSerializer.deserialize(graphState, json)
        val m = graphState.modules.filterIsInstance<PianoRollModule>().firstOrNull()
        assertNotNull(m)
        assertEquals(1, m.notes.size)
        val note = m.notes.first()
        assertTrue(note.isRoot, "note with missing parent should become a root")
        assertEquals(Ratio(1, 1), note.ratio)
        // Its absolute pitch is preserved relative to middle C.
        assertTrue(abs(note.rootPitch - (60.0 + Ratio(3, 2).semitones)) < 0.01)
    }

    @Test
    fun testNoteModeAndAmplitudeSerializationRoundTrip() {
        val src = GraphState()
        val module = PianoRollModule()
        module.noteOutputMode = PianoRollModule.NoteOutputMode.NOTE_ON_OFF
        module.notes.add(PianoRollNote(id = 0, startTicks = 0, durationTicks = 240, rootPitch = 62.0, amplitude = 0.65))
        src.addModule(module, Offset.Zero)

        val json = GraphSerializer.serialize(src, src.modules)
        val dst = GraphState()
        GraphSerializer.deserialize(dst, json)

        val loaded = dst.modules.filterIsInstance<PianoRollModule>().firstOrNull()
        assertNotNull(loaded)
        assertEquals(PianoRollModule.NoteOutputMode.NOTE_ON_OFF, loaded.noteOutputMode)
        assertEquals(1, loaded.notes.size)
        assertEquals(0.65, loaded.notes[0].amplitude, 1e-6)
    }
}
