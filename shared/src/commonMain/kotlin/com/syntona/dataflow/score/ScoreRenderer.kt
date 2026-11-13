package com.syntona.dataflow.score

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.roundToInt

/** Palette for score rendering (paper-like light mode & soft slate dark mode). */
data class ScoreRenderColors(
    val ink: Color,
    val staffLine: Color,
    val measureNumber: Color,
    val activeNote: Color,
) {
    companion object {
        fun forTheme(darkMode: Boolean): ScoreRenderColors = if (darkMode) {
            ScoreRenderColors(
                ink = Color(0xFFE2E8F0),
                staffLine = Color(0xFF94A3B8),
                measureNumber = Color(0xFF64748B),
                activeNote = Color(0xFF60A5FA),
            )
        } else {
            ScoreRenderColors(
                ink = Color(0xFF111827),
                staffLine = Color(0xFF374151),
                measureNumber = Color(0xFF6B7280),
                activeNote = Color(0xFF2563EB),
            )
        }
    }
}

data class ScoreLayout(
    val totalWidthPx: Float,
    val totalHeightPx: Float,
)

/**
 * Pure layout and engraving renderer for Phase 1 single-stave notation.
 * Draws strictly through [ScoreDrawTarget] so screen and future PDF outputs share one implementation.
 */
object ScoreRenderer {

    private const val STEM_LENGTH_SPACES = 3.5f
    private const val TOP_MARGIN_SPACES = 4.5f
    private const val BOTTOM_MARGIN_SPACES = 4.5f

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
    ): ScoreLayout {
        val sp = staffSpacePx.coerceAtLeast(4f)
        val emPx = sp * 4f
        val leftMargin = sp * 2.0f
        val rightMargin = sp * 2.0f

        // Bottom line (step 0) is bottomStaffY; step 8 is top line (topStaffY = bottomStaffY - 4 * sp)
        val topStaffY = TOP_MARGIN_SPACES * sp
        val bottomStaffY = topStaffY + 4f * sp
        val totalHeight = bottomStaffY + BOTTOM_MARGIN_SPACES * sp

        fun yForStep(stepFromBottom: Int): Float = bottomStaffY - stepFromBottom * (sp * 0.5f)

        // Pre-pass: lay out header (clef, key signature, time signature) + measures horizontally
        var cursorX = leftMargin + sp * 0.7f

        val clefX = cursorX
        val clefWidth = max(target.measureGlyphWidth(score.clef.glyph, emPx), sp * 2.6f)
        cursorX += clefWidth + sp * 0.8f

        val keySteps = score.keySignature.trebleStaffSteps()
        val keyGlyph = if (score.keySignature.fifths >= 0) SmuflGlyphs.ACCIDENTAL_SHARP else SmuflGlyphs.ACCIDENTAL_FLAT
        val keyGlyphStepWidth = if (score.keySignature.fifths >= 0) {
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
            target.measureGlyphWidth(numStr, emPx),
            target.measureGlyphWidth(denStr, emPx),
        ).coerceAtLeast(sp * 1.6f)
        cursorX += timeSigWidth + sp * 1.6f

        data class PlacedItem(
            val measureIdx: Int,
            val globalIdx: Int,
            val item: ScoreItem,
            val x: Float,
            val accidental: Accidental?,
            val staffStep: Int?,
        )

        data class PlacedMeasure(
            val measure: ScoreMeasure,
            val startX: Float,
            val barlineX: Float,
            val items: List<PlacedItem>,
        )

        val placedMeasures = mutableListOf<PlacedMeasure>()
        var globalIdx = 0

        for ((mIdx, measure) in score.measures.withIndex()) {
            val mStartX = cursorX
            // Measure-local accidental table keyed by diatonicStep
            val activeAlters = mutableMapOf<Int, Int>()
            val itemsInMeasure = mutableListOf<PlacedItem>()

            cursorX += sp * 1.1f
            for (item in measure.items) {
                var shownAccidental: Accidental? = null
                var step: Int? = null
                if (item is ScoreItem.Note) {
                    val keyDefault = score.keySignature.defaultAlter(item.pitch.letter)
                    val dStep = item.pitch.diatonicStep
                    val currentAlter = activeAlters[dStep] ?: keyDefault
                    if (item.pitch.alter != currentAlter) {
                        shownAccidental = when (item.pitch.alter) {
                            1 -> Accidental.SHARP
                            -1 -> Accidental.FLAT
                            else -> Accidental.NATURAL
                        }
                        activeAlters[dStep] = item.pitch.alter
                    }
                    step = score.clef.staffStepFromBottom(item.pitch)
                }

                // Leave space to the left of the notehead when printing an accidental
                if (shownAccidental != null) {
                    cursorX += accidentalWidth(shownAccidental, sp) + sp * 0.35f
                }

                val itemX = cursorX
                itemsInMeasure.add(PlacedItem(mIdx, globalIdx++, item, itemX, shownAccidental, step))

                val advance = advanceForItem(item, sp)
                cursorX += advance
            }

            if (measure.items.isEmpty()) {
                cursorX += sp * 6f
            } else {
                cursorX += sp * 0.6f
            }

            val barlineX = cursorX
            placedMeasures.add(PlacedMeasure(measure, mStartX, barlineX, itemsInMeasure))
            cursorX += sp * 0.6f
        }

        val finalEndX = max(cursorX + sp * 0.8f + rightMargin, minWidthPx)
        val staffEndX = if (placedMeasures.isNotEmpty()) {
            max(placedMeasures.last().barlineX + SmuflGlyphs.THICK_BARLINE_THICKNESS * sp + sp * 1.5f, minWidthPx - rightMargin)
        } else {
            finalEndX - rightMargin
        }

        // 1. Draw the 5 staff lines
        val staffLineThick = SmuflGlyphs.STAFF_LINE_THICKNESS * sp
        for (lineIdx in 0..4) {
            val y = yForStep(lineIdx * 2)
            target.drawLine(leftMargin, y, staffEndX, y, staffLineThick, colors.staffLine)
        }

        // 2. Draw clef, key signature, time signature
        val clefBaselineY = yForStep(score.clef.glyphLineFromBottom * 2)
        target.drawGlyph(score.clef.glyph, clefX, clefBaselineY, emPx, colors.ink)

        keySteps.forEachIndexed { idx, stepOffset ->
            val kx = keyStartX + idx * keyGlyphStepWidth
            target.drawGlyph(keyGlyph, kx, yForStep(stepOffset), emPx, colors.ink)
        }

        // Time sig upper digit centered on line 3 (step 6), lower digit on line 1 (step 2)
        target.drawGlyph(numStr, timeSigX, yForStep(6), emPx, colors.ink)
        target.drawGlyph(denStr, timeSigX, yForStep(2), emPx, colors.ink)

        // 3. Draw measures, barlines, notes, and rests
        val thinBar = SmuflGlyphs.THIN_BARLINE_THICKNESS * sp
        val thickBar = SmuflGlyphs.THICK_BARLINE_THICKNESS * sp
        val stemThick = SmuflGlyphs.STEM_THICKNESS * sp
        val ledgerThick = SmuflGlyphs.LEGER_LINE_THICKNESS * sp
        val ledgerExt = SmuflGlyphs.LEGER_LINE_EXTENSION * sp

        for ((idx, pm) in placedMeasures.withIndex()) {
            // Subtle measure number above bar start (after first barline)
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
                val itemColor = if (pi.globalIdx == activeItemGlobalIndex) colors.activeNote else colors.ink
                when (val item = pi.item) {
                    is ScoreItem.Note -> {
                        val step = pi.staffStep ?: 4
                        val y = yForStep(step)
                        val noteheadWidth = if (item.duration == NoteDuration.WHOLE) {
                            SmuflGlyphs.NOTEHEAD_WHOLE_WIDTH * sp
                        } else {
                            SmuflGlyphs.NOTEHEAD_WIDTH * sp
                        }

                        // Ledger lines below staff (step <= -2) and above staff (step >= 10)
                        if (step <= -2) {
                            var ls = -2
                            while (ls >= step) {
                                val ly = yForStep(ls)
                                target.drawLine(
                                    pi.x - ledgerExt,
                                    ly,
                                    pi.x + noteheadWidth + ledgerExt,
                                    ly,
                                    ledgerThick,
                                    itemColor,
                                )
                                ls -= 2
                            }
                        }
                        if (step >= 10) {
                            var ls = 10
                            while (ls <= step) {
                                val ly = yForStep(ls)
                                target.drawLine(
                                    pi.x - ledgerExt,
                                    ly,
                                    pi.x + noteheadWidth + ledgerExt,
                                    ly,
                                    ledgerThick,
                                    itemColor,
                                )
                                ls += 2
                            }
                        }

                        // Accidental
                        pi.accidental?.let { acc ->
                            val accWidth = accidentalWidth(acc, sp)
                            target.drawGlyph(
                                acc.glyph,
                                pi.x - accWidth - sp * 0.25f,
                                y,
                                emPx,
                                itemColor,
                            )
                        }

                        // Notehead
                        target.drawGlyph(item.duration.noteheadGlyph, pi.x, y, emPx, itemColor)

                        // Stem + Flag
                        if (item.duration.hasStem) {
                            val stemDown = step >= 4 // middle line (B4) and above get downward stems
                            val stemLen = STEM_LENGTH_SPACES * sp
                            if (!stemDown) {
                                val stemX = pi.x + SmuflGlyphs.STEM_UP_ANCHOR_X * sp - stemThick * 0.5f
                                val stemStartY = y - SmuflGlyphs.STEM_UP_ANCHOR_Y * sp
                                val stemEndY = y - stemLen
                                target.drawLine(stemX, stemStartY, stemX, stemEndY, stemThick, itemColor)

                                val flagGlyph = when (item.duration.flagCount) {
                                    1 -> SmuflGlyphs.FLAG_8TH_UP
                                    2 -> SmuflGlyphs.FLAG_16TH_UP
                                    else -> null
                                }
                                if (flagGlyph != null) {
                                    target.drawGlyph(flagGlyph, stemX - stemThick * 0.5f, stemEndY, emPx, itemColor)
                                }
                            } else {
                                val stemX = pi.x + SmuflGlyphs.STEM_DOWN_ANCHOR_X * sp + stemThick * 0.5f
                                val stemStartY = y - SmuflGlyphs.STEM_DOWN_ANCHOR_Y * sp
                                val stemEndY = y + stemLen
                                target.drawLine(stemX, stemStartY, stemX, stemEndY, stemThick, itemColor)

                                val flagGlyph = when (item.duration.flagCount) {
                                    1 -> SmuflGlyphs.FLAG_8TH_DOWN
                                    2 -> SmuflGlyphs.FLAG_16TH_DOWN
                                    else -> null
                                }
                                if (flagGlyph != null) {
                                    target.drawGlyph(flagGlyph, stemX - stemThick * 0.5f, stemEndY, emPx, itemColor)
                                }
                            }
                        }

                        // Augmentation dot (nudged half-space upward if note sits on a line)
                        if (item.isDotted) {
                            val dotStep = if (step % 2 == 0) step + 1 else step
                            target.drawGlyph(
                                SmuflGlyphs.AUGMENTATION_DOT,
                                pi.x + noteheadWidth + sp * 0.35f,
                                yForStep(dotStep),
                                emPx,
                                itemColor,
                            )
                        }
                    }
                    is ScoreItem.Rest -> {
                        // Whole rest hangs from 4th line (step 6); half rest sits on 3rd line (step 4); others center on step 4
                        val restStep = if (item.duration == NoteDuration.WHOLE) 6 else 4
                        val restY = yForStep(restStep)
                        target.drawGlyph(item.duration.restGlyph, pi.x, restY, emPx, itemColor)
                        if (item.isDotted) {
                            target.drawGlyph(
                                SmuflGlyphs.AUGMENTATION_DOT,
                                pi.x + sp * 1.45f,
                                yForStep(5),
                                emPx,
                                itemColor,
                            )
                        }
                    }
                }
            }

            // Barline at end of measure (final barline is thin+thick double bar)
            val isLast = idx == placedMeasures.lastIndex
            if (isLast) {
                val sep = SmuflGlyphs.THIN_THICK_SEPARATION * sp
                val thickX = staffEndX - thickBar * 0.5f
                val thinX = thickX - sep - thickBar * 0.5f - thinBar * 0.5f
                target.drawLine(thinX, topStaffY, thinX, bottomStaffY, thinBar, colors.ink)
                target.drawLine(thickX, topStaffY, thickX, bottomStaffY, thickBar, colors.ink)
            } else {
                target.drawLine(pm.barlineX, topStaffY, pm.barlineX, bottomStaffY, thinBar, colors.ink)
            }
        }

        // Initial left barline closing the stave cleanly
        target.drawLine(leftMargin, topStaffY, leftMargin, bottomStaffY, thinBar, colors.ink)

        return ScoreLayout(
            totalWidthPx = (staffEndX + rightMargin).coerceAtLeast(minWidthPx),
            totalHeightPx = totalHeight.roundToInt().toFloat(),
        )
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
        }
        val dotBonus = if (item.isDotted) 0.7f else 0f
        return (base + dotBonus) * sp
    }
}
