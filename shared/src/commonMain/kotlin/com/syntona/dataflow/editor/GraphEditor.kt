package com.syntona.dataflow.editor

import com.syntona.ui.DropdownArrow
import com.syntona.ui.LightbulbIcon
import com.syntona.ui.VerticalEllipsisIcon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.syntona.dataflow.modules.ArmModule
import com.syntona.dataflow.modules.RecorderModule
import com.syntona.util.formatMinutesSecondsMillis
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import com.syntona.dataflow.modules.EquationModule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.KickJackCommand
import com.syntona.dataflow.engine.SetValueCommand
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.*
import com.softsynth.ksyn.compose.Oscilloscope
import com.syntona.dataflow.modules.NumberModule
import com.syntona.dataflow.modules.ScaleModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.MixerModule
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.CommandModule
import com.syntona.dataflow.modules.FloatSampleModule
import com.syntona.dataflow.modules.SamplePlayerModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.ScopeModule
import com.syntona.dataflow.modules.ShowModule
import com.syntona.dataflow.modules.GlowModule
import com.syntona.dataflow.modules.FlashModule
import com.syntona.dataflow.modules.SendModule
import com.syntona.dataflow.modules.ReceiveModule
import com.syntona.dataflow.modules.CommentModule
import com.syntona.dataflow.modules.KeyboardModule
import com.syntona.dataflow.modules.SEMITONES_PER_OCTAVE
import com.syntona.dataflow.modules.currentAmplitude
import com.softsynth.ksyn.compose.BlackWhiteKeyboard
import com.syntona.dataflow.modules.SequenceModule
import com.syntona.dataflow.modules.ModuloSelectModule
import com.syntona.dataflow.modules.SegmentedEnvelopeModule
import com.syntona.dataflow.modules.LinkModule
import com.syntona.dataflow.modules.KeyMatchModule
import com.syntona.dataflow.modules.MatchControlModule
import com.syntona.dataflow.modules.BeatClockModule
import com.syntona.dataflow.modules.TimelineModule
import com.syntona.dataflow.modules.TimelineTrigger
import com.syntona.dataflow.modules.KeyNotesModule
import com.syntona.dataflow.modules.PianoRollModule
import com.syntona.dataflow.modules.KickModule
import com.syntona.dataflow.modules.MidiInModule
import com.syntona.dataflow.modules.MidiOutModule
import com.syntona.dataflow.modules.LoadKickModule
import com.syntona.dataflow.modules.ShowManyModule
import com.softsynth.ksyn.compose.SegmentedEnvelopeEditor
import com.syntona.dataflow.model.cleanKotlinIdentifier
import com.syntona.dataflow.editor.FileState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun GraphEditor(state: GraphState, modifier: Modifier = Modifier) {
    val colors = LocalGraphColors.current
    val mdiState = LocalMdiState.current
    val focusRequester = remember { FocusRequester() }
    state.requestFocus = { focusRequester.requestFocus() }
    val editingModule = state.editingModule
    LaunchedEffect(editingModule) {
        if (editingModule == null) {
            focusRequester.requestFocus()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        val parentPatch = state.parentPatch
        if (parentPatch != null && !parentPatch.isRoot) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.moduleBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Patch Name:",
                    color = if (colors.background == Color(0xFF1E1E1E)) Color.LightGray else Color.DarkGray,
                    fontSize = 12.sp
                )
                var tempName by remember(parentPatch) { mutableStateOf(parentPatch.name) }
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .background(colors.background, RoundedCornerShape(4.dp))
                        .border(1.dp, colors.moduleBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .onKeyEvent { true }
                ) {
                    BasicTextField(
                        value = tempName,
                        onValueChange = { input ->
                            val filtered = cleanKotlinIdentifier(input)
                            tempName = filtered
                            if (filtered.isNotEmpty()) {
                                parentPatch.name = filtered
                                mdiState.windows.find { it.id === parentPatch }?.let { win ->
                                    win.title = filtered
                                }
                                FileState.markDirty()
                            }
                        },
                        textStyle = TextStyle(
                            color = if (colors.background == Color(0xFF1E1E1E)) Color.White else Color.Black,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(if (colors.background == Color(0xFF1E1E1E)) Color.White else Color.Black),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            HorizontalDivider(color = colors.gridLines, thickness = 1.dp)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(colors.background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (state.editingModule != null) return@onKeyEvent false
                var handled = state.onKeyEvent(keyEvent)
                if (!handled) {
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete)) {
                        state.deleteSelectedModules()
                        handled = true
                    } else if (keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.Z &&
                        (keyEvent.isMetaPressed || keyEvent.isCtrlPressed)) {
                        if (keyEvent.isShiftPressed) state.engine?.redo()
                        else state.engine?.undo()
                        handled = true
                    }
                }
                handled
            }
            .onGloballyPositioned { state.updateRootOffset(it.positionInRoot()) }
            .pointerInput(state) {
                detectTapGestures {
                    focusRequester.requestFocus()
                    mdiState.activeGraphState = state
                    // Tap on background clears selection
                    state.clearSelection()
                }
            }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    state.selectionRectStart = down.position
                    state.selectionRectEnd = down.position
                    drag(down.id) { change ->
                        change.consume()
                        state.selectionRectEnd = change.position
                    }
                    val rectStart = state.selectionRectStart
                    val rectEnd = state.selectionRectEnd
                    if (rectStart != null && rectEnd != null) {
                        state.selectModulesInRect(rectStart, rectEnd, density)
                    }
                    state.selectionRectStart = null
                    state.selectionRectEnd = null
                }
            }
        ) {
            val isRootGraph = parentPatch == null || parentPatch.isRoot
            if (isRootGraph && !FileState.isDirty && FileState.currentFilePath == null && state.modules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .background(Color(0x11FFFFFF), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        LightbulbIcon(color = Color(0xFFFFD54F), size = 18.dp)
                        Text(
                            text = "Look in the Help menu for Examples and Tutorials!",
                            color = Color(0xAAFFFFFF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Draw Wires behind modules
            WireRenderer(state)

            // Draw Modules — add cases here for modules with custom layouts
            state.modules.forEach { module ->
                when (module) {
                    is NumberModule -> NumberModuleNode(module, state)
                    is EquationModule -> EquationModuleNode(module, state)
                    is ScaleModule -> ScaleModuleNode(module, state)
                    is FaderModule -> FaderModuleNode(module, state)
                    is KnobModule -> KnobModuleNode(module, state)
                    is PatchModule -> PatchModuleNode(module, state)
                    is SequenceModule -> SequenceModuleNode(module, state)
                    is MixerModule -> MixerModuleNode(module, state)
                    is ModuloSelectModule -> ModuleNode(module, state, headerColor = colors.headerSequence)
                    is InputModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                    is CommandModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                    is OutputModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                    is AudioInputModule -> ModuleNode(module, state, headerColor = colors.headerAudio, label = module.name)
                    is AudioOutputModule -> ModuleNode(module, state, headerColor = colors.headerAudio, label = module.name)
                    is ScopeModule -> ScopeModuleNode(module, state)
                    is KeyboardModule -> KeyboardModuleNode(module, state)
                    is CommentModule -> CommentModuleNode(module, state)
                    is SegmentedEnvelopeModule -> SegmentedEnvelopeModuleNode(module, state)
                    is PianoRollModule -> PianoRollModuleNode(module, state)
                    is KeyMatchModule -> KeyMatchModuleNode(module, state)
                    is MatchControlModule -> MatchControlModuleNode(module, state)
                    is GlowModule -> GlowModuleNode(module, state)
                    is FlashModule -> FlashModuleNode(module, state)
                    is SendModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                    is ReceiveModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                    is TimelineModule -> TimelineModuleNode(module, state)
                    is MidiInModule -> MidiInModuleNode(module, state)
                    is MidiOutModule -> MidiOutModuleNode(module, state)
                    else -> ModuleNode(module, state)
                }
            }

            // Draw selection rectangle (in screen space, account for rootOffset)
            val selRectStart = state.selectionRectStart
            val selRectEnd = state.selectionRectEnd
            if (selRectStart != null && selRectEnd != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = minOf(selRectStart.x, selRectEnd.x)
                    val top = minOf(selRectStart.y, selRectEnd.y)
                    val right = maxOf(selRectStart.x, selRectEnd.x)
                    val bottom = maxOf(selRectStart.y, selRectEnd.y)
                    drawRect(
                        color = colors.selectionRect,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawRect(
                        color = colors.selectionRect.copy(alpha = 0.1f),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top)
                    )
                }
            }

            // Editor overlay — backdrop + panel
            state.editingModule?.let { module ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .pointerInput(Unit) { detectTapGestures { state.editingModule = null } }
                )
                ModuleEditor(
                    module = module,
                    onDismiss = { state.editingModule = null },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

        }
    }
}

@Composable
fun WireRenderer(state: GraphState) {
    val colors = LocalGraphColors.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        state.connections.forEach { conn ->
            val start = state.jackOffsets[conn.output] ?: Offset.Zero
            val end = state.jackOffsets[conn.input] ?: Offset.Zero

            if (start != Offset.Zero && end != Offset.Zero) {
                val wireColor = when (conn.output) {
                    is AudioOutputJack -> colors.wireAudio
                    is VoiceOutputJack -> colors.wireVoice
                    is DataQueueOutputJack -> colors.wireDataQueue
                    else -> colors.wireDefault
                }
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    if (conn.output.module is TimelineModule) {
                        val dy = 40f
                        val dx = ((end.x - start.x).coerceAtLeast(60f) / 2f).coerceAtMost(100f)
                        cubicTo(start.x, start.y + dy, end.x - dx, end.y, end.x, end.y)
                    } else {
                        val controlX = (start.x + end.x) / 2
                        cubicTo(controlX, start.y, controlX, end.y, end.x, end.y)
                    }
                }
                drawPath(path, color = wireColor, style = Stroke(width = 3f))
            }
        }

        // Draw rubber-band wire as a simple straight line
        val draggingOutput = state.draggingWireByOutput
        if (draggingOutput != null) {
            val start = state.jackOffsets[draggingOutput] ?: Offset.Zero
            val end = state.dragPointerPosition
            if (start != Offset.Zero) {
                val wireColor = when (draggingOutput) {
                    is AudioOutputJack -> colors.wireAudio
                    is VoiceOutputJack -> colors.wireVoice
                    is DataQueueOutputJack -> colors.wireDataQueue
                    else -> colors.wireDragging
                }
                drawLine(
                    color = wireColor,
                    start = start,
                    end = end,
                    strokeWidth = 3f
                )
            }
        }
    }
}

@Composable
fun ModuleNode(module: Module, state: GraphState, headerColor: Color? = null, label: String = module.name) {
    val colors = LocalGraphColors.current
    val resolvedHeaderColor = headerColor ?: colors.headerDefault
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    // A show module's width follows the text it is displaying, so a value losing a digit would
    // narrow the whole node. With values streaming in that reads as a jitter, so the node may
    // grow to fit but never shrink back. Reset when the composable is reused for another module.
    val nodeDensity = LocalDensity.current
    var widestSoFar by remember(module) { mutableStateOf(0.dp) }
    val growOnlyWidth = module is ShowModule

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .then(if (growOnlyWidth) Modifier.widthIn(min = widestSoFar) else Modifier)
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
                if (growOnlyWidth) {
                    with(nodeDensity) {
                        val measured = size.width.toDp()
                        if (measured > widestSoFar) widestSoFar = measured
                    }
                }
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        // fillMaxWidth rather than IntrinsicSize.Max, so the header and body still span the node
        // when the grow-only minimum holds it wider than its content needs.
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            ModuleHeader(module = module, state = state, headerColor = resolvedHeaderColor, label = label)

            // Show custom content for ShowModule
            if (module is ShowModule) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.Black)) {
                    Text(
                        text = module.displayText,
                        color = Color.Green,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Show the transport position for RecorderModule. The cursor is advanced on the audio
            // thread and is not Compose state, so poll it rather than expecting a recomposition.
            if (module is RecorderModule) {
                var cursorText by remember { mutableStateOf(formatMinutesSecondsMillis(0.0)) }
                LaunchedEffect(module) {
                    while (true) {
                        cursorText = formatMinutesSecondsMillis(module.displayCursorSeconds)
                        delay(50)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cursorText,
                        color = if (module.unit.isRecording) Color.Red else Color.Green,
                        fontSize = 12.sp,
                        // Monospace keeps every digit the same width, so the module does not
                        // change size as the time advances.
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Show custom content for ShowManyModule: scrolling list of messages
            if (module is ShowManyModule) {
                val scrollState = rememberLazyListState()
                LaunchedEffect(scrollState) {
                    snapshotFlow { scrollState.layoutInfo.totalItemsCount }
                        .collect { totalItems ->
                            if (totalItems > 0) {
                                try {
                                    scrollState.scrollToItem(totalItems - 1)
                                } catch (_: Exception) {
                                    // Ignore index race conditions during rapid state updates
                                }
                            }
                        }
                }
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .width(220.dp)
                        .height(120.dp)
                        .background(Color.Black)
                        .padding(4.dp)
                ) {
                    items(module.messages) { msgText ->
                        Text(
                            text = msgText,
                            color = Color.Green,
                            fontSize = 10.sp
                        )
                    }
                }
            }




            if (module is ArmModule) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (module.isArmed) "● ARMED" else "○ DISARMED",
                        color = if (module.isArmed) colors.jackKickable else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Jacks: inputs at top, outputs at bottom, single column
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module is BeatClockModule) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            .background(Color(0xFFE0E0E0))
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = module.division,
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            DropdownArrow(
                                color = Color.Gray,
                                size = 7.dp
                            )
                        }
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        val divisions = listOf(
                            "4/1", "3/1", "2/1", "1/1", "1/2", "3/8", "1/4", "3/16",
                            "1/8", "1/16", "1/32", "1/3", "1/6", "1/12", "1/5", "1/10"
                        )
                        divisions.forEach { div ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(div, fontSize = 12.sp) },
                                onClick = {
                                    module.division = div
                                    FileState.markDirty()
                                    expanded = false
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            )
                        }
                    }
                }
            }
            if (module !is TimelineModule) {
                if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                    HorizontalDivider(color = Color.Black, thickness = 1.dp)
                }
                module.outputs.forEach { JackView(it, state, isLeft = false) }
            }
        }
    }
}

@Composable
fun KeyMatchModuleNode(module: KeyMatchModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .size(width = 80.dp, height = 80.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            // Body containing the button and labeled jacks
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Button
                var isPressed by remember { mutableStateOf(false) }
                val buttonBg = if (isPressed) colors.buttonBgPressed else colors.buttonBg

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(buttonBg, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                        .pointerInput(module) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val e = state.engine
                                    val time = ScoreClock.now()
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            isPressed = true
                                            val msg = Message.FloatValue(time, module.keyChar.code.toDouble())
                                            if (e != null) {
                                                e.post(SendOutputCommand(module.on, msg))
                                            } else {
                                                module.on.send(msg)
                                            }
                                        }
                                        PointerEventType.Release -> {
                                            isPressed = false
                                            val msg = Message.FloatValue(time, module.keyChar.code.toDouble())
                                            if (e != null) {
                                                e.post(SendOutputCommand(module.off, msg))
                                            } else {
                                                module.off.send(msg)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Text(
                        text = module.keyChar.toString().uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                // Labeled Jacks stacked vertically
                Column(
                    modifier = Modifier.fillMaxHeight().weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.End
                ) {
                    JackView(module.on, state, isLeft = false)
                    JackView(module.off, state, isLeft = false)
                }
            }
        }
    }
}

/**
 * A compact horizontal value indicator showing a value between 0.0 and 1.0
 * as a simple horizontal line with a vertical thumb moving from left to right.
 */
@Composable
fun HorizontalValueIndicator(
    value: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Gray,
    thumbColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val strokePx = 1.5.dp.toPx()
        val thumbWidthPx = 2.dp.toPx()
        val thumbHeightPx = 10.dp.toPx()
        val halfThumbWidth = thumbWidthPx / 2f
        val centerY = size.height / 2f

        val startX = halfThumbWidth
        val endX = size.width - halfThumbWidth
        val usableWidth = (endX - startX).coerceAtLeast(0f)

        // Draw horizontal line
        drawLine(
            color = lineColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokePx
        )

        // Calculate thumb X position (0.0 = startX, 1.0 = endX)
        val fraction = value.coerceIn(0f, 1f)
        val thumbX = startX + fraction * usableWidth

        // Draw vertical thumb
        drawLine(
            color = thumbColor,
            start = Offset(thumbX, centerY - thumbHeightPx / 2f),
            end = Offset(thumbX, centerY + thumbHeightPx / 2f),
            strokeWidth = thumbWidthPx
        )
    }
}

@Composable
fun MatchControlModuleNode(module: MatchControlModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            // Inputs: "in", "learn", "index"
            module.inputs.forEach { JackView(it, state, isLeft = true) }

            HorizontalDivider(color = Color.Black, thickness = 1.dp)

            // Output row with graphic display to the left of the "value" output jack
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalValueIndicator(
                    value = module.currentValue.toFloat(),
                    modifier = Modifier
                        .padding(start = 6.dp, end = 2.dp)
                        .width(40.dp)
                        .height(16.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    JackView(module.output, state, isLeft = false)
                }
            }
        }
    }
}

@Composable
fun MidiInModuleNode(module: MidiInModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerInputOutput, label = module.name)

            // On-module channel selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                var expanded by remember { mutableStateOf(false) }
                val chText = if (module.channelFilter == -1) "Ch: ALL" else "Ch: ${module.channelFilter}"
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0E0E0))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = chText,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        DropdownArrow(
                            color = Color.Gray,
                            size = 7.dp
                        )
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val channels = listOf(-1) + (1..16).toList()
                    channels.forEach { ch ->
                        val label = if (ch == -1) "ALL" else "Channel $ch"
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label, fontSize = 12.sp) },
                            onClick = {
                                module.selectChannelFilter(ch)
                                FileState.markDirty()
                                expanded = false
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        )
                    }
                }
            }

            if (module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
                module.outputs.forEach { JackView(it, state, isLeft = false) }
            }
        }
    }
}

@Composable
fun MidiOutModuleNode(module: MidiOutModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerInputOutput, label = module.name)

            // On-module channel selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                var expanded by remember { mutableStateOf(false) }
                val chText = "Ch: ${module.channel}"
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0E0E0))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = chText,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        DropdownArrow(
                            color = Color.Gray,
                            size = 7.dp
                        )
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    (1..16).forEach { ch ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Channel $ch", fontSize = 12.sp) },
                            onClick = {
                                module.selectChannel(ch)
                                FileState.markDirty()
                                expanded = false
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        )
                    }
                }
            }

            if (module.inputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
                module.inputs.forEach { JackView(it, state, isLeft = true) }
            }
        }
    }
}

private fun formatValue(value: Double): String = formatNumber(value)

@Composable
fun JackView(jack: Jack, state: GraphState, isLeft: Boolean) {
    if (jack is CommandJack) {
        CommandJackView(jack, state, isLeft)
        return
    }
    if (jack is CommandOutputJack) {
        CommandOutputJackView(jack, state, isLeft)
        return
    }
    val colors = LocalGraphColors.current
    val textColor = if (jack.isHighlighted) Color.Black else Color.LightGray
    val backgroundColor = when {
        jack.isHighlighted -> colors.jackBgHighlighted
        jack is AudioInputJack || jack is AudioOutputJack -> colors.jackBgAudio
        jack is VoiceInputJack || jack is VoiceOutputJack -> colors.jackBgVoice
        jack is DataQueueInputJack || jack is DataQueueOutputJack -> colors.jackBgDataQueue
        else -> colors.jackBgDefault
    }

    var isHovered by remember { mutableStateOf(false) }
    var isValueDragging by remember { mutableStateOf(false) }
    var valueDragCurrentValue by remember { mutableStateOf(0.0) }
    var valueDragAccumX by remember { mutableStateOf(0f) }
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }

    val displayText = when {
        isValueDragging -> formatValue(valueDragCurrentValue)
        isHovered -> formatValue(jack.getValue())
        else -> jack.name
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onGloballyPositioned { layoutCoordinates ->
                jackRootPosition = layoutCoordinates.positionInRoot()
                val size = layoutCoordinates.size
                val pos = jackRootPosition - state.rootOffset
                val edgeOffset = if (isLeft) {
                    Offset(0f, size.height / 2f)
                } else {
                    Offset(size.width.toFloat(), size.height / 2f)
                }
                state.updateJackOffset(jack, pos + edgeOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .background(backgroundColor)
            // Hover detection
            .pointerInput("hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                            else -> {}
                        }
                    }
                }
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    val unconnectedInput = jack is InputJack &&
                        state.connections.none { it.input == jack }
                    if (unconnectedInput) {
                        valueDragCurrentValue = jack.getValue()
                        valueDragAccumX = 0f
                        isValueDragging = true
                    } else {
                        if (jack is OutputJack) state.startDraggingFromOutput(jack, isShiftPressed)
                        else if (jack is InputJack) state.startDraggingFromInput(jack)
                        state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (isValueDragging) {
                                isValueDragging = false
                            } else {
                                state.onPointerUp(null)
                            }
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            if (isValueDragging) {
                                val inputJack = jack as InputJack
                                valueDragAccumX += dragAmount.x
                                val range = inputJack.max - inputJack.min
                                val sensitivity = range / 400.0 * 10.0.pow(-valueDragAccumX / 200.0)
                                val low = minOf(inputJack.min, inputJack.max)
                                val high = maxOf(inputJack.min, inputJack.max)
                                val newValue = (valueDragCurrentValue - dragAmount.y * sensitivity)
                                    .coerceIn(low, high)
                                valueDragCurrentValue = newValue
                                val e = state.engine
                                if (e != null) e.post(SetValueCommand(inputJack, newValue, ScoreClock.now()))
                                else inputJack.setValue(newValue)
                                state.markChanged()
                                FileState.markDirty()
                            } else {
                                state.onPointerMove(state.dragPointerPosition + dragAmount)
                            }
                        }
                    }
                }
            }
    ) {
        if (isLeft) {
            JackMarker(jack)
            Spacer(modifier = Modifier.width(4.dp))
            Text(displayText, color = textColor, fontSize = 10.sp, maxLines = 1, softWrap = false)
            if (jack.module is SendModule) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(">>", color = colors.jackDefault, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
            if (jack.module is ReceiveModule) {
                Text(">>", color = colors.jackDefault, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = jack.name,
                    color = Color.Transparent,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = displayText,
                    color = textColor,
                    fontSize = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.matchParentSize()
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Outputs are never kickable, so this is the plain type-coloured square.
            JackMarker(jack)
        }
    }
}

@Composable
fun CommandJackView(jack: CommandJack, state: GraphState, isLeft: Boolean) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val edgeOffset = if (isLeft) Offset(0f, size.height / 2f)
                                 else Offset(size.width.toFloat(), size.height / 2f)
                state.updateJackOffset(jack, jackRootPosition - state.rootOffset + edgeOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .pointerInput(jack) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        state.startDraggingFromInput(jack)
                        state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onPointerMove(state.dragPointerPosition + dragAmount)
                    },
                    onDragEnd = { state.onPointerUp(null) },
                    onDragCancel = { state.onPointerUp(null) }
                )
            }
    ) {
        if (isLeft) {
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
            Spacer(modifier = Modifier.width(4.dp))
        }
        // A highlighted command jack is one whose module is waiting for something - armed, or
        // learning a controller. Tinting the button is what makes that state visible; without
        // it, setting isHighlighted on a CommandJack has no effect at all.
        Button(
            onClick = {},
            modifier = Modifier
                .height(24.dp)
                .pointerInput(jack) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val e = state.engine
                        if (e != null) {
                            e.post(KickJackCommand(jack, ScoreClock.now()))
                        } else {
                            jack.receive(com.syntona.dataflow.model.Message.FloatValue(ScoreTime.Zero, 0.0))
                        }
                    }
                },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = if (jack.isHighlighted) {
                ButtonDefaults.buttonColors(
                    containerColor = colors.buttonBgArmed,
                    contentColor = Color.Black
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                jack.name,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        (jack.module as? CommandJackBadge)?.badgeFor(jack)?.let { badge ->
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                badge,
                fontSize = 10.sp,
                color = if (jack.isHighlighted) colors.buttonBgArmed else Color.Gray,
                maxLines = 1,
                softWrap = false
            )
        }
        if (!isLeft) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
        }
    }
}

@Composable
fun CommandOutputJackView(jack: CommandOutputJack, state: GraphState, isLeft: Boolean) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val edgeOffset = if (isLeft) Offset(0f, size.height / 2f)
                                 else Offset(size.width.toFloat(), size.height / 2f)
                state.updateJackOffset(jack, jackRootPosition - state.rootOffset + edgeOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    state.startDraggingFromOutput(jack, isShiftPressed)
                    state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            state.onPointerUp(null)
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            state.onPointerMove(state.dragPointerPosition + dragAmount)
                        }
                    }
                }
            }
    ) {
        if (isLeft) {
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Button(
            onClick = {},
            modifier = Modifier
                .height(20.dp)
                .pointerInput(jack) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val e = state.engine
                        val time = ScoreClock.now()
                        if (e != null) {
                            e.post(SendOutputCommand(jack, Message.FloatValue(time, 0.0)))
                        } else {
                            jack.send(Message.FloatValue(time, 0.0))
                        }
                    }
                },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) {
            Text("Kick", fontSize = 10.sp)
        }
        if (!isLeft) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
        }
    }
}

/**
 * Connector dot with wire-drag support but no name label.
 * Used by compact module layouts such as [NumberModuleNode].
 */
/**
 * The square that marks a jack, coloured by the kind of message it carries, with a red triangle
 * butting against its right edge when arriving there runs the module.
 *
 * Two facts needed one square. Colour used to mean the message type except when the jack was
 * kickable, when it meant that instead - so a kickable voice jack could advertise only one of
 * the two, and always the less useful one. Colour now always means the type; the triangle alone
 * means kickable.
 */
@Composable
private fun JackMarker(jack: Jack, squareSize: Dp = 8.dp) {
    val colors = LocalGraphColors.current
    val typeColor = when {
        jack is AudioInputJack || jack is AudioOutputJack -> colors.jackAudio
        jack is VoiceInputJack || jack is VoiceOutputJack -> colors.jackVoice
        jack is DataQueueInputJack || jack is DataQueueOutputJack -> colors.jackDataQueue
        else -> colors.jackDefault
    }
    val kickColor = colors.jackKickable
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(squareSize).background(typeColor))
        if (jack is InputJack && jack.isKickable) {
            Canvas(modifier = Modifier.size(width = squareSize / 2, height = squareSize)) {
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    },
                    kickColor
                )
            }
        }
    }
}

@Composable
fun CompactJackConnector(jack: Jack, state: GraphState, isLeft: Boolean) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(12.dp)
            .then(if (jack.isHighlighted) Modifier.background(colors.jackBgHighlighted, shape = RoundedCornerShape(3.dp)) else Modifier)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val edgeX = if (isLeft) 0f else size.width.toFloat()
                state.updateJackOffset(
                    jack,
                    jackRootPosition - state.rootOffset + Offset(edgeX, size.height / 2f),
                    Size(size.width.toFloat(), size.height.toFloat())
                )
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    if (jack is OutputJack) state.startDraggingFromOutput(jack, isShiftPressed)
                    else if (jack is InputJack) state.startDraggingFromInput(jack)
                    state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            state.onPointerUp(null)
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            state.onPointerMove(state.dragPointerPosition + dragAmount)
                        }
                    }
                }
            }
    ) {
        // Square plus triangle is exactly the 12.dp box, so a kickable jack keeps the same
        // footprint in the compact nodes and nothing around it shifts.
        JackMarker(jack)
    }
}

@Composable
fun FaderModuleNode(module: FaderModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(70.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp).padding(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
                }
                VerticalSlider(
                    value = module.value.toFloat(),
                    onValueChange = { v ->
                        val e = state.engine
                        if (e != null) e.post(SetValueCommand(module.valueIn, v.toDouble(), ScoreClock.now()))
                        else module.setValue(v.toDouble())
                        state.markChanged()
                    },
                    valueRange = module.valueIn.min.toFloat()..module.valueIn.max.toFloat(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.outputs.forEach { CompactJackConnector(it, state, isLeft = false) }
                }
            }

            NumberField(
                value = module.value,
                onCommit = { v ->
                    val e = state.engine
                    if (e != null) e.post(SetValueCommand(module.valueIn, v, ScoreClock.now()))
                    else module.setValue(v)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PatchModuleNode(module: PatchModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .widthIn(min = 100.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.modulePatchBackground
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            val suffix = if (module.isModified) "*" else ""
            val displayName = if (module is PolyPatchModule) "${module.name}$suffix[${module.maxVoices}]" else "${module.name}$suffix"
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerPatch,
                label = displayName,
                onDoubleTap = {
                    val savedX = module.windowX
                    val savedY = module.windowY
                    val restoredPosition = if (savedX != null && savedY != null) {
                        Offset(savedX * density.density, savedY * density.density)
                    } else {
                        val parentWin = mdiState.windows.find { it.graphState === state }
                        if (parentWin != null) {
                            Offset(parentWin.position.x + 28f * density.density, parentWin.position.y + 28f * density.density)
                        } else {
                            val pos = state.modulePositions[module] ?: Offset(100f, 100f)
                            Offset(
                                (pos.x + 20f) * density.density,
                                (pos.y + 30f) * density.density
                            )
                        }
                    }
                    val window = MdiWindowInfo(
                        id = module,
                        title = module.name,
                        position = restoredPosition,
                        width = module.windowWidth?.dp ?: 600.dp,
                        height = module.windowHeight?.dp ?: 450.dp,
                        graphState = module.graphState,
                        content = { GraphEditor(state = module.graphState) }
                    )
                    window.onGeometryChanged = {
                        module.windowX = window.position.x / density.density
                        module.windowY = window.position.y / density.density
                        module.windowWidth = window.width.value
                        module.windowHeight = window.height.value
                    }
                    mdiState.open(window)
                },
                trailingContent = {
                    val activeGlowModules = module.graphState.modules.filter { it is GlowModule || it is FlashModule }
                    if (activeGlowModules.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            activeGlowModules.forEach { subModule ->
                                val isOn = when (subModule) {
                                    is GlowModule -> subModule.isOn
                                    is FlashModule -> subModule.isOn
                                    else -> false
                                }
                                TooltipArea(text = subModule.name) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                if (isOn) Color.Yellow else Color(0xFF444444),
                                                CircleShape
                                            )
                                            .border(0.5.dp, Color.Gray, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                    Arrangement.SpaceBetween
                } else if (module.inputs.isNotEmpty()) {
                    Arrangement.Start
                } else {
                    Arrangement.End
                }
            ) {
                if (module.inputs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        horizontalAlignment = Alignment.Start
                    ) {
                        module.inputs.forEach { JackView(it, state, isLeft = true) }
                    }
                }
                if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (module.outputs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        horizontalAlignment = Alignment.End
                    ) {
                        module.outputs.forEach { JackView(it, state, isLeft = false) }
                    }
                }
            }
        }
    }
}

@Composable
fun NumberModuleNode(module: NumberModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(140.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(4.dp)
            ) {
                Column {
                    module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
                }
                NumberField(
                    value = module.value,
                    onCommit = { v ->
                        val e = state.engine
                        if (e != null) e.post(SetValueCommand(module.valueIn, v, ScoreClock.now()))
                        else module.setValue(v)
                    },
                    modifier = Modifier.weight(1f)
                )
                Column {
                    module.outputs.forEach { CompactJackConnector(it, state, isLeft = false) }
                }
            }
        }
    }
}

@Composable
fun SequenceModuleNode(module: SequenceModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(110.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerSequence, height = 28.dp)

            val controlInputs = module.inputs.filter { it !== module.reset && it !== module.increment && !module.stepInputs.contains(it) }
            val controlOutputs = module.outputs.filter { !module.stepOutputs.contains(it) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompactJackConnector(module.reset, state, isLeft = true)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(module.reset.name, fontSize = 10.sp, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompactJackConnector(module.increment, state, isLeft = true)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(module.increment.name, fontSize = 10.sp, color = Color.Gray)
                    }
                    controlInputs.forEach { jack ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompactJackConnector(jack, state, isLeft = true)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(jack.name, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    controlOutputs.forEach { jack ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(jack.name, fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactJackConnector(jack, state, isLeft = false)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray, thickness = 1.dp)

            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                for (i in 0 until module.numOutputs) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
                            CompactJackConnector(module.stepInputs[i], state, isLeft = true)
                        }
                        SequenceStepTextField(
                            value = module.values[i],
                            onCommit = { text ->
                                module.values[i] = text
                                val numVal = text.toDoubleOrNull()
                                if (numVal != null) {
                                    val e = state.engine
                                    if (e != null) e.post(SetValueCommand(module.stepInputs[i], numVal, ScoreClock.now()))
                                    else module.stepInputs[i].setValue(numVal)
                                }
                                FileState.markDirty()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.CenterEnd) {
                            CompactJackConnector(module.stepOutputs[i], state, isLeft = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SequenceStepTextField(
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value) }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDirty) text = value
    }

    fun commit() {
        onCommit(text)
        isDirty = false
    }

    BasicTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            isDirty = true
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 12.sp, color = Color.Black),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier
            .background(if (isDirty) Color(0xFFFFB0B0) else Color.White)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    commit()
                    true
                } else false
            }
    )
}

@Composable
fun KnobModuleNode(module: KnobModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(90.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerKnob)

            // Knob area with jacks on either side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
                }
                RotaryKnob(
                    value = module.value.toFloat(),
                    onValueChange = { v ->
                        val e = state.engine
                        if (e != null) e.post(SetValueCommand(module.valueIn, v.toDouble(), ScoreClock.now()))
                        else module.setValue(v.toDouble())
                        state.markChanged()
                    },
                    valueRange = module.valueIn.min.toFloat()..module.valueIn.max.toFloat(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.outputs.forEach { CompactJackConnector(it, state, isLeft = false) }
                }
            }

            // Numeric readout / direct entry
            NumberField(
                value = module.value,
                onCommit = { v ->
                    val e = state.engine
                    if (e != null) e.post(SetValueCommand(module.valueIn, v, ScoreClock.now()))
                    else module.setValue(v)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ResizableModuleBox(
    module: Module,
    state: GraphState,
    headerColor: Color,
    initialWidth: Dp,
    initialHeight: Dp,
    minWidth: Dp = 100.dp,
    minHeight: Dp = 50.dp,
    onResize: (Dp, Dp) -> Unit = { _, _ -> },
    content: @Composable BoxScope.(Dp, Dp) -> Unit
) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val density = LocalDensity.current

    // Local state for smooth dragging, initialized from module's state
    var currentWidth by remember(module, initialWidth) { mutableStateOf(initialWidth) }
    var currentHeight by remember(module, initialHeight) { mutableStateOf(initialHeight) }

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(currentWidth)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = headerColor)

            Box(modifier = Modifier.width(currentWidth).height(currentHeight)) {
                content(currentWidth, currentHeight)

                // Resize grip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .pointerInput(module) {
                            detectDragGestures(
                                onDragEnd = {
                                    onResize(currentWidth, currentHeight)
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val dw: Dp = (dragAmount.x / density.density).dp
                                val dh: Dp = (dragAmount.y / density.density).dp
                                currentWidth = (currentWidth + dw).coerceAtLeast(minWidth)
                                currentHeight = (currentHeight + dh).coerceAtLeast(minHeight)
                             }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val grip = colors.faderGrip
                        for (i in 1..3) {
                            val d = size.width * i / 4f
                            drawLine(grip, Offset(d, size.height), Offset(size.width, d), strokeWidth = 1.5f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentModuleNode(module: CommentModule, state: GraphState) {
    val colors = LocalGraphColors.current
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerComment,
        initialWidth = module.width.dp,
        initialHeight = module.height.dp,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        BasicTextField(
            value = module.text,
            onValueChange = { module.text = it },
            modifier = Modifier.fillMaxSize().padding(8.dp).onKeyEvent { true },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White)
        )
    }
}

/** Width of one jack in the keyboard's top row, wide enough for "amplitude". */
private val KEYBOARD_JACK_WIDTH = 76.dp

@Composable
fun KeyboardModuleNode(module: KeyboardModule, state: GraphState) {
    val colors = LocalGraphColors.current
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerInputOutput,
        initialWidth = module.width.dp,
        initialHeight = module.height.dp,
        minWidth = 260.dp,
        minHeight = 80.dp,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            // Jacks sit above the keys rather than beside them, so the keyboard gets the whole
            // width of the module. JackView, not CompactJackConnector, because these carry values
            // worth seeing: it swaps the name for the value on hover and drags to set it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column() {
                    module.inputs.forEach { jack ->
                        Box(modifier = Modifier.width(KEYBOARD_JACK_WIDTH)) {
                            JackView(jack, state, isLeft = true)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                module.outputs.forEach { jack ->
                    Box(modifier = Modifier.width(KEYBOARD_JACK_WIDTH)) {
                        JackView(jack, state, isLeft = false)
                    }
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                // Reading module.octave, not the jack, is what makes the keys redraw and start
                // sending their new pitches when the octave input changes.
                val sendNote = { noteNumber: Int, opcode: Message.VoiceOpcode ->
                    val e = state.engine
                    val msg = Message.VoiceMessage(
                        time = ScoreClock.now(),
                        opcode = opcode,
                        tag = noteNumber,
                        value = noteNumber.toDouble(),
                        extra = module.currentAmplitude()
                    )
                    if (e != null) {
                        e.post(SendOutputCommand(module.output, msg))
                    } else {
                        module.output.send(msg)
                    }
                }
                BlackWhiteKeyboard(
                    modifier = Modifier.fillMaxSize(),
                    onKeyDown = { noteNumber -> sendNote(noteNumber, Message.VoiceOpcode.NOTE_ON) },
                    onKeyUp = { noteNumber -> sendNote(noteNumber, Message.VoiceOpcode.NOTE_OFF) },
                    numNotes = module.numNotes,
                    startingNote = module.octave * SEMITONES_PER_OCTAVE
                )
            }
        }
    }
}

@Composable
fun ScopeModuleNode(module: ScopeModule, state: GraphState) {
    val colors = LocalGraphColors.current
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerAudio,
        initialWidth = module.width.dp,
        initialHeight = module.height.dp,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp)
            ) {
                module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
            }
            Oscilloscope(
                probe = module.scope,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SegmentedEnvelopeModuleNode(module: SegmentedEnvelopeModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerInputOutput,
                onDoubleTap = {
                    mdiState.open(
                        MdiWindowInfo(
                            id = module,
                            title = module.name,
                            position = Offset(
                                ((state.modulePositions[module]?.x ?: 100f) + 20f) * density.density,
                                ((state.modulePositions[module]?.y ?: 100f) + 30f) * density.density
                            ),
                            graphState = state,
                            content = {
                                val localMdi = LocalMdiState.current
                                val windowInfo = remember { localMdi.windows.find { it.id == module } }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.background)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ModuleNameEditor(
                                        module = module,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        textColor = Color.LightGray,
                                        borderColor = Color.Gray,
                                        onNameChanged = { windowInfo?.title = it }
                                    )

                                    androidx.compose.material3.HorizontalDivider(color = Color.Gray, thickness = 1.dp)

                                    Box(modifier = Modifier.weight(1f)) {
                                        SegmentedEnvelopeEditor(
                                            envelope = module.envelope,
                                            onChanged = {
                                                state.markChanged()
                                                FileState.markDirty()
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    )
                }
            )
            // Draw jacks.
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
            }
            module.outputs.forEach { JackView(it, state, isLeft = false) }
        }
    }
}


@Composable
fun PianoRollModuleNode(module: PianoRollModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerSequence,
                onDoubleTap = {
                    val savedX = module.windowX
                    val savedY = module.windowY
                    val restoredPosition = if (savedX != null && savedY != null) {
                        Offset(savedX * density.density, savedY * density.density)
                    } else {
                        val pos = state.modulePositions[module] ?: Offset(100f, 100f)
                        Offset(
                            (pos.x + 20f) * density.density,
                            (pos.y + 30f) * density.density
                        )
                    }
                    val window = MdiWindowInfo(
                        id = module,
                        title = module.name,
                        position = restoredPosition,
                        width = module.windowWidth?.dp ?: 800.dp,
                        height = module.windowHeight?.dp ?: 500.dp,
                        graphState = state,
                        content = { PianoRollEditor(module, state) }
                    )
                    window.onGeometryChanged = {
                        module.windowX = window.position.x / density.density
                        module.windowY = window.position.y / density.density
                        module.windowWidth = window.width.value
                        module.windowHeight = window.height.value
                    }
                    mdiState.open(window)
                }
            )
            // Draw jacks.
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
            }
            module.outputs.forEach { JackView(it, state, isLeft = false) }
        }
    }
}


fun openHelpForModule(
    module: Module,
    mdiState: MdiState,
    engine: com.syntona.dataflow.engine.DataFlowEngine?,
    density: Float,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val type = when (module) {
        is com.syntona.dataflow.modules.PolyPatchModule -> "polyPatch"
        is com.syntona.dataflow.modules.PatchModule -> "patch"
        else -> module.type
    }
    val helpPath = com.syntona.dataflow.registry.HelpRegistry.getHelpFilePath(type) ?: return
    val currentEngine = engine ?: return
    scope.launch {
        try {
            val bytes = com.syntona.SharedResources.readBytes("files/$helpPath")
            val content = bytes.decodeToString()
            val newDoc = com.syntona.openNewDocument(mdiState, currentEngine, density)
            com.syntona.loadIntoDocument(mdiState, newDoc, helpPath, content, density)
        } catch (e: Exception) {
            println("Failed to load help file $helpPath: ${e.message}")
        }
    }
}

@Composable
fun ModuleHeader(
    module: Module,
    state: GraphState,
    headerColor: Color = LocalGraphColors.current.headerDefault,
    label: String = module.name,
    height: Dp = 24.dp,
    onDoubleTap: () -> Unit = { state.editingModule = module },
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val type = when (module) {
        is com.syntona.dataflow.modules.PolyPatchModule -> "polyPatch"
        is com.syntona.dataflow.modules.PatchModule -> "patch"
        else -> module.type
    }
    val hasHelp = com.syntona.dataflow.registry.HelpRegistry.hasHelpFile(type)

    TooltipArea(text = getModuleTooltipText(module)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(headerColor)
                .headerGestures(
                    state = state,
                    module = module,
                    mdiState = mdiState,
                    onRightClick = { showMenu = true },
                    onDoubleTap = onDoubleTap
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    if (trailingContent != null) {
                        trailingContent()
                    }
                }

                Box {
                    Box(
                        modifier = Modifier
                            .clickable { showMenu = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        VerticalEllipsisIcon(color = Color.White, size = 12.dp)
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (module is FloatSampleModule || module is SamplePlayerModule || module is RecorderModule) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Load from WAV File...", fontSize = 12.sp) },
                                onClick = {
                                    showMenu = false
                                    chooseWavOpenFile { path ->
                                        when (module) {
                                            is FloatSampleModule -> module.loadWavFile(path)
                                            is SamplePlayerModule -> module.loadWavFile(path)
                                            is RecorderModule -> module.loadWavFile(path)
                                        }
                                        state.markChanged()
                                        FileState.markDirty()
                                    }
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Save to WAV File...", fontSize = 12.sp) },
                                onClick = {
                                    showMenu = false
                                    chooseWavRecordFile(defaultName = "${module.name}.wav") { path ->
                                        when (module) {
                                            is FloatSampleModule -> module.saveWavFile(path)
                                            is SamplePlayerModule -> module.saveWavFile(path)
                                            is RecorderModule -> module.saveWavFile(path)
                                        }
                                        state.markChanged()
                                        FileState.markDirty()
                                    }
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            )
                        }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Help...", fontSize = 12.sp) },
                            enabled = hasHelp,
                            onClick = {
                                showMenu = false
                                openHelpForModule(module, mdiState, state.engine, density, scope)
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.headerGestures(
    state: GraphState,
    module: Module,
    mdiState: MdiState? = null,
    onRightClick: (() -> Unit)? = null,
    onDoubleTap: () -> Unit
): Modifier = this.pointerInput(module, state) {
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown()
        if (currentEvent.buttons.isSecondaryPressed || currentEvent.keyboardModifiers.isCtrlPressed) {
            down.consume()
            onRightClick?.invoke()
            return@awaitEachGesture
        }
        down.consume()
        state.requestFocus?.invoke()
        if (mdiState != null) {
            mdiState.activeGraphState = state
        }
        if (!state.selectedModules.contains(module)) {
            state.clearSelection()
            state.selectModule(module)
        }
        var wasDragged = false
        drag(down.id) { change ->
            change.consume()
            wasDragged = true
            val dragAmount = (change.position - change.previousPosition) / density
            if (state.selectedModules.contains(module)) {
                state.selectedModules.forEach { m ->
                    state.updateModulePosition(m, dragAmount)
                }
            } else {
                state.updateModulePosition(module, dragAmount)
            }
        }
        if (!wasDragged) {
            val secondDown = withTimeoutOrNull(doubleTapTimeout) { awaitFirstDown() }
            if (secondDown != null) {
                secondDown.consume()
                onDoubleTap()
                drag(secondDown.id) { it.consume() }
            }
        }
    }
}

@Composable
fun MixerModuleNode(module: MixerModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(150.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerAudio)

            // Channel strips
            for (i in 0 until module.numInputs) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 6.dp)
                ) {
                    val inputJack = module.getInputJack(i)
                    if (inputJack != null) {
                        CompactJackConnector(inputJack, state, isLeft = true)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${i + 1}",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Left knob: Volume (exponential taper sets gain)
                    RotaryKnob(
                        value = module.getVolumeKnobValue(i),
                        onValueChange = { v ->
                            val gainJack = module.getGainJack(i)
                            if (gainJack != null) {
                                val gainVal = (50.0.pow(v.toDouble()) - 1.0) / 49.0
                                val e = state.engine
                                if (e != null) e.post(SetValueCommand(gainJack, gainVal, ScoreClock.now()))
                                else gainJack.setValue(gainVal)
                            }
                            state.markChanged()
                        },
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Right knob: Pan (-1 to +1)
                    RotaryKnob(
                        value = module.getPanKnobValue(i),
                        onValueChange = { v ->
                            val panJack = module.getPanJack(i)
                            if (panJack != null) {
                                val e = state.engine
                                if (e != null) e.post(SetValueCommand(panJack, v.toDouble(), ScoreClock.now()))
                                else panJack.setValue(v.toDouble())
                            }
                            state.markChanged()
                        },
                        valueRange = -1f..1f,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(28.dp))
                }
            }

            // Master / Output Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val ampJack = module.inputs.find { it.name == "amplitude" }
                if (ampJack != null) {
                    CompactJackConnector(ampJack, state, isLeft = true)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("amp", color = Color.LightGray, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.weight(1f))
                Text("Master", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    val out0 = module.outputs.find { it.name == "output0" }
                    val out1 = module.outputs.find { it.name == "output1" }
                    if (out0 != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("L", color = Color.LightGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactJackConnector(out0, state, isLeft = false)
                        }
                    }
                    if (out1 != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R", color = Color.LightGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactJackConnector(out1, state, isLeft = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EquationModuleNode(module: EquationModule, state: GraphState) {
    val colors = LocalGraphColors.current
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerDefault,
        initialWidth = module.width.dp,
        initialHeight = module.height.dp,
        minWidth = 140.dp,
        minHeight = 36.dp,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Inputs on the left
            Column(
                modifier = Modifier.width(26.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                module.inputs.forEach { JackView(it, state, isLeft = true) }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Text field and Output on the right
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                EquationField(
                    value = module.equation,
                    errorMessage = module.errorMessage,
                    onCommit = { eq ->
                        val success = module.compile(eq)
                        if (success) {
                            state.markChanged()
                            FileState.markDirty()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                CompactJackConnector(module.output, state, isLeft = false)
            }
        }
    }
}

@Composable
fun EquationField(
    value: String,
    errorMessage: String?,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value) }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDirty) text = value
    }

    fun commit() {
        onCommit(text)
        isDirty = false
    }

    Box(
        modifier = modifier
    ) {
        TooltipArea(
            text = errorMessage ?: ""
        ) {
            BasicTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    isDirty = true
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = Color.Black),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            isDirty -> Color(0xFFFFB0B0)
                            errorMessage != null -> Color(0xFFFFC266)
                            else -> Color.White
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            commit()
                            true
                        } else false
                    }
                    .onKeyEvent { true }
            )
        }
    }
}

@Composable
fun ScaleModuleNode(module: ScaleModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    val moduleWidth = if (module.isEditable) 240.dp else 135.dp
    val inputsWidth = if (module.isEditable) 54.dp else 40.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(moduleWidth)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inputs on the left
                Column(
                    modifier = Modifier.width(inputsWidth),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    module.inputs.forEach { JackView(it, state, isLeft = true) }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Scale Display/Field and Output on the right
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (module.isEditable) {
                        ScaleField(
                            value = module.scaleString,
                            errorMessage = module.errorMessage,
                            onCommit = { scaleStr ->
                                val success = module.updateScale(scaleStr)
                                if (success) {
                                    state.markChanged()
                                    FileState.markDirty()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = "[${module.scaleDegrees.joinToString(",") { formatNumber(it) }}]",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    CompactJackConnector(module.output, state, isLeft = false)
                }
            }
        }
    }
}

@Composable
fun ScaleField(
    value: String,
    errorMessage: String?,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value) }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDirty) text = value
    }

    fun commit() {
        onCommit(text)
        isDirty = false
    }

    Box(
        modifier = modifier
    ) {
        TooltipArea(
            text = errorMessage ?: ""
        ) {
            BasicTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    isDirty = true
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = Color.Black),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            isDirty -> Color(0xFFFF8080) // Turns red while editing
                            errorMessage != null -> Color(0xFFFFC266) // Orange if error
                            else -> Color.White
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            commit()
                            true
                        } else false
                    }
                    .onKeyEvent { true }
            )
        }
    }
}

@Composable
fun GlowModuleNode(module: GlowModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(80.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    module.inputs.forEach { JackView(it, state, isLeft = true) }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (module.isOn) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Yellow.copy(alpha = 0.3f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Yellow, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(0xFF333333), CircleShape)
                                .border(1.dp, Color(0xFF555555), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlashModuleNode(module: FlashModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(80.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    module.inputs.forEach { JackView(it, state, isLeft = true) }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (module.isOn) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Yellow.copy(alpha = 0.3f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Yellow, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(0xFF333333), CircleShape)
                                .border(1.dp, Color(0xFF555555), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineTriggerJackView(
    jack: OutputJack,
    state: GraphState,
    viewportLeftGraphX: Float,
    viewportRightGraphX: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(colors.jackDefault)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val pos = jackRootPosition - state.rootOffset
                val bottomCenterOffset = Offset(size.width / 2f, size.height.toFloat())
                val unclippedX = pos.x + bottomCenterOffset.x
                val clippedX = unclippedX.coerceIn(viewportLeftGraphX, viewportRightGraphX)
                val connectionOffset = Offset(clippedX, pos.y + bottomCenterOffset.y)
                state.updateJackOffset(jack, connectionOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    state.startDraggingFromOutput(jack, isShiftPressed)
                    state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            state.onPointerUp(null)
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            state.onPointerMove(state.dragPointerPosition + dragAmount)
                        }
                    }
                }
            }
    )
}

@Composable
fun TimelineModuleNode(module: TimelineModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val density = LocalDensity.current
    val inputsWidth = 70.dp
    val spacing = 12.dp
    val viewportWidth = (module.width.dp - inputsWidth - spacing).coerceAtLeast(100.dp)

    val minZoom = (viewportWidth.value / (24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
    val maxZoom = (viewportWidth.value / (24f * 2.0f)).coerceIn(4f, 20f)
    val coercedZoom = module.zoom.coerceIn(minZoom, maxZoom)

    val viewportLeftGraphX = position.x + with(density) { (inputsWidth + spacing).toPx() }
    val viewportRightGraphX = position.x + with(density) { module.width.dp.toPx() }

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(module.width.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerDefault,
                label = "Timeline",
                height = 28.dp,
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .height(18.dp)
                                .width(28.dp)
                                .pointerInput(module) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        module.addTrigger(module.seekTime)
                                        FileState.markDirty()
                                    }
                                },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = if (module.loopMode) "🔁" else "➡️",
                            color = if (module.loopMode) Color.Green else Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                module.loopMode = !module.loopMode
                                FileState.markDirty()
                            }
                        )
                        Text("Zoom", fontSize = 10.sp, color = Color.White)
                        Slider(
                            value = coercedZoom,
                            onValueChange = {
                                module.zoom = it
                                FileState.markDirty()
                            },
                            valueRange = minZoom..maxZoom,
                            modifier = Modifier.width(70.dp).height(18.dp)
                        )
                    }
                }
            )

            // Body with 0 padding at bottom/right
            Box(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, end = 0.dp, bottom = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    verticalAlignment = Alignment.Top
                ) {
                    // Inputs Column (start, stop) on the left (fixed width = 70.dp, padded bottom to keep clear of edge)
                    Column(
                        modifier = Modifier.width(70.dp).padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        module.inputs.forEach { JackView(it, state, isLeft = true) }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Scrollable timeline viewport area
                    val inputsWidth = 70.dp
                    val spacing = 12.dp // no right padding, only margin/borders
                    val viewportWidth = (module.width.dp - inputsWidth - spacing).coerceAtLeast(100.dp)

                    val scrollState = rememberScrollState()
                    val extraTime = 5.0
                    val timelineWidth = 24.dp * ((module.maxTime + extraTime).toFloat() * coercedZoom)
                    val maxTimeX = 24.dp * (module.maxTime.toFloat() * coercedZoom)

                    // Stationary container for the top Scrollbar and the scrollable viewport
                    Column(
                        modifier = Modifier.width(viewportWidth).fillMaxHeight()
                    ) {
                        val coroutineScope = rememberCoroutineScope()

                        // New Row 1 (Top): Horizontal Scrollbar (height = 12.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(Color(0xFFE5E7EB), RoundedCornerShape(2.dp))
                                .pointerInput(scrollState.maxValue, timelineWidth) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val trackWidthPx = size.width.toFloat()
                                            val viewportWidthPx = with(density) { viewportWidth.toPx() }
                                            val timelineWidthPx = with(density) { timelineWidth.toPx() }

                                            if (timelineWidthPx > viewportWidthPx && scrollState.maxValue > 0) {
                                                val ratio = viewportWidthPx / timelineWidthPx
                                                val thumbWidthPx = trackWidthPx * ratio

                                                var clickX = down.position.x
                                                val targetScrollPx = ((clickX - thumbWidthPx / 2f) / (trackWidthPx - thumbWidthPx)) * scrollState.maxValue
                                                coroutineScope.launch {
                                                    scrollState.scrollTo(targetScrollPx.roundToInt().coerceIn(0, scrollState.maxValue))
                                                }

                                                do {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull()
                                                    if (change != null && change.pressed) {
                                                        change.consume()
                                                        clickX = change.position.x
                                                        val dragScrollPx = ((clickX - thumbWidthPx / 2f) / (trackWidthPx - thumbWidthPx)) * scrollState.maxValue
                                                        coroutineScope.launch {
                                                            scrollState.scrollTo(dragScrollPx.roundToInt().coerceIn(0, scrollState.maxValue))
                                                        }
                                                    }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        }
                                    }
                                }
                        ) {
                            val viewportWidthPx = with(density) { viewportWidth.toPx() }
                            val timelineWidthPx = with(density) { timelineWidth.toPx() }
                            if (timelineWidthPx > viewportWidthPx && scrollState.maxValue > 0) {
                                val ratio = viewportWidthPx / timelineWidthPx
                                val scrollPct = scrollState.value.toFloat() / scrollState.maxValue
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(ratio)
                                        .offset(x = (viewportWidth * (1f - ratio) * scrollPct))
                                        .background(Color(0xFF888888), RoundedCornerShape(2.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Scrollable viewport area (height weight 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .horizontalScroll(scrollState)
                        ) {
                            Column(
                                modifier = Modifier.width(timelineWidth).fillMaxHeight()
                            ) {
                                var currentPlayheadTime by remember { mutableStateOf(0.0) }
                                if (module.isPlaying) {
                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            val nowNs = ScoreClock.now().nanoseconds
                                            val elapsed = (nowNs - module.playbackStartTimeNs) / 1_000_000_000.0
                                            currentPlayheadTime = module.playbackStartPos + elapsed
                                            kotlinx.coroutines.delay(16)
                                        }
                                    }
                                } else {
                                    currentPlayheadTime = module.seekTime
                                }

                                // Row 2: Loop position indicator & maxTime marker (height = 16.dp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                                        .pointerInput(module) {
                                            val getPxPerSecond = {
                                                val d = density.density
                                                val inputsWidthPx = 70f * d
                                                val spacingPx = 12f * d
                                                val viewportWidthPx = (module.width.toFloat() * d - inputsWidthPx - spacingPx).coerceAtLeast(100f * d)
                                                val minZoomVal = (viewportWidthPx / (d * 24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
                                                val maxZoomVal = (viewportWidthPx / (d * 24f * 2.0f)).coerceIn(4f, 20f)
                                                val currentZoom = module.zoom.coerceIn(minZoomVal, maxZoomVal)
                                                d * 24f * currentZoom
                                            }

                                            awaitPointerEventScope {
                                                while (true) {
                                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                    val pxPerSecond = getPxPerSecond()
                                                    val clickX = down.position.x

                                                    val maxTimeXPx = (module.maxTime * pxPerSecond.toDouble()).toFloat()
                                                    val loopStartXPx = (module.loopStart * pxPerSecond.toDouble()).toFloat()
                                                    val loopEndXPx = (module.loopEnd * pxPerSecond.toDouble()).toFloat()

                                                    val tolerancePx = 12f * density.density

                                                    val distMaxTime = kotlin.math.abs(clickX - maxTimeXPx)
                                                    val distStart = kotlin.math.abs(clickX - loopStartXPx)
                                                    val distEnd = kotlin.math.abs(clickX - loopEndXPx)

                                                    val target = listOf(
                                                        "maxTime" to distMaxTime,
                                                        "loopStart" to distStart,
                                                        "loopEnd" to distEnd
                                                    ).filter { it.second <= tolerancePx }
                                                     .minByOrNull { it.second }?.first

                                                    if (target == "maxTime") {
                                                        down.consume()
                                                        do {
                                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                                            val change = event.changes.firstOrNull()
                                                            if (change != null && change.pressed) {
                                                                change.consume()
                                                                val currentX = change.position.x
                                                                val pxPerSecondDrag = getPxPerSecond()
                                                                val lastTriggerTime = module.triggers.map { it.time }.maxOrNull() ?: 0.0
                                                                val draggedTime = (currentX / pxPerSecondDrag).toDouble()
                                                                module.maxTime = draggedTime.coerceAtLeast(lastTriggerTime).coerceAtLeast(1.0)

                                                                // Scroll timeline when dragged past the end of the viewport
                                                                val d = density.density
                                                                val inputsWidthPx = 70f * d
                                                                val spacingPx = 12f * d
                                                                val viewportWidthPx = (module.width.toFloat() * d - inputsWidthPx - spacingPx).coerceAtLeast(100f * d)
                                                                if (currentX > scrollState.value + viewportWidthPx - 20f) {
                                                                    val targetScroll = (currentX - viewportWidthPx + 20f).roundToInt()
                                                                    coroutineScope.launch {
                                                                        scrollState.scrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
                                                                    }
                                                                } else if (currentX < scrollState.value + 20f) {
                                                                    val targetScroll = (currentX - 20f).roundToInt()
                                                                    coroutineScope.launch {
                                                                        scrollState.scrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
                                                                    }
                                                                }
                                                                FileState.markDirty()
                                                            }
                                                        } while (event.changes.any { it.pressed })
                                                    } else if (target == "loopStart") {
                                                        down.consume()
                                                        do {
                                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                                            val change = event.changes.firstOrNull()
                                                            if (change != null && change.pressed) {
                                                                change.consume()
                                                                val currentX = change.position.x
                                                                val pxPerSecondDrag = getPxPerSecond()
                                                                val timeAtX = (currentX / pxPerSecondDrag).toDouble()
                                                                module.loopStart = timeAtX.coerceIn(0.0, module.loopEnd - 0.1)
                                                                FileState.markDirty()
                                                            }
                                                        } while (event.changes.any { it.pressed })
                                                    } else if (target == "loopEnd") {
                                                        down.consume()
                                                        do {
                                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                                            val change = event.changes.firstOrNull()
                                                            if (change != null && change.pressed) {
                                                                change.consume()
                                                                val currentX = change.position.x
                                                                val pxPerSecondDrag = getPxPerSecond()
                                                                val timeAtX = (currentX / pxPerSecondDrag).toDouble()
                                                                module.loopEnd = timeAtX.coerceIn(module.loopStart + 0.1, module.maxTime)
                                                                FileState.markDirty()
                                                            }
                                                        } while (event.changes.any { it.pressed })
                                                    } else {
                                                        val pxPerSecondDrag = getPxPerSecond()
                                                        var timeAtX = (clickX / pxPerSecondDrag).toDouble().coerceIn(0.0, module.maxTime)
                                                        val isDraggingStart = distStart < distEnd
                                                        if (isDraggingStart) {
                                                            module.loopStart = timeAtX.coerceIn(0.0, module.loopEnd - 0.1)
                                                        } else {
                                                            module.loopEnd = timeAtX.coerceIn(module.loopStart + 0.1, module.maxTime)
                                                        }
                                                        FileState.markDirty()

                                                        do {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull()
                                                            if (change != null && change.pressed) {
                                                                change.consume()
                                                                val currentX = change.position.x
                                                                val pxPerSecondDragInner = getPxPerSecond()
                                                                timeAtX = (currentX / pxPerSecondDragInner).toDouble().coerceIn(0.0, module.maxTime)
                                                                if (isDraggingStart) {
                                                                    module.loopStart = timeAtX.coerceIn(0.0, module.loopEnd - 0.1)
                                                                } else {
                                                                    module.loopEnd = timeAtX.coerceIn(module.loopStart + 0.1, module.maxTime)
                                                                }
                                                                FileState.markDirty()
                                                            }
                                                        } while (event.changes.any { it.pressed })
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    // Highlight loop range
                                    val startPct = (module.loopStart / (module.maxTime + extraTime)).toFloat()
                                    val endPct = (module.loopEnd / (module.maxTime + extraTime)).toFloat()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(endPct - startPct)
                                            .offset(x = timelineWidth * startPct)
                                            .background(Color(0xFF90CAF9).copy(alpha = 0.6f))
                                    )
                                    // Loop Start handle
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp, 16.dp)
                                            .offset(x = timelineWidth * startPct)
                                            .background(Color.Blue)
                                    )
                                    // Loop End handle
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp, 16.dp)
                                            .offset(x = timelineWidth * endPct - 6.dp)
                                            .background(Color.Blue)
                                    )
                                    // Special maxTime marker handle (Red Flag ⇥) - in Row 2!
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp, 16.dp)
                                            .offset(x = maxTimeX - 7.dp)
                                            .background(Color(0xFFD32F2F), RoundedCornerShape(2.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⇥", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Row 3: Horizontal time position indicator (ruler / slider with thumb) - Height 20.dp
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .background(Color(0xFFCCCCCC), RoundedCornerShape(2.dp))
                                        .pointerInput(module) {
                                            val getPxPerSecond = {
                                                val d = density.density
                                                val inputsWidthPx = 70f * d
                                                val spacingPx = 12f * d
                                                val viewportWidthPx = (module.width.toFloat() * d - inputsWidthPx - spacingPx).coerceAtLeast(100f * d)
                                                val minZoomVal = (viewportWidthPx / (d * 24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
                                                val maxZoomVal = (viewportWidthPx / (d * 24f * 2.0f)).coerceIn(4f, 20f)
                                                val currentZoom = module.zoom.coerceIn(minZoomVal, maxZoomVal)
                                                d * 24f * currentZoom
                                            }

                                            awaitPointerEventScope {
                                                while (true) {
                                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                    var currentX = down.position.x
                                                    val pxPerSecond = getPxPerSecond()
                                                    val newSeek = (currentX / pxPerSecond).toDouble().coerceIn(0.0, module.maxTime)
                                                    module.seekTo(newSeek)
                                                    FileState.markDirty()

                                                    do {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull()
                                                        if (change != null && change.pressed) {
                                                            change.consume()
                                                            currentX = change.position.x
                                                            val pxPerSecondDrag = getPxPerSecond()
                                                            val dragSeek = (currentX / pxPerSecondDrag).toDouble().coerceIn(0.0, module.maxTime)
                                                            module.seekTo(dragSeek)
                                                            FileState.markDirty()
                                                        }
                                                    } while (event.changes.any { it.pressed })
                                                }
                                            }
                                        }
                                ) {
                                    // Dynamic Ruler Ticks depending on zoom and density
                                    val possibleIntervals = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 60.0)
                                    val pxPerSecond = density.density * 24f * coercedZoom
                                    val tickInterval = possibleIntervals.firstOrNull { interval ->
                                        interval * pxPerSecond >= 40f
                                    } ?: 60.0
                                    val numTicks = (module.maxTime / tickInterval).toInt()

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val tickColor = Color(0xFF666666)
                                        for (i in 0..numTicks) {
                                            val timeSeconds = i * tickInterval
                                            if (timeSeconds > module.maxTime) break

                                            val pct = (timeSeconds / (module.maxTime + extraTime)).toFloat()
                                            val x = size.width * pct

                                            // Draw main tick line
                                            drawLine(
                                                color = tickColor,
                                                start = Offset(x, size.height - 6f),
                                                end = Offset(x, size.height),
                                                strokeWidth = 1f
                                            )

                                            // Draw minor ticks
                                            if (i < numTicks) {
                                                val nextTime = (i + 1) * tickInterval
                                                val midTime = (timeSeconds + nextTime) / 2.0
                                                if (midTime <= module.maxTime) {
                                                    val midPct = (midTime / (module.maxTime + extraTime)).toFloat()
                                                    val midX = size.width * midPct
                                                    drawLine(
                                                        color = tickColor.copy(alpha = 0.4f),
                                                        start = Offset(midX, size.height - 3f),
                                                        end = Offset(midX, size.height),
                                                        strokeWidth = 0.8f
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Draw text labels
                                    for (i in 0..numTicks) {
                                        val timeSeconds = i * tickInterval
                                        if (timeSeconds > module.maxTime) break
                                        val pct = (timeSeconds / (module.maxTime + extraTime)).toFloat()

                                        val label = if (timeSeconds % 1.0 == 0.0) {
                                            "${timeSeconds.toInt()}"
                                        } else {
                                            val rounded = (timeSeconds * 10.0).roundToInt() / 10.0
                                            "${rounded}"
                                        }

                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            color = Color(0xFF444444),
                                            modifier = Modifier
                                                .offset(x = timelineWidth * pct - 15.dp, y = (-2).dp)
                                                .width(30.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    // Seek thumb
                                    val thumbPct = (module.seekTime / (module.maxTime + extraTime)).toFloat()
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp, 20.dp)
                                            .offset(x = timelineWidth * thumbPct - 4.dp)
                                            .background(Color.DarkGray.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Row 4: cursor, triggers (diamond + jack) - stretches to the bottom of the module
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color(0xFFE5E7EB), RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .pointerInput(module) {
                                            val getPxPerSecond = {
                                                val d = density.density
                                                val inputsWidthPx = 70f * d
                                                val spacingPx = 12f * d
                                                val viewportWidthPx = (module.width.toFloat() * d - inputsWidthPx - spacingPx).coerceAtLeast(100f * d)
                                                val minZoomVal = (viewportWidthPx / (d * 24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
                                                val maxZoomVal = (viewportWidthPx / (d * 24f * 2.0f)).coerceIn(4f, 20f)
                                                val currentZoom = module.zoom.coerceIn(minZoomVal, maxZoomVal)
                                                d * 24f * currentZoom
                                            }

                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val down = event.changes.firstOrNull { !it.previousPressed && it.pressed } ?: continue
                                                    val isShiftHeld = event.keyboardModifiers.isShiftPressed
                                                    val pxPerSecond = getPxPerSecond()
                                                    val clickX = down.position.x
                                                    val clickY = down.position.y

                                                    val tolerancePx = 16f * density.density
                                                    val topYBound = 16f * density.density

                                                    val activeTrigger = if (clickY <= topYBound) {
                                                        module.triggers.minByOrNull {
                                                            val trigXPx = (it.time * pxPerSecond.toDouble()).toFloat()
                                                            kotlin.math.abs(trigXPx - clickX)
                                                        }?.takeIf {
                                                            val trigXPx = (it.time * pxPerSecond.toDouble()).toFloat()
                                                            kotlin.math.abs(trigXPx - clickX) <= tolerancePx
                                                        }
                                                    } else {
                                                        null
                                                    }

                                                    if (activeTrigger != null) {
                                                        down.consume()
                                                        if (isShiftHeld) {
                                                            module.removeTrigger(activeTrigger)
                                                            FileState.markDirty()
                                                        } else {
                                                            do {
                                                                val dragEvent = awaitPointerEvent(PointerEventPass.Initial)
                                                                val change = dragEvent.changes.firstOrNull()
                                                                if (change != null && change.pressed) {
                                                                    change.consume()
                                                                    val currentX = change.position.x
                                                                    val pxPerSecondDrag = getPxPerSecond()
                                                                    activeTrigger.time = (currentX / pxPerSecondDrag).toDouble().coerceIn(0.0, module.maxTime)
                                                                    FileState.markDirty()
                                                                }
                                                            } while (dragEvent.changes.any { it.pressed })
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    // Red cursor
                                    val cursorPct = (currentPlayheadTime / (module.maxTime + extraTime)).toFloat().coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(2.dp)
                                            .offset(x = timelineWidth * cursorPct)
                                            .background(Color.Red)
                                    )

                                    // Shaded unusable area past maxTime
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(timelineWidth - maxTimeX)
                                            .offset(x = maxTimeX)
                                            .background(Color(0x1F000000))
                                    )

                                    // Vertical marker boundary line extending down through Row 4
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .offset(x = maxTimeX)
                                            .background(Color(0xFFD32F2F).copy(alpha = 0.5f))
                                    )

                                    // Draw triggers
                                    for (trigger in module.triggers) {
                                        val trigPct = (trigger.time / (module.maxTime + extraTime)).toFloat()
                                        val triggerX = timelineWidth * trigPct

                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(24.dp)
                                                .offset(x = triggerX - 12.dp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.fillMaxHeight().padding(top = 2.dp, bottom = 0.dp)
                                            ) {
                                                // Diamond at top (8.dp)
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .graphicsLayer(rotationZ = 45f)
                                                        .background(Color.Yellow)
                                                )
                                                // Vertical line (dark stem)
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .weight(1f)
                                                        .background(Color(0xFF4B5563))
                                                )
                                                // Jack at bottom (8.dp)
                                                TimelineTriggerJackView(trigger.outputJack, state, viewportLeftGraphX, viewportRightGraphX)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Horizontal Resize grip on the bottom-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .pointerInput(module) {
                            detectDragGestures(
                                onDragEnd = { FileState.markDirty() }
                            ) { change, dragAmount ->
                                change.consume()
                                val dw = dragAmount.x / density.density
                                module.width = (module.width + dw).coerceAtLeast(200.0)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val grip = colors.faderGrip
                        val w = size.width
                        val h = size.height
                        drawLine(grip, Offset(w - 4f, h - 12f), Offset(w - 4f, h - 2f), strokeWidth = 1.5f)
                        drawLine(grip, Offset(w - 8f, h - 12f), Offset(w - 8f, h - 2f), strokeWidth = 1.5f)
                    }
                }
            }
        }
    }
}




