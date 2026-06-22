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

    fun copy(graphState: GraphState, modules: List<Module>) {
        pasteCount = 0
        pasteBuffer = GraphSerializer.serialize(graphState, modules)
    }

    fun paste(graphState: GraphState) {
        val json = pasteBuffer ?: return
        pasteCount++
        val offset = Offset(20f * pasteCount, 20f * pasteCount)
        graphState.clearSelection()
        val created = GraphSerializer.deserialize(graphState, json, offset)
        created.forEach { graphState.selectModule(it) }
    }
}
