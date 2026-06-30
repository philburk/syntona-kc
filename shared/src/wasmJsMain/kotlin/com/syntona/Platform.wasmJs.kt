package com.syntona

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

actual fun isCapsLockOn(event: androidx.compose.ui.input.key.KeyEvent?): Boolean {
    capsLockOverrideForTesting?.let { return it }
    val keyboardEvent = event?.nativeKeyEvent as? org.w3c.dom.events.KeyboardEvent
    return keyboardEvent?.getModifierState("CapsLock") ?: false
}
