package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.Poly
import com.calcsuite.core.PolynomialRoots
import com.calcsuite.core.Rat
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs

/**
 * Solves a separable equation `y′ = f(x)·g(y)`.
 *
 * The two sides are separated, integrated, and the result is turned back into
 * an explicit `y(x)` when the integral of the y side can be inverted. The
 * constant solutions that dividing by `g(y)` would lose are found separately
 * and reported, rather than being dropped.
 */
object SeparableSolver {

    /**
     * How the y side of the separation can be turned back into `y(x)`.
     *
     * The constant in the separated equation, `F(x) = G(y) + C`, is additive,
     * but the explicit form usually multiplies by it — `y = C·e^F(x)` rather
     * than `y = e^(F(x) - C)`. The two constants are related, and the
     * conversion is what [constant] does.
     */
    data class Inversion(
        /** The solution, with `{C}` standing for the constant. */
        val template: String,
        /** The constant to show, given the additive constant. */
        val constant: (Double) -> Double,
        /** The solution as a tree, given the constant to show. */
        val node: ((Double) -> Node)?,
    )

    /** The result of separating and integrating. */
    data class Separation(
        /** ∫ f(x) dx, the x side. */
        val fIntegral: Node,
        /** ∫ 1/g(y) dy, the y side. */
        val gIntegral: Node,
        /** How to turn the separation back into y(x), when it can be done. */
        val inversion: Inversion?,
        /** The constant solutions that dividing by g(y) would lose. */
        val constantSolutions: List<Double>,
    )

    /**
     * Separates and integrates the equation.
     *
     * Returns null when the integrals cannot be produced.
     */
    fun separate(ode: ParsedOde, form: SeparableForm): Separation? {
        val x = ode.independent
        val y = ode.dependent

        // 1/g(y) — the y side of the separated equation.
        val inverseG = Node.Div(Node.Num(1.0), form.b)
        val gIntegral = IntegrationEngine.antiderivative(inverseG, y) ?: return null
        val fIntegral = IntegrationEngine.antiderivative(form.a, x) ?: return null

        return Separation(
            fIntegral = fIntegral,
            gIntegral = gIntegral,
            inversion = invert(gIntegral, fIntegral, x, y),
            constantSolutions = constantSolutionsOf(form.b, y),
        )
    }

    /**
     * The zeros of g(y), which are the constant solutions the separation loses.
     *
     * Only computed when g is a polynomial in y, which is the case that
     * matters in practice.
     */
    fun constantSolutionsOf(b: Node, y: String): List<Double> {
        val poly = toPoly(b, y) ?: return emptyList()
        return PolynomialRoots.realRoots(poly)
            .filter { it.value.isFinite() }
            .map { it.value }
            .distinctBy { Math.round(it * 1e6) }
            .sorted()
    }

    // ------------------------------------------------------- polynomial work

    /** A tree expanded into its powers of one variable plus a constant part. */
    private class Expansion(val powers: Map<Int, Rat>, val constant: Rat?)

    /**
     * Turns a tree in [variable] into a polynomial over the rationals, when it is
     * one. Returns null when the tree is not a polynomial in that variable.
     */
    fun toPoly(node: Node, variable: String): Poly? {
        val expansion = expand(node, variable) ?: return null
        val coefficients = mutableListOf<Rat>()
        for ((power, value) in expansion.powers) {
            while (coefficients.size <= power) coefficients.add(Rat.ZERO)
            coefficients[power] = coefficients[power] + value
        }
        expansion.constant?.let {
            if (coefficients.isEmpty()) coefficients.add(Rat.ZERO)
            coefficients[0] = coefficients[0] + it
        }
        return Poly.of(coefficients)
    }

    /**
     * Expands a tree into its powers of [variable] and its constant part.
     *
     * Returns null when the tree is not a polynomial in that variable.
     */
    private fun expand(node: Node, variable: String): Expansion? {
        return when (node) {
        is Node.Num -> Expansion(mapOf(0 to Rat.ofDecimal(node.value.toString())), null)
        is Node.Neg -> {
            val inner = expand(node.a, variable) ?: return null
            Expansion(
                inner.powers.mapValues { (_, value) -> -value },
                inner.constant?.let { -it },
            )
        }
        is Node.Add -> {
            val a = expand(node.a, variable) ?: return null
            val b = expand(node.b, variable) ?: return null
            addExpansions(a, b)
        }
        is Node.Sub -> {
            val a = expand(node.a, variable) ?: return null
            val b = expand(node.b, variable) ?: return null
            addExpansions(a, negate(b))
        }
        is Node.Mul -> {
            val a = expand(node.a, variable) ?: return null
            val b = expand(node.b, variable) ?: return null
            multiplyExpansions(a, b)
        }
        is Node.Div -> {
            val a = expand(node.a, variable) ?: return null
            val b = expand(node.b, variable) ?: return null
            val denominator = b.constant
            if (denominator == null || b.powers.isNotEmpty()) return null
            val scaled = a.powers.mapValues { (_, value) -> value / denominator }
            Expansion(scaled, a.constant?.let { it / denominator })
        }
        is Node.Pow -> {
            val exponent = (node.b as? Node.Num)?.value?.toInt() ?: return null
            if (exponent < 0 || exponent > 32) return null
            val base = expand(node.a, variable) ?: return null
            // Anything to the power of zero is one, which is the constant term.
            if (exponent == 0) return Expansion(mapOf(0 to Rat.ONE), null)
            var acc = Expansion(mapOf(0 to Rat.ONE), null)
            repeat(exponent) {
                acc = multiplyExpansions(acc, base)
            }
            acc
        }
        is Node.Var ->
            if (node.name.equals(variable, ignoreCase = true)) {
                Expansion(mapOf(1 to Rat.ONE), null)
            } else {
                // A different variable cannot be a coefficient of a polynomial
                // in this one.
                null
            }
        else -> null
        }
    }

    private fun addExpansions(a: Expansion, b: Expansion): Expansion {
        val powers = a.powers.toMutableMap()
        for ((power, value) in b.powers) {
            powers[power] = (powers[power] ?: Rat.ZERO) + value
        }
        val constant = when {
            a.constant != null && b.constant != null -> a.constant + b.constant
            a.constant != null -> a.constant
            else -> b.constant
        }
        return Expansion(powers, constant)
    }

    private fun negate(a: Expansion): Expansion = Expansion(
        a.powers.mapValues { (_, value) -> -value },
        a.constant?.let { -it },
    )

    private fun multiplyExpansions(a: Expansion, b: Expansion): Expansion {
        val powers = mutableMapOf<Int, Rat>()
        for ((pa, va) in a.powers) {
            for ((pb, vb) in b.powers) {
                powers[pa + pb] = (powers[pa + pb] ?: Rat.ZERO) + va * vb
            }
        }
        val constant = if (a.constant != null && b.constant != null) a.constant * b.constant else null
        return Expansion(powers, constant)
    }

    // --------------------------------------------------------- inversion

    /**
     * Inverts the y side of the separation, when it can be inverted.
     *
     * Three shapes are recognised: `c·ln|y|`, which gives `y = C·e^(f(x))`;
     * `y^m/m`, which gives `y = (m·f(x) + C)^(1/m)`; and `−cos(y)`. Anything
     * else is left in the implicit form, which is a complete answer on its own.
     */
    fun invert(gIntegral: Node, fIntegral: Node, x: String, y: String): Inversion? {
        val log = matchLog(gIntegral, y)
        if (log != null) {
            val exponent = if (log == 1.0) {
                NodePrinter.print(fIntegral)
            } else {
                "(${NodePrinter.print(fIntegral)} ÷ $log)"
            }
            val exponential = Node.Fun("exp", listOf(fIntegral))
            return Inversion(
                template = "y = {C}·e^($exponent)",
                // y = ±e^(F − C) means the multiplicative constant is e^(−C).
                constant = { kotlin.math.exp(-it) },
                node = { c -> Node.Mul(Node.Num(c), exponential) },
            )
        }
        val power = matchPowerOverM(gIntegral, y)
        if (power != null) {
            val (exponent, denominator) = power
            return Inversion(
                template = "y = ($denominator·(${NodePrinter.print(fIntegral)}) + {C})^(1/$exponent)",
                constant = { it },
                node = { c ->
                    Node.Pow(
                        Node.Add(
                            Node.Mul(Node.Num(denominator.toDouble()), fIntegral),
                            Node.Num(c),
                        ),
                        Node.Num(1.0 / exponent),
                    )
                },
            )
        }
        val negatedCos = matchNegatedCos(gIntegral, y)
        if (negatedCos != null) {
            return Inversion(
                template = "y = arccos($negatedCos + {C})",
                constant = { it },
                node = null,
            )
        }
        return null
    }

    /** Recognises `c·ln|y|` and returns the constant, or null. */
    private fun matchLog(node: Node, y: String): Double? {
        if (node is Node.Fun && node.name == "ln" && node.args.size == 1) {
            val argument = node.args[0]
            if (argument is Node.Var && argument.name.equals(y, ignoreCase = true)) return 1.0
            return null
        }
        if (node is Node.Mul) {
            val factor = node.a as? Node.Num
            val other = node.b
            if (factor != null && other is Node.Fun && other.name == "ln" && other.args.size == 1) {
                val argument = other.args[0]
                if (argument is Node.Var && argument.name.equals(y, ignoreCase = true)) {
                    return factor.value
                }
            }
        }
        return null
    }

    /** Recognises `y^m / m`, returning the exponent and the denominator. */
    private fun matchPowerOverM(node: Node, y: String): Pair<Int, Int>? {
        if (node !is Node.Div) return null
        val numerator = node.a
        val denominator = node.b as? Node.Num ?: return null
        val m = denominator.value.toInt()
        if (m == 0 || denominator.value != m.toDouble()) return null
        if (numerator !is Node.Pow) return null
        val base = numerator.a
        val exponent = (numerator.b as? Node.Num)?.value?.toInt() ?: return null
        if (base !is Node.Var || !base.name.equals(y, ignoreCase = true)) return null
        if (exponent != 1 - m) return null
        return exponent to m
    }

    /** Recognises `−cos(y)`. */
    private fun matchNegatedCos(node: Node, y: String): String? {
        val negated = node is Node.Neg
        val cos = if (negated) node.a else node
        if (cos !is Node.Fun || cos.name != "cos" || cos.args.size != 1) return null
        val argument = cos.args[0]
        if (argument !is Node.Var || !argument.name.equals(y, ignoreCase = true)) return null
        val inner = NodePrinter.print(cos)
        return if (negated) "−($inner)" else "−$inner"
    }

    /**
     * The value of the constant that satisfies an initial condition.
     */
    fun constantFromCondition(
        particular: Node,
        homogeneous: Node,
        at: Double,
        value: Double,
        x: String,
    ): Double? {
        val env = mapOf(x to at)
        val particularValue = Evaluator.eval(particular, env).valueOrNull() ?: return null
        val homogeneousValue = Evaluator.eval(homogeneous, env).valueOrNull() ?: return null
        if (abs(homogeneousValue) < 1e-14) return null
        return (value - particularValue) / homogeneousValue
    }
}
