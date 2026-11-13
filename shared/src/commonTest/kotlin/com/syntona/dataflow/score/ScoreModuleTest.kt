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
        data class DrawnGlyph(val glyph: String, val x: Float, val baselineY: Float, val fontSizePx: Float)
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
            glyphs.add(DrawnGlyph(glyph, x, baselineY, fontSizePx))
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
}
