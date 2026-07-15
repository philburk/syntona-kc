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
import com.syntona.dataflow.model.Module

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
    private var _title by mutableStateOf(title)
    var title: String
        get() {
            val module = id as? com.syntona.dataflow.modules.PatchModule
            if (module != null) {
                return module.headerTitle
            }
            return _title
        }
        set(value) {
            val module = id as? com.syntona.dataflow.modules.PatchModule
            if (module != null) {
                module.name = value
            } else {
                _title = value
            }
        }
    var position by mutableStateOf(position)
    var minimized by mutableStateOf(minimized)
    var width by mutableStateOf(width)
    var height by mutableStateOf(height)
    var expanded by mutableStateOf(false)
    private var savedWidth = width
    private var savedHeight = height

    // Fired whenever position/width/height change, so owners (e.g. PatchModuleNode) can
    // persist the current geometry without relying on recomposition to observe it.
    var onGeometryChanged: (() -> Unit)? = null

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
        onGeometryChanged?.invoke()
    }
}

class MdiState {
    val windows = mutableStateListOf<MdiWindowInfo>()
    var activeGraphState by mutableStateOf<GraphState?>(null)
    var rootGraphState by mutableStateOf<GraphState?>(null)

    // Container size and position in pixels, updated by MdiDesktop
    var containerWidth by mutableStateOf(0f)
    var containerHeight by mutableStateOf(0f)
    var desktopOffset by mutableStateOf(Offset.Zero)

    // Position of the outermost App Box in screen coordinates, set by App
    var appRootOffset by mutableStateOf(Offset.Zero)

    // Module being dragged from the ModuleTree (null when no drag in progress)
    var treeDragCreator by mutableStateOf<(() -> Module)?>(null)
    var treeDragPosition by mutableStateOf(Offset.Zero)

    fun updateContainerSize(widthPx: Float, heightPx: Float) {
        containerWidth = widthPx
        containerHeight = heightPx
    }

    fun startTreeDrag(creator: () -> Module, globalPosition: Offset) {
        treeDragCreator = creator
        treeDragPosition = globalPosition
    }

    fun updateTreeDragPosition(delta: Offset) {
        treeDragPosition += delta
    }

    fun cancelTreeDrag() {
        treeDragCreator = null
    }

    /** Returns the GraphState of the topmost window under [globalPos], or null if none. */
    fun graphStateAtPosition(globalPos: Offset, density: Float): GraphState? {
        val localPos = globalPos - desktopOffset
        return windows.lastOrNull { window ->
            if (window.minimized || window.graphState == null) return@lastOrNull false
            val winW = window.width.value * density
            val winH = window.height.value * density
            localPos.x >= window.position.x && localPos.x <= window.position.x + winW &&
            localPos.y >= window.position.y && localPos.y <= window.position.y + winH
        }?.graphState
    }

    fun open(window: MdiWindowInfo) {
        (window.id as? com.syntona.dataflow.modules.PatchModule)?.isWindowOpen = true
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
        if (removed.isNotEmpty()) {
            (id as? com.syntona.dataflow.modules.PatchModule)?.isWindowOpen = false
        }
        windows.removeAll { it.id == id && it.closable }
    }

    fun bringToFront(window: MdiWindowInfo) {
        if (windows.lastOrNull() !== window) {
            windows.remove(window)
            windows.add(window)
        }
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
            window.onGeometryChanged?.invoke()
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
            window.onGeometryChanged?.invoke()
        }
    }

    fun toggleMinimize(id: Any) {
        windows.find { it.id == id }?.let { it.minimized = !it.minimized }
    }

    fun toggleExpand(id: Any) {
        windows.find { it.id == id }?.toggleExpand()
    }

    fun closeAllClosable() {
        windows.forEach { w ->
            if (w.closable) {
                (w.id as? com.syntona.dataflow.modules.PatchModule)?.isWindowOpen = false
            }
        }
        windows.removeAll { it.closable }
        activeGraphState = rootGraphState
    }
}

