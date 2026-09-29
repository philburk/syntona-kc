package com.syntona

import com.syntona.ui.ChevronRightIcon
import com.syntona.ui.DropdownArrow
import com.syntona.ui.WireLayerIcon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.syntona.dataflow.editor.LocalGraphColors
import com.syntona.dataflow.editor.DarkGraphColors
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.SyntonaModel
import com.syntona.core.audio.KSynRecorder
import com.syntona.core.audio.SynthKSyn
import kotlinx.coroutines.delay
import com.syntona.dataflow.editor.*
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.modules.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope


/**
 * The root patch has no module entry of its own in the saved JSON (only its children are
 * serialized), so its window's position/size travel as top-level fields instead. Applies them
 * to the already-open root window and syncs them back onto [rootPatch] so a subsequent Save
 * round-trips correctly even if the window isn't touched again.
 */
fun restoreRootWindowGeometry(
    mdiState: MdiState,
    rootPatch: PatchModule,
    content: String,
    density: Float
) {
    val geometry = GraphSerializer.readRootWindowGeometry(content) ?: return
    val rootWindow = mdiState.windows.find { it.id == rootPatch } ?: return
    if (geometry.x != null && geometry.y != null) {
        rootWindow.position = Offset(geometry.x * density, geometry.y * density)
    }
    geometry.width?.let { rootWindow.width = it.dp }
    geometry.height?.let { rootWindow.height = it.dp }
    mdiState.cascadeIfOverlapping(rootWindow)
    rootWindow.onGeometryChanged?.invoke()
}

fun restoreWindowStates(
    mdiState: MdiState,
    graphState: GraphState,
    density: Float
) {
    fun traverse(gs: GraphState) {
        for (module in gs.modules) {
            if (module is PatchModule) {
                if (module.isWindowOpen) {
                    val savedX = module.windowX
                    val savedY = module.windowY
                    val restoredPosition = if (savedX != null && savedY != null) {
                        Offset(savedX * density, savedY * density)
                    } else {
                        Offset(120f, 90f)
                    }
                    val window = MdiWindowInfo(
                        id = module,
                        title = module.name,
                        position = restoredPosition,
                        width = module.windowWidth?.dp ?: 600.dp,
                        height = module.windowHeight?.dp ?: 450.dp,
                        graphState = module.graphState,
                        content = { GraphEditor(module.graphState) }
                    )
                    window.onGeometryChanged = {
                        val newX = window.position.x / density
                        val newY = window.position.y / density
                        val newW = window.width.value
                        val newH = window.height.value
                        if (module.windowWidth != null && (
                            module.windowWidth != newW || module.windowHeight != newH ||
                            module.windowX != newX || module.windowY != newY
                        )) {
                            module.isModified = true
                            module.parentPatch?.graphState?.markChanged()
                            FileState.documentFor(module.graphState)?.markDirty()
                        }
                        module.windowX = newX
                        module.windowY = newY
                        module.windowWidth = newW
                        module.windowHeight = newH
                    }
                    window.onGeometryChanged?.invoke()
                    mdiState.open(window)
                }
                traverse(module.graphState)
            }
        }
    }
    traverse(graphState)
}

/**
 * Creates a top level document and opens its window.
 *
 * Used both for the document the app starts with and for any opened alongside it, so that loading
 * an example never has to discard work in progress. Each document owns its own root patch, file
 * path and dirty flag; [FileState] tracks which one is in front.
 */
fun openNewDocument(
    mdiState: MdiState,
    engine: com.syntona.dataflow.engine.DataFlowEngine,
    density: Float,
    name: String = "root",
    closable: Boolean = FileState.documents.isNotEmpty()
): DocumentState {
    val rootPatch = PatchModule(name).also { it.graphState.engine = engine }
    val document = DocumentState(rootPatch)

    // Cascade each new window so it does not land exactly on top of the previous one.
    val step = 24f * FileState.documents.size
    rootPatch.windowX = 10f + step
    rootPatch.windowY = 10f + step
    rootPatch.windowWidth = 800f
    rootPatch.windowHeight = 500f

    val window = MdiWindowInfo(
        id = rootPatch,
        title = document.displayName,
        position = Offset(rootPatch.windowX!! * density, rootPatch.windowY!! * density),
        closable = closable,
        graphState = rootPatch.graphState,
        width = rootPatch.windowWidth!!.dp,
        height = rootPatch.windowHeight!!.dp,
        content = { GraphEditor(rootPatch.graphState) }
    )
    window.onGeometryChanged = {
        rootPatch.windowX = window.position.x / density
        rootPatch.windowY = window.position.y / density
        rootPatch.windowWidth = window.width.value
        rootPatch.windowHeight = window.height.value
    }
    // Marks this document dirty rather than whichever happens to be in front.
    rootPatch.graphState.onChange = {
        if (!rootPatch.graphState.isDeserializing) {
            rootPatch.isModified = true
        }
        document.markDirty()
    }

    FileState.addDocument(document)
    mdiState.open(window)
    return document
}

/**
 * Loads [content] into [document], replacing whatever it held.
 *
 * Shared by every load path so that opening into a new window behaves exactly like opening into
 * the current one, apart from which document it targets.
 */
fun loadIntoDocument(
    mdiState: MdiState,
    document: DocumentState,
    path: String,
    content: String,
    density: Float
) {
    val graphState = document.rootPatch.graphState
    graphState.clearAllModules()
    graphState.engine?.clearHistory()
    mdiState.closeSubWindows(document)
    document.rootPatch.filePath = path
    GraphSerializer.deserialize(graphState, content, density = density, basePath = path)
    restoreWindowStates(mdiState, graphState, density)
    restoreRootWindowGeometry(mdiState, document.rootPatch, content, density)
    document.markSaved(path)
    document.rootPatch.clearModifiedRecursively()
    val win = mdiState.windows.firstOrNull { it.id == document.rootPatch }
    if (win != null) {
        win.title = document.displayName
    }
    graphState.kickLoadKickModules()
    // The URL carries the resource-relative path, which is not the same as the path on disk once
    // a resource has been saved from a source checkout.
    document.resourcePath?.let { setUrlPatchPath(it) }
}

/**
 * Loads a recent patch from [path] into the active document (or opens a new document window).
 */
fun openRecentPatch(
    path: String,
    mdiState: MdiState,
    engine: com.syntona.dataflow.engine.DataFlowEngine?,
    density: Float
) {
    com.syntona.dataflow.editor.readRecentPatchFile(path) { content ->
        if (content == null) {
            println("Unable to load recent patch '$path': file could not be read from disk.")
            return@readRecentPatchFile
        }
        val current = FileState.activeDocument
        fun openInto(into: DocumentState) {
            loadIntoDocument(mdiState, into, path, content, density)
        }
        FileState.requestIfDirty(
            openInNewWindow = {
                if (engine != null) {
                    openInto(openNewDocument(mdiState, engine, density))
                }
            }
        ) {
            if (current != null) openInto(current)
        }
    }
}

/**
 * Loads whatever patch the current URL names into [document].
 *
 * That is either a patch encoded in the fragment by "Share Piece as URL...", or a bundled example
 * or tutorial named by the query string. Shared by the startup load and by the listener that
 * reacts to the URL changing later, so both behave identically.
 *
 * @return true if a patch was loaded.
 */
private suspend fun loadPatchFromUrl(
    mdiState: MdiState,
    document: DocumentState,
    density: Float
): Boolean {
    val encodedJson = getUrlEncodedPatchJson()
    if (!encodedJson.isNullOrEmpty()) {
        return try {
            loadIntoDocument(
                mdiState, document, "shared_piece$SYNTONA_FILE_SUFFIX", encodedJson, density
            )
            true
        } catch (e: Exception) {
            println("Failed to load encoded URL patch: ${e.message}")
            false
        }
    }

    val rawUrlPath = getUrlPatchPath()
    if (rawUrlPath.isNullOrEmpty()) return false
    val filesIndex = com.syntona.SharedResources.getFilesIndex().toSet()
    val validatedPath = sanitizeAndValidateResourcePath(rawUrlPath, filesIndex) ?: return false
    return try {
        val bytes = com.syntona.SharedResources.readBytes("files/$validatedPath")
        loadIntoDocument(mdiState, document, validatedPath, bytes.decodeToString(), density)
        true
    } catch (e: Exception) {
        println("Failed to load URL patch: ${e.message}")
        false
    }
}

/**
 * True when this is a key-down of [letter], for matching shortcuts such as Cmd+C.
 *
 * Matching on [Key] alone is not enough. When a modifier is held, Compose on the web reports the
 * key as unidentified - `Key(keyCode: -1)` - so `key == Key.C` is never true there and every
 * Cmd shortcut silently did nothing. The character does survive as [utf16CodePoint], so that is
 * used as a fallback. [key] is still checked first because it is the reliable path on desktop.
 */
private fun KeyEvent.isShortcutFor(letter: Char, key: Key): Boolean {
    if (type != KeyEventType.KeyDown) return false
    if (this.key == key) return true
    val code = utf16CodePoint
    return code != 0 && code.toChar().lowercaseChar() == letter.lowercaseChar()
}

@Composable
@Preview
fun App(
    onMdiStateReady: ((MdiState) -> Unit)? = null,
    showEditMenuInToolbar: Boolean = true,
    showFileMenuInToolbar: Boolean = true,
) {
    MaterialTheme {
        CompositionLocalProvider(LocalGraphColors provides DarkGraphColors) {
            var showContent by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
        val examplesList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("examples/") && (it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX)) }
        }
        val tutorialsList by produceState<List<String>>(emptyList()) {
            value = com.syntona.SharedResources.getFilesIndex()
                .filter { it.startsWith("tutorials/") && (it.endsWith(".json") || it.endsWith(SYNTONA_FILE_SUFFIX)) }
        }
        val syntonaModel = remember { SyntonaModel() }
        val appDensity = LocalDensity.current.density

        val rootPatch = remember {
            PatchModule("root").also { it.graphState.engine = syntonaModel.engine }.apply {
                windowX = 10f
                windowY = 10f
                windowWidth = 800f
                windowHeight = 500f
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

        var shareNotice by remember { mutableStateOf<String?>(null) }
        var shareNoticeIsProblem by remember { mutableStateOf(false) }
        shareNotice?.let { notice ->
            LaunchedEffect(notice) {
                kotlinx.coroutines.delay(if (shareNoticeIsProblem) 6000 else 3000)
                shareNotice = null
            }
        }

        var exportWarningMessage by remember { mutableStateOf<String?>(null) }
        var onExportWarningProceed by remember { mutableStateOf<(() -> Unit)?>(null) }

        val mdiState = remember {
            MdiState().also { mdi ->
                val document = DocumentState(rootPatch)
                val rootWindow = MdiWindowInfo(
                    id = rootPatch,
                    title = rootPatch.name,
                    position = Offset(
                        (rootPatch.windowX ?: 10f) * appDensity,
                        (rootPatch.windowY ?: 10f) * appDensity
                    ),
                    // The first document cannot be closed, so there is always somewhere to work.
                    closable = false,
                    graphState = rootPatch.graphState,
                    width = rootPatch.windowWidth?.dp ?: 800.dp,
                    height = rootPatch.windowHeight?.dp ?: 500.dp,
                    content = { GraphEditor(rootPatch.graphState) }
                )
                rootWindow.onGeometryChanged = {
                    rootPatch.windowX = rootWindow.position.x / appDensity
                    rootPatch.windowY = rootWindow.position.y / appDensity
                    rootPatch.windowWidth = rootWindow.width.value
                    rootPatch.windowHeight = rootWindow.height.value
                }
                rootPatch.graphState.onChange = {
                    if (!rootPatch.graphState.isDeserializing) {
                        rootPatch.isModified = true
                    }
                    document.markDirty()
                }
                FileState.addDocument(document)
                mdi.open(rootWindow)
            }
        }

        SideEffect {
            onMdiStateReady?.invoke(mdiState)
        }
        LaunchedEffect(Unit) {
            syntonaModel.start()
            com.syntona.dataflow.registry.HelpRegistry.loadHelpFilesIndex()
            com.syntona.dataflow.registry.InstrumentRegistry.loadInstrumentsIndex()
            com.syntona.dataflow.registry.SoundRegistry.loadSoundsIndex()
            val activeDoc = FileState.activeDocument ?: FileState.documents.firstOrNull()
            if (activeDoc != null) {
                loadPatchFromUrl(mdiState, activeDoc, appDensity)
            }
        }

        // Pasting a new share link over an old one only changes the fragment, which the browser
        // treats as the same document: no reload happens, so the new patch has to be picked up
        // here. The user's work is never replaced without asking.
        DisposableEffect(mdiState, scope) {
            val stopListening = observeUrlPatchChanges {
                val engine = syntonaModel.engine
                FileState.requestIfDirty(
                    openInNewWindow = {
                        val document = openNewDocument(mdiState, engine, appDensity)
                        scope.launch { loadPatchFromUrl(mdiState, document, appDensity) }
                    }
                ) {
                    val document = FileState.activeDocument ?: FileState.documents.firstOrNull()
                    if (document != null) {
                        scope.launch { loadPatchFromUrl(mdiState, document, appDensity) }
                    }
                }
            }
            onDispose { stopListening() }
        }
        LaunchedEffect(mdiState, scope) {
            LinkModule.onTriggerLoad = { path ->
                val current = FileState.activeDocument
                val currentEngine = current?.rootPatch?.graphState?.engine
                if (current != null && currentEngine != null) {
                    fun load(into: DocumentState) {
                        scope.launch {
                            try {
                                val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                loadIntoDocument(
                                    mdiState, into, path, bytes.decodeToString(), appDensity
                                )
                            } catch (e: Exception) {
                                println("Failed to load link file: ${e.message}")
                            }
                        }
                    }
                    FileState.requestIfDirty(
                        openInNewWindow = {
                            load(openNewDocument(mdiState, currentEngine, appDensity))
                        }
                    ) {
                        load(current)
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
                    scope.launch { syntonaModel.start() }
                }) {
                    Text("DataFlow")
                }
                Button(onClick = {
                    showContent = !showContent
                    scope.launch { syntonaModel.start() }
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
                            // Take focus back whenever it leaves the app entirely. Key events are
                            // only delivered to the focused node and its ancestors, so once
                            // Compose has nothing focused - which happens after clicking around
                            // the canvas and menus - no shortcut or Delete would ever fire again.
                            // hasFocus covers the whole subtree, so a focused text field inside
                            // keeps its focus and is not fought over.
                            .onFocusChanged { focusState ->
                                if (!focusState.hasFocus) appFocusRequester.requestFocus()
                            }
                            .focusable()
                            // WebAudio cannot start until after the user has clicked.
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    var hasStarted = false
                                    while (!hasStarted) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press) {
                                            scope.launch { syntonaModel.start() }
                                            hasStarted = true
                                        }
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                val engine = mdiState.activeGraphState?.engine
                                // Command keystrokes are matched before the graph gets a look at
                                // them, so that an application shortcut can never be swallowed by
                                // a patch that is listening for computer keyboard notes.
                                val isCommand =
                                    keyEvent.isMetaPressed || keyEvent.isCtrlPressed
                                when {
                                    !isCommand &&
                                    mdiState.activeGraphState?.onKeyEvent(keyEvent) == true -> true
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) -> {
                                        val handler = mdiState.activeEditHandler
                                        if (handler != null) {
                                            if (handler.canDelete()) handler.delete()
                                        } else {
                                            mdiState.activeGraphState?.deleteSelected()
                                        }
                                        true
                                    }
                                    isCommand && keyEvent.isShortcutFor('z', Key.Z) -> {
                                        if (keyEvent.isShiftPressed) engine?.redo()
                                        else engine?.undo()
                                        true
                                    }
                                    isCommand && keyEvent.isShortcutFor('c', Key.C) -> {
                                        EditActions.copy(mdiState, appDensity)
                                        true
                                    }
                                    isCommand && keyEvent.isShortcutFor('x', Key.X) -> {
                                        EditActions.cut(mdiState, appDensity)
                                        true
                                    }
                                    isCommand && keyEvent.isShortcutFor('v', Key.V) -> {
                                        EditActions.paste(mdiState, appDensity)
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
                                     // Resolve the frontmost document here so every item below acts on
                                     // the window the user is looking at, not the first one ever opened.
                                     val activeDoc = FileState.activeDocument
                                     val docRoot = activeDoc?.rootPatch ?: rootPatch
                                     val gs = docRoot.graphState
                                     val engine = syntonaModel.engine
                                     val performShareUrl: () -> Unit = {
                                         mdiState.syncChildWindowStates(docRoot)
                                         val json = GraphSerializer.serialize(
                                             gs, gs.modules.toList(), appDensity,
                                             rootWindowX = docRoot.windowX,
                                             rootWindowY = docRoot.windowY,
                                             rootWindowWidth = docRoot.windowWidth,
                                             rootWindowHeight = docRoot.windowHeight
                                         )
                                         val minified = GraphSerializer.minifyJson(json)
                                         scope.launch {
                                             val result = sharePatchUrl(minified)
                                             shareNoticeIsProblem =
                                                 result.url == null || !result.copiedToClipboard || result.warning != null
                                             shareNotice = when {
                                                 result.url == null -> "Could not build a link for this patch."
                                                 !result.copiedToClipboard ->
                                                     "Link created, but the browser would not write to the clipboard."
                                                 result.warning != null -> result.warning
                                                 else -> "URL copied to clipboard!"
                                             }
                                         }
                                     }
                                     var fileExpanded by remember { mutableStateOf(false) }
                                     Box {
                                         Button(onClick = { fileExpanded = true }) { Text("File") }
                                         DropdownMenu(
                                             expanded = fileExpanded,
                                             onDismissRequest = { fileExpanded = false }
                                         ) {
                                              CompactDropdownMenuItem(
                                                  text = { Text("New") },
                                                  trailingIcon = { ShortcutHint("⌘N") },
                                                  onClick = {
                                                      fileExpanded = false
                                                      val current = FileState.activeDocument
                                                      FileState.requestIfDirty(
                                                          openInNewWindow = {
                                                              openNewDocument(mdiState, engine, appDensity)
                                                          }
                                                      ) {
                                                          if (current != null) {
                                                              val currentGs = current.rootPatch.graphState
                                                              currentGs.clearAllModules()
                                                              currentGs.engine?.clearHistory()
                                                              current.rootPatch.clearModifiedRecursively()
                                                              current.markNew()
                                                              mdiState.closeSubWindows(current)
                                                              setUrlPatchPath(null)
                                                          }
                                                      }
                                                  }
                                              )
                                              CompactDropdownMenuItem(
                                                  text = { Text("Open...") },
                                                  trailingIcon = { ShortcutHint("⌘O") },
                                                  onClick = {
                                                      fileExpanded = false
                                                      val current = FileState.activeDocument
                                                      fun openInto(into: DocumentState) {
                                                          showOpenFileDialog { name, content ->
                                                              loadIntoDocument(
                                                                  mdiState, into, name, content, appDensity
                                                              )
                                                          }
                                                      }
                                                      FileState.requestIfDirty(
                                                          openInNewWindow = {
                                                              openInto(openNewDocument(mdiState, engine, appDensity))
                                                          }
                                                      ) {
                                                          if (current != null) openInto(current)
                                                      }
                                                  }
                                              )
                                               var recentExpanded by remember { mutableStateOf(false) }
                                               val recents = com.syntona.storage.RecentPatchesStore.recentPatches
                                               var recentItemWidth by remember { mutableStateOf(0) }
                                               val localDensity = androidx.compose.ui.platform.LocalDensity.current
                                               val recentItemWidthDp = remember(recentItemWidth, localDensity) {
                                                   with(localDensity) { recentItemWidth.toDp() }
                                               }
                                               Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                                                   recentItemWidth = coordinates.size.width
                                               }) {
                                                   CompactDropdownMenuItem(
                                                       text = { Text("Recent Patches") },
                                                       trailingIcon = { Text("›", fontSize = 16.sp) },
                                                       enabled = recents.isNotEmpty(),
                                                       onClick = { recentExpanded = true },
                                                       modifier = Modifier.pointerInput(Unit) {
                                                           awaitPointerEventScope {
                                                               while (true) {
                                                                   val event = awaitPointerEvent()
                                                                   if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                                                                       if (recents.isNotEmpty()) {
                                                                           recentExpanded = true
                                                                       }
                                                                   }
                                                               }
                                                           }
                                                       }
                                                   )
                                                   if (recents.isNotEmpty()) {
                                                       DropdownMenu(
                                                           expanded = recentExpanded,
                                                           onDismissRequest = { recentExpanded = false },
                                                           offset = androidx.compose.ui.unit.DpOffset(x = recentItemWidthDp, y = (-48).dp)
                                                       ) {
                                                           recents.forEach { path ->
                                                               val fileName = path.substringAfterLast('/').substringAfterLast('\\').ifEmpty { path }
                                                               CompactDropdownMenuItem(
                                                                   text = {
                                                                       Text(
                                                                           text = fileName,
                                                                           maxLines = 1,
                                                                           overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                       )
                                                                   },
                                                                   onClick = {
                                                                       recentExpanded = false
                                                                       fileExpanded = false
                                                                       openRecentPatch(path, mdiState, engine, appDensity)
                                                                   }
                                                               )
                                                           }
                                                       }
                                                   }
                                               }
                                              CompactDropdownMenuItem(
                                                   text = { Text("Add Patch from File...") },
                                                   onClick = {
                                                       fileExpanded = false
                                                       val activeGs = mdiState.activeGraphState
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
                                                                       if (newPatch is PolyPatchModule) {
                                                                           newPatch.clearVoices()
                                                                       }
                                                                       GraphSerializer.deserialize(newPatch.graphState, content, Offset.Zero, appDensity, basePath = path)
                                                                       if (newPatch is PolyPatchModule) {
                                                                           newPatch.ensureDefaultModulesExist()
                                                                       }
                                                                   } finally {
                                                                       newPatch.graphState.isDeserializing = false
                                                                   }
                                                                   if (getPlatform().isWeb) {
                                                                       newPatch.detachFile()
                                                                   }
                                                                   newPatch.clearModifiedRecursively()

                                                                   activeGs.addModule(newPatch, Offset(100f, 100f))
                                                               }
                                                           }
                                                       }
                                                   }
                                               )
                                              HorizontalDivider()
                                              CompactDropdownMenuItem(
                                                  text = { Text("Save") },
                                                  trailingIcon = { ShortcutHint("⌘S") },
                                                  enabled = FileState.isDirty && FileState.currentFilePath != null && !FileState.isResource,
                                                  onClick = {
                                                      fileExpanded = false
                                                      mdiState.syncChildWindowStates(docRoot)
                                                      val modifiedChildren = docRoot.findModifiedReferencedPatches()
                                                      if (modifiedChildren.isNotEmpty()) {
                                                          FileState.patchesToConfirmSave.clear()
                                                          FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                                          FileState.postConfirmationSaveAction = {
                                                              mdiState.syncChildWindowStates(docRoot)
                                                              val path = FileState.currentFilePath
                                                              val json = GraphSerializer.serialize(
                                                                  gs, gs.modules.toList(), appDensity,
                                                                  rootWindowX = docRoot.windowX,
                                                                  rootWindowY = docRoot.windowY,
                                                                  rootWindowWidth = docRoot.windowWidth,
                                                                  rootWindowHeight = docRoot.windowHeight,
                                                                  basePath = path
                                                              )
                                                              if (path != null) {
                                                                  saveFileDirect(path, json)
                                                                  docRoot.filePath = path
                                                                  docRoot.clearModifiedRecursively()
                                                                  FileState.markSaved(path)
                                                              } else {
                                                                  showSaveFileDialog(json, DEFAULT_PATCH_FILENAME) { savedPath ->
                                                                      val finalJson = GraphSerializer.serialize(
                                                                          gs, gs.modules.toList(), appDensity,
                                                                          rootWindowX = docRoot.windowX,
                                                                          rootWindowY = docRoot.windowY,
                                                                          rootWindowWidth = docRoot.windowWidth,
                                                                          rootWindowHeight = docRoot.windowHeight,
                                                                          basePath = savedPath
                                                                      )
                                                                      saveFileDirect(savedPath, finalJson)
                                                                      docRoot.filePath = savedPath
                                                                      docRoot.clearModifiedRecursively()
                                                                      FileState.markSaved(savedPath)
                                                                  }
                                                              }
                                                          }
                                                      } else {
                                                          val path = FileState.currentFilePath
                                                          val json = GraphSerializer.serialize(
                                                              gs, gs.modules.toList(), appDensity,
                                                              rootWindowX = docRoot.windowX,
                                                              rootWindowY = docRoot.windowY,
                                                              rootWindowWidth = docRoot.windowWidth,
                                                              rootWindowHeight = docRoot.windowHeight,
                                                              basePath = path
                                                          )
                                                          if (path != null) {
                                                              saveFileDirect(path, json)
                                                              docRoot.filePath = path
                                                              docRoot.clearModifiedRecursively()
                                                              FileState.markSaved(path)
                                                          } else {
                                                              showSaveFileDialog(json, DEFAULT_PATCH_FILENAME) { savedPath ->
                                                                  val finalJson = GraphSerializer.serialize(
                                                                      gs, gs.modules.toList(), appDensity,
                                                                      rootWindowX = docRoot.windowX,
                                                                      rootWindowY = docRoot.windowY,
                                                                      rootWindowWidth = docRoot.windowWidth,
                                                                      rootWindowHeight = docRoot.windowHeight,
                                                                      basePath = savedPath
                                                                  )
                                                                  saveFileDirect(savedPath, finalJson)
                                                                  docRoot.filePath = savedPath
                                                                  docRoot.clearModifiedRecursively()
                                                                  FileState.markSaved(savedPath)
                                                              }
                                                          }
                                                      }
                                                  }
                                              )
                                              CompactDropdownMenuItem(
                                                   text = { Text("Save As...") },
                                                   trailingIcon = { ShortcutHint("⌘⇧S") },
                                                   onClick = {
                                                       fileExpanded = false
                                                       mdiState.syncChildWindowStates(docRoot)
                                                       val modifiedChildren = docRoot.findModifiedReferencedPatches()
                                                       if (modifiedChildren.isNotEmpty()) {
                                                           FileState.patchesToConfirmSave.clear()
                                                           FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                                           FileState.postConfirmationSaveAction = {
                                                               mdiState.syncChildWindowStates(docRoot)
                                                               showSaveFileDialog(
                                                                   GraphSerializer.serialize(
                                                                       gs, gs.modules.toList(), appDensity,
                                                                       rootWindowX = docRoot.windowX,
                                                                       rootWindowY = docRoot.windowY,
                                                                       rootWindowWidth = docRoot.windowWidth,
                                                                       rootWindowHeight = docRoot.windowHeight
                                                                   ),
                                                                   FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
                                                               ) { savedPath ->
                                                                   val finalJson = GraphSerializer.serialize(
                                                                       gs, gs.modules.toList(), appDensity,
                                                                       rootWindowX = docRoot.windowX,
                                                                       rootWindowY = docRoot.windowY,
                                                                       rootWindowWidth = docRoot.windowWidth,
                                                                       rootWindowHeight = docRoot.windowHeight,
                                                                       basePath = savedPath
                                                                   )
                                                                   saveFileDirect(savedPath, finalJson)
                                                                   docRoot.filePath = savedPath
                                                                   docRoot.clearModifiedRecursively()
                                                                   FileState.markSaved(savedPath)
                                                               }
                                                           }
                                                       } else {
                                                           showSaveFileDialog(
                                                               GraphSerializer.serialize(
                                                                   gs, gs.modules.toList(), appDensity,
                                                                   rootWindowX = docRoot.windowX,
                                                                   rootWindowY = docRoot.windowY,
                                                                   rootWindowWidth = docRoot.windowWidth,
                                                                   rootWindowHeight = docRoot.windowHeight
                                                               ),
                                                               FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
                                                           ) { savedPath ->
                                                               val finalJson = GraphSerializer.serialize(
                                                                   gs, gs.modules.toList(), appDensity,
                                                                   rootWindowX = docRoot.windowX,
                                                                   rootWindowY = docRoot.windowY,
                                                                   rootWindowWidth = docRoot.windowWidth,
                                                                   rootWindowHeight = docRoot.windowHeight,
                                                                   basePath = savedPath
                                                               )
                                                               saveFileDirect(savedPath, finalJson)
                                                               docRoot.filePath = savedPath
                                                               docRoot.clearModifiedRecursively()
                                                               FileState.markSaved(savedPath)
                                                           }
                                                       }
                                                   }
                                               )
                                               if (isSaveAsResourceAvailable()) {
                                                   CompactDropdownMenuItem(
                                                       text = { Text("SaveAsResource...") },
                                                       onClick = {
                                                           fileExpanded = false
                                                           mdiState.syncChildWindowStates(docRoot)
                                                           val modifiedChildren = docRoot.findModifiedReferencedPatches()
                                                           if (modifiedChildren.isNotEmpty()) {
                                                               FileState.patchesToConfirmSave.clear()
                                                               FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                                               FileState.postConfirmationSaveAction = {
                                                                   mdiState.syncChildWindowStates(docRoot)
                                                                   showSaveAsResourceFileDialog(
                                                                       GraphSerializer.serialize(
                                                                           gs, gs.modules.toList(), appDensity,
                                                                           rootWindowX = docRoot.windowX,
                                                                           rootWindowY = docRoot.windowY,
                                                                           rootWindowWidth = docRoot.windowWidth,
                                                                           rootWindowHeight = docRoot.windowHeight
                                                                       ),
                                                                       FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
                                                                   ) { savedPath ->
                                                                       val finalJson = GraphSerializer.serialize(
                                                                           gs, gs.modules.toList(), appDensity,
                                                                           rootWindowX = docRoot.windowX,
                                                                           rootWindowY = docRoot.windowY,
                                                                           rootWindowWidth = docRoot.windowWidth,
                                                                           rootWindowHeight = docRoot.windowHeight,
                                                                           basePath = savedPath
                                                                       )
                                                                       saveFileDirect(savedPath, finalJson)
                                                                       docRoot.filePath = savedPath
                                                                       docRoot.clearModifiedRecursively()
                                                                       FileState.markSaved(savedPath)
                                                                       mdiState.windows.firstOrNull { it.id == docRoot }?.let { win ->
                                                                           win.title = FileState.activeDocument?.displayName ?: docRoot.name
                                                                       }
                                                                   }
                                                               }
                                                           } else {
                                                               showSaveAsResourceFileDialog(
                                                                   GraphSerializer.serialize(
                                                                       gs, gs.modules.toList(), appDensity,
                                                                       rootWindowX = docRoot.windowX,
                                                                       rootWindowY = docRoot.windowY,
                                                                       rootWindowWidth = docRoot.windowWidth,
                                                                       rootWindowHeight = docRoot.windowHeight
                                                                   ),
                                                                   FileState.currentFileName ?: DEFAULT_PATCH_FILENAME
                                                               ) { savedPath ->
                                                                   val finalJson = GraphSerializer.serialize(
                                                                       gs, gs.modules.toList(), appDensity,
                                                                       rootWindowX = docRoot.windowX,
                                                                       rootWindowY = docRoot.windowY,
                                                                       rootWindowWidth = docRoot.windowWidth,
                                                                       rootWindowHeight = docRoot.windowHeight,
                                                                       basePath = savedPath
                                                                   )
                                                                   saveFileDirect(savedPath, finalJson)
                                                                   docRoot.filePath = savedPath
                                                                   docRoot.clearModifiedRecursively()
                                                                   FileState.markSaved(savedPath)
                                                                   mdiState.windows.firstOrNull { it.id == docRoot }?.let { win ->
                                                                       win.title = FileState.activeDocument?.displayName ?: docRoot.name
                                                                   }
                                                               }
                                                           }
                                                       }
                                                   )
                                               }
                                              CompactDropdownMenuItem(
                                                  text = { Text("Save Patch As...") },
                                                  onClick = {
                                                      fileExpanded = false
                                                      val target = mdiState.activeGraphState?.parentPatch
                                                      if (target != null) {
                                                          val json = GraphSerializer.serializePatch(target, appDensity, target.filePath)
                                                          val defaultName = target.filePath?.let { p ->
                                                              p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
                                                          } ?: "${target.name}$SYNTONA_FILE_SUFFIX"
                                                          showSaveFileDialog(json, defaultName) { path ->
                                                              val finalJson = GraphSerializer.serializePatch(target, appDensity, path)
                                                              saveFileDirect(path, finalJson)
                                                              val rPath = resourceRelativePath(path)
                                                              if (rPath != null) {
                                                                  if (rPath.startsWith("instruments/")) {
                                                                      val instrumentName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                                                      com.syntona.dataflow.registry.InstrumentRegistry.registerInstrument(instrumentName, finalJson, path)
                                                                  } else if (rPath.startsWith("sounds/")) {
                                                                      val soundName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                                                      com.syntona.dataflow.registry.SoundRegistry.registerSound(soundName, finalJson, path)
                                                                  }
                                                              }
                                                              if (target === docRoot) {
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
                                              )
                                              if (isSaveAsResourceAvailable()) {
                                                  CompactDropdownMenuItem(
                                                      text = { Text("Save Patch as Resource...") },
                                                      onClick = {
                                                          fileExpanded = false
                                                          val target = mdiState.activeGraphState?.parentPatch
                                                          if (target != null) {
                                                              val json = GraphSerializer.serializePatch(target, appDensity, target.filePath)
                                                              val defaultName = target.filePath?.let { p ->
                                                                  p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
                                                              } ?: "${target.name}$SYNTONA_FILE_SUFFIX"
                                                              showSaveAsResourceFileDialog(json, defaultName) { path ->
                                                                  val finalJson = GraphSerializer.serializePatch(target, appDensity, path)
                                                                  saveFileDirect(path, finalJson)
                                                                  target.filePath = path
                                                                  target.clearModifiedRecursively()
                                                                  val rPath = resourceRelativePath(path)
                                                                  if (rPath != null) {
                                                                      if (rPath.startsWith("instruments/")) {
                                                                          val instrumentName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                                                          com.syntona.dataflow.registry.InstrumentRegistry.registerInstrument(instrumentName, finalJson, path)
                                                                      } else if (rPath.startsWith("sounds/")) {
                                                                          val soundName = rPath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
                                                                          com.syntona.dataflow.registry.SoundRegistry.registerSound(soundName, finalJson, path)
                                                                      }
                                                                  }
                                                                  if (target === docRoot) {
                                                                      FileState.markSaved(path)
                                                                  }
                                                              }
                                                          }
                                                      }
                                                  )
                                              }
                                              CompactDropdownMenuItem(
                                                    text = { Text("Detach File from Patch") },
                                                    enabled = mdiState.activeGraphState?.parentPatch?.let { it.filePath != null || it.resourcePath != null } == true,
                                                    onClick = {
                                                        fileExpanded = false
                                                        val target = mdiState.activeGraphState?.parentPatch
                                                        if (target != null) {
                                                            target.detachFile()
                                                        }
                                                    }
                                                )
                                               val activeExportPatch = mdiState.activeGraphState?.parentPatch
                                               val canExport = activeExportPatch != null && !activeExportPatch.isRoot
                                               var exportSubmenuExpanded by remember { mutableStateOf(false) }
                                               Box {
                                                   CompactDropdownMenuItem(
                                                       text = { Text("Export") },
                                                       trailingIcon = { ChevronRightIcon(color = Color.Gray, size = 10.dp) },
                                                       enabled = canExport,
                                                       onClick = { exportSubmenuExpanded = true }
                                                   )
                                                   DropdownMenu(
                                                       expanded = exportSubmenuExpanded,
                                                       onDismissRequest = { exportSubmenuExpanded = false }
                                                   ) {
                                                       CompactDropdownMenuItem(
                                                           text = { Text("Source code for KSyn...") },
                                                           onClick = {
                                                               exportSubmenuExpanded = false
                                                               fileExpanded = false
                                                               val target = mdiState.activeGraphState?.parentPatch ?: return@CompactDropdownMenuItem
                                                               val onProceed = {
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
                                                               val warning = com.syntona.export.SourceExporter.exportWarningMessage(target, forJSyn = false)
                                                               if (warning != null) {
                                                                   exportWarningMessage = warning
                                                                   onExportWarningProceed = onProceed
                                                               } else {
                                                                   onProceed()
                                                               }
                                                           }
                                                       )
                                                       CompactDropdownMenuItem(
                                                           text = { Text("Source code for JSyn...") },
                                                           onClick = {
                                                               exportSubmenuExpanded = false
                                                               fileExpanded = false
                                                               val target = mdiState.activeGraphState?.parentPatch ?: return@CompactDropdownMenuItem
                                                               val onProceed = {
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
                                                               val warning = com.syntona.export.SourceExporter.exportWarningMessage(target, forJSyn = true)
                                                               if (warning != null) {
                                                                   exportWarningMessage = warning
                                                                   onExportWarningProceed = onProceed
                                                               } else {
                                                                   onProceed()
                                                               }
                                                           }
                                                       )
                                                   }
                                               }
                                               if (isShareUrlAvailable()) {
                                                   CompactDropdownMenuItem(
                                                       text = { Text("Share Piece as URL...") },
                                                       onClick = {
                                                           fileExpanded = false
                                                           performShareUrl()
                                                       }
                                                   )
                                               }
                                               HorizontalDivider()
                                              CompactDropdownMenuItem(
                                                  text = { Text("Settings...") },
                                                  onClick = {
                                                      fileExpanded = false
                                                      com.syntona.ui.SettingsDialogState.open()
                                                  }
                                              )
                                         }
                                     }
                                 }
                                if (showEditMenuInToolbar) {
                                    val activeGs = mdiState.activeGraphState
                                    val editHandler = mdiState.activeEditHandler
                                    val hasSelection = activeGs?.selectedModules?.isNotEmpty() == true
                                    var editExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        Button(onClick = { editExpanded = true }) { Text("Edit") }
                                        DropdownMenu(
                                            expanded = editExpanded,
                                            onDismissRequest = { editExpanded = false }
                                        ) {
                                            CompactDropdownMenuItem(
                                                text = { Text("Undo") },
                                                trailingIcon = { ShortcutHint("⌘Z") },
                                                onClick = {
                                                    mdiState.activeGraphState?.engine?.undo()
                                                    editExpanded = false
                                                }
                                            )
                                            CompactDropdownMenuItem(
                                                text = { Text("Redo") },
                                                trailingIcon = { ShortcutHint("⌘⇧Z") },
                                                onClick = {
                                                    mdiState.activeGraphState?.engine?.redo()
                                                    editExpanded = false
                                                }
                                            )
                                            HorizontalDivider()
                                            CompactDropdownMenuItem(
                                                text = { Text("Cut") },
                                                trailingIcon = { ShortcutHint("⌘X") },
                                                enabled = EditActions.canCut(mdiState),
                                                onClick = {
                                                    EditActions.cut(mdiState, appDensity)
                                                    editExpanded = false
                                                }
                                            )
                                            CompactDropdownMenuItem(
                                                text = { Text("Copy") },
                                                trailingIcon = { ShortcutHint("⌘C") },
                                                enabled = EditActions.canCopy(mdiState),
                                                onClick = {
                                                    EditActions.copy(mdiState, appDensity)
                                                    editExpanded = false
                                                }
                                            )
                                            CompactDropdownMenuItem(
                                                text = { Text("Paste") },
                                                trailingIcon = { ShortcutHint("⌘V") },
                                                enabled = EditActions.canPaste(mdiState),
                                                onClick = {
                                                    EditActions.paste(mdiState, appDensity)
                                                    editExpanded = false
                                                }
                                            )
                                            HorizontalDivider()
                                            CompactDropdownMenuItem(
                                                text = { Text("Make a Patch") },
                                                enabled = EditActions.canMakePatch(mdiState),
                                                onClick = {
                                                    EditActions.makePatch(mdiState, appDensity)
                                                    editExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (showFileMenuInToolbar) {
                                    var windowsExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        Button(onClick = { windowsExpanded = true }) { Text("Window") }
                                        DropdownMenu(
                                            expanded = windowsExpanded,
                                            onDismissRequest = { windowsExpanded = false }
                                        ) {
                                            WindowMenu.build(mdiState).forEach { entry ->
                                                CompactDropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = entry.menuText,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = if (entry.isActive) {
                                                                FontWeight.Bold
                                                            } else {
                                                                FontWeight.Normal
                                                            }
                                                        )
                                                    },
                                                    onClick = {
                                                        windowsExpanded = false
                                                        WindowMenu.show(mdiState, entry.window)
                                                    }
                                                )
                                            }
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
                                                 CompactDropdownMenuItem(
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
                                                     RenderDropdownMenuNode(examplesTree, mdiState, rootPatch, scope, onClose = {
                                                         examplesExpanded = false
                                                         helpExpanded = false
                                                     })
                                                 }
                                             }
                                             Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                                                 itemWidth = coordinates.size.width
                                             }) {
                                                 CompactDropdownMenuItem(
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
                                                     RenderDropdownMenuNode(tutorialsTree, mdiState, rootPatch, scope, onClose = {
                                                         tutorialsExpanded = false
                                                         helpExpanded = false
                                                     })
                                                 }
                                             }
                                             CompactDropdownMenuItem(
                                                 text = { Text("Join Syntona Server on Discord...") },
                                                 onClick = {
                                                     helpExpanded = false
                                                     openUrlInBrowser("https://discord.gg/Q5tAD5bqw")
                                                 }
                                             )
                                             CompactDropdownMenuItem(
                                                 text = { Text("Discuss Syntona on Discord...") },
                                                 onClick = {
                                                     helpExpanded = false
                                                     openUrlInBrowser("https://discord.com/channels/1532908817677942846/1532924422321864855")
                                                 }
                                             )
                                             CompactDropdownMenuItem(
                                                 text = { Text("About Syntona...") },
                                                 onClick = {
                                                     helpExpanded = false
                                                     com.syntona.ui.AboutDialogState.open()
                                                 }
                                             )
                                         }
                                     }
                                 }

                                 if (showFileMenuInToolbar) {
                                     Spacer(modifier = Modifier.width(4.dp))
                                     Box(
                                         modifier = Modifier
                                             .width(1.dp)
                                             .fillMaxHeight(0.6f)
                                             .background(Color.Gray.copy(alpha = 0.5f))
                                     )
                                     Spacer(modifier = Modifier.width(4.dp))
                                 }

                                 // Toggle wires on top of or behind modules
                                 val isWiresOnTop = GraphViewSettings.wiresOnTop
                                 TooltipArea(
                                     text = if (isWiresOnTop) "Wires Now on Top: Click to draw wires behind modules"
                                     else "Wires Now Behind: Click to draw wires on top of modules",
                                     alignment = Alignment.BottomStart
                                 ) {
                                     CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                         Button(
                                             onClick = { GraphViewSettings.toggleWiresOnTop() },
                                             modifier = Modifier.size(width = 34.dp, height = 30.dp),
                                             shape = RoundedCornerShape(4.dp),
                                             colors = ButtonDefaults.buttonColors(
                                                 containerColor = if (isWiresOnTop) Color(0xFF2D4356) else Color(0xFF2A2A2A),
                                                 contentColor = if (isWiresOnTop) Color(0xFF80D8FF) else Color(0xFFB0BEC5)
                                             ),
                                             border = BorderStroke(
                                                 width = 1.dp,
                                                 color = if (isWiresOnTop) Color(0xFF5C89AF) else Color(0xFF444444)
                                             ),
                                             contentPadding = PaddingValues(0.dp)
                                         ) {
                                             WireLayerIcon(
                                                 wiresOnTop = isWiresOnTop,
                                                 color = if (isWiresOnTop) Color(0xFFECEFF1) else Color(0xFFB0BEC5),
                                                 wireColor = if (isWiresOnTop) Color(0xFF44DD44) else Color(0xFF81C784),
                                                 size = 18.dp
                                             )
                                         }
                                     }
                                 }

                                 Spacer(modifier = Modifier.width(4.dp))
                                 Box(
                                     modifier = Modifier
                                         .width(1.dp)
                                         .fillMaxHeight(0.6f)
                                         .background(Color.Gray.copy(alpha = 0.5f))
                                 )
                                 Spacer(modifier = Modifier.width(4.dp))

                                 var cpuUsage by remember { mutableStateOf(0.0) }
                                 LaunchedEffect(Unit) {
                                     while (true) {
                                         cpuUsage = SynthKSyn.synth.usage
                                         delay(100)
                                     }
                                 }
                                 Row(
                                     verticalAlignment = Alignment.CenterVertically,
                                     modifier = Modifier.padding(horizontal = 4.dp)
                                 ) {
                                     Text(
                                         text = "CPU: ${(cpuUsage * 100).toInt().toString().padStart(2, '0')}%",
                                         color = Color.White,
                                         style = MaterialTheme.typography.bodySmall.copy(
                                             fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                         ),
                                         modifier = Modifier.padding(end = 4.dp).width(65.dp)
                                     )
                                     Box(
                                         modifier = Modifier
                                             .width(60.dp)
                                             .height(10.dp)
                                             .background(Color(0xFF2E2E2E), RoundedCornerShape(2.dp))
                                             .border(0.5.dp, Color.Gray, RoundedCornerShape(2.dp))
                                     ) {
                                         val fillWidth = (60.dp * cpuUsage.coerceIn(0.0, 1.0).toFloat())
                                         val barColor = when {
                                             cpuUsage >= 0.8 -> Color.Red
                                             cpuUsage >= 0.6 -> Color(0xFFFFA500)
                                             else -> Color.Green
                                         }
                                         Box(
                                             modifier = Modifier
                                                 .fillMaxHeight()
                                                 .width(fillWidth)
                                                 .background(barColor, RoundedCornerShape(2.dp))
                                         )
                                     }
                                 }
                                 Spacer(modifier = Modifier.width(4.dp))
                                 Box(
                                     modifier = Modifier
                                         .width(1.dp)
                                         .fillMaxHeight(0.6f)
                                         .background(Color.Gray.copy(alpha = 0.5f))
                                 )
                                 Spacer(modifier = Modifier.width(4.dp))

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
                                            DropdownArrow(color = Color.Gray, size = 8.dp)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        listOf(1, 2, 3, 5, 10, 20, 60).forEach { mins ->
                                            CompactDropdownMenuItem(
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
                    if (FileState.patchesToConfirmSave.isNotEmpty()) {
                        val patchToConfirm = FileState.patchesToConfirmSave.first()
                        val targetPath = patchToConfirm.resourcePath ?: patchToConfirm.filePath ?: ""
                        AlertDialog(
                            onDismissRequest = {
                                FileState.patchesToConfirmSave.clear()
                                FileState.postConfirmationSaveAction = null
                            },
                            title = { Text("Save Referenced Patch") },
                            text = { Text("The referenced patch '${patchToConfirm.name}' has been modified. Do you want to save its changes back to '$targetPath' or detach it?") },
                            confirmButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        val rPath = patchToConfirm.resourcePath
                                        val fPath = patchToConfirm.filePath
                                        val childJson = GraphSerializer.serializePatch(patchToConfirm, appDensity, fPath)
                                        if (rPath != null) {
                                            saveResourceFileDirect(rPath, childJson)
                                        } else if (fPath != null) {
                                            saveFileDirect(fPath, childJson)
                                        }
                                        patchToConfirm.clearModifiedRecursively()
                                        FileState.patchesToConfirmSave.removeAt(0)
                                        if (FileState.patchesToConfirmSave.isEmpty()) {
                                            FileState.postConfirmationSaveAction?.invoke()
                                            FileState.postConfirmationSaveAction = null
                                        }
                                    }) { Text("Save") }
                                    Button(onClick = {
                                        patchToConfirm.filePath = null
                                        patchToConfirm.resourcePath = null
                                        patchToConfirm.clearModifiedRecursively()
                                        FileState.patchesToConfirmSave.removeAt(0)
                                        if (FileState.patchesToConfirmSave.isEmpty()) {
                                            FileState.postConfirmationSaveAction?.invoke()
                                            FileState.postConfirmationSaveAction = null
                                        }
                                    }) { Text("Detach") }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    FileState.patchesToConfirmSave.clear()
                                    FileState.postConfirmationSaveAction = null
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (FileState.pendingAction != null) {
                        val canOpenInNewWindow = FileState.pendingOpenInNewWindow != null
                        AlertDialog(
                            onDismissRequest = { FileState.clearPendingAction() },
                            title = { Text("Unsaved Changes") },
                            text = {
                                Text(
                                    if (canOpenInNewWindow) {
                                        "This patch has unsaved changes. Discard them, or leave " +
                                            "them alone and open the other patch in a new window?"
                                    } else {
                                        "Discard unsaved changes?"
                                    }
                                )
                            },
                            confirmButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (canOpenInNewWindow) {
                                        Button(onClick = { FileState.confirmPendingInNewWindow() }) {
                                            Text("Open in New Window")
                                        }
                                    }
                                    Button(onClick = { FileState.confirmPendingAction() }) {
                                        Text("Discard")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { FileState.clearPendingAction() }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                    FileState.loadingProblemsReport?.let { report ->
                        AlertDialog(
                            onDismissRequest = { FileState.loadingProblemsReport = null },
                            title = { Text("Load Problems Report") },
                            text = { Text(report) },
                            confirmButton = {
                                Button(onClick = { FileState.loadingProblemsReport = null }) { Text("OK") }
                            }
                        )
                    }
                    exportWarningMessage?.let { warning ->
                        AlertDialog(
                            onDismissRequest = {
                                exportWarningMessage = null
                                onExportWarningProceed = null
                            },
                            title = { Text("Export Warning") },
                            text = { Text(warning) },
                            confirmButton = {
                                Button(onClick = {
                                    val proceed = onExportWarningProceed
                                    exportWarningMessage = null
                                    onExportWarningProceed = null
                                    proceed?.invoke()
                                }) { Text("Proceed") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    exportWarningMessage = null
                                    onExportWarningProceed = null
                                }) { Text("Cancel") }
                            }
                        )
                    }
                    if (com.syntona.ui.SettingsDialogState.isVisible) {
                        com.syntona.ui.SettingsDialog(
                            onDismiss = { com.syntona.ui.SettingsDialogState.close() }
                        )
                    }
                    if (com.syntona.ui.AboutDialogState.isVisible) {
                        com.syntona.ui.AboutDialog(
                            onDismiss = { com.syntona.ui.AboutDialogState.close() }
                        )
                    }
                    com.syntona.dataflow.modules.PolyPatchPrompter.activePromptModule?.let { module ->
                        var voiceCountText by remember { mutableStateOf("8") }
                        var isStereo by remember { mutableStateOf(false) }
                        AlertDialog(
                            onDismissRequest = {
                                val graph = com.syntona.dataflow.modules.PolyPatchPrompter.activePromptGraphState
                                if (graph != null) {
                                    graph.removeModulesDirectly(listOf(module))
                                    graph.markChanged()
                                }
                                com.syntona.dataflow.modules.PolyPatchPrompter.activePromptModule = null
                                com.syntona.dataflow.modules.PolyPatchPrompter.activePromptGraphState = null
                            },
                            title = { Text("Polyphonic Patch Voices") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Enter the maximum number of polyphonic voices:")
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = voiceCountText,
                                        onValueChange = { voiceCountText = it.filter { c -> c.isDigit() } },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                            .padding(8.dp)
                                            .onKeyEvent { true },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black)
                                    )
                                    Text("Output:")
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { isStereo = false }
                                        ) {
                                            RadioButton(
                                                selected = !isStereo,
                                                onClick = { isStereo = false }
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Mono")
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { isStereo = true }
                                        ) {
                                            RadioButton(
                                                selected = isStereo,
                                                onClick = { isStereo = true }
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Stereo")
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val count = voiceCountText.toIntOrNull() ?: 8
                                    module.initializeVoices(count.coerceIn(1, 32), isStereo = isStereo)
                                    com.syntona.dataflow.modules.PolyPatchPrompter.activePromptModule = null
                                    com.syntona.dataflow.modules.PolyPatchPrompter.activePromptGraphState = null
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    val graph = com.syntona.dataflow.modules.PolyPatchPrompter.activePromptGraphState
                                    if (graph != null) {
                                        graph.removeModulesDirectly(listOf(module))
                                        graph.markChanged()
                                    }
                                    com.syntona.dataflow.modules.PolyPatchPrompter.activePromptModule = null
                                    com.syntona.dataflow.modules.PolyPatchPrompter.activePromptGraphState = null
                                }) { Text("Cancel") }
                            }
                        )
                    }
                    shareNotice?.let { notice ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 48.dp, end = 16.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Surface(
                                color = if (shareNoticeIsProblem) Color(0xFFB35300) else Color(0xFF2E7D32),
                                contentColor = Color.White,
                                shape = RoundedCornerShape(6.dp),
                                shadowElevation = 6.dp
                            ) {
                                Text(
                                    text = notice,
                                    modifier = Modifier
                                        .widthIn(max = 320.dp)
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    } // end outer Box
                }
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
                val displayName = segment.removeSuffix(".json").removeSuffix(SYNTONA_FILE_SUFFIX)
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
    rootPatch: PatchModule,
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
            CompactDropdownMenuItem(
                text = { Text(child.name) },
                onClick = {
                    onClose()
                    // Examples/Tutorials replace the whole document, so they target the root of
                    // the frontmost one rather than whichever sub-patch window is active.
                    val current = FileState.activeDocument
                    val engine = current?.rootPatch?.graphState?.engine
                    if (current != null && engine != null) {
                        fun load(into: DocumentState) {
                            scope.launch {
                                try {
                                    val bytes = com.syntona.SharedResources.readBytes("files/$path")
                                    loadIntoDocument(
                                        state, into, path, bytes.decodeToString(), density
                                    )
                                } catch (e: Exception) {
                                    println("Failed to load: ${e.message}")
                                }
                            }
                        }
                        FileState.requestIfDirty(
                            openInNewWindow = { load(openNewDocument(state, engine, density)) }
                        ) {
                            load(current)
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
                CompactDropdownMenuItem(
                    text = { Text(child.name) },
                    trailingIcon = { ChevronRightIcon(color = Color.Gray, size = 10.dp) },
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
                    RenderDropdownMenuNode(child, state, rootPatch, scope, onClose = {
                        isExpanded = false
                        onClose()
                    })
                }
            }
        }
    }
}

