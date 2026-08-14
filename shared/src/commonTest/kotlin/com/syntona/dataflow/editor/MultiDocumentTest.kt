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

        first.markSaved("/tmp/first.sntn")
        second.markSaved("examples/second.sntn")
        first.markDirty()

        assertTrue(first.isDirty, "editing one document should not depend on the other")
        assertFalse(second.isDirty)
        assertEquals("/tmp/first.sntn", first.currentFilePath)
        assertEquals("examples/second.sntn", second.currentFilePath)
        assertFalse(first.isResource)
        assertTrue(second.isResource, "an example is a resource whichever document holds it")
    }

    @Test
    fun testFileStateReportsTheFrontmostDocument() {
        val first = newDocument("first")
        val second = newDocument("second")
        first.markSaved("/tmp/first.sntn")
        second.markSaved("/tmp/second.sntn")

        // addDocument focuses what it just added.
        assertSame(second, FileState.activeDocument)
        assertEquals("/tmp/second.sntn", FileState.currentFilePath)

        FileState.focusDocument(first)
        assertEquals("/tmp/first.sntn", FileState.currentFilePath)
        assertEquals("first.sntn", FileState.currentFileName)
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
    fun testPianoRollEditorWindowClosesOnNewDocument() {
        val mdi = MdiState()
        val doc = newDocument("doc")
        val rootGraph = doc.rootPatch.graphState

        val pianoRoll = com.syntona.dataflow.modules.PianoRollModule()
        rootGraph.addModule(pianoRoll, Offset.Zero)

        val rootWindow = MdiWindowInfo(
            id = doc.rootPatch,
            title = "doc",
            graphState = rootGraph,
            closable = false,
            content = {}
        )
        val pianoRollWindow = MdiWindowInfo(
            id = pianoRoll,
            title = pianoRoll.name,
            graphState = rootGraph,
            content = {}
        )
        mdi.open(rootWindow)
        mdi.open(pianoRollWindow)
        assertEquals(2, mdi.windows.size)

        mdi.closeSubWindows(doc)

        assertEquals(1, mdi.windows.size)
        assertTrue(mdi.windows.any { it.id === doc.rootPatch })
        assertFalse(mdi.windows.any { it.id === pianoRoll })
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
        document.markSaved("examples/thing.sntn")
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
        document.markSaved("/tmp/a.sntn")
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
        document.markSaved("/tmp/a.sntn")

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
        mine.markSaved("/tmp/mine.sntn")

        val example = newDocument("example")
        example.markSaved("examples/glow_flash.sntn")

        // Focusing the example must not touch the other document's patch.
        FileState.focusDocument(example)
        assertEquals("/tmp/mine.sntn", mine.rootPatch.filePath)
        assertEquals("examples/glow_flash.sntn", example.rootPatch.filePath)

        FileState.focusDocument(mine)
        assertEquals("/tmp/mine.sntn", mine.rootPatch.filePath)
        assertEquals("examples/glow_flash.sntn", example.rootPatch.filePath)

        // Titles are derived from the patch path, so they stay distinct too.
        assertTrue(mine.rootPatch.headerTitle.contains("mine.sntn"))
        assertEquals("example", example.rootPatch.headerTitle)
    }

    @Test
    fun testStartingANewDocumentClearsItsOwnFileNameOnly() {
        val first = newDocument("first")
        first.markSaved("/tmp/first.sntn")
        val second = newDocument("second")
        second.markSaved("/tmp/second.sntn")

        second.markNew()
        assertEquals(null, second.rootPatch.filePath)
        assertEquals("root", second.rootPatch.name, "patch name should reset to root")
        assertEquals("root", second.displayName, "display name should reset to root")
        assertEquals("root", second.rootPatch.headerTitle, "header title should reset to root")
        assertEquals("/tmp/first.sntn", first.rootPatch.filePath, "the other document is untouched")
    }

    @Test
    fun testMarkNewResetsWindowHeaderToRootAfterLoadingExample() {
        val mdi = MdiState()
        val doc = newDocument("root")
        val rootWindow = MdiWindowInfo(
            id = doc.rootPatch,
            title = doc.rootPatch.name,
            closable = false,
            graphState = doc.rootPatch.graphState,
            content = {}
        )
        mdi.open(rootWindow)

        // Simulate loading an example into the document
        doc.markSaved("examples/drum_rhythm.json")
        rootWindow.title = doc.displayName

        assertEquals("drum_rhythm", rootWindow.title)
        assertEquals("drum_rhythm", doc.rootPatch.headerTitle)

        // File -> New
        doc.markNew()

        assertEquals("root", doc.rootPatch.name)
        assertEquals("root", doc.rootPatch.headerTitle)
        assertEquals("root", rootWindow.title)
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

    @Test
    fun testDeletePatchClosesItsWindow() {
        val mdi = MdiState()
        val doc = newDocument("doc")
        val rootWindow = MdiWindowInfo(
            id = doc.rootPatch,
            title = "doc",
            closable = false,
            graphState = doc.rootPatch.graphState,
            content = {}
        )
        mdi.open(rootWindow)

        val childPatch = PatchModule("osc")
        doc.rootPatch.graphState.addModule(childPatch, Offset.Zero)

        val childWindow = MdiWindowInfo(
            id = childPatch,
            title = "osc",
            closable = true,
            graphState = childPatch.graphState,
            content = {}
        )
        mdi.open(childWindow)

        assertEquals(2, mdi.windows.size)
        assertTrue(childPatch.isWindowOpen)

        doc.rootPatch.graphState.selectModule(childPatch)
        doc.rootPatch.graphState.deleteSelectedModules()

        assertEquals(1, mdi.windows.size, "deleting the patch module should close its window")
        assertSame(rootWindow, mdi.windows.first())
        assertFalse(childPatch.isWindowOpen)
    }

    @Test
    fun testDeleteParentPatchClosesDescendantWindows() {
        val mdi = MdiState()
        val doc = newDocument("doc")
        val rootWindow = MdiWindowInfo(
            id = doc.rootPatch,
            title = "doc",
            closable = false,
            graphState = doc.rootPatch.graphState,
            content = {}
        )
        mdi.open(rootWindow)

        val parent = PatchModule("parent")
        doc.rootPatch.graphState.addModule(parent, Offset.Zero)

        val child = PatchModule("child")
        parent.graphState.addModule(child, Offset.Zero)

        val parentWindow = MdiWindowInfo(
            id = parent,
            title = "parent",
            closable = true,
            graphState = parent.graphState,
            content = {}
        )
        val childWindow = MdiWindowInfo(
            id = child,
            title = "child",
            closable = true,
            graphState = child.graphState,
            content = {}
        )
        mdi.open(parentWindow)
        mdi.open(childWindow)

        assertEquals(3, mdi.windows.size)

        doc.rootPatch.graphState.selectModule(parent)
        doc.rootPatch.graphState.deleteSelectedModules()

        assertEquals(1, mdi.windows.size, "deleting the parent patch should close all descendant windows")
        assertSame(rootWindow, mdi.windows.first())
        assertFalse(parent.isWindowOpen)
        assertFalse(child.isWindowOpen)
    }

    @Test
    fun testDeleteModuleWithWindowClosesWindow() {
        val mdi = MdiState()
        val doc = newDocument("doc")
        val rootWindow = MdiWindowInfo(
            id = doc.rootPatch,
            title = "doc",
            closable = false,
            graphState = doc.rootPatch.graphState,
            content = {}
        )
        mdi.open(rootWindow)

        val pianoRoll = com.syntona.dataflow.modules.PianoRollModule()
        doc.rootPatch.graphState.addModule(pianoRoll, Offset.Zero)

        val pianoRollWindow = MdiWindowInfo(
            id = pianoRoll,
            title = "pianoRoll",
            closable = true,
            graphState = doc.rootPatch.graphState,
            content = {}
        )
        mdi.open(pianoRollWindow)

        assertEquals(2, mdi.windows.size)

        doc.rootPatch.graphState.selectModule(pianoRoll)
        doc.rootPatch.graphState.deleteSelectedModules()

        assertEquals(1, mdi.windows.size, "deleting a module with an open window should close that window")
        assertSame(rootWindow, mdi.windows.first())
    }
}
