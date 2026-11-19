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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed as isKeyShiftPressed
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.syntona.dataflow.editor.saveFileDirect
import com.syntona.dataflow.editor.scoreBeamCursor
import com.syntona.dataflow.editor.scoreDynamicCursor
import com.syntona.dataflow.editor.scoreSymbolCursor
import com.syntona.dataflow.editor.scoreTieCursor
import com.syntona.dataflow.editor.showExportSourceFileDialog
import com.syntona.dataflow.editor.showOpenTextFileDialog
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.ui.LoopIcon
import com.syntona.ui.PlayIcon
import com.syntona.ui.RecordIcon
import com.syntona.ui.RewindIcon
import com.syntona.ui.StopIcon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import syntonakc.shared.generated.resources.Res
import syntonakc.shared.generated.resources.leland
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ScoreEditMode {
    SELECT,
    ENTRY,
    DYNAMIC,
    BEAM,
    TIE,
}

internal sealed interface ScoreAltTupletAction {
    data object OpenCustomDialog : ScoreAltTupletAction
    data class SetRatio(val ratio: Pair<Int, Int>?) : ScoreAltTupletAction
}

/**
 * Resolves `Alt+N` / `Alt+0..9` tuplet shortcuts using physical [key] first so macOS Option-key
 * symbol translation (`Option+3` -> `'£'`, `Option+N` -> dead key) does not swallow shortcuts.
 */
internal fun resolveAltTupletShortcut(key: Key, utf16CodePoint: Int): ScoreAltTupletAction? {
    if (key == Key.N) return ScoreAltTupletAction.OpenCustomDialog

    val digitFromKey: Int? = when (key) {
        Key.Zero, Key.NumPad0 -> 0
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        else -> null
    }

    val keyChar = utf16CodePoint.toChar().lowercaseChar()
    if (digitFromKey == null && (keyChar == 'n' || keyChar == '\u02dc')) {
        return ScoreAltTupletAction.OpenCustomDialog
    }
    val resolvedDigit = digitFromKey ?: when (keyChar) {
        in '0'..'9' -> keyChar - '0'
        // Fallback for macOS US Option-key translated symbols when key enum isn't a digit
        'º' -> 0
        '¡' -> 1
        '™' -> 2
        '£' -> 3
        '¢' -> 4
        '∞' -> 5
        '§' -> 6
        '¶' -> 7
        '•' -> 8
        'ª' -> 9
        else -> null
    } ?: return null

    return when (resolvedDigit) {
        0, 1 -> ScoreAltTupletAction.SetRatio(null)
        in 2..9 -> {
            val ratio = when (resolvedDigit) {
                2 -> 2 to 3
                3 -> 3 to 2
                4 -> 4 to 3
                5 -> 5 to 4
                6 -> 6 to 4
                7 -> 7 to 4
                8 -> 8 to 6
                9 -> 9 to 8
                else -> resolvedDigit to 2
            }
            ScoreAltTupletAction.SetRatio(ratio)
        }
        else -> null
    }
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
        val baseDynamics: Set<ScoreDynamicLocation> = emptySet(),
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

private data class InstrumentDialogState(
    val editingIndex: Int?,
    val name: String,
    val instrumentType: InstrumentType,
    val lowPitchText: String,
    val highPitchText: String,
    val selectedPreset: InstrumentPreset? = null,
)

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
    val verticalScrollState = rememberScrollState()
    val toolbarScrollState = rememberScrollState()

    var editorMode by remember { mutableStateOf(ScoreEditMode.ENTRY) }
    var activeStaff by remember { mutableStateOf(ScoreStaffId(0, 0)) }
    var activeDynamic by remember { mutableStateOf(DynamicMark.MF) }
    var selectedDynamics by remember { mutableStateOf<Set<ScoreDynamicLocation>>(emptySet()) }
    var hoveringDynamic by remember { mutableStateOf(false) }
    var hoveringInstrument by remember { mutableStateOf(false) }
    var entryDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var entryIsRest by remember { mutableStateOf(false) }
    var entryIsDotted by remember { mutableStateOf(false) }
    var durationEditState by remember { mutableStateOf<DurationEditPendingState?>(null) }
    var durationEditLastDur by remember { mutableStateOf(NoteDuration.QUARTER) }
    var openPalette by remember { mutableStateOf<ScorePaletteKind?>(null) }
    var pendingAccidental by remember { mutableStateOf<Accidental?>(null) }
    var pendingStaccato by remember { mutableStateOf(false) }
    var customTupletDialogOpen by remember { mutableStateOf(false) }
    var customTupletActualText by remember { mutableStateOf("3") }
    var customTupletNormalText by remember { mutableStateOf("2") }
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
    var pendingScrollMeasures by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var editingTimeSig by remember { mutableStateOf<TimeSigEditSession?>(null) }
    var timeSigInputValue by remember { mutableStateOf(TextFieldValue("")) }
    var timeSigHadFocus by remember { mutableStateOf(false) }
    val timeSigFocusRequester = remember { FocusRequester() }
    var instrumentDialog by remember { mutableStateOf<InstrumentDialogState?>(null) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    val score = module.scoreData

    fun importMusicXmlContent(xmlContent: String) {
        val imported = try {
            ScoreMusicXml.importFromString(xmlContent)
        } catch (ex: Exception) {
            importErrorMessage = ex.message ?: "Unable to parse MusicXML file."
            return
        }
        if (module.isPlaying) {
            module.requestPlayToggle()
        }
        module.exitStepRecording()
        val beforeInst = score.instruments.toList()
        val beforeKey = score.keySignature
        val beforeTime = score.timeSignature
        val beforeTempo = module.tempo.getValue()
        val afterTempo = imported.tempoBpm

        selectedLocations = emptySet()
        selectedMeasures = emptySet()
        selectedClefChange = null
        selectedDynamics = emptySet()
        pendingAccidental = null
        activePitchText = null
        activeChordName = null
        activeStaff = ScoreStaffId(0, 0)
        module.placeCursor(null)

        val cmd = ReplaceScoreInstrumentsCommand(
            module = module,
            beforeInstruments = beforeInst,
            afterInstruments = imported.instruments,
            beforeKeySignature = beforeKey,
            afterKeySignature = imported.keySignature,
            beforeTimeSignature = beforeTime,
            afterTimeSignature = imported.timeSignature,
            beforeTempo = if (afterTempo != null) beforeTempo else null,
            afterTempo = afterTempo,
            onSync = {
                selectedLocations = emptySet()
                selectedMeasures = emptySet()
                selectedClefChange = null
                selectedDynamics = emptySet()
                module.placeCursor(null)
                activeStaff = ScoreStaffId(0, 0)
            },
        )
        val e = module.getEngine()
        if (e != null) {
            e.post(cmd)
        } else {
            cmd.execute()
        }
        coroutineScope.launch { scrollState.animateScrollTo(0) }
    }

    fun resolvedActiveStaff(): ScoreStaffId {
        val part = score.instruments.getOrNull(activeStaff.instrumentIdx)
        return when {
            part == null -> ScoreStaffId(0, 0)
            activeStaff.staffIdx >= part.staffCount -> ScoreStaffId(activeStaff.instrumentIdx, 0)
            else -> activeStaff
        }
    }

    fun reorderInstrument(fromIdx: Int, toIdx: Int) {
        val beforeInst = score.instruments.toList()
        val afterInst = ScoreInstrumentPart.reorderInstruments(beforeInst, fromIdx, toIdx)
        if (afterInst === beforeInst) return

        selectedLocations = selectedLocations.map { loc ->
            val mappedIdx = ScoreInstrumentPart.remapInstrumentIndex(loc.staff.instrumentIdx, fromIdx, toIdx)
            loc.copy(staff = loc.staff.copy(instrumentIdx = mappedIdx))
        }.toSet()
        selectedDynamics = selectedDynamics.map { dyn ->
            val mappedIdx = ScoreInstrumentPart.remapInstrumentIndex(dyn.staff.instrumentIdx, fromIdx, toIdx)
            dyn.copy(staff = dyn.staff.copy(instrumentIdx = mappedIdx))
        }.toSet()
        selectedClefChange = selectedClefChange?.let { sc ->
            val mappedIdx = ScoreInstrumentPart.remapInstrumentIndex(sc.staff.instrumentIdx, fromIdx, toIdx)
            sc.copy(staff = sc.staff.copy(instrumentIdx = mappedIdx))
        }
        activeStaff = activeStaff.copy(
            instrumentIdx = ScoreInstrumentPart.remapInstrumentIndex(activeStaff.instrumentIdx, fromIdx, toIdx)
        )
        module.cursorPosition?.let { cur ->
            val mappedIdx = ScoreInstrumentPart.remapInstrumentIndex(cur.staff.instrumentIdx, fromIdx, toIdx)
            module.placeCursor(cur.copy(staff = cur.staff.copy(instrumentIdx = mappedIdx)))
        }
        val cmd = ReplaceScoreInstrumentsCommand(module, beforeInst, afterInst)
        val e = module.getEngine()
        if (e != null) {
            e.post(cmd)
        } else {
            cmd.execute()
        }
        focusRequester.requestFocus()
    }

    fun refreshPitchDisplayFromSelection(
        overrideStaff: ScoreStaffId? = null,
        overrideMeasures: List<ScoreMeasure>? = null,
        selection: Set<ScoreItemLocation> = selectedLocations,
    ) {
        fun measuresFor(staff: ScoreStaffId): List<ScoreMeasure> =
            if (overrideMeasures != null && (overrideStaff == null || overrideStaff == staff)) {
                overrideMeasures
            } else {
                score.staffMeasures(staff)
            }

        val selectedPitches = selection.flatMap { loc ->
            val staffMeasures = measuresFor(loc.staff)
            when (val item = ScoreEditing.itemAt(staffMeasures, loc)) {
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
                val staffMeasures = measuresFor(slot.staff)
                val chord = ScoreEditing.itemAt(staffMeasures, slot) as? ScoreItem.Chord
                if (chord != null) slot to chord else null
            }
        activeChordName = singleSlotChord?.let { (slot, chord) ->
            val staffMeasures = measuresFor(slot.staff)
            val key = ScoreEditing.effectiveKeyAtMeasure(staffMeasures, score.keySignature, slot.measureIdx)
            ScoreChordAnalyzer.recognizeChordName(chord.pitches, key)
        }
    }

    DisposableEffect(module) {
        module.onRealTimeMeasuresCommitted = { staffId, beforeMeasures, afterMeasures ->
            val engine = state.engine ?: module.getEngine()
            if (engine != null) {
                engine.post(
                    ReplaceScoreMeasuresCommand(score, beforeMeasures, afterMeasures, staff = staffId)
                )
            }
            state.markChanged()
        }
        onDispose {
            module.onRealTimeMeasuresCommitted = null
        }
    }

    fun postCommand(command: SyntonaCommand) {
        val engine = state.engine ?: module.getEngine()
        if (engine != null) {
            engine.post(command)
            if (module.isPlaying) {
                engine.post(object : SyntonaCommand {
                    override val isUndoable = false
                    override fun execute() {
                        module.notifyScoreEditedWhilePlaying()
                    }
                    override fun undo() {}
                })
            }
        } else {
            command.execute()
            if (module.isPlaying) {
                module.notifyScoreEditedWhilePlaying()
            }
        }
        state.markChanged()
    }

    fun targetClefLocation(): ScoreClefLocation {
        val base = ScoreEditing.resolveTargetClefLocation(
            selectedClefChange = selectedClefChange,
            selectedLocations = selectedLocations,
            cursorPosition = module.cursorPosition,
            selectedMeasures = selectedMeasures,
        )
        val staff = selectedClefChange?.staff
            ?: selectedLocations.firstOrNull()?.staff
            ?: module.cursorPosition?.staff
            ?: resolvedActiveStaff()
        return base.copy(staff = staff)
    }

    fun applyClef(newClef: Clef?, target: ScoreClefLocation) {
        val targetStaff = target.staff
        val beforeInitialClef = score.staffClef(targetStaff)
        val beforeMeasures = score.staffMeasures(targetStaff).toList()
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
                staff = targetStaff,
            )
        )
    }

    fun applyDynamic(
        location: ScoreDynamicLocation,
        mark: DynamicMark,
        placement: DynamicPlacement,
    ) {
        val targetStaff = location.staff
        val before = score.staffMeasures(targetStaff).toList()
        val prevSelected = selectedDynamics
        val (after, placedLoc) = ScoreEditing.setDynamic(
            measures = before,
            location = location,
            marking = ScoreDynamicMarking(mark = mark, placement = placement),
        )
        if (after == before || placedLoc == null) return
        val placedWithStaff = placedLoc.copy(staff = targetStaff)
        selectedLocations = emptySet()
        selectedMeasures = emptySet()
        selectedClefChange = null
        selectedDynamics = setOf(placedWithStaff)
        activePitchText = null
        activeChordName = null
        postCommand(
            ReplaceScoreMeasuresCommand(score, before, after, staff = targetStaff) { restored ->
                selectedDynamics = if (restored == before) {
                    prevSelected.filterTo(linkedSetOf()) { loc ->
                        score.staffMeasures(loc.staff).getOrNull(loc.measureIdx)?.dynamics?.containsKey(loc.itemIdx) == true
                    }
                } else {
                    setOf(placedWithStaff)
                }
            }
        )
    }

    fun toggleBeamAt(loc: ScoreItemLocation) {
        val targetStaff = loc.staff
        val before = score.staffMeasures(targetStaff).toList()
        val after = ScoreEditing.toggleBeamAt(
            measures = before,
            initialClef = score.staffClef(targetStaff),
            initialTimeSignature = score.timeSignature,
            slotLoc = loc,
        )
        if (after == before) return
        postCommand(
            ReplaceScoreMeasuresCommand(score, before, after, staff = targetStaff) {
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            }
        )
    }

    fun toggleTieAt(loc: ScoreItemLocation) {
        val targetStaff = loc.staff
        val before = score.staffMeasures(targetStaff).toList()
        val after = ScoreEditing.toggleTieAt(
            measures = before,
            slotLoc = loc,
        )
        if (after == before) return
        postCommand(
            ReplaceScoreMeasuresCommand(score, before, after, staff = targetStaff) {
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            }
        )
    }

    fun updateMeasuresAcrossStaves(
        staves: Set<ScoreStaffId>,
        transform: (ScoreStaffId, List<ScoreMeasure>) -> List<ScoreMeasure>,
        onAfterRestore: ((Boolean) -> Unit)? = null,
    ) {
        if (staves.isEmpty()) return
        val singleStaff = staves.singleOrNull()
        if (singleStaff != null) {
            val before = score.staffMeasures(singleStaff).toList()
            val after = transform(singleStaff, before)
            if (after == before) return
            postCommand(
                ReplaceScoreMeasuresCommand(score, before, after, staff = singleStaff) { restored ->
                    onAfterRestore?.invoke(restored == before)
                }
            )
        } else {
            val beforeInst = score.instruments.toList()
            var changed = false
            val afterInst = beforeInst.mapIndexed { instIdx, part ->
                var updatedPart = part
                for (sIdx in 0 until part.staffCount) {
                    val staffId = ScoreStaffId(instIdx, sIdx)
                    if (staffId in staves) {
                        val cur = updatedPart.measuresForStaff(sIdx)
                        val next = transform(staffId, cur)
                        if (next != cur) {
                            updatedPart = updatedPart.withStaffMeasures(sIdx, next)
                            changed = true
                        }
                    }
                }
                updatedPart
            }
            if (!changed) return
            postCommand(
                ReplaceScoreInstrumentsCommand(module, beforeInst, afterInst) {
                    onAfterRestore?.invoke(score.instruments.toList() == beforeInst)
                }
            )
        }
    }

    fun fillScoreWithRests() {
        durationEditState = null
        updateMeasuresAcrossStaves(
            staves = score.allStaffIds().toSet(),
            transform = { _, measures ->
                ScoreEditing.fillMeasuresWithRests(measures, score.timeSignature)
            },
            onAfterRestore = {
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            },
        )
    }

    fun toggleAutoFillRests() {
        val next = !module.autoFillRests
        module.autoFillRests = next
        if (next) {
            fillScoreWithRests()
        }
    }

    fun replaceEntryItem(
        measures: List<ScoreMeasure>,
        loc: ScoreItemLocation,
        item: ScoreItem,
    ): List<ScoreMeasure> = if (module.autoFillRests) {
        ScoreEditing.replaceItemWithAutoFillRests(measures, loc, item, score.timeSignature)
    } else {
        ScoreEditing.replaceItem(measures, loc, item)
    }

    fun insertEntryItem(
        measures: List<ScoreMeasure>,
        measureIdx: Int,
        itemIdx: Int,
        item: ScoreItem,
        appendNewMeasure: Boolean,
        shiftClefAtIndex: Boolean,
    ): Pair<List<ScoreMeasure>, ScoreItemLocation> = if (module.autoFillRests) {
        ScoreEditing.insertItemWithAutoFillRests(
            measures = measures,
            measureIdx = measureIdx,
            itemIdx = itemIdx,
            item = item,
            initialTimeSignature = score.timeSignature,
            appendNewMeasure = appendNewMeasure,
            shiftClefAtIndex = shiftClefAtIndex,
        )
    } else {
        ScoreEditing.insertItem(
            measures = measures,
            measureIdx = measureIdx,
            itemIdx = itemIdx,
            item = item,
            appendNewMeasure = appendNewMeasure,
            shiftClefAtIndex = shiftClefAtIndex,
        )
    }

    fun filterValidLocations(locs: Set<ScoreItemLocation>): Set<ScoreItemLocation> =
        locs.filterTo(linkedSetOf()) { loc ->
            val ms = score.staffMeasures(loc.staff)
            ScoreEditing.pitchAt(ms, loc) != null ||
                (loc.chordNoteIdx == null && ScoreEditing.itemAt(ms, loc) != null)
        }

    fun filterValidDynamics(dyns: Set<ScoreDynamicLocation>): Set<ScoreDynamicLocation> =
        dyns.filterTo(linkedSetOf()) { loc ->
            score.staffMeasures(loc.staff).getOrNull(loc.measureIdx)?.dynamics?.containsKey(loc.itemIdx) == true
        }

    fun insertEmptyBar() {
        durationEditState = null
        val beforeInst = score.instruments.toList()
        val insertIdx = selectedMeasures.minOrNull()?.coerceIn(0, score.measures.size)
        val afterInst = beforeInst.map { part ->
            val rawPart = if (insertIdx != null) {
                val updatedUpper = ScoreEditing.insertEmptyMeasureAt(part.measures, insertIdx)
                if (part.staffCount >= 2) {
                    val updatedLower = ScoreEditing.insertEmptyMeasureAt(part.lowerMeasures, insertIdx)
                    part.copy(measures = updatedUpper, lowerMeasures = updatedLower)
                } else {
                    part.copy(measures = updatedUpper)
                }
            } else {
                val updatedUpper = ScoreEditing.appendEmptyMeasure(part.measures)
                if (part.staffCount >= 2) {
                    val updatedLower = ScoreEditing.appendEmptyMeasure(part.lowerMeasures)
                    part.copy(
                        measures = ScoreEditing.renumber(updatedUpper),
                        lowerMeasures = ScoreEditing.renumber(updatedLower),
                    )
                } else {
                    part.copy(measures = ScoreEditing.renumber(updatedUpper))
                }
            }
            if (module.autoFillRests) {
                rawPart.copy(
                    measures = ScoreEditing.fillMeasuresWithRests(rawPart.measures, score.timeSignature),
                    lowerMeasures = if (rawPart.staffCount >= 2) {
                        ScoreEditing.fillMeasuresWithRests(rawPart.lowerMeasures, score.timeSignature)
                    } else {
                        rawPart.lowerMeasures
                    },
                )
            } else {
                rawPart
            }
        }
        if (insertIdx == null) {
            postCommand(ReplaceScoreInstrumentsCommand(module, beforeInst, afterInst))
            return
        }
        val prevMeasures = selectedMeasures
        val prevLocs = selectedLocations
        val prevDynamics = selectedDynamics
        val prevClef = selectedClefChange
        val prevCursor = module.cursorPosition
        val afterMeasures = setOf(insertIdx)
        val afterLocs = prevLocs.map { loc ->
            if (loc.measureIdx >= insertIdx) loc.copy(measureIdx = loc.measureIdx + 1) else loc
        }.toCollection(linkedSetOf())
        val afterDynamics = prevDynamics.map { dyn ->
            if (dyn.measureIdx >= insertIdx) dyn.copy(measureIdx = dyn.measureIdx + 1) else dyn
        }.toCollection(linkedSetOf())
        val afterClef = prevClef?.let { c ->
            if (c.measureIdx >= insertIdx) c.copy(measureIdx = c.measureIdx + 1) else c
        }
        val afterCursor = prevCursor?.let { c ->
            if (c.measureIdx >= insertIdx) c.copy(measureIdx = c.measureIdx + 1) else c
        }
        postCommand(
            ReplaceScoreInstrumentsCommand(module, beforeInst, afterInst) {
                if (score.instruments.toList() == beforeInst) {
                    selectedMeasures = prevMeasures
                    selectedLocations = prevLocs
                    selectedDynamics = prevDynamics
                    selectedClefChange = prevClef
                    module.placeCursor(prevCursor)
                } else {
                    selectedMeasures = afterMeasures
                    selectedLocations = afterLocs
                    selectedDynamics = afterDynamics
                    selectedClefChange = afterClef
                    module.placeCursor(afterCursor)
                }
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            }
        )
    }

    fun deleteMeasuresSelection(targetMeasures: Set<Int>) {
        if (targetMeasures.isEmpty()) return
        durationEditState = null
        val beforeInst = score.instruments.toList()
        val beforeKey = score.keySignature
        val beforeTime = score.timeSignature
        var nextKey = beforeKey
        var nextTime = beforeTime
        val afterInst = beforeInst.mapIndexed { instIdx, part ->
            val res0 = ScoreEditing.deleteMeasures(beforeKey, beforeTime, part.clef, part.measures, targetMeasures)
            if (instIdx == 0) {
                nextKey = res0.initialKey
                nextTime = res0.initialTime
            }
            if (part.staffCount >= 2) {
                val res1 = ScoreEditing.deleteMeasures(beforeKey, beforeTime, part.lowerClef, part.lowerMeasures, targetMeasures)
                part.copy(
                    clef = res0.initialClef,
                    lowerClef = res1.initialClef,
                    measures = res0.measures,
                    lowerMeasures = res1.measures,
                )
            } else {
                part.copy(
                    clef = res0.initialClef,
                    measures = res0.measures,
                )
            }
        }
        if (afterInst == beforeInst && nextKey == beforeKey && nextTime == beforeTime) return
        val prevMeasures = selectedMeasures
        val prevLocs = selectedLocations
        val prevDynamics = selectedDynamics
        val prevClef = selectedClefChange
        val prevCursor = module.cursorPosition
        postCommand(
            ReplaceScoreInstrumentsCommand(
                module = module,
                beforeInstruments = beforeInst,
                afterInstruments = afterInst,
                beforeKeySignature = beforeKey,
                afterKeySignature = nextKey,
                beforeTimeSignature = beforeTime,
                afterTimeSignature = nextTime,
            ) {
                if (score.instruments.toList() == beforeInst) {
                    selectedMeasures = prevMeasures
                    selectedLocations = prevLocs
                    selectedDynamics = prevDynamics
                    selectedClefChange = prevClef
                    module.placeCursor(prevCursor)
                    refreshPitchDisplayFromSelection(selection = prevLocs)
                } else {
                    selectedMeasures = emptySet()
                    selectedLocations = emptySet()
                    selectedDynamics = emptySet()
                    selectedClefChange = null
                    activePitchText = null
                    activeChordName = null
                    module.placeCursor(null)
                }
            }
        )
    }

    fun deleteSelection() {
        durationEditState = null
        if (selectedLocations.isEmpty() && selectedDynamics.isEmpty()) {
            val clefLoc = selectedClefChange
            if (clefLoc != null) {
                applyClef(null, clefLoc)
                return
            }
            if (selectedMeasures.isNotEmpty()) {
                deleteMeasuresSelection(selectedMeasures)
            }
            return
        }
        val prevSelection = selectedLocations
        val prevDynamics = selectedDynamics
        val affectedStaves = (prevSelection.map { it.staff } + prevDynamics.map { it.staff }).toSet()
        val replacedRestLocs = linkedSetOf<ScoreItemLocation>()
        selectedLocations = emptySet()
        selectedDynamics = emptySet()
        activePitchText = null
        activeChordName = null
        updateMeasuresAcrossStaves(
            staves = affectedStaves,
            transform = { staffId, ms ->
                val staffDyns = prevDynamics.filterTo(mutableSetOf()) { it.staff == staffId }
                val staffLocs = prevSelection.filterTo(mutableSetOf()) { it.staff == staffId }
                val afterDyns = if (staffDyns.isEmpty()) ms else ScoreEditing.deleteDynamics(ms, staffDyns)
                if (staffLocs.isEmpty()) {
                    afterDyns
                } else {
                    val res = ScoreEditing.deleteSelectionItems(
                        measures = afterDyns,
                        selection = staffLocs,
                        autoFillRests = module.autoFillRests,
                        initialTimeSignature = score.timeSignature,
                    )
                    res.selectedRestLocations.forEach { loc ->
                        replacedRestLocs.add(loc.copy(staff = staffId))
                    }
                    res.measures
                }
            },
            onAfterRestore = { isUndo ->
                selectedLocations = filterValidLocations(if (isUndo) prevSelection else replacedRestLocs)
                selectedDynamics = filterValidDynamics(prevDynamics)
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            },
        )
        selectedLocations = filterValidLocations(replacedRestLocs)
        refreshPitchDisplayFromSelection(selection = selectedLocations)
    }

    fun toggleDotMode() {
        val next = !entryIsDotted
        entryIsDotted = next
        module.stepRecordDotted = next
    }

    fun clearSelection() {
        selectedLocations = emptySet()
        selectedMeasures = emptySet()
        selectedDynamics = emptySet()
        selectedClefChange = null
        activePitchText = null
        activeChordName = null
    }

    fun applyTupletRatio(ratio: Pair<Int, Int>?) {
        if (selectedLocations.isNotEmpty() && editorMode == ScoreEditMode.SELECT && !module.isStepRecording) {
            val prevSelection = selectedLocations
            val affectedStaves = prevSelection.map { it.staff }.toSet()
            updateMeasuresAcrossStaves(
                staves = affectedStaves,
                transform = { staffId, ms ->
                    val staffLocs = prevSelection.filterTo(mutableSetOf()) { it.staff == staffId }
                    ScoreEditing.applyTupletOnSelection(ms, staffLocs, ratio)
                },
                onAfterRestore = { _ ->
                    selectedLocations = filterValidLocations(prevSelection)
                    refreshPitchDisplayFromSelection(selection = selectedLocations)
                },
            )
            return
        }
        clearSelection()
        val nextRatio = when {
            ratio == null -> null
            module.stepRecordTupletRatio == ratio && (editorMode == ScoreEditMode.ENTRY || module.isStepRecording) -> null
            else -> ratio
        }
        module.stepRecordTupletRatio = nextRatio
        if (nextRatio != null && !module.isStepRecording) {
            editorMode = ScoreEditMode.ENTRY
            module.placeCursor(null)
        }
    }

    fun applyDurationToSelection(duration: NoteDuration, isDotted: Boolean) {
        if (selectedLocations.isEmpty()) return
        val prevSelection = selectedLocations
        val affectedStaves = prevSelection.map { it.staff }.toSet()
        updateMeasuresAcrossStaves(
            staves = affectedStaves,
            transform = { staffId, ms ->
                val staffLocs = prevSelection.filterTo(mutableSetOf()) { it.staff == staffId }
                val updated = ScoreEditing.setDurationOnSelection(ms, staffLocs, duration, isDotted)
                if (module.autoFillRests) {
                    ScoreEditing.fillMeasuresWithRests(updated, score.timeSignature)
                } else {
                    updated
                }
            },
            onAfterRestore = { _ ->
                selectedLocations = filterValidLocations(prevSelection)
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            },
        )
    }

    fun selectDuration(duration: NoteDuration) {
        clearSelection()
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
        val hasSelectedNotes = selectedLocations.any { loc ->
            ScoreEditing.itemAt(score.staffMeasures(loc.staff), loc)?.soundingPitches()?.isNotEmpty() == true
        }
        if (hasSelectedNotes) {
            val prevSelection = selectedLocations
            val affectedStaves = prevSelection.map { it.staff }.toSet()
            updateMeasuresAcrossStaves(
                staves = affectedStaves,
                transform = { staffId, ms ->
                    val staffLocs = prevSelection.filterTo(mutableSetOf()) { it.staff == staffId }
                    ScoreEditing.setAccidentalOnSelection(ms, staffLocs, accidental.alter)
                },
                onAfterRestore = {
                    refreshPitchDisplayFromSelection(selection = selectedLocations)
                },
            )
            val firstLoc = selectedLocations.firstOrNull()
            val firstMeasures = firstLoc?.let { score.staffMeasures(it.staff) }
            val firstPitch = firstLoc?.let { loc ->
                val ms = firstMeasures ?: return@let null
                ScoreEditing.pitchAt(ms, loc) ?: ScoreEditing.itemAt(ms, loc)?.soundingPitches()?.firstOrNull()
            }
            val firstItem = firstLoc?.let { loc -> firstMeasures?.let { ScoreEditing.itemAt(it, loc) } }
            if (firstPitch != null && firstItem != null) {
                module.previewPitch(
                    firstPitch.midiPitch.toDouble(),
                    isStaccato = firstItem.noteStaccato(),
                    instrumentIdx = firstLoc.staff.instrumentIdx,
                )
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            }
        } else {
            pendingAccidental = if (pendingAccidental == accidental) null else accidental
        }
    }

    fun transposeSelection(steps: Int, chromatic: Boolean) {
        if (selectedLocations.isEmpty() || steps == 0) return
        val mode = if (chromatic) {
            ScoreTransposeMode.CHROMATIC_SEMITONE
        } else {
            ScoreTransposeMode.DIATONIC_IN_KEY
        }
        val prevSelection = selectedLocations
        val affectedStaves = prevSelection.map { it.staff }.toSet()
        val remappedByStaff = mutableMapOf<ScoreStaffId, Set<ScoreItemLocation>>()
        updateMeasuresAcrossStaves(
            staves = affectedStaves,
            transform = { staffId, ms ->
                val staffLocs = prevSelection.filterTo(mutableSetOf()) { it.staff == staffId }
                val (transposedMeasures, remappedLocs) = ScoreEditing.transposeSelection(
                    measures = ms,
                    initialKey = score.keySignature,
                    selection = staffLocs,
                    steps = steps,
                    mode = mode,
                )
                remappedByStaff[staffId] = remappedLocs
                transposedMeasures
            },
            onAfterRestore = { isUndo ->
                val nextSel = if (isUndo) {
                    filterValidLocations(prevSelection)
                } else {
                    val merged = remappedByStaff.values.flatten().toCollection(linkedSetOf())
                    filterValidLocations(merged.ifEmpty { prevSelection })
                }
                selectedLocations = nextSel
                refreshPitchDisplayFromSelection(selection = nextSel)
            },
        )
        val mergedSelection = remappedByStaff.values.flatten().toCollection(linkedSetOf())
        if (mergedSelection.isNotEmpty()) {
            selectedLocations = mergedSelection
        }
        val singleSlot = selectedLocations.map { it.slotLocation() }.distinct().singleOrNull()
        if (singleSlot != null) {
            val staffMs = score.staffMeasures(singleSlot.staff)
            val previewLoc = selectedLocations.first()
            val previewItem = ScoreEditing.itemAt(staffMs, singleSlot)
            val previewPitch = ScoreEditing.pitchAt(staffMs, previewLoc)
                ?: previewItem?.soundingPitches()?.firstOrNull()
            if (previewPitch != null) {
                module.previewPitch(
                    previewPitch.midiPitch.toDouble(),
                    isStaccato = previewItem?.noteStaccato() == true,
                    instrumentIdx = singleSlot.staff.instrumentIdx,
                )
            }
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
        val hasSelectedNotes = selectedLocations.any { loc ->
            ScoreEditing.itemAt(score.staffMeasures(loc.staff), loc)?.soundingPitches()?.isNotEmpty() == true
        }
        if (hasSelectedNotes) {
            val prevSelection = selectedLocations
            val affectedStaves = prevSelection.map { it.staff }.toSet()
            updateMeasuresAcrossStaves(
                staves = affectedStaves,
                transform = { staffId, ms ->
                    val staffLocs = prevSelection.filterTo(mutableSetOf()) { it.staff == staffId }
                    ScoreEditing.toggleStaccatoOnSelection(ms, staffLocs)
                },
            )
            val firstLoc = selectedLocations.firstOrNull()
            val firstMeasures = firstLoc?.let { score.staffMeasures(it.staff) }
            val firstPitch = firstLoc?.let { loc ->
                val ms = firstMeasures ?: return@let null
                ScoreEditing.pitchAt(ms, loc) ?: ScoreEditing.itemAt(ms, loc)?.soundingPitches()?.firstOrNull()
            }
            val firstItem = firstLoc?.let { loc -> firstMeasures?.let { ScoreEditing.itemAt(it, loc) } }
            if (firstPitch != null && firstItem != null) {
                module.previewPitch(
                    firstPitch.midiPitch.toDouble(),
                    isStaccato = firstItem.noteStaccato(),
                    instrumentIdx = firstLoc.staff.instrumentIdx,
                )
            }
        } else {
            pendingStaccato = !pendingStaccato
            module.stepRecordStaccato = pendingStaccato
        }
    }

    fun copySelection() {
        val targetStaff = selectedLocations.firstOrNull()?.staff
            ?: selectedDynamics.firstOrNull()?.staff
            ?: selectedClefChange?.staff
            ?: resolvedActiveStaff()
        val content = ScoreEditing.copySelectionContent(
            measures = score.staffMeasures(targetStaff),
            initialClef = score.staffClef(targetStaff),
            initialKeySignature = score.keySignature,
            initialTimeSignature = score.timeSignature,
            selectedLocations = selectedLocations.filterTo(linkedSetOf()) { it.staff == targetStaff },
            selectedMeasures = selectedMeasures,
            selectedClefChange = selectedClefChange?.takeIf { it.staff == targetStaff },
            selectedDynamics = selectedDynamics.filterTo(linkedSetOf()) { it.staff == targetStaff },
        )
        ScoreClipboard.copyContent(content)
    }

    fun pasteClipboard() {
        val clipContent = ScoreClipboard.content ?: return
        if (clipContent is ScoreClipboardContent.ClefOnly) {
            applyClef(clipContent.clef, targetClefLocation())
            return
        }
        val targetStaff = selectedLocations.firstOrNull()?.staff
            ?: module.cursorPosition?.staff
            ?: resolvedActiveStaff()
        val beforeMeasures = score.staffMeasures(targetStaff).toList()
        val beforeInitialClef = score.staffClef(targetStaff)
        val beforeInitialKey = score.keySignature
        val beforeInitialTime = score.timeSignature
        val beforeSelection = selectedLocations
        val beforeSelectedMeasures = selectedMeasures
        val beforeSelectedDynamics = selectedDynamics
        val beforeCursor = module.cursorPosition
        val result = ScoreEditing.pasteClipboardContent(
            measures = beforeMeasures,
            content = clipContent,
            initialClef = beforeInitialClef,
            initialKeySignature = beforeInitialKey,
            initialTimeSignature = beforeInitialTime,
            selectedLocations = beforeSelection.filterTo(linkedSetOf()) { it.staff == targetStaff },
            cursorPosition = beforeCursor?.takeIf { it.staff == targetStaff },
            selectedMeasures = beforeSelectedMeasures,
            appendMeasureOnFull = module.isStepRecording,
        ) ?: return
        selectedClefChange = null
        val afterInitialClef = result.initialClef ?: beforeInitialClef
        val afterInitialKey = result.initialKeySignature ?: beforeInitialKey
        val afterInitialTime = result.initialTimeSignature ?: beforeInitialTime
        val staffLocations = result.pastedLocations.mapTo(linkedSetOf()) { it.copy(staff = targetStaff) }
        val staffDynamics = result.pastedDynamics.mapTo(linkedSetOf()) { it.copy(staff = targetStaff) }
        val afterMeasures = if (module.autoFillRests) {
            ScoreEditing.fillMeasuresWithRests(result.measures, afterInitialTime)
        } else {
            result.measures
        }
        postCommand(
            ReplaceScoreDocumentCommand(
                scoreData = score,
                beforeInitialClef = beforeInitialClef,
                beforeInitialKey = beforeInitialKey,
                beforeInitialTime = beforeInitialTime,
                beforeMeasures = beforeMeasures,
                afterInitialClef = afterInitialClef,
                afterInitialKey = afterInitialKey,
                afterInitialTime = afterInitialTime,
                afterMeasures = afterMeasures,
                staff = targetStaff,
            ) { _, isUndo ->
                if (isUndo) {
                    module.placeCursor(beforeCursor)
                    selectedMeasures = beforeSelectedMeasures
                    selectedLocations = filterValidLocations(beforeSelection)
                    selectedDynamics = filterValidDynamics(beforeSelectedDynamics)
                } else if (module.isStepRecording) {
                    module.placeCursor(result.nextCursor.copy(staff = targetStaff))
                    selectedMeasures = emptySet()
                    selectedLocations = emptySet()
                    selectedDynamics = emptySet()
                    pendingScrollMeasures = result.pastedMeasures.ifEmpty {
                        (staffLocations.map { it.measureIdx } +
                            staffDynamics.map { it.measureIdx }).toCollection(linkedSetOf())
                    }
                } else {
                    module.placeCursor(null)
                    selectedMeasures = result.pastedMeasures
                    selectedLocations = filterValidLocations(staffLocations)
                    selectedDynamics = filterValidDynamics(staffDynamics)
                    pendingScrollMeasures = result.pastedMeasures.ifEmpty {
                        (staffLocations.map { it.measureIdx } +
                            staffDynamics.map { it.measureIdx }).toCollection(linkedSetOf())
                    }
                }
                refreshPitchDisplayFromSelection(selection = selectedLocations)
            }
        )
    }

    // Route Cut / Copy / Paste / Delete from the global window Edit handler while Score window is active.
    DisposableEffect(module) {
        val window = mdiState.windows.find { it.id == module }
        val handler = object : EditActionHandler {
            override fun canCopy() =
                selectedLocations.isNotEmpty() || selectedMeasures.isNotEmpty() ||
                    selectedClefChange != null || selectedDynamics.isNotEmpty()

            override fun canCut() =
                selectedLocations.isNotEmpty() ||
                    selectedDynamics.isNotEmpty() ||
                    selectedClefChange?.let { it.measureIdx > 0 || it.itemIdx > 0 } == true

            override fun canPaste() = ScoreClipboard.isNotEmpty
            override fun canDelete() =
                selectedLocations.isNotEmpty() || selectedClefChange != null || selectedDynamics.isNotEmpty()
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
        if (editingTimeSig != null || customTupletDialogOpen) return false
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
            } else if (editorMode == ScoreEditMode.ENTRY && module.stepRecordTupletRatio != null) {
                durationEditState = null
                module.stepRecordTupletRatio = null
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
        if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
            if (!event.isAltPressed && selectedLocations.isNotEmpty() && !module.isStepRecording) {
                durationEditState = null
                val step = if (event.key == Key.DirectionUp) 1 else -1
                transposeSelection(steps = step, chromatic = event.isKeyShiftPressed)
                return true
            }
            return false
        }

        if (event.isAltPressed) {
            when (val altAction = resolveAltTupletShortcut(event.key, event.utf16CodePoint)) {
                ScoreAltTupletAction.OpenCustomDialog -> {
                    openPalette = null
                    customTupletActualText = (module.stepRecordTupletRatio?.first ?: 3).toString()
                    customTupletNormalText = (module.stepRecordTupletRatio?.second ?: 2).toString()
                    customTupletDialogOpen = true
                    return true
                }
                is ScoreAltTupletAction.SetRatio -> {
                    openPalette = null
                    applyTupletRatio(altAction.ratio)
                    return true
                }
                null -> return false
            }
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
            'p' -> {
                if (!module.isStepRecording) {
                    openPalette = null
                    pendingAccidental = null
                    activeDynamic = if (event.isKeyShiftPressed) {
                        DynamicMark.MP
                    } else if (editorMode == ScoreEditMode.DYNAMIC) {
                        DynamicMark.nextPianoCycle(activeDynamic)
                    } else {
                        DynamicMark.P
                    }
                    editorMode = ScoreEditMode.DYNAMIC
                    module.placeCursor(null)
                }
                true
            }
            'f' -> {
                if (!module.isStepRecording) {
                    openPalette = null
                    pendingAccidental = null
                    activeDynamic = if (event.isKeyShiftPressed) {
                        DynamicMark.MF
                    } else if (editorMode == ScoreEditMode.DYNAMIC) {
                        DynamicMark.nextForteCycle(activeDynamic)
                    } else {
                        DynamicMark.F
                    }
                    editorMode = ScoreEditMode.DYNAMIC
                    module.placeCursor(null)
                }
                true
            }
            'b' -> {
                if (event.isKeyShiftPressed && !module.isStepRecording) {
                    openPalette = null
                    pendingAccidental = null
                    editorMode = ScoreEditMode.BEAM
                    module.placeCursor(null)
                    true
                } else {
                    false
                }
            }
            'd' -> { toggleDotMode(); true }
            't' -> {
                if (event.isKeyShiftPressed && !module.isStepRecording) {
                    openPalette = null
                    pendingAccidental = null
                    editorMode = ScoreEditMode.TIE
                    module.placeCursor(null)
                } else {
                    selectDuration(NoteDuration.THIRTY_SECOND)
                }
                true
            }
            's', 'e', 'q', 'h', 'w' -> {
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
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    !keyEvent.isMetaPressed &&
                    !keyEvent.isCtrlPressed &&
                    !keyEvent.isAltPressed &&
                    editingTimeSig == null &&
                    !customTupletDialogOpen &&
                    instrumentDialog == null &&
                    (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown) &&
                    selectedLocations.isNotEmpty() &&
                    !module.isStepRecording
                ) {
                    handleKeyEvent(keyEvent)
                } else {
                    false
                }
            }
            .onKeyEvent(::handleKeyEvent)
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
            ScoreFileMenu(
                textColor = toolbarText,
                onImportMusicXml = {
                    showOpenTextFileDialog(
                        extensions = listOf("musicxml", "xml"),
                        filterDescription = "MusicXML files (*.musicxml, *.xml)",
                        dialogTitle = "Import MusicXML",
                    ) { _, content ->
                        importMusicXmlContent(content)
                        focusRequester.requestFocus()
                    }
                },
                onExportMusicXml = {
                    showExportSourceFileDialog(
                        defaultName = "${ScoreMusicXml.DEFAULT_TITLE}.musicxml",
                        extension = "musicxml",
                        dialogTitle = "Export MusicXML",
                    ) { chosenPath ->
                        val xml = ScoreMusicXml.exportModuleToString(module)
                        saveFileDirect(chosenPath, xml)
                        focusRequester.requestFocus()
                    }
                },
            )

            ScoreEditMenu(
                textColor = toolbarText,
                autoFillRests = module.autoFillRests,
                onFillWithRests = {
                    fillScoreWithRests()
                    focusRequester.requestFocus()
                },
                onToggleAutoFillRests = {
                    toggleAutoFillRests()
                    focusRequester.requestFocus()
                },
            )

            Text("|", color = borderMuted)

            TextButton(
                onClick = {
                    module.requestRewind()
                    selectedLocations = emptySet()
                    selectedMeasures = emptySet()
                    selectedClefChange = null
                    selectedDynamics = emptySet()
                    activePitchText = null
                    activeChordName = null
                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RewindIcon(color = toolbarText, size = 10.dp)
                    Text("Rewind", fontSize = 12.sp, color = toolbarText)
                }
            }

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

            val loopColor = if (isDark) Color(0xFF81C784) else Color(0xFF15803D)
            ToolbarToggleChip(
                selected = module.loopMode,
                accentColor = loopColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = {
                    module.toggleLoopMode()
                    FileState.markDirty()
                },
            ) { color ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LoopIcon(
                        color = if (module.loopMode) loopColor else color,
                        size = 11.dp,
                    )
                    Text("Loop", fontSize = 11.sp, color = if (module.loopMode) loopColor else color)
                }
            }

            val recColor = Color(0xFFE53935)
            ToolbarToggleChip(
                selected = module.isRealTimeRecording,
                accentColor = recColor,
                borderMuted = borderMuted,
                textColor = toolbarText,
                isDark = isDark,
                onClick = {
                    module.requestRealTimeRecordToggle()
                    focusRequester.requestFocus()
                },
            ) { color ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RecordIcon(
                        color = if (module.isRealTimeRecording) recColor else color,
                        size = 9.dp,
                    )
                    Text("Rec", fontSize = 11.sp, color = if (module.isRealTimeRecording) recColor else color)
                }
            }

            ScoreRecordSettingsMenu(
                recordMode = module.recordMode,
                quantizeGrid = module.recordQuantizeGrid,
                detectTriplets = module.recordDetectTriplets,
                latencyMs = module.recordLatencyMs,
                estimatedOutputLatencyMs = module.estimatedOutputLatencyMs(),
                textColor = toolbarText,
                accentColor = accentColor,
                isDark = isDark,
                onSelectRecordMode = {
                    module.recordMode = it
                    FileState.markDirty()
                    focusRequester.requestFocus()
                },
                onSelectQuantizeGrid = {
                    module.recordQuantizeGrid = it
                    FileState.markDirty()
                    focusRequester.requestFocus()
                },
                onToggleDetectTriplets = {
                    module.recordDetectTriplets = !module.recordDetectTriplets
                    FileState.markDirty()
                    focusRequester.requestFocus()
                },
                onChangeLatencyMs = { nextMs ->
                    module.recordLatencyMs = nextMs.coerceIn(ScoreModule.MIN_LATENCY_MS, ScoreModule.MAX_LATENCY_MS)
                    FileState.markDirty()
                },
            )

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

            ScoreKeySelector(
                selected = activeTargetKey,
                textColor = toolbarText,
                onSelect = { applyKeySignature(it, targetMeasureIndices) },
            )

            ScoreTimeSigSelector(
                selected = activeTargetTime,
                textColor = toolbarText,
                onSelect = { applyTimeSignature(it, targetMeasureIndices) },
            )

            TextButton(
                onClick = {
                    insertEmptyBar()
                    focusRequester.requestFocus()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false },
            ) {
                Text("+ Bar", fontSize = 11.sp, color = toolbarText)
            }

            val hasSelectedBars = selectedMeasures.isNotEmpty()
            TextButton(
                enabled = hasSelectedBars,
                onClick = {
                    deleteMeasuresSelection(selectedMeasures)
                    focusRequester.requestFocus()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false },
            ) {
                Text(
                    "- Bar",
                    fontSize = 11.sp,
                    color = if (hasSelectedBars) toolbarText else toolbarText.copy(alpha = 0.35f),
                )
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
            val clefTargetLoc = targetClefLocation()
            val targetClefStaffMeasures = score.staffMeasures(clefTargetLoc.staff)
            val activeTargetClef = ScoreEditing.effectiveClefAt(
                measures = targetClefStaffMeasures,
                initialClef = score.staffClef(clefTargetLoc.staff),
                measureIdx = clefTargetLoc.measureIdx,
                itemIdx = clefTargetLoc.itemIdx,
            )
            val canRemoveClefChange = (clefTargetLoc.measureIdx > 0 || clefTargetLoc.itemIdx > 0) &&
                ScoreEditing.hasExplicitClefChangeAt(targetClefStaffMeasures, clefTargetLoc)
            val selectedSlotItems = selectedLocations
                .mapNotNull { loc -> ScoreEditing.itemAt(score.staffMeasures(loc.staff), loc) }
                .filter { it.soundingPitches().isNotEmpty() }
            val staccatoActive = if (selectedSlotItems.isNotEmpty()) {
                selectedSlotItems.all { it.noteStaccato() }
            } else {
                pendingStaccato
            }
            val selectedPitchesForPalette = selectedLocations.flatMap { loc ->
                when (val item = ScoreEditing.itemAt(score.staffMeasures(loc.staff), loc)) {
                    is ScoreItem.Note -> listOf(item.pitch)
                    is ScoreItem.Chord -> if (loc.chordNoteIdx != null) {
                        listOfNotNull(item.pitches.getOrNull(loc.chordNoteIdx))
                    } else {
                        item.pitches
                    }
                    else -> emptyList()
                }
            }
            val activeAlter = if (selectedPitchesForPalette.isNotEmpty()) {
                selectedPitchesForPalette.map { it.alter }.distinct().singleOrNull()
            } else {
                pendingAccidental?.alter
            }
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
                activeDynamic = activeDynamic,
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
                        activeAlter = activeAlter,
                        staccatoActive = staccatoActive,
                        activeTupletRatio = module.stepRecordTupletRatio,
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
                        onApplyAccidental = { acc ->
                            applyAccidental(acc)
                            focusRequester.requestFocus()
                        },
                        onToggleStaccato = {
                            toggleStaccato()
                            focusRequester.requestFocus()
                        },
                        onSelectTuplet = { ratio ->
                            applyTupletRatio(ratio)
                            openPalette = null
                            focusRequester.requestFocus()
                        },
                        onOpenCustomTuplet = {
                            openPalette = null
                            customTupletActualText = (module.stepRecordTupletRatio?.first ?: 3).toString()
                            customTupletNormalText = (module.stepRecordTupletRatio?.second ?: 2).toString()
                            customTupletDialogOpen = true
                        },
                        onSelectBeamTool = {
                            module.exitStepRecording()
                            openPalette = null
                            pendingAccidental = null
                            editorMode = ScoreEditMode.BEAM
                            module.placeCursor(null)
                            hoverPos = null
                            focusRequester.requestFocus()
                        },
                        onSelectTieTool = {
                            module.exitStepRecording()
                            openPalette = null
                            pendingAccidental = null
                            editorMode = ScoreEditMode.TIE
                            module.placeCursor(null)
                            hoverPos = null
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
                } else if (kind == openPalette && kind == ScorePaletteKind.DYNAMICS) {
                    DynamicsPalettePopup(
                        offsetX = popupOffsetX,
                        activeDynamic = activeDynamic,
                        isDark = isDark,
                        toolbarText = toolbarText,
                        accentColor = accentColor,
                        borderMuted = borderMuted,
                        musicFontFamily = musicFontFamily,
                        onSelectDynamic = { chosenMark ->
                            activeDynamic = chosenMark
                            module.exitStepRecording()
                            module.placeCursor(null)
                            editorMode = ScoreEditMode.DYNAMIC
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
                    val sidePanelWidthDp = maxOf(104.dp, with(density) { (staffSpacePx * 8.2f).toDp() })
                    val scoreViewportWidth = (viewportWidth - sidePanelWidthDp - 1.dp).coerceAtLeast(160.dp)
                    val minWidthPx = with(density) { scoreViewportWidth.toPx() }.coerceAtLeast(480f)
                    val instrumentsSnapshot = score.instruments.toList()
                    val activeTupletRatio = module.stepRecordTupletRatio
                    val layoutTupletRatio = if (!module.isStepRecording && editorMode == ScoreEditMode.ENTRY) {
                        activeTupletRatio
                    } else if (module.isStepRecording) {
                        activeTupletRatio
                    } else {
                        null
                    }
                    val layout = remember(
                        instrumentsSnapshot,
                        score.clef,
                        score.keySignature,
                        score.timeSignature,
                        staffSpacePx,
                        minWidthPx,
                        layoutTupletRatio,
                    ) {
                        ScoreRenderer.computeLayout(
                            score = score,
                            staffSpacePx = staffSpacePx,
                            minWidthPx = minWidthPx,
                            activeTupletRatio = layoutTupletRatio,
                            includeInstrumentLabelGutter = false,
                            measureGlyphWidth = measureGlyphWidth,
                        )
                    }
                    val currentLayout by rememberUpdatedState(layout)
                    val currentStaffSpacePx by rememberUpdatedState(staffSpacePx)

                    LaunchedEffect(module.activeItemIndex, module.activeItemsByStaff, module.isPlaying) {
                        if (!module.isPlaying) return@LaunchedEffect
                        val activePlaced = currentLayout.staves.asSequence()
                            .mapNotNull { ps ->
                                val idx = module.activeItemsByStaff[ps.staffId]
                                    ?: if (ps.staffId == ScoreStaffId(0, 0)) module.activeItemIndex else null
                                if (idx == null) null
                                else ps.placedMeasures.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.globalIdx == idx }
                            }
                            .firstOrNull() ?: return@LaunchedEffect
                        val vpW = with(density) { scoreViewportWidth.toPx() }
                        val curScroll = scrollState.value.toFloat()
                        if (activePlaced.boxRight > curScroll + vpW - 24f || activePlaced.boxLeft < curScroll) {
                            val target = (activePlaced.boxLeft - vpW * 0.2f).roundToInt()
                                .coerceIn(0, scrollState.maxValue)
                            scrollState.animateScrollTo(target)
                        }
                    }

                    LaunchedEffect(module.cursorPosition, module.isStepRecording) {
                        val cursor = module.cursorPosition ?: return@LaunchedEffect
                        if (!module.isStepRecording) return@LaunchedEffect
                        val cursorX = currentLayout.cursorXFor(cursor) ?: return@LaunchedEffect
                        val vpW = with(density) { scoreViewportWidth.toPx() }
                        val curScroll = scrollState.value.toFloat()
                        if (cursorX > curScroll + vpW - 36f || cursorX < curScroll + 24f) {
                            val target = (cursorX - vpW * 0.7f).roundToInt()
                                .coerceIn(0, scrollState.maxValue)
                            scrollState.animateScrollTo(target)
                        }
                    }

                    LaunchedEffect(pendingScrollMeasures, layout) {
                        val targetMeasures = pendingScrollMeasures
                        if (targetMeasures.isEmpty()) return@LaunchedEffect
                        val firstM = targetMeasures.min().coerceAtLeast(0)
                        val lastM = targetMeasures.max().coerceAtLeast(0)
                        val lastPm = layout.placedMeasures.getOrNull(lastM) ?: return@LaunchedEffect
                        val firstPm = layout.placedMeasures.getOrNull(firstM) ?: lastPm
                        pendingScrollMeasures = emptySet()
                        withFrameNanos { }
                        val vpW = with(density) { scoreViewportWidth.toPx() }
                        val curScroll = scrollState.value.toFloat()
                        if (lastPm.barlineX > curScroll + vpW - 24f || firstPm.startX < curScroll) {
                            val desired = if (lastPm.barlineX - firstPm.startX <= vpW - 48f) {
                                (lastPm.barlineX - vpW + 48f).coerceAtLeast(firstPm.startX - 24f)
                            } else {
                                firstPm.startX - 24f
                            }
                            val target = desired.roundToInt().coerceIn(0, scrollState.maxValue)
                            scrollState.animateScrollTo(target)
                        }
                    }

            val canvasWidthDp = maxOf(
                scoreViewportWidth,
                with(density) { layout.totalWidthPx.toDp() },
            )
            val canvasHeightDp = maxOf(
                viewportHeight,
                with(density) { layout.totalHeightPx.toDp() },
            )
            val renderColors = ScoreRenderColors.forTheme(isDark)

            val cursorTupletText = activeTupletRatio?.let { ratio ->
                ScoreTuplet("cursor", ratio.first, ratio.second).displayText(score.timeSignature)
            }
            val activeCursor = when {
                dragState is ScoreDragState.PitchDrag -> moveCursor()
                hoveringInstrument || hoveringTimeSig ||
                    (editorMode == ScoreEditMode.SELECT && !module.isStepRecording && (hoveringClef || hoveringDynamic)) ->
                    PointerIcon.Hand
                !module.isStepRecording && editorMode == ScoreEditMode.ENTRY ->
                    scoreSymbolCursor(entryDuration, entryIsRest, entryIsDotted, isDark, cursorTupletText)
                !module.isStepRecording && editorMode == ScoreEditMode.DYNAMIC ->
                    scoreDynamicCursor(activeDynamic.label, isDark)
                !module.isStepRecording && editorMode == ScoreEditMode.BEAM ->
                    scoreBeamCursor(isDark)
                !module.isStepRecording && editorMode == ScoreEditMode.TIE ->
                    scoreTieCursor(isDark)
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
                activeTupletRatio,
            ) {
                if (module.isStepRecording || editorMode != ScoreEditMode.ENTRY || dragState != null || editingTimeSig != null) {
                    return@remember null
                }
                val pos = hoverPos ?: return@remember null
                if (pos.x < layout.headerEndX - layout.staffSpacePx * 0.5f) return@remember null
                if (layout.instrumentGroupAt(pos.x, pos.y) != null) return@remember null
                if (layout.timeSignatureAt(pos.x, pos.y) != null) return@remember null
                if (layout.clefChangeAt(pos.x, pos.y) != null) return@remember null
                val hoverStaff = layout.staffAtY(pos.y) ?: return@remember null
                val step = hoverStaff.stepAtY(pos.y)
                val snappedX = if (!entryIsRest) {
                    layout.itemColumnAt(pos.x, pos.y)?.x ?: (pos.x - layout.staffSpacePx * 0.65f)
                } else {
                    pos.x - layout.staffSpacePx * 0.65f
                }
                val tupletText = activeTupletRatio?.let { ratio ->
                    val target = layout.insertionTargetAt(pos.x, pos.y)
                    val targetPm = target?.let { hoverStaff.placedMeasures.getOrNull(it.measureIdx) }
                    val prevIdx = (target?.itemIdx ?: 0) - 1
                    val joinsIncomplete = targetPm?.tupletGroups?.any { grp ->
                        !grp.isFull && prevIdx in grp.itemIndices &&
                            grp.tuplet.actualCount == ratio.first && grp.tuplet.normalCount == ratio.second
                    } == true
                    if (joinsIncomplete) null
                    else ScoreTuplet("preview", ratio.first, ratio.second).displayText(
                        targetPm?.activeTimeSignature ?: score.timeSignature
                    )
                }
                ScoreGhostPreview(
                    x = snappedX,
                    staffStep = step,
                    duration = entryDuration,
                    isRest = entryIsRest,
                    isDotted = entryIsDotted,
                    accidental = if (entryIsRest) null else pendingAccidental,
                    isStaccato = if (entryIsRest) false else pendingStaccato,
                    staff = hoverStaff.staffId,
                    tupletText = tupletText,
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
            val displayedSelectedDynamics = if (rubberBandState != null && rubberBand != null) {
                val dynHits = layout.dynamicsInRect(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.bottom)
                if (rubberBandState.additive) rubberBandState.baseDynamics + dynHits else dynHits
            } else {
                selectedDynamics
            }
            val displayedSelectedMeasures = if (rubberBandState != null && rubberBand != null) {
                val isTiny = abs(rubberBandState.end.x - rubberBandState.start.x) < 4f &&
                    abs(rubberBandState.end.y - rubberBandState.start.y) < 4f
                if (isTiny) {
                    selectedMeasures
                } else {
                    val liveHits = layout.itemsInRect(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.bottom)
                    val liveDynHits = layout.dynamicsInRect(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.bottom)
                    val liveMeasures = layout.measuresInRect(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.bottom)
                    if (liveMeasures.size > 1 || (liveHits.isEmpty() && liveDynHits.isEmpty())) {
                        if (rubberBandState.additive) rubberBandState.baseMeasures + liveMeasures else liveMeasures
                    } else {
                        emptySet()
                    }
                }
            } else {
                selectedMeasures
            }

            val effectiveLoopRange = module.effectiveLoopRange(layout.placedMeasures.size)
            Row(modifier = Modifier.fillMaxSize()) {
                ScoreInstrumentSidePanel(
                    layout = layout,
                    staffSpacePx = staffSpacePx,
                    panelWidth = sidePanelWidthDp,
                    canvasHeight = canvasHeightDp,
                    verticalScrollOffsetPx = verticalScrollState.value,
                    loopMode = module.loopMode,
                    loopRange = effectiveLoopRange,
                    recordArmedInstrumentIdx = module.recordArmedInstrumentIdx,
                    isDark = isDark,
                    bgColor = bgColor,
                    borderMuted = borderMuted,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    colors = renderColors,
                    onToggleLoopMode = {
                        module.toggleLoopMode()
                        FileState.markDirty()
                        focusRequester.requestFocus()
                    },
                    onEditInstrument = { idx, part ->
                        activeStaff = ScoreStaffId(idx, 0)
                        instrumentDialog = InstrumentDialogState(
                            editingIndex = idx,
                            name = part.name,
                            instrumentType = part.instrumentType,
                            lowPitchText = ScoreInstrumentPart.formatMidiPitch(part.minMidiPitch),
                            highPitchText = ScoreInstrumentPart.formatMidiPitch(part.maxMidiPitch),
                            selectedPreset = InstrumentPreset.entries.firstOrNull { preset ->
                                preset.displayName == part.name &&
                                    preset.instrumentType == part.instrumentType &&
                                    preset.minMidiPitch == part.minMidiPitch &&
                                    preset.maxMidiPitch == part.maxMidiPitch
                            },
                        )
                    },
                    onToggleRecordArm = { idx ->
                        module.toggleRecordArm(idx)
                        focusRequester.requestFocus()
                    },
                    onReorderInstrument = { fromIdx, toIdx ->
                        reorderInstrument(fromIdx, toIdx)
                    },
                    onAddInstrument = {
                        val nextNum = score.instruments.size + 1
                        instrumentDialog = InstrumentDialogState(
                            editingIndex = null,
                            name = "Synth $nextNum",
                            instrumentType = ScoreInstrumentPart.DEFAULT_TYPE,
                            lowPitchText = ScoreInstrumentPart.formatMidiPitch(ScoreInstrumentPart.DEFAULT_MIN_MIDI),
                            highPitchText = ScoreInstrumentPart.formatMidiPitch(ScoreInstrumentPart.DEFAULT_MAX_MIDI),
                            selectedPreset = InstrumentPreset.SYNTH,
                        )
                    },
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(borderMuted)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    ScoreLoopBar(
                        layout = layout,
                        scrollOffsetPx = scrollState.value,
                        loopMode = module.loopMode,
                        loopRange = effectiveLoopRange,
                        isDark = isDark,
                        borderMuted = borderMuted,
                        toolbarText = toolbarText,
                        textMeasurer = textMeasurer,
                        showLeftGutterLabel = false,
                        onLoopRangeChange = { startIdx, endIdx ->
                            module.setLoopRange(startIdx, endIdx)
                            FileState.markDirty()
                        },
                        onToggleLoopMode = {
                            module.toggleLoopMode()
                            FileState.markDirty()
                            focusRequester.requestFocus()
                        },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(verticalScrollState)
                            .horizontalScroll(scrollState)
                    ) {
                Box(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .height(canvasHeightDp)
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
                                            hoveringInstrument = pos != null && currentLayout.instrumentGroupAt(pos.x, pos.y) != null
                                            hoveringTimeSig = pos != null && currentLayout.timeSignatureAt(pos.x, pos.y) != null
                                            hoveringClef = pos != null && currentLayout.clefChangeAt(pos.x, pos.y) != null
                                            hoveringDynamic = pos != null && currentLayout.dynamicAt(pos.x, pos.y) != null
                                            hoveringItem = pos != null && currentLayout.itemAt(pos.x, pos.y) != null
                                        }
                                        PointerEventType.Exit -> {
                                            hoverPos = null
                                            hoveringInstrument = false
                                            hoveringTimeSig = false
                                            hoveringClef = false
                                            hoveringDynamic = false
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
                                lyt.staffAtY(downPos.y)?.let { activeStaff = it.staffId }

                                val hitInstrument = lyt.instrumentGroupAt(downPos.x, downPos.y)
                                if (hitInstrument != null && !isShift) {
                                    val part = hitInstrument.instrument
                                    activeStaff = ScoreStaffId(hitInstrument.instrumentIdx, 0)
                                    instrumentDialog = InstrumentDialogState(
                                        editingIndex = hitInstrument.instrumentIdx,
                                        name = part.name,
                                        instrumentType = part.instrumentType,
                                        lowPitchText = ScoreInstrumentPart.formatMidiPitch(part.minMidiPitch),
                                        highPitchText = ScoreInstrumentPart.formatMidiPitch(part.maxMidiPitch),
                                        selectedPreset = InstrumentPreset.entries.firstOrNull { preset ->
                                            preset.displayName == part.name &&
                                                preset.instrumentType == part.instrumentType &&
                                                preset.minMidiPitch == part.minMidiPitch &&
                                                preset.maxMidiPitch == part.maxMidiPitch
                                        },
                                    )
                                    return@awaitEachGesture
                                }

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
                                        anchorY = lyt.yForStep(step, loc.staff),
                                    )
                                }

                                if (module.isStepRecording || editorMode == ScoreEditMode.SELECT) {
                                    val hitClef = if (hit == null && !module.isStepRecording) {
                                        lyt.clefChangeAt(downPos.x, downPos.y)
                                    } else {
                                        null
                                    }
                                    if (hitClef != null) {
                                        activeStaff = hitClef.staff
                                        module.placeCursor(null)
                                        selectedClefChange = hitClef.location
                                        selectedDynamics = emptySet()
                                        selectedLocations = emptySet()
                                        selectedMeasures = emptySet()
                                        activePitchText = null
                                        activeChordName = null
                                        return@awaitEachGesture
                                    }
                                    selectedClefChange = null
                                    val hitDyn = if (hit == null && !module.isStepRecording) {
                                        lyt.dynamicAt(downPos.x, downPos.y)
                                    } else {
                                        null
                                    }
                                    if (hitDyn != null) {
                                        activeStaff = hitDyn.staff
                                        module.placeCursor(null)
                                        if (isShift) {
                                            selectedDynamics = if (hitDyn.location in selectedDynamics) {
                                                selectedDynamics - hitDyn.location
                                            } else {
                                                selectedDynamics + hitDyn.location
                                            }
                                        } else {
                                            selectedDynamics = setOf(hitDyn.location)
                                            selectedLocations = emptySet()
                                            selectedMeasures = emptySet()
                                            activePitchText = null
                                            activeChordName = null
                                        }
                                        return@awaitEachGesture
                                    }
                                    if (hit != null && !module.isStepRecording) {
                                        activeStaff = hit.staff
                                        if (!isShift) {
                                            selectedDynamics = emptySet()
                                        }
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
                                            val staffMeasuresCopy = score.staffMeasures(clickedLoc.staff).toList()
                                            when (val item = hit.item) {
                                                is ScoreItem.Note -> {
                                                    module.previewPitch(
                                                        item.pitch.midiPitch.toDouble(),
                                                        isStaccato = item.isStaccato,
                                                        instrumentIdx = clickedLoc.staff.instrumentIdx,
                                                    )
                                                    refreshPitchDisplayFromSelection()
                                                    val step = hit.staffStep ?: hit.effectiveClef.staffStepFromBottom(item.pitch)
                                                    beginNoteDrag(clickedLoc, step, item.pitch.alter, staffMeasuresCopy, hit.x)
                                                }
                                                is ScoreItem.Chord -> {
                                                    if (chordNote != null) {
                                                        module.previewPitch(
                                                            chordNote.pitch.midiPitch.toDouble(),
                                                            isStaccato = item.isStaccato,
                                                            instrumentIdx = clickedLoc.staff.instrumentIdx,
                                                        )
                                                        refreshPitchDisplayFromSelection()
                                                        beginNoteDrag(
                                                            clickedLoc,
                                                            chordNote.staffStep,
                                                            chordNote.pitch.alter,
                                                            staffMeasuresCopy,
                                                            chordNote.headX,
                                                        )
                                                    } else {
                                                        item.pitches.firstOrNull()?.let {
                                                            module.previewPitch(
                                                                it.midiPitch.toDouble(),
                                                                isStaccato = item.isStaccato,
                                                                instrumentIdx = clickedLoc.staff.instrumentIdx,
                                                            )
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
                                        val cPos = lyt.cursorPositionAt(downPos.x, downPos.y)
                                        cPos?.let { activeStaff = it.staff }
                                        module.placeCursor(cPos)
                                    } else if (module.isStepRecording && hit != null) {
                                        selectedLocations = emptySet()
                                        selectedMeasures = emptySet()
                                        activePitchText = null
                                        activeChordName = null
                                        val cPos = lyt.cursorPositionAt(downPos.x, downPos.y)
                                        cPos?.let { activeStaff = it.staff }
                                        module.placeCursor(cPos)
                                    } else {
                                        if (!isShift) {
                                            selectedDynamics = emptySet()
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
                                            baseDynamics = selectedDynamics,
                                        )
                                    }
                                } else if (editorMode == ScoreEditMode.DYNAMIC) {
                                    val target = lyt.dynamicTargetAt(downPos.x, downPos.y)
                                    if (target != null) {
                                        activeStaff = target.first.staff
                                        applyDynamic(target.first, activeDynamic, target.second)
                                    }
                                    return@awaitEachGesture
                                } else if (editorMode == ScoreEditMode.BEAM) {
                                    if (hit != null) {
                                        activeStaff = hit.staff
                                        toggleBeamAt(hit.location.slotLocation())
                                    }
                                    return@awaitEachGesture
                                } else if (editorMode == ScoreEditMode.TIE) {
                                    if (hit != null) {
                                        activeStaff = hit.staff
                                        val chordNote = lyt.chordNoteAt(hit, downPos.x, downPos.y)
                                        val targetLoc = if (chordNote != null) {
                                            hit.location.copy(chordNoteIdx = chordNote.noteIdx)
                                        } else {
                                            hit.location.slotLocation()
                                        }
                                        toggleTieAt(targetLoc)
                                    }
                                    return@awaitEachGesture
                                } else {
                                    // ENTRY mode
                                    selectedDynamics = emptySet()
                                    selectedClefChange = null
                                    val colItem = if (!entryIsRest) lyt.itemColumnAt(downPos.x, downPos.y) else null
                                    if (hit != null && (entryIsRest || hit.item is ScoreItem.Rest)) {
                                        val hitStaff = hit.staff
                                        activeStaff = hitStaff
                                        val beforeMeasures = score.staffMeasures(hitStaff).toList()
                                        val isDifferentKindOrDuration =
                                            (entryIsRest && hit.item !is ScoreItem.Rest) ||
                                                (!entryIsRest && hit.item is ScoreItem.Rest) ||
                                                (hit.item.duration != entryDuration) ||
                                                (hit.item.isDotted != entryIsDotted)

                                        if (isDifferentKindOrDuration) {
                                            if (entryIsRest) {
                                                val newRest = ScoreItem.Rest(
                                                    entryDuration,
                                                    isDotted = entryIsDotted,
                                                    tuplet = hit.item.tuplet,
                                                )
                                                val after = replaceEntryItem(beforeMeasures, hit.location, newRest)
                                                selectedLocations = setOf(hit.location)
                                                activePitchText = null
                                                activeChordName = null
                                                postCommand(ReplaceScoreMeasuresCommand(score, beforeMeasures, after, staff = hitStaff))
                                            } else {
                                                val step = lyt.stepAtY(downPos.y, hitStaff)
                                                val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                    beforeMeasures,
                                                    score.keySignature,
                                                    hit.location.measureIdx,
                                                )
                                                val measureClef = ScoreEditing.effectiveClefAt(
                                                    beforeMeasures,
                                                    score.staffClef(hitStaff),
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
                                                    tuplet = hit.item.tuplet,
                                                )
                                                pendingAccidental = null
                                                val after = replaceEntryItem(beforeMeasures, hit.location, newNote)
                                                score.replaceStaffMeasures(hitStaff, after)
                                                selectedLocations = setOf(hit.location)
                                                refreshPitchDisplayFromSelection(hitStaff, after, selectedLocations)
                                                module.previewPitch(
                                                    pitch.midiPitch.toDouble(),
                                                    isStaccato = newNote.isStaccato,
                                                    instrumentIdx = hitStaff.instrumentIdx,
                                                )
                                                beginNoteDrag(hit.location, step, pitch.alter, beforeMeasures, hit.x)
                                            }
                                        } else {
                                            selectedLocations = setOf(hit.location)
                                            selectedMeasures = emptySet()
                                            activePitchText = null
                                            activeChordName = null
                                        }
                                    } else if (colItem != null) {
                                        val colStaff = colItem.staff
                                        activeStaff = colStaff
                                        val beforeMeasures = score.staffMeasures(colStaff).toList()
                                        val step = lyt.stepAtY(downPos.y, colStaff)
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
                                                    tuplet = colItem.item.tuplet,
                                                )
                                                pendingAccidental = null
                                                val after = replaceEntryItem(beforeMeasures, colItem.location, newNote)
                                                score.replaceStaffMeasures(colStaff, after)
                                                selectedLocations = setOf(colItem.location)
                                                refreshPitchDisplayFromSelection(colStaff, after, selectedLocations)
                                                module.previewPitch(
                                                    pitch.midiPitch.toDouble(),
                                                    isStaccato = newNote.isStaccato,
                                                    instrumentIdx = colStaff.instrumentIdx,
                                                )
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
                                                    instrumentIdx = colStaff.instrumentIdx,
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
                                            val (after, newSlotLoc) = ScoreEditing.addPitchToSlot(beforeMeasures, colItem.location, pitch)
                                            val newLoc = newSlotLoc.copy(staff = colStaff)
                                            score.replaceStaffMeasures(colStaff, after)
                                            selectedLocations = setOf(newLoc)
                                            selectedMeasures = emptySet()
                                            refreshPitchDisplayFromSelection(colStaff, after, selectedLocations)
                                            module.previewPitch(
                                                pitch.midiPitch.toDouble(),
                                                isStaccato = colItem.item.noteStaccato(),
                                                instrumentIdx = colStaff.instrumentIdx,
                                            )
                                            beginNoteDrag(newLoc, step, pitch.alter, beforeMeasures, colItem.x)
                                        }
                                    } else {
                                        val target = lyt.insertionTargetAt(downPos.x, downPos.y)
                                        if (target != null) {
                                            val targetStaff = target.staff
                                            activeStaff = targetStaff
                                            val beforeMeasures = score.staffMeasures(targetStaff).toList()
                                            val clickedPm = lyt.staves
                                                .firstOrNull { it.staffId == targetStaff }
                                                ?.placedMeasures
                                                ?.getOrNull(target.measureIdx)
                                            val joinPrecedingIncomplete = clickedPm?.tupletGroups?.any { tg ->
                                                !tg.isFull &&
                                                    target.itemIdx == tg.itemIndices.last() + 1 &&
                                                    downPos.x <= tg.endX + sp * 0.35f
                                            } == true
                                            val insertedTuplet = module.resolveTupletForInsertion(
                                                measures = beforeMeasures,
                                                measureIdx = target.measureIdx,
                                                insertIdx = target.itemIdx,
                                                baseTicks = entryDuration.totalTicks(entryIsDotted),
                                                joinPrecedingIncomplete = joinPrecedingIncomplete,
                                            )
                                            if (entryIsRest) {
                                                val newRest = ScoreItem.Rest(
                                                    entryDuration,
                                                    isDotted = entryIsDotted,
                                                    tuplet = insertedTuplet,
                                                )
                                                val (after, insertedLoc) = insertEntryItem(
                                                    measures = beforeMeasures,
                                                    measureIdx = target.measureIdx,
                                                    itemIdx = target.itemIdx,
                                                    item = newRest,
                                                    appendNewMeasure = target.appendNewMeasure,
                                                    shiftClefAtIndex = !target.afterClefChange,
                                                )
                                                val newLoc = insertedLoc.copy(staff = targetStaff)
                                                selectedLocations = setOf(newLoc)
                                                activePitchText = null
                                                activeChordName = null
                                                postCommand(ReplaceScoreMeasuresCommand(score, beforeMeasures, after, staff = targetStaff))
                                            } else {
                                                val step = lyt.stepAtY(downPos.y, targetStaff)
                                                val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                    beforeMeasures,
                                                    score.keySignature,
                                                    target.measureIdx,
                                                )
                                                val effectiveClef = lyt.effectiveClefAtX(downPos.x, downPos.y)
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
                                                    tuplet = insertedTuplet,
                                                )
                                                pendingAccidental = null
                                                val (after, insertedLoc) = insertEntryItem(
                                                    measures = beforeMeasures,
                                                    measureIdx = target.measureIdx,
                                                    itemIdx = target.itemIdx,
                                                    item = newNote,
                                                    appendNewMeasure = target.appendNewMeasure,
                                                    shiftClefAtIndex = !target.afterClefChange,
                                                )
                                                val newLoc = insertedLoc.copy(staff = targetStaff)
                                                score.replaceStaffMeasures(targetStaff, after)
                                                selectedLocations = setOf(newLoc)
                                                activePitchText = pitch.pitchDisplay()
                                                activeChordName = null
                                                module.previewPitch(
                                                    pitch.midiPitch.toDouble(),
                                                    isStaccato = newNote.isStaccato,
                                                    instrumentIdx = targetStaff.instrumentIdx,
                                                )
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
                                                val dragStaff = drag.location.staff
                                                val alterOverride = if (newStep == drag.initialStep) drag.initialAlter else null
                                                val currentMeasures = score.staffMeasures(dragStaff).toList()
                                                val measureKey = ScoreEditing.effectiveKeyAtMeasure(
                                                    currentMeasures,
                                                    score.keySignature,
                                                    drag.location.measureIdx,
                                                )
                                                val measureClef = ScoreEditing.effectiveClefAt(
                                                    currentMeasures,
                                                    score.staffClef(dragStaff),
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
                                                val trackedWithStaff = trackedLoc.copy(staff = dragStaff)
                                                drag.location = trackedWithStaff
                                                score.replaceStaffMeasures(dragStaff, updated)
                                                selectedLocations = setOf(trackedWithStaff)
                                                drag.anchorY = lyt.yForStep(newStep, dragStaff)
                                                refreshPitchDisplayFromSelection(dragStaff, updated, selectedLocations)
                                                val updatedItem = ScoreEditing.itemAt(updated, trackedWithStaff)
                                                module.previewPitch(
                                                    newPitch.midiPitch.toDouble(),
                                                    isStaccato = updatedItem?.noteStaccato() == true,
                                                    instrumentIdx = dragStaff.instrumentIdx,
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
                                        val dragStaff = drag.location.staff
                                        val afterMeasures = score.staffMeasures(dragStaff).toList()
                                        if (afterMeasures != drag.beforeMeasures) {
                                            val loc = drag.location
                                            postCommand(
                                                ReplaceScoreMeasuresCommand(score, drag.beforeMeasures, afterMeasures, staff = dragStaff) { restored ->
                                                    selectedLocations = if (
                                                        ScoreEditing.pitchAt(restored, loc) != null ||
                                                        (loc.chordNoteIdx == null && ScoreEditing.itemAt(restored, loc) != null)
                                                    ) {
                                                        setOf(loc)
                                                    } else {
                                                        emptySet()
                                                    }
                                                    refreshPitchDisplayFromSelection(dragStaff, restored, selectedLocations)
                                                }
                                            )
                                        }
                                    }
                                    is ScoreDragState.RubberBand -> {
                                        val isClick = abs(drag.end.x - drag.start.x) < 4f &&
                                            abs(drag.end.y - drag.start.y) < 4f
                                        if (isClick) {
                                            val clickedMeasure = lyt.measureAt(drag.start.x, drag.start.y)
                                            if (!drag.additive) {
                                                selectedDynamics = emptySet()
                                                selectedLocations = emptySet()
                                                activePitchText = null
                                                activeChordName = null
                                                selectedMeasures = clickedMeasure?.let(::setOf) ?: emptySet()
                                                module.placeCursor(lyt.cursorPositionAt(drag.start.x, drag.start.y))
                                            } else if (clickedMeasure != null) {
                                                val nextMeasures = if (clickedMeasure in drag.baseMeasures) {
                                                    drag.baseMeasures - clickedMeasure
                                                } else {
                                                    drag.baseMeasures + clickedMeasure
                                                }
                                                selectedMeasures = nextMeasures
                                                selectedDynamics = emptySet()
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
                                            val dynHits = lyt.dynamicsInRect(left, top, right, bottom)
                                            val mHits = lyt.measuresInRect(left, top, right, bottom)
                                            selectedLocations = if (drag.additive) drag.baseSelection + hits else hits
                                            selectedDynamics = if (drag.additive) drag.baseDynamics + dynHits else dynHits
                                            selectedMeasures = if (mHits.size > 1 || (hits.isEmpty() && dynHits.isEmpty())) {
                                                if (drag.additive) drag.baseMeasures + mHits else mHits
                                            } else {
                                                emptySet()
                                            }
                                            if (selectedLocations.isNotEmpty() ||
                                                selectedDynamics.isNotEmpty() ||
                                                selectedMeasures.isNotEmpty()
                                            ) {
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
                        selectedDynamics = displayedSelectedDynamics,
                        ghostPreview = ghostPreview,
                        rubberBand = rubberBand,
                        cursorPosition = module.cursorPosition,
                        isStepRecording = module.isStepRecording,
                        activeItemsByStaff = module.activeItemsByStaff,
                        activeTupletRatio = layoutTupletRatio,
                        recordingMeasureIdx = module.activeRecordingMeasureIdx,
                        recordingInstrumentIdx = if (module.isRealTimeRecording) module.recordArmedInstrumentIdx else null,
                        includeInstrumentLabelGutter = false,
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
            val tupletSuffix = module.stepRecordTupletRatio?.let { (n, m) -> " [${n}:${m} Tuplet]" }.orEmpty()
            val modeSummary = when {
                durationEditState == DurationEditPendingState.AWAITING_DURATION ->
                    "Set Duration: press t/s/e/q/h/w (or d for dotted), Esc to cancel"
                durationEditState == DurationEditPendingState.AWAITING_DURATION_DOTTED ->
                    "Set Dotted Duration: press t/s/e/q/h/w, Esc to cancel"
                durationEditState == DurationEditPendingState.AWAITING_OPTIONAL_DOT ->
                    "Duration: ${durationEditLastDur.displayLabel} (press d for dot)"
                module.isStepRecording -> "Mode: Step Record ($dotPrefix${entryDuration.displayLabel}$tupletSuffix)"
                editorMode == ScoreEditMode.SELECT ->
                    if (entryIsDotted) "Mode: Select (Dot On)" else "Mode: Select"
                editorMode == ScoreEditMode.DYNAMIC ->
                    "Mode: Dynamic (${activeDynamic.label}) — click above/below stave"
                editorMode == ScoreEditMode.BEAM ->
                    "Mode: Beam — click a note to beam/un-beam (Esc to exit)"
                editorMode == ScoreEditMode.TIE ->
                    "Mode: Tie — click a note to tie/untie to next note (Esc to exit)"
                else -> "Mode: $dotPrefix${entryDuration.displayLabel} ${if (entryIsRest) "Rest" else "Note"}$tupletSuffix"
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

            val curInst = score.instrumentAt(resolvedActiveStaff().instrumentIdx)
            if (curInst != null) {
                Spacer(modifier = Modifier.width(8.dp))
                val rangeStr = "${ScoreInstrumentPart.formatMidiPitch(curInst.minMidiPitch)}–${ScoreInstrumentPart.formatMidiPitch(curInst.maxMidiPitch)}"
                Text(
                    text = "${curInst.name} ($rangeStr)",
                    fontSize = 11.sp,
                    color = toolbarText.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Esc: Select | t s e q h w: Dur | Alt+2..9/n: Tuplet | p/f: Dyn | Shift+B: Beam | d: Dot | Del: Delete",
                fontSize = 10.sp,
                color = toolbarText.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }

        if (customTupletDialogOpen) {
            CustomTupletDialog(
                actualText = customTupletActualText,
                normalText = customTupletNormalText,
                isDark = isDark,
                accentColor = accentColor,
                borderMuted = borderMuted,
                onActualChange = { customTupletActualText = it },
                onNormalChange = { customTupletNormalText = it },
                onPresetClick = { ratio ->
                    customTupletActualText = ratio.first.toString()
                    customTupletNormalText = ratio.second.toString()
                },
                onDismiss = {
                    customTupletDialogOpen = false
                    focusRequester.requestFocus()
                },
                onConfirm = { actual, normal ->
                    customTupletDialogOpen = false
                    if ((selectedLocations.isEmpty() || editorMode != ScoreEditMode.SELECT) &&
                        module.stepRecordTupletRatio == (actual to normal)
                    ) {
                        clearSelection()
                        module.stepRecordTupletRatio = actual to normal
                        if (!module.isStepRecording) {
                            editorMode = ScoreEditMode.ENTRY
                            module.placeCursor(null)
                        }
                    } else {
                        applyTupletRatio(actual to normal)
                    }
                    focusRequester.requestFocus()
                },
            )
        }

        val errorMsg = importErrorMessage
        if (errorMsg != null) {
            AlertDialog(
                onDismissRequest = {
                    importErrorMessage = null
                    focusRequester.requestFocus()
                },
                title = { Text("Import MusicXML Failed") },
                text = { Text(errorMsg) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            importErrorMessage = null
                            focusRequester.requestFocus()
                        }
                    ) {
                        Text("OK")
                    }
                },
            )
        }

        val activeDialog = instrumentDialog
        if (activeDialog != null) {
            InstrumentEditorDialog(
                state = activeDialog,
                canDelete = activeDialog.editingIndex != null && score.instruments.size > 1,
                isDark = isDark,
                accentColor = accentColor,
                borderMuted = borderMuted,
                onStateChange = { instrumentDialog = it },
                onDismiss = {
                    instrumentDialog = null
                    focusRequester.requestFocus()
                },
                onConfirm = { stateSnapshot ->
                    val minMidi = ScoreInstrumentPart.parsePitchOrMidi(stateSnapshot.lowPitchText)
                        ?: ScoreInstrumentPart.DEFAULT_MIN_MIDI
                    val maxMidi = ScoreInstrumentPart.parsePitchOrMidi(stateSnapshot.highPitchText)
                        ?: ScoreInstrumentPart.DEFAULT_MAX_MIDI
                    val low = minOf(minMidi, maxMidi)
                    val high = maxOf(minMidi, maxMidi)
                    val rec = ScoreInstrumentPart.recommendStaffSetup(low, high)
                    val cleanName = stateSnapshot.name.trim().ifEmpty {
                        "Instrument ${(stateSnapshot.editingIndex ?: score.instruments.size) + 1}"
                    }
                    val beforeInst = score.instruments.toList()
                    val measureCount = score.maxMeasureCount()
                    val afterInst = if (stateSnapshot.editingIndex == null) {
                        val existingJacks = beforeInst.map { it.jackName }
                        val jackName = ScoreInstrumentPart.uniqueJackName(cleanName, existingJacks)
                        var instNum = beforeInst.size + 1
                        while (beforeInst.any { it.id == "inst-$instNum" }) {
                            instNum++
                        }
                        val emptyUpper = score.createSyncedEmptyMeasures(measureCount)
                        val emptyLower = if (rec.staffCount >= 2) score.createSyncedEmptyMeasures(measureCount) else listOf(ScoreMeasure(1, emptyList()))
                        beforeInst + ScoreInstrumentPart(
                            id = "inst-$instNum",
                            name = cleanName,
                            instrumentType = stateSnapshot.instrumentType,
                            minMidiPitch = low,
                            maxMidiPitch = high,
                            staffCount = rec.staffCount,
                            clef = rec.clef,
                            lowerClef = rec.lowerClef,
                            jackName = jackName,
                            measures = emptyUpper,
                            lowerMeasures = emptyLower,
                        )
                    } else {
                        val editIdx = stateSnapshot.editingIndex
                        beforeInst.mapIndexed { idx, existing ->
                            if (idx != editIdx) {
                                existing
                            } else {
                                val nextJack = if (
                                    editIdx == 0 &&
                                    existing.jackName == ScoreInstrumentPart.DEFAULT_JACK_NAME &&
                                    cleanName == existing.name
                                ) {
                                    ScoreInstrumentPart.DEFAULT_JACK_NAME
                                } else {
                                    val otherJacks = beforeInst.filterIndexed { i, _ -> i != editIdx }.map { it.jackName }
                                    ScoreInstrumentPart.uniqueJackName(cleanName, otherJacks)
                                }
                                val nextLowerMeasures = if (rec.staffCount >= 2 && existing.staffCount < 2) {
                                    score.createSyncedEmptyMeasures(measureCount)
                                } else {
                                    existing.lowerMeasures
                                }
                                val clefChangedToRec = (existing.minMidiPitch != low || existing.maxMidiPitch != high) &&
                                    existing.measures.all { it.items.isEmpty() }
                                existing.copy(
                                    name = cleanName,
                                    instrumentType = stateSnapshot.instrumentType,
                                    minMidiPitch = low,
                                    maxMidiPitch = high,
                                    staffCount = rec.staffCount,
                                    clef = if (clefChangedToRec) rec.clef else existing.clef,
                                    lowerClef = if (clefChangedToRec) rec.lowerClef else existing.lowerClef,
                                    jackName = nextJack,
                                    lowerMeasures = nextLowerMeasures,
                                )
                            }
                        }
                    }
                    postCommand(ReplaceScoreInstrumentsCommand(module, beforeInst, afterInst))
                    val targetIdx = stateSnapshot.editingIndex ?: (afterInst.lastIndex)
                    activeStaff = ScoreStaffId(targetIdx.coerceAtLeast(0), 0)
                    instrumentDialog = null
                    focusRequester.requestFocus()
                },
                onDelete = {
                    val editIdx = activeDialog.editingIndex ?: return@InstrumentEditorDialog
                    val beforeInst = score.instruments.toList()
                    if (beforeInst.size > 1 && editIdx in beforeInst.indices) {
                        val afterInst = beforeInst.filterIndexed { idx, _ -> idx != editIdx }
                        selectedLocations = emptySet()
                        selectedDynamics = emptySet()
                        selectedClefChange = null
                        activeStaff = ScoreStaffId(0, 0)
                        module.placeCursor(null)
                        postCommand(ReplaceScoreInstrumentsCommand(module, beforeInst, afterInst))
                    }
                    instrumentDialog = null
                    focusRequester.requestFocus()
                },
            )
        }
    }
}

@Composable
private fun ScoreRecordArmButton(
    isArmed: Boolean,
    isDark: Boolean,
    borderMuted: Color,
    onClick: () -> Unit,
) {
    val activeRed = Color(0xFFE53935)
    val bgColor = when {
        isArmed -> activeRed
        isDark -> Color(0xFF1E293B)
        else -> Color(0xFFF1F5F9)
    }
    val strokeColor = if (isArmed) activeRed else borderMuted
    val textColor = when {
        isArmed -> Color.White
        isDark -> Color(0xFFCBD5E1)
        else -> Color(0xFF475569)
    }
    Box(
        modifier = Modifier
            .size(width = 18.dp, height = 16.dp)
            .background(color = bgColor, shape = RoundedCornerShape(3.dp))
            .border(1.dp, strokeColor, RoundedCornerShape(3.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "R",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

@Composable
private fun InstrumentReorderButton(
    isUp: Boolean,
    enabled: Boolean,
    isDark: Boolean,
    accentColor: Color,
    borderMuted: Color,
    onClick: () -> Unit,
) {
    val bgColor = when {
        !enabled -> if (isDark) Color(0x551E293B) else Color(0x66F1F5F9)
        isDark -> Color(0xFF1E293B)
        else -> Color(0xFFF1F5F9)
    }
    val strokeColor = if (enabled) borderMuted else borderMuted.copy(alpha = 0.4f)
    val arrowColor = when {
        !enabled -> if (isDark) Color(0x5594A3B8) else Color(0x5564748B)
        isDark -> Color(0xFFE2E8F0)
        else -> accentColor
    }
    Box(
        modifier = Modifier
            .size(width = 18.dp, height = 16.dp)
            .background(color = bgColor, shape = RoundedCornerShape(3.dp))
            .border(1.dp, strokeColor, RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (isUp) {
                    moveTo(w * 0.5f, h * 0.15f)
                    lineTo(w * 0.9f, h * 0.85f)
                    lineTo(w * 0.1f, h * 0.85f)
                } else {
                    moveTo(w * 0.1f, h * 0.15f)
                    lineTo(w * 0.9f, h * 0.15f)
                    lineTo(w * 0.5f, h * 0.85f)
                }
                close()
            }
            drawPath(path = path, color = arrowColor)
        }
    }
}

@Composable
private fun InstrumentEditorDialog(
    state: InstrumentDialogState,
    canDelete: Boolean,
    isDark: Boolean,
    accentColor: Color,
    borderMuted: Color,
    onStateChange: (InstrumentDialogState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (InstrumentDialogState) -> Unit,
    onDelete: () -> Unit,
) {
    val fieldBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val mutedText = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)

    val parsedLow = ScoreInstrumentPart.parsePitchOrMidi(state.lowPitchText)
    val parsedHigh = ScoreInstrumentPart.parsePitchOrMidi(state.highPitchText)
    val rangeValid = parsedLow != null && parsedHigh != null
    val effectiveLow = if (parsedLow != null && parsedHigh != null) minOf(parsedLow, parsedHigh) else ScoreInstrumentPart.DEFAULT_MIN_MIDI
    val effectiveHigh = if (parsedLow != null && parsedHigh != null) maxOf(parsedLow, parsedHigh) else ScoreInstrumentPart.DEFAULT_MAX_MIDI
    val recommendation = ScoreInstrumentPart.recommendStaffSetup(effectiveLow, effectiveHigh)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (state.editingIndex == null) "New Instrument" else "Edit Instrument",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Preset", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = mutedText)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InstrumentPreset.entries.forEach { preset ->
                        val isSelected = state.selectedPreset == preset
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.2f) else fieldBg,
                            modifier = Modifier
                                .border(1.dp, if (isSelected) accentColor else borderMuted, RoundedCornerShape(6.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    onStateChange(
                                        state.copy(
                                            selectedPreset = preset,
                                            name = preset.displayName,
                                            instrumentType = preset.instrumentType,
                                            lowPitchText = ScoreInstrumentPart.formatMidiPitch(preset.minMidiPitch),
                                            highPitchText = ScoreInstrumentPart.formatMidiPitch(preset.maxMidiPitch),
                                        )
                                    )
                                }
                        ) {
                            Text(
                                text = preset.displayName,
                                fontSize = 11.sp,
                                color = if (isSelected) accentColor else textColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                Text("Instrument Name", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = mutedText)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(fieldBg, RoundedCornerShape(6.dp))
                        .border(1.dp, borderMuted, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = state.name,
                        onValueChange = { onStateChange(state.copy(name = it, selectedPreset = null)) },
                        singleLine = true,
                        textStyle = TextStyle(color = textColor, fontSize = 13.sp),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text("Note Range (note name like A0, C8 or MIDI 0–127)", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = mutedText)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lowest", fontSize = 10.sp, color = mutedText)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(fieldBg, RoundedCornerShape(6.dp))
                                .border(1.dp, if (parsedLow != null) borderMuted else Color(0xFFEF4444), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            BasicTextField(
                                value = state.lowPitchText,
                                onValueChange = { onStateChange(state.copy(lowPitchText = it, selectedPreset = null)) },
                                singleLine = true,
                                textStyle = TextStyle(color = textColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(accentColor),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text("to", fontSize = 12.sp, color = mutedText, modifier = Modifier.padding(top = 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Highest", fontSize = 10.sp, color = mutedText)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(fieldBg, RoundedCornerShape(6.dp))
                                .border(1.dp, if (parsedHigh != null) borderMuted else Color(0xFFEF4444), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            BasicTextField(
                                value = state.highPitchText,
                                onValueChange = { onStateChange(state.copy(highPitchText = it, selectedPreset = null)) },
                                singleLine = true,
                                textStyle = TextStyle(color = textColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(accentColor),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                val staffDesc = if (recommendation.staffCount >= 2) {
                    "2 staves (${recommendation.clef.label} + ${recommendation.lowerClef.label})"
                } else {
                    "1 staff (${recommendation.clef.label})"
                }
                Text(
                    text = if (rangeValid) {
                        "Range: ${ScoreInstrumentPart.formatMidiPitch(effectiveLow)} ($effectiveLow) – " +
                            "${ScoreInstrumentPart.formatMidiPitch(effectiveHigh)} ($effectiveHigh)  •  $staffDesc"
                    } else {
                        "Enter valid pitch names (e.g. G3, C#4, Bb5) or MIDI numbers (0–127)"
                    },
                    fontSize = 11.sp,
                    color = if (rangeValid) accentColor else Color(0xFFEF4444),
                )

                Text("Instrument Type", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = mutedText)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    InstrumentType.entries.chunked(3).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowTypes.forEach { t ->
                                val isSel = state.instrumentType == t
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSel) accentColor.copy(alpha = 0.2f) else fieldBg,
                                    modifier = Modifier
                                        .border(1.dp, if (isSel) accentColor else borderMuted, RoundedCornerShape(6.dp))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            onStateChange(state.copy(instrumentType = t, selectedPreset = null))
                                        }
                                ) {
                                    Text(
                                        text = t.label,
                                        fontSize = 11.sp,
                                        color = if (isSel) accentColor else textColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "Articulations: ${state.instrumentType.availableArticulations.joinToString(", ")}",
                    fontSize = 10.sp,
                    color = mutedText,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    ) {
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(state) },
                    enabled = rangeValid,
                ) {
                    Text(if (state.editingIndex == null) "Add" else "Save")
                }
            }
        },
    )
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
private fun ScoreToolbarMenu(
    label: String,
    textColor: Color,
    menuContent: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier
                .height(28.dp)
                .focusProperties { canFocus = false },
        ) {
            Text(label, fontSize = 12.sp, color = textColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
            menuContent(dismiss)
        }
    }
}

@Composable
private fun ScoreFileMenu(
    textColor: Color,
    onImportMusicXml: () -> Unit,
    onExportMusicXml: () -> Unit,
) {
    ScoreToolbarMenu(label = "File", textColor = textColor) { dismiss ->
        CompactDropdownMenuItem(
            text = { Text("Import MusicXML...") },
            onClick = {
                dismiss()
                onImportMusicXml()
            },
        )
        CompactDropdownMenuItem(
            text = { Text("Export MusicXML...") },
            onClick = {
                dismiss()
                onExportMusicXml()
            },
        )
    }
}

@Composable
private fun ScoreEditMenu(
    textColor: Color,
    autoFillRests: Boolean,
    onFillWithRests: () -> Unit,
    onToggleAutoFillRests: () -> Unit,
) {
    ScoreToolbarMenu(label = "Edit", textColor = textColor) { dismiss ->
        CompactDropdownMenuItem(
            text = { Text("Fill with Rests") },
            onClick = {
                dismiss()
                onFillWithRests()
            },
        )
        CompactDropdownMenuItem(
            text = {
                val prefix = if (autoFillRests) "✓ " else "   "
                Text("${prefix}Auto Fill Rests")
            },
            onClick = {
                dismiss()
                onToggleAutoFillRests()
            },
        )
    }
}

@Composable
private fun ScoreRecordSettingsMenu(
    recordMode: ScoreRecordMode,
    quantizeGrid: ScoreQuantizeGrid,
    detectTriplets: Boolean,
    latencyMs: Double,
    estimatedOutputLatencyMs: Double,
    textColor: Color,
    accentColor: Color,
    isDark: Boolean,
    onSelectRecordMode: (ScoreRecordMode) -> Unit,
    onSelectQuantizeGrid: (ScoreQuantizeGrid) -> Unit,
    onToggleDetectTriplets: () -> Unit,
    onChangeLatencyMs: (Double) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val roundedLat = (latencyMs * 10.0).roundToInt() / 10.0
    val latText = if (roundedLat == roundedLat.toInt().toDouble()) {
        "${roundedLat.toInt()}ms"
    } else {
        "${roundedLat}ms"
    }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier
                .height(28.dp)
                .focusProperties { canFocus = false },
        ) {
            Text(
                "${recordMode.label} · $latText ▾",
                fontSize = 11.sp,
                color = textColor,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val sectionColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            Text(
                text = "Record Mode",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = sectionColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            for (mode in ScoreRecordMode.entries) {
                CompactDropdownMenuItem(
                    text = {
                        val prefix = if (mode == recordMode) "✓ " else "   "
                        Text("$prefix${mode.label} Measure")
                    },
                    onClick = {
                        expanded = false
                        onSelectRecordMode(mode)
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text = "Quantize",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = sectionColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            for (grid in ScoreQuantizeGrid.entries) {
                CompactDropdownMenuItem(
                    text = {
                        val prefix = if (grid == quantizeGrid) "✓ " else "   "
                        Text("$prefix${grid.label} Note")
                    },
                    onClick = {
                        expanded = false
                        onSelectQuantizeGrid(grid)
                    },
                )
            }
            CompactDropdownMenuItem(
                text = {
                    val prefix = if (detectTriplets) "✓ " else "   "
                    Text("${prefix}Detect Triplets")
                },
                onClick = {
                    expanded = false
                    onToggleDetectTriplets()
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text = "Audio Latency (subtracted)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = sectionColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SmallLatencyButton("-10", accentColor, isDark) { onChangeLatencyMs(latencyMs - 10.0) }
                SmallLatencyButton("-2", accentColor, isDark) { onChangeLatencyMs(latencyMs - 2.0) }
                Text(
                    text = latText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                SmallLatencyButton("+2", accentColor, isDark) { onChangeLatencyMs(latencyMs + 2.0) }
                SmallLatencyButton("+10", accentColor, isDark) { onChangeLatencyMs(latencyMs + 10.0) }
            }
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SmallLatencyButton("0 ms", accentColor, isDark) { onChangeLatencyMs(0.0) }
                val estRounded = (estimatedOutputLatencyMs * 10.0).roundToInt() / 10.0
                SmallLatencyButton("3/4 RT (${estRounded}ms)", accentColor, isDark) {
                    onChangeLatencyMs(estimatedOutputLatencyMs)
                }
            }
        }
    }
}

@Composable
private fun SmallLatencyButton(
    label: String,
    accentColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val fg = if (isDark) Color(0xFFE2E8F0) else accentColor
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(3.dp))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium)
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
private fun CustomTupletDialog(
    actualText: String,
    normalText: String,
    isDark: Boolean,
    accentColor: Color,
    borderMuted: Color,
    onActualChange: (String) -> Unit,
    onNormalChange: (String) -> Unit,
    onPresetClick: (Pair<Int, Int>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val dialogBg = if (isDark) Color(0xFF1E223B) else Color(0xFFFFFFFF)
    val fieldBg = if (isDark) Color(0xFF111428) else Color(0xFFF1F5F9)
    val textColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val mutedText = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)

    val actualVal = actualText.trim().toIntOrNull()?.takeIf { it in 2..32 }
    val normalVal = normalText.trim().toIntOrNull()?.takeIf { it in 1..32 }
    val valid = actualVal != null && normalVal != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        title = {
            Text(
                text = "Custom Tuplet Ratio",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Fit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .background(fieldBg, RoundedCornerShape(6.dp))
                            .border(1.dp, if (actualVal != null) accentColor else Color(0xFFEF4444), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = actualText,
                            onValueChange = { next ->
                                if (next.length <= 2 && next.all { it.isDigit() }) onActualChange(next)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text("into", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .background(fieldBg, RoundedCornerShape(6.dp))
                            .border(1.dp, if (normalVal != null) accentColor else Color(0xFFEF4444), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = normalText,
                            onValueChange = { next ->
                                if (next.length <= 2 && next.all { it.isDigit() }) onNormalChange(next)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text("Common Ratios", fontSize = 11.sp, color = mutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScoreTuplet.PRESETS.forEach { preset ->
                        val isSel = actualVal == preset.first && normalVal == preset.second
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (isSel) accentColor.copy(alpha = 0.2f) else fieldBg,
                            modifier = Modifier
                                .border(1.dp, if (isSel) accentColor else borderMuted, RoundedCornerShape(5.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onPresetClick(preset) }
                        ) {
                            Text(
                                text = "${preset.first}:${preset.second}",
                                fontSize = 11.sp,
                                color = if (isSel) accentColor else textColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = { if (actualVal != null && normalVal != null) onConfirm(actualVal, normalVal) },
                    enabled = valid,
                ) {
                    Text("Apply")
                }
            }
        },
    )
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
    activeDynamic: DynamicMark,
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
                        ((editorMode == ScoreEditMode.ENTRY || editorMode == ScoreEditMode.BEAM || editorMode == ScoreEditMode.TIE) && !isStepRecording) ||
                            isStepRecording
                    ScorePaletteKind.DYNAMICS ->
                        editorMode == ScoreEditMode.DYNAMIC && !isStepRecording
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
                            ScorePaletteKind.DYNAMICS -> {
                                Text(
                                    text = activeDynamic.glyph,
                                    fontFamily = musicFontFamily,
                                    fontSize = 18.sp,
                                    color = contentColor,
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
    activeAlter: Int?,
    staccatoActive: Boolean,
    activeTupletRatio: Pair<Int, Int>?,
    isDark: Boolean,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    onSelectOption: (NoteDuration, Boolean) -> Unit,
    onToggleDot: () -> Unit,
    onApplyAccidental: (Accidental) -> Unit,
    onToggleStaccato: () -> Unit,
    onSelectTuplet: (Pair<Int, Int>?) -> Unit,
    onOpenCustomTuplet: () -> Unit,
    onSelectBeamTool: () -> Unit,
    onSelectTieTool: () -> Unit,
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

                // Accidentals row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Accidentals",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = toolbarText,
                        modifier = Modifier.width(62.dp),
                    )
                    ScorePalettes.accidentalOptions.forEach { option ->
                        val selected = activeAlter == option.accidental.alter
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
                                .clickable { onApplyAccidental(option.accidental) }
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = option.glyph,
                                fontFamily = musicFontFamily,
                                fontSize = 15.sp,
                                color = contentColor,
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

                // Articulations row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Articulations",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = toolbarText,
                        modifier = Modifier.width(62.dp),
                    )
                    val staccContentColor = if (staccatoActive) {
                        if (isDark) Color.White else accentColor
                    } else {
                        toolbarText
                    }
                    val staccBg = if (staccatoActive) accentColor.copy(alpha = 0.22f) else tileBg
                    Row(
                        modifier = Modifier
                            .background(staccBg, RoundedCornerShape(5.dp))
                            .border(
                                width = if (staccatoActive) 1.5.dp else 1.dp,
                                color = if (staccatoActive) accentColor else borderMuted,
                                shape = RoundedCornerShape(5.dp),
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onToggleStaccato() }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "·",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = staccContentColor,
                        )
                        Text(
                            text = "Staccato",
                            fontSize = 10.sp,
                            fontWeight = if (staccatoActive) FontWeight.SemiBold else FontWeight.Medium,
                            color = staccContentColor,
                        )
                        Text(
                            text = "1",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (staccatoActive) FontWeight.Bold else FontWeight.Medium,
                            color = staccContentColor.copy(alpha = if (staccatoActive) 1f else 0.8f),
                        )
                    }
                }

                // Tuplets row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Tuplets",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = toolbarText,
                        modifier = Modifier.width(62.dp),
                    )
                    val tupletChipColor = if (isDark) Color(0xFFFF9800) else Color(0xFFE65100)
                    val offSel = activeTupletRatio == null
                    Row(
                        modifier = Modifier
                            .background(if (offSel) accentColor.copy(alpha = 0.2f) else tileBg, RoundedCornerShape(5.dp))
                            .border(
                                width = if (offSel) 1.5.dp else 1.dp,
                                color = if (offSel) accentColor else borderMuted,
                                shape = RoundedCornerShape(5.dp),
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onSelectTuplet(null) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "Off",
                            fontSize = 9.sp,
                            fontWeight = if (offSel) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (offSel && isDark) Color.White else if (offSel) accentColor else toolbarText,
                        )
                    }
                    ScoreTuplet.PRESETS.forEach { preset ->
                        val isSel = activeTupletRatio == preset
                        Row(
                            modifier = Modifier
                                .background(if (isSel) tupletChipColor.copy(alpha = 0.22f) else tileBg, RoundedCornerShape(5.dp))
                                .border(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) tupletChipColor else borderMuted,
                                    shape = RoundedCornerShape(5.dp),
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onSelectTuplet(preset) }
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "${preset.first}:${preset.second}",
                                fontSize = 9.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel && isDark) Color.White else if (isSel) tupletChipColor else toolbarText,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .background(tileBg, RoundedCornerShape(5.dp))
                            .border(1.dp, borderMuted, RoundedCornerShape(5.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onOpenCustomTuplet() }
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "N:M…",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = toolbarText,
                        )
                    }
                }

                // Tools row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Tools",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = toolbarText,
                        modifier = Modifier.width(62.dp),
                    )
                    val beamSelected = editorMode == ScoreEditMode.BEAM && !isStepRecording
                    val beamContentColor = if (beamSelected) {
                        if (isDark) Color.White else accentColor
                    } else {
                        toolbarText
                    }
                    val beamBg = if (beamSelected) accentColor.copy(alpha = 0.22f) else tileBg
                    Row(
                        modifier = Modifier
                            .background(beamBg, RoundedCornerShape(5.dp))
                            .border(
                                width = if (beamSelected) 1.5.dp else 1.dp,
                                color = if (beamSelected) accentColor else borderMuted,
                                shape = RoundedCornerShape(5.dp),
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onSelectBeamTool() }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Beam",
                            fontSize = 10.sp,
                            fontWeight = if (beamSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = beamContentColor,
                        )
                        Text(
                            text = "Shift+B",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (beamSelected) FontWeight.Bold else FontWeight.Medium,
                            color = beamContentColor.copy(alpha = if (beamSelected) 1f else 0.8f),
                        )
                    }
                    val tieSelected = editorMode == ScoreEditMode.TIE && !isStepRecording
                    val tieContentColor = if (tieSelected) {
                        if (isDark) Color.White else accentColor
                    } else {
                        toolbarText
                    }
                    val tieBg = if (tieSelected) accentColor.copy(alpha = 0.22f) else tileBg
                    Row(
                        modifier = Modifier
                            .background(tieBg, RoundedCornerShape(5.dp))
                            .border(
                                width = if (tieSelected) 1.5.dp else 1.dp,
                                color = if (tieSelected) accentColor else borderMuted,
                                shape = RoundedCornerShape(5.dp),
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onSelectTieTool() }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Tie",
                            fontSize = 10.sp,
                            fontWeight = if (tieSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = tieContentColor,
                        )
                        Text(
                            text = "Shift+T",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (tieSelected) FontWeight.Bold else FontWeight.Medium,
                            color = tieContentColor.copy(alpha = if (tieSelected) 1f else 0.8f),
                        )
                    }
                }
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
            modifier = Modifier.width(62.dp),
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
private fun ScoreInstrumentSidePanel(
    layout: ScoreLayout,
    staffSpacePx: Float,
    panelWidth: Dp,
    canvasHeight: Dp,
    verticalScrollOffsetPx: Int,
    loopMode: Boolean,
    loopRange: IntRange,
    recordArmedInstrumentIdx: Int?,
    isDark: Boolean,
    bgColor: Color,
    borderMuted: Color,
    toolbarText: Color,
    accentColor: Color,
    colors: ScoreRenderColors,
    onToggleLoopMode: () -> Unit,
    onEditInstrument: (Int, ScoreInstrumentPart) -> Unit,
    onToggleRecordArm: (Int) -> Unit,
    onReorderInstrument: (Int, Int) -> Unit,
    onAddInstrument: () -> Unit,
) {
    val density = LocalDensity.current
    val loopHeaderBg = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val loopLabelColor = if (loopMode) {
        if (isDark) Color(0xFF81C784) else Color(0xFF15803D)
    } else {
        toolbarText.copy(alpha = 0.72f)
    }
    val rangeLabel = if (loopRange.first == loopRange.last) {
        "Loop m.${loopRange.first + 1}"
    } else {
        "Loop ${loopRange.first + 1}–${loopRange.last + 1}"
    }

    Column(
        modifier = Modifier
            .width(panelWidth)
            .fillMaxHeight()
            .background(bgColor),
    ) {
        // Sticky top-left corner cell aligned with ScoreLoopBar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(loopHeaderBg)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onToggleLoopMode),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = rangeLabel,
                fontSize = 10.sp,
                fontWeight = if (loopMode) FontWeight.SemiBold else FontWeight.Normal,
                color = loopLabelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(borderMuted),
            )
        }

        // Vertically synced instrument rows + '+' add-instrument button (never scrolls horizontally)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .wrapContentHeight(align = Alignment.Top, unbounded = true),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .offset { IntOffset(0, -verticalScrollOffsetPx) },
            ) {
                val lastInstIdx = layout.instrumentGroups.lastIndex
                for (grp in layout.instrumentGroups) {
                    val idx = grp.instrumentIdx
                    val part = grp.instrument
                    val midY = (grp.topStaffY + grp.bottomStaffY) * 0.5f
                    val nameFontSize = with(density) { (staffSpacePx * 1.32f).coerceIn(11f, 16f).toSp() }
                    val rangeFontSize = with(density) { (staffSpacePx * 0.95f).coerceIn(9f, 12f).toSp() }
                    val blockTopY = with(density) { (midY - staffSpacePx * 1.45f).coerceAtLeast(2f).toDp() }
                    val rangeText = "${ScoreInstrumentPart.formatMidiPitch(part.minMidiPitch)}–" +
                        ScoreInstrumentPart.formatMidiPitch(part.maxMidiPitch)

                    Column(
                        modifier = Modifier
                            .offset(y = blockTopY)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onEditInstrument(idx, part) },
                        ) {
                            Text(
                                text = part.name,
                                fontSize = nameFontSize,
                                fontWeight = FontWeight.Medium,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = rangeText,
                                fontSize = rangeFontSize,
                                color = colors.measureNumber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ScoreRecordArmButton(
                                isArmed = recordArmedInstrumentIdx == idx,
                                isDark = isDark,
                                borderMuted = borderMuted,
                                onClick = { onToggleRecordArm(idx) },
                            )
                            if (layout.instrumentGroups.size > 1) {
                                InstrumentReorderButton(
                                    isUp = true,
                                    enabled = idx > 0,
                                    isDark = isDark,
                                    accentColor = accentColor,
                                    borderMuted = borderMuted,
                                    onClick = { onReorderInstrument(idx, idx - 1) },
                                )
                                InstrumentReorderButton(
                                    isUp = false,
                                    enabled = idx < lastInstIdx,
                                    isDark = isDark,
                                    accentColor = accentColor,
                                    borderMuted = borderMuted,
                                    onClick = { onReorderInstrument(idx, idx + 1) },
                                )
                            }
                        }
                    }
                }

                // Pinned '+' button below the last instrument to add a new track/instrument
                val lastBottomY = layout.staves.lastOrNull()?.bottomStaffY ?: layout.bottomStaffY
                val addBtnY = with(density) { (lastBottomY + staffSpacePx * 2.5f).toDp() }
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = addBtnY)
                        .background(
                            color = accentColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onAddInstrument)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else accentColor,
                    )
                }
            }
        }
    }
}

private enum class ScoreLoopDragMode {
    START_HANDLE,
    END_HANDLE,
    MOVE_WINDOW,
    SWEEP_SELECT,
}

@Composable
private fun ScoreLoopBar(
    layout: ScoreLayout,
    scrollOffsetPx: Int,
    loopMode: Boolean,
    loopRange: IntRange,
    isDark: Boolean,
    borderMuted: Color,
    toolbarText: Color,
    textMeasurer: TextMeasurer,
    onLoopRangeChange: (Int, Int) -> Unit,
    onToggleLoopMode: () -> Unit,
    showLeftGutterLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val currentLayout by rememberUpdatedState(layout)
    val currentScrollOffset by rememberUpdatedState(scrollOffsetPx.toFloat())
    val currentLoopRange by rememberUpdatedState(loopRange)
    val currentOnRangeChange by rememberUpdatedState(onLoopRangeChange)
    val currentOnToggleLoopMode by rememberUpdatedState(onToggleLoopMode)

    val barBg = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val trackBg = if (isDark) Color(0xFF1E293B) else Color(0xFFE0E0E0)
    val activeFill = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.45f) else Color(0xFF90CAF9).copy(alpha = 0.65f)
    val inactiveFill = if (isDark) Color(0xFF475569).copy(alpha = 0.35f) else Color(0xFF94A3B8).copy(alpha = 0.28f)
    val activeHandle = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val inactiveHandle = if (isDark) Color(0xFF64748B) else Color(0xFF64748B)
    val activeBorder = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val labelTextColor = if (loopMode) {
        if (isDark) Color(0xFF81C784) else Color(0xFF15803D)
    } else {
        toolbarText.copy(alpha = 0.72f)
    }
    val barNumberColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val tickColor = if (isDark) Color(0x33FFFFFF) else Color(0x33000000)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(barBg)
            .clipToBounds()
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val lyt = currentLayout
                    if (lyt.placedMeasures.isEmpty()) return@awaitEachGesture
                    val clickCanvasX = down.position.x + currentScrollOffset
                    val range = currentLoopRange
                    val (loopLeftX, loopRightX) = lyt.loopRangeSpanX(range) ?: return@awaitEachGesture
                    val firstLeft = lyt.measureSpanX(0)?.first ?: return@awaitEachGesture
                    val lastRight = lyt.measureSpanX(lyt.placedMeasures.lastIndex)?.second ?: return@awaitEachGesture
                    val tolPx = 8.dp.toPx()

                    if (clickCanvasX < firstLeft - tolPx) {
                        down.consume()
                        var releasedInGutter = true
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                change.consume()
                                releasedInGutter = (change.position.x + currentScrollOffset) < firstLeft
                            }
                        } while (event.changes.any { it.pressed })
                        if (releasedInGutter) {
                            currentOnToggleLoopMode()
                        }
                        return@awaitEachGesture
                    }

                    val nearLeft = abs(clickCanvasX - loopLeftX) <= tolPx
                    val nearRight = abs(clickCanvasX - loopRightX) <= tolPx
                    val dragMode = when {
                        nearLeft && (!nearRight || abs(clickCanvasX - loopLeftX) <= abs(clickCanvasX - loopRightX)) ->
                            ScoreLoopDragMode.START_HANDLE
                        nearRight -> ScoreLoopDragMode.END_HANDLE
                        clickCanvasX in loopLeftX..loopRightX -> ScoreLoopDragMode.MOVE_WINDOW
                        clickCanvasX <= lastRight + tolPx -> ScoreLoopDragMode.SWEEP_SELECT
                        else -> return@awaitEachGesture
                    }

                    down.consume()
                    val anchorMeasure = lyt.measureIndexForLoopBarX(clickCanvasX, tolPx) ?: range.first
                    val fixedStart = range.first
                    val fixedEnd = range.last
                    val windowSpan = (fixedEnd - fixedStart).coerceAtLeast(0)
                    if (dragMode == ScoreLoopDragMode.SWEEP_SELECT) {
                        currentOnRangeChange(anchorMeasure, anchorMeasure)
                    }

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            change.consume()
                            val curLyt = currentLayout
                            if (curLyt.placedMeasures.isEmpty()) continue
                            val curCanvasX = change.position.x + currentScrollOffset
                            when (dragMode) {
                                ScoreLoopDragMode.START_HANDLE -> {
                                    val newStart = curLyt.snapLoopStartMeasureForX(curCanvasX, fixedEnd)
                                    currentOnRangeChange(newStart, fixedEnd)
                                }
                                ScoreLoopDragMode.END_HANDLE -> {
                                    val newEnd = curLyt.snapLoopEndMeasureForX(curCanvasX, fixedStart)
                                    currentOnRangeChange(fixedStart, newEnd)
                                }
                                ScoreLoopDragMode.MOVE_WINDOW -> {
                                    val curMeasure = curLyt.measureIndexForLoopBarX(curCanvasX, tolPx) ?: continue
                                    val maxStart = (curLyt.placedMeasures.lastIndex - windowSpan).coerceAtLeast(0)
                                    val newStart = (fixedStart + (curMeasure - anchorMeasure)).coerceIn(0, maxStart)
                                    currentOnRangeChange(newStart, newStart + windowSpan)
                                }
                                ScoreLoopDragMode.SWEEP_SELECT -> {
                                    val curMeasure = curLyt.measureIndexForLoopBarX(curCanvasX, tolPx) ?: continue
                                    currentOnRangeChange(min(anchorMeasure, curMeasure), max(anchorMeasure, curMeasure))
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val toScreenX = { canvasX: Float -> canvasX - scrollOffsetPx.toFloat() }
        val firstLeftCanvas = layout.measureSpanX(0)?.first ?: 0f
        val lastRightCanvas = layout.measureSpanX(layout.placedMeasures.lastIndex)?.second ?: firstLeftCanvas
        val trackLeft = toScreenX(firstLeftCanvas)
        val trackRight = toScreenX(lastRightCanvas)
        val gutterRight = trackLeft.coerceIn(0f, size.width)

        val rangeLabel = if (loopRange.first == loopRange.last) {
            "Loop m.${loopRange.first + 1}"
        } else {
            "Loop ${loopRange.first + 1}–${loopRange.last + 1}"
        }
        val labelMeasured = textMeasurer.measure(
            text = rangeLabel,
            style = TextStyle(
                color = labelTextColor,
                fontSize = 10.sp,
                fontWeight = if (loopMode) FontWeight.SemiBold else FontWeight.Normal,
            ),
            softWrap = false,
            maxLines = 1,
        )
        if (showLeftGutterLabel && gutterRight > 12f) {
            drawText(
                textLayoutResult = labelMeasured,
                topLeft = Offset(
                    x = 8.dp.toPx().coerceAtMost((gutterRight - labelMeasured.size.width).coerceAtLeast(2f)),
                    y = (size.height - labelMeasured.size.height) * 0.5f,
                ),
            )
        }

        if (layout.placedMeasures.isNotEmpty()) {
            val drawLeft = max(0f, trackLeft)
            val drawRight = min(size.width, trackRight)
            if (drawRight > drawLeft) {
                drawRect(
                    color = trackBg,
                    topLeft = Offset(drawLeft, 2.dp.toPx()),
                    size = Size(drawRight - drawLeft, size.height - 4.dp.toPx()),
                )
            }

            val loopSpan = layout.loopRangeSpanX(loopRange)
            if (loopSpan != null) {
                val selLeft = toScreenX(loopSpan.first)
                val selRight = toScreenX(loopSpan.second)
                val clippedLeft = max(0f, selLeft)
                val clippedRight = min(size.width, selRight)
                val topY = 2.dp.toPx()
                val boxH = size.height - 4.dp.toPx()
                if (clippedRight > clippedLeft) {
                    drawRect(
                        color = if (loopMode) activeFill else inactiveFill,
                        topLeft = Offset(clippedLeft, topY),
                        size = Size(clippedRight - clippedLeft, boxH),
                    )
                    if (loopMode) {
                        drawLine(activeBorder, Offset(clippedLeft, topY), Offset(clippedRight, topY), strokeWidth = 1.5f)
                        drawLine(activeBorder, Offset(clippedLeft, topY + boxH), Offset(clippedRight, topY + boxH), strokeWidth = 1.5f)
                    }
                }

                val handleW = 6.dp.toPx()
                val handleColor = if (loopMode) activeHandle else inactiveHandle
                if (selLeft + handleW >= 0f && selLeft <= size.width) {
                    drawRect(
                        color = handleColor,
                        topLeft = Offset(selLeft, topY),
                        size = Size(min(handleW, (selRight - selLeft) * 0.5f), boxH),
                    )
                }
                val rightHandleX = selRight - min(handleW, (selRight - selLeft) * 0.5f)
                if (selRight >= 0f && rightHandleX <= size.width) {
                    drawRect(
                        color = handleColor,
                        topLeft = Offset(rightHandleX, topY),
                        size = Size(min(handleW, (selRight - selLeft) * 0.5f), boxH),
                    )
                }
            }

            for (mIdx in layout.placedMeasures.indices) {
                val (mLeftC, mRightC) = layout.measureSpanX(mIdx) ?: continue
                val mLeft = toScreenX(mLeftC)
                val mRight = toScreenX(mRightC)
                if (mRight < 0f || mLeft > size.width) continue
                if (mLeft >= 0f && mLeft <= size.width) {
                    drawLine(tickColor, Offset(mLeft, 2.dp.toPx()), Offset(mLeft, size.height - 2.dp.toPx()), strokeWidth = 1f)
                }
                if (mIdx == layout.placedMeasures.lastIndex && mRight >= 0f && mRight <= size.width) {
                    drawLine(tickColor, Offset(mRight, 2.dp.toPx()), Offset(mRight, size.height - 2.dp.toPx()), strokeWidth = 1f)
                }
                val cellWidth = mRight - mLeft
                if (cellWidth >= 14.dp.toPx()) {
                    val numRes = textMeasurer.measure(
                        text = (mIdx + 1).toString(),
                        style = TextStyle(color = barNumberColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                        softWrap = false,
                        maxLines = 1,
                    )
                    val numX = mLeft + 7.dp.toPx()
                    if (numX >= 0f && numX + numRes.size.width <= min(size.width, mRight)) {
                        drawText(
                            textLayoutResult = numRes,
                            topLeft = Offset(numX, (size.height - numRes.size.height) * 0.5f),
                        )
                    }
                }
            }
        }

        drawLine(
            color = borderMuted,
            start = Offset(0f, size.height - 0.5f),
            end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1f,
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


@Composable
private fun DynamicsPalettePopup(
    offsetX: Int,
    activeDynamic: DynamicMark,
    isDark: Boolean,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    onSelectDynamic: (DynamicMark) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    val popupBg = if (isDark) Color(0xFF1E2238) else Color(0xFFF8FAFC)
    val tileBg = if (isDark) Color(0xFF282E4A) else Color(0xFFE2E8F0).copy(alpha = 0.7f)
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(offsetX, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            color = popupBg,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .wrapContentSize()
                .border(1.dp, borderMuted, RoundedCornerShape(8.dp))
                .onKeyEvent(onKeyEvent),
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = ScorePaletteKind.DYNAMICS.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = toolbarText,
                    )
                    Text(
                        text = "p/f cycle, Shift+P=mp, Shift+F=mf",
                        fontSize = 9.sp,
                        color = toolbarText.copy(alpha = 0.75f),
                    )
                }
                DynamicPaletteRow(
                    rowLabel = "Soft",
                    options = ScorePalettes.softDynamicOptions,
                    activeDynamic = activeDynamic,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    onSelectDynamic = onSelectDynamic,
                )
                DynamicPaletteRow(
                    rowLabel = "Loud",
                    options = ScorePalettes.loudDynamicOptions,
                    activeDynamic = activeDynamic,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    onSelectDynamic = onSelectDynamic,
                )
                DynamicPaletteRow(
                    rowLabel = "Accent",
                    options = ScorePalettes.accentDynamicOptions,
                    activeDynamic = activeDynamic,
                    isDark = isDark,
                    tileBg = tileBg,
                    toolbarText = toolbarText,
                    accentColor = accentColor,
                    borderMuted = borderMuted,
                    musicFontFamily = musicFontFamily,
                    onSelectDynamic = onSelectDynamic,
                )
            }
        }
    }
}

@Composable
private fun DynamicPaletteRow(
    rowLabel: String,
    options: List<ScoreDynamicOption>,
    activeDynamic: DynamicMark,
    isDark: Boolean,
    tileBg: Color,
    toolbarText: Color,
    accentColor: Color,
    borderMuted: Color,
    musicFontFamily: FontFamily,
    onSelectDynamic: (DynamicMark) -> Unit,
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
            modifier = Modifier.width(40.dp),
        )
        options.forEach { option ->
            val selected = option.mark == activeDynamic
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
                    .clickable { onSelectDynamic(option.mark) }
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = option.glyph,
                    fontFamily = musicFontFamily,
                    fontSize = 20.sp,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
