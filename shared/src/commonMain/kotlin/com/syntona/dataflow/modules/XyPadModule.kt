package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class XyPadModule : Module("xyPad") {
    val kick = addInput("kick", isKickable = true)
    var x by mutableStateOf(0.5)
    var y by mutableStateOf(0.5)

    val inX: InputJack = addCustomInput(object : InputJack("x", this) {
        override fun getValue(): Double = x
        override fun getDoubleValue(default: Double): Double = x
    }).also {
        it.min = 0.0
        it.max = 1.0
    }
    val inY: InputJack = addCustomInput(object : InputJack("y", this) {
        override fun getValue(): Double = y
        override fun getDoubleValue(default: Double): Double = y
    }).also {
        it.min = 0.0
        it.max = 1.0
    }

    val outX = addOutput("x")
    val outY = addOutput("y")
    val on = addOutput("on")
    val off = addOutput("off")

    var width by mutableStateOf(225f)
    var height by mutableStateOf(180f)

    var isExponentialX: Boolean
        get() = inX.isExponential
        set(value) { inX.isExponential = value }

    var isExponentialY: Boolean
        get() = inY.isExponential
        set(value) { inY.isExponential = value }

    var isIntegerX: Boolean
        get() = inX.isInteger
        set(value) { inX.isInteger = value }

    var isIntegerY: Boolean
        get() = inY.isInteger
        set(value) { inY.isInteger = value }

    fun setX(newX: Double, time: ScoreTime = ScoreClock.now()) {
        val rounded = if (inX.isInteger) kotlin.math.round(newX) else newX
        val low = minOf(inX.min, inX.max)
        val high = maxOf(inX.min, inX.max)
        x = rounded.coerceIn(low, high)
        outX.send(Message.FloatValue(time, x))
    }

    fun setY(newY: Double, time: ScoreTime = ScoreClock.now()) {
        val rounded = if (inY.isInteger) kotlin.math.round(newY) else newY
        val low = minOf(inY.min, inY.max)
        val high = maxOf(inY.min, inY.max)
        y = rounded.coerceIn(low, high)
        outY.send(Message.FloatValue(time, y))
    }

    fun setXY(newX: Double, newY: Double, time: ScoreTime = ScoreClock.now()) {
        val roundedX = if (inX.isInteger) kotlin.math.round(newX) else newX
        val roundedY = if (inY.isInteger) kotlin.math.round(newY) else newY
        val lowX = minOf(inX.min, inX.max)
        val highX = maxOf(inX.min, inX.max)
        val lowY = minOf(inY.min, inY.max)
        val highY = maxOf(inY.min, inY.max)
        x = roundedX.coerceIn(lowX, highX)
        y = roundedY.coerceIn(lowY, highY)
        // When sending messages out multiple jacks from one module at the same time,
        // start sending from the bottom jacks and work up.
        outY.send(Message.FloatValue(time, y))
        outX.send(Message.FloatValue(time, x))
    }

    fun press(time: ScoreTime = ScoreClock.now()) {
        on.send(Message.FloatValue(time, 1.0))
    }

    fun release(time: ScoreTime = ScoreClock.now()) {
        off.send(Message.FloatValue(time, 0.0))
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === inX && msg is Message.FloatValue) {
            setX(msg.value, msg.time)
        } else if (jack === inY && msg is Message.FloatValue) {
            setY(msg.value, msg.time)
        }
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {
        // When sending messages out multiple jacks from one module at the same time,
        // start sending from the bottom jacks and work up.
        outY.send(Message.FloatValue(triggerMessage.time, y))
        outX.send(Message.FloatValue(triggerMessage.time, x))
    }
}
