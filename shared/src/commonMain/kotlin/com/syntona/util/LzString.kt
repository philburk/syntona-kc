package com.syntona.util

/**
 * Pure Kotlin implementation of the LZ-String compression algorithm.
 * Produces output compatible with LZ-String `compressToEncodedURIComponent` / `decompressFromEncodedURIComponent`.
 */
object LzString {
    private const val KEY_STR_URI_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-$"

    fun compressToEncodedURIComponent(uncompressed: String?): String {
        if (uncompressed == null) return ""
        return compress(uncompressed, 6) { KEY_STR_URI_SAFE[it] }
    }

    fun decompressFromEncodedURIComponent(compressed: String?): String? {
        if (compressed.isNullOrEmpty()) return if (compressed == null) "" else null
        val input = compressed.replace(' ', '+')
        return try {
            decompress(input.length, 32) { index ->
                if (index < input.length) {
                    val idx = KEY_STR_URI_SAFE.indexOf(input[index])
                    if (idx >= 0) idx else 0
                } else 0
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun compress(uncompressed: String, bitsPerMode: Int, getCharFromInt: (Int) -> Char): String {
        if (uncompressed.isEmpty()) return ""
        val contextDictionary = mutableMapOf<String, Int>()
        val contextDictionaryToCreate = mutableSetOf<String>()
        var contextC: String
        var contextW = ""
        var contextEnlargeIn = 2
        var contextDictSize = 3
        var contextNumBits = 2
        val contextData = StringBuilder()
        var contextDataVal = 0
        var contextDataPosition = 0

        for (ii in uncompressed.indices) {
            contextC = uncompressed[ii].toString()
            if (!contextDictionary.containsKey(contextC)) {
                contextDictionary[contextC] = contextDictSize++
                contextDictionaryToCreate.add(contextC)
            }

            val contextWc = contextW + contextC
            if (contextDictionary.containsKey(contextWc)) {
                contextW = contextWc
            } else {
                if (contextDictionaryToCreate.contains(contextW)) {
                    if (contextW[0].code < 256) {
                        for (i in 0 until contextNumBits) {
                            contextDataVal = contextDataVal shl 1
                            if (contextDataPosition == bitsPerMode - 1) {
                                contextDataPosition = 0
                                contextData.append(getCharFromInt(contextDataVal))
                                contextDataVal = 0
                            } else {
                                contextDataPosition++
                            }
                        }
                        var value = contextW[0].code
                        for (i in 0 until 8) {
                            contextDataVal = (contextDataVal shl 1) or (value and 1)
                            if (contextDataPosition == bitsPerMode - 1) {
                                contextDataPosition = 0
                                contextData.append(getCharFromInt(contextDataVal))
                                contextDataVal = 0
                            } else {
                                contextDataPosition++
                            }
                            value = value shr 1
                        }
                    } else {
                        var value = 1
                        for (i in 0 until contextNumBits) {
                            contextDataVal = (contextDataVal shl 1) or value
                            if (contextDataPosition == bitsPerMode - 1) {
                                contextDataPosition = 0
                                contextData.append(getCharFromInt(contextDataVal))
                                contextDataVal = 0
                            } else {
                                contextDataPosition++
                            }
                            value = 0
                        }
                        value = contextW[0].code
                        for (i in 0 until 16) {
                            contextDataVal = (contextDataVal shl 1) or (value and 1)
                            if (contextDataPosition == bitsPerMode - 1) {
                                contextDataPosition = 0
                                contextData.append(getCharFromInt(contextDataVal))
                                contextDataVal = 0
                            } else {
                                contextDataPosition++
                            }
                            value = value shr 1
                        }
                    }
                    contextEnlargeIn--
                    if (contextEnlargeIn == 0) {
                        contextEnlargeIn = 1 shl contextNumBits
                        contextNumBits++
                    }
                    contextDictionaryToCreate.remove(contextW)
                } else {
                    var value = contextDictionary[contextW]!!
                    for (i in 0 until contextNumBits) {
                        contextDataVal = (contextDataVal shl 1) or (value and 1)
                        if (contextDataPosition == bitsPerMode - 1) {
                            contextDataPosition = 0
                            contextData.append(getCharFromInt(contextDataVal))
                            contextDataVal = 0
                        } else {
                            contextDataPosition++
                        }
                        value = value shr 1
                    }
                }
                contextEnlargeIn--
                if (contextEnlargeIn == 0) {
                    contextEnlargeIn = 1 shl contextNumBits
                    contextNumBits++
                }
                contextDictionary[contextWc] = contextDictSize++
                contextW = contextC
            }
        }

        if (contextW.isNotEmpty()) {
            if (contextDictionaryToCreate.contains(contextW)) {
                if (contextW[0].code < 256) {
                    for (i in 0 until contextNumBits) {
                        contextDataVal = contextDataVal shl 1
                        if (contextDataPosition == bitsPerMode - 1) {
                            contextDataPosition = 0
                            contextData.append(getCharFromInt(contextDataVal))
                            contextDataVal = 0
                        } else {
                            contextDataPosition++
                        }
                    }
                    var value = contextW[0].code
                    for (i in 0 until 8) {
                        contextDataVal = (contextDataVal shl 1) or (value and 1)
                        if (contextDataPosition == bitsPerMode - 1) {
                            contextDataPosition = 0
                            contextData.append(getCharFromInt(contextDataVal))
                            contextDataVal = 0
                        } else {
                            contextDataPosition++
                        }
                        value = value shr 1
                    }
                } else {
                    var value = 1
                    for (i in 0 until contextNumBits) {
                        contextDataVal = (contextDataVal shl 1) or value
                        if (contextDataPosition == bitsPerMode - 1) {
                            contextDataPosition = 0
                            contextData.append(getCharFromInt(contextDataVal))
                            contextDataVal = 0
                        } else {
                            contextDataPosition++
                        }
                        value = 0
                    }
                    value = contextW[0].code
                    for (i in 0 until 16) {
                        contextDataVal = (contextDataVal shl 1) or (value and 1)
                        if (contextDataPosition == bitsPerMode - 1) {
                            contextDataPosition = 0
                            contextData.append(getCharFromInt(contextDataVal))
                            contextDataVal = 0
                        } else {
                            contextDataPosition++
                        }
                        value = value shr 1
                    }
                }
                contextEnlargeIn--
                if (contextEnlargeIn == 0) {
                    contextEnlargeIn = 1 shl contextNumBits
                    contextNumBits++
                }
                contextDictionaryToCreate.remove(contextW)
            } else {
                var value = contextDictionary[contextW]!!
                for (i in 0 until contextNumBits) {
                    contextDataVal = (contextDataVal shl 1) or (value and 1)
                    if (contextDataPosition == bitsPerMode - 1) {
                        contextDataPosition = 0
                        contextData.append(getCharFromInt(contextDataVal))
                        contextDataVal = 0
                    } else {
                        contextDataPosition++
                    }
                    value = value shr 1
                }
            }
            contextEnlargeIn--
            if (contextEnlargeIn == 0) {
                contextEnlargeIn = 1 shl contextNumBits
                contextNumBits++
            }
        }

        var value = 2
        for (i in 0 until contextNumBits) {
            contextDataVal = (contextDataVal shl 1) or (value and 1)
            if (contextDataPosition == bitsPerMode - 1) {
                contextDataPosition = 0
                contextData.append(getCharFromInt(contextDataVal))
                contextDataVal = 0
            } else {
                contextDataPosition++
            }
            value = value shr 1
        }

        while (true) {
            contextDataVal = contextDataVal shl 1
            if (contextDataPosition == bitsPerMode - 1) {
                contextData.append(getCharFromInt(contextDataVal))
                break
            } else {
                contextDataPosition++
            }
        }
        return contextData.toString()
    }

    private fun decompress(length: Int, resetValue: Int, getNextValue: (Int) -> Int): String? {
        val dictionary = mutableListOf<String>()
        var next: Int
        var enlargeIn = 4
        var dictSize = 4
        var numBits = 3
        var entry: String
        val result = StringBuilder()
        var w: String
        var c: String

        val dataVal = getNextValue(0)
        var dataPosition = resetValue
        var dataIndex = 1

        val dataObj = DataObj(dataVal, dataPosition, dataIndex)

        fun readBits(bitsCount: Int): Int {
            var bits = 0
            var maxpower = 1 shl bitsCount
            var power = 1
            while (power != maxpower) {
                val resb = dataObj.value and dataObj.position
                dataObj.position = dataObj.position shr 1
                if (dataObj.position == 0) {
                    dataObj.position = resetValue
                    dataObj.value = getNextValue(dataObj.index++)
                }
                bits = bits or (if (resb > 0) 1 else 0) * power
                power = power shl 1
            }
            return bits
        }

        val nextVal = readBits(2)
        val firstChar: Char = when (nextVal) {
            0 -> readBits(8).toChar()
            1 -> readBits(16).toChar()
            else -> return ""
        }
        dictionary.add("0")
        dictionary.add("1")
        dictionary.add("2")
        w = firstChar.toString()
        result.append(firstChar)

        while (true) {
            if (dataObj.index > length) return ""
            var cInt = readBits(numBits)
            when (cInt) {
                0 -> {
                    val bits = readBits(8)
                    if (dictSize < dictionary.size) {
                        dictionary[dictSize] = bits.toChar().toString()
                    } else {
                        dictionary.add(bits.toChar().toString())
                    }
                    cInt = dictSize++
                    enlargeIn--
                }
                1 -> {
                    val bits = readBits(16)
                    if (dictSize < dictionary.size) {
                        dictionary[dictSize] = bits.toChar().toString()
                    } else {
                        dictionary.add(bits.toChar().toString())
                    }
                    cInt = dictSize++
                    enlargeIn--
                }
                2 -> return result.toString()
            }

            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }

            if (cInt < dictionary.size) {
                entry = dictionary[cInt]
            } else {
                if (cInt == dictSize) {
                    entry = w + w[0]
                } else {
                    return null
                }
            }
            result.append(entry)

            if (dictSize < dictionary.size) {
                dictionary[dictSize] = w + entry[0]
            } else {
                dictionary.add(w + entry[0])
            }
            dictSize++
            enlargeIn--

            w = entry

            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }
        }
    }

    private class DataObj(var value: Int, var position: Int, var index: Int)

    private fun getBaseValue(alphabet: String, character: Char): Int {
        return alphabet.indexOf(character)
    }
}
