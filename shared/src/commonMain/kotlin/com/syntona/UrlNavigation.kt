package com.syntona

/**
 * Sanitizes and validates a candidate resource path against security constraints and the known files index.
 *
 * Prevents directory traversal (e.g. `..`), protocol schemes (`http://`, `file://`), absolute paths (`/`),
 * and unindexed files. Returns null if invalid or unlisted.
 */
fun sanitizeAndValidateResourcePath(rawPath: String?, validFiles: Set<String>): String? {
    if (rawPath.isNullOrBlank()) return null
    val trimmed = rawPath.trim()

    // Reject directory traversal attempts
    if (trimmed.contains("..")) return null

    // Reject absolute paths and protocol schemes
    if (trimmed.startsWith('/') || trimmed.startsWith('\\')) return null
    if (trimmed.contains("://") || trimmed.contains(':')) return null

    // Reject paths not starting with expected resource folders
    if (!trimmed.startsWith("examples/") &&
        !trimmed.startsWith("tutorials/") &&
        !trimmed.startsWith("help/") &&
        trimmed != "lineIn$SYNTONA_FILE_SUFFIX"
    ) {
        return null
    }

    // Must exist in pre-compiled resource files index
    return if (validFiles.contains(trimmed)) trimmed else null
}

expect fun getUrlPatchPath(): String?
expect fun setUrlPatchPath(path: String?)
