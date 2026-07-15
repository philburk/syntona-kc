package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object FileState {
    var isDirty by mutableStateOf(false)
        private set

    // Full path on JVM (e.g. "/Users/phil/Documents/patch.json"), filename on WASM.
    var currentFilePath by mutableStateOf<String?>(null)
        private set

    val patchesToConfirmSave = androidx.compose.runtime.mutableStateListOf<com.syntona.dataflow.modules.PatchModule>()
    var postConfirmationSaveAction by mutableStateOf<(() -> Unit)?>(null)

    val currentFileName: String?
        get() = currentFilePath?.let { p ->
            p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
        }

    val isResource: Boolean
        get() = currentFilePath?.let { path ->
            path.startsWith("examples/") ||
            path.startsWith("tutorials/")
        } ?: false

    // When non-null, the app shows an "unsaved changes" confirmation dialog.
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)

    // When non-null, the app shows an import/load problems report dialog.
    var loadingProblemsReport by mutableStateOf<String?>(null)

    fun markDirty() { isDirty = true }

    fun markSaved(path: String) {
        currentFilePath = path
        isDirty = false
    }

    fun markNew() {
        currentFilePath = null
        isDirty = false
    }

    fun requestIfDirty(action: () -> Unit) {
        if (isDirty && !isResource) pendingAction = action else action()
    }
}
