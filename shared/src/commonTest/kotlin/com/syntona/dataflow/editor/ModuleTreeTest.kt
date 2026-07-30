package com.syntona.dataflow.editor

import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleTreeTest {

    @Test
    fun testBundleFilteringLogic() {
        val root = ModuleRegistry.root

        // Finding core -> basic bundle
        val coreBundle = root.subBundles.find { it.name == "core" }
        val basicBundle = coreBundle?.subBundles?.find { it.name == "basic" }

        if (basicBundle != null) {
            val matchingModules = basicBundle.modules.filter { it.name.contains("tim", ignoreCase = true) }
            assertTrue(matchingModules.any { it.name == "timeline" || it.name == "addTime" })
            assertFalse(matchingModules.any { it.name == "clock" })

            val noMatching = basicBundle.modules.filter { it.name.contains("xyz123nonexistent", ignoreCase = true) }
            assertTrue(noMatching.isEmpty())
        }
    }
}
