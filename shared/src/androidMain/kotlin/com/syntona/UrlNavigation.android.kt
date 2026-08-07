package com.syntona

actual fun getUrlPatchPath(): String? = null

actual fun setUrlPatchPath(path: String?) {}

actual fun openUrlInBrowser(url: String) {}

actual fun isShareUrlAvailable(): Boolean = false
// Sharing a patch as a link only means anything in a browser.
actual suspend fun sharePatchUrl(json: String): ShareUrlResult = ShareUrlResult(null, false)
actual suspend fun getUrlEncodedPatchJson(): String? = null

// There is no address bar to watch outside a browser.
actual fun observeUrlPatchChanges(onChange: () -> Unit): () -> Unit = {}
