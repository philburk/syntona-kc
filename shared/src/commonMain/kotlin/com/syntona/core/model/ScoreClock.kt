package com.syntona.core.model

import kotlin.time.TimeSource

object ScoreClock {
    private val mark = TimeSource.Monotonic.markNow()
    fun now(): ScoreTime = ScoreTime(mark.elapsedNow().inWholeNanoseconds)
}
