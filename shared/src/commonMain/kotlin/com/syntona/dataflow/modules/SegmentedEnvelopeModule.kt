package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.data.SegmentedEnvelope
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class SegmentedEnvelopeModule : Module("segEnv") {
    var envelope: SegmentedEnvelope by mutableStateOf(
        SegmentedEnvelope(
            doubleArrayOf(
                0.02, 0.9, // duration,value pair 0, "attack"
                0.10, 0.5, // pair 1, "decay"
                0.50, 0.0  // pair 2, "release"
            )
        ).apply {
            // Hang at end of decay segment to provide a "sustain" segment.
            // Note End point is one past the last index.
            sustainBegin = 2
            sustainEnd = 2
        }
    )

    private val queueOnIn = addCommandInput("queueOn")
    private val queueOffIn = addCommandInput("queueOff")
    private val queueIn = addCommandInput("queue")
    private val output = addDataQueueOutput("out")

    override fun onMessageReceived(jack: com.syntona.dataflow.model.InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        when (jack) {
            queueOnIn -> output.send(Message.DataQueueMessage(ScoreClock.now(), envelope, Message.DataQueueOpcode.QUEUE_ON))
            queueOffIn -> output.send(Message.DataQueueMessage(ScoreClock.now(), envelope, Message.DataQueueOpcode.QUEUE_OFF))
            queueIn -> output.send(Message.DataQueueMessage(ScoreClock.now(), envelope, Message.DataQueueOpcode.QUEUE))
        }
    }

    override fun execute(triggerMessage: Message) {}
}
