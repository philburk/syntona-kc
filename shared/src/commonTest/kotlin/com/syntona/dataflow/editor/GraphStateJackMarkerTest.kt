package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.FaderModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GraphStateJackMarkerTest {

    @Test
    fun testJackMarkerCentersTrackingAndCleanup() {
        val graphState = GraphState()
        val fader = FaderModule()
        graphState.modules.add(fader)

        val inJack = fader.inputs.first()
        val outJack = fader.outputs.first()

        // Set marker centers
        val inCenter = Offset(100f, 200f)
        val outCenter = Offset(300f, 200f)
        graphState.jackMarkerCenters[inJack] = inCenter
        graphState.jackMarkerCenters[outJack] = outCenter

        assertEquals(inCenter, graphState.jackMarkerCenters[inJack])
        assertEquals(outCenter, graphState.jackMarkerCenters[outJack])

        // Remove module directly
        graphState.removeModulesDirectly(listOf(fader))

        // Ensure jackMarkerCenters were cleaned up
        assertFalse(graphState.jackMarkerCenters.containsKey(inJack))
        assertFalse(graphState.jackMarkerCenters.containsKey(outJack))
    }
}
