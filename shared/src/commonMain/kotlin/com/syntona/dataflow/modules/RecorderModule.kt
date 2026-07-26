package com.syntona.dataflow.modules

import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.unitgen.Recorder

/**
 * Records the audio arriving at "in" and plays it back to "out", driven by the record, play,
 * pause and rewind buttons.
 *
 * While recording, a repeating task on the engine coroutine refills the recording's segment pool
 * so that the audio thread can grow the recording without allocating.
 */
class RecorderModule : Module("recorder"), EngineAware {

    val unit = Recorder()

    val input: AudioInputJack
    val amplitude: AudioInputJack
    val recordIn: CommandJack
    val playIn: CommandJack
    val pauseIn: CommandJack
    val rewindIn: CommandJack
    val output: AudioOutputJack

    private var engine: DataFlowEngine? = null
    private var replenishEvent: ScheduledEvent? = null

    init {
        SynthKSyn.synth.add(unit)
        if (unit.isStartRequired) unit.start()

        input = addAudioInput("in", unit.input, 0)
        amplitude = addAudioInput("amplitude", unit.amplitude, 0)
        recordIn = addCommandInput("record")
        playIn = addCommandInput("play")
        pauseIn = addCommandInput("pause")
        rewindIn = addCommandInput("rewind")
        output = addAudioOutput("out", unit.output, 0)

        // Fill the pool up front so the first record does not have to allocate.
        unit.replenish()
    }

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun execute(triggerMessage: Message) {
        when {
            recordIn.isKicked -> {
                unit.record()
                startReplenishing()
            }
            playIn.isKicked -> unit.play()
            pauseIn.isKicked -> unit.pause()
            rewindIn.isKicked -> unit.rewind()
        }
    }

    /**
     * Keeps the segment pool topped up while recording. Scheduling happens on the engine
     * coroutine, so this must only be started from [execute] or from a scheduled event.
     */
    private fun startReplenishing() {
        if (replenishEvent != null) return
        scheduleReplenish()
    }

    private fun scheduleReplenish() {
        val e = engine ?: return
        replenishEvent = e.schedule(ScoreClock.now() + ScoreTime(REPLENISH_PERIOD_NS)) {
            replenishEvent = null
            unit.replenish()
            // Recording may have been paused, or have stopped on its own, since the last refill.
            if (unit.isRecording) scheduleReplenish()
        }
    }

    override fun teardown() {
        replenishEvent?.cancel()
        replenishEvent = null
        unit.pause()
        if (unit.isStartRequired) unit.stop()
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }

    companion object {
        /**
         * A default segment holds about 85 ms of audio at 48 kHz, so refilling this often keeps
         * the pool far ahead of demand. Refilling a full pool does nothing, so this is cheap.
         */
        private const val REPLENISH_PERIOD_NS = 20_000_000L
    }
}
