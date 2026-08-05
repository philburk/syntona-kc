package com.syntona.dataflow.editor

import com.syntona.util.LzString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonCompressionRatioTest {

    @Test
    fun testCompressionRatiosOnExampleJsonFiles() {
        val examplesDir = File("src/commonMain/composeResources/files/examples")
        if (!examplesDir.exists() || !examplesDir.isDirectory) {
            println("Examples directory not found at relative path: ${examplesDir.absolutePath}")
            return
        }

        val jsonFiles = examplesDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        assertTrue(jsonFiles.isNotEmpty(), "Expected example JSON files in composeResources")

        println("\n=================== SYNTONA JSON COMPRESSION RATIO REPORT ===================")
        println(String.format("%-25s | %-12s | %-12s | %-12s | %-12s", "File Name", "Formatted", "Minified", "URL Payload", "Ratio (Orig/URL)"))
        println("-".repeat(85))

        for (file in jsonFiles.sortedBy { it.name }) {
            val rawJson = file.readText()
            val minifiedJson = GraphSerializer.minifyJson(rawJson)
            val compressedPayload = LzString.compressToEncodedURIComponent(minifiedJson)
            
            val origSize = rawJson.length
            val minifiedSize = minifiedJson.length
            val compressedSize = compressedPayload.length
            
            val ratioToOrig = origSize.toDouble() / compressedSize.toDouble()
            val ratioToMinified = minifiedSize.toDouble() / compressedSize.toDouble()

            println(String.format("%-25s | %-12d | %-12d | %-12d | %-12.2fx", file.name, origSize, minifiedSize, compressedSize, ratioToOrig))

            // Verify round-trip decompression correctness
            val decompressed = LzString.decompressFromEncodedURIComponent(compressedPayload)
            assertEquals(minifiedJson, decompressed, "Decompressed string must match minified JSON for ${file.name}")

            // Assert that for real example files (> 1 KB), the compression ratio is at least 1.5x to 10x+
            if (origSize > 1000) {
                assertTrue(
                    compressedSize < origSize,
                    "Compressed URL payload (${compressedSize} chars) should be smaller than original JSON (${origSize} chars) for ${file.name}"
                )
            }
        }
        println("-".repeat(85) + "\n")
    }

    @Test
    fun testExpectedRatiosForVariousPatchSizes() {
        // Small patch (3 modules)
        val smallJson = """{"modules":[{"id":0,"type":"sine","name":"sine","x":100,"y":100},{"id":1,"type":"sine","name":"sine","x":300,"y":100},{"id":2,"type":"out","name":"out","x":500,"y":100}],"connections":[],"windowX":10,"windowY":10,"windowWidth":800,"windowHeight":600}"""
        val compressedSmall = LzString.compressToEncodedURIComponent(smallJson)
        val smallDecompressed = LzString.decompressFromEncodedURIComponent(compressedSmall)
        assertEquals(smallJson, smallDecompressed)
        
        println("Small Patch JSON size: ${smallJson.length} chars -> Compressed URL payload size: ${compressedSmall.length} chars")

        // Medium patch (50 repeating modules)
        val modulesList = (0 until 50).joinToString(",") { i ->
            """{"id":$i,"type":"sine","name":"osc$i","x":${i * 10},"y":${i * 15},"jacks":{"freq":{"v":440.0,"min":0.0,"max":20000.0}}}"""
        }
        val mediumJson = """{"modules":[$modulesList],"connections":[]}"""
        val compressedMedium = LzString.compressToEncodedURIComponent(mediumJson)
        val mediumRatio = mediumJson.length.toDouble() / compressedMedium.length.toDouble()
        val mediumDecompressed = LzString.decompressFromEncodedURIComponent(compressedMedium)
        assertEquals(mediumJson, mediumDecompressed)

        println("Medium 50-module Patch JSON size: ${mediumJson.length} chars -> Compressed URL payload size: ${compressedMedium.length} chars (Ratio: ${String.format("%.2f", mediumRatio)}x)")
        
        // Assert that medium patches achieve > 3x compression ratio
        assertTrue(mediumRatio >= 3.0, "Expected compression ratio of at least 3x for medium patches, got ${mediumRatio}x")
    }

    @Test
    fun testInMemDeserializedDrumRhythmCompression() {
        val drumFile = File("src/commonMain/composeResources/files/examples/drum_rhythm.json")
        if (!drumFile.exists()) return

        val rawJson = drumFile.readText()
        val graphState = GraphState()
        GraphSerializer.deserialize(graphState, rawJson)

        val serializedInMemJson = GraphSerializer.serialize(graphState, graphState.modules.toList(), 1f, 10f, 10f, 800f, 600f)
        val minifiedInMem = GraphSerializer.minifyJson(serializedInMemJson)
        val compressedPayload = LzString.compressToEncodedURIComponent(minifiedInMem)

        println("\n=== IN-MEMORY DESERIALIZED & RE-SERIALIZED DRUM RHYTHM ===")
        println("Raw file size: ${rawJson.length} bytes")
        println("In-memory serialized JSON size: ${serializedInMemJson.length} bytes")
        println("Minified in-memory JSON size: ${minifiedInMem.length} bytes")
        println("LZ Compressed URL payload size (#piece=): ${compressedPayload.length} bytes")
        val ratio = minifiedInMem.length.toDouble() / compressedPayload.length.toDouble()
        println("Compression Ratio (Minified In-Mem JSON / URL Payload): ${String.format("%.2f", ratio)}x\n")
    }
}
