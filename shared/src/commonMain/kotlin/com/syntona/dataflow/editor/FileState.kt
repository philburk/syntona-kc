package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.modules.PatchModule

/**
 * Tracks every open document and which one file actions apply to.
 *
 * Several top level documents can be open at once, so that loading an example or another patch
 * does not have to discard work in progress. The properties here - [isDirty], [currentFilePath]
 * and so on - refer to the frontmost document, which is what a File menu or a window title cares
 * about; use [documents] directly when something has to apply to all of them, such as checking for
 * unsaved work before quitting.
 */
object FileState {

    /** Every open top level document, in the order they were opened. */
    val documents = mutableStateListOf<DocumentState>()

    /** The document that file actions apply to, normally the frontmost one. */
    var activeDocument by mutableStateOf<DocumentState?>(null)
        private set

    fun addDocument(document: DocumentState) {
        if (documents.none { it === document }) documents.add(document)
        activeDocument = document
    }

    fun removeDocument(document: DocumentState) {
        documents.removeAll { it === document }
        if (activeDocument === document) activeDocument = documents.lastOrNull()
    }

    /** Makes [document] the one that file actions apply to. */
    fun focusDocument(document: DocumentState?) {
        if (document != null && documents.none { it === document }) return
        activeDocument = document
    }

    /**
     * The document that owns [graphState], found by walking up from the patch it belongs to.
     * Returns null for a graph that is not part of any open document.
     */
    fun documentFor(graphState: GraphState?): DocumentState? {
        if (graphState == null) return null
        documents.firstOrNull { it.rootPatch.graphState === graphState }?.let { return it }
        var patch: PatchModule? = graphState.parentPatch
        while (patch != null) {
            documents.firstOrNull { it.rootPatch === patch }?.let { return it }
            patch = patch.parentPatch
        }
        return null
    }

    /** Every document with work that would be lost, for the quit check. */
    val documentsWithUnsavedWork: List<DocumentState>
        get() = documents.filter { it.hasUnsavedWork }

    val hasAnyUnsavedWork: Boolean
        get() = documents.any { it.hasUnsavedWork }

    /**
     * Runs [action] unless *any* open document has unsaved work, in which case it confirms first.
     *
     * Used for quitting, where every document is at stake rather than just the frontmost one.
     * There is no new-window alternative here, so the dialog keeps its two buttons.
     */
    fun requestIfAnyDirty(action: () -> Unit) {
        if (hasAnyUnsavedWork) {
            pendingAction = action
            pendingOpenInNewWindow = null
        } else {
            action()
        }
    }

    // --- The frontmost document, which is what most callers mean ----------------------------

    val isDirty: Boolean
        get() = activeDocument?.isDirty ?: false

    val currentFilePath: String?
        get() = activeDocument?.currentFilePath

    val currentFileName: String?
        get() = activeDocument?.currentFileName

    val isResource: Boolean
        get() = activeDocument?.isResource ?: false

    /** The active document's path within the resource tree, or null when it is an ordinary file. */
    val resourcePath: String?
        get() = activeDocument?.resourcePath

    fun markDirty() {
        activeDocument?.markDirty()
    }

    fun markSaved(path: String) {
        activeDocument?.markSaved(path)
    }

    fun markNew() {
        activeDocument?.markNew()
    }

    // --- Dialog state ------------------------------------------------------------------------

    val patchesToConfirmSave = mutableStateListOf<PatchModule>()
    var postConfirmationSaveAction by mutableStateOf<(() -> Unit)?>(null)

    /** When non-null, the app shows the unsaved changes confirmation. */
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
        private set

    /**
     * Set alongside [pendingAction] when the request can also be satisfied by opening a second
     * document instead of replacing the current one. Drives the third button on the dialog.
     */
    var pendingOpenInNewWindow by mutableStateOf<(() -> Unit)?>(null)
        private set

    /** When non-null, the app shows an import/load problems report dialog. */
    var loadingProblemsReport by mutableStateOf<String?>(null)

    /**
     * Runs [action], first asking about unsaved work if there is any.
     *
     * When [openInNewWindow] is given, the confirmation also offers to leave the current document
     * alone and open the incoming patch as a second top level document instead.
     */
    fun requestIfDirty(openInNewWindow: (() -> Unit)? = null, action: () -> Unit) {
        val document = activeDocument
        if (document != null && document.hasUnsavedWork) {
            pendingAction = action
            pendingOpenInNewWindow = openInNewWindow
        } else {
            action()
        }
    }

    /** Runs the pending action, discarding the current document's changes. */
    fun confirmPendingAction() {
        val action = pendingAction
        clearPendingAction()
        action?.invoke()
    }

    /** Runs the pending request as a second document, leaving the current one untouched. */
    fun confirmPendingInNewWindow() {
        val action = pendingOpenInNewWindow
        clearPendingAction()
        action?.invoke()
    }

    fun clearPendingAction() {
        pendingAction = null
        pendingOpenInNewWindow = null
    }
}
