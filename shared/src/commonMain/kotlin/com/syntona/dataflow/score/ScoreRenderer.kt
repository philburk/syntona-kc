package com.syntona.dataflow.score

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Palette for score rendering (paper-like light mode & soft slate dark mode). */
data class ScoreRenderColors(
    val ink: Color,
    val staffLine: Color,
    val measureNumber: Color,
    val activeNote: Color,
    val selectedNote: Color,
    val selectedBoxFill: Color,
    val selectedBoxBorder: Color,
    val overflowNote: Color,
    val ghostNote: Color,
    val cursor: Color,
    val stepCursor: Color,
    val incompleteTuplet: Color = Color(0xFFF57C00),
) {
    companion object {
        fun forTheme(darkMode: Boolean): ScoreRenderColors = if (darkMode) {
            ScoreRenderColors(
                ink = Color(0xFFE2E8F0),
                staffLine = Color(0xFF94A3B8),
                measureNumber = Color(0xFF64748B),
                activeNote = Color(0xFF60A5FA),
                selectedNote = Color(0xFFFBBF24),
                selectedBoxFill = Color(0x36FBBF24),
                selectedBoxBorder = Color(0xDDFBBF24),
                overflowNote = Color(0xFFEF4444),
                ghostNote = Color(0x6694A3B8),
                cursor = Color(0xFF60A5FA),
                stepCursor = Color(0xFFEF4444),
                incompleteTuplet = Color(0xFFFF9800),
            )
        } else {
            ScoreRenderColors(
                ink = Color(0xFF111827),
                staffLine = Color(0xFF374151),
                measureNumber = Color(0xFF6B7280),
                activeNote = Color(0xFF2563EB),
                selectedNote = Color(0xFFD97706),
                selectedBoxFill = Color(0x33F59E0B),
                selectedBoxBorder = Color(0xDDF59E0B),
                overflowNote = Color(0xFFDC2626),
                ghostNote = Color(0x6664748B),
                cursor = Color(0xFF2563EB),
                stepCursor = Color(0xFFDC2626),
                incompleteTuplet = Color(0xFFE65100),
            )
        }
    }
}

data class ScorePlacedChordNote(
    val noteIdx: Int,
    val pitch: SpelledPitch,
    val staffStep: Int,
    val accidental: Accidental?,
    val headX: Float,
    val y: Float,
    val accX: Float?,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
)

data class ScorePlacedItem(
    val measureIdx: Int,
    val itemIdx: Int,
    val globalIdx: Int,
    val item: ScoreItem,
    val x: Float,
    val width: Float,
    val accidental: Accidental?,
    val staffStep: Int?,
    val centerY: Float,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val isOverflow: Boolean,
    val chordNotes: List<ScorePlacedChordNote> = emptyList(),
    val stemDown: Boolean = false,
    val effectiveClef: Clef = Clef.TREBLE,
    val isBeamed: Boolean = false,
    val stemTipYOverride: Float? = null,
    val staff: ScoreStaffId = ScoreStaffId(0, 0),
    val isOutOfRange: Boolean = false,
    val outOfRangeChordIndices: Set<Int> = emptySet(),
) {
    val location: ScoreItemLocation get() = ScoreItemLocation(measureIdx, itemIdx, staff = staff)
}

data class ScorePlacedBeamGroup(
    val measureIdx: Int,
    val itemIndices: List<Int>,
    val stemDown: Boolean,
    val firstStemX: Float,
    val lastStemX: Float,
    val firstTipY: Float,
    val lastTipY: Float,
    val stemXsByItemIdx: Map<Int, Float>,
    val stemTipsByItemIdx: Map<Int, Float>,
)

data class ScorePlacedClefChange(
    val measureIdx: Int,
    val itemIdx: Int,
    val clef: Clef,
    val x: Float,
    val width: Float,
    val topY: Float,
    val bottomY: Float,
    val staff: ScoreStaffId = ScoreStaffId(0, 0),
) {
    val location: ScoreClefLocation get() = ScoreClefLocation(measureIdx, itemIdx, staff = staff)
}

data class ScorePlacedDynamic(
    val measureIdx: Int,
    val itemIdx: Int,
    val marking: ScoreDynamicMarking,
    val x: Float,
    val baselineY: Float,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val staff: ScoreStaffId = ScoreStaffId(0, 0),
) {
    val location: ScoreDynamicLocation get() = ScoreDynamicLocation(measureIdx, itemIdx, staff = staff)
}

data class ScorePlacedTimeSignature(
    val measureIdx: Int,
    val timeSignature: TimeSignature,
    val x: Float,
    val width: Float,
    val numWidth: Float,
    val denWidth: Float,
    val topY: Float,
    val bottomY: Float,
    val staff: ScoreStaffId = ScoreStaffId(0, 0),
)

data class ScorePlacedTupletGroup(
    val measureIdx: Int,
    val itemIndices: List<Int>,
    val tuplet: ScoreTuplet,
    val displayText: String,
    val drawBracket: Boolean,
    val isFull: Boolean,
    val aboveStaff: Boolean,
    val startX: Float,
    val endX: Float,
    val bracketY: Float,
    val numberX: Float,
    val numberBaselineY: Float,
    val numberWidth: Float,
    val numberEmPx: Float,
)

data class ScorePlacedMeasure(
    val measureIdx: Int,
    val measure: ScoreMeasure,
    val startX: Float,
    val barlineX: Float,
    val items: List<ScorePlacedItem>,
    val totalTicks: Int,
    val capacityTicks: Int,
    val contentStartX: Float = startX,
    val keyChangeX: Float = startX,
    val keyChangeGlyphs: List<Pair<Int, Accidental>> = emptyList(),
    val activeKeySignature: KeySignature = KeySignature.C_MAJOR,
    val timeChange: ScorePlacedTimeSignature? = null,
    val activeTimeSignature: TimeSignature = TimeSignature.COMMON_TIME,
    val clefChanges: List<ScorePlacedClefChange> = emptyList(),
    val barlineClefChange: ScorePlacedClefChange? = null,
    val activeOpeningClef: Clef = Clef.TREBLE,
    val activeClosingClef: Clef = Clef.TREBLE,
    val dynamics: List<ScorePlacedDynamic> = emptyList(),
    val beamGroups: List<ScorePlacedBeamGroup> = emptyList(),
    val tupletGroups: List<ScorePlacedTupletGroup> = emptyList(),
    val staff: ScoreStaffId = ScoreStaffId(0, 0),
)

data class ScoreInsertionTarget(
    val measureIdx: Int,
    val itemIdx: Int,
    val appendNewMeasure: Boolean,
    val afterClefChange: Boolean = true,
    val staff: ScoreStaffId = ScoreStaffId(0, 0),
)

data class ScoreGhostPreview(
    val x: Float,
    val staffStep: Int,
    val duration: NoteDuration,
    val isRest: Boolean,
    val isDotted: Boolean = false,
    val accidental: Accidental? = null,
    val isStaccato: Boolean = false,
    val staff: ScoreStaffId = ScoreStaffId(0, 0),
    val tupletText: String? = null,
)

data class ScoreRubberBand(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Layout geometry for one rendered instrument group (spanning 1 or 2 staves). */
data class ScorePlacedInstrumentGroup(
    val instrumentIdx: Int,
    val instrument: ScoreInstrumentPart,
    val topStaffY: Float,
    val bottomStaffY: Float,
    val labelLeft: Float,
    val labelTop: Float,
    val labelRight: Float,
    val labelBottom: Float,
    val braceX: Float,
)

/** Layout geometry for one rendered 5-line staff belonging to [staffId]. */
data class ScorePlacedStaff(
    val staffId: ScoreStaffId,
    val instrument: ScoreInstrumentPart,
    val staffSpacePx: Float,
    val leftBarX: Float,
    val topStaffY: Float,
    val bottomStaffY: Float,
    val rowTopY: Float,
    val rowBottomY: Float,
    val openingClef: Clef,
    val clefX: Float,
    val keyStartX: Float,
    val keySteps: List<Int>,
    val keyGlyph: String,
    val keyGlyphStepWidth: Float,
    val timeSigX: Float,
    val timeSigWidth: Float,
    val numWidth: Float,
    val denWidth: Float,
    val numStr: String,
    val denStr: String,
    val headerEndX: Float,
    val placedMeasures: List<ScorePlacedMeasure>,
    val placedTimeSignatures: List<ScorePlacedTimeSignature>,
    val placedClefChanges: List<ScorePlacedClefChange>,
    val placedDynamics: List<ScorePlacedDynamic>,
) {
    fun yForStep(stepFromBottom: Int): Float = bottomStaffY - stepFromBottom * (staffSpacePx * 0.5f)

    fun stepAtY(y: Float, minStep: Int = -12, maxStep: Int = 20): Int =
        ((bottomStaffY - y) / (staffSpacePx * 0.5f)).roundToInt().coerceIn(minStep, maxStep)

    fun cursorXFor(cursor: ScoreCursorPosition): Float? {
        val pm = placedMeasures.getOrNull(cursor.measureIdx) ?: return null
        val sp = staffSpacePx
        val trailingClef = pm.clefChanges.lastOrNull { it.itemIdx >= pm.items.size }
        if (pm.items.isEmpty()) {
            return if (trailingClef != null) {
                trailingClef.x + trailingClef.width + sp * 0.35f
            } else {
                pm.contentStartX + sp * 1.1f
            }
        }
        val clampedIdx = cursor.itemIdx.coerceIn(0, pm.items.size)
        return when {
            clampedIdx == 0 -> ((pm.contentStartX + sp * 0.35f) + pm.items.first().boxLeft) * 0.5f
            clampedIdx >= pm.items.size -> {
                val tailRight = maxOf(
                    pm.items.last().boxRight,
                    trailingClef?.let { it.x + it.width } ?: 0f,
                )
                val limit = min(pm.barlineX - sp * 0.25f, tailRight + sp * 0.9f)
                max(tailRight + sp * 0.25f, (tailRight + limit) * 0.5f)
            }
            else -> {
                val midClef = pm.clefChanges.lastOrNull { it.itemIdx == clampedIdx }
                val leftEdge = midClef?.let { it.x + it.width } ?: pm.items[clampedIdx - 1].boxRight
                (leftEdge + pm.items[clampedIdx].boxLeft) * 0.5f
            }
        }
    }
}

data class ScoreLayout(
    val totalWidthPx: Float,
    val totalHeightPx: Float,
    val staffSpacePx: Float = 10f,
    val leftBarX: Float = 0f,
    val topStaffY: Float = 0f,
    val bottomStaffY: Float = 0f,
    val headerEndX: Float = 0f,
    val staffEndX: Float = 0f,
    val openingClef: Clef = Clef.TREBLE,
    val placedMeasures: List<ScorePlacedMeasure> = emptyList(),
    val placedTimeSignatures: List<ScorePlacedTimeSignature> = emptyList(),
    val placedClefChanges: List<ScorePlacedClefChange> = emptyList(),
    val placedDynamics: List<ScorePlacedDynamic> = emptyList(),
    val staves: List<ScorePlacedStaff> = emptyList(),
    val instrumentGroups: List<ScorePlacedInstrumentGroup> = emptyList(),
    val showInstrumentLabels: Boolean = false,
) {
    fun staffForId(id: ScoreStaffId): ScorePlacedStaff? =
        staves.firstOrNull { it.staffId == id } ?: staves.firstOrNull()

    fun staffAtY(y: Float): ScorePlacedStaff? =
        staves.firstOrNull { y >= it.rowTopY && y <= it.rowBottomY }
            ?: staves.minByOrNull { abs(y - (it.topStaffY + it.bottomStaffY) * 0.5f) }

    fun instrumentGroupAt(x: Float, y: Float): ScorePlacedInstrumentGroup? {
        if (!showInstrumentLabels) return null
        val sp = staffSpacePx
        return instrumentGroups.firstOrNull { grp ->
            x >= grp.labelLeft - sp * 0.5f && x <= grp.labelRight + sp * 0.5f &&
                y >= grp.labelTop - sp * 0.5f && y <= grp.labelBottom + sp * 0.5f
        }
    }

    fun yForStep(stepFromBottom: Int, staffId: ScoreStaffId = ScoreStaffId(0, 0)): Float {
        val ps = staffForId(staffId)
        return ps?.yForStep(stepFromBottom) ?: (bottomStaffY - stepFromBottom * (staffSpacePx * 0.5f))
    }

    fun stepAtY(y: Float, staffId: ScoreStaffId = ScoreStaffId(0, 0), minStep: Int = -12, maxStep: Int = 20): Int {
        val ps = staffForId(staffId)
        return ps?.stepAtY(y, minStep, maxStep)
            ?: ((bottomStaffY - y) / (staffSpacePx * 0.5f)).roundToInt().coerceIn(minStep, maxStep)
    }

    /** Returns the [ScorePlacedClefChange] under `(x, y)`, or `null` if none. */
    fun clefChangeAt(x: Float, y: Float): ScorePlacedClefChange? {
        val sp = staffSpacePx
        val padX = sp * 0.35f
        val padY = sp * 0.4f
        return placedClefChanges.firstOrNull { cc ->
            x >= cc.x - padX && x <= cc.x + cc.width + padX &&
                y >= cc.topY - padY && y <= cc.bottomY + padY
        }
    }

    /** Returns the [ScorePlacedTimeSignature] under `(x, y)`, or `null` if none. */
    fun timeSignatureAt(x: Float, y: Float): ScorePlacedTimeSignature? {
        val sp = staffSpacePx
        val padX = sp * 0.35f
        val padY = sp * 0.4f
        return placedTimeSignatures.firstOrNull { ts ->
            x >= ts.x - padX && x <= ts.x + ts.width + padX &&
                y >= ts.topY - padY && y <= ts.bottomY + padY
        }
    }

    /** Returns the [ScorePlacedDynamic] under `(x, y)`, or `null` if none. */
    fun dynamicAt(x: Float, y: Float): ScorePlacedDynamic? {
        val sp = staffSpacePx
        val padX = sp * 0.35f
        val padY = sp * 0.35f
        return placedDynamics.firstOrNull { dyn ->
            x >= dyn.boxLeft - padX && x <= dyn.boxRight + padX &&
                y >= dyn.boxTop - padY && y <= dyn.boxBottom + padY
        }
    }

    /** Returns every [ScoreDynamicLocation] whose bounding box intersects the given rectangle. */
    fun dynamicsInRect(left: Float, top: Float, right: Float, bottom: Float): Set<ScoreDynamicLocation> {
        val minX = min(left, right)
        val maxX = max(left, right)
        val minY = min(top, bottom)
        val maxY = max(top, bottom)
        val hit = linkedSetOf<ScoreDynamicLocation>()
        for (dyn in placedDynamics) {
            if (dyn.boxRight >= minX && dyn.boxLeft <= maxX &&
                dyn.boxBottom >= minY && dyn.boxTop <= maxY
            ) {
                hit.add(dyn.location)
            }
        }
        return hit
    }

    /**
     * Resolves a click at `(x, y)` into the target `(ScoreDynamicLocation, DynamicPlacement)` for
     * placing a dynamic above or below the stave in a measure.
     */
    fun dynamicTargetAt(x: Float, y: Float): Pair<ScoreDynamicLocation, DynamicPlacement>? {
        val targetStaff = staffAtY(y)
        val measures = targetStaff?.placedMeasures ?: placedMeasures
        if (measures.isEmpty()) return null
        val pm = measures.firstOrNull { x >= it.startX && x <= it.barlineX }
            ?: measures.minByOrNull { m ->
                when {
                    x < m.startX -> m.startX - x
                    x > m.barlineX -> x - m.barlineX
                    else -> 0f
                }
            }
            ?: return null
        val staffTop = targetStaff?.topStaffY ?: topStaffY
        val staffBottom = targetStaff?.bottomStaffY ?: bottomStaffY
        val placement = if (y < (staffTop + staffBottom) * 0.5f) {
            DynamicPlacement.ABOVE
        } else {
            DynamicPlacement.BELOW
        }
        val sp = staffSpacePx
        val itemIdx = if (pm.items.isEmpty()) {
            0
        } else {
            val last = pm.items.last()
            if (x > last.boxRight + sp * 1.5f) {
                pm.items.size
            } else {
                pm.items.minByOrNull { pi -> abs(x - (pi.x + pi.width * 0.5f)) }?.itemIdx ?: 0
            }
        }
        val staffId = targetStaff?.staffId ?: ScoreStaffId(0, 0)
        return ScoreDynamicLocation(pm.measureIdx, itemIdx, staff = staffId) to placement
    }

    /** Returns the [ScorePlacedItem] under `(x, y)`, preferring the nearest item center if boxes touch. */
    fun itemAt(x: Float, y: Float): ScorePlacedItem? {
        val sp = staffSpacePx
        var best: ScorePlacedItem? = null
        var bestDist = Float.MAX_VALUE
        val candidateStaves = if (staves.isNotEmpty()) {
            staves
        } else {
            return null
        }
        for (ps in candidateStaves) {
            if (y < ps.rowTopY - sp * 2f || y > ps.rowBottomY + sp * 2f) continue
            for (pm in ps.placedMeasures) {
                for (pi in pm.items) {
                    val padX = sp * 0.4f
                    val padY = if (pi.item is ScoreItem.Rest) sp * 0.4f else sp * 0.6f
                    if (x >= pi.boxLeft - padX && x <= pi.boxRight + padX &&
                        y >= pi.boxTop - padY && y <= pi.boxBottom + padY
                    ) {
                        val cx = pi.x + pi.width * 0.5f
                        val dist = abs(x - cx) + abs(y - pi.centerY) * 0.35f
                        if (dist < bestDist) {
                            bestDist = dist
                            best = pi
                        }
                    }
                }
            }
        }
        return best
    }

    /**
     * Returns the specific [ScorePlacedChordNote] inside [placedItem] near `(x, y)` (when clicking
     * on a notehead in a chord), or `null` if not close to any individual notehead.
     */
    fun chordNoteAt(placedItem: ScorePlacedItem, x: Float, y: Float): ScorePlacedChordNote? {
        if (placedItem.chordNotes.isEmpty()) return null
        val sp = staffSpacePx
        val pad = sp * 0.55f
        return placedItem.chordNotes
            .filter { cn ->
                x >= cn.boxLeft - pad && x <= cn.boxRight + pad &&
                    y >= cn.boxTop - pad && y <= cn.boxBottom + pad
            }
            .minByOrNull { cn ->
                abs(y - cn.y) + abs(x - (cn.headX + placedItem.width * 0.5f)) * 0.3f
            }
    }

    /**
     * Returns an existing non-rest [ScorePlacedItem] on the staff under [y] whose notehead column
     * aligns horizontally with [x], used in Entry mode to stack notes vertically onto an existing note or chord.
     */
    fun itemColumnAt(x: Float, y: Float? = null): ScorePlacedItem? {
        val sp = staffSpacePx
        val measures = if (y != null) staffAtY(y)?.placedMeasures ?: placedMeasures else placedMeasures
        var best: ScorePlacedItem? = null
        var bestDist = Float.MAX_VALUE
        for (pm in measures) {
            for (pi in pm.items) {
                if (pi.item is ScoreItem.Rest) continue
                val minHeadX = pi.chordNotes.minOfOrNull { it.headX } ?: pi.x
                val maxHeadRight = pi.chordNotes.maxOfOrNull { it.headX + pi.width } ?: (pi.x + pi.width)
                if (x >= minHeadX - sp * 0.5f && x <= maxHeadRight + sp * 0.5f) {
                    val cx = (minHeadX + maxHeadRight) * 0.5f
                    val dist = abs(x - cx)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = pi
                    }
                }
            }
        }
        return best
    }

    /** Returns all items whose visual bounding box intersects the given rectangle across all staves. */
    fun itemsInRect(left: Float, top: Float, right: Float, bottom: Float): Set<ScoreItemLocation> {
        val result = linkedSetOf<ScoreItemLocation>()
        for (ps in staves) {
            for (pm in ps.placedMeasures) {
                for (pi in pm.items) {
                    if (pi.boxRight >= left && pi.boxLeft <= right &&
                        pi.boxBottom >= top && pi.boxTop <= bottom
                    ) {
                        result.add(pi.location)
                    }
                }
            }
        }
        return result
    }

    /** Computes where a new item clicked at `(x, y)` should be inserted, or `null` if inside the clef/key header. */
    fun insertionTargetAt(x: Float, y: Float? = null): ScoreInsertionTarget? {
        val targetStaff = if (y != null) staffAtY(y) else staves.firstOrNull()
        val staffId = targetStaff?.staffId ?: ScoreStaffId(0, 0)
        val measures = targetStaff?.placedMeasures ?: placedMeasures
        val firstStart = measures.firstOrNull()?.startX ?: (targetStaff?.headerEndX ?: headerEndX)
        if (x < firstStart - staffSpacePx * 0.5f) return null
        if (measures.isEmpty()) {
            return ScoreInsertionTarget(measureIdx = 0, itemIdx = 0, appendNewMeasure = true, staff = staffId)
        }
        for (pm in measures) {
            if (x <= pm.barlineX) {
                val insertIdx = pm.items.indexOfFirst { x < it.x + it.width * 0.5f }
                    .let { if (it < 0) pm.items.size else it }
                val ccAtIdx = pm.clefChanges.firstOrNull { it.itemIdx > 0 && it.itemIdx == insertIdx }
                val afterClefChange = ccAtIdx == null || x >= ccAtIdx.x + ccAtIdx.width * 0.5f
                return ScoreInsertionTarget(
                    measureIdx = pm.measureIdx,
                    itemIdx = insertIdx,
                    appendNewMeasure = false,
                    afterClefChange = afterClefChange,
                    staff = staffId,
                )
            }
        }
        val lastPm = measures.last()
        return if (lastPm.measure.items.isEmpty()) {
            ScoreInsertionTarget(measureIdx = lastPm.measureIdx, itemIdx = 0, appendNewMeasure = false, staff = staffId)
        } else {
            ScoreInsertionTarget(measureIdx = measures.size, itemIdx = 0, appendNewMeasure = true, staff = staffId)
        }
    }

    /** Returns the effective [Clef] for an insertion or hover at canvas coordinate `(x, y)`. */
    fun effectiveClefAtX(x: Float, y: Float? = null): Clef {
        val targetStaff = if (y != null) staffAtY(y) else staves.firstOrNull()
        val measures = targetStaff?.placedMeasures ?: placedMeasures
        val fallbackClef = targetStaff?.openingClef ?: openingClef
        val target = insertionTargetAt(x, y) ?: return fallbackClef
        if (target.appendNewMeasure || target.measureIdx >= measures.size) {
            return measures.lastOrNull()?.activeClosingClef ?: fallbackClef
        }
        val pm = measures[target.measureIdx]
        var clef = pm.activeOpeningClef
        for (cc in pm.clefChanges) {
            if (cc.itemIdx > 0 && x >= cc.x + cc.width * 0.5f) {
                clef = cc.clef
            }
        }
        return clef
    }

    /**
     * Resolves a selection-mode click at `(x, y)` to a [ScoreCursorPosition] inside an empty measure
     * or between/around notes, or `null` if inside the clef/key header.
     */
    fun cursorPositionAt(x: Float, y: Float? = null): ScoreCursorPosition? {
        val targetStaff = if (y != null) staffAtY(y) else staves.firstOrNull()
        val staffId = targetStaff?.staffId ?: ScoreStaffId(0, 0)
        val measures = targetStaff?.placedMeasures ?: placedMeasures
        val firstStart = measures.firstOrNull()?.startX ?: (targetStaff?.headerEndX ?: headerEndX)
        if (x < firstStart - staffSpacePx * 0.5f) return null
        if (measures.isEmpty()) return ScoreCursorPosition(0, 0, staff = staffId)
        for (pm in measures) {
            if (x <= pm.barlineX) {
                val itemIdx = if (pm.items.isEmpty()) {
                    0
                } else {
                    pm.items.indexOfFirst { x < it.x + it.width * 0.5f }
                        .let { if (it < 0) pm.items.size else it }
                }
                return ScoreCursorPosition(measureIdx = pm.measureIdx, itemIdx = itemIdx, staff = staffId)
            }
        }
        val lastPm = measures.last()
        return ScoreCursorPosition(measureIdx = lastPm.measureIdx, itemIdx = lastPm.items.size, staff = staffId)
    }

    /** Returns the 0-based measure index under `(x, y)`, or `null` if outside measure bounds. */
    fun measureAt(x: Float, y: Float? = null): Int? {
        val targetStaff = if (y != null) staffAtY(y) else staves.firstOrNull()
        val measures = targetStaff?.placedMeasures ?: placedMeasures
        for ((idx, pm) in measures.withIndex()) {
            val leftBound = if (idx == 0) pm.startX - staffSpacePx * 0.5f else measures[idx - 1].barlineX
            if (x >= leftBound && x <= pm.barlineX) {
                return pm.measureIdx
            }
        }
        return null
    }

    /** Returns the indices of all measures whose horizontal bounds intersect `[left, right]` and intersect any staff vertically. */
    fun measuresInRect(left: Float, top: Float, right: Float, bottom: Float): Set<Int> {
        val sp = staffSpacePx
        val intersectsAnyStaff = staves.any { ps ->
            bottom >= ps.topStaffY - sp && top <= ps.bottomStaffY + sp
        }
        if (!intersectsAnyStaff) return emptySet()
        val result = linkedSetOf<Int>()
        for ((idx, pm) in placedMeasures.withIndex()) {
            val leftBound = if (idx == 0) pm.startX else placedMeasures[idx - 1].barlineX
            if (pm.barlineX >= left && leftBound <= right) {
                result.add(pm.measureIdx)
            }
        }
        return result
    }

    /** Computes the horizontal canvas coordinate for rendering the vertical cursor at [cursor]. */
    fun cursorXFor(cursor: ScoreCursorPosition): Float? {
        val targetStaff = staffForId(cursor.staff) ?: return null
        return targetStaff.cursorXFor(cursor)
    }
}

/**
 * Pure layout and engraving renderer for single-stave notation.
 * Draws strictly through [ScoreDrawTarget] so screen and future PDF outputs share one implementation.
 */
object ScoreRenderer {

    private const val STEM_LENGTH_SPACES = 3.5f
    private const val STEM_LENGTH_32ND_SPACES = 4.25f
    private const val TOP_MARGIN_SPACES = 4.5f
    private const val BOTTOM_MARGIN_SPACES = 5.5f
    private const val CLEF_CHANGE_SCALE = 0.72f

    private data class StaffLayoutResult(
        val staff: ScorePlacedStaff,
        val instrumentIdx: Int,
        val isLastStaffInInstrument: Boolean,
    )

    private fun computeHeaderAndMeasures(
        score: ScoreData,
        staffSpacePx: Float,
        minWidthPx: Float,
        measureGlyphWidth: (String, Float) -> Float,
        activeTupletRatio: Pair<Int, Int>? = null,
    ): Pair<List<StaffLayoutResult>, ScoreLayout> {
        val sp = staffSpacePx.coerceAtLeast(4f)
        val emPx = sp * 4f
        val changeEmPx = emPx * CLEF_CHANGE_SCALE
        val instruments = score.instruments.ifEmpty { listOf(ScoreInstrumentPart(id = "inst-1")) }
        val showInstrumentLabels = instruments.isNotEmpty()
        val leftMargin = if (showInstrumentLabels) sp * 8.5f else sp * 2.0f
        val rightMargin = sp * 2.0f

        data class StaffBuildState(
            val staffId: ScoreStaffId,
            val instrumentIdx: Int,
            val instrument: ScoreInstrumentPart,
            val measures: List<ScoreMeasure>,
            val openingClef: Clef,
            val topStaffY: Float,
            val bottomStaffY: Float,
            var rowTopY: Float = 0f,
            var rowBottomY: Float = 0f,
            var keySteps: List<Int> = emptyList(),
            var keyGlyph: String = SmuflGlyphs.ACCIDENTAL_SHARP,
            var keyGlyphStepWidth: Float = SmuflGlyphs.ACCIDENTAL_SHARP_WIDTH * sp + sp * 0.18f,
            var effectiveClef: Clef = openingClef,
            var effectiveKey: KeySignature = score.keySignature,
            var effectiveTime: TimeSignature = score.timeSignature,
            var globalIdx: Int = 0,
            val placedMeasures: MutableList<ScorePlacedMeasure> = mutableListOf(),
            val placedTimeSignatures: MutableList<ScorePlacedTimeSignature> = mutableListOf(),
            val placedClefChanges: MutableList<ScorePlacedClefChange> = mutableListOf(),
            val placedDynamics: MutableList<ScorePlacedDynamic> = mutableListOf(),
        ) {
            fun yForStep(stepFromBottom: Int): Float = bottomStaffY - stepFromBottom * (sp * 0.5f)
        }

        val staffStates = mutableListOf<StaffBuildState>()
        var nextTopY = TOP_MARGIN_SPACES * sp
        for ((instIdx, inst) in instruments.withIndex()) {
            if (instIdx > 0) {
                nextTopY += 9.5f * sp
            }
            for (sIdx in 0 until inst.staffCount) {
                if (sIdx > 0) {
                    nextTopY += 7.5f * sp
                }
                val topY = nextTopY
                val bottomY = topY + 4f * sp
                nextTopY = bottomY
                val ms = inst.measuresForStaff(sIdx)
                val openClef = ms.firstOrNull()?.clefChanges?.get(0) ?: inst.clefForStaff(sIdx)
                staffStates.add(
                    StaffBuildState(
                        staffId = ScoreStaffId(instIdx, sIdx),
                        instrumentIdx = instIdx,
                        instrument = inst,
                        measures = ms,
                        openingClef = openClef,
                        topStaffY = topY,
                        bottomStaffY = bottomY,
                        effectiveClef = openClef,
                    )
                )
            }
        }
        val totalHeight = nextTopY + BOTTOM_MARGIN_SPACES * sp
        for ((idx, st) in staffStates.withIndex()) {
            st.rowTopY = if (idx == 0) {
                0f
            } else {
                (staffStates[idx - 1].bottomStaffY + st.topStaffY) * 0.5f
            }
            st.rowBottomY = if (idx == staffStates.lastIndex) {
                totalHeight
            } else {
                (st.bottomStaffY + staffStates[idx + 1].topStaffY) * 0.5f
            }
        }

        val instrumentGroups = instruments.mapIndexed { instIdx, inst ->
            val groupStaves = staffStates.filter { it.instrumentIdx == instIdx }
            val grpTop = groupStaves.first().topStaffY
            val grpBottom = groupStaves.last().bottomStaffY
            val midY = (grpTop + grpBottom) * 0.5f
            val halfLabelH = max(sp * 1.8f, (grpBottom - grpTop) * 0.5f + sp * 0.5f)
            ScorePlacedInstrumentGroup(
                instrumentIdx = instIdx,
                instrument = inst,
                topStaffY = grpTop,
                bottomStaffY = grpBottom,
                labelLeft = sp * 0.4f,
                labelTop = midY - halfLabelH,
                labelRight = leftMargin - sp * 1.15f,
                labelBottom = midY + halfLabelH,
                braceX = leftMargin - sp * 0.55f,
            )
        }

        // Opening header aligned horizontally across all staves
        var cursorX = leftMargin + sp * 0.7f
        val clefX = cursorX
        val maxClefWidth = staffStates.maxOf { st ->
            max(measureGlyphWidth(st.openingClef.glyph, emPx), sp * 2.6f)
        }
        cursorX += maxClefWidth + sp * 0.8f

        val openingKey = staffStates.firstOrNull()?.measures?.firstOrNull()?.keySignature ?: score.keySignature
        val keyStartX = cursorX
        var maxKeyBlockWidth = 0f
        for (st in staffStates) {
            st.effectiveKey = st.measures.firstOrNull()?.keySignature ?: score.keySignature
            val ks = st.effectiveKey.staffSteps(st.openingClef)
            st.keySteps = ks
            st.keyGlyph = if (st.effectiveKey.fifths >= 0) SmuflGlyphs.ACCIDENTAL_SHARP else SmuflGlyphs.ACCIDENTAL_FLAT
            st.keyGlyphStepWidth = if (st.effectiveKey.fifths >= 0) {
                SmuflGlyphs.ACCIDENTAL_SHARP_WIDTH * sp + sp * 0.18f
            } else {
                SmuflGlyphs.ACCIDENTAL_FLAT_WIDTH * sp + sp * 0.14f
            }
            if (ks.isNotEmpty()) {
                maxKeyBlockWidth = max(maxKeyBlockWidth, ks.size * st.keyGlyphStepWidth + sp * 0.7f)
            }
        }
        cursorX += maxKeyBlockWidth

        val openingTime = staffStates.firstOrNull()?.measures?.firstOrNull()?.timeSignature ?: score.timeSignature
        val numStr = SmuflGlyphs.timeSigNumber(openingTime.numerator)
        val denStr = SmuflGlyphs.timeSigNumber(openingTime.denominator)
        val timeSigX = cursorX
        val numWidth = measureGlyphWidth(numStr, emPx)
        val denWidth = measureGlyphWidth(denStr, emPx)
        val timeSigWidth = max(numWidth, denWidth).coerceAtLeast(sp * 1.6f)
        cursorX += timeSigWidth + sp * 1.6f
        val headerEndX = cursorX

        val allTimeSignatures = mutableListOf<ScorePlacedTimeSignature>()
        val allClefChanges = mutableListOf<ScorePlacedClefChange>()
        val allDynamics = mutableListOf<ScorePlacedDynamic>()
        for (st in staffStates) {
            st.effectiveTime = st.measures.firstOrNull()?.timeSignature ?: score.timeSignature
            val ts = ScorePlacedTimeSignature(
                measureIdx = 0,
                timeSignature = st.effectiveTime,
                x = timeSigX,
                width = timeSigWidth,
                numWidth = numWidth,
                denWidth = denWidth,
                topY = st.topStaffY,
                bottomY = st.bottomStaffY,
                staff = st.staffId,
            )
            st.placedTimeSignatures.add(ts)
            allTimeSignatures.add(ts)
        }

        val measureCount = score.maxMeasureCount().coerceAtLeast(1)
        val stemThick = SmuflGlyphs.STEM_THICKNESS * sp

        for (mIdx in 0 until measureCount) {
            val mStartX = cursorX
            val keyChangeX = mStartX

            data class MeasureHeaderPrep(
                val measure: ScoreMeasure,
                val measureOpeningClef: Clef,
                val keyChangeGlyphs: List<Pair<Int, Accidental>>,
                val keyWidth: Float,
                val timeChange: TimeSignature?,
                val timeWidth: Float,
                val numW: Float,
                val denW: Float,
                val activeKeyAfter: KeySignature,
                val activeTimeAfter: TimeSignature,
            )

            val preps = staffStates.map { st ->
                val measure = st.measures.getOrNull(mIdx) ?: ScoreMeasure(number = mIdx + 1, items = emptyList())
                val measureOpeningClef = st.effectiveClef
                val kGlyphs = if (mIdx > 0 && measure.keySignature != null && measure.keySignature != st.effectiveKey) {
                    ScoreEditing.midMeasureKeyGlyphs(st.effectiveKey, measure.keySignature, st.effectiveClef).also {
                        st.effectiveKey = measure.keySignature
                    }
                } else {
                    emptyList()
                }
                val kEnd = if (kGlyphs.isNotEmpty()) {
                    keyChangeGlyphLayout(keyChangeX, kGlyphs, sp).second - keyChangeX + sp * 0.45f
                } else {
                    0f
                }
                val tChange = if (mIdx > 0 && measure.timeSignature != null && measure.timeSignature != st.effectiveTime) {
                    measure.timeSignature.also { st.effectiveTime = it }
                } else {
                    null
                }
                var tNumW = 0f
                var tDenW = 0f
                val tWidth = if (tChange != null) {
                    tNumW = measureGlyphWidth(SmuflGlyphs.timeSigNumber(tChange.numerator), emPx)
                    tDenW = measureGlyphWidth(SmuflGlyphs.timeSigNumber(tChange.denominator), emPx)
                    max(tNumW, tDenW).coerceAtLeast(sp * 1.6f) + sp * 0.6f
                } else {
                    0f
                }
                MeasureHeaderPrep(
                    measure = measure,
                    measureOpeningClef = measureOpeningClef,
                    keyChangeGlyphs = kGlyphs,
                    keyWidth = kEnd,
                    timeChange = tChange,
                    timeWidth = tWidth,
                    numW = tNumW,
                    denW = tDenW,
                    activeKeyAfter = st.effectiveKey,
                    activeTimeAfter = st.effectiveTime,
                )
            }

            val maxKeyW = preps.maxOf { it.keyWidth }
            val maxTimeW = preps.maxOf { it.timeWidth }
            val tsStartX = keyChangeX + maxKeyW
            val contentStartX = tsStartX + maxTimeW

            data class PreparedScoreItem(
                val iIdx: Int,
                val item: ScoreItem,
                val startTick: RationalTicks,
                val isOverflow: Boolean,
                val effectiveClef: Clef,
                val midClefBefore: Clef?,
                val midClefWidth: Float,
                val noteWidth: Float,
                val pitches: List<SpelledPitch>,
                val steps: List<Int>,
                val stemDown: Boolean,
                val outOfRangeIndices: Set<Int>,
                val headXOffsets: FloatArray,
                val leftDispPx: Float,
                val rightDispPx: Float,
                val noteAccs: Array<Accidental?>,
                val accColumnByNote: IntArray,
                val colOffsetsFromLeftEdge: FloatArray,
                val totalAccWidth: Float,
                val visualLeftOverhangPx: Float,
                val visualRightExtentPx: Float,
                val boxRightRelPx: Float,
            )

            data class PreparedMeasureStaff(
                val prep: MeasureHeaderPrep,
                val placedTimeChange: ScorePlacedTimeSignature?,
                val preparedItems: List<PreparedScoreItem>,
                val trailingClefs: List<Triple<Int, Clef, Float>>,
                val beamGroups: List<ScoreBeamGroup>,
                val tupletGroups: List<ScoreTupletGroup>,
                val incompleteTupletEndingAt: Map<Int, ScoreTupletGroup>,
                val trailingIncompleteTuplet: ScoreTupletGroup?,
                val trailingCompletedTuplet: Boolean,
                val measureClosingClef: Clef,
                val measureTicks: RationalTicks,
                val measureCapacity: Int,
                val nextBeat1Clef: Clef?,
                val nextBeat1ClefWidth: Float,
            )

            data class MeasureBodyResult(
                val prep: MeasureHeaderPrep,
                val placedTimeChange: ScorePlacedTimeSignature?,
                val itemsInMeasure: MutableList<ScorePlacedItem>,
                val midClefChanges: List<ScorePlacedClefChange>,
                val beamGroups: List<ScoreBeamGroup>,
                val tupletGroups: List<ScoreTupletGroup>,
                val measureClosingClef: Clef,
                val measureTicks: Int,
                val measureCapacity: Int,
                val endBeforeBarClefX: Float,
                val nextBeat1Clef: Clef?,
                val nextBeat1ClefWidth: Float,
            )

            // Pass 1: Analyze items per staff without fixing X so rhythmic slices align across staves.
            val preparedStaves = staffStates.mapIndexed { sIdx, st ->
                val prep = preps[sIdx]
                val measure = prep.measure
                val placedTimeChange = prep.timeChange?.let { tc ->
                    val tsBoxWidth = (prep.timeWidth - sp * 0.6f).coerceAtLeast(sp * 1.6f)
                    ScorePlacedTimeSignature(
                        measureIdx = mIdx,
                        timeSignature = tc,
                        x = tsStartX,
                        width = tsBoxWidth,
                        numWidth = prep.numW,
                        denWidth = prep.denW,
                        topY = st.topStaffY,
                        bottomY = st.bottomStaffY,
                        staff = st.staffId,
                    ).also {
                        st.placedTimeSignatures.add(it)
                        allTimeSignatures.add(it)
                    }
                }
                val measureCapacity = prep.activeTimeAfter.measureCapacityTicks
                val capacityRatio = RationalTicks.of(measureCapacity)
                val activeAlters = mutableMapOf<Int, Int>()
                var measureTicks = RationalTicks.ZERO
                val beamGroups = ScoreEditing.computeBeamGroups(
                    measure = measure,
                    measureIdx = mIdx,
                    openingClef = prep.measureOpeningClef,
                    timeSignature = prep.activeTimeAfter,
                )
                val tupletGroups = ScoreEditing.computeTupletGroups(
                    measure = measure,
                    measureIdx = mIdx,
                    openingClef = prep.measureOpeningClef,
                    timeSignature = prep.activeTimeAfter,
                )
                val incompleteTupletEndingAt = mutableMapOf<Int, ScoreTupletGroup>()
                for (tg in tupletGroups) {
                    if (!tg.isFull) {
                        val lastIdx = tg.itemIndices.lastOrNull() ?: continue
                        incompleteTupletEndingAt[lastIdx] = tg
                    }
                }
                val beamGroupByItemIdx = mutableMapOf<Int, ScoreBeamGroup>()
                for (bg in beamGroups) {
                    for (bIdx in bg.itemIndices) {
                        beamGroupByItemIdx[bIdx] = bg
                    }
                }

                val ledgerExt = SmuflGlyphs.LEGER_LINE_EXTENSION * sp
                val preparedItems = ArrayList<PreparedScoreItem>(measure.items.size)
                for ((iIdx, item) in measure.items.withIndex()) {
                    var midClefBefore: Clef? = null
                    var midClefWidth = 0f
                    if (iIdx > 0) {
                        val midClef = measure.clefChanges[iIdx]
                        if (midClef != null && midClef != st.effectiveClef) {
                            midClefBefore = midClef
                            midClefWidth = max(measureGlyphWidth(midClef.glyph, changeEmPx), sp * 1.9f)
                            st.effectiveClef = midClef
                        }
                    }
                    val startTick = measureTicks
                    val itemTicks = item.exactTicks()
                    val newMeasureTicks = startTick + itemTicks
                    val isOverflow = newMeasureTicks > capacityRatio
                    measureTicks = newMeasureTicks

                    val pitches = item.soundingPitches()
                    val noteWidth = when (item) {
                        is ScoreItem.Rest -> sp * 1.2f
                        is ScoreItem.Note, is ScoreItem.Chord -> if (item.duration == NoteDuration.WHOLE) {
                            SmuflGlyphs.NOTEHEAD_WHOLE_WIDTH * sp
                        } else {
                            SmuflGlyphs.NOTEHEAD_WIDTH * sp
                        }
                    }

                    if (pitches.isNotEmpty()) {
                        val steps = pitches.map { st.effectiveClef.staffStepFromBottom(it) }
                        val bottomStep = steps.first()
                        val topStep = steps.last()
                        val stemDown = beamGroupByItemIdx[iIdx]?.stemDown ?: ((topStep - 4) >= (4 - bottomStep))

                        val outOfRangeIndices = pitches.indices
                            .filterTo(mutableSetOf()) { !st.instrument.isMidiInRange(pitches[it].midiPitch) }

                        val headXOffsets = FloatArray(pitches.size)
                        val dispWidth = (noteWidth - stemThick).coerceAtLeast(0f)
                        if (!stemDown) {
                            var prevNormal = true
                            for (i in 1 until pitches.size) {
                                if (steps[i] - steps[i - 1] <= 1 && prevNormal) {
                                    headXOffsets[i] = dispWidth
                                    prevNormal = false
                                } else {
                                    prevNormal = true
                                }
                            }
                        } else {
                            var nextNormal = true
                            for (i in (pitches.lastIndex - 1) downTo 0) {
                                if (steps[i + 1] - steps[i] <= 1 && nextNormal) {
                                    headXOffsets[i] = -dispWidth
                                    nextNormal = false
                                } else {
                                    nextNormal = true
                                }
                            }
                        }
                        val leftDispPx = -(headXOffsets.minOrNull() ?: 0f).coerceAtMost(0f)
                        val rightDispPx = (headXOffsets.maxOrNull() ?: 0f).coerceAtLeast(0f)

                        val noteAccs = arrayOfNulls<Accidental>(pitches.size)
                        for (i in pitches.indices) {
                            val p = pitches[i]
                            val keyDefault = prep.activeKeyAfter.defaultAlter(p.letter)
                            val dStep = p.diatonicStep
                            val currentAlter = activeAlters[dStep] ?: keyDefault
                            if (p.alter != currentAlter) {
                                noteAccs[i] = when (p.alter) {
                                    1 -> Accidental.SHARP
                                    -1 -> Accidental.FLAT
                                    else -> Accidental.NATURAL
                                }
                                activeAlters[dStep] = p.alter
                            }
                        }

                        val accColumnByNote = IntArray(pitches.size) { -1 }
                        val columnLastStep = mutableListOf<Int>()
                        val columnWidths = mutableListOf<Float>()
                        for (i in pitches.indices.reversed()) {
                            val acc = noteAccs[i] ?: continue
                            val s = steps[i]
                            val w = accidentalWidth(acc, sp)
                            var chosenCol = columnLastStep.indexOfFirst { lastStep -> lastStep - s >= 5 }
                            if (chosenCol < 0) {
                                chosenCol = columnLastStep.size
                                columnLastStep.add(s)
                                columnWidths.add(w)
                            } else {
                                columnLastStep[chosenCol] = s
                                columnWidths[chosenCol] = max(columnWidths[chosenCol], w)
                            }
                            accColumnByNote[i] = chosenCol
                        }

                        val accGap = sp * 0.25f
                        val colOffsetsFromLeftEdge = FloatArray(columnWidths.size)
                        var runningOffset = 0f
                        for (c in columnWidths.indices) {
                            runningOffset += columnWidths[c] + accGap
                            colOffsetsFromLeftEdge[c] = runningOffset
                        }
                        val totalAccWidth = if (columnWidths.isNotEmpty()) runningOffset + sp * 0.1f else 0f

                        val hasLedger = bottomStep <= -2 || topStep >= 10
                        val hasUpFlag = !stemDown && iIdx !in beamGroupByItemIdx && item.duration.flagCount > 0
                        val clefBlockLeft = if (midClefBefore != null) midClefWidth + sp * 0.55f else 0f
                        val ledgerLeft = if (hasLedger) leftDispPx + ledgerExt else 0f
                        val visualLeftOverhangPx = max(leftDispPx + totalAccWidth, ledgerLeft) + clefBlockLeft
                        val dotOverhang = if (item.isDotted) sp * 0.85f else 0f
                        val flagOverhang = if (hasUpFlag) sp * 0.65f else 0f
                        val ledgerRight = if (hasLedger) ledgerExt else 0f
                        val visualRightExtentPx = noteWidth + rightDispPx + maxOf(dotOverhang, flagOverhang, ledgerRight)
                        val boxRightRelPx = noteWidth + rightDispPx + (if (item.isDotted) sp * 0.7f else sp * 0.15f)

                        preparedItems.add(
                            PreparedScoreItem(
                                iIdx = iIdx,
                                item = item,
                                startTick = startTick,
                                isOverflow = isOverflow,
                                effectiveClef = st.effectiveClef,
                                midClefBefore = midClefBefore,
                                midClefWidth = midClefWidth,
                                noteWidth = noteWidth,
                                pitches = pitches,
                                steps = steps,
                                stemDown = stemDown,
                                outOfRangeIndices = outOfRangeIndices,
                                headXOffsets = headXOffsets,
                                leftDispPx = leftDispPx,
                                rightDispPx = rightDispPx,
                                noteAccs = noteAccs,
                                accColumnByNote = accColumnByNote,
                                colOffsetsFromLeftEdge = colOffsetsFromLeftEdge,
                                totalAccWidth = totalAccWidth,
                                visualLeftOverhangPx = visualLeftOverhangPx,
                                visualRightExtentPx = visualRightExtentPx,
                                boxRightRelPx = boxRightRelPx,
                            )
                        )
                    } else {
                        val clefBlockLeft = if (midClefBefore != null) midClefWidth + sp * 0.55f else 0f
                        val dotOverhang = if (item.isDotted) sp * 0.85f else 0f
                        val visualRightExtentPx = noteWidth + dotOverhang
                        val boxRightRelPx = noteWidth + (if (item.isDotted) sp * 0.7f else sp * 0.15f)
                        preparedItems.add(
                            PreparedScoreItem(
                                iIdx = iIdx,
                                item = item,
                                startTick = startTick,
                                isOverflow = isOverflow,
                                effectiveClef = st.effectiveClef,
                                midClefBefore = midClefBefore,
                                midClefWidth = midClefWidth,
                                noteWidth = noteWidth,
                                pitches = emptyList(),
                                steps = emptyList(),
                                stemDown = false,
                                outOfRangeIndices = emptySet(),
                                headXOffsets = FloatArray(0),
                                leftDispPx = 0f,
                                rightDispPx = 0f,
                                noteAccs = emptyArray(),
                                accColumnByNote = IntArray(0),
                                colOffsetsFromLeftEdge = FloatArray(0),
                                totalAccWidth = 0f,
                                visualLeftOverhangPx = sp * 0.15f + clefBlockLeft,
                                visualRightExtentPx = visualRightExtentPx,
                                boxRightRelPx = boxRightRelPx,
                            )
                        )
                    }
                }

                val trailingClefs = mutableListOf<Triple<Int, Clef, Float>>()
                for ((clefKey, clefVal) in measure.clefChanges.entries.sortedBy { it.key }) {
                    if (clefKey == 0) continue
                    if (clefKey >= measure.items.size && clefVal != st.effectiveClef) {
                        val ccW = max(measureGlyphWidth(clefVal.glyph, changeEmPx), sp * 1.9f)
                        trailingClefs.add(Triple(clefKey, clefVal, ccW))
                        st.effectiveClef = clefVal
                    }
                }
                val measureClosingClef = st.effectiveClef
                val trailingIncompleteTuplet = if (measure.items.isNotEmpty()) {
                    incompleteTupletEndingAt[measure.items.lastIndex]
                } else {
                    null
                }
                val trailingCompletedTuplet = tupletGroups.lastOrNull()
                    ?.let { it.isFull && it.itemIndices.lastOrNull() == measure.items.lastIndex } == true

                val nextMeasure = st.measures.getOrNull(mIdx + 1)
                val nextBeat1Clef = nextMeasure?.clefChanges?.get(0)
                val barClefW = if (nextMeasure != null && nextBeat1Clef != null && nextBeat1Clef != st.effectiveClef) {
                    max(measureGlyphWidth(nextBeat1Clef.glyph, changeEmPx), sp * 1.9f)
                } else {
                    0f
                }

                PreparedMeasureStaff(
                    prep = prep,
                    placedTimeChange = placedTimeChange,
                    preparedItems = preparedItems,
                    trailingClefs = trailingClefs,
                    beamGroups = beamGroups,
                    tupletGroups = tupletGroups,
                    incompleteTupletEndingAt = incompleteTupletEndingAt,
                    trailingIncompleteTuplet = trailingIncompleteTuplet,
                    trailingCompletedTuplet = trailingCompletedTuplet,
                    measureClosingClef = measureClosingClef,
                    measureTicks = measureTicks,
                    measureCapacity = measureCapacity,
                    nextBeat1Clef = if (barClefW > 0f) nextBeat1Clef else null,
                    nextBeat1ClefWidth = barClefW,
                )
            }

            // Pass 2: Solve shared horizontal coordinates across all rhythmic slices in this measure.
            val sliceTicks = preparedStaves
                .flatMap { ps -> ps.preparedItems.map { it.startTick } }
                .distinct()
                .sorted()
            val tickToSliceIdx = HashMap<RationalTicks, Int>(sliceTicks.size * 2)
            sliceTicks.forEachIndexed { idx, t -> tickToSliceIdx[t] = idx }

            var alignedMeasureEndX = contentStartX + sp * 1.1f
            val sliceX = if (sliceTicks.isNotEmpty()) {
                val sliceCount = sliceTicks.size
                val maxEndTick = maxOf(
                    sliceTicks.last(),
                    preparedStaves.maxOf { it.measureTicks },
                )
                val sliceLeftOverhang = FloatArray(sliceCount)
                val sliceRightExtent = FloatArray(sliceCount)
                for (ps in preparedStaves) {
                    for (pi in ps.preparedItems) {
                        val sIdx = tickToSliceIdx[pi.startTick] ?: continue
                        if (pi.visualLeftOverhangPx > sliceLeftOverhang[sIdx]) {
                            sliceLeftOverhang[sIdx] = pi.visualLeftOverhangPx
                        }
                        if (pi.visualRightExtentPx > sliceRightExtent[sIdx]) {
                            sliceRightExtent[sIdx] = pi.visualRightExtentPx
                        }
                    }
                }

                val x0 = max(
                    contentStartX + sp * 1.1f,
                    contentStartX + sp * 0.55f + sliceLeftOverhang[0],
                )
                val intervalW = FloatArray(sliceCount)
                val minOpticalGap = sp * 0.42f
                val minBarlineGap = sp * 0.55f
                for (k in 0 until sliceCount) {
                    val nextTick = if (k < sliceCount - 1) sliceTicks[k + 1] else maxEndTick
                    val deltaTicks = (nextTick - sliceTicks[k]).toDoubleTicks()
                    val idealW = if (deltaTicks > 0.0) {
                        idealAdvanceSpacesForTicks(deltaTicks) * sp
                    } else {
                        sp * 1.85f
                    }
                    val rodW = if (k < sliceCount - 1) {
                        sliceRightExtent[k] + minOpticalGap + sliceLeftOverhang[k + 1]
                    } else {
                        sliceRightExtent[k] + minBarlineGap
                    }
                    intervalW[k] = max(idealW, rodW)
                }

                // Preserve mid-measure incomplete tuplet entry space between an incomplete group
                // and the following item on the same staff.
                for (ps in preparedStaves) {
                    for ((iIdx, incTg) in ps.incompleteTupletEndingAt) {
                        if (iIdx >= ps.preparedItems.lastIndex) continue
                        val itemA = ps.preparedItems[iIdx]
                        val itemB = ps.preparedItems[iIdx + 1]
                        val sA = tickToSliceIdx[itemA.startTick] ?: continue
                        val sB = tickToSliceIdx[itemB.startTick] ?: continue
                        if (sB <= sA) continue
                        val remainingSlots = incompleteTupletRemainingSlots(ps.prep.measure, incTg)
                        val tupletEntryGap = remainingSlots * sp * 3.4f + sp * 0.8f
                        val nextLeftRel = itemB.leftDispPx + itemB.totalAccWidth +
                            (if (itemB.midClefBefore != null) itemB.midClefWidth + sp * 0.55f else 0f) +
                            sp * 0.15f
                        val requiredSpan = itemA.boxRightRelPx + tupletEntryGap + nextLeftRel
                        var currentSpan = 0f
                        for (k in sA until sB) currentSpan += intervalW[k]
                        if (currentSpan < requiredSpan) {
                            intervalW[sB - 1] += (requiredSpan - currentSpan)
                        }
                    }
                }

                val xs = FloatArray(sliceCount)
                xs[0] = x0
                for (k in 1 until sliceCount) {
                    xs[k] = xs[k - 1] + intervalW[k - 1]
                }
                alignedMeasureEndX = xs[sliceCount - 1] + intervalW[sliceCount - 1] + sp * 0.6f
                xs
            } else {
                FloatArray(0)
            }

            // Pass 3: Place items on each staff using the shared time-slice coordinates.
            val bodies = staffStates.mapIndexed { sIdx, st ->
                val pStaff = preparedStaves[sIdx]
                val prep = pStaff.prep
                val measure = prep.measure
                val capacityRatio = RationalTicks.of(pStaff.measureCapacity)
                val itemsInMeasure = ArrayList<ScorePlacedItem>(pStaff.preparedItems.size)
                val midClefChanges = mutableListOf<ScorePlacedClefChange>()

                for (pi in pStaff.preparedItems) {
                    val itemX = sliceX[tickToSliceIdx[pi.startTick] ?: 0]
                    val midClef = pi.midClefBefore
                    if (midClef != null) {
                        val ccW = pi.midClefWidth
                        val ccX = itemX - pi.leftDispPx - pi.totalAccWidth - sp * 0.55f - ccW
                        val cc = ScorePlacedClefChange(
                            measureIdx = mIdx,
                            itemIdx = pi.iIdx,
                            clef = midClef,
                            x = ccX,
                            width = ccW,
                            topY = st.topStaffY,
                            bottomY = st.bottomStaffY,
                            staff = st.staffId,
                        )
                        midClefChanges.add(cc)
                        st.placedClefChanges.add(cc)
                        allClefChanges.add(cc)
                    }

                    val item = pi.item
                    if (pi.pitches.isNotEmpty()) {
                        val bottomStep = pi.steps.first()
                        val topStep = pi.steps.last()
                        val isOutOfRange = pi.outOfRangeIndices.isNotEmpty()
                        val chordAnchorLeft = itemX - pi.leftDispPx
                        val chordNotes = pi.pitches.mapIndexed { idx, p ->
                            val s = pi.steps[idx]
                            val y = st.yForStep(s)
                            val hx = itemX + pi.headXOffsets[idx]
                            val acc = pi.noteAccs[idx]
                            val col = pi.accColumnByNote[idx]
                            val accX = if (acc != null && col >= 0) {
                                chordAnchorLeft - pi.colOffsetsFromLeftEdge[col]
                            } else {
                                null
                            }
                            ScorePlacedChordNote(
                                noteIdx = idx,
                                pitch = p,
                                staffStep = s,
                                accidental = acc,
                                headX = hx,
                                y = y,
                                accX = accX,
                                boxLeft = min(hx - sp * 0.12f, accX ?: hx),
                                boxTop = y - sp * 0.55f,
                                boxRight = hx + pi.noteWidth + sp * 0.12f,
                                boxBottom = y + sp * 0.55f,
                            )
                        }

                        val minLeft = chordNotes.minOf { it.boxLeft }
                        val maxRight = max(
                            chordNotes.maxOf { it.headX } + pi.noteWidth + (if (item.isDotted) sp * 0.7f else sp * 0.15f),
                            chordNotes.maxOf { it.boxRight },
                        )
                        val topY = st.yForStep(topStep)
                        val bottomY = st.yForStep(bottomStep)
                        val (boxTop, boxBottom) = if (!item.duration.hasStem) {
                            (topY - sp * 0.75f) to (bottomY + sp * 0.75f)
                        } else {
                            val stemLen = stemLengthSpaces(item.duration) * sp
                            if (!pi.stemDown) {
                                (topY - stemLen - sp * 0.25f) to (bottomY + sp * 0.75f)
                            } else {
                                (topY - sp * 0.75f) to (bottomY + stemLen + sp * 0.25f)
                            }
                        }

                        itemsInMeasure.add(
                            ScorePlacedItem(
                                measureIdx = mIdx,
                                itemIdx = pi.iIdx,
                                globalIdx = st.globalIdx++,
                                item = item,
                                x = itemX,
                                width = pi.noteWidth,
                                accidental = chordNotes.firstOrNull()?.accidental,
                                staffStep = bottomStep,
                                centerY = (topY + bottomY) * 0.5f,
                                boxLeft = minLeft,
                                boxTop = boxTop,
                                boxRight = maxRight,
                                boxBottom = boxBottom,
                                isOverflow = pi.isOverflow,
                                chordNotes = chordNotes,
                                stemDown = pi.stemDown,
                                effectiveClef = pi.effectiveClef,
                                staff = st.staffId,
                                isOutOfRange = isOutOfRange,
                                outOfRangeChordIndices = pi.outOfRangeIndices,
                            )
                        )
                    } else {
                        val itemCenterY = st.yForStep(if (item.duration == NoteDuration.WHOLE) 6 else 4)
                        itemsInMeasure.add(
                            ScorePlacedItem(
                                measureIdx = mIdx,
                                itemIdx = pi.iIdx,
                                globalIdx = st.globalIdx++,
                                item = item,
                                x = itemX,
                                width = pi.noteWidth,
                                accidental = null,
                                staffStep = null,
                                centerY = itemCenterY,
                                boxLeft = itemX - sp * 0.15f,
                                boxTop = st.topStaffY + sp * 0.2f,
                                boxRight = itemX + pi.noteWidth + (if (item.isDotted) sp * 0.7f else sp * 0.15f),
                                boxBottom = st.bottomStaffY - sp * 0.2f,
                                isOverflow = pi.isOverflow,
                                effectiveClef = pi.effectiveClef,
                                staff = st.staffId,
                            )
                        )
                    }
                }

                var tailX = if (itemsInMeasure.isNotEmpty()) {
                    val lastItem = itemsInMeasure.last()
                    val lastPrep = pStaff.preparedItems.last()
                    max(
                        lastItem.boxRight,
                        lastItem.x + advanceForItem(lastItem.item, sp) + lastPrep.rightDispPx,
                    )
                } else {
                    contentStartX + sp * 1.1f
                }

                if (pStaff.trailingClefs.isNotEmpty()) {
                    var clefCursorX = if (itemsInMeasure.isNotEmpty()) {
                        itemsInMeasure.last().boxRight + sp * 0.35f
                    } else {
                        contentStartX + sp * 3.1f
                    }
                    for ((clefKey, clefVal, ccW) in pStaff.trailingClefs) {
                        val cc = ScorePlacedClefChange(
                            measureIdx = mIdx,
                            itemIdx = clefKey,
                            clef = clefVal,
                            x = clefCursorX,
                            width = ccW,
                            topY = st.topStaffY,
                            bottomY = st.bottomStaffY,
                            staff = st.staffId,
                        )
                        midClefChanges.add(cc)
                        st.placedClefChanges.add(cc)
                        allClefChanges.add(cc)
                        clefCursorX += ccW + sp * 0.55f
                    }
                    tailX = max(tailX, clefCursorX)
                }

                val trailingPad = when {
                    pStaff.trailingIncompleteTuplet != null -> {
                        val remainingSlots = incompleteTupletRemainingSlots(measure, pStaff.trailingIncompleteTuplet)
                        remainingSlots * sp * 3.4f + sp * 1.4f
                    }
                    activeTupletRatio != null && measure.items.isEmpty() -> {
                        max(sp * 6f, activeTupletRatio.first * sp * 3.0f)
                    }
                    activeTupletRatio != null && !pStaff.trailingCompletedTuplet && pStaff.measureTicks < capacityRatio -> {
                        sp * 0.6f + activeTupletRatio.first * sp * 2.6f
                    }
                    measure.items.isEmpty() -> sp * 6f
                    else -> sp * 0.6f
                }
                val staffEndBeforeBarClef = max(alignedMeasureEndX, tailX + trailingPad)

                MeasureBodyResult(
                    prep = prep,
                    placedTimeChange = pStaff.placedTimeChange,
                    itemsInMeasure = itemsInMeasure,
                    midClefChanges = midClefChanges,
                    beamGroups = pStaff.beamGroups,
                    tupletGroups = pStaff.tupletGroups,
                    measureClosingClef = pStaff.measureClosingClef,
                    measureTicks = pStaff.measureTicks.toDoubleTicks().roundToInt(),
                    measureCapacity = pStaff.measureCapacity,
                    endBeforeBarClefX = staffEndBeforeBarClef,
                    nextBeat1Clef = pStaff.nextBeat1Clef,
                    nextBeat1ClefWidth = pStaff.nextBeat1ClefWidth,
                )
            }

            val maxEndBeforeBarClef = bodies.maxOf { it.endBeforeBarClefX }
            val maxBarClefBlock = bodies.maxOf { body ->
                if (body.nextBeat1Clef != null) body.nextBeat1ClefWidth + sp * 0.5f else 0f
            }
            val barlineX = maxEndBeforeBarClef + maxBarClefBlock

            for ((sIdx, st) in staffStates.withIndex()) {
                val body = bodies[sIdx]
                val prep = body.prep
                val measure = prep.measure
                val barlineClefChange = if (body.nextBeat1Clef != null) {
                    val ccW = body.nextBeat1ClefWidth
                    val cc = ScorePlacedClefChange(
                        measureIdx = mIdx + 1,
                        itemIdx = 0,
                        clef = body.nextBeat1Clef,
                        x = barlineX - ccW - sp * 0.5f,
                        width = ccW,
                        topY = st.topStaffY,
                        bottomY = st.bottomStaffY,
                        staff = st.staffId,
                    )
                    st.placedClefChanges.add(cc)
                    allClefChanges.add(cc)
                    st.effectiveClef = body.nextBeat1Clef
                    cc
                } else {
                    null
                }

                val placedBeamGroups = buildPlacedBeamGroups(
                    measureIdx = mIdx,
                    beamGroups = body.beamGroups,
                    itemsInMeasure = body.itemsInMeasure,
                    sp = sp,
                    stemThick = stemThick,
                )
                val placedTupletGroups = buildPlacedTupletGroups(
                    measureIdx = mIdx,
                    tupletGroups = body.tupletGroups,
                    itemsInMeasure = body.itemsInMeasure,
                    midClefChanges = body.midClefChanges,
                    placedBeamGroups = placedBeamGroups,
                    barlineX = barlineX,
                    topStaffY = st.topStaffY,
                    bottomStaffY = st.bottomStaffY,
                    sp = sp,
                    measureGlyphWidth = measureGlyphWidth,
                )
                val placedDynamicsForMeasure = if (measure.dynamics.isEmpty()) {
                    emptyList()
                } else {
                    buildList {
                        for ((dIdx, marking) in measure.dynamics.entries.sortedBy { it.key }) {
                            val anchorItem = body.itemsInMeasure.getOrNull(dIdx)
                            val rawX = when {
                                anchorItem != null -> anchorItem.x
                                body.itemsInMeasure.isNotEmpty() -> (body.itemsInMeasure.last().boxRight + sp * 1.1f)
                                    .coerceAtMost(barlineX - sp * 2.2f)
                                else -> contentStartX + sp * 0.5f
                            }
                            val estWidth = max(sp * 1.5f, (marking.mark.label.length * 0.9f + 0.55f) * sp)
                            val drawX = rawX - estWidth * 0.22f
                            val baselineY = when (marking.placement) {
                                DynamicPlacement.BELOW -> maxOf(
                                    st.bottomStaffY + sp * 3.1f,
                                    (anchorItem?.boxBottom ?: st.bottomStaffY) + sp * 2.0f,
                                )
                                DynamicPlacement.ABOVE -> minOf(
                                    st.topStaffY - sp * 2.0f,
                                    (anchorItem?.boxTop ?: st.topStaffY) - sp * 0.9f,
                                )
                            }
                            add(
                                ScorePlacedDynamic(
                                    measureIdx = mIdx,
                                    itemIdx = dIdx,
                                    marking = marking,
                                    x = drawX,
                                    baselineY = baselineY,
                                    boxLeft = drawX - sp * 0.15f,
                                    boxTop = baselineY - sp * 1.45f,
                                    boxRight = drawX + estWidth + sp * 0.15f,
                                    boxBottom = baselineY + sp * 0.45f,
                                    staff = st.staffId,
                                )
                            )
                        }
                    }
                }
                st.placedDynamics.addAll(placedDynamicsForMeasure)
                allDynamics.addAll(placedDynamicsForMeasure)

                st.placedMeasures.add(
                    ScorePlacedMeasure(
                        measureIdx = mIdx,
                        measure = measure,
                        startX = mStartX,
                        barlineX = barlineX,
                        items = body.itemsInMeasure,
                        totalTicks = body.measureTicks,
                        capacityTicks = body.measureCapacity,
                        contentStartX = contentStartX,
                        keyChangeX = keyChangeX,
                        keyChangeGlyphs = prep.keyChangeGlyphs,
                        activeKeySignature = prep.activeKeyAfter,
                        timeChange = body.placedTimeChange,
                        activeTimeSignature = prep.activeTimeAfter,
                        clefChanges = body.midClefChanges,
                        barlineClefChange = barlineClefChange,
                        activeOpeningClef = prep.measureOpeningClef,
                        activeClosingClef = body.measureClosingClef,
                        dynamics = placedDynamicsForMeasure,
                        beamGroups = placedBeamGroups,
                        tupletGroups = placedTupletGroups,
                        staff = st.staffId,
                    )
                )
            }
            cursorX = barlineX + sp * 0.6f
        }

        val primaryMeasures = staffStates.first().placedMeasures
        val finalEndX = max(cursorX + sp * 0.8f + rightMargin, minWidthPx)
        val staffEndX = if (primaryMeasures.isNotEmpty()) {
            max(
                primaryMeasures.last().barlineX + SmuflGlyphs.THICK_BARLINE_THICKNESS * sp + sp * 1.5f,
                minWidthPx - rightMargin,
            )
        } else {
            finalEndX - rightMargin
        }

        val staffResults = staffStates.mapIndexed { idx, st ->
            val nextInst = staffStates.getOrNull(idx + 1)?.instrumentIdx
            val placedStaff = ScorePlacedStaff(
                staffId = st.staffId,
                instrument = st.instrument,
                staffSpacePx = sp,
                leftBarX = leftMargin,
                topStaffY = st.topStaffY,
                bottomStaffY = st.bottomStaffY,
                rowTopY = st.rowTopY,
                rowBottomY = st.rowBottomY,
                openingClef = st.openingClef,
                clefX = clefX,
                keyStartX = keyStartX,
                keySteps = st.keySteps,
                keyGlyph = st.keyGlyph,
                keyGlyphStepWidth = st.keyGlyphStepWidth,
                timeSigX = timeSigX,
                timeSigWidth = timeSigWidth,
                numWidth = numWidth,
                denWidth = denWidth,
                numStr = numStr,
                denStr = denStr,
                headerEndX = headerEndX,
                placedMeasures = st.placedMeasures,
                placedTimeSignatures = st.placedTimeSignatures,
                placedClefChanges = st.placedClefChanges,
                placedDynamics = st.placedDynamics,
            )
            StaffLayoutResult(
                staff = placedStaff,
                instrumentIdx = st.instrumentIdx,
                isLastStaffInInstrument = nextInst != st.instrumentIdx,
            )
        }

        val primaryStaff = staffResults.first().staff
        val layout = ScoreLayout(
            totalWidthPx = (staffEndX + rightMargin).coerceAtLeast(minWidthPx),
            totalHeightPx = totalHeight.roundToInt().toFloat(),
            staffSpacePx = sp,
            leftBarX = leftMargin,
            topStaffY = primaryStaff.topStaffY,
            bottomStaffY = primaryStaff.bottomStaffY,
            headerEndX = headerEndX,
            staffEndX = staffEndX,
            openingClef = primaryStaff.openingClef,
            placedMeasures = primaryStaff.placedMeasures,
            placedTimeSignatures = allTimeSignatures,
            placedClefChanges = allClefChanges,
            placedDynamics = allDynamics,
            staves = staffResults.map { it.staff },
            instrumentGroups = instrumentGroups,
            showInstrumentLabels = showInstrumentLabels,
        )
        return staffResults to layout
    }

    /** Computes the [ScoreLayout] without drawing, for hit-testing and coordinate mapping. */
    fun computeLayout(
        score: ScoreData,
        staffSpacePx: Float,
        minWidthPx: Float,
        measureGlyphWidth: (String, Float) -> Float,
    ): ScoreLayout = computeHeaderAndMeasures(
        score = score,
        staffSpacePx = staffSpacePx,
        minWidthPx = minWidthPx,
        measureGlyphWidth = measureGlyphWidth,
        activeTupletRatio = null,
    ).second

    /** Computes the [ScoreLayout] with an optional active tuplet ratio reserving entry space. */
    fun computeLayout(
        score: ScoreData,
        staffSpacePx: Float,
        minWidthPx: Float,
        activeTupletRatio: Pair<Int, Int>?,
        measureGlyphWidth: (String, Float) -> Float,
    ): ScoreLayout = computeHeaderAndMeasures(
        score = score,
        staffSpacePx = staffSpacePx,
        minWidthPx = minWidthPx,
        measureGlyphWidth = measureGlyphWidth,
        activeTupletRatio = activeTupletRatio,
    ).second

    /**
     * Renders [score] onto [target] using SMuFL coordinates scaled by [staffSpacePx]
     * (`1.0 staffSpace = 1/4 em`).
     */
    fun render(
        score: ScoreData,
        target: ScoreDrawTarget,
        staffSpacePx: Float,
        minWidthPx: Float,
        colors: ScoreRenderColors,
        activeItemGlobalIndex: Int? = null,
        selectedLocations: Set<ScoreItemLocation> = emptySet(),
        selectedMeasureIndices: Set<Int> = emptySet(),
        selectedClefChange: ScoreClefLocation? = null,
        selectedDynamics: Set<ScoreDynamicLocation> = emptySet(),
        ghostPreview: ScoreGhostPreview? = null,
        rubberBand: ScoreRubberBand? = null,
        cursorPosition: ScoreCursorPosition? = null,
        isStepRecording: Boolean = false,
        activeItemsByStaff: Map<ScoreStaffId, Int> = emptyMap(),
        activeTupletRatio: Pair<Int, Int>? = null,
    ): ScoreLayout {
        val (_, layout) = computeHeaderAndMeasures(
            score = score,
            staffSpacePx = staffSpacePx,
            minWidthPx = minWidthPx,
            measureGlyphWidth = target::measureGlyphWidth,
            activeTupletRatio = activeTupletRatio,
        )
        val sp = layout.staffSpacePx
        val emPx = sp * 4f
        val changeEmPx = emPx * CLEF_CHANGE_SCALE
        val staffEndX = layout.staffEndX
        val staffLineThick = SmuflGlyphs.STAFF_LINE_THICKNESS * sp
        val thinBar = SmuflGlyphs.THIN_BARLINE_THICKNESS * sp
        val thickBar = SmuflGlyphs.THICK_BARLINE_THICKNESS * sp
        val stemThick = SmuflGlyphs.STEM_THICKNESS * sp
        val ledgerThick = SmuflGlyphs.LEGER_LINE_THICKNESS * sp
        val ledgerExt = SmuflGlyphs.LEGER_LINE_EXTENSION * sp

        // 0. Draw instrument labels & Grand Staff braces on the left system margin
        if (layout.showInstrumentLabels) {
            for (grp in layout.instrumentGroups) {
                val midY = (grp.topStaffY + grp.bottomStaffY) * 0.5f
                target.drawText(
                    text = grp.instrument.name,
                    x = grp.labelLeft,
                    baselineY = midY + sp * 0.15f,
                    fontSizePx = sp * 1.35f,
                    color = colors.ink,
                )
                val rangeText = "${ScoreInstrumentPart.formatMidiPitch(grp.instrument.minMidiPitch)}–" +
                    ScoreInstrumentPart.formatMidiPitch(grp.instrument.maxMidiPitch)
                target.drawText(
                    text = rangeText,
                    x = grp.labelLeft,
                    baselineY = midY + sp * 1.45f,
                    fontSizePx = sp * 0.95f,
                    color = colors.measureNumber,
                )
                if (grp.instrument.staffCount >= 2) {
                    drawGrandStaffBrace(
                        target = target,
                        x = grp.braceX,
                        topY = grp.topStaffY,
                        bottomY = grp.bottomStaffY,
                        sp = sp,
                        color = colors.ink,
                    )
                }
            }
        }

        // 1. Render every instrument staff
        for ((staffIdx, ps) in layout.staves.withIndex()) {
            val staffActiveIdx = activeItemsByStaff[ps.staffId]
                ?: if (ps.staffId == ScoreStaffId(0, 0)) activeItemGlobalIndex else null

            for (lineIdx in 0..4) {
                val y = ps.yForStep(lineIdx * 2)
                target.drawLine(ps.leftBarX, y, staffEndX, y, staffLineThick, colors.staffLine)
            }

            val clefBaselineY = ps.yForStep(ps.openingClef.glyphLineFromBottom * 2)
            target.drawGlyph(ps.openingClef.glyph, ps.clefX, clefBaselineY, emPx, colors.ink)

            ps.keySteps.forEachIndexed { idx, stepOffset ->
                val kx = ps.keyStartX + idx * ps.keyGlyphStepWidth
                target.drawGlyph(ps.keyGlyph, kx, ps.yForStep(stepOffset), emPx, colors.ink)
            }

            val headerNumX = ps.timeSigX + (ps.timeSigWidth - ps.numWidth) * 0.5f
            val headerDenX = ps.timeSigX + (ps.timeSigWidth - ps.denWidth) * 0.5f
            target.drawGlyph(ps.numStr, headerNumX, ps.yForStep(6), emPx, colors.ink)
            target.drawGlyph(ps.denStr, headerDenX, ps.yForStep(2), emPx, colors.ink)

            for ((idx, pm) in ps.placedMeasures.withIndex()) {
                if (pm.measureIdx in selectedMeasureIndices) {
                    val hlLeft = if (idx == 0) pm.startX else ps.placedMeasures[idx - 1].barlineX
                    val hlTop = ps.topStaffY - sp * 0.55f
                    val hlBottom = ps.bottomStaffY + sp * 0.55f
                    val borderThick = (sp * 0.12f).coerceAtLeast(1f)
                    target.fillRect(hlLeft, hlTop, (pm.barlineX - hlLeft).coerceAtLeast(1f), hlBottom - hlTop, colors.selectedBoxFill)
                    target.drawLine(hlLeft, hlTop, pm.barlineX, hlTop, borderThick, colors.selectedBoxBorder)
                    target.drawLine(hlLeft, hlBottom, pm.barlineX, hlBottom, borderThick, colors.selectedBoxBorder)
                }

                if (pm.keyChangeGlyphs.isNotEmpty()) {
                    val (glyphXs, _) = keyChangeGlyphLayout(pm.keyChangeX, pm.keyChangeGlyphs, sp)
                    pm.keyChangeGlyphs.forEachIndexed { gIdx, (kStep, acc) ->
                        target.drawGlyph(acc.glyph, glyphXs[gIdx], ps.yForStep(kStep), emPx, colors.ink)
                    }
                }

                pm.timeChange?.let { ts ->
                    val cNumStr = SmuflGlyphs.timeSigNumber(ts.timeSignature.numerator)
                    val cDenStr = SmuflGlyphs.timeSigNumber(ts.timeSignature.denominator)
                    val cNumX = ts.x + (ts.width - ts.numWidth) * 0.5f
                    val cDenX = ts.x + (ts.width - ts.denWidth) * 0.5f
                    target.drawGlyph(cNumStr, cNumX, ps.yForStep(6), emPx, colors.ink)
                    target.drawGlyph(cDenStr, cDenX, ps.yForStep(2), emPx, colors.ink)
                }

                if (staffIdx == 0 && idx > 0) {
                    target.drawText(
                        text = pm.measure.number.toString(),
                        x = pm.startX - sp * 0.35f,
                        baselineY = ps.topStaffY - sp * 1.1f,
                        fontSizePx = sp * 1.15f,
                        color = colors.measureNumber,
                    )
                }

                for (pi in pm.items) {
                    val isSelected = pi.location in selectedLocations
                    val selectedChordIndices = if (isSelected) {
                        emptySet()
                    } else {
                        selectedLocations.mapNotNull { loc ->
                            if (loc.staff == pi.staff && loc.measureIdx == pi.measureIdx && loc.itemIdx == pi.itemIdx) {
                                loc.chordNoteIdx
                            } else {
                                null
                            }
                        }.toSet()
                    }
                    val isPlaying = pi.globalIdx == staffActiveIdx

                    fun drawSelectionBox(left: Float, top: Float, right: Float, bottom: Float) {
                        val pad = sp * 0.25f
                        val rx = left - pad
                        val ry = top - pad
                        val rw = (right - left) + pad * 2f
                        val rh = (bottom - top) + pad * 2f
                        target.fillRect(rx, ry, rw, rh, colors.selectedBoxFill)
                        val borderThick = (sp * 0.12f).coerceAtLeast(1f)
                        target.drawLine(rx, ry, rx + rw, ry, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx, ry + rh, rx + rw, ry + rh, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx, ry, rx, ry + rh, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx + rw, ry, rx + rw, ry + rh, borderThick, colors.selectedBoxBorder)
                    }

                    if (isSelected) {
                        drawSelectionBox(pi.boxLeft, pi.boxTop, pi.boxRight, pi.boxBottom)
                    } else {
                        for (cnIdx in selectedChordIndices) {
                            pi.chordNotes.getOrNull(cnIdx)?.let { cn ->
                                drawSelectionBox(cn.boxLeft, cn.boxTop, cn.boxRight, cn.boxBottom)
                            }
                        }
                    }

                    val allOutOfRange = pi.chordNotes.isNotEmpty() &&
                        pi.outOfRangeChordIndices.size == pi.chordNotes.size
                    val itemColor = when {
                        isPlaying -> colors.activeNote
                        pi.isOverflow || allOutOfRange -> colors.overflowNote
                        isSelected -> colors.selectedNote
                        else -> colors.ink
                    }
                    val chordNoteHighlightColor = when {
                        isPlaying -> colors.activeNote
                        pi.isOverflow -> colors.overflowNote
                        else -> colors.selectedNote
                    }
                    val outOfRangeNoteColor = if (isPlaying) colors.activeNote else colors.overflowNote

                    drawScoreItem(
                        target = target,
                        yForStep = ps::yForStep,
                        item = pi.item,
                        x = pi.x,
                        staffStep = pi.staffStep,
                        accidental = pi.accidental,
                        color = itemColor,
                        sp = sp,
                        emPx = emPx,
                        stemThick = stemThick,
                        ledgerThick = ledgerThick,
                        ledgerExt = ledgerExt,
                        placedChordNotes = pi.chordNotes,
                        stemDownOverride = pi.stemDown,
                        selectedChordNoteIndices = selectedChordIndices,
                        selectedNoteColor = chordNoteHighlightColor,
                        outOfRangeChordNoteIndices = pi.outOfRangeChordIndices,
                        outOfRangeColor = outOfRangeNoteColor,
                        suppressFlags = pi.isBeamed,
                        stemTipYOverride = pi.stemTipYOverride,
                    )
                }

                for (pbg in pm.beamGroups) {
                    val groupItems = pbg.itemIndices.mapNotNull { pm.items.getOrNull(it) }
                    if (groupItems.size < 2) continue
                    val beamColor = when {
                        groupItems.any { staffActiveIdx == it.globalIdx } -> colors.activeNote
                        groupItems.all { it.isOverflow || (it.chordNotes.isNotEmpty() && it.outOfRangeChordIndices.size == it.chordNotes.size) } ->
                            colors.overflowNote
                        groupItems.all { it.location in selectedLocations } -> colors.selectedNote
                        else -> colors.ink
                    }
                    drawBeamGroup(
                        target = target,
                        beamGroup = pbg,
                        itemsInGroup = groupItems,
                        sp = sp,
                        stemThick = stemThick,
                        color = beamColor,
                    )
                }

                for (ptg in pm.tupletGroups) {
                    val groupItems = ptg.itemIndices.mapNotNull { pm.items.getOrNull(it) }
                    if (groupItems.isEmpty()) continue
                    val tupletColor = when {
                        !ptg.isFull -> colors.incompleteTuplet
                        groupItems.any { staffActiveIdx == it.globalIdx } -> colors.activeNote
                        groupItems.all { it.isOverflow || (it.chordNotes.isNotEmpty() && it.outOfRangeChordIndices.size == it.chordNotes.size) } ->
                            colors.overflowNote
                        groupItems.all { it.location in selectedLocations } -> colors.selectedNote
                        else -> colors.ink
                    }
                    drawTupletGroup(
                        target = target,
                        tupletGroup = ptg,
                        sp = sp,
                        color = tupletColor,
                    )
                }

                val clefsToDraw = buildList {
                    addAll(pm.clefChanges)
                    pm.barlineClefChange?.let { add(it) }
                }
                for (cc in clefsToDraw) {
                    val isClefSelected = cc.location == selectedClefChange
                    if (isClefSelected) {
                        val pad = sp * 0.25f
                        val rx = cc.x - pad
                        val ry = ps.topStaffY - sp * 0.4f
                        val rw = cc.width + pad * 2f
                        val rh = (ps.bottomStaffY - ps.topStaffY) + sp * 0.8f
                        target.fillRect(rx, ry, rw, rh, colors.selectedBoxFill)
                        val borderThick = (sp * 0.12f).coerceAtLeast(1f)
                        target.drawLine(rx, ry, rx + rw, ry, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx, ry + rh, rx + rw, ry + rh, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx, ry, rx, ry + rh, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx + rw, ry, rx + rw, ry + rh, borderThick, colors.selectedBoxBorder)
                    }
                    val clefColor = if (isClefSelected) colors.selectedNote else colors.ink
                    val clefY = ps.yForStep(cc.clef.glyphLineFromBottom * 2)
                    target.drawGlyph(cc.clef.glyph, cc.x, clefY, changeEmPx, clefColor)
                }

                for (pd in pm.dynamics) {
                    val isDynSelected = pd.location in selectedDynamics
                    if (isDynSelected) {
                        val pad = sp * 0.2f
                        val rx = pd.boxLeft - pad
                        val ry = pd.boxTop - pad
                        val rw = (pd.boxRight - pd.boxLeft) + pad * 2f
                        val rh = (pd.boxBottom - pd.boxTop) + pad * 2f
                        target.fillRect(rx, ry, rw, rh, colors.selectedBoxFill)
                        val borderThick = (sp * 0.12f).coerceAtLeast(1f)
                        target.drawLine(rx, ry, rx + rw, ry, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx, ry + rh, rx + rw, ry + rh, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx, ry, rx, ry + rh, borderThick, colors.selectedBoxBorder)
                        target.drawLine(rx + rw, ry, rx + rw, ry + rh, borderThick, colors.selectedBoxBorder)
                    }
                    val dynColor = if (isDynSelected) colors.selectedNote else colors.ink
                    target.drawGlyph(pd.marking.mark.glyph, pd.x, pd.baselineY, emPx, dynColor)
                }
            }

            // Ties for this staff (within measures and across barlines)
            drawTies(
                target = target,
                staffMeasures = ps.placedMeasures,
                sp = sp,
                colors = colors,
                selectedLocations = selectedLocations,
                activeItemGlobalIndex = staffActiveIdx,
            )
        }

        // 2. Shared barlines per instrument group (spanning single or Grand Staff height cleanly)
        val firstMeasures = layout.staves.firstOrNull()?.placedMeasures.orEmpty()
        for ((idx, pm) in firstMeasures.withIndex()) {
            val isLast = idx == firstMeasures.lastIndex
            for (grp in layout.instrumentGroups) {
                if (isLast) {
                    val sep = SmuflGlyphs.THIN_THICK_SEPARATION * sp
                    val doubleBarEndX = max(pm.barlineX + thickBar + sep + thinBar, min(staffEndX, pm.barlineX + sp * 1.2f))
                    val thickX = doubleBarEndX - thickBar * 0.5f
                    val thinX = thickX - sep - thickBar * 0.5f - thinBar * 0.5f
                    target.drawLine(thinX, grp.topStaffY, thinX, grp.bottomStaffY, thinBar, colors.ink)
                    target.drawLine(thickX, grp.topStaffY, thickX, grp.bottomStaffY, thickBar, colors.ink)
                } else {
                    target.drawLine(pm.barlineX, grp.topStaffY, pm.barlineX, grp.bottomStaffY, thinBar, colors.ink)
                }
            }
        }

        // Initial left system barline connecting all staves cleanly
        val sysTopY = layout.staves.firstOrNull()?.topStaffY ?: layout.topStaffY
        val sysBottomY = layout.staves.lastOrNull()?.bottomStaffY ?: layout.bottomStaffY
        target.drawLine(layout.leftBarX, sysTopY, layout.leftBarX, sysBottomY, thinBar, colors.ink)

        // Optional ghost preview while hovering in entry mode
        if (ghostPreview != null) {
            val ghostStaff = layout.staffForId(ghostPreview.staff)
            val yFn: (Int) -> Float = ghostStaff?.let { it::yForStep } ?: { step -> layout.yForStep(step, ghostPreview.staff) }
            val ghostItem = if (ghostPreview.isRest) {
                ScoreItem.Rest(ghostPreview.duration, isDotted = ghostPreview.isDotted)
            } else {
                ScoreItem.Note(
                    pitch = SpelledPitch(NoteLetter.C, 0, 4),
                    duration = ghostPreview.duration,
                    isDotted = ghostPreview.isDotted,
                    isStaccato = ghostPreview.isStaccato,
                )
            }
            drawScoreItem(
                target = target,
                yForStep = yFn,
                item = ghostItem,
                x = ghostPreview.x,
                staffStep = ghostPreview.staffStep,
                accidental = ghostPreview.accidental,
                color = colors.ghostNote,
                sp = sp,
                emPx = emPx,
                stemThick = stemThick,
                ledgerThick = ledgerThick,
                ledgerExt = ledgerExt,
            )
            val ghostTupletText = ghostPreview.tupletText
            if (ghostTupletText != null) {
                val staffTop = ghostStaff?.topStaffY ?: layout.topStaffY
                val bracketY = min(staffTop - sp * 1.4f, yFn(ghostPreview.staffStep) - sp * 1.6f)
                val barLeft = ghostPreview.x - sp * 0.25f
                val targetSlots = activeTupletRatio?.first ?: 3
                val desiredRight = ghostPreview.x + targetSlots * sp * 2.5f
                val enclosingPm = ghostStaff?.placedMeasures?.firstOrNull {
                    ghostPreview.x >= it.startX && ghostPreview.x <= it.barlineX
                }
                val barRight = if (enclosingPm != null) {
                    min(max(ghostPreview.x + sp * 2.8f, enclosingPm.barlineX - sp * 0.55f), desiredRight)
                } else {
                    desiredRight
                }
                val tupletEmPx = sp * 3.2f
                val tupletGlyph = SmuflGlyphs.tupletNumber(ghostTupletText)
                val numW = (ghostTupletText.length * sp * 0.65f).coerceAtLeast(sp * 0.7f)
                drawTupletBracketAndLabel(
                    target = target,
                    startX = barLeft,
                    endX = barRight,
                    bracketY = bracketY,
                    aboveStaff = true,
                    drawBracket = true,
                    glyph = tupletGlyph,
                    numberX = (barLeft + barRight) * 0.5f,
                    numberBaselineY = bracketY + sp * 0.52f,
                    numberWidth = numW,
                    numberEmPx = tupletEmPx,
                    sp = sp,
                    color = colors.incompleteTuplet,
                )
            }
        }

        // Optional vertical insertion / step-record cursor on its target staff
        if (cursorPosition != null) {
            val cursorStaff = layout.staffForId(cursorPosition.staff)
            val cx = cursorStaff?.cursorXFor(cursorPosition)
            if (cx != null) {
                val cursorColor = if (isStepRecording) colors.stepCursor else colors.cursor
                val cursorTop = cursorStaff.topStaffY - sp * 1.35f
                val cursorBottom = cursorStaff.bottomStaffY + sp * 1.35f
                val cursorStroke = max(2f, sp * 0.2f)
                val capHalf = sp * 0.35f
                target.drawLine(cx, cursorTop, cx, cursorBottom, cursorStroke, cursorColor)
                target.drawLine(cx - capHalf, cursorTop, cx + capHalf, cursorTop, cursorStroke * 0.8f, cursorColor)
                target.drawLine(cx - capHalf, cursorBottom, cx + capHalf, cursorBottom, cursorStroke * 0.8f, cursorColor)
            }
        }

        // Optional rubber-band selection rectangle
        if (rubberBand != null) {
            val w = rubberBand.right - rubberBand.left
            val h = rubberBand.bottom - rubberBand.top
            target.fillRect(rubberBand.left, rubberBand.top, w, h, colors.selectedBoxFill)
            val stroke = 1.25f
            target.drawLine(rubberBand.left, rubberBand.top, rubberBand.right, rubberBand.top, stroke, colors.selectedBoxBorder)
            target.drawLine(rubberBand.left, rubberBand.bottom, rubberBand.right, rubberBand.bottom, stroke, colors.selectedBoxBorder)
            target.drawLine(rubberBand.left, rubberBand.top, rubberBand.left, rubberBand.bottom, stroke, colors.selectedBoxBorder)
            target.drawLine(rubberBand.right, rubberBand.top, rubberBand.right, rubberBand.bottom, stroke, colors.selectedBoxBorder)
        }

        return layout
    }

    private fun drawGrandStaffBrace(
        target: ScoreDrawTarget,
        x: Float,
        topY: Float,
        bottomY: Float,
        sp: Float,
        color: Color,
    ) {
        val midY = (topY + bottomY) * 0.5f
        val braceStroke = (sp * 0.24f).coerceAtLeast(1.8f)
        val tipX = x - sp * 0.55f
        val bellyX = x + sp * 0.15f
        val controlX = x - sp * 0.85f
        target.drawCubicBezier(
            x0 = x,
            y0 = topY,
            cx1 = controlX,
            cy1 = topY + (midY - topY) * 0.32f,
            cx2 = bellyX,
            cy2 = midY - (midY - topY) * 0.28f,
            x3 = tipX,
            y3 = midY,
            strokeWidth = braceStroke,
            color = color,
        )
        target.drawCubicBezier(
            x0 = tipX,
            y0 = midY,
            cx1 = bellyX,
            cy1 = midY + (bottomY - midY) * 0.28f,
            cx2 = controlX,
            cy2 = bottomY - (bottomY - midY) * 0.32f,
            x3 = x,
            y3 = bottomY,
            strokeWidth = braceStroke,
            color = color,
        )
    }

    private fun drawScoreItem(
        target: ScoreDrawTarget,
        yForStep: (Int) -> Float,
        item: ScoreItem,
        x: Float,
        staffStep: Int?,
        accidental: Accidental?,
        color: Color,
        sp: Float,
        emPx: Float,
        stemThick: Float,
        ledgerThick: Float,
        ledgerExt: Float,
        placedChordNotes: List<ScorePlacedChordNote> = emptyList(),
        stemDownOverride: Boolean? = null,
        selectedChordNoteIndices: Set<Int> = emptySet(),
        selectedNoteColor: Color = color,
        outOfRangeChordNoteIndices: Set<Int> = emptySet(),
        outOfRangeColor: Color = color,
        suppressFlags: Boolean = false,
        stemTipYOverride: Float? = null,
    ) {
        when (item) {
            is ScoreItem.Note, is ScoreItem.Chord -> {
                val noteheadWidth = if (item.duration == NoteDuration.WHOLE) {
                    SmuflGlyphs.NOTEHEAD_WHOLE_WIDTH * sp
                } else {
                    SmuflGlyphs.NOTEHEAD_WIDTH * sp
                }
                val notesToDraw = if (placedChordNotes.isNotEmpty()) {
                    placedChordNotes
                } else {
                    val s = staffStep ?: 4
                    val ny = yForStep(s)
                    val accX = accidental?.let { acc -> x - accidentalWidth(acc, sp) - sp * 0.25f }
                    val fallbackPitch = when (item) {
                        is ScoreItem.Note -> item.pitch
                        is ScoreItem.Chord -> item.pitches.first()
                        is ScoreItem.Rest -> SpelledPitch(NoteLetter.C, 0, 4)
                    }
                    listOf(
                        ScorePlacedChordNote(
                            noteIdx = 0,
                            pitch = fallbackPitch,
                            staffStep = s,
                            accidental = accidental,
                            headX = x,
                            y = ny,
                            accX = accX,
                            boxLeft = min(x, accX ?: x),
                            boxTop = ny - sp * 0.55f,
                            boxRight = x + noteheadWidth,
                            boxBottom = ny + sp * 0.55f,
                        )
                    )
                }

                val bottomNote = notesToDraw.first()
                val topNote = notesToDraw.last()
                val bottomStep = bottomNote.staffStep
                val topStep = topNote.staffStep
                val minHeadX = notesToDraw.minOf { it.headX }
                val maxHeadRight = notesToDraw.maxOf { it.headX } + noteheadWidth
                val stemDown = stemDownOverride ?: ((topStep - 4) >= (4 - bottomStep))

                if (bottomStep <= -2) {
                    var ls = -2
                    while (ls >= bottomStep) {
                        val ly = yForStep(ls)
                        target.drawLine(
                            minHeadX - ledgerExt,
                            ly,
                            maxHeadRight + ledgerExt,
                            ly,
                            ledgerThick,
                            color,
                        )
                        ls -= 2
                    }
                }
                if (topStep >= 10) {
                    var ls = 10
                    while (ls <= topStep) {
                        val ly = yForStep(ls)
                        target.drawLine(
                            minHeadX - ledgerExt,
                            ly,
                            maxHeadRight + ledgerExt,
                            ly,
                            ledgerThick,
                            color,
                        )
                        ls += 2
                    }
                }

                for (cn in notesToDraw) {
                    val noteColor = when {
                        cn.noteIdx in outOfRangeChordNoteIndices -> outOfRangeColor
                        cn.noteIdx in selectedChordNoteIndices -> selectedNoteColor
                        else -> color
                    }
                    cn.accidental?.let { acc ->
                        val ax = cn.accX ?: (cn.headX - accidentalWidth(acc, sp) - sp * 0.25f)
                        target.drawGlyph(acc.glyph, ax, cn.y, emPx, noteColor)
                    }
                    target.drawGlyph(item.duration.noteheadGlyph, cn.headX, cn.y, emPx, noteColor)
                }

                if (item.duration.hasStem) {
                    val stemLen = stemLengthSpaces(item.duration) * sp
                    if (!stemDown) {
                        val stemX = x + SmuflGlyphs.STEM_UP_ANCHOR_X * sp - stemThick * 0.5f
                        val stemStartY = bottomNote.y - SmuflGlyphs.STEM_UP_ANCHOR_Y * sp
                        val stemEndY = stemTipYOverride ?: (topNote.y - stemLen)
                        target.drawLine(stemX, stemStartY, stemX, stemEndY, stemThick, color)

                        val flagGlyph = if (suppressFlags) {
                            null
                        } else {
                            when (item.duration.flagCount) {
                                1 -> SmuflGlyphs.FLAG_8TH_UP
                                2 -> SmuflGlyphs.FLAG_16TH_UP
                                3 -> SmuflGlyphs.FLAG_32ND_UP
                                else -> null
                            }
                        }
                        if (flagGlyph != null) {
                            target.drawGlyph(flagGlyph, stemX - stemThick * 0.5f, stemEndY, emPx, color)
                        }
                    } else {
                        val stemX = x + SmuflGlyphs.STEM_DOWN_ANCHOR_X * sp + stemThick * 0.5f
                        val stemStartY = topNote.y - SmuflGlyphs.STEM_DOWN_ANCHOR_Y * sp
                        val stemEndY = stemTipYOverride ?: (bottomNote.y + stemLen)
                        target.drawLine(stemX, stemStartY, stemX, stemEndY, stemThick, color)

                        val flagGlyph = if (suppressFlags) {
                            null
                        } else {
                            when (item.duration.flagCount) {
                                1 -> SmuflGlyphs.FLAG_8TH_DOWN
                                2 -> SmuflGlyphs.FLAG_16TH_DOWN
                                3 -> SmuflGlyphs.FLAG_32ND_DOWN
                                else -> null
                            }
                        }
                        if (flagGlyph != null) {
                            target.drawGlyph(flagGlyph, stemX - stemThick * 0.5f, stemEndY, emPx, color)
                        }
                    }
                }

                if (item.isDotted) {
                    val dotX = maxHeadRight + sp * 0.35f
                    val usedDotSteps = HashSet<Int>(notesToDraw.size)
                    for (cn in notesToDraw) {
                        var dotStep = if (cn.staffStep % 2 == 0) cn.staffStep + 1 else cn.staffStep
                        if (!usedDotSteps.add(dotStep)) {
                            dotStep = if ((cn.staffStep - 1) !in usedDotSteps && (cn.staffStep - 1) % 2 != 0) {
                                cn.staffStep - 1
                            } else {
                                dotStep + 2
                            }
                            usedDotSteps.add(dotStep)
                        }
                        val dotColor = if (cn.noteIdx in outOfRangeChordNoteIndices) outOfRangeColor else color
                        target.drawGlyph(
                            SmuflGlyphs.AUGMENTATION_DOT,
                            dotX,
                            yForStep(dotStep),
                            emPx,
                            dotColor,
                        )
                    }
                }

                if (item.noteStaccato()) {
                    val placeBelow = item.duration.hasStem && !stemDown
                    val extremeNote = if (placeBelow) bottomNote else topNote
                    val staccatoStep = staccatoStepForNote(extremeNote.staffStep, placeBelow)
                    val dotX = extremeNote.headX + noteheadWidth * 0.5f - SmuflGlyphs.ARTIC_STACCATO_WIDTH * sp * 0.5f
                    val dotY = yForStep(staccatoStep) +
                        if (placeBelow) -SmuflGlyphs.ARTIC_STACCATO_WIDTH * sp * 0.5f else SmuflGlyphs.ARTIC_STACCATO_WIDTH * sp * 0.5f
                    val staccatoGlyph = if (placeBelow) {
                        SmuflGlyphs.ARTIC_STACCATO_BELOW
                    } else {
                        SmuflGlyphs.ARTIC_STACCATO_ABOVE
                    }
                    target.drawGlyph(staccatoGlyph, dotX, dotY, emPx, color)
                }
            }
            is ScoreItem.Rest -> {
                val restStep = if (item.duration == NoteDuration.WHOLE) 6 else 4
                val restY = yForStep(restStep)
                target.drawGlyph(item.duration.restGlyph, x, restY, emPx, color)
                if (item.isDotted) {
                    target.drawGlyph(
                        SmuflGlyphs.AUGMENTATION_DOT,
                        x + sp * 1.45f,
                        yForStep(5),
                        emPx,
                        color,
                    )
                }
            }
        }
    }

    /**
     * Computes the staff step for a note's staccato dot so it stays clear of the notehead oval
     * (at least 2 steps / 1 staff space away) and never collides with a staff or ledger line.
     */
    internal fun staccatoStepForNote(noteStep: Int, placeBelow: Boolean): Int {
        val candidate = if (placeBelow) noteStep - 2 else noteStep + 2
        val onStaffLine = candidate in 0..8 && candidate % 2 == 0
        val onLowerLedgerLine = candidate <= -2 && candidate % 2 == 0 && candidate >= noteStep
        val onUpperLedgerLine = candidate >= 10 && candidate % 2 == 0 && candidate <= noteStep
        return if (onStaffLine || onLowerLedgerLine || onUpperLedgerLine) {
            if (placeBelow) candidate - 1 else candidate + 1
        } else {
            candidate
        }
    }

    private fun stemLengthSpaces(duration: NoteDuration): Float =
        if (duration.flagCount >= 3) STEM_LENGTH_32ND_SPACES else STEM_LENGTH_SPACES

    private fun buildPlacedBeamGroups(
        measureIdx: Int,
        beamGroups: List<ScoreBeamGroup>,
        itemsInMeasure: MutableList<ScorePlacedItem>,
        sp: Float,
        stemThick: Float,
    ): List<ScorePlacedBeamGroup> {
        if (beamGroups.isEmpty()) return emptyList()
        val result = ArrayList<ScorePlacedBeamGroup>(beamGroups.size)
        for (bg in beamGroups) {
            val placed = bg.itemIndices.mapNotNull { itemsInMeasure.getOrNull(it) }
            if (placed.size < 2) continue
            val maxFlags = placed.maxOf { it.item.duration.flagCount }
            val stemLen = when {
                maxFlags >= 3 -> STEM_LENGTH_32ND_SPACES
                maxFlags == 2 -> 3.75f
                else -> STEM_LENGTH_SPACES
            } * sp

            val stemXs = FloatArray(placed.size) { idx ->
                val pi = placed[idx]
                if (!bg.stemDown) {
                    pi.x + SmuflGlyphs.STEM_UP_ANCHOR_X * sp - stemThick * 0.5f
                } else {
                    pi.x + SmuflGlyphs.STEM_DOWN_ANCHOR_X * sp + stemThick * 0.5f
                }
            }
            val rawTips = FloatArray(placed.size) { idx ->
                val pi = placed[idx]
                if (!bg.stemDown) {
                    (pi.chordNotes.lastOrNull()?.y ?: pi.centerY) - stemLen
                } else {
                    (pi.chordNotes.firstOrNull()?.y ?: pi.centerY) + stemLen
                }
            }

            val x1 = stemXs.first()
            val x2 = stemXs.last()
            val dx = (x2 - x1).coerceAtLeast(1f)
            val y1Raw = rawTips.first()
            val y2Raw = rawTips.last()
            val maxSlant = min(sp * 1.25f, dx * 0.25f)
            val clampedDy = ((y2Raw - y1Raw) * 0.55f).coerceIn(-maxSlant, maxSlant)
            val slope = clampedDy / dx

            fun anchorY(sx: Float): Float = y1Raw + slope * (sx - x1)
            val shift = if (!bg.stemDown) {
                placed.indices.minOf { k -> rawTips[k] - anchorY(stemXs[k]) }
            } else {
                placed.indices.maxOf { k -> rawTips[k] - anchorY(stemXs[k]) }
            }
            val firstTipY = y1Raw + shift
            val lastTipY = firstTipY + clampedDy

            val stemXsByIdx = mutableMapOf<Int, Float>()
            val stemTipsByIdx = mutableMapOf<Int, Float>()
            for (k in placed.indices) {
                val itemIdx = bg.itemIndices[k]
                val sx = stemXs[k]
                val tipY = firstTipY + slope * (sx - x1)
                stemXsByIdx[itemIdx] = sx
                stemTipsByIdx[itemIdx] = tipY
                val old = itemsInMeasure[itemIdx]
                itemsInMeasure[itemIdx] = old.copy(
                    isBeamed = true,
                    stemTipYOverride = tipY,
                    boxTop = min(old.boxTop, tipY - sp * 0.25f),
                    boxBottom = max(old.boxBottom, tipY + sp * 0.25f),
                )
            }
            result.add(
                ScorePlacedBeamGroup(
                    measureIdx = measureIdx,
                    itemIndices = bg.itemIndices,
                    stemDown = bg.stemDown,
                    firstStemX = x1,
                    lastStemX = x2,
                    firstTipY = firstTipY,
                    lastTipY = lastTipY,
                    stemXsByItemIdx = stemXsByIdx,
                    stemTipsByItemIdx = stemTipsByIdx,
                )
            )
        }
        return result
    }

    private fun drawBeamGroup(
        target: ScoreDrawTarget,
        beamGroup: ScorePlacedBeamGroup,
        itemsInGroup: List<ScorePlacedItem>,
        sp: Float,
        stemThick: Float,
        color: Color,
    ) {
        val maxFlags = itemsInGroup.maxOf { it.item.duration.flagCount }.coerceAtLeast(1)
        val dx = (beamGroup.lastStemX - beamGroup.firstStemX).coerceAtLeast(1f)
        val slope = (beamGroup.lastTipY - beamGroup.firstTipY) / dx
        val dir = if (!beamGroup.stemDown) 1f else -1f
        val beamThick = SmuflGlyphs.BEAM_THICKNESS * sp
        val beamStep = SmuflGlyphs.BEAM_SPACING * sp
        val stemXs = FloatArray(itemsInGroup.size) { idx ->
            beamGroup.stemXsByItemIdx[beamGroup.itemIndices[idx]] ?: itemsInGroup[idx].x
        }

        for (level in 0 until maxFlags) {
            val requiredFlags = level + 1
            val levelOffset = dir * level * beamStep
            fun lineY(x: Float): Float =
                beamGroup.firstTipY + slope * (x - beamGroup.firstStemX) + levelOffset

            var idx = 0
            while (idx < itemsInGroup.size) {
                if (itemsInGroup[idx].item.duration.flagCount < requiredFlags) {
                    idx++
                    continue
                }
                val startIdx = idx
                while (idx + 1 < itemsInGroup.size && itemsInGroup[idx + 1].item.duration.flagCount >= requiredFlags) {
                    idx++
                }
                val endIdx = idx
                if (endIdx > startIdx) {
                    val xStart = stemXs[startIdx] - stemThick * 0.5f
                    val xEnd = stemXs[endIdx] + stemThick * 0.5f
                    target.drawBeamSegment(
                        x1 = xStart,
                        y1 = lineY(xStart),
                        x2 = xEnd,
                        y2 = lineY(xEnd),
                        heightPx = dir * beamThick,
                        color = color,
                    )
                } else if (level >= 1) {
                    val pointRight = when {
                        startIdx == 0 -> true
                        startIdx == itemsInGroup.lastIndex -> false
                        itemsInGroup[startIdx - 1].item.isDotted -> false
                        startIdx % 2 == 1 -> false
                        else -> true
                    }
                    val neighborGap = if (pointRight) {
                        abs(stemXs[startIdx + 1] - stemXs[startIdx])
                    } else {
                        abs(stemXs[startIdx] - stemXs[startIdx - 1])
                    }
                    val stubLen = min(sp * 1.15f, neighborGap * 0.45f)
                    val (xStart, xEnd) = if (pointRight) {
                        val s = stemXs[startIdx] - stemThick * 0.5f
                        s to (s + stubLen)
                    } else {
                        val e = stemXs[startIdx] + stemThick * 0.5f
                        (e - stubLen) to e
                    }
                    target.drawBeamSegment(
                        x1 = xStart,
                        y1 = lineY(xStart),
                        x2 = xEnd,
                        y2 = lineY(xEnd),
                        heightPx = dir * beamThick,
                        color = color,
                    )
                }
                idx++
            }
        }
    }

    private fun drawTies(
        target: ScoreDrawTarget,
        staffMeasures: List<ScorePlacedMeasure>,
        sp: Float,
        colors: ScoreRenderColors,
        selectedLocations: Set<ScoreItemLocation>,
        activeItemGlobalIndex: Int?,
    ) {
        val tieStroke = (sp * 0.18f).coerceAtLeast(1.5f)
        data class TieNotePos(
            val midiPitch: Int,
            val headX: Float,
            val width: Float,
            val y: Float,
            val staffStep: Int,
            val isTied: Boolean,
            val chordNoteIdx: Int?,
        )

        fun tieNotesForItem(pi: ScorePlacedItem): List<TieNotePos> = when (val item = pi.item) {
            is ScoreItem.Note -> listOf(
                TieNotePos(
                    midiPitch = item.pitch.midiPitch,
                    headX = pi.x,
                    width = pi.width,
                    y = pi.centerY,
                    staffStep = pi.staffStep ?: 4,
                    isTied = item.isTiedToNext,
                    chordNoteIdx = null,
                )
            )
            is ScoreItem.Chord -> pi.chordNotes.mapIndexed { idx, cn ->
                TieNotePos(
                    midiPitch = cn.pitch.midiPitch,
                    headX = cn.headX,
                    width = pi.width,
                    y = cn.y,
                    staffStep = cn.staffStep,
                    isTied = cn.pitch.midiPitch in item.tiedMidiPitches,
                    chordNoteIdx = idx,
                )
            }
            is ScoreItem.Rest -> emptyList()
        }

        for ((pmIdx, pm) in staffMeasures.withIndex()) {
            for ((piIdx, pi) in pm.items.withIndex()) {
                val srcNotes = tieNotesForItem(pi)
                if (srcNotes.none { it.isTied }) continue
                val nextPi = pm.items.getOrNull(piIdx + 1)
                    ?: staffMeasures.getOrNull(pmIdx + 1)?.items?.firstOrNull()
                    ?: continue
                val dstNotes = tieNotesForItem(nextPi)
                if (dstNotes.isEmpty()) continue

                for (src in srcNotes) {
                    if (!src.isTied) continue
                    val dst = dstNotes.firstOrNull { it.midiPitch == src.midiPitch } ?: continue

                    val curveDir = when {
                        src.chordNoteIdx != null -> {
                            when {
                                src.chordNoteIdx == srcNotes.lastIndex -> -1f
                                src.chordNoteIdx == 0 -> 1f
                                src.chordNoteIdx >= srcNotes.size / 2 -> -1f
                                else -> 1f
                            }
                        }
                        !pi.item.duration.hasStem -> if (src.staffStep >= 4) -1f else 1f
                        pi.stemDown -> -1f
                        else -> 1f
                    }

                    val dotOffset = if (pi.item.isDotted) sp * 0.55f else 0f
                    var x1 = src.headX + src.width + dotOffset + sp * 0.1f
                    var x2 = dst.headX - sp * 0.1f
                    var yOffset = sp * 0.52f
                    if (x2 - x1 < sp * 0.9f) {
                        x1 = src.headX + src.width * 0.65f
                        x2 = dst.headX + dst.width * 0.35f
                        yOffset = sp * 0.65f
                    }
                    val y1 = src.y + curveDir * yOffset
                    val y2 = dst.y + curveDir * yOffset
                    val span = (x2 - x1).coerceAtLeast(sp)
                    val arch = (span * 0.24f).coerceIn(sp * 0.55f, sp * 1.45f) * curveDir
                    val cx1 = x1 + span * 0.28f
                    val cx2 = x2 - span * 0.28f
                    val cy1 = y1 + arch
                    val cy2 = y2 + arch

                    val noteSelected = pi.location in selectedLocations ||
                        (src.chordNoteIdx != null && pi.location.copy(chordNoteIdx = src.chordNoteIdx) in selectedLocations)
                    val tieColor = when {
                        activeItemGlobalIndex != null &&
                            (pi.globalIdx == activeItemGlobalIndex || nextPi.globalIdx == activeItemGlobalIndex) ->
                            colors.activeNote
                        pi.isOverflow ||
                            (src.chordNoteIdx ?: 0) in pi.outOfRangeChordIndices -> colors.overflowNote
                        noteSelected -> colors.selectedNote
                        else -> colors.ink
                    }

                    target.drawCubicBezier(
                        x0 = x1,
                        y0 = y1,
                        cx1 = cx1,
                        cy1 = cy1,
                        cx2 = cx2,
                        cy2 = cy2,
                        x3 = x2,
                        y3 = y2,
                        strokeWidth = tieStroke,
                        color = tieColor,
                    )
                }
            }
        }
    }

    private fun keyChangeGlyphLayout(
        startX: Float,
        glyphs: List<Pair<Int, Accidental>>,
        sp: Float,
    ): Pair<List<Float>, Float> {
        var x = startX
        var prevAcc: Accidental? = null
        val xs = ArrayList<Float>(glyphs.size)
        for ((_, acc) in glyphs) {
            if (prevAcc != null && prevAcc != acc) {
                x += sp * 0.25f
            }
            xs.add(x)
            x += accidentalWidth(acc, sp) + sp * 0.16f
            prevAcc = acc
        }
        return xs to x
    }

    private fun accidentalWidth(acc: Accidental, sp: Float): Float = when (acc) {
        Accidental.SHARP -> SmuflGlyphs.ACCIDENTAL_SHARP_WIDTH * sp
        Accidental.FLAT -> SmuflGlyphs.ACCIDENTAL_FLAT_WIDTH * sp
        Accidental.NATURAL -> SmuflGlyphs.ACCIDENTAL_NATURAL_WIDTH * sp
    }

    private fun incompleteTupletRemainingSlots(
        measure: ScoreMeasure,
        tg: ScoreTupletGroup,
    ): Int {
        val t = tg.tuplet
        return if (t.baseTicks > 0) {
            val unscaledSum = tg.itemIndices.sumOf { measure.items[it].totalTicks }
            val remTicks = (t.actualCount * t.baseTicks - unscaledSum).coerceAtLeast(t.baseTicks)
            ((remTicks + t.baseTicks - 1) / t.baseTicks).coerceAtLeast(1)
        } else {
            (t.actualCount - tg.itemIndices.size).coerceAtLeast(1)
        }
    }

    private fun buildPlacedTupletGroups(
        measureIdx: Int,
        tupletGroups: List<ScoreTupletGroup>,
        itemsInMeasure: List<ScorePlacedItem>,
        midClefChanges: List<ScorePlacedClefChange>,
        placedBeamGroups: List<ScorePlacedBeamGroup>,
        barlineX: Float,
        topStaffY: Float,
        bottomStaffY: Float,
        sp: Float,
        measureGlyphWidth: (String, Float) -> Float,
    ): List<ScorePlacedTupletGroup> {
        if (tupletGroups.isEmpty()) return emptyList()
        val result = ArrayList<ScorePlacedTupletGroup>(tupletGroups.size)
        val tupletEmPx = sp * 3.2f
        for (tg in tupletGroups) {
            val placed = tg.itemIndices.mapNotNull { itemsInMeasure.getOrNull(it) }
            if (placed.isEmpty()) continue
            val matchingBeam = placedBeamGroups.firstOrNull { bg ->
                tg.itemIndices.first() in bg.itemIndices && tg.itemIndices.last() in bg.itemIndices
            }
            val tupletGlyph = SmuflGlyphs.tupletNumber(tg.displayText)
            val measuredW = measureGlyphWidth(tupletGlyph, tupletEmPx)
                .coerceAtLeast(tg.displayText.length * sp * 0.65f)

            val first = placed.first()
            val last = placed.last()
            val aboveStaff = when {
                matchingBeam != null -> !matchingBeam.stemDown
                placed.any { it.item is ScoreItem.Rest } -> true
                placed.all { it.stemDown } -> false
                else -> true
            }

            if (!tg.drawBracket && matchingBeam != null) {
                val bx1 = matchingBeam.stemXsByItemIdx[tg.itemIndices.first()] ?: matchingBeam.firstStemX
                val by1 = matchingBeam.stemTipsByItemIdx[tg.itemIndices.first()] ?: matchingBeam.firstTipY
                val bx2 = matchingBeam.stemXsByItemIdx[tg.itemIndices.last()] ?: matchingBeam.lastStemX
                val by2 = matchingBeam.stemTipsByItemIdx[tg.itemIndices.last()] ?: matchingBeam.lastTipY
                val midX = (bx1 + bx2) * 0.5f
                val midBeamY = (by1 + by2) * 0.5f
                val baselineY = if (aboveStaff) {
                    midBeamY - sp * 0.55f
                } else {
                    midBeamY + sp * 1.65f
                }
                result.add(
                    ScorePlacedTupletGroup(
                        measureIdx = measureIdx,
                        itemIndices = tg.itemIndices,
                        tuplet = tg.tuplet,
                        displayText = tg.displayText,
                        drawBracket = false,
                        isFull = tg.isFull,
                        aboveStaff = aboveStaff,
                        startX = bx1,
                        endX = bx2,
                        bracketY = midBeamY,
                        numberX = midX,
                        numberBaselineY = baselineY,
                        numberWidth = measuredW,
                        numberEmPx = tupletEmPx,
                    )
                )
            } else {
                val startX = first.x - sp * 0.25f
                val normalEndX = if (placed.size >= 2) {
                    max(last.boxRight, last.x + last.width + sp * 0.25f)
                } else {
                    max(first.boxRight + sp * 0.3f, first.x + advanceForItem(first.item, sp) * 0.9f)
                }
                val endX = if (!tg.isFull) {
                    val afterIdx = tg.itemIndices.last() + 1
                    val nextItem = itemsInMeasure.getOrNull(afterIdx)
                    val nextClef = midClefChanges.firstOrNull {
                        it.itemIdx == afterIdx || (nextItem == null && it.itemIdx > tg.itemIndices.last())
                    }
                    val trailingLimitX = when {
                        nextClef != null && nextItem != null -> min(nextClef.x, nextItem.boxLeft) - sp * 0.35f
                        nextClef != null -> nextClef.x - sp * 0.35f
                        nextItem != null -> nextItem.boxLeft - sp * 0.35f
                        else -> barlineX - sp * 0.55f
                    }
                    max(normalEndX, trailingLimitX)
                } else {
                    normalEndX
                }
                val bracketY = if (aboveStaff) {
                    min(topStaffY - sp * 1.45f, placed.minOf { it.boxTop } - sp * 0.7f)
                } else {
                    max(bottomStaffY + sp * 1.45f, placed.maxOf { it.boxBottom } + sp * 0.7f)
                }
                result.add(
                    ScorePlacedTupletGroup(
                        measureIdx = measureIdx,
                        itemIndices = tg.itemIndices,
                        tuplet = tg.tuplet,
                        displayText = tg.displayText,
                        drawBracket = tg.drawBracket,
                        isFull = tg.isFull,
                        aboveStaff = aboveStaff,
                        startX = startX,
                        endX = endX,
                        bracketY = bracketY,
                        numberX = (startX + endX) * 0.5f,
                        numberBaselineY = bracketY + sp * 0.52f,
                        numberWidth = measuredW,
                        numberEmPx = tupletEmPx,
                    )
                )
            }
        }
        return result
    }

    private fun drawTupletGroup(
        target: ScoreDrawTarget,
        tupletGroup: ScorePlacedTupletGroup,
        sp: Float,
        color: Color,
    ) {
        drawTupletBracketAndLabel(
            target = target,
            startX = tupletGroup.startX,
            endX = tupletGroup.endX,
            bracketY = tupletGroup.bracketY,
            aboveStaff = tupletGroup.aboveStaff,
            drawBracket = tupletGroup.drawBracket,
            glyph = SmuflGlyphs.tupletNumber(tupletGroup.displayText),
            numberX = tupletGroup.numberX,
            numberBaselineY = tupletGroup.numberBaselineY,
            numberWidth = tupletGroup.numberWidth,
            numberEmPx = tupletGroup.numberEmPx,
            sp = sp,
            color = color,
        )
    }

    private fun drawTupletBracketAndLabel(
        target: ScoreDrawTarget,
        startX: Float,
        endX: Float,
        bracketY: Float,
        aboveStaff: Boolean,
        drawBracket: Boolean,
        glyph: String,
        numberX: Float,
        numberBaselineY: Float,
        numberWidth: Float,
        numberEmPx: Float,
        sp: Float,
        color: Color,
    ) {
        if (drawBracket) {
            val thick = (SmuflGlyphs.TUPLET_BRACKET_THICKNESS * sp).coerceAtLeast(1.1f)
            val hookDir = if (aboveStaff) 1f else -1f
            val hookLen = sp * 0.65f
            val gapHalf = numberWidth * 0.5f + sp * 0.3f
            val gapLeft = (numberX - gapHalf).coerceAtLeast(startX)
            val gapRight = (numberX + gapHalf).coerceAtMost(endX)

            target.drawLine(startX, bracketY + hookDir * hookLen, startX, bracketY, thick, color)
            target.drawLine(endX, bracketY + hookDir * hookLen, endX, bracketY, thick, color)
            if (gapLeft > startX + sp * 0.1f) {
                target.drawLine(startX, bracketY, gapLeft, bracketY, thick, color)
            }
            if (endX > gapRight + sp * 0.1f) {
                target.drawLine(gapRight, bracketY, endX, bracketY, thick, color)
            }
        }
        target.drawGlyph(
            glyph = glyph,
            x = numberX - numberWidth * 0.5f,
            baselineY = numberBaselineY,
            fontSizePx = numberEmPx,
            color = color,
        )
    }

    /**
     * Gourlay-style logarithmic duration-to-space curve in staff spaces (`sp`), matching standard
     * engraving proportions from 32nd notes (`1.85 sp`) through whole notes (`5.8 sp`) and
     * interpolating smoothly for dotted values, tuplets, and cross-staff subdivisions.
     */
    private fun idealAdvanceSpacesForTicks(durationTicks: Double): Float {
        val quarterTicks = NoteDuration.QUARTER.ticks.toDouble()
        val beats = (durationTicks / quarterTicks).coerceAtLeast(1.0 / 32.0)
        val x = log2(beats).toFloat()
        return when {
            x <= -3f -> (1.85f + (x + 3f) * 0.12f).coerceAtLeast(1.65f)
            x <= -2f -> 1.85f + (x + 3f) * (2.0f - 1.85f)
            x <= -1f -> 2.0f + (x + 2f) * (2.5f - 2.0f)
            x <= 0f -> 2.5f + (x + 1f) * (3.3f - 2.5f)
            x <= 1f -> 3.3f + x * (4.4f - 3.3f)
            x <= 2f -> 4.4f + (x - 1f) * (5.8f - 4.4f)
            else -> 5.8f + (x - 2f) * 1.4f
        }
    }

    private fun advanceForItem(item: ScoreItem, sp: Float): Float =
        idealAdvanceSpacesForTicks(item.exactTicks().toDoubleTicks()) * sp
}
