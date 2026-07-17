package com.syntona

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.syntona.dataflow.editor.CopyPasteService
import com.syntona.dataflow.editor.FileState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.MdiState
import com.syntona.dataflow.editor.MdiWindowInfo
import com.syntona.dataflow.editor.GraphEditor
import com.syntona.dataflow.editor.JsonParser
import com.syntona.dataflow.editor.JsonValue
import com.syntona.dataflow.editor.saveFileDirect
import com.syntona.dataflow.editor.showOpenFileDialog
import com.syntona.dataflow.editor.showSaveFileDialog
import com.syntona.dataflow.modules.LinkModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch

fun main() = application {
    if (java.awt.Desktop.isDesktopSupported()) {
        val desktop = java.awt.Desktop.getDesktop()
        if (desktop.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler { _, response ->
                if (FileState.isDirty && !FileState.isResource) {
                    response.cancelQuit()
                    FileState.requestIfDirty {
                        exitApplication()
                    }
                } else {
                    response.performQuit()
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            FileState.requestIfDirty {
                exitApplication()
            }
        },
        title = if (FileState.isDirty) "• SyntonaKC" else "SyntonaKC",
        state = rememberWindowState(width = 1600.dp, height = 1200.dp),
    ) {
        var mdiState by remember { mutableStateOf<MdiState?>(null) }
        val scope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val examplesList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("examples/") && it.endsWith(".json") }
        }
        val tutorialsList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("tutorials/") && it.endsWith(".json") }
        }

        LaunchedEffect(mdiState, scope) {
            LinkModule.onTriggerLoad = { path ->
                val state = mdiState
                if (state != null) {
                    val gs = state.rootGraphState ?: state.activeGraphState
                    val engine = gs?.engine
                    if (gs != null) {
                        FileState.requestIfDirty {
                            scope.launch {
                                try {
                                    val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                    val content = bytes.decodeToString()
                                    gs.clearAllModules()
                                    engine?.clearHistory()
                                    state.closeAllClosable()
                                    val rootPatch = state.windows.find { !it.closable }?.id as? com.syntona.dataflow.modules.PatchModule
                                    GraphSerializer.deserialize(gs, content, density = density)
                                    com.syntona.restoreWindowStates(state, gs, density)
                                    if (rootPatch != null) {
                                        restoreRootWindowGeometry(state, rootPatch, content, density)
                                    }
                                    FileState.markSaved(path)
                                    gs.kickLoadKickModules()
                                } catch (e: Exception) {
                                    println("Failed to load link file: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }

        mdiState?.let { state ->
            MenuBar {
                Menu("File") {
                    val gs = state.rootGraphState ?: state.activeGraphState
                    val engine = gs?.engine
                    Item("New", shortcut = KeyShortcut(Key.N, meta = true)) {
                        FileState.requestIfDirty {
                            gs?.clearAllModules()
                            engine?.clearHistory()
                            val rootPatch = state.windows.find { !it.closable }?.id as? PatchModule
                            rootPatch?.clearModifiedRecursively()
                            FileState.markNew()
                            state.closeAllClosable()
                        }
                    }
                    Item("Open...", shortcut = KeyShortcut(Key.O, meta = true)) {
                        FileState.requestIfDirty {
                            showOpenFileDialog { name, content ->
                                if (gs != null) {
                                    gs.clearAllModules()
                                    engine?.clearHistory()
                                    state.closeAllClosable()
                                    val rootPatch = state.windows.find { !it.closable }?.id as? PatchModule
                                    GraphSerializer.deserialize(gs, content, density = density)
                                    com.syntona.restoreWindowStates(state, gs, density)
                                    if (rootPatch != null) {
                                        restoreRootWindowGeometry(state, rootPatch, content, density)
                                        rootPatch.clearModifiedRecursively()
                                    }
                                    FileState.markSaved(name)
                                    gs.kickLoadKickModules()
                                }
                            }
                        }
                    }
                    Item("Add Patch from File...") {
                        val activeGs = state.activeGraphState
                        if (activeGs != null) {
                            showOpenFileDialog { path, content ->
                                val parsed = try { JsonParser(content).parse() as? JsonValue.Obj } catch (_: Exception) { null }
                                if (parsed != null) {
                                    val fileType = (parsed.map["type"] as? JsonValue.Str)?.value ?: "patch"
                                    val fileName = path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast(".json")
                                    val newPatch = if (fileType == "polyPatch") {
                                        val poly = PolyPatchModule(fileName)
                                        val maxV = (parsed.map["maxVoices"] as? JsonValue.Num)?.value?.toInt() ?: 4
                                        poly.initializeVoices(maxV)
                                        poly
                                    } else {
                                        PatchModule(fileName)
                                    }
                                    newPatch.filePath = path
                                    newPatch.clearModifiedRecursively()

                                    newPatch.graphState.isDeserializing = true
                                    try {
                                        newPatch.graphState.removeModulesDirectly(newPatch.graphState.modules.toList())
                                        GraphSerializer.deserialize(newPatch.graphState, content, Offset.Zero, density)
                                        if (newPatch is PolyPatchModule) {
                                            newPatch.ensureDefaultModulesExist()
                                        }
                                    } finally {
                                        newPatch.graphState.isDeserializing = false
                                    }
                                    newPatch.clearModifiedRecursively()

                                    activeGs.addModule(newPatch, Offset(100f, 100f))
                                }
                            }
                        }
                    }
                    Separator()
                    Item(
                        "Save",
                        shortcut = KeyShortcut(Key.S, meta = true),
                        enabled = FileState.isDirty && FileState.currentFilePath != null && !FileState.isResource
                    ) {
                        if (gs != null) {
                            val rootPatch = state.windows.find { !it.closable }?.id as? PatchModule
                            if (rootPatch != null) {
                                val modifiedChildren = rootPatch.findModifiedReferencedPatches()
                                if (modifiedChildren.isNotEmpty()) {
                                    FileState.patchesToConfirmSave.clear()
                                    FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                    FileState.postConfirmationSaveAction = {
                                        val json = GraphSerializer.serialize(
                                            gs, gs.modules.toList(), density,
                                            rootWindowX = rootPatch.windowX,
                                            rootWindowY = rootPatch.windowY,
                                            rootWindowWidth = rootPatch.windowWidth,
                                            rootWindowHeight = rootPatch.windowHeight
                                        )
                                        val path = FileState.currentFilePath
                                        if (path != null) {
                                            saveFileDirect(path, json)
                                            rootPatch.filePath = path
                                            rootPatch.clearModifiedRecursively()
                                            FileState.markSaved(path)
                                        } else {
                                            showSaveFileDialog(json, "patch.json") { savedPath ->
                                                rootPatch.filePath = savedPath
                                                rootPatch.clearModifiedRecursively()
                                                FileState.markSaved(savedPath)
                                            }
                                        }
                                    }
                                } else {
                                    val json = GraphSerializer.serialize(
                                        gs, gs.modules.toList(), density,
                                        rootWindowX = rootPatch.windowX,
                                        rootWindowY = rootPatch.windowY,
                                        rootWindowWidth = rootPatch.windowWidth,
                                        rootWindowHeight = rootPatch.windowHeight
                                    )
                                    val path = FileState.currentFilePath
                                    if (path != null) {
                                        saveFileDirect(path, json)
                                        rootPatch.filePath = path
                                        rootPatch.clearModifiedRecursively()
                                        FileState.markSaved(path)
                                    } else {
                                        showSaveFileDialog(json, "patch.json") { savedPath ->
                                            rootPatch.filePath = savedPath
                                            rootPatch.clearModifiedRecursively()
                                            FileState.markSaved(savedPath)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Item("Save As...", shortcut = KeyShortcut(Key.S, meta = true, shift = true)) {
                        if (gs != null) {
                            val rootPatch = state.windows.find { !it.closable }?.id as? PatchModule
                            if (rootPatch != null) {
                                val modifiedChildren = rootPatch.findModifiedReferencedPatches()
                                if (modifiedChildren.isNotEmpty()) {
                                    FileState.patchesToConfirmSave.clear()
                                    FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                    FileState.postConfirmationSaveAction = {
                                        showSaveFileDialog(
                                            GraphSerializer.serialize(
                                                gs, gs.modules.toList(), density,
                                                rootWindowX = rootPatch.windowX,
                                                rootWindowY = rootPatch.windowY,
                                                rootWindowWidth = rootPatch.windowWidth,
                                                rootWindowHeight = rootPatch.windowHeight
                                            ),
                                            FileState.currentFileName ?: "patch.json"
                                        ) { savedPath ->
                                            rootPatch.filePath = savedPath
                                            rootPatch.clearModifiedRecursively()
                                            FileState.markSaved(savedPath)
                                        }
                                    }
                                } else {
                                    showSaveFileDialog(
                                        GraphSerializer.serialize(
                                            gs, gs.modules.toList(), density,
                                            rootWindowX = rootPatch.windowX,
                                            rootWindowY = rootPatch.windowY,
                                            rootWindowWidth = rootPatch.windowWidth,
                                            rootWindowHeight = rootPatch.windowHeight
                                        ),
                                        FileState.currentFileName ?: "patch.json"
                                    ) { savedPath ->
                                        rootPatch.filePath = savedPath
                                        rootPatch.clearModifiedRecursively()
                                        FileState.markSaved(savedPath)
                                    }
                                }
                            }
                        }
                    }
                    Item("Save Patch As...") {
                        val target = state.activeGraphState?.parentPatch
                        if (target != null) {
                            val json = GraphSerializer.serializePatch(target, density)
                            val defaultName = target.filePath?.let { p ->
                                p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
                            } ?: "${target.name}.json"
                            showSaveFileDialog(json, defaultName) { path ->
                                target.filePath = path
                                target.clearModifiedRecursively()
                                val rootPatch = state.windows.find { !it.closable }?.id as? PatchModule
                                if (target === rootPatch) {
                                    FileState.markSaved(path)
                                }
                            }
                        }
                    }
                    Item(
                        "Detach File from Patch",
                        enabled = state.activeGraphState?.parentPatch?.filePath != null
                    ) {
                        val target = state.activeGraphState?.parentPatch
                        if (target != null) {
                            target.filePath = null
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
                Menu("Help ") {
                    val gs = state.rootGraphState ?: state.activeGraphState
                    val examplesTree = remember(examplesList) {
                        com.syntona.buildMenuTree(examplesList, "examples/")
                    }
                    val tutorialsTree = remember(tutorialsList) {
                        com.syntona.buildMenuTree(tutorialsList, "tutorials/")
                    }

                    Menu("Load Example") {
                        RenderMenuNode(examplesTree, gs, state, scope)
                    }
                    Menu("Load Tutorial") {
                        RenderMenuNode(tutorialsTree, gs, state, scope)
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

@Composable
fun androidx.compose.ui.window.MenuScope.RenderMenuNode(
    node: com.syntona.MenuNode,
    gs: com.syntona.dataflow.editor.GraphState?,
    state: com.syntona.dataflow.editor.MdiState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val sortedChildren = node.children.values.sortedWith(
        compareBy<com.syntona.MenuNode> { it.path != null }
            .thenBy { it.name }
    )

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    for (child in sortedChildren) {
        val path = child.path
        if (path != null) {
            Item(child.name) {
                if (gs != null) {
                    val engine = gs.engine
                    FileState.requestIfDirty {
                        scope.launch {
                            try {
                                val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                val content = bytes.decodeToString()
                                gs.clearAllModules()
                                engine?.clearHistory()
                                state.closeAllClosable()
                                val rootPatch = state.windows.find { !it.closable }?.id as? com.syntona.dataflow.modules.PatchModule
                                GraphSerializer.deserialize(gs, content, density = density)
                                com.syntona.restoreWindowStates(state, gs, density)
                                if (rootPatch != null) {
                                    restoreRootWindowGeometry(state, rootPatch, content, density)
                                }
                                FileState.markSaved(path)
                                gs.kickLoadKickModules()
                            } catch (e: Exception) {
                                println("Failed to load: ${e.message}")
                            }
                        }
                    }
                }
            }
        } else {
            Menu(child.name) {
                RenderMenuNode(child, gs, state, scope)
            }
        }
    }
}

