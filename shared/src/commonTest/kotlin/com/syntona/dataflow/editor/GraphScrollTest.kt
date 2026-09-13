package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.NumberModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphScrollTest {

    @Test
    fun testInitialScrollState() {
        val graphState = GraphState()
        assertEquals(0f, graphState.scrollX, "scrollX should initialize to 0")
        assertEquals(0f, graphState.scrollY, "scrollY should initialize to 0")
    }

    @Test
    fun testEmptyGraphContentExtent() {
        val graphState = GraphState()
        val density = 1.0f
        val extent = graphState.computeContentExtent(density)
        assertEquals(0f, extent.width, "Empty graph content width should be 0")
        assertEquals(0f, extent.height, "Empty graph content height should be 0")
    }

    @Test
    fun testContentExtentWithModules() {
        val graphState = GraphState()
        val density = 2.0f
        val padding = 40f * density // 80px

        val mod1 = NumberModule()
        graphState.addModule(mod1, Offset(100f, 50f))
        graphState.updateModuleSize(mod1, 160f, 120f)

        val extent1 = graphState.computeContentExtent(density, padding)
        // pos in px: (100 * 2 = 200, 50 * 2 = 100)
        // right = 200 + 160 = 360; bottom = 100 + 120 = 220
        assertEquals(360f + padding, extent1.width)
        assertEquals(220f + padding, extent1.height)

        val mod2 = NumberModule()
        graphState.addModule(mod2, Offset(300f, 200f))
        graphState.updateModuleSize(mod2, 100f, 80f)

        val extent2 = graphState.computeContentExtent(density, padding)
        // mod2 right = 300 * 2 + 100 = 700; bottom = 200 * 2 + 80 = 480
        assertEquals(700f + padding, extent2.width)
        assertEquals(480f + padding, extent2.height)
    }

    @Test
    fun testScrollbarsNeededOnlyWhenContentExceedsViewport() {
        val graphState = GraphState()
        val density = 1.0f

        val mod = NumberModule()
        graphState.addModule(mod, Offset(400f, 300f))
        graphState.updateModuleSize(mod, 100f, 100f)

        val extent = graphState.computeContentExtent(density, padding = 20f)
        // right = 400 + 100 + 20 = 520
        // bottom = 300 + 100 + 20 = 420
        assertEquals(520f, extent.width)
        assertEquals(420f, extent.height)

        // Case 1: Large desktop window (e.g. 1000x800) -> fits completely
        val largeViewportWidth = 1000f
        val largeViewportHeight = 800f
        val maxScrollXLarge = maxOf(0f, maxOf(largeViewportWidth, extent.width) - largeViewportWidth)
        val maxScrollYLarge = maxOf(0f, maxOf(largeViewportHeight, extent.height) - largeViewportHeight)
        assertFalse(maxScrollXLarge > 0f, "Horizontal scrollbar should NOT appear when content fits in large window")
        assertFalse(maxScrollYLarge > 0f, "Vertical scrollbar should NOT appear when content fits in large window")

        // Case 2: Small laptop window (e.g. 400x350) -> content exceeds viewport
        val smallViewportWidth = 400f
        val smallViewportHeight = 350f
        val maxScrollXSmall = maxOf(0f, maxOf(smallViewportWidth, extent.width) - smallViewportWidth)
        val maxScrollYSmall = maxOf(0f, maxOf(smallViewportHeight, extent.height) - smallViewportHeight)
        assertTrue(maxScrollXSmall > 0f, "Horizontal scrollbar SHOULD appear on small laptop window")
        assertTrue(maxScrollYSmall > 0f, "Vertical scrollbar SHOULD appear on small laptop window")
        assertEquals(120f, maxScrollXSmall)
        assertEquals(70f, maxScrollYSmall)
    }

    @Test
    fun testResetScrollAndClearAllModules() {
        val graphState = GraphState()
        val mod = NumberModule()
        graphState.addModule(mod, Offset(100f, 100f))

        graphState.scrollX = 45f
        graphState.scrollY = 60f
        assertEquals(45f, graphState.scrollX)
        assertEquals(60f, graphState.scrollY)

        graphState.resetScroll()
        assertEquals(0f, graphState.scrollX)
        assertEquals(0f, graphState.scrollY)

        graphState.scrollX = 20f
        graphState.scrollY = 30f
        graphState.clearAllModules()
        assertEquals(0f, graphState.scrollX)
        assertEquals(0f, graphState.scrollY)
        assertTrue(graphState.modules.isEmpty())
    }
}
