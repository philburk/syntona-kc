/*
 * Copyright (C) 2026 Phil Burk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.syntona.core.model

/**
 * A thread-safe queue for scheduling musical events.
 * Events are ordered by [ScoreTime].
 */
class ScheduledQueue<T> {
    // Note: Kotlin/Common doesn't have a built-in SortedMap or a multi-platform 'synchronized'.
    // For a simple implementation, we'll use a sorted list of entries.
    // In a high-performance audio app, you might want to use a more specialized structure
    // like a PriorityQueue if available, or a platform-specific concurrent TreeMap.

    private val entries = mutableListOf<Entry<T>>()

    private data class Entry<T>(val time: ScoreTime, val obj: T)

    val isEmpty: Boolean
        get() = entries.isEmpty()

    /**
     * Add an object to the queue at the specified time.
     */
    fun add(time: ScoreTime, obj: T) {
        val newEntry = Entry(time, obj)
        // Insert while maintaining sort order (stable FIFO for equal timestamps)
        val index = entries.binarySearch { it.time.compareTo(time) }
        val insertionPoint = if (index < 0) {
            -(index + 1)
        } else {
            var p = index
            while (p < entries.size && entries[p].time <= time) {
                p++
            }
            p
        }
        entries.add(insertionPoint, newEntry)
    }

    /**
     * Remove and return the next object if its time is less than or equal to [time].
     */
    fun removeNext(time: ScoreTime): T? {
        if (entries.isEmpty()) return null

        val first = entries.first()
        if (first.time <= time) {
            return entries.removeAt(0).obj
        }
        return null
    }

    /**
     * Get the time of the next event in the queue, or null if empty.
     */
    val nextTime: ScoreTime?
        get() = entries.firstOrNull()?.time
}
