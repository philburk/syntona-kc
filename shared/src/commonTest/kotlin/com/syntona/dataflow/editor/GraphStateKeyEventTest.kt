package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.KeyModule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the interaction between playing notes on the computer keyboard and the application's own
 * keyboard shortcuts.
 */
class GraphStateKeyEventTest {

    private fun graphWithKeyModule(): GraphState {
        val graphState = GraphState()
        graphState.addModule(KeyModule(), Offset.Zero)
        return graphState
    }

    @Test
    fun testPlainLetterIsHandledAsANote() {
        val graphState = graphWithKeyModule()
        assertTrue(
            graphState.handleKeyStroke('C'.code, isDown = true),
            "a plain letter should reach the key module"
        )
    }

    @Test
    fun testAutoRepeatIsIgnoredWhileTheKeyIsHeld() {
        val graphState = graphWithKeyModule()
        assertTrue(graphState.handleKeyStroke('C'.code, isDown = true))
        assertTrue(
            graphState.handleKeyStroke('C'.code, isDown = true),
            "a repeat while held should be swallowed rather than retriggering the note"
        )
        graphState.handleKeyStroke('C'.code, isDown = false)
        assertTrue(
            graphState.handleKeyStroke('C'.code, isDown = true),
            "after release the note should trigger again"
        )
    }

    /**
     * The regression. Cmd+C used to look identical to playing the note "C", so the graph consumed
     * it and Copy never ran. Worse, macOS does not deliver a KeyUp for a letter while Cmd is held,
     * so "C" stayed in the pressed set and every later Cmd+C was discarded as an auto-repeat,
     * which is why copy worked once or twice and then stopped for good.
     */
    @Test
    fun testCommandKeystrokesAreNotTreatedAsNotes() {
        val graphState = graphWithKeyModule()

        assertFalse(
            graphState.handleKeyStroke('C'.code, isDown = true, isCommandPressed = true),
            "Cmd+C must fall through so the Copy shortcut can run"
        )
        // Repeated with no KeyUp in between, which is the situation macOS actually produces and
        // the reason copying used to stop working after the first press or two.
        repeat(5) {
            assertFalse(
                graphState.handleKeyStroke('C'.code, isDown = true, isCommandPressed = true),
                "Cmd+C must keep falling through"
            )
        }
        assertFalse(
            graphState.handleKeyStroke('V'.code, isDown = true, isCommandPressed = true),
            "Cmd+V must fall through so the Paste shortcut can run"
        )

        // The command presses must not have left the letters stuck down, which would have
        // silenced those notes for the rest of the session.
        assertTrue(
            graphState.handleKeyStroke('C'.code, isDown = true),
            "playing C afterwards should still work"
        )
        assertTrue(
            graphState.handleKeyStroke('V'.code, isDown = true),
            "playing V afterwards should still work"
        )
    }
}
