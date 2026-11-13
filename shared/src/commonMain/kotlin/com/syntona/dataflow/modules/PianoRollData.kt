package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2

/** A whole-number frequency ratio, kept reduced by gcd. */
data class Ratio(val n: Int, val d: Int) {
    val value: Double get() = n.toDouble() / d.toDouble()

    /** Interval size in fractional semitones. */
    val semitones: Double get() = 12.0 * log2(value)

    fun reduced(): Ratio {
        val g = gcd(n, d)
        return if (g <= 1) this else Ratio(n / g, d / g)
    }

    override fun toString(): String = "$n/$d"

    companion object {
        val UNISON = Ratio(1, 1)

        fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)
    }
}

/**
 * One note in a [PianoRollModule]. Time is measured in ticks ([PianoRollTiming.TPB] per beat).
 *
 * Pitch is derived, never stored: a root note (parentId == -1) sounds at [rootPitch]
 * (fractional semitones, 60.0 = middle C); a chained note sounds at its parent's pitch
 * plus the interval of [ratio]. Moving a parent therefore moves all dependents.
 */
class PianoRollNote(
    val id: Int,
    startTicks: Int,
    durationTicks: Int,
    parentId: Int = -1,
    ratio: Ratio = Ratio.UNISON,
    rootPitch: Double = 60.0,
    amplitude: Double = PianoRollModule.NOTE_AMPLITUDE,
) {
    var startTicks by mutableStateOf(startTicks)
    var durationTicks by mutableStateOf(durationTicks)

    /** Id of the note this note's pitch is relative to, or -1 for a root note. */
    var parentId by mutableStateOf(parentId)

    /** Frequency ratio relative to the parent. Unison for root notes. */
    var ratio by mutableStateOf(ratio)

    /** Absolute pitch in fractional semitones; only meaningful when this is a root note. */
    var rootPitch by mutableStateOf(rootPitch)

    /** Normalized note amplitude / velocity in [0.0, 1.0]. */
    var amplitude by mutableStateOf(amplitude)

    /** Transient selection state; not serialized. */
    var selected by mutableStateOf(false)

    val endTicks: Int get() = startTicks + durationTicks

    val isRoot: Boolean get() = parentId < 0

    fun copyData(newId: Int = id): PianoRollNote =
        PianoRollNote(newId, startTicks, durationTicks, parentId, ratio, rootPitch, amplitude)
}

/** Timing constants shared by the module and its editor. */
object PianoRollTiming {
    /** Ticks per beat (quarter note). */
    const val TPB = 240

    /** Grid snap quantum: a sixteenth note. */
    const val GRID_TICKS = TPB / 4

    const val BEATS_PER_MEASURE = 4
    const val TICKS_PER_MEASURE = TPB * BEATS_PER_MEASURE

    const val DEFAULT_NOTE_TICKS = TPB

    const val MIN_TEMPO = 20.0
    const val MAX_TEMPO = 300.0
    const val DEFAULT_TEMPO = 120.0

    const val MIN_BPM = 20
    const val MAX_BPM = 300
    const val DEFAULT_BPM = 120

    fun nanosPerTick(tempo: Double): Double = 60_000_000_000.0 / (tempo * TPB)
    fun nanosPerTick(bpm: Int): Long = (60_000_000_000.0 / (bpm.toDouble() * TPB)).toLong()

    fun snapTicks(ticks: Int): Int {
        val snapped = ((ticks.toDouble() / GRID_TICKS) + 0.5).toInt() * GRID_TICKS
        return snapped.coerceAtLeast(0)
    }
}

/** Just-intonation ratio selection, mirroring the RelInt reference app. */
object RatioMath {
    /** Base ratios within one octave; inverses and octave transpositions are derived. */
    val BASE_RATIOS = listOf(
        Ratio(1, 1), Ratio(16, 15), Ratio(10, 9), Ratio(9, 8),
        Ratio(8, 7), Ratio(7, 6), Ratio(6, 5), Ratio(5, 4),
        Ratio(4, 3), Ratio(45, 32), Ratio(3, 2), Ratio(8, 5),
        Ratio(5, 3), Ratio(7, 4), Ratio(16, 9), Ratio(15, 8),
    )

    private val searchRatios = BASE_RATIOS + Ratio(2, 1)

    /**
     * Returns the supported ratio closest to [semitoneOffset] (positive = up).
     * Octaves beyond the first are folded into the numerator (or denominator when
     * descending), so e.g. +19 semitones resolves to 3/1 and -12 to 1/2.
     */
    fun closestRatio(semitoneOffset: Double): Ratio {
        val isUp = semitoneOffset >= 0.0
        val absSemitones = abs(semitoneOffset)

        var octaves = floor(absSemitones / 12.0).toInt()
        val remainder = absSemitones % 12.0

        var best = searchRatios.first()
        var minDiff = Double.MAX_VALUE
        for (r in searchRatios) {
            val diff = abs(r.semitones - remainder)
            if (diff < minDiff) {
                minDiff = diff
                best = r
            }
        }
        if (best.n == 2 && best.d == 1) {
            octaves += 1
            best = Ratio.UNISON
        }

        val octaveFactor = 1 shl octaves
        return if (isUp) {
            Ratio(best.n * octaveFactor, best.d).reduced()
        } else {
            Ratio(best.d, best.n * octaveFactor).reduced()
        }
    }
}
