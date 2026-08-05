@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona

import kotlinx.browser.window

@JsName("decodeURIComponent")
private external fun decodeURIComponent(encodedURI: String): String

@JsName("encodeURIComponent")
private external fun encodeURIComponent(str: String): String

@JsFun("""(uncompressed) => {
    if (uncompressed == null) return "";
    var keyStrUriSafe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-$";
    var _compress = function(uncompressed, bitsPerMode, getCharFromInt) {
        if (uncompressed == null) return "";
        var i, value,
            context_dictionary= {},
            context_dictionaryToCreate= {},
            context_c="",
            context_wc="",
            context_w="",
            context_enlargeIn= 2,
            context_dictSize= 3,
            context_numBits= 2,
            context_data=[],
            context_data_val=0,
            context_data_position=0,
            ii;

        for (ii = 0; ii < uncompressed.length; ii += 1) {
            context_c = uncompressed.charAt(ii);
            if (!Object.prototype.hasOwnProperty.call(context_dictionary,context_c)) {
                context_dictionary[context_c] = context_dictSize++;
                context_dictionaryToCreate[context_c] = true;
            }

            context_wc = context_w + context_c;
            if (Object.prototype.hasOwnProperty.call(context_dictionary,context_wc)) {
                context_w = context_wc;
            } else {
                if (Object.prototype.hasOwnProperty.call(context_dictionaryToCreate,context_w)) {
                    if (context_w.charCodeAt(0)<256) {
                        for (i=0 ; i<context_numBits ; i++) {
                            context_data_val = (context_data_val << 1);
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                        }
                        value = context_w.charCodeAt(0);
                        for (i=0 ; i<8 ; i++) {
                            context_data_val = (context_data_val << 1) | (value & 1);
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = value >> 1;
                        }
                    } else {
                        value = 1;
                        for (i=0 ; i<context_numBits ; i++) {
                            context_data_val = (context_data_val << 1) | value;
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = 0;
                        }
                        value = context_w.charCodeAt(0);
                        for (i=0 ; i<16 ; i++) {
                            context_data_val = (context_data_val << 1) | (value & 1);
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = value >> 1;
                        }
                    }
                    context_enlargeIn--;
                    if (context_enlargeIn == 0) {
                        context_enlargeIn = Math.pow(2, context_numBits);
                        context_numBits++;
                    }
                    delete context_dictionaryToCreate[context_w];
                } else {
                    value = context_dictionary[context_w];
                    for (i=0 ; i<context_numBits ; i++) {
                        context_data_val = (context_data_val << 1) | (value & 1);
                        if (context_data_position == bitsPerMode - 1) {
                            context_data_position = 0;
                            context_data.push(getCharFromInt(context_data_val));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = value >> 1;
                    }
                }
                context_enlargeIn--;
                if (context_enlargeIn == 0) {
                    context_enlargeIn = Math.pow(2, context_numBits);
                    context_numBits++;
                }
                context_dictionary[context_wc] = context_dictSize++;
                context_w = String(context_c);
            }
        }

        if (context_w !== "") {
            if (Object.prototype.hasOwnProperty.call(context_dictionaryToCreate,context_w)) {
                if (context_w.charCodeAt(0)<256) {
                    for (i=0 ; i<context_numBits ; i++) {
                        context_data_val = (context_data_val << 1);
                        if (context_data_position == bitsPerMode - 1) {
                            context_data_position = 0;
                            context_data.push(getCharFromInt(context_data_val));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                    }
                    value = context_w.charCodeAt(0);
                    for (i=0 ; i<8 ; i++) {
                        context_data_val = (context_data_val << 1) | (value & 1);
                        if (context_data_position == bitsPerMode - 1) {
                            context_data_position = 0;
                            context_data.push(getCharFromInt(context_data_val));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = value >> 1;
                    }
                } else {
                    value = 1;
                    for (i=0 ; i<context_numBits ; i++) {
                        context_data_val = (context_data_val << 1) | value;
                        if (context_data_position == bitsPerMode - 1) {
                            context_data_position = 0;
                            context_data.push(getCharFromInt(context_data_val));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = 0;
                    }
                    value = context_w.charCodeAt(0);
                    for (i=0 ; i<16 ; i++) {
                        context_data_val = (context_data_val << 1) | (value & 1);
                        if (context_data_position == bitsPerMode - 1) {
                            context_data_position = 0;
                            context_data.push(getCharFromInt(context_data_val));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = value >> 1;
                    }
                }
                context_enlargeIn--;
                if (context_enlargeIn == 0) {
                    context_enlargeIn = Math.pow(2, context_numBits);
                    context_numBits++;
                }
                delete context_dictionaryToCreate[context_w];
            } else {
                value = context_dictionary[context_w];
                for (i=0 ; i<context_numBits ; i++) {
                    context_data_val = (context_data_val << 1) | (value & 1);
                    if (context_data_position == bitsPerMode - 1) {
                        context_data_position = 0;
                        context_data.push(getCharFromInt(context_data_val));
                        context_data_val = 0;
                    } else {
                        context_data_position++;
                    }
                    value = value >> 1;
                }
            }
            context_enlargeIn--;
            if (context_enlargeIn == 0) {
                context_enlargeIn = Math.pow(2, context_numBits);
                context_numBits++;
            }
        }

        value = 2;
        for (i=0 ; i<context_numBits ; i++) {
            context_data_val = (context_data_val << 1) | (value & 1);
            if (context_data_position == bitsPerMode - 1) {
                context_data_position = 0;
                context_data.push(getCharFromInt(context_data_val));
                context_data_val = 0;
            } else {
                context_data_position++;
            }
            value = value >> 1;
        }

        while (true) {
            context_data_val = (context_data_val << 1);
            if (context_data_position == bitsPerMode - 1) {
                context_data.push(getCharFromInt(context_data_val));
                break;
            }
            else context_data_position++;
        }
        return context_data.join('');
    };

    return _compress(uncompressed, 6, function(a){return keyStrUriSafe.charAt(a);});
}""")
private external fun jsLzCompress(input: String): String

@JsFun("""(input) => {
    if (input == null || input == "") return null;
    input = input.replace(/ /g, "+");
    var keyStrUriSafe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-$";
    var baseReverseDic = {};
    function getBaseValue(alphabet, character) {
        if (!baseReverseDic[alphabet]) {
            baseReverseDic[alphabet] = {};
            for (var i = 0; i < alphabet.length; i++) {
                baseReverseDic[alphabet][alphabet.charAt(i)] = i;
            }
        }
        return baseReverseDic[alphabet][character];
    }

    var _decompress = function(length, resetValue, getNextValue) {
        var dictionary = [],
            next,
            enlargeIn = 4,
            dictSize = 4,
            numBits = 3,
            entry = "",
            result = [],
            i,
            w,
            bits, resb, maxpower, power,
            c,
            data = {val:getNextValue(0), position:resetValue, index:1};

        for (i = 0; i < 3; i += 1) {
            dictionary[i] = i;
        }

        bits = 0;
        maxpower = Math.pow(2,2);
        power=1;
        while (power!=maxpower) {
            resb = data.val & data.position;
            data.position >>= 1;
            if (data.position == 0) {
                data.position = resetValue;
                data.val = getNextValue(data.index++);
            }
            bits |= (resb>0?1:0) * power;
            power <<= 1;
        }

        switch (next = bits) {
            case 0:
                bits = 0;
                maxpower = Math.pow(2,8);
                power=1;
                while (power!=maxpower) {
                    resb = data.val & data.position;
                    data.position >>= 1;
                    if (data.position == 0) {
                        data.position = resetValue;
                        data.val = getNextValue(data.index++);
                    }
                    bits |= (resb>0?1:0) * power;
                    power <<= 1;
                }
                c = String.fromCharCode(bits);
                break;
            case 1:
                bits = 0;
                maxpower = Math.pow(2,16);
                power=1;
                while (power!=maxpower) {
                    resb = data.val & data.position;
                    data.position >>= 1;
                    if (data.position == 0) {
                        data.position = resetValue;
                        data.val = getNextValue(data.index++);
                    }
                    bits |= (resb>0?1:0) * power;
                    power <<= 1;
                }
                c = String.fromCharCode(bits);
                break;
            case 2:
                return "";
        }
        dictionary[3] = c;
        w = c;
        result.push(c);
        while (true) {
            if (data.index > length) {
                return "";
            }

            bits = 0;
            maxpower = Math.pow(2,numBits);
            power=1;
            while (power!=maxpower) {
                resb = data.val & data.position;
                data.position >>= 1;
                if (data.position == 0) {
                    data.position = resetValue;
                    data.val = getNextValue(data.index++);
                }
                bits |= (resb>0?1:0) * power;
                power <<= 1;
            }

            switch (c = bits) {
                case 0:
                    bits = 0;
                    maxpower = Math.pow(2,8);
                    power=1;
                    while (power!=maxpower) {
                        resb = data.val & data.position;
                        data.position >>= 1;
                        if (data.position == 0) {
                            data.position = resetValue;
                            data.val = getNextValue(data.index++);
                        }
                        bits |= (resb>0?1:0) * power;
                        power <<= 1;
                    }
                    dictionary[dictSize++] = String.fromCharCode(bits);
                    c = dictSize-1;
                    enlargeIn--;
                    break;
                case 1:
                    bits = 0;
                    maxpower = Math.pow(2,16);
                    power=1;
                    while (power!=maxpower) {
                        resb = data.val & data.position;
                        data.position >>= 1;
                        if (data.position == 0) {
                            data.position = resetValue;
                            data.val = getNextValue(data.index++);
                        }
                        bits |= (resb>0?1:0) * power;
                        power <<= 1;
                    }
                    dictionary[dictSize++] = String.fromCharCode(bits);
                    c = dictSize-1;
                    enlargeIn--;
                    break;
                case 2:
                    return result.join('');
            }

            if (enlargeIn == 0) {
                enlargeIn = Math.pow(2, numBits);
                numBits++;
            }

            if (dictionary[c]) {
                entry = dictionary[c];
            } else {
                if (c === dictSize) {
                    entry = w + w.charAt(0);
                } else {
                    return null;
                }
            }
            result.push(entry);

            dictionary[dictSize++] = w + entry.charAt(0);
            enlargeIn--;

            w = entry;

            if (enlargeIn == 0) {
                enlargeIn = Math.pow(2, numBits);
                numBits++;
            }
        }
    };

    return _decompress(input.length, 32, function(index) { return getBaseValue(keyStrUriSafe, input.charAt(index)); });
}""")
private external fun jsLzDecompress(input: String): String

@JsFun("""(json) => {
    try {
        var keyStrUriSafe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-$";
        var _compress = function(uncompressed, bitsPerMode, getCharFromInt) {
            if (uncompressed == null) return "";
            var i, value,
                context_dictionary= {},
                context_dictionaryToCreate= {},
                context_c="",
                context_wc="",
                context_w="",
                context_enlargeIn= 2,
                context_dictSize= 3,
                context_numBits= 2,
                context_data=[],
                context_data_val=0,
                context_data_position=0,
                ii;

            for (ii = 0; ii < uncompressed.length; ii += 1) {
                context_c = uncompressed.charAt(ii);
                if (!Object.prototype.hasOwnProperty.call(context_dictionary,context_c)) {
                    context_dictionary[context_c] = context_dictSize++;
                    context_dictionaryToCreate[context_c] = true;
                }

                context_wc = context_w + context_c;
                if (Object.prototype.hasOwnProperty.call(context_dictionary,context_wc)) {
                    context_w = context_wc;
                } else {
                    if (Object.prototype.hasOwnProperty.call(context_dictionaryToCreate,context_w)) {
                        if (context_w.charCodeAt(0)<256) {
                            for (i=0 ; i<context_numBits ; i++) {
                                context_data_val = (context_data_val << 1);
                                if (context_data_position == bitsPerMode - 1) {
                                    context_data_position = 0;
                                    context_data.push(getCharFromInt(context_data_val));
                                    context_data_val = 0;
                                } else {
                                    context_data_position++;
                                }
                            }
                            value = context_w.charCodeAt(0);
                            for (i=0 ; i<8 ; i++) {
                                context_data_val = (context_data_val << 1) | (value & 1);
                                if (context_data_position == bitsPerMode - 1) {
                                    context_data_position = 0;
                                    context_data.push(getCharFromInt(context_data_val));
                                    context_data_val = 0;
                                } else {
                                    context_data_position++;
                                }
                                value = value >> 1;
                            }
                        } else {
                            value = 1;
                            for (i=0 ; i<context_numBits ; i++) {
                                context_data_val = (context_data_val << 1) | value;
                                if (context_data_position == bitsPerMode - 1) {
                                    context_data_position = 0;
                                    context_data.push(getCharFromInt(context_data_val));
                                    context_data_val = 0;
                                } else {
                                    context_data_position++;
                                }
                                value = 0;
                            }
                            value = context_w.charCodeAt(0);
                            for (i=0 ; i<16 ; i++) {
                                context_data_val = (context_data_val << 1) | (value & 1);
                                if (context_data_position == bitsPerMode - 1) {
                                    context_data_position = 0;
                                    context_data.push(getCharFromInt(context_data_val));
                                    context_data_val = 0;
                                } else {
                                    context_data_position++;
                                }
                                value = value >> 1;
                            }
                        }
                        context_enlargeIn--;
                        if (context_enlargeIn == 0) {
                            context_enlargeIn = Math.pow(2, context_numBits);
                            context_numBits++;
                        }
                        delete context_dictionaryToCreate[context_w];
                    } else {
                        value = context_dictionary[context_w];
                        for (i=0 ; i<context_numBits ; i++) {
                            context_data_val = (context_data_val << 1) | (value & 1);
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = value >> 1;
                        }
                    }
                    context_enlargeIn--;
                    if (context_enlargeIn == 0) {
                        context_enlargeIn = Math.pow(2, context_numBits);
                        context_numBits++;
                    }
                    context_dictionary[context_wc] = context_dictSize++;
                    context_w = String(context_c);
                }
            }

            if (context_w !== "") {
                if (Object.prototype.hasOwnProperty.call(context_dictionaryToCreate,context_w)) {
                    if (context_w.charCodeAt(0)<256) {
                        for (i=0 ; i<context_numBits ; i++) {
                            context_data_val = (context_data_val << 1);
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                        }
                        value = context_w.charCodeAt(0);
                        for (i=0 ; i<8 ; i++) {
                            context_data_val = (context_data_val << 1) | (value & 1);
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = value >> 1;
                        }
                    } else {
                        value = 1;
                        for (i=0 ; i<context_numBits ; i++) {
                            context_data_val = (context_data_val << 1) | value;
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = 0;
                        }
                        value = context_w.charCodeAt(0);
                        for (i=0 ; i<16 ; i++) {
                            context_data_val = (context_data_val << 1) | (value & 1);
                            if (context_data_position == bitsPerMode - 1) {
                                context_data_position = 0;
                                context_data.push(getCharFromInt(context_data_val));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = value >> 1;
                        }
                    }
                    context_enlargeIn--;
                    if (context_enlargeIn == 0) {
                        context_enlargeIn = Math.pow(2, context_numBits);
                        context_numBits++;
                    }
                    delete context_dictionaryToCreate[context_w];
                } else {
                    value = context_dictionary[context_w];
                    for (i=0 ; i<context_numBits ; i++) {
                        context_data_val = (context_data_val << 1) | (value & 1);
                        if (context_data_position == bitsPerMode - 1) {
                            context_data_position = 0;
                            context_data.push(getCharFromInt(context_data_val));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = value >> 1;
                    }
                }
                context_enlargeIn--;
                if (context_enlargeIn == 0) {
                    context_enlargeIn = Math.pow(2, context_numBits);
                    context_numBits++;
                }
            }

            value = 2;
            for (i=0 ; i<context_numBits ; i++) {
                context_data_val = (context_data_val << 1) | (value & 1);
                if (context_data_position == bitsPerMode - 1) {
                    context_data_position = 0;
                    context_data.push(getCharFromInt(context_data_val));
                    context_data_val = 0;
                } else {
                    context_data_position++;
                }
                value = value >> 1;
            }

            while (true) {
                context_data_val = (context_data_val << 1);
                if (context_data_position == bitsPerMode - 1) {
                    context_data.push(getCharFromInt(context_data_val));
                    break;
                }
                else context_data_position++;
            }
            return context_data.join('');
        };

        var minified = json;
        try {
            minified = JSON.stringify(JSON.parse(json));
        } catch (_) {}
        var compressed = _compress(minified, 6, function(a){return keyStrUriSafe.charAt(a);});
        if (!compressed) return false;
        var origin = window.location.origin;
        var pathname = window.location.pathname;
        var fullUrl = origin + pathname + '#piece=' + compressed;
        
        window.history.replaceState(null, '', fullUrl);

        var textarea = document.createElement('textarea');
        textarea.value = fullUrl;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        try {
            document.execCommand('copy');
        } catch (_) {}
        document.body.removeChild(textarea);

        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(fullUrl).catch(function() {});
        }
        return true;
    } catch(e) {
        console.error('Error sharing URL:', e);
        return false;
    }
}""")
private external fun jsSharePatchUrl(json: String): Boolean

actual fun isShareUrlAvailable(): Boolean = true

actual fun sharePatchUrl(json: String): Boolean {
    return jsSharePatchUrl(json)
}

actual fun getUrlEncodedPatchJson(): String? {
    try {
        val hash = window.location.hash
        if (hash.isNotEmpty()) {
            val query = hash.removePrefix("#")
            if (query.startsWith("piece=")) {
                val encoded = query.substringAfter("piece=")
                if (encoded.isNotEmpty()) {
                    val decompressed = jsLzDecompress(encoded)
                    if (!decompressed.isNullOrEmpty()) {
                        return decompressed
                    }
                }
            } else if (query.startsWith("data=")) {
                val encoded = query.substringAfter("data=")
                if (encoded.isNotEmpty()) {
                    val decompressed = jsLzDecompress(encoded)
                    if (!decompressed.isNullOrEmpty()) {
                        return decompressed
                    }
                }
            } else if (query.isNotEmpty() && !query.startsWith("examples/") && !query.startsWith("tutorials/") && !query.contains("=")) {
                val decompressed = jsLzDecompress(query)
                if (!decompressed.isNullOrEmpty()) {
                    return decompressed
                }
            }
        }
    } catch (e: Exception) {
        println("Error reading encoded patch JSON: ${e.message}")
    }
    return null
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
