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
}
