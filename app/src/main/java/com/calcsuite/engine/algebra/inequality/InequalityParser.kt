package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.Rat

/** Raised when the input cannot be understood. The message is user-facing. */
class InequalityParseException(message: String) : Exception(message)

/**
 * A relation between two expressions.
 *
 * A compound inequality such as `-2 < 3x + 1 <= 10` becomes
 * `And(Simple(-2, <, 3x+1), Simple(3x+1, <=, 10))`, and a system of
 * inequalities becomes an `And` of the individual relations.
 */
sealed interface Rel {

    data class Simple(val lhs: IExpr, val op: CompareOp, val rhs: IExpr) : Rel

    /** Every part must hold. */
    data class And(val parts: List<Rel>) : Rel

    /** At least one part must hold. */
    data class Or(val parts: List<Rel>) : Rel
}

/** The outcome of parsing an input, before any solving happens. */
data class ParsedInequality(
    val relation: Rel,
    val variable: String,
    val variables: List<String>,
    /** How many separate chains the input contained. */
    val chainCount: Int,
    /** How many comparisons the input contained in total. */
    val relationCount: Int,
)

/**
 * Parses inequality input.
 *
 * The language is a superset of the app's other expression languages: the same
 * `+ - * / ^` with implicit multiplication, the same constants, and the same
 * function names, plus `|…|` for absolute value and the comparison operators.
 *
 * A **chain** is one or more comparisons sharing an expression, so
 * `-2 < 3x + 1 <= 10` is a single chain of two comparisons. **Chains** are
 * separated by a line break, `and`, or `or`, and a chain count above one means
 * the input is a system.
 */
object InequalityParser {

    private enum class T {
        NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, CARET,
        LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, PIPE,
        COMPARE, NEWLINE, AND, OR, END
    }

    private class Token(val type: T, val text: String, val number: Rat? = null)

    private const val FUNCTIONS = "sqrt,cbrt,abs,ln,log,log2,exp,sin,cos,tan,asin,acos,atan,sign,floor,ceil,round,fact"

    // ------------------------------------------------------------- tokenizer

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\n' -> {
                    tokens.add(Token(T.NEWLINE, "\n"))
                    i++
                }
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < input.length && input[i + 1].isDigit()) -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    if (i < input.length && (input[i] == 'e' || input[i] == 'E')) {
                        val mark = i
                        var j = i + 1
                        if (j < input.length && (input[j] == '+' || input[j] == '-')) j++
                        if (j < input.length && input[j].isDigit()) {
                            i = j
                            while (i < input.length && input[i].isDigit()) i++
                        } else {
                            i = mark
                        }
                    }
                    val text = input.substring(start, i)
                    val value = try {
                        Rat.ofDecimal(text)
                    } catch (_: NumberFormatException) {
                        throw InequalityParseException("\"$text\" is not a number")
                    }
                    tokens.add(Token(T.NUMBER, text, value))
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) i++
                    val text = input.substring(start, i)
                    when (text.lowercase()) {
                        "and" -> tokens.add(Token(T.AND, text))
                        "or" -> tokens.add(Token(T.OR, text))
                        else -> tokens.add(Token(T.IDENT, text))
                    }
                }
                c == '<' || c == '>' || c == '=' -> {
                    if (i + 1 < input.length && input[i + 1] == '=') {
                        tokens.add(Token(T.COMPARE, input.substring(i, i + 2)))
                        i += 2
                    } else {
                        tokens.add(Token(T.COMPARE, c.toString()))
                        i++
                    }
                }
                else -> {
                    val type = when (c) {
                        '+' -> T.PLUS
                        '-' -> T.MINUS
                        '*' -> T.STAR
                        '/' -> T.SLASH
                        '^' -> T.CARET
                        '(' -> T.LPAREN
                        ')' -> T.RPAREN
                        '[' -> T.LBRACKET
                        ']' -> T.RBRACKET
                        ',' -> T.COMMA
                        '|' -> T.PIPE
                        else -> throw InequalityParseException("\"$c\" is not something this tool understands")
                    }
                    tokens.add(Token(type, c.toString()))
                    i++
                }
            }
        }
        tokens.add(Token(T.END, ""))
        return tokens
    }

    // ---------------------------------------------------------------- parser

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        /** How many absolute-value bars are currently open. */
        private var absDepth = 0

        private fun peek(): Token = tokens[pos]

        private fun peekAt(offset: Int): Token = tokens.getOrElse(pos + offset) { tokens.last() }

        private fun advance(): Token = tokens[pos++]

        private fun expect(type: T, what: String) {
            if (peek().type != type) {
                throw InequalityParseException("Expected $what")
            }
            advance()
        }

        /** How many separate chains the input contained. */
        var chainCount = 0
            private set

        /** Parses the whole input into a relation tree. */
        fun parseSystem(): Rel {
            val chains = mutableListOf<List<Rel.Simple>>()
            val connectors = mutableListOf<T>()
            skipSeparators()
            while (peek().type != T.END) {
                chains.add(parseChain())
                chainCount++
                skipSeparators()
                if (peek().type == T.AND || peek().type == T.OR) {
                    connectors.add(advance().type)
                    skipSeparators()
                } else if (peek().type != T.END) {
                    // A line break separated chain: it is another condition of
                    // the same system, and all of them must hold.
                    connectors.add(T.AND)
                } else break
            }
            if (chains.isEmpty()) throw InequalityParseException("Enter an inequality to solve")
            if (chains.size == 1) {
                val parts = chains.first()
                return if (parts.size == 1) parts.first() else Rel.And(parts)
            }
            // Several chains: join them with the connectors that were written.
            var relation: Rel = Rel.And(chains.first())
            for (index in 1 until chains.size) {
                val connector = connectors.getOrElse(index - 1) { T.AND }
                val next = chains[index]
                val part: Rel = if (next.size == 1) next.first() else Rel.And(next)
                relation = if (connector == T.OR) Rel.Or(listOf(relation, part)) else Rel.And(listOf(relation, part))
            }
            return relation
        }

        private fun skipSeparators() {
            while (peek().type == T.NEWLINE) advance()
        }

        /** One chain: `expr` followed by one or more `compare expr` groups. */
        private fun parseChain(): List<Rel.Simple> {
            val first = parseExpression()
            if (peek().type != T.COMPARE) {
                throw InequalityParseException("An inequality needs a comparison, such as <, >, ≤ or ≥")
            }
            val parts = mutableListOf<Rel.Simple>()
            var previous = first
            while (peek().type == T.COMPARE) {
                val symbol = advance().text
                val op = CompareOp.fromSymbol(symbol)
                    ?: throw InequalityParseException("\"$symbol\" is not a comparison this tool understands")
                val right = parseExpression()
                parts.add(Rel.Simple(previous, op, right))
                previous = right
            }
            return parts
        }

        fun parseExpression(): IExpr {
            var left = parseTerm()
            while (true) {
                when (peek().type) {
                    T.PLUS -> { advance(); left = IExpr.Add(left, parseTerm()) }
                    T.MINUS -> { advance(); left = IExpr.Sub(left, parseTerm()) }
                    else -> return left
                }
            }
        }

        private fun parseTerm(): IExpr {
            var left = parseFactor()
            while (true) {
                when (peek().type) {
                    T.STAR -> { advance(); left = IExpr.Mul(left, parseFactor()) }
                    T.SLASH -> { advance(); left = IExpr.Div(left, parseFactor()) }
                    else -> {
                        // Implicit multiplication: 2x, 3(x+1), 2|x|.
                        if (startsFactor(peek().type)) {
                            left = IExpr.Mul(left, parseFactor())
                        } else return left
                    }
                }
            }
        }

        private fun startsFactor(type: T): Boolean = when (type) {
            T.NUMBER, T.IDENT, T.LPAREN, T.LBRACKET -> true
            // A bar inside an absolute value closes it, so it must not be read
            // as the start of a factor there.
            T.PIPE -> absDepth == 0
            else -> false
        }

        private fun parseFactor(): IExpr {
            if (peek().type == T.MINUS) {
                advance()
                return IExpr.Neg(parseFactor())
            }
            if (peek().type == T.PLUS) {
                advance()
                return parseFactor()
            }
            return parsePower()
        }

        private fun parsePower(): IExpr {
            val base = parsePrimary()
            if (peek().type == T.CARET) {
                advance()
                return IExpr.Pow(base, parseFactor())
            }
            return base
        }

        private fun parsePrimary(): IExpr {
            val token = peek()
            when (token.type) {
                T.NUMBER -> {
                    advance()
                    return IExpr.Num(token.number!!)
                }
                T.PIPE -> {
                    // |expression| — absolute value.
                    advance()
                    absDepth++
                    val inner = parseExpression()
                    absDepth--
                    if (peek().type != T.PIPE) {
                        throw InequalityParseException("Absolute value needs a closing |")
                    }
                    advance()
                    return IExpr.Call("abs", listOf(inner))
                }
                T.LPAREN -> {
                    advance()
                    val inner = parseExpression()
                    expect(T.RPAREN, "a closing bracket")
                    return inner
                }
                T.LBRACKET -> {
                    advance()
                    val inner = parseExpression()
                    expect(T.RBRACKET, "a closing bracket")
                    return inner
                }
                T.IDENT -> {
                    advance()
                    val name = token.text
                    if (peek().type == T.LPAREN) {
                        advance()
                        val args = mutableListOf<IExpr>()
                        if (peek().type != T.RPAREN) {
                            args.add(parseExpression())
                            while (peek().type == T.COMMA) {
                                advance()
                                args.add(parseExpression())
                            }
                        }
                        expect(T.RPAREN, "a closing bracket")
                        val lower = name.lowercase()
                        if (lower !in FUNCTIONS.split(",")) {
                            throw InequalityParseException("Unknown function \"$name\"")
                        }
                        return IExpr.Call(lower, args)
                    }
                    return when (name.lowercase()) {
                        "pi", "π" -> IExpr.Num(Rat.ofDecimal("3.141592653589793"))
                        "e" -> IExpr.Num(Rat.ofDecimal("2.718281828459045"))
                        else -> IExpr.Var(name)
                    }
                }
                else -> throw InequalityParseException("\"${token.text}\" is not something this tool understands")
            }
        }
    }

    /**
     * Parses [input] as an inequality or a system of inequalities.
     *
     * @param expectedVariables how many variables the input may mention. One
     *   variable means a single-variable solve; two means a two-variable
     *   linear system, which is handled separately.
     */
    fun parse(input: String, expectedVariables: Int = 1): ParsedInequality {
        val text = input.trim()
        if (text.isEmpty()) throw InequalityParseException("Enter an inequality to solve")
        if (text.any { it == '=' && text.contains("==") }) {
            throw InequalityParseException("An inequality needs a single comparison, such as <, >, ≤ or ≥")
        }
        val parser = Parser(tokenize(text))
        val relation = parser.parseSystem()
        val variables = relationVariables(relation)
        if (variables.size > expectedVariables) {
            throw InequalityParseException(
                "This tool solves in one variable, but the input mentions ${variables.joinToString(", ")}",
            )
        }
        if (variables.isEmpty()) {
            throw InequalityParseException("The inequality needs a variable, for example x")
        }
        val ordered = variables.sortedBy { VARIABLE_ORDER.indexOf(it).let { if (it < 0) 99 else it } }
        return ParsedInequality(relation, ordered.first(), ordered, parser.chainCount, countRelations(relation))
    }

    /** The conventional order for variables, so x comes before y before z. */
    private val VARIABLE_ORDER = listOf("x", "y", "z")

    private fun countRelations(relation: Rel): Int = when (relation) {
        is Rel.Simple -> 1
        is Rel.And -> relation.parts.sumOf { countRelations(it) }
        is Rel.Or -> relation.parts.sumOf { countRelations(it) }
    }

    private fun relationVariables(relation: Rel, into: MutableList<String> = mutableListOf()): List<String> {
        when (relation) {
            is Rel.Simple -> {
                relation.lhs.variables(into)
                relation.rhs.variables(into)
            }
            is Rel.And -> relation.parts.forEach { relationVariables(it, into) }
            is Rel.Or -> relation.parts.forEach { relationVariables(it, into) }
        }
        return into
    }
}
