package com.syntona.dataflow.model

/**
 * Container for a collection of modules and their connections.
 */
class UnitGraph {
    private val _modules = mutableListOf<Module>()
    val modules: List<Module> get() = _modules

    fun addModule(module: Module) {
        _modules.add(module)
    }

    fun removeModule(module: Module) {
        _modules.remove(module)
        // Note: In a full implementation, we would also remove all connections
        // associated with this module's jacks.
    }

    fun connect(output: OutputJack, input: InputJack) {
        output.connect(input)
    }

    fun disconnect(output: OutputJack, input: InputJack) {
        output.disconnect(input)
    }
}
