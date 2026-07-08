package com.syntona.dataflow.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
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
import com.syntona.dataflow.modules.DoubleEntryModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.ScopeModule
import com.syntona.dataflow.modules.ShowModule
import com.syntona.dataflow.modules.CommentModule
import com.syntona.dataflow.modules.SequenceModule
import com.syntona.dataflow.modules.SegmentedEnvelopeModule
import com.syntona.dataflow.modules.LinkModule
import com.syntona.dataflow.modules.KeyMatchModule
import com.syntona.dataflow.modules.KickModule
import com.syntona.dataflow.modules.LoadKickModule
import com.syntona.dataflow.modules.ShowVoiceModule
import com.softsynth.ksyn.compose.SegmentedEnvelopeEditor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun GraphEditor(state: GraphState, modifier: Modifier = Modifier) {
    val mdiState = LocalMdiState.current
    val focusRequester = remember { FocusRequester() }
    val editingModule = state.editingModule
    LaunchedEffect(editingModule) {
        if (editingModule == null) {
            focusRequester.requestFocus()
        }
    }
    val isKeyboardActive = state.isKeyboardModeActive
    LaunchedEffect(isKeyboardActive) {
        if (isKeyboardActive) {
            focusRequester.requestFocus()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1E1E1E))
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { keyEvent ->
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
                    state.selectModulesInRect(rectStart, rectEnd)
                }
                state.selectionRectStart = null
                state.selectionRectEnd = null
            }
        }
    ) {
        // Draw Wires behind modules
        WireRenderer(state)

        // Draw Modules — add cases here for modules with custom layouts
        state.modules.forEach { module ->
            when (module) {
                is DoubleEntryModule -> DoubleEntryModuleNode(module, state)
                is FaderModule -> FaderModuleNode(module, state)
                is KnobModule -> KnobModuleNode(module, state)
                is PatchModule -> PatchModuleNode(module, state)
                is SequenceModule -> SequenceModuleNode(module, state)
                is InputModule -> ModuleNode(module, state, headerColor = Color(0xFF6A3D00), label = module.name)
                is OutputModule -> ModuleNode(module, state, headerColor = Color(0xFF6A3D00), label = module.name)
                is AudioInputModule -> ModuleNode(module, state, headerColor = Color(0xFF1A5C1A), label = module.name)
                is AudioOutputModule -> ModuleNode(module, state, headerColor = Color(0xFF1A5C1A), label = module.name)
                is ScopeModule -> ScopeModuleNode(module, state)
                is CommentModule -> CommentModuleNode(module, state)
                is SegmentedEnvelopeModule -> SegmentedEnvelopeModuleNode(module, state)
                is KeyMatchModule -> KeyMatchModuleNode(module, state)
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
                    color = Color.Cyan,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawRect(
                    color = Color.Cyan.copy(alpha = 0.1f),
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

@Composable
fun WireRenderer(state: GraphState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        state.connections.forEach { conn ->
            val start = state.jackOffsets[conn.output] ?: Offset.Zero
            val end = state.jackOffsets[conn.input] ?: Offset.Zero

            if (start != Offset.Zero && end != Offset.Zero) {
                val isAudio = conn.output is AudioOutputJack
                val wireColor = if (isAudio) Color(0xFF44DD44) else Color.Gray
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    val controlX = (start.x + end.x) / 2
                    cubicTo(controlX, start.y, controlX, end.y, end.x, end.y)
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
                val isAudio = draggingOutput is AudioOutputJack
                drawLine(
                    color = if (isAudio) Color(0xFF44DD44) else Color.Yellow,
                    start = start,
                    end = end,
                    strokeWidth = 3f
                )
            }
        }
    }
}

@Composable
fun ModuleNode(module: Module, state: GraphState, headerColor: Color = Color.Blue, label: String = module.name) {
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(headerColor)
                    .headerGestures(state, module) {
                        state.editingModule = module
                    }
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Show custom content for ShowModule
            if (module is ShowModule) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.Black)) {
                    Text(
                        text = formatNumber(module.displayValue),
                        color = Color.Green,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Show custom content for ShowVoiceModule: last N VoiceMessages, one per line
            if (module is ShowVoiceModule) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.Black)
                ) {
                    module.messages.forEach { msg ->
                        Text(
                            text = "${msg.opcode} t=${msg.tag} v=${formatNumber(msg.value)} x=${formatNumber(msg.extra)}",
                            color = Color.Green,
                            fontSize = 10.sp
                        )
                    }
                }
            }




            // Jacks: inputs at top, outputs at bottom, single column
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
            }
            module.outputs.forEach { JackView(it, state, isLeft = false) }
        }
    }
}

@Composable
fun KeyMatchModuleNode(module: KeyMatchModule, state: GraphState) {
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .size(width = 80.dp, height = 80.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            // Drag handle (header bar matching standard modules)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color.Blue)
                    .headerGestures(state, module) {
                        state.editingModule = module
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp).align(Alignment.CenterStart)
                )
            }

            // Body containing the button and unlabelled jacks
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Button
                var isPressed by remember { mutableStateOf(false) }
                val buttonBg = if (isPressed) Color.Gray else Color(0xFF3E3E3E)

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

                // Unlabelled Jacks stacked vertically
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.End
                ) {
                    UnlabelledJackView(module.on, state)
                    UnlabelledJackView(module.off, state)
                }
            }
        }
    }
}

@Composable
fun UnlabelledJackView(jack: Jack, state: GraphState) {
    val connectorColor = Color.Cyan
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(12.dp)
            .onGloballyPositioned { layoutCoordinates ->
                jackRootPosition = layoutCoordinates.positionInRoot()
                val size = layoutCoordinates.size
                val pos = jackRootPosition - state.rootOffset
                val edgeOffset = Offset(size.width.toFloat(), size.height / 2f)
                state.updateJackOffset(jack, pos + edgeOffset)
            }
            .pointerInput(jack) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        if (jack is OutputJack) state.startDraggingFromOutput(jack)
                        state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onPointerMove(state.dragPointerPosition + dragAmount)
                    },
                    onDragEnd = {
                        state.onPointerUp(null)
                    },
                    onDragCancel = {
                        state.onPointerUp(null)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(connectorColor)
                .align(Alignment.Center)
        )
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
    val connectorColor = when {
        jack is InputJack && jack.isKickable -> Color.Red
        jack is AudioInputJack || jack is AudioOutputJack -> Color(0xFF44DD44)
        jack is DataQueueInputJack || jack is DataQueueOutputJack -> Color.Yellow
        else -> Color.Cyan
    }
    val textColor = if (jack.isHighlighted) Color.Black else Color.LightGray
    val backgroundColor = when {
        jack.isHighlighted -> Color(180, 170, 130)
        jack is AudioInputJack || jack is AudioOutputJack -> Color(0xFF1A3A1A)
        jack is DataQueueInputJack || jack is DataQueueOutputJack -> Color(0xFF3A3A1A)
        else -> Color.DarkGray
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
                state.updateJackOffset(jack, pos + edgeOffset)
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
            // Drag detection: value edit or wire drag depending on jack state
            .pointerInput(jack) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val unconnectedInput = jack is InputJack &&
                            state.connections.none { it.input == jack }
                        if (unconnectedInput) {
                            valueDragCurrentValue = jack.getValue()
                            valueDragAccumX = 0f
                            isValueDragging = true
                        } else {
                            if (jack is OutputJack) state.startDraggingFromOutput(jack)
                            else if (jack is InputJack) state.startDraggingFromInput(jack)
                            // Place the rubber-band end at the actual pointer position,
                            // not at the jack edge, to avoid an initial offset.
                            state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isValueDragging) {
                            val inputJack = jack as InputJack
                            valueDragAccumX += dragAmount.x
                            // Apply current sensitivity to this frame's delta only (incremental).
                            // Left = coarser, right = finer. Using accumulated Y would
                            // retroactively re-scale all past movement when X changes.
                            val range = inputJack.max - inputJack.min
                            val sensitivity = range / 400.0 * 10.0.pow(-valueDragAccumX / 200.0)
                            val newValue = (valueDragCurrentValue - dragAmount.y * sensitivity)
                                .coerceIn(inputJack.min, inputJack.max)
                            valueDragCurrentValue = newValue
                            val e = state.engine
                            if (e != null) e.post(SetValueCommand(inputJack, newValue, ScoreClock.now()))
                            else inputJack.setValue(newValue)
                        } else {
                            state.onPointerMove(state.dragPointerPosition + dragAmount)
                        }
                    },
                    onDragEnd = {
                        if (isValueDragging) {
                            isValueDragging = false
                        } else {
                            state.onPointerUp(null)
                        }
                    },
                    onDragCancel = {
                        isValueDragging = false
                        state.onPointerUp(null)
                    }
                )
            }
    ) {
        if (isLeft) {
            Box(modifier = Modifier.size(8.dp).background(connectorColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text(displayText, color = textColor, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
            Text(displayText, color = textColor, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).background(connectorColor))
        }
    }
}

@Composable
fun CommandJackView(jack: CommandJack, state: GraphState, isLeft: Boolean) {
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
                state.updateJackOffset(jack, jackRootPosition - state.rootOffset + edgeOffset)
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
            Box(modifier = Modifier.size(8.dp).background(Color.Red))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Button(
            onClick = {
                val e = state.engine
                if (e != null) {
                    e.post(KickJackCommand(jack, ScoreClock.now()))
                } else {
                    jack.receive(com.syntona.dataflow.model.Message.FloatValue(ScoreTime.Zero, 0.0))
                }
            },
            modifier = Modifier.height(24.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(jack.name, fontSize = 10.sp)
        }
        if (!isLeft) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).background(Color.Red))
        }
    }
}

@Composable
fun CommandOutputJackView(jack: CommandOutputJack, state: GraphState, isLeft: Boolean) {
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
                state.updateJackOffset(jack, jackRootPosition - state.rootOffset + edgeOffset)
            }
            .pointerInput(jack) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        state.startDraggingFromOutput(jack)
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
            Box(modifier = Modifier.size(8.dp).background(Color.Red))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Button(
            onClick = {
                val e = state.engine
                val time = ScoreClock.now()
                if (e != null) {
                    e.post(SendOutputCommand(jack, Message.FloatValue(time, 0.0)))
                } else {
                    jack.send(Message.FloatValue(time, 0.0))
                }
            },
            modifier = Modifier.height(20.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) {
            Text("Kick", fontSize = 10.sp)
        }
        if (!isLeft) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).background(Color.Red))
        }
    }
}

/**
 * Connector dot with wire-drag support but no name label.
 * Used by compact module layouts such as [DoubleEntryModuleNode].
 */
@Composable
fun CompactJackConnector(jack: Jack, state: GraphState, isLeft: Boolean) {
    val connectorColor = when {
        jack is InputJack && jack.isKickable -> Color.Red
        jack is AudioInputJack || jack is AudioOutputJack -> Color(0xFF44DD44)
        jack is DataQueueInputJack || jack is DataQueueOutputJack -> Color.Yellow
        else -> Color.Cyan
    }
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(12.dp)
            .then(if (jack.isHighlighted) Modifier.background(Color(190, 180, 130), shape = RoundedCornerShape(3.dp)) else Modifier)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val edgeX = if (isLeft) 0f else size.width.toFloat()
                state.updateJackOffset(
                    jack,
                    jackRootPosition - state.rootOffset + Offset(edgeX, size.height / 2f)
                )
            }
            .pointerInput(jack) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        if (jack is OutputJack) state.startDraggingFromOutput(jack)
                        else if (jack is InputJack) state.startDraggingFromInput(jack)
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
        Box(modifier = Modifier.size(8.dp).background(connectorColor))
    }
}

@Composable
fun FaderModuleNode(module: FaderModule, state: GraphState) {
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(70.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color.Blue)
                    .headerGestures(state, module) {
                        state.editingModule = module
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

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
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(120.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF1A3A2A)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color(0xFF2E7D52))
                    .headerGestures(state, module) {
                        val savedX = module.windowX
                        val savedY = module.windowY
                        val restoredPosition = if (savedX != null && savedY != null) {
                            Offset(savedX * density.density, savedY * density.density)
                        } else {
                            Offset(
                                (state.modulePositions[module]?.x ?: 100f) + 20f,
                                (state.modulePositions[module]?.y ?: 100f) + 30f
                            )
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
                        // Explicit callback (fired directly from MdiState's mutation sites)
                        // rather than reactive recomposition, so Save always sees current geometry.
                        window.onGeometryChanged = {
                            module.windowX = window.position.x / density.density
                            module.windowY = window.position.y / density.density
                            module.windowWidth = window.width.value
                            module.windowHeight = window.height.value
                        }
                        mdiState.open(window)
                    }
            ) {
                val displayName = if (module is PolyPatchModule) "${module.name}[${module.maxVoices}]" else module.name
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    module.inputs.forEach { JackView(it, state, isLeft = true) }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    module.outputs.forEach { JackView(it, state, isLeft = false) }
                }
            }
        }
    }
}

@Composable
fun DoubleEntryModuleNode(module: DoubleEntryModule, state: GraphState) {
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(140.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color.Blue)
                    .headerGestures(state, module) {
                        state.editingModule = module
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

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
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(110.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0xFF2B506E))
                    .headerGestures(state, module) {
                        state.editingModule = module
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            val controlInputs = module.inputs.filter { it !== module.reset && it !== module.increment && !module.stepInputs.contains(it) }
            val controlOutputs = module.outputs.filter { !module.stepOutputs.contains(it) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    CompactJackConnector(module.reset, state, isLeft = true)
                    CompactJackConnector(module.increment, state, isLeft = true)
                    controlInputs.forEach { CompactJackConnector(it, state, isLeft = true) }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    controlOutputs.forEach { CompactJackConnector(it, state, isLeft = false) }
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
                        NumberField(
                            value = module.values[i],
                            onCommit = { v ->
                                val e = state.engine
                                if (e != null) e.post(SetValueCommand(module.stepInputs[i], v, ScoreClock.now()))
                                else module.stepInputs[i].setValue(v)
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
fun KnobModuleNode(module: KnobModule, state: GraphState) {
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(90.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            // Title bar — drag to move, double-tap to open editor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color(0xFF1565C0))
                    .headerGestures(state, module) {
                        state.editingModule = module
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

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
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val density = LocalDensity.current

    // Local state for smooth dragging, initialized from module's state
    var currentWidth by remember(module, initialWidth) { mutableStateOf(initialWidth) }
    var currentHeight by remember(module, initialHeight) { mutableStateOf(initialHeight) }

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            Box(
                modifier = Modifier
                    .width(currentWidth)
                    .height(24.dp)
                    .background(headerColor)
                    .headerGestures(state, module) {
                        state.editingModule = module
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

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
                        val grip = Color(0xFF888888)
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
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = Color(0xFF4A4A4A),
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
            modifier = Modifier.fillMaxSize().padding(8.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp)
        )
    }
}

@Composable
fun ScopeModuleNode(module: ScopeModule, state: GraphState) {
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = Color(0xFF1A3A1A),
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
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) Color(0xFFFFA500) else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color(0xFF6A3D00))
                    .headerGestures(state, module) {
                        mdiState.open(
                            MdiWindowInfo(
                                id = module,
                                title = module.name,
                                position = Offset(
                                    (state.modulePositions[module]?.x ?: 100f) + 20f,
                                    (state.modulePositions[module]?.y ?: 100f) + 30f
                                ),
                                content = {
                                    val localMdi = LocalMdiState.current
                                    val windowInfo = remember { localMdi.windows.find { it.id == module } }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF1E1E1E))
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
                                            SegmentedEnvelopeEditor(module.envelope)
                                        }
                                    }
                                }
                            )
                        )
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            // Draw jacks.
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
            }
            module.outputs.forEach { JackView(it, state, isLeft = false) }
        }
    }
}


fun Modifier.headerGestures(
    state: GraphState,
    module: Module,
    onDoubleTap: () -> Unit
): Modifier = this.pointerInput(module, state) {
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        if (!state.selectedModules.contains(module)) {
            state.clearSelection()
            state.selectModule(module)
        }
        var wasDragged = false
        drag(down.id) { change ->
            change.consume()
            wasDragged = true
            val dragAmount = change.position - change.previousPosition
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

