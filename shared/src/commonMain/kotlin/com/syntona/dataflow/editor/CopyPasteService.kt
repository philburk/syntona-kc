package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.Module

object CopyPasteService {
    var pasteBuffer by mutableStateOf<String?>(null)
        private set

    private var pasteCount = 0

    fun copy(graphState: GraphState, modules: List<Module>, density: Float = 1f) {
        pasteCount = 0
        pasteBuffer = GraphSerializer.serialize(graphState, modules, density)
    }

    fun paste(graphState: GraphState, density: Float = 1f) {
        val json = pasteBuffer ?: return
        pasteCount++
        val offset = Offset(20f * pasteCount, 20f * pasteCount)
        graphState.clearSelection()
        val created = GraphSerializer.deserialize(graphState, json, offset, density)
        created.forEach { graphState.selectModule(it) }
    }

    /** Empties the clipboard. Mainly so one test cannot leak into the next. */
    fun clear() {
        pasteBuffer = null
        pasteCount = 0
    }
}
