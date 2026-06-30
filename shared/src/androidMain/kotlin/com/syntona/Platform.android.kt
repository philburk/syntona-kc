package com.syntona

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun isCapsLockOn(event: androidx.compose.ui.input.key.KeyEvent?): Boolean {
    capsLockOverrideForTesting?.let { return it }
    val meta = event?.nativeKeyEvent?.metaState ?: 0
    return (meta and android.view.KeyEvent.META_CAPS_LOCK_ON) != 0
}
