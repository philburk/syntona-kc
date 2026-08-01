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
                .filter { it.startsWith("help/") && it.endsWith(SYNTONA_FILE_SUFFIX) }
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
     * E.g. "keyboard13" -> "help/music/input/keyboard13.sntn"
     */
    fun getHelpFilePath(typeName: String): String? {
        val bundlePath = ModuleRegistry.getBundlePath(typeName)
        if (bundlePath != null) {
            val path = "help/${bundlePath.replace('.', '/')}$SYNTONA_FILE_SUFFIX"
            if (availableHelpFiles.contains(path)) return path
        }
        val fallbackPath = "help/${typeName.replace('.', '/')}$SYNTONA_FILE_SUFFIX"
        if (availableHelpFiles.contains(fallbackPath)) return fallbackPath
        return null
    }

    /**
     * Returns true if a help file exists for the given module type.
     */
    fun hasHelpFile(typeName: String): Boolean {
        return getHelpFilePath(typeName) != null
    }
}
