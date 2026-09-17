package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A discrete message event recorded with its relative arrival time (in timeline units)
 * from the start of a TrackSection.
 */
class TrackMessageEvent(
    initialTime: Double,
    initialValue: Double
) {
    var time by mutableStateOf(initialTime)
    var value by mutableStateOf(initialValue)

    operator fun component1(): Double = time
    operator fun component2(): Double = value

    fun copy(time: Double = this.time, value: Double = this.value): TrackMessageEvent =
        TrackMessageEvent(time, value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackMessageEvent) return false
        return time == other.time && value == other.value
    }

    override fun hashCode(): Int = 31 * time.hashCode() + value.hashCode()

    override fun toString(): String = "TrackMessageEvent(time=$time, value=$value)"
}

/**
 * Data payload for a TrackSection inside a MessageTrack.
 * Stores discrete events with relative arrival times and values.
 */
class MessageSectionData(
    initialEvents: List<TrackMessageEvent>? = null,
    initialMin: Double = 0.0,
    initialMax: Double = 1.0
) {
    var min by mutableStateOf(initialMin)
    var max by mutableStateOf(initialMax)

    val events = mutableStateListOf<TrackMessageEvent>().apply {
        if (initialEvents != null) {
            addAll(initialEvents.sortedBy { it.time })
        }
    }

    fun addEvent(time: Double, value: Double): TrackMessageEvent {
        if (value < min) min = value
        if (value > max) max = value
        val clampedVal = value.coerceIn(min, max)

        // If an event already exists at almost the exact same time (< 0.001s), update its value
        val existingIndex = events.indexOfFirst { kotlin.math.abs(it.time - time) < 0.001 }
        if (existingIndex >= 0) {
            val updated = events[existingIndex].copy(value = clampedVal)
            events[existingIndex] = updated
            return updated
        }

        val event = TrackMessageEvent(time, clampedVal)
        val insertIndex = events.indexOfFirst { it.time > time }
        if (insertIndex >= 0) {
            events.add(insertIndex, event)
        } else {
            events.add(event)
        }
        return event
    }

    fun removeEvent(event: TrackMessageEvent) {
        events.remove(event)
    }

    fun clearEvents() {
        events.clear()
    }

    fun copy(): MessageSectionData {
        return MessageSectionData(
            initialEvents = events.map { it.copy() },
            initialMin = min,
            initialMax = max
        )
    }
}
