package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.CounterModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.TimelineModule
import com.syntona.dataflow.modules.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Only one window may have a selection. A user double-clicked a polyPatch to open it, which left
 * the polyPatch selected in the parent window, then selected a module inside it; DELETE went to
 * the parent and deleted the whole polyPatch.
 */
class SingleWindowSelectionTest {

    private class Setup {
        val mdiState = MdiState()
        val parent = GraphState()
        val polyPatch = PolyPatchModule()
        val inner = CounterModule()

        init {
            parent.addModule(polyPatch, Offset.Zero)
            polyPatch.graphState.addModule(inner, Offset.Zero)
            mdiState.open(MdiWindowInfo(id = "parent", title = "parent", graphState = parent, content = {}))
            mdiState.open(
                MdiWindowInfo(id = polyPatch, title = polyPatch.name, graphState = polyPatch.graphState, content = {})
            )
        }
    }

    @Test
    fun selectingInOneWindowDeselectsTheOthers() {
        val s = Setup()
        s.parent.selectModule(s.polyPatch)

        s.polyPatch.graphState.selectModule(s.inner)

        assertTrue(s.parent.selectedModules.isEmpty(), "the polyPatch should no longer be selected in the parent")
        assertEquals(listOf(s.inner), s.polyPatch.graphState.selectedModules.toList())
    }

    @Test
    fun aRectangleSelectionDeselectsTheOthers() {
        val s = Setup()
        s.parent.selectModule(s.polyPatch)

        s.polyPatch.graphState.selectModulesInRect(Offset(-10f, -10f), Offset(500f, 500f), density = 1f)

        assertTrue(s.parent.selectedModules.isEmpty())
        assertTrue(s.inner in s.polyPatch.graphState.selectedModules)
    }

    @Test
    fun deleteAfterSelectingInsideLeavesTheParentAlone() {
        // The user's exact case: whichever window DELETE reaches, the polyPatch must survive.
        val s = Setup()
        s.parent.selectModule(s.polyPatch)
        s.polyPatch.graphState.selectModule(s.inner)

        s.parent.deleteSelected()

        assertTrue(s.polyPatch in s.parent.modules, "the polyPatch was deleted from the parent")
    }

    @Test
    fun selectingATrackSectionDeselectsTheOthers() {
        val s = Setup()
        val timeline = TimelineModule()
        s.polyPatch.graphState.addModule(timeline, Offset(400f, 0f))
        val track = timeline.addTrack(TrackType.AUDIO, "a")
        val section = track.addSectionDirect(id = 1, start = 0.0, duration = 1.0, repeats = 1, name = "s")
        s.parent.selectModule(s.polyPatch)

        s.polyPatch.graphState.selectTrackSection(timeline, track, section)

        assertTrue(s.parent.selectedModules.isEmpty())
        timeline.teardown()
    }
}
