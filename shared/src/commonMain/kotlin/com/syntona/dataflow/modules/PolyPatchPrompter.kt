package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.editor.GraphState

object PolyPatchPrompter {
    var activePromptModule by mutableStateOf<PolyPatchModule?>(null)
    var activePromptGraphState by mutableStateOf<GraphState?>(null)
}
