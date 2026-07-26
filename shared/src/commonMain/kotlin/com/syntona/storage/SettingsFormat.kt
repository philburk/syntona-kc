package com.syntona.storage

/**
 * A line-oriented `key=value` text format for [SettingsStore].
 *
 * Chosen so the files stay readable and hand-editable, and so it needs no serialization library
 * on any platform. Backslash, carriage return and newline are escaped in both key and value, and
 * `=` is escaped in the key, so the first unescaped `=` on a line always separates the two.
 * Values may contain a raw `=`.
 *
 * Blank lines and lines starting with `#` are ignored, so a comment can be left in a file by hand.
 */
private const val HEADER = "# Syntona settings"

internal fun encodeSettings(values: Map<String, String>): String = buildString {
    append(HEADER)
    append('\n')
    for ((key, value) in values) {
        append(escape(key, escapeEquals = true))
        append('=')
        append(escape(value, escapeEquals = false))
        append('\n')
    }
}

internal fun decodeSettings(text: String): Map<String, String> {
    val values = LinkedHashMap<String, String>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val separator = indexOfUnescapedEquals(line) ?: continue
        val key = unescape(line.substring(0, separator))
        val value = unescape(line.substring(separator + 1))
        if (key.isNotEmpty()) values[key] = value
    }
    return values
}

private fun escape(text: String, escapeEquals: Boolean): String = buildString {
    for (c in text) {
        when {
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '=' && escapeEquals -> append("\\=")
            else -> append(c)
        }
    }
}

private fun unescape(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            i++
            when (val next = text[i]) {
                'n' -> append('\n')
                'r' -> append('\r')
                else -> append(next) // covers \\ and \= , and passes anything else through
            }
        } else {
            append(c)
        }
        i++
    }
}

/** Index of the first `=` that is not escaped, or null if the line has no separator. */
private fun indexOfUnescapedEquals(line: String): Int? {
    var i = 0
    var escaped = false
    while (i < line.length) {
        val c = line[i]
        when {
            escaped -> escaped = false
            c == '\\' -> escaped = true
            c == '=' -> return i
        }
        i++
    }
    return null
}
