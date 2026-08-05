package com.syntona

actual fun getUrlPatchPath(): String? = null

actual fun setUrlPatchPath(path: String?) {}

actual fun openUrlInBrowser(url: String) {}

actual fun isShareUrlAvailable(): Boolean = false
actual fun sharePatchUrl(json: String): Boolean = false
actual fun getUrlEncodedPatchJson(): String? = null
