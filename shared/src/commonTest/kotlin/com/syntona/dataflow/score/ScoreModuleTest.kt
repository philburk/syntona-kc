package com.syntona.dataflow.score

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
        val score = ScoreModule()
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
        val scoreModule = ScoreModule()
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
}
