package com.calcsuite.expr

import java.math.BigDecimal

internal sealed interface Token {
    data class Num(val text: String) : Token
    data class Ident(val name: String) : Token
    data object Plus : Token
    data object Minus : Token
    data object Star : Token
    data object Slash : Token
    data object Caret : Token
    data object LParen : Token
    data object RParen : Token
    data object Bang : Token
    data object Percent : Token
    data object Comma : Token
    data object Mod : Token
    data object End : Token
}

internal object Lexer {
    fun tokenize(input: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++

                c.isDigit() || (c == '.' && i + 1 < n && input[i + 1].isDigit()) -> {
                    val start = i
                    while (i < n && (input[i].isDigit() || input[i] == '.')) i++
                    // Careful: allow "1.2.3"? No: the parser rejects it. Only accept
                    // a well-formed mantissa here.
                    val mantissa = input.substring(start, i)
                    if (mantissa.count { it == '.' } > 1) {
                        throw ExprParseException("Malformed number \"$mantissa\"")
                    }
                    // Scientific notation: e / E followed by digits or a sign+digits.
                    if (i < n && (input[i] == 'e' || input[i] == 'E')) {
                        var j = i + 1
                        if (j < n && (input[j] == '+' || input[j] == '-')) j++
                        if (j < n && input[j].isDigit()) {
                            while (j < n && input[j].isDigit()) j++
                            tokens.add(Token.Num(input.substring(start, j)))
                            i = j
                            continue
                        }
                    }
                    tokens.add(Token.Num(mantissa))
                }

                c.isLetter() || c == 'π' || c == 'τ' || c == 'φ' -> {
                    val start = i
                    i++
                    // Identifiers may contain digits and a decimal point after the
                    // first letter so that names like log2 and inputs such as
                    // "sin30.5" stay together; parseIdentCall splits them if needed.
                    while (i < n && (input[i].isLetterOrDigit() || input[i] == '.' ||
                            input[i] == 'π' || input[i] == 'τ' || input[i] == 'φ')
                    ) i++
                    val word = input.substring(start, i)
                    if (word.equals("mod", ignoreCase = true)) tokens.add(Token.Mod)
                    else tokens.add(Token.Ident(word))
                }

                c == '+' -> { tokens.add(Token.Plus); i++ }
                c == '-' || c == '\u2212' -> { tokens.add(Token.Minus); i++ }
                c == '*' || c == '\u00D7' || c == '\u2217' -> { tokens.add(Token.Star); i++ }
                c == '/' || c == '\u00F7' -> { tokens.add(Token.Slash); i++ }
                c == '^' || c == '\u2038' -> { tokens.add(Token.Caret); i++ }
                c == '(' || c == '[' -> { tokens.add(Token.LParen); i++ }
                c == ')' || c == ']' -> { tokens.add(Token.RParen); i++ }
                c == '!' -> { tokens.add(Token.Bang); i++ }
                c == '%' -> { tokens.add(Token.Percent); i++ }
                c == ',' -> { tokens.add(Token.Comma); i++ }
                c == '√' -> { tokens.add(Token.Ident("sqrt")); i++ }
                else -> throw ExprParseException("Unexpected character '$c'")
            }
        }
        tokens.add(Token.End)
        return tokens
    }
}

class ExprParseException(message: String) : Exception(message)

internal class ExpressionParser(input: String) {
    private val tokens: List<Token> = Lexer.tokenize(input)
    private var pos = 0

    private fun peek(): Token = tokens[pos]
    private fun next(): Token = tokens[pos++]
    private fun expect(token: Token) {
        if (peek() != token) throw ExprParseException("Expected \"$token\"")
        pos++
    }

    fun parse(): Expr {
        if (peek() == Token.End) throw ExprParseException("Empty expression")
        val expr = parseAdditive()
        if (peek() != Token.End) throw ExprParseException("Unexpected \"${describe(peek())}\"")
        return expr
    }

    private fun describe(t: Token): String = when (t) {
        is Token.Num -> t.text
        is Token.Ident -> t.name
        Token.Plus -> "+"
        Token.Minus -> "−"
        Token.Star -> "×"
        Token.Slash -> "÷"
        Token.Caret -> "^"
        Token.LParen -> "("
        Token.RParen -> ")"
        Token.Bang -> "!"
        Token.Percent -> "%"
        Token.Comma -> ","
        Token.Mod -> "mod"
        Token.End -> "end of expression"
    }

    private fun parseAdditive(): Expr {
        var left = parseMultiplicative()
        while (true) {
            left = when (peek()) {
                Token.Plus -> { pos++; Expr.Binary(BinOp.Add, left, parseMultiplicative()) }
                Token.Minus -> { pos++; Expr.Binary(BinOp.Sub, left, parseMultiplicative()) }
                Token.Mod -> { pos++; Expr.Binary(BinOp.Mod, left, parseMultiplicative()) }
                else -> return left
            }
        }
    }

    private fun parseMultiplicative(): Expr {
        var left = parseUnary()
        while (true) {
            left = when {
                peek() == Token.Star -> { pos++; Expr.Binary(BinOp.Mul, left, parseUnary()) }
                peek() == Token.Slash -> { pos++; Expr.Binary(BinOp.Div, left, parseUnary()) }
                startsPrimary(peek()) -> Expr.Binary(BinOp.Mul, left, parseUnary())
                else -> return left
            }
        }
    }

    private fun startsPrimary(t: Token): Boolean =
        t is Token.Num || t is Token.Ident || t == Token.LParen

    private fun parseUnary(): Expr {
        return when (peek()) {
            Token.Minus -> { pos++; Expr.Unary(UnOp.Neg, parseUnary()) }
            Token.Plus -> { pos++; Expr.Unary(UnOp.Pos, parseUnary()) }
            else -> parsePower()
        }
    }

    private fun parsePower(): Expr {
        val base = parsePostfix()
        if (peek() == Token.Caret) {
            pos++
            val exponent = parseUnary() // right-associative, allows 2^-3
            return Expr.Binary(BinOp.Pow, base, exponent)
        }
        return base
    }

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()
        while (true) {
            expr = when (peek()) {
                Token.Bang -> { pos++; Expr.Postfix(PostOp.Factorial, expr) }
                Token.Percent -> { pos++; Expr.Postfix(PostOp.Percent, expr) }
                else -> return expr
            }
        }
    }

    private fun parsePrimary(): Expr {
        val token = next()
        return when (token) {
            is Token.Num -> {
                val value = try {
                    BigDecimal(token.text)
                } catch (_: NumberFormatException) {
                    throw ExprParseException("Malformed number \"${token.text}\"")
                }
                Expr.Num(value)
            }

            is Token.Ident -> parseIdentCall(token.name)

            Token.LParen -> {
                val inner = parseAdditive()
                expect(Token.RParen)
                inner
            }

            else -> throw ExprParseException("Unexpected \"${describe(token)}\"")
        }
    }

    private fun parseIdentCall(nameRaw: String): Expr {
        var name = nameRaw
        var trailingNumber: BigDecimal? = null
        var fn = Functions.find(name)
        var const = Consts.find(name)

        // "sin30", "sin30.5", "pi3", "e5" …: split a known name from a numeric tail.
        if (fn == null && const == null) {
            val splitIdx = name.indexOfFirst { it.isDigit() || it == '.' }
            if (splitIdx > 0) {
                val prefix = name.substring(0, splitIdx)
                val digits = name.substring(splitIdx)
                val prefixFn = Functions.find(prefix)
                val prefixConst = Consts.find(prefix)
                val trailing = try {
                    if (digits.endsWith(".")) null else BigDecimal(digits)
                } catch (_: NumberFormatException) {
                    null
                }
                if (trailing != null && (prefixFn != null || prefixConst != null)) {
                    name = prefix
                    fn = prefixFn
                    const = prefixConst
                    trailingNumber = trailing
                }
            }
        }

        if (trailingNumber != null && fn == null && const != null) {
            // Constant followed by digits: implicit multiplication (pi3 → π × 3).
            return Expr.Binary(BinOp.Mul, Expr.Const(const.name, const.value), Expr.Num(trailingNumber))
        }

        return when {
            peek() == Token.LParen && trailingNumber == null && (fn != null || const == null) -> {
                // Function call (or unknown-name call that will be reported later).
                pos++
                val args = ArrayList<Expr>()
                if (peek() != Token.RParen) {
                    args.add(parseAdditive())
                    while (peek() == Token.Comma) {
                        pos++
                        args.add(parseAdditive())
                    }
                }
                expect(Token.RParen)
                if (fn == null) throw ExprParseException("Unknown function \"$name\"")
                if (args.size < fn.minArgs || args.size > fn.maxArgs) {
                    throw ExprParseException("\"$name\" expects ${fn.signature.substringAfter('(')}")
                }
                Expr.Call(fn.name, args)
            }

            fn != null -> {
                // Bare function application: sin 30, sin30, …
                if (fn.minArgs > 1) {
                    throw ExprParseException("\"$name\" requires parentheses: ${fn.signature}")
                }
                val arg: Expr = when {
                    trailingNumber != null -> Expr.Num(trailingNumber)
                    peek() == Token.End || peek() == Token.RParen || peek() == Token.Comma ->
                        throw ExprParseException("Missing argument for \"$name\"")
                    else -> parseUnary()
                }
                Expr.Call(fn.name, listOf(arg))
            }

            const != null -> {
                // Constant: may be followed by parentheses acting as multiplication:
                // e(2) → e × 2 handled by implicit multiplication in the caller.
                Expr.Const(const.name, const.value)
            }

            else -> throw ExprParseException("Unknown name \"$name\"")
        }
    }
}
