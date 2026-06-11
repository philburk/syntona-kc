package com.syntona.dataflow.registry

import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.*

/**
 * Registry of available modules organized by bundles.
 */
object ModuleRegistry {

    data class ModuleEntry(
        val name: String,
        val creator: () -> Module
    )

    data class Bundle(
        val name: String,
        val subBundles: MutableList<Bundle> = mutableListOf(),
        val modules: MutableList<ModuleEntry> = mutableListOf()
    )

    val root = Bundle("Modules")

    init {
        val core = getOrCreateBundle(root, "core")

        val math = getOrCreateBundle(core, "math")
        math.modules.add(ModuleEntry("+") { AddModule() })
        math.modules.add(ModuleEntry("*") { MultiplyModule() })
        math.modules.add(ModuleEntry("Random") { RandomModule() })

        val input = getOrCreateBundle(core, "input")
        input.modules.add(ModuleEntry("Kick") { KickModule() })

        val view = getOrCreateBundle(core, "view")
        view.modules.add(ModuleEntry("Show") { ShowModule() })

        // Add Print to view as well
        view.modules.add(ModuleEntry("Print") { PrintModule() })
    }

    private fun getOrCreateBundle(parent: Bundle, name: String): Bundle {
        return parent.subBundles.find { it.name == name }
            ?: Bundle(name).also { parent.subBundles.add(it) }
    }
}
