package com.syntona

actual fun getUrlPatchPath(): String? = null

actual fun setUrlPatchPath(path: String?) {}

actual fun isShareUrlAvailable(): Boolean = false
actual fun sharePatchUrl(json: String): Boolean = false
actual fun getUrlEncodedPatchJson(): String? = null

actual fun openUrlInBrowser(url: String) {
    try {
        if (java.awt.Desktop.isDesktopSupported()) {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
                return
            }
        }
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", url))
            os.contains("win") -> Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url))
            os.contains("nix") || os.contains("nux") -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
        }
    } catch (e: Exception) {
        println("Failed to open URL in browser: ${e.message}")
    }
}
