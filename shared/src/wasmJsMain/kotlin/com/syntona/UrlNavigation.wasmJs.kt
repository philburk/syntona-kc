@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona

import kotlinx.browser.window

@JsName("decodeURIComponent")
private external fun decodeURIComponent(encodedURI: String): String

@JsName("encodeURIComponent")
private external fun encodeURIComponent(str: String): String

actual fun getUrlPatchPath(): String? {
    val search = window.location.search
    if (search.isEmpty()) return null
    val query = search.removePrefix("?")
    if (query.isEmpty()) return null

    val params = query.split("&")
    for (param in params) {
        val parts = param.split("=")
        if (parts.size == 2 && (parts[0] == "patch" || parts[0] == "path")) {
            return try { decodeURIComponent(parts[1]) } catch (_: Exception) { parts[1] }
        }
        if (parts.size == 1 && parts[0].isNotEmpty()) {
            val decoded = try { decodeURIComponent(parts[0]) } catch (_: Exception) { parts[0] }
            if (decoded.startsWith("examples/") || decoded.startsWith("tutorials/") || decoded.startsWith("help/")) {
                return decoded
            }
        }
    }
    return null
}

actual fun setUrlPatchPath(path: String?) {
    try {
        val currentPath = window.location.pathname
        val displayPath = path?.let {
            if (it.endsWith(".json")) it.removeSuffix(".json") + SYNTONA_FILE_SUFFIX else it
        }
        val newUrl = if (displayPath != null) {
            val encoded = encodeURIComponent(displayPath).replace("%2F", "/")
            "$currentPath?patch=$encoded"
        } else {
            currentPath
        }
        window.history.replaceState(null, "", newUrl)
    } catch (_: Exception) {}
}

actual fun openUrlInBrowser(url: String) {
    try {
        window.open(url, "_blank")
    } catch (e: Exception) {
        println("Failed to open URL: ${e.message}")
    }
}
