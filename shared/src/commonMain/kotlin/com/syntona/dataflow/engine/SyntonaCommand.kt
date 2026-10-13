package com.syntona.dataflow.engine

interface SyntonaCommand {
    val isUndoable: Boolean get() = false
    fun execute()
    fun undo()

    /**
     * Called when the command leaves the undo history for good, so it can never be undone or
     * redone again. A command can release here what it kept alive only for undo.
     */
    fun discard() {}
}
