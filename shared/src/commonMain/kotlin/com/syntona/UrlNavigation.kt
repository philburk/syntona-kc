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
        trimmed != "lineIn$SYNTONA_FILE_SUFFIX" &&
        trimmed != "lineIn.json" &&
        trimmed != "lineIn"
    ) {
        return null
    }

    // Must exist in pre-compiled resource files index
    if (validFiles.contains(trimmed)) return trimmed

    if (trimmed.endsWith(SYNTONA_FILE_SUFFIX)) {
        val jsonPath = trimmed.removeSuffix(SYNTONA_FILE_SUFFIX) + ".json"
        if (validFiles.contains(jsonPath)) return jsonPath
    }
    val jsonPath = "$trimmed.json"
    if (validFiles.contains(jsonPath)) return jsonPath

    return null
}

/**
 * Outcome of [sharePatchUrl].
 *
 * @property url the shareable link, or null if one could not be built.
 * @property copiedToClipboard whether the link actually reached the clipboard. The browser can
 *   refuse the write, so this is reported rather than assumed.
 * @property warning a note worth showing the user even though sharing succeeded.
 */
data class ShareUrlResult(
    val url: String?,
    val copiedToClipboard: Boolean,
    val warning: String? = null
)

expect fun getUrlPatchPath(): String?
expect fun setUrlPatchPath(path: String?)
expect fun openUrlInBrowser(url: String)
expect fun isShareUrlAvailable(): Boolean

/** Builds a shareable link containing [json] and copies it to the clipboard. Web only. */
expect suspend fun sharePatchUrl(json: String): ShareUrlResult

/** Returns the patch JSON carried by the current URL, if any. Web only. */
expect suspend fun getUrlEncodedPatchJson(): String?
