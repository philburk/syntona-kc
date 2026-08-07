@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.events.Event
import kotlin.js.Promise

/**
 * Key of the URL fragment that carries a shared patch.
 *
 * The payload lives in the fragment rather than the query string because browsers never send the
 * fragment to the server, so a shared patch does not end up in web server logs on the way.
 */
private const val PATCH_FRAGMENT_KEY = "patch="

/**
 * Length past which a link starts getting wrapped or truncated by mail clients and chat apps.
 * Browsers themselves accept far longer URLs, so this is a warning threshold, not a hard limit.
 */
private const val SAFE_URL_LENGTH = 2000

@JsName("decodeURIComponent")
private external fun decodeURIComponent(encodedURI: String): String

@JsName("encodeURIComponent")
private external fun encodeURIComponent(str: String): String

/**
 * Compresses with raw DEFLATE and encodes base64url.
 *
 * The browser already ships a tuned zlib behind `CompressionStream`, so there is no compressor to
 * write or maintain here. base64url keeps the result safe to paste into a URL without escaping:
 * `+/` become `-_`, and `=` padding is dropped and rebuilt on the way back in.
 */
@JsFun("""(text) => {
    const bytes = new TextEncoder().encode(text);
    const stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream('deflate-raw'));
    return new Response(stream).arrayBuffer().then((buffer) => {
        const view = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < view.length; i++) binary += String.fromCharCode(view[i]);
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    });
}""")
private external fun jsDeflateToBase64Url(text: String): Promise<JsString>

/** Inverse of [jsDeflateToBase64Url]. Rejects if the payload is not valid base64url DEFLATE. */
@JsFun("""(payload) => {
    let base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    while (base64.length % 4 !== 0) base64 += '=';
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
    return new Response(stream).text();
}""")
private external fun jsInflateFromBase64Url(payload: String): Promise<JsString>

/**
 * Copies to the clipboard, reporting whether it worked.
 *
 * Deliberately only the async Clipboard API: the older `document.execCommand('copy')` route needs a
 * focused `<textarea>`, and stealing focus away from the Compose canvas is what broke keyboard
 * shortcuts on the web before.
 */
@JsFun("""(text) => navigator.clipboard.writeText(text).then(() => true, () => false)""")
private external fun jsCopyToClipboard(text: String): Promise<JsBoolean>

@JsFun("""() => typeof CompressionStream !== 'undefined' &&
    typeof DecompressionStream !== 'undefined' &&
    typeof navigator !== 'undefined' && !!navigator.clipboard""")
private external fun jsIsShareSupported(): Boolean

actual fun isShareUrlAvailable(): Boolean = jsIsShareSupported()

actual suspend fun sharePatchUrl(json: String): ShareUrlResult {
    val payload = try {
        jsDeflateToBase64Url(json).await<JsString>().toString()
    } catch (e: Throwable) {
        println("Failed to compress patch for sharing: ${e.message}")
        return ShareUrlResult(url = null, copiedToClipboard = false)
    }

    // Built from origin + pathname so any fragment or query already on the page is dropped rather
    // than concatenated. The address bar is left alone: replacing it would mean a reload restores
    // this snapshot instead of whatever the user goes on to do.
    val url = window.location.origin + window.location.pathname + "#" + PATCH_FRAGMENT_KEY + payload

    val copied = try {
        jsCopyToClipboard(url).await<JsBoolean>().toBoolean()
    } catch (_: Throwable) {
        false
    }

    val warning = if (url.length > SAFE_URL_LENGTH) {
        "This patch makes a ${url.length} character link. Some mail and chat apps break links " +
            "that long, so send it as a file if it does not survive the trip."
    } else {
        null
    }
    return ShareUrlResult(url, copied, warning)
}

actual suspend fun getUrlEncodedPatchJson(): String? {
    val hash = window.location.hash.removePrefix("#")
    if (!hash.startsWith(PATCH_FRAGMENT_KEY)) return null
    val payload = hash.removePrefix(PATCH_FRAGMENT_KEY)
    if (payload.isEmpty()) return null
    return try {
        jsInflateFromBase64Url(payload).await<JsString>().toString().ifEmpty { null }
    } catch (e: Throwable) {
        println("Could not decode the patch in this URL: ${e.message}")
        null
    }
}

actual fun observeUrlPatchChanges(onChange: () -> Unit): () -> Unit {
    // "hashchange" is what the browser fires instead of navigating when a pasted URL differs from
    // the current one only in its fragment. "popstate" covers the back and forward buttons.
    val listener: (Event) -> Unit = { onChange() }
    window.addEventListener("hashchange", listener)
    window.addEventListener("popstate", listener)
    return {
        window.removeEventListener("hashchange", listener)
        window.removeEventListener("popstate", listener)
    }
}

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
