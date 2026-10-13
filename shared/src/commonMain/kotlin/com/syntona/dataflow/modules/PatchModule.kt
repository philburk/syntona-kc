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

open class PatchModule(name: String = "patch", type: String = "patch") : Module(type), EngineAware {

    init {
        this.name = name
    }

    // Maps jacks on this PatchModule to the I/O modules inside the patch
    protected val inputLinks = mutableMapOf<InputJack, InputModule>()
    protected val commandLinks = mutableMapOf<InputJack, CommandModule>()
    protected val outputLinks = mutableMapOf<OutputJack, OutputModule>()
    protected val audioInputLinks = mutableMapOf<AudioInputJack, AudioInputModule>()
    protected val audioOutputLinks = mutableMapOf<AudioOutputJack, AudioOutputModule>()
    protected val audioOutput2Links = mutableMapOf<AudioOutputJack, AudioOutput2Module>()

    // Last known position/size (in dp) of this patch's editing window, kept live-synced by
    // PatchModuleNode while the window is open so it can be saved and restored with the patch.
    var windowX by mutableStateOf<Float?>(null)
    var windowY by mutableStateOf<Float?>(null)
    var windowWidth by mutableStateOf<Float?>(null)
    var windowHeight by mutableStateOf<Float?>(null)
    var isWindowOpen by mutableStateOf(false)

    var filePath by mutableStateOf<String?>(null)
    var resourcePath by mutableStateOf<String?>(null)
    var isModified by mutableStateOf(false)

    val isRoot: Boolean
        get() = parentPatch == null

    val headerTitle: String
        get() {
            val suffix = if (isModified) "*" else ""
            if (resourcePath != null) {
                return "$name$suffix"
            }
            val path = filePath
            return if (path == null) {
                "$name$suffix"
            } else {
                val isResource = path.startsWith("examples/") || path.startsWith("tutorials/") || path.startsWith("help/") || path.startsWith("instruments/") || path.startsWith("sounds/")
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
                    if (module.isModified) {
                        if (module.filePath != null) {
                            result.add(module)
                        } else if (module.resourcePath != null) {
                            if (com.syntona.dataflow.editor.isSaveAsResourceAvailable()) {
                                result.add(module)
                            } else {
                                // If the user does not have SaveAsResource privilege, automatically detach
                                module.resourcePath = null
                            }
                        }
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

    fun detachFile() {
        filePath = null
        resourcePath = null
    }

    override fun setEngine(engine: DataFlowEngine) {
        graphState.engine = engine
    }

    /**
     * Returns true if [module] inside this patch may be deleted by the user.
     * Subclasses can override to protect intrinsic / built-in modules.
     */
    open fun canDeleteChildModule(module: Module): Boolean = module.isDeletable

    /** Returns true if an input module named [name] would get a special meaning inside this patch. */
    open fun isReservedInputName(name: String): Boolean = false

    protected open fun onChildModuleAdded(module: Module) {
        module.parentPatch = this
        when (module) {
            is InputModule -> {
                val isUnnamed = module.name == "input"
                val proposedName = if (isUnnamed) "in${inputLinks.size + 1}" else module.name
                val jack = addInput(proposedName)
                inputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.name = proposedName
                module.hasAutoName = isUnnamed
            }
            is CommandModule -> {
                val proposedName = if (module.name == "command") "cmd${commandLinks.size + 1}" else module.name
                val jack = addCommandInput(proposedName)
                commandLinks[jack] = module
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
                val isUnnamed = module.name == "input~" || module.name == "aInput"
                val proposedName = if (isUnnamed) "aIn${audioInputLinks.size + 1}" else module.name
                val jack = addAudioInput(proposedName, module.passThrough.input, 0)
                audioInputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.name = proposedName
                module.hasAutoName = isUnnamed
            }
            is AudioOutputModule -> {
                val proposedName = if (module.name == "output~" || module.name == "aOutput") "aOut${audioOutputLinks.size + 1}" else module.name
                val jack = addAudioOutput(proposedName, module.passThrough.output, 0)
                audioOutputLinks[jack] = module
                module.patch = this
                module.patchJack = jack
                module.name = proposedName
            }
            is AudioOutput2Module -> {
                val proposedBase = if (module.name == "output2~" || module.name == "output") "out" else module.name
                val jack0 = addAudioOutput("${proposedBase}0", module.twoInDualOut.output, 0)
                val jack1 = addAudioOutput("${proposedBase}1", module.twoInDualOut.output, 1)
                audioOutput2Links[jack0] = module
                audioOutput2Links[jack1] = module
                module.patch = this
                module.patchJack0 = jack0
                module.patchJack1 = jack1
                module.name = proposedBase
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
                        parentGs.jackMarkerCenters.remove(jack)
                        val conns = parentGs.connections.filter { it.input === jack }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeInput(jack)
                    inputLinks.remove(jack)
                }
            }
            is CommandModule -> {
                val jack = module.patchJack
                if (jack != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(jack)
                        parentGs.jackBounds.remove(jack)
                        parentGs.jackMarkerCenters.remove(jack)
                        val conns = parentGs.connections.filter { it.input === jack }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeInput(jack)
                    commandLinks.remove(jack)
                }
            }
            is OutputModule -> {
                val jack = module.patchJack
                if (jack != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(jack)
                        parentGs.jackBounds.remove(jack)
                        parentGs.jackMarkerCenters.remove(jack)
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
                        parentGs.jackMarkerCenters.remove(jack)
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
                        parentGs.jackMarkerCenters.remove(jack)
                        val conns = parentGs.connections.filter { it.output === jack }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeOutput(jack)
                    audioOutputLinks.remove(jack)
                }
            }
            is AudioOutput2Module -> {
                val j0 = module.patchJack0
                val j1 = module.patchJack1
                if (j0 != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(j0)
                        parentGs.jackBounds.remove(j0)
                        parentGs.jackMarkerCenters.remove(j0)
                        val conns = parentGs.connections.filter { it.output === j0 }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeOutput(j0)
                    audioOutput2Links.remove(j0)
                }
                if (j1 != null) {
                    parentPatch?.graphState?.let { parentGs ->
                        parentGs.jackOffsets.remove(j1)
                        parentGs.jackBounds.remove(j1)
                        parentGs.jackMarkerCenters.remove(j1)
                        val conns = parentGs.connections.filter { it.output === j1 }
                        conns.forEach { parentGs.disconnect(it.output, it.input) }
                    }
                    removeOutput(j1)
                    audioOutput2Links.remove(j1)
                }
            }
        }
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        // Forward incoming messages to the matching InputModule or CommandModule inside the patch
        inputLinks[jack]?.receive(msg)
        commandLinks[jack]?.receive(msg)
        super.onMessageReceived(jack, msg)
    }

    // Called by OutputModule when it receives a message — routes to the patch's OutputJack
    internal open fun receiveFromOutputModule(outputModule: OutputModule, msg: Message) {
        outputLinks.entries.find { it.value === outputModule }?.key?.send(msg)
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        graphState.modules.forEach { it.teardown() }
        graphState.tearDownDetachedModules()
        super.teardown()
    }
}
