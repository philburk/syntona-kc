package com.syntona.dataflow.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineBeatUtilsTest {

    @Test
    fun testCalculateSmallestVisibleSubdivision() {
        val density = 1.0f

        // At zoom 1.0: 24 px per beat -> minSpacing = 8 px -> 1/2 beat is 12 px (>= 8 px)
        val subZoom1 = TimelineBeatUtils.calculateSmallestVisibleSubdivision(24.0, density)
        assertEquals(0.5, subZoom1, "At 24 px/beat, smallest visible subdivision should be 1/2 beat")

        // At zoom 1.5: 36 px per beat -> 1/4 beat is 9 px (>= 8 px)
        val subZoom1_5 = TimelineBeatUtils.calculateSmallestVisibleSubdivision(36.0, density)
        assertEquals(0.25, subZoom1_5, "At 36 px/beat, smallest visible subdivision should be 1/4 beat")

        // At zoom 2.0: 48 px per beat -> 1/4 beat is 12 px (>= 8 px)
        val subZoom2 = TimelineBeatUtils.calculateSmallestVisibleSubdivision(48.0, density)
        assertEquals(0.25, subZoom2, "At 48 px/beat, smallest visible subdivision should be 1/4 beat")

        // At zoom 3.0: 72 px per beat -> 1/8 beat is 9 px (>= 8 px)
        val subZoom3 = TimelineBeatUtils.calculateSmallestVisibleSubdivision(72.0, density)
        assertEquals(0.125, subZoom3, "At 72 px/beat, smallest visible subdivision should be 1/8 beat")

        // At zoom 6.0: 144 px per beat -> 1/16 beat is 9 px (>= 8 px)
        val subZoom6 = TimelineBeatUtils.calculateSmallestVisibleSubdivision(144.0, density)
        assertEquals(0.0625, subZoom6, "At 144 px/beat, smallest visible subdivision should be 1/16 beat")

        // Zoomed out (zoom 0.5): 12 px per beat -> 1 beat is 12 px (>= 8 px)
        val subZoomOut = TimelineBeatUtils.calculateSmallestVisibleSubdivision(12.0, density)
        assertEquals(1.0, subZoomOut, "At 12 px/beat, smallest visible subdivision should be 1 beat")

        // Zoomed far out (zoom 0.2): 4.8 px per beat -> 2 beats is 9.6 px (>= 8 px)
        val subZoomFarOut = TimelineBeatUtils.calculateSmallestVisibleSubdivision(4.8, density)
        assertEquals(2.0, subZoomFarOut, "At 4.8 px/beat, smallest visible subdivision should be 2 beats")

        // Verify pixel spacing is always >= minSpacing (8 px)
        val testScales = listOf(2.4, 4.8, 12.0, 24.0, 36.0, 48.0, 72.0, 96.0, 144.0, 288.0)
        for (scale in testScales) {
            val sub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(scale, density)
            assertTrue(sub * scale >= 7.999, "Subdivision $sub at scale $scale gives spacing ${sub * scale} px (< 8 px)")
        }
    }

    @Test
    fun testSnapToSubdivisionQuarterBeats() {
        val quarter = 0.25
        assertEquals(0.0, TimelineBeatUtils.snapToSubdivision(0.0, quarter))
        assertEquals(1.0, TimelineBeatUtils.snapToSubdivision(1.12, quarter))
        assertEquals(1.25, TimelineBeatUtils.snapToSubdivision(1.13, quarter))
        assertEquals(1.25, TimelineBeatUtils.snapToSubdivision(1.25, quarter))
        assertEquals(1.25, TimelineBeatUtils.snapToSubdivision(1.37, quarter))
        assertEquals(1.5, TimelineBeatUtils.snapToSubdivision(1.38, quarter))
        assertEquals(2.5, TimelineBeatUtils.snapToSubdivision(2.49, quarter))
        assertEquals(2.75, TimelineBeatUtils.snapToSubdivision(2.8, quarter))
    }

    @Test
    fun testSnapToSubdivisionEighthBeats() {
        val eighth = 0.125
        assertEquals(0.0, TimelineBeatUtils.snapToSubdivision(0.0, eighth))
        assertEquals(0.0, TimelineBeatUtils.snapToSubdivision(0.06, eighth))
        assertEquals(0.125, TimelineBeatUtils.snapToSubdivision(0.07, eighth))
        assertEquals(0.125, TimelineBeatUtils.snapToSubdivision(0.125, eighth))
        assertEquals(0.375, TimelineBeatUtils.snapToSubdivision(0.38, eighth))
        assertEquals(0.5, TimelineBeatUtils.snapToSubdivision(0.51, eighth))
        assertEquals(0.875, TimelineBeatUtils.snapToSubdivision(0.87, eighth))
    }

    @Test
    fun testSnapToSubdivisionHalfAndWholeBeats() {
        assertEquals(1.0, TimelineBeatUtils.snapToSubdivision(1.24, 0.5))
        assertEquals(1.5, TimelineBeatUtils.snapToSubdivision(1.26, 0.5))
        assertEquals(3.0, TimelineBeatUtils.snapToSubdivision(3.4, 1.0))
        assertEquals(4.0, TimelineBeatUtils.snapToSubdivision(3.6, 1.0))
        assertEquals(2.0, TimelineBeatUtils.snapToSubdivision(2.9, 2.0))
        assertEquals(4.0, TimelineBeatUtils.snapToSubdivision(3.1, 2.0))
    }

    @Test
    fun testCalculateLabelInterval() {
        val density = 1.0f
        // 24 px per beat: 35 px min label spacing -> label every 2 beats (48 px)
        val lZoom1 = TimelineBeatUtils.calculateLabelInterval(24.0, density)
        assertEquals(2.0, lZoom1)

        // 48 px per beat: >= 35 px -> label every 1 beat (48 px)
        val lZoom2 = TimelineBeatUtils.calculateLabelInterval(48.0, density)
        assertEquals(1.0, lZoom2)

        // 6 px per beat: 35 px min -> label every 8 beats (48 px)
        val lZoomFar = TimelineBeatUtils.calculateLabelInterval(6.0, density)
        assertEquals(8.0, lZoomFar)
    }

    @Test
    fun testFormatBeatLabel() {
        assertEquals("0", TimelineBeatUtils.formatBeatLabel(0.0))
        assertEquals("4", TimelineBeatUtils.formatBeatLabel(4.0))
        assertEquals("16", TimelineBeatUtils.formatBeatLabel(16.0))
        assertEquals("2.5", TimelineBeatUtils.formatBeatLabel(2.5))
    }
}
