package com.syntona.dataflow.score

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
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
) {
    val location: ScoreItemLocation get() = ScoreItemLocation(measureIdx, itemIdx)
}

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
)

data class ScoreInsertionTarget(
    val measureIdx: Int,
    val itemIdx: Int,
    val appendNewMeasure: Boolean,
)

data class ScoreGhostPreview(
    val x: Float,
    val staffStep: Int,
    val duration: NoteDuration,
    val isRest: Boolean,
    val accidental: Accidental? = null,
    val isStaccato: Boolean = false,
)

data class ScoreRubberBand(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class ScoreLayout(
    val totalWidthPx: Float,
    val totalHeightPx: Float,
    val staffSpacePx: Float = 10f,
    val topStaffY: Float = 0f,
    val bottomStaffY: Float = 0f,
    val headerEndX: Float = 0f,
    val staffEndX: Float = 0f,
    val placedMeasures: List<ScorePlacedMeasure> = emptyList(),
) {
    fun yForStep(stepFromBottom: Int): Float = bottomStaffY - stepFromBottom * (staffSpacePx * 0.5f)

    fun stepAtY(y: Float, minStep: Int = -12, maxStep: Int = 20): Int =
        ((bottomStaffY - y) / (staffSpacePx * 0.5f)).roundToInt().coerceIn(minStep, maxStep)

    /** Returns the [ScorePlacedItem] under `(x, y)`, preferring the nearest item center if boxes touch. */
    fun itemAt(x: Float, y: Float): ScorePlacedItem? {
        val sp = staffSpacePx
        var best: ScorePlacedItem? = null
        var bestDist = Float.MAX_VALUE
        for (pm in placedMeasures) {
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
     * Returns an existing non-rest [ScorePlacedItem] whose notehead column aligns horizontally with
     * [x], used in Entry mode to stack notes vertically onto an existing note or chord.
     */
    fun itemColumnAt(x: Float): ScorePlacedItem? {
        val sp = staffSpacePx
        var best: ScorePlacedItem? = null
        var bestDist = Float.MAX_VALUE
        for (pm in placedMeasures) {
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

    /** Returns all items whose visual bounding box intersects the given rectangle. */
    fun itemsInRect(left: Float, top: Float, right: Float, bottom: Float): Set<ScoreItemLocation> {
        val result = linkedSetOf<ScoreItemLocation>()
        for (pm in placedMeasures) {
            for (pi in pm.items) {
                if (pi.boxRight >= left && pi.boxLeft <= right &&
                    pi.boxBottom >= top && pi.boxTop <= bottom
                ) {
                    result.add(pi.location)
                }
            }
        }
        return result
    }

    /** Computes where a new item clicked at [x] should be inserted, or `null` if inside the clef/key header. */
    fun insertionTargetAt(x: Float): ScoreInsertionTarget? {
        val firstStart = placedMeasures.firstOrNull()?.startX ?: headerEndX
        if (x < firstStart - staffSpacePx * 0.5f) return null
        if (placedMeasures.isEmpty()) {
            return ScoreInsertionTarget(measureIdx = 0, itemIdx = 0, appendNewMeasure = true)
        }
        for (pm in placedMeasures) {
            if (x <= pm.barlineX) {
                val insertIdx = pm.items.indexOfFirst { x < it.x + it.width * 0.5f }
                    .let { if (it < 0) pm.items.size else it }
                return ScoreInsertionTarget(
                    measureIdx = pm.measureIdx,
                    itemIdx = insertIdx,
                    appendNewMeasure = false,
                )
            }
        }
        val lastPm = placedMeasures.last()
        return if (lastPm.measure.items.isEmpty()) {
            ScoreInsertionTarget(measureIdx = lastPm.measureIdx, itemIdx = 0, appendNewMeasure = false)
        } else {
            ScoreInsertionTarget(measureIdx = placedMeasures.size, itemIdx = 0, appendNewMeasure = true)
        }
    }

    /**
     * Resolves a selection-mode click at [x] to a [ScoreCursorPosition] inside an empty measure
     * or between/around notes, or `null` if inside the clef/key header.
     */
    fun cursorPositionAt(x: Float): ScoreCursorPosition? {
        val firstStart = placedMeasures.firstOrNull()?.startX ?: headerEndX
        if (x < firstStart - staffSpacePx * 0.5f) return null
        if (placedMeasures.isEmpty()) return ScoreCursorPosition(0, 0)
        for (pm in placedMeasures) {
            if (x <= pm.barlineX) {
                val itemIdx = if (pm.items.isEmpty()) {
                    0
                } else {
                    pm.items.indexOfFirst { x < it.x + it.width * 0.5f }
                        .let { if (it < 0) pm.items.size else it }
                }
                return ScoreCursorPosition(measureIdx = pm.measureIdx, itemIdx = itemIdx)
            }
        }
        val lastPm = placedMeasures.last()
        return ScoreCursorPosition(measureIdx = lastPm.measureIdx, itemIdx = lastPm.items.size)
    }

    /** Returns the 0-based measure index under [x], or `null` if outside measure bounds (e.g. header). */
    fun measureAt(x: Float): Int? {
        for ((idx, pm) in placedMeasures.withIndex()) {
            val leftBound = if (idx == 0) pm.startX - staffSpacePx * 0.5f else placedMeasures[idx - 1].barlineX
            if (x >= leftBound && x <= pm.barlineX) {
                return pm.measureIdx
            }
        }
        return null
    }

    /** Returns the indices of all measures whose horizontal bounds intersect `[left, right]` across the staff. */
    fun measuresInRect(left: Float, top: Float, right: Float, bottom: Float): Set<Int> {
        val sp = staffSpacePx
        if (bottom < topStaffY - sp || top > bottomStaffY + sp) return emptySet()
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
        val pm = placedMeasures.getOrNull(cursor.measureIdx) ?: return null
        val sp = staffSpacePx
        if (pm.items.isEmpty()) {
            return pm.contentStartX + sp * 1.1f
        }
        val clampedIdx = cursor.itemIdx.coerceIn(0, pm.items.size)
        return when {
            clampedIdx == 0 -> ((pm.contentStartX + sp * 0.35f) + pm.items.first().boxLeft) * 0.5f
            clampedIdx >= pm.items.size -> {
                val tailRight = pm.items.last().boxRight
                val limit = min(pm.barlineX - sp * 0.25f, tailRight + sp * 0.9f)
                max(tailRight + sp * 0.25f, (tailRight + limit) * 0.5f)
            }
            else -> (pm.items[clampedIdx - 1].boxRight + pm.items[clampedIdx].boxLeft) * 0.5f
        }
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
    private const val BOTTOM_MARGIN_SPACES = 4.5f

    private data class HeaderLayout(
        val leftMargin: Float,
        val rightMargin: Float,
        val topStaffY: Float,
        val bottomStaffY: Float,
        val totalHeight: Float,
        val clefX: Float,
        val keyStartX: Float,
        val keySteps: List<Int>,
        val keyGlyph: String,
        val keyGlyphStepWidth: Float,
        val timeSigX: Float,
        val numStr: String,
        val denStr: String,
        val headerEndX: Float,
    )

    private fun computeHeaderAndMeasures(
        score: ScoreData,
        staffSpacePx: Float,
        minWidthPx: Float,
        measureGlyphWidth: (String, Float) -> Float,
    ): Pair<HeaderLayout, ScoreLayout> {
        val sp = staffSpacePx.coerceAtLeast(4f)
        val emPx = sp * 4f
        val leftMargin = sp * 2.0f
        val rightMargin = sp * 2.0f

        // Bottom line (step 0) is bottomStaffY; step 8 is top line (topStaffY = bottomStaffY - 4 * sp)
        val topStaffY = TOP_MARGIN_SPACES * sp
        val bottomStaffY = topStaffY + 4f * sp
        val totalHeight = bottomStaffY + BOTTOM_MARGIN_SPACES * sp

        fun yForStep(stepFromBottom: Int): Float = bottomStaffY - stepFromBottom * (sp * 0.5f)

        var cursorX = leftMargin + sp * 0.7f

        val clefX = cursorX
        val clefWidth = max(measureGlyphWidth(score.clef.glyph, emPx), sp * 2.6f)
        cursorX += clefWidth + sp * 0.8f

        val openingKey = score.measures.firstOrNull()?.keySignature ?: score.keySignature
        val keySteps = openingKey.trebleStaffSteps()
        val keyGlyph = if (openingKey.fifths >= 0) SmuflGlyphs.ACCIDENTAL_SHARP else SmuflGlyphs.ACCIDENTAL_FLAT
        val keyGlyphStepWidth = if (openingKey.fifths >= 0) {
            SmuflGlyphs.ACCIDENTAL_SHARP_WIDTH * sp + sp * 0.18f
        } else {
            SmuflGlyphs.ACCIDENTAL_FLAT_WIDTH * sp + sp * 0.14f
        }
        val keyStartX = cursorX
        if (keySteps.isNotEmpty()) {
            cursorX += keySteps.size * keyGlyphStepWidth + sp * 0.7f
        }

        val numStr = SmuflGlyphs.timeSigNumber(score.timeSignature.numerator)
        val denStr = SmuflGlyphs.timeSigNumber(score.timeSignature.denominator)
        val timeSigX = cursorX
        val timeSigWidth = max(
            measureGlyphWidth(numStr, emPx),
            measureGlyphWidth(denStr, emPx),
        ).coerceAtLeast(sp * 1.6f)
        cursorX += timeSigWidth + sp * 1.6f
        val headerEndX = cursorX

        val measureCapacity = score.timeSignature.measureCapacityTicks
        val placedMeasures = mutableListOf<ScorePlacedMeasure>()
        var globalIdx = 0
        var effectiveKey = openingKey

        for ((mIdx, measure) in score.measures.withIndex()) {
            val mStartX = cursorX
            val keyChangeX = cursorX
            val keyChangeGlyphs = if (mIdx > 0 && measure.keySignature != null && measure.keySignature != effectiveKey) {
                ScoreEditing.midMeasureKeyGlyphs(effectiveKey, measure.keySignature).also {
                    effectiveKey = measure.keySignature
                }
            } else {
                emptyList()
            }
            if (keyChangeGlyphs.isNotEmpty()) {
                val (_, endKeyX) = keyChangeGlyphLayout(keyChangeX, keyChangeGlyphs, sp)
                cursorX = endKeyX + sp * 0.45f
            }
            val contentStartX = cursorX
            val activeAlters = mutableMapOf<Int, Int>()
            val itemsInMeasure = mutableListOf<ScorePlacedItem>()
            var measureTicks = 0

            cursorX += sp * 1.1f
            val stemThick = SmuflGlyphs.STEM_THICKNESS * sp
            for ((iIdx, item) in measure.items.withIndex()) {
                val itemTicks = item.totalTicks
                val newMeasureTicks = measureTicks + itemTicks
                val isOverflow = newMeasureTicks > measureCapacity
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
                    val steps = pitches.map { score.clef.staffStepFromBottom(it) }
                    val bottomStep = steps.first()
                    val topStep = steps.last()
                    val stemDown = (topStep - 4) >= (4 - bottomStep)

                    // Displace adjacent seconds horizontally so noteheads never overlap
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

                    // Compute accidentals per chord note & stagger columns when vertically < 5 steps apart
                    val noteAccs = arrayOfNulls<Accidental>(pitches.size)
                    for (i in pitches.indices) {
                        val p = pitches[i]
                        val keyDefault = effectiveKey.defaultAlter(p.letter)
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

                    cursorX += leftDispPx + totalAccWidth
                    val itemX = cursorX
                    val chordAnchorLeft = itemX - leftDispPx

                    val chordNotes = pitches.mapIndexed { idx, p ->
                        val s = steps[idx]
                        val y = yForStep(s)
                        val hx = itemX + headXOffsets[idx]
                        val acc = noteAccs[idx]
                        val col = accColumnByNote[idx]
                        val accX = if (acc != null && col >= 0) {
                            chordAnchorLeft - colOffsetsFromLeftEdge[col]
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
                            boxRight = hx + noteWidth + sp * 0.12f,
                            boxBottom = y + sp * 0.55f,
                        )
                    }

                    val minLeft = chordNotes.minOf { it.boxLeft }
                    val maxRight = max(
                        chordNotes.maxOf { it.headX } + noteWidth + (if (item.isDotted) sp * 0.7f else sp * 0.15f),
                        chordNotes.maxOf { it.boxRight },
                    )
                    val topY = yForStep(topStep)
                    val bottomY = yForStep(bottomStep)
                    val (boxTop, boxBottom) = if (!item.duration.hasStem) {
                        (topY - sp * 0.75f) to (bottomY + sp * 0.75f)
                    } else {
                        val stemLen = stemLengthSpaces(item.duration) * sp
                        if (!stemDown) {
                            (topY - stemLen - sp * 0.25f) to (bottomY + sp * 0.75f)
                        } else {
                            (topY - sp * 0.75f) to (bottomY + stemLen + sp * 0.25f)
                        }
                    }

                    itemsInMeasure.add(
                        ScorePlacedItem(
                            measureIdx = mIdx,
                            itemIdx = iIdx,
                            globalIdx = globalIdx++,
                            item = item,
                            x = itemX,
                            width = noteWidth,
                            accidental = chordNotes.firstOrNull()?.accidental,
                            staffStep = bottomStep,
                            centerY = (topY + bottomY) * 0.5f,
                            boxLeft = minLeft,
                            boxTop = boxTop,
                            boxRight = maxRight,
                            boxBottom = boxBottom,
                            isOverflow = isOverflow,
                            chordNotes = chordNotes,
                            stemDown = stemDown,
                        )
                    )
                    cursorX += advanceForItem(item, sp) + rightDispPx
                } else {
                    val itemX = cursorX
                    val itemCenterY = yForStep(if (item.duration == NoteDuration.WHOLE) 6 else 4)
                    itemsInMeasure.add(
                        ScorePlacedItem(
                            measureIdx = mIdx,
                            itemIdx = iIdx,
                            globalIdx = globalIdx++,
                            item = item,
                            x = itemX,
                            width = noteWidth,
                            accidental = null,
                            staffStep = null,
                            centerY = itemCenterY,
                            boxLeft = itemX - sp * 0.15f,
                            boxTop = topStaffY + sp * 0.2f,
                            boxRight = itemX + noteWidth + (if (item.isDotted) sp * 0.7f else sp * 0.15f),
                            boxBottom = bottomStaffY - sp * 0.2f,
                            isOverflow = isOverflow,
                        )
                    )
                    cursorX += advanceForItem(item, sp)
                }
            }

            cursorX += if (measure.items.isEmpty()) sp * 6f else sp * 0.6f
            val barlineX = cursorX
            placedMeasures.add(
                ScorePlacedMeasure(
                    measureIdx = mIdx,
                    measure = measure,
                    startX = mStartX,
                    barlineX = barlineX,
                    items = itemsInMeasure,
                    totalTicks = measureTicks,
                    capacityTicks = measureCapacity,
                    contentStartX = contentStartX,
                    keyChangeX = keyChangeX,
                    keyChangeGlyphs = keyChangeGlyphs,
                    activeKeySignature = effectiveKey,
                )
            )
            cursorX += sp * 0.6f
        }

        val finalEndX = max(cursorX + sp * 0.8f + rightMargin, minWidthPx)
        val staffEndX = if (placedMeasures.isNotEmpty()) {
            max(
                placedMeasures.last().barlineX + SmuflGlyphs.THICK_BARLINE_THICKNESS * sp + sp * 1.5f,
                minWidthPx - rightMargin,
            )
        } else {
            finalEndX - rightMargin
        }

        val header = HeaderLayout(
            leftMargin = leftMargin,
            rightMargin = rightMargin,
            topStaffY = topStaffY,
            bottomStaffY = bottomStaffY,
            totalHeight = totalHeight,
            clefX = clefX,
            keyStartX = keyStartX,
            keySteps = keySteps,
            keyGlyph = keyGlyph,
            keyGlyphStepWidth = keyGlyphStepWidth,
            timeSigX = timeSigX,
            numStr = numStr,
            denStr = denStr,
            headerEndX = headerEndX,
        )
        val layout = ScoreLayout(
            totalWidthPx = (staffEndX + rightMargin).coerceAtLeast(minWidthPx),
            totalHeightPx = totalHeight.roundToInt().toFloat(),
            staffSpacePx = sp,
            topStaffY = topStaffY,
            bottomStaffY = bottomStaffY,
            headerEndX = headerEndX,
            staffEndX = staffEndX,
            placedMeasures = placedMeasures,
        )
        return header to layout
    }

    /** Computes the [ScoreLayout] without drawing, for hit-testing and coordinate mapping. */
    fun computeLayout(
        score: ScoreData,
        staffSpacePx: Float,
        minWidthPx: Float,
        measureGlyphWidth: (String, Float) -> Float,
    ): ScoreLayout = computeHeaderAndMeasures(score, staffSpacePx, minWidthPx, measureGlyphWidth).second

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
        ghostPreview: ScoreGhostPreview? = null,
        rubberBand: ScoreRubberBand? = null,
        cursorPosition: ScoreCursorPosition? = null,
        isStepRecording: Boolean = false,
    ): ScoreLayout {
        val (header, layout) = computeHeaderAndMeasures(
            score = score,
            staffSpacePx = staffSpacePx,
            minWidthPx = minWidthPx,
            measureGlyphWidth = target::measureGlyphWidth,
        )
        val sp = layout.staffSpacePx
        val emPx = sp * 4f
        val topStaffY = header.topStaffY
        val bottomStaffY = header.bottomStaffY
        val staffEndX = layout.staffEndX

        // 1. Draw the 5 staff lines
        val staffLineThick = SmuflGlyphs.STAFF_LINE_THICKNESS * sp
        for (lineIdx in 0..4) {
            val y = layout.yForStep(lineIdx * 2)
            target.drawLine(header.leftMargin, y, staffEndX, y, staffLineThick, colors.staffLine)
        }

        // 2. Draw clef, key signature, time signature
        val clefBaselineY = layout.yForStep(score.clef.glyphLineFromBottom * 2)
        target.drawGlyph(score.clef.glyph, header.clefX, clefBaselineY, emPx, colors.ink)

        header.keySteps.forEachIndexed { idx, stepOffset ->
            val kx = header.keyStartX + idx * header.keyGlyphStepWidth
            target.drawGlyph(header.keyGlyph, kx, layout.yForStep(stepOffset), emPx, colors.ink)
        }

        target.drawGlyph(header.numStr, header.timeSigX, layout.yForStep(6), emPx, colors.ink)
        target.drawGlyph(header.denStr, header.timeSigX, layout.yForStep(2), emPx, colors.ink)

        // 3. Draw measures, barlines, notes, and rests
        val thinBar = SmuflGlyphs.THIN_BARLINE_THICKNESS * sp
        val thickBar = SmuflGlyphs.THICK_BARLINE_THICKNESS * sp
        val stemThick = SmuflGlyphs.STEM_THICKNESS * sp
        val ledgerThick = SmuflGlyphs.LEGER_LINE_THICKNESS * sp
        val ledgerExt = SmuflGlyphs.LEGER_LINE_EXTENSION * sp

        for ((idx, pm) in layout.placedMeasures.withIndex()) {
            if (pm.measureIdx in selectedMeasureIndices) {
                val hlLeft = if (idx == 0) pm.startX else layout.placedMeasures[idx - 1].barlineX
                val hlTop = topStaffY - sp * 0.55f
                val hlBottom = bottomStaffY + sp * 0.55f
                val borderThick = (sp * 0.12f).coerceAtLeast(1f)
                target.fillRect(hlLeft, hlTop, (pm.barlineX - hlLeft).coerceAtLeast(1f), hlBottom - hlTop, colors.selectedBoxFill)
                target.drawLine(hlLeft, hlTop, pm.barlineX, hlTop, borderThick, colors.selectedBoxBorder)
                target.drawLine(hlLeft, hlBottom, pm.barlineX, hlBottom, borderThick, colors.selectedBoxBorder)
            }

            if (pm.keyChangeGlyphs.isNotEmpty()) {
                val (glyphXs, _) = keyChangeGlyphLayout(pm.keyChangeX, pm.keyChangeGlyphs, sp)
                pm.keyChangeGlyphs.forEachIndexed { gIdx, (kStep, acc) ->
                    target.drawGlyph(acc.glyph, glyphXs[gIdx], layout.yForStep(kStep), emPx, colors.ink)
                }
            }

            if (idx > 0) {
                target.drawText(
                    text = pm.measure.number.toString(),
                    x = pm.startX - sp * 0.35f,
                    baselineY = topStaffY - sp * 1.1f,
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
                        if (loc.measureIdx == pi.measureIdx && loc.itemIdx == pi.itemIdx) loc.chordNoteIdx else null
                    }.toSet()
                }
                val isPlaying = pi.globalIdx == activeItemGlobalIndex

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

                val itemColor = when {
                    isPlaying -> colors.activeNote
                    pi.isOverflow -> colors.overflowNote
                    isSelected -> colors.selectedNote
                    else -> colors.ink
                }
                val chordNoteHighlightColor = when {
                    isPlaying -> colors.activeNote
                    pi.isOverflow -> colors.overflowNote
                    else -> colors.selectedNote
                }

                drawScoreItem(
                    target = target,
                    layout = layout,
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
                )
            }

            // Barline at end of measure
            val isLast = idx == layout.placedMeasures.lastIndex
            if (isLast) {
                val sep = SmuflGlyphs.THIN_THICK_SEPARATION * sp
                val doubleBarEndX = max(pm.barlineX + thickBar + sep + thinBar, min(staffEndX, pm.barlineX + sp * 1.2f))
                val thickX = doubleBarEndX - thickBar * 0.5f
                val thinX = thickX - sep - thickBar * 0.5f - thinBar * 0.5f
                target.drawLine(thinX, topStaffY, thinX, bottomStaffY, thinBar, colors.ink)
                target.drawLine(thickX, topStaffY, thickX, bottomStaffY, thickBar, colors.ink)
            } else {
                target.drawLine(pm.barlineX, topStaffY, pm.barlineX, bottomStaffY, thinBar, colors.ink)
            }
        }

        // Initial left barline closing the stave cleanly
        target.drawLine(header.leftMargin, topStaffY, header.leftMargin, bottomStaffY, thinBar, colors.ink)

        // Optional ghost preview while hovering in entry mode
        if (ghostPreview != null) {
            val ghostItem = if (ghostPreview.isRest) {
                ScoreItem.Rest(ghostPreview.duration)
            } else {
                ScoreItem.Note(
                    pitch = SpelledPitch(NoteLetter.C, 0, 4), // pitch unused because staffStep is passed explicitly
                    duration = ghostPreview.duration,
                    isStaccato = ghostPreview.isStaccato,
                )
            }
            drawScoreItem(
                target = target,
                layout = layout,
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
        }

        // Optional vertical insertion / step-record cursor
        if (cursorPosition != null) {
            layout.cursorXFor(cursorPosition)?.let { cx ->
                val cursorColor = if (isStepRecording) colors.stepCursor else colors.cursor
                val cursorTop = topStaffY - sp * 1.35f
                val cursorBottom = bottomStaffY + sp * 1.35f
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

    private fun drawScoreItem(
        target: ScoreDrawTarget,
        layout: ScoreLayout,
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
                    val ny = layout.yForStep(s)
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

                // Ledger lines below staff (step <= -2) and above staff (step >= 10)
                if (bottomStep <= -2) {
                    var ls = -2
                    while (ls >= bottomStep) {
                        val ly = layout.yForStep(ls)
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
                        val ly = layout.yForStep(ls)
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

                // Accidentals & noteheads
                for (cn in notesToDraw) {
                    val noteColor = if (cn.noteIdx in selectedChordNoteIndices) selectedNoteColor else color
                    cn.accidental?.let { acc ->
                        val ax = cn.accX ?: (cn.headX - accidentalWidth(acc, sp) - sp * 0.25f)
                        target.drawGlyph(acc.glyph, ax, cn.y, emPx, noteColor)
                    }
                    target.drawGlyph(item.duration.noteheadGlyph, cn.headX, cn.y, emPx, noteColor)
                }

                // Stem + Flag (connects bottom to top notehead, extending by stemLen)
                if (item.duration.hasStem) {
                    val stemLen = stemLengthSpaces(item.duration) * sp
                    if (!stemDown) {
                        val stemX = x + SmuflGlyphs.STEM_UP_ANCHOR_X * sp - stemThick * 0.5f
                        val stemStartY = bottomNote.y - SmuflGlyphs.STEM_UP_ANCHOR_Y * sp
                        val stemEndY = topNote.y - stemLen
                        target.drawLine(stemX, stemStartY, stemX, stemEndY, stemThick, color)

                        val flagGlyph = when (item.duration.flagCount) {
                            1 -> SmuflGlyphs.FLAG_8TH_UP
                            2 -> SmuflGlyphs.FLAG_16TH_UP
                            3 -> SmuflGlyphs.FLAG_32ND_UP
                            else -> null
                        }
                        if (flagGlyph != null) {
                            target.drawGlyph(flagGlyph, stemX - stemThick * 0.5f, stemEndY, emPx, color)
                        }
                    } else {
                        val stemX = x + SmuflGlyphs.STEM_DOWN_ANCHOR_X * sp + stemThick * 0.5f
                        val stemStartY = topNote.y - SmuflGlyphs.STEM_DOWN_ANCHOR_Y * sp
                        val stemEndY = bottomNote.y + stemLen
                        target.drawLine(stemX, stemStartY, stemX, stemEndY, stemThick, color)

                        val flagGlyph = when (item.duration.flagCount) {
                            1 -> SmuflGlyphs.FLAG_8TH_DOWN
                            2 -> SmuflGlyphs.FLAG_16TH_DOWN
                            3 -> SmuflGlyphs.FLAG_32ND_DOWN
                            else -> null
                        }
                        if (flagGlyph != null) {
                            target.drawGlyph(flagGlyph, stemX - stemThick * 0.5f, stemEndY, emPx, color)
                        }
                    }
                }

                // Augmentation dot(s)
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
                        target.drawGlyph(
                            SmuflGlyphs.AUGMENTATION_DOT,
                            dotX,
                            layout.yForStep(dotStep),
                            emPx,
                            color,
                        )
                    }
                }

                // Staccato dot (placed opposite the stem on the outer notehead, or above whole notes)
                if (item.noteStaccato()) {
                    val placeBelow = item.duration.hasStem && !stemDown
                    val extremeNote = if (placeBelow) bottomNote else topNote
                    val staccatoStep = staccatoStepForNote(extremeNote.staffStep, placeBelow)
                    val dotX = extremeNote.headX + noteheadWidth * 0.5f - SmuflGlyphs.ARTIC_STACCATO_WIDTH * sp * 0.5f
                    val dotY = layout.yForStep(staccatoStep) +
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
                val restY = layout.yForStep(restStep)
                target.drawGlyph(item.duration.restGlyph, x, restY, emPx, color)
                if (item.isDotted) {
                    target.drawGlyph(
                        SmuflGlyphs.AUGMENTATION_DOT,
                        x + sp * 1.45f,
                        layout.yForStep(5),
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

    private fun advanceForItem(item: ScoreItem, sp: Float): Float {
        val base = when (item.duration) {
            NoteDuration.WHOLE -> 5.8f
            NoteDuration.HALF -> 4.4f
            NoteDuration.QUARTER -> 3.3f
            NoteDuration.EIGHTH -> 2.5f
            NoteDuration.SIXTEENTH -> 2.0f
            NoteDuration.THIRTY_SECOND -> 1.85f
        }
        val dotBonus = if (item.isDotted) 0.7f else 0f
        return (base + dotBonus) * sp
    }
}
