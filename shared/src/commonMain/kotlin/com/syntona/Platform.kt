package com.syntona

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
