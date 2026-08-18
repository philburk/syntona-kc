package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.PatchModule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WindowMenuTest {

    @AfterTest
    fun clearDocuments() {
        FileState.documents.toList().forEach { FileState.removeDocument(it) }
    }

    private fun document(name: String, path: String? = null): DocumentState =
        DocumentState(PatchModule(name)).also {
            FileState.addDocument(it)
            if (path != null) it.markSaved(path)
        }

    private fun MdiState.openWindowFor(patch: PatchModule): MdiWindowInfo {
        val window = MdiWindowInfo(
            id = patch, title = patch.name, graphState = patch.graphState, content = {}
        )
        open(window)
        return window
    }

    @Test
    fun testUsesTheFileNameWhenThereIsOne() {
        val saved = document("root", "/Users/phil/Documents/glow_flash.sntn")
        val unsaved = document("sketch")
        assertEquals(
            listOf("glow_flash.sntn", "sketch"),
            WindowMenu.documentLabels(listOf(saved, unsaved))
        )
    }

    @Test
    fun testSameFileNameIsResolvedByShowingEnoughOfThePath() {
        val example = document("root", "/app/composeResources/files/examples/glow_flash.sntn")
        val tutorial = document("root", "/app/composeResources/files/tutorials/glow_flash.sntn")
        assertEquals(
            listOf("examples/glow_flash.sntn", "tutorials/glow_flash.sntn"),
            WindowMenu.documentLabels(listOf(example, tutorial))
        )
    }

    @Test
    fun testWidensFurtherWhenOneFolderIsNotEnough() {
        val first = document("root", "/a/shared/patches/song.sntn")
        val second = document("root", "/b/shared/patches/song.sntn")
        val labels = WindowMenu.documentLabels(listOf(first, second))
        assertEquals(2, labels.toSet().size, "labels must end up distinct: $labels")
        assertTrue(labels.all { it.endsWith("song.sntn") })
        assertTrue(labels[0] != labels[1])
    }

    @Test
    fun testUnsavedDocumentsWithTheSameNameAreNumbered() {
        val first = document("root")
        val second = document("root")
        val third = document("root")
        assertEquals(
            listOf("root", "root (2)", "root (3)"),
            WindowMenu.documentLabels(listOf(first, second, third)),
            "with no path to widen, fall back to numbering"
        )
    }

    @Test
    fun testOnlyCollidingNamesAreQualified() {
        val a = document("root", "/x/examples/one.sntn")
        val b = document("root", "/y/examples/two.sntn")
        val labels = WindowMenu.documentLabels(listOf(a, b))
        assertEquals(
            listOf("one.sntn", "two.sntn"),
            labels,
            "distinct names should stay short rather than being padded with folders"
        )
    }

    @Test
    fun testPatchWindowsAreGroupedUnderTheirDocument() {
        val mdi = MdiState()
        val first = document("first", "/tmp/first.sntn")
        val second = document("second", "/tmp/second.sntn")

        val firstOsc = PatchModule("osc")
        first.rootPatch.graphState.addModule(firstOsc, Offset.Zero)
        val secondOsc = PatchModule("osc")
        second.rootPatch.graphState.addModule(secondOsc, Offset.Zero)

        mdi.openWindowFor(first.rootPatch)
        mdi.openWindowFor(firstOsc)
        mdi.openWindowFor(second.rootPatch)
        mdi.openWindowFor(secondOsc)

        val entries = WindowMenu.build(mdi)
        assertEquals(
            listOf("first.sntn", "osc", "second.sntn", "osc"),
            entries.map { it.label },
            "each document is followed by its own patch windows"
        )
        assertEquals(
            listOf(true, false, true, false),
            entries.map { it.isDocument },
            "the grouping is what tells the two 'osc' windows apart"
        )
    }

    @Test
    fun testPatchWindowsCollidingInsideOneDocumentAreNumbered() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/doc.sntn")
        val first = PatchModule("osc")
        val second = PatchModule("osc")
        doc.rootPatch.graphState.addModule(first, Offset.Zero)
        doc.rootPatch.graphState.addModule(second, Offset.Zero)

        mdi.openWindowFor(doc.rootPatch)
        mdi.openWindowFor(first)
        mdi.openWindowFor(second)

        assertEquals(
            listOf("doc.sntn", "osc", "osc (2)"),
            WindowMenu.build(mdi).map { it.label }
        )
    }

    @Test
    fun testTheFrontmostWindowIsMarked() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/doc.sntn")
        val child = PatchModule("child")
        doc.rootPatch.graphState.addModule(child, Offset.Zero)

        val rootWindow = mdi.openWindowFor(doc.rootPatch)
        val childWindow = mdi.openWindowFor(child)

        assertTrue(WindowMenu.build(mdi).single { it.window === childWindow }.isActive)
        assertFalse(WindowMenu.build(mdi).single { it.window === rootWindow }.isActive)

        mdi.bringToFront(rootWindow)
        assertTrue(WindowMenu.build(mdi).single { it.window === rootWindow }.isActive)
    }

    @Test
    fun testShowRestoresAMinimizedWindowAndBringsItForward() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/doc.sntn")
        val child = PatchModule("child")
        doc.rootPatch.graphState.addModule(child, Offset.Zero)

        val rootWindow = mdi.openWindowFor(doc.rootPatch)
        val childWindow = mdi.openWindowFor(child)
        rootWindow.minimized = true

        WindowMenu.show(mdi, rootWindow)
        assertFalse(rootWindow.minimized, "picking a minimized window should restore it")
        assertSame(rootWindow, mdi.windows.last(), "and bring it to the front")
        assertSame(doc.rootPatch.graphState, mdi.activeGraphState)
        assertTrue(childWindow in mdi.windows)
    }

    @Test
    fun testUnsavedWorkIsMarkedOnTheDocumentEntry() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/doc.sntn")
        mdi.openWindowFor(doc.rootPatch)
        assertEquals("doc.sntn", WindowMenu.build(mdi).first().label)

        doc.markDirty()
        assertEquals(
            "doc.sntn*",
            WindowMenu.build(mdi).first().label,
            "an asterisk shows which documents have unsaved work"
        )
    }

    /**
     * The marker sits to the left of the indent and reserves its width when absent, so names at
     * the same level always start in the same column whichever window happens to be in front.
     */
    @Test
    fun testNamesLineUpWhicheverWindowIsInFront() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/song.sntn")
        val osc = PatchModule("osc")
        val filter = PatchModule("filter")
        doc.rootPatch.graphState.addModule(osc, Offset.Zero)
        doc.rootPatch.graphState.addModule(filter, Offset.Zero)

        val rootWindow = mdi.openWindowFor(doc.rootPatch)
        val oscWindow = mdi.openWindowFor(osc)
        mdi.openWindowFor(filter)

        fun columnOf(label: String) =
            WindowMenu.build(mdi).single { it.label == label }.menuText.indexOfFirst { it != ' ' && it != '*' }

        val documentColumn = columnOf("song.sntn")
        val childColumn = columnOf("osc")
        assertEquals(columnOf("filter"), childColumn, "sub windows share one indent")
        assertTrue(childColumn > documentColumn, "sub windows are indented past the document")

        // Moving the marker around must not move any name.
        WindowMenu.show(mdi, oscWindow)
        assertEquals(documentColumn, columnOf("song.sntn"))
        assertEquals(childColumn, columnOf("osc"))
        assertEquals(childColumn, columnOf("filter"))

        WindowMenu.show(mdi, rootWindow)
        assertEquals(documentColumn, columnOf("song.sntn"))
        assertEquals(childColumn, columnOf("osc"))
    }

    @Test
    fun testTheFrontWindowIsMarkedWithAnAsterisk() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/song.sntn")
        val osc = PatchModule("osc")
        doc.rootPatch.graphState.addModule(osc, Offset.Zero)
        mdi.openWindowFor(doc.rootPatch)
        val oscWindow = mdi.openWindowFor(osc)

        // osc was opened last, so it is the one in front.
        val entries = WindowMenu.build(mdi)
        assertEquals("*     osc", entries.single { it.window === oscWindow }.menuText)
        assertEquals("  song.sntn", entries.first().menuText)

        WindowMenu.show(mdi, mdi.windows.first { it.id === doc.rootPatch })
        val afterFocus = WindowMenu.build(mdi)
        assertEquals("* song.sntn", afterFocus.first().menuText)
        assertEquals("      osc", afterFocus.single { it.window === oscWindow }.menuText)
    }

    @Test
    fun testMinimizedWindowsAreLabelled() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/song.sntn")
        val window = mdi.openWindowFor(doc.rootPatch)
        window.minimized = true
        assertTrue(WindowMenu.build(mdi).first().menuText.endsWith("(minimized)"))
    }

    @Test
    fun testWindowsWithoutADocumentStillAppear() {
        val mdi = MdiState()
        val doc = document("doc", "/tmp/doc.sntn")
        mdi.openWindowFor(doc.rootPatch)
        val standalone = MdiWindowInfo(id = "pianoRoll", title = "Piano Roll", content = {})
        mdi.open(standalone)

        val entries = WindowMenu.build(mdi)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.window === standalone && it.label == "Piano Roll" })
    }

    @Test
    fun testNewWindowOverExistingWindowIsOffsetDownAndRight() {
        val mdi = MdiState()
        mdi.density = 1f
        val win1 = MdiWindowInfo(id = "p1", title = "P1", position = Offset(100f, 100f), content = {})
        val win2 = MdiWindowInfo(id = "p2", title = "P2", position = Offset(100f, 100f), content = {})

        mdi.open(win1)
        assertEquals(Offset(100f, 100f), win1.position)

        mdi.open(win2)
        // win2 should be offset down and right from win1
        assertTrue(win2.position.x > win1.position.x)
        assertTrue(win2.position.y > win1.position.y)
        assertEquals(Offset(128f, 128f), win2.position)
    }
}
