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
    var sample: FloatSample by mutableStateOf(createSample())

    private val queueOnIn = addCommandInput("queueOn")
    private val queueOffIn = addCommandInput("queueOff")
    private val queueIn = addCommandInput("queue")
    private val queueLoopIn = addCommandInput("queueLoop")
    private val output = addDataQueueOutput("output")
    private val rateOutput = addOutput("rate")

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        when (jack) {
            queueOnIn -> queueSample(Message.DataQueueOpcode.QUEUE_ON)
            queueOffIn -> queueSample(Message.DataQueueOpcode.QUEUE_OFF)
            queueIn -> queueSample(Message.DataQueueOpcode.QUEUE)
            queueLoopIn -> queueSample(Message.DataQueueOpcode.QUEUE_LOOP)
        }
    }

    /**
     * Emits the sample's native frame rate on [rateOutput] first, so a reader's rate port is
     * already set by the time the queue command arrives, then queues the sample itself.
     */
    private fun queueSample(opcode: Message.DataQueueOpcode) {
        val now = ScoreClock.now()
        rateOutput.send(Message.FloatValue(now, sample.frameRate))
        output.send(Message.DataQueueMessage(now, sample, opcode))
    }

    override fun execute(triggerMessage: Message) {}

    companion object {
        /** Frames are allocated for this many seconds of mono audio. */
        const val DURATION_SECONDS = 8

        /**
         * Allocates a silent mono sample holding [DURATION_SECONDS] of audio at the synth's
         * frame rate. FloatSample's constructor leaves [FloatSample.frameRate] at its 44100.0
         * default, so it is set explicitly here to match the buffer that was actually
         * allocated — otherwise the "rate" output would misreport at any other synth rate.
         */
        private fun createSample(): FloatSample {
            val frameRate = SynthKSyn.synth.frameRate
            return FloatSample(frameRate * DURATION_SECONDS).apply {
                this.frameRate = frameRate.toDouble()
            }
        }
    }
}
