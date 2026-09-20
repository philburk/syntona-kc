package com.syntona.dataflow.engine

import com.syntona.core.model.ScoreClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertTrue

class DataFlowEngineTest {

    private fun command(action: () -> Unit) = object : SyntonaCommand {
        override val isUndoable = false
        override fun execute() = action()
        override fun undo() {}
    }

    /** Waits for [condition], so a test does not depend on how quickly the engine runs. */
    private suspend fun awaitTrue(condition: () -> Boolean): Boolean =
        withTimeoutOrNull(5_000) {
            while (!condition()) delay(5)
            true
        } ?: false

    @Test
    fun engineKeepsRunningAfterACommandThrows() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val engine = DataFlowEngine(scope)
        try {
            val ran = AtomicBoolean(false)
            engine.post(command { throw IllegalArgumentException("boom") })
            engine.post(command { ran.set(true) })
            assertTrue(awaitTrue { ran.get() }, "A failing command must not stop the engine")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun engineKeepsRunningAfterAScheduledEventThrows() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val engine = DataFlowEngine(scope)
        try {
            val fired = AtomicBoolean(false)
            // Scheduling has to happen on the engine thread, so do it from a command
            engine.post(command {
                engine.schedule(ScoreClock.now()) { throw IllegalStateException("boom") }
                engine.schedule(ScoreClock.now()) { fired.set(true) }
            })
            assertTrue(awaitTrue { fired.get() }, "A failing scheduled event must not stop the engine")
        } finally {
            scope.cancel()
        }
    }
}
