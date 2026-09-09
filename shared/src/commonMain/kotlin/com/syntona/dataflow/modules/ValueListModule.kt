package com.syntona.dataflow.modules

import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.IntegerInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack

/**
 * A module that stores a dynamic list of floating-point values backed by a primitive DoubleArray.
 * Optimized for real-time performance with zero memory allocations during runtime operations.
 *
 * Inputs:
 * - `get` (IntegerInputJack, kickable): Receives an index, retrieves value at that index, and emits to `out`.
 * - `set` (InputJack, kickable): Sets value at `index` to the incoming value.
 * - `index` (IntegerInputJack, non-kickable): Target index for `set`.
 * - `add` (InputJack, kickable): Appends incoming value to the list and emits updated size to `size`.
 * - `clear` (CommandJack): Resets size to 0, resizes capacity if needed, and emits 0 to `size`.
 * - `capacity` (IntegerInputJack, non-kickable): Maximum size limit (default 1024). Reallocates only on `clear`.
 *
 * Outputs:
 * - `out` (OutputJack): Value retrieved by `get`.
 * - `size` (OutputJack, integer): Current number of elements, emitted on `add` or `clear`.
 */
class ValueListModule : Module("valueList") {

    var count: Int = 0
        private set

    /** Current number of elements in the list. */
    val size: Int get() = count

    private var storage = DoubleArray(1024)

    /** Physical size of the backing DoubleArray. */
    val currentStorageCapacity: Int
        get() = storage.size

    val addIn = addInput("add", isKickable = true)

    val getIn = addIntegerInput("get", isKickable = true).also {
        it.min = 0.0
        it.max = 65535.0
    }

    val setIn = addInput("set", isKickable = true)

    val indexIn = addIntegerInput("index", isKickable = false).also {
        it.min = 0.0
        it.max = 65535.0
    }

    val clearIn = addCommandInput("clear")

    val capacityIn = addIntegerInput("capacity", isKickable = false).also {
        it.min = 1.0
        it.max = 65536.0
        it.setValue(1024.0)
    }

    val out = addOutput("out")
    val sizeOut = addIntegerOutput("size")

    // Convenient aliases
    val getJack get() = getIn
    val setJack get() = setIn
    val indexJack get() = indexIn
    val addJack get() = addIn
    val clearJack get() = clearIn
    val capacityJack get() = capacityIn

    override fun execute(triggerMessage: Message) {
        if (addIn.isKicked) {
            val maxCap = minOf(capacityIn.getClippedIntValue(), storage.size)
            if (count < maxCap) {
                val value = (triggerMessage as? Message.FloatValue)?.value ?: addIn.getDoubleValue()
                storage[count++] = value
                sizeOut.send(Message.FloatValue(triggerMessage.time, count.toDouble()))
            }
        }

        if (getIn.isKicked) {
            val idx = (triggerMessage as? Message.FloatValue)?.value?.toInt() ?: getIn.getDoubleValue().toInt()
            if (idx in 0 until count) {
                val value = storage[idx]
                out.send(Message.FloatValue(triggerMessage.time, value))
            }
        }

        if (setIn.isKicked) {
            val idx = indexIn.getDoubleValue().toInt()
            if (idx in 0 until count) {
                val value = (triggerMessage as? Message.FloatValue)?.value ?: setIn.getDoubleValue()
                storage[idx] = value
            }
        }

        if (clearIn.isKicked) {
            count = 0
            val requestedCap = capacityIn.getClippedIntValue().coerceAtLeast(1)
            if (storage.size != requestedCap) {
                storage = DoubleArray(requestedCap)
            }
            sizeOut.send(Message.FloatValue(triggerMessage.time, 0.0))
        }
    }

    /** Access element at [index] without firing jacks (useful for testing or direct inspection). */
    operator fun get(index: Int): Double? {
        return if (index in 0 until count) storage[index] else null
    }
}
