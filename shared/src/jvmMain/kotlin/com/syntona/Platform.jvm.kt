package com.syntona

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun isCapsLockOn(event: androidx.compose.ui.input.key.KeyEvent?): Boolean {
    capsLockOverrideForTesting?.let { return it }
    return try {
        java.awt.Toolkit.getDefaultToolkit().getLockingKeyState(java.awt.event.KeyEvent.VK_CAPS_LOCK)
    } catch (e: Exception) {
        false
    }
}
