@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val isWeb: Boolean = true
}

actual fun getPlatform(): Platform = WasmPlatform()

@JsFun("""() => {
    var d = new Date();
    var y = d.getFullYear();
    var m = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}""")
private external fun jsCurrentIsoDate(): String

actual fun currentIsoDate(): String = jsCurrentIsoDate()
