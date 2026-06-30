package com.syntona

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

var capsLockOverrideForTesting: Boolean? = null

expect fun isCapsLockOn(event: androidx.compose.ui.input.key.KeyEvent?): Boolean
