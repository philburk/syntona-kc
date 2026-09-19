package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.KickJackCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertTrue

class TimelinePlaybackTest {

    private fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            Thread.sleep(10)
        }
    }

    @Test
    fun testBeatLineTriggerAtTimeZeroFiresOnStart() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val engine = DataFlowEngine(scope)
            val beatLine = BeatLineModule()
            beatLine.setEngine(engine)
            val flash = FlashModule()
            flash.setEngine(engine)

            val trigger = beatLine.addTrigger(0.0)
            trigger.outputJack.connect(flash.flashInput)

            // Rewind then start
            beatLine.seekTo(0.0)
            engine.post(KickJackCommand(beatLine.start, ScoreClock.now()))

            waitUntil { flash.triggerCount > 0 }
            assertTrue(flash.triggerCount >= 1, "Trigger at time 0 should fire and increment flash.triggerCount")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun testBeatLineTriggerAtTimeZeroFiresOnRequestRecordToggle() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val engine = DataFlowEngine(scope)
            val beatLine = BeatLineModule()
            beatLine.setEngine(engine)
            val flash = FlashModule()
            flash.setEngine(engine)

            val trigger = beatLine.addTrigger(0.0)
            trigger.outputJack.connect(flash.flashInput)

            // Empty MIDI track with no regions (matches user scenario)
            beatLine.addTrack(TrackType.MIDI)

            // Rewind then toggle record
            beatLine.seekTo(0.0)
            beatLine.requestRecordToggle()

            waitUntil { beatLine.isRecording && flash.triggerCount > 0 }
            assertTrue(beatLine.isRecording, "beatLine should be recording")
            assertTrue(flash.triggerCount >= 1, "Trigger at time 0 must fire and increment flash.triggerCount when recording starts")

            // Toggle record again to stop
            beatLine.requestRecordToggle()
            waitUntil { !beatLine.isRecording }
            assertTrue(!beatLine.isRecording)
        } finally {
            scope.cancel()
        }
    }
}
