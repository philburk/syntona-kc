package com.syntona

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

fun getEventCharCode(event: KeyEvent): Int {
    return when (event.key) {
        Key.A -> 'A'.code
        Key.B -> 'B'.code
        Key.C -> 'C'.code
        Key.D -> 'D'.code
        Key.E -> 'E'.code
        Key.F -> 'F'.code
        Key.G -> 'G'.code
        Key.H -> 'H'.code
        Key.I -> 'I'.code
        Key.J -> 'J'.code
        Key.K -> 'K'.code
        Key.L -> 'L'.code
        Key.M -> 'M'.code
        Key.N -> 'N'.code
        Key.O -> 'O'.code
        Key.P -> 'P'.code
        Key.Q -> 'Q'.code
        Key.R -> 'R'.code
        Key.S -> 'S'.code
        Key.T -> 'T'.code
        Key.U -> 'U'.code
        Key.V -> 'V'.code
        Key.W -> 'W'.code
        Key.X -> 'X'.code
        Key.Y -> 'Y'.code
        Key.Z -> 'Z'.code
        Key.Zero -> '0'.code
        Key.One -> '1'.code
        Key.Two -> '2'.code
        Key.Three -> '3'.code
        Key.Four -> '4'.code
        Key.Five -> '5'.code
        Key.Six -> '6'.code
        Key.Seven -> '7'.code
        Key.Eight -> '8'.code
        Key.Nine -> '9'.code
        Key.Spacebar -> 32
        Key.Comma -> ','.code
        else -> 0
    }
}
