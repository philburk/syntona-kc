package com.syntona.dataflow.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.dataflow.model.*
import com.syntona.dataflow.modules.DoubleEntryModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.ShowModule
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun GraphEditor(state: GraphState, modifier: Modifier = Modifier) {
    val mdiState = LocalMdiState.current
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1E1E1E))
        .onGloballyPositioned { state.updateRootOffset(it.positionInRoot()) }
        .pointerInput(state) { detectTapGestures { mdiState.activeGraphState = state } }
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
                is InputModule -> ModuleNode(module, state, headerColor = Color(0xFF6A3D00), label = module.displayName)
                is OutputModule -> ModuleNode(module, state, headerColor = Color(0xFF6A3D00), label = module.displayName)
                else -> ModuleNode(module, state)
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
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    // Create a smooth Bezier curve
                    val controlX = (start.x + end.x) / 2
                    cubicTo(controlX, start.y, controlX, end.y, end.x, end.y)
                }
                drawPath(path, color = Color.Gray, style = Stroke(width = 2f))
            }
        }

        // Draw rubber-band wire as a simple straight line
        val draggingOutput = state.draggingWireByOutput
        if (draggingOutput != null) {
            val start = state.jackOffsets[draggingOutput] ?: Offset.Zero
            val end = state.dragPointerPosition
            if (start != Offset.Zero) {
                drawLine(
                    color = Color.Yellow,
                    start = start,
                    end = end,
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
fun ModuleNode(module: Module, state: GraphState, headerColor: Color = Color.Blue, label: String = module.name) {
    val position = state.modulePositions[module] ?: Offset.Zero

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(140.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(headerColor)
                    .pointerInput("drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state.updateModulePosition(module, dragAmount)
                        }
                    }
                    .pointerInput("tap") {
                        detectTapGestures(
                            onDoubleTap = { state.editingModule = module }
                        )
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

            // Jacks: Inputs on left, Outputs on right
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

private fun formatValue(value: Double): String = formatNumber(value)

@Composable
fun JackView(jack: Jack, state: GraphState, isLeft: Boolean) {
    val connectorColor = if (jack is InputJack && jack.isKickable) Color.Red else Color.Cyan
    val textColor = if (jack.isHighlighted) Color.Black else Color.LightGray
    val backgroundColor = if (jack.isHighlighted) Color(180, 170, 130) else Color.DarkGray

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
        modifier = Modifier
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
                            valueDragCurrentValue = (jack as InputJack).getValue()
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
                            inputJack.setValue(newValue)
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

/**
 * Connector dot with wire-drag support but no name label.
 * Used by compact module layouts such as [DoubleEntryModuleNode].
 */
@Composable
fun CompactJackConnector(jack: Jack, state: GraphState, isLeft: Boolean) {
    val connectorColor = if (jack is InputJack && jack.isKickable) Color.Red else Color.Cyan
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(12.dp)
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

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(70.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color.Blue)
                    .pointerInput("drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state.updateModulePosition(module, dragAmount)
                        }
                    }
                    .pointerInput("tap") {
                        detectTapGestures(onDoubleTap = { state.editingModule = module })
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
                    onValueChange = { module.setValue(it.toDouble()) },
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
                onCommit = { module.setValue(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PatchModuleNode(module: PatchModule, state: GraphState) {
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(120.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF1A3A2A)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color(0xFF2E7D52))
                    .pointerInput("drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state.updateModulePosition(module, dragAmount)
                        }
                    }
                    .pointerInput("tap") {
                        detectTapGestures(
                            onDoubleTap = {
                                mdiState.open(
                                    MdiWindowInfo(
                                        id = module,
                                        title = module.name,
                                        position = Offset(
                                            (state.modulePositions[module]?.x ?: 100f) + 20f,
                                            (state.modulePositions[module]?.y ?: 100f) + 30f
                                        ),
                                        graphState = module.graphState,
                                        content = { GraphEditor(state = module.graphState) }
                                    )
                                )
                            }
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

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(140.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = Color.DarkGray
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color.Blue)
                    .pointerInput("drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state.updateModulePosition(module, dragAmount)
                        }
                    }
                    .pointerInput("tap") {
                        detectTapGestures(onDoubleTap = { state.editingModule = module })
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
                    onCommit = { module.setValue(it) },
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
fun KnobModuleNode(module: KnobModule, state: GraphState) {
    val position = state.modulePositions[module] ?: Offset.Zero

    Surface(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(90.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
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
                    .pointerInput("drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state.updateModulePosition(module, dragAmount)
                        }
                    }
                    .pointerInput("tap") {
                        detectTapGestures(onDoubleTap = { state.editingModule = module })
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
                    onValueChange = { module.setValue(it.toDouble()) },
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
                onCommit = { module.setValue(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
