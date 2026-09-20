package com.syntona.dataflow.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.dataflow.modules.MessageSectionData
import com.syntona.dataflow.modules.MessageTrack
import com.syntona.dataflow.modules.TrackMessageEvent
import com.syntona.dataflow.modules.TrackSection
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt


@Composable
fun MessageColumnEditor(
    section: TrackSection,
    track: MessageTrack,
    modifier: Modifier = Modifier
) {
    val data = section.data as? MessageSectionData ?: remember(section) {
        MessageSectionData().also { section.data = it }
    }
    // Section data is also read and recorded on the engine thread, so edit it there.
    val runOnEngine = track.timeline::runOnEngine

    var selectedEvent by remember { mutableStateOf<TrackMessageEvent?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val mdiState = LocalMdiState.current
    DisposableEffect(mdiState, section, selectedEvent) {
        val window = mdiState.windows.firstOrNull { it.id === section }
        if (window != null) {
            window.editHandler = object : EditActionHandler {
                override fun canCopy(): Boolean = false
                override fun canPaste(): Boolean = false
                override fun canDelete(): Boolean = selectedEvent != null
                override fun cut() {}
                override fun copy() {}
                override fun paste() {}
                override fun delete() {
                    val ev = selectedEvent
                    if (ev != null) {
                        runOnEngine { data.removeEvent(ev) }
                        selectedEvent = null
                        FileState.markDirty()
                    }
                }
            }
        }
        onDispose {
            if (window != null && window.editHandler != null) {
                window.editHandler = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace)
                ) {
                    val ev = selectedEvent
                    if (ev != null) {
                        runOnEngine { data.removeEvent(ev) }
                        selectedEvent = null
                        FileState.markDirty()
                    }
                    return@onKeyEvent true
                }
                false
            }
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${track.name} : ${section.name.ifEmpty { "Section ${section.id + 1}" }}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${data.events.size} Events",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF333333), RoundedCornerShape(3.dp))
                        .clickable {
                            runOnEngine { data.clearEvents() }
                            selectedEvent = null
                            FileState.markDirty()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Clear",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp
                    )
                }

                val curSel = selectedEvent
                if (curSel != null) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFC62828), RoundedCornerShape(3.dp))
                            .clickable {
                                runOnEngine { data.removeEvent(curSel) }
                                selectedEvent = null
                                FileState.markDirty()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Delete Point",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "t: ${formatNumber(curSel.time)}  v: ${formatNumber(curSel.value)}",
                        color = Color(0xFF80CBC4),
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Presets:", color = Color.Gray, fontSize = 10.sp)
                listOf(1.0 to "0..1", 10.0 to "0..10", 127.0 to "0..127", 1000.0 to "0..1k").forEach { (r, label) ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2C2C2C), RoundedCornerShape(2.dp))
                            .clickable {
                                runOnEngine {
                                    data.min = 0.0
                                    data.max = r
                                }
                                FileState.markDirty()
                            }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(label, color = Color(0xFFB0BEC5), fontSize = 9.sp)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dur: ${formatNumber(section.duration)}", color = Color.Gray, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Editor Area: Y-Axis labels, Interactive Canvas, and Vertical Range Slider
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            // Y-Axis Ruler
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(formatNumber(data.max), color = Color.Gray, fontSize = 9.sp)
                Text(formatNumber((data.max + data.min) / 2.0), color = Color(0xFF555555), fontSize = 9.sp)
                Text(formatNumber(data.min), color = Color.Gray, fontSize = 9.sp)
            }

            // Interactive Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF141414), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                    .pointerInput(data, section.duration) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            focusRequester.requestFocus()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val range = (data.max - data.min).takeIf { it > 0.0 } ?: 1.0
                            val dur = section.duration.coerceAtLeast(0.001)

                            val posToTime = { x: Float ->
                                ((x / w).toDouble() * dur).coerceIn(0.0, dur)
                            }
                            val posToVal = { y: Float ->
                                val normY = (1f - (y / h)).coerceIn(0f, 1f).toDouble()
                                data.min + normY * range
                            }

                            val clickX = down.position.x
                            val clickY = down.position.y

                            // Check if an existing event is clicked (circle or stem proximity)
                            val hitEvent = data.events.minByOrNull { ev ->
                                val evX = (ev.time / dur).toFloat() * w
                                val normV = ((ev.value - data.min) / range).coerceIn(0.0, 1.0).toFloat()
                                val evY = (1f - normV) * h
                                val headDist = hypot(clickX - evX, clickY - evY)
                                val stemDist = if (clickY >= evY - 8f && clickY <= h + 8f) {
                                    kotlin.math.abs(clickX - evX)
                                } else Float.MAX_VALUE
                                minOf(headDist, stemDist)
                            }?.takeIf { ev ->
                                val evX = (ev.time / dur).toFloat() * w
                                val normV = ((ev.value - data.min) / range).coerceIn(0.0, 1.0).toFloat()
                                val evY = (1f - normV) * h
                                val headDist = hypot(clickX - evX, clickY - evY)
                                val stemDist = if (clickY >= evY - 8f && clickY <= h + 8f) {
                                    kotlin.math.abs(clickX - evX)
                                } else Float.MAX_VALUE
                                headDist <= 22f || stemDist <= 10f
                            }

                            val isAlt = currentEvent.keyboardModifiers.isAltPressed
                            var activeEvent: TrackMessageEvent? = null

                            if (hitEvent != null) {
                                if (isAlt) {
                                    // Alt-click deletes event
                                    runOnEngine { data.removeEvent(hitEvent) }
                                    selectedEvent = null
                                    FileState.markDirty()
                                    return@awaitEachGesture
                                } else {
                                    activeEvent = hitEvent
                                    selectedEvent = hitEvent
                                }
                            } else {
                                // Click on empty space creates a new event
                                val newTime = posToTime(clickX)
                                val newVal = posToVal(clickY)
                                val ev = TrackMessageEvent(newTime, newVal)
                                runOnEngine { data.insertEvent(ev) }
                                activeEvent = ev
                                selectedEvent = ev
                                FileState.markDirty()
                            }

                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                change.consume()

                                val curEv = activeEvent
                                val t = posToTime(change.position.x)
                                val v = posToVal(change.position.y)
                                runOnEngine {
                                    curEv.time = t
                                    curEv.value = v
                                    if (v > data.max) data.max = v
                                    if (v < data.min) data.min = v
                                }
                                FileState.markDirty()
                            }

                            // Keep events sorted by time after drag
                            runOnEngine { data.events.sortBy { it.time } }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val dur = section.duration.coerceAtLeast(0.001)
                    val range = (data.max - data.min).takeIf { it > 0.0 } ?: 1.0

                    // Subtle horizontal grid lines (25%, 50%, 75%)
                    for (i in 1..3) {
                        val y = h * (i / 4f)
                        drawLine(
                            color = Color(0xFF222222),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    // Subtle vertical time grid lines (every 1/4 of duration)
                    for (i in 1..3) {
                        val x = w * (i / 4f)
                        drawLine(
                            color = Color(0xFF222222),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    // Render discrete events: skinny columns with circle at (time, value)
                    data.events.forEach { ev ->
                        val evX = ((ev.time / dur).toFloat() * w).coerceIn(0f, w)
                        val normV = ((ev.value - data.min) / range).coerceIn(0.0, 1.0).toFloat()
                        val evY = ((1f - normV) * h).coerceIn(0f, h)
                        val isCur = ev === selectedEvent

                        // Skinny column (stem) from bottom to value circle
                        drawLine(
                            color = if (isCur) Color(0xFF4DB6AC) else Color(0xFF26A69A).copy(alpha = 0.7f),
                            start = Offset(evX, h),
                            end = Offset(evX, evY),
                            strokeWidth = if (isCur) 2.5f else 1.5f
                        )

                        // Outer circle
                        drawCircle(
                            color = if (isCur) Color.White else Color(0xFF80CBC4),
                            radius = if (isCur) 6.dp.toPx() else 4.5f.dp.toPx(),
                            center = Offset(evX, evY)
                        )

                        // Inner circle
                        drawCircle(
                            color = if (isCur) Color(0xFF00796B) else Color(0xFF004D40),
                            radius = if (isCur) 3.5f.dp.toPx() else 2.5f.dp.toPx(),
                            center = Offset(evX, evY)
                        )
                    }
                }
            }

            // Vertical Range Slider
            val currentRange = (data.max - data.min).coerceAtLeast(0.1)
            val minExp = -1f  // 10^-1 = 0.1
            val maxExp = 3f   // 10^3 = 1000.0 (or higher if range exceeds 1000)
            val effectiveMaxExp = maxOf(maxExp, log10(currentRange.toFloat()).coerceAtLeast(1f))
            val curExp = log10(currentRange.toFloat())
            val rangeFraction = ((curExp - minExp) / (effectiveMaxExp - minExp)).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Range", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    formatNumber(currentRange),
                    color = Color(0xFF80CBC4),
                    fontSize = 8.sp
                )
                VerticalSlider(
                    value = rangeFraction,
                    onValueChange = { frac ->
                        val p = minExp + frac * (effectiveMaxExp - minExp)
                        val r = 10.0.pow(p.toDouble())
                        val roundedR = when {
                            r < 1.0 -> ((r * 10.0).roundToInt() / 10.0)
                            r < 20.0 -> ((r * 2.0).roundToInt() / 2.0)
                            r < 200.0 -> r.roundToInt().toDouble()
                            else -> ((r / 10.0).roundToInt() * 10.0)
                        }.coerceAtLeast(0.1)
                        runOnEngine { data.max = data.min + roundedR }
                        FileState.markDirty()
                    },
                    modifier = Modifier.weight(1f).width(24.dp),
                    valueRange = 0f..1f,
                    trackColor = Color(0xFF2A2A2A),
                    fillColor = Color(0xFF26A69A),
                    thumbColor = Color(0xFF80CBC4)
                )
            }
        }
    }
}
