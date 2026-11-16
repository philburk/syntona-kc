package com.syntona.util.musicxml

/** Lightweight immutable XML DOM element used by [MusicXmlReader]. */
internal data class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    val text: String,
) {
    fun child(tagName: String): XmlElement? = children.firstOrNull { it.name == tagName }

    fun childrenNamed(tagName: String): List<XmlElement> = children.filter { it.name == tagName }

    fun childText(tagName: String): String? = child(tagName)?.text

    fun hasChild(tagName: String): Boolean = children.any { it.name == tagName }
}

/**
 * Self-contained, pure-Kotlin XML parser supporting UTF-8 BOM, XML declarations, comments,
 * `<!DOCTYPE>` blocks (with optional internal subsets), CDATA sections, single/double-quoted
 * attributes, and standard/numeric XML character entities.
 */
internal object XmlParser {

    fun parse(input: String): XmlElement {
        val state = Cursor(input)
        state.skipBom()
        while (true) {
            state.skipWhitespace()
            if (state.isEof) {
                throw IllegalArgumentException("Empty XML document")
            }
            if (state.startsWith("<?")) {
                state.skipUntil("?>")
            } else if (state.startsWith("<!--")) {
                state.skipUntil("-->")
            } else if (state.startsWith("<!DOCTYPE") || state.startsWith("<!doctype")) {
                state.skipDoctype()
            } else if (state.startsWith("<![CDATA[")) {
                state.skipUntil("]]>")
            } else if (state.peek() == '<') {
                return parseElement(state)
            } else {
                throw IllegalArgumentException("Unexpected content before root XML element at index ${state.pos}")
            }
        }
    }

    private fun parseElement(state: Cursor): XmlElement {
        state.expect('<')
        if (state.isEof || state.peek() == '/' || state.peek() == '!' || state.peek() == '?') {
            throw IllegalArgumentException("Invalid XML start tag at index ${state.pos}")
        }
        val rawName = state.readName()
        if (rawName.isEmpty()) {
            throw IllegalArgumentException("Missing XML tag name at index ${state.pos}")
        }
        val tagName = rawName.substringAfter(':')

        val attrs = linkedMapOf<String, String>()
        while (true) {
            state.skipWhitespace()
            if (state.isEof) {
                throw IllegalArgumentException("Unterminated start tag <$tagName>")
            }
            if (state.startsWith("/>")) {
                state.advance(2)
                return XmlElement(tagName, attrs, emptyList(), "")
            }
            if (state.peek() == '>') {
                state.advance(1)
                break
            }
            val attrName = state.readName().substringAfter(':')
            if (attrName.isEmpty()) {
                throw IllegalArgumentException("Expected attribute name in <$tagName> at index ${state.pos}")
            }
            state.skipWhitespace()
            if (!state.isEof && state.peek() == '=') {
                state.advance(1)
                state.skipWhitespace()
                val attrValue = state.readQuotedValue()
                attrs[attrName] = unescapeEntities(attrValue)
            } else {
                attrs[attrName] = ""
            }
        }

        val children = mutableListOf<XmlElement>()
        val textBuf = StringBuilder()
        while (!state.isEof) {
            if (state.peek() == '<') {
                when {
                    state.startsWith("</") -> {
                        state.advance(2)
                        val closeName = state.readName().substringAfter(':')
                        state.skipWhitespace()
                        state.expect('>')
                        if (closeName != tagName) {
                            throw IllegalArgumentException(
                                "Mismatched closing tag </$closeName> for <$tagName> at index ${state.pos}"
                            )
                        }
                        return XmlElement(tagName, attrs, children, unescapeEntities(textBuf.toString()))
                    }
                    state.startsWith("<!--") -> state.skipUntil("-->")
                    state.startsWith("<?") -> state.skipUntil("?>")
                    state.startsWith("<![CDATA[") -> {
                        state.advance(9)
                        val cdataStart = state.pos
                        val closeIdx = state.indexOf("]]>")
                        if (closeIdx < 0) {
                            throw IllegalArgumentException("Unterminated CDATA section in <$tagName>")
                        }
                        textBuf.append(state.slice(cdataStart, closeIdx))
                        state.pos = closeIdx + 3
                    }
                    else -> children.add(parseElement(state))
                }
            } else {
                val chunkStart = state.pos
                val nextTag = state.indexOfChar('<')
                if (nextTag < 0) {
                    throw IllegalArgumentException("Unterminated XML element <$tagName>")
                }
                textBuf.append(state.slice(chunkStart, nextTag))
                state.pos = nextTag
            }
        }
        throw IllegalArgumentException("Unexpected end of XML before closing </$tagName>")
    }

    internal fun unescapeEntities(raw: String): String {
        if ('&' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '&') {
                val semi = raw.indexOf(';', i + 1)
                if (semi > i + 1 && semi - i <= 12) {
                    val entity = raw.substring(i + 1, semi)
                    val decoded = decodeEntity(entity)
                    if (decoded != null) {
                        out.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }

    private fun decodeEntity(entity: String): String? = when (entity) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        else -> if (entity.startsWith("#x", ignoreCase = true)) {
            entity.substring(2).toIntOrNull(16)?.let { codePointString(it) }
        } else if (entity.startsWith("#")) {
            entity.substring(1).toIntOrNull(10)?.let { codePointString(it) }
        } else {
            null
        }
    }

    private fun codePointString(cp: Int): String? {
        if (cp <= 0 || cp > 0x10FFFF) return null
        return if (cp <= 0xFFFF) {
            cp.toChar().toString()
        } else {
            val offset = cp - 0x10000
            val hi = (0xD800 + (offset ushr 10)).toChar()
            val lo = (0xDC00 + (offset and 0x3FF)).toChar()
            "$hi$lo"
        }
    }

    private class Cursor(private val src: String) {
        var pos: Int = 0
        val len: Int = src.length
        val isEof: Boolean get() = pos >= len

        fun peek(): Char = src[pos]

        fun advance(count: Int) {
            pos += count
        }

        fun skipBom() {
            if (!isEof && src[pos] == '\uFEFF') {
                pos++
            }
        }

        fun skipWhitespace() {
            while (pos < len && src[pos].isWhitespace()) {
                pos++
            }
        }

        fun startsWith(prefix: String): Boolean = src.startsWith(prefix, pos)

        fun expect(expected: Char) {
            if (isEof || src[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at index $pos")
            }
            pos++
        }

        fun indexOf(needle: String): Int = src.indexOf(needle, pos)

        fun indexOfChar(ch: Char): Int = src.indexOf(ch, pos)

        fun slice(start: Int, end: Int): String = src.substring(start, end)

        fun skipUntil(delimiter: String) {
            val idx = src.indexOf(delimiter, pos)
            if (idx < 0) {
                throw IllegalArgumentException("Unterminated XML construct looking for '$delimiter'")
            }
            pos = idx + delimiter.length
        }

        fun skipDoctype() {
            var depth = 0
            var quote: Char? = null
            while (pos < len) {
                val c = src[pos++]
                if (quote != null) {
                    if (c == quote) quote = null
                } else {
                    when (c) {
                        '\'', '"' -> quote = c
                        '<' -> depth++
                        '>' -> {
                            depth--
                            if (depth <= 0) return
                        }
                    }
                }
            }
            throw IllegalArgumentException("Unterminated DOCTYPE declaration")
        }

        fun readName(): String {
            val start = pos
            while (pos < len) {
                val c = src[pos]
                if (c.isWhitespace() || c == '>' || c == '/' || c == '=') break
                pos++
            }
            return src.substring(start, pos)
        }

        fun readQuotedValue(): String {
            if (isEof) throw IllegalArgumentException("Unexpected EOF reading attribute value")
            val quote = src[pos]
            if (quote != '\'' && quote != '"') {
                throw IllegalArgumentException("Expected attribute quote at index $pos")
            }
            pos++
            val start = pos
            val close = src.indexOf(quote, pos)
            if (close < 0) {
                throw IllegalArgumentException("Unterminated attribute value starting at index ${start - 1}")
            }
            pos = close + 1
            return src.substring(start, close)
        }
    }
}
