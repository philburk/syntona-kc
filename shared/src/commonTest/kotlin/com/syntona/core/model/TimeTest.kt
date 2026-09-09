package com.syntona.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

import kotlin.jvm.JvmInline

@JvmInline
value class TestEvent(val value: Int): Comparable<TestEvent> {
    override fun compareTo(other: TestEvent): Int = value.compareTo(other.value)
}

class TimeTest {

    @Test
    fun scoreTime() {
        val a = ScoreTime(100)
        val b = ScoreTime(200)
        val c = a + b

        assertEquals(ScoreTime(300), c)
        assertEquals(200, b.nanoseconds)
    }

    @Test
    fun scoreTimeSecondsConversion() {
        val t = ScoreTime(1_500_000_000L)
        assertEquals(1.5, t.seconds)

        val fromSec = ScoreTime.fromSeconds(2.75)
        assertEquals(2_750_000_000L, fromSec.nanoseconds)
        assertEquals(2.75, fromSec.seconds)
    }

    @Test
    fun queueOneEvent() {
        val queue = ScheduledQueue<TestEvent>()
        queue.add(ScoreTime(100), TestEvent(555))
        val nothing = queue.removeNext(ScoreTime(50))
        assertNull(nothing)
        val first = queue.removeNext(ScoreTime(100))
        assertEquals(TestEvent(555), first)
    }


    @Test
    fun queueTwoEvents() {
        val queue = ScheduledQueue<TestEvent>()
        queue.add(ScoreTime(100), TestEvent(555))
        queue.add(ScoreTime(75), TestEvent(777))

        val a = queue.removeNext(ScoreTime(80))
        val b = queue.removeNext(ScoreTime(120))
        assertEquals(TestEvent(777), a)
        assertEquals(TestEvent(555), b)
    }

}
