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
 * Registry that discovers and manages sound patches from composeResources/files/sounds/.
 * Discovered sounds appear under the "music.sounds" bundle in the ModuleTree.
 */
object SoundRegistry {

    val soundJsonCache = mutableMapOf<String, String>()

    /**
     * Loads the available sound JSON files from composeResources/files/sounds.
     */
    suspend fun loadSoundsIndex() {
        try {
            val soundFiles = SharedResources.getFilesIndex()
                .filter { it.startsWith("sounds/") && (it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX)) }

            val soundsBundle = ModuleRegistry.getOrCreateBundle(
                ModuleRegistry.getOrCreateBundle(ModuleRegistry.root, "music"),
                "sounds"
            )

            // Clear previous dynamically loaded sounds to avoid duplicates on re-index
            soundsBundle.modules.clear()

            for (path in soundFiles) {
                try {
                    val bytes = SharedResources.readBytes("files/$path")
                    val jsonContent = bytes.decodeToString()
                    val soundName = path.substringAfterLast('/')
                        .substringBeforeLast(".json")
                        .substringBeforeLast(SYNTONA_FILE_SUFFIX)

                    soundJsonCache[soundName] = jsonContent

                    soundsBundle.modules.add(
                        ModuleRegistry.ModuleEntry(soundName) {
                            createSoundPatch(soundName, jsonContent, path)
                        }
                    )
                } catch (e: Exception) {
                    println("Failed to load sound resource $path: $e")
                }
            }
        } catch (e: Exception) {
            println("Failed to load sounds index: $e")
        }
    }

    /**
     * Registers a sound JSON directly into the music.sounds bundle.
     */
    fun registerSound(name: String, jsonContent: String, filePath: String? = null) {
        soundJsonCache[name] = jsonContent
        val soundsBundle = ModuleRegistry.getOrCreateBundle(
            ModuleRegistry.getOrCreateBundle(ModuleRegistry.root, "music"),
            "sounds"
        )
        soundsBundle.modules.removeAll { it.name == name }
        soundsBundle.modules.add(
            ModuleRegistry.ModuleEntry(name) {
                createSoundPatch(name, jsonContent, filePath ?: "sounds/$name.json")
            }
        )
    }

    /**
     * Instantiates a PatchModule or PolyPatchModule from sound JSON content,
     * identical to "Add Patch from File...".
     */
    fun createSoundPatch(name: String, jsonContent: String, resourcePath: String? = null): Module {
        val parsed = try { JsonParser(jsonContent).parse() as? JsonValue.Obj } catch (_: Exception) { null }
        val fileType = (parsed?.map?.get("type") as? JsonValue.Str)?.value ?: "patch"
        val isPoly = fileType == "polyPatch" || fileType == "core.polyPatch"
        val newPatch = if (isPoly) {
            val poly = PolyPatchModule(name)
            val maxV = (parsed?.map?.get("maxVoices") as? JsonValue.Num)?.value?.toInt() ?: 4
            poly.initializeVoices(maxV)
            poly
        } else {
            PatchModule(name)
        }
        newPatch.resourcePath = resourcePath
        newPatch.filePath = null
        newPatch.graphState.isDeserializing = true
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
        }
        newPatch.clearModifiedRecursively()
        return newPatch
    }
}
