package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok

/**
 * Recursive-descent parser for the calculus expression syntax.
 *
 * Grammar (lowest to highest precedence):
 *   expr   := term (('+' | '−') term)*
 *   term   := factor (('*' | '×' | '·' | '/' | '÷') factor)*
 *   factor := unary ('^' factor)?            // right-associative
 *   unary  := ('−' | '+')? postfix
 *   postfix := primary ('!')*
 *   primary:= number | constant | name '(' args ')' | name | '(' expr ')'
 */
class CalculusParser(private val text: String) {

    private var pos = 0

    companion object {
        private val FUNCS = setOf(
            "sin", "cos", "tan", "csc", "sec", "cot",
            "asin", "acos", "atan", "atan2",
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
            "ln", "log", "log2", "exp", "sqrt", "cbrt", "root",
            "abs", "sign", "floor", "ceil", "round", "min", "max",
        )

        fun parse(text: String): CalcResult<Node> =
        try {
            parseChecked(text)
        } catch (e: ParseError) {
            err(ErrorKind.Syntax, e.message ?: "The expression could not be parsed")
        }

    private fun parseChecked(text: String): CalcResult<Node> {
        val p = CalculusParser(text)
        val node = p.parseExpr()
        p.skipSpace()
        if (!p.atEnd()) {
            return err(ErrorKind.Syntax, "Unexpected “${p.rest()}”")
        }
        return node?.let { ok(it) }
            ?: err(ErrorKind.InvalidInput, "Enter an expression")
    }
    }

    class ParseError(override val message: String) : Exception(message)

    private fun atEnd(): Boolean = pos >= text.length
    private fun rest(): String = text.substring(pos).take(12)

    private fun peek(): Char? = if (atEnd()) null else text[pos]

    private fun skipSpace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun match(vararg symbols: String): String? {
        skipSpace()
        for (s in symbols) {
            if (text.startsWith(s, pos)) {
                pos += s.length
                return s
            }
        }
        return null
    }

    private fun error(message: String): CalcResult<Node> =
        err(ErrorKind.Syntax, message)

    private fun parseExpr(): Node? {
        var left = parseTerm() ?: return null
        while (true) {
            skipSpace()
            when {
                match("+") != null -> {
                    val right = parseTerm() ?: return null
                    left = Node.Add(left, right)
                }
                match("-", "−", "–") != null -> {
                    val right = parseTerm() ?: return null
                    left = Node.Sub(left, right)
                }
                else -> return left
            }
        }
    }

    private fun parseTerm(): Node? {
        var left = parseFactor() ?: return null
        while (true) {
            skipSpace()
            when {
                match("/", "÷") != null -> {
                    val right = parseFactor() ?: return null
                    left = Node.Div(left, right)
                }
                // explicit multiplication, and implicit (2x, 2π, 2(x+1))
                match("*", "×", "·") != null -> {
                    val right = parseFactor() ?: return null
                    left = Node.Mul(left, right)
                }
                startsImplicitFactor() -> {
                    val right = parseFactor() ?: return null
                    left = Node.Mul(left, right)
                }
                else -> return left
            }
        }
    }

    /** True when the upcoming token begins a factor and is adjacent (no operator). */
    private fun startsImplicitFactor(): Boolean {
        skipSpace()
        val c = peek() ?: return false
        if (c.isDigit() || c == '.') return true
        if (c == '(' || c == 'π' || c == 'τ' || c == 'φ') return true
        if (c == '−' || c == '-') return false
        if (!c.isLetter()) return false
        var i = pos
        while (i < text.length && text[i].isLetter()) i++
        val word = text.substring(pos, i)
        if (word.isEmpty()) return false
        // Skip whitespace: "2 sin(30)" is implicit multiplication too.
        var j = i
        while (j < text.length && text[j].isWhitespace()) j++
        val next = text.getOrNull(j)
        val lower = word.lowercase()
        return when {
            // A function call or a lone function name is still a factor: 2sin(30), 2sin 30.
            lower in FUNCS -> true
            Constants.value(word) != null -> true
            // A bare variable is a factor: 2x, 3xy. "e" followed by digits is a number.
            lower == "e" && next?.let { it.isDigit() || it == '.' } == true -> false
            else -> true
        }
    }

    private fun parseFactor(): Node? = parseUnary()

    /**
     * Reads a signed factor.
     *
     * The power is read inside the sign, so that the sign binds looser than
     * the power: -x^2 is -(x^2), which is what the grammar at the top of
     * this file says and what the printer's own text assumes. A sign that
     * comes before another sign is a sign of the whole of what follows.
     */
    private fun parseUnary(): Node? {
        skipSpace()
        if (match("-", "−") != null) {
            val operand = parseUnary() ?: return null
            return Node.Neg(operand)
        }
        match("+")
        val base = parsePostfix() ?: return null
        skipSpace()
        if (match("^") != null) {
            val exp = parseFactor() ?: return null
            return Node.Pow(base, exp)
        }
        return base
    }

    private fun parsePostfix(): Node? {
        var node = parsePrimary() ?: return null
        while (true) {
            skipSpace()
            if (peek() == '!') {
                pos++
                node = Node.Fun("fact", listOf(node))
            } else {
                return node
            }
        }
    }

    private fun parsePrimary(): Node? {
        skipSpace()
        if (atEnd()) return null
        val c = peek()!!

        if (match("(") != null) {
            val inner = parseExpr() ?: throw ParseError("Missing expression inside parentheses")
            if (match(")") == null) throw ParseError("Missing closing parenthesis")
            return inner
        }
        if (c.isDigit() || c == '.') return parseNumber()
        if (c.isLetter() || c == 'π' || c == 'τ' || c == 'φ') return parseName()
        throw ParseError("Unexpected “$c”")
    }

    private fun parseNumber(): Node {
        val start = pos
        while (!atEnd() && (text[pos].isDigit() || text[pos] == '.')) pos++
        // scientific notation
        if (!atEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
            val save = pos
            pos++
            if (!atEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
            if (!atEnd() && text[pos].isDigit()) {
                while (!atEnd() && text[pos].isDigit()) pos++
            } else {
                pos = save
            }
        }
        val s = text.substring(start, pos)
        val v = s.toDoubleOrNull() ?: throw ParseError("“$s” is not a number")
        return Node.Num(v)
    }

    private fun parseName(): Node {
        val start = pos
        while (!atEnd() && (text[pos].isLetterOrDigit() || text[pos] == '_')) pos++
        var word = text.substring(start, pos)
        // unicode constants
        if (word.isEmpty()) {
            val c = text[pos]
            pos++
            return Node.Const(c.toString())
        }
        val lower = word.lowercase()
        // Constants win over function names when they are written bare
        // (e and p are both a constant and a function name).
        if (lower == "e" && pos < text.length && !text[pos].isLetterOrDigit()) {
            return Node.Const(word)
        }
        if (lower == "p") return Node.Const("pi")
        skipSpace()
        if (peek() == '(') {
            if (Constants.value(lower) != null) return Node.Const(word)
            if (lower !in FUNCS) throw ParseError("Unknown function “$word”")
            pos++ // consume '('
            val args = mutableListOf<Node>()
            skipSpace()
            if (peek() == ')') {
                pos++
            } else {
                while (true) {
                    val arg = parseExpr() ?: throw ParseError("Missing argument for $word")
                    args.add(arg)
                    skipSpace()
                    if (match(",") != null) continue
                    if (match(")") != null) break
                    throw ParseError("Missing closing parenthesis in $word(…)")
                }
            }
            return Node.Fun(lower, args)
        }
        Constants.value(word)?.let { return Node.Const(word) }
        if (lower in FUNCS) return Node.Fun(lower, emptyList()) // will error on eval
        return Node.Var(word)
    }
}