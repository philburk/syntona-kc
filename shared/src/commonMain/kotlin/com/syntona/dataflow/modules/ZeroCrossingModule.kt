package com.syntona.dataflow.modules

import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.unitgen.ZeroCrossingDetector

/**
 * A Syntona module that detects positive zero crossings of an audio signal.
 *
 * Provides:
 * - "in" (AudioInputJack): The incoming audio signal.
 * - "out" (AudioOutputJack): Audio-rate impulse signal (1.0f on crossing sample, 0.0f otherwise).
 * - "hysteresis" (AudioInputJack): Threshold required to re-arm and trigger, preventing chatter.
 * - "holdOffMs" (AudioInputJack): Minimum duration in milliseconds between triggers (min 10.0 ms, default 40.0 ms).
 * - "reset" (CommandJack): Resets the internal counter and state.
 * - "trigger" (OutputJack): Control-rate event output that sends a [Message.FloatValue]
 *   containing the cumulative crossing count whenever a zero crossing is detected.
 */
class ZeroCrossingModule : Module("zeroCrossing"), EngineAware {

    override val isAllowedInPolyPatch: Boolean
        get() = false

    val detector = ZeroCrossingDetector()

    val input: AudioInputJack
    val output: AudioOutputJack
    val hysteresis: AudioInputJack
    val holdOffMs: AudioInputJack
    val resetIn: CommandJack
    val trigger: OutputJack

    private var engine: DataFlowEngine? = null

    init {
        SynthKSyn.synth.add(detector)
        if (detector.isStartRequired) {
            detector.start()
        }

        input = addAudioInput("in", detector.input, 0)
        hysteresis = addAudioInput("hysteresis", detector.hysteresis, 0).also {
            it.min = 0.0
            it.max = 1.0
            it.setValue(0.01)
        }
        holdOffMs = addAudioInput("holdOffMs", detector.holdOffMs, 0).also {
            it.min = 10.0
            it.max = 1000.0
            it.setValue(40.0)
        }
        resetIn = addCommandInput("reset")
        output = addAudioOutput("out", detector.output, 0)
        trigger = addOutput("trigger")

        detector.onZeroCrossing = { synthTime, _ ->
            val e = engine
            if (e != null) {
                val now = ScoreClock.now()
                e.post(SendOutputCommand(trigger, Message.FloatValue(now, synthTime)))
            }
        }
    }

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun execute(triggerMessage: Message) {
        if (resetIn.isKicked) {
            detector.reset()
            trigger.send(Message.FloatValue(triggerMessage.time, 0.0))
        }
    }

    override fun teardown() {
        detector.onZeroCrossing = null
        detector.stop()
        SynthKSyn.synth.remove(detector)
        super.teardown()
    }
}
