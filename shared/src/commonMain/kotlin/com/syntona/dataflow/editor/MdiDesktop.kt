package com.syntona.dataflow.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun MdiDesktop(
    mdiState: MdiState,
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        background()

        // Floating windows, rendered in list order so last = topmost
        mdiState.windows.forEach { window ->
            if (!window.minimized) {
                MdiWindowFrame(window, mdiState)
            }
        }

        // Minimized windows shown as title bars along the bottom
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            mdiState.windows.filter { it.minimized }.forEach { window ->
                MdiMinimizedBar(window, mdiState)
            }
        }
    }
}

@Composable
fun MdiWindowFrame(window: MdiWindowInfo, mdiState: MdiState) {
    val w = if (window.expanded) window.normalWidth * 1.6f else window.normalWidth
    val h = if (window.expanded) window.normalHeight * 1.6f else window.normalHeight

    Surface(
        modifier = Modifier
            .offset { IntOffset(window.position.x.roundToInt(), window.position.y.roundToInt()) }
            .width(w)
            .height(h)
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(8.dp))
            .pointerInput(window.id) {
                detectTapGestures { mdiState.bringToFront(window) }
            },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2D2D2D),
        shadowElevation = 8.dp
    ) {
        Column {
            MdiTitleBar(window, mdiState)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                window.content()
            }
        }
    }
}

@Composable
private fun MdiTitleBar(window: MdiWindowInfo, mdiState: MdiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFF3C3C3C))
            .pointerInput(window.id) {
                detectDragGestures(
                    onDragStart = { mdiState.bringToFront(window) },
                    onDrag = { change, delta ->
                        change.consume()
                        mdiState.updatePosition(window.id, delta)
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        TrafficLightButton(
            color = Color(0xFFFF5F57),
            enabled = window.closable,
            onClick = { mdiState.close(window.id) }
        )
        Spacer(modifier = Modifier.width(6.dp))
        TrafficLightButton(
            color = Color(0xFFFFBD2E),
            onClick = { mdiState.toggleMinimize(window.id) }
        )
        Spacer(modifier = Modifier.width(6.dp))
        TrafficLightButton(
            color = Color(0xFF28C840),
            onClick = { mdiState.toggleExpand(window.id) }
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = window.title,
            color = Color(0xFFCCCCCC),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrafficLightButton(color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val displayColor = if (enabled) color else Color(0xFF555555)
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(displayColor, CircleShape)
            .then(
                if (enabled) Modifier.pointerInput(Unit) { detectTapGestures { onClick() } }
                else Modifier
            )
    )
}

@Composable
private fun MdiMinimizedBar(window: MdiWindowInfo, mdiState: MdiState) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .background(Color(0xFF3C3C3C), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp)
            .pointerInput(window.id) {
                detectTapGestures { mdiState.toggleMinimize(window.id) }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(window.title, color = Color(0xFFCCCCCC), fontSize = 11.sp)
    }
}
