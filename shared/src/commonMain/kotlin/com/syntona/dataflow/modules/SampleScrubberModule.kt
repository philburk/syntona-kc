package com.syntona.dataflow.modules

import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.data.SequentialData
import com.softsynth.ksyn.unitgen.SampleScrubber
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.toMonoFloatSample

/**
 * Wraps KSyn's [SampleScrubber] with a "data" [DataQueueInputJack] for setting the sample,
 * similar to [SampleReaderModule].
 */
class SampleScrubberModule : UnitGeneratorModule(TYPE_NAME, SampleScrubber()) {
    val sampleScrubber: SampleScrubber
        get() = unit as SampleScrubber

    val dataInput: DataQueueInputJack = addDataQueueInput("data", index = 0)

    var sample: FloatSample?
        get() = sampleScrubber.getSample()
        set(value) {
            sampleScrubber.setSample(value)
        }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === dataInput && msg is Message.DataQueueMessage) {
            val mono = (msg.data as? SequentialData)?.toMonoFloatSample()
            sample = mono
            (parentPatch as? PolyPatchModule)?.syncSample(this, mono)
        }
        super.onMessageReceived(jack, msg)
    }

    companion object {
        const val TYPE_NAME = "sampleScrubber"
    }
}
