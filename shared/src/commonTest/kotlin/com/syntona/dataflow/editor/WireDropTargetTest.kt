package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.softsynth.ksyn.unitgen.FilterLowPass
import com.softsynth.ksyn.unitgen.SineOscillator
import com.syntona.dataflow.modules.UnitGeneratorModule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dropping a wire near the edge of a jack must connect to the jack under the pointer,
 * not to a neighbor whose enlarged hit margin overlaps it.
 */
class WireDropTargetTest {

    private fun dropAt(pointerY: Float): String {
        val graphState = GraphState()
        val osc = UnitGeneratorModule("osc", SineOscillator())
        val filter = UnitGeneratorModule("filter", FilterLowPass())
        graphState.addModule(osc, Offset.Zero)
        graphState.addModule(filter, Offset(300f, 0f))

        val upper = filter.inputs.first { it.name == "in" }
        val lower = filter.inputs.first { it.name == "frequency" }
        // Two adjacent 16px jacks. Register the lower jack first so that picking the
        // first overlapping margin would choose it.
        graphState.updateJackOffset(lower, Offset(300f, 124f), Size(40f, 16f))
        graphState.updateJackOffset(upper, Offset(300f, 108f), Size(40f, 16f))

        graphState.startDraggingFromOutput(osc.outputs.first { it.name == "out" })
        graphState.onPointerMove(Offset(310f, pointerY))
        graphState.onPointerUp(null)
        return graphState.connections.single().input.name
    }

    @Test
    fun testDropOnBottomOfUpperJackConnectsToUpperJack() {
        // Upper jack spans y 100..116, lower jack spans 116..132.
        assertEquals("in", dropAt(114f))
    }

    @Test
    fun testDropOnTopOfLowerJackConnectsToLowerJack() {
        assertEquals("frequency", dropAt(118f))
    }
}
