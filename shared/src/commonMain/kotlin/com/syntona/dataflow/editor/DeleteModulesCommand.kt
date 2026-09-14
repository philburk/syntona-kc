package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.dataflow.model.Module

class DeleteModulesCommand(
    private val graphState: GraphState,
    private val modules: List<Module>,
    private val savedPositions: Map<Module, Offset>,
    private val savedConnections: List<GraphState.UIConnection>
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        val toRemove = modules.filter { graphState.canDeleteModule(it) }
        graphState.removeModulesDirectly(toRemove)
    }

    override fun undo() {
        modules.forEach { module ->
            graphState.addModuleDirectly(module, savedPositions[module] ?: Offset.Zero)
        }
        savedConnections.forEach { conn ->
            graphState.connectDirectly(conn.output, conn.input)
        }
        modules.forEach { graphState.selectModule(it) }
    }
}
