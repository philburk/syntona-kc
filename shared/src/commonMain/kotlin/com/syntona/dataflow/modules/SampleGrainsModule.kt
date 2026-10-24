package com.syntona.dataflow.modules

import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.data.SequentialData
import com.softsynth.ksyn.unitgen.SampleGrains
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.toMonoFloatSample
import com.syntona.dataflow.registry.ModuleRegistry

/**
 * Granular synthesis module that uses a connected sample buffer ([FloatSampleModule] or [RecorderModule])
 * as source material via KSyn's [SampleGrains].
 */
class SampleGrainsModule : UnitGeneratorModule(
    TYPE_NAME,
    SampleGrains().apply { allocate(ModuleRegistry.GRAIN_FARM_GRAINS) }
) {
    val grains: SampleGrains get() = unit as SampleGrains

    val dataInput: DataQueueInputJack = addDataQueueInput("data", index = 0)

    var sample: FloatSample?
        get() = grains.getSample()
        set(value) {
            val mono = value?.toMono()
            grains.setSample(mono)
            (parentPatch as? PolyPatchModule)?.syncSample(this, mono)
        }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        if (jack === dataInput) {
            val data = ((msg as? Message.DataQueueMessage)?.data as? SequentialData) ?: dataInput.currentData
            sample = data?.toMonoFloatSample()
        }
    }

    companion object {
        const val TYPE_NAME = "sampleGrains"
    }
}
