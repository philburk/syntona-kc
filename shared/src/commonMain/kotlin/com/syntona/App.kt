package com.syntona

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.SyntonaModel
import com.syntona.core.audio.KSynRecorder
import com.syntona.dataflow.editor.*
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.modules.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope


@Composable
@Preview
fun App(
    onMdiStateReady: ((MdiState) -> Unit)? = null,
    showEditMenuInToolbar: Boolean = true,
    showFileMenuInToolbar: Boolean = true,
) {
    MaterialTheme {
        var showContent by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        val examplesList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("examples/") && it.endsWith(".json") }
        }
        val tutorialsList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("tutorials/") && it.endsWith(".json") }
        }
        val syntonaModel = remember { SyntonaModel() }
        val appDensity = LocalDensity.current.density

        val rootPatch = remember {
            PatchModule("root").also { it.graphState.engine = syntonaModel.engine }.apply {
                graphState.apply {
                    /*
                    val kick = KickModule()
                    val random = RandomModule()
                    val add = AddModule()
                    val show = ShowModule()

                    addModule(kick, Offset(50f, 50f))
                    addModule(random, Offset(250f, 50f))
                    addModule(add, Offset(450f, 150f))
                    addModule(show, Offset(650f, 150f))

                    connect(kick.output, random.kick)
                    connect(random.output, add.inputA)
                    connect(add.output, show.input)
                     */
                }
            }
        }

        val mdiState = remember {
            MdiState().also { mdi ->
                mdi.rootGraphState = rootPatch.graphState
                mdi.activeGraphState = rootPatch.graphState
                mdi.open(
                    MdiWindowInfo(
                        id = rootPatch,
                        title = rootPatch.name,
                        position = Offset(10f, 10f),
                        closable = false,
                        graphState = rootPatch.graphState,
                        width = 800.dp,
                        height = 500.dp,
                        content = { GraphEditor(rootPatch.graphState) }
                    )
                )
            }
        }
        SideEffect {
            onMdiStateReady?.invoke(mdiState)
            rootPatch.graphState.onChange = { FileState.markDirty() }
        }
        LaunchedEffect(Unit) {
            syntonaModel.start()
        }
        LaunchedEffect(FileState.currentFilePath) {
            val rootWindow = mdiState.windows.find { it.id == rootPatch }
            if (rootWindow != null) {
                val path = FileState.currentFilePath
                rootWindow.title = if (path == null) {
                    rootPatch.name
                } else {
                    val marker = "composeResources/files/"
                    if (path.contains(marker)) {
                        path.substringAfter(marker)
                    } else {
                        path
                    }
                }
            }
        }
        LaunchedEffect(mdiState, scope) {
            LinkModule.onTriggerLoad = { path ->
                val activeGs = mdiState.activeGraphState
                val activeEngine = activeGs?.engine
                if (activeGs != null) {
                    FileState.requestIfDirty {
                        scope.launch {
                            try {
                                val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                val content = bytes.decodeToString()
                                activeGs.clearAllModules()
                                activeEngine?.clearHistory()
                                mdiState.closeAllClosable()
                                GraphSerializer.deserialize(activeGs, content, density = appDensity)
                                FileState.markSaved(path)
                                activeGs.kickLoadKickModules()
                            } catch (e: Exception) {
                                println("Failed to load link file: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!showContent) {
                Text("Choose top level editor:")
                Button(onClick = {
                    showContent = !showContent
                    syntonaModel.start()
                }) {
                    Text("DataFlow")
                }
                Button(onClick = {
                    showContent = !showContent
                    syntonaModel.start()
                }) {
                    Text("Hierarchy")
                }
            } else {
                CompositionLocalProvider(LocalMdiState provides mdiState) {
                    val appFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { appFocusRequester.requestFocus() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(appFocusRequester)
                            .focusable()
                            // WebAudio cannot start until after the user has clicked.
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    var hasStarted = false
                                    while (!hasStarted) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press) {
                                            syntonaModel.start()
                                            hasStarted = true
                                        }
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                val engine = mdiState.activeGraphState?.engine
                                when {
                                    mdiState.activeGraphState?.onKeyEvent(keyEvent) == true -> true
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) -> {
                                        mdiState.activeGraphState?.deleteSelectedModules()
                                        true
                                    }
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.Z &&
                                    (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) -> {
                                        if (keyEvent.isShiftPressed) engine?.redo()
                                        else engine?.undo()
                                        true
                                    }
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.C &&
                                    (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) -> {
                                        val gs = mdiState.activeGraphState
                                        if (gs != null && gs.selectedModules.isNotEmpty()) {
                                            CopyPasteService.copy(gs, gs.selectedModules.toList(), appDensity)
                                        }
                                        true
                                    }
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.X &&
                                    (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) -> {
                                        val gs = mdiState.activeGraphState
                                        if (gs != null && gs.selectedModules.isNotEmpty()) {
                                            CopyPasteService.copy(gs, gs.selectedModules.toList(), appDensity)
                                            gs.deleteSelectedModules()
                                        }
                                        true
                                    }
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.V &&
                                    (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) -> {
                                        mdiState.activeGraphState?.let { CopyPasteService.paste(it, appDensity) }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .onGloballyPositioned { mdiState.appRootOffset = it.positionInRoot() }
                    ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Panel: Module Tree
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(200.dp)
                                .background(Color(0xFF252525))
                        ) {
                            ModuleTree(state = rootPatch.graphState)
                        }

                        // Right Panel: Toolbar + MDI Desktop
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // Toolbar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(Color(0xFF1A1A1A))
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {


                                 if (showFileMenuInToolbar) {
                                     val gs = rootPatch.graphState
                                     val engine = syntonaModel.engine
                                     var fileExpanded by remember { mutableStateOf(false) }
                                     Box {
                                         Button(onClick = { fileExpanded = true }) { Text("File") }
                                         DropdownMenu(
                                             expanded = fileExpanded,
                                             onDismissRequest = { fileExpanded = false }
                                         ) {
                                             DropdownMenuItem(
                                                 text = { Text("New") },
                                                 trailingIcon = { ShortcutHint("⌘N") },
                                                 onClick = {
                                                     fileExpanded = false
                                                     FileState.requestIfDirty {
                                                         gs.clearAllModules()
                                                         engine.clearHistory()
                                                         FileState.markNew()
                                                         mdiState.closeAllClosable()
                                                     }
                                                 }
                                             )
                                             DropdownMenuItem(
                                                 text = { Text("Open...") },
                                                 trailingIcon = { ShortcutHint("⌘O") },
                                                 onClick = {
                                                     fileExpanded = false
                                                     FileState.requestIfDirty {
                                                         showOpenFileDialog { name, content ->
                                                             gs.clearAllModules()
                                                             engine.clearHistory()
                                                             mdiState.closeAllClosable()
                                                             GraphSerializer.deserialize(gs, content, density = appDensity)
                                                             FileState.markSaved(name)
                                                             gs.kickLoadKickModules()
                                                         }
                                                     }
                                                 }
                                             )
                                             HorizontalDivider()
                                             DropdownMenuItem(
                                                 text = { Text("Save") },
                                                 trailingIcon = { ShortcutHint("⌘S") },
                                                 enabled = FileState.isDirty,
                                                 onClick = {
                                                     fileExpanded = false
                                                     val json = GraphSerializer.serialize(gs, gs.modules.toList(), appDensity)
                                                     val path = FileState.currentFilePath
                                                     if (path != null) {
                                                         saveFileDirect(path, json)
                                                         FileState.markSaved(path)
                                                     } else {
                                                         showSaveFileDialog(json, "patch.json") { FileState.markSaved(it) }
                                                     }
                                                 }
                                             )
                                             DropdownMenuItem(
                                                 text = { Text("Save As...") },
                                                 trailingIcon = { ShortcutHint("⌘⇧S") },
                                                 onClick = {
                                                     fileExpanded = false
                                                     showSaveFileDialog(
                                                         GraphSerializer.serialize(gs, gs.modules.toList(), appDensity),
                                                         FileState.currentFileName ?: "patch.json"
                                                     ) { FileState.markSaved(it) }
                                                 }
                                             )
                                         }
                                     }
                                 }
                                if (showEditMenuInToolbar) {
                                    val activeGs = mdiState.activeGraphState
                                    val hasSelection = activeGs?.selectedModules?.isNotEmpty() == true
                                    var editExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        Button(onClick = { editExpanded = true }) { Text("Edit") }
                                        DropdownMenu(
                                            expanded = editExpanded,
                                            onDismissRequest = { editExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Undo") },
                                                trailingIcon = { ShortcutHint("⌘Z") },
                                                onClick = {
                                                    mdiState.activeGraphState?.engine?.undo()
                                                    editExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Redo") },
                                                trailingIcon = { ShortcutHint("⌘⇧Z") },
                                                onClick = {
                                                    mdiState.activeGraphState?.engine?.redo()
                                                    editExpanded = false
                                                }
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Cut") },
                                                trailingIcon = { ShortcutHint("⌘X") },
                                                enabled = hasSelection,
                                                onClick = {
                                                    val gs = activeGs ?: return@DropdownMenuItem
                                                    CopyPasteService.copy(gs, gs.selectedModules.toList(), appDensity)
                                                    gs.deleteSelectedModules()
                                                    editExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Copy") },
                                                trailingIcon = { ShortcutHint("⌘C") },
                                                enabled = hasSelection,
                                                onClick = {
                                                    val gs = activeGs ?: return@DropdownMenuItem
                                                    CopyPasteService.copy(gs, gs.selectedModules.toList(), appDensity)
                                                    editExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Paste") },
                                                trailingIcon = { ShortcutHint("⌘V") },
                                                enabled = CopyPasteService.pasteBuffer != null,
                                                onClick = {
                                                    activeGs?.let { CopyPasteService.paste(it, appDensity) }
                                                    editExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (showFileMenuInToolbar) {
                                     var helpExpanded by remember { mutableStateOf(false) }
                                     var tutorialsExpanded by remember { mutableStateOf(false) }
                                     var examplesExpanded by remember { mutableStateOf(false) }
                                     Box {
                                         Button(onClick = { helpExpanded = true }) { Text("Help") }
                                         DropdownMenu(
                                             expanded = helpExpanded,
                                             onDismissRequest = { helpExpanded = false }
                                         ) {
                                             var itemWidth by remember { mutableStateOf(0) }
                                             val density = androidx.compose.ui.platform.LocalDensity.current
                                             val itemWidthDp = remember(itemWidth, density) {
                                                 with(density) { itemWidth.toDp() }
                                             }
                                             val examplesTree = remember(examplesList) {
                                                 buildMenuTree(examplesList, "examples/")
                                             }
                                             val tutorialsTree = remember(tutorialsList) {
                                                 buildMenuTree(tutorialsList, "tutorials/")
                                             }

                                             Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                                                 itemWidth = coordinates.size.width
                                             }) {
                                                 DropdownMenuItem(
                                                     text = { Text("Load Example") },
                                                     trailingIcon = { Text("›", fontSize = 16.sp) },
                                                     onClick = { examplesExpanded = true },
                                                     modifier = Modifier.pointerInput(Unit) {
                                                         awaitPointerEventScope {
                                                             while (true) {
                                                                 val event = awaitPointerEvent()
                                                                 if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                                                     examplesExpanded = true
                                                                     tutorialsExpanded = false
                                                                 }
                                                             }
                                                         }
                                                     }
                                                 )
                                                 DropdownMenu(
                                                     expanded = examplesExpanded,
                                                     onDismissRequest = { examplesExpanded = false },
                                                     offset = androidx.compose.ui.unit.DpOffset(x = itemWidthDp, y = (-48).dp)
                                                 ) {
                                                     RenderDropdownMenuNode(examplesTree, mdiState, scope, onClose = {
                                                         examplesExpanded = false
                                                         helpExpanded = false
                                                     })
                                                 }
                                             }
                                             Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                                                 itemWidth = coordinates.size.width
                                             }) {
                                                 DropdownMenuItem(
                                                     text = { Text("Load Tutorial") },
                                                     trailingIcon = { Text("›", fontSize = 16.sp) },
                                                     onClick = { tutorialsExpanded = true },
                                                     modifier = Modifier.pointerInput(Unit) {
                                                         awaitPointerEventScope {
                                                             while (true) {
                                                                 val event = awaitPointerEvent()
                                                                 if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                                                     tutorialsExpanded = true
                                                                     examplesExpanded = false
                                                                 }
                                                             }
                                                         }
                                                     }
                                                 )
                                                 DropdownMenu(
                                                     expanded = tutorialsExpanded,
                                                     onDismissRequest = { tutorialsExpanded = false },
                                                     offset = androidx.compose.ui.unit.DpOffset(x = itemWidthDp, y = (-48).dp)
                                                 ) {
                                                     RenderDropdownMenuNode(tutorialsTree, mdiState, scope, onClose = {
                                                         tutorialsExpanded = false
                                                         helpExpanded = false
                                                     })
                                                 }
                                             }
                                         }
                                     }
                                 }

                                 Spacer(modifier = Modifier.width(4.dp))
                                 val activeGraph = mdiState.activeGraphState
                                 if (activeGraph != null) {
                                     val isMac = getPlatform().name.contains("Mac", ignoreCase = true)
                                     val shortcutHint = if (isMac) "⌘K" else "Ctrl+K"
                                     val isActive = activeGraph.isKeyboardModeActive
                                     Button(
                                         onClick = { activeGraph.isKeyboardModeActive = !activeGraph.isKeyboardModeActive },
                                         colors = ButtonDefaults.buttonColors(
                                             containerColor = if (isActive) Color(0xFF005A9C) else Color(0xFF2E2E2E),
                                             contentColor = Color.White
                                         ),
                                         modifier = Modifier.height(32.dp),
                                         contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                     ) {
                                         Text(
                                             text = if (isActive) "⌨️ Keyboard Active ($shortcutHint)" else "⌨️ Keyboard Disabled ($shortcutHint)",
                                             style = MaterialTheme.typography.bodySmall
                                         )
                                     }
                                     Spacer(modifier = Modifier.width(4.dp))
                                     Box(
                                         modifier = Modifier
                                             .width(1.dp)
                                             .fillMaxHeight(0.6f)
                                             .background(Color.Gray.copy(alpha = 0.5f))
                                     )
                                     Spacer(modifier = Modifier.width(4.dp))
                                 }

                                Text(
                                    text = "KSyn:",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = {
                                        chooseWavRecordFile("recording.wav") { path ->
                                            KSynRecorder.startRecording(path)
                                        }
                                    },
                                    enabled = !KSynRecorder.isRecording,
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = Color(0xFF2E2E2E),
                                        disabledContentColor = Color.Gray
                                    ),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Capture...")
                                }
                                Button(
                                    onClick = { KSynRecorder.stopRecording() },
                                    enabled = KSynRecorder.isRecording,
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = Color(0xFF2E2E2E),
                                        disabledContentColor = Color.Gray
                                    ),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Stop")
                                }
                                Text(
                                    text = KSynRecorder.durationText,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                )
                                if (KSynRecorder.hasSkippedSamples) {
                                    Text(
                                        text = "!",
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = "Max:",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                var menuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(KSynRecorder.maxDurationMinutes.toString())
                                            Text("▼", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        listOf(1, 2, 3, 5, 10, 20, 60).forEach { mins ->
                                            DropdownMenuItem(
                                                text = { Text("$mins") },
                                                onClick = {
                                                    KSynRecorder.maxDurationMinutes = mins
                                                    menuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "minutes",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // MDI Desktop
                            MdiDesktop(
                                mdiState = mdiState,
                                modifier = Modifier.weight(1f).background(Color(0xFF181818))
                            )
                        }
                    }

                    // Ghost rectangle shown while dragging from ModuleTree
                    mdiState.treeDragCreator?.let {
                        Box(
                            modifier = Modifier
                                .offset {
                                    val pos = mdiState.treeDragPosition - mdiState.appRootOffset
                                    IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                }
                                .width(140.dp)
                                .height(60.dp)
                                .background(Color.Cyan.copy(alpha = 0.3f))
                                .border(1.dp, Color.Cyan, RoundedCornerShape(4.dp))
                        )
                    }
                    FileState.pendingAction?.let { pendingAction ->
                        AlertDialog(
                            onDismissRequest = { FileState.pendingAction = null },
                            title = { Text("Unsaved Changes") },
                            text = { Text("Discard unsaved changes?") },
                            confirmButton = {
                                Button(onClick = {
                                    FileState.pendingAction = null
                                    pendingAction()
                                }) { Text("Discard") }
                            },
                            dismissButton = {
                                TextButton(onClick = { FileState.pendingAction = null }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                    } // end outer Box
                }
            }
        }
    }
}

@Composable
private fun ShortcutHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

class MenuNode(
    val name: String,
    val path: String? = null,
    val children: MutableMap<String, MenuNode> = mutableMapOf()
)

fun buildMenuTree(paths: List<String>, rootPrefix: String): MenuNode {
    val root = MenuNode("")
    for (path in paths) {
        val relativePath = if (path.startsWith(rootPrefix)) {
            path.substring(rootPrefix.length)
        } else {
            path
        }
        val segments = relativePath.split('/')
        var current = root
        for (i in segments.indices) {
            val segment = segments[i]
            if (i == segments.lastIndex) {
                val displayName = segment.removeSuffix(".json")
                current.children[segment] = MenuNode(displayName, path)
            } else {
                current = current.children.getOrPut(segment) { MenuNode(segment) }
            }
        }
    }
    return root
}

@Composable
fun RenderDropdownMenuNode(
    node: MenuNode,
    state: MdiState,
    scope: CoroutineScope,
    onClose: () -> Unit
) {
    val density = LocalDensity.current.density
    val sortedChildren = node.children.values.sortedWith(
        compareBy<MenuNode> { it.path != null }
            .thenBy { it.name }
    )

    for (child in sortedChildren) {
        val path = child.path
        if (path != null) {
            DropdownMenuItem(
                text = { Text(child.name) },
                onClick = {
                    onClose()
                    val activeGs = state.activeGraphState
                    val activeEngine = activeGs?.engine
                    if (activeGs != null) {
                        FileState.requestIfDirty {
                            scope.launch {
                                try {
                                    val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                    val content = bytes.decodeToString()
                                    activeGs.clearAllModules()
                                    activeEngine?.clearHistory()
                                    state.closeAllClosable()
                                    GraphSerializer.deserialize(activeGs, content, density = density)
                                    FileState.markSaved(path)
                                    activeGs.kickLoadKickModules()
                                } catch (e: Exception) {
                                    println("Failed to load: ${e.message}")
                                }
                            }
                        }
                    }
                }
            )
        } else {
            var isExpanded by remember { mutableStateOf(false) }
            var width by remember { mutableStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val widthDp = with(density) { width.toDp() }

            Box(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    width = coordinates.size.width
                }
            ) {
                DropdownMenuItem(
                    text = { Text(child.name) },
                    trailingIcon = { Text("›", fontSize = 16.sp) },
                    onClick = { isExpanded = true },
                    modifier = Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                    isExpanded = true
                                }
                            }
                        }
                    }
                )
                DropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false },
                    offset = androidx.compose.ui.unit.DpOffset(x = widthDp, y = (-48).dp)
                ) {
                    RenderDropdownMenuNode(child, state, scope, onClose = {
                        isExpanded = false
                        onClose()
                    })
                }
            }
        }
    }
}

