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
import com.syntona.dataflow.editor.EditActions
import com.syntona.dataflow.editor.FileState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.MdiState
import com.syntona.dataflow.editor.MdiWindowInfo
import com.syntona.dataflow.editor.GraphEditor
import com.syntona.dataflow.editor.JsonParser
import com.syntona.dataflow.editor.JsonValue
import com.syntona.dataflow.editor.isSaveAsResourceAvailable
import com.syntona.dataflow.editor.saveFileDirect
import com.syntona.dataflow.editor.showOpenFileDialog
import com.syntona.dataflow.editor.showSaveAsResourceFileDialog
import com.syntona.dataflow.editor.showSaveFileDialog
import com.syntona.dataflow.modules.LinkModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch

fun main() {
    System.setProperty("apple.awt.application.name", "Syntona")
    application {
        if (java.awt.Desktop.isDesktopSupported()) {
        val desktop = java.awt.Desktop.getDesktop()
        if (desktop.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler { _, response ->
                // Every open document is at stake when quitting, not just the frontmost one.
                if (FileState.hasAnyUnsavedWork) {
                    response.cancelQuit()
                    FileState.requestIfAnyDirty {
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
            FileState.requestIfAnyDirty {
                exitApplication()
            }
        },
        title = if (FileState.hasAnyUnsavedWork) "• SyntonaKC" else "SyntonaKC",
        state = rememberWindowState(width = 1600.dp, height = 1200.dp),
    ) {
        var mdiState by remember { mutableStateOf<MdiState?>(null) }
        val scope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val examplesList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("examples/") && (it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX)) }
        }
        val tutorialsList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("tutorials/") && (it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX)) }
        }

        LaunchedEffect(Unit) {
            com.syntona.dataflow.registry.HelpRegistry.loadHelpFilesIndex()
        }

        LaunchedEffect(mdiState, scope) {
            LinkModule.onTriggerLoad = { path ->
                val state = mdiState
                if (state != null) {
                    val current = FileState.activeDocument
                    val engine = current?.rootPatch?.graphState?.engine
                    if (current != null && engine != null) {
                        fun load(into: com.syntona.dataflow.editor.DocumentState) {
                            scope.launch {
                                try {
                                    val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                    com.syntona.loadIntoDocument(
                                        state, into, path, bytes.decodeToString(), density
                                    )
                                } catch (e: Exception) {
                                    println("Failed to load link file: ${e.message}")
                                }
                            }
                        }
                        FileState.requestIfDirty(
                            openInNewWindow = {
                                load(com.syntona.openNewDocument(state, engine, density))
                            }
                        ) {
                            load(current)
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
                        val current = FileState.activeDocument
                        FileState.requestIfDirty(
                            openInNewWindow = {
                                if (engine != null) {
                                    com.syntona.openNewDocument(state, engine, density)
                                }
                            }
                        ) {
                            if (current != null) {
                                current.rootPatch.graphState.clearAllModules()
                                current.rootPatch.graphState.engine?.clearHistory()
                                current.rootPatch.clearModifiedRecursively()
                                current.markNew()
                                state.closeSubWindows(current)
                                com.syntona.setUrlPatchPath(null)
                            }
                        }
                    }
                    Item("Open...", shortcut = KeyShortcut(Key.O, meta = true)) {
                        val current = FileState.activeDocument
                        fun openInto(into: com.syntona.dataflow.editor.DocumentState) {
                            showOpenFileDialog { name, content ->
                                com.syntona.loadIntoDocument(state, into, name, content, density)
                            }
                        }
                        FileState.requestIfDirty(
                            openInNewWindow = {
                                if (engine != null) {
                                    openInto(com.syntona.openNewDocument(state, engine, density))
                                }
                            }
                        ) {
                            if (current != null) openInto(current)
                        }
                    }
                    Item("Add Patch from File...") {
                        val activeGs = state.activeGraphState
                        if (activeGs != null) {
                            showOpenFileDialog { path, content ->
                                val parsed = try { JsonParser(content).parse() as? JsonValue.Obj } catch (_: Exception) { null }
                                if (parsed != null) {
                                    val fileType = (parsed.map["type"] as? JsonValue.Str)?.value ?: "patch"
                                    val fileName = path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast(SYNTONA_FILE_SUFFIX)
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
                    // Always enabled for the same reason as Cut/Copy/Paste below: a stale disabled
                    // flag makes macOS swallow Cmd+S. Saving an unchanged patch is harmless, and
                    // with no path yet this falls through to the Save As dialog, which is what
                    // Cmd+S should do anyway. Only a bundled resource must be left alone.
                    Item(
                        "Save",
                        shortcut = KeyShortcut(Key.S, meta = true)
                    ) {
                        if (!FileState.isResource && gs != null) {
                            val rootPatch = FileState.activeDocument?.rootPatch
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
                                            showSaveFileDialog(json, DEFAULT_PATCH_FILENAME) { savedPath ->
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
                                        showSaveFileDialog(json, DEFAULT_PATCH_FILENAME) { savedPath ->
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
                            val rootPatch = FileState.activeDocument?.rootPatch
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
                                            FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
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
                                        FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
                                    ) { savedPath ->
                                        rootPatch.filePath = savedPath
                                        rootPatch.clearModifiedRecursively()
                                        FileState.markSaved(savedPath)
                                    }
                                }
                            }
                        }
                    }
                    if (isSaveAsResourceAvailable()) {
                        Item("SaveAsResource...") {
                            if (gs != null) {
                                val rootPatch = FileState.activeDocument?.rootPatch
                                if (rootPatch != null) {
                                    val modifiedChildren = rootPatch.findModifiedReferencedPatches()
                                    if (modifiedChildren.isNotEmpty()) {
                                        FileState.patchesToConfirmSave.clear()
                                        FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                        FileState.postConfirmationSaveAction = {
                                            showSaveAsResourceFileDialog(
                                                GraphSerializer.serialize(
                                                    gs, gs.modules.toList(), density,
                                                    rootWindowX = rootPatch.windowX,
                                                    rootWindowY = rootPatch.windowY,
                                                    rootWindowWidth = rootPatch.windowWidth,
                                                    rootWindowHeight = rootPatch.windowHeight
                                                ),
                                                FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
                                            ) { savedPath ->
                                                rootPatch.filePath = savedPath
                                                rootPatch.clearModifiedRecursively()
                                                FileState.markSaved(savedPath)
                                            }
                                        }
                                    } else {
                                        showSaveAsResourceFileDialog(
                                            GraphSerializer.serialize(
                                                gs, gs.modules.toList(), density,
                                                rootWindowX = rootPatch.windowX,
                                                rootWindowY = rootPatch.windowY,
                                                rootWindowWidth = rootPatch.windowWidth,
                                                rootWindowHeight = rootPatch.windowHeight
                                            ),
                                            FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
                                        ) { savedPath ->
                                            rootPatch.filePath = savedPath
                                            rootPatch.clearModifiedRecursively()
                                            FileState.markSaved(savedPath)
                                        }
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
                            } ?: "${target.name}$SYNTONA_FILE_SUFFIX"
                            showSaveFileDialog(json, defaultName) { path ->
                                target.filePath = path
                                target.clearModifiedRecursively()
                                val rootPatch = FileState.activeDocument?.rootPatch
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
                    Separator()
                    Item("Settings...", shortcut = KeyShortcut(Key.Comma, meta = true)) {
                        com.syntona.ui.SettingsDialogState.open()
                    }
                }
                Menu("Edit") {
                    // These actions deliberately look the active window up when they run. The
                    // menu bar builds each item once and reuses its action for the keyboard
                    // shortcut, so a GraphState captured out here would be whatever was frontmost
                    // when the menu was last composed, not what the user is actually editing.
                    Item("Undo", shortcut = KeyShortcut(Key.Z, meta = true)) {
                        state.activeGraphState?.engine?.undo()
                    }
                    Item("Redo", shortcut = KeyShortcut(Key.Z, meta = true, shift = true)) {
                        state.activeGraphState?.engine?.redo()
                    }
                    Separator()
                    // Deliberately left always enabled rather than greyed out when there is
                    // nothing to act on. The menu bar is an AWT component and its enabled flag
                    // does not reliably follow Compose state, and macOS silently swallows the
                    // accelerator of a disabled item: that is what made Cmd+C work only
                    // sometimes, and Cmd+V never, since Paste starts disabled and the menu rarely
                    // recomposed to notice the clipboard filling up. EditActions already does
                    // nothing when there is no selection or an empty clipboard, so the guard
                    // lives there instead of in the enabled flag.
                    Item("Cut", shortcut = KeyShortcut(Key.X, meta = true)) {
                        EditActions.cut(state, density)
                    }
                    Item("Copy", shortcut = KeyShortcut(Key.C, meta = true)) {
                        EditActions.copy(state, density)
                    }
                    Item("Paste", shortcut = KeyShortcut(Key.V, meta = true)) {
                        EditActions.paste(state, density)
                    }
                    Separator()
                    Item("Make a Patch") {
                        EditActions.makePatch(state, density)
                    }
                }
                Menu("Window") {
                    com.syntona.dataflow.editor.WindowMenu.build(state).forEach { entry ->
                        Item(entry.menuText) {
                            com.syntona.dataflow.editor.WindowMenu.show(state, entry.window)
                        }
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
                    Item("Discuss on Discord...") {
                        com.syntona.openUrlInBrowser("https://discord.gg/Q5tAD5bqw")
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
                val current = FileState.activeDocument
                val engine = current?.rootPatch?.graphState?.engine
                if (current != null && engine != null) {
                    fun load(into: com.syntona.dataflow.editor.DocumentState) {
                        scope.launch {
                            try {
                                val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                com.syntona.loadIntoDocument(
                                    state, into, path, bytes.decodeToString(), density
                                )
                            } catch (e: Exception) {
                                println("Failed to load: ${e.message}")
                            }
                        }
                    }
                    FileState.requestIfDirty(
                        openInNewWindow = {
                            load(com.syntona.openNewDocument(state, engine, density))
                        }
                    ) {
                        load(current)
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

