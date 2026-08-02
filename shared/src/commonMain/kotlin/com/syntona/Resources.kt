package com.syntona

import syntonakc.shared.generated.resources.Res

object SharedResources {
    suspend fun readBytes(path: String): ByteArray {
        return try {
            Res.readBytes(path)
        } catch (e: Exception) {
            if (path.endsWith(".sntn")) {
                Res.readBytes(path.removeSuffix(".sntn") + ".json")
            } else if (path.endsWith(".json")) {
                Res.readBytes(path.removeSuffix(".json") + ".sntn")
            } else {
                throw e
            }
        }
    }

    suspend fun getFilesIndex(): List<String> {
        return try {
            val bytes = Res.readBytes("files/files_index.txt")
            bytes.decodeToString().split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
