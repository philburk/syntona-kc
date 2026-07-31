package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.ShowModule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers having several top level documents open at once, which is what lets an example be opened
 * without discarding work in progress.
 */
class MultiDocumentTest {

    @AfterTest
    fun clearDocuments() {
        // FileState is a singleton, so one test must not leak into the next.
        FileState.documents.toList().forEach { FileState.removeDocument(it) }
        FileState.clearPendingAction()
    }

    private fun newDocument(name: String): DocumentState =
        DocumentState(PatchModule(name)).also { FileState.addDocument(it) }

    @Test
    fun testEachDocumentTracksItsOwnFileAndDirtyState() {
        val first = newDocument("first")
        val second = newDocument("second")

        first.markSaved("/tmp/first.json")
        second.markSaved("examples/second.json")
        first.markDirty()

        assertTrue(first.isDirty, "editing one document should not depend on the other")
        assertFalse(second.isDirty)
        assertEquals("/tmp/first.json", first.currentFilePath)
        assertEquals("examples/second.json", second.currentFilePath)
        assertFalse(first.isResource)
        assertTrue(second.isResource, "an example is a resource whichever document holds it")
    }

    @Test
    fun testFileStateReportsTheFrontmostDocument() {
        val first = newDocument("first")
        val second = newDocument("second")
        first.markSaved("/tmp/first.json")
        second.markSaved("/tmp/second.json")

        // addDocument focuses what it just added.
        assertSame(second, FileState.activeDocument)
        assertEquals("/tmp/second.json", FileState.currentFilePath)

        FileState.focusDocument(first)
        assertEquals("/tmp/first.json", FileState.currentFilePath)
        assertEquals("first.json", FileState.currentFileName)
    }

    @Test
    fun testDocumentIsFoundFromANestedPatch() {
        val document = newDocument("root")
        val child = PatchModule("child")
        document.rootPatch.graphState.addModule(child, Offset.Zero)
        val grandchild = PatchModule("grandchild")
        child.graphState.addModule(grandchild, Offset.Zero)

        assertSame(document, FileState.documentFor(document.rootPatch.graphState))
        assertSame(document, FileState.documentFor(child.graphState), "one level down")
        assertSame(document, FileState.documentFor(grandchild.graphState), "two levels down")
        assertNull(FileState.documentFor(GraphState()), "a graph with no document")
    }

    @Test
    fun testFocusingAWindowSwitchesTheActiveDocument() {
        val mdi = MdiState()
        val first = newDocument("first")
        val second = newDocument("second")

        val firstWindow = MdiWindowInfo(
            id = first.rootPatch,
            title = "first",
            graphState = first.rootPatch.graphState,
            content = {}
        )
        val secondWindow = MdiWindowInfo(
            id = second.rootPatch,
            title = "second",
            graphState = second.rootPatch.graphState,
            content = {}
        )
        mdi.open(firstWindow)
        mdi.open(secondWindow)
        assertSame(second, FileState.activeDocument)

        mdi.bringToFront(firstWindow)
        assertSame(first, FileState.activeDocument, "the File menu should follow the front window")
        assertSame(first.rootPatch.graphState, mdi.rootGraphState)
    }

    /**
     * The trap in this design: clearing a document before a load must not take the other
     * documents' windows with it.
     */
    @Test
    fun testClosingSubWindowsLeavesOtherDocumentsAlone() {
        val mdi = MdiState()
        val first = newDocument("first")
        val second = newDocument("second")

        val firstSub = PatchModule("firstSub")
        first.rootPatch.graphState.addModule(firstSub, Offset.Zero)
        val secondSub = PatchModule("secondSub")
        second.rootPatch.graphState.addModule(secondSub, Offset.Zero)

        listOf(
            first.rootPatch to first.rootPatch.graphState,
            firstSub to firstSub.graphState,
            second.rootPatch to second.rootPatch.graphState,
            secondSub to secondSub.graphState
        ).forEach { (patch, graph) ->
            mdi.open(
                MdiWindowInfo(id = patch, title = patch.name, graphState = graph, content = {})
            )
        }
        assertEquals(4, mdi.windows.size)

        mdi.closeSubWindows(first)

        assertTrue(mdi.windows.any { it.id === first.rootPatch }, "keeps its own top window")
        assertFalse(mdi.windows.any { it.id === firstSub }, "closes its own sub window")
        assertTrue(mdi.windows.any { it.id === second.rootPatch }, "other document untouched")
        assertTrue(mdi.windows.any { it.id === secondSub }, "other document's sub window untouched")
    }

    @Test
    fun testClosingATopWindowClosesThatDocument() {
        val mdi = MdiState()
        val first = newDocument("first")
        val second = newDocument("second")
        val secondSub = PatchModule("secondSub")
        second.rootPatch.graphState.addModule(secondSub, Offset.Zero)

        mdi.open(
            MdiWindowInfo(
                id = first.rootPatch, title = "first",
                graphState = first.rootPatch.graphState, closable = false, content = {}
            )
        )
        mdi.open(
            MdiWindowInfo(
                id = second.rootPatch, title = "second",
                graphState = second.rootPatch.graphState, content = {}
            )
        )
        mdi.open(
            MdiWindowInfo(
                id = secondSub, title = "secondSub", graphState = secondSub.graphState, content = {}
            )
        )

        mdi.close(second.rootPatch)

        assertFalse(FileState.documents.any { it === second }, "the document should be gone")
        assertFalse(mdi.windows.any { it.id === secondSub }, "its sub windows should go too")
        assertTrue(mdi.windows.any { it.id === first.rootPatch }, "the other document remains")
        assertSame(first, FileState.activeDocument, "focus falls back to what is left")
    }

    @Test
    fun testTheFirstDocumentCannotBeClosed() {
        val mdi = MdiState()
        val first = newDocument("first")
        mdi.open(
            MdiWindowInfo(
                id = first.rootPatch, title = "first",
                graphState = first.rootPatch.graphState, closable = false, content = {}
            )
        )
        mdi.close(first.rootPatch)
        assertTrue(FileState.documents.any { it === first }, "there must always be somewhere to work")
        assertEquals(1, mdi.windows.size)
    }

    @Test
    fun testQuitConsidersEveryDocument() {
        val first = newDocument("first")
        val second = newDocument("second")
        assertFalse(FileState.hasAnyUnsavedWork)

        // A background document with unsaved work must still block quitting.
        first.markDirty()
        FileState.focusDocument(second)
        assertFalse(FileState.isDirty, "the frontmost document is clean")
        assertTrue(FileState.hasAnyUnsavedWork, "but another one is not")
        assertEquals(listOf(first), FileState.documentsWithUnsavedWork)
    }

    @Test
    fun testAnExampleIsNotTreatedAsUnsavedWork() {
        val document = newDocument("doc")
        document.markSaved("examples/thing.json")
        document.markDirty()
        assertTrue(document.isDirty)
        assertFalse(
            document.hasUnsavedWork,
            "a bundled example can be reloaded, so it is not work at risk"
        )
        assertFalse(FileState.hasAnyUnsavedWork)
    }

    @Test
    fun testTheNewWindowOptionIsOnlyOfferedWhenGiven() {
        val document = newDocument("doc")
        document.markSaved("/tmp/a.json")
        document.markDirty()

        var replaced = 0
        var openedNew = 0
        FileState.requestIfDirty(openInNewWindow = { openedNew++ }) { replaced++ }
        assertEquals(0, replaced, "should wait for the user to choose")
        assertTrue(FileState.pendingOpenInNewWindow != null, "third button should be offered")

        FileState.confirmPendingInNewWindow()
        assertEquals(1, openedNew)
        assertEquals(0, replaced, "the current document must be left alone")
        assertNull(FileState.pendingAction, "the dialog should close")

        // Without an alternative, only Discard and Cancel are possible.
        FileState.requestIfDirty { replaced++ }
        assertNull(FileState.pendingOpenInNewWindow)
        FileState.confirmPendingAction()
        assertEquals(1, replaced)
    }

    @Test
    fun testACleanDocumentSkipsTheDialogEntirely() {
        val document = newDocument("doc")
        document.markSaved("/tmp/a.json")

        var ran = 0
        FileState.requestIfDirty(openInNewWindow = { }) { ran++ }
        assertEquals(1, ran, "nothing to lose, so no confirmation")
        assertNull(FileState.pendingAction)
    }

    /**
     * The file name has to stay with its own window. A global effect used to copy the frontmost
     * document's path onto the first document's patch, so opening an example in a second window
     * relabelled the original window with the example's name as soon as it was focused.
     */
    @Test
    fun testEachWindowKeepsItsOwnFileName() {
        val mine = newDocument("mine")
        mine.markSaved("/tmp/mine.json")

        val example = newDocument("example")
        example.markSaved("examples/glow_flash.json")

        // Focusing the example must not touch the other document's patch.
        FileState.focusDocument(example)
        assertEquals("/tmp/mine.json", mine.rootPatch.filePath)
        assertEquals("examples/glow_flash.json", example.rootPatch.filePath)

        FileState.focusDocument(mine)
        assertEquals("/tmp/mine.json", mine.rootPatch.filePath)
        assertEquals("examples/glow_flash.json", example.rootPatch.filePath)

        // Titles are derived from the patch path, so they stay distinct too.
        assertTrue(mine.rootPatch.headerTitle.contains("mine.json"))
        assertTrue(example.rootPatch.headerTitle.contains("glow_flash.json"))
    }

    @Test
    fun testStartingANewDocumentClearsItsOwnFileNameOnly() {
        val first = newDocument("first")
        first.markSaved("/tmp/first.json")
        val second = newDocument("second")
        second.markSaved("/tmp/second.json")

        second.markNew()
        assertEquals(null, second.rootPatch.filePath)
        assertEquals("/tmp/first.json", first.rootPatch.filePath, "the other document is untouched")
    }

    @Test
    fun testEditsMarkTheOwningDocumentNotTheFrontmostOne() {
        val background = newDocument("background")
        // Wire the change hook the way openNewDocument does.
        background.rootPatch.graphState.onChange = { background.markDirty() }
        val front = newDocument("front")

        assertSame(front, FileState.activeDocument)
        background.rootPatch.graphState.addModule(ShowModule(), Offset.Zero)

        assertTrue(background.isDirty, "the document that changed should be the dirty one")
        assertFalse(front.isDirty, "the frontmost document did not change")
    }
}
