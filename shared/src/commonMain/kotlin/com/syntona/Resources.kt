package com.syntona

import syntonakc.shared.generated.resources.Res

object SharedResources {
    suspend fun readBytes(path: String): ByteArray {
        return Res.readBytes(path)
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
