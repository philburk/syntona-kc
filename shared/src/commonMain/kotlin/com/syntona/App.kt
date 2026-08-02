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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
                        module.windowX = window.position.x / density
                        module.windowY = window.position.y / density
                        module.windowWidth = window.width.value
                        module.windowHeight = window.height.value
                    }
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
    GraphSerializer.deserialize(graphState, content, density = density)
    restoreWindowStates(mdiState, graphState, density)
    restoreRootWindowGeometry(mdiState, document.rootPatch, content, density)
    document.markSaved(path)
    document.rootPatch.clearModifiedRecursively()
    val win = mdiState.windows.firstOrNull { it.id == document.rootPatch }
    if (win != null) {
        win.title = document.displayName
    }
    graphState.kickLoadKickModules()
    if (document.isResource) {
        setUrlPatchPath(path)
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
            val rawUrlPath = getUrlPatchPath()
            if (!rawUrlPath.isNullOrEmpty()) {
                val filesIndex = com.syntona.SharedResources.getFilesIndex().toSet()
                val validatedPath = sanitizeAndValidateResourcePath(rawUrlPath, filesIndex)
                if (validatedPath != null) {
                    try {
                        val bytes = com.syntona.SharedResources.readBytes("files/$validatedPath")
                        val activeDoc = FileState.activeDocument ?: FileState.documents.firstOrNull()
                        if (activeDoc != null) {
                            loadIntoDocument(mdiState, activeDoc, validatedPath, bytes.decodeToString(), appDensity)
                        }
                    } catch (e: Exception) {
                        println("Failed to load initial URL patch: ${e.message}")
                    }
                }
            }
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
                                            mdiState.activeGraphState?.deleteSelectedModules()
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
                                                                       GraphSerializer.deserialize(newPatch.graphState, content, Offset.Zero, appDensity)
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
                                               )
                                              HorizontalDivider()
                                              CompactDropdownMenuItem(
                                                  text = { Text("Save") },
                                                  trailingIcon = { ShortcutHint("⌘S") },
                                                  enabled = FileState.isDirty && FileState.currentFilePath != null && !FileState.isResource,
                                                  onClick = {
                                                      fileExpanded = false
                                                      val modifiedChildren = docRoot.findModifiedReferencedPatches()
                                                      if (modifiedChildren.isNotEmpty()) {
                                                          FileState.patchesToConfirmSave.clear()
                                                          FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                                          FileState.postConfirmationSaveAction = {
                                                              val json = GraphSerializer.serialize(
                                                                  gs, gs.modules.toList(), appDensity,
                                                                  rootWindowX = docRoot.windowX,
                                                                  rootWindowY = docRoot.windowY,
                                                                  rootWindowWidth = docRoot.windowWidth,
                                                                  rootWindowHeight = docRoot.windowHeight
                                                              )
                                                              val path = FileState.currentFilePath
                                                              if (path != null) {
                                                                  saveFileDirect(path, json)
                                                                  docRoot.filePath = path
                                                                  docRoot.clearModifiedRecursively()
                                                                  FileState.markSaved(path)
                                                              } else {
                                                                  showSaveFileDialog(json, DEFAULT_PATCH_FILENAME) { savedPath ->
                                                                      docRoot.filePath = savedPath
                                                                      docRoot.clearModifiedRecursively()
                                                                      FileState.markSaved(savedPath)
                                                                  }
                                                              }
                                                          }
                                                      } else {
                                                          val json = GraphSerializer.serialize(
                                                              gs, gs.modules.toList(), appDensity,
                                                              rootWindowX = docRoot.windowX,
                                                              rootWindowY = docRoot.windowY,
                                                              rootWindowWidth = docRoot.windowWidth,
                                                              rootWindowHeight = docRoot.windowHeight
                                                          )
                                                          val path = FileState.currentFilePath
                                                          if (path != null) {
                                                              saveFileDirect(path, json)
                                                              docRoot.filePath = path
                                                              docRoot.clearModifiedRecursively()
                                                              FileState.markSaved(path)
                                                          } else {
                                                              showSaveFileDialog(json, DEFAULT_PATCH_FILENAME) { savedPath ->
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
                                                       val modifiedChildren = docRoot.findModifiedReferencedPatches()
                                                       if (modifiedChildren.isNotEmpty()) {
                                                           FileState.patchesToConfirmSave.clear()
                                                           FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                                           FileState.postConfirmationSaveAction = {
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
                                                           val modifiedChildren = docRoot.findModifiedReferencedPatches()
                                                           if (modifiedChildren.isNotEmpty()) {
                                                               FileState.patchesToConfirmSave.clear()
                                                               FileState.patchesToConfirmSave.addAll(modifiedChildren)
                                                               FileState.postConfirmationSaveAction = {
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
                                                          val json = GraphSerializer.serializePatch(target, appDensity)
                                                          val defaultName = target.filePath?.let { p ->
                                                              p.substringAfterLast('/').substringAfterLast('\\').ifEmpty { p }
                                                          } ?: "${target.name}$SYNTONA_FILE_SUFFIX"
                                                          showSaveFileDialog(json, defaultName) { path ->
                                                              target.filePath = path
                                                              target.clearModifiedRecursively()
                                                              if (target === docRoot) {
                                                                  FileState.markSaved(path)
                                                              }
                                                          }
                                                      }
                                                  }
                                              )
                                              CompactDropdownMenuItem(
                                                  text = { Text("Detach File from Patch") },
                                                  enabled = mdiState.activeGraphState?.parentPatch?.filePath != null,
                                                  onClick = {
                                                      fileExpanded = false
                                                      val target = mdiState.activeGraphState?.parentPatch
                                                      if (target != null) {
                                                          target.filePath = null
                                                      }
                                                  }
                                              )
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
                                                 text = { Text("Discuss on Discord...") },
                                                 onClick = {
                                                     helpExpanded = false
                                                     openUrlInBrowser("https://discord.gg/Q5tAD5bqw")
                                                 }
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
                                            Text("▼", style = MaterialTheme.typography.labelSmall)
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
                        AlertDialog(
                            onDismissRequest = {
                                FileState.patchesToConfirmSave.clear()
                                FileState.postConfirmationSaveAction = null
                            },
                            title = { Text("Save Referenced Patch") },
                            text = { Text("The referenced patch '${patchToConfirm.name}' has been modified. Do you want to save its changes back to '${patchToConfirm.filePath}' or detach it?") },
                            confirmButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        val childJson = GraphSerializer.serializePatch(patchToConfirm, appDensity)
                                        saveFileDirect(patchToConfirm.filePath!!, childJson)
                                        patchToConfirm.clearModifiedRecursively()
                                        FileState.patchesToConfirmSave.removeAt(0)
                                        if (FileState.patchesToConfirmSave.isEmpty()) {
                                            FileState.postConfirmationSaveAction?.invoke()
                                            FileState.postConfirmationSaveAction = null
                                        }
                                    }) { Text("Save") }
                                    Button(onClick = {
                                        patchToConfirm.filePath = null
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
                    if (com.syntona.ui.SettingsDialogState.isVisible) {
                        com.syntona.ui.SettingsDialog(
                            onDismiss = { com.syntona.ui.SettingsDialogState.close() }
                        )
                    }
                    com.syntona.dataflow.modules.PolyPatchPrompter.activePromptModule?.let { module ->
                        var voiceCountText by remember { mutableStateOf("8") }
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
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Enter the maximum number of polyphonic voices:")
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = voiceCountText,
                                        onValueChange = { voiceCountText = it.filter { c -> c.isDigit() } },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                            .padding(8.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black)
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val count = voiceCountText.toIntOrNull() ?: 8
                                    module.initializeVoices(count.coerceIn(1, 32))
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
                    RenderDropdownMenuNode(child, state, rootPatch, scope, onClose = {
                        isExpanded = false
                        onClose()
                    })
                }
            }
        }
    }
}

