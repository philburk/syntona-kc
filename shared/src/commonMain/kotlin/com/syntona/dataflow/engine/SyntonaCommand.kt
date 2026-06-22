package com.syntona.dataflow.engine

interface SyntonaCommand {
    val isUndoable: Boolean get() = false
    fun execute()
    fun undo()
}
