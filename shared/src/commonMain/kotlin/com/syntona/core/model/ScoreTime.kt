package com.syntona.core.model

import kotlin.jvm.JvmInline

@JvmInline
value class ScoreTime(val nanoseconds: Long) : Comparable<ScoreTime> {
    override fun compareTo(other: ScoreTime): Int = nanoseconds.compareTo(other.nanoseconds)

    operator fun plus(other: ScoreTime) = ScoreTime(this.nanoseconds + other.nanoseconds)
    operator fun minus(other: ScoreTime) = ScoreTime(this.nanoseconds - other.nanoseconds)

    val seconds: Double
        get() = nanoseconds.toDouble() / NANOS_PER_SECOND

    companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0

        val Zero = ScoreTime(0L)

        fun fromSeconds(seconds: Double): ScoreTime =
            ScoreTime((seconds * NANOS_PER_SECOND).toLong())
    }
}
