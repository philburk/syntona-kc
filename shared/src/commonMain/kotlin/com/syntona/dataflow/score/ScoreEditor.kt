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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import com.syntona.ui.RecordIcon
import com.syntona.ui.StopIcon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import syntonakc.shared.generated.resources.Res
import syntonakc.shared.generated.resources.leland
import kotlin.math.abs
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
        var location: ScoreItemLocation,
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
        val baseMeasures: Set<Int>,
    ) : ScoreDragState {
        var end by mutableStateOf(start)
    }
}

private data class TimeSigEditSession(
    val measureIdx: Int,
    val original: TimeSignature,
    val anchorX: Float,
    val anchorY: Float,
)

private enum class DurationEditPendingState {
    AWAITING_DURATION,
    AWAITING_DURATION_DOTTED,
    AWAITING_OPTIONAL_DOT,
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
    var entryIsDotted by remember { mutableStateOf(false) }
    var durationEditState by remember { mutableStateOf<DurationEditPendingState?>(null) }
    var durationEditLastDur by remember { mutableStateOf(NoteDuration.QUARTER) }
    var openPalette by remember { mutableStateOf<ScorePaletteKind?>(null) }
    var pendingAccidental by remember { mutableStateOf<Accidental?>(null) }
    var pendingStaccato by remember { mutableStateOf(false) }
    var selectedLocations by remember { mutableStateOf<Set<ScoreItemLocation>>(emptySet()) }
    var selectedMeasures by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedClefChange by remember { mutableStateOf<ScoreClefLocation?>(null) }
    var activePitchText by remember { mutableStateOf<String?>(null) }
    var activeChordName by remember { mutableStateOf<String?>(null) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    var hoveringItem by remember { mutableStateOf(false) }
    var hoveringTimeSig by remember { mutableStateOf(false) }
    var hoveringClef by remember { mutableStateOf(false) }
    var dragState by remember { mutableStateOf<ScoreDragState?>(null) }
    var editingTimeSig by remember { mutableStateOf<TimeSigEditSession?>(null) }
    var timeSigInputValue by remember { mutableStateOf(TextFieldValue("")) }
    var timeSigHadFocus by remember { mutableStateOf(false) }
    val timeSigFocusRequester = remember { FocusRequester() }

    val score = module.scoreData

    fun refreshPitchDisplayFromSelection(
        measures: List<ScoreMeasure> = score.measures,
        selection: Set<ScoreItemLocation> = selectedLocations,
    ) {
        val selectedPitches = selection.flatMap { loc ->
            when (val item = ScoreEditing.itemAt(measures, loc)) {
                is ScoreItem.Note -> listOf(item.pitch)
                is ScoreItem.Chord -> if (loc.chordNoteIdx != null) {
                    listOfNotNull(item.pitches.getOrNull(loc.chordNoteIdx))
                } else {
                    item.pitches
                }
                else -> emptyList()
            }
        }
        activePitchText = when {
            selectedPitches.size == 1 -> selectedPitches.first().pitchDisplay()
            selectedPitches.size > 1 -> "${selectedPitches.first().pitchDisplay()} (+${selectedPitches.size - 1})"
            else -> null
        }

        val singleSlotChord = selection
            .map { it.slotLocation() }
            .distinct()
            .singleOrNull()
            ?.let { slot ->
                val chord = ScoreEditing.itemAt(measures, slot) as? ScoreItem.Chord
                if (chord != null) slot to chord else null
            }
        activeChordName = singleSlotChord?.let { (slot, chord) ->
            val key = ScoreEditing.effectiveKeyAtMeasure(measures, score.keySignature, slot.measureIdx)
            ScoreChordAnalyzer.recognizeChordName(chord.pitches, key)
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

    fun targetClefLocation(): ScoreClefLocation = ScoreEditing.resolveTargetClefLocation(
        selectedClefChange = selectedClefChange,
        selectedLocations = selectedLocations,
        cursorPosition = module.cursorPosition,
        selectedMeasures = selectedMeasures,
    )

    fun applyClef(newClef: Clef?, target: ScoreClefLocation) {
        val beforeInitialClef = score.clef
        val beforeMeasures = score.measures.toList()
        val (afterInitialClef, afterMeasures) = ScoreEditing.setClefChange(
            initialClef = beforeInitialClef,
            measures = beforeMeasures,
            location = target,
            newClef = newClef,
        )
        if (afterInitialClef == beforeInitialClef && afterMeasures == beforeMeasures) return
        if (newClef == null) {
            selectedClefChange = null
        }
        postCommand(
            SetScoreClefCommand(
                scoreData = score,
                beforeInitialClef = beforeInitialClef,
                beforeMeasures = beforeMeasures,
                afterInitialClef = afterInitialClef,
                afterMeasures = afterMeasures,
            )
        )
    }

    fun deleteSelection() {
        durationEditState = null
        if (selectedLocations.isEmpty()) {
            selectedClefChange?.let { clefLoc ->
                applyClef(null, clefLoc)
            }
            return
        }
        val before = score.measures.toList()
        val prevSelection = selectedLocations
        val after = ScoreEditing.deleteItems(before, prevSelection)
        selectedLocations = emptySet()
        activePitchText = null
        activeChordName = null
        postCommand(
            ReplaceScoreMeasuresCommand(score, before, after) { restored ->
                selectedLocations = prevSelection.filterTo(linkedSetOf()) { loc ->
                    ScoreEditing.pitchAt(restored, loc) != null ||
                        (loc.chordNoteIdx == null && ScoreEditing.itemAt(restored, loc) != null)
                }
                refreshPitchDisplayFromSelection(restored, selectedLocations)
            }
        )
    }

    fun toggleDotMode() {
        val next = !entryIsDotted
        entryIsDotted = next
        module.stepRecordDotted = next
    }

    fun applyDurationToSelection(duration: NoteDuration, isDotted: Boolean) {
        if (selectedLocations.isEmpty()) return
        val before = score.measures.toList()
        val prevSelection = selectedLocations
        val after = ScoreEditing.setDurationOnSelection(before, prevSelection, duration, isDotted)
        if (after != before) {
            postCommand(
                ReplaceScoreMeasuresCommand(score, before, after) { restored ->
                    selectedLocations = prevSelection.filterTo(linkedSetOf()) { loc ->
                        ScoreEditing.pitchAt(restored, loc) != null ||
                            (loc.chordNoteIdx == null && ScoreEditing.itemAt(restored, loc) != null)
                    }
                    refreshPitchDisplayFromSelection(restored, selectedLocations)
                }
            )
        }
    }

    fun selectDuration(duration: NoteDuration) {
        if (module.isStepRecording) {
            entryDuration = duration
            entryIsRest = false
            module.stepRecordDuration = duration
            return
        }
        if (editorMode == ScoreEditMode.ENTRY && entryDuration == duration) {
            entryIsRest = !entryIsRest
        } else {
            editorMode = ScoreEditMode.ENTRY
            entryDuration = duration
            entryIsRest = false
            module.stepRecordDuration = duration
            module.placeCursor(null)
        }
    }

    fun applyAccidental(accidental: Accidental) {
        val before = score.measures.toList()
        val after = ScoreEditing.setAccidentalOnSelection(before, selectedLocations, accidental.alter)
        val hasSelectedNotes = selectedLocations.any { loc ->
            ScoreEditing.itemAt(before, loc)?.soundingPitches()?.isNotEmpty() == true
        }
        if (hasSelectedNotes) {
            if (after != before) {
                postCommand(ReplaceScoreMeasuresCommand(score, before, after))
            }
            val firstLoc = selectedLocations.firstOrNull()
            val firstPitch = firstLoc?.let { loc ->
                ScoreEditing.pitchAt(after, loc) ?: ScoreEditing.itemAt(after, loc)?.soundingPitches()?.firstOrNull()
            }
            val firstItem = firstLoc?.let { ScoreEditing.itemAt(after, it) }
            if (firstPitch != null && firstItem != null) {
                module.previewPitch(firstPitch.midiPitch.toDouble(), isStaccato = firstItem.noteStaccato())
                refreshPitchDisplayFromSelection(after, selectedLocations)
            }
        } else {
            pendingAccidental = if (pendingAccidental == accidental) null else accidental
        }
    }

    fun targetMeasuresForKeyChange(): Set<Int> {
        if (selectedMeasures.isNotEmpty()) return selectedMeasures
        if (selectedLocations.isNotEmpty()) return selectedLocations.map { it.measureIdx }.toSet()
        module.cursorPosition?.let { return setOf(it.measureIdx) }
        return if (score.measures.isNotEmpty()) setOf(0) else emptySet()
    }

    fun applyKeySignature(newKey: KeySignature, targetMeasures: Set<Int>) {
        if (targetMeasures.isEmpty()) return
        val beforeInitialKey = score.keySignature
        val beforeMeasures = score.measures.toList()
        val (afterInitialKey, afterMeasures) = ScoreEditing.setKeySignatureOnMeasures(
            initialKey = beforeInitialKey,
            measures = beforeMeasures,
            selectedMeasureIndices = targetMeasures,
            newKey = newKey,
        )
        if (afterInitialKey == beforeInitialKey && afterMeasures == beforeMeasures) return
        postCommand(
            SetScoreKeySignatureCommand(
                scoreData = score,
                beforeInitialKey = beforeInitialKey,
                beforeMeasures = beforeMeasures,
                afterInitialKey = afterInitialKey,
                afterMeasures = afterMeasures,
            )
        )
    }

    fun applyTimeSignature(newTime: TimeSignature?, targetMeasures: Set<Int>) {
        if (targetMeasures.isEmpty()) return
        val beforeInitialTime = score.timeSignature
        val beforeMeasures = score.measures.toList()
        val (afterInitialTime, afterMeasures) = ScoreEditing.setTimeSignatureOnMeasures(
            initialTime = beforeInitialTime,
            measures = beforeMeasures,
            selectedMeasureIndices = targetMeasures,
            newTime = newTime,
        )
        if (afterInitialTime == beforeInitialTime && afterMeasures == beforeMeasures) return
        postCommand(
            SetScoreTimeSignatureCommand(
                scoreData = score,
                beforeInitialTime = beforeInitialTime,
                beforeMeasures = beforeMeasures,
                afterInitialTime = afterInitialTime,
                afterMeasures = afterMeasures,
            )
        )
    }

    fun commitTimeSigEdit(returnFocusToCanvas: Boolean = true) {
        val session = editingTimeSig ?: return
        editingTimeSig = null
        timeSigHadFocus = false
        val trimmed = timeSigInputValue.text.trim()
        if (trimmed.isEmpty()) {
            if (session.measureIdx > 0) {
                applyTimeSignature(null, setOf(session.measureIdx))
            }
        } else {
            TimeSignature.parse(trimmed)?.let { parsed ->
                applyTimeSignature(parsed, setOf(session.measureIdx))
            }
        }
        if (returnFocusToCanvas) {
            focusRequester.requestFocus()
        }
    }

    fun cancelTimeSigEdit() {
        if (editingTimeSig == null) return
        editingTimeSig = null
        timeSigHadFocus = false
        focusRequester.requestFocus()
    }

    fun toggleStaccato() {
        val before = score.measures.toList()
        val hasSelectedNotes = selectedLocations.any { loc ->
            ScoreEditing.itemAt(before, loc)?.soundingPitches()?.isNotEmpty() == true
        }
        if (hasSelectedNotes) {
            val after = ScoreEditing.toggleStaccatoOnSelection(before, selectedLocations)
            if (after != before) {
                postCommand(ReplaceScoreMeasuresCommand(score, before, after))
            }
            val firstLoc = selectedLocations.firstOrNull()
            val firstPitch = firstLoc?.let { loc ->
                ScoreEditing.pitchAt(after, loc) ?: ScoreEditing.itemAt(after, loc)?.soundingPitches()?.firstOrNull()
            }
            val firstItem = firstLoc?.let { ScoreEditing.itemAt(after, it) }
            if (firstPitch != null && firstItem != null) {
                module.previewPitch(firstPitch.midiPitch.toDouble(), isStaccato = firstItem.noteStaccato())
            }
        } else {
            pendingStaccato = !pendingStaccato
            module.stepRecordStaccato = pendingStaccato
        }
    }

    fun copySelection() {
        val sorted = selectedLocations
            .map { it.slotLocation() }
            .distinct()
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
        if (openPalette == null && editingTimeSig == null) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(editingTimeSig) {
        if (editingTimeSig != null) {
            timeSigFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(module.stepRecordRevision) {
        if (module.stepRecordRevision > 0) {
            state.markChanged()
        }
    }

    fun toggleStepRecording() {
        durationEditState = null
        val nextEnabled = !module.isStepRecording
        module.stepRecordDuration = entryDuration
        module.stepRecordStaccato = pendingStaccato
        module.stepRecordDotted = entryIsDotted
        module.setStepRecordingEnabled(nextEnabled)
        if (nextEnabled) {
            editorMode = ScoreEditMode.SELECT
            selectedLocations = emptySet()
            activePitchText = null
            activeChordName = null
            hoverPos = null
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (editingTimeSig != null) return false
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isMetaPressed || event.isCtrlPressed) return false
        if (event.key == Key.Escape) {
            if (openPalette != null) {
                openPalette = null
                durationEditState = null
            } else if (
                durationEditState == DurationEditPendingState.AWAITING_DURATION ||
                durationEditState == DurationEditPendingState.AWAITING_DURATION_DOTTED
            ) {
                durationEditState = null
            } else if (module.isStepRecording) {
                durationEditState = null
                module.exitStepRecording()
                editorMode = ScoreEditMode.SELECT
                hoverPos = null
            } else {
                durationEditState = null
                editorMode = ScoreEditMode.SELECT
                hoverPos = null
            }
            return true
        }
        if (event.key == Key.Delete || event.key == Key.Backspace) {
            deleteSelection()
            return true
        }
        if (event.key == Key.Spacebar) {
            durationEditState = null
            module.requestPlayToggle()
            return true
        }

        val keyChar = event.utf16CodePoint.toChar().lowercaseChar()
        val charDuration = when (keyChar) {
            't' -> NoteDuration.THIRTY_SECOND
            's' -> NoteDuration.SIXTEENTH
            'e' -> NoteDuration.EIGHTH
            'q' -> NoteDuration.QUARTER
            'h' -> NoteDuration.HALF
            'w' -> NoteDuration.WHOLE
            else -> null
        }

        when (durationEditState) {
            DurationEditPendingState.AWAITING_DURATION -> {
                if (keyChar == 'd') {
                    durationEditState = DurationEditPendingState.AWAITING_DURATION_DOTTED
                    return true
                }
                if (charDuration != null) {
                    applyDurationToSelection(charDuration, isDotted = false)
                    durationEditLastDur = charDuration
                    durationEditState = DurationEditPendingState.AWAITING_OPTIONAL_DOT
                    return true
                }
                durationEditState = null
                if (keyChar == '=') return true
            }
            DurationEditPendingState.AWAITING_DURATION_DOTTED -> {
                if (charDuration != null) {
                    applyDurationToSelection(charDuration, isDotted = true)
                    durationEditState = null
                    return true
                }
                durationEditState = null
                if (keyChar == 'd') return true
            }
            DurationEditPendingState.AWAITING_OPTIONAL_DOT -> {
                if (keyChar == 'd') {
                    applyDurationToSelection(durationEditLastDur, isDotted = true)
                    durationEditState = null
                    return true
                }
                durationEditState = null
            }
            null -> {}
        }

        return when (keyChar) {
            '=' -> {
                if (selectedLocations.isNotEmpty()) {
                    durationEditState = DurationEditPendingState.AWAITING_DURATION
                }
                true
            }
            'd' -> { toggleDotMode(); true }
            't', 's', 'e', 'q', 'h', 'w' -> {
                selectDuration(charDuration!!)
                true
            }
            '1' -> { toggleStaccato(); true }
            '2' -> { applyAccidental(Accidental.FLAT); true }
            '3' -> { applyAccidental(Accidental.SHARP); true }
            '4' -> { applyAccidental(Accidental.NATURAL); true }
            else -> false
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
                selected = editorMode == ScoreEditMode.SELECT && !module.isStepRecording,
                accentColor = accentColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = {
                    module.exitStepRecording()
                    editorMode = ScoreEditMode.SELECT
                    hoverPos = null
                },
            ) { color ->
                Text("Select", fontSize = 11.sp, color = color)
            }

            val stepColor = Color(0xFFEF4444)
            ToolbarToggleChip(
                selected = module.isStepRecording,
                accentColor = stepColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = { toggleStepRecording() },
            ) { color ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RecordIcon(
                        color = if (module.isStepRecording) stepColor else color,
                        size = 9.dp,
                    )
                    Text("Step", fontSize = 11.sp, color = if (module.isStepRecording) stepColor else color)
                }
            }

            Text("|", color = borderMuted)

            val selectedSlotItems = selectedLocations
                .mapNotNull { loc -> ScoreEditing.itemAt(score.measures, loc) }
                .filter { it.soundingPitches().isNotEmpty() }
            val staccatoActive = if (selectedSlotItems.isNotEmpty()) {
                selectedSlotItems.all { it.noteStaccato() }
            } else {
                pendingStaccato
            }
            val selectedPitchesForToolbar = selectedLocations.flatMap { loc ->
                when (val item = ScoreEditing.itemAt(score.measures, loc)) {
                    is ScoreItem.Note -> listOf(item.pitch)
                    is ScoreItem.Chord -> if (loc.chordNoteIdx != null) {
                        listOfNotNull(item.pitches.getOrNull(loc.chordNoteIdx))
                    } else {
                        item.pitches
                    }
                    else -> emptyList()
                }
            }
            val activeAlter = if (selectedPitchesForToolbar.isNotEmpty()) {
                selectedPitchesForToolbar.map { it.alter }.distinct().singleOrNull()
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
                onSelect = { applyKeySignature(it, setOf(0)) },
            )

            ScoreTimeSigSelector(
                selected = score.timeSignature,
                textColor = toolbarText,
                onSelect = { applyTimeSignature(it, setOf(0)) },
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
                    selectedMeasures = emptySet()
                    activePitchText = null
                    activeChordName = null
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
            val targetMeasureIndices = targetMeasuresForKeyChange()
            val firstTargetIdx = targetMeasureIndices.minOrNull() ?: 0
            val activeTargetKey = ScoreEditing.effectiveKeyAtMeasure(
                measures = score.measures,
                initialKey = score.keySignature,
                measureIdx = firstTargetIdx,
            )
            val activeTargetTime = ScoreEditing.effectiveTimeAtMeasure(
                measures = score.measures,
                initialTime = score.timeSignature,
                measureIdx = firstTargetIdx,
            )
            val clefTargetLoc = targetClefLocation()
            val activeTargetClef = ScoreEditing.effectiveClefAt(
                measures = score.measures,
                initialClef = score.clef,
                measureIdx = clefTargetLoc.measureIdx,
                itemIdx = clefTargetLoc.itemIdx,
            )
            val canRemoveClefChange = (clefTargetLoc.measureIdx > 0 || clefTargetLoc.itemIdx > 0) &&
                ScoreEditing.hasExplicitClefChangeAt(score.measures, clefTargetLoc)
            ScorePalettePanel(
                openPalette = openPalette,
                editorMode = editorMode,
                isStepRecording = module.isStepRecording,
                entryDuration = entryDuration,
                entryIsRest = entryIsRest,
                entryIsDotted = entryIsDotted,
                activeKeySignature = activeTargetKey,
                activeTimeSignature = activeTargetTime,
                activeClef = activeTargetClef,
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
                val gapPx = with(density) { 4.dp.roundToPx() }
                val popupOffsetX = (buttonWidthPx + gapPx).coerceAtLeast(1)
                if (kind == openPalette && kind == ScorePaletteKind.NOTES_RESTS) {
                    NotesRestsPalettePopup(
                        offsetX = popupOffsetX,
                        editorMode = editorMode,
                        isStepRecording = module.isStepRecording,
                        entryDuration = entryDuration,
                        entryIsRest = entryIsRest,
                        entryIsDotted = entryIsDotted,
                        isDark = isDark,
                        toolbarText = toolbarText,
                        accentColor = accentColor,
                        borderMuted = borderMuted,
                        musicFontFamily = musicFontFamily,
                        textMeasurer = textMeasurer,
                        onSelectOption = { duration, isRest ->
                            entryDuration = duration
                            module.stepRecordDuration = duration
                            if (module.isStepRecording && !isRest) {
                                entryIsRest = false
                            } else {
                                module.exitStepRecording()
                                module.placeCursor(null)
                                editorMode = ScoreEditMode.ENTRY
                                entryIsRest = isRest
                            }
                            openPalette = null
                            focusRequester.requestFocus()
                        },
                        onToggleDot = {
                            toggleDotMode()
                            focusRequester.requestFocus()
                        },
                        onDismiss = { openPalette = null },
                        onKeyEvent = ::handleKeyEvent,
                    )
                } else if (kind == openPalette && kind == ScorePaletteKind.KEY_SIGNATURES) {
                    KeySignaturesPalettePopup(
                        offsetX = popupOffsetX,
                        targetMeasures = targetMeasureIndices,
                        activeKey = activeTargetKey,
                        activeTime = activeTargetTime,
                        isDark = isDark,
                        toolbarText = toolbarText,
                        accentColor = accentColor,
                        borderMuted = borderMuted,
                        musicFontFamily = musicFontFamily,
                        onSelectKey = { chosenKey ->
                            applyKeySignature(chosenKey, targetMeasureIndices)
                            openPalette = null
                            focusRequester.requestFocus()
                        },
                        onSelectTimeSignature = { chosenTime ->
                            applyTimeSignature(chosenTime, targetMeasureIndices)
                            openPalette = null
                            focusRequester.requestFocus()
                        },
                        onDismiss = { openPalette = null },
                        onKeyEvent = ::handleKeyEvent,
                    )
                } else if (kind == openPalette && kind == ScorePaletteKind.CLEFS) {
                    ClefsPalettePopup(
                        offsetX = popupOffsetX,
                        targetLocation = clefTargetLoc,
                        activeClef = activeTargetClef,
                        canRemove = canRemoveClefChange,
                        isDark = isDark,
                        toolbarText = toolbarText,
                        accentColor = accentColor,
                        borderMuted = borderMuted,
                        musicFontFamily = musicFontFamily,
                        onSelectClef = { chosenClef ->
                            applyClef(chosenClef, clefTargetLoc)
                            openPalette = null
                            focusRequester.requestFocus()
                        },
                        onRemoveClef = {
                            applyClef(null, clefTargetLoc)
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

                    LaunchedEffect(module.cursorPosition, module.isStepRecording) {
                        val cursor = module.cursorPosition ?: return@LaunchedEffect
                        if (!module.isStepRecording) return@LaunchedEffect
                        val cursorX = currentLayout.cursorXFor(cursor) ?: return@LaunchedEffect
                        val vpW = with(density) { viewportWidth.toPx() }
                        val curScroll = scrollState.value.toFloat()
                        if (cursorX > curScroll + vpW - 36f || cursorX < curScroll + 24f) {
                            val target = (cursorX - vpW * 0.7f).roundToInt()
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
                hoveringTimeSig || (editorMode == ScoreEditMode.SELECT && !module.isStepRecording && hoveringClef) -> PointerIcon.Hand
                !module.isStepRecording && editorMode == ScoreEditMode.ENTRY ->
                    scoreSymbolCursor(entryDuration, entryIsRest, entryIsDotted, isDark)
                hoveringItem -> moveCursor()
                else -> PointerIcon.Default
            }

            val ghostPreview = remember(
                editorMode,
                module.isStepRecording,
                hoverPos,
                dragState,
                layout,
                entryDuration,
                entryIsRest,
                entryIsDotted,
                pendingAccidental,
                pendingStaccato,
                editingTimeSig,
            ) {
                if (module.isStepRecording || editorMode != ScoreEditMode.ENTRY || dragState != null || editingTimeSig != null) {
                    return@remember null
                }
                val pos = hoverPos ?: return@remember null
                if (pos.x < layout.headerEndX - layout.staffSpacePx * 0.5f) return@remember null
                if (layout.timeSignatureAt(pos.x, pos.y) != null) return@remember null
                if (layout.clefChangeAt(pos.x, pos.y) != null) return@remember null
                val step = layout.stepAtY(pos.y)
                val snappedX = if (!entryIsRest) {
                    layout.itemColumnAt(pos.x)?.x ?: (pos.x - layout.staffSpacePx * 0.65f)
                } else {
                    pos.x - layout.staffSpacePx * 0.65f
                }
                ScoreGhostPreview(
                    x = snappedX,
                    staffStep = step,
                    duration = entryDuration,
                    isRest = entryIsRest,
                    isDotted = entryIsDotted,
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
            val displayedSelectedMeasures = if (rubberBandState != null && rubberBand != null) {
                val isTiny = abs(rubberBandState.end.x - rubberBandState.start.x) < 4f &&
                    abs(rubberBandState.end.y - rubberBandState.start.y) < 4f
                if (isTiny) {
                    selectedMeasures
                } else {
                    val liveHits = layout.itemsInRect(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.bottom)
                    val liveMeasures = layout.measuresInRect(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.bottom)
                    if (liveMeasures.size > 1 || liveHits.isEmpty()) {
                        if (rubberBandState.additive) rubberBandState.baseMeasures + liveMeasures else liveMeasures
                    } else {
                        emptySet()
                    }
                }
            } else {
                selectedMeasures
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
                                            hoveringTimeSig = pos != null && currentLayout.timeSignatureAt(pos.x, pos.y) != null
                                            hoveringClef = pos != null && currentLayout.clefChangeAt(pos.x, pos.y) != null
                                            hoveringItem = pos != null && currentLayout.itemAt(pos.x, pos.y) != null
                                        }
                                        PointerEventType.Exit -> {
                                            hoverPos = null
                                            hoveringTimeSig = false
                                            hoveringClef = false
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
                                down.consume()
                                if (editingTimeSig != null) {
                                    commitTimeSigEdit(returnFocusToCanvas = true)
                                    return@awaitEachGesture
                                }
                                focusRequester.requestFocus()
                                durationEditState = null

                                val downPos = down.position
                                val isShift = currentEvent.keyboardModifiers.isShiftPressed
                                val lyt = currentLayout
                                val sp = currentStaffSpacePx
                                val hitTimeSig = lyt.timeSignatureAt(downPos.x, downPos.y)
                                if (hitTimeSig != null && !isShift) {
                                    selectedClefChange = null
                                    val label = hitTimeSig.timeSignature.label
                                    editingTimeSig = TimeSigEditSession(
                                        measureIdx = hitTimeSig.measureIdx,
                                        original = hitTimeSig.timeSignature,
                                        anchorX = hitTimeSig.x,
                                        anchorY = hitTimeSig.topY,
                                    )
                                    timeSigInputValue = TextFieldValue(
                                        text = label,
                                        selection = TextRange(0, label.length),
                                    )
                                    timeSigHadFocus = false
                                    return@awaitEachGesture
                                }
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

                                if (module.isStepRecording || editorMode == ScoreEditMode.SELECT) {
                                    val hitClef = if (hit == null && !module.isStepRecording) {
                                        lyt.clefChangeAt(downPos.x, downPos.y)
                                    } else {
                                        null
                                    }
                                    if (hitClef != null) {
                                        module.placeCursor(null)
                                        selectedClefChange = hitClef.location
                                        selectedLocations = emptySet()
                                        selectedMeasures = emptySet()
                                        activePitchText = null
                                        activeChordName = null
                                        return@awaitEachGesture
                                    }
                                    selectedClefChange = null
                                    if (hit != null && !module.isStepRecording) {
                                        module.placeCursor(null)
                                        val chordNote = if (hit.item is ScoreItem.Chord) {
                                            lyt.chordNoteAt(hit, downPos.x, downPos.y)
                                        } else {
                                            null
                                        }
                                        val clickedLoc = if (hit.item is ScoreItem.Chord && chordNote != null) {
                                            hit.location.copy(chordNoteIdx = chordNote.noteIdx)
                                        } else {
                                            hit.location
                                        }
                                        if (isShift) {
                                            selectedLocations = if (clickedLoc in selectedLocations) {
                                                selectedLocations - clickedLoc
                                            } else {
                                                selectedLocations + clickedLoc
                                            }
                                            refreshPitchDisplayFromSelection()
                                        } else {
                                            selectedLocations = setOf(clickedLoc)
                                            selectedMeasures = emptySet()
                                            when (val item = hit.item) {
                                                is ScoreItem.Note -> {
                                                    module.previewPitch(item.pitch.midiPitch.toDouble(), isStaccato = item.isStaccato)
                                                    refreshPitchDisplayFromSelection()
                                                    val step = hit.staffStep ?: hit.effectiveClef.staffStepFromBottom(item.pitch)
                                                    beginNoteDrag(clickedLoc, step, item.pitch.alter, score.measures.toList(), hit.x)
                                                }
                                                is ScoreItem.Chord -> {
                                                    if (chordNote != null) {
                                                        module.previewPitch(chordNote.pitch.midiPitch.toDouble(), isStaccato = item.isStaccato)
                                                        refreshPitchDisplayFromSelection()
                                                        beginNoteDrag(
                                                            clickedLoc,
                                                            chordNote.staffStep,
                                                            chordNote.pitch.alter,
                                                            score.measures.toList(),
                                                            chordNote.headX,
                                                        )
                                                    } else {
                                                        item.pitches.firstOrNull()?.let {
                                                            module.previewPitch(it.midiPitch.toDouble(), isStaccato = item.isStaccato)
                                                        }
                                                        refreshPitchDisplayFromSelection()
                                                    }
                                                }
                                                is ScoreItem.Rest -> {
                                                    activePitchText = null
                                                    activeChordName = null
                                                }
                                            }
                                        }
                                    } else if (module.isStepRecording && hit == null) {
                                        selectedLocations = emptySet()
                                        selectedMeasures = emptySet()
                                        activePitchText = null
                                        activeChordName = null
                                        module.placeCursor(lyt.cursorPositionAt(downPos.x))
                                    } else if (module.isStepRecording && hit != null) {
                                        selectedLocations = emptySet()
                                        selectedMeasures = emptySet()
                                        activePitchText = null
                                        activeChordName = null
                                        module.placeCursor(lyt.cursorPositionAt(downPos.x))
                                    } else {
                                        if (!isShift) {
                                            selectedLocations = emptySet()
                                            selectedMeasures = emptySet()
                                            activePitchText = null
                                            activeChordName = null
                                        }
                                        dragState = ScoreDragState.RubberBand(
                                            start = downPos,
                                            additive = isShift,
                                            baseSelection = selectedLocations,
                                            baseMeasures = if (selectedMeasures.isNotEmpty()) {
                                                selectedMeasures
                                            } else {
                                                selectedLocations.map { it.measureIdx }.toSet()
                                            },
                                        )
                                    }
                                } else {
                                    // ENTRY mode
                                    selectedClefChange = null
                                    val beforeMeasures = score.measures.toList()
                                    val colItem = if (!entryIsRest) lyt.itemColumnAt(downPos.x) else null
                                    if (hit != null && (entryIsRest || hit.item is ScoreItem.Rest)) {
                                        val isDifferentKindOrDuration =
                                            (entryIsRest && hit.item !is ScoreItem.Rest) ||
                                                (!entryIsRest && hit.item is ScoreItem.Rest) ||
                                                (hit.item.duration != entryDuration) ||
                                                (hit.item.isDotted != entryIsDotted)

                                        if (isDifferentKindOrDuration) {
                                            if (entryIsRest) {
                                                val newRest = ScoreItem.Rest(entryDuration, isDotted = entryIsDotted)
                                                val after = ScoreEditing.replaceItem(beforeMeasures, hit.location, newRest)
                                                selectedLocations = setOf(hit.location)
                                                activePitchText = null
                                                activeChordName = null
                                                postCommand(ReplaceScoreMeasuresCommand(score, beforeMeasures, after))
                                            } else {
                                                val step = lyt.stepAtY(downPos.y)
                                                val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                    beforeMeasures,
                                                    score.keySignature,
                                                    hit.location.measureIdx,
                                                )
                                                val measureClef = ScoreEditing.effectiveClefAt(
                                                    beforeMeasures,
                                                    score.clef,
                                                    hit.location.measureIdx,
                                                    hit.location.itemIdx,
                                                )
                                                val pitch = measureClef.pitchForStaffStep(
                                                    stepFromBottom = step,
                                                    keySignature = measureKey,
                                                    alterOverride = pendingAccidental?.alter,
                                                )
                                                val newNote = ScoreItem.Note(
                                                    pitch = pitch,
                                                    duration = entryDuration,
                                                    isDotted = entryIsDotted,
                                                    isStaccato = pendingStaccato,
                                                )
                                                pendingAccidental = null
                                                val after = ScoreEditing.replaceItem(beforeMeasures, hit.location, newNote)
                                                score.replaceMeasures(after)
                                                selectedLocations = setOf(hit.location)
                                                refreshPitchDisplayFromSelection(after, selectedLocations)
                                                module.previewPitch(pitch.midiPitch.toDouble(), isStaccato = newNote.isStaccato)
                                                beginNoteDrag(hit.location, step, pitch.alter, beforeMeasures, hit.x)
                                            }
                                        } else {
                                            selectedLocations = setOf(hit.location)
                                            selectedMeasures = emptySet()
                                            activePitchText = null
                                            activeChordName = null
                                        }
                                    } else if (colItem != null) {
                                        // Stack onto an existing note/chord column, or select/replace an occupied step
                                        val step = lyt.stepAtY(downPos.y)
                                        val existingCn = colItem.chordNotes.find { it.staffStep == step }
                                        if (existingCn != null) {
                                            if (colItem.item is ScoreItem.Note &&
                                                (colItem.item.duration != entryDuration || colItem.item.isDotted != entryIsDotted)
                                            ) {
                                                val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                    beforeMeasures,
                                                    score.keySignature,
                                                    colItem.location.measureIdx,
                                                )
                                                val pitch = colItem.effectiveClef.pitchForStaffStep(
                                                    stepFromBottom = step,
                                                    keySignature = measureKey,
                                                    alterOverride = pendingAccidental?.alter,
                                                )
                                                val newNote = ScoreItem.Note(
                                                    pitch = pitch,
                                                    duration = entryDuration,
                                                    isDotted = entryIsDotted,
                                                    isStaccato = pendingStaccato,
                                                )
                                                pendingAccidental = null
                                                val after = ScoreEditing.replaceItem(beforeMeasures, colItem.location, newNote)
                                                score.replaceMeasures(after)
                                                selectedLocations = setOf(colItem.location)
                                                refreshPitchDisplayFromSelection(after, selectedLocations)
                                                module.previewPitch(pitch.midiPitch.toDouble(), isStaccato = newNote.isStaccato)
                                                beginNoteDrag(colItem.location, step, pitch.alter, beforeMeasures, colItem.x)
                                            } else {
                                                val loc = if (colItem.item is ScoreItem.Chord) {
                                                    colItem.location.copy(chordNoteIdx = existingCn.noteIdx)
                                                } else {
                                                    colItem.location
                                                }
                                                selectedLocations = setOf(loc)
                                                selectedMeasures = emptySet()
                                                refreshPitchDisplayFromSelection()
                                                module.previewPitch(
                                                    existingCn.pitch.midiPitch.toDouble(),
                                                    isStaccato = colItem.item.noteStaccato(),
                                                )
                                                beginNoteDrag(loc, existingCn.staffStep, existingCn.pitch.alter, beforeMeasures, existingCn.headX)
                                            }
                                        } else {
                                            val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                beforeMeasures,
                                                score.keySignature,
                                                colItem.location.measureIdx,
                                            )
                                            val pitch = colItem.effectiveClef.pitchForStaffStep(
                                                stepFromBottom = step,
                                                keySignature = measureKey,
                                                alterOverride = pendingAccidental?.alter,
                                            )
                                            pendingAccidental = null
                                            val (after, newLoc) = ScoreEditing.addPitchToSlot(beforeMeasures, colItem.location, pitch)
                                            score.replaceMeasures(after)
                                            selectedLocations = setOf(newLoc)
                                            selectedMeasures = emptySet()
                                            refreshPitchDisplayFromSelection(after, selectedLocations)
                                            module.previewPitch(
                                                pitch.midiPitch.toDouble(),
                                                isStaccato = colItem.item.noteStaccato(),
                                            )
                                            beginNoteDrag(newLoc, step, pitch.alter, beforeMeasures, colItem.x)
                                        }
                                    } else {
                                        val target = lyt.insertionTargetAt(downPos.x)
                                        if (target != null) {
                                            if (entryIsRest) {
                                                val newRest = ScoreItem.Rest(entryDuration, isDotted = entryIsDotted)
                                                val (after, newLoc) = ScoreEditing.insertItem(
                                                    measures = beforeMeasures,
                                                    measureIdx = target.measureIdx,
                                                    itemIdx = target.itemIdx,
                                                    item = newRest,
                                                    appendNewMeasure = target.appendNewMeasure,
                                                    shiftClefAtIndex = !target.afterClefChange,
                                                )
                                                selectedLocations = setOf(newLoc)
                                                activePitchText = null
                                                activeChordName = null
                                                postCommand(ReplaceScoreMeasuresCommand(score, beforeMeasures, after))
                                            } else {
                                                val step = lyt.stepAtY(downPos.y)
                                                val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                    beforeMeasures,
                                                    score.keySignature,
                                                    target.measureIdx,
                                                )
                                                val effectiveClef = lyt.effectiveClefAtX(downPos.x)
                                                val pitch = effectiveClef.pitchForStaffStep(
                                                    stepFromBottom = step,
                                                    keySignature = measureKey,
                                                    alterOverride = pendingAccidental?.alter,
                                                )
                                                val newNote = ScoreItem.Note(
                                                    pitch = pitch,
                                                    duration = entryDuration,
                                                    isDotted = entryIsDotted,
                                                    isStaccato = pendingStaccato,
                                                )
                                                pendingAccidental = null
                                                val (after, newLoc) = ScoreEditing.insertItem(
                                                    measures = beforeMeasures,
                                                    measureIdx = target.measureIdx,
                                                    itemIdx = target.itemIdx,
                                                    item = newNote,
                                                    appendNewMeasure = target.appendNewMeasure,
                                                    shiftClefAtIndex = !target.afterClefChange,
                                                )
                                                score.replaceMeasures(after)
                                                selectedLocations = setOf(newLoc)
                                                activePitchText = pitch.pitchDisplay()
                                                activeChordName = null
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
                                                val currentMeasures = score.measures.toList()
                                                val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                    currentMeasures,
                                                    score.keySignature,
                                                    drag.location.measureIdx,
                                                )
                                                val measureClef = ScoreEditing.effectiveClefAt(
                                                    currentMeasures,
                                                    score.clef,
                                                    drag.location.measureIdx,
                                                    drag.location.itemIdx,
                                                )
                                                val newPitch = measureClef.pitchForStaffStep(
                                                    stepFromBottom = newStep,
                                                    keySignature = measureKey,
                                                    alterOverride = alterOverride,
                                                )
                                                val (updated, trackedLoc) = ScoreEditing.setNotePitchAndTrackLocation(
                                                    currentMeasures,
                                                    drag.location,
                                                    newPitch,
                                                )
                                                drag.location = trackedLoc
                                                score.replaceMeasures(updated)
                                                selectedLocations = setOf(trackedLoc)
                                                drag.anchorY = lyt.yForStep(newStep)
                                                refreshPitchDisplayFromSelection(updated, selectedLocations)
                                                val updatedItem = ScoreEditing.itemAt(updated, trackedLoc)
                                                module.previewPitch(
                                                    newPitch.midiPitch.toDouble(),
                                                    isStaccato = updatedItem?.noteStaccato() == true,
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
                                                    selectedLocations = if (
                                                        ScoreEditing.pitchAt(restored, loc) != null ||
                                                        (loc.chordNoteIdx == null && ScoreEditing.itemAt(restored, loc) != null)
                                                    ) {
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
                                        val isClick = abs(drag.end.x - drag.start.x) < 4f &&
                                            abs(drag.end.y - drag.start.y) < 4f
                                        if (isClick) {
                                            val clickedMeasure = lyt.measureAt(drag.start.x)
                                            if (!drag.additive) {
                                                selectedLocations = emptySet()
                                                activePitchText = null
                                                activeChordName = null
                                                selectedMeasures = clickedMeasure?.let(::setOf) ?: emptySet()
                                                module.placeCursor(lyt.cursorPositionAt(drag.start.x))
                                            } else if (clickedMeasure != null) {
                                                val nextMeasures = if (clickedMeasure in drag.baseMeasures) {
                                                    drag.baseMeasures - clickedMeasure
                                                } else {
                                                    drag.baseMeasures + clickedMeasure
                                                }
                                                selectedMeasures = nextMeasures
                                                selectedLocations = emptySet()
                                                activePitchText = null
                                                activeChordName = null
                                                if (nextMeasures.isNotEmpty()) {
                                                    module.placeCursor(null)
                                                }
                                            }
                                        } else {
                                            val left = min(drag.start.x, drag.end.x)
                                            val top = min(drag.start.y, drag.end.y)
                                            val right = max(drag.start.x, drag.end.x)
                                            val bottom = max(drag.start.y, drag.end.y)
                                            val hits = lyt.itemsInRect(left, top, right, bottom)
                                            val mHits = lyt.measuresInRect(left, top, right, bottom)
                                            selectedLocations = if (drag.additive) drag.baseSelection + hits else hits
                                            selectedMeasures = if (mHits.size > 1 || hits.isEmpty()) {
                                                if (drag.additive) drag.baseMeasures + mHits else mHits
                                            } else {
                                                emptySet()
                                            }
                                            if (selectedLocations.isNotEmpty() || selectedMeasures.isNotEmpty()) {
                                                module.placeCursor(null)
                                            }
                                            refreshPitchDisplayFromSelection()
                                        }
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
                        selectedMeasureIndices = displayedSelectedMeasures,
                        selectedClefChange = selectedClefChange,
                        ghostPreview = ghostPreview,
                        rubberBand = rubberBand,
                        cursorPosition = module.cursorPosition,
                        isStepRecording = module.isStepRecording,
                    )
                }

                // Floating pitch badge right next to the note being placed/dragged
                val pitchDrag = dragState as? ScoreDragState.PitchDrag
                val pitchLabel = activePitchText
                if (pitchDrag != null && pitchLabel != null) {
                    val badgeLabel = activeChordName?.let { "$pitchLabel ($it)" } ?: pitchLabel
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
                            text = badgeLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Inline text box when clicking a time signature
                val activeTimeEdit = editingTimeSig
                if (activeTimeEdit != null) {
                    val boxX = (activeTimeEdit.anchorX - 6f).roundToInt().coerceAtLeast(0)
                    val boxY = (activeTimeEdit.anchorY + staffSpacePx * 0.7f).roundToInt().coerceAtLeast(0)
                    val editBg = if (isDark) Color(0xFF0F172A) else Color.White
                    val editTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(boxX, boxY) }
                            .width(64.dp)
                            .background(editBg, RoundedCornerShape(4.dp))
                            .border(1.5.dp, accentColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.Enter, Key.NumPadEnter -> {
                                        commitTimeSigEdit(returnFocusToCanvas = true)
                                        true
                                    }
                                    Key.Escape -> {
                                        cancelTimeSigEdit()
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        BasicTextField(
                            value = timeSigInputValue,
                            onValueChange = { next ->
                                if (next.text.all { it.isDigit() || it == '/' || it.isWhitespace() }) {
                                    timeSigInputValue = next
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = editTextColor,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(accentColor),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { commitTimeSigEdit(returnFocusToCanvas = true) },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(timeSigFocusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        timeSigHadFocus = true
                                    } else if (timeSigHadFocus) {
                                        commitTimeSigEdit(returnFocusToCanvas = false)
                                    }
                                },
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
            val dotPrefix = if (entryIsDotted) "Dotted " else ""
            val modeSummary = when {
                durationEditState == DurationEditPendingState.AWAITING_DURATION ->
                    "Set Duration: press t/s/e/q/h/w (or d for dotted), Esc to cancel"
                durationEditState == DurationEditPendingState.AWAITING_DURATION_DOTTED ->
                    "Set Dotted Duration: press t/s/e/q/h/w, Esc to cancel"
                durationEditState == DurationEditPendingState.AWAITING_OPTIONAL_DOT ->
                    "Duration: ${durationEditLastDur.displayLabel} (press d for dot)"
                module.isStepRecording -> "Mode: Step Record ($dotPrefix${entryDuration.displayLabel})"
                editorMode == ScoreEditMode.SELECT ->
                    if (entryIsDotted) "Mode: Select (Dot On)" else "Mode: Select"
                else -> "Mode: $dotPrefix${entryDuration.displayLabel} ${if (entryIsRest) "Rest" else "Note"}"
            }
            Text(
                modeSummary,
                fontSize = 11.sp,
                color = when {
                    durationEditState != null -> accentColor
                    module.isStepRecording -> Color(0xFFEF4444)
                    else -> toolbarText
                },
                fontWeight = FontWeight.Medium,
            )

            val displayedPitchText = activePitchText ?: if (module.isStepRecording) {
                module.lastStepRecordedText
            } else {
                null
            }
            if (displayedPitchText != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = displayedPitchText,
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
            val chordLabel = activeChordName
            if (chordLabel != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF10B981).copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Chord: $chordLabel",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Esc: Select | t s e q h w: Dur (2x=Rest) | d: Dot | =: Set Dur | 1: Stacc  2: b  3: #  4: Nat | Del: Delete",
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
    isStepRecording: Boolean,
    entryDuration: NoteDuration,
    entryIsRest: Boolean,
    entryIsDotted: Boolean,
    activeKeySignature: KeySignature,
    activeTimeSignature: TimeSignature,
    activeClef: Clef,
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
                    ScorePaletteKind.NOTES_RESTS ->
                        (editorMode == ScoreEditMode.ENTRY && !isStepRecording) || isStepRecording
                    ScorePaletteKind.KEY_SIGNATURES, ScorePaletteKind.CLEFS -> false
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
                                val showCurrentDuration = editorMode == ScoreEditMode.ENTRY || isStepRecording
                                val previewDur = if (showCurrentDuration) {
                                    entryDuration
                                } else {
                                    NoteDuration.QUARTER
                                }
                                val previewIsRest = editorMode == ScoreEditMode.ENTRY &&
                                    !isStepRecording &&
                                    entryIsRest
                                val previewGlyph = if (previewIsRest) {
                                    previewDur.restGlyph
                                } else {
                                    previewDur.noteSymbolGlyph
                                }
                                ScorePaletteGlyph(
                                    glyph = previewGlyph,
                                    isRest = previewIsRest,
                                    duration = previewDur,
                                    isDotted = if (showCurrentDuration) entryIsDotted else false,
                                    color = contentColor,
                                    staffLineColor = borderMuted,
                                    musicFontFamily = musicFontFamily,
                                    textMeasurer = textMeasurer,
                                    width = 36.dp,
                                    height = 24.dp,
                                    glyphSizeSp = 20f,
                                )
                            }
                            ScorePaletteKind.KEY_SIGNATURES -> {
                                KeySignatureMiniStaff(
                                    keySignature = activeKeySignature,
                                    clef = activeClef,
                                    timeSignature = activeTimeSignature,
                                    inkColor = contentColor,
                                    staffLineColor = borderMuted,
                                    musicFontFamily = musicFontFamily,
                                    textMeasurer = textMeasurer,
                                    width = 48.dp,
                                    height = 22.dp,
                                    staffSpaceDp = 3.2f,
                                )
                            }
                            ScorePaletteKind.CLEFS -> {
                                ClefMiniStaff(
                                    clef = activeClef,
                                    inkColor = contentColor,
                                    staffLineColor = borderMuted,
                                    musicFontFamily = musicFontFamily,
                                    textMeasurer = textMeasurer,
                                    width = 48.dp,
                                    height = 24.dp,
                                    staffSpaceDp = 3.2f,
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
    isStepRecording: Boolean,
    entryDuration: NoteDuration,
    entryIsRest: Boolean,
    entryIsDotted: Boolean,
    isDark: Boolean,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    onSelectOption: (NoteDuration, Boolean) -> Unit,
    onToggleDot: () -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = ScorePaletteKind.NOTES_RESTS.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                    )
                    val dotContentColor = if (entryIsDotted) {
                        if (isDark) Color.White else accentColor
                    } else {
                        toolbarText
                    }
                    val dotBg = if (entryIsDotted) accentColor.copy(alpha = 0.22f) else tileBg
                    Row(
                        modifier = Modifier
                            .background(dotBg, RoundedCornerShape(5.dp))
                            .border(
                                width = if (entryIsDotted) 1.5.dp else 1.dp,
                                color = if (entryIsDotted) accentColor else borderMuted,
                                shape = RoundedCornerShape(5.dp),
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onToggleDot() }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = SmuflGlyphs.AUGMENTATION_DOT,
                            fontFamily = musicFontFamily,
                            fontSize = 14.sp,
                            color = dotContentColor,
                        )
                        Text(
                            text = "Dot",
                            fontSize = 10.sp,
                            fontWeight = if (entryIsDotted) FontWeight.SemiBold else FontWeight.Medium,
                            color = dotContentColor,
                        )
                        Text(
                            text = "d",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (entryIsDotted) FontWeight.Bold else FontWeight.Medium,
                            color = dotContentColor.copy(alpha = if (entryIsDotted) 1f else 0.8f),
                        )
                    }
                }

                NotesRestsPaletteRow(
                    rowLabel = "Notes",
                    options = ScorePalettes.noteOptions,
                    editorMode = editorMode,
                    isStepRecording = isStepRecording,
                    entryDuration = entryDuration,
                    entryIsRest = entryIsRest,
                    entryIsDotted = entryIsDotted,
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
                    isStepRecording = isStepRecording,
                    entryDuration = entryDuration,
                    entryIsRest = entryIsRest,
                    entryIsDotted = entryIsDotted,
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
    isStepRecording: Boolean,
    entryDuration: NoteDuration,
    entryIsRest: Boolean,
    entryIsDotted: Boolean,
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
            val effectiveIsRest = !isStepRecording && entryIsRest
            val selected = (editorMode == ScoreEditMode.ENTRY || isStepRecording) &&
                entryDuration == option.duration &&
                effectiveIsRest == option.isRest
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
                    isDotted = entryIsDotted,
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
private fun KeySignaturesPalettePopup(
    offsetX: Int,
    targetMeasures: Set<Int>,
    activeKey: KeySignature,
    activeTime: TimeSignature,
    isDark: Boolean,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    onSelectKey: (KeySignature) -> Unit,
    onSelectTimeSignature: (TimeSignature) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    val popupBg = if (isDark) Color(0xFF161832) else Color(0xFFFFFFFF)
    val tileBg = if (isDark) Color(0xFF202446) else Color(0xFFF1F5F9)
    val titleColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val textMeasurer = rememberTextMeasurer()

    val sortedNums = targetMeasures.map { it + 1 }.sorted()
    val targetDescription = when {
        sortedNums.isEmpty() -> "No measure"
        sortedNums.size == 1 -> "Measure ${sortedNums.first()}"
        else -> "Measures ${sortedNums.joinToString(", ")}"
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = ScorePaletteKind.KEY_SIGNATURES.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                    )
                    Text(
                        text = targetDescription,
                        fontSize = 9.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }

                KeySignaturePaletteRow(
                    rowLabel = "Sharps",
                    options = ScorePalettes.sharpKeyOptions,
                    activeKey = activeKey,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    onSelectKey = onSelectKey,
                )

                KeySignaturePaletteRow(
                    rowLabel = "Flats",
                    options = ScorePalettes.flatKeyOptions,
                    activeKey = activeKey,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    onSelectKey = onSelectKey,
                )

                TimeSignaturePaletteRow(
                    rowLabel = "Time",
                    options = ScorePalettes.timeSignatureOptions,
                    activeTime = activeTime,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    onSelectTimeSignature = onSelectTimeSignature,
                )
            }
        }
    }
}

@Composable
private fun TimeSignaturePaletteRow(
    rowLabel: String,
    options: List<ScoreTimeSigOption>,
    activeTime: TimeSignature,
    isDark: Boolean,
    tileBg: Color,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    onSelectTimeSignature: (TimeSignature) -> Unit,
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
            modifier = Modifier.width(34.dp),
        )
        options.forEach { option ->
            val ts = option.timeSignature
            val selected = ts == activeTime
            val contentColor = if (selected) {
                if (isDark) Color.White else accentColor
            } else {
                toolbarText
            }
            val bg = if (selected) accentColor.copy(alpha = 0.22f) else tileBg
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .background(bg, RoundedCornerShape(5.dp))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accentColor else borderMuted,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelectTimeSignature(ts) }
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = ts.numerator.toString(),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                )
                Text(
                    text = ts.denominator.toString(),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun KeySignaturePaletteRow(
    rowLabel: String,
    options: List<ScoreKeyOption>,
    activeKey: KeySignature,
    isDark: Boolean,
    tileBg: Color,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    onSelectKey: (KeySignature) -> Unit,
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
            modifier = Modifier.width(34.dp),
        )
        options.forEach { option ->
            val selected = option.keySignature == activeKey
            val contentColor = if (selected) {
                if (isDark) Color.White else accentColor
            } else {
                toolbarText
            }
            val bg = if (selected) accentColor.copy(alpha = 0.22f) else tileBg
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .background(bg, RoundedCornerShape(5.dp))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accentColor else borderMuted,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelectKey(option.keySignature) }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                KeySignatureMiniStaff(
                    keySignature = option.keySignature,
                    inkColor = contentColor,
                    staffLineColor = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    width = 40.dp,
                    height = 22.dp,
                    staffSpaceDp = 3.2f,
                )
                Text(
                    text = "${option.shortName} (${option.countLabel})",
                    fontSize = 8.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ClefsPalettePopup(
    offsetX: Int,
    targetLocation: ScoreClefLocation,
    activeClef: Clef,
    canRemove: Boolean,
    isDark: Boolean,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    onSelectClef: (Clef) -> Unit,
    onRemoveClef: () -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    val popupBg = if (isDark) Color(0xFF161832) else Color(0xFFFFFFFF)
    val tileBg = if (isDark) Color(0xFF202446) else Color(0xFFF1F5F9)
    val titleColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val textMeasurer = rememberTextMeasurer()

    val targetDescription = when {
        targetLocation.measureIdx == 0 && targetLocation.itemIdx == 0 -> "Opening clef"
        targetLocation.itemIdx == 0 -> "Measure ${targetLocation.measureIdx + 1}, beat 1"
        else -> "Measure ${targetLocation.measureIdx + 1}, item ${targetLocation.itemIdx + 1}"
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = ScorePaletteKind.CLEFS.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                    )
                    Text(
                        text = targetDescription,
                        fontSize = 9.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                    )
                    if (canRemove) {
                        Text(
                            text = "Remove",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFDC2626),
                            modifier = Modifier
                                .background(
                                    Color(0xFFDC2626).copy(alpha = 0.12f),
                                    RoundedCornerShape(4.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFDC2626).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onRemoveClef() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                ClefPaletteRow(
                    rowLabel = "Primary",
                    options = ScorePalettes.primaryClefOptions,
                    activeClef = activeClef,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    onSelectClef = onSelectClef,
                )
                ClefPaletteRow(
                    rowLabel = "Octave",
                    options = ScorePalettes.octaveClefOptions,
                    activeClef = activeClef,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    onSelectClef = onSelectClef,
                )
            }
        }
    }
}

@Composable
private fun ClefPaletteRow(
    rowLabel: String,
    options: List<ScoreClefOption>,
    activeClef: Clef,
    isDark: Boolean,
    tileBg: Color,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    onSelectClef: (Clef) -> Unit,
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
            modifier = Modifier.width(42.dp),
        )
        options.forEach { option ->
            val selected = option.clef == activeClef
            val contentColor = if (selected) {
                if (isDark) Color.White else accentColor
            } else {
                toolbarText
            }
            val bg = if (selected) accentColor.copy(alpha = 0.22f) else tileBg
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .background(bg, RoundedCornerShape(5.dp))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accentColor else borderMuted,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelectClef(option.clef) }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                ClefMiniStaff(
                    clef = option.clef,
                    inkColor = contentColor,
                    staffLineColor = borderMuted,
                    musicFontFamily = musicFontFamily,
                    textMeasurer = textMeasurer,
                    width = 44.dp,
                    height = 24.dp,
                    staffSpaceDp = 3.2f,
                )
                Text(
                    text = option.label,
                    fontSize = 8.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ClefMiniStaff(
    clef: Clef,
    inkColor: Color,
    staffLineColor: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    width: Dp,
    height: Dp,
    staffSpaceDp: Float,
) {
    Canvas(modifier = Modifier.size(width = width, height = height)) {
        val sp = staffSpaceDp.dp.toPx()
        val emPx = sp * 4f
        val topStaffY = (size.height - 4f * sp) * 0.5f
        val bottomStaffY = topStaffY + 4f * sp
        val lineLeft = 2.dp.toPx()
        val lineRight = size.width - 2.dp.toPx()
        for (lineIdx in 0..4) {
            val y = bottomStaffY - lineIdx * sp
            drawLine(
                color = staffLineColor,
                start = Offset(lineLeft, y),
                end = Offset(lineRight, y),
                strokeWidth = 0.8f,
            )
        }
        val emSp = with(this) { emPx.toSp() }
        val style = TextStyle(fontFamily = musicFontFamily, fontSize = emSp, color = inkColor)
        val measured = textMeasurer.measure(
            text = clef.glyph,
            style = style,
            softWrap = false,
            maxLines = 1,
        )
        val glyphX = (size.width - measured.size.width) * 0.5f
        val refStep = clef.glyphLineFromBottom * 2
        val baselineY = bottomStaffY - refStep * (sp * 0.5f)
        drawText(
            textLayoutResult = measured,
            color = inkColor,
            topLeft = Offset(glyphX, baselineY - measured.firstBaseline),
        )
    }
}

@Composable
private fun KeySignatureMiniStaff(
    keySignature: KeySignature,
    inkColor: Color,
    staffLineColor: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    width: Dp,
    height: Dp,
    staffSpaceDp: Float,
    clef: Clef = Clef.TREBLE,
    timeSignature: TimeSignature? = null,
) {
    Canvas(modifier = Modifier.size(width = width, height = height)) {
        val sp = staffSpaceDp.dp.toPx()
        val emPx = sp * 4f
        val topStaffY = (size.height - 4f * sp) * 0.5f
        val bottomStaffY = topStaffY + 4f * sp
        val lineLeft = 2.dp.toPx()
        val lineRight = size.width - 2.dp.toPx()
        for (lineIdx in 0..4) {
            val y = bottomStaffY - lineIdx * sp
            drawLine(
                color = staffLineColor,
                start = Offset(lineLeft, y),
                end = Offset(lineRight, y),
                strokeWidth = 0.8f,
            )
        }
        val emSp = with(this) { emPx.toSp() }
        val style = TextStyle(fontFamily = musicFontFamily, fontSize = emSp, color = inkColor)
        val steps = keySignature.staffSteps(clef)

        val numMeasured = timeSignature?.let {
            textMeasurer.measure(
                text = SmuflGlyphs.timeSigNumber(it.numerator),
                style = style,
                softWrap = false,
                maxLines = 1,
            )
        }
        val denMeasured = timeSignature?.let {
            textMeasurer.measure(
                text = SmuflGlyphs.timeSigNumber(it.denominator),
                style = style,
                softWrap = false,
                maxLines = 1,
            )
        }
        val tsWidth = if (numMeasured != null && denMeasured != null) {
            max(numMeasured.size.width, denMeasured.size.width).toFloat()
        } else {
            0f
        }
        val tsGap = if (steps.isNotEmpty() && timeSignature != null) sp * 0.7f else 0f

        val accMeasured = if (steps.isNotEmpty()) {
            val glyph = if (keySignature.fifths >= 0) {
                SmuflGlyphs.ACCIDENTAL_SHARP
            } else {
                SmuflGlyphs.ACCIDENTAL_FLAT
            }
            textMeasurer.measure(text = glyph, style = style, softWrap = false, maxLines = 1)
        } else {
            null
        }
        val stepW = if (accMeasured != null) {
            val availForKey = (lineRight - lineLeft - tsWidth - tsGap - accMeasured.size.width).coerceAtLeast(0f)
            (availForKey / (steps.size - 1).coerceAtLeast(1)).coerceAtMost(sp * 1.1f)
        } else {
            0f
        }
        val totalKeyW = if (accMeasured != null) {
            (steps.size - 1) * stepW + accMeasured.size.width
        } else {
            0f
        }
        val combinedW = totalKeyW + tsGap + tsWidth
        val startX = ((size.width - combinedW) * 0.5f).coerceAtLeast(lineLeft)

        if (accMeasured != null) {
            steps.forEachIndexed { idx, step ->
                val gx = startX + idx * stepW
                val gy = bottomStaffY - step * (sp * 0.5f)
                drawText(
                    textLayoutResult = accMeasured,
                    color = inkColor,
                    topLeft = Offset(gx, gy - accMeasured.firstBaseline),
                )
            }
        }
        if (numMeasured != null && denMeasured != null) {
            val tsX = startX + totalKeyW + tsGap
            val numX = tsX + (tsWidth - numMeasured.size.width) * 0.5f
            val denX = tsX + (tsWidth - denMeasured.size.width) * 0.5f
            val numY = bottomStaffY - 6f * (sp * 0.5f)
            val denY = bottomStaffY - 2f * (sp * 0.5f)
            drawText(
                textLayoutResult = numMeasured,
                color = inkColor,
                topLeft = Offset(numX, numY - numMeasured.firstBaseline),
            )
            drawText(
                textLayoutResult = denMeasured,
                color = inkColor,
                topLeft = Offset(denX, denY - denMeasured.firstBaseline),
            )
        }
    }
}

@Composable
private fun ScorePaletteGlyph(
    glyph: String,
    isRest: Boolean,
    duration: NoteDuration,
    isDotted: Boolean = false,
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
        val dotMeasured = if (isDotted) {
            textMeasurer.measure(
                text = SmuflGlyphs.AUGMENTATION_DOT,
                style = TextStyle(fontFamily = musicFontFamily, fontSize = glyphSizeSp.sp, color = color),
                softWrap = false,
                maxLines = 1,
            )
        } else {
            null
        }
        val dotGap = if (dotMeasured != null) 1.5.dp.toPx() else 0f
        val totalWidth = measured.size.width + dotGap + (dotMeasured?.size?.width ?: 0)
        val x = ((size.width - totalWidth) * 0.5f).coerceAtLeast(0f)
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
        if (dotMeasured != null) {
            val dotBaselineY = when {
                isRest && duration == NoteDuration.WHOLE -> baselineY + glyphSizeSp.sp.toPx() * 0.10f
                isRest && duration == NoteDuration.HALF -> baselineY - glyphSizeSp.sp.toPx() * 0.12f
                else -> baselineY
            }
            val dotX = x + measured.size.width + dotGap
            drawText(
                textLayoutResult = dotMeasured,
                color = color,
                topLeft = Offset(dotX, dotBaselineY - dotMeasured.firstBaseline),
            )
        }
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
