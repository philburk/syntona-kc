package com.syntona.dataflow.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
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
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.syntona.dataflow.editor.CompactDropdownMenuItem
import com.syntona.dataflow.editor.EditActionHandler
import com.syntona.dataflow.editor.FileState
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.GraphViewSettings
import com.syntona.dataflow.editor.LocalMdiState
import com.syntona.dataflow.editor.moveCursor
import com.syntona.dataflow.editor.scoreSymbolCursor
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.ui.PlayIcon
import com.syntona.ui.StopIcon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import syntonakc.shared.generated.resources.Res
import syntonakc.shared.generated.resources.leland
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private object ScoreClipboard {
    var items: List<ScoreItem> = emptyList()
}

private enum class ScoreEditMode {
    SELECT,
    ENTRY,
}

private sealed interface ScoreDragState {
    class PitchDrag(
        val location: ScoreItemLocation,
        val initialStep: Int,
        val initialAlter: Int,
        val beforeMeasures: List<ScoreMeasure>,
        val downY: Float,
        var lastStep: Int,
        val anchorX: Float,
        var anchorY: Float,
    ) : ScoreDragState

    class RubberBand(
        val start: Offset,
        val additive: Boolean,
        val baseSelection: Set<ScoreItemLocation>,
    ) : ScoreDragState {
        var end by mutableStateOf(start)
    }
}

@Composable
fun ScoreEditor(module: ScoreModule, state: GraphState) {
    val isDark = GraphViewSettings.isDarkMode
    val bgColor = if (isDark) Color(0xFF1A1A2E) else Color(0xFFFFFDF8)
    val toolbarBg = if (isDark) Color(0xFF12122A) else Color(0xFFE2E8F0)
    val toolbarText = if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
    val accentColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val borderMuted = if (isDark) Color(0x33FFFFFF) else Color(0x33000000)

    val lelandFont = Font(Res.font.leland)
    val musicFontFamily = remember(lelandFont) { FontFamily(lelandFont) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val mdiState = LocalMdiState.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var staffSpaceDp by remember { mutableStateOf(10f) }
    val scrollState = rememberScrollState()
    val toolbarScrollState = rememberScrollState()

    var editorMode by remember { mutableStateOf(ScoreEditMode.ENTRY) }
    var entryDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var entryIsRest by remember { mutableStateOf(false) }
    var openPalette by remember { mutableStateOf<ScorePaletteKind?>(null) }
    var pendingAccidental by remember { mutableStateOf<Accidental?>(null) }
    var pendingStaccato by remember { mutableStateOf(false) }
    var selectedLocations by remember { mutableStateOf<Set<ScoreItemLocation>>(emptySet()) }
    var activePitchText by remember { mutableStateOf<String?>(null) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    var hoveringItem by remember { mutableStateOf(false) }
    var dragState by remember { mutableStateOf<ScoreDragState?>(null) }

    val score = module.scoreData

    fun refreshPitchDisplayFromSelection(
        measures: List<ScoreMeasure> = score.measures,
        selection: Set<ScoreItemLocation> = selectedLocations,
    ) {
        val selectedNotes = selection.mapNotNull { ScoreEditing.itemAt(measures, it) as? ScoreItem.Note }
        activePitchText = if (selectedNotes.size == 1) {
            selectedNotes.first().pitch.pitchDisplay()
        } else if (selectedNotes.size > 1) {
            "${selectedNotes.first().pitch.pitchDisplay()} (+${selectedNotes.size - 1})"
        } else {
            null
        }
    }

    fun postCommand(command: SyntonaCommand) {
        val engine = state.engine ?: module.getEngine()
        if (engine != null) {
            engine.post(command)
        } else {
            command.execute()
        }
        state.markChanged()
    }

    fun deleteSelection() {
        if (selectedLocations.isEmpty()) return
        val before = score.measures.toList()
        val after = ScoreEditing.deleteItems(before, selectedLocations)
        selectedLocations = emptySet()
        activePitchText = null
        postCommand(
            ReplaceScoreMeasuresCommand(score, before, after) { restored ->
                selectedLocations = selectedLocations.filterTo(linkedSetOf()) {
                    ScoreEditing.itemAt(restored, it) != null
                }
                refreshPitchDisplayFromSelection(restored, selectedLocations)
            }
        )
    }

    fun selectDuration(duration: NoteDuration) {
        if (editorMode == ScoreEditMode.ENTRY && entryDuration == duration) {
            entryIsRest = !entryIsRest
        } else {
            editorMode = ScoreEditMode.ENTRY
            entryDuration = duration
            entryIsRest = false
        }
    }

    fun applyAccidental(accidental: Accidental) {
        val before = score.measures.toList()
        val after = ScoreEditing.setAccidentalOnSelection(before, selectedLocations, accidental.alter)
        val hasSelectedNotes = selectedLocations.any { ScoreEditing.itemAt(before, it) is ScoreItem.Note }
        if (hasSelectedNotes) {
            if (after != before) {
                postCommand(ReplaceScoreMeasuresCommand(score, before, after))
            }
            val firstNote = selectedLocations.firstNotNullOfOrNull { ScoreEditing.itemAt(after, it) as? ScoreItem.Note }
            if (firstNote != null) {
                module.previewPitch(firstNote.pitch.midiPitch.toDouble(), isStaccato = firstNote.isStaccato)
                refreshPitchDisplayFromSelection(after, selectedLocations)
            }
        } else {
            pendingAccidental = if (pendingAccidental == accidental) null else accidental
        }
    }

    fun toggleStaccato() {
        val before = score.measures.toList()
        val hasSelectedNotes = selectedLocations.any { ScoreEditing.itemAt(before, it) is ScoreItem.Note }
        if (hasSelectedNotes) {
            val after = ScoreEditing.toggleStaccatoOnSelection(before, selectedLocations)
            if (after != before) {
                postCommand(ReplaceScoreMeasuresCommand(score, before, after))
            }
            val firstNote = selectedLocations.firstNotNullOfOrNull { ScoreEditing.itemAt(after, it) as? ScoreItem.Note }
            if (firstNote != null) {
                module.previewPitch(firstNote.pitch.midiPitch.toDouble(), isStaccato = firstNote.isStaccato)
            }
        } else {
            pendingStaccato = !pendingStaccato
        }
    }

    fun copySelection() {
        val sorted = selectedLocations
            .sortedWith(compareBy<ScoreItemLocation> { it.measureIdx }.thenBy { it.itemIdx })
        val copied = sorted.mapNotNull { ScoreEditing.itemAt(score.measures, it) }
        if (copied.isNotEmpty()) {
            ScoreClipboard.items = copied
        }
    }

    fun pasteClipboard() {
        val clip = ScoreClipboard.items
        if (clip.isEmpty()) return
        val before = score.measures.toList()
        val lastSel = selectedLocations
            .maxWithOrNull(compareBy<ScoreItemLocation> { it.measureIdx }.thenBy { it.itemIdx })
        var targetMIdx = lastSel?.measureIdx ?: (before.lastIndex.coerceAtLeast(0))
        var targetIIdx = if (lastSel != null) {
            lastSel.itemIdx + 1
        } else {
            before.getOrNull(targetMIdx)?.items?.size ?: 0
        }
        var current = before
        val pastedLocs = linkedSetOf<ScoreItemLocation>()
        for (item in clip) {
            val (next, loc) = ScoreEditing.insertItem(current, targetMIdx, targetIIdx, item)
            current = next
            pastedLocs.add(loc)
            targetMIdx = loc.measureIdx
            targetIIdx = loc.itemIdx + 1
        }
        selectedLocations = pastedLocs
        refreshPitchDisplayFromSelection(current, pastedLocs)
        postCommand(
            ReplaceScoreMeasuresCommand(score, before, current) { restored ->
                selectedLocations = pastedLocs.filterTo(linkedSetOf()) {
                    ScoreEditing.itemAt(restored, it) != null
                }
                refreshPitchDisplayFromSelection(restored, selectedLocations)
            }
        )
    }

    // Route Cut / Copy / Paste / Delete from the global window Edit handler while Score window is active.
    DisposableEffect(module) {
        val window = mdiState.windows.find { it.id == module }
        val handler = object : EditActionHandler {
            override fun canCopy() = selectedLocations.isNotEmpty()
            override fun canPaste() = ScoreClipboard.items.isNotEmpty()
            override fun canDelete() = selectedLocations.isNotEmpty()
            override fun cut() {
                copySelection()
                deleteSelection()
            }
            override fun copy() = copySelection()
            override fun paste() = pasteClipboard()
            override fun delete() = deleteSelection()
        }
        window?.editHandler = handler
        onDispose {
            if (window?.editHandler === handler) window.editHandler = null
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(openPalette) {
        if (openPalette == null) {
            focusRequester.requestFocus()
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isMetaPressed || event.isCtrlPressed) return false
        return when {
            event.key == Key.Escape -> {
                if (openPalette != null) {
                    openPalette = null
                } else {
                    editorMode = ScoreEditMode.SELECT
                    hoverPos = null
                }
                true
            }
            event.key == Key.Delete || event.key == Key.Backspace -> {
                deleteSelection()
                true
            }
            event.key == Key.Spacebar -> {
                module.requestPlayToggle()
                true
            }
            else -> when (event.utf16CodePoint.toChar().lowercaseChar()) {
                't' -> { selectDuration(NoteDuration.THIRTY_SECOND); true }
                's' -> { selectDuration(NoteDuration.SIXTEENTH); true }
                'e' -> { selectDuration(NoteDuration.EIGHTH); true }
                'q' -> { selectDuration(NoteDuration.QUARTER); true }
                'h' -> { selectDuration(NoteDuration.HALF); true }
                'w' -> { selectDuration(NoteDuration.WHOLE); true }
                '1' -> { toggleStaccato(); true }
                '2' -> { applyAccidental(Accidental.FLAT); true }
                '3' -> { applyAccidental(Accidental.SHARP); true }
                '4' -> { applyAccidental(Accidental.NATURAL); true }
                else -> false
            }
        }
    }

    val measureGlyphWidth = remember(textMeasurer, density, musicFontFamily) {
        { glyph: String, fontSizePx: Float ->
            val fontSizeSp = with(density) { fontSizePx.toSp() }
            textMeasurer.measure(
                text = glyph,
                style = TextStyle(fontFamily = musicFontFamily, fontSize = fontSizeSp),
                softWrap = false,
                maxLines = 1,
            ).size.width.toFloat()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent(::handleKeyEvent)
    ) {
        // Top toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(toolbarBg)
                .horizontalScroll(toolbarScrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = { module.requestPlayToggle() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (module.isPlaying) {
                        StopIcon(color = Color(0xFFEF4444), size = 10.dp)
                        Text("Stop", fontSize = 12.sp, color = Color(0xFFEF4444))
                    } else {
                        val playColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                        PlayIcon(color = playColor, size = 10.dp)
                        Text("Play", fontSize = 12.sp, color = playColor)
                    }
                }
            }

            Text("|", color = borderMuted)

            ToolbarToggleChip(
                selected = editorMode == ScoreEditMode.SELECT,
                accentColor = accentColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = {
                    editorMode = ScoreEditMode.SELECT
                    hoverPos = null
                },
            ) { color ->
                Text("Select", fontSize = 11.sp, color = color)
            }

            Text("|", color = borderMuted)

            val selectedNotes = selectedLocations.mapNotNull { ScoreEditing.itemAt(score.measures, it) as? ScoreItem.Note }
            val staccatoActive = if (selectedNotes.isNotEmpty()) {
                selectedNotes.all { it.isStaccato }
            } else {
                pendingStaccato
            }
            val activeAlter = if (selectedNotes.isNotEmpty()) {
                selectedNotes.map { it.pitch.alter }.distinct().singleOrNull()
            } else {
                pendingAccidental?.alter
            }

            ToolbarToggleChip(
                selected = staccatoActive,
                accentColor = accentColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = { toggleStaccato() },
            ) { color ->
                Text("1·", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
            }

            ToolbarToggleChip(
                selected = activeAlter == Accidental.FLAT.alter,
                accentColor = accentColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = { applyAccidental(Accidental.FLAT) },
            ) { color ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("2", fontSize = 9.sp, color = color.copy(alpha = 0.75f))
                    Text(SmuflGlyphs.ACCIDENTAL_FLAT, fontFamily = musicFontFamily, fontSize = 15.sp, color = color)
                }
            }

            ToolbarToggleChip(
                selected = activeAlter == Accidental.SHARP.alter,
                accentColor = accentColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = { applyAccidental(Accidental.SHARP) },
            ) { color ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("3", fontSize = 9.sp, color = color.copy(alpha = 0.75f))
                    Text(SmuflGlyphs.ACCIDENTAL_SHARP, fontFamily = musicFontFamily, fontSize = 15.sp, color = color)
                }
            }

            ToolbarToggleChip(
                selected = activeAlter == Accidental.NATURAL.alter,
                accentColor = accentColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = { applyAccidental(Accidental.NATURAL) },
            ) { color ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("4", fontSize = 9.sp, color = color.copy(alpha = 0.75f))
                    Text(SmuflGlyphs.ACCIDENTAL_NATURAL, fontFamily = musicFontFamily, fontSize = 15.sp, color = color)
                }
            }

            Text("|", color = borderMuted)

            ScoreKeySelector(
                selected = score.keySignature,
                textColor = toolbarText,
                onSelect = {
                    score.keySignature = it
                    FileState.markDirty()
                    state.markChanged()
                },
            )

            ScoreTimeSigSelector(
                selected = score.timeSignature,
                textColor = toolbarText,
                onSelect = {
                    score.timeSignature = it
                    FileState.markDirty()
                    state.markChanged()
                },
            )

            TextButton(
                onClick = {
                    val before = score.measures.toList()
                    val after = ScoreEditing.appendEmptyMeasure(before)
                    postCommand(ReplaceScoreMeasuresCommand(score, before, after))
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false },
            ) {
                Text("+ Bar", fontSize = 11.sp, color = toolbarText)
            }

            TextButton(
                onClick = {
                    val before = score.measures.toList()
                    val after = ScoreEditing.clearToSingleEmptyMeasure()
                    selectedLocations = emptySet()
                    activePitchText = null
                    postCommand(ReplaceScoreMeasuresCommand(score, before, after))
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false },
            ) {
                Text("Clear", fontSize = 11.sp, color = toolbarText)
            }

            Text("|", color = borderMuted)

            Text("Size", fontSize = 10.sp, color = toolbarText)
            Slider(
                value = staffSpaceDp,
                onValueChange = { staffSpaceDp = it },
                valueRange = 6f..18f,
                modifier = Modifier
                    .width(84.dp)
                    .focusProperties { canFocus = false },
                colors = SliderDefaults.colors(
                    thumbColor = if (isDark) Color.White else Color.DarkGray,
                    activeTrackColor = if (isDark) Color.Yellow else Color(0xFFF59E0B),
                    inactiveTrackColor = if (isDark) Color.LightGray else Color(0xFF94A3B8),
                ),
            )
        }

        // Main area: left palette panel + scrollable score canvas + horizontal scrollbar
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ScorePalettePanel(
                openPalette = openPalette,
                editorMode = editorMode,
                entryDuration = entryDuration,
                entryIsRest = entryIsRest,
                toolbarBg = toolbarBg,
                toolbarText = toolbarText,
                accentColor = accentColor,
                borderMuted = borderMuted,
                isDark = isDark,
                musicFontFamily = musicFontFamily,
                textMeasurer = textMeasurer,
                onTogglePalette = { kind ->
                    openPalette = if (openPalette == kind) null else kind
                },
            ) { kind, buttonWidthPx ->
                if (kind == openPalette && kind == ScorePaletteKind.NOTES_RESTS) {
                    val gapPx = with(density) { 4.dp.roundToPx() }
                    NotesRestsPalettePopup(
                        offsetX = (buttonWidthPx + gapPx).coerceAtLeast(1),
                        editorMode = editorMode,
                        entryDuration = entryDuration,
                        entryIsRest = entryIsRest,
                        isDark = isDark,
                        toolbarText = toolbarText,
                        accentColor = accentColor,
                        borderMuted = borderMuted,
                        musicFontFamily = musicFontFamily,
                        textMeasurer = textMeasurer,
                        onSelectOption = { duration, isRest ->
                            editorMode = ScoreEditMode.ENTRY
                            entryDuration = duration
                            entryIsRest = isRest
                            openPalette = null
                            focusRequester.requestFocus()
                        },
                        onDismiss = { openPalette = null },
                        onKeyEvent = ::handleKeyEvent,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Scrollable single-stave score canvas
                // Note: horizontalScroll MUST wrap an inner container rather than BoxWithConstraints itself,
                // otherwise horizontalScroll passes maxWidth=Infinity into BoxWithConstraints and collapses
                // the canvas hit-test bounds.
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    val viewportWidth = maxWidth
                    val viewportHeight = maxHeight
                    val staffSpacePx = with(density) { staffSpaceDp.dp.toPx() }
                    val minWidthPx = with(density) { viewportWidth.toPx() }.coerceAtLeast(600f)
                    val measuresSnapshot = score.measures.toList()
                    val layout = remember(
                        measuresSnapshot,
                        score.clef,
                        score.keySignature,
                        score.timeSignature,
                        staffSpacePx,
                        minWidthPx,
                    ) {
                        ScoreRenderer.computeLayout(
                            score = score,
                            staffSpacePx = staffSpacePx,
                            minWidthPx = minWidthPx,
                            measureGlyphWidth = measureGlyphWidth,
                        )
                    }
                    val currentLayout by rememberUpdatedState(layout)
                    val currentStaffSpacePx by rememberUpdatedState(staffSpacePx)

                    LaunchedEffect(module.activeItemIndex, module.isPlaying) {
                        if (!module.isPlaying) return@LaunchedEffect
                        val activeIdx = module.activeItemIndex ?: return@LaunchedEffect
                        val placed = currentLayout.placedMeasures.asSequence()
                            .flatMap { it.items.asSequence() }
                            .firstOrNull { it.globalIdx == activeIdx } ?: return@LaunchedEffect
                        val vpW = with(density) { viewportWidth.toPx() }
                        val curScroll = scrollState.value.toFloat()
                        if (placed.boxRight > curScroll + vpW - 24f || placed.boxLeft < curScroll) {
                            val target = (placed.boxLeft - vpW * 0.2f).roundToInt()
                                .coerceIn(0, scrollState.maxValue)
                            scrollState.animateScrollTo(target)
                        }
                    }

            val canvasWidthDp = maxOf(
                viewportWidth,
                with(density) { layout.totalWidthPx.toDp() },
            )
            val renderColors = ScoreRenderColors.forTheme(isDark)

            val activeCursor = when {
                dragState is ScoreDragState.PitchDrag -> moveCursor()
                editorMode == ScoreEditMode.ENTRY -> scoreSymbolCursor(entryDuration, entryIsRest, isDark)
                hoveringItem -> moveCursor()
                else -> PointerIcon.Default
            }

            val ghostPreview = remember(
                editorMode,
                hoverPos,
                dragState,
                layout,
                entryDuration,
                entryIsRest,
                pendingAccidental,
                pendingStaccato,
            ) {
                if (editorMode != ScoreEditMode.ENTRY || dragState != null) return@remember null
                val pos = hoverPos ?: return@remember null
                if (pos.x < layout.headerEndX - layout.staffSpacePx * 0.5f) return@remember null
                val step = layout.stepAtY(pos.y)
                ScoreGhostPreview(
                    x = pos.x - layout.staffSpacePx * 0.65f,
                    staffStep = step,
                    duration = entryDuration,
                    isRest = entryIsRest,
                    accidental = if (entryIsRest) null else pendingAccidental,
                    isStaccato = if (entryIsRest) false else pendingStaccato,
                )
            }

            val rubberBandState = dragState as? ScoreDragState.RubberBand
            val rubberBand = rubberBandState?.let { rb ->
                ScoreRubberBand(
                    left = min(rb.start.x, rb.end.x),
                    top = min(rb.start.y, rb.end.y),
                    right = max(rb.start.x, rb.end.x),
                    bottom = max(rb.start.y, rb.end.y),
                )
            }
            val displayedSelectedLocations = if (rubberBandState != null && rubberBand != null) {
                val liveHits = layout.itemsInRect(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.bottom)
                if (rubberBandState.additive) rubberBandState.baseSelection + liveHits else liveHits
            } else {
                selectedLocations
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .height(viewportHeight)
                ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerHoverIcon(activeCursor)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Move -> {
                                            val pos = event.changes.firstOrNull()?.position
                                            hoverPos = pos
                                            hoveringItem = pos != null && currentLayout.itemAt(pos.x, pos.y) != null
                                        }
                                        PointerEventType.Exit -> {
                                            hoverPos = null
                                            hoveringItem = false
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                focusRequester.requestFocus()
                                down.consume()

                                val downPos = down.position
                                val isShift = currentEvent.keyboardModifiers.isShiftPressed
                                val lyt = currentLayout
                                val sp = currentStaffSpacePx
                                val hit = lyt.itemAt(downPos.x, downPos.y)

                                fun beginNoteDrag(
                                    loc: ScoreItemLocation,
                                    step: Int,
                                    alter: Int,
                                    beforeMeasures: List<ScoreMeasure>,
                                    anchorX: Float,
                                ) {
                                    dragState = ScoreDragState.PitchDrag(
                                        location = loc,
                                        initialStep = step,
                                        initialAlter = alter,
                                        beforeMeasures = beforeMeasures,
                                        downY = downPos.y,
                                        lastStep = step,
                                        anchorX = anchorX,
                                        anchorY = lyt.yForStep(step),
                                    )
                                }

                                if (editorMode == ScoreEditMode.SELECT) {
                                    if (hit != null) {
                                        if (isShift) {
                                            selectedLocations = if (hit.location in selectedLocations) {
                                                selectedLocations - hit.location
                                            } else {
                                                selectedLocations + hit.location
                                            }
                                            refreshPitchDisplayFromSelection()
                                        } else {
                                            selectedLocations = setOf(hit.location)
                                            val item = hit.item
                                            if (item is ScoreItem.Note) {
                                                module.previewPitch(item.pitch.midiPitch.toDouble(), isStaccato = item.isStaccato)
                                                activePitchText = item.pitch.pitchDisplay()
                                                val step = hit.staffStep ?: score.clef.staffStepFromBottom(item.pitch)
                                                beginNoteDrag(hit.location, step, item.pitch.alter, score.measures.toList(), hit.x)
                                            } else {
                                                activePitchText = null
                                            }
                                        }
                                    } else {
                                        if (!isShift) {
                                            selectedLocations = emptySet()
                                            activePitchText = null
                                        }
                                        dragState = ScoreDragState.RubberBand(
                                            start = downPos,
                                            additive = isShift,
                                            baseSelection = selectedLocations,
                                        )
                                    }
                                } else {
                                    // ENTRY mode
                                    val beforeMeasures = score.measures.toList()
                                    if (hit != null) {
                                        val isDifferentKindOrDuration =
                                            (entryIsRest && hit.item is ScoreItem.Note) ||
                                                (!entryIsRest && hit.item is ScoreItem.Rest) ||
                                                (hit.item.duration != entryDuration)

                                        if (isDifferentKindOrDuration) {
                                            if (entryIsRest) {
                                                val newRest = ScoreItem.Rest(entryDuration)
                                                val after = ScoreEditing.replaceItem(beforeMeasures, hit.location, newRest)
                                                selectedLocations = setOf(hit.location)
                                                activePitchText = null
                                                postCommand(ReplaceScoreMeasuresCommand(score, beforeMeasures, after))
                                            } else {
                                                val step = lyt.stepAtY(downPos.y)
                                                val pitch = score.clef.pitchForStaffStep(
                                                    stepFromBottom = step,
                                                    keySignature = score.keySignature,
                                                    alterOverride = pendingAccidental?.alter,
                                                )
                                                val newNote = ScoreItem.Note(
                                                    pitch = pitch,
                                                    duration = entryDuration,
                                                    isStaccato = pendingStaccato,
                                                )
                                                pendingAccidental = null
                                                val after = ScoreEditing.replaceItem(beforeMeasures, hit.location, newNote)
                                                score.replaceMeasures(after)
                                                selectedLocations = setOf(hit.location)
                                                activePitchText = pitch.pitchDisplay()
                                                module.previewPitch(pitch.midiPitch.toDouble(), isStaccato = newNote.isStaccato)
                                                beginNoteDrag(hit.location, step, pitch.alter, beforeMeasures, hit.x)
                                            }
                                        } else {
                                            selectedLocations = setOf(hit.location)
                                            val item = hit.item
                                            if (item is ScoreItem.Note) {
                                                module.previewPitch(item.pitch.midiPitch.toDouble(), isStaccato = item.isStaccato)
                                                activePitchText = item.pitch.pitchDisplay()
                                                val step = hit.staffStep ?: score.clef.staffStepFromBottom(item.pitch)
                                                beginNoteDrag(hit.location, step, item.pitch.alter, beforeMeasures, hit.x)
                                            } else {
                                                activePitchText = null
                                            }
                                        }
                                    } else {
                                        val target = lyt.insertionTargetAt(downPos.x)
                                        if (target != null) {
                                            if (entryIsRest) {
                                                val newRest = ScoreItem.Rest(entryDuration)
                                                val (after, newLoc) = ScoreEditing.insertItem(
                                                    measures = beforeMeasures,
                                                    measureIdx = target.measureIdx,
                                                    itemIdx = target.itemIdx,
                                                    item = newRest,
                                                    appendNewMeasure = target.appendNewMeasure,
                                                )
                                                selectedLocations = setOf(newLoc)
                                                activePitchText = null
                                                postCommand(ReplaceScoreMeasuresCommand(score, beforeMeasures, after))
                                            } else {
                                                val step = lyt.stepAtY(downPos.y)
                                                val pitch = score.clef.pitchForStaffStep(
                                                    stepFromBottom = step,
                                                    keySignature = score.keySignature,
                                                    alterOverride = pendingAccidental?.alter,
                                                )
                                                val newNote = ScoreItem.Note(
                                                    pitch = pitch,
                                                    duration = entryDuration,
                                                    isStaccato = pendingStaccato,
                                                )
                                                pendingAccidental = null
                                                val (after, newLoc) = ScoreEditing.insertItem(
                                                    measures = beforeMeasures,
                                                    measureIdx = target.measureIdx,
                                                    itemIdx = target.itemIdx,
                                                    item = newNote,
                                                    appendNewMeasure = target.appendNewMeasure,
                                                )
                                                score.replaceMeasures(after)
                                                selectedLocations = setOf(newLoc)
                                                activePitchText = pitch.pitchDisplay()
                                                module.previewPitch(pitch.midiPitch.toDouble(), isStaccato = newNote.isStaccato)
                                                beginNoteDrag(newLoc, step, pitch.alter, beforeMeasures, downPos.x)
                                            }
                                        }
                                    }
                                }

                                // Drag loop
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val pos = change.position
                                    hoverPos = pos

                                    when (val drag = dragState) {
                                        is ScoreDragState.PitchDrag -> {
                                            val deltaSteps = ((drag.downY - pos.y) / (sp * 0.5f)).roundToInt()
                                            val newStep = (drag.initialStep + deltaSteps).coerceIn(-12, 20)
                                            if (newStep != drag.lastStep) {
                                                drag.lastStep = newStep
                                                val alterOverride = if (newStep == drag.initialStep) drag.initialAlter else null
                                                val newPitch = score.clef.pitchForStaffStep(
                                                    stepFromBottom = newStep,
                                                    keySignature = score.keySignature,
                                                    alterOverride = alterOverride,
                                                )
                                                val currentMeasures = score.measures.toList()
                                                val updated = ScoreEditing.setNotePitch(currentMeasures, drag.location, newPitch)
                                                score.replaceMeasures(updated)
                                                drag.anchorY = lyt.yForStep(newStep)
                                                activePitchText = newPitch.pitchDisplay()
                                                val updatedNote = ScoreEditing.itemAt(updated, drag.location) as? ScoreItem.Note
                                                module.previewPitch(
                                                    newPitch.midiPitch.toDouble(),
                                                    isStaccato = updatedNote?.isStaccato == true,
                                                )
                                            }
                                        }
                                        is ScoreDragState.RubberBand -> {
                                            drag.end = pos
                                        }
                                        null -> {}
                                    }
                                    change.consume()
                                }

                                // Pointer up: commit drag command or rubber band selection
                                when (val drag = dragState) {
                                    is ScoreDragState.PitchDrag -> {
                                        val afterMeasures = score.measures.toList()
                                        if (afterMeasures != drag.beforeMeasures) {
                                            val loc = drag.location
                                            postCommand(
                                                ReplaceScoreMeasuresCommand(score, drag.beforeMeasures, afterMeasures) { restored ->
                                                    selectedLocations = if (ScoreEditing.itemAt(restored, loc) != null) {
                                                        setOf(loc)
                                                    } else {
                                                        emptySet()
                                                    }
                                                    refreshPitchDisplayFromSelection(restored, selectedLocations)
                                                }
                                            )
                                        }
                                    }
                                    is ScoreDragState.RubberBand -> {
                                        val left = min(drag.start.x, drag.end.x)
                                        val top = min(drag.start.y, drag.end.y)
                                        val right = max(drag.start.x, drag.end.x)
                                        val bottom = max(drag.start.y, drag.end.y)
                                        val hits = lyt.itemsInRect(left, top, right, bottom)
                                        selectedLocations = if (drag.additive) drag.baseSelection + hits else hits
                                        refreshPitchDisplayFromSelection()
                                    }
                                    null -> {}
                                }
                                dragState = null
                            }
                        }
                ) {
                    val target = ComposeScoreDrawTarget(
                        drawScope = this,
                        textMeasurer = textMeasurer,
                        density = density,
                        musicFontFamily = musicFontFamily,
                    )
                    ScoreRenderer.render(
                        score = score,
                        target = target,
                        staffSpacePx = staffSpacePx,
                        minWidthPx = minWidthPx,
                        colors = renderColors,
                        activeItemGlobalIndex = module.activeItemIndex,
                        selectedLocations = displayedSelectedLocations,
                        ghostPreview = ghostPreview,
                        rubberBand = rubberBand,
                    )
                }

                // Floating pitch badge right next to the note being placed/dragged
                val pitchDrag = dragState as? ScoreDragState.PitchDrag
                val pitchLabel = activePitchText
                if (pitchDrag != null && pitchLabel != null) {
                    val badgeX = (pitchDrag.anchorX + 20f).roundToInt()
                    val badgeY = (pitchDrag.anchorY - 34f).roundToInt().coerceAtLeast(4)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(badgeX, badgeY) }
                            .background(
                                color = if (isDark) Color(0xE60F172A) else Color(0xE61E293B),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .border(1.dp, accentColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = pitchLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                }
            }
        }

                ScoreHorizontalScrollBar(
                    scrollState = scrollState,
                    toolbarBg = toolbarBg,
                    accentColor = accentColor,
                    isDark = isDark,
                    onJumpTo = { targetPx ->
                        coroutineScope.launch { scrollState.scrollTo(targetPx) }
                    },
                )
            }
        }

        // Bottom status bar with pitch display and shortcut reference
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(toolbarBg)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val modeSummary = if (editorMode == ScoreEditMode.SELECT) {
                "Mode: Select"
            } else {
                "Mode: ${entryDuration.displayLabel} ${if (entryIsRest) "Rest" else "Note"}"
            }
            Text(modeSummary, fontSize = 11.sp, color = toolbarText, fontWeight = FontWeight.Medium)

            if (activePitchText != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = activePitchText!!,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else accentColor,
                    )
                }
            } else if (selectedLocations.isNotEmpty()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${selectedLocations.size} selected",
                    fontSize = 11.sp,
                    color = accentColor,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Esc: Select | t s e q h w: Dur (2x=Rest) | 1: Stacc  2: b  3: #  4: Nat | Del: Delete",
                fontSize = 10.sp,
                color = toolbarText.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToolbarToggleChip(
    selected: Boolean,
    accentColor: Color,
    borderMuted: Color,
    textColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val contentColor = if (selected) {
        if (isDark) Color.White else accentColor
    } else {
        textColor
    }
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = Modifier
            .defaultMinSize(minWidth = 26.dp, minHeight = 28.dp)
            .height(28.dp)
            .border(
                width = 1.dp,
                color = if (selected) accentColor else borderMuted,
                shape = RoundedCornerShape(4.dp),
            )
            .focusProperties { canFocus = false },
    ) {
        content(contentColor)
    }
}

@Composable
private fun ScoreKeySelector(
    selected: KeySignature,
    textColor: Color,
    onSelect: (KeySignature) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier
                .height(28.dp)
                .focusProperties { canFocus = false },
        ) {
            Text("Key: ${selected.label}", fontSize = 11.sp, color = textColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            KeySignature.COMMON_PHASE1_PRESETS.forEach { preset ->
                CompactDropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ScoreTimeSigSelector(
    selected: TimeSignature,
    textColor: Color,
    onSelect: (TimeSignature) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier
                .height(28.dp)
                .focusProperties { canFocus = false },
        ) {
            Text("Time: ${selected.label}", fontSize = 11.sp, color = textColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeSignature.PRESETS.forEach { preset ->
                CompactDropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ScorePalettePanel(
    openPalette: ScorePaletteKind?,
    editorMode: ScoreEditMode,
    entryDuration: NoteDuration,
    entryIsRest: Boolean,
    toolbarBg: Color,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    isDark: Boolean,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    onTogglePalette: (ScorePaletteKind) -> Unit,
    popupContent: @Composable (ScorePaletteKind, Int) -> Unit,
) {
    val panelScrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(toolbarBg)
    ) {
        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .verticalScroll(panelScrollState)
                .padding(horizontal = 5.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScorePaletteKind.entries.forEach { kind ->
                val isOpen = openPalette == kind
                val isToolActive = when (kind) {
                    ScorePaletteKind.NOTES_RESTS -> editorMode == ScoreEditMode.ENTRY
                }
                val highlighted = isOpen || isToolActive
                val contentColor = if (highlighted) {
                    if (isDark) Color.White else accentColor
                } else {
                    toolbarText
                }
                val tileBg = when {
                    isOpen -> accentColor.copy(alpha = 0.24f)
                    isToolActive -> accentColor.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
                var buttonWidthPx by remember { mutableStateOf(0) }
                Box(
                    modifier = Modifier.onGloballyPositioned { coords ->
                        buttonWidthPx = coords.size.width
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tileBg, RoundedCornerShape(6.dp))
                            .border(
                                width = 1.dp,
                                color = if (highlighted) accentColor else borderMuted,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onTogglePalette(kind) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        when (kind) {
                            ScorePaletteKind.NOTES_RESTS -> {
                                val previewDur = if (editorMode == ScoreEditMode.ENTRY) {
                                    entryDuration
                                } else {
                                    NoteDuration.QUARTER
                                }
                                val previewIsRest = editorMode == ScoreEditMode.ENTRY && entryIsRest
                                val previewGlyph = if (previewIsRest) {
                                    previewDur.restGlyph
                                } else {
                                    previewDur.noteSymbolGlyph
                                }
                                ScorePaletteGlyph(
                                    glyph = previewGlyph,
                                    isRest = previewIsRest,
                                    duration = previewDur,
                                    color = contentColor,
                                    staffLineColor = borderMuted,
                                    musicFontFamily = musicFontFamily,
                                    textMeasurer = textMeasurer,
                                    width = 36.dp,
                                    height = 24.dp,
                                    glyphSizeSp = 20f,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = kind.buttonLabel,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                color = contentColor,
                                textAlign = TextAlign.Center,
                                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                text = "▸",
                                fontSize = 9.sp,
                                color = contentColor.copy(alpha = 0.8f),
                            )
                        }
                    }
                    popupContent(kind, buttonWidthPx)
                }
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(borderMuted)
        )
    }
}

@Composable
private fun NotesRestsPalettePopup(
    offsetX: Int,
    editorMode: ScoreEditMode,
    entryDuration: NoteDuration,
    entryIsRest: Boolean,
    isDark: Boolean,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    onSelectOption: (NoteDuration, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    val popupBg = if (isDark) Color(0xFF161832) else Color(0xFFFFFFFF)
    val tileBg = if (isDark) Color(0xFF202446) else Color(0xFFF1F5F9)
    val titleColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(offsetX, 0),
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            color = popupBg,
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .wrapContentSize()
                .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                .onKeyEvent(onKeyEvent),
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = ScorePaletteKind.NOTES_RESTS.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )

                NotesRestsPaletteRow(
                    rowLabel = "Notes",
                    options = ScorePalettes.noteOptions,
                    editorMode = editorMode,
                    entryDuration = entryDuration,
                    entryIsRest = entryIsRest,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    onSelectOption = onSelectOption,
                )

                NotesRestsPaletteRow(
                    rowLabel = "Rests",
                    options = ScorePalettes.restOptions,
                    editorMode = editorMode,
                    entryDuration = entryDuration,
                    entryIsRest = entryIsRest,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    onSelectOption = onSelectOption,
                )
            }
        }
    }
}

@Composable
private fun NotesRestsPaletteRow(
    rowLabel: String,
    options: List<ScoreNoteRestOption>,
    editorMode: ScoreEditMode,
    entryDuration: NoteDuration,
    entryIsRest: Boolean,
    isDark: Boolean,
    tileBg: Color,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    onSelectOption: (NoteDuration, Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = rowLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = toolbarText,
            modifier = Modifier.width(30.dp),
        )
        options.forEach { option ->
            val selected = editorMode == ScoreEditMode.ENTRY &&
                entryDuration == option.duration &&
                entryIsRest == option.isRest
            val contentColor = if (selected) {
                if (isDark) Color.White else accentColor
            } else {
                toolbarText
            }
            val bg = if (selected) accentColor.copy(alpha = 0.22f) else tileBg
            Column(
                modifier = Modifier
                    .width(30.dp)
                    .background(bg, RoundedCornerShape(5.dp))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accentColor else borderMuted,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelectOption(option.duration, option.isRest) }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                ScorePaletteGlyph(
                    glyph = option.glyph,
                    isRest = option.isRest,
                    duration = option.duration,
                    color = contentColor,
                    staffLineColor = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    width = 24.dp,
                    height = 20.dp,
                    glyphSizeSp = if (option.isRest) 15f else 17f,
                )
                Text(
                    text = option.shortcutChar.toString(),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor.copy(alpha = if (selected) 1f else 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ScorePaletteGlyph(
    glyph: String,
    isRest: Boolean,
    duration: NoteDuration,
    color: Color,
    staffLineColor: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    width: Dp,
    height: Dp,
    glyphSizeSp: Float,
) {
    Canvas(modifier = Modifier.size(width = width, height = height)) {
        val measured = textMeasurer.measure(
            text = glyph,
            style = TextStyle(fontFamily = musicFontFamily, fontSize = glyphSizeSp.sp, color = color),
            softWrap = false,
            maxLines = 1,
        )
        val x = ((size.width - measured.size.width) * 0.5f).coerceAtLeast(0f)
        val baselineY = when {
            isRest && duration == NoteDuration.WHOLE -> size.height * 0.42f
            isRest && duration == NoteDuration.HALF -> size.height * 0.62f
            isRest -> size.height * 0.52f
            duration == NoteDuration.WHOLE -> size.height * 0.58f
            else -> size.height * 0.80f
        }
        if (isRest && (duration == NoteDuration.WHOLE || duration == NoteDuration.HALF)) {
            val lineHalfW = size.width * 0.34f
            val cx = size.width * 0.5f
            drawLine(
                color = staffLineColor,
                start = Offset(cx - lineHalfW, baselineY),
                end = Offset(cx + lineHalfW, baselineY),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawText(
            textLayoutResult = measured,
            color = color,
            topLeft = Offset(x, baselineY - measured.firstBaseline),
        )
    }
}

@Composable
private fun ScoreHorizontalScrollBar(
    scrollState: ScrollState,
    toolbarBg: Color,
    accentColor: Color,
    isDark: Boolean,
    onJumpTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentScrollPx by rememberUpdatedState(scrollState.value.toFloat())
    val currentMaxScrollPx by rememberUpdatedState(scrollState.maxValue.toFloat())
    val currentOnJumpTo by rememberUpdatedState(onJumpTo)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(toolbarBg)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(scrollState) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val trackWidth = size.width.toFloat()
                    val maxScroll = currentMaxScrollPx
                    if (trackWidth <= 0f || maxScroll <= 0f) return@awaitEachGesture

                    val startScroll = currentScrollPx
                    val metrics = ScoreScrollMath.computeThumbMetrics(
                        trackWidthPx = trackWidth,
                        scrollPx = startScroll,
                        maxScrollPx = maxScroll,
                    )
                    val clickX = down.position.x
                    val isInsideThumb = clickX >= metrics.thumbX &&
                        clickX <= (metrics.thumbX + metrics.thumbWidth)

                    fun applyPointerX(pointerX: Float) {
                        val target = if (isInsideThumb) {
                            ScoreScrollMath.scrollForThumbDrag(
                                startScrollPx = startScroll,
                                deltaX = pointerX - clickX,
                                trackTravelPx = metrics.trackTravel,
                                maxScrollPx = maxScroll,
                            )
                        } else {
                            ScoreScrollMath.scrollForTrackClick(
                                clickX = pointerX,
                                thumbWidthPx = metrics.thumbWidth,
                                trackTravelPx = metrics.trackTravel,
                                maxScrollPx = maxScroll,
                            )
                        }
                        currentOnJumpTo(target)
                    }

                    if (!isInsideThumb) {
                        applyPointerX(clickX)
                    }
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.find { it.id == down.id } ?: break
                        if (!change.pressed) break
                        applyPointerX(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        val trackWidth = size.width
        val trackHeight = size.height
        val trackLineColor = if (isDark) Color(0x18FFFFFF) else Color(0x22000000)

        drawLine(
            color = trackLineColor,
            start = Offset(0f, 0f),
            end = Offset(trackWidth, 0f),
            strokeWidth = 1f,
        )

        val barHeight = 6.dp.toPx()
        val barY = (trackHeight - barHeight) / 2f

        drawRoundRect(
            color = trackLineColor,
            topLeft = Offset(0f, barY),
            size = Size(trackWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )

        val metrics = ScoreScrollMath.computeThumbMetrics(
            trackWidthPx = trackWidth,
            scrollPx = scrollState.value.toFloat(),
            maxScrollPx = scrollState.maxValue.toFloat(),
        )
        if (metrics.thumbWidth > 0f) {
            val thumbColor = if (metrics.maxScrollPx > 0f) {
                accentColor
            } else {
                if (isDark) Color(0x4494A3B8) else Color(0x5564748B)
            }
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(metrics.thumbX, barY),
                size = Size(metrics.thumbWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
        }
    }
}
