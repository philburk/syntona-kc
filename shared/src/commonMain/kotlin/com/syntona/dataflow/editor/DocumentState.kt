package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.modules.PatchModule

/**
 * One open document: a top level patch together with where it came from and whether it has
 * unsaved changes.
 *
 * Several of these can be open at once, so that loading an example or another patch does not have
 * to throw away work in progress. File actions apply to whichever document is frontmost, which
 * [FileState] tracks.
 */
class DocumentState(val rootPatch: PatchModule) {

    var isDirty by mutableStateOf(false)
        private set

    /** Full path on JVM, filename on WASM, or a resource path such as `examples/foo.sntn`. */
    var currentFilePath by mutableStateOf<String?>(null)
        private set

    val currentFileName: String?
        get() = currentFilePath?.let { p ->
            val name = p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
            val clean = name.removeSuffix(".json").removeSuffix(com.syntona.SYNTONA_FILE_SUFFIX)
            "$clean${com.syntona.SYNTONA_FILE_SUFFIX}"
        }

    /** True for the examples, tutorials, and help files that ship with the app, which must not be overwritten. */
    val isResource: Boolean
        get() = currentFilePath?.let { path ->
            path.startsWith("examples/") || path.startsWith("tutorials/") || path.startsWith("help/")
        } ?: false

    /** Title for this document's window: clean name for built-in resources, file name with extension for saved files. */
    val displayName: String
        get() {
            val file = currentFilePath ?: return rootPatch.name
            val name = file.substringAfterLast('/').substringAfterLast('\\').ifEmpty { file }
            return if (isResource) {
                name.removeSuffix(".json").removeSuffix(com.syntona.SYNTONA_FILE_SUFFIX)
            } else {
                name
            }
        }

    fun markDirty() {
        isDirty = true
    }

    fun markSaved(path: String) {
        val targetPath = if (!path.startsWith("examples/") && !path.startsWith("tutorials/") && !path.startsWith("help/")) {
            if (path.endsWith(".json")) path.removeSuffix(".json") + com.syntona.SYNTONA_FILE_SUFFIX else path
        } else {
            path
        }
        currentFilePath = targetPath
        isDirty = false
        rootPatch.filePath = targetPath
    }

    fun markNew() {
        currentFilePath = null
        isDirty = false
        rootPatch.filePath = null
    }

    /** Unsaved work that would actually be lost. A bundled resource can always be reloaded. */
    val hasUnsavedWork: Boolean
        get() = isDirty && !isResource
}
