package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.ShowModule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditActionsTest {

    @AfterTest
    fun clearClipboard() {
        // CopyPasteService is a singleton, so one test must not leak into the next.
        CopyPasteService.clear()
    }

    private fun mdiWithGraph(): Pair<MdiState, GraphState> {
        val graphState = GraphState()
        val mdi = MdiState()
        mdi.rootGraphState = graphState
        mdi.activeGraphState = graphState
        return mdi to graphState
    }

    @Test
    fun testCopyRequiresASelection() {
        val (mdi, graphState) = mdiWithGraph()
        graphState.addModule(ShowModule(), Offset.Zero)

        assertFalse(EditActions.canCopy(mdi), "nothing selected yet")
        EditActions.copy(mdi)
        assertFalse(EditActions.canPaste(mdi), "copying nothing should not fill the clipboard")

        graphState.selectModule(graphState.modules.first())
        assertTrue(EditActions.canCopy(mdi))
        EditActions.copy(mdi)
        assertTrue(EditActions.canPaste(mdi), "copy should have filled the clipboard")
    }

    @Test
    fun testCopyThenPasteAddsAModule() {
        val (mdi, graphState) = mdiWithGraph()
        graphState.addModule(ShowModule(), Offset(10f, 20f))
        graphState.selectModule(graphState.modules.first())

        EditActions.copy(mdi)
        EditActions.paste(mdi)

        assertEquals(2, graphState.modules.size, "paste should have added a copy")
    }

    @Test
    fun testCutRemovesTheSelectionAndFillsTheClipboard() {
        val (mdi, graphState) = mdiWithGraph()
        graphState.addModule(ShowModule(), Offset.Zero)
        graphState.selectModule(graphState.modules.first())

        assertTrue(EditActions.canCut(mdi))
        EditActions.cut(mdi)

        assertEquals(0, graphState.modules.size, "cut should have removed the module")
        assertTrue(EditActions.canPaste(mdi), "cut should have filled the clipboard")
    }

    /**
     * The regression this all exists for: the desktop menu bar builds each item once and reuses
     * its action for the keyboard shortcut. If the action captured the active graph when the menu
     * was composed, then selecting modules afterwards, or bringing another window forward, would
     * leave the shortcut copying from the wrong place, or from nothing at all.
     */
    @Test
    fun testActionsFollowTheActiveGraphRatherThanACapturedOne() {
        val mdi = MdiState()
        val firstGraph = GraphState()
        mdi.rootGraphState = firstGraph
        mdi.activeGraphState = firstGraph

        // Stand in for the menu being composed while nothing is selected anywhere.
        val actionBuiltEarly = { EditActions.copy(mdi) }
        assertFalse(EditActions.canCopy(mdi))

        // The user then brings a different window forward and selects something in it.
        val secondGraph = GraphState()
        secondGraph.addModule(ShowModule(), Offset(5f, 5f))
        secondGraph.selectModule(secondGraph.modules.first())
        mdi.activeGraphState = secondGraph

        assertTrue(EditActions.canCopy(mdi), "should see the newly active graph")
        actionBuiltEarly()
        assertTrue(
            EditActions.canPaste(mdi),
            "an action built earlier must still copy from whatever is active now"
        )

        EditActions.paste(mdi)
        assertEquals(2, secondGraph.modules.size, "paste should land in the active graph")
        assertEquals(0, firstGraph.modules.size, "the stale graph should be untouched")
    }

    @Test
    fun testPasteDoesNothingWithAnEmptyClipboard() {
        val (mdi, graphState) = mdiWithGraph()
        assertFalse(EditActions.canPaste(mdi))
        EditActions.paste(mdi)
        assertEquals(0, graphState.modules.size)
    }

    @Test
    fun testActionsAreSafeWithNoActiveGraph() {
        val mdi = MdiState()
        assertFalse(EditActions.canCut(mdi))
        assertFalse(EditActions.canCopy(mdi))
        assertFalse(EditActions.canPaste(mdi))
        // Must not throw when a shortcut fires with nothing open.
        EditActions.cut(mdi)
        EditActions.copy(mdi)
        EditActions.paste(mdi)
    }

    @Test
    fun testAWindowWithItsOwnHandlerTakesPrecedence() {
        val (mdi, graphState) = mdiWithGraph()
        graphState.addModule(ShowModule(), Offset.Zero)
        graphState.selectModule(graphState.modules.first())

        var copied = 0
        var pasted = 0
        val handler = object : EditActionHandler {
            override fun canCopy() = true
            override fun canPaste() = true
            override fun canDelete() = true
            override fun cut() { copied++ }
            override fun copy() { copied++ }
            override fun paste() { pasted++ }
            override fun delete() {}
        }
        mdi.open(
            MdiWindowInfo(id = "editor", title = "Editor", content = {})
                .also { it.editHandler = handler }
        )

        EditActions.copy(mdi)
        EditActions.paste(mdi)
        assertEquals(1, copied, "the frontmost window's handler should have received the copy")
        assertEquals(1, pasted)
        assertEquals(
            1,
            graphState.modules.size,
            "the module graph must not also be pasted into"
        )
    }
}
