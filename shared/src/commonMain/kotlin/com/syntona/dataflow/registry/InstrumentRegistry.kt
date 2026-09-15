package com.syntona.dataflow.registry

import androidx.compose.ui.geometry.Offset
import com.syntona.SYNTONA_FILE_SUFFIX
import com.syntona.SharedResources
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.JsonParser
import com.syntona.dataflow.editor.JsonValue
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule

/**
 * Registry that discovers and manages instrument patches from composeResources/files/instruments/.
 * Discovered instruments appear under the "music.instruments" bundle in the ModuleTree.
 */
object InstrumentRegistry {

    val instrumentJsonCache = mutableMapOf<String, String>()

    /**
     * Loads the available instrument JSON files from composeResources/files/instruments.
     */
    suspend fun loadInstrumentsIndex() {
        try {
            val instrumentFiles = SharedResources.getFilesIndex()
                .filter { it.startsWith("instruments/") && (it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX)) }

            val instrumentsBundle = ModuleRegistry.getOrCreateBundle(
                ModuleRegistry.getOrCreateBundle(ModuleRegistry.root, "music"),
                "instruments"
            )

            // Clear previous dynamically loaded instruments to avoid duplicates on re-index
            instrumentsBundle.modules.clear()

            for (path in instrumentFiles) {
                try {
                    val bytes = SharedResources.readBytes("files/$path")
                    val jsonContent = bytes.decodeToString()
                    val instrumentName = path.substringAfterLast('/')
                        .substringBeforeLast(".json")
                        .substringBeforeLast(SYNTONA_FILE_SUFFIX)

                    instrumentJsonCache[instrumentName] = jsonContent

                    instrumentsBundle.modules.add(
                        ModuleRegistry.ModuleEntry(instrumentName) {
                            createInstrumentPatch(instrumentName, instrumentJsonCache[instrumentName] ?: jsonContent, path)
                        }
                    )
                } catch (e: Exception) {
                    println("Failed to load instrument resource $path: $e")
                }
            }
        } catch (e: Exception) {
            println("Failed to load instruments index: $e")
        }
    }

    /**
     * Registers an instrument JSON directly into the music.instruments bundle.
     */
    fun registerInstrument(name: String, jsonContent: String, filePath: String? = null) {
        instrumentJsonCache[name] = jsonContent
        val instrumentsBundle = ModuleRegistry.getOrCreateBundle(
            ModuleRegistry.getOrCreateBundle(ModuleRegistry.root, "music"),
            "instruments"
        )
        instrumentsBundle.modules.removeAll { it.name == name }
        instrumentsBundle.modules.add(
            ModuleRegistry.ModuleEntry(name) {
                createInstrumentPatch(name, jsonContent, filePath ?: "instruments/$name.json")
            }
        )
    }

    /**
     * Instantiates a PatchModule or PolyPatchModule from instrument JSON content,
     * identical to "Add Patch from File...".
     */
    fun createInstrumentPatch(name: String, jsonContent: String, resourcePath: String? = null): Module {
        val parsed = try { JsonParser(jsonContent).parse() as? JsonValue.Obj } catch (_: Exception) { null }
        val fileType = (parsed?.map?.get("type") as? JsonValue.Str)?.value ?: "patch"
        val isPoly = fileType == "polyPatch" || fileType == "core.polyPatch"
        val newPatch = if (isPoly) {
            val poly = PolyPatchModule(name)
            val maxV = (parsed?.map?.get("maxVoices") as? JsonValue.Num)?.value?.toInt() ?: 4
            val isStereo = (parsed?.map?.get("isStereo") as? JsonValue.Bool)?.value ?: false
            poly.initializeVoices(maxV, isStereo = isStereo)
            poly
        } else {
            PatchModule(name)
        }
        (parsed?.map?.get("windowX") as? JsonValue.Num)?.value?.let { newPatch.windowX = it.toFloat() }
        (parsed?.map?.get("windowY") as? JsonValue.Num)?.value?.let { newPatch.windowY = it.toFloat() }
        (parsed?.map?.get("windowWidth") as? JsonValue.Num)?.value?.let { newPatch.windowWidth = it.toFloat() }
        (parsed?.map?.get("windowHeight") as? JsonValue.Num)?.value?.let { newPatch.windowHeight = it.toFloat() }
        newPatch.resourcePath = resourcePath
        newPatch.filePath = null
        newPatch.graphState.isDeserializing = true
        if (newPatch is PolyPatchModule) {
            newPatch.voices.forEach { it.graphState.isDeserializing = true }
        }
        try {
            newPatch.graphState.removeModulesDirectly(newPatch.graphState.modules.toList())
            if (newPatch is PolyPatchModule) {
                newPatch.clearVoices()
            }
            GraphSerializer.deserialize(newPatch.graphState, jsonContent, Offset.Zero, 1.0f)
            if (newPatch is PolyPatchModule) {
                newPatch.ensureDefaultModulesExist()
            }
        } finally {
            newPatch.graphState.isDeserializing = false
            if (newPatch is PolyPatchModule) {
                newPatch.voices.forEach { it.graphState.isDeserializing = false }
            }
        }
        newPatch.clearModifiedRecursively()
        return newPatch
    }
}
