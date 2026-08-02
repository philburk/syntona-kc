package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.SYNTONA_FILE_SUFFIX

open class PatchModule(name: String = "patch") : Module(name), EngineAware {

    // Maps jacks on this PatchModule to the I/O modules inside the patch
    protected val inputLinks = mutableMapOf<InputJack, InputModule>()
    protected val outputLinks = mutableMapOf<OutputJack, OutputModule>()
    protected val audioInputLinks = mutableMapOf<AudioInputJack, AudioInputModule>()
    protected val audioOutputLinks = mutableMapOf<AudioOutputJack, AudioOutputModule>()

    // Last known position/size (in dp) of this patch's editing window, kept live-synced by
    // PatchModuleNode while the window is open so it can be saved and restored with the patch.
    var windowX by mutableStateOf<Float?>(null)
    var windowY by mutableStateOf<Float?>(null)
    var windowWidth by mutableStateOf<Float?>(null)
    var windowHeight by mutableStateOf<Float?>(null)
    var isWindowOpen by mutableStateOf(false)

    var filePath by mutableStateOf<String?>(null)
    var isModified by mutableStateOf(false)

    val headerTitle: String
        get() {
            val suffix = if (isModified) "*" else ""
            val path = filePath
            return if (path == null) {
                "$name$suffix"
            } else {
                val isResource = path.startsWith("examples/") || path.startsWith("tutorials/") || path.startsWith("help/")
                if (isResource) {
                    "$name$suffix"
                } else {
                    val marker = "composeResources/files/"
                    val formattedPath = if (path.contains(marker)) {
                        path.substringAfter(marker)
                    } else {
                        path
                    }
                    val cleanPath = if (formattedPath.endsWith(".json")) {
                        formattedPath.removeSuffix(".json") + SYNTONA_FILE_SUFFIX
                    } else {
                        formattedPath
                    }
                    "$name$suffix - $cleanPath"
                }
            }
        }

    val graphState: GraphState = GraphState(onModuleAdded = ::onChildModuleAdded).also { gs ->
        gs.parentPatch = this
        gs.onModuleRemovedCallback = ::onChildModuleRemoved
        gs.onChange = {
            if (!gs.isDeserializing) {
                isModified = true
                parentPatch?.graphState?.markChanged()
            }
        }
    }

    fun findModifiedReferencedPatches(): List<PatchModule> {
        val result = mutableListOf<PatchModule>()
        fun traverse(patch: PatchModule) {
            for (module in patch.graphState.modules) {
                if (module is PatchModule) {
                    if (module.filePath != null && module.isModified) {
                        result.add(module)
                    }
                    traverse(module)
                }
            }
        }
        traverse(this)
        return result
    }

    fun clearModifiedRecursively() {
        isModified = false
        for (module in graphState.modules) {
            if (module is PatchModule) {
                module.clearModifiedRecursively()
            }
        }
    }

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

    protected open fun onChildModuleRemoved(module: Module) {
        when (module) {
            is InputModule -> {
                val jack = module.patchJack
                if (jack != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(jack)
                        parentGs.jackBounds.remove(jack)
                        val conns = parentGs.connections.filter { it.input === jack }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeInput(jack)
                    inputLinks.remove(jack)
                }
            }
            is OutputModule -> {
                val jack = module.patchJack
                if (jack != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(jack)
                        parentGs.jackBounds.remove(jack)
                        val conns = parentGs.connections.filter { it.output === jack }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeOutput(jack)
                    outputLinks.remove(jack)
                }
            }
            is AudioInputModule -> {
                val jack = module.patchJack
                if (jack != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(jack)
                        parentGs.jackBounds.remove(jack)
                        val conns = parentGs.connections.filter { it.input === jack }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeInput(jack)
                    audioInputLinks.remove(jack)
                }
            }
            is AudioOutputModule -> {
                val jack = module.patchJack
                if (jack != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(jack)
                        parentGs.jackBounds.remove(jack)
                        val conns = parentGs.connections.filter { it.output === jack }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeOutput(jack)
                    audioOutputLinks.remove(jack)
                }
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
