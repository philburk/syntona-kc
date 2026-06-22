package com.syntona

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.syntona.dataflow.editor.CopyPasteService
import com.syntona.dataflow.editor.MdiState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SyntonaKC",
    ) {
        var mdiState by remember { mutableStateOf<MdiState?>(null) }

        mdiState?.let { state ->
            MenuBar {
                Menu("Edit") {
                    val gs = state.activeGraphState
                    val hasSelection = gs?.selectedModules?.isNotEmpty() == true
                    Item("Undo", shortcut = KeyShortcut(Key.Z, meta = true)) {
                        gs?.engine?.undo()
                    }
                    Item("Redo", shortcut = KeyShortcut(Key.Z, meta = true, shift = true)) {
                        gs?.engine?.redo()
                    }
                    Separator()
                    Item("Cut", shortcut = KeyShortcut(Key.X, meta = true), enabled = hasSelection) {
                        if (gs != null) {
                            CopyPasteService.copy(gs, gs.selectedModules.toList())
                            gs.deleteSelectedModules()
                        }
                    }
                    Item("Copy", shortcut = KeyShortcut(Key.C, meta = true), enabled = hasSelection) {
                        if (gs != null) {
                            CopyPasteService.copy(gs, gs.selectedModules.toList())
                        }
                    }
                    Item(
                        "Paste",
                        shortcut = KeyShortcut(Key.V, meta = true),
                        enabled = CopyPasteService.pasteBuffer != null
                    ) {
                        gs?.let { CopyPasteService.paste(it) }
                    }
                }
            }
        }

        App(onMdiStateReady = { mdiState = it }, showEditMenuInToolbar = false)
    }
}
