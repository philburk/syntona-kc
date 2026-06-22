package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack

class PatchModule(name: String = "patch") : Module(name), EngineAware {

    // Maps jacks on this PatchModule to the I/O modules inside the patch
    private val inputLinks = mutableMapOf<InputJack, InputModule>()
    private val outputLinks = mutableMapOf<OutputJack, OutputModule>()

    val graphState = GraphState(onModuleAdded = ::onChildModuleAdded)

    override fun setEngine(engine: DataFlowEngine) {
        graphState.engine = engine
    }

    private fun onChildModuleAdded(module: Module) {
        when (module) {
            is InputModule -> {
                val jack = addInput("in${inputLinks.size + 1}")
                inputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.displayName = jack.name
            }
            is OutputModule -> {
                val jack = addOutput("out${outputLinks.size + 1}")
                outputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.displayName = jack.name
            }
        }
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        // Forward incoming messages to the matching InputModule inside the patch
        inputLinks[jack]?.receive(msg)
        super.onMessageReceived(jack, msg)
    }

    // Called by OutputModule when it receives a message — routes to the patch's OutputJack
    internal fun receiveFromOutputModule(outputModule: OutputModule, msg: Message) {
        outputLinks.entries.find { it.value === outputModule }?.key?.send(msg)
    }

    override fun execute(triggerMessage: Message) {}
}
