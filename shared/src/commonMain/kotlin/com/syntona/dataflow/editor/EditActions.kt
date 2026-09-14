package com.syntona.dataflow.editor

/**
 * Cut, copy and paste for whichever window is frontmost.
 *
 * Every function resolves the active window when it is called, rather than being handed a
 * [GraphState] by the caller. That matters for the desktop menu bar: its items are built once and
 * their actions are reused for the keyboard shortcuts, so anything captured when the menu was
 * composed goes stale as soon as the user selects something or brings a different window forward.
 *
 * Routing matches what the key handler has always done: a window with its own
 * [EditActionHandler], such as the piano roll, gets the action, and everything else falls through
 * to the module selection of [MdiState.activeGraphState].
 */
object EditActions {

    fun canCut(mdi: MdiState): Boolean {
        mdi.activeEditHandler?.let { return it.canCut() }
        val gs = mdi.activeGraphState ?: return false
        return gs.selectedModules.any { gs.canDeleteModule(it) }
    }

    fun canCopy(mdi: MdiState): Boolean {
        mdi.activeEditHandler?.let { return it.canCopy() }
        return mdi.activeGraphState?.selectedModules?.isNotEmpty() == true
    }

    fun canPaste(mdi: MdiState): Boolean {
        mdi.activeEditHandler?.let { return it.canPaste() }
        return mdi.activeGraphState != null && CopyPasteService.pasteBuffer != null
    }

    fun canMakePatch(mdi: MdiState): Boolean {
        if (mdi.activeEditHandler != null) return false
        val gs = mdi.activeGraphState ?: return false
        if (gs.selectedModules.isEmpty()) return false
        return gs.selectedModules.all { gs.canDeleteModule(it) }
    }

    fun cut(mdi: MdiState, density: Float = 1f) {
        val handler = mdi.activeEditHandler
        if (handler != null) {
            if (handler.canCut()) handler.cut()
            return
        }
        val graphState = mdi.activeGraphState ?: return
        if (graphState.selectedModules.isEmpty()) return
        CopyPasteService.copy(graphState, graphState.selectedModules.toList(), density)
        graphState.deleteSelectedModules()
    }

    fun copy(mdi: MdiState, density: Float = 1f) {
        val handler = mdi.activeEditHandler
        if (handler != null) {
            if (handler.canCopy()) handler.copy()
            return
        }
        val graphState = mdi.activeGraphState ?: return
        if (graphState.selectedModules.isEmpty()) return
        CopyPasteService.copy(graphState, graphState.selectedModules.toList(), density)
    }

    fun paste(mdi: MdiState, density: Float = 1f) {
        val handler = mdi.activeEditHandler
        if (handler != null) {
            if (handler.canPaste()) handler.paste()
            return
        }
        val graphState = mdi.activeGraphState ?: return
        CopyPasteService.paste(graphState, density)
    }

    fun makePatch(mdi: MdiState, density: Float = 1f): com.syntona.dataflow.modules.PatchModule? {
        if (mdi.activeEditHandler != null) return null
        val graphState = mdi.activeGraphState ?: return null
        if (graphState.selectedModules.isEmpty()) return null
        return PatchExtractor.extractToPatch(graphState, graphState.selectedModules.toList(), density)
    }
}
