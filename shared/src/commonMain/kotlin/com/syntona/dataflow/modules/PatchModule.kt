package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack

open class PatchModule(name: String = "patch") : Module(name), EngineAware {

    // Maps jacks on this PatchModule to the I/O modules inside the patch
    protected val inputLinks = mutableMapOf<InputJack, InputModule>()
    protected val outputLinks = mutableMapOf<OutputJack, OutputModule>()
    protected val audioInputLinks = mutableMapOf<AudioInputJack, AudioInputModule>()
    protected val audioOutputLinks = mutableMapOf<AudioOutputJack, AudioOutputModule>()

    val graphState = GraphState(onModuleAdded = ::onChildModuleAdded)

    override fun setEngine(engine: DataFlowEngine) {
        graphState.engine = engine
    }

    protected open fun onChildModuleAdded(module: Module) {
        module.parentPatch = this
        when (module) {
            is InputModule -> {
                val proposedName = if (module.name == "input") "in${inputLinks.size + 1}" else module.name
                val jack = addInput(proposedName)
                inputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.name = proposedName
            }
            is OutputModule -> {
                val proposedName = if (module.name == "output") "out${outputLinks.size + 1}" else module.name
                val jack = addOutput(proposedName)
                outputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.name = proposedName
            }
            is AudioInputModule -> {
                val proposedName = if (module.name == "aInput") "aIn${audioInputLinks.size + 1}" else module.name
                val jack = addAudioInput(proposedName, module.passThrough.input, 0)
                audioInputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.name = proposedName
            }
            is AudioOutputModule -> {
                val proposedName = if (module.name == "aOutput") "aOut${audioOutputLinks.size + 1}" else module.name
                val jack = addAudioOutput(proposedName, module.passThrough.output, 0)
                audioOutputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.name = proposedName
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

    override fun teardown() {
        graphState.clearAllModules()
        super.teardown()
    }
}
