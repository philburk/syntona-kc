package com.syntona

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.syntona.dataflow.editor.CopyPasteService
import com.syntona.dataflow.editor.FileState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.MdiState
import com.syntona.dataflow.editor.saveFileDirect
import com.syntona.dataflow.editor.showOpenFileDialog
import com.syntona.dataflow.editor.showSaveFileDialog

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = if (FileState.isDirty) "• SyntonaKC" else "SyntonaKC",
    ) {
        var mdiState by remember { mutableStateOf<MdiState?>(null) }

        mdiState?.let { state ->
            MenuBar {
                Menu("File") {
                    val gs = state.activeGraphState
                    val engine = gs?.engine
                    Item("New", shortcut = KeyShortcut(Key.N, meta = true)) {
                        FileState.requestIfDirty {
                            gs?.clearAllModules()
                            engine?.clearHistory()
                            FileState.markNew()
                        }
                    }
                    Item("Open...", shortcut = KeyShortcut(Key.O, meta = true)) {
                        FileState.requestIfDirty {
                            showOpenFileDialog { name, content ->
                                if (gs != null) {
                                    gs.clearAllModules()
                                    engine?.clearHistory()
                                    GraphSerializer.deserialize(gs, content)
                                    FileState.markSaved(name)
                                }
                            }
                        }
                    }
                    Separator()
                    Item(
                        "Save",
                        shortcut = KeyShortcut(Key.S, meta = true),
                        enabled = FileState.isDirty
                    ) {
                        if (gs != null) {
                            val json = GraphSerializer.serialize(gs, gs.modules.toList())
                            val path = FileState.currentFilePath
                            if (path != null) {
                                saveFileDirect(path, json)
                                FileState.markSaved(path)
                            } else {
                                showSaveFileDialog(json, "patch.json") { FileState.markSaved(it) }
                            }
                        }
                    }
                    Item("Save As...", shortcut = KeyShortcut(Key.S, meta = true, shift = true)) {
                        if (gs != null) {
                            showSaveFileDialog(
                                GraphSerializer.serialize(gs, gs.modules.toList()),
                                FileState.currentFileName ?: "patch.json"
                            ) { FileState.markSaved(it) }
                        }
                    }
                }
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

        App(
            onMdiStateReady = { mdiState = it },
            showEditMenuInToolbar = false,
            showFileMenuInToolbar = false,
        )
    }
}
