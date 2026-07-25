package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.data.FloatSample
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Holds a mono [FloatSample] that can be queued onto a KSyn data queue port, such as the
 * "monoRdr" reader. This mirrors [SegmentedEnvelopeModule] but carries audio sample data
 * instead of an envelope.
 */
class FloatSampleModule : Module("sample") {
    // Sized from the synth's frame rate, which defaults to 44100 before the synth is started,
    // so the buffer is always allocated even if no module is created after audio starts.
    var sample: FloatSample by mutableStateOf(
        FloatSample(SynthKSyn.synth.frameRate * DURATION_SECONDS)
    )

    private val queueOnIn = addCommandInput("queueOn")
    private val queueOffIn = addCommandInput("queueOff")
    private val queueIn = addCommandInput("queue")
    private val queueLoopIn = addCommandInput("queueLoop")
    private val output = addDataQueueOutput("output")

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        when (jack) {
            queueOnIn -> output.send(Message.DataQueueMessage(ScoreClock.now(), sample, Message.DataQueueOpcode.QUEUE_ON))
            queueOffIn -> output.send(Message.DataQueueMessage(ScoreClock.now(), sample, Message.DataQueueOpcode.QUEUE_OFF))
            queueIn -> output.send(Message.DataQueueMessage(ScoreClock.now(), sample, Message.DataQueueOpcode.QUEUE))
            queueLoopIn -> output.send(Message.DataQueueMessage(ScoreClock.now(), sample, Message.DataQueueOpcode.QUEUE_LOOP))
        }
    }

    override fun execute(triggerMessage: Message) {}

    companion object {
        /** Frames are allocated for this many seconds of mono audio. */
        const val DURATION_SECONDS = 8
    }
}
