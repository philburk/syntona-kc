package com.syntona.dataflow.score

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.EditActionHandler
import com.syntona.dataflow.editor.EditActions
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.MdiState
import com.syntona.dataflow.editor.MdiWindowInfo
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScoreModuleTest {

    private class RecordingDrawTarget : ScoreDrawTarget {
        data class DrawnLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val strokeWidth: Float)
        data class DrawnRect(val x: Float, val y: Float, val width: Float, val height: Float)
        data class DrawnBezier(val x0: Float, val y0: Float, val x3: Float, val y3: Float)
        data class DrawnGlyph(
            val glyph: String,
            val x: Float,
            val baselineY: Float,
            val fontSizePx: Float,
            val color: Color,
        )
        data class DrawnText(val text: String, val x: Float, val baselineY: Float, val fontSizePx: Float)
        data class DrawnBeam(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val heightPx: Float)

        val lines = mutableListOf<DrawnLine>()
        val rects = mutableListOf<DrawnRect>()
        val beziers = mutableListOf<DrawnBezier>()
        val glyphs = mutableListOf<DrawnGlyph>()
        val texts = mutableListOf<DrawnText>()
        val beams = mutableListOf<DrawnBeam>()

        override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Color) {
            lines.add(DrawnLine(x1, y1, x2, y2, strokeWidth))
        }

        override fun fillRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
            rects.add(DrawnRect(x, y, width, height))
        }

        override fun drawCubicBezier(
            x0: Float,
            y0: Float,
            cx1: Float,
            cy1: Float,
            cx2: Float,
            cy2: Float,
            x3: Float,
            y3: Float,
            strokeWidth: Float,
            color: Color,
        ) {
            beziers.add(DrawnBezier(x0, y0, x3, y3))
        }

        override fun drawGlyph(glyph: String, x: Float, baselineY: Float, fontSizePx: Float, color: Color) {
            glyphs.add(DrawnGlyph(glyph, x, baselineY, fontSizePx, color))
        }

        override fun drawText(text: String, x: Float, baselineY: Float, fontSizePx: Float, color: Color) {
            texts.add(DrawnText(text, x, baselineY, fontSizePx))
        }

        override fun measureGlyphWidth(glyph: String, fontSizePx: Float): Float {
            return fontSizePx * 0.35f
        }

        override fun drawBeamSegment(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            heightPx: Float,
            color: Color,
        ) {
            beams.add(DrawnBeam(x1, y1, x2, y2, heightPx))
        }
    }

    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, () -> Unit>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            scheduled.add(time to action)
            return ScheduledEvent(action)
        }
    }

    private class VoiceReceiver : Module("voiceReceiver") {
        val inJack = addVoiceInput("in", isKickable = true)
        val received = mutableListOf<Message.VoiceMessage>()

        override fun execute(triggerMessage: Message) {
            if (triggerMessage is Message.VoiceMessage) {
                received.add(triggerMessage)
            }
        }
    }

    @Test
    fun testRegistryLookupAndDescription() {
        val byPath = ModuleRegistry.findCreator("music.compose.score")
        val byName = ModuleRegistry.findCreator("score")
        assertNotNull(byPath, "music.compose.score should be registered")
        assertNotNull(byName, "score should be registered")
        assertEquals("music.compose.score", ModuleRegistry.getBundlePath("score"))
        assertNotNull(ModuleRegistry.moduleDescriptions["score"])

        val module = byPath()
        assertTrue(module is ScoreModule)
    }

    @Test
    fun testTrebleStaffStepMappingAndKeySignature() {
        assertEquals(0, Clef.TREBLE.staffStepFromBottom(SpelledPitch(NoteLetter.E, 0, 4)))
        assertEquals(2, Clef.TREBLE.staffStepFromBottom(SpelledPitch(NoteLetter.G, 0, 4)))
        assertEquals(4, Clef.TREBLE.staffStepFromBottom(SpelledPitch(NoteLetter.B, 0, 4)))
        assertEquals(-2, Clef.TREBLE.staffStepFromBottom(SpelledPitch(NoteLetter.C, 0, 4)))
        assertEquals(10, Clef.TREBLE.staffStepFromBottom(SpelledPitch(NoteLetter.A, 0, 5)))

        assertEquals(1, KeySignature.D_MAJOR.defaultAlter(NoteLetter.F))
        assertEquals(1, KeySignature.D_MAJOR.defaultAlter(NoteLetter.C))
        assertEquals(0, KeySignature.D_MAJOR.defaultAlter(NoteLetter.G))
        assertEquals(listOf(8, 5), KeySignature.D_MAJOR.trebleStaffSteps())
    }

    @Test
    fun testNewScoreStartsEmptyInKeyOfCFourFour() {
        val rawData = ScoreData()
        assertEquals(KeySignature.C_MAJOR, rawData.keySignature)
        assertEquals(TimeSignature(4, 4), rawData.timeSignature)
        assertEquals(8, rawData.measures.size)
        assertEquals((1..8).toList(), rawData.measures.map { it.number })
        assertTrue(rawData.measures.all { it.items.isEmpty() }, "Raw ScoreData starts with 8 empty measures")

        val module = ScoreModule()
        assertTrue(module.autoFillRests, "Auto Fill Rests should be on by default")
        assertEquals(KeySignature.C_MAJOR, module.scoreData.keySignature)
        assertEquals(TimeSignature(4, 4), module.scoreData.timeSignature)
        assertEquals(8, module.scoreData.measures.size)
        assertEquals((1..8).toList(), module.scoreData.measures.map { it.number })
        assertTrue(
            module.scoreData.measures.all { it.items == listOf(ScoreItem.Rest(NoteDuration.WHOLE)) },
            "New score module with autoFillRests=true should fill its 8 bars with whole rests",
        )
        assertEquals(1, module.scoreData.instruments.size)
        val defaultInst = module.scoreData.instruments[0]
        assertEquals("Synth", defaultInst.name)
        assertEquals(InstrumentType.ELECTRONIC, defaultInst.instrumentType)
        assertEquals(50, defaultInst.minMidiPitch) // D3
        assertEquals(91, defaultInst.maxMidiPitch) // G6
        assertEquals("D3 – G6", defaultInst.rangeSummary())
        assertEquals(1, defaultInst.staffCount)
        assertEquals(Clef.TREBLE, defaultInst.clef)
        assertEquals(8, defaultInst.measures.size)
        assertTrue(defaultInst.measures.all { it.items == listOf(ScoreItem.Rest(NoteDuration.WHOLE)) })

        // Left instrument header is always rendered and clickable even for a single default instrument
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = module.scoreData,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )
        assertTrue(layout.showInstrumentLabels)
        assertTrue(target.texts.any { it.text == "Synth" })
        assertTrue(target.texts.any { it.text == "D3–G6" })
        val grp = layout.instrumentGroups.single()
        val hit = layout.instrumentGroupAt((grp.labelLeft + grp.braceX) * 0.5f, (grp.topStaffY + grp.bottomStaffY) * 0.5f)
        assertNotNull(hit)
        assertEquals(0, hit.instrumentIdx)
    }

    @Test
    fun testPhase1RenderEmitsStaffClefSignaturesNotesAndRests() {
        val score = ScoreData().apply { loadDefaultSample() }
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )

        assertTrue(layout.totalWidthPx >= 400f)
        assertTrue(layout.totalHeightPx > 40f)

        val glyphStrings = target.glyphs.map { it.glyph }
        // Treble clef + D major (2 sharps) + 4/4 time sig
        assertTrue(SmuflGlyphs.G_CLEF in glyphStrings)
        assertTrue(glyphStrings.count { it == SmuflGlyphs.ACCIDENTAL_SHARP } >= 2)
        // D major sample has 2 key sig sharps + 1 inline G#4 sharp (F#4 and C#5 use key sig)
        assertEquals(3, glyphStrings.count { it == SmuflGlyphs.ACCIDENTAL_SHARP })
        assertEquals(2, glyphStrings.count { it == SmuflGlyphs.timeSigDigit(4) })

        // Distinct noteheads & rests in Phase 1 sample
        assertTrue(SmuflGlyphs.NOTEHEAD_WHOLE in glyphStrings)
        assertTrue(SmuflGlyphs.NOTEHEAD_HALF in glyphStrings)
        assertTrue(SmuflGlyphs.NOTEHEAD_BLACK in glyphStrings)
        assertTrue(SmuflGlyphs.REST_WHOLE in glyphStrings)
        assertTrue(SmuflGlyphs.REST_HALF in glyphStrings)
        assertTrue(SmuflGlyphs.REST_QUARTER in glyphStrings)
        assertTrue(SmuflGlyphs.REST_8TH in glyphStrings)
        assertTrue(SmuflGlyphs.REST_16TH in glyphStrings)
        assertTrue(SmuflGlyphs.AUGMENTATION_DOT in glyphStrings)
        assertTrue(
            SmuflGlyphs.FLAG_8TH_UP in glyphStrings || SmuflGlyphs.FLAG_8TH_DOWN in glyphStrings
        )

        // Staff lines (5 horizontal full-width lines) + barlines + ledger lines (C4 & A5) + stems
        val horizontalLines = target.lines.filter { it.y1 == it.y2 }
        assertTrue(horizontalLines.size >= 7, "Expected 5 staff lines plus C4 and A5 ledger lines")
        val verticalLines = target.lines.filter { it.x1 == it.x2 }
        assertTrue(verticalLines.size >= 10, "Expected barlines and stems")
    }

    @Test
    fun testSerializationRoundTrip() {
        val graph = GraphState()
        val score = ScoreModule().apply {
            scoreData.keySignature = KeySignature.E_FLAT
            scoreData.timeSignature = TimeSignature.SIX_EIGHT
            windowX = 120f
            windowY = 80f
            windowWidth = 920f
            windowHeight = 360f
        }
        graph.addModule(score, Offset(40f, 60f))

        val json = GraphSerializer.serialize(graph, graph.modules)
        assertTrue(json.contains("music.compose.score"))

        val reloadedGraph = GraphState()
        val restored = GraphSerializer.deserialize(reloadedGraph, json)
        val restoredScore = restored.filterIsInstance<ScoreModule>().singleOrNull()
        assertNotNull(restoredScore)
        assertEquals(KeySignature.E_FLAT, restoredScore.scoreData.keySignature)
        assertEquals(TimeSignature.SIX_EIGHT, restoredScore.scoreData.timeSignature)
        assertEquals(120f, restoredScore.windowX)
        assertEquals(80f, restoredScore.windowY)
        assertEquals(920f, restoredScore.windowWidth)
        assertEquals(360f, restoredScore.windowHeight)
    }

    @Test
    fun testPlaybackEmitsNoteVoiceMessagesForNotesAndSkipsRests() {
        val engine = TestEngine()
        val score = ScoreModule().apply { scoreData.loadDefaultSample() }
        score.setEngine(engine)

        val receiver = VoiceReceiver()
        score.output.connect(receiver.inJack)

        score.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        assertTrue(score.isPlaying)

        val expectedNotes = score.scoreData.measures.flatMap { it.items }.filterIsInstance<ScoreItem.Note>()
        assertTrue(expectedNotes.isNotEmpty())

        for ((_, action) in engine.scheduled) {
            action()
        }

        assertEquals(expectedNotes.size, receiver.received.size)
        assertEquals(expectedNotes.first().pitch.midiPitch.toDouble(), receiver.received.first().value)
        assertTrue(receiver.received.all { it.opcode == Message.VoiceOpcode.NOTE && it.duration > 0.0 })
        assertFalse(score.isPlaying)
    }

    @Test
    fun testStaffStepToPitchAndDisplay() {
        val e4 = Clef.TREBLE.pitchForStaffStep(0, KeySignature.C_MAJOR)
        assertEquals(SpelledPitch(NoteLetter.E, 0, 4), e4)
        assertEquals("E4, 64", e4.pitchDisplay())

        val fSharp4 = Clef.TREBLE.pitchForStaffStep(1, KeySignature.D_MAJOR)
        assertEquals(SpelledPitch(NoteLetter.F, 1, 4), fSharp4)
        assertEquals("F#4, 66", fSharp4.pitchDisplay())

        val cSharp4 = Clef.TREBLE.pitchForStaffStep(-2, KeySignature.C_MAJOR, alterOverride = 1)
        assertEquals(SpelledPitch(NoteLetter.C, 1, 4), cSharp4)
        assertEquals("C#4, 61", cSharp4.pitchDisplay())

        val bFlat4 = Clef.TREBLE.pitchForStaffStep(4, KeySignature.B_FLAT)
        assertEquals(SpelledPitch(NoteLetter.B, -1, 4), bFlat4)
        assertEquals("Bb4, 70", bFlat4.pitchDisplay())
    }

    @Test
    fun testThirtySecondNotesRestsAndStaccatoRender() {
        val score = ScoreData()
        score.replaceMeasures(
            listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(
                            pitch = SpelledPitch(NoteLetter.G, 0, 4),
                            duration = NoteDuration.THIRTY_SECOND,
                            isStaccato = true,
                        ),
                        ScoreItem.Rest(NoteDuration.THIRTY_SECOND),
                        ScoreItem.Note(
                            pitch = SpelledPitch(NoteLetter.D, 0, 5),
                            duration = NoteDuration.THIRTY_SECOND,
                            isStaccato = true,
                        ),
                    ),
                )
            )
        )
        val target = RecordingDrawTarget()
        ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )

        val glyphStrings = target.glyphs.map { it.glyph }
        assertTrue(SmuflGlyphs.FLAG_32ND_UP in glyphStrings)
        assertTrue(SmuflGlyphs.FLAG_32ND_DOWN in glyphStrings)
        assertTrue(SmuflGlyphs.REST_32ND in glyphStrings)
        assertTrue(SmuflGlyphs.ARTIC_STACCATO_BELOW in glyphStrings)
        assertTrue(SmuflGlyphs.ARTIC_STACCATO_ABOVE in glyphStrings)
    }

    @Test
    fun testOverflowNotesAreHighlightedInRed() {
        val score = ScoreData()
        score.timeSignature = TimeSignature.COMMON_TIME // 4/4 = 960 ticks
        score.replaceMeasures(
            listOf(
                ScoreMeasure(
                    number = 1,
                    items = List(5) { idx ->
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 5), NoteDuration.QUARTER)
                    },
                )
            )
        )
        val colors = ScoreRenderColors.forTheme(darkMode = false)
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = colors,
        )

        val placed = layout.placedMeasures.single().items
        assertEquals(5, placed.size)
        assertFalse(placed[0].isOverflow)
        assertFalse(placed[1].isOverflow)
        assertFalse(placed[2].isOverflow)
        assertFalse(placed[3].isOverflow)
        assertTrue(placed[4].isOverflow, "Fifth quarter note in 4/4 must be flagged as overflow")

        val noteheadGlyphs = target.glyphs.filter { it.glyph == SmuflGlyphs.NOTEHEAD_BLACK }
        assertEquals(5, noteheadGlyphs.size)
        assertEquals(colors.ink, noteheadGlyphs[3].color)
        assertEquals(colors.overflowNote, noteheadGlyphs[4].color)
    }

    @Test
    fun testEditingOperationsLayoutHitTestAndUndoRedo() {
        val score = ScoreData()
        score.keySignature = KeySignature.C_MAJOR
        score.replaceMeasures(ScoreEditing.clearToSingleEmptyMeasure())

        val layout1 = ScoreRenderer.computeLayout(score, staffSpacePx = 10f, minWidthPx = 600f) { _, fontSize ->
            fontSize * 0.35f
        }
        val emptyTarget = layout1.insertionTargetAt(layout1.placedMeasures.first().startX + 15f)
        assertNotNull(emptyTarget)
        assertEquals(0, emptyTarget.measureIdx)
        assertEquals(0, emptyTarget.itemIdx)
        assertFalse(emptyTarget.appendNewMeasure)

        // Place first note (E4 quarter)
        val before1 = score.measures.toList()
        val note1 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val (after1, loc1) = ScoreEditing.insertItem(before1, emptyTarget.measureIdx, emptyTarget.itemIdx, note1)
        val cmd1 = ReplaceScoreMeasuresCommand(score, before1, after1)
        cmd1.execute()
        assertEquals(1, score.measures[0].items.size)
        assertEquals(ScoreItemLocation(0, 0), loc1)

        // Hit test the placed note & drag pitch up to G4
        val layout2 = ScoreRenderer.computeLayout(score, 10f, 600f) { _, fs -> fs * 0.35f }
        val placedNote = layout2.placedMeasures[0].items[0]
        val hit = layout2.itemAt(placedNote.x + placedNote.width * 0.5f, placedNote.centerY)
        assertNotNull(hit)
        assertEquals(loc1, hit.location)

        val draggedMeasures = ScoreEditing.setNotePitch(
            score.measures.toList(),
            loc1,
            SpelledPitch(NoteLetter.G, 0, 4),
        )
        score.replaceMeasures(draggedMeasures)
        assertEquals(SpelledPitch(NoteLetter.G, 0, 4), (score.measures[0].items[0] as ScoreItem.Note).pitch)

        // Replace quarter note with half rest
        val replaced = ScoreEditing.replaceItem(score.measures.toList(), loc1, ScoreItem.Rest(NoteDuration.HALF))
        score.replaceMeasures(replaced)
        assertTrue(score.measures[0].items[0] is ScoreItem.Rest)
        assertEquals(NoteDuration.HALF, score.measures[0].items[0].duration)

        // Click past the final barline to start Measure 2
        val layout3 = ScoreRenderer.computeLayout(score, 10f, 600f) { _, fs -> fs * 0.35f }
        val pastEndTarget = layout3.insertionTargetAt(layout3.placedMeasures.last().barlineX + 25f)
        assertNotNull(pastEndTarget)
        assertTrue(pastEndTarget.appendNewMeasure)
        val (afterM2, loc2) = ScoreEditing.insertItem(
            score.measures.toList(),
            pastEndTarget.measureIdx,
            pastEndTarget.itemIdx,
            ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.QUARTER),
            appendNewMeasure = pastEndTarget.appendNewMeasure,
        )
        score.replaceMeasures(afterM2)
        assertEquals(2, score.measures.size)

        // Apply sharp & toggle staccato on Measure 2 note
        val sel = setOf(loc2)
        val sharpened = ScoreEditing.setAccidentalOnSelection(score.measures.toList(), sel, 1)
        val staccatoed = ScoreEditing.toggleStaccatoOnSelection(sharpened, sel)
        score.replaceMeasures(staccatoed)
        val editedNote = score.measures[1].items[0] as ScoreItem.Note
        assertEquals(1, editedNote.pitch.alter)
        assertTrue(editedNote.isStaccato)

        // Delete selection
        val afterDelete = ScoreEditing.deleteItems(score.measures.toList(), setOf(loc1, loc2))
        score.replaceMeasures(afterDelete)
        assertTrue(score.measures.all { it.items.isEmpty() })

        // Undo cmd1 restores initial empty measure
        cmd1.undo()
        assertEquals(1, score.measures.size)
        assertTrue(score.measures[0].items.isEmpty())
    }

    @Test
    fun testStaccatoPlaybackUsesShortDurationAndKeepsRhythmicSpacing() {
        val engine = TestEngine()
        val score = ScoreModule()
        score.setEngine(engine)
        score.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER, isStaccato = false),
                    ),
                )
            )
        )

        val receiver = VoiceReceiver()
        score.output.connect(receiver.inJack)
        score.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))

        for ((_, action) in engine.scheduled) {
            action()
        }

        assertEquals(2, receiver.received.size)
        val first = receiver.received[0]
        val second = receiver.received[1]
        assertTrue(first.duration <= ScoreModule.MAX_STACCATO_SECONDS + 1e-6)
        assertTrue(second.duration >= 0.45, "Quarter note at 120 BPM should be ~0.5s, got ${second.duration}")
        val deltaSec = (second.time.nanoseconds - first.time.nanoseconds) / 1_000_000_000.0
        assertTrue(deltaSec >= 0.49, "Second note should still start one full quarter beat later, got $deltaSec")
    }

    @Test
    fun testSerializationRoundTripPreservesEditedMeasuresStaccatoAndThirtySecond() {
        val graph = GraphState()
        val score = ScoreModule().apply {
            scoreData.keySignature = KeySignature.G_MAJOR
            scoreData.timeSignature = TimeSignature.THREE_FOUR
            scoreData.replaceMeasures(
                listOf(
                    ScoreMeasure(
                        number = 1,
                        items = listOf(
                            ScoreItem.Note(
                                pitch = SpelledPitch(NoteLetter.F, 1, 4),
                                duration = NoteDuration.THIRTY_SECOND,
                                isDotted = false,
                                amplitude = 0.4,
                                isStaccato = true,
                            ),
                            ScoreItem.Rest(NoteDuration.THIRTY_SECOND, isDotted = true),
                        ),
                    )
                )
            )
        }
        graph.addModule(score, Offset(10f, 20f))

        val json = GraphSerializer.serialize(graph, graph.modules)
        val reloadedGraph = GraphState()
        val restored = GraphSerializer.deserialize(reloadedGraph, json)
        val restoredScore = restored.filterIsInstance<ScoreModule>().singleOrNull()
        assertNotNull(restoredScore)
        assertEquals(1, restoredScore.scoreData.measures.size)
        assertEquals(score.scoreData.measures.toList(), restoredScore.scoreData.measures.toList())
    }

    @Test
    fun testRubberBandSelectionAndSelectedBoxRendering() {
        val score = ScoreData().apply { loadDefaultSample() }
        val colors = ScoreRenderColors.forTheme(darkMode = false)
        val layout = ScoreRenderer.computeLayout(score, staffSpacePx = 10f, minWidthPx = 800f) { _, fs -> fs * 0.35f }

        val firstItem = layout.placedMeasures[0].items[0]
        val thirdItem = layout.placedMeasures[0].items[2]
        val hits = layout.itemsInRect(
            left = firstItem.boxLeft,
            top = minOf(firstItem.boxTop, thirdItem.boxTop),
            right = thirdItem.boxRight,
            bottom = maxOf(firstItem.boxBottom, thirdItem.boxBottom),
        )
        assertEquals(
            setOf(ScoreItemLocation(0, 0), ScoreItemLocation(0, 1), ScoreItemLocation(0, 2)),
            hits,
        )

        val target = RecordingDrawTarget()
        ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 800f,
            colors = colors,
            selectedLocations = hits,
            rubberBand = ScoreRubberBand(firstItem.boxLeft, firstItem.boxTop, thirdItem.boxRight, thirdItem.boxBottom),
        )
        // 3 selection highlight boxes + 1 rubber-band rect
        assertEquals(4, target.rects.size)
        assertTrue(target.glyphs.any { it.color == colors.selectedNote })
    }

    @Test
    fun testNotesRestsPaletteOptionsContainGlyphsAndShortcuts() {
        assertEquals("Notes/Rests", ScorePaletteKind.NOTES_RESTS.title)

        val expectedShortcuts = listOf('w', 'h', 'q', 'e', 's', 't')
        assertEquals(6, ScorePalettes.noteOptions.size)
        assertEquals(6, ScorePalettes.restOptions.size)
        assertEquals(expectedShortcuts, ScorePalettes.noteOptions.map { it.shortcutChar })
        assertEquals(expectedShortcuts, ScorePalettes.restOptions.map { it.shortcutChar })

        assertTrue(ScorePalettes.noteOptions.all { !it.isRest && it.glyph == it.duration.noteSymbolGlyph })
        assertTrue(ScorePalettes.restOptions.all { it.isRest && it.glyph == it.duration.restGlyph })
        assertEquals(SmuflGlyphs.NOTE_QUARTER_UP, ScorePalettes.noteOptions[2].glyph)
        assertEquals(SmuflGlyphs.REST_QUARTER, ScorePalettes.restOptions[2].glyph)

        assertEquals(
            listOf(Accidental.FLAT, Accidental.SHARP, Accidental.NATURAL),
            ScorePalettes.accidentalOptions.map { it.accidental },
        )
        assertEquals(listOf('2', '3', '4'), ScorePalettes.accidentalOptions.map { it.shortcutChar })
    }

    @Test
    fun testHorizontalScrollbarMathForShortAndLongScores() {
        // When everything fits in the viewport, thumb spans 100% and travel is 0
        val shortMetrics = ScoreScrollMath.computeThumbMetrics(
            trackWidthPx = 600f,
            scrollPx = 0f,
            maxScrollPx = 0f,
        )
        assertEquals(600f, shortMetrics.thumbWidth, 0.01f)
        assertEquals(0f, shortMetrics.trackTravel, 0.01f)
        assertEquals(0f, shortMetrics.thumbX, 0.01f)

        // Long score: 24 measures exceeding a 600px viewport
        val longScore = ScoreData()
        longScore.replaceMeasures(
            List(24) { idx ->
                ScoreMeasure(
                    number = idx + 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.QUARTER),
                    ),
                )
            }
        )
        val layout = ScoreRenderer.computeLayout(longScore, staffSpacePx = 10f, minWidthPx = 600f) { _, fs -> fs * 0.35f }
        assertTrue(layout.totalWidthPx > 2000f)

        val viewportWidth = 600f
        val maxScroll = layout.totalWidthPx - viewportWidth
        val midMetrics = ScoreScrollMath.computeThumbMetrics(
            trackWidthPx = viewportWidth,
            scrollPx = maxScroll * 0.5f,
            maxScrollPx = maxScroll,
        )
        assertTrue(midMetrics.thumbWidth in (viewportWidth * ScoreScrollMath.MIN_THUMB_FRACTION)..viewportWidth)
        assertEquals(midMetrics.trackTravel * 0.5f, midMetrics.thumbX, 0.5f)

        // Dragging right by half the track travel advances scroll by half of maxScroll
        val draggedScroll = ScoreScrollMath.scrollForThumbDrag(
            startScrollPx = 0f,
            deltaX = midMetrics.trackTravel * 0.5f,
            trackTravelPx = midMetrics.trackTravel,
            maxScrollPx = maxScroll,
        )
        assertTrue(draggedScroll in ((maxScroll * 0.5f).toInt() - 2)..((maxScroll * 0.5f).toInt() + 2))

        // Clicking at the right end of the track scrolls to the final measures
        val endScroll = ScoreScrollMath.scrollForTrackClick(
            clickX = viewportWidth,
            thumbWidthPx = midMetrics.thumbWidth,
            trackTravelPx = midMetrics.trackTravel,
            maxScrollPx = maxScroll,
        )
        assertEquals(maxScroll.roundToInt(), endScroll)
    }

    @Test
    fun testMidiPitchSpellingInSharpFlatAndCKeys() {
        // Sharp key (D major: F#, C#) spells pitch classes 6 and 1 as F#4 and C#5
        assertEquals(SpelledPitch(NoteLetter.F, 1, 4), SpelledPitch.fromMidiPitch(66, KeySignature.D_MAJOR))
        assertEquals(SpelledPitch(NoteLetter.C, 1, 5), SpelledPitch.fromMidiPitch(73, KeySignature.D_MAJOR))
        assertEquals(SpelledPitch(NoteLetter.D, 0, 4), SpelledPitch.fromMidiPitch(62, KeySignature.D_MAJOR))

        // Flat key (F major: Bb) spells pitch class 10 as Bb4 and pitch class 3 as Eb4
        assertEquals(SpelledPitch(NoteLetter.B, -1, 4), SpelledPitch.fromMidiPitch(70, KeySignature.F_MAJOR))
        assertEquals(SpelledPitch(NoteLetter.E, -1, 4), SpelledPitch.fromMidiPitch(63, KeySignature.F_MAJOR))

        // C major defaults black keys to sharps
        assertEquals(SpelledPitch(NoteLetter.F, 1, 4), SpelledPitch.fromMidiPitch(66, KeySignature.C_MAJOR))
        assertEquals(66, SpelledPitch.fromMidiPitch(66, KeySignature.D_MAJOR).midiPitch)
        assertEquals(70, SpelledPitch.fromMidiPitch(70, KeySignature.F_MAJOR).midiPitch)
    }

    @Test
    fun testStepInsertCursorPlacementAndRendererCursor() {
        val score = ScoreData()
        score.timeSignature = TimeSignature.COMMON_TIME // 960 ticks per measure
        score.replaceMeasures(ScoreEditing.clearToSingleEmptyMeasure())

        val n1 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.HALF)
        val n2 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.HALF)
        val n3 = ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.QUARTER)

        // Insert half note at start of empty measure -> cursor moves to (0, 1)
        val r1 = ScoreEditing.stepInsertItem(
            measures = score.measures,
            cursor = ScoreCursorPosition(0, 0),
            item = n1,
            measureCapacityTicks = score.timeSignature.measureCapacityTicks,
        )
        assertEquals(ScoreCursorPosition(0, 1), r1.nextCursor)
        assertEquals(1, r1.measures.size)

        // Insert second half note at (0, 1) -> measure is now full (960 ticks), appends measure 2 & advances cursor to (1, 0)
        val r2 = ScoreEditing.stepInsertItem(
            measures = r1.measures,
            cursor = r1.nextCursor,
            item = n2,
            measureCapacityTicks = score.timeSignature.measureCapacityTicks,
        )
        assertEquals(2, r2.measures.size)
        assertEquals(2, r2.measures[0].items.size)
        assertTrue(r2.measures[1].items.isEmpty())
        assertEquals(ScoreCursorPosition(1, 0), r2.nextCursor)

        // Inserting between items at (0, 1) keeps cursor immediately after the inserted item at (0, 2)
        val rMid = ScoreEditing.stepInsertItem(
            measures = r2.measures,
            cursor = ScoreCursorPosition(0, 1),
            item = n3,
            measureCapacityTicks = score.timeSignature.measureCapacityTicks,
        )
        assertEquals(listOf(n1, n3, n2), rMid.measures[0].items)
        assertEquals(ScoreCursorPosition(0, 2), rMid.nextCursor)

        score.replaceMeasures(r2.measures)
        val target = RecordingDrawTarget()
        val colors = ScoreRenderColors.forTheme(darkMode = false)
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = colors,
            cursorPosition = ScoreCursorPosition(1, 0),
            isStepRecording = true,
        )

        // Clicking inside empty measure 2 resolves to cursor (1, 0)
        val m2 = layout.placedMeasures[1]
        assertEquals(ScoreCursorPosition(1, 0), layout.cursorPositionAt(m2.startX + 12f))
        // Clicking between items 0 and 1 in measure 1 resolves to (0, 1)
        val m1 = layout.placedMeasures[0]
        val betweenX = (m1.items[0].x + m1.items[0].width + m1.items[1].x) * 0.5f
        assertEquals(ScoreCursorPosition(0, 1), layout.cursorPositionAt(betweenX))

        val cursorX = layout.cursorXFor(ScoreCursorPosition(1, 0))
        assertNotNull(cursorX)
        assertTrue(target.lines.any { kotlin.math.abs(it.x1 - cursorX) < 0.01f && kotlin.math.abs(it.x2 - cursorX) < 0.01f })
    }

    @Test
    fun testStepRecordingMidiInputDurationSwitchThruAndUndoRedo() {
        val score = ScoreModule().apply { autoFillRests = false }
        score.scoreData.keySignature = KeySignature.C_MAJOR
        score.scoreData.timeSignature = TimeSignature.COMMON_TIME
        score.scoreData.replaceMeasures(ScoreEditing.clearToSingleEmptyMeasure())

        val receiver = VoiceReceiver()
        score.output.connect(receiver.inJack)
        val inputJack = score.inputs.firstOrNull { it.name == "in" }
        assertNotNull(inputJack)

        // MIDI note arriving while step recording is OFF is ignored
        score.input.receive(
            Message.VoiceMessage(ScoreTime(1L), Message.VoiceOpcode.NOTE_ON, tag = 60, value = 60.0, extra = 0.8)
        )
        assertTrue(score.scoreData.measures[0].items.isEmpty())

        // Enable step recording and place cursor at measure 1 start
        score.placeCursor(ScoreCursorPosition(0, 0))
        score.stepRecordDuration = NoteDuration.EIGHTH
        score.setStepRecordingEnabled(true)
        assertTrue(score.isStepRecording)

        // NOTE_ON inserts an eighth note at C4 and advances cursor, plus sends thru
        score.input.receive(
            Message.VoiceMessage(ScoreTime(2L), Message.VoiceOpcode.NOTE_ON, tag = 60, value = 60.0, extra = 0.75)
        )
        assertEquals(1, score.scoreData.measures[0].items.size)
        val firstNote = score.scoreData.measures[0].items[0] as ScoreItem.Note
        assertEquals(SpelledPitch(NoteLetter.C, 0, 4), firstNote.pitch)
        assertEquals(NoteDuration.EIGHTH, firstNote.duration)
        assertEquals(ScoreCursorPosition(0, 1), score.cursorPosition)
        assertEquals(1, receiver.received.size)

        // NOTE_OFF and NOTE_ON with velocity 0 do not insert notes, still forward thru
        score.input.receive(
            Message.VoiceMessage(ScoreTime(3L), Message.VoiceOpcode.NOTE_OFF, tag = 60, value = 60.0, extra = 0.0)
        )
        score.input.receive(
            Message.VoiceMessage(ScoreTime(4L), Message.VoiceOpcode.NOTE_ON, tag = 60, value = 60.0, extra = 0.0)
        )
        assertEquals(1, score.scoreData.measures[0].items.size)
        assertEquals(3, receiver.received.size)

        // Changing duration while step recording stays armed and records next note with new duration
        score.stepRecordDuration = NoteDuration.QUARTER
        assertTrue(score.isStepRecording)
        score.input.receive(
            Message.VoiceMessage(ScoreTime(5L), Message.VoiceOpcode.NOTE, tag = 64, value = 64.0, extra = 0.9, duration = 0.4)
        )
        assertEquals(2, score.scoreData.measures[0].items.size)
        val secondNote = score.scoreData.measures[0].items[1] as ScoreItem.Note
        assertEquals(SpelledPitch(NoteLetter.E, 0, 4), secondNote.pitch)
        assertEquals(NoteDuration.QUARTER, secondNote.duration)
        assertEquals(ScoreCursorPosition(0, 2), score.cursorPosition)

        // Undoable StepRecordScoreNoteCommand restores and re-applies recorded notes & cursor
        val cmd3 = StepRecordScoreNoteCommand(score, midiPitch = 67, amplitude = 0.85)
        cmd3.execute()
        assertEquals(3, score.scoreData.measures[0].items.size)
        assertEquals(ScoreCursorPosition(0, 3), score.cursorPosition)
        cmd3.undo()
        assertEquals(2, score.scoreData.measures[0].items.size)
        assertEquals(ScoreCursorPosition(0, 2), score.cursorPosition)
        cmd3.execute()
        assertEquals(3, score.scoreData.measures[0].items.size)
        assertEquals(ScoreCursorPosition(0, 3), score.cursorPosition)

        // ESC / exitStepRecording disarms step recording without clearing the score
        score.exitStepRecording()
        assertFalse(score.isStepRecording)
        score.input.receive(
            Message.VoiceMessage(ScoreTime(6L), Message.VoiceOpcode.NOTE_ON, tag = 69, value = 69.0, extra = 0.8)
        )
        assertEquals(3, score.scoreData.measures[0].items.size)
    }

    @Test
    fun testStaccatoDotsStayClearOfNoteheadsAndStaffLines() {
        // Line notes and space notes across treble staff (C4=-2 through F5=8)
        assertEquals(-4, ScoreRenderer.staccatoStepForNote(noteStep = -2, placeBelow = true))
        assertEquals(-3, ScoreRenderer.staccatoStepForNote(noteStep = -1, placeBelow = true))
        assertEquals(-1, ScoreRenderer.staccatoStepForNote(noteStep = 2, placeBelow = true))
        assertEquals(7, ScoreRenderer.staccatoStepForNote(noteStep = 4, placeBelow = false))
        assertEquals(9, ScoreRenderer.staccatoStepForNote(noteStep = 6, placeBelow = false))
        assertEquals(10, ScoreRenderer.staccatoStepForNote(noteStep = 8, placeBelow = false))

        val sp = 10f
        val score = ScoreData()
        score.replaceMeasures(
            listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.QUARTER, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.QUARTER, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.QUARTER, isStaccato = true),
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 5), NoteDuration.QUARTER, isStaccato = true),
                    ),
                )
            )
        )
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = sp,
            minWidthPx = 500f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )

        val noteheads = target.glyphs.filter { it.glyph == SmuflGlyphs.NOTEHEAD_BLACK }
        val staccatos = target.glyphs.filter {
            it.glyph == SmuflGlyphs.ARTIC_STACCATO_BELOW || it.glyph == SmuflGlyphs.ARTIC_STACCATO_ABOVE
        }
        assertEquals(5, noteheads.size)
        assertEquals(5, staccatos.size)

        val staffLineYs = (0..8 step 2).map { layout.yForStep(it) }
        for (i in noteheads.indices) {
            val headY = noteheads[i].baselineY
            val dotGlyph = staccatos[i]
            val dotCenterY = if (dotGlyph.glyph == SmuflGlyphs.ARTIC_STACCATO_BELOW) {
                dotGlyph.baselineY + SmuflGlyphs.ARTIC_STACCATO_WIDTH * sp * 0.5f
            } else {
                dotGlyph.baselineY - SmuflGlyphs.ARTIC_STACCATO_WIDTH * sp * 0.5f
            }
            assertTrue(
                kotlin.math.abs(dotCenterY - headY) >= sp - 0.05f,
                "Staccato dot $i too close to notehead oval: |dotCenterY=$dotCenterY - headY=$headY| < $sp",
            )
            for (lineY in staffLineYs) {
                assertTrue(
                    kotlin.math.abs(dotCenterY - lineY) >= sp * 0.4f,
                    "Staccato dot $i should not sit on staff line y=$lineY (dotCenterY=$dotCenterY)",
                )
            }
        }
    }

    @Test
    fun testKeySignaturePaletteOptionsButtonLabelAndMeasureApplication() {
        assertEquals("Keys/\nTimes", ScorePaletteKind.KEY_SIGNATURES.buttonLabel)
        assertEquals("Keys/Times", ScorePaletteKind.KEY_SIGNATURES.title)
        val paletteKeys = (ScorePalettes.sharpKeyOptions + ScorePalettes.flatKeyOptions).map { it.keySignature }
        assertEquals(KeySignature.entries.size, paletteKeys.size)
        assertEquals(KeySignature.entries.toSet(), paletteKeys.toSet())

        val fSharp4 = ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 4), NoteDuration.QUARTER)
        val cSharp5 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 1, 5), NoteDuration.QUARTER)
        val bb4 = ScoreItem.Note(SpelledPitch(NoteLetter.B, -1, 4), NoteDuration.QUARTER)

        val initialMeasures = listOf(
            ScoreMeasure(number = 1, items = listOf(fSharp4)),
            ScoreMeasure(number = 2, items = listOf(cSharp5)),
            ScoreMeasure(number = 3, items = listOf(bb4), keySignature = KeySignature.F_MAJOR),
            ScoreMeasure(number = 4, items = listOf(fSharp4), keySignature = KeySignature.E_MAJOR),
        )

        // Multi-measure selection (measures 2..4 = indices 1..3): sets key on first selected (index 1)
        // and clears key signatures from later selected measures (indices 2..3), preserving all pitches.
        val (nextInit, updatedMeasures) = ScoreEditing.setKeySignatureOnMeasures(
            initialKey = KeySignature.D_MAJOR,
            measures = initialMeasures,
            selectedMeasureIndices = setOf(1, 2, 3),
            newKey = KeySignature.C_MAJOR,
        )
        assertEquals(KeySignature.D_MAJOR, nextInit)
        assertEquals(null, updatedMeasures[0].keySignature)
        assertEquals(KeySignature.C_MAJOR, updatedMeasures[1].keySignature)
        assertEquals(null, updatedMeasures[2].keySignature)
        assertEquals(null, updatedMeasures[3].keySignature)

        // Note pitch values are completely unchanged across all measures
        assertEquals(fSharp4, updatedMeasures[0].items[0])
        assertEquals(cSharp5, updatedMeasures[1].items[0])
        assertEquals(bb4, updatedMeasures[2].items[0])
        assertEquals(fSharp4, updatedMeasures[3].items[0])
        assertEquals(KeySignature.C_MAJOR, ScoreEditing.effectiveKeyAtMeasure(updatedMeasures, nextInit, 1))
        assertEquals(KeySignature.C_MAJOR, ScoreEditing.effectiveKeyAtMeasure(updatedMeasures, nextInit, 3))

        // Selecting measure 1 + later measures updates opening key and clears later explicit keys
        val (m1Init, m1Measures) = ScoreEditing.setKeySignatureOnMeasures(
            initialKey = nextInit,
            measures = updatedMeasures,
            selectedMeasureIndices = setOf(0, 1),
            newKey = KeySignature.G_MAJOR,
        )
        assertEquals(KeySignature.G_MAJOR, m1Init)
        assertEquals(null, m1Measures[0].keySignature)
        assertEquals(null, m1Measures[1].keySignature)
    }

    @Test
    fun testMidScoreKeyChangeRenderingUndoRedoAndSerialization() {
        val graph = GraphState()
        val scoreModule = ScoreModule().apply {
            scoreData.keySignature = KeySignature.D_MAJOR
            scoreData.replaceMeasures(
                listOf(
                    ScoreMeasure(
                        number = 1,
                        items = listOf(ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 4), NoteDuration.QUARTER)),
                    ),
                    ScoreMeasure(
                        number = 2,
                        // In D major, F#4 has no inline accidental; once Measure 2 changes to C major, F#4 stays F#4
                        // and automatically renders an inline sharp accidental.
                        items = listOf(ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 4), NoteDuration.QUARTER)),
                    ),
                )
            )
        }

        // Apply C major to Measure 2 via undoable command
        val beforeKey = scoreModule.scoreData.keySignature
        val beforeMeasures = scoreModule.scoreData.measures.toList()
        val (afterKey, afterMeasures) = ScoreEditing.setKeySignatureOnMeasures(
            initialKey = beforeKey,
            measures = beforeMeasures,
            selectedMeasureIndices = setOf(1),
            newKey = KeySignature.C_MAJOR,
        )
        val cmd = SetScoreKeySignatureCommand(scoreModule.scoreData, beforeKey, beforeMeasures, afterKey, afterMeasures)
        cmd.execute()

        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = scoreModule.scoreData,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            selectedMeasureIndices = setOf(1),
        )
        // Measure 2 switched D major (2#) -> C major (0), so 2 cancellation naturals + F#4 inline sharp are drawn
        assertEquals(2, layout.placedMeasures[1].keyChangeGlyphs.size)
        assertTrue(layout.placedMeasures[1].keyChangeGlyphs.all { it.second == Accidental.NATURAL })
        assertEquals(Accidental.SHARP, layout.placedMeasures[1].items[0].accidental)
        assertEquals(66, (scoreModule.scoreData.measures[1].items[0] as ScoreItem.Note).pitch.midiPitch)
        assertEquals(1, layout.measureAt(layout.placedMeasures[1].barlineX - 5f))

        // Serialization round-trip preserves per-measure key signature and exact note pitches
        graph.addModule(scoreModule, Offset.Zero)
        val json = GraphSerializer.serialize(graph, graph.modules)
        val reloaded = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertEquals(KeySignature.D_MAJOR, reloaded.scoreData.keySignature)
        assertEquals(null, reloaded.scoreData.measures[0].keySignature)
        assertEquals(KeySignature.C_MAJOR, reloaded.scoreData.measures[1].keySignature)
        assertEquals(66, (reloaded.scoreData.measures[1].items[0] as ScoreItem.Note).pitch.midiPitch)

        // Undo restores Measure 2 back to null key signature (inheriting D major -> F#4 has no inline accidental)
        cmd.undo()
        assertEquals(null, scoreModule.scoreData.measures[1].keySignature)
        val undoneLayout = ScoreRenderer.computeLayout(scoreModule.scoreData, 10f, 600f) { _, fs -> fs * 0.35f }
        assertEquals(null, undoneLayout.placedMeasures[1].items[0].accidental)
    }

    @Test
    fun testChordEditingStackingDeleteAccidentalStaccatoAndPitchDrag() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val initial = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(e4, NoteDuration.QUARTER))))
        val slot = ScoreItemLocation(0, 0)

        // Stack C4 onto E4 -> becomes Chord([C4, E4]) and tracks chordNoteIdx = 0
        val (withTwo, c4Loc) = ScoreEditing.addPitchToSlot(initial, slot, c4)
        assertEquals(ScoreItemLocation(0, 0, 0), c4Loc)
        val twoChord = withTwo[0].items[0] as ScoreItem.Chord
        assertEquals(listOf(c4, e4), twoChord.pitches)

        // Stack G4 -> becomes Chord([C4, E4, G4]), G4 at chordNoteIdx = 2
        val (withThree, g4Loc) = ScoreEditing.addPitchToSlot(withTwo, slot, g4)
        assertEquals(ScoreItemLocation(0, 0, 2), g4Loc)
        assertEquals(listOf(c4, e4, g4), (withThree[0].items[0] as ScoreItem.Chord).pitches)

        // Apply flat accidental to only the middle notehead (E4 -> Eb4)
        val withEb = ScoreEditing.setAccidentalOnSelection(withThree, setOf(ScoreItemLocation(0, 0, 1)), -1)
        assertEquals(
            listOf(c4, SpelledPitch(NoteLetter.E, -1, 4), g4),
            (withEb[0].items[0] as ScoreItem.Chord).pitches,
        )

        // Drag bottom note (C4) above G4 -> re-sorts and tracks updated chordNoteIdx = 2
        val b4 = SpelledPitch(NoteLetter.B, 0, 4)
        val (afterDrag, trackedLoc) = ScoreEditing.setNotePitchAndTrackLocation(withEb, ScoreItemLocation(0, 0, 0), b4)
        assertEquals(ScoreItemLocation(0, 0, 2), trackedLoc)
        assertEquals(b4, ScoreEditing.pitchAt(afterDrag, trackedLoc))

        // Toggle staccato on chord
        val withStacc = ScoreEditing.toggleStaccatoOnSelection(afterDrag, setOf(trackedLoc))
        assertTrue((withStacc[0].items[0] as ScoreItem.Chord).isStaccato)

        // Delete 1 notehead of 3 -> 2-note Chord; delete 1 of 2 -> unwraps back to ScoreItem.Note
        val twoRemaining = ScoreEditing.deleteItems(withThree, setOf(ScoreItemLocation(0, 0, 1)))
        val remainingChord = twoRemaining[0].items[0] as ScoreItem.Chord
        assertEquals(listOf(c4, g4), remainingChord.pitches)

        val unwrapped = ScoreEditing.deleteItems(twoRemaining, setOf(ScoreItemLocation(0, 0, 0)))
        val singleNote = unwrapped[0].items[0] as ScoreItem.Note
        assertEquals(g4, singleNote.pitch)

        // Deleting whole chord (chordNoteIdx == null) removes the entire item
        val cleared = ScoreEditing.deleteItems(withThree, setOf(slot))
        assertTrue(cleared[0].items.isEmpty())
    }

    @Test
    fun testChordRecognitionAcrossKeysAndQualities() {
        val cSharpMinor7 = listOf(
            SpelledPitch(NoteLetter.C, 1, 4),
            SpelledPitch(NoteLetter.E, 0, 4),
            SpelledPitch(NoteLetter.G, 1, 4),
            SpelledPitch(NoteLetter.B, 0, 4),
        )
        assertEquals("C#m7", ScoreChordAnalyzer.recognizeChordName(cSharpMinor7, KeySignature.D_MAJOR))
        assertEquals("C#m7", ScoreChordAnalyzer.recognizeChordName(cSharpMinor7, KeySignature.A_MAJOR))

        val dFlatMinor7 = listOf(
            SpelledPitch(NoteLetter.D, -1, 4),
            SpelledPitch(NoteLetter.F, -1, 4),
            SpelledPitch(NoteLetter.A, -1, 4),
            SpelledPitch(NoteLetter.C, -1, 5),
        )
        assertEquals("Dbm7", ScoreChordAnalyzer.recognizeChordName(dFlatMinor7, KeySignature.D_FLAT))

        assertEquals(
            "C",
            ScoreChordAnalyzer.recognizeChordName(
                listOf(SpelledPitch(NoteLetter.C, 0, 4), SpelledPitch(NoteLetter.E, 0, 4), SpelledPitch(NoteLetter.G, 0, 4)),
                KeySignature.C_MAJOR,
            ),
        )
        assertEquals(
            "C/E",
            ScoreChordAnalyzer.recognizeChordName(
                listOf(SpelledPitch(NoteLetter.E, 0, 4), SpelledPitch(NoteLetter.G, 0, 4), SpelledPitch(NoteLetter.C, 0, 5)),
                KeySignature.C_MAJOR,
            ),
        )
        assertEquals(
            "Fmaj7",
            ScoreChordAnalyzer.recognizeChordName(
                listOf(
                    SpelledPitch(NoteLetter.F, 0, 4),
                    SpelledPitch(NoteLetter.A, 0, 4),
                    SpelledPitch(NoteLetter.C, 0, 5),
                    SpelledPitch(NoteLetter.E, 0, 5),
                ),
                KeySignature.C_MAJOR,
            ),
        )
        assertEquals(
            "G7",
            ScoreChordAnalyzer.recognizeChordName(
                listOf(
                    SpelledPitch(NoteLetter.G, 0, 3),
                    SpelledPitch(NoteLetter.B, 0, 3),
                    SpelledPitch(NoteLetter.D, 0, 4),
                    SpelledPitch(NoteLetter.F, 0, 4),
                ),
                KeySignature.C_MAJOR,
            ),
        )
        assertEquals(
            "Bm7b5",
            ScoreChordAnalyzer.recognizeChordName(
                listOf(
                    SpelledPitch(NoteLetter.B, 0, 3),
                    SpelledPitch(NoteLetter.D, 0, 4),
                    SpelledPitch(NoteLetter.F, 0, 4),
                    SpelledPitch(NoteLetter.A, 0, 4),
                ),
                KeySignature.C_MAJOR,
            ),
        )
        assertEquals(
            "Dsus4",
            ScoreChordAnalyzer.recognizeChordName(
                listOf(SpelledPitch(NoteLetter.D, 0, 4), SpelledPitch(NoteLetter.G, 0, 4), SpelledPitch(NoteLetter.A, 0, 4)),
                KeySignature.C_MAJOR,
            ),
        )
        assertEquals(null, ScoreChordAnalyzer.recognizeChordName(listOf(SpelledPitch(NoteLetter.C, 0, 4)), KeySignature.C_MAJOR))
    }

    @Test
    fun testChordEngravingSecondsStaggeredAccidentalsAndHitTesting() {
        val clusterChord = ScoreItem.Chord(
            pitches = listOf(
                SpelledPitch(NoteLetter.C, 1, 4), // step -2
                SpelledPitch(NoteLetter.D, 1, 4), // step -1 (second above C#4)
                SpelledPitch(NoteLetter.E, 1, 4), // step 0 (second above D#4)
            ),
            duration = NoteDuration.QUARTER,
        )
        val score = ScoreData().apply {
            keySignature = KeySignature.C_MAJOR
            replaceMeasures(listOf(ScoreMeasure(1, listOf(clusterChord))))
        }
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 500f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            selectedLocations = setOf(ScoreItemLocation(0, 0, 1)),
        )
        val placed = layout.placedMeasures[0].items[0]
        assertEquals(3, placed.chordNotes.size)
        // Low chord (steps -2..0) -> stems go UP; second D#4 is offset right of C#4, E#4 aligns back to stem column
        assertFalse(placed.stemDown)
        assertTrue(placed.chordNotes[1].headX > placed.chordNotes[0].headX)
        assertEquals(placed.chordNotes[0].headX, placed.chordNotes[2].headX)
        // Adjacent sharps stagger into distinct horizontal accidental columns
        val accXs = placed.chordNotes.mapNotNull { it.accX }.distinct()
        assertTrue(accXs.size >= 2)

        // itemColumnAt aligns anywhere over the notehead column, and chordNoteAt hits individual noteheads
        assertEquals(placed, layout.itemColumnAt(placed.x + 2f))
        val midCn = placed.chordNotes[1]
        assertEquals(midCn, layout.chordNoteAt(placed, midCn.headX + 3f, midCn.y))
    }

    @Test
    fun testChordPlaybackVoiceTagsAndSerializationRoundTrip() {
        val chord = ScoreItem.Chord(
            pitches = listOf(
                SpelledPitch(NoteLetter.C, 1, 4),
                SpelledPitch(NoteLetter.E, 0, 4),
                SpelledPitch(NoteLetter.G, 1, 4),
                SpelledPitch(NoteLetter.B, 0, 4),
            ),
            duration = NoteDuration.HALF,
            isDotted = true,
            amplitude = 0.72,
            isStaccato = true,
        )
        val engine = TestEngine()
        val scoreModule = ScoreModule()
        scoreModule.setEngine(engine)
        scoreModule.scoreData.replaceMeasures(listOf(ScoreMeasure(1, listOf(chord))))

        val receiver = VoiceReceiver()
        scoreModule.output.connect(receiver.inJack)
        scoreModule.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))

        for ((_, action) in engine.scheduled) {
            action()
        }

        assertEquals(4, receiver.received.size)
        assertEquals(setOf(61.0, 64.0, 68.0, 71.0), receiver.received.map { it.value }.toSet())
        assertEquals(4, receiver.received.map { it.tag }.distinct().size)
        assertTrue(receiver.received.all { it.opcode == Message.VoiceOpcode.NOTE })

        val graph = GraphState()
        graph.addModule(scoreModule, Offset.Zero)
        val json = GraphSerializer.serialize(graph, graph.modules)
        val reloaded = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        val reloadedChord = reloaded.scoreData.measures[0].items[0] as ScoreItem.Chord
        assertEquals(chord, reloadedChord)
    }

    @Test
    fun testPlaybackHonorsEmptyAndIncompleteMeasuresPerTimeSignature() {
        val engine = TestEngine()
        val scoreModule = ScoreModule()
        scoreModule.setEngine(engine)
        // 4/4 at default 120 BPM => 1 quarter = 0.5s, 1 measure = 2.0s.
        scoreModule.scoreData.timeSignature = TimeSignature.COMMON_TIME
        scoreModule.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(1, emptyList()),
                ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.WHOLE))),
                ScoreMeasure(3, emptyList()),
                ScoreMeasure(4, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.WHOLE))),
            )
        )

        val receiver = VoiceReceiver()
        scoreModule.output.connect(receiver.inJack)
        scoreModule.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))

        // Step through scheduled events in time order and verify activeItemIndex clears during empty M3.
        val sorted = engine.scheduled.sortedBy { it.first.nanoseconds }
        var sawClearedHighlightAtM3Start = false
        for ((time, action) in sorted) {
            action()
            if (time.nanoseconds == 4_000_000_000L && scoreModule.activeItemIndex == null) {
                sawClearedHighlightAtM3Start = true
            }
        }

        assertTrue(sawClearedHighlightAtM3Start, "activeItemIndex should clear when entering empty measure 3")
        assertEquals(2, receiver.received.size)
        assertEquals(2_000_000_000L, receiver.received[0].time.nanoseconds)
        assertEquals(6_000_000_000L, receiver.received[1].time.nanoseconds)
        assertEquals(8_000_000_001L, sorted.last().first.nanoseconds)
        assertFalse(scoreModule.isPlaying)
    }

    @Test
    fun testPlaybackPadsIncompleteMeasuresAndTrailingEmptyMeasuresInThreeFour() {
        val engine = TestEngine()
        val scoreModule = ScoreModule()
        scoreModule.setEngine(engine)
        // 3/4 at default 120 BPM => 1 quarter = 0.5s, 1 measure = 1.5s.
        scoreModule.scoreData.timeSignature = TimeSignature.THREE_FOUR
        scoreModule.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.QUARTER))),
                ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.HALF))),
                ScoreMeasure(3, emptyList()),
            )
        )

        val receiver = VoiceReceiver()
        scoreModule.output.connect(receiver.inJack)
        scoreModule.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))

        val sorted = engine.scheduled.sortedBy { it.first.nanoseconds }
        var clearedAtImplicitRestInM1 = false
        for ((time, action) in sorted) {
            action()
            if (time.nanoseconds == 500_000_000L) {
                clearedAtImplicitRestInM1 = scoreModule.activeItemIndex == null
            }
        }

        assertTrue(clearedAtImplicitRestInM1, "activeItemIndex should clear during missing rests in incomplete M1")
        assertEquals(2, receiver.received.size)
        assertEquals(0L, receiver.received[0].time.nanoseconds)
        assertEquals(1_500_000_000L, receiver.received[1].time.nanoseconds)
        assertEquals(4_500_000_001L, sorted.last().first.nanoseconds)
        assertNull(scoreModule.activeItemIndex)
        assertFalse(scoreModule.isPlaying)
    }

    @Test
    fun testPlaybackDoesNotRewindOverfullMeasures() {
        val engine = TestEngine()
        val scoreModule = ScoreModule()
        scoreModule.setEngine(engine)
        // 4/4 at default 120 BPM => 5 quarter notes in M1 = 2.5s; M2 should start at 2.5s.
        scoreModule.scoreData.timeSignature = TimeSignature.COMMON_TIME
        scoreModule.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    1,
                    List(5) { ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER) },
                ),
                ScoreMeasure(
                    2,
                    listOf(ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)),
                ),
            )
        )

        val receiver = VoiceReceiver()
        scoreModule.output.connect(receiver.inJack)
        scoreModule.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled.sortedBy { it.first.nanoseconds }) {
            action()
        }

        assertEquals(6, receiver.received.size)
        assertEquals(2_000_000_000L, receiver.received[4].time.nanoseconds)
        assertEquals(2_500_000_000L, receiver.received[5].time.nanoseconds)
    }

    @Test
    fun testTimeSignatureParseAndPaletteOptions() {
        assertEquals(TimeSignature(3, 4), TimeSignature.parse("3/4"))
        assertEquals(TimeSignature(12, 8), TimeSignature.parse(" 12 / 8 "))
        assertEquals(TimeSignature(2, 2), TimeSignature.parse("2/2"))
        assertEquals(TimeSignature(5, 4), TimeSignature.parse("5/4"))
        assertNull(TimeSignature.parse(""))
        assertNull(TimeSignature.parse("3"))
        assertNull(TimeSignature.parse("3/0"))
        assertNull(TimeSignature.parse("3/3"))
        assertNull(TimeSignature.parse("0/4"))
        assertNull(TimeSignature.parse("40/4"))
        assertNull(TimeSignature.parse("a/4"))
        assertNull(TimeSignature.parse("3/4/4"))

        val paletteTimeSigs = ScorePalettes.timeSignatureOptions.map { it.timeSignature }.toSet()
        assertTrue(TimeSignature(3, 4) in paletteTimeSigs)
        assertTrue(TimeSignature(2, 2) in paletteTimeSigs)
        assertTrue(TimeSignature(4, 4) in paletteTimeSigs)
        assertTrue(TimeSignature(6, 8) in paletteTimeSigs)
        assertTrue(TimeSignature(12, 8) in paletteTimeSigs)
    }

    @Test
    fun testMeasureTimeSignatureEditingUndoRedoAndRemovalRules() {
        val score = ScoreData()
        score.timeSignature = TimeSignature.COMMON_TIME
        val m1 = ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.WHOLE)))
        val m2 = ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.HALF)))
        val m3 = ScoreMeasure(3, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)))
        score.replaceMeasures(listOf(m1, m2, m3))

        // Multi-measure selection sets only the first selected measure (measure index 1).
        val (init1, measures1) = ScoreEditing.setTimeSignatureOnMeasures(
            initialTime = score.timeSignature,
            measures = score.measures,
            selectedMeasureIndices = setOf(1, 2),
            newTime = TimeSignature.THREE_FOUR,
        )
        assertEquals(TimeSignature.COMMON_TIME, init1)
        assertEquals(TimeSignature.THREE_FOUR, measures1[1].timeSignature)
        assertNull(measures1[2].timeSignature)
        assertEquals(TimeSignature.THREE_FOUR, ScoreEditing.effectiveTimeAtMeasure(measures1, init1, 2))

        val cmd = SetScoreTimeSignatureCommand(
            scoreData = score,
            beforeInitialTime = score.timeSignature,
            beforeMeasures = score.measures.toList(),
            afterInitialTime = init1,
            afterMeasures = measures1,
        )
        cmd.execute()
        assertEquals(TimeSignature.THREE_FOUR, score.measures[1].timeSignature)
        cmd.undo()
        assertNull(score.measures[1].timeSignature)
        cmd.execute()

        // Removing time signature on measure 0 is disallowed (no-op).
        val (afterClearM0Init, afterClearM0Measures) = ScoreEditing.setTimeSignatureOnMeasures(
            initialTime = score.timeSignature,
            measures = score.measures,
            selectedMeasureIndices = setOf(0),
            newTime = null,
        )
        assertEquals(TimeSignature.COMMON_TIME, afterClearM0Init)
        assertEquals(score.measures.toList(), afterClearM0Measures)

        // Removing time signature on measure 1 clears it.
        val (_, afterClearM1Measures) = ScoreEditing.setTimeSignatureOnMeasures(
            initialTime = score.timeSignature,
            measures = score.measures,
            selectedMeasureIndices = setOf(1),
            newTime = null,
        )
        assertNull(afterClearM1Measures[1].timeSignature)
        assertEquals(TimeSignature.COMMON_TIME, ScoreEditing.effectiveTimeAtMeasure(afterClearM1Measures, score.timeSignature, 2))
    }

    @Test
    fun testMidScoreTimeSignatureRenderingHitTestingPlaybackAndSerialization() {
        val scoreModule = ScoreModule()
        scoreModule.scoreData.timeSignature = TimeSignature.COMMON_TIME // 4/4 (960 ticks)
        scoreModule.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.HALF)), // 480 ticks (incomplete 4/4)
                ),
                ScoreMeasure(
                    number = 2,
                    timeSignature = TimeSignature.TWELVE_EIGHT, // 1440 ticks
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.WHOLE), // 960 ticks
                        ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.HALF), // +480 = 1440 (not overflow in 12/8!)
                    ),
                ),
                ScoreMeasure(
                    number = 3,
                    timeSignature = TimeSignature.THREE_FOUR, // 720 ticks
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.HALF), // overflows 3/4
                    ),
                ),
            )
        )

        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = scoreModule.scoreData,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )

        assertEquals(3, layout.placedTimeSignatures.size)
        assertEquals(0, layout.placedTimeSignatures[0].measureIdx)
        assertEquals(TimeSignature.COMMON_TIME, layout.placedTimeSignatures[0].timeSignature)
        assertEquals(1, layout.placedTimeSignatures[1].measureIdx)
        assertEquals(TimeSignature.TWELVE_EIGHT, layout.placedTimeSignatures[1].timeSignature)
        assertEquals(2, layout.placedTimeSignatures[2].measureIdx)
        assertEquals(TimeSignature.THREE_FOUR, layout.placedTimeSignatures[2].timeSignature)

        // Hit testing each placed time signature finds the matching measureIdx.
        for (placedTs in layout.placedTimeSignatures) {
            val hit = layout.timeSignatureAt(placedTs.x + placedTs.width * 0.5f, (placedTs.topY + placedTs.bottomY) * 0.5f)
            assertEquals(placedTs, hit)
        }

        // Per-measure capacity ticks & overflow check
        assertEquals(960, layout.placedMeasures[0].capacityTicks)
        assertEquals(1440, layout.placedMeasures[1].capacityTicks)
        assertFalse(layout.placedMeasures[1].items[1].isOverflow, "Half note completing 12/8 should not overflow")
        assertEquals(720, layout.placedMeasures[2].capacityTicks)
        assertTrue(layout.placedMeasures[2].items[1].isOverflow, "Second half note in 3/4 should overflow")

        // Playback honors M1 (4/4 = 2.0s), M2 (12/8 = 3.0s), M3 (3/4 overfull 2 halfs = 2.0s)
        val engine = TestEngine()
        scoreModule.setEngine(engine)
        val receiver = VoiceReceiver()
        scoreModule.output.connect(receiver.inJack)
        scoreModule.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled.sortedBy { it.first.nanoseconds }) {
            action()
        }
        assertEquals(5, receiver.received.size)
        assertEquals(0L, receiver.received[0].time.nanoseconds)
        assertEquals(2_000_000_000L, receiver.received[1].time.nanoseconds) // M2 starts after 4/4 (2.0s)
        assertEquals(4_000_000_000L, receiver.received[2].time.nanoseconds) // + whole note (2.0s)
        assertEquals(5_000_000_000L, receiver.received[3].time.nanoseconds) // M3 starts after 12/8 (3.0s from M2 start)

        // Serialization round-trip preserves mid-score time signatures including 12/8
        val graph = GraphState()
        graph.addModule(scoreModule, Offset.Zero)
        val json = GraphSerializer.serialize(graph, graph.modules)
        val reloaded = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertEquals(TimeSignature.COMMON_TIME, reloaded.scoreData.timeSignature)
        assertNull(reloaded.scoreData.measures[0].timeSignature)
        assertEquals(TimeSignature.TWELVE_EIGHT, reloaded.scoreData.measures[1].timeSignature)
        assertEquals(TimeSignature.THREE_FOUR, reloaded.scoreData.measures[2].timeSignature)
    }

    @Test
    fun testStepRecordHonorsActiveMeasureTimeSignature() {
        val scoreModule = ScoreModule()
        scoreModule.scoreData.timeSignature = TimeSignature.COMMON_TIME
        // M2 is 3/8 (360 ticks); two quarter notes (480 ticks >= 360) should advance cursor to M3
        scoreModule.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(1, emptyList()),
                ScoreMeasure(2, emptyList(), timeSignature = TimeSignature.THREE_EIGHT),
            )
        )
        scoreModule.stepRecordDuration = NoteDuration.QUARTER
        scoreModule.setStepRecordingEnabled(true)
        scoreModule.placeCursor(ScoreCursorPosition(measureIdx = 1, itemIdx = 0))

        scoreModule.input.receive(
            Message.VoiceMessage(ScoreTime(0L), Message.VoiceOpcode.NOTE_ON, tag = 0, value = 60.0, extra = 0.8)
        )
        assertEquals(ScoreCursorPosition(measureIdx = 1, itemIdx = 1), scoreModule.cursorPosition)
        scoreModule.input.receive(
            Message.VoiceMessage(ScoreTime(1L), Message.VoiceOpcode.NOTE_ON, tag = 1, value = 62.0, extra = 0.8)
        )
        assertEquals(ScoreCursorPosition(measureIdx = 2, itemIdx = 0), scoreModule.cursorPosition)
        assertEquals(3, scoreModule.scoreData.measures.size)
    }

    @Test
    fun testSetDurationOnSelectionWithAndWithoutDots() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val measures = listOf(
            ScoreMeasure(
                1,
                listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER, amplitude = 0.75, isStaccato = true),
                    ScoreItem.Chord(listOf(c4, e4, g4), NoteDuration.QUARTER, amplitude = 0.9, isStaccato = false),
                    ScoreItem.Rest(NoteDuration.QUARTER),
                    ScoreItem.Note(g4, NoteDuration.WHOLE), // unselected
                ),
            ),
        )
        // Select note, middle note of chord, and rest -> set dotted 8th (180 ticks)
        val sel = setOf(
            ScoreItemLocation(0, 0),
            ScoreItemLocation(0, 1, chordNoteIdx = 1),
            ScoreItemLocation(0, 2),
        )
        val dottedEighths = ScoreEditing.setDurationOnSelection(measures, sel, NoteDuration.EIGHTH, isDotted = true)
        val m1 = dottedEighths[0].items
        val n0 = m1[0] as ScoreItem.Note
        assertEquals(NoteDuration.EIGHTH, n0.duration)
        assertTrue(n0.isDotted)
        assertEquals(180, n0.totalTicks)
        assertEquals(0.75, n0.amplitude)
        assertTrue(n0.isStaccato)

        val ch1 = m1[1] as ScoreItem.Chord
        assertEquals(NoteDuration.EIGHTH, ch1.duration)
        assertTrue(ch1.isDotted)
        assertEquals(180, ch1.totalTicks)
        assertEquals(listOf(c4, e4, g4), ch1.pitches)

        val r2 = m1[2] as ScoreItem.Rest
        assertEquals(NoteDuration.EIGHTH, r2.duration)
        assertTrue(r2.isDotted)
        assertEquals(180, r2.totalTicks)

        val unselected = m1[3] as ScoreItem.Note
        assertEquals(NoteDuration.WHOLE, unselected.duration)
        assertFalse(unselected.isDotted)

        // Dotted 32nd (30 + 15 = 45 ticks)
        val dotted32nds = ScoreEditing.setDurationOnSelection(
            dottedEighths,
            setOf(ScoreItemLocation(0, 0)),
            NoteDuration.THIRTY_SECOND,
            isDotted = true,
        )
        assertEquals(45, dotted32nds[0].items[0].totalTicks)

        // Change to undotted half note, then verify idempotent no-op returns exact input list
        val undottedHalf = ScoreEditing.setDurationOnSelection(dottedEighths, sel, NoteDuration.HALF, isDotted = false)
        assertEquals(NoteDuration.HALF, undottedHalf[0].items[0].duration)
        assertFalse(undottedHalf[0].items[0].isDotted)
        assertEquals(480, undottedHalf[0].items[0].totalTicks)
        assertTrue(undottedHalf === ScoreEditing.setDurationOnSelection(undottedHalf, sel, NoteDuration.HALF, isDotted = false))
    }

    @Test
    fun testStepRecordingDottedNotesAndMeasureAdvance() {
        val scoreModule = ScoreModule().apply { autoFillRests = false }
        scoreModule.scoreData.timeSignature = TimeSignature.THREE_FOUR // 720 ticks per measure
        scoreModule.scoreData.replaceMeasures(listOf(ScoreMeasure(1, emptyList())))
        scoreModule.stepRecordDuration = NoteDuration.HALF
        scoreModule.stepRecordDotted = true
        scoreModule.setStepRecordingEnabled(true)
        scoreModule.placeCursor(ScoreCursorPosition(measureIdx = 0, itemIdx = 0))

        // Dotted half (720 ticks) fills 3/4 measure completely -> cursor advances to M2
        scoreModule.input.receive(
            Message.VoiceMessage(ScoreTime(0L), Message.VoiceOpcode.NOTE_ON, tag = 0, value = 60.0, extra = 0.8)
        )
        val m1Note = scoreModule.scoreData.measures[0].items.single() as ScoreItem.Note
        assertEquals(NoteDuration.HALF, m1Note.duration)
        assertTrue(m1Note.isDotted)
        assertEquals(720, m1Note.totalTicks)
        assertEquals(ScoreCursorPosition(measureIdx = 1, itemIdx = 0), scoreModule.cursorPosition)

        // Turn off dot mode and record an undotted quarter
        scoreModule.stepRecordDotted = false
        scoreModule.stepRecordDuration = NoteDuration.QUARTER
        scoreModule.input.receive(
            Message.VoiceMessage(ScoreTime(1L), Message.VoiceOpcode.NOTE_ON, tag = 1, value = 64.0, extra = 0.8)
        )
        val m2Note = scoreModule.scoreData.measures[1].items.single() as ScoreItem.Note
        assertEquals(NoteDuration.QUARTER, m2Note.duration)
        assertFalse(m2Note.isDotted)
        assertEquals(240, m2Note.totalTicks)
    }

    @Test
    fun testGhostPreviewDottedNoteAndRestRendering() {
        val score = ScoreData().apply {
            replaceMeasures(listOf(ScoreMeasure(1, emptyList())))
        }
        val noteTarget = RecordingDrawTarget()
        ScoreRenderer.render(
            score = score,
            target = noteTarget,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            ghostPreview = ScoreGhostPreview(
                x = 120f,
                staffStep = 4,
                duration = NoteDuration.QUARTER,
                isRest = false,
                isDotted = true,
            ),
        )
        assertEquals(1, noteTarget.glyphs.count { it.glyph == SmuflGlyphs.AUGMENTATION_DOT })

        val restTarget = RecordingDrawTarget()
        ScoreRenderer.render(
            score = score,
            target = restTarget,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            ghostPreview = ScoreGhostPreview(
                x = 120f,
                staffStep = 4,
                duration = NoteDuration.EIGHTH,
                isRest = true,
                isDotted = true,
            ),
        )
        assertEquals(1, restTarget.glyphs.count { it.glyph == SmuflGlyphs.AUGMENTATION_DOT })
    }

    @Test
    fun testClefPitchMappingAndKeySignaturePositions() {
        // Bass clef: F3 is on 4th line from bottom (step 6), G2 is on bottom line (step 0)
        assertEquals(6, Clef.BASS.staffStepFromBottom(SpelledPitch(NoteLetter.F, 0, 3)))
        assertEquals(0, Clef.BASS.staffStepFromBottom(SpelledPitch(NoteLetter.G, 0, 2)))
        assertEquals(SpelledPitch(NoteLetter.F, 1, 3), Clef.BASS.pitchForStaffStep(6, KeySignature.G_MAJOR))

        // Alto clef: C4 sits on middle line (step 4)
        assertEquals(4, Clef.ALTO.staffStepFromBottom(SpelledPitch(NoteLetter.C, 0, 4)))
        assertEquals(SpelledPitch(NoteLetter.C, 0, 4), Clef.ALTO.pitchForStaffStep(4, KeySignature.C_MAJOR))

        // Tenor clef: C4 sits on 4th line from bottom (step 6)
        assertEquals(6, Clef.TENOR.staffStepFromBottom(SpelledPitch(NoteLetter.C, 0, 4)))
        assertEquals(SpelledPitch(NoteLetter.C, 0, 4), Clef.TENOR.pitchForStaffStep(6, KeySignature.C_MAJOR))

        // Octave clefs shift sounding octave while preserving line position
        assertEquals(2, Clef.TREBLE_8VB.staffStepFromBottom(SpelledPitch(NoteLetter.G, 0, 3)))
        assertEquals(2, Clef.TREBLE_8VA.staffStepFromBottom(SpelledPitch(NoteLetter.G, 0, 5)))
        assertEquals(6, Clef.BASS_8VB.staffStepFromBottom(SpelledPitch(NoteLetter.F, 0, 2)))
        assertEquals(6, Clef.BASS_8VA.staffStepFromBottom(SpelledPitch(NoteLetter.F, 0, 4)))

        // Per-clef key signature accidental staff positions
        assertEquals(listOf(6, 3), KeySignature.D_MAJOR.staffSteps(Clef.BASS))
        assertEquals(listOf(7, 4), KeySignature.D_MAJOR.staffSteps(Clef.ALTO))
        assertEquals(listOf(2, 6), KeySignature.D_MAJOR.staffSteps(Clef.TENOR))
        assertEquals(listOf(2, 5, 1), KeySignature.E_FLAT.staffSteps(Clef.BASS))
        assertEquals(listOf(3, 6, 2), KeySignature.E_FLAT.staffSteps(Clef.ALTO))
        assertEquals(listOf(5, 8, 4), KeySignature.E_FLAT.staffSteps(Clef.TENOR))
    }

    @Test
    fun testSetClefCommandUndoRedoAndIndexShifting() {
        val score = ScoreData()
        score.clef = Clef.TREBLE
        val c4Note = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.HALF)
        val e4Note = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.HALF)
        val g3Note = ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 3), NoteDuration.HALF)
        val m1 = ScoreMeasure(1, listOf(c4Note, e4Note))
        val m2 = ScoreMeasure(2, listOf(g3Note))
        score.replaceMeasures(listOf(m1, m2))

        // Set mid-measure clef at (0, 1)
        val (init1, measures1) = ScoreEditing.setClefChange(
            initialClef = score.clef,
            measures = score.measures,
            location = ScoreClefLocation(0, 1),
            newClef = Clef.ALTO,
        )
        assertEquals(Clef.TREBLE, init1)
        assertEquals(Clef.ALTO, measures1[0].clefChanges[1])
        assertEquals(Clef.TREBLE, ScoreEditing.effectiveClefAt(measures1, init1, 0, 0))
        assertEquals(Clef.ALTO, ScoreEditing.effectiveClefAt(measures1, init1, 0, 1))
        assertEquals(Clef.ALTO, ScoreEditing.effectiveClefAt(measures1, init1, 1, 0))

        val cmd = SetScoreClefCommand(
            scoreData = score,
            beforeInitialClef = score.clef,
            beforeMeasures = score.measures.toList(),
            afterInitialClef = init1,
            afterMeasures = measures1,
        )
        cmd.execute()
        assertEquals(Clef.ALTO, score.measures[0].clefChanges[1])
        // Sounding pitches remain unchanged across clef changes
        assertEquals(SpelledPitch(NoteLetter.E, 0, 4), (score.measures[0].items[1] as ScoreItem.Note).pitch)
        assertEquals(SpelledPitch(NoteLetter.G, 0, 3), (score.measures[1].items[0] as ScoreItem.Note).pitch)

        cmd.undo()
        assertTrue(score.measures[0].clefChanges.isEmpty())
        cmd.execute()

        // Inserting after the clef change keeps clefChanges at index 1; inserting before shifts to index 2
        val b4Note = ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.QUARTER)
        val (afterClefInsert, _) = ScoreEditing.insertItem(
            measures = score.measures,
            measureIdx = 0,
            itemIdx = 1,
            item = b4Note,
            shiftClefAtIndex = false,
        )
        assertEquals(Clef.ALTO, afterClefInsert[0].clefChanges[1])

        val (beforeClefInsert, _) = ScoreEditing.insertItem(
            measures = score.measures,
            measureIdx = 0,
            itemIdx = 1,
            item = b4Note,
            shiftClefAtIndex = true,
        )
        assertNull(beforeClefInsert[0].clefChanges[1])
        assertEquals(Clef.ALTO, beforeClefInsert[0].clefChanges[2])

        // Deleting item 0 shifts clef at index 2 down to index 1
        val afterDelete = ScoreEditing.deleteItems(
            measures = beforeClefInsert,
            selection = setOf(ScoreItemLocation(0, 0)),
        )
        assertNull(afterDelete[0].clefChanges[2])
        assertEquals(Clef.ALTO, afterDelete[0].clefChanges[1])

        // Opening clef removal is ignored (no-op)
        val (unchangedInit, _) = ScoreEditing.setClefChange(
            initialClef = Clef.TREBLE,
            measures = score.measures,
            location = ScoreClefLocation(0, 0),
            newClef = null,
        )
        assertEquals(Clef.TREBLE, unchangedInit)
    }

    @Test
    fun testBeatOneAndMidMeasureClefLayoutAndRendering() {
        val score = ScoreData()
        score.clef = Clef.TREBLE
        score.keySignature = KeySignature.C_MAJOR
        score.timeSignature = TimeSignature.COMMON_TIME
        score.replaceMeasures(
            listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.HALF),
                    ),
                    // Mid-measure change to Alto before item 1
                    clefChanges = mapOf(1 to Clef.ALTO),
                ),
                ScoreMeasure(
                    number = 2,
                    keySignature = KeySignature.G_MAJOR,
                    timeSignature = TimeSignature.THREE_FOUR,
                    // Beat-1 clef change to Bass coinciding with key & time change
                    clefChanges = mapOf(0 to Clef.BASS),
                    items = listOf(
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 3), NoteDuration.HALF),
                        ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.QUARTER),
                    ),
                ),
            )
        )

        val target = RecordingDrawTarget()
        val staffSpacePx = 10f
        val emPx = staffSpacePx * 4f
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = staffSpacePx,
            minWidthPx = 800f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )

        assertEquals(Clef.TREBLE, layout.openingClef)
        val pm1 = layout.placedMeasures[0]
        val pm2 = layout.placedMeasures[1]

        // Mid-measure clef change in M1: item 0 uses Treble (C4 = step -2), item 1 uses Alto (C4 = step 4)
        assertEquals(Clef.TREBLE, pm1.items[0].effectiveClef)
        assertEquals(-2, pm1.items[0].staffStep)
        assertEquals(Clef.ALTO, pm1.items[1].effectiveClef)
        assertEquals(4, pm1.items[1].staffStep)

        val midClef = pm1.clefChanges.single()
        assertEquals(ScoreClefLocation(0, 1), midClef.location)
        assertTrue(midClef.x > pm1.items[0].x)
        assertTrue(midClef.x < pm1.items[1].x)
        assertEquals(
            midClef,
            layout.clefChangeAt(midClef.x + midClef.width * 0.5f, (midClef.topY + midClef.bottomY) * 0.5f),
        )
        assertEquals(Clef.TREBLE, layout.effectiveClefAtX(pm1.items[0].x))
        assertEquals(Clef.ALTO, layout.effectiveClefAtX(pm1.items[1].x))

        // Beat-1 Bass clef for M2 sits immediately before M1's barline at ~72% emPx,
        // and precedes M2's key signature (rendered with Bass staff positions) and time signature.
        val barlineClef = pm1.barlineClefChange
        assertNotNull(barlineClef)
        assertEquals(ScoreClefLocation(1, 0), barlineClef.location)
        assertEquals(Clef.BASS, barlineClef.clef)
        assertTrue(barlineClef.x + barlineClef.width <= pm1.barlineX + 0.01f)
        assertEquals(Clef.BASS, pm2.activeOpeningClef)
        assertEquals(listOf(6 to Accidental.SHARP), pm2.keyChangeGlyphs)

        val midGlyph = target.glyphs.firstOrNull {
            it.glyph == SmuflGlyphs.C_CLEF && it.fontSizePx < emPx * 0.8f
        }
        assertNotNull(midGlyph)
        assertEquals(emPx * 0.72f, midGlyph.fontSizePx, 0.01f)

        val fClefGlyph = target.glyphs.firstOrNull { it.glyph == SmuflGlyphs.F_CLEF }
        assertNotNull(fClefGlyph)
        assertEquals(emPx * 0.72f, fClefGlyph.fontSizePx, 0.01f)

        val gSharpGlyph = target.glyphs.lastOrNull { it.glyph == SmuflGlyphs.ACCIDENTAL_SHARP }
        val timeNumGlyph = target.glyphs.lastOrNull { it.glyph == SmuflGlyphs.timeSigNumber(3) }
        assertNotNull(gSharpGlyph)
        assertNotNull(timeNumGlyph)
        assertTrue(fClefGlyph.x < pm1.barlineX)
        assertTrue(pm1.barlineX < gSharpGlyph.x)
        assertTrue(gSharpGlyph.x < timeNumGlyph.x)
    }

    @Test
    fun testClefSerializationRoundTrip() {
        val scoreModule = ScoreModule()
        scoreModule.scoreData.clef = Clef.TENOR
        scoreModule.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    number = 1,
                    items = listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.HALF)),
                    clefChanges = mapOf(1 to Clef.TREBLE_8VB),
                ),
                ScoreMeasure(
                    number = 2,
                    items = listOf(ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 3), NoteDuration.WHOLE)),
                    clefChanges = mapOf(0 to Clef.BASS_8VB),
                ),
            )
        )

        val graphState = GraphState()
        graphState.addModule(scoreModule, Offset.Zero)
        val json = GraphSerializer.serialize(graphState, graphState.modules)
        val loadedModule = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()

        assertEquals(Clef.TENOR, loadedModule.scoreData.clef)
        assertEquals(mapOf(1 to Clef.TREBLE_8VB), loadedModule.scoreData.measures[0].clefChanges)
        assertEquals(mapOf(0 to Clef.BASS_8VB), loadedModule.scoreData.measures[1].clefChanges)
    }

    @Test
    fun testResolveTargetClefLocationPrefersMidMeasureCursorOverMeasureHighlight() {
        // Clicking inside a measure in select mode sets both selectedMeasures and cursorPosition.
        // Mid-measure cursor must win so inserting a clef puts it at the cursor, not beat 1.
        assertEquals(
            ScoreClefLocation(5, 2),
            ScoreEditing.resolveTargetClefLocation(
                selectedClefChange = null,
                selectedLocations = emptySet(),
                cursorPosition = ScoreCursorPosition(5, 2),
                selectedMeasures = setOf(5),
            ),
        )

        // When cursor is cleared (e.g. rubber-band / Shift measure selection), use selected measure beat 1.
        assertEquals(
            ScoreClefLocation(4, 0),
            ScoreEditing.resolveTargetClefLocation(
                selectedClefChange = null,
                selectedLocations = emptySet(),
                cursorPosition = null,
                selectedMeasures = setOf(4, 5),
            ),
        )

        // Explicit note selection targets the first selected item.
        assertEquals(
            ScoreClefLocation(5, 1),
            ScoreEditing.resolveTargetClefLocation(
                selectedClefChange = null,
                selectedLocations = setOf(ScoreItemLocation(5, 3), ScoreItemLocation(5, 1, chordNoteIdx = 0)),
                cursorPosition = ScoreCursorPosition(5, 2),
                selectedMeasures = emptySet(),
            ),
        )

        // Explicit selected clef change takes top priority; empty state falls back to opening (0, 0).
        assertEquals(
            ScoreClefLocation(2, 0),
            ScoreEditing.resolveTargetClefLocation(
                selectedClefChange = ScoreClefLocation(2, 0),
                selectedLocations = setOf(ScoreItemLocation(5, 1)),
                cursorPosition = ScoreCursorPosition(5, 2),
                selectedMeasures = setOf(5),
            ),
        )
        assertEquals(
            ScoreClefLocation(0, 0),
            ScoreEditing.resolveTargetClefLocation(
                selectedClefChange = null,
                selectedLocations = emptySet(),
                cursorPosition = null,
                selectedMeasures = emptySet(),
            ),
        )
    }

    @Test
    fun testCopyAndPasteSelectedNotesAcrossMeasuresAndTimeSignatures() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val d4 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val e4g4 = ScoreItem.Chord(
            pitches = listOf(SpelledPitch(NoteLetter.E, 0, 4), SpelledPitch(NoteLetter.G, 0, 4)),
            duration = NoteDuration.QUARTER,
            isDotted = true,
            isStaccato = true,
        )
        val qRest = ScoreItem.Rest(NoteDuration.QUARTER)
        val sourceMeasures = listOf(
            ScoreMeasure(1, listOf(c4, d4, e4g4, qRest)),
        )

        // Copying out-of-order selection sorts by measure/item and extracts single chord notehead when specified
        val partialCopied = ScoreEditing.copySelectedItems(
            measures = sourceMeasures,
            selection = setOf(
                ScoreItemLocation(0, 2, chordNoteIdx = 1), // G4 only
                ScoreItemLocation(0, 0), // C4
            ),
        )
        assertEquals(2, partialCopied.size)
        assertEquals(c4, partialCopied[0])
        val extractedG4 = partialCopied[1] as ScoreItem.Note
        assertEquals(SpelledPitch(NoteLetter.G, 0, 4), extractedG4.pitch)
        assertEquals(NoteDuration.QUARTER, extractedG4.duration)
        assertTrue(extractedG4.isDotted)
        assertTrue(extractedG4.isStaccato)

        // Full bar copy preserves chord, note, rest, and order
        val fullCopied = ScoreEditing.copySelectedItems(
            measures = sourceMeasures,
            selection = setOf(
                ScoreItemLocation(0, 0),
                ScoreItemLocation(0, 1),
                ScoreItemLocation(0, 2),
                ScoreItemLocation(0, 3),
            ),
        )
        assertEquals(listOf(c4, d4, e4g4, qRest), fullCopied)

        // Pasting 4 quarter-or-longer items into an all-empty 2-bar 3/4 score starts at M1 beat 1 and wraps at barline
        val emptyThreeFour = listOf(
            ScoreMeasure(1, emptyList()),
            ScoreMeasure(2, emptyList()),
        )
        val wrapResult = ScoreEditing.pasteItems(
            measures = emptyThreeFour,
            itemsToPaste = listOf(c4, d4, c4, d4),
            initialTimeSignature = TimeSignature.THREE_FOUR,
        )
        assertNotNull(wrapResult)
        assertEquals(2, wrapResult.measures.size)
        assertEquals(listOf(c4, d4, c4), wrapResult.measures[0].items)
        assertEquals(listOf(d4), wrapResult.measures[1].items)
        assertEquals(
            setOf(
                ScoreItemLocation(0, 0),
                ScoreItemLocation(0, 1),
                ScoreItemLocation(0, 2),
                ScoreItemLocation(1, 0),
            ),
            wrapResult.pastedLocations,
        )

        // Pasting at a mid-measure cursor inserts right at the cursor
        val midCursorResult = ScoreEditing.pasteItems(
            measures = listOf(ScoreMeasure(1, listOf(c4, qRest))),
            itemsToPaste = listOf(d4),
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 1),
            selectedMeasures = setOf(0),
        )
        assertNotNull(midCursorResult)
        assertEquals(listOf(c4, d4, qRest), midCursorResult.measures[0].items)
        assertEquals(setOf(ScoreItemLocation(0, 1)), midCursorResult.pastedLocations)
        assertEquals(ScoreCursorPosition(0, 2), midCursorResult.nextCursor)
    }

    @Test
    fun testCopyAndPasteBetweenScoreModulesViaEditActions() {
        ScoreClipboard.clear()
        try {
            val engine = TestEngine()
            val score1 = ScoreModule().apply { setEngine(engine) }
            val score2 = ScoreModule().apply { setEngine(engine) }
            val fSharp4 = ScoreItem.Note(SpelledPitch(NoteLetter.F, 1, 4), NoteDuration.QUARTER)
            val a4 = ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.HALF)
            score1.scoreData.replaceMeasures(listOf(ScoreMeasure(1, listOf(fSharp4, a4))))
            score2.scoreData.replaceMeasures(listOf(ScoreMeasure(1, emptyList())))

            var sel1 = setOf(ScoreItemLocation(0, 0), ScoreItemLocation(0, 1))
            var sel2 = emptySet<ScoreItemLocation>()
            score2.placeCursor(ScoreCursorPosition(0, 0))

            fun makeHandler(mod: ScoreModule, getSel: () -> Set<ScoreItemLocation>, setSel: (Set<ScoreItemLocation>) -> Unit) =
                object : EditActionHandler {
                    override fun canCopy() = getSel().isNotEmpty()
                    override fun canCut() = getSel().isNotEmpty()
                    override fun canPaste() = ScoreClipboard.items.isNotEmpty()
                    override fun canDelete() = getSel().isNotEmpty()
                    override fun copy() {
                        val copied = ScoreEditing.copySelectedEntries(mod.scoreData.measures, getSel())
                        if (copied.isNotEmpty()) ScoreClipboard.copy(copied)
                    }
                    override fun cut() {
                        copy()
                        delete()
                    }
                    override fun paste() {
                        val clip = ScoreClipboard.items
                        val before = mod.scoreData.measures.toList()
                        val res = ScoreEditing.pasteClipboardItems(
                            measures = before,
                            entries = clip,
                            initialTimeSignature = mod.scoreData.timeSignature,
                            selectedLocations = getSel(),
                            cursorPosition = mod.cursorPosition,
                        ) ?: return
                        mod.placeCursor(null)
                        setSel(res.pastedLocations)
                        mod.scoreData.replaceMeasures(res.measures)
                    }
                    override fun delete() {
                        val after = ScoreEditing.deleteItems(mod.scoreData.measures, getSel())
                        setSel(emptySet())
                        mod.scoreData.replaceMeasures(after)
                    }
                }

            val mdi = MdiState()
            val win1 = MdiWindowInfo(id = score1, title = "Score 1", content = {}).also {
                it.editHandler = makeHandler(score1, { sel1 }, { sel1 = it })
            }
            val win2 = MdiWindowInfo(id = score2, title = "Score 2", content = {}).also {
                it.editHandler = makeHandler(score2, { sel2 }, { sel2 = it })
            }
            mdi.open(win1)
            mdi.open(win2)

            // Copy from Score 1 (frontmost)
            mdi.bringToFront(win1)
            assertTrue(EditActions.canCopy(mdi))
            assertFalse(EditActions.canPaste(mdi))
            EditActions.copy(mdi)

            // Switch to Score 2 and paste
            mdi.bringToFront(win2)
            assertTrue(EditActions.canPaste(mdi))
            EditActions.paste(mdi)
            assertEquals(listOf(fSharp4, a4), score2.scoreData.measures[0].items)
            assertEquals(setOf(ScoreItemLocation(0, 0), ScoreItemLocation(0, 1)), sel2)
            // Score 1 stays intact
            assertEquals(listOf(fSharp4, a4), score1.scoreData.measures[0].items)

            // Cut from Score 2 removes items from Score 2 and updates clipboard
            sel2 = setOf(ScoreItemLocation(0, 1))
            EditActions.cut(mdi)
            assertEquals(listOf(fSharp4), score2.scoreData.measures[0].items)
            assertEquals(listOf(ScoreClipboardItem(a4, 0)), ScoreClipboard.items)
        } finally {
            ScoreClipboard.clear()
        }
    }

    @Test
    fun testCopyAndPastePreservesIncompleteMeasureTiming() {
        val q1 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val q2 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val q3 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val q4 = ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.QUARTER)

        // 4/4 staff: M1 empty, M2 has 2 quarters (missing half rest), M3 has 2 quarters (missing half rest), M4 empty
        val measures = listOf(
            ScoreMeasure(1, emptyList()),
            ScoreMeasure(2, listOf(q1, q2)),
            ScoreMeasure(3, listOf(q3, q4)),
            ScoreMeasure(4, emptyList()),
        )
        val entries = ScoreEditing.copySelectedEntries(
            measures = measures,
            selection = setOf(
                ScoreItemLocation(1, 0),
                ScoreItemLocation(1, 1),
                ScoreItemLocation(2, 0),
                ScoreItemLocation(2, 1),
            ),
        )
        assertEquals(
            listOf(
                ScoreClipboardItem(q1, 0),
                ScoreClipboardItem(q2, 0),
                ScoreClipboardItem(q3, 1),
                ScoreClipboardItem(q4, 1),
            ),
            entries,
        )

        // Pasting into M4 preserves separate incomplete measures M4 and M5 instead of collapsing into 1 bar
        val pasted = ScoreEditing.pasteClipboardItems(
            measures = measures,
            entries = entries,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(measureIdx = 3, itemIdx = 0),
        )
        assertNotNull(pasted)
        assertEquals(5, pasted.measures.size)
        assertEquals(listOf(q1, q2), pasted.measures[3].items)
        assertEquals(listOf(q3, q4), pasted.measures[4].items)

        // An empty skipped bar between selected notes (e.g. M2 and M4 with M3 empty) also preserves the 1-bar gap
        val gappedEntries = ScoreEditing.copySelectedEntries(
            measures = listOf(
                ScoreMeasure(1, emptyList()),
                ScoreMeasure(2, listOf(q1)),
                ScoreMeasure(3, emptyList()),
                ScoreMeasure(4, listOf(q2)),
            ),
            selection = setOf(ScoreItemLocation(1, 0), ScoreItemLocation(3, 0)),
        )
        val gappedPaste = ScoreEditing.pasteClipboardItems(
            measures = listOf(ScoreMeasure(1, emptyList())),
            entries = gappedEntries,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 0),
        )
        assertNotNull(gappedPaste)
        assertEquals(3, gappedPaste.measures.size)
        assertEquals(listOf(q1), gappedPaste.measures[0].items)
        assertTrue(gappedPaste.measures[1].items.isEmpty())
        assertEquals(listOf(q2), gappedPaste.measures[2].items)
    }

    @Test
    fun testCopyAndPasteTenMeasuresPreservesAllNotationTricks() {
        val dottedStaccato = ScoreItem.Note(
            pitch = SpelledPitch(NoteLetter.F, 1, 3),
            duration = NoteDuration.QUARTER,
            isDotted = true,
            isStaccato = true,
        )
        val chordItem = ScoreItem.Chord(
            pitches = listOf(SpelledPitch(NoteLetter.C, 0, 4), SpelledPitch(NoteLetter.E, 0, 4), SpelledPitch(NoteLetter.G, 0, 4)),
            duration = NoteDuration.QUARTER,
        )
        val bFlatNote = ScoreItem.Note(SpelledPitch(NoteLetter.B, -1, 3), NoteDuration.QUARTER, isDotted = true)
        val staccatoNote = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 3), NoteDuration.QUARTER, isStaccato = true)
        val eighthRest = ScoreItem.Rest(NoteDuration.EIGHTH)
        val sixEightDotted = ScoreItem.Note(SpelledPitch(NoteLetter.G, 1, 4), NoteDuration.QUARTER, isDotted = true)
        val accidentalNote = ScoreItem.Note(SpelledPitch(NoteLetter.D, 1, 4), NoteDuration.EIGHTH)

        // 10 measures exercising opening signatures, mid-score key/time/beat-1/mid-measure/trailing clefs,
        // chords, dots, staccato, accidentals, incomplete bars, and completely empty bars.
        val sourceInitialClef = Clef.BASS
        val sourceInitialKey = KeySignature.G_MAJOR
        val sourceInitialTime = TimeSignature(3, 4)
        val sourceMeasures = listOf(
            // M1: inherits BASS / G_MAJOR / 3_4
            ScoreMeasure(1, listOf(dottedStaccato, eighthRest)),
            // M2: completely empty bar
            ScoreMeasure(2, emptyList()),
            // M3: changes to F_MAJOR / 4_4 / TREBLE on beat 1, then ALTO mid-measure at item 2
            ScoreMeasure(
                number = 3,
                items = listOf(chordItem, bFlatNote, staccatoNote, eighthRest),
                keySignature = KeySignature.F_MAJOR,
                timeSignature = TimeSignature.COMMON_TIME,
                clefChanges = mapOf(0 to Clef.TREBLE, 2 to Clef.ALTO),
            ),
            // M4: inherits F_MAJOR / 4_4 / ALTO
            ScoreMeasure(4, listOf(chordItem)),
            // M5: inherits F_MAJOR / 4_4 / ALTO, trailing clef TENOR after item 2
            ScoreMeasure(5, listOf(staccatoNote, bFlatNote), clefChanges = mapOf(2 to Clef.TENOR)),
            // M6: inherits TENOR from M5 trailing clef, changes to 6/8 & E_MAJOR
            ScoreMeasure(
                number = 6,
                items = listOf(sixEightDotted, accidentalNote),
                keySignature = KeySignature.E_MAJOR,
                timeSignature = TimeSignature(6, 8),
            ),
            // M7: empty bar in E_MAJOR / 6_8 / TENOR
            ScoreMeasure(7, emptyList()),
            // M8: beat-1 BASS clef, single staccato note
            ScoreMeasure(8, listOf(staccatoNote), clefChanges = mapOf(0 to Clef.BASS)),
            // M9: mid-measure TREBLE clef at item 1, incomplete bar
            ScoreMeasure(9, listOf(dottedStaccato, accidentalNote), clefChanges = mapOf(1 to Clef.TREBLE)),
            // M10: chord + trailing ALTO clef
            ScoreMeasure(10, listOf(chordItem), clefChanges = mapOf(1 to Clef.ALTO)),
        )

        val clipContent = ScoreEditing.copySelectionContent(
            measures = sourceMeasures,
            initialClef = sourceInitialClef,
            initialKeySignature = sourceInitialKey,
            initialTimeSignature = sourceInitialTime,
            selectedLocations = setOf(ScoreItemLocation(0, 0), ScoreItemLocation(9, 0)),
            selectedMeasures = (0..9).toSet(),
        )
        assertTrue(clipContent is ScoreClipboardContent.MeasureRange)
        assertEquals(10, clipContent.measures.size)

        // Case A: Paste all 10 measures into a blank C-major 4/4 Treble score at M1
        val targetPaste = ScoreEditing.pasteClipboardContent(
            measures = listOf(ScoreMeasure(1, emptyList())),
            content = clipContent,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 0),
        )
        assertNotNull(targetPaste)
        val outClef = targetPaste.initialClef ?: Clef.TREBLE
        val outKey = targetPaste.initialKeySignature ?: KeySignature.C_MAJOR
        val outTime = targetPaste.initialTimeSignature ?: TimeSignature.COMMON_TIME
        assertEquals(sourceInitialClef, outClef)
        assertEquals(sourceInitialKey, outKey)
        assertEquals(sourceInitialTime, outTime)
        assertEquals(10, targetPaste.measures.size)
        for (i in 0 until 10) {
            assertEquals(sourceMeasures[i].items, targetPaste.measures[i].items, "items mismatch at M${i + 1}")
            assertEquals(
                ScoreEditing.effectiveKeyAtMeasure(sourceMeasures, sourceInitialKey, i),
                ScoreEditing.effectiveKeyAtMeasure(targetPaste.measures, outKey, i),
                "effective key mismatch at M${i + 1}",
            )
            assertEquals(
                ScoreEditing.effectiveTimeAtMeasure(sourceMeasures, sourceInitialTime, i),
                ScoreEditing.effectiveTimeAtMeasure(targetPaste.measures, outTime, i),
                "effective time mismatch at M${i + 1}",
            )
            assertEquals(
                ScoreEditing.effectiveClefAt(sourceMeasures, sourceInitialClef, i, 0),
                ScoreEditing.effectiveClefAt(targetPaste.measures, outClef, i, 0),
                "opening clef mismatch at M${i + 1}",
            )
            assertEquals(
                ScoreEditing.effectiveClefAt(sourceMeasures, sourceInitialClef, i, Int.MAX_VALUE),
                ScoreEditing.effectiveClefAt(targetPaste.measures, outClef, i, Int.MAX_VALUE),
                "ending clef mismatch at M${i + 1}",
            )
            assertEquals(
                sourceMeasures[i].clefChanges.filterKeys { it > 0 },
                targetPaste.measures[i].clefChanges.filterKeys { it > 0 },
                "mid-measure/trailing clefs mismatch at M${i + 1}",
            )
        }

        // Case B: Copy M4..M5 (which only inherited F_MAJOR / 4_4 / ALTO) and paste into M2 of a C_MAJOR / 4_4 / TREBLE score.
        // Pasted M2 must receive F_MAJOR and beat-1 ALTO clef, while omitting redundant 4/4 time signature.
        val inheritedRange = ScoreEditing.copySelectionContent(
            measures = sourceMeasures,
            initialClef = sourceInitialClef,
            initialKeySignature = sourceInitialKey,
            initialTimeSignature = sourceInitialTime,
            selectedMeasures = setOf(3, 4),
        )
        assertTrue(inheritedRange is ScoreClipboardContent.MeasureRange)
        val splicedPaste = ScoreEditing.pasteClipboardContent(
            measures = listOf(ScoreMeasure(1, listOf(chordItem)), ScoreMeasure(2, emptyList())),
            content = inheritedRange,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(1, 0),
        )
        assertNotNull(splicedPaste)
        assertEquals(3, splicedPaste.measures.size)
        assertEquals(KeySignature.F_MAJOR, splicedPaste.measures[1].keySignature)
        assertNull(splicedPaste.measures[1].timeSignature)
        assertEquals(Clef.ALTO, splicedPaste.measures[1].clefChanges[0])
        assertEquals(mapOf(2 to Clef.TENOR), splicedPaste.measures[2].clefChanges)
    }

    @Test
    fun testCopyAndPasteStandaloneClefAndMidMeasureItemClef() {
        val q1 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val q2 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val q3 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val measures = listOf(
            ScoreMeasure(1, listOf(q1)),
            ScoreMeasure(2, listOf(q1, q2, q3), clefChanges = mapOf(1 to Clef.ALTO)),
            ScoreMeasure(3, listOf(q1), clefChanges = mapOf(0 to Clef.TREBLE)),
        )

        // Standalone mid-measure clef selection copy -> ClefOnly
        val clefClip = ScoreEditing.copySelectionContent(
            measures = measures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedClefChange = ScoreClefLocation(1, 1),
        )
        assertEquals(ScoreClipboardContent.ClefOnly(Clef.ALTO), clefClip)
        ScoreClipboard.copyContent(clefClip)
        assertTrue(ScoreClipboard.isNotEmpty)
        val (clefAfter, updatedClefMeasures) = ScoreEditing.setClefChange(
            initialClef = Clef.TREBLE,
            measures = measures,
            location = ScoreClefLocation(2, 1),
            newClef = (ScoreClipboard.content as ScoreClipboardContent.ClefOnly).clef,
        )
        assertEquals(Clef.TREBLE, clefAfter)
        assertEquals(Clef.ALTO, updatedClefMeasures[2].clefChanges[1])

        // ItemSelection spanning a mid-measure clef captures clefBefore and re-applies it on paste
        val itemClip = ScoreEditing.copySelectionContent(
            measures = measures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(1, 0), ScoreItemLocation(1, 1)),
        )
        assertTrue(itemClip is ScoreClipboardContent.ItemSelection)
        assertEquals(
            listOf(
                ScoreClipboardItem(q1, 0, clefBefore = null),
                ScoreClipboardItem(q2, 0, clefBefore = Clef.ALTO),
            ),
            itemClip.items,
        )
        val itemPaste = ScoreEditing.pasteClipboardContent(
            measures = measures,
            content = itemClip,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(2, 0),
        )
        assertNotNull(itemPaste)
        assertEquals(listOf(q1, q2, q1), itemPaste.measures[2].items)
        assertEquals(Clef.ALTO, itemPaste.measures[2].clefChanges[1])
    }

    @Test
    fun testReplaceScoreDocumentCommandUndoRedo() {
        val scoreData = ScoreData().apply {
            clef = Clef.TREBLE
            keySignature = KeySignature.C_MAJOR
            timeSignature = TimeSignature.COMMON_TIME
            replaceMeasures(listOf(ScoreMeasure(1, emptyList())))
        }
        val beforeMeasures = scoreData.measures.toList()
        val note = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 3), NoteDuration.HALF)
        val afterMeasures = listOf(ScoreMeasure(1, listOf(note)))

        val cmd = ReplaceScoreDocumentCommand(
            scoreData = scoreData,
            beforeInitialClef = Clef.TREBLE,
            beforeInitialKey = KeySignature.C_MAJOR,
            beforeInitialTime = TimeSignature.COMMON_TIME,
            beforeMeasures = beforeMeasures,
            afterInitialClef = Clef.BASS,
            afterInitialKey = KeySignature.F_MAJOR,
            afterInitialTime = TimeSignature(3, 4),
            afterMeasures = afterMeasures,
        )
        cmd.execute()
        assertEquals(Clef.BASS, scoreData.clef)
        assertEquals(KeySignature.F_MAJOR, scoreData.keySignature)
        assertEquals(TimeSignature(3, 4), scoreData.timeSignature)
        assertEquals(afterMeasures, scoreData.measures.toList())

        cmd.undo()
        assertEquals(Clef.TREBLE, scoreData.clef)
        assertEquals(KeySignature.C_MAJOR, scoreData.keySignature)
        assertEquals(TimeSignature.COMMON_TIME, scoreData.timeSignature)
        assertEquals(beforeMeasures, scoreData.measures.toList())

        cmd.execute()
        assertEquals(Clef.BASS, scoreData.clef)
        assertEquals(KeySignature.F_MAJOR, scoreData.keySignature)
        assertEquals(TimeSignature(3, 4), scoreData.timeSignature)
        assertEquals(afterMeasures, scoreData.measures.toList())
    }

    @Test
    fun testPasteExtendingPastLastBarAddsEnoughMeasures() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val d4 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val f4 = ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.QUARTER)

        // Destination has 2 non-empty measures (4/4, C_MAJOR, TREBLE)
        val destMeasures = listOf(
            ScoreMeasure(1, listOf(c4, d4, e4, f4)),
            ScoreMeasure(2, listOf(c4, d4, e4, f4)),
        )
        // Copied 3-measure range starts in G_MAJOR / 3_4 / BASS and ends on a trailing empty bar
        val clipRange = ScoreEditing.copySelectionContent(
            measures = listOf(
                ScoreMeasure(1, listOf(c4, d4, e4), clefChanges = mapOf(1 to Clef.ALTO)),
                ScoreMeasure(2, listOf(f4)),
                ScoreMeasure(3, emptyList()),
            ),
            initialClef = Clef.BASS,
            initialKeySignature = KeySignature.G_MAJOR,
            initialTimeSignature = TimeSignature(3, 4),
            selectedMeasures = setOf(0, 1, 2),
        )
        assertTrue(clipRange is ScoreClipboardContent.MeasureRange)

        // Pasting with cursor at the end of the last non-empty measure (1, 4) keeps M2 intact and
        // automatically adds M3..M5 with M3's opening key/time/clef preserved.
        val pastedPastEnd = ScoreEditing.pasteClipboardContent(
            measures = destMeasures,
            content = clipRange,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(1, 4),
        )
        assertNotNull(pastedPastEnd)
        assertEquals(5, pastedPastEnd.measures.size)
        assertEquals(listOf(c4, d4, e4, f4), pastedPastEnd.measures[1].items)
        assertEquals(listOf(c4, d4, e4), pastedPastEnd.measures[2].items)
        assertEquals(KeySignature.G_MAJOR, pastedPastEnd.measures[2].keySignature)
        assertEquals(TimeSignature(3, 4), pastedPastEnd.measures[2].timeSignature)
        assertEquals(Clef.BASS, pastedPastEnd.measures[2].clefChanges[0])
        assertEquals(Clef.ALTO, pastedPastEnd.measures[2].clefChanges[1])
        assertEquals(listOf(f4), pastedPastEnd.measures[3].items)
        assertTrue(pastedPastEnd.measures[4].items.isEmpty())

        // Consecutive paste when selection includes the trailing empty bar (M5) starts at M6 (M6..M8)
        val secondPaste = ScoreEditing.pasteClipboardContent(
            measures = pastedPastEnd.measures,
            content = clipRange,
            initialClef = pastedPastEnd.initialClef ?: Clef.TREBLE,
            initialKeySignature = pastedPastEnd.initialKeySignature ?: KeySignature.C_MAJOR,
            initialTimeSignature = pastedPastEnd.initialTimeSignature ?: TimeSignature.COMMON_TIME,
            selectedLocations = pastedPastEnd.pastedLocations,
            selectedMeasures = pastedPastEnd.pastedMeasures,
        )
        assertNotNull(secondPaste)
        assertEquals(8, secondPaste.measures.size)
        assertEquals(listOf(c4, d4, e4), secondPaste.measures[5].items)
        assertEquals(listOf(f4), secondPaste.measures[6].items)
        assertTrue(secondPaste.measures[7].items.isEmpty())

        // ItemSelection extending across multiple full bars past the last measure also appends all needed bars
        val itemSelection = ScoreClipboardContent.ItemSelection(
            listOf(
                ScoreClipboardItem(c4, 0),
                ScoreClipboardItem(d4, 0),
                ScoreClipboardItem(e4, 0),
                ScoreClipboardItem(f4, 0),
                ScoreClipboardItem(c4, 1),
                ScoreClipboardItem(d4, 1),
            )
        )
        val itemsPastEnd = ScoreEditing.pasteClipboardContent(
            measures = destMeasures,
            content = itemSelection,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(1, 4),
        )
        assertNotNull(itemsPastEnd)
        assertEquals(4, itemsPastEnd.measures.size)
        assertEquals(listOf(c4, d4, e4, f4), itemsPastEnd.measures[2].items)
        assertEquals(listOf(c4, d4), itemsPastEnd.measures[3].items)
    }

    @Test
    fun testDynamicKeyboardCyclesAndPaletteOptions() {
        assertTrue(ScorePaletteKind.DYNAMICS in ScorePaletteKind.entries)
        val marks = ScorePalettes.dynamicOptions.map { it.mark }
        assertTrue(DynamicMark.MF in marks)
        assertTrue(DynamicMark.FF in marks)
        assertTrue(DynamicMark.PPPP in marks)
        assertTrue(DynamicMark.FFFF in marks)

        // 'p' cycle: p -> pp -> ppp -> pppp -> mp -> p
        assertEquals(DynamicMark.P, DynamicMark.nextPianoCycle(null))
        assertEquals(DynamicMark.P, DynamicMark.nextPianoCycle(DynamicMark.MF))
        assertEquals(DynamicMark.PP, DynamicMark.nextPianoCycle(DynamicMark.P))
        assertEquals(DynamicMark.PPP, DynamicMark.nextPianoCycle(DynamicMark.PP))
        assertEquals(DynamicMark.PPPP, DynamicMark.nextPianoCycle(DynamicMark.PPP))
        assertEquals(DynamicMark.MP, DynamicMark.nextPianoCycle(DynamicMark.PPPP))
        assertEquals(DynamicMark.P, DynamicMark.nextPianoCycle(DynamicMark.MP))

        // 'f' cycle: f -> ff -> fff -> ffff -> mf -> f
        assertEquals(DynamicMark.F, DynamicMark.nextForteCycle(null))
        assertEquals(DynamicMark.F, DynamicMark.nextForteCycle(DynamicMark.P))
        assertEquals(DynamicMark.FF, DynamicMark.nextForteCycle(DynamicMark.F))
        assertEquals(DynamicMark.FFF, DynamicMark.nextForteCycle(DynamicMark.FF))
        assertEquals(DynamicMark.FFFF, DynamicMark.nextForteCycle(DynamicMark.FFF))
        assertEquals(DynamicMark.MF, DynamicMark.nextForteCycle(DynamicMark.FFFF))
        assertEquals(DynamicMark.F, DynamicMark.nextForteCycle(DynamicMark.MF))

        // Monotonic musical amplitude progression from pppp to ffff
        val ordered = listOf(
            DynamicMark.PPPP, DynamicMark.PPP, DynamicMark.PP, DynamicMark.P,
            DynamicMark.MP, DynamicMark.MF, DynamicMark.F, DynamicMark.FF,
            DynamicMark.FFF, DynamicMark.FFFF,
        )
        for (i in 0 until ordered.lastIndex) {
            assertTrue(
                ordered[i].amplitude < ordered[i + 1].amplitude,
                "Expected ${ordered[i]} < ${ordered[i + 1]} amplitude",
            )
        }
    }

    @Test
    fun testDynamicPlacementShiftingDeletionAndLayoutHitTesting() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val d4 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val initial = listOf(
            ScoreMeasure(1, listOf(c4, d4)),
            ScoreMeasure(2, emptyList()),
        )

        val (withBelow, loc0) = ScoreEditing.setDynamic(
            initial,
            ScoreDynamicLocation(0, 1),
            ScoreDynamicMarking(DynamicMark.MF, DynamicPlacement.BELOW),
        )
        assertEquals(ScoreDynamicLocation(0, 1), loc0)
        val (withAbove, loc1) = ScoreEditing.setDynamic(
            withBelow,
            ScoreDynamicLocation(1, 0),
            ScoreDynamicMarking(DynamicMark.FF, DynamicPlacement.ABOVE),
        )
        assertEquals(ScoreDynamicLocation(1, 0), loc1)

        // Inserting a note before index 1 shifts the MF dynamic to index 2
        val (afterInsert, _) = ScoreEditing.insertItem(withAbove, 0, 0, e4, shiftClefAtIndex = true)
        assertEquals(ScoreDynamicMarking(DynamicMark.MF, DynamicPlacement.BELOW), afterInsert[0].dynamics[2])
        assertNull(afterInsert[0].dynamics[1])

        // Deleting the inserted note shifts MF back to index 1
        val afterItemDelete = ScoreEditing.deleteItems(afterInsert, setOf(ScoreItemLocation(0, 0)))
        assertEquals(ScoreDynamicMarking(DynamicMark.MF, DynamicPlacement.BELOW), afterItemDelete[0].dynamics[1])

        // Render and verify placement, hit-testing, rubber-band selection, and click-target resolution
        val scoreData = ScoreData().apply {
            clef = Clef.TREBLE
            keySignature = KeySignature.C_MAJOR
            timeSignature = TimeSignature.COMMON_TIME
            replaceMeasures(afterItemDelete)
        }
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = scoreData,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(false),
            selectedDynamics = setOf(ScoreDynamicLocation(0, 1)),
        )
        assertEquals(2, layout.placedDynamics.size)
        val mfPlaced = layout.placedDynamics.first { it.location == ScoreDynamicLocation(0, 1) }
        val ffPlaced = layout.placedDynamics.first { it.location == ScoreDynamicLocation(1, 0) }
        assertTrue(mfPlaced.baselineY > layout.bottomStaffY)
        assertTrue(ffPlaced.baselineY < layout.topStaffY)
        assertEquals(mfPlaced, layout.dynamicAt((mfPlaced.boxLeft + mfPlaced.boxRight) * 0.5f, (mfPlaced.boxTop + mfPlaced.boxBottom) * 0.5f))
        assertTrue(target.glyphs.any { it.glyph == DynamicMark.MF.glyph })
        assertTrue(target.glyphs.any { it.glyph == DynamicMark.FF.glyph })
        assertEquals(
            setOf(ScoreDynamicLocation(0, 1)),
            layout.dynamicsInRect(mfPlaced.boxLeft, mfPlaced.boxTop, mfPlaced.boxRight, mfPlaced.boxBottom),
        )

        val aboveTarget = layout.dynamicTargetAt(layout.placedMeasures[0].items[0].x, layout.topStaffY - 25f)
        assertNotNull(aboveTarget)
        assertEquals(ScoreDynamicLocation(0, 0), aboveTarget.first)
        assertEquals(DynamicPlacement.ABOVE, aboveTarget.second)

        val belowTarget = layout.dynamicTargetAt(layout.placedMeasures[0].items[1].x, layout.bottomStaffY + 25f)
        assertNotNull(belowTarget)
        assertEquals(ScoreDynamicLocation(0, 1), belowTarget.first)
        assertEquals(DynamicPlacement.BELOW, belowTarget.second)

        // Explicit deletion removes only selected dynamics
        val deleted = ScoreEditing.deleteDynamics(afterItemDelete, setOf(ScoreDynamicLocation(0, 1)))
        assertTrue(deleted[0].dynamics.isEmpty())
        assertEquals(ScoreDynamicMarking(DynamicMark.FF, DynamicPlacement.ABOVE), deleted[1].dynamics[0])
    }

    @Test
    fun testDynamicCopyPasteAndSerialization() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val d4 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val m1Dyn = ScoreDynamicMarking(DynamicMark.P, DynamicPlacement.BELOW)
        val m2Dyn = ScoreDynamicMarking(DynamicMark.FF, DynamicPlacement.ABOVE)
        val sourceMeasures = listOf(
            ScoreMeasure(1, listOf(c4, d4), dynamics = mapOf(0 to m1Dyn)),
            ScoreMeasure(2, listOf(c4), dynamics = mapOf(0 to m2Dyn)),
        )

        // 1. Standalone dynamics copy & paste spanning past final bar appends bars
        val clipDyns = ScoreEditing.copySelectionContent(
            measures = sourceMeasures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedDynamics = setOf(ScoreDynamicLocation(0, 0), ScoreDynamicLocation(1, 0)),
        )
        assertTrue(clipDyns is ScoreClipboardContent.DynamicsOnly)
        val pastedDyns = ScoreEditing.pasteClipboardContent(
            measures = listOf(ScoreMeasure(1, listOf(c4)), ScoreMeasure(2, listOf(d4))),
            content = clipDyns,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(1, 0),
        )
        assertNotNull(pastedDyns)
        assertEquals(3, pastedDyns.measures.size)
        assertEquals(m1Dyn, pastedDyns.measures[1].dynamics[0])
        assertEquals(m2Dyn, pastedDyns.measures[2].dynamics[0])

        // 2. MeasureRange copy & paste preserves dynamics
        val clipRange = ScoreEditing.copySelectionContent(
            measures = sourceMeasures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedMeasures = setOf(0, 1),
        )
        assertTrue(clipRange is ScoreClipboardContent.MeasureRange)
        val pastedRange = ScoreEditing.pasteClipboardContent(
            measures = listOf(ScoreMeasure(1, listOf(c4))),
            content = clipRange,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 1),
        )
        assertNotNull(pastedRange)
        assertEquals(3, pastedRange.measures.size)
        assertEquals(m1Dyn, pastedRange.measures[1].dynamics[0])
        assertEquals(m2Dyn, pastedRange.measures[2].dynamics[0])

        // 3. ItemSelection carries dynamic attached to copied note
        val clipItem = ScoreEditing.copySelectionContent(
            measures = sourceMeasures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(0, 0)),
        )
        assertTrue(clipItem is ScoreClipboardContent.ItemSelection)
        val pastedItem = ScoreEditing.pasteClipboardContent(
            measures = listOf(ScoreMeasure(1, listOf(d4))),
            content = clipItem,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 1),
        )
        assertNotNull(pastedItem)
        assertEquals(m1Dyn, pastedItem.measures[0].dynamics[1])

        // 4. GraphSerializer round-trip preserves dynamics and placements
        val graph = GraphState()
        val module = ScoreModule()
        module.scoreData.replaceMeasures(sourceMeasures)
        graph.addModule(module, Offset.Zero)
        val json = GraphSerializer.serialize(graph, graph.modules)
        val reloaded = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertEquals(m1Dyn, reloaded.scoreData.measures[0].dynamics[0])
        assertEquals(m2Dyn, reloaded.scoreData.measures[1].dynamics[0])
    }

    @Test
    fun testPlaybackAppliesDynamicsToNoteAmplitudes() {
        val engine = TestEngine()
        val score = ScoreModule()
        score.setEngine(engine)
        val receiver = VoiceReceiver()
        score.output.connect(receiver.inJack)

        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val d4 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val f4 = ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.QUARTER)
        score.scoreData.replaceMeasures(
            listOf(
                // Note 0: no dynamic (default 0.25)
                // Note 1: p (0.21), persists to Note 2
                // Note 3: ff (0.72)
                ScoreMeasure(
                    1,
                    listOf(c4, d4, e4, f4),
                    dynamics = mapOf(
                        1 to ScoreDynamicMarking(DynamicMark.P, DynamicPlacement.BELOW),
                        3 to ScoreDynamicMarking(DynamicMark.FF, DynamicPlacement.ABOVE),
                    ),
                ),
                // Note 4 (Measure 2): ff carries over; Note 5: sfz accent (0.80); Note 6: ff still active; Note 7: fp -> F attack then P next
                ScoreMeasure(
                    2,
                    listOf(c4, d4, e4, f4),
                    dynamics = mapOf(
                        1 to ScoreDynamicMarking(DynamicMark.SFZ, DynamicPlacement.BELOW),
                        3 to ScoreDynamicMarking(DynamicMark.FP, DynamicPlacement.BELOW),
                    ),
                ),
                ScoreMeasure(3, listOf(c4)),
            )
        )

        score.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled) {
            action()
        }

        val amps = receiver.received.map { it.extra }
        assertEquals(9, amps.size)
        assertEquals(0.25, amps[0], 1e-6)
        assertEquals(DynamicMark.P.amplitude, amps[1], 1e-6)
        assertEquals(DynamicMark.P.amplitude, amps[2], 1e-6)
        assertEquals(DynamicMark.FF.amplitude, amps[3], 1e-6)
        assertEquals(DynamicMark.FF.amplitude, amps[4], 1e-6)
        assertEquals(DynamicMark.SFZ.amplitude, amps[5], 1e-6)
        assertEquals(DynamicMark.FF.amplitude, amps[6], 1e-6)
        assertEquals(DynamicMark.F.amplitude, amps[7], 1e-6)
        assertEquals(DynamicMark.P.amplitude, amps[8], 1e-6)
    }

    @Test
    fun testPlayFromCursorRewindAndStartJackFromBeginning() {
        val engine = TestEngine()
        val score = ScoreModule()
        score.setEngine(engine)
        val receiver = VoiceReceiver()
        score.output.connect(receiver.inJack)

        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val d4 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val f4 = ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.QUARTER)
        val g4 = ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.QUARTER)

        // M1 has dynamic P on C4 and 2 quarter notes + 2 missing beats; M2 has E4, F4, G4.
        score.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    1,
                    listOf(c4, d4),
                    dynamics = mapOf(0 to ScoreDynamicMarking(DynamicMark.P, DynamicPlacement.BELOW)),
                ),
                ScoreMeasure(2, listOf(e4, f4, g4)),
            )
        )

        // 1. Place cursor on Measure 2 slot 1 (F4) and start playback from cursor
        score.placeCursor(ScoreCursorPosition(1, 1))
        score.startPlaybackFromCursor(ScoreTime(0L))
        assertTrue(score.isPlaying)
        for ((_, action) in engine.scheduled) {
            action()
        }
        assertFalse(score.isPlaying)
        // F4 and G4 play immediately starting at t=0, inheriting dynamic P from Measure 1!
        assertEquals(listOf(f4.pitch.midiPitch.toDouble(), g4.pitch.midiPitch.toDouble()), receiver.received.map { it.value })
        assertEquals(0L, receiver.received[0].time.nanoseconds)
        assertTrue(receiver.received[1].time.nanoseconds > 0L)
        assertEquals(DynamicMark.P.amplitude, receiver.received[0].extra, 1e-6)
        assertEquals(DynamicMark.P.amplitude, receiver.received[1].extra, 1e-6)

        // 2. Trailing cursor in incomplete M1 (slot 2, after D4): M2 E4 waits for the 2 missing beats of M1
        engine.scheduled.clear()
        receiver.received.clear()
        score.placeCursor(ScoreCursorPosition(0, 2))
        score.startPlaybackFromCursor(ScoreTime(0L))
        for ((_, action) in engine.scheduled) {
            action()
        }
        assertEquals(3, receiver.received.size)
        assertEquals(e4.pitch.midiPitch.toDouble(), receiver.received[0].value)
        // Two beats at 120 BPM = 1.0s
        assertEquals(1_000_000_000L, receiver.received[0].time.nanoseconds)

        // 3. Triggering the "start" jack STILL plays from the very beginning regardless of cursorPosition
        engine.scheduled.clear()
        receiver.received.clear()
        score.placeCursor(ScoreCursorPosition(1, 2))
        score.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled) {
            action()
        }
        assertEquals(
            listOf(c4, d4, e4, f4, g4).map { it.pitch.midiPitch.toDouble() },
            receiver.received.map { it.value },
        )
        assertEquals(0L, receiver.received[0].time.nanoseconds)

        // 4. Cursor at end of score (after last note) does not get stuck in isPlaying
        engine.scheduled.clear()
        receiver.received.clear()
        score.placeCursor(ScoreCursorPosition(1, 3))
        score.startPlaybackFromCursor(ScoreTime(0L))
        assertFalse(score.isPlaying)
        assertTrue(engine.scheduled.isEmpty())

        // 5. Rewind moves cursor to the beginning (0, 0) and playback starts from beginning again
        score.requestRewind()
        assertEquals(ScoreCursorPosition(0, 0), score.cursorPosition)
        score.startPlaybackFromCursor(ScoreTime(0L))
        for ((_, action) in engine.scheduled) {
            action()
        }
        assertEquals(5, receiver.received.size)
        assertEquals(c4.pitch.midiPitch.toDouble(), receiver.received[0].value)
    }

    @Test
    fun automaticBeamingGroupsByMeterSubdivisionAndBreaksOnRestsAndClefs() {
        fun e(step: Int) = ScoreItem.Note(
            pitch = SpelledPitch(NoteLetter.entries[step.mod(7)], 0, 4 + step / 7),
            duration = NoteDuration.EIGHTH,
        )
        fun s() = ScoreItem.Note(pitch = SpelledPitch(NoteLetter.C, 0, 5), duration = NoteDuration.SIXTEENTH)

        // 4/4: 8 pure eighth notes beam as two half-bar groups [0..3] and [4..7]
        val eightEighths = ScoreMeasure(1, List(8) { e(it) })
        val groups44 = ScoreEditing.computeBeamGroups(eightEighths, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6, 7)), groups44.map { it.itemIndices })

        // 4/4 with 16ths in beat 2 splits at beat 2: [e e] | [s s s s]
        val mixed44 = ScoreMeasure(1, listOf(e(0), e(1), s(), s(), s(), s()))
        val mixedGroups = ScoreEditing.computeBeamGroups(mixed44, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3, 4, 5)), mixedGroups.map { it.itemIndices })

        // 4/4 with eighth rest or quarter note breaks groups cleanly
        val withRest = ScoreMeasure(
            1,
            listOf(e(0), ScoreItem.Rest(NoteDuration.EIGHTH), e(1), e(2), ScoreItem.Rest(NoteDuration.QUARTER), e(3), e(4)),
        )
        val restGroups = ScoreEditing.computeBeamGroups(withRest, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertEquals(listOf(listOf(2, 3), listOf(5, 6)), restGroups.map { it.itemIndices })

        // 3/4 groups per quarter beat: [0,1], [2,3], [4,5]
        val sixEighths = ScoreMeasure(1, List(6) { e(it) })
        val groups34 = ScoreEditing.computeBeamGroups(sixEighths, 0, Clef.TREBLE, TimeSignature.THREE_FOUR)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5)), groups34.map { it.itemIndices })

        // Compound 6/8 groups by dotted-quarter beat: [0,1,2], [3,4,5]
        val groups68 = ScoreEditing.computeBeamGroups(sixEighths, 0, Clef.TREBLE, TimeSignature.SIX_EIGHT)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4, 5)), groups68.map { it.itemIndices })

        // Mid-measure clef change breaks automatic beaming
        val withClef = ScoreMeasure(1, List(4) { e(it) }, clefChanges = mapOf(2 to Clef.BASS))
        val clefGroups = ScoreEditing.computeBeamGroups(withClef, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), clefGroups.map { it.itemIndices })
    }

    @Test
    fun manualBeamingTogglesUnbeamsAndBeamsAndSurvivesEditsAndSerialization() {
        fun e(letter: NoteLetter) = ScoreItem.Note(SpelledPitch(letter, 0, 5), NoteDuration.EIGHTH)
        fun s(letter: NoteLetter) = ScoreItem.Note(SpelledPitch(letter, 0, 5), NoteDuration.SIXTEENTH)

        // Start in 3/4: [0,1] beamed, [2,3] beamed
        val initial = ScoreMeasure(1, listOf(e(NoteLetter.C), e(NoteLetter.D), e(NoteLetter.E), e(NoteLetter.F)))
        // Clicking note 0 (beamed to following 1) un-beams 0->1
        val afterUnbeam = ScoreEditing.toggleBeamClick(initial, Clef.TREBLE, TimeSignature.THREE_FOUR, 0)
        assertEquals(mapOf(0 to false), afterUnbeam.beamOverrides)
        assertEquals(
            listOf(listOf(2, 3)),
            ScoreEditing.computeBeamGroups(afterUnbeam, 0, Clef.TREBLE, TimeSignature.THREE_FOUR).map { it.itemIndices },
        )

        // Clicking note 1 (not beamed to following 2) beams 1->2 -> joining [1, 2, 3]
        val afterManualJoin = ScoreEditing.toggleBeamClick(afterUnbeam, Clef.TREBLE, TimeSignature.THREE_FOUR, 1)
        assertEquals(mapOf(0 to false, 1 to true), afterManualJoin.beamOverrides)
        assertEquals(
            listOf(listOf(1, 2, 3)),
            ScoreEditing.computeBeamGroups(afterManualJoin, 0, Clef.TREBLE, TimeSignature.THREE_FOUR).map { it.itemIndices },
        )

        // Clicking interior note 2 (beamed to following 3) breaks ONLY 2->3 (keeping [1, 2] beamed!)
        val afterSplit = ScoreEditing.toggleBeamClick(afterManualJoin, Clef.TREBLE, TimeSignature.THREE_FOUR, 2)
        assertEquals(
            listOf(listOf(1, 2)),
            ScoreEditing.computeBeamGroups(afterSplit, 0, Clef.TREBLE, TimeSignature.THREE_FOUR).map { it.itemIndices },
        )

        // Sequential restore repro: unbeam 4 sixteenths, then re-beam left-to-right (0, then 1, then 2)
        var sixteenths = ScoreMeasure(1, listOf(s(NoteLetter.C), s(NoteLetter.D), s(NoteLetter.E), s(NoteLetter.F)))
        for (idx in listOf(0, 1, 2)) {
            sixteenths = ScoreEditing.toggleBeamClick(sixteenths, Clef.TREBLE, TimeSignature.COMMON_TIME, idx)
        }
        assertEquals(
            emptyList<List<Int>>(),
            ScoreEditing.computeBeamGroups(sixteenths, 0, Clef.TREBLE, TimeSignature.COMMON_TIME).map { it.itemIndices },
        )
        sixteenths = ScoreEditing.toggleBeamClick(sixteenths, Clef.TREBLE, TimeSignature.COMMON_TIME, 0)
        assertEquals(
            listOf(listOf(0, 1)),
            ScoreEditing.computeBeamGroups(sixteenths, 0, Clef.TREBLE, TimeSignature.COMMON_TIME).map { it.itemIndices },
        )
        // Clicking note 1 (preceded by beamed note 0, not beamed to note 2) must beam [1, 2] -> [0, 1, 2]
        sixteenths = ScoreEditing.toggleBeamClick(sixteenths, Clef.TREBLE, TimeSignature.COMMON_TIME, 1)
        assertEquals(
            listOf(listOf(0, 1, 2)),
            ScoreEditing.computeBeamGroups(sixteenths, 0, Clef.TREBLE, TimeSignature.COMMON_TIME).map { it.itemIndices },
        )
        sixteenths = ScoreEditing.toggleBeamClick(sixteenths, Clef.TREBLE, TimeSignature.COMMON_TIME, 2)
        assertEquals(
            listOf(listOf(0, 1, 2, 3)),
            ScoreEditing.computeBeamGroups(sixteenths, 0, Clef.TREBLE, TimeSignature.COMMON_TIME).map { it.itemIndices },
        )
        assertEquals(emptyMap(), sixteenths.beamOverrides)

        // Inserting an item at index 1 shifts overrides at >= 1; deleting shifts back / drops invalidated links
        val inserted = ScoreEditing.insertItem(listOf(afterManualJoin), 0, 0, e(NoteLetter.G)).first.first()
        assertEquals(mapOf(1 to false, 2 to true), inserted.beamOverrides)
        val deleted = ScoreEditing.deleteItems(listOf(inserted), setOf(ScoreItemLocation(0, 0))).first()
        assertEquals(mapOf(0 to false, 1 to true), deleted.beamOverrides)

        // Serialization round-trip preserves beamOverrides
        val score = ScoreModule()
        score.scoreData.timeSignature = TimeSignature.THREE_FOUR
        score.scoreData.replaceMeasures(listOf(afterManualJoin))
        val graph = GraphState()
        graph.addModule(score, Offset.Zero)
        val json = GraphSerializer.serialize(graph, graph.modules)
        val restoredScore = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertEquals(afterManualJoin.beamOverrides, restoredScore.scoreData.measures.first().beamOverrides)

        // Copy/paste of MeasureRange preserves manual beam overrides
        val copied = ScoreEditing.copySelectionContent(
            measures = listOf(afterManualJoin),
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.THREE_FOUR,
            selectedMeasures = setOf(0),
        )!!
        val pasted = ScoreEditing.pasteClipboardContent(
            measures = listOf(afterManualJoin, ScoreMeasure(2, emptyList())),
            content = copied,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.THREE_FOUR,
            cursorPosition = ScoreCursorPosition(1, 0),
        )!!
        assertEquals(afterManualJoin.beamOverrides, pasted.measures[1].beamOverrides)

        // Copy/paste of ItemSelection (e.g. rubber-band note selection in one measure) also preserves manual beam overrides
        val copiedItems = ScoreEditing.copySelectionContent(
            measures = listOf(afterManualJoin),
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.THREE_FOUR,
            selectedLocations = (0..3).map { ScoreItemLocation(0, it) }.toSet(),
        )!!
        val pastedItems = ScoreEditing.pasteClipboardContent(
            measures = listOf(afterManualJoin, ScoreMeasure(2, emptyList())),
            content = copiedItems,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.THREE_FOUR,
            cursorPosition = ScoreCursorPosition(1, 0),
        )!!
        assertEquals(afterManualJoin.beamOverrides, pastedItems.measures[1].beamOverrides)
        assertEquals(
            listOf(listOf(1, 2, 3)),
            ScoreEditing.computeBeamGroups(pastedItems.measures[1], 1, Clef.TREBLE, TimeSignature.THREE_FOUR).map { it.itemIndices },
        )
    }

    @Test
    fun rendererDrawsBeamsAndStubsWithoutFlagsAndPlaybackIsUnaffected() {
        val target = RecordingDrawTarget()
        val score = ScoreData()
        val dotted8th = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 5), NoteDuration.EIGHTH, isDotted = true)
        val sixteenth = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 5), NoteDuration.SIXTEENTH)
        val twoSixteenthsAndEighth = listOf(
            dotted8th,
            sixteenth,
            ScoreItem.Rest(NoteDuration.QUARTER),
            ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 5), NoteDuration.EIGHTH),
        )
        score.replaceMeasures(listOf(ScoreMeasure(1, twoSixteenthsAndEighth)))

        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 12f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(false),
        )

        // Beat 1 [dotted8th, 16th] is beamed: primary beam + 16th broken beamlet = 2 DrawnBeams;
        // item index 3 (isolated eighth note) keeps its individual flag glyph, while beamed items have no flags.
        assertEquals(2, target.beams.size)
        val flagGlyphs = target.glyphs.filter {
            it.glyph == SmuflGlyphs.FLAG_8TH_UP ||
                it.glyph == SmuflGlyphs.FLAG_8TH_DOWN ||
                it.glyph == SmuflGlyphs.FLAG_16TH_UP ||
                it.glyph == SmuflGlyphs.FLAG_16TH_DOWN
        }
        assertEquals(1, flagGlyphs.size)
        assertTrue(layout.placedMeasures.single().items[0].isBeamed)
        assertTrue(layout.placedMeasures.single().items[1].isBeamed)
        assertFalse(layout.placedMeasures.single().items[3].isBeamed)

        // Verify beams do NOT change playback notes or timing
        val engine = TestEngine()
        val module = ScoreModule()
        module.setEngine(engine)
        val receiver = VoiceReceiver()
        module.output.connect(receiver.inJack)

        val unoverridden = ScoreMeasure(1, twoSixteenthsAndEighth)
        val overridden = ScoreMeasure(1, twoSixteenthsAndEighth, beamOverrides = mapOf(0 to false))

        module.scoreData.replaceMeasures(listOf(unoverridden))
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        val normalTimes = engine.scheduled.map { it.first }
        for ((_, action) in engine.scheduled) action()
        val normalNotes = receiver.received.map { it.value to it.extra }

        engine.scheduled.clear()
        receiver.received.clear()
        module.scoreData.replaceMeasures(listOf(overridden))
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        val overriddenTimes = engine.scheduled.map { it.first }
        for ((_, action) in engine.scheduled) action()
        val overriddenNotes = receiver.received.map { it.value to it.extra }

        assertEquals(normalTimes, overriddenTimes)
        assertEquals(normalNotes, overriddenNotes)
    }

    @Test
    fun toggleTieSameMeasureCrossMeasureAndChord() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)

        val measures = listOf(
            ScoreMeasure(
                1,
                listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER),
                    ScoreItem.Note(c4, NoteDuration.QUARTER),
                    ScoreItem.Note(d4, NoteDuration.QUARTER),
                    ScoreItem.Chord(listOf(c4, e4), NoteDuration.QUARTER),
                ),
            ),
            ScoreMeasure(
                2,
                listOf(
                    ScoreItem.Chord(listOf(c4, g4), NoteDuration.HALF),
                    ScoreItem.Rest(NoteDuration.HALF),
                ),
            ),
        )

        // Mismatch pitch (C4 -> D4) does not toggle on
        val unchangedMismatch = ScoreEditing.toggleTieAt(measures, ScoreItemLocation(0, 1))
        assertEquals(measures, unchangedMismatch)

        // Note 0 -> Note 1 (same pitch C4): toggles ON then OFF
        val tiedSameBar = ScoreEditing.toggleTieAt(measures, ScoreItemLocation(0, 0))
        assertTrue((tiedSameBar[0].items[0] as ScoreItem.Note).isTiedToNext)
        assertTrue(ScoreEditing.hasActiveTieForPitch(tiedSameBar, 0, 0, c4.midiPitch))
        val untiedSameBar = ScoreEditing.toggleTieAt(tiedSameBar, ScoreItemLocation(0, 0))
        assertFalse((untiedSameBar[0].items[0] as ScoreItem.Note).isTiedToNext)

        // Cross-measure chord notehead tie: C4 (index 0) ties across barline, E4 (index 1) has no match
        val crossBarChordTie = ScoreEditing.toggleTieAt(measures, ScoreItemLocation(0, 3, chordNoteIdx = 0))
        val tiedChord = crossBarChordTie[0].items[3] as ScoreItem.Chord
        assertEquals(setOf(c4.midiPitch), tiedChord.tiedMidiPitches)
        assertTrue(ScoreEditing.hasActiveTieForPitch(crossBarChordTie, 0, 3, c4.midiPitch))
        assertEquals(crossBarChordTie, ScoreEditing.toggleTieAt(crossBarChordTie, ScoreItemLocation(0, 3, chordNoteIdx = 1)))

        // Removing untied top chord note preserves C4's outgoing tie on remaining Note
        val deletedE4 = ScoreEditing.deleteItems(crossBarChordTie, setOf(ScoreItemLocation(0, 3, chordNoteIdx = 1)))
        val remainingNote = deletedE4[0].items[3] as ScoreItem.Note
        assertEquals(c4, remainingNote.pitch)
        assertTrue(remainingNote.isTiedToNext)
    }

    @Test
    fun playbackCombinesTiedDurationsAcrossMeasureBoundaryAndCursorStart() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)

        val engine = TestEngine()
        val module = ScoreModule()
        module.setEngine(engine)
        val receiver = VoiceReceiver()
        module.output.connect(receiver.inJack)

        // Bar 1: Half rest + Half C4 (tied across barline) -> Bar 2: Quarter C4 + Quarter D4
        val noteMeasures = listOf(
            ScoreMeasure(
                1,
                listOf(
                    ScoreItem.Rest(NoteDuration.HALF),
                    ScoreItem.Note(c4, NoteDuration.HALF, isTiedToNext = true),
                ),
            ),
            ScoreMeasure(
                2,
                listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER),
                    ScoreItem.Note(d4, NoteDuration.QUARTER),
                ),
            ),
        )
        module.scoreData.replaceMeasures(noteMeasures)
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))

        val activeIndices = mutableListOf<Int?>()
        for ((_, action) in engine.scheduled) {
            action()
            activeIndices.add(module.activeItemIndex)
        }
        val notes = receiver.received.filter { it.opcode == Message.VoiceOpcode.NOTE }
        // Only C4 (at bar 1 beat 3, duration = 1.0s + 0.5s = 1.5s) and D4 (duration = 0.5s)
        assertEquals(2, notes.size)
        assertEquals(c4.midiPitch.toDouble(), notes[0].value, 1e-6)
        assertEquals(1.5, notes[0].duration, 1e-3)
        assertEquals(d4.midiPitch.toDouble(), notes[1].value, 1e-6)
        assertEquals(0.5, notes[1].duration, 1e-3)
        // Visual highlight still steps across all 4 slots (0, 1, 2, 3) before clearing
        assertTrue(listOf(0, 1, 2, 3).all { it in activeIndices })

        // Play from Cursor starting at Bar 2 slot 0 (mid-tie target) SHOULD sound C4 for 0.5s
        engine.scheduled.clear()
        receiver.received.clear()
        module.placeCursor(ScoreCursorPosition(1, 0))
        module.startPlaybackFromCursor()
        for ((_, action) in engine.scheduled) action()
        val cursorNotes = receiver.received.filter { it.opcode == Message.VoiceOpcode.NOTE }
        assertEquals(2, cursorNotes.size)
        assertEquals(c4.midiPitch.toDouble(), cursorNotes[0].value, 1e-6)
        assertEquals(0.5, cursorNotes[0].duration, 1e-3)

        // Partial chord tie: [C4(tied), E4] -> [C4, G4]
        engine.scheduled.clear()
        receiver.received.clear()
        module.placeCursor(null)
        module.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    1,
                    listOf(
                        ScoreItem.Chord(listOf(c4, e4), NoteDuration.QUARTER, tiedMidiPitches = setOf(c4.midiPitch)),
                        ScoreItem.Chord(listOf(c4, g4), NoteDuration.QUARTER),
                    ),
                )
            )
        )
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled) action()
        val chordNotes = receiver.received.filter { it.opcode == Message.VoiceOpcode.NOTE }
        assertEquals(3, chordNotes.size)
        assertEquals(c4.midiPitch.toDouble(), chordNotes[0].value, 1e-6)
        assertEquals(1.0, chordNotes[0].duration, 1e-3)
        assertEquals(e4.midiPitch.toDouble(), chordNotes[1].value, 1e-6)
        assertEquals(0.5, chordNotes[1].duration, 1e-3)
        assertEquals(g4.midiPitch.toDouble(), chordNotes[2].value, 1e-6)
        assertEquals(0.5, chordNotes[2].duration, 1e-3)
    }

    @Test
    fun rendererDrawsTiesWithinAndAcrossMeasuresAndSkipsDanglingTie() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val target = RecordingDrawTarget()
        val score = ScoreData()
        score.replaceMeasures(
            listOf(
                ScoreMeasure(
                    1,
                    listOf(
                        ScoreItem.Note(c4, NoteDuration.QUARTER, isTiedToNext = true),
                        ScoreItem.Note(c4, NoteDuration.HALF),
                        ScoreItem.Note(c4, NoteDuration.QUARTER, isTiedToNext = true),
                    ),
                ),
                ScoreMeasure(
                    2,
                    listOf(
                        ScoreItem.Note(c4, NoteDuration.QUARTER),
                        // Dangling tie into mismatched pitch should not draw
                        ScoreItem.Note(c4, NoteDuration.QUARTER, isTiedToNext = true),
                        ScoreItem.Note(e4, NoteDuration.HALF),
                    ),
                ),
            )
        )

        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 12f,
            minWidthPx = 800f,
            colors = ScoreRenderColors.forTheme(false),
        )
        assertEquals(2, target.beziers.size)
        val bar1LastX = layout.placedMeasures[0].items[2].x
        val bar2FirstX = layout.placedMeasures[1].items[0].x
        val crossTie = target.beziers[1]
        assertTrue(crossTie.x0 > bar1LastX && crossTie.x3 <= bar2FirstX)
    }

    @Test
    fun tiesSurviveCopyPasteAndGraphSerializerRoundTrip() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val m1 = ScoreMeasure(
            1,
            listOf(
                ScoreItem.Note(c4, NoteDuration.QUARTER, isTiedToNext = true),
                ScoreItem.Note(c4, NoteDuration.QUARTER),
                ScoreItem.Chord(listOf(c4, e4), NoteDuration.HALF, tiedMidiPitches = setOf(c4.midiPitch)),
            ),
        )
        val m2 = ScoreMeasure(
            2,
            listOf(ScoreItem.Chord(listOf(c4, e4), NoteDuration.WHOLE)),
        )
        val measures = listOf(m1, m2)

        // ItemSelection copy/paste
        val itemClipboard = ScoreEditing.copySelectionContent(
            measures = measures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(0, 0), ScoreItemLocation(0, 1), ScoreItemLocation(0, 2)),
        )!!
        val pastedItems = ScoreEditing.pasteClipboardContent(
            measures = listOf(ScoreMeasure(1, emptyList())),
            content = itemClipboard,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 0),
        )!!
        assertTrue((pastedItems.measures[0].items[0] as ScoreItem.Note).isTiedToNext)
        assertEquals(setOf(c4.midiPitch), (pastedItems.measures[0].items[2] as ScoreItem.Chord).tiedMidiPitches)

        // MeasureRange copy/paste
        val measureClipboard = ScoreEditing.copySelectionContent(
            measures = measures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedMeasures = setOf(0, 1),
        )!!
        val pastedMeasures = ScoreEditing.pasteClipboardContent(
            measures = listOf(ScoreMeasure(1, emptyList())),
            content = measureClipboard,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedMeasures = setOf(0),
        )!!
        assertTrue((pastedMeasures.measures[0].items[0] as ScoreItem.Note).isTiedToNext)
        assertEquals(setOf(c4.midiPitch), (pastedMeasures.measures[0].items[2] as ScoreItem.Chord).tiedMidiPitches)

        // GraphSerializer JSON round-trip
        val graph = GraphState()
        val module = ScoreModule()
        module.scoreData.replaceMeasures(measures)
        graph.addModule(module, Offset.Zero)
        val json = GraphSerializer.serialize(graph, graph.modules)
        val loadedScore = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single().scoreData.measures
        assertTrue((loadedScore[0].items[0] as ScoreItem.Note).isTiedToNext)
        assertFalse((loadedScore[0].items[1] as ScoreItem.Note).isTiedToNext)
        assertEquals(setOf(c4.midiPitch), (loadedScore[0].items[2] as ScoreItem.Chord).tiedMidiPitches)
    }

    @Test
    fun testInstrumentPresetsTypesAndAutoClefAndStaffRecommendation() {
        assertEquals(7, InstrumentType.entries.size)
        assertTrue("Pizzicato" in InstrumentType.BOWED_STRINGS.availableArticulations)
        assertTrue("Pizzicato" in InstrumentType.PLUCKED_STRINGS.availableArticulations)

        // Default 88-key piano range A0..C8 (21..108) -> 2 staves (Treble + Bass)
        assertEquals(21, ScoreInstrumentPart.parsePitchOrMidi("A0"))
        assertEquals(108, ScoreInstrumentPart.parsePitchOrMidi("c8"))
        assertEquals(61, ScoreInstrumentPart.parsePitchOrMidi("C#4"))
        assertEquals(58, ScoreInstrumentPart.parsePitchOrMidi("Bb3"))
        assertEquals(21, ScoreInstrumentPart.parsePitchOrMidi("21"))
        assertNull(ScoreInstrumentPart.parsePitchOrMidi("not-a-note"))
        assertNull(ScoreInstrumentPart.parsePitchOrMidi("140"))

        // Default Synth range D3..G6 (50..91) -> 1 staff (Treble)
        val defaultRec = ScoreInstrumentPart.recommendStaffSetup(
            ScoreInstrumentPart.DEFAULT_MIN_MIDI,
            ScoreInstrumentPart.DEFAULT_MAX_MIDI,
        )
        assertEquals(1, defaultRec.staffCount)
        assertEquals(Clef.TREBLE, defaultRec.clef)

        val synthRec = ScoreInstrumentPart.recommendStaffSetup(
            InstrumentPreset.SYNTH.minMidiPitch,
            InstrumentPreset.SYNTH.maxMidiPitch,
        )
        assertEquals(1, synthRec.staffCount)
        assertEquals(Clef.TREBLE, synthRec.clef)

        // Built-in presets map to appropriate clefs and staff counts
        val violinRec = ScoreInstrumentPart.recommendStaffSetup(
            InstrumentPreset.VIOLIN.minMidiPitch,
            InstrumentPreset.VIOLIN.maxMidiPitch,
        )
        assertEquals(1, violinRec.staffCount)
        assertEquals(Clef.TREBLE, violinRec.clef)

        val violaRec = ScoreInstrumentPart.recommendStaffSetup(
            InstrumentPreset.VIOLA.minMidiPitch,
            InstrumentPreset.VIOLA.maxMidiPitch,
        )
        assertEquals(1, violaRec.staffCount)
        assertEquals(Clef.ALTO, violaRec.clef)

        val celloRec = ScoreInstrumentPart.recommendStaffSetup(
            InstrumentPreset.CELLO.minMidiPitch,
            InstrumentPreset.CELLO.maxMidiPitch,
        )
        assertEquals(1, celloRec.staffCount)
        assertEquals(Clef.BASS, celloRec.clef)

        val guitarRec = ScoreInstrumentPart.recommendStaffSetup(
            InstrumentPreset.GUITAR.minMidiPitch,
            InstrumentPreset.GUITAR.maxMidiPitch,
        )
        assertEquals(1, guitarRec.staffCount)
        assertEquals(Clef.TREBLE_8VB, guitarRec.clef)

        val pianoRec = ScoreInstrumentPart.recommendStaffSetup(
            InstrumentPreset.PIANO.minMidiPitch,
            InstrumentPreset.PIANO.maxMidiPitch,
        )
        assertEquals(2, pianoRec.staffCount)
        assertEquals(Clef.TREBLE, pianoRec.clef)
        assertEquals(Clef.BASS, pianoRec.lowerClef)

        val trumpetRec = ScoreInstrumentPart.recommendStaffSetup(
            InstrumentPreset.TRUMPET.minMidiPitch,
            InstrumentPreset.TRUMPET.maxMidiPitch,
        )
        assertEquals(1, trumpetRec.staffCount)
        assertEquals(Clef.TREBLE, trumpetRec.clef)
    }

    @Test
    fun testMultiInstrumentOutputsUndoRedoAndSyncedPlaybackRouting() {
        val engine = TestEngine()
        val patch = com.syntona.dataflow.modules.PatchModule()
        val graph = patch.graphState
        val module = ScoreModule()
        module.setEngine(engine)
        graph.addModule(module, Offset.Zero)

        val beforeInst = module.scoreData.instruments.toList()
        val pianoInst = ScoreInstrumentPart(
            id = "inst-2",
            name = "Piano",
            instrumentType = InstrumentType.KEYBOARDS,
            minMidiPitch = InstrumentPreset.PIANO.minMidiPitch,
            maxMidiPitch = InstrumentPreset.PIANO.maxMidiPitch,
            staffCount = 2,
            clef = Clef.TREBLE,
            lowerClef = Clef.BASS,
            jackName = "Piano",
            measures = listOf(
                ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 5), NoteDuration.WHOLE))),
                ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 5), NoteDuration.HALF))),
            ),
            lowerMeasures = listOf(
                ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 3), NoteDuration.HALF))),
                ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 2), NoteDuration.QUARTER))),
            ),
        )
        val celloInst = ScoreInstrumentPart(
            id = "inst-3",
            name = "Cello",
            instrumentType = InstrumentType.BOWED_STRINGS,
            minMidiPitch = InstrumentPreset.CELLO.minMidiPitch,
            maxMidiPitch = InstrumentPreset.CELLO.maxMidiPitch,
            staffCount = 1,
            clef = Clef.BASS,
            jackName = "Cello",
            measures = listOf(
                ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 2), NoteDuration.QUARTER))),
                ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.QUARTER))),
            ),
        )
        val leadInst = beforeInst.single().copy(
            measures = listOf(
                ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.HALF))),
                ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.B, 0, 4), NoteDuration.QUARTER))),
            )
        )

        val cmd = ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = beforeInst,
            afterInstruments = listOf(leadInst, pianoInst, celloInst),
        )
        cmd.execute()
        assertEquals(listOf("out", "Piano", "Cello"), module.outputs.map { it.name })

        val recvLead = VoiceReceiver()
        val recvPiano = VoiceReceiver()
        val recvCello = VoiceReceiver()
        graph.addModule(recvLead, Offset(200f, 0f))
        graph.addModule(recvPiano, Offset(200f, 100f))
        graph.addModule(recvCello, Offset(200f, 200f))
        val outLead = module.outputForInstrument(0)
        val outPiano = module.outputForInstrument(1)
        val outCello = module.outputForInstrument(2)
        graph.connect(outLead, recvLead.inJack)
        graph.connect(outPiano, recvPiano.inJack)
        graph.connect(outCello, recvCello.inJack)
        assertTrue(outPiano in graph.connections.map { it.output })

        // Start playback at 120 BPM (bar 1 = 2.0s in 4/4)
        module.scoreData.timeSignature = TimeSignature.COMMON_TIME
        module.tempo.setValue(120.0)
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))

        // Record NOTE times (in seconds) per receiver
        val leadNotes = mutableListOf<Pair<Double, Int>>()
        val pianoNotes = mutableListOf<Pair<Double, Int>>()
        val celloNotes = mutableListOf<Pair<Double, Int>>()
        for ((time, action) in engine.scheduled.sortedBy { it.first.nanoseconds }) {
            val delaySec = time.nanoseconds / 1_000_000_000.0
            val prevLead = recvLead.received.size
            val prevPiano = recvPiano.received.size
            val prevCello = recvCello.received.size
            action()
            recvLead.received.drop(prevLead).filter { it.opcode == Message.VoiceOpcode.NOTE }
                .forEach { leadNotes.add(delaySec to it.value.toInt()) }
            recvPiano.received.drop(prevPiano).filter { it.opcode == Message.VoiceOpcode.NOTE }
                .forEach { pianoNotes.add(delaySec to it.value.toInt()) }
            recvCello.received.drop(prevCello).filter { it.opcode == Message.VoiceOpcode.NOTE }
                .forEach { celloNotes.add(delaySec to it.value.toInt()) }
        }

        // Instrument 0 only receives A4 (69) at t=0 and B4 (71) at bar 2 t=2.0s
        assertEquals(listOf(0.0 to 69, 2.0 to 71), leadNotes)
        // Instrument 1 (2-staff Grand Staff Piano) routes both upper (E5=76, G5=79) and lower (C3=48, G2=43) to Piano jack
        assertEquals(setOf(76, 48, 79, 43), pianoNotes.map { it.second }.toSet())
        assertTrue(pianoNotes.any { it.second == 79 && kotlin.math.abs(it.first - 2.0) < 1e-6 })
        assertTrue(pianoNotes.any { it.second == 43 && kotlin.math.abs(it.first - 2.0) < 1e-6 })
        // Instrument 2 (Cello) only receives C2 (36) at t=0 and D3 (50) synced cleanly at bar 2 t=2.0s
        assertEquals(listOf(0.0 to 36, 2.0 to 50), celloNotes)

        // Undo restores single instrument and disconnects/removes extra outputs
        cmd.undo()
        assertEquals(listOf("out"), module.outputs.map { it.name })
        assertFalse(outPiano in graph.connections.map { it.output })
        cmd.execute()
        assertEquals(listOf("out", "Piano", "Cello"), module.outputs.map { it.name })
    }

    @Test
    fun testOutOfRangeNotesPlayableAndRenderedRed() {
        val engine = TestEngine()
        val module = ScoreModule()
        module.setEngine(engine)
        val recv = VoiceReceiver()
        module.output.connect(recv.inJack)

        val c3 = SpelledPitch(NoteLetter.C, 0, 3) // MIDI 48 (< Violin min G3=55 -> out of range!)
        val a4 = SpelledPitch(NoteLetter.A, 0, 4) // MIDI 69 (in range)
        val violinPart = ScoreInstrumentPart(
            id = "inst-1",
            name = "Violin",
            instrumentType = InstrumentType.BOWED_STRINGS,
            minMidiPitch = InstrumentPreset.VIOLIN.minMidiPitch,
            maxMidiPitch = InstrumentPreset.VIOLIN.maxMidiPitch,
            staffCount = 1,
            clef = Clef.TREBLE,
            measures = listOf(
                ScoreMeasure(
                    1,
                    listOf(
                        ScoreItem.Note(c3, NoteDuration.QUARTER),
                        ScoreItem.Chord(listOf(c3, a4), NoteDuration.QUARTER),
                    ),
                )
            ),
        )
        module.scoreData.replaceAllInstruments(listOf(violinPart))

        val colors = ScoreRenderColors.forTheme(false)
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = module.scoreData,
            target = target,
            staffSpacePx = 12f,
            minWidthPx = 800f,
            colors = colors,
        )
        val placedItems = layout.placedMeasures[0].items
        assertTrue(placedItems[0].isOutOfRange)
        assertEquals(setOf(0), placedItems[0].outOfRangeChordIndices)
        assertTrue(placedItems[1].isOutOfRange)
        assertEquals(setOf(0), placedItems[1].outOfRangeChordIndices)

        val redNoteheads = target.glyphs.filter { it.color == colors.overflowNote && it.glyph == NoteDuration.QUARTER.noteheadGlyph }
        val normalNoteheads = target.glyphs.filter { it.color == colors.ink && it.glyph == NoteDuration.QUARTER.noteheadGlyph }
        assertEquals(2, redNoteheads.size) // Both C3 noteheads rendered red
        assertEquals(1, normalNoteheads.size) // In-range A4 rendered normal ink

        // Out-of-range notes still play normally
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled) action()
        val playedMidis = recv.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value.toInt() }
        assertTrue(c3.midiPitch in playedMidis)
        assertTrue(a4.midiPitch in playedMidis)
    }

    @Test
    fun testMultiInstrumentAlignedBarlinesGrandStaffAndHitTesting() {
        val score = ScoreData()
        score.replaceAllInstruments(
            listOf(
                ScoreInstrumentPart(
                    id = "inst-1",
                    name = "Piano",
                    instrumentType = InstrumentType.KEYBOARDS,
                    minMidiPitch = 21,
                    maxMidiPitch = 108,
                    staffCount = 2,
                    clef = Clef.TREBLE,
                    lowerClef = Clef.BASS,
                    measures = listOf(
                        ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 5), NoteDuration.QUARTER))),
                        ScoreMeasure(2, emptyList()),
                    ),
                    lowerMeasures = listOf(
                        ScoreMeasure(
                            1,
                            listOf(
                                ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 3), NoteDuration.SIXTEENTH),
                                ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 3), NoteDuration.SIXTEENTH),
                                ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 3), NoteDuration.SIXTEENTH),
                                ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 3), NoteDuration.SIXTEENTH),
                            ),
                        ),
                        ScoreMeasure(2, emptyList()),
                    ),
                ),
                ScoreInstrumentPart(
                    id = "inst-2",
                    name = "Violin",
                    instrumentType = InstrumentType.BOWED_STRINGS,
                    minMidiPitch = 55,
                    maxMidiPitch = 105,
                    staffCount = 1,
                    clef = Clef.TREBLE,
                    jackName = "Violin",
                    measures = listOf(
                        ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.HALF))),
                        ScoreMeasure(2, emptyList()),
                    ),
                ),
            )
        )

        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 12f,
            minWidthPx = 900f,
            colors = ScoreRenderColors.forTheme(false),
        )
        assertEquals(3, layout.staves.size)
        assertEquals(2, layout.instrumentGroups.size)
        assertTrue(layout.showInstrumentLabels)
        assertTrue(target.texts.any { it.text == "Piano" })
        assertTrue(target.texts.any { it.text == "Violin" })

        // All 3 staves share identical synchronized barline X coordinates
        val bar1X = layout.staves[0].placedMeasures[0].barlineX
        assertEquals(bar1X, layout.staves[1].placedMeasures[0].barlineX, 0.01f)
        assertEquals(bar1X, layout.staves[2].placedMeasures[0].barlineX, 0.01f)

        // Vertical hit-testing resolves each distinct staff and item accurately
        val violinStaff = layout.staves[2]
        val violinItem = violinStaff.placedMeasures[0].items[0]
        val hit = layout.itemAt((violinItem.boxLeft + violinItem.boxRight) * 0.5f, (violinItem.boxTop + violinItem.boxBottom) * 0.5f)
        assertNotNull(hit)
        assertEquals(ScoreStaffId(1, 0), hit.staff)

        val bassStaff = layout.staves[1]
        val bassItem = bassStaff.placedMeasures[0].items[2]
        val hitBass = layout.itemAt((bassItem.boxLeft + bassItem.boxRight) * 0.5f, (bassItem.boxTop + bassItem.boxBottom) * 0.5f)
        assertNotNull(hitBass)
        assertEquals(ScoreStaffId(0, 1), hitBass.staff)
        assertEquals(2, hitBass.itemIdx)
    }

    @Test
    fun testMultiInstrumentAndWiredOutputsSurviveGraphSerializerRoundTrip() {
        val graph = GraphState()
        val scoreMod = ScoreModule()
        val sink0 = ScoreModule()
        val sink1 = ScoreModule()
        graph.addModule(scoreMod, Offset.Zero)
        graph.addModule(sink0, Offset(240f, 0f))
        graph.addModule(sink1, Offset(240f, 160f))

        scoreMod.scoreData.replaceAllInstruments(
            listOf(
                ScoreInstrumentPart(
                    id = "inst-1",
                    name = "Grand Piano",
                    instrumentType = InstrumentType.KEYBOARDS,
                    minMidiPitch = 21,
                    maxMidiPitch = 108,
                    staffCount = 2,
                    clef = Clef.TREBLE,
                    lowerClef = Clef.BASS,
                    jackName = "out",
                    measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 5), NoteDuration.QUARTER)))),
                    lowerMeasures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 3), NoteDuration.HALF)))),
                ),
                ScoreInstrumentPart(
                    id = "inst-2",
                    name = "Trumpet",
                    instrumentType = InstrumentType.BRASS,
                    minMidiPitch = 52,
                    maxMidiPitch = 86,
                    staffCount = 1,
                    clef = Clef.TREBLE,
                    jackName = "Trumpet",
                    measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.WHOLE)))),
                ),
            )
        )
        scoreMod.syncInstrumentOutputs()
        graph.connect(scoreMod.outputForInstrument(0), sink0.input)
        graph.connect(scoreMod.outputForInstrument(1), sink1.input)

        val json = GraphSerializer.serialize(graph, graph.modules)
        val restoredGraph = GraphState()
        val restoredModules = GraphSerializer.deserialize(restoredGraph, json)
        val restoredScore = restoredModules.filterIsInstance<ScoreModule>().first()

        assertEquals(2, restoredScore.scoreData.instruments.size)
        val rPiano = restoredScore.scoreData.instruments[0]
        assertEquals("Grand Piano", rPiano.name)
        assertEquals(2, rPiano.staffCount)
        assertEquals(Clef.TREBLE, rPiano.clef)
        assertEquals(Clef.BASS, rPiano.lowerClef)
        assertEquals(1, rPiano.lowerMeasures[0].items.size)
        val rTrumpet = restoredScore.scoreData.instruments[1]
        assertEquals("Trumpet", rTrumpet.name)
        assertEquals(InstrumentType.BRASS, rTrumpet.instrumentType)
        assertEquals(52, rTrumpet.minMidiPitch)
        assertEquals(86, rTrumpet.maxMidiPitch)
        assertEquals("Trumpet", rTrumpet.jackName)

        // Verify per-instrument output jacks AND wire connections were restored
        assertEquals(listOf("out", "Trumpet"), restoredScore.outputs.map { it.name })
        assertEquals(2, restoredGraph.connections.size)
        assertTrue(restoredGraph.connections.any { it.output.module === restoredScore && it.output.name == "out" })
        assertTrue(restoredGraph.connections.any { it.output.module === restoredScore && it.output.name == "Trumpet" })
    }

    @Test
    fun testRenamingInstrumentsUpdatesOutputJackNamesUndoRedoAndSerialization() {
        val patch = com.syntona.dataflow.modules.PatchModule()
        val graph = patch.graphState
        val module = ScoreModule()
        val sink0 = ScoreModule()
        val sink1 = ScoreModule()
        graph.addModule(module, Offset.Zero)
        graph.addModule(sink0, Offset(240f, 0f))
        graph.addModule(sink1, Offset(240f, 160f))

        // Start with Instrument 0 ("Instrument 1" / jack "out") and Instrument 1 ("Piano" / jack "Piano")
        val inst0 = module.scoreData.instruments.first()
        val inst1 = ScoreInstrumentPart(
            id = "inst-2",
            name = "Piano",
            instrumentType = InstrumentType.KEYBOARDS,
            jackName = "Piano",
        )
        ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = listOf(inst0),
            afterInstruments = listOf(inst0, inst1),
        ).execute()
        assertEquals(listOf("out", "Piano"), module.outputs.map { it.name })

        graph.connect(module.outputForInstrument(0), sink0.input)
        graph.connect(module.outputForInstrument(1), sink1.input)

        // Rename Instrument 1 ("Piano") -> "Frog"
        val renameSecondCmd = ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = listOf(inst0, inst1),
            afterInstruments = listOf(inst0, inst1.copy(name = "Frog", jackName = "Frog")),
        )
        renameSecondCmd.execute()
        assertEquals(listOf("out", "Frog"), module.outputs.map { it.name })
        assertEquals(2, graph.connections.size)

        // Undo restores "Piano" jack name while keeping wire intact
        renameSecondCmd.undo()
        assertEquals(listOf("out", "Piano"), module.outputs.map { it.name })
        assertEquals(2, graph.connections.size)

        // Redo re-applies "Frog"
        renameSecondCmd.execute()
        assertEquals(listOf("out", "Frog"), module.outputs.map { it.name })

        // Rename Instrument 0 ("Instrument 1") -> "Toad"
        val afterSecond = module.scoreData.instruments.toList()
        val renameFirstCmd = ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = afterSecond,
            afterInstruments = listOf(afterSecond[0].copy(name = "Toad", jackName = "Toad"), afterSecond[1]),
        )
        renameFirstCmd.execute()
        assertEquals(listOf("Toad", "Frog"), module.outputs.map { it.name })
        assertEquals(2, graph.connections.size)

        // Verify JSON serialization/deserialization preserves renamed jacks and their wires
        val json = GraphSerializer.serialize(graph, graph.modules)
        val restoredGraph = GraphState()
        val restoredModules = GraphSerializer.deserialize(restoredGraph, json)
        val restoredScore = restoredModules.filterIsInstance<ScoreModule>().first()
        assertEquals(listOf("Toad", "Frog"), restoredScore.outputs.map { it.name })
        assertEquals(2, restoredGraph.connections.size)
        assertTrue(restoredGraph.connections.any { it.output.module === restoredScore && it.output.name == "Toad" })
        assertTrue(restoredGraph.connections.any { it.output.module === restoredScore && it.output.name == "Frog" })

        // Undo restores Instrument 0 jack back to "out"
        renameFirstCmd.undo()
        assertEquals(listOf("out", "Frog"), module.outputs.map { it.name })
    }

    @Test
    fun testReorderInstrumentsPreservesWiresUpdatesOutputOrderUndoRedoAndPlaybackRouting() {
        val engine = TestEngine()
        val patch = com.syntona.dataflow.modules.PatchModule()
        val graph = patch.graphState
        val module = ScoreModule()
        module.setEngine(engine)
        val recvA = VoiceReceiver()
        val recvB = VoiceReceiver()
        val recvC = VoiceReceiver()
        graph.addModule(module, Offset.Zero)
        graph.addModule(recvA, Offset(240f, 0f))
        graph.addModule(recvB, Offset(240f, 100f))
        graph.addModule(recvC, Offset(240f, 200f))

        val instA = module.scoreData.instruments.first().copy(
            name = "Violin",
            jackName = "Violin",
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.QUARTER)))),
        )
        val instB = ScoreInstrumentPart(
            id = "inst-2",
            name = "Viola",
            jackName = "Viola",
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)))),
        )
        val instC = ScoreInstrumentPart(
            id = "inst-3",
            name = "Cello",
            jackName = "Cello",
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 2), NoteDuration.QUARTER)))),
        )
        val initialRoster = listOf(instA, instB, instC)
        ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = module.scoreData.instruments.toList(),
            afterInstruments = initialRoster,
        ).execute()
        assertEquals(listOf("Violin", "Viola", "Cello"), module.outputs.map { it.name })

        val jackA = module.outputForInstrument(0)
        val jackB = module.outputForInstrument(1)
        val jackC = module.outputForInstrument(2)
        graph.connect(jackA, recvA.inJack)
        graph.connect(jackB, recvB.inJack)
        graph.connect(jackC, recvC.inJack)

        // Move Cello (index 2) up to index 0 (two moves: 2 -> 1 -> 0)
        val afterUp1 = ScoreInstrumentPart.reorderInstruments(initialRoster, 2, 1)
        assertEquals(listOf("Violin", "Cello", "Viola"), afterUp1.map { it.name })
        val cmdUp1 = ReplaceScoreInstrumentsCommand(module, initialRoster, afterUp1)
        cmdUp1.execute()
        assertEquals(listOf("Violin", "Cello", "Viola"), module.outputs.map { it.name })

        val afterUp2 = ScoreInstrumentPart.reorderInstruments(afterUp1, 1, 0)
        assertEquals(listOf("Cello", "Violin", "Viola"), afterUp2.map { it.name })
        val cmdUp2 = ReplaceScoreInstrumentsCommand(module, afterUp1, afterUp2)
        cmdUp2.execute()
        assertEquals(listOf("Cello", "Violin", "Viola"), module.outputs.map { it.name })

        // Same jack objects stay wired to their original receivers!
        assertSame(jackC, module.outputForInstrument(0))
        assertSame(jackA, module.outputForInstrument(1))
        assertSame(jackB, module.outputForInstrument(2))
        assertEquals(3, graph.connections.size)

        // Index remapping tracks cursors/selections cleanly
        assertEquals(0, ScoreInstrumentPart.remapInstrumentIndex(2, 2, 0))
        assertEquals(1, ScoreInstrumentPart.remapInstrumentIndex(0, 2, 0))
        assertEquals(2, ScoreInstrumentPart.remapInstrumentIndex(1, 2, 0))
        assertEquals(2, ScoreInstrumentPart.remapInstrumentIndex(0, 0, 2))
        assertEquals(0, ScoreInstrumentPart.remapInstrumentIndex(1, 0, 2))
        assertEquals(1, ScoreInstrumentPart.remapInstrumentIndex(2, 0, 2))

        // Playback after reorder still routes each instrument's notes to its original receiver
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled.sortedBy { it.first.nanoseconds }) {
            action()
        }
        assertEquals(listOf(69), recvA.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value.toInt() })
        assertEquals(listOf(60), recvB.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value.toInt() })
        assertEquals(listOf(43), recvC.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value.toInt() })

        // JSON serialization/deserialization preserves reordered instruments, jack order, and wires
        val serGraph = GraphState()
        val serScore = ScoreModule()
        val sink0 = ScoreModule()
        val sink1 = ScoreModule()
        val sink2 = ScoreModule()
        serGraph.addModule(serScore, Offset.Zero)
        serGraph.addModule(sink0, Offset(240f, 0f))
        serGraph.addModule(sink1, Offset(240f, 100f))
        serGraph.addModule(sink2, Offset(240f, 200f))
        serScore.scoreData.replaceAllInstruments(module.scoreData.instruments.toList())
        serScore.syncInstrumentOutputs()
        serGraph.connect(serScore.outputForInstrument(0), sink0.input)
        serGraph.connect(serScore.outputForInstrument(1), sink1.input)
        serGraph.connect(serScore.outputForInstrument(2), sink2.input)

        val json = GraphSerializer.serialize(serGraph, serGraph.modules)
        val restoredGraph = GraphState()
        val restoredModules = GraphSerializer.deserialize(restoredGraph, json)
        val restoredScore = restoredModules.filterIsInstance<ScoreModule>().first()
        assertEquals(listOf("Cello", "Violin", "Viola"), restoredScore.scoreData.instruments.map { it.name })
        assertEquals(listOf("Cello", "Violin", "Viola"), restoredScore.outputs.map { it.name })
        assertEquals(3, restoredGraph.connections.size)
        assertTrue(restoredGraph.connections.any { it.output.name == "Cello" && it.input.module === restoredModules[1] })
        assertTrue(restoredGraph.connections.any { it.output.name == "Violin" && it.input.module === restoredModules[2] })
        assertTrue(restoredGraph.connections.any { it.output.name == "Viola" && it.input.module === restoredModules[3] })

        // Undo restores order and keeps all 3 wires intact
        cmdUp2.undo()
        cmdUp1.undo()
        assertEquals(listOf("Violin", "Viola", "Cello"), module.outputs.map { it.name })
        assertSame(jackA, module.outputForInstrument(0))
        assertSame(jackB, module.outputForInstrument(1))
        assertSame(jackC, module.outputForInstrument(2))
        assertEquals(3, graph.connections.size)
    }

    @Test
    fun testTupletRatioDisplayAndExactCapacity() {
        // Standard ratios display top number only in simple meters
        assertEquals("3", ScoreTuplet("g1", 3, 2).displayText(TimeSignature.COMMON_TIME))
        assertEquals("5", ScoreTuplet("g2", 5, 4).displayText(TimeSignature.COMMON_TIME))
        assertEquals("6", ScoreTuplet("g3", 6, 4).displayText(TimeSignature.COMMON_TIME))
        assertEquals("7", ScoreTuplet("g4", 7, 4).displayText(TimeSignature.COMMON_TIME))
        assertEquals("9", ScoreTuplet("g5", 9, 8).displayText(TimeSignature.COMMON_TIME))
        assertEquals("11", ScoreTuplet("g6", 11, 8).displayText(TimeSignature.COMMON_TIME))

        // Compound meter 6/8 displays compound duplet as single "2"
        assertEquals("2", ScoreTuplet("gd", 2, 3).displayText(TimeSignature(6, 8)))

        // Non-obvious denominators display full N:M ratio
        assertEquals("4:3", ScoreTuplet("gn1", 4, 3).displayText(TimeSignature.COMMON_TIME))
        assertEquals("5:3", ScoreTuplet("gn2", 5, 3).displayText(TimeSignature.COMMON_TIME))
        assertEquals("7:6", ScoreTuplet("gn3", 7, 6).displayText(TimeSignature.COMMON_TIME))

        // 7:4 septuplet of 16ths in 4/4 sums to exactly 1 quarter beat (240 ticks) + 3 quarters = 960 ticks (no overflow!)
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val sepTuplet = ScoreTuplet("sep", 7, 4)
        val sepItems = List(7) {
            ScoreItem.Note(c4, NoteDuration.SIXTEENTH, tuplet = sepTuplet)
        } + List(3) {
            ScoreItem.Note(c4, NoteDuration.QUARTER)
        }
        val m = ScoreMeasure(1, sepItems)
        assertEquals(RationalTicks.of(960), ScoreEditing.measureExactTicks(m))

        val score = ScoreData().apply {
            timeSignature = TimeSignature.COMMON_TIME
            replaceMeasures(listOf(m))
        }
        val layout = ScoreRenderer.render(
            score = score,
            target = RecordingDrawTarget(),
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )
        val placedItems = layout.placedMeasures[0].items
        assertTrue(placedItems.none { it.isOverflow }, "7:4 + 3 quarters should fill 4/4 cleanly with no overflow")

        // Adding one more 16th tips over 960 ticks -> flagged as overflow
        val overScore = ScoreData().apply {
            timeSignature = TimeSignature.COMMON_TIME
            replaceMeasures(listOf(ScoreMeasure(1, sepItems + ScoreItem.Note(c4, NoteDuration.SIXTEENTH))))
        }
        val overLayout = ScoreRenderer.render(
            score = overScore,
            target = RecordingDrawTarget(),
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )
        assertTrue(overLayout.placedMeasures[0].items.last().isOverflow)
    }

    @Test
    fun testTupletBracketsAndAutoBeaming() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)

        // 1. Complete beamed eighth triplet -> unbroken single beam -> omit bracket
        val tBeam = ScoreTuplet("tb", 3, 2, baseTicks = NoteDuration.EIGHTH.ticks)
        val beamedMeasure = ScoreMeasure(
            1,
            listOf(
                ScoreItem.Note(c4, NoteDuration.EIGHTH, tuplet = tBeam),
                ScoreItem.Note(e4, NoteDuration.EIGHTH, tuplet = tBeam),
                ScoreItem.Note(c4, NoteDuration.EIGHTH, tuplet = tBeam),
                ScoreItem.Note(c4, NoteDuration.EIGHTH), // non-tuplet follows
            ),
        )
        val links = ScoreEditing.effectiveBeamLinks(beamedMeasure, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertTrue(links[0])
        assertTrue(links[1])
        assertFalse(links[2], "Auto-beam must break across tuplet boundary")
        val beamedGroups = ScoreEditing.computeTupletGroups(beamedMeasure, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertEquals(1, beamedGroups.size)
        assertTrue(beamedGroups[0].isFull)
        assertFalse(beamedGroups[0].drawBracket, "Single unbroken beam omits bracket")
        assertEquals("3", beamedGroups[0].displayText)

        // 2. Broken beam inside triplet -> draws bracket
        val brokenMeasure = beamedMeasure.copy(beamOverrides = mapOf(1 to false))
        val brokenGroups = ScoreEditing.computeTupletGroups(brokenMeasure, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertTrue(brokenGroups.single().drawBracket, "Broken beam must draw bracket")

        // 3. Rest inside triplet -> draws bracket
        val tRest = ScoreTuplet("tr", 3, 2, baseTicks = NoteDuration.EIGHTH.ticks)
        val restMeasure = ScoreMeasure(
            1,
            listOf(
                ScoreItem.Note(c4, NoteDuration.EIGHTH, tuplet = tRest),
                ScoreItem.Rest(NoteDuration.EIGHTH, tuplet = tRest),
                ScoreItem.Note(e4, NoteDuration.EIGHTH, tuplet = tRest),
            ),
        )
        val restGroups = ScoreEditing.computeTupletGroups(restMeasure, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertTrue(restGroups.single().isFull)
        assertTrue(restGroups.single().drawBracket, "Rest in tuplet requires bracket")

        // 4. Quarter triplet (unbeamed) -> draws bracket
        val tQuarter = ScoreTuplet("tq", 3, 2, baseTicks = NoteDuration.QUARTER.ticks)
        val quarterMeasure = ScoreMeasure(
            1,
            List(3) { ScoreItem.Note(c4, NoteDuration.QUARTER, tuplet = tQuarter) },
        )
        val quarterGroups = ScoreEditing.computeTupletGroups(quarterMeasure, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertTrue(quarterGroups.single().isFull)
        assertTrue(quarterGroups.single().drawBracket, "Unbeamed quarter triplet requires bracket")

        // 5. Partial orange entry (2 of 3 items entered) -> !isFull & orange render color
        val partialMeasure = ScoreMeasure(
            1,
            listOf(
                ScoreItem.Note(c4, NoteDuration.EIGHTH, tuplet = tBeam),
                ScoreItem.Note(e4, NoteDuration.EIGHTH, tuplet = tBeam),
            ),
        )
        val partialGroups = ScoreEditing.computeTupletGroups(partialMeasure, 0, Clef.TREBLE, TimeSignature.COMMON_TIME)
        assertFalse(partialGroups.single().isFull)
        assertTrue(partialGroups.single().drawBracket)

        val partialScore = ScoreData().apply { replaceMeasures(listOf(partialMeasure)) }
        val drawTarget = RecordingDrawTarget()
        val colors = ScoreRenderColors.forTheme(darkMode = false)
        ScoreRenderer.render(
            score = partialScore,
            target = drawTarget,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = colors,
        )
        assertTrue(
            drawTarget.glyphs.any { it.glyph == SmuflGlyphs.tupletNumber("3") && it.color == colors.incompleteTuplet },
            "Incomplete tuplet renders with incompleteTuplet orange color",
        )
    }

    @Test
    fun testTupletPlaybackTimingTiesAndMultiInstrumentSync() {
        val engine = TestEngine()
        val module = ScoreModule()
        val inst1Out = module.outputForInstrument(0)
        val recv1 = VoiceReceiver()
        val graph = GraphState()
        graph.addModule(module, Offset.Zero)
        graph.addModule(recv1, Offset(200f, 0f))
        graph.connect(inst1Out, recv1.inJack)
        module.setEngine(engine)

        // Second instrument to verify measure boundary synchronization when bar 1 uses tuplets
        val inst2 = ScoreInstrumentPart(
            id = "inst-2",
            name = "Viola",
            instrumentType = InstrumentType.BOWED_STRINGS,
            minMidiPitch = 48,
            maxMidiPitch = 88,
            clef = Clef.ALTO,
            jackName = "Viola",
            measures = listOf(
                ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                ScoreMeasure(2, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER))),
            ),
        )
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val triplet = ScoreTuplet("t1", 3, 2)
        // Bar 1 for inst 1: quarter triplet (480 ticks total -> 160 ticks each, tied 1->2) + half note (480 ticks) = 960 ticks
        val inst1 = module.scoreData.instruments[0].copy(
            measures = listOf(
                ScoreMeasure(
                    1,
                    listOf(
                        ScoreItem.Note(c4, NoteDuration.QUARTER, isTiedToNext = true, tuplet = triplet),
                        ScoreItem.Note(c4, NoteDuration.QUARTER, tuplet = triplet),
                        ScoreItem.Note(d4, NoteDuration.QUARTER, tuplet = triplet),
                        ScoreItem.Note(e4, NoteDuration.HALF),
                    ),
                ),
                ScoreMeasure(2, listOf(ScoreItem.Note(g4, NoteDuration.QUARTER))),
            ),
        )
        module.scoreData.timeSignature = TimeSignature.COMMON_TIME // 4/4 = 960 ticks
        module.tempo.receive(Message.FloatValue(ScoreTime(0L), 120.0)) // 240 ticks = 0.5s -> 160 ticks = 1/3 s, 320 ticks = 2/3 s, 480 ticks = 1.0 s, bar 2 = 2.0 s
        module.scoreData.replaceAllInstruments(listOf(inst1, inst2))
        module.syncInstrumentOutputs()

        val recv2 = VoiceReceiver()
        graph.addModule(recv2, Offset(200f, 100f))
        graph.connect(module.outputForInstrument(1), recv2.inJack)

        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        for ((_, action) in engine.scheduled.sortedBy { it.first.nanoseconds }) {
            action()
        }

        val notes1 = recv1.received.filter { it.opcode == Message.VoiceOpcode.NOTE }
        // Note 0 ties into note 1, so only 4 attacks: C4 (320 ticks = 2/3s), D4 (160 ticks = 1/3s), E4 (480 ticks = 1.0s), G4 (bar 2 at 2.0s)
        assertEquals(listOf(60, 62, 64, 67), notes1.map { it.value.toInt() })
        assertEquals(0L, notes1[0].time.nanoseconds)
        assertEquals(2.0 / 3.0, notes1[0].duration, 1e-4)
        assertTrue(kotlin.math.abs(notes1[1].time.nanoseconds - 666_666_667L) < 20_000L)
        assertEquals(1.0 / 3.0, notes1[1].duration, 1e-4)
        assertTrue(kotlin.math.abs(notes1[2].time.nanoseconds - 1_000_000_000L) < 20_000L)
        assertTrue(kotlin.math.abs(notes1[3].time.nanoseconds - 2_000_000_000L) < 20_000L)

        // Inst 2's bar 2 note also starts cleanly at 2.0s (measure boundaries aligned)
        val notes2 = recv2.received.filter { it.opcode == Message.VoiceOpcode.NOTE }
        assertEquals(1, notes2.size)
        assertTrue(kotlin.math.abs(notes2[0].time.nanoseconds - 2_000_000_000L) < 20_000L)
    }

    @Test
    fun testTupletSelectionEditingStepRecordPasteAndSerialization() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)

        // 1. Wrap & unwrap contiguous run via applyTupletOnSelection
        val baseMeasures = listOf(
            ScoreMeasure(
                1,
                listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER),
                    ScoreItem.Note(d4, NoteDuration.QUARTER),
                    ScoreItem.Note(e4, NoteDuration.QUARTER),
                ),
            )
        )
        val sel = setOf(ScoreItemLocation(0, 0), ScoreItemLocation(0, 1), ScoreItemLocation(0, 2))
        val wrapped = ScoreEditing.applyTupletOnSelection(baseMeasures, sel, 3 to 2)
        val tGroup = wrapped[0].items.map { it.tuplet }
        assertTrue(tGroup.all { it != null && it.actualCount == 3 && it.normalCount == 2 })
        assertEquals(1, tGroup.map { it!!.groupId }.distinct().size)

        // Calling again with same ratio unwraps back to normal notes
        val unwrapped = ScoreEditing.applyTupletOnSelection(wrapped, sel, 3 to 2)
        assertTrue(unwrapped[0].items.all { it.tuplet == null })

        // 2. Step recording with 3:2 across >1 measure cleanly rolls over groupId once full
        val module = ScoreModule().apply { autoFillRests = false }
        module.scoreData.timeSignature = TimeSignature(2, 4) // 480 ticks per bar (1 quarter triplet fills 1 bar)
        module.scoreData.replaceMeasures(listOf(ScoreMeasure(1, emptyList())))
        module.stepRecordDuration = NoteDuration.QUARTER
        module.stepRecordTupletRatio = 3 to 2
        module.setStepRecordingEnabled(true)
        module.placeCursor(ScoreCursorPosition(0, 0))

        for (midi in listOf(60, 62, 64, 65)) {
            module.input.receive(
                Message.VoiceMessage(ScoreTime(midi.toLong()), Message.VoiceOpcode.NOTE_ON, tag = midi, value = midi.toDouble(), extra = 0.8)
            )
        }
        assertEquals(3, module.scoreData.measures[0].items.size)
        val m1Groups = module.scoreData.measures[0].items.map { it.tuplet!!.groupId }.distinct()
        assertEquals(1, m1Groups.size)
        assertEquals(1, module.scoreData.measures[1].items.size)
        val m2GroupId = module.scoreData.measures[1].items[0].tuplet!!.groupId
        assertFalse(m2GroupId == m1Groups.single(), "Full triplet in bar 1 starts fresh groupId in bar 2")

        // 3. Copy/paste allocates fresh distinct groupIds
        val clip = ScoreEditing.copySelectionContent(
            measures = module.scoreData.measures,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature(2, 4),
            selectedLocations = sel,
            selectedMeasures = emptySet(),
            selectedClefChange = null,
            selectedDynamics = emptySet(),
        )
        val pasteTargetMeasures = ScoreEditing.appendEmptyMeasure(module.scoreData.measures)
        val pasteResult = ScoreEditing.pasteClipboardContent(
            measures = pasteTargetMeasures,
            content = clip!!,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature(2, 4),
            initialClef = Clef.TREBLE,
            cursorPosition = ScoreCursorPosition(2, 0),
        )!!
        val pastedGroupId = pasteResult.measures[2].items[0].tuplet!!.groupId
        assertFalse(pastedGroupId == m1Groups.single())
        assertFalse(pastedGroupId == m2GroupId)

        // 4. JSON serialization round-trip preserves tuplet metadata accurately
        val serGraph = GraphState()
        serGraph.addModule(module, Offset.Zero)
        module.scoreData.replaceMeasures(wrapped)
        val json = GraphSerializer.serialize(serGraph, serGraph.modules)
        val restoredGraph = GraphState()
        val restoredScore = GraphSerializer.deserialize(restoredGraph, json).filterIsInstance<ScoreModule>().single()
        val restoredItems = restoredScore.scoreData.measures[0].items
        assertEquals(3, restoredItems.size)
        assertTrue(restoredItems.all { it.tuplet == wrapped[0].items[0].tuplet })
    }

    @Test
    fun testIncompleteTupletBarExpandsForEntryAndRestoresNormalWidthWhenFull() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val t = ScoreTuplet("t1", 3, 2, baseTicks = NoteDuration.QUARTER.ticks)
        val incompleteScore = ScoreData().apply {
            replaceMeasures(listOf(ScoreMeasure(1, listOf(ScoreItem.Note(c4, NoteDuration.QUARTER, tuplet = t)))))
        }
        val completeScore = ScoreData().apply {
            replaceMeasures(
                listOf(
                    ScoreMeasure(
                        1,
                        List(3) { ScoreItem.Note(c4, NoteDuration.QUARTER, tuplet = t) },
                    )
                )
            )
        }

        val partialLayout = ScoreRenderer.render(
            score = incompleteScore,
            target = RecordingDrawTarget(),
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            activeTupletRatio = 3 to 2,
        )
        val partialPm = partialLayout.placedMeasures[0]
        val partialTg = partialPm.tupletGroups.single()
        val firstNote = partialPm.items.single()
        val partialAvailableRight = partialPm.barlineX - firstNote.boxRight
        assertTrue(partialAvailableRight > 50f, "Incomplete tuplet bar reserves generous room to the right of note 1")
        assertTrue(partialTg.endX - firstNote.boxRight > 40f, "Orange tuplet bar spans across entry room")

        // Clicking well to the right of note 1 stays inside M1 and does NOT collide with note 1's chord column
        val clickX = firstNote.boxRight + 24f
        val clickY = firstNote.centerY
        assertEquals(null, partialLayout.itemColumnAt(clickX, clickY))
        val target = partialLayout.insertionTargetAt(clickX, clickY)
        assertNotNull(target)
        assertEquals(0, target.measureIdx)
        assertEquals(1, target.itemIdx)
        assertFalse(target.appendNewMeasure)

        // Normal full 3-note tuplet snaps bar width back to tight engraved spacing (no extra trailing padding)
        val fullLayout = ScoreRenderer.render(
            score = completeScore,
            target = RecordingDrawTarget(),
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            activeTupletRatio = 3 to 2,
        )
        val normalLayout = ScoreRenderer.render(
            score = completeScore,
            target = RecordingDrawTarget(),
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            activeTupletRatio = null,
        )
        assertEquals(
            normalLayout.placedMeasures[0].barlineX,
            fullLayout.placedMeasures[0].barlineX,
            0.01f,
            "Once full, the bar uses its normal width",
        )
    }

    @Test
    fun testMidMeasureIncompleteTupletBeforeFollowingNoteExpandsAndJoinsOnInsert() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        // Repro: two quarter notes in a measure, select first note, apply 3:2 tuplet.
        val initialMeasures = listOf(
            ScoreMeasure(
                1,
                listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER),
                    ScoreItem.Note(g4, NoteDuration.QUARTER),
                ),
            )
        )
        val wrapped = ScoreEditing.applyTupletOnSelection(
            measures = initialMeasures,
            selection = setOf(ScoreItemLocation(0, 0)),
            ratio = 3 to 2,
        )
        val score = ScoreData().apply { replaceMeasures(wrapped) }
        val layout = ScoreRenderer.render(
            score = score,
            target = RecordingDrawTarget(),
            staffSpacePx = 10f,
            minWidthPx = 500f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )
        val pm = layout.placedMeasures[0]
        val tg = pm.tupletGroups.single()
        assertFalse(tg.isFull, "Single wrapped quarter note in 3:2 must stay incomplete")
        val firstNote = pm.items[0]
        val secondNote = pm.items[1]
        assertTrue(secondNote.boxLeft - firstNote.boxRight > 60f, "Following quarter note shifts right cleanly")
        assertTrue(tg.endX - firstNote.boxRight > 50f, "Incomplete tuplet bar extends across entry room before Q2")

        // Clicking inside the orange bracket misses Q1's chord hitbox and targets itemIdx = 1
        val clickX = firstNote.boxRight + 24f
        val clickY = firstNote.centerY
        assertEquals(null, layout.itemColumnAt(clickX, clickY))
        val target = layout.insertionTargetAt(clickX, clickY)
        assertNotNull(target)
        assertEquals(0, target.measureIdx)
        assertEquals(1, target.itemIdx)

        // Even with stepRecordTupletRatio == null, clicking under the incomplete bar joins it
        val module = ScoreModule()
        assertEquals(null, module.stepRecordTupletRatio)
        val tupletFor2nd = module.resolveTupletForInsertion(
            measures = wrapped,
            measureIdx = target.measureIdx,
            insertIdx = target.itemIdx,
            baseTicks = NoteDuration.QUARTER.ticks,
            joinPrecedingIncomplete = clickX <= tg.endX + 3.5f,
        )
        assertNotNull(tupletFor2nd)
        assertEquals(wrapped[0].items[0].tuplet!!.groupId, tupletFor2nd.groupId)

        val (after2nd, _) = ScoreEditing.insertItem(
            measures = wrapped,
            measureIdx = 0,
            itemIdx = 1,
            item = ScoreItem.Note(c4, NoteDuration.QUARTER, tuplet = tupletFor2nd),
        )
        val tupletFor3rd = module.resolveTupletForInsertion(
            measures = after2nd,
            measureIdx = 0,
            insertIdx = 2,
            baseTicks = NoteDuration.QUARTER.ticks,
            joinPrecedingIncomplete = true,
        )
        val (after3rd, _) = ScoreEditing.insertItem(
            measures = after2nd,
            measureIdx = 0,
            itemIdx = 2,
            item = ScoreItem.Note(c4, NoteDuration.QUARTER, tuplet = tupletFor3rd),
        )
        val completedGroups = ScoreEditing.computeTupletGroups(
            measure = after3rd[0],
            measureIdx = 0,
            openingClef = Clef.TREBLE,
            timeSignature = TimeSignature.COMMON_TIME,
        )
        assertTrue(completedGroups.single().isFull, "Adding 2nd and 3rd notes completes the 3:2 tuplet")
    }

    @Test
    fun testInKeyAndChromaticTranspositionAcrossMeasuresAndKeys() {
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val fSharp4 = SpelledPitch(NoteLetter.F, 1, 4)
        val rest = ScoreItem.Rest(NoteDuration.QUARTER)

        // Bar 1 in D Major, Bar 2 switches to F Major
        val measures = listOf(
            ScoreMeasure(1, listOf(ScoreItem.Note(d4, NoteDuration.QUARTER), ScoreItem.Note(e4, NoteDuration.QUARTER), rest)),
            ScoreMeasure(
                2,
                listOf(ScoreItem.Note(SpelledPitch(NoteLetter.A, 0, 4), NoteDuration.QUARTER)),
                keySignature = KeySignature.F_MAJOR,
            ),
        )
        val sel = setOf(
            ScoreItemLocation(0, 0),
            ScoreItemLocation(0, 1),
            ScoreItemLocation(0, 2),
            ScoreItemLocation(1, 0),
        )

        // 1. In-key up: D4 -> E4, E4 -> F#4 (D Maj), rest untouched, A4 -> Bb4 (F Maj)
        val (upInKey, remapped1) = ScoreEditing.transposeSelection(
            measures = measures,
            initialKey = KeySignature.D_MAJOR,
            selection = sel,
            steps = 1,
            mode = ScoreTransposeMode.DIATONIC_IN_KEY,
        )
        assertEquals(sel, remapped1)
        assertEquals(e4, (upInKey[0].items[0] as ScoreItem.Note).pitch)
        assertEquals(fSharp4, (upInKey[0].items[1] as ScoreItem.Note).pitch)
        assertTrue(upInKey[0].items[2] is ScoreItem.Rest)
        assertEquals(SpelledPitch(NoteLetter.B, -1, 4), (upInKey[1].items[0] as ScoreItem.Note).pitch)

        // Stepping in-key back down returns cleanly to starting pitches
        val (downInKey, _) = ScoreEditing.transposeSelection(
            measures = upInKey,
            initialKey = KeySignature.D_MAJOR,
            selection = remapped1,
            steps = -1,
            mode = ScoreTransposeMode.DIATONIC_IN_KEY,
        )
        assertEquals(d4, (downInKey[0].items[0] as ScoreItem.Note).pitch)
        assertEquals(e4, (downInKey[0].items[1] as ScoreItem.Note).pitch)
        assertEquals(SpelledPitch(NoteLetter.A, 0, 4), (downInKey[1].items[0] as ScoreItem.Note).pitch)

        // 2. Chromatic semitone up (Shift+Up): D4 -> D#4, E4 -> F4 in D Maj; A4 -> Bb4 in F Maj
        val (upChromatic, _) = ScoreEditing.transposeSelection(
            measures = measures,
            initialKey = KeySignature.D_MAJOR,
            selection = sel,
            steps = 1,
            mode = ScoreTransposeMode.CHROMATIC_SEMITONE,
        )
        assertEquals(SpelledPitch(NoteLetter.D, 1, 4), (upChromatic[0].items[0] as ScoreItem.Note).pitch)
        assertEquals(SpelledPitch(NoteLetter.F, 0, 4), (upChromatic[0].items[1] as ScoreItem.Note).pitch)
        assertEquals(SpelledPitch(NoteLetter.B, -1, 4), (upChromatic[1].items[0] as ScoreItem.Note).pitch)

        // Chromatic down from D4 in C Major hits C#4, and another semitone hits C4
        val cMajMeasures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(d4, NoteDuration.QUARTER))))
        val (step1Down, _) = ScoreEditing.transposeSelection(
            measures = cMajMeasures,
            initialKey = KeySignature.C_MAJOR,
            selection = setOf(ScoreItemLocation(0, 0)),
            steps = -1,
            mode = ScoreTransposeMode.CHROMATIC_SEMITONE,
        )
        assertEquals(SpelledPitch(NoteLetter.C, 1, 4), (step1Down[0].items[0] as ScoreItem.Note).pitch)
        val (step2Down, _) = ScoreEditing.transposeSelection(
            measures = step1Down,
            initialKey = KeySignature.C_MAJOR,
            selection = setOf(ScoreItemLocation(0, 0)),
            steps = -1,
            mode = ScoreTransposeMode.CHROMATIC_SEMITONE,
        )
        assertEquals(SpelledPitch(NoteLetter.C, 0, 4), (step2Down[0].items[0] as ScoreItem.Note).pitch)
    }

    @Test
    fun testTranspositionChordsTiesAndMultiStaffUndoRedo() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val cSharp4 = SpelledPitch(NoteLetter.C, 1, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)

        // 1. Tie preservation when both tied notes transpose together vs break when only one moves
        val tiedMeasures = listOf(
            ScoreMeasure(
                1,
                listOf(
                    ScoreItem.Note(c4, NoteDuration.QUARTER, isTiedToNext = true),
                    ScoreItem.Note(c4, NoteDuration.QUARTER),
                ),
            )
        )
        val (bothTiedMoved, _) = ScoreEditing.transposeSelection(
            measures = tiedMeasures,
            initialKey = KeySignature.C_MAJOR,
            selection = setOf(ScoreItemLocation(0, 0), ScoreItemLocation(0, 1)),
            steps = 1,
            mode = ScoreTransposeMode.DIATONIC_IN_KEY,
        )
        val firstMoved = bothTiedMoved[0].items[0] as ScoreItem.Note
        val secondMoved = bothTiedMoved[0].items[1] as ScoreItem.Note
        assertEquals(d4, firstMoved.pitch)
        assertEquals(d4, secondMoved.pitch)
        assertTrue(firstMoved.isTiedToNext, "Moving both tied notes together keeps tie intact")

        val (onlyFirstMoved, _) = ScoreEditing.transposeSelection(
            measures = bothTiedMoved,
            initialKey = KeySignature.C_MAJOR,
            selection = setOf(ScoreItemLocation(0, 0)),
            steps = 1,
            mode = ScoreTransposeMode.DIATONIC_IN_KEY,
        )
        assertFalse((onlyFirstMoved[0].items[0] as ScoreItem.Note).isTiedToNext, "Pitch mismatch clears broken tie")

        // 2. Selected single chord note moving past neighbor tracks remapped chordNoteIdx; cluster respells cleanly
        val chordMeasure = listOf(
            ScoreMeasure(1, listOf(ScoreItem.Chord(listOf(c4, e4), NoteDuration.QUARTER)))
        )
        val (jumpedPast, jumpedSel) = ScoreEditing.transposeSelection(
            measures = chordMeasure,
            initialKey = KeySignature.C_MAJOR,
            selection = setOf(ScoreItemLocation(0, 0, chordNoteIdx = 0)),
            steps = 3,
            mode = ScoreTransposeMode.DIATONIC_IN_KEY,
        )
        val movedChord = jumpedPast[0].items[0] as ScoreItem.Chord
        assertEquals(listOf(e4, SpelledPitch(NoteLetter.F, 0, 4)), movedChord.pitches)
        assertEquals(setOf(ScoreItemLocation(0, 0, chordNoteIdx = 1)), jumpedSel)

        val (mergedNote, mergedSel) = ScoreEditing.transposeSelection(
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Chord(listOf(d4, e4), NoteDuration.QUARTER)))),
            initialKey = KeySignature.C_MAJOR,
            selection = setOf(ScoreItemLocation(0, 0, chordNoteIdx = 0)),
            steps = 1,
            mode = ScoreTransposeMode.DIATONIC_IN_KEY,
        )
        assertEquals(e4, (mergedNote[0].items[0] as ScoreItem.Note).pitch)
        assertEquals(setOf(ScoreItemLocation(0, 0, chordNoteIdx = null)), mergedSel)

        val cluster = listOf(ScoreMeasure(1, listOf(ScoreItem.Chord(listOf(cSharp4, d4), NoteDuration.QUARTER))))
        val (clusterDown, _) = ScoreEditing.transposeSelection(
            measures = cluster,
            initialKey = KeySignature.C_MAJOR,
            selection = setOf(ScoreItemLocation(0, 0)),
            steps = -1,
            mode = ScoreTransposeMode.CHROMATIC_SEMITONE,
        )
        val clusterChord = clusterDown[0].items[0] as ScoreItem.Chord
        assertEquals(listOf(60, 61), clusterChord.pitches.map { it.midiPitch })

        // 3. Multi-staff rubber-band selection & transposition across multiple instruments with Undo/Redo
        val module = ScoreModule()
        val inst1 = ScoreInstrumentPart(
            id = "inst-1",
            name = "Violin",
            instrumentType = InstrumentType.BOWED_STRINGS,
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(c4, NoteDuration.QUARTER)))),
        )
        val inst2 = ScoreInstrumentPart(
            id = "inst-2",
            name = "Cello",
            instrumentType = InstrumentType.BOWED_STRINGS,
            clef = Clef.BASS,
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 3), NoteDuration.QUARTER)))),
        )
        module.scoreData.keySignature = KeySignature.G_MAJOR
        module.scoreData.replaceAllInstruments(listOf(inst1, inst2))
        module.syncInstrumentOutputs()

        val layout = ScoreRenderer.render(
            score = module.scoreData,
            target = RecordingDrawTarget(),
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )
        val staff1Item = layout.staves[0].placedMeasures[0].items.single()
        val staff2Item = layout.staves[1].placedMeasures[0].items.single()
        val multiSel = layout.itemsInRect(
            left = minOf(staff1Item.boxLeft, staff2Item.boxLeft) - 5f,
            top = staff1Item.boxTop - 5f,
            right = maxOf(staff1Item.boxRight, staff2Item.boxRight) + 5f,
            bottom = staff2Item.boxBottom + 5f,
        )
        assertEquals(2, multiSel.size, "Rubber-band selects notes across both staves")

        val beforeInst = module.scoreData.instruments.toList()
        val afterInst = beforeInst.mapIndexed { iIdx, part ->
            val staffId = ScoreStaffId(iIdx, 0)
            val staffLocs = multiSel.filterTo(mutableSetOf()) { it.staff == staffId }
            val (nextMs, _) = ScoreEditing.transposeSelection(
                measures = part.measures,
                initialKey = module.scoreData.keySignature,
                selection = staffLocs,
                steps = 3,
                mode = ScoreTransposeMode.DIATONIC_IN_KEY,
            )
            part.withStaffMeasures(0, nextMs)
        }
        val cmd = ReplaceScoreInstrumentsCommand(module, beforeInst, afterInst)
        cmd.execute()
        // G Major: C4 + 3 scale steps -> F#4; G3 + 3 scale steps -> C4
        assertEquals(
            SpelledPitch(NoteLetter.F, 1, 4),
            (module.scoreData.instruments[0].measures[0].items[0] as ScoreItem.Note).pitch,
        )
        assertEquals(
            SpelledPitch(NoteLetter.C, 0, 4),
            (module.scoreData.instruments[1].measures[0].items[0] as ScoreItem.Note).pitch,
        )

        cmd.undo()
        assertEquals(c4, (module.scoreData.instruments[0].measures[0].items[0] as ScoreItem.Note).pitch)
        assertEquals(
            SpelledPitch(NoteLetter.G, 0, 3),
            (module.scoreData.instruments[1].measures[0].items[0] as ScoreItem.Note).pitch,
        )
    }

    @Test
    fun testInsertEmptyMeasureBeforeSelectionAndDeleteMultipleMeasuresUndoRedo() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)

        val base = listOf(
            ScoreMeasure(1, listOf(ScoreItem.Note(c4, NoteDuration.QUARTER, isTiedToNext = true))),
            ScoreMeasure(
                2,
                listOf(ScoreItem.Note(c4, NoteDuration.QUARTER)),
                keySignature = KeySignature.D_MAJOR,
                timeSignature = TimeSignature(5, 4),
                clefChanges = mapOf(0 to Clef.BASS),
            ),
            ScoreMeasure(3, listOf(ScoreItem.Note(d4, NoteDuration.QUARTER))),
            ScoreMeasure(4, listOf(ScoreItem.Note(e4, NoteDuration.QUARTER))),
        )

        // 1. Insert before measure 0 (beginning of piece) and before measure 1 (middle, breaks tie from 0->1)
        val atStart = ScoreEditing.insertEmptyMeasureAt(base, 0)
        assertEquals(5, atStart.size)
        assertTrue(atStart[0].items.isEmpty())
        assertEquals(c4, (atStart[1].items.single() as ScoreItem.Note).pitch)
        assertEquals(listOf(1, 2, 3, 4, 5), atStart.map { it.number })

        val beforeMid = ScoreEditing.insertEmptyMeasureAt(base, 1)
        assertEquals(5, beforeMid.size)
        assertFalse((beforeMid[0].items.single() as ScoreItem.Note).isTiedToNext, "Tie clears before inserted empty bar")
        assertTrue(beforeMid[1].items.isEmpty())
        assertEquals(KeySignature.D_MAJOR, beforeMid[2].keySignature)

        // 2. Delete middle non-contiguous group {1, 3} -> leaves bars 0 & 2, clears dangling tie from 0->2 (c4!=d4)
        val delMiddle = ScoreEditing.deleteMeasures(
            initialKey = KeySignature.C_MAJOR,
            initialTime = TimeSignature.COMMON_TIME,
            initialClef = Clef.TREBLE,
            measures = base,
            indicesToDelete = setOf(1, 3),
        )
        assertEquals(2, delMiddle.measures.size)
        assertEquals(KeySignature.C_MAJOR, delMiddle.initialKey)
        assertFalse((delMiddle.measures[0].items.single() as ScoreItem.Note).isTiedToNext)
        assertEquals(d4, (delMiddle.measures[1].items.single() as ScoreItem.Note).pitch)

        // 3. Delete measure 0 -> promotes measure 1's key/time/clef cleanly with multi-instrument undo/redo
        val module = ScoreModule()
        val inst1 = ScoreInstrumentPart(
            id = "inst-1",
            name = "Inst 1",
            instrumentType = InstrumentType.KEYBOARDS,
            clef = Clef.TREBLE,
            measures = base,
        )
        module.scoreData.replaceAllInstruments(listOf(inst1))
        val delFirst = ScoreEditing.deleteMeasures(
            initialKey = KeySignature.C_MAJOR,
            initialTime = TimeSignature.COMMON_TIME,
            initialClef = inst1.clef,
            measures = inst1.measures,
            indicesToDelete = setOf(0),
        )
        assertEquals(3, delFirst.measures.size)
        assertEquals(KeySignature.D_MAJOR, delFirst.initialKey)
        assertEquals(TimeSignature(5, 4), delFirst.initialTime)
        assertEquals(Clef.BASS, delFirst.initialClef)
        assertEquals(null, delFirst.measures[0].keySignature)
        assertEquals(null, delFirst.measures[0].timeSignature)
        assertFalse(0 in delFirst.measures[0].clefChanges)

        val cmd = ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = listOf(inst1),
            afterInstruments = listOf(inst1.copy(clef = delFirst.initialClef, measures = delFirst.measures)),
            beforeKeySignature = KeySignature.C_MAJOR,
            afterKeySignature = delFirst.initialKey,
            beforeTimeSignature = TimeSignature.COMMON_TIME,
            afterTimeSignature = delFirst.initialTime,
        )
        cmd.execute()
        assertEquals(KeySignature.D_MAJOR, module.scoreData.keySignature)
        assertEquals(TimeSignature(5, 4), module.scoreData.timeSignature)
        assertEquals(Clef.BASS, module.scoreData.instruments[0].clef)

        cmd.undo()
        assertEquals(KeySignature.C_MAJOR, module.scoreData.keySignature)
        assertEquals(TimeSignature.COMMON_TIME, module.scoreData.timeSignature)
        assertEquals(Clef.TREBLE, module.scoreData.instruments[0].clef)
        assertEquals(4, module.scoreData.instruments[0].measures.size)

        // 4. Deleting all bars leaves one clean empty measure
        val delAll = ScoreEditing.deleteMeasures(
            initialKey = KeySignature.C_MAJOR,
            initialTime = TimeSignature.COMMON_TIME,
            initialClef = Clef.TREBLE,
            measures = base,
            indicesToDelete = setOf(0, 1, 2, 3),
        )
        assertEquals(1, delAll.measures.size)
        assertTrue(delAll.measures[0].items.isEmpty())
    }

    @Test
    fun testFillMeasuresWithRestsSimpleMetersAndBoundaryCases() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.EIGHTH)

        // 4/4 with 3 quarter notes -> adds 1 quarter rest
        val threeQuarters = ScoreMeasure(1, listOf(c4, c4, c4))
        val filledQuarter = ScoreEditing.fillMeasureWithRests(threeQuarters, TimeSignature.COMMON_TIME)
        assertEquals(4, filledQuarter.items.size)
        assertEquals(ScoreItem.Rest(NoteDuration.QUARTER), filledQuarter.items.last())
        assertEquals(RationalTicks.of(960), ScoreEditing.measureExactTicks(filledQuarter))

        // 4/4 with 1 quarter note -> Quarter rest (beat 2) + Half rest (beats 3-4)
        val oneQuarter = ScoreMeasure(1, listOf(c4))
        val filledBeatAligned = ScoreEditing.fillMeasureWithRests(oneQuarter, TimeSignature.COMMON_TIME)
        assertEquals(
            listOf(
                c4,
                ScoreItem.Rest(NoteDuration.QUARTER),
                ScoreItem.Rest(NoteDuration.HALF),
            ),
            filledBeatAligned.items,
        )

        // 4/4 with 1 eighth note -> Eighth + Quarter + Half rests
        val oneEighth = ScoreMeasure(1, listOf(e4))
        val filledEighth = ScoreEditing.fillMeasureWithRests(oneEighth, TimeSignature.COMMON_TIME)
        assertEquals(
            listOf(
                e4,
                ScoreItem.Rest(NoteDuration.EIGHTH),
                ScoreItem.Rest(NoteDuration.QUARTER),
                ScoreItem.Rest(NoteDuration.HALF),
            ),
            filledEighth.items,
        )

        // 4/4 empty measure -> Whole rest
        val emptyFourFour = ScoreMeasure(1, emptyList())
        val filledEmpty = ScoreEditing.fillMeasureWithRests(emptyFourFour, TimeSignature.COMMON_TIME)
        assertEquals(listOf(ScoreItem.Rest(NoteDuration.WHOLE)), filledEmpty.items)

        // Full and overfull measures stay untouched
        val fullBar = ScoreMeasure(1, listOf(c4, c4, c4, c4))
        assertEquals(fullBar, ScoreEditing.fillMeasureWithRests(fullBar, TimeSignature.COMMON_TIME))
        val overfullBar = ScoreMeasure(1, listOf(c4, c4, c4, c4, c4))
        assertEquals(overfullBar, ScoreEditing.fillMeasureWithRests(overfullBar, TimeSignature.COMMON_TIME))
    }

    @Test
    fun testFillMeasuresWithRestsEffectiveTimeCompoundAndIncompleteTuplets() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.EIGHTH)
        val triplet = ScoreTuplet(groupId = "t1", actualCount = 3, normalCount = 2, baseTicks = NoteDuration.EIGHTH.ticks)
        val tripletNote = ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.EIGHTH, tuplet = triplet)

        val measures = listOf(
            // Measure 1 (4/4): incomplete triplet (2 of 3 eighth notes = 160 ticks) -> triplet eighth rest + Quarter + Half
            ScoreMeasure(1, listOf(tripletNote, tripletNote)),
            // Measure 2 (6/8): empty -> two dotted-quarter beat rests
            ScoreMeasure(2, emptyList(), timeSignature = TimeSignature.SIX_EIGHT),
            // Measure 3 (6/8): 1 eighth note -> eighth + eighth + dotted quarter
            ScoreMeasure(3, listOf(e4)),
            // Measure 4 (3/4): 1 quarter note -> quarter + quarter
            ScoreMeasure(4, listOf(c4), timeSignature = TimeSignature.THREE_FOUR),
        )

        val filled = ScoreEditing.fillMeasuresWithRests(measures, TimeSignature.COMMON_TIME)
        assertEquals(4, filled.size)

        assertEquals(
            listOf(
                tripletNote,
                tripletNote,
                ScoreItem.Rest(NoteDuration.EIGHTH, tuplet = triplet),
                ScoreItem.Rest(NoteDuration.QUARTER),
                ScoreItem.Rest(NoteDuration.HALF),
            ),
            filled[0].items,
        )
        assertEquals(RationalTicks.of(960), ScoreEditing.measureExactTicks(filled[0]))

        assertEquals(
            listOf(
                ScoreItem.Rest(NoteDuration.QUARTER, isDotted = true),
                ScoreItem.Rest(NoteDuration.QUARTER, isDotted = true),
            ),
            filled[1].items,
        )
        assertEquals(RationalTicks.of(720), ScoreEditing.measureExactTicks(filled[1]))

        assertEquals(
            listOf(
                e4,
                ScoreItem.Rest(NoteDuration.EIGHTH),
                ScoreItem.Rest(NoteDuration.EIGHTH),
                ScoreItem.Rest(NoteDuration.QUARTER, isDotted = true),
            ),
            filled[2].items,
        )
        assertEquals(RationalTicks.of(720), ScoreEditing.measureExactTicks(filled[2]))

        assertEquals(
            listOf(
                c4,
                ScoreItem.Rest(NoteDuration.QUARTER),
                ScoreItem.Rest(NoteDuration.QUARTER),
            ),
            filled[3].items,
        )
        assertEquals(RationalTicks.of(720), ScoreEditing.measureExactTicks(filled[3]))
    }

    @Test
    fun testAltTupletShortcutsWorkAcrossMacOsOptionAndPhysicalKeys() {
        // Physical Key.Three with macOS Option+3 char '£', undefined char 0, or ASCII '3'
        assertEquals(ScoreAltTupletAction.SetRatio(3 to 2), resolveAltTupletShortcut(Key.Three, '£'.code))
        assertEquals(ScoreAltTupletAction.SetRatio(3 to 2), resolveAltTupletShortcut(Key.Three, 0))
        assertEquals(ScoreAltTupletAction.SetRatio(3 to 2), resolveAltTupletShortcut(Key.Three, '3'.code))
        assertEquals(ScoreAltTupletAction.SetRatio(3 to 2), resolveAltTupletShortcut(Key.NumPad3, 0))

        // Other Option-translated presets on macOS
        assertEquals(ScoreAltTupletAction.SetRatio(2 to 3), resolveAltTupletShortcut(Key.Two, '™'.code))
        assertEquals(ScoreAltTupletAction.SetRatio(5 to 4), resolveAltTupletShortcut(Key.Five, '∞'.code))
        assertEquals(ScoreAltTupletAction.SetRatio(7 to 4), resolveAltTupletShortcut(Key.Seven, '¶'.code))
        assertEquals(ScoreAltTupletAction.SetRatio(9 to 8), resolveAltTupletShortcut(Key.Nine, 'ª'.code))

        // Turn tuplet mode off with 0/1 even under macOS Option translation
        assertEquals(ScoreAltTupletAction.SetRatio(null), resolveAltTupletShortcut(Key.Zero, 'º'.code))
        assertEquals(ScoreAltTupletAction.SetRatio(null), resolveAltTupletShortcut(Key.One, '¡'.code))

        // Alt+N custom tuplet dialog even with macOS dead-key tilde / zero utf16CodePoint
        assertEquals(ScoreAltTupletAction.OpenCustomDialog, resolveAltTupletShortcut(Key.N, '\u02dc'.code))
        assertEquals(ScoreAltTupletAction.OpenCustomDialog, resolveAltTupletShortcut(Key.N, 0))
        assertEquals(ScoreAltTupletAction.OpenCustomDialog, resolveAltTupletShortcut(Key.N, 'n'.code))

        // Character-only fallback if Key enum is Unknown
        assertEquals(ScoreAltTupletAction.SetRatio(3 to 2), resolveAltTupletShortcut(Key.Unknown, '£'.code))
        assertNull(resolveAltTupletShortcut(Key.E, 'e'.code))
    }

    @Test
    fun testTripletEntryModeEighthNoteRendersGhostAndTupletEntryBar() {
        val module = ScoreModule()
        module.scoreData.keySignature = KeySignature.C_MAJOR
        module.scoreData.timeSignature = TimeSignature.COMMON_TIME
        module.scoreData.replaceMeasures(listOf(ScoreMeasure(1, emptyList())))
        module.stepRecordDuration = NoteDuration.EIGHTH
        module.stepRecordTupletRatio = 3 to 2

        val target = RecordingDrawTarget()
        val colors = ScoreRenderColors.forTheme(darkMode = false)

        // Hovering before first note draws ghost note + orange '3' tuplet bracket
        val hoverTarget = RecordingDrawTarget()
        val preLayout = ScoreRenderer.render(
            score = module.scoreData,
            target = hoverTarget,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = colors,
            ghostPreview = ScoreGhostPreview(
                x = 120f,
                staffStep = 2,
                duration = NoteDuration.EIGHTH,
                isRest = false,
                tupletText = ScoreTuplet("cursor", 3, 2).displayText(module.scoreData.timeSignature),
            ),
            activeTupletRatio = module.stepRecordTupletRatio,
        )
        assertTrue(preLayout.placedMeasures.isNotEmpty())
        assertTrue(
            hoverTarget.glyphs.any {
                it.glyph == SmuflGlyphs.tupletNumber("3") && it.color == colors.incompleteTuplet
            },
            "Ghost preview in triplet mode should draw orange '3'",
        )

        // Clicking inserts 1st eighth note with active triplet -> orange incomplete tuplet entry bar
        val insertedTuplet = module.resolveTupletForInsertion(
            measures = module.scoreData.measures,
            measureIdx = 0,
            insertIdx = 0,
            baseTicks = NoteDuration.EIGHTH.ticks,
        )
        assertNotNull(insertedTuplet)
        assertEquals(3, insertedTuplet.actualCount)
        assertEquals(2, insertedTuplet.normalCount)

        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val (afterFirst, _) = ScoreEditing.insertItem(
            measures = module.scoreData.measures,
            measureIdx = 0,
            itemIdx = 0,
            item = ScoreItem.Note(g4, NoteDuration.EIGHTH, tuplet = insertedTuplet),
        )
        module.scoreData.replaceMeasures(afterFirst)

        val postLayout = ScoreRenderer.render(
            score = module.scoreData,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 400f,
            colors = colors,
            activeTupletRatio = module.stepRecordTupletRatio,
        )
        val tg = postLayout.placedMeasures.single().tupletGroups.single()
        assertFalse(tg.isFull, "Single triplet eighth note should stay an incomplete tuplet entry bar")
        assertTrue(tg.drawBracket, "Incomplete triplet should show the tuplet entry bracket")
        assertTrue(
            target.glyphs.any { it.glyph == SmuflGlyphs.tupletNumber("3") && it.color == colors.incompleteTuplet },
            "Measure with 1st triplet eighth should display orange '3' tuplet entry bar",
        )
    }

    @Test
    fun testDeleteNoteReplacesWithSelectedRestAndDeleteRestShiftsFollowingNote() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val n1 = ScoreItem.Note(c4, NoteDuration.QUARTER)
        val n2 = ScoreItem.Note(d4, NoteDuration.QUARTER)
        val chord = ScoreItem.Chord(listOf(c4, e4), NoteDuration.HALF)

        val measures = listOf(ScoreMeasure(1, listOf(n1, n2, chord)))

        // 1. Deleting a note replaces it in-place with a rest of the same duration and leaves it selected
        val res1 = ScoreEditing.deleteSelectionItems(measures, setOf(ScoreItemLocation(0, 0)))
        assertEquals(
            listOf(ScoreItem.Rest(NoteDuration.QUARTER), n2, chord),
            res1.measures[0].items,
        )
        assertEquals(setOf(ScoreItemLocation(0, 0)), res1.selectedRestLocations)

        // 2. Deleting that selected rest removes it and moves the following note (n2) to index 0
        val res2 = ScoreEditing.deleteSelectionItems(res1.measures, res1.selectedRestLocations)
        assertEquals(listOf(n2, chord), res2.measures[0].items)
        assertTrue(res2.selectedRestLocations.isEmpty())

        // 3. Deleting a single pitch of a chord reduces to a note without replacing with a rest
        val resChordPitch = ScoreEditing.deleteSelectionItems(
            res2.measures,
            setOf(ScoreItemLocation(0, 1, chordNoteIdx = 0)),
        )
        assertEquals(listOf(n2, ScoreItem.Note(e4, NoteDuration.HALF)), resChordPitch.measures[0].items)
        assertTrue(resChordPitch.selectedRestLocations.isEmpty())

        // 4. Deleting a whole chord replaces it with a rest of the same duration and selects it
        val resWholeChord = ScoreEditing.deleteSelectionItems(
            res2.measures,
            setOf(ScoreItemLocation(0, 1)),
        )
        assertEquals(listOf(n2, ScoreItem.Rest(NoteDuration.HALF)), resWholeChord.measures[0].items)
        assertEquals(setOf(ScoreItemLocation(0, 1)), resWholeChord.selectedRestLocations)
    }

    @Test
    fun testAutoFillRestsDefaultOnAddingNotesStepRecordAndSerialization() {
        val module = ScoreModule()
        assertTrue(module.autoFillRests)
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val f4 = SpelledPitch(NoteLetter.F, 0, 4)

        // Adding an eighth note into a whole-rest bar starts with aligned smaller rest (8th) then larger rests (Q, H)
        val (afterEighth, locE) = ScoreEditing.insertItemWithAutoFillRests(
            measures = module.scoreData.measures,
            measureIdx = 0,
            itemIdx = 0,
            item = ScoreItem.Note(c4, NoteDuration.EIGHTH),
            initialTimeSignature = module.scoreData.timeSignature,
        )
        assertEquals(ScoreItemLocation(0, 0), locE)
        assertEquals(
            listOf(
                ScoreItem.Note(c4, NoteDuration.EIGHTH),
                ScoreItem.Rest(NoteDuration.EIGHTH),
                ScoreItem.Rest(NoteDuration.QUARTER),
                ScoreItem.Rest(NoteDuration.HALF),
            ),
            afterEighth[0].items,
        )

        // Step recording 4 quarter notes into auto-filled whole-rest bar advances through rest slots then to bar 2
        module.placeCursor(ScoreCursorPosition(0, 0))
        module.stepRecordDuration = NoteDuration.QUARTER
        for (pitch in listOf(c4, d4, e4, f4)) {
            val res = ScoreEditing.stepInsertItem(
                measures = module.scoreData.measures,
                cursor = module.cursorPosition!!,
                item = ScoreItem.Note(pitch, NoteDuration.QUARTER),
                measureCapacityTicks = module.scoreData.timeSignature.measureCapacityTicks,
                autoFillRests = module.autoFillRests,
                initialTimeSignature = module.scoreData.timeSignature,
            )
            module.scoreData.replaceMeasures(res.measures)
            module.placeCursor(res.nextCursor)
        }
        assertEquals(4, module.scoreData.measures[0].items.size)
        assertTrue(module.scoreData.measures[0].items.all { it is ScoreItem.Note })
        assertEquals(ScoreCursorPosition(1, 0), module.cursorPosition)

        // Replacing a beat-2 quarter rest with a 16th note re-aligns trailing rests to [16th, 8th, Half]
        val (afterQuarter, _) = ScoreEditing.insertItemWithAutoFillRests(
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE)))),
            measureIdx = 0,
            itemIdx = 0,
            item = ScoreItem.Note(c4, NoteDuration.QUARTER),
            initialTimeSignature = TimeSignature.COMMON_TIME,
        )
        assertEquals(
            listOf(
                ScoreItem.Note(c4, NoteDuration.QUARTER),
                ScoreItem.Rest(NoteDuration.QUARTER),
                ScoreItem.Rest(NoteDuration.HALF),
            ),
            afterQuarter[0].items,
        )
        val afterSixteenthReplace = ScoreEditing.replaceItemWithAutoFillRests(
            measures = afterQuarter,
            loc = ScoreItemLocation(0, 1),
            item = ScoreItem.Note(d4, NoteDuration.SIXTEENTH),
            initialTimeSignature = TimeSignature.COMMON_TIME,
        )
        assertEquals(
            listOf(
                ScoreItem.Note(c4, NoteDuration.QUARTER),
                ScoreItem.Note(d4, NoteDuration.SIXTEENTH),
                ScoreItem.Rest(NoteDuration.SIXTEENTH),
                ScoreItem.Rest(NoteDuration.EIGHTH),
                ScoreItem.Rest(NoteDuration.HALF),
            ),
            afterSixteenthReplace[0].items,
        )

        // GraphSerializer preserves autoFillRests = false
        module.autoFillRests = false
        val graph = GraphState()
        graph.addModule(module, Offset.Zero)
        val json = GraphSerializer.serialize(graph, graph.modules)
        val restored = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertFalse(restored.autoFillRests)
    }

    @Test
    fun testStaccatoAccidentalAndDurationEditsWorkOnLowerAndSecondaryStaves() {
        val f3 = SpelledPitch(NoteLetter.F, 0, 3)
        val g3 = SpelledPitch(NoteLetter.G, 0, 3)
        val staffMeasures = listOf(
            ScoreMeasure(
                number = 1,
                items = listOf(
                    ScoreItem.Note(f3, NoteDuration.QUARTER, isStaccato = false),
                    ScoreItem.Chord(listOf(f3, g3), NoteDuration.QUARTER, isStaccato = false),
                ),
            )
        )
        val lowerStaffId = ScoreStaffId(instrumentIdx = 0, staffIdx = 1)
        val secondInstStaffId = ScoreStaffId(instrumentIdx = 1, staffIdx = 0)
        val selLower = setOf(ScoreItemLocation(0, 0, staff = lowerStaffId))
        val selChordPitch = setOf(ScoreItemLocation(0, 1, chordNoteIdx = 1, staff = secondInstStaffId))

        // Staccato toggles ON and OFF when selection carries non-(0,0) ScoreStaffId
        val staccOn = ScoreEditing.toggleStaccatoOnSelection(staffMeasures, selLower)
        assertTrue((staccOn[0].items[0] as ScoreItem.Note).isStaccato)
        val staccOff = ScoreEditing.toggleStaccatoOnSelection(staccOn, selLower)
        assertFalse((staccOff[0].items[0] as ScoreItem.Note).isStaccato)

        val chordStacc = ScoreEditing.toggleStaccatoOnSelection(staffMeasures, selChordPitch)
        assertTrue((chordStacc[0].items[1] as ScoreItem.Chord).isStaccato)

        // Accidental & duration edits also work on non-(0,0) staves
        val sharpened = ScoreEditing.setAccidentalOnSelection(staccOn, selLower, 1)
        assertEquals(1, (sharpened[0].items[0] as ScoreItem.Note).pitch.alter)
        val resized = ScoreEditing.setDurationOnSelection(sharpened, selLower, NoteDuration.EIGHTH, isDotted = true)
        val resizedNote = resized[0].items[0] as ScoreItem.Note
        assertEquals(NoteDuration.EIGHTH, resizedNote.duration)
        assertTrue(resizedNote.isDotted)
        assertTrue(resizedNote.isStaccato)

        // ScoreRenderer draws the staccato glyph on the lower staff
        val score = ScoreData().apply {
            replaceAllInstruments(
                listOf(
                    ScoreInstrumentPart(
                        id = "inst-1",
                        name = "Piano",
                        staffCount = 2,
                        clef = Clef.TREBLE,
                        lowerClef = Clef.BASS,
                        measures = listOf(ScoreMeasure(1, emptyList())),
                        lowerMeasures = staccOn,
                    )
                )
            )
        }
        val target = RecordingDrawTarget()
        ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
        )
        assertTrue(
            target.glyphs.any {
                it.glyph == SmuflGlyphs.ARTIC_STACCATO_ABOVE || it.glyph == SmuflGlyphs.ARTIC_STACCATO_BELOW
            },
            "Rendered lower staff should draw staccato articulation glyph",
        )
    }

    @Test
    fun testPhase22CrossStaffRhythmicAlignmentViolinViolaRepro() {
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val a4 = SpelledPitch(NoteLetter.A, 0, 4)
        val c5 = SpelledPitch(NoteLetter.C, 0, 5)
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)

        val violinM1 = ScoreMeasure(
            1,
            List(8) { idx -> ScoreItem.Note(if (idx % 2 == 0) g4 else a4, NoteDuration.SIXTEENTH) } +
                listOf(ScoreItem.Note(c5, NoteDuration.QUARTER), ScoreItem.Note(c5, NoteDuration.QUARTER)),
        )
        val violinM2 = ScoreMeasure(
            2,
            listOf(ScoreItem.Note(c5, NoteDuration.HALF), ScoreItem.Note(c5, NoteDuration.HALF)),
        )

        val violaM1 = ScoreMeasure(
            1,
            List(4) { ScoreItem.Note(c4, NoteDuration.QUARTER) },
        )
        val violaM2 = ScoreMeasure(
            2,
            listOf(
                ScoreItem.Note(c4, NoteDuration.EIGHTH),
                ScoreItem.Note(d4, NoteDuration.EIGHTH),
            ) + List(8) { ScoreItem.Note(c4, NoteDuration.THIRTY_SECOND) } + listOf(
                ScoreItem.Note(d4, NoteDuration.QUARTER),
                ScoreItem.Note(c4, NoteDuration.QUARTER),
            ),
        )

        val score = ScoreData().apply {
            replaceAllInstruments(
                listOf(
                    ScoreInstrumentPart(
                        id = "violin",
                        name = "Violin",
                        instrumentType = InstrumentType.BOWED_STRINGS,
                        staffCount = 1,
                        clef = Clef.TREBLE,
                        measures = listOf(violinM1, violinM2),
                    ),
                    ScoreInstrumentPart(
                        id = "viola",
                        name = "Viola",
                        instrumentType = InstrumentType.BOWED_STRINGS,
                        staffCount = 1,
                        clef = Clef.ALTO,
                        measures = listOf(violaM1, violaM2),
                    ),
                )
            )
        }

        val layout = ScoreRenderer.computeLayout(score, staffSpacePx = 12f, minWidthPx = 1000f) { _, fs -> fs * 0.35f }
        val vlnM1 = layout.staves[0].placedMeasures[0].items
        val vlaM1 = layout.staves[1].placedMeasures[0].items
        val vlnM2 = layout.staves[0].placedMeasures[1].items
        val vlaM2 = layout.staves[1].placedMeasures[1].items

        // Bar 1: Beats 1, 2, 3, 4 align vertically between Violin and Viola
        assertEquals(vlnM1[0].x, vlaM1[0].x, 0.01f)
        assertEquals(vlnM1[4].x, vlaM1[1].x, 0.01f)
        assertEquals(vlnM1[8].x, vlaM1[2].x, 0.01f)
        assertEquals(vlnM1[9].x, vlaM1[3].x, 0.01f)
        // Beat 1 (4x16th) is wider than Beat 3 (1xQ)
        assertTrue(vlaM1[1].x - vlaM1[0].x > vlaM1[3].x - vlaM1[2].x)

        // Bar 2: Beat 1 (H1 / 8th1) and Beat 3 (H2 / Q1) align vertically
        assertEquals(vlnM2[0].x, vlaM2[0].x, 0.01f)
        assertEquals(vlnM2[1].x, vlaM2[10].x, 0.01f)

        // All adjacent items have non-overlapping visual bounds and synchronized barlines
        for (i in 0 until vlaM2.lastIndex) {
            assertTrue(vlaM2[i].boxRight < vlaM2[i + 1].boxLeft, "Viola Bar 2 items $i and ${i + 1} must not overlap")
        }
        assertEquals(
            layout.staves[0].placedMeasures[0].barlineX,
            layout.staves[1].placedMeasures[0].barlineX,
            0.01f,
        )
        assertEquals(
            layout.staves[0].placedMeasures[1].barlineX,
            layout.staves[1].placedMeasures[1].barlineX,
            0.01f,
        )
    }

    @Test
    fun testPhase22AccidentalsClefsAndPolyrhythmsPreserveCrossStaffAlignment() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val cSharp4 = SpelledPitch(NoteLetter.C, 1, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val fSharp4 = SpelledPitch(NoteLetter.F, 1, 4)
        val gSharp4 = SpelledPitch(NoteLetter.G, 1, 4)
        val triplet = ScoreTuplet("t1", actualCount = 3, normalCount = 2, baseTicks = NoteDuration.EIGHTH.ticks)

        // Staff 0: Beat 1 16th + Beat 1.25 chord with stacked sharps + Beat 2 mid-measure clef + Beat 1..2 triplets in bar 2
        val s0m1 = ScoreMeasure(
            number = 1,
            items = listOf(
                ScoreItem.Note(c4, NoteDuration.SIXTEENTH),
                ScoreItem.Chord(listOf(cSharp4, fSharp4, gSharp4), NoteDuration.SIXTEENTH),
                ScoreItem.Note(d4, NoteDuration.EIGHTH),
                ScoreItem.Note(e4, NoteDuration.QUARTER),
                ScoreItem.Note(d4, NoteDuration.HALF),
            ),
            clefChanges = mapOf(3 to Clef.BASS),
        )
        // Staff 1: Two 16ths on Beat 1 (unaccented) + Quarter on Beat 2 (aligns with S0 Beat 2 clef+note) + Half on Beat 3
        val s1m1 = ScoreMeasure(
            number = 1,
            items = listOf(
                ScoreItem.Note(c4, NoteDuration.SIXTEENTH),
                ScoreItem.Note(d4, NoteDuration.SIXTEENTH),
                ScoreItem.Note(e4, NoteDuration.EIGHTH),
                ScoreItem.Note(c4, NoteDuration.QUARTER),
                ScoreItem.Note(d4, NoteDuration.HALF),
            ),
        )
        // Measure 2: Polyrhythm 3 triplet 8ths vs 2 regular 8ths on Beat 1, followed by Beat 2 Quarter on both staves
        val s0m2 = ScoreMeasure(
            number = 2,
            items = List(3) { ScoreItem.Note(c4, NoteDuration.EIGHTH, tuplet = triplet) } +
                listOf(ScoreItem.Note(d4, NoteDuration.QUARTER)),
        )
        val s1m2 = ScoreMeasure(
            number = 2,
            items = listOf(
                ScoreItem.Note(c4, NoteDuration.EIGHTH),
                ScoreItem.Note(d4, NoteDuration.EIGHTH),
                ScoreItem.Note(e4, NoteDuration.QUARTER),
            ),
        )

        val score = ScoreData().apply {
            replaceAllInstruments(
                listOf(
                    ScoreInstrumentPart(
                        id = "piano",
                        name = "Piano",
                        staffCount = 2,
                        clef = Clef.TREBLE,
                        lowerClef = Clef.TREBLE,
                        measures = listOf(s0m1, s0m2),
                        lowerMeasures = listOf(s1m1, s1m2),
                    )
                )
            )
        }

        val layout = ScoreRenderer.computeLayout(score, staffSpacePx = 10f, minWidthPx = 900f) { _, fs -> fs * 0.35f }
        val topM1 = layout.staves[0].placedMeasures[0]
        val botM1 = layout.staves[1].placedMeasures[0]

        // Noteheads stay aligned at beat 1.25 despite stacked sharps only on top staff
        assertEquals(topM1.items[1].x, botM1.items[1].x, 0.01f)
        // Accidentals on topM1[1] clear topM1[0] cleanly without colliding
        assertTrue(topM1.items[0].boxRight < topM1.items[1].boxLeft)

        // Noteheads stay aligned at beat 2 despite mid-measure clef change before topM1[3]
        assertEquals(topM1.items[3].x, botM1.items[3].x, 0.01f)
        val midClef = topM1.clefChanges.single { it.itemIdx == 3 }
        assertTrue(topM1.items[2].boxRight < midClef.x, "Mid-measure clef must clear preceding note")
        assertTrue(midClef.x + midClef.width < topM1.items[3].boxLeft, "Mid-measure clef must precede Beat 2 note")

        // Polyrhythm Measure 2: Beat 1 (t=0) and Beat 2 (t=480) align across both staves, no overlaps
        val topM2 = layout.staves[0].placedMeasures[1].items
        val botM2 = layout.staves[1].placedMeasures[1].items
        assertEquals(topM2[0].x, botM2[0].x, 0.01f)
        assertEquals(topM2[3].x, botM2[2].x, 0.01f)
        assertTrue(topM2[1].x < botM2[1].x && botM2[1].x < topM2[2].x, "Regular 8th sits strictly between triplet 8ths 2 and 3")
        for (i in 0 until topM2.lastIndex) {
            assertTrue(topM2[i].boxRight < topM2[i + 1].boxLeft)
        }
        for (i in 0 until botM2.lastIndex) {
            assertTrue(botM2[i].boxRight < botM2[i + 1].boxLeft)
        }
    }

    @Test
    fun testPhase23LoopRangeClampingAndLayoutBarHelpers() {
        val module = ScoreModule()
        assertFalse(module.loopMode)
        assertEquals(0, module.loopStartMeasure)
        assertEquals(ScoreModule.DEFAULT_LOOP_END_MEASURE, module.loopEndMeasure)
        assertEquals(0..3, module.effectiveLoopRange())

        module.toggleLoopMode()
        assertTrue(module.loopMode)

        // Setting reversed or out-of-bounds indices clamps cleanly to valid contiguous bounds
        module.setLoopRange(-2, 99)
        assertEquals(0, module.loopStartMeasure)
        assertEquals(7, module.loopEndMeasure)
        module.setLoopRange(5, 2)
        assertEquals(5, module.loopStartMeasure)
        assertEquals(5, module.loopEndMeasure)
        assertEquals(2..2, module.effectiveLoopRange(measureCount = 3))

        module.setLoopRange(1, 3)
        val layout = ScoreRenderer.computeLayout(module.scoreData, staffSpacePx = 10f, minWidthPx = 900f) { _, fs ->
            fs * 0.35f
        }
        val (m0Left, m0Right) = layout.measureSpanX(0)!!
        val (m1Left, _) = layout.measureSpanX(1)!!
        val (_, m3Right) = layout.measureSpanX(3)!!
        assertEquals(m0Right, m1Left, 0.01f)
        val (loopLeft, loopRight) = layout.loopRangeSpanX(1..3)!!
        assertEquals(m1Left, loopLeft, 0.01f)
        assertEquals(m3Right, loopRight, 0.01f)

        // Gutter clicks before Bar 1 return null; interior clicks return 0-based measure index
        assertNull(layout.measureIndexForLoopBarX(m0Left - 20f, tolerancePx = 8f))
        assertEquals(0, layout.measureIndexForLoopBarX((m0Left + m0Right) * 0.5f))
        assertEquals(2, layout.measureIndexForLoopBarX((layout.measureSpanX(2)!!.first + layout.measureSpanX(2)!!.second) * 0.5f))

        // Handle snapping stays constrained by opposite handle
        assertEquals(2, layout.snapLoopStartMeasureForX(layout.measureSpanX(2)!!.first + 1f, loopEndMeasure = 3))
        assertEquals(3, layout.snapLoopStartMeasureForX(m3Right + 50f, loopEndMeasure = 3))
        assertEquals(2, layout.snapLoopEndMeasureForX(layout.measureSpanX(2)!!.second - 1f, loopStartMeasure = 1))
        assertEquals(1, layout.snapLoopEndMeasureForX(m0Left - 20f, loopStartMeasure = 1))
    }

    @Test
    fun testPhase23LoopPlaybackRewindsAtEndOfLoopSectionAndClampsTies() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)
        val module = ScoreModule()
        val engine = TestEngine()
        module.setEngine(engine)
        val receiver = VoiceReceiver()
        module.output.connect(receiver.inJack)

        // 4 bars in 4/4 at 120 BPM (2.0s per bar):
        // Bar 1: C4 Q
        // Bar 2: D4 H + E4 H (tied across barline into Bar 3!)
        // Bar 3: E4 Q + F4 Q
        // Bar 4: G4 Q
        module.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(1, listOf(ScoreItem.Note(c4, NoteDuration.QUARTER))),
                ScoreMeasure(
                    2,
                    listOf(
                        ScoreItem.Note(d4, NoteDuration.HALF),
                        ScoreItem.Note(e4, NoteDuration.HALF, isTiedToNext = true),
                    ),
                ),
                ScoreMeasure(
                    3,
                    listOf(
                        ScoreItem.Note(e4, NoteDuration.QUARTER),
                        ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.QUARTER),
                    ),
                ),
                ScoreMeasure(4, listOf(ScoreItem.Note(g4, NoteDuration.QUARTER))),
            )
        )

        // Loop only Bar 2 (0-based index 1..1) and start from Bar 1
        module.setLoopRange(1, 1)
        module.loopMode = true
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))
        assertTrue(module.isPlaying)

        // Execute Cycle 1 events cleanly up through rewind at t = 4.0s (end of Bar 2)
        val cycle1 = engine.scheduled.sortedBy { it.first.nanoseconds }.toList()
        engine.scheduled.clear()
        cycle1.forEach { (_, action) -> action() }

        val cycle1Notes = receiver.received.filter { it.opcode == Message.VoiceOpcode.NOTE }
        // Bar 1 (C4 at 0.0s) + Bar 2 (D4 at 2.0s, E4 at 3.0s) — never reaches Bar 3/Bar 4
        assertEquals(
            listOf(c4.midiPitch.toDouble(), d4.midiPitch.toDouble(), e4.midiPitch.toDouble()),
            cycle1Notes.map { it.value },
        )
        assertEquals(0L, cycle1Notes[0].time.nanoseconds)
        assertEquals(2_000_000_000L, cycle1Notes[1].time.nanoseconds)
        assertEquals(3_000_000_000L, cycle1Notes[2].time.nanoseconds)
        // E4's tie into Bar 3 is clamped to end of Bar 2 (1.0s instead of 1.5s)
        assertEquals(1.0, cycle1Notes[2].duration, 1e-3)
        // At rewind time (4.0s), playback rewound cleanly to Bar 2 instead of stopping
        assertTrue(module.isPlaying)

        // Execute Cycle 2 (now playing ONLY Bar 2 starting at 4.0s)
        receiver.received.clear()
        val cycle2 = engine.scheduled.sortedBy { it.first.nanoseconds }.toList()
        engine.scheduled.clear()
        cycle2.forEach { (_, action) -> action() }

        val cycle2Notes = receiver.received.filter { it.opcode == Message.VoiceOpcode.NOTE }
        assertEquals(listOf(d4.midiPitch.toDouble(), e4.midiPitch.toDouble()), cycle2Notes.map { it.value })
        assertEquals(4_000_000_000L, cycle2Notes[0].time.nanoseconds)
        assertEquals(5_000_000_000L, cycle2Notes[1].time.nanoseconds)
        assertTrue(module.isPlaying)

        // Interrupting Cycle 3 mid-note sends explicit NOTE_OFF and terminates loop cleanly
        receiver.received.clear()
        val cycle3Downbeat = engine.scheduled.filter { it.first.nanoseconds == 6_000_000_000L }
        cycle3Downbeat.forEach { (_, action) -> action() }
        module.stop.receive(Message.FloatValue(ScoreTime(6_250_000_000L), 1.0))
        assertFalse(module.isPlaying)
        assertTrue(
            receiver.received.any { it.opcode == Message.VoiceOpcode.NOTE_OFF && it.value == d4.midiPitch.toDouble() },
            "Stopping mid-note during loop playback should send NOTE_OFF",
        )
    }

    @Test
    fun testPhase23LoopPersistenceRoundTrip() {
        val graph = GraphState()
        val module = ScoreModule()
        graph.addModule(module, Offset.Zero)
        module.loopMode = true
        module.setLoopRange(2, 5)

        val json = GraphSerializer.serialize(graph, graph.modules)
        val restored = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertTrue(restored.loopMode)
        assertEquals(2, restored.loopStartMeasure)
        assertEquals(5, restored.loopEndMeasure)
    }

    @Test
    fun testPhase24TranscriberStraightDottedChordsTripletsAndTies() {
        val q = NoteDuration.QUARTER.ticks.toDouble()
        val e = NoteDuration.EIGHTH.ticks.toDouble()
        val s = NoteDuration.SIXTEENTH.ticks.toDouble()

        // 1. Dotted quarter + eighth + chord (C4+E4) on Beat 3 + quarter rest on Beat 4 (with human jitter)
        val rawMixed = listOf(
            RecordedRawNote(startTick = 6.0, endTick = q * 1.5 - 4.0, midiPitch = 60),
            RecordedRawNote(startTick = q * 1.5 + 5.0, endTick = q * 2.0 - 3.0, midiPitch = 62),
            RecordedRawNote(startTick = q * 2.0 - 5.0, endTick = q * 3.0 - 4.0, midiPitch = 64),
            RecordedRawNote(startTick = q * 2.0 + 4.0, endTick = q * 3.0 - 2.0, midiPitch = 67),
        )
        val (mMixed, carriedMixed) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawMixed,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        assertTrue(carriedMixed.isEmpty())
        assertEquals(4, mMixed.items.size)
        val n0 = mMixed.items[0] as ScoreItem.Note
        assertEquals(NoteDuration.QUARTER, n0.duration)
        assertTrue(n0.isDotted)
        val n1 = mMixed.items[1] as ScoreItem.Note
        assertEquals(NoteDuration.EIGHTH, n1.duration)
        assertFalse(n1.isDotted)
        val c2 = mMixed.items[2] as ScoreItem.Chord
        assertEquals(NoteDuration.QUARTER, c2.duration)
        assertEquals(listOf(64, 67), c2.pitches.map { it.midiPitch })
        val r3 = mMixed.items[3] as ScoreItem.Rest
        assertEquals(NoteDuration.QUARTER, r3.duration)

        // 2. Triplet detection on Beat 1 (3:2 eighth triplets with slight jitter) + straight quarter on Beat 2
        val tStep = q / 3.0
        val rawTriplet = listOf(
            RecordedRawNote(startTick = 3.0, endTick = tStep - 2.0, midiPitch = 60),
            RecordedRawNote(startTick = tStep + 4.0, endTick = tStep * 2.0 - 3.0, midiPitch = 62),
            RecordedRawNote(startTick = tStep * 2.0 - 4.0, endTick = q - 2.0, midiPitch = 64),
            RecordedRawNote(startTick = q + 2.0, endTick = q * 2.0 - 2.0, midiPitch = 65),
        )
        val (mTriplet, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawTriplet,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
            detectTriplets = true,
        )
        assertTrue(mTriplet.items.size >= 5)
        val tripletNotes = mTriplet.items.take(3).map { it as ScoreItem.Note }
        assertTrue(tripletNotes.all { it.duration == NoteDuration.EIGHTH && it.tuplet != null })
        assertEquals(1, tripletNotes.map { it.tuplet!!.groupId }.distinct().size)
        assertEquals(3, tripletNotes[0].tuplet!!.actualCount)
        assertEquals(2, tripletNotes[0].tuplet!!.normalCount)
        val beat2Note = mTriplet.items[3] as ScoreItem.Note
        assertEquals(NoteDuration.QUARTER, beat2Note.duration)
        assertNull(beat2Note.tuplet)

        // 3. Slightly swung straight 8ths are NOT misclassified as triplets
        val rawStraight8 = listOf(
            RecordedRawNote(startTick = 0.0, endTick = e, midiPitch = 60),
            RecordedRawNote(startTick = e + 10.0, endTick = q, midiPitch = 62),
        )
        val (mStraight, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawStraight8,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
            detectTriplets = true,
        )
        assertTrue(mStraight.items.take(2).all { it.tuplet == null && it.duration == NoteDuration.EIGHTH })

        // 4. Sustained note crossing the barline sets isTiedToNext and carries tail to measure 2
        val rawTie = listOf(
            RecordedRawNote(startTick = q * 3.0, endTick = q * 5.0, midiPitch = 69),
        )
        val (mBar1, carriedTie) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawTie,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        val tiedLast = mBar1.items.last() as ScoreItem.Note
        assertTrue(tiedLast.isTiedToNext)
        assertEquals(NoteDuration.QUARTER, tiedLast.duration)
        assertEquals(1, carriedTie.size)
        assertEquals(q * 4.0, carriedTie[0].startTick, 1e-3)
        assertEquals(q * 5.0, carriedTie[0].endTick, 1e-3)
        assertTrue(s > 0.0)
    }

    @Test
    fun testPhase24PerInstrumentRecordArmGrandStaffSplitLatencyAndReplaceMerge() {
        val module = ScoreModule()
        val engine = TestEngine()
        module.setEngine(engine)
        val pianoOut = VoiceReceiver()

        // Create a 2-instrument score: Piano (Grand Staff, 2 staves) + Violin (1 staff)
        module.scoreData.replaceAllInstruments(
            listOf(
                ScoreInstrumentPart(
                    id = "piano",
                    name = "Piano",
                    staffCount = 2,
                    clef = Clef.TREBLE,
                    lowerClef = Clef.BASS,
                    measures = listOf(
                        ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                        ScoreMeasure(2, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                    ),
                    lowerMeasures = listOf(
                        ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                        ScoreMeasure(2, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                    ),
                ),
                ScoreInstrumentPart(
                    id = "violin",
                    name = "Violin",
                    staffCount = 1,
                    clef = Clef.TREBLE,
                    measures = listOf(
                        ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                        ScoreMeasure(2, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                    ),
                ),
            )
        )
        module.syncInstrumentOutputs()
        module.outputForInstrument(0).connect(pianoOut.inJack)

        // Exclusive per-instrument record arming
        assertNull(module.recordArmedInstrumentIdx)
        module.toggleRecordArm(0)
        assertEquals(0, module.recordArmedInstrumentIdx)
        module.toggleRecordArm(1)
        assertEquals(1, module.recordArmedInstrumentIdx)
        module.toggleRecordArm(1)
        assertNull(module.recordArmedInstrumentIdx)

        // Arm Piano (Grand Staff), configure 40ms audio latency & loop Bar 1 (2.0s at 120 BPM)
        module.toggleRecordArm(0)
        module.recordLatencyMs = 40.0
        module.recordMode = ScoreRecordMode.REPLACE
        module.setLoopRange(0, 0)
        module.loopMode = true

        module.startRealTimeRecording(ScoreTime(0L))
        assertTrue(module.isRealTimeRecording)
        assertTrue(module.isPlaying)

        // Play two-handed: Left hand C3 (48) + Right hand C4 (60) at beat 1, arriving 40ms late due to audio latency!
        module.input.receive(Message.VoiceMessage(ScoreTime(40_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 1, value = 48.0, extra = 0.7))
        module.input.receive(Message.VoiceMessage(ScoreTime(40_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 2, value = 60.0, extra = 0.8))
        module.input.receive(Message.VoiceMessage(ScoreTime(540_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 1, value = 48.0, extra = 0.0))
        module.input.receive(Message.VoiceMessage(ScoreTime(540_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 2, value = 60.0, extra = 0.0))

        // Deferred commit: before barline fires, measures still have their initial whole rest
        assertEquals(listOf(ScoreItem.Rest(NoteDuration.WHOLE)), module.scoreData.instruments[0].measures[0].items)

        // Advance to end of Bar 1 (2.0s) -> deferred commit fires and rewinds loop
        val pass1 = engine.scheduled.sortedBy { it.first.nanoseconds }.toList()
        engine.scheduled.clear()
        pass1.forEach { (_, action) -> action() }

        // Upper staff got C4 quarter on Beat 1; Lower staff got C3 quarter on Beat 1!
        val upperItems = module.scoreData.instruments[0].measures[0].items
        val lowerItems = module.scoreData.instruments[0].lowerMeasures[0].items
        assertEquals(60, (upperItems[0] as ScoreItem.Note).pitch.midiPitch)
        assertEquals(NoteDuration.QUARTER, upperItems[0].duration)
        assertEquals(48, (lowerItems[0] as ScoreItem.Note).pitch.midiPitch)
        assertEquals(NoteDuration.QUARTER, lowerItems[0].duration)

        // Pass 2: Silent loop pass in REPLACE mode must preserve existing notes untouched
        val pass2 = engine.scheduled.sortedBy { it.first.nanoseconds }.toList()
        engine.scheduled.clear()
        pass2.forEach { (_, action) -> action() }
        assertEquals(60, (module.scoreData.instruments[0].measures[0].items[0] as ScoreItem.Note).pitch.midiPitch)

        // Pass 3: Switch to MERGE mode and layer E4 (64) on Beat 1 -> merges with C4 into a C4+E4 chord!
        module.recordMode = ScoreRecordMode.MERGE
        module.input.receive(Message.VoiceMessage(ScoreTime(4_040_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 3, value = 64.0, extra = 0.8))
        module.input.receive(Message.VoiceMessage(ScoreTime(4_540_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 3, value = 64.0, extra = 0.0))
        val pass3 = engine.scheduled.sortedBy { it.first.nanoseconds }.toList()
        engine.scheduled.clear()
        pass3.forEach { (_, action) -> action() }

        val mergedBeat1 = module.scoreData.instruments[0].measures[0].items[0] as ScoreItem.Chord
        assertEquals(listOf(60, 64), mergedBeat1.pitches.map { it.midiPitch })
        assertEquals(NoteDuration.QUARTER, mergedBeat1.duration)
    }

    @Test
    fun testPhase24LiveEditingWhileLoopingAndSettingsPersistence() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)

        val module = ScoreModule()
        val engine = TestEngine()
        module.setEngine(engine)
        val receiver = VoiceReceiver()
        module.output.connect(receiver.inJack)

        module.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    1,
                    listOf(
                        ScoreItem.Note(c4, NoteDuration.HALF),
                        ScoreItem.Note(e4, NoteDuration.HALF),
                    ),
                )
            )
        )
        module.setLoopRange(0, 0)
        module.loopMode = true
        module.start.receive(Message.FloatValue(ScoreTime(0L), 1.0))

        // Play beat 1 (t=0s: C4), then while looping at t=0.5s edit beat 3 from E4 to G4!
        val beat1Events = engine.scheduled.filter { it.first.nanoseconds == 0L }
        beat1Events.forEach { (_, action) -> action() }
        assertEquals(listOf(60.0), receiver.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value })

        module.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(
                    1,
                    listOf(
                        ScoreItem.Note(c4, NoteDuration.HALF),
                        ScoreItem.Note(g4, NoteDuration.HALF),
                    ),
                )
            )
        )
        engine.scheduled.clear()
        module.notifyScoreEditedWhilePlaying(ScoreTime(500_000_000L))

        // Remaining events in the SAME loop pass now play G4 (67) instead of E4 (64)!
        val remaining = engine.scheduled.sortedBy { it.first.nanoseconds }.toList()
        engine.scheduled.clear()
        remaining.forEach { (_, action) -> action() }
        val playedMidis = receiver.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value }
        assertEquals(listOf(60.0, 67.0), playedMidis)
        assertTrue(module.isPlaying)

        // GraphSerializer round-trips real-time recording settings
        val graph = GraphState()
        graph.addModule(module, Offset.Zero)
        module.recordMode = ScoreRecordMode.MERGE
        module.recordLatencyMs = 22.5
        module.recordQuantizeGrid = ScoreQuantizeGrid.EIGHTH
        module.recordDetectTriplets = false

        val json = GraphSerializer.serialize(graph, graph.modules)
        val restored = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertEquals(ScoreRecordMode.MERGE, restored.recordMode)
        assertEquals(22.5, restored.recordLatencyMs, 1e-4)
        assertEquals(ScoreQuantizeGrid.EIGHTH, restored.recordQuantizeGrid)
        assertFalse(restored.recordDetectTriplets)
    }

    @Test
    fun testNonScrollingInstrumentSidePanelLayoutMode() {
        val score = ScoreData().apply {
            replaceAllInstruments(
                listOf(
                    ScoreInstrumentPart(
                        id = "piano",
                        name = "Piano",
                        staffCount = 2,
                        clef = Clef.TREBLE,
                        lowerClef = Clef.BASS,
                        measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE)))),
                        lowerMeasures = listOf(ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE)))),
                    ),
                    ScoreInstrumentPart(
                        id = "violin",
                        name = "Violin",
                        staffCount = 1,
                        clef = Clef.TREBLE,
                        measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE)))),
                    ),
                )
            )
        }
        val target = RecordingDrawTarget()
        val layout = ScoreRenderer.render(
            score = score,
            target = target,
            staffSpacePx = 10f,
            minWidthPx = 600f,
            colors = ScoreRenderColors.forTheme(darkMode = false),
            includeInstrumentLabelGutter = false,
        )
        assertFalse(layout.showInstrumentLabels)
        // Inline instrument text is omitted so the pinned side panel renders it instead
        assertFalse(target.texts.any { it.text == "Piano" || it.text == "Violin" })
        // Left margin shrinks to compact brace gutter while preserving instrument group Y coordinates
        assertTrue(layout.leftBarX < 30f)
        assertEquals(2, layout.instrumentGroups.size)
        assertTrue(layout.instrumentGroups[0].bottomStaffY < layout.instrumentGroups[1].topStaffY)
        // Grand Staff brace is still drawn on the score canvas beside the opening barline
        assertTrue(target.beziers.isNotEmpty())
    }

    @Test
    fun testPhase24HeldNotePastLoopEndStopsAtLastMeasureWithoutWrapping() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val module = ScoreModule()
        val engine = TestEngine()
        module.setEngine(engine)

        val initialBar1 = listOf(ScoreItem.Note(c4, NoteDuration.HALF), ScoreItem.Rest(NoteDuration.HALF))
        module.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(1, initialBar1),
                ScoreMeasure(2, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
            )
        )
        module.toggleRecordArm(0)
        module.recordLatencyMs = 0.0
        module.recordMode = ScoreRecordMode.REPLACE
        module.setLoopRange(0, 1)
        module.loopMode = true

        module.startRealTimeRecording(ScoreTime(0L))
        // In Bar 2 (2.0s..4.0s at 120 BPM), attack G4 at Beat 3 (3.0s) and hold it past 4.0s into the next loop pass!
        module.input.receive(Message.VoiceMessage(ScoreTime(3_000_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 1, value = 67.0, extra = 0.8))

        // Commit Bar 1 (2.0s) and Bar 2 / Loop End (4.0s)
        val pass1 = engine.scheduled.sortedBy { it.first.nanoseconds }.toList()
        engine.scheduled.clear()
        pass1.forEach { (_, action) -> action() }

        // Bar 2 stops cleanly at the barline with G4 Half (isTiedToNext == false)
        val bar2Items = module.scoreData.measures[1].items
        assertEquals(2, bar2Items.size)
        assertEquals(ScoreItem.Rest(NoteDuration.HALF), bar2Items[0])
        val g4Half = bar2Items[1] as ScoreItem.Note
        assertEquals(67, g4Half.pitch.midiPitch)
        assertEquals(NoteDuration.HALF, g4Half.duration)
        assertFalse(g4Half.isTiedToNext)

        // User releases G4 at 4.5s (during Bar 1 of the next pass); Bar 1 must remain completely untouched
        module.input.receive(Message.VoiceMessage(ScoreTime(4_500_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 1, value = 67.0, extra = 0.0))
        val pass2Bar1 = engine.scheduled.filter { it.first.nanoseconds <= 6_000_000_000L }.sortedBy { it.first.nanoseconds }
        pass2Bar1.forEach { (_, action) -> action() }

        assertEquals(initialBar1, module.scoreData.measures[0].items)
    }

    @Test
    fun testPhase24EarlyAttackIntoMeasureNDoesNotEraseMeasureNMinus1() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val module = ScoreModule()
        val engine = TestEngine()
        module.setEngine(engine)

        // 3-bar loop: Bar 1 (index 0) already has C4+D4 Half notes; Bar 2 (index 1) is rest.
        val existingBar1 = listOf(ScoreItem.Note(c4, NoteDuration.HALF), ScoreItem.Note(d4, NoteDuration.HALF))
        module.scoreData.replaceMeasures(
            listOf(
                ScoreMeasure(1, existingBar1),
                ScoreMeasure(2, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
                ScoreMeasure(3, listOf(ScoreItem.Rest(NoteDuration.WHOLE))),
            )
        )
        module.toggleRecordArm(0)
        module.recordLatencyMs = 0.0
        module.recordMode = ScoreRecordMode.REPLACE
        module.setLoopRange(0, 2)
        module.loopMode = true

        module.startRealTimeRecording(ScoreTime(0L))

        // Play E4 (64) slightly early for Beat 1 of Bar 2 (at 1.98s, 20ms before the 2.0s barline) and release at 2.5s
        module.input.receive(Message.VoiceMessage(ScoreTime(1_980_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 1, value = 64.0, extra = 0.8))

        // Bar 1 commits at 2.0s while E4 is still held across the barline -> Bar 1 MUST NOT be erased!
        val bar1Events = engine.scheduled.filter { it.first.nanoseconds <= 2_000_000_000L }.sortedBy { it.first.nanoseconds }
        bar1Events.forEach { (_, action) -> action() }
        assertEquals(existingBar1, module.scoreData.measures[0].items)

        // Release E4 at 2.5s and commit Bar 2 at 4.0s -> Bar 2 gets E4 Quarter on Beat 1
        module.input.receive(Message.VoiceMessage(ScoreTime(2_500_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 1, value = 64.0, extra = 0.0))
        val bar2Events = engine.scheduled.filter { it.first.nanoseconds in 2_000_000_001L..4_000_000_000L }.sortedBy { it.first.nanoseconds }
        bar2Events.forEach { (_, action) -> action() }

        val bar2Items = module.scoreData.measures[1].items
        val firstNote = bar2Items[0] as ScoreItem.Note
        assertEquals(64, firstNote.pitch.midiPitch)
        assertEquals(NoteDuration.QUARTER, firstNote.duration)
        assertEquals(existingBar1, module.scoreData.measures[0].items)
    }

    @Test
    fun testPhase24ReplacePunchMutesRestOfMeasureForArmedInstrumentOnly() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val e4 = SpelledPitch(NoteLetter.E, 0, 4)
        val f4 = SpelledPitch(NoteLetter.F, 0, 4)
        val g4 = SpelledPitch(NoteLetter.G, 0, 4)

        val module = ScoreModule()
        val engine = TestEngine()
        module.setEngine(engine)
        val armedOut = VoiceReceiver()
        val otherOut = VoiceReceiver()

        module.scoreData.replaceAllInstruments(
            listOf(
                ScoreInstrumentPart(
                    id = "lead",
                    name = "Lead",
                    measures = listOf(
                        ScoreMeasure(
                            1,
                            listOf(
                                ScoreItem.Note(c4, NoteDuration.QUARTER),
                                ScoreItem.Note(d4, NoteDuration.QUARTER),
                                ScoreItem.Note(e4, NoteDuration.QUARTER),
                                ScoreItem.Note(f4, NoteDuration.QUARTER),
                            ),
                        ),
                        ScoreMeasure(2, listOf(ScoreItem.Note(g4, NoteDuration.QUARTER))),
                    ),
                ),
                ScoreInstrumentPart(
                    id = "bass",
                    name = "Bass",
                    measures = listOf(
                        ScoreMeasure(
                            1,
                            listOf(
                                ScoreItem.Note(g4, NoteDuration.HALF),
                                ScoreItem.Note(g4, NoteDuration.HALF),
                            ),
                        ),
                        ScoreMeasure(2, listOf(ScoreItem.Note(g4, NoteDuration.WHOLE))),
                    ),
                ),
            )
        )
        module.syncInstrumentOutputs()
        module.outputForInstrument(0).connect(armedOut.inJack)
        module.outputForInstrument(1).connect(otherOut.inJack)

        module.toggleRecordArm(0)
        module.recordMode = ScoreRecordMode.REPLACE
        module.recordLatencyMs = 0.0
        module.startRealTimeRecording(ScoreTime(0L))

        // Beat 1 (t=0s): old C4 (60) plays on Lead and old G4 (67) plays on Bass
        engine.scheduled.filter { it.first.nanoseconds == 0L }.forEach { (_, action) -> action() }
        assertEquals(listOf(60.0), armedOut.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value })

        // At t=0.25s (before Beat 2), performer plays A4 (69) into Lead -> cuts off Beat 1 C4 and mutes Beats 2..4!
        armedOut.received.clear()
        module.input.receive(Message.VoiceMessage(ScoreTime(250_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 9, value = 69.0, extra = 0.8))
        assertTrue(
            armedOut.received.any { it.opcode == Message.VoiceOpcode.NOTE_OFF && it.value == 60.0 },
            "Punch-in should send NOTE_OFF for previously sounding Beat 1 C4",
        )
        armedOut.received.clear()
        module.input.receive(Message.VoiceMessage(ScoreTime(750_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 9, value = 69.0, extra = 0.0))

        // Fire Beats 2..4 of Bar 1 (0.5s..1.99s) and Bar 2 Beat 1 (2.0s)
        engine.scheduled
            .filter { it.first.nanoseconds > 0L && it.first.nanoseconds <= 2_000_000_000L }
            .sortedBy { it.first.nanoseconds }
            .forEach { (_, action) -> action() }

        // Lead Beats 2..4 (D4, E4, F4) were muted; Bar 2 (G4 at 2.0s) plays normally!
        assertEquals(listOf(67.0), armedOut.received.filter { it.opcode == Message.VoiceOpcode.NOTE }.map { it.value })
        // Unarmed Bass still played its second Half note at 1.0s + Bar 2 at 2.0s
        assertEquals(3, otherOut.received.count { it.opcode == Message.VoiceOpcode.NOTE })
    }

    @Test
    fun testPhase24DutyCycleLeansTowardContiguousNotes() {
        val q = NoteDuration.QUARTER.ticks.toDouble()
        val h = NoteDuration.HALF.ticks.toDouble()
        val e = NoteDuration.EIGHTH.ticks.toDouble()

        // 1. Four quarter notes played with ~79% duty cycle (190/240 ticks) -> 4 contiguous quarter notes
        val rawQuarters = (0 until 4).map { i ->
            RecordedRawNote(startTick = i * q, endTick = i * q + q * 0.79, midiPitch = 60 + i * 2)
        }
        for (grid in listOf(ScoreQuantizeGrid.SIXTEENTH, ScoreQuantizeGrid.THIRTY_SECOND)) {
            val (mQuarters, _) = ScoreTranscriber.transcribeMeasure(
                rawNotes = rawQuarters,
                measureNumber = 1,
                measureStartTick = 0.0,
                timeSignature = TimeSignature.COMMON_TIME,
                keySignature = KeySignature.C_MAJOR,
                quantizeGrid = grid,
            )
            assertEquals(4, mQuarters.items.size, "Expected 4 quarter notes on grid $grid")
            assertTrue(mQuarters.items.all { it is ScoreItem.Note && it.duration == NoteDuration.QUARTER && !it.isDotted })
        }

        // 2. Two half notes played with 80% duty cycle -> 2 contiguous half notes
        val rawHalves = listOf(
            RecordedRawNote(startTick = 0.0, endTick = h * 0.80, midiPitch = 60),
            RecordedRawNote(startTick = h, endTick = h + h * 0.80, midiPitch = 64),
        )
        val (mHalves, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawHalves,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        assertEquals(2, mHalves.items.size)
        assertTrue(mHalves.items.all { it is ScoreItem.Note && it.duration == NoteDuration.HALF && !it.isDotted })

        // 3. Contiguous eighth notes at 80% duty cycle on 1/32 grid -> 8 contiguous eighth notes
        val rawEighths = (0 until 8).map { i ->
            RecordedRawNote(startTick = i * e, endTick = i * e + e * 0.80, midiPitch = 60 + (i % 4))
        }
        val (mEighths, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawEighths,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
            quantizeGrid = ScoreQuantizeGrid.THIRTY_SECOND,
        )
        assertEquals(8, mEighths.items.size)
        assertTrue(mEighths.items.all { it is ScoreItem.Note && it.duration == NoteDuration.EIGHTH && !it.isDotted })

        // 4. Quarter notes at 80% duty cycle followed by clear quarter rests -> Quarter + Quarter Rest (not dotted 8th + 16th rest)
        val rawQuarterWithRest = listOf(
            RecordedRawNote(startTick = 0.0, endTick = q * 0.80, midiPitch = 60),
            RecordedRawNote(startTick = q * 2.0, endTick = q * 2.80, midiPitch = 62),
        )
        val (mWithRest, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawQuarterWithRest,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        assertEquals(4, mWithRest.items.size)
        assertEquals(NoteDuration.QUARTER, (mWithRest.items[0] as ScoreItem.Note).duration)
        assertFalse((mWithRest.items[0] as ScoreItem.Note).isDotted)
        assertEquals(ScoreItem.Rest(NoteDuration.QUARTER), mWithRest.items[1])
        assertEquals(NoteDuration.QUARTER, (mWithRest.items[2] as ScoreItem.Note).duration)
        assertFalse((mWithRest.items[2] as ScoreItem.Note).isDotted)
        assertEquals(ScoreItem.Rest(NoteDuration.QUARTER), mWithRest.items[3])

        // 5. Staccato notes (different pitches) at 50% duty cycle still preserve clear rests (Eighth + Eighth Rest)
        val rawStaccato = listOf(
            RecordedRawNote(startTick = 0.0, endTick = q * 0.50, midiPitch = 60),
            RecordedRawNote(startTick = q, endTick = q + q * 0.50, midiPitch = 62),
        )
        val (mStaccato, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawStaccato,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        assertEquals(NoteDuration.EIGHTH, (mStaccato.items[0] as ScoreItem.Note).duration)
        assertEquals(ScoreItem.Rest(NoteDuration.EIGHTH), mStaccato.items[1])
        assertEquals(NoteDuration.EIGHTH, (mStaccato.items[2] as ScoreItem.Note).duration)
        assertEquals(ScoreItem.Rest(NoteDuration.EIGHTH), mStaccato.items[3])
        assertEquals(ScoreItem.Rest(NoteDuration.HALF), mStaccato.items[4])

        // 6. Non-repeated notes at 68% duty cycle -> contiguous quarter notes (general threshold 0.65)
        val raw68 = (0 until 4).map { i ->
            RecordedRawNote(startTick = i * q, endTick = i * q + q * 0.68, midiPitch = 60 + i * 2)
        }
        val (m68, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = raw68,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        assertEquals(4, m68.items.size)
        assertTrue(m68.items.all { it is ScoreItem.Note && it.duration == NoteDuration.QUARTER && !it.isDotted })

        // 7. Repeated same-pitch notes at 50% duty cycle -> contiguous quarter notes (relaxed repeated-pitch threshold 0.45)
        val rawRepeated = listOf(
            RecordedRawNote(startTick = 0.0, endTick = q * 0.50, midiPitch = 60),
            RecordedRawNote(startTick = q, endTick = q + q * 0.50, midiPitch = 60),
            RecordedRawNote(startTick = q * 2.0, endTick = q * 2.0 + q * 0.50, midiPitch = 60),
            RecordedRawNote(startTick = q * 3.0, endTick = q * 3.0 + q * 0.70, midiPitch = 60),
        )
        val (mRepeated, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawRepeated,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        assertEquals(4, mRepeated.items.size)
        assertTrue(mRepeated.items.all { it is ScoreItem.Note && it.duration == NoteDuration.QUARTER && !it.isDotted })

        // 8. Very short staccatissimo repeated notes (30% duty cycle) still preserve rests
        val rawRepeatedStaccatissimo = listOf(
            RecordedRawNote(startTick = 0.0, endTick = q * 0.30, midiPitch = 60),
            RecordedRawNote(startTick = q, endTick = q + q * 0.30, midiPitch = 60),
        )
        val (mRepStacc, _) = ScoreTranscriber.transcribeMeasure(
            rawNotes = rawRepeatedStaccatissimo,
            measureNumber = 1,
            measureStartTick = 0.0,
            timeSignature = TimeSignature.COMMON_TIME,
            keySignature = KeySignature.C_MAJOR,
        )
        assertEquals(NoteDuration.SIXTEENTH, (mRepStacc.items[0] as ScoreItem.Note).duration)
        assertTrue(mRepStacc.items[1] is ScoreItem.Rest)
    }

    @Test
    fun testResolveTrailingAndAdjacentRestsOnFillPasteAndDelete() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.EIGHTH)
        val h4 = ScoreItem.Note(SpelledPitch(NoteLetter.G, 0, 4), NoteDuration.HALF)

        // 1. Pasting 4 quarter notes before a whole rest -> trailing whole rest is deleted
        val pastedFour = ScoreEditing.pasteClipboardItems(
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE)))),
            entries = List(4) { ScoreClipboardItem(relativeMeasureOffset = 0, item = c4) },
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 0),
        )!!
        val resolvedFour = ScoreEditing.fillMeasuresWithRests(pastedFour.measures, TimeSignature.COMMON_TIME)
        assertEquals(listOf(c4, c4, c4, c4), resolvedFour[0].items)

        // 2. Pasting 2 quarter notes before a whole rest -> trailing whole rest becomes a half rest
        val pastedTwo = ScoreEditing.pasteClipboardItems(
            measures = listOf(ScoreMeasure(1, listOf(ScoreItem.Rest(NoteDuration.WHOLE)))),
            entries = List(2) { ScoreClipboardItem(relativeMeasureOffset = 0, item = c4) },
            initialTimeSignature = TimeSignature.COMMON_TIME,
            cursorPosition = ScoreCursorPosition(0, 0),
        )!!
        val resolvedTwo = ScoreEditing.fillMeasuresWithRests(pastedTwo.measures, TimeSignature.COMMON_TIME)
        assertEquals(listOf(c4, c4, ScoreItem.Rest(NoteDuration.HALF)), resolvedTwo[0].items)

        // 3. Deleting all 4 quarter notes with autoFillRests merges 4 quarter rests into 1 whole rest
        val fourQuarters = listOf(ScoreMeasure(1, listOf(c4, c4, c4, c4)))
        val delAll = ScoreEditing.deleteSelectionItems(
            measures = fourQuarters,
            selection = (0 until 4).mapTo(linkedSetOf()) { ScoreItemLocation(0, it) },
            autoFillRests = true,
            initialTimeSignature = TimeSignature.COMMON_TIME,
        )
        assertEquals(listOf(ScoreItem.Rest(NoteDuration.WHOLE)), delAll.measures[0].items)
        assertEquals(setOf(ScoreItemLocation(0, 0)), delAll.selectedRestLocations)

        // 4. Deleting beats 3 & 4 merges into a trailing half rest
        val delLastTwo = ScoreEditing.deleteSelectionItems(
            measures = fourQuarters,
            selection = setOf(ScoreItemLocation(0, 2), ScoreItemLocation(0, 3)),
            autoFillRests = true,
            initialTimeSignature = TimeSignature.COMMON_TIME,
        )
        assertEquals(listOf(c4, c4, ScoreItem.Rest(NoteDuration.HALF)), delLastTwo.measures[0].items)
        assertEquals(setOf(ScoreItemLocation(0, 2)), delLastTwo.selectedRestLocations)

        // 5. Deleting beats 2 & 3 keeps 2 quarter rests (half rest at beat 2 is unaligned)
        val delMiddleTwo = ScoreEditing.deleteSelectionItems(
            measures = fourQuarters,
            selection = setOf(ScoreItemLocation(0, 1), ScoreItemLocation(0, 2)),
            autoFillRests = true,
            initialTimeSignature = TimeSignature.COMMON_TIME,
        )
        assertEquals(
            listOf(c4, ScoreItem.Rest(NoteDuration.QUARTER), ScoreItem.Rest(NoteDuration.QUARTER), c4),
            delMiddleTwo.measures[0].items,
        )
        assertEquals(setOf(ScoreItemLocation(0, 1), ScoreItemLocation(0, 2)), delMiddleTwo.selectedRestLocations)

        // 6. Deleting beats 1 & 2 merges into a leading half rest
        val delFirstTwo = ScoreEditing.deleteSelectionItems(
            measures = fourQuarters,
            selection = setOf(ScoreItemLocation(0, 0), ScoreItemLocation(0, 1)),
            autoFillRests = true,
            initialTimeSignature = TimeSignature.COMMON_TIME,
        )
        assertEquals(listOf(ScoreItem.Rest(NoteDuration.HALF), c4, c4), delFirstTwo.measures[0].items)
        assertEquals(setOf(ScoreItemLocation(0, 0)), delFirstTwo.selectedRestLocations)

        // 7. Fill with Rests merges 4 quarter rests into 1 whole rest, and keeps aligned smaller-first rests
        val fourQRestBar = ScoreMeasure(1, List(4) { ScoreItem.Rest(NoteDuration.QUARTER) })
        assertEquals(
            listOf(ScoreItem.Rest(NoteDuration.WHOLE)),
            ScoreEditing.fillMeasureWithRests(fourQRestBar, TimeSignature.COMMON_TIME).items,
        )
        val alignedSmallerFirst = ScoreMeasure(
            1,
            listOf(e4, ScoreItem.Rest(NoteDuration.EIGHTH), ScoreItem.Rest(NoteDuration.QUARTER), h4),
        )
        assertEquals(
            alignedSmallerFirst.items,
            ScoreEditing.fillMeasureWithRests(alignedSmallerFirst, TimeSignature.COMMON_TIME).items,
        )
    }

    @Test
    fun testPasteReplacesSelectedRestInsteadOfAdvancingToNextMeasure() {
        val c4 = ScoreItem.Note(SpelledPitch(NoteLetter.C, 0, 4), NoteDuration.QUARTER)
        val d4 = ScoreItem.Note(SpelledPitch(NoteLetter.D, 0, 4), NoteDuration.QUARTER)
        val e4 = ScoreItem.Note(SpelledPitch(NoteLetter.E, 0, 4), NoteDuration.QUARTER)
        val f4 = ScoreItem.Note(SpelledPitch(NoteLetter.F, 0, 4), NoteDuration.QUARTER)
        val wholeRest = ScoreItem.Rest(NoteDuration.WHOLE)
        val qRest = ScoreItem.Rest(NoteDuration.QUARTER)

        // 1. Copy 4 quarter notes from M1, select whole rest in M2, paste -> M2 has the 4 notes and no M3 is added
        val twoBarScore = listOf(
            ScoreMeasure(1, listOf(c4, d4, e4, f4)),
            ScoreMeasure(2, listOf(wholeRest)),
        )
        val copiedFour = ScoreEditing.copySelectionContent(
            measures = twoBarScore,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = (0..3).mapTo(linkedSetOf()) { ScoreItemLocation(0, it) },
        )!!
        val pastedOntoWholeRest = ScoreEditing.pasteClipboardContent(
            measures = twoBarScore,
            content = copiedFour,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(1, 0)),
        )!!
        assertEquals(2, pastedOntoWholeRest.measures.size)
        assertEquals(listOf(c4, d4, e4, f4), pastedOntoWholeRest.measures[1].items)
        assertEquals(
            (0..3).mapTo(linkedSetOf()) { ScoreItemLocation(1, it) },
            pastedOntoWholeRest.pastedLocations,
        )

        // 2. Selecting notes and pasting replaces those selected notes in place
        val copiedTwo = ScoreEditing.copySelectionContent(
            measures = twoBarScore,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(0, 2), ScoreItemLocation(0, 3)),
        )!!
        val replacedNotePaste = ScoreEditing.pasteClipboardContent(
            measures = pastedOntoWholeRest.measures,
            content = copiedTwo,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(1, 0), ScoreItemLocation(1, 1)),
        )!!
        assertEquals(2, replacedNotePaste.measures.size)
        assertEquals(listOf(e4, f4, e4, f4), replacedNotePaste.measures[1].items)
        assertEquals(
            setOf(ScoreItemLocation(1, 0), ScoreItemLocation(1, 1)),
            replacedNotePaste.pastedLocations,
        )

        // 3. Selecting a mid-measure quarter rest replaces it in place
        val midRestMeasures = listOf(ScoreMeasure(1, listOf(c4, qRest, d4, qRest)))
        val copiedOne = ScoreEditing.copySelectionContent(
            measures = twoBarScore,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(0, 2)),
        )!!
        val pastedMidRest = ScoreEditing.pasteClipboardContent(
            measures = midRestMeasures,
            content = copiedOne,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(0, 1)),
        )!!
        assertEquals(listOf(c4, e4, d4, qRest), pastedMidRest.measures[0].items)

        // 4. Selecting a whole rest and pasting a MeasureRange also replaces the rest in M2
        val copiedMeasure = ScoreEditing.copySelectionContent(
            measures = twoBarScore,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedMeasures = setOf(0),
        )!!
        val pastedMeasureOntoRest = ScoreEditing.pasteClipboardContent(
            measures = twoBarScore,
            content = copiedMeasure,
            initialClef = Clef.TREBLE,
            initialKeySignature = KeySignature.C_MAJOR,
            initialTimeSignature = TimeSignature.COMMON_TIME,
            selectedLocations = setOf(ScoreItemLocation(1, 0)),
        )!!
        assertEquals(2, pastedMeasureOntoRest.measures.size)
        assertEquals(listOf(c4, d4, e4, f4), pastedMeasureOntoRest.measures[1].items)
    }

    @Test
    fun testPhase25LoopLocksTravelWithLeftEdge() {
        val graph = GraphState()
        val module = ScoreModule()
        graph.addModule(module, Offset.Zero)
        val measures = (1..16).map { ScoreMeasure(it, listOf(ScoreItem.Rest(NoteDuration.WHOLE))) }
        module.scoreData.replaceMeasures(measures)

        // Starts out unlocked
        assertTrue(module.loopLockedOffsets.isEmpty())

        // Loop over bars 10, 11, 12, 13 (0-based 9..12)
        module.setLoopRange(9, 12)
        module.loopMode = true
        assertFalse(module.isMeasureLockedInLoop(9))
        assertFalse(module.isMeasureLockedInLoop(10))

        // Lock bars 10 and 11 (0-based 9 and 10)
        module.toggleLoopMeasureLock(9)
        module.toggleLoopMeasureLock(10)
        assertEquals(setOf(0, 1), module.loopLockedOffsets)
        assertTrue(module.isMeasureLockedInLoop(9))
        assertTrue(module.isMeasureLockedInLoop(10))
        assertFalse(module.isMeasureLockedInLoop(11))
        assertFalse(module.isMeasureLockedInLoop(12))

        // Drag the loop one measure over to bars 11, 12, 13, 14 (0-based 10..13)
        module.setLoopRange(10, 13)
        assertTrue(module.isMeasureLockedInLoop(10)) // bar 11
        assertTrue(module.isMeasureLockedInLoop(11)) // bar 12
        assertFalse(module.isMeasureLockedInLoop(12)) // bar 13
        assertFalse(module.isMeasureLockedInLoop(13)) // bar 14

        // When loopMode is off, isMeasureLockedInLoop returns false while UI offset state remains
        module.loopMode = false
        assertFalse(module.isMeasureLockedInLoop(10))
        assertTrue(module.isLoopOffsetLocked(10))
        module.loopMode = true

        // Serialization round-trip preserves loopLockedOffsets
        val json = GraphSerializer.serialize(graph, graph.modules)
        val restored = GraphSerializer.deserialize(GraphState(), json).filterIsInstance<ScoreModule>().single()
        assertEquals(10, restored.loopStartMeasure)
        assertEquals(13, restored.loopEndMeasure)
        assertEquals(setOf(0, 1), restored.loopLockedOffsets)
        assertTrue(restored.isMeasureLockedInLoop(10))
        assertTrue(restored.isMeasureLockedInLoop(11))
        assertFalse(restored.isMeasureLockedInLoop(12))

        // Shrinking loop to 1 measure prunes out-of-range offsets
        module.setLoopRange(10, 10)
        assertEquals(setOf(0), module.loopLockedOffsets)
    }

    @Test
    fun testPhase25LockedMeasuresProtectedDuringRealTimeLoopRecording() {
        val c4 = SpelledPitch(NoteLetter.C, 0, 4)
        val d4 = SpelledPitch(NoteLetter.D, 0, 4)
        val module = ScoreModule()
        val engine = TestEngine()
        module.setEngine(engine)
        val armedOut = VoiceReceiver()
        val bar1Original = ScoreMeasure(
            number = 1,
            items = listOf(ScoreItem.Note(c4, NoteDuration.WHOLE)),
        )
        val bar2Original = ScoreMeasure(
            number = 2,
            items = listOf(ScoreItem.Note(d4, NoteDuration.WHOLE)),
        )
        module.scoreData.replaceMeasures(listOf(bar1Original, bar2Original))
        module.syncInstrumentOutputs()
        module.outputForInstrument(0).connect(armedOut.inJack)

        module.toggleRecordArm(0)
        module.recordMode = ScoreRecordMode.REPLACE
        module.recordLatencyMs = 0.0
        module.loopMode = true
        module.setLoopRange(0, 1)
        module.toggleLoopMeasureLock(0) // Lock Bar 1; Bar 2 remains unlocked

        module.startRealTimeRecording(ScoreTime(0L))

        // Bar 1 (0s..2s at 120 BPM) is locked -> activeRecordingMeasureIdx stays null and Bar 1 plays normally
        engine.scheduled.filter { it.first.nanoseconds == 0L }.forEach { (_, action) -> action() }
        assertEquals(null, module.activeRecordingMeasureIdx)

        // Play E4 (64) during locked Bar 1 -> must NOT silence Bar 1 or overwrite it
        armedOut.received.clear()
        module.input.receive(Message.VoiceMessage(ScoreTime(250_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 1, value = 64.0, extra = 0.8))
        assertFalse(
            armedOut.received.any { it.opcode == Message.VoiceOpcode.NOTE_OFF && it.value == 60.0 },
            "Locked measure must not punch/silence playback notes",
        )
        module.input.receive(Message.VoiceMessage(ScoreTime(750_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 1, value = 64.0, extra = 0.0))

        // Advance past Bar 1 barline (2.0s) into Bar 2 (unlocked)
        engine.scheduled
            .filter { it.first.nanoseconds > 0L && it.first.nanoseconds <= 2_000_000_000L }
            .sortedBy { it.first.nanoseconds }
            .forEach { (_, action) -> action() }

        // Bar 1 is still intact (original Whole note C4)
        val bar1After = module.scoreData.measures[0]
        assertEquals(1, bar1After.items.size)
        assertEquals(60, (bar1After.items[0] as ScoreItem.Note).pitch.midiPitch)
        assertEquals(NoteDuration.WHOLE, (bar1After.items[0] as ScoreItem.Note).duration)

        // Bar 2 is unlocked -> activeRecordingMeasureIdx is 1, and playing G4 (67) overwrites Bar 2
        assertEquals(1, module.activeRecordingMeasureIdx)
        module.input.receive(Message.VoiceMessage(ScoreTime(2_000_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 2, value = 67.0, extra = 0.8))
        module.input.receive(Message.VoiceMessage(ScoreTime(3_900_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 2, value = 67.0, extra = 0.0))

        module.stopRealTimeRecording(ScoreTime(4_000_000_000L))

        // Bar 1 still untouched; Bar 2 updated to G4 (67)
        assertEquals(60, (module.scoreData.measures[0].items[0] as ScoreItem.Note).pitch.midiPitch)
        val bar2After = module.scoreData.measures[1]
        assertTrue(bar2After.items.any { it is ScoreItem.Note && it.soundingPitches().any { p -> p.midiPitch == 67 } })
        assertFalse(bar2After.items.any { it is ScoreItem.Note && it.soundingPitches().any { p -> p.midiPitch == 62 } })

        // Verify MERGE mode also leaves locked Bar 1 untouched
        module.recordMode = ScoreRecordMode.MERGE
        module.startRealTimeRecording(ScoreTime(0L))
        module.input.receive(Message.VoiceMessage(ScoreTime(500_000_000L), Message.VoiceOpcode.NOTE_ON, tag = 3, value = 69.0, extra = 0.8))
        module.input.receive(Message.VoiceMessage(ScoreTime(1_000_000_000L), Message.VoiceOpcode.NOTE_OFF, tag = 3, value = 69.0, extra = 0.0))
        module.stopRealTimeRecording(ScoreTime(1_500_000_000L))

        val bar1AfterMerge = module.scoreData.measures[0]
        assertEquals(1, bar1AfterMerge.items.size)
        assertEquals(60, (bar1AfterMerge.items[0] as ScoreItem.Note).pitch.midiPitch)
    }
}
