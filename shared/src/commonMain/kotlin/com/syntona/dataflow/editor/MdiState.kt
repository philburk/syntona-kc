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
import com.syntona.dataflow.modules.PatchModule

val LocalMdiState = compositionLocalOf<MdiState> { error("No MdiState provided") }

/**
 * Implemented by editors hosted in MDI windows (e.g. the piano-roll) that want the
 * global Edit menu and its keyboard shortcuts routed to them while their window is
 * frontmost. Windows without a handler fall through to the graph-level behavior.
 */
interface EditActionHandler {
    fun canCut(): Boolean = canCopy()
    fun canCopy(): Boolean
    fun canPaste(): Boolean
    fun canDelete(): Boolean
    fun cut()
    fun copy()
    fun paste()
    fun delete()
}

class MdiWindowInfo(
    val id: Any,
    title: String,
    position: Offset = Offset(80f, 60f),
    minimized: Boolean = false,
    val closable: Boolean = true,
    width: Dp = 600.dp,
    height: Dp = 450.dp,
    val graphState: GraphState? = null,
    val onClose: (() -> Unit)? = null,
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

    // Receives Edit menu actions (Cut/Copy/Paste/Delete) while this window is frontmost.
    var editHandler by mutableStateOf<EditActionHandler?>(null)

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

    /**
     * Edit handler of the frontmost (non-minimized) window, or null when that window
     * has none — in which case Edit actions fall through to [activeGraphState].
     */
    val activeEditHandler: EditActionHandler?
        get() = windows.lastOrNull { !it.minimized }?.editHandler
    /**
     * Root graph of the frontmost document. Derived rather than stored, so it can never disagree
     * with which document is actually in front now that several can be open.
     */
    val rootGraphState: GraphState?
        get() = FileState.activeDocument?.rootPatch?.graphState

    // Container size and position in pixels, updated by MdiDesktop
    var containerWidth by mutableStateOf(0f)
    var containerHeight by mutableStateOf(0f)
    var desktopOffset by mutableStateOf(Offset.Zero)
    var density by mutableStateOf(1f)

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

    /**
     * If [window] sits directly over (or close to) any other existing visible window,
     * offset it down and right so the underlying window remains slightly visible.
     */
    fun cascadeIfOverlapping(window: MdiWindowInfo) {
        val d = if (density > 0f) density else 1f
        val step = 28f * d
        val threshold = 20f * d
        var currentPos = window.position
        var changed = false
        var iterations = 0
        while (iterations < 20 && windows.any { it !== window && !it.minimized && kotlin.math.abs(currentPos.x - it.position.x) < threshold && kotlin.math.abs(currentPos.y - it.position.y) < threshold }) {
            currentPos = Offset(currentPos.x + step, currentPos.y + step)
            if (containerWidth > 0f && currentPos.x + 100f > containerWidth) {
                currentPos = Offset(10f * d, currentPos.y)
            }
            if (containerHeight > 0f && currentPos.y + 50f > containerHeight) {
                currentPos = Offset(currentPos.x, 10f * d)
            }
            changed = true
            iterations++
        }
        if (changed) {
            window.position = currentPos
            window.onGeometryChanged?.invoke()
        }
    }

    fun open(window: MdiWindowInfo) {
        val patch = window.id as? PatchModule
        if (patch != null) {
            patch.isWindowOpen = true
            if (patch.parentPatch != null && patch.parentPatch?.graphState?.isDeserializing != true) {
                patch.isModified = true
                patch.parentPatch?.graphState?.markChanged()
                FileState.documentFor(patch.graphState)?.markDirty()
            }
        }
        val existing = windows.find { it.id == window.id }
        if (existing != null) {
            if (existing.minimized) existing.minimized = false
            bringToFront(existing)
        } else {
            cascadeIfOverlapping(window)
            windows.add(window)
            window.graphState?.let { focusGraph(it) }
        }
        window.graphState?.addModuleRemovedListener(::onModuleRemovedFromGraph)
        (window.id as? Module)?.parentPatch?.graphState?.addModuleRemovedListener(::onModuleRemovedFromGraph)
    }

    fun closeWindowsForModules(deletedModules: Collection<Module>) {
        if (deletedModules.isEmpty()) return
        val allDeleted = mutableSetOf<Module>()
        fun collect(m: Module) {
            allDeleted.add(m)
            if (m is PatchModule) {
                for (child in m.graphState.modules) {
                    collect(child)
                }
            }
        }
        deletedModules.forEach { collect(it) }

        val doomed = windows.filter { w ->
            w.closable && (
                w.id in allDeleted ||
                (w.graphState != null && allDeleted.any { it is PatchModule && it.graphState === w.graphState })
            )
        }
        if (doomed.isEmpty()) return

        doomed.forEach { w ->
            if (w.graphState == activeGraphState) activeGraphState = null
            (w.id as? PatchModule)?.isWindowOpen = false
        }
        windows.removeAll { w -> doomed.any { it === w } }

        if (activeGraphState == null) {
            windows.lastOrNull { !it.minimized }?.graphState?.let { focusGraph(it) }
        }
    }

    fun closeWindowsForModule(deletedModule: Module) {
        closeWindowsForModules(listOf(deletedModule))
    }

    private fun onModuleRemovedFromGraph(module: Module) {
        closeWindowsForModule(module)
    }

    fun close(id: Any) {
        val removed = windows.filter { it.id == id && it.closable }
        removed.forEach { w ->
            if (w.graphState == activeGraphState) activeGraphState = null
            w.onClose?.invoke()
        }
        if (removed.isNotEmpty()) {
            val patch = id as? com.syntona.dataflow.modules.PatchModule
            if (patch != null) {
                patch.isWindowOpen = false
                if (patch.parentPatch != null) {
                    patch.isModified = true
                    patch.parentPatch?.graphState?.markChanged()
                    FileState.documentFor(patch.graphState)?.markDirty()
                }
            }
        }
        windows.removeAll { it.id == id && it.closable }

        // Closing a document's top level window closes the document itself, along with any of its
        // sub patch windows that are still open, and tears down its root patch to stop and disconnect modules.
        val closedDocument = FileState.documents.firstOrNull { it.rootPatch === id }
        if (closedDocument != null && removed.isNotEmpty()) {
            val orphans = windows.filter { FileState.documentFor(it.graphState) === closedDocument }
            orphans.forEach {
                (it.id as? com.syntona.dataflow.modules.PatchModule)?.isWindowOpen = false
                if (it.graphState == activeGraphState) activeGraphState = null
            }
            windows.removeAll { w -> orphans.any { it === w } }
            FileState.removeDocument(closedDocument)
            closedDocument.rootPatch.graphState.onChange = null
            // Stops the modules, then releases them. Teardown alone leaves them in the graph, so
            // that undoing a delete can restore a patch, but a closed document is never coming back.
            closedDocument.rootPatch.teardown()
            closedDocument.rootPatch.graphState.clearAllModules(tearDown = false)
        }
        if (activeGraphState == null) {
            windows.lastOrNull { !it.minimized }?.graphState?.let { focusGraph(it) }
        }
    }

    fun bringToFront(window: MdiWindowInfo) {
        if (windows.lastOrNull() !== window) {
            windows.remove(window)
            windows.add(window)
        }
        window.graphState?.let { focusGraph(it) }
    }

    /**
     * Makes [graphState] the active one and follows it up to the document that owns it, so the
     * File menu, window title and save actions all apply to the document you are looking at.
     */
    private fun focusGraph(graphState: GraphState) {
        activeGraphState = graphState
        FileState.documentFor(graphState)?.let { FileState.focusDocument(it) }
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

    /**
     * Closes the sub patch windows belonging to [document], leaving its own top level window and
     * every other document alone. Used when a document's contents are being replaced by a load.
     */
    fun closeSubWindows(document: DocumentState?) {
        if (document == null) return
        val doomed = windows.filter { w ->
            w.closable &&
                w.id !== document.rootPatch &&
                FileState.documentFor(w.graphState) === document
        }
        doomed.forEach { w ->
            (w.id as? com.syntona.dataflow.modules.PatchModule)?.isWindowOpen = false
        }
        windows.removeAll { w -> doomed.any { it === w } }
        activeGraphState = document.rootPatch.graphState
    }

    /**
     * Closes the sub patch windows of the frontmost document.
     *
     * Note that this deliberately does not touch other documents. Several top level documents can
     * be open at once, and clearing one before a load must not take the others' windows with it.
     */
    fun closeAllClosable() {
        closeSubWindows(FileState.activeDocument)
    }

    /**
     * Synchronizes each child patch's [PatchModule.isWindowOpen] with whether an MDI window
     * for it is currently open in [windows].
     */
    fun syncChildWindowStates(rootPatch: PatchModule) {
        for (module in rootPatch.graphState.modules) {
            if (module is PatchModule) {
                module.isWindowOpen = windows.any { it.id === module }
                syncChildWindowStates(module)
            }
        }
    }
}

