package com.syntona

object SyntonaVersion {
    val major = 0
    val minor = 7
    val revision = 3
    val code = (major shl 24) + (minor shl 16) + revision
    override fun toString(): String = "$major.$minor.$revision beta"
}
