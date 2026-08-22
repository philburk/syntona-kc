package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.CommandModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule

object PatchExtractor {

    /**
     * Extracts [modulesToExtract] from [graphState] into a new [PatchModule].
     *
     * The selected modules are removed from [graphState] and placed inside the new sub-patch,
     * maintaining their relative positions and internal connections. Any connections crossing
     * the selection boundary are replicated using [InputModule], [AudioInputModule],
     * [OutputModule], or [AudioOutputModule] instances with descriptive, unique port names.
     *
     * @return the newly created [PatchModule] in [graphState], or null if [modulesToExtract] is empty.
     */
    fun extractToPatch(
        graphState: GraphState,
        modulesToExtract: List<Module>,
        density: Float = 1f
    ): PatchModule? {
        if (modulesToExtract.isEmpty()) return null
        val selectedSet = modulesToExtract.toSet()

        // 1. Compute bounding box in graph coordinates
        val minX = modulesToExtract.minOf { (graphState.modulePositions[it] ?: Offset.Zero).x }
        val minY = modulesToExtract.minOf { (graphState.modulePositions[it] ?: Offset.Zero).y }
        val maxX = modulesToExtract.maxOf { (graphState.modulePositions[it] ?: Offset.Zero).x }

        // 2. Identify external incoming and outgoing connections
        val incomingConns = graphState.connections.filter { it.output.module !in selectedSet && it.input.module in selectedSet }
        val outgoingConns = graphState.connections.filter { it.output.module in selectedSet && it.input.module !in selectedSet }

        // Group incoming connections by target jack and whether source is audio
        val incomingByJackAndKind = incomingConns.groupBy { Pair(it.input, it.output is AudioOutputJack) }
        // Group outgoing connections by the source jack inside the selection
        val outgoingByJack = outgoingConns.groupBy { it.output }

        // 3. Serialize selected modules to JSON
        val json = GraphSerializer.serialize(graphState, modulesToExtract, density)

        // 4. Remove selected modules from parent graph
        graphState.removeModulesDirectly(modulesToExtract)

        // 5. Create new PatchModule with a unique name in the parent graph
        val existingNames = graphState.modules.map { it.name }.toSet()
        var patchName = "patch"
        var suffix = 1
        while (existingNames.contains(patchName)) {
            patchName = "patch$suffix"
            suffix++
        }
        val patch = PatchModule(patchName)
        graphState.addModule(patch, Offset(minX, minY))
        graphState.clearSelection()
        graphState.selectModule(patch)

        // 6. Deserialize extracted modules into patch's graphState
        val innerOffsetX = 240f - minX
        val innerOffsetY = 80f - minY
        val innerOffset = Offset(innerOffsetX, innerOffsetY)
        val createdModules = GraphSerializer.deserialize(patch.graphState, json, innerOffset, density)
        val oldToNewMap = modulesToExtract.indices.associate { i -> modulesToExtract[i] to createdModules[i] }

        // 7. Create input modules inside patch and replicate incoming connections
        val usedInputNames = mutableSetOf<String>()
        val genericInputNames = setOf("in", "input", "input0", "value", "ain", "ainput")

        for ((key, conns) in incomingByJackAndKind) {
            val (oldTargetJack, isAudioConn) = key
            val oldTargetModule = oldTargetJack.module
            val newTargetModule = oldToNewMap[oldTargetModule] ?: continue
            val newTargetJack = newTargetModule.inputs.find { it.name == oldTargetJack.name } ?: continue

            val preferredName = if (genericInputNames.contains(oldTargetJack.name.lowercase())) {
                "in"
            } else {
                oldTargetJack.name
            }
            val finalName = chooseUniqueName(preferredName, usedInputNames)

            val targetPos = patch.graphState.modulePositions[newTargetModule] ?: Offset.Zero
            val inModPos = Offset(30f, targetPos.y)

            if (isAudioConn && newTargetJack is AudioInputJack) {
                val aInMod = AudioInputModule()
                aInMod.name = finalName
                patch.graphState.addModule(aInMod, inModPos)
                patch.graphState.connect(aInMod.output, newTargetJack)
                val patchJack = aInMod.patchJack
                if (patchJack != null) {
                    for (conn in conns) {
                        graphState.connect(conn.output, patchJack)
                    }
                }
            } else if (newTargetJack is CommandJack) {
                val cmdMod = CommandModule()
                cmdMod.name = finalName
                patch.graphState.addModule(cmdMod, inModPos)
                patch.graphState.connect(cmdMod.output, newTargetJack)
                val patchJack = cmdMod.patchJack
                if (patchJack != null) {
                    for (conn in conns) {
                        graphState.connect(conn.output, patchJack)
                    }
                }
            } else {
                val inMod = InputModule()
                inMod.name = finalName
                patch.graphState.addModule(inMod, inModPos)
                patch.graphState.connect(inMod.output, newTargetJack)
                val patchJack = inMod.patchJack
                if (patchJack != null) {
                    for (conn in conns) {
                        graphState.connect(conn.output, patchJack)
                    }
                }
            }
        }

        // 8. Create output modules inside patch and replicate outgoing connections
        val usedOutputNames = mutableSetOf<String>()
        val genericOutputNames = setOf("out", "output", "output0", "aout", "aoutput")
        val rightX = (maxX - minX) + 380f

        for ((oldSourceJack, conns) in outgoingByJack) {
            val oldSourceModule = oldSourceJack.module
            val newSourceModule = oldToNewMap[oldSourceModule] ?: continue
            val isAudio = oldSourceJack is AudioOutputJack
            val newSourceJack = newSourceModule.outputs.find { it.name == oldSourceJack.name } ?: continue

            val preferredName = if (genericOutputNames.contains(oldSourceJack.name.lowercase())) {
                "out"
            } else {
                oldSourceJack.name
            }
            val finalName = chooseUniqueName(preferredName, usedOutputNames)

            val sourcePos = patch.graphState.modulePositions[newSourceModule] ?: Offset.Zero
            val outModPos = Offset(rightX, sourcePos.y)

            if (isAudio && newSourceJack is AudioOutputJack) {
                val aOutMod = AudioOutputModule()
                aOutMod.name = finalName
                patch.graphState.addModule(aOutMod, outModPos)
                patch.graphState.connect(newSourceJack, aOutMod.input)
                val patchJack = aOutMod.patchJack
                if (patchJack != null) {
                    for (conn in conns) {
                        val extIn = conn.input
                        if (extIn is AudioInputJack) {
                            graphState.connect(patchJack, extIn)
                        }
                    }
                }
            } else {
                val outMod = OutputModule()
                outMod.name = finalName
                patch.graphState.addModule(outMod, outModPos)
                patch.graphState.connect(newSourceJack, outMod.input)
                val patchJack = outMod.patchJack
                if (patchJack != null) {
                    for (conn in conns) {
                        graphState.connect(patchJack, conn.input)
                    }
                }
            }
        }

        graphState.markChanged()
        patch.graphState.markChanged()
        return patch
    }

    private fun chooseUniqueName(
        preferredName: String,
        usedNames: MutableSet<String>
    ): String {
        if (!usedNames.contains(preferredName)) {
            usedNames.add(preferredName)
            return preferredName
        }
        var count = 2
        while (usedNames.contains("${preferredName}$count")) {
            count++
        }
        val result = "${preferredName}$count"
        usedNames.add(result)
        return result
    }
}
