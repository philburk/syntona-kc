package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.unitgen.ChebyshevOscillator

/**
 * Waveshaping oscillator built on KSyn's [ChebyshevOscillator]: a sine wave passed through a
 * Chebyshev polynomial. The jacks come from the oscillator's ports; this class adds the
 * polynomial [order], which is chosen from a menu rather than patched.
 */
class ChebyshevOscModule : UnitGeneratorModule(TYPE_NAME, ChebyshevOscillator()) {
    private val oscillator: ChebyshevOscillator get() = unit as ChebyshevOscillator

    private var _order by mutableStateOf(oscillator.order)

    /**
     * Order of the Chebyshev polynomial, one of [ChebyshevOscillator.ORDERS].
     * Out-of-range or even values, such as from a hand-edited file, snap to the nearest valid one.
     */
    var order: Int
        get() = _order
        set(value) {
            val valid = nearestValidOrder(value)
            _order = valid
            oscillator.order = valid
            (parentPatch as? PolyPatchModule)?.syncChebyshevOrder(this, valid)
        }

    companion object {
        const val TYPE_NAME = "chebyshevOsc"

        fun nearestValidOrder(order: Int): Int =
            ChebyshevOscillator.ORDERS.minBy { kotlin.math.abs(it - order) }
    }
}
