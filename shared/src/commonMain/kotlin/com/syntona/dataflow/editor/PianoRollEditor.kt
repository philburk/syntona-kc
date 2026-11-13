package com.syntona.dataflow.editor

import com.syntona.ui.PlayIcon
import com.syntona.ui.StopIcon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.modules.AddNotesCommand
import com.syntona.dataflow.modules.ChangeNotesCommand
import com.syntona.dataflow.modules.DeleteNotesCommand
import com.syntona.dataflow.modules.NoteFields
import com.syntona.dataflow.modules.PianoRollModule
import com.syntona.dataflow.modules.PianoRollNote
import com.syntona.dataflow.modules.PianoRollTiming
import com.syntona.dataflow.modules.Ratio
import com.syntona.dataflow.modules.RatioMath
import com.syntona.dataflow.modules.captureFields
import com.syntona.dataflow.modules.applyFields
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------- colors

private val isDarkPianoRoll: Boolean get() = GraphViewSettings.isDarkMode

private val PR_BACKGROUND: Color get() = if (isDarkPianoRoll) Color(0xFF1A1A2E) else Color(0xFFF8FAFC)
private val PR_GRID_LINE: Color get() = if (isDarkPianoRoll) Color(0x22FFFFFF) else Color(0x18000000)
private val PR_OCTAVE_LINE: Color get() = if (isDarkPianoRoll) Color(0x55FFFFFF) else Color(0x44000000)
private val PR_BEAT_LINE: Color get() = if (isDarkPianoRoll) Color(0x14FFFFFF) else Color(0x14000000)
private val PR_MEASURE_LINE: Color get() = if (isDarkPianoRoll) Color(0x33FFFFFF) else Color(0x33000000)
private val PR_NOTE_FILL: Color get() = Color(0xFF64748B)
private val PR_NOTE_ROOT_FILL: Color get() = Color(0xFF475569)
private val PR_NOTE_PLAYING: Color get() = Color(0xFF3B82F6)
private val PR_NOTE_TEXT: Color get() = Color.White
private val PR_SELECTED_OUTLINE: Color get() = Color(0xFFEF4444)
private val PR_CONNECTOR: Color get() = if (isDarkPianoRoll) Color(0xFF60A5FA) else Color(0xFF2563EB)
private val PR_RATIO_TEXT: Color get() = if (isDarkPianoRoll) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
private val PR_GHOST: Color get() = if (isDarkPianoRoll) Color(0x8894A3B8) else Color(0x8864748B)
private val PR_PLAYHEAD: Color get() = Color(0xFFEF4444)
private val PR_RULER_BG: Color get() = if (isDarkPianoRoll) Color(0xFF12122A) else Color(0xFFE2E8F0)
private val PR_RULER_TEXT: Color get() = if (isDarkPianoRoll) Color(0xFF94A3B8) else Color(0xFF334155)
private val PR_RUBBER_SELECT: Color get() = Color(0x333B82F6)
private val PR_RUBBER_ERASE: Color get() = Color(0x33EF4444)
private val PR_CHAIN_LINE: Color get() = if (isDarkPianoRoll) Color(0xFF10B981) else Color(0xFF059669)

// ---------------------------------------------------------------------------- tools

private enum class PianoRollTool(val label: String) {
    Select("Select"),
    Edit("Edit"),
    Chain("Chain"),
    Erase("Erase"),
}

/** Shared note clipboard so notes can be pasted across pianoRoll modules. */
object PianoRollClipboard {
    data class ClipNote(val id: Int, val fields: NoteFields, val absolutePitch: Double)

    var notes: List<ClipNote>? = null
    var pasteTargetTicks: Int = 0
}

// ------------------------------------------------------------------- view transform

private class PianoRollView {
    /** Horizontal zoom, in pixels per tick. */
    var zoom by mutableStateOf(0.5f)

    /** Leftmost visible tick. */
    var scrollTicks by mutableStateOf(-PianoRollTiming.GRID_TICKS.toFloat())

    /** Pitch (semitones) at the vertical center of the canvas. */
    var centerPitch by mutableStateOf(66f)

    var noteHeightPx = 40f
    var widthPx = 0f
    var heightPx = 0f

    fun xOf(ticks: Float): Float = (ticks - scrollTicks) * zoom
    fun ticksAt(x: Float): Float = x / zoom + scrollTicks
    fun yOf(pitch: Float): Float = heightPx / 2f - (pitch - centerPitch) * noteHeightPx
    fun pitchAt(y: Float): Float = centerPitch + (heightPx / 2f - y) / noteHeightPx
}

// --------------------------------------------------------------------- drag states

private sealed interface DragState {
    class MoveNotes(
        val target: PianoRollNote,
        val group: List<PianoRollNote>,
        val before: Map<Int, NoteFields>,
        val groupIds: Set<Int>,
        val downTicks: Float,
        val downPitch: Float,
    ) : DragState

    class ResizeNote(
        val note: PianoRollNote,
        val before: NoteFields,
        val downTicks: Float,
    ) : DragState

    class CreateChild(val parent: PianoRollNote) : DragState {
        var ghostStartTicks by mutableStateOf(0)
        var ghostRatio by mutableStateOf(Ratio.UNISON)
    }

    class ChainDrag(val source: PianoRollNote) : DragState {
        var cursor by mutableStateOf(Offset.Zero)
        var hoverTargetId by mutableStateOf(-1)
    }

    class RubberBand(val start: Offset, val erase: Boolean) : DragState {
        var end by mutableStateOf(start)
    }
}

// ----------------------------------------------------------------------- composable

@Composable
fun PianoRollEditor(
    module: PianoRollModule,
    state: GraphState,
    noteOutputMode: PianoRollModule.NoteOutputMode = module.noteOutputMode,
    onNoteOutputModeChange: ((PianoRollModule.NoteOutputMode) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val mdiState = LocalMdiState.current
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    val view = remember { PianoRollView() }
    view.noteHeightPx = with(density) { 20.dp.toPx() }
    val edgeZonePx = with(density) { 8.dp.toPx() }

    var tool by remember { mutableStateOf(PianoRollTool.Edit) }
    var defaultDurationTicks by remember { mutableStateOf(PianoRollTiming.DEFAULT_NOTE_TICKS) }
    var dragState by remember { mutableStateOf<DragState?>(null) }
    var hoverCursor by remember { mutableStateOf(PointerIcon.Default) }
    var playheadTicks by remember { mutableStateOf<Double?>(null) }
    var lastPreviewPitch by remember { mutableStateOf<Double?>(null) }

    fun postCommand(command: com.syntona.dataflow.engine.SyntonaCommand) {
        val engine = state.engine ?: module.getEngine()
        if (engine != null) {
            engine.post(command)
        } else {
            command.execute()
        }
        state.markChanged()
    }

    fun previewIfChanged(pitch: Double) {
        val last = lastPreviewPitch
        if (last == null || abs(pitch - last) > 0.01) {
            lastPreviewPitch = pitch
            module.previewPitch(pitch)
        }
    }

    fun selectedNotes(): List<PianoRollNote> = module.notes.filter { it.selected }

    fun copySelection() {
        val selected = selectedNotes()
        if (selected.isEmpty()) return
        PianoRollClipboard.notes = selected.map {
            PianoRollClipboard.ClipNote(it.id, it.captureFields(), module.pitchOf(it))
        }
        PianoRollClipboard.pasteTargetTicks =
            PianoRollTiming.snapTicks(selected.maxOf { it.endTicks })
    }

    fun pasteClipboard() {
        val clip = PianoRollClipboard.notes ?: return
        if (clip.isEmpty()) return
        val clipIds = clip.map { it.id }.toSet()
        val minStart = clip.minOf { it.fields.startTicks }
        val maxEnd = clip.maxOf { it.fields.startTicks + it.fields.durationTicks }
        val shift = PianoRollClipboard.pasteTargetTicks - minStart
        val idMap = clip.associate { it.id to module.allocateNoteId() }

        val newNotes = clip.map { clipNote ->
            val f = clipNote.fields
            val externalParent = f.parentId >= 0 && f.parentId !in clipIds
            PianoRollNote(
                id = idMap.getValue(clipNote.id),
                startTicks = f.startTicks + shift,
                durationTicks = f.durationTicks,
                parentId = when {
                    f.parentId < 0 || externalParent -> -1
                    else -> idMap.getValue(f.parentId)
                },
                ratio = if (f.parentId < 0 || externalParent) Ratio.UNISON else f.ratio,
                rootPitch = if (externalParent) clipNote.absolutePitch else f.rootPitch,
            )
        }
        module.notes.forEach { it.selected = false }
        newNotes.forEach { it.selected = true }
        PianoRollClipboard.pasteTargetTicks += max(PianoRollTiming.GRID_TICKS, maxEnd - minStart)
        postCommand(AddNotesCommand(module, newNotes))
    }

    fun deleteSelection() {
        val selected = selectedNotes()
        if (selected.isEmpty()) return
        postCommand(DeleteNotesCommand(module, selected.map { it.id }))
    }

    // Route the global Edit menu / shortcuts to this editor while its window is frontmost.
    DisposableEffect(module) {
        val window = mdiState.windows.find { it.id == module }
        val handler = object : EditActionHandler {
            override fun canCopy() = module.notes.any { it.selected }
            override fun canPaste() = !PianoRollClipboard.notes.isNullOrEmpty()
            override fun canDelete() = module.notes.any { it.selected }
            override fun copy() = copySelection()
            override fun cut() {
                copySelection()
                deleteSelection()
            }
            override fun paste() = pasteClipboard()
            override fun delete() = deleteSelection()
        }
        window?.editHandler = handler
        onDispose {
            if (window?.editHandler === handler) window.editHandler = null
        }
    }

    // Smooth playhead: sample the module's playback clock every frame while playing.
    LaunchedEffect(module.isPlaying) {
        if (!module.isPlaying) {
            playheadTicks = null
            return@LaunchedEffect
        }
        while (module.isPlaying) {
            withFrameNanos { }
            val ticks = module.playheadTicks(ScoreClock.now())
            playheadTicks = ticks
            // Auto-scroll to keep the playhead visible.
            if (ticks != null && view.widthPx > 0f) {
                val x = view.xOf(ticks.toFloat())
                if (x > view.widthPx * 0.85f) {
                    view.scrollTicks += (view.widthPx * 0.5f) / view.zoom
                } else if (x < 0f) {
                    view.scrollTicks = ticks.toFloat() - (view.widthPx * 0.15f) / view.zoom
                }
            }
        }
        playheadTicks = null
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // ------------------------------------------------------------------ gestures

    fun noteAt(ticks: Float, pitch: Float): PianoRollNote? {
        val halfNotePitch = 0.5f
        for (note in module.notes.asReversed()) {
            val notePitch = module.pitchOf(note).toFloat()
            if (ticks >= note.startTicks && ticks <= note.endTicks &&
                abs(pitch - notePitch) <= halfNotePitch
            ) {
                return note
            }
        }
        return null
    }

    fun moveGroupFor(target: PianoRollNote): List<PianoRollNote> {
        val ids = mutableSetOf<Int>()
        val roots = if (target.selected) selectedNotes() else listOf(target)
        for (n in roots) {
            ids.add(n.id)
            module.descendantsOf(n.id).forEach { ids.add(it.id) }
        }
        return module.notes.filter { it.id in ids }
    }

    fun finishMove(drag: DragState.MoveNotes) {
        val after = drag.group.associate { it.id to it.captureFields() }
        // Revert to the pre-drag snapshot, then apply the final state through an
        // undoable command so execute/undo are symmetric.
        drag.group.forEach { it.applyFields(drag.before.getValue(it.id)) }
        val edits = drag.group.mapNotNull { note ->
            val before = drag.before.getValue(note.id)
            val afterFields = after.getValue(note.id)
            if (before == afterFields) null
            else ChangeNotesCommand.Edit(note.id, before, afterFields)
        }
        if (edits.isNotEmpty()) postCommand(ChangeNotesCommand(module, edits))
    }

    fun finishResize(drag: DragState.ResizeNote) {
        val after = drag.note.captureFields()
        drag.note.applyFields(drag.before)
        if (after != drag.before) {
            postCommand(ChangeNotesCommand(module, listOf(ChangeNotesCommand.Edit(drag.note.id, drag.before, after))))
        }
    }

    fun applyMove(drag: DragState.MoveNotes, ticks: Float, pitch: Float) {
        val dxTicks = run {
            val raw = (ticks - drag.downTicks).roundToInt()
            (raw.toDouble() / PianoRollTiming.GRID_TICKS).roundToInt() * PianoRollTiming.GRID_TICKS
        }

        // Determine the pitch delta applied to the whole group.
        val target = drag.target
        val targetBefore = drag.before.getValue(target.id)
        val parentInGroup = targetBefore.parentId >= 0 && targetBefore.parentId in drag.groupIds
        val appliedDelta: Double
        var targetRatio: Ratio? = null
        if (targetBefore.parentId >= 0 && !parentInGroup) {
            // Chained note dragged on its own: re-derive its ratio from the cursor
            // position relative to its (stationary) parent.
            val parent = module.findNote(targetBefore.parentId)
            if (parent != null) {
                val parentPitch = module.pitchOf(parent)
                val newRatio = RatioMath.closestRatio(pitch - parentPitch)
                targetRatio = newRatio
                appliedDelta = newRatio.semitones - targetBefore.ratio.semitones
            } else {
                appliedDelta = 0.0
            }
        } else {
            appliedDelta = (pitch - drag.downPitch).roundToInt().toDouble()
        }

        var previewPitch: Double? = null
        for (note in drag.group) {
            val before = drag.before.getValue(note.id)
            note.startTicks = max(0, before.startTicks + dxTicks)
            when {
                note.id == target.id && targetRatio != null -> note.ratio = targetRatio
                before.parentId < 0 -> note.rootPitch = before.rootPitch + appliedDelta
                before.parentId !in drag.groupIds -> {
                    // Chained to a note outside the moving group: keep the audible
                    // pitch shift by adjusting the ratio.
                    note.ratio = RatioMath.closestRatio(before.ratio.semitones + appliedDelta)
                }
                // Parent moves with the group: ratio unchanged, pitch follows.
            }
            if (note.id == target.id) previewPitch = module.pitchOf(note)
        }
        previewPitch?.let { previewIfChanged(it) }
    }

    // -------------------------------------------------------------------- layout

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PR_BACKGROUND)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.isMetaPressed || event.isCtrlPressed) return@onKeyEvent false
                when {
                    event.key == Key.Spacebar -> {
                        module.requestPlayToggle()
                        true
                    }
                    else -> when (event.utf16CodePoint.toChar().lowercaseChar()) {
                        'v' -> { tool = PianoRollTool.Select; true }
                        'a' -> { tool = PianoRollTool.Edit; true }
                        'c' -> { tool = PianoRollTool.Chain; true }
                        'd' -> { tool = PianoRollTool.Erase; true }
                        's' -> { defaultDurationTicks = PianoRollTiming.TPB / 4; true }
                        'e' -> { defaultDurationTicks = PianoRollTiming.TPB / 2; true }
                        'q' -> { defaultDurationTicks = PianoRollTiming.TPB; true }
                        'h' -> { defaultDurationTicks = PianoRollTiming.TPB * 2; true }
                        'w' -> { defaultDurationTicks = PianoRollTiming.TPB * 4; true }
                        else -> false
                    }
                }
            }
    ) {
        PianoRollToolbar(
            module = module,
            tool = tool,
            onToolChange = { tool = it },
            defaultDurationTicks = defaultDurationTicks,
            onDefaultDurationChange = { defaultDurationTicks = it },
            noteOutputMode = noteOutputMode,
            onNoteOutputModeChange = onNoteOutputModeChange ?: {
                module.noteOutputMode = it
                FileState.markDirty()
            },
            zoom = view.zoom,
            onZoomChange = { newZoom ->
                // Keep the center of the view stable while zooming.
                val centerTicks = view.ticksAt(view.widthPx / 2f)
                view.zoom = newZoom
                view.scrollTicks = centerTicks - (view.widthPx / 2f) / view.zoom
            },
        )

        PianoRollRuler(module, view, playheadTicks)

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerHoverIcon(hoverCursor)
                .pointerInput(Unit) {
                    // Hover tracking (cursor feedback) and wheel scrolling.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                    if (event.keyboardModifiers.isShiftPressed) {
                                        view.scrollTicks += (delta.y + delta.x) * 20f / view.zoom
                                    } else {
                                        view.scrollTicks += delta.x * 20f / view.zoom
                                        view.centerPitch += delta.y * 0.3f
                                    }
                                    view.scrollTicks = view.scrollTicks.coerceAtLeast(-PianoRollTiming.TICKS_PER_MEASURE.toFloat())
                                    event.changes.forEach { it.consume() }
                                }
                                PointerEventType.Move -> {
                                    if (dragState == null && event.changes.none { it.pressed }) {
                                        val pos = event.changes.first().position
                                        val ticks = view.ticksAt(pos.x)
                                        val pitch = view.pitchAt(pos.y)
                                        val note = noteAt(ticks, pitch)
                                        hoverCursor = when {
                                            tool != PianoRollTool.Edit -> PointerIcon.Default
                                            note == null -> crosshairCursor()
                                            ticks > note.endTicks - edgeZonePx / view.zoom -> resizeHorizontalCursor()
                                            ticks < note.startTicks + edgeZonePx / view.zoom -> moveCursor()
                                            else -> crosshairCursor()
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
                .pointerInput(tool) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        focusRequester.requestFocus()
                        val isShift = currentEvent.keyboardModifiers.isShiftPressed
                        val downTicks = view.ticksAt(down.position.x)
                        val downPitch = view.pitchAt(down.position.y)
                        val hitNote = noteAt(downTicks, downPitch)
                        down.consume()

                        when (tool) {
                            PianoRollTool.Select -> {
                                if (hitNote != null) {
                                    if (!isShift && !hitNote.selected) {
                                        module.notes.forEach { it.selected = false }
                                    }
                                    hitNote.selected = if (isShift) !hitNote.selected else true
                                } else {
                                    if (!isShift) module.notes.forEach { it.selected = false }
                                    dragState = DragState.RubberBand(down.position, erase = false)
                                }
                            }
                            PianoRollTool.Erase -> {
                                if (hitNote != null) {
                                    postCommand(DeleteNotesCommand(module, listOf(hitNote.id)))
                                } else {
                                    dragState = DragState.RubberBand(down.position, erase = true)
                                }
                            }
                            PianoRollTool.Chain -> {
                                if (hitNote != null) {
                                    dragState = DragState.ChainDrag(hitNote).also { it.cursor = down.position }
                                }
                            }
                            PianoRollTool.Edit -> {
                                if (hitNote != null) {
                                    val edgeTicks = edgeZonePx / view.zoom
                                    when {
                                        downTicks > hitNote.endTicks - edgeTicks -> {
                                            dragState = DragState.ResizeNote(hitNote, hitNote.captureFields(), downTicks)
                                        }
                                        downTicks < hitNote.startTicks + edgeTicks -> {
                                            val group = moveGroupFor(hitNote)
                                            dragState = DragState.MoveNotes(
                                                target = hitNote,
                                                group = group,
                                                before = group.associate { it.id to it.captureFields() },
                                                groupIds = group.map { it.id }.toSet(),
                                                downTicks = downTicks,
                                                downPitch = downPitch,
                                            )
                                            previewIfChanged(module.pitchOf(hitNote))
                                        }
                                        else -> {
                                            val drag = DragState.CreateChild(hitNote)
                                            drag.ghostStartTicks = PianoRollTiming.snapTicks(hitNote.endTicks)
                                            drag.ghostRatio = RatioMath.closestRatio(
                                                (downPitch - module.pitchOf(hitNote))
                                            )
                                            dragState = drag
                                            previewIfChanged(module.pitchOf(hitNote) + drag.ghostRatio.semitones)
                                        }
                                    }
                                } else {
                                    // Click on empty grid: create an equal-tempered root note.
                                    val note = PianoRollNote(
                                        id = module.allocateNoteId(),
                                        startTicks = PianoRollTiming.snapTicks(downTicks.roundToInt()),
                                        durationTicks = defaultDurationTicks,
                                        rootPitch = downPitch.roundToInt().toDouble(),
                                    )
                                    previewIfChanged(note.rootPitch)
                                    postCommand(AddNotesCommand(module, listOf(note)))
                                }
                            }
                        }

                        // Drag loop.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == down.id } ?: break
                            val pos = change.position
                            val ticks = view.ticksAt(pos.x)
                            val pitch = view.pitchAt(pos.y)

                            if (change.pressed) {
                                when (val drag = dragState) {
                                    is DragState.MoveNotes -> applyMove(drag, ticks, pitch)
                                    is DragState.ResizeNote -> {
                                        val dTicks = (ticks - drag.downTicks).roundToInt()
                                        val snapped = (dTicks.toDouble() / PianoRollTiming.GRID_TICKS).roundToInt() *
                                            PianoRollTiming.GRID_TICKS
                                        drag.note.durationTicks =
                                            max(PianoRollTiming.GRID_TICKS, drag.before.durationTicks + snapped)
                                    }
                                    is DragState.CreateChild -> {
                                        val parent = drag.parent
                                        val fromEnd = ticks - parent.endTicks
                                        val snapped = (fromEnd / PianoRollTiming.GRID_TICKS).roundToInt() *
                                            PianoRollTiming.GRID_TICKS
                                        drag.ghostStartTicks = max(0, parent.endTicks + snapped)
                                        val ratio = RatioMath.closestRatio((pitch - module.pitchOf(parent)))
                                        if (ratio != drag.ghostRatio) {
                                            drag.ghostRatio = ratio
                                            previewIfChanged(module.pitchOf(parent) + ratio.semitones)
                                        }
                                    }
                                    is DragState.ChainDrag -> {
                                        drag.cursor = pos
                                        val target = noteAt(ticks, pitch)
                                        drag.hoverTargetId =
                                            if (target != null && target.id != drag.source.id &&
                                                !module.isDescendant(target.id, drag.source.id)
                                            ) target.id else -1
                                    }
                                    is DragState.RubberBand -> drag.end = pos
                                    null -> {}
                                }
                                change.consume()
                            } else {
                                break
                            }
                        }

                        // Pointer up: commit the interaction.
                        when (val drag = dragState) {
                            is DragState.MoveNotes -> finishMove(drag)
                            is DragState.ResizeNote -> finishResize(drag)
                            is DragState.CreateChild -> {
                                val parent = drag.parent
                                val child = PianoRollNote(
                                    id = module.allocateNoteId(),
                                    startTicks = drag.ghostStartTicks,
                                    durationTicks = defaultDurationTicks,
                                    parentId = parent.id,
                                    ratio = drag.ghostRatio,
                                )
                                postCommand(AddNotesCommand(module, listOf(child)))
                            }
                            is DragState.ChainDrag -> {
                                val target = module.findNote(drag.hoverTargetId)
                                if (target != null) {
                                    val source = drag.source
                                    val before = source.captureFields()
                                    val newRatio = RatioMath.closestRatio(
                                        module.pitchOf(source) - module.pitchOf(target)
                                    )
                                    val after = before.copy(parentId = target.id, ratio = newRatio)
                                    postCommand(
                                        ChangeNotesCommand(
                                            module,
                                            listOf(ChangeNotesCommand.Edit(source.id, before, after))
                                        )
                                    )
                                    previewIfChanged(module.pitchOf(target) + newRatio.semitones)
                                }
                            }
                            is DragState.RubberBand -> {
                                val minT = view.ticksAt(min(drag.start.x, drag.end.x))
                                val maxT = view.ticksAt(max(drag.start.x, drag.end.x))
                                val maxP = view.pitchAt(min(drag.start.y, drag.end.y))
                                val minP = view.pitchAt(max(drag.start.y, drag.end.y))
                                val hits = module.notes.filter { note ->
                                    val notePitch = module.pitchOf(note).toFloat()
                                    note.startTicks < maxT && note.endTicks > minT &&
                                        notePitch > minP - 0.5f && notePitch < maxP + 0.5f
                                }
                                if (drag.erase) {
                                    if (hits.isNotEmpty()) {
                                        postCommand(DeleteNotesCommand(module, hits.map { it.id }))
                                    }
                                } else {
                                    hits.forEach { it.selected = true }
                                }
                            }
                            null -> {}
                        }
                        dragState = null
                        lastPreviewPitch = null
                    }
                }
        ) {
            view.widthPx = size.width
            view.heightPx = size.height
            val noteH = view.noteHeightPx

            // --- Horizontal semitone rows -------------------------------------
            val topPitch = view.pitchAt(0f).toInt() + 1
            val bottomPitch = view.pitchAt(size.height).toInt() - 1
            for (pitch in bottomPitch..topPitch) {
                val y = view.yOf(pitch.toFloat() + 0.5f)
                val isC = pitch.mod(12) == 0
                drawLine(
                    color = if (isC) PR_OCTAVE_LINE else PR_GRID_LINE,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (isC) 2f else 1f,
                )
            }

            // --- Vertical beat/measure lines -----------------------------------
            val firstBeat = (view.scrollTicks / PianoRollTiming.TPB).toInt() - 1
            val lastBeat = (view.ticksAt(size.width) / PianoRollTiming.TPB).toInt() + 1
            for (beat in firstBeat..lastBeat) {
                val ticks = beat * PianoRollTiming.TPB
                if (ticks < 0) continue
                val x = view.xOf(ticks.toFloat())
                val isMeasure = ticks % PianoRollTiming.TICKS_PER_MEASURE == 0
                drawLine(
                    color = if (isMeasure) PR_MEASURE_LINE else PR_BEAT_LINE,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (isMeasure) 2f else 1f,
                )
            }

            // --- Connector lines with ratio labels ------------------------------
            val ratioStyle = TextStyle(color = PR_RATIO_TEXT, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            for (note in module.notes) {
                if (note.isRoot) continue
                val parent = module.findNote(note.parentId) ?: continue
                val parentY = view.yOf(module.pitchOf(parent).toFloat())
                val childY = view.yOf(module.pitchOf(note).toFloat())
                val parentRightX = view.xOf(parent.endTicks.toFloat())
                val childX = view.xOf(note.startTicks.toFloat())
                drawLine(PR_CONNECTOR, Offset(parentRightX, parentY), Offset(parentRightX, childY), strokeWidth = 1.5f)
                drawLine(PR_CONNECTOR, Offset(parentRightX, childY), Offset(childX, childY), strokeWidth = 1.5f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = note.ratio.toString(),
                    style = ratioStyle,
                    topLeft = Offset(parentRightX + 4f, (parentY + childY) / 2f - 6f),
                )
            }

            // --- Chain drag line -------------------------------------------------
            (dragState as? DragState.ChainDrag)?.let { drag ->
                val sourceX = view.xOf(drag.source.startTicks.toFloat())
                val sourceY = view.yOf(module.pitchOf(drag.source).toFloat())
                val target = module.findNote(drag.hoverTargetId)
                val endPoint = if (target != null) {
                    Offset(view.xOf(target.endTicks.toFloat()), view.yOf(module.pitchOf(target).toFloat()))
                } else drag.cursor
                drawLine(
                    color = if (target != null) PR_CHAIN_LINE else PR_GHOST,
                    start = Offset(sourceX, sourceY),
                    end = endPoint,
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
                if (target != null) {
                    val ratio = RatioMath.closestRatio(module.pitchOf(drag.source) - module.pitchOf(target))
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "Link: $ratio",
                        style = ratioStyle,
                        topLeft = drag.cursor + Offset(14f, -6f),
                    )
                }
            }

            // --- Notes -----------------------------------------------------------
            val labelStyle = TextStyle(color = PR_NOTE_TEXT, fontSize = 9.sp)
            val playhead = playheadTicks
            for (note in module.notes) {
                val pitch = module.pitchOf(note)
                val x = view.xOf(note.startTicks.toFloat())
                val w = note.durationTicks * view.zoom
                val y = view.yOf(pitch.toFloat()) - noteH / 2f
                if (x > size.width || x + w < 0f || y > size.height || y + noteH < 0f) continue

                val isPlayingNote = playhead != null &&
                    playhead >= note.startTicks && playhead <= note.endTicks
                val fill = when {
                    isPlayingNote -> PR_NOTE_PLAYING
                    note.isRoot -> PR_NOTE_ROOT_FILL
                    else -> PR_NOTE_FILL
                }
                drawRect(fill, topLeft = Offset(x, y), size = Size(w, noteH))
                // Edge affordances.
                drawRect(Color(0x22FFFFFF), topLeft = Offset(x, y), size = Size(min(4f, w), noteH))
                drawRect(Color(0x33FFFFFF), topLeft = Offset(x + w - min(4f, w), y), size = Size(min(4f, w), noteH))
                if (note.selected) {
                    drawRect(
                        PR_SELECTED_OUTLINE,
                        topLeft = Offset(x - 1.5f, y - 1.5f),
                        size = Size(w + 3f, noteH + 3f),
                        style = Stroke(width = 3f),
                    )
                }
                if (w > 30f) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = formatPitch(pitch),
                        style = labelStyle,
                        topLeft = Offset(x + 6f, y + noteH / 2f - 6f),
                    )
                }
            }

            // --- Create-child ghost -----------------------------------------------
            (dragState as? DragState.CreateChild)?.let { drag ->
                val parent = drag.parent
                val ghostPitch = module.pitchOf(parent) + drag.ghostRatio.semitones
                val gx = view.xOf(drag.ghostStartTicks.toFloat())
                val gw = defaultDurationTicks * view.zoom
                val gy = view.yOf(ghostPitch.toFloat()) - noteH / 2f
                drawRect(
                    PR_GHOST,
                    topLeft = Offset(gx, gy),
                    size = Size(gw, noteH),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "Ratio: ${drag.ghostRatio}",
                    style = ratioStyle,
                    topLeft = Offset(gx + gw + 8f, gy + noteH / 2f - 6f),
                )
            }

            // --- Rubber band --------------------------------------------------------
            (dragState as? DragState.RubberBand)?.let { drag ->
                val left = min(drag.start.x, drag.end.x)
                val top = min(drag.start.y, drag.end.y)
                val bandSize = Size(abs(drag.end.x - drag.start.x), abs(drag.end.y - drag.start.y))
                val fill = if (drag.erase) PR_RUBBER_ERASE else PR_RUBBER_SELECT
                drawRect(fill, topLeft = Offset(left, top), size = bandSize)
                drawRect(
                    if (drag.erase) PR_PLAYHEAD else PR_NOTE_PLAYING,
                    topLeft = Offset(left, top),
                    size = bandSize,
                    style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))),
                )
            }

            // --- Play-start marker + playhead ---------------------------------------
            val markerX = view.xOf(module.playStartTicks.toFloat())
            if (markerX >= 0f && markerX <= size.width) {
                drawLine(PR_PLAYHEAD.copy(alpha = 0.3f), Offset(markerX, 0f), Offset(markerX, size.height), strokeWidth = 1f)
            }
            playhead?.let {
                val px = view.xOf(it.toFloat())
                drawLine(PR_PLAYHEAD, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2f)
            }
        }

        PianoRollScrollBar(view, module, playheadTicks)
    }
}

@Composable
private fun PianoRollScrollBar(
    view: PianoRollView,
    module: PianoRollModule,
    playheadTicks: Double?,
    modifier: Modifier = Modifier
) {
    val minScroll = -PianoRollTiming.TICKS_PER_MEASURE.toFloat()
    val maxNoteTicks = module.notes.maxOfOrNull { it.endTicks }?.toFloat() ?: 0f
    val maxPlayhead = playheadTicks?.toFloat() ?: 0f
    val maxPlayStart = module.playStartTicks.toFloat()

    val totalContentEnd = maxOf(maxNoteTicks, maxPlayhead, maxPlayStart)
    val contentEndWithPadding = totalContentEnd + PianoRollTiming.TICKS_PER_MEASURE.toFloat()

    val visibleWidthTicks = if (view.zoom > 0f) view.widthPx / view.zoom else 1000f
    val totalRangeTicks = contentEndWithPadding - minScroll
    val maxScroll = maxOf(minScroll, contentEndWithPadding - visibleWidthTicks)

    val currentScroll = view.scrollTicks.coerceIn(minScroll, maxScroll)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(PR_RULER_BG)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(minScroll, maxScroll, totalRangeTicks, visibleWidthTicks) {
                awaitEachGesture {
                    val down = awaitFirstDown()

                    val trackWidth = size.width.toFloat()
                    if (trackWidth <= 0f) return@awaitEachGesture

                    val thumbWidthFraction = (visibleWidthTicks / totalRangeTicks).coerceIn(0.05f, 1.0f)
                    val thumbWidth = trackWidth * thumbWidthFraction
                    val scrollFraction = ((view.scrollTicks - minScroll) / totalRangeTicks).coerceIn(0f, 1f - thumbWidthFraction)
                    val thumbX = trackWidth * scrollFraction

                    val clickX = down.position.x
                    val isInsideThumb = clickX >= thumbX && clickX <= (thumbX + thumbWidth)

                    val startScroll = view.scrollTicks

                    fun updateScroll(currentX: Float) {
                        if (isInsideThumb) {
                            val dx = currentX - clickX
                            val dScroll = (dx / trackWidth) * totalRangeTicks
                            view.scrollTicks = (startScroll + dScroll).coerceIn(minScroll, maxScroll)
                        } else {
                            val targetScroll = minScroll + (currentX / trackWidth) * totalRangeTicks - (visibleWidthTicks / 2f)
                            view.scrollTicks = targetScroll.coerceIn(minScroll, maxScroll)
                        }
                    }

                    if (!isInsideThumb) {
                        updateScroll(clickX)
                    }
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.find { it.id == down.id } ?: break
                        if (!change.pressed) break
                        updateScroll(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        val trackWidth = size.width
        val trackHeight = size.height

        // Draw top separator border
        val trackLineColor = if (isDarkPianoRoll) Color(0x14FFFFFF) else Color(0x22000000)
        drawLine(
            color = trackLineColor,
            start = Offset(0f, 0f),
            end = Offset(trackWidth, 0f),
            strokeWidth = 1f
        )

        // Draw thin track background line in the middle
        val barHeight = 6.dp.toPx()
        val barY = (trackHeight - barHeight) / 2f

        drawRoundRect(
            color = trackLineColor,
            topLeft = Offset(0f, barY),
            size = Size(trackWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f)
        )

        // Calculate thumb width and position
        if (totalRangeTicks > 0f) {
            val thumbWidthFraction = (visibleWidthTicks / totalRangeTicks).coerceIn(0.05f, 1.0f)
            val thumbWidth = trackWidth * thumbWidthFraction

            val scrollFraction = ((currentScroll - minScroll) / totalRangeTicks).coerceIn(0f, 1f - thumbWidthFraction)
            val thumbX = trackWidth * scrollFraction

            // Draw thumb
            drawRoundRect(
                color = PR_NOTE_PLAYING,
                topLeft = Offset(thumbX, barY),
                size = Size(thumbWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f)
            )
        }
    }
}

// ------------------------------------------------------------------------- toolbar

@Composable
private fun PianoRollToolbar(
    module: PianoRollModule,
    tool: PianoRollTool,
    onToolChange: (PianoRollTool) -> Unit,
    defaultDurationTicks: Int,
    onDefaultDurationChange: (Int) -> Unit,
    noteOutputMode: PianoRollModule.NoteOutputMode,
    onNoteOutputModeChange: (PianoRollModule.NoteOutputMode) -> Unit,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PR_RULER_BG)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val borderMuted = if (isDarkPianoRoll) Color(0x33FFFFFF) else Color(0x33000000)
        PianoRollTool.entries.forEach { t ->
            val selected = tool == t
            TextButton(
                onClick = { onToolChange(t) },
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = if (selected) PR_NOTE_PLAYING else borderMuted,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .height(30.dp)
                    .focusProperties { canFocus = false },
            ) {
                Text(
                    t.label,
                    fontSize = 11.sp,
                    color = if (selected) (if (isDarkPianoRoll) Color.White else PR_NOTE_PLAYING) else PR_RULER_TEXT
                )
            }
        }

        Text("|", color = borderMuted)

        TextButton(
            onClick = { module.requestPlayToggle() },
            modifier = Modifier
                .height(30.dp)
                .focusProperties { canFocus = false },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (module.isPlaying) {
                    StopIcon(color = PR_PLAYHEAD, size = 10.dp)
                    Text("Stop", fontSize = 12.sp, color = PR_PLAYHEAD)
                } else {
                    val playColor = if (isDarkPianoRoll) Color(0xFF34D399) else Color(0xFF059669)
                    PlayIcon(color = playColor, size = 10.dp)
                    Text("Play", fontSize = 12.sp, color = playColor)
                }
            }
        }

        DurationSelector(defaultDurationTicks, onDefaultDurationChange)

        NoteModeSelector(
            mode = noteOutputMode,
            onChange = onNoteOutputModeChange,
        )

        Text("|", color = borderMuted)

        Text("Zoom", fontSize = 10.sp, color = PR_RULER_TEXT)
        Slider(
            value = zoom,
            onValueChange = onZoomChange,
            valueRange = 0.1f..2f,
            modifier = Modifier.width(110.dp),
            colors = SliderDefaults.colors(
                thumbColor = if (isDarkPianoRoll) Color.White else Color.DarkGray, // Sets the thumb color
                activeTrackColor = if (isDarkPianoRoll) Color.Yellow else Color(0xFFF59E0B), // Matches the track to the left of the thumb
                inactiveTrackColor = if (isDarkPianoRoll) Color.LightGray else Color(0xFF94A3B8) // Track to the right of the thumb
            ),
            // 1. Override the default track
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    // 2. Set the gap to 0 to prevent the background from showing through
                    thumbTrackGapSize = 0.dp,
                    colors = SliderDefaults.colors(
                        activeTrackColor = if (isDarkPianoRoll) Color.Yellow else Color(0xFFF59E0B),
                        inactiveTrackColor = if (isDarkPianoRoll) Color.LightGray else Color(0xFF94A3B8)
                    )
                )
            },
        )
    }
}

@Composable
private fun DurationSelector(durationTicks: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        Triple("1/16", "s", PianoRollTiming.TPB / 4),
        Triple("1/8", "e", PianoRollTiming.TPB / 2),
        Triple("1/4", "q", PianoRollTiming.TPB),
        Triple("1/2", "h", PianoRollTiming.TPB * 2),
        Triple("1", "w", PianoRollTiming.TPB * 4),
    )
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(30.dp)
                .focusProperties { canFocus = false }
        ) {
            val label = options.find { it.third == durationTicks }?.first ?: "1/4"
            Text("Dur: $label", fontSize = 11.sp, color = PR_RULER_TEXT)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, shortcut, ticks) ->
                CompactDropdownMenuItem(
                    text = { Text("$label ($shortcut)") },
                    onClick = {
                        onChange(ticks)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteModeSelector(
    mode: PianoRollModule.NoteOutputMode,
    onChange: (PianoRollModule.NoteOutputMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        PianoRollModule.NoteOutputMode.NOTE to "NOTE (with duration)",
        PianoRollModule.NoteOutputMode.NOTE_ON_OFF to "NOTE_ON / NOTE_OFF",
    )
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(30.dp)
                .focusProperties { canFocus = false }
        ) {
            val shortLabel = if (mode == PianoRollModule.NoteOutputMode.NOTE) "NOTE" else "ON/OFF"
            Text("Out: $shortLabel", fontSize = 11.sp, color = PR_RULER_TEXT)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (candidate, label) ->
                CompactDropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onChange(candidate)
                        expanded = false
                    },
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- ruler

@Composable
private fun PianoRollRuler(module: PianoRollModule, view: PianoRollView, playheadTicks: Double?) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(PR_RULER_BG)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun setMarker(x: Float) {
                        val ticks = view.ticksAt(x).roundToInt()
                        module.playStartTicks = PianoRollTiming.snapTicks(ticks)
                        FileState.markDirty()
                    }
                    setMarker(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.find { it.id == down.id } ?: break
                        if (!change.pressed) break
                        setMarker(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        val h = size.height
        val labelStyle = TextStyle(color = PR_RULER_TEXT, fontSize = 9.sp)

        val firstTick = ((view.scrollTicks / PianoRollTiming.GRID_TICKS).toInt() - 1) * PianoRollTiming.GRID_TICKS
        val lastTick = view.ticksAt(size.width).toInt() + PianoRollTiming.GRID_TICKS
        var ticks = firstTick
        while (ticks <= lastTick) {
            if (ticks >= 0) {
                val x = view.xOf(ticks.toFloat())
                val isMeasure = ticks % PianoRollTiming.TICKS_PER_MEASURE == 0
                val isBeat = ticks % PianoRollTiming.TPB == 0
                val tickH = when {
                    isMeasure -> 12f
                    isBeat -> 8f
                    else -> 4f
                }
                drawLine(PR_RULER_TEXT, Offset(x, h), Offset(x, h - tickH), strokeWidth = 1f)
                if (isMeasure) {
                    val measureNum = ticks / PianoRollTiming.TICKS_PER_MEASURE + 1
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "$measureNum",
                        style = labelStyle,
                        topLeft = Offset(x + 3f, 1f),
                    )
                }
            }
            ticks += PianoRollTiming.GRID_TICKS
        }

        // Play-start marker: red triangle.
        val markerX = view.xOf(module.playStartTicks.toFloat())
        if (markerX >= -10f && markerX <= size.width + 10f) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(markerX - 6f, h - 8f)
                lineTo(markerX + 6f, h - 8f)
                lineTo(markerX, h)
                close()
            }
            drawPath(path, PR_PLAYHEAD)
        }

        // Playhead triangle while playing.
        playheadTicks?.let {
            val px = view.xOf(it.toFloat())
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(px - 5f, 0f)
                lineTo(px + 5f, 0f)
                lineTo(px, 8f)
                close()
            }
            drawPath(path, PR_PLAYHEAD)
        }
    }
}

// ------------------------------------------------------------------------- helpers

/** Formats a pitch with exactly two decimal places (no String.format in common code). */
internal fun formatPitch(pitch: Double): String {
    val scaled = (pitch * 100.0).roundToInt()
    val whole = scaled / 100
    val frac = abs(scaled % 100)
    val fracStr = if (frac < 10) "0$frac" else "$frac"
    return "$whole.$fracStr"
}
