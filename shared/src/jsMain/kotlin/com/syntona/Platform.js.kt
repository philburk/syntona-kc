package com.syntona

import web.navigator.navigator

class JsPlatform: Platform {
    private val userAgent = navigator.userAgent
    private val browserList = listOf("Chrome", "Firefox", "Safari", "Edge")

    override val name: String = userAgent.findAnyOf(browserList, ignoreCase = true)
            ?.let { (startIndex) -> userAgent.substring(startIndex).substringBefore(" ") }
            ?: "Unknown"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun isCapsLockOn(event: androidx.compose.ui.input.key.KeyEvent?): Boolean {
    capsLockOverrideForTesting?.let { return it }
    val keyboardEvent = event?.nativeKeyEvent as? org.w3c.dom.events.KeyboardEvent
    return keyboardEvent?.getModifierState("CapsLock") ?: false
}
