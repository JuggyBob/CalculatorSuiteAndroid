package com.calcsuite.expr

import java.math.BigDecimal

/** Angle unit used by trigonometric functions. */
enum class AngleMode { DEG, RAD, GRAD }

/** Operator tokens of the expression language. */
enum class BinOp(val symbol: String) {
    Add("+"), Sub("−"), Mul("×"), Div("÷"), Pow("^"), Mod("mod")
}

enum class UnOp(val symbol: String) { Neg("−"), Pos("+") }

enum class PostOp(val symbol: String) { Factorial("!"), Percent("%") }

/** Abstract syntax tree produced by [ExpressionParser]. */
sealed interface Expr {
    data class Num(val value: BigDecimal) : Expr
    data class Const(val name: String, val value: BigDecimal) : Expr
    data class Unary(val op: UnOp, val operand: Expr) : Expr
    data class Binary(val op: BinOp, val left: Expr, val right: Expr) : Expr
    data class Postfix(val op: PostOp, val operand: Expr) : Expr
    data class Call(val name: String, val args: List<Expr>) : Expr
}

/** Definition of a named function in the expression language. */
data class FunctionDef(
    val name: String,
    val minArgs: Int,
    val maxArgs: Int,
    /** Arity shown in help text. */
    val signature: String,
    val description: String,
)

/** Definition of a named constant. */
data class ConstantDef(val name: String, val value: BigDecimal, val description: String)

object Consts {
    val PI: BigDecimal = BigDecimal("3.14159265358979323846264338327950288")
    val E: BigDecimal = BigDecimal("2.71828182845904523536028747135266250")
    val TAU: BigDecimal = PI.multiply(BigDecimal("2"))
    val PHI: BigDecimal = BigDecimal("1.61803398874989484820458683436563812")

    val ALL: List<ConstantDef> = listOf(
        ConstantDef("pi", PI, "π ≈ 3.14159"),
        ConstantDef("π", PI, "π ≈ 3.14159"),
        ConstantDef("e", E, "e ≈ 2.71828"),
        ConstantDef("tau", TAU, "τ = 2π"),
        ConstantDef("τ", TAU, "τ = 2π"),
        ConstantDef("phi", PHI, "Golden ratio φ"),
        ConstantDef("φ", PHI, "Golden ratio φ"),
    )

    fun find(name: String): ConstantDef? = ALL.firstOrNull { it.name == name.lowercase() }
}

object Functions {
    val ALL: List<FunctionDef> = listOf(
        FunctionDef("sin", 1, 1, "sin(x)", "Sine"),
        FunctionDef("cos", 1, 1, "cos(x)", "Cosine"),
        FunctionDef("tan", 1, 1, "tan(x)", "Tangent"),
        FunctionDef("csc", 1, 1, "csc(x)", "Cosecant (1/sin)"),
        FunctionDef("sec", 1, 1, "sec(x)", "Secant (1/cos)"),
        FunctionDef("cot", 1, 1, "cot(x)", "Cotangent (1/tan)"),
        FunctionDef("asin", 1, 1, "asin(x)", "Arcsine (result in current angle unit)"),
        FunctionDef("acos", 1, 1, "acos(x)", "Arccosine"),
        FunctionDef("atan", 1, 1, "atan(x)", "Arctangent"),
        FunctionDef("atan2", 2, 2, "atan2(y, x)", "Angle of the point (x, y)"),
        FunctionDef("sinh", 1, 1, "sinh(x)", "Hyperbolic sine"),
        FunctionDef("cosh", 1, 1, "cosh(x)", "Hyperbolic cosine"),
        FunctionDef("tanh", 1, 1, "tanh(x)", "Hyperbolic tangent"),
        FunctionDef("asinh", 1, 1, "asinh(x)", "Inverse hyperbolic sine"),
        FunctionDef("acosh", 1, 1, "acosh(x)", "Inverse hyperbolic cosine"),
        FunctionDef("atanh", 1, 1, "atanh(x)", "Inverse hyperbolic tangent"),
        FunctionDef("ln", 1, 1, "ln(x)", "Natural logarithm"),
        FunctionDef("log", 1, 2, "log(x[, base])", "Base-10 logarithm, or log(x, base)"),
        FunctionDef("log2", 1, 1, "log2(x)", "Base-2 logarithm"),
        FunctionDef("exp", 1, 1, "exp(x)", "e raised to x"),
        FunctionDef("sqrt", 1, 1, "sqrt(x)", "Square root"),
        FunctionDef("cbrt", 1, 1, "cbrt(x)", "Cube root"),
        FunctionDef("root", 2, 2, "root(x, n)", "n-th root of x"),
        FunctionDef("abs", 1, 1, "abs(x)", "Absolute value"),
        FunctionDef("sign", 1, 1, "sign(x)", "−1, 0 or 1"),
        FunctionDef("floor", 1, 1, "floor(x)", "Round down"),
        FunctionDef("ceil", 1, 1, "ceil(x)", "Round up"),
        FunctionDef("round", 1, 2, "round(x[, digits])", "Round half away from zero"),
        FunctionDef("fact", 1, 1, "fact(n)", "Factorial of a non-negative integer"),
        FunctionDef("ncr", 2, 2, "nCr(n, r)", "Combinations (exact integer)"),
        FunctionDef("npr", 2, 2, "nPr(n, r)", "Permutations (exact integer)"),
        FunctionDef("gcd", 2, 2, "gcd(a, b)", "Greatest common divisor"),
        FunctionDef("lcm", 2, 2, "lcm(a, b)", "Least common multiple"),
        FunctionDef("min", 1, 8, "min(a, b, …)", "Smallest value"),
        FunctionDef("max", 1, 8, "max(a, b, …)", "Largest value"),
        FunctionDef("hypot", 2, 2, "hypot(x, y)", "√(x² + y²)"),
        FunctionDef("deg", 1, 1, "deg(x)", "Convert radians to degrees"),
        FunctionDef("rad", 1, 1, "rad(x)", "Convert degrees to radians"),
    )

    private val byName: Map<String, FunctionDef> = ALL.associateBy { it.name }

    fun find(name: String): FunctionDef? = byName[name.lowercase()]

    /**
     * The subset that has a dedicated key on the scientific keypad, paired with
     * the label printed on that key. Used by the help panel so the documented
     * list matches what the user can actually press.
     */
    val KEYPAD: Map<String, String> = mapOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "asin" to "sin⁻¹", "acos" to "cos⁻¹", "atan" to "tan⁻¹",
        "ln" to "ln", "exp" to "eˣ", "log" to "log", "log10" to "10ˣ",
        "sqrt" to "√", "cbrt" to "∛",
    )

    /** Function names reachable by typing but with no dedicated keypad key. */
    val TYPED_ONLY: Set<String> = ALL.map { it.name }.toSet() - KEYPAD.keys

    /**
     * Operators and postfix/unary syntax accepted by the parser. These are part
     * of the calculator but are not functions, so they are listed separately in
     * the help panel (previously `mod` was documented nowhere).
     */
    val OPERATORS: List<Triple<String, String, String>> = listOf(
        Triple("+ − × ÷", "a + b, a − b, a × b, a ÷ b", "Arithmetic"),
        Triple("^", "a^b", "Power, right-associative: 2^3^2 = 2^9"),
        Triple("mod", "a mod b", "Remainder of the integer division (mod key)"),
        Triple("±", "(±)5 or 5(±)", "Toggle the sign of the number at the cursor"),
        Triple("!", "5!", "Postfix factorial, exact for integers up to 10000!"),
        Triple("%", "200 + 10%", "Percent of the left term: 200 + 10% = 220"),
        Triple("×", "2π, 3(4+5), 2sin(30)", "Implicit multiplication"),
    )
}
