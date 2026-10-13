package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.dataflow.model.Module

/**
 * Deletes modules without tearing them down, so undo can put back the same working modules.
 * Teardown would stop and release their audio units, voices and MIDI subscriptions for good.
 * The modules are torn down when this command leaves the undo history, see [discard].
 */
class DeleteModulesCommand(
    private val graphState: GraphState,
    private val modules: List<Module>,
    private val savedPositions: Map<Module, Offset>,
    private val savedConnections: List<GraphState.UIConnection>
) : SyntonaCommand {
    override val isUndoable = true

    private var removed: List<Module> = emptyList()

    override fun execute() {
        removed = modules.filter { graphState.canDeleteModule(it) }
        graphState.removeModulesDirectly(removed, tearDown = false)
        graphState.detachedModules.addAll(removed)
    }

    override fun undo() {
        graphState.detachedModules.removeAll(removed)
        removed.forEach { module ->
            graphState.addModuleDirectly(module, savedPositions[module] ?: Offset.Zero)
        }
        savedConnections.forEach { conn ->
            graphState.connectDirectly(conn.output, conn.input)
        }
        removed.forEach { graphState.selectModule(it) }
        removed = emptyList()
    }

    override fun discard() {
        graphState.tearDownDetachedModules(removed)
        removed = emptyList()
    }
}
