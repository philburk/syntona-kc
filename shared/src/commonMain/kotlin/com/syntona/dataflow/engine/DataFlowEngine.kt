package com.syntona.dataflow.engine

import androidx.compose.runtime.snapshots.Snapshot
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.core.model.ScheduledQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

open class DataFlowEngine(private val scope: CoroutineScope) {

    private val commandChannel = Channel<SyntonaCommand>(Channel.UNLIMITED)
    // Access only from within the engine coroutine
    private val scheduledQueue = ScheduledQueue<ScheduledEvent>()
    private val undoStack = ArrayDeque<SyntonaCommand>()
    private val redoStack = ArrayDeque<SyntonaCommand>()

    init {
        scope.launch { runLoop() }
    }

    fun post(command: SyntonaCommand) {
        commandChannel.trySend(command)
    }

    // Must only be called from within the engine coroutine (e.g., from module execute() or event fire())
    open fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
        val event = ScheduledEvent(action)
        scheduledQueue.add(time, event)
        return event
    }

    fun clearHistory() {
        post(object : SyntonaCommand {
            override val isUndoable = false
            override fun execute() { undoStack.clear(); redoStack.clear() }
            override fun undo() {}
        })
    }

    fun undo() {
        post(object : SyntonaCommand {
            override val isUndoable = false
            override fun execute() {
                val cmd = undoStack.removeLastOrNull() ?: return
                cmd.undo()
                redoStack.addLast(cmd)
            }
            override fun undo() {}
        })
    }

    fun redo() {
        post(object : SyntonaCommand {
            override val isUndoable = false
            override fun execute() {
                val cmd = redoStack.removeLastOrNull() ?: return
                cmd.execute()
                if (cmd.isUndoable) undoStack.addLast(cmd)
            }
            override fun undo() {}
        })
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            fireScheduledEvents()

            // Drain all pending commands without blocking
            while (true) {
                val cmd = commandChannel.tryReceive().getOrNull() ?: break
                processCommand(cmd)
            }

            // Calculate delay until next scheduled event
            val now = ScoreClock.now()
            val nextNs = scheduledQueue.nextTime?.nanoseconds
            val delayMs = if (nextNs != null) {
                ((nextNs - now.nanoseconds) / 1_000_000L).coerceIn(1L, MAX_WAIT_MS)
            } else {
                MAX_WAIT_MS
            }

            // Suspend until next command arrives or timer fires
            val cmd = withTimeoutOrNull(delayMs) { commandChannel.receive() }
            if (cmd != null) {
                processCommand(cmd)
            }
        }
    }

    private fun processCommand(cmd: SyntonaCommand) {
        // One failing command must not stop the engine, which drives playback and every UI edit
        if (!runGuarded("command ${cmd::class.simpleName}") { cmd.execute() }) return
        if (cmd.isUndoable) {
            undoStack.addLast(cmd)
            redoStack.clear()
        }
    }

    /** Runs [action] in a snapshot. Returns false if it failed, having reported why. */
    private fun runGuarded(what: String, action: () -> Unit): Boolean =
        try {
            Snapshot.withMutableSnapshot(action)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("DataFlowEngine: $what failed: $e")
            e.printStackTrace()
            false
        }

    private fun fireScheduledEvents() {
        val now = ScoreClock.now()
        while (true) {
            val event = scheduledQueue.removeNext(now) ?: break
            if (!event.isCancelled) {
                runGuarded("scheduled event") { event.fire() }
            }
        }
    }

    companion object {
        private const val MAX_WAIT_MS = 10_000L
    }
}
