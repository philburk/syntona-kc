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
            if (desktop.isSupported(java.awt.Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler {
                    com.syntona.ui.AboutDialogState.open()
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
            com.syntona.dataflow.registry.InstrumentRegistry.loadInstrumentsIndex()
            com.syntona.dataflow.registry.SoundRegistry.loadSoundsIndex()
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
                    Menu("Recent Patches") {
                        val recents = com.syntona.storage.RecentPatchesStore.recentPatches
                        if (recents.isEmpty()) {
                            Item("No Recent Patches", enabled = false) {}
                        } else {
                            recents.forEach { path ->
                                val fileName = path.substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }
                                Item(fileName) {
                                    com.syntona.openRecentPatch(path, state, engine, density)
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
                                    val isPoly = fileType == "polyPatch" || fileType == "core.polyPatch"
                                    val fileName = path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                    val newPatch = if (isPoly) {
                                        val poly = PolyPatchModule(fileName)
                                        val maxV = (parsed.map["maxVoices"] as? JsonValue.Num)?.value?.toInt() ?: 4
                                        val isStereo = (parsed.map["isStereo"] as? JsonValue.Bool)?.value ?: false
                                        poly.initializeVoices(maxV, isStereo = isStereo)
                                        poly
                                    } else {
                                        PatchModule(fileName)
                                    }
                                    (parsed.map["windowX"] as? JsonValue.Num)?.value?.let { newPatch.windowX = it.toFloat() }
                                    (parsed.map["windowY"] as? JsonValue.Num)?.value?.let { newPatch.windowY = it.toFloat() }
                                    (parsed.map["windowWidth"] as? JsonValue.Num)?.value?.let { newPatch.windowWidth = it.toFloat() }
                                    (parsed.map["windowHeight"] as? JsonValue.Num)?.value?.let { newPatch.windowHeight = it.toFloat() }
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
                                state.syncChildWindowStates(rootPatch)
                                val modifiedChildren = rootPatch.findModifiedReferencedPatches()
                                if (modifiedChildren.isNotEmpty()) {
                                    FileState.patchesToConfirmSave.clear()
                                    FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                    FileState.postConfirmationSaveAction = {
                                        state.syncChildWindowStates(rootPatch)
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
                                state.syncChildWindowStates(rootPatch)
                                val modifiedChildren = rootPatch.findModifiedReferencedPatches()
                                if (modifiedChildren.isNotEmpty()) {
                                    FileState.patchesToConfirmSave.clear()
                                    FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                    FileState.postConfirmationSaveAction = {
                                        state.syncChildWindowStates(rootPatch)
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
                                    state.syncChildWindowStates(rootPatch)
                                    val modifiedChildren = rootPatch.findModifiedReferencedPatches()
                                    if (modifiedChildren.isNotEmpty()) {
                                        FileState.patchesToConfirmSave.clear()
                                        FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                        FileState.postConfirmationSaveAction = {
                                            state.syncChildWindowStates(rootPatch)
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
                            state.syncChildWindowStates(target)
                            val json = GraphSerializer.serializePatch(target, density)
                            val defaultName = target.filePath?.let { p ->
                                p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
                            } ?: "${target.name}$SYNTONA_FILE_SUFFIX"
                            showSaveFileDialog(json, defaultName) { path ->
                                val rPath = com.syntona.dataflow.editor.resourceRelativePath(path)
                                if (rPath != null) {
                                    if (rPath.startsWith("instruments/")) {
                                        val instrumentName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                        com.syntona.dataflow.registry.InstrumentRegistry.registerInstrument(instrumentName, json, path)
                                    } else if (rPath.startsWith("sounds/")) {
                                        val soundName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                        com.syntona.dataflow.registry.SoundRegistry.registerSound(soundName, json, path)
                                    }
                                }
                                val rootPatch = FileState.activeDocument?.rootPatch
                                if (target === rootPatch) {
                                    target.filePath = path
                                    target.clearModifiedRecursively()
                                    FileState.markSaved(path)
                                } else {
                                    target.detachFile()
                                    target.clearModifiedRecursively()
                                    target.parentPatch?.graphState?.markChanged()
                                }
                            }
                        }
                    }
                    if (isSaveAsResourceAvailable()) {
                        Item("Save Patch as Resource...") {
                            val target = state.activeGraphState?.parentPatch
                            if (target != null) {
                                state.syncChildWindowStates(target)
                                val json = GraphSerializer.serializePatch(target, density)
                                val defaultName = target.filePath?.let { p ->
                                    p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
                                } ?: "${target.name}$SYNTONA_FILE_SUFFIX"
                                showSaveAsResourceFileDialog(json, defaultName) { path ->
                                    target.filePath = path
                                    target.clearModifiedRecursively()
                                    val rPath = com.syntona.dataflow.editor.resourceRelativePath(path)
                                    if (rPath != null) {
                                        if (rPath.startsWith("instruments/")) {
                                            val instrumentName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                            com.syntona.dataflow.registry.InstrumentRegistry.registerInstrument(instrumentName, json, path)
                                        } else if (rPath.startsWith("sounds/")) {
                                            val soundName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                            com.syntona.dataflow.registry.SoundRegistry.registerSound(soundName, json, path)
                                        }
                                    }
                                    val rootPatch = FileState.activeDocument?.rootPatch
                                    if (target === rootPatch) {
                                        FileState.markSaved(path)
                                    }
                                }
                            }
                        }
                    }
                    Item(
                        "Detach File from Patch",
                        enabled = state.activeGraphState?.parentPatch?.let { it.filePath != null || it.resourcePath != null } == true
                    ) {
                        val target = state.activeGraphState?.parentPatch
                        if (target != null) {
                            target.detachFile()
                        }
                    }
                    val activePatch = state.activeGraphState?.parentPatch
                    val canExport = activePatch != null && !activePatch.isRoot
                    Menu("Export") {
                        Item("Source code for KSyn...", enabled = canExport) {
                            val target = state.activeGraphState?.parentPatch ?: return@Item
                            val warning = com.syntona.export.SourceExporter.exportWarningMessage(target, forJSyn = false)
                            if (warning != null) {
                                val response = javax.swing.JOptionPane.showConfirmDialog(
                                    null,
                                    warning,
                                    "Export Warning",
                                    javax.swing.JOptionPane.YES_NO_OPTION,
                                    javax.swing.JOptionPane.WARNING_MESSAGE
                                )
                                if (response != javax.swing.JOptionPane.YES_OPTION) {
                                    return@Item
                                }
                            }
                            val defaultClassName = com.syntona.export.SourceExporter.sanitizeClassName(target.name)
                            com.syntona.dataflow.editor.showExportSourceFileDialog(
                                defaultName = "$defaultClassName.kt",
                                extension = "kt",
                                dialogTitle = "Export Source Code for KSyn"
                            ) { chosenPath ->
                                val fileStem = chosenPath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast(".kt")
                                val className = com.syntona.export.SourceExporter.sanitizeClassName(fileStem)
                                val code = com.syntona.export.KSynSourceExporter.export(target, className)
                                saveFileDirect(chosenPath, code)
                            }
                        }
                        Item("Source code for JSyn...", enabled = canExport) {
                            val target = state.activeGraphState?.parentPatch ?: return@Item
                            val warning = com.syntona.export.SourceExporter.exportWarningMessage(target, forJSyn = true)
                            if (warning != null) {
                                val response = javax.swing.JOptionPane.showConfirmDialog(
                                    null,
                                    warning,
                                    "Export Warning",
                                    javax.swing.JOptionPane.YES_NO_OPTION,
                                    javax.swing.JOptionPane.WARNING_MESSAGE
                                )
                                if (response != javax.swing.JOptionPane.YES_OPTION) {
                                    return@Item
                                }
                            }
                            val defaultClassName = com.syntona.export.SourceExporter.sanitizeClassName(target.name)
                            com.syntona.dataflow.editor.showExportSourceFileDialog(
                                defaultName = "$defaultClassName.java",
                                extension = "java",
                                dialogTitle = "Export Source Code for JSyn"
                            ) { chosenPath ->
                                val fileStem = chosenPath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast(".java")
                                val className = com.syntona.export.SourceExporter.sanitizeClassName(fileStem)
                                val code = com.syntona.export.JSynSourceExporter.export(target, className)
                                saveFileDirect(chosenPath, code)
                            }
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
                    Item("Join Syntona Server on Discord...") {
                        com.syntona.openUrlInBrowser("https://discord.gg/Q5tAD5bqw")
                    }
                    Item("Discuss Syntona on Discord...") {
                        com.syntona.openUrlInBrowser("https://discord.com/channels/1532908817677942846/1532924422321864855")
                    }
                    Item("About Syntona...") {
                        com.syntona.ui.AboutDialogState.open()
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

