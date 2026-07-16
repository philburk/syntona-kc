package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import space.kscience.kmath.expressions.MST
import space.kscience.kmath.expressions.Symbol
import kotlin.math.pow

class EquationModule(type: String) : Module(type) {
    var equation by mutableStateOf("x")
    var errorMessage by mutableStateOf<String?>(null)
    private var parsedMst: MST? = null

    // Inputs:
    // f(x) has "x" (isKickable = true)
    // f(x,y) has "x" (isKickable = true) and "y" (isKickable = false)
    // f(x,y,z) has "x" (isKickable = true), "y" (isKickable = false), and "z" (isKickable = false)
    val inputX = addInput("x", isKickable = true)
    val inputY = if (type == "f(x,y)" || type == "f(x,y,z)") addInput("y", isKickable = false) else null
    val inputZ = if (type == "f(x,y,z)") addInput("z", isKickable = false) else null
    
    // Output:
    val output = addOutput("out")

    init {
        compile(equation)
    }

    fun compile(eq: String): Boolean {
        if (eq.isBlank()) {
            errorMessage = "Equation cannot be empty"
            return false
        }
        return try {
            val parser = SimpleExpressionParser(eq)
            val mst = parser.parse()
            validateMst(mst)
            parsedMst = mst
            equation = eq
            errorMessage = null
            true
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
            println("Failed to compile equation '$eq': $errorMessage")
            false
        }
    }

    private fun validateMst(node: MST) {
        when (node) {
            is MST.Numeric -> {}
            is Symbol -> {
                val allowed = when (name) {
                    "f(x)" -> listOf("x")
                    "f(x,y)" -> listOf("x", "y")
                    "f(x,y,z)" -> listOf("x", "y", "z")
                    else -> listOf("x", "y", "z")
                }
                if (node.identity !in allowed) {
                    throw IllegalArgumentException("Variable '${node.identity}' not allowed in $name module")
                }
            }
            is MST.Unary -> {
                val allowed = listOf("+", "-", "sin", "cos", "tan", "ln", "log", "exp", "sqrt", "abs")
                if (node.operation !in allowed) {
                    throw IllegalArgumentException("Unknown function: ${node.operation}")
                }
                validateMst(node.value)
            }
            is MST.Binary -> {
                val allowed = listOf("+", "-", "*", "/", "^", "power")
                if (node.operation !in allowed) {
                    throw IllegalArgumentException("Unknown operator: ${node.operation}")
                }
                validateMst(node.left)
                validateMst(node.right)
            }
            else -> {
                val str = node.toString()
                val allowed = when (name) {
                    "f(x)" -> listOf("x")
                    "f(x,y)" -> listOf("x", "y")
                    "f(x,y,z)" -> listOf("x", "y", "z")
                    else -> listOf("x", "y", "z")
                }
                if (str !in allowed) {
                    throw IllegalArgumentException("Variable '$str' not allowed in $name module")
                }
            }
        }
    }

    override fun execute(triggerMessage: Message) {
        val mst = parsedMst ?: return
        val xVal = inputX.getDoubleValue()
        val yVal = inputY?.getDoubleValue() ?: 0.0
        val zVal = inputZ?.getDoubleValue() ?: 0.0

        try {
            val result = evaluateMst(mst, xVal, yVal, zVal)
            output.send(Message.FloatValue(triggerMessage.time, result))
        } catch (e: Exception) {
            println("Evaluation failed: ${e.message}")
        }
    }

    private fun evaluateMst(node: MST, x: Double, y: Double, z: Double): Double {
        return when (node) {
            is MST.Numeric -> node.value.toDouble()
            is Symbol -> {
                when (node.identity) {
                    "x" -> x
                    "y" -> y
                    "z" -> z
                    else -> 0.0
                }
            }
            is MST.Unary -> {
                val arg = evaluateMst(node.value, x, y, z)
                when (node.operation) {
                    "+" -> arg
                    "-" -> -arg
                    "sin" -> kotlin.math.sin(arg)
                    "cos" -> kotlin.math.cos(arg)
                    "tan" -> kotlin.math.tan(arg)
                    "ln", "log" -> kotlin.math.ln(arg)
                    "exp" -> kotlin.math.exp(arg)
                    "sqrt" -> kotlin.math.sqrt(arg)
                    "abs" -> kotlin.math.abs(arg)
                    else -> throw IllegalArgumentException("Unknown unary operation: ${node.operation}")
                }
            }
            is MST.Binary -> {
                val l = evaluateMst(node.left, x, y, z)
                val r = evaluateMst(node.right, x, y, z)
                when (node.operation) {
                    "+" -> l + r
                    "-" -> l - r
                    "*" -> l * r
                    "/" -> l / r
                    "^", "power" -> l.pow(r)
                    else -> throw IllegalArgumentException("Unknown binary operation: ${node.operation}")
                }
            }
            else -> {
                val name = node.toString()
                when (name) {
                    "x" -> x
                    "y" -> y
                    "z" -> z
                    else -> 0.0
                }
            }
        }
    }
}

class SimpleExpressionParser(val input: String) {
    private var pos = 0

    fun parse(): MST {
        val node = parseExpression()
        skipWhitespace()
        if (pos < input.length) {
            throw IllegalArgumentException("Unexpected characters at end of expression: ${input.substring(pos)}")
        }
        return node
    }

    private fun parseExpression(): MST {
        var node = parseTerm()
        while (true) {
            skipWhitespace()
            if (pos >= input.length) break
            val char = input[pos]
            if (char == '+' || char == '-') {
                pos++
                val right = parseTerm()
                node = MST.Binary(char.toString(), node, right)
            } else {
                break
            }
        }
        return node
    }

    private fun parseTerm(): MST {
        var node = parseFactor()
        while (true) {
            skipWhitespace()
            if (pos >= input.length) break
            val char = input[pos]
            if (char == '*' || char == '/') {
                pos++
                val right = parseFactor()
                node = MST.Binary(char.toString(), node, right)
            } else {
                break
            }
        }
        return node
    }

    private fun parseFactor(): MST {
        var node = parsePrimary()
        skipWhitespace()
        if (pos < input.length && input[pos] == '^') {
            pos++
            val exponent = parseFactor()
            node = MST.Binary("^", node, exponent)
        }
        return node
    }

    private fun parsePrimary(): MST {
        skipWhitespace()
        if (pos >= input.length) {
            throw IllegalArgumentException("Unexpected end of expression")
        }
        val char = input[pos]
        if (char == '(') {
            pos++ // consume '('
            val node = parseExpression()
            skipWhitespace()
            if (pos >= input.length || input[pos] != ')') {
                throw IllegalArgumentException("Expected ')'")
            }
            pos++ // consume ')'
            return node
        }
        if (char == '-' || char == '+') {
            pos++
            val inner = parseFactor()
            return MST.Unary(char.toString(), inner)
        }
        if (char.isDigit() || char == '.') {
            return parseNumber()
        }
        if (char.isLetter()) {
            return parseIdentifier()
        }
        throw IllegalArgumentException("Unexpected character: $char")
    }

    private fun parseNumber(): MST {
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
            pos++
        }
        val numStr = input.substring(start, pos)
        val value = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
        return MST.Numeric(value)
    }

    private fun parseIdentifier(): MST {
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
            pos++
        }
        val name = input.substring(start, pos)
        skipWhitespace()
        if (pos < input.length && input[pos] == '(') {
            // Function call
            pos++ // consume '('
            val arg = parseExpression()
            skipWhitespace()
            if (pos >= input.length || input[pos] != ')') {
                throw IllegalArgumentException("Expected ')' after function argument")
            }
            pos++ // consume ')'
            return MST.Unary(name, arg)
        }
        // It's a variable/symbol
        return Symbol(name)
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }
}
