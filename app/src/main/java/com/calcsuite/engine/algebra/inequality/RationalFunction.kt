package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.Poly
import com.calcsuite.core.Rat

/**
 * A rational function of one variable: `num(x) / den(x)` with exact rational
 * coefficients.
 *
 * The numerator and denominator are kept exactly as they were built, with **no
 * cancelling**. That is deliberate: in `(x^2 - 4) / (x - 2)` the factor `x - 2`
 * appears in both, and cancelling it would silently put `2` back into the
 * domain even though the original expression is undefined there. Keeping both
 * polynomials means the zeros of the denominator stay excluded.
 */
class RatFun(val num: Poly, val den: Poly) {

    companion object {
        val ONE = RatFun(Poly.ONE, Poly.ONE)
    }

    /** The product of two rational functions. */
    operator fun times(other: RatFun): RatFun = RatFun(num * other.num, den * other.den)

    /** The value at [x], or null when the denominator vanishes there. */
    fun evaluate(x: Rat): Rat? {
        val d = den.evaluate(x)
        if (d.isZero()) return null
        return num.evaluate(x) / d
    }

    /** The sign of the function at [x]; 0 when it is zero, null when undefined. */
    fun signAt(x: Rat): Int? {
        val d = den.evaluate(x)
        if (d.isZero()) return null
        // The sign of a quotient is the sign of its numerator times the sign of
        // its denominator.
        return num.evaluate(x).signum() * d.signum()
    }

    /** True when the function has a pole at [x]. */
    fun isPoleAt(x: Rat): Boolean = den.evaluate(x).isZero()

    override fun toString(): String = "$num / $den"
}

/**
 * Converts an expression into a rational function of [variable], or returns
 * null when that is not possible.
 *
 * Returns null for anything the solver would have to approximate: a logarithm,
 * a square root of a non-constant, a sine. Those forms still get solved, but
 * through the numeric path, and the difference is reported to the user.
 */
fun IExpr.toRationalFunction(variable: String): RatFun? {
    fun constant(v: Rat) = RatFun(Poly.constant(v), Poly.ONE)
    return when (this) {
        is IExpr.Num -> constant(value)
        is IExpr.Var ->
            if (name == variable) RatFun(Poly.monomial(1), Poly.ONE) else null
        is IExpr.Neg -> a.toRationalFunction(variable)?.let { RatFun(it.num * Rat.of(-1L), it.den) }
        is IExpr.Add -> {
            val a = a.toRationalFunction(variable) ?: return null
            val b = b.toRationalFunction(variable) ?: return null
            RatFun(a.num * b.den + b.num * a.den, a.den * b.den)
        }
        is IExpr.Sub -> {
            val a = a.toRationalFunction(variable) ?: return null
            val b = b.toRationalFunction(variable) ?: return null
            RatFun(a.num * b.den - b.num * a.den, a.den * b.den)
        }
        is IExpr.Mul -> {
            val a = a.toRationalFunction(variable) ?: return null
            val b = b.toRationalFunction(variable) ?: return null
            RatFun(a.num * b.num, a.den * b.den)
        }
        is IExpr.Div -> {
            val a = a.toRationalFunction(variable) ?: return null
            val b = b.toRationalFunction(variable) ?: return null
            if (b.num.isZero()) return null   // division by zero
            RatFun(a.num * b.den, a.den * b.num)
        }
        is IExpr.Pow -> {
            val exponent = (b as? IExpr.Num)?.value?.toBigIntegerOrNull() ?: return null
            val base = a
            if (exponent.signum() >= 0) {
                var acc = constant(Rat.ONE)
                repeat(exponent.toInt().coerceAtMost(64)) { acc = acc * (base.toRationalFunction(variable) ?: return null) }
                acc
            } else {
                val positive = exponent.negate().toInt().coerceAtMost(64)
                var acc = constant(Rat.ONE)
                repeat(positive) { acc = acc * (base.toRationalFunction(variable) ?: return null) }
                if (acc.num.isZero()) return null
                RatFun(acc.den, acc.num)
            }
        }
        is IExpr.Call -> null
    }
}

/** The variables that appear in an expression, in the order they were seen. */
fun IExpr.variables(into: MutableList<String> = mutableListOf()): List<String> {
    when (this) {
        is IExpr.Num -> Unit
        is IExpr.Var -> if (name !in into) into.add(name)
        is IExpr.Neg -> a.variables(into)
        is IExpr.Add -> { a.variables(into); b.variables(into) }
        is IExpr.Sub -> { a.variables(into); b.variables(into) }
        is IExpr.Mul -> { a.variables(into); b.variables(into) }
        is IExpr.Div -> { a.variables(into); b.variables(into) }
        is IExpr.Pow -> { a.variables(into); b.variables(into) }
        is IExpr.Call -> args.forEach { it.variables(into) }
    }
    return into
}

/** Every function name that appears in an expression. */
fun IExpr.functions(into: MutableSet<String> = mutableSetOf()): Set<String> {
    when (this) {
        is IExpr.Num, is IExpr.Var -> Unit
        is IExpr.Neg -> a.functions(into)
        is IExpr.Add -> { a.functions(into); b.functions(into) }
        is IExpr.Sub -> { a.functions(into); b.functions(into) }
        is IExpr.Mul -> { a.functions(into); b.functions(into) }
        is IExpr.Div -> { a.functions(into); b.functions(into) }
        is IExpr.Pow -> { a.functions(into); b.functions(into) }
        is IExpr.Call -> { into.add(name); args.forEach { it.functions(into) } }
    }
    return into
}
