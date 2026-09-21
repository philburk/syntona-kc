package com.syntona.dataflow.editor

import com.syntona.dataflow.modules.BeatLineModule
import com.syntona.dataflow.modules.TimelineModule
import com.syntona.dataflow.modules.TrackType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ruler, the loop markers and the trigger row turn a pointer position into a time by dividing
 * by the pixels per unit. That has to be the scale the lanes were drawn at, or clicking the ruler
 * lands somewhere else than it looks.
 */
class TimelineZoomTest {

    private fun timeAtPixel(module: TimelineModule, x: Float, densityScale: Float): Double =
        (x / timelinePxPerUnit(module, densityScale)).toDouble()

    /** Where the lanes actually draw [time], the way TrackLaneView does. */
    private fun pixelOfTime(module: TimelineModule, time: Double, densityScale: Float): Float {
        val zoom = timelineCoercedZoom(module, timelineLayoutOf(module).viewportWidth)
        return (time * densityScale * 24f * zoom).toFloat()
    }

    @Test
    fun clickingTheRulerLandsOnTheTimeDrawnThere() {
        val beatLine = BeatLineModule()
        beatLine.width = 900.0
        beatLine.maxTime = 30.0 // As after dragging the end of song marker right
        beatLine.addTrack(TrackType.MESSAGE) // Gives the module its input and output jack columns
        beatLine.zoom = 0.01f // Zoomed out far enough to be clamped to the minimum

        val density = 2f
        listOf(4.0, 12.0, 26.0, 29.5).forEach { time ->
            val x = pixelOfTime(beatLine, time, density)
            assertEquals(time, timeAtPixel(beatLine, x, density), 0.001, "Click at beat $time")
        }
    }

    @Test
    fun theOutputJackColumnCountsTowardsTheViewport() {
        val withOutputs = BeatLineModule().apply {
            width = 900.0
            maxTime = 30.0
            addTrack(TrackType.MESSAGE) // Has both an input and an output jack
        }
        val withoutOutputs = BeatLineModule().apply {
            width = 900.0
            maxTime = 30.0
        }
        assertTrue(
            timelineLayoutOf(withOutputs).viewportWidth < timelineLayoutOf(withoutOutputs).viewportWidth,
            "Jack columns take space away from the lanes"
        )
    }

    @Test
    fun theWholeSongFitsAtTheSmallestZoom() {
        val beatLine = BeatLineModule().apply {
            width = 900.0
            maxTime = 30.0
            addTrack(TrackType.MESSAGE)
            zoom = 0.001f // Below the minimum, so it is clamped
        }
        val layout = timelineLayoutOf(beatLine)
        val zoom = timelineCoercedZoom(beatLine, layout.viewportWidth)
        val drawnWidth = 24f * (beatLine.maxTime.toFloat() + 5f) * zoom
        assertTrue(
            abs(drawnWidth - layout.viewportWidth.value) < 0.01f,
            "At the smallest zoom the song plus its tail fills the lanes exactly, was $drawnWidth"
        )
    }
}
