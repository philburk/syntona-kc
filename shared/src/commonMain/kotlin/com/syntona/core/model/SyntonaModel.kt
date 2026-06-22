package com.syntona.core.model

import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.engine.DataFlowEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SyntonaModel {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val engine = DataFlowEngine(scope)

    fun start() {
        SynthKSyn.start()
    }
}
