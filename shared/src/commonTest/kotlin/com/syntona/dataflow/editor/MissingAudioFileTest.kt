package com.syntona.dataflow.editor

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sample whose audio file cannot be found keeps a silent buffer while still showing the file
 * name, so loading must report it rather than leave the user with unexplained silence.
 */
class MissingAudioFileTest {

    private fun problemsLoading(type: String): List<String> {
        val json = """{"modules":[{"id":0,"type":"$type","name":"lost","x":0,"y":0,""" +
            """"filePath":"no_such_file_9f3a.wav"}],"connections":[]}"""
        val problems = mutableListOf<String>()
        GraphSerializer.deserialize(GraphState(), json, problems = problems, basePath = "/no/such/dir/song.sntn")
        return problems
    }

    @Test
    fun aMissingSampleFileIsReported() {
        for (type in listOf("ksyn.sampling.sample", "ksyn.sampling.samplePlayer")) {
            val problems = problemsLoading(type)
            assertTrue(
                problems.any { "no_such_file_9f3a.wav" in it && "/no/such/dir/no_such_file_9f3a.wav" in it },
                "$type: expected a missing-file problem, got $problems"
            )
        }
    }
}
