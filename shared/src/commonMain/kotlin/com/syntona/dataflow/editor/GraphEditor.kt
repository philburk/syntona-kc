package com.syntona.dataflow.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.syntona.dataflow.modules.ShowModule
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun GraphEditor(state: GraphState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1E1E1E))
        .onGloballyPositioned { state.updateRootOffset(it.positionInRoot()) }
    ) {
        // Draw Wires behind modules
        WireRenderer(state)

        // Draw Modules
        state.modules.forEach { module ->
            ModuleNode(module, state)
        }

        // Draw dragged module shadow from tree
        state.draggedModuleCreator?.let {
            Box(
                modifier = Modifier
                    .offset {
                        val pos = state.draggedModulePosition - state.rootOffset
                        IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                    }
                    .width(140.dp)
                    .height(60.dp)
                    .background(Color.Cyan.copy(alpha = 0.3f))
                    .border(1.dp, Color.Cyan, RoundedCornerShape(4.dp))
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
fun ModuleNode(module: Module, state: GraphState) {
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
                    .background(Color.Blue)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state.updateModulePosition(module, dragAmount)
                        }
                    }
            ) {
                Text(
                    text = module.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Show custom content for ShowModule
            if (module is ShowModule) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.Black)) {
                    Text(
                        text = module.displayValue.toString().take(6),
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

private fun formatValue(value: Double): String = "%.4g".format(value)

@Composable
fun JackView(jack: Jack, state: GraphState, isLeft: Boolean) {
    val connectorColor = if (jack is InputJack && jack.isKickable) Color.Red else Color.Cyan
    val textColor = if (jack.isHighlighted) Color.Black else Color.LightGray
    val backgroundColor = if (jack.isHighlighted) Color(180, 170, 130) else Color.DarkGray

    var isHovered by remember { mutableStateOf(false) }
    var isValueDragging by remember { mutableStateOf(false) }
    var valueDragStartValue by remember { mutableStateOf(0.0) }
    var valueDragCurrentValue by remember { mutableStateOf(0.0) }
    var valueDragAccumX by remember { mutableStateOf(0f) }
    var valueDragAccumY by remember { mutableStateOf(0f) }

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
                val size = layoutCoordinates.size
                val pos = layoutCoordinates.positionInRoot() - state.rootOffset
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
                    onDragStart = { _ ->
                        val unconnectedInput = jack is InputJack &&
                            state.connections.none { it.input == jack }
                        if (unconnectedInput) {
                            val startVal = (jack as InputJack).getValue()
                            valueDragStartValue = startVal
                            valueDragCurrentValue = startVal
                            valueDragAccumX = 0f
                            valueDragAccumY = 0f
                            isValueDragging = true
                        } else {
                            if (jack is OutputJack) state.startDraggingFromOutput(jack)
                            else if (jack is InputJack) state.startDraggingFromInput(jack)
                            state.onPointerMove(state.jackOffsets[jack] ?: Offset.Zero)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isValueDragging) {
                            valueDragAccumX += dragAmount.x
                            valueDragAccumY += dragAmount.y
                            // X position controls sensitivity: left = coarse, right = fine
                            // Every 200 px shifts sensitivity by one decade
                            val sensitivity = 0.01 * 10.0.pow(-valueDragAccumX / 200.0)
                            val newValue = valueDragStartValue - valueDragAccumY * sensitivity
                            valueDragCurrentValue = newValue
                            (jack as InputJack).setValue(newValue)
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
