package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class ScaleModule(
    type: String,
    val isEditable: Boolean,
    defaultScale: DoubleArray
) : Module(type) {
    var scaleString by mutableStateOf(defaultScale.joinToString(",") { formatNumber(it) })
    var scaleDegrees by mutableStateOf(defaultScale)
    var errorMessage by mutableStateOf<String?>(null)

    // Inputs:
    val inputIn = addIntegerInput("in", isKickable = true)
    val inputOffset = addInput("offset", isKickable = false).apply { setValue(36.0) }

    // Output:
    val output = addOutput("out")

    fun updateScale(scaleStr: String): Boolean {
        if (scaleStr.isBlank()) {
            errorMessage = "Scale cannot be empty"
            return false
        }
        val parts = scaleStr.split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        val doubles = mutableListOf<Double>()
        for ((idx, p) in parts.withIndex()) {
            val d = p.toDoubleOrNull()
            if (d != null) {
                doubles.add(d)
            } else {
                val ratioParts = p.split(Regex("[:/]"))
                if (ratioParts.size == 2) {
                    val num = ratioParts[0].toIntOrNull()
                    val den = ratioParts[1].toIntOrNull()
                    if (num != null && den != null && den != 0) {
                        val ratioVal = num.toDouble() / den.toDouble()
                        if (ratioVal > 0) {
                            val pitch = 12.0 * kotlin.math.log2(ratioVal)
                            doubles.add(pitch)
                            continue
                        }
                    }
                }
                errorMessage = "Invalid number or ratio at index $idx: '$p'"
                return false
            }
        }
        if (doubles.isEmpty()) {
            errorMessage = "Scale must contain at least one pitch degree"
            return false
        }
        scaleDegrees = doubles.toDoubleArray()
        scaleString = scaleStr
        errorMessage = null
        return true
    }

    override fun execute(triggerMessage: Message) {
        val inputVal = inputIn.getDoubleValue()
        val offsetVal = inputOffset.getDoubleValue()
        val size = scaleDegrees.size
        if (size == 0) return

        // Formula:
        // index = input % scale.size
        // octave = (input - index) / scale.size
        // output = scale[index] + (12 * octave) + offset
        val inputInt = if (inputVal < 0.0) kotlin.math.floor(inputVal).toInt() else inputVal.toInt()
        var index = inputInt % size
        if (index < 0) {
            index += size
        }
        val octave = (inputInt - index) / size

        val scaleVal = scaleDegrees[index]
        val result = scaleVal + (12.0 * octave) + offsetVal

        output.send(Message.FloatValue(triggerMessage.time, result))
    }

    private fun formatNumber(value: Double): String {
        if (value == value.toLong().toDouble()) {
            return value.toLong().toString()
        }
        return value.toString()
    }
}
