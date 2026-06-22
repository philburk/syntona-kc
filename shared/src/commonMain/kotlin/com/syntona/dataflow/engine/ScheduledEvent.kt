package com.syntona.dataflow.engine

class ScheduledEvent(private val action: () -> Unit) {
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }

    fun fire() {
        if (!isCancelled) action()
    }
}
