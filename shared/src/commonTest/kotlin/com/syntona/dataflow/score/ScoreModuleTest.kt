package com.syntona.dataflow.score

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
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

        val lines = mutableListOf<DrawnLine>()
        val rects = mutableListOf<DrawnRect>()
        val beziers = mutableListOf<DrawnBezier>()
        val glyphs = mutableListOf<DrawnGlyph>()
        val texts = mutableListOf<DrawnText>()

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
    fun testPhase1RenderEmitsStaffClefSignaturesNotesAndRests() {
        val score = ScoreData()
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
        val score = ScoreModule()
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
        val score = ScoreData()
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
}
