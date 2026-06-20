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
    width: Dp = 600.dp,
    height: Dp = 450.dp,
    val graphState: GraphState? = null,
    val content: @Composable () -> Unit
) {
    var title by mutableStateOf(title)
    var position by mutableStateOf(position)
    var minimized by mutableStateOf(minimized)
    var width by mutableStateOf(width)
    var height by mutableStateOf(height)
    var expanded by mutableStateOf(false)
    private var savedWidth = width
    private var savedHeight = height

    fun toggleExpand() {
        if (expanded) {
            width = savedWidth
            height = savedHeight
        } else {
            savedWidth = width
            savedHeight = height
            width = width * 1.6f
            height = height * 1.6f
        }
        expanded = !expanded
    }
}

class MdiState {
    val windows = mutableStateListOf<MdiWindowInfo>()
    var activeGraphState by mutableStateOf<GraphState?>(null)

    // Container size in pixels, updated by MdiDesktop via onSizeChanged
    var containerWidth by mutableStateOf(0f)
    var containerHeight by mutableStateOf(0f)

    fun updateContainerSize(widthPx: Float, heightPx: Float) {
        containerWidth = widthPx
        containerHeight = heightPx
    }

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
            // Keep at least 80px of title bar visible on all edges
            val minGrabbable = 80f
            val titleBarHeight = 28f
            val maxX = if (containerWidth > 0f) (containerWidth - minGrabbable) else Float.MAX_VALUE
            val maxY = if (containerHeight > 0f) (containerHeight - titleBarHeight) else Float.MAX_VALUE
            window.position = Offset(
                (window.position.x + delta.x).coerceIn(0f, maxX),
                (window.position.y + delta.y).coerceIn(0f, maxY)
            )
        }
    }

    fun resize(id: Any, deltaX: Float, deltaY: Float, density: Float) {
        windows.find { it.id == id }?.let { window ->
            val minW = 150.dp
            val minH = 80.dp
            val maxWPx = if (containerWidth > 0f) containerWidth - window.position.x else Float.MAX_VALUE
            val maxHPx = if (containerHeight > 0f) containerHeight - window.position.y else Float.MAX_VALUE
            val maxW = (maxWPx / density).dp
            val maxH = (maxHPx / density).dp
            window.width = (window.width + (deltaX / density).dp).coerceIn(minW, maxW)
            window.height = (window.height + (deltaY / density).dp).coerceIn(minH, maxH)
        }
    }

    fun toggleMinimize(id: Any) {
        windows.find { it.id == id }?.let { it.minimized = !it.minimized }
    }

    fun toggleExpand(id: Any) {
        windows.find { it.id == id }?.toggleExpand()
    }
}
