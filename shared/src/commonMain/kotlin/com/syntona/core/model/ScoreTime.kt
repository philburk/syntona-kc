package com.syntona.core.model

import kotlin.jvm.JvmInline

@JvmInline
value class ScoreTime(val nanoseconds: Long) : Comparable<ScoreTime> {
    override fun compareTo(other: ScoreTime): Int = nanoseconds.compareTo(other.nanoseconds)

    operator fun plus(other: ScoreTime) = ScoreTime(this.nanoseconds + other.nanoseconds)
    operator fun minus(other: ScoreTime) = ScoreTime(this.nanoseconds - other.nanoseconds)

    companion object {
        val Zero = ScoreTime(0L)
    }
}
