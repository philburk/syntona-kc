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
            p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
        }

    /** True for the examples, tutorials, and help files that ship with the app, which must not be overwritten. */
    val isResource: Boolean
        get() = currentFilePath?.let { path ->
            path.startsWith("examples/") || path.startsWith("tutorials/") || path.startsWith("help/")
        } ?: false

    /** Title for this document's window: its file name, or the patch name before it is saved. */
    val displayName: String
        get() = currentFileName ?: rootPatch.name

    fun markDirty() {
        isDirty = true
    }

    fun markSaved(path: String) {
        currentFilePath = path
        isDirty = false
        // The window title reads the patch's own path, so keep the two in step here rather than
        // syncing them from outside. Doing it globally meant the frontmost document's path could
        // be written onto a different document's patch, relabelling the wrong window.
        rootPatch.filePath = path
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
