package com.syntona.dataflow.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalMdiState = compositionLocalOf<MdiState> { error("No MdiState provided") }

class MdiWindowInfo(
    val id: Any,
    title: String,
    position: Offset = Offset(80f, 60f),
    minimized: Boolean = false,
    val closable: Boolean = true,
    val normalWidth: Dp = 600.dp,
    val normalHeight: Dp = 450.dp,
    val graphState: GraphState? = null,
    val content: @Composable () -> Unit
) {
    var title by mutableStateOf(title)
    var position by mutableStateOf(position)
    var minimized by mutableStateOf(minimized)
    var expanded by mutableStateOf(false)
}

class MdiState {
    val windows = mutableStateListOf<MdiWindowInfo>()
    var activeGraphState by mutableStateOf<GraphState?>(null)

    fun open(window: MdiWindowInfo) {
        val existing = windows.find { it.id == window.id }
        if (existing != null) {
            if (existing.minimized) existing.minimized = false
            bringToFront(existing)
        } else {
            windows.add(window)
            window.graphState?.let { activeGraphState = it }
        }
    }

    fun close(id: Any) {
        val removed = windows.filter { it.id == id && it.closable }
        removed.forEach { w -> if (w.graphState == activeGraphState) activeGraphState = null }
        windows.removeAll { it.id == id && it.closable }
    }

    fun bringToFront(window: MdiWindowInfo) {
        windows.remove(window)
        windows.add(window)
        window.graphState?.let { activeGraphState = it }
    }

    fun updatePosition(id: Any, delta: Offset) {
        windows.find { it.id == id }?.let { window ->
            window.position = Offset(
                (window.position.x + delta.x).coerceAtLeast(0f),
                (window.position.y + delta.y).coerceAtLeast(0f)
            )
        }
    }

    fun toggleMinimize(id: Any) {
        windows.find { it.id == id }?.let { it.minimized = !it.minimized }
    }

    fun toggleExpand(id: Any) {
        windows.find { it.id == id }?.let { it.expanded = !it.expanded }
    }
}
