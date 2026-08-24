package com.syntona.dataflow.registry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.SYNTONA_FILE_SUFFIX
import com.syntona.SharedResources

/**
 * Registry that tracks and resolves module help files stored under composeResources/files/help/.
 */
object HelpRegistry {

    var availableHelpFiles by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Loads the available help files from the files index.
     */
    suspend fun loadHelpFilesIndex() {
        try {
            availableHelpFiles = SharedResources.getFilesIndex()
                .filter { it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX) }
                .toSet()
        } catch (_: Exception) {
            availableHelpFiles = emptySet()
        }
    }

    /**
     * Sets the set of available help files directly (useful for tests or static initialization).
     */
    fun setHelpFiles(files: Set<String>) {
        availableHelpFiles = files
    }

    /**
     * Resolves the help resource path for a given module type name.
     * E.g. "keyboard13" -> "help/music/input/keyboard13.json" or ".sntn",
     * or a custom tutorial / example resource path registered in [ModuleRegistry].
     */
    fun getHelpFilePath(typeName: String): String? {
        // 1. Check if an explicit helpResourcePath is configured in ModuleRegistry
        val explicitPath = ModuleRegistry.getHelpResourcePath(typeName)
        if (explicitPath != null) {
            if (availableHelpFiles.isEmpty() || availableHelpFiles.contains(explicitPath)) {
                return explicitPath
            }
            if (availableHelpFiles.contains("$explicitPath.json")) {
                return "$explicitPath.json"
            }
            if (availableHelpFiles.contains("$explicitPath$SYNTONA_FILE_SUFFIX")) {
                return "$explicitPath$SYNTONA_FILE_SUFFIX"
            }
        }

        // 2. Standard resolution under help/ using bundle path
        val bundlePath = ModuleRegistry.getBundlePath(typeName)
        if (bundlePath != null) {
            val pathJson = "help/${bundlePath.replace('.', '/')}.json"
            if (availableHelpFiles.contains(pathJson)) return pathJson
            val pathSntn = "help/${bundlePath.replace('.', '/')}$SYNTONA_FILE_SUFFIX"
            if (availableHelpFiles.contains(pathSntn)) return pathSntn
        }

        // 3. Fallback resolution using typeName directly
        val fallbackJson = "help/${typeName.replace('.', '/')}.json"
        if (availableHelpFiles.contains(fallbackJson)) return fallbackJson
        val fallbackSntn = "help/${typeName.replace('.', '/')}$SYNTONA_FILE_SUFFIX"
        if (availableHelpFiles.contains(fallbackSntn)) return fallbackSntn
        return null
    }

    /**
     * Returns true if a help file exists for the given module type.
     */
    fun hasHelpFile(typeName: String): Boolean {
        return getHelpFilePath(typeName) != null
    }
}
