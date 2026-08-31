package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlin.math.pow
import kotlin.random.Random

class EquationModule(type: String) : Module(type) {
    private var _equation by mutableStateOf("x")
    var equation: String
        get() = _equation
        set(value) {
            _equation = value
            compile(value)
        }

    val minWidth: Float get() = 140f
    val minHeight: Float
        get() = when (type) {
            "f(x,y,z)" -> 88f
            "f(x,y)" -> 60f
            else -> 40f
        }

    private var _width by mutableStateOf(180f)
    var width: Float
        get() = _width
        set(value) {
            _width = maxOf(value, minWidth)
        }

    private var _height by mutableStateOf(defaultHeightFor(type))
    var height: Float
        get() = _height
        set(value) {
            _height = maxOf(value, minHeight)
        }

    var errorMessage by mutableStateOf<String?>(null)
    private var parsedAst: ExpressionNode? = null

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
        compile(_equation)
    }

    fun compile(eq: String): Boolean {
        if (eq.isBlank()) {
            errorMessage = "Equation cannot be empty"
            return false
        }
        return try {
            val parser = SimpleExpressionParser(eq)
            val ast = parser.parse()
            validateAst(ast)
            parsedAst = ast
            _equation = eq
            errorMessage = null
            true
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
            println("Failed to compile equation '$eq': $errorMessage")
            false
        }
    }

    override fun execute(triggerMessage: Message) {
        val ast = parsedAst ?: return
        val xVal = inputX.getDoubleValue()
        val yVal = inputY?.getDoubleValue() ?: 0.0
        val zVal = inputZ?.getDoubleValue() ?: 0.0

        try {
            val result = evaluateAst(ast, xVal, yVal, zVal)
            output.send(Message.FloatValue(triggerMessage.time, result))
        } catch (e: Exception) {
            println("Evaluation failed: ${e.message}")
        }
    }

    private fun validateAst(node: ExpressionNode) {
        when (node) {
            is NumericNode -> {}
            is VariableNode -> {
                val allowed = when (name) {
                    "f(x)" -> listOf("x")
                    "f(x,y)" -> listOf("x", "y")
                    "f(x,y,z)" -> listOf("x", "y", "z")
                    else -> listOf("x", "y", "z")
                }
                if (node.name !in allowed) {
                    throw IllegalArgumentException("Variable '${node.name}' not allowed in $name module")
                }
            }
            is NullaryNode -> {
                val allowed = listOf("rand")
                if (node.operation !in allowed) {
                    throw IllegalArgumentException("Unknown function: ${node.operation}()")
                }
            }
            is UnaryNode -> {
                val allowed = listOf("+", "-", "sin", "cos", "tan", "ln", "log", "exp", "sqrt", "abs", "floor", "round", "p2f", "f2p")
                if (node.operation !in allowed) {
                    throw IllegalArgumentException("Unknown function: ${node.operation}")
                }
                validateAst(node.arg)
            }
            is BinaryNode -> {
                val allowed = listOf("+", "-", "*", "/", "^", "power")
                if (node.operation !in allowed) {
                    throw IllegalArgumentException("Unknown operator: ${node.operation}")
                }
                validateAst(node.left)
                validateAst(node.right)
            }
        }
    }

    private fun evaluateAst(node: ExpressionNode, x: Double, y: Double, z: Double): Double {
        return when (node) {
            is NumericNode -> node.value
            is VariableNode -> {
                when (node.name) {
                    "x" -> x
                    "y" -> y
                    "z" -> z
                    else -> 0.0
                }
            }
            is NullaryNode -> {
                when (node.operation) {
                    "rand" -> Random.nextDouble()
                    else -> throw IllegalArgumentException("Unknown function: ${node.operation}()")
                }
            }
            is UnaryNode -> {
                val arg = evaluateAst(node.arg, x, y, z)
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
                    "floor" -> kotlin.math.floor(arg)
                    "round" -> kotlin.math.round(arg)
                    "p2f" -> com.softsynth.math.AudioMath.pitchToFrequency(arg)
                    "f2p" -> com.softsynth.math.AudioMath.frequencyToPitch(arg)
                    else -> throw IllegalArgumentException("Unknown unary operation: ${node.operation}")
                }
            }
            is BinaryNode -> {
                val l = evaluateAst(node.left, x, y, z)
                val r = evaluateAst(node.right, x, y, z)
                when (node.operation) {
                    "+" -> l + r
                    "-" -> l - r
                    "*" -> l * r
                    "/" -> l / r
                    "^", "power" -> l.pow(r)
                    else -> throw IllegalArgumentException("Unknown binary operation: ${node.operation}")
                }
            }
        }
    }

    companion object {
        fun defaultHeightFor(type: String): Float = when (type) {
            "f(x,y,z)" -> 88f
            "f(x,y)" -> 60f
            else -> 40f
        }
    }
}

// AST Nodes
sealed interface ExpressionNode
data class NumericNode(val value: Double) : ExpressionNode
data class VariableNode(val name: String) : ExpressionNode
data class NullaryNode(val operation: String) : ExpressionNode
data class UnaryNode(val operation: String, val arg: ExpressionNode) : ExpressionNode
data class BinaryNode(val operation: String, val left: ExpressionNode, val right: ExpressionNode) : ExpressionNode

// Parser
class SimpleExpressionParser(val input: String) {
    private var pos = 0

    fun parse(): ExpressionNode {
        val node = parseExpression()
        skipWhitespace()
        if (pos < input.length) {
            throw IllegalArgumentException("Unexpected characters at end of expression: ${input.substring(pos)}")
        }
        return node
    }

    private fun parseExpression(): ExpressionNode {
        var node = parseTerm()
        while (true) {
            skipWhitespace()
            if (pos >= input.length) break
            val char = input[pos]
            if (char == '+' || char == '-') {
                pos++
                val right = parseTerm()
                node = BinaryNode(char.toString(), node, right)
            } else {
                break
            }
        }
        return node
    }

    private fun parseTerm(): ExpressionNode {
        var node = parseFactor()
        while (true) {
            skipWhitespace()
            if (pos >= input.length) break
            val char = input[pos]
            if (char == '*' || char == '/') {
                pos++
                val right = parseFactor()
                node = BinaryNode(char.toString(), node, right)
            } else {
                break
            }
        }
        return node
    }

    private fun parseFactor(): ExpressionNode {
        var node = parsePrimary()
        skipWhitespace()
        if (pos < input.length && input[pos] == '^') {
            pos++
            val exponent = parseFactor()
            node = BinaryNode("^", node, exponent)
        }
        return node
    }

    private fun parsePrimary(): ExpressionNode {
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
            return UnaryNode(char.toString(), inner)
        }
        if (char.isDigit() || char == '.') {
            return parseNumber()
        }
        if (char.isLetter()) {
            return parseIdentifier()
        }
        throw IllegalArgumentException("Unexpected character: $char")
    }

    private fun parseNumber(): ExpressionNode {
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
            pos++
        }
        val numStr = input.substring(start, pos)
        val value = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
        return NumericNode(value)
    }

    private fun parseIdentifier(): ExpressionNode {
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
            pos++
        }
        val name = input.substring(start, pos)
        skipWhitespace()
        if (pos < input.length && input[pos] == '(') {
            // Function call
            pos++ // consume '('
            skipWhitespace()
            if (pos < input.length && input[pos] == ')') {
                pos++ // consume ')'
                return NullaryNode(name)
            }
            val arg = parseExpression()
            skipWhitespace()
            if (pos >= input.length || input[pos] != ')') {
                throw IllegalArgumentException("Expected ')' after function argument")
            }
            pos++ // consume ')'
            return UnaryNode(name, arg)
        }
        // It's a variable/symbol
        return VariableNode(name)
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }
}
