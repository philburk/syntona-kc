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

    val currentFileName: String?
        get() = currentFilePath?.let { p ->
            p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
        }

    // When non-null, the app shows an "unsaved changes" confirmation dialog.
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)

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
        if (isDirty) pendingAction = action else action()
    }
}
