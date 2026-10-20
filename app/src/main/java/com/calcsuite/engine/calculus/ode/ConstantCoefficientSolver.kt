package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.formatNumber
import java.math.BigInteger
import com.calcsuite.core.Poly
import com.calcsuite.engine.advanced.ComplexNum
import com.calcsuite.engine.advanced.PolyRoots
import com.calcsuite.core.Rat
import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A complex number, used for the roots of the characteristic equation. */
data class Complex(val re: Double, val im: Double) {
    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun div(o: Complex): Complex {
        val d = o.re * o.re + o.im * o.im
        if (d == 0.0) return Complex(Double.NaN, Double.NaN)
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }
    fun abs(): Double = sqrt(re * re + im * im)
    override fun toString(): String =
        if (im == 0.0) formatNumber(re) else "${formatNumber(re)} ${if (im < 0) "−" else "+"} ${formatNumber(abs(im))}i"
}

/** One root of the characteristic equation. */
data class CharacteristicRoot(
    val real: Double,
    val imaginary: Double,
    val multiplicity: Int,
) {
    val isReal: Boolean get() = imaginary == 0.0
}

/**
 * Solves a linear equation with constant coefficients,
 *
 *     a_n·y⁽ⁿ⁾ + … + a_1·y′ + a_0·y = F(x)
 *
 * The homogeneous part comes from the characteristic equation
 * `a_n·rⁿ + … + a_0 = 0`: a real root r gives `e^(r·x)`, repeated roots give
 * `x·e^(r·x)` and so on, and a complex pair a ± bi gives `e^(a·x)·cos(b·x)` and
 * `e^(a·x)·sin(b·x)`.
 *
 * The particular part comes from undetermined coefficients: for a polynomial
 * forcing term the trial is a polynomial, and for an exponential forcing term
 * it is a multiple of that exponential.
 */
object ConstantCoefficientSolver {

    /**
     * A particular solution, as a tree and as exact text.
     *
     * The text is built from the rational coefficients, so a coefficient of one
     * half is written `1/2` rather than a rounded decimal.
     */
    data class Particular(val node: Node, val text: String)

    /** The pieces of the solution. */
    data class Result(
        /** The characteristic equation, as a polynomial in r. */
        val characteristic: Poly,
        /** The roots of the characteristic equation. */
        val roots: List<CharacteristicRoot>,
        /** The basis functions of the homogeneous solution. */
        val basis: List<Node>,
        /** The homogeneous solution, rendered. */
        val homogeneous: String,
        /** The particular solution, when one could be produced. */
        val particular: Node?,
        /** The particular solution, rendered. */
        val particularText: String?,
        /** True when the particular solution is only available in quadrature. */
        val quadratureOnly: Boolean,
    )

    /**
     * Solves the equation, or returns null when the shape is not one this
     * solver handles.
     */
    fun solve(ode: ParsedOde): Result? {
        val order = ode.order
        val x = ode.independent
        val coefficients = ode.coefficients

        // The characteristic equation, normalised so the leading term is 1.
        val leading = coefficients[order] ?: return null
        val leadingValue = OdeParser.numericValue(leading) ?: return null
        if (leadingValue == 0.0) return null
        val characteristicCoefficients = ArrayList<Rat>(order + 1)
        for (k in 0..order) {
            val c: Node = coefficients[k] ?: Node.Num(0.0)
            val value = OdeParser.numericValue(c) ?: return null
            characteristicCoefficients.add(
                Rat.ofDecimal(value.toString()) / Rat.ofDecimal(leadingValue.toString()),
            )
        }
        val characteristic = Poly.of(characteristicCoefficients)

        val roots = pairConjugates(characteristicRoots(characteristic))
        if (roots.isEmpty()) return null

        val basis = homogeneousBasis(roots, x)
        val homogeneous = renderHomogeneous(roots)

        val particular = particularSolution(ode, characteristic, roots, x)
        return Result(
            characteristic = characteristic,
            roots = roots,
            basis = basis,
            homogeneous = homogeneous,
            particular = particular?.node,
            particularText = particular?.text,
            quadratureOnly = particular == null,
        )
    }

    // ------------------------------------------------------------- the roots

    /**
     * The roots of the characteristic polynomial.
     *
     * Rational roots are found exactly and divided out, which also gives their
     * multiplicity. Whatever is left is a quadratic, solved with the formula,
     * or a higher-degree remainder whose roots are found numerically — with
     * the complex ones included.
     */
    /**
     * The roots of the characteristic polynomial, with their multiplicities.
     *
     * The roots themselves come from the shared polynomial engine, so the
     * exact rational ones, the quadratic formula and the numerical ones are
     * found the same way here as anywhere else in the app.
     */
    fun characteristicRoots(polynomial: Poly): List<CharacteristicRoot> {
        val roots = PolyRoots.complexRoots(polynomial)
        // A repeated root is one entry with a multiplicity, rather than
        // several entries, because the homogeneous solution needs a different
        // factor for each one. A conjugate pair stays as two entries here and
        // is paired up afterwards.
        val grouped = mutableListOf<Pair<ComplexNum, Int>>()
        for (root in roots) {
            val existing = grouped.indexOfFirst { (other, _) ->
                abs(other.re - root.re) < 1e-9 && abs(other.im - root.im) < 1e-9
            }
            if (existing >= 0) {
                grouped[existing] = grouped[existing].first to (grouped[existing].second + 1)
            } else {
                grouped.add(root to 1)
            }
        }
        return grouped.map { (root, count) ->
            CharacteristicRoot(root.re, root.im, count)
        }
    }

    fun pairConjugates(roots: List<CharacteristicRoot>): List<CharacteristicRoot> {
        val used = BooleanArray(roots.size)
        val paired = mutableListOf<CharacteristicRoot>()
        for (i in roots.indices) {
            if (used[i]) continue
            val root = roots[i]
            if (root.isReal) {
                used[i] = true
                paired.add(root)
                continue
            }
            var partner = -1
            for (j in roots.indices) {
                if (j == i || used[j]) continue
                val other = roots[j]
                if (!other.isReal &&
                    abs(other.real - root.real) < 1e-6 &&
                    abs(other.imaginary + root.imaginary) < 1e-6
                ) {
                    partner = j
                    break
                }
            }
            used[i] = true
            if (partner >= 0) {
                used[partner] = true
                paired.add(root.copy(multiplicity = maxOf(root.multiplicity, roots[partner].multiplicity)))
            } else {
                paired.add(root)
            }
        }
        return paired
    }

    /**
     * The basis functions of the homogeneous solution.
     *
     * A real root r of multiplicity m gives `x^k·e^(r·x)` for k = 0..m−1, and
     * a complex pair a ± bi gives `e^(a·x)·cos(b·x)` and `e^(a·x)·sin(b·x)`.
     */
    fun homogeneousBasis(roots: List<CharacteristicRoot>, x: String): List<Node> {
        val basis = mutableListOf<Node>()
        for (root in roots) {
            if (root.isReal) {
                for (k in 0 until root.multiplicity) {
                    val exponential = Node.Fun("exp", listOf(Node.Mul(Node.Num(root.real), Node.Var(x))))
                    basis.add(
                        if (k == 0) exponential
                        else Node.Mul(Node.Pow(Node.Var(x), Node.Num(k.toDouble())), exponential),
                    )
                }
            } else {
                val exponential = Node.Fun("exp", listOf(Node.Mul(Node.Num(root.real), Node.Var(x))))
                val angle = Node.Mul(Node.Num(root.imaginary), Node.Var(x))
                basis.add(Node.Mul(exponential, Node.Fun("cos", listOf(angle))))
                basis.add(Node.Mul(exponential, Node.Fun("sin", listOf(angle))))
            }
        }
        return basis
    }

    /** The homogeneous solution, rendered. */
    /** The homogeneous solution, rendered. */
    fun renderHomogeneous(roots: List<CharacteristicRoot>): String {
        val terms = mutableListOf<String>()
        for (root in roots) {
            if (root.isReal) {
                for (k in 0 until root.multiplicity) {
                    val exponential = exponentialText(root.real)
                    terms.add(if (k == 0) exponential else "${powerText(k)}\u00B7$exponential")
                }
            } else {
                val exponential = exponentialText(root.real)
                val angle = angleText(root.imaginary)
                val prefix = if (root.real == 0.0) "" else "$exponential\u00B7"
                terms.add("${prefix}cos($angle)")
                terms.add("${prefix}sin($angle)")
            }
        }
        return terms.joinToString(" + ")
    }

    /** `e^(2x)`, `e^(x)` or `e^(-x)`, written the way a person would. */
    fun exponentialText(rate: Double): String {
        if (rate == 0.0) return "1"
        if (rate == 1.0) return "e^(x)"
        if (rate == -1.0) return "e^(-x)"
        val sign = if (rate < 0) "\u2212" else ""
        return "e^($sign${formatNumber(abs(rate))}x)"
    }

    /** `x`, `x^2` and so on. */
    fun powerText(power: Int): String = if (power == 1) "x" else "x^$power"

    /** `x`, or `2x` when the rate is not one. */
    fun angleText(rate: Double): String {
        if (rate == 1.0) return "x"
        if (rate == -1.0) return "\u2212x"
        val sign = if (rate < 0) "\u2212" else ""
        return "$sign${formatNumber(abs(rate))}x"
    }

    /**
     * A polynomial written the way a person would: `x^2 - 3x + 2` rather than
     * `1x^2 + -3x^1 + 2`.
     */
    fun renderPolynomial(polynomial: Poly): String {
        val terms = mutableListOf<String>()
        for ((power, coefficient) in polynomial.coeffs.withIndex().reversed()) {
            if (coefficient.isZero()) continue
            val value = coefficient.toDouble()
            val negative = value < 0
            val magnitude = abs(value)
            val body = when {
                power == 0 -> formatNumber(magnitude)
                power == 1 && magnitude == 1.0 -> "x"
                power == 1 -> "${formatNumber(magnitude)}x"
                magnitude == 1.0 -> "x^$power"
                else -> "${formatNumber(magnitude)}x^$power"
            }
            if (terms.isEmpty()) {
                terms.add((if (negative) "\u2212" else "") + body)
            } else {
                terms.add((if (negative) " \u2212 " else " + ") + body)
            }
        }
        return if (terms.isEmpty()) "0" else terms.joinToString("")
    }

    // ---------------------------------------------------- the particular part

    /**
     * A particular solution, by undetermined coefficients.
     *
     * A polynomial forcing term is handled by trying a polynomial trial and
     * solving for its coefficients exactly; an exponential forcing term is
     * handled in closed form.
     */
    fun particularSolution(
        ode: ParsedOde,
        characteristic: Poly,
        roots: List<CharacteristicRoot>,
        x: String,
    ): Particular? {
        val rhs = ode.rhs
        if (OdeParser.isZero(rhs)) return null
        exponentialParticular(ode, characteristic, roots, x)?.let { return it }
        return polynomialParticular(ode, x)
    }

    /**
     * The particular solution for a polynomial forcing term.
     *
     * The trial is a polynomial whose degree is raised by the multiplicity of
     * zero as a root of the characteristic equation, which is what the method
     * requires when the forcing resonates with the homogeneous solution.
     */
    private fun polynomialParticular(ode: ParsedOde, x: String): Particular? {
        val poly = SeparableSolver.toPoly(ode.rhs, x) ?: return null
        if (poly.isZero() || poly.degree < 0) return null
        val order = ode.order
        val operator = (0..order).map { k ->
            val c = ode.coefficient(k)
            val value = if (c == null) 0.0 else (OdeParser.numericValue(c) ?: return null)
            value
        }
        val degree = poly.degree

        // The multiplicity of zero as a characteristic root sets how far the
        // trial has to be raised.
        val shift = (0..order).count { k ->
            // p(r) = Σ a_k r^k, so the multiplicity of zero is how many of the
            // lowest coefficients vanish.
            k == 0 && operator[0] == 0.0
        }
        var extra = 0
        for (k in 0..order) {
            if (operator[k] == 0.0) extra++ else break
        }
        val trialDegree = degree + extra

        // The columns of the system: applying the operator to each basis term.
        val matrix = ArrayList<List<Rat>>()
        val rhs = ArrayList<Rat>()
        for (i in 0..trialDegree) {
            val row = MutableList<Rat>(trialDegree + 1) { Rat.ZERO }
            for (k in 0..trialDegree) {
                val applied = applyOperator(operator, x, k)
                    ?: return null
                val appliedPoly = SeparableSolver.toPoly(applied, x) ?: return null
                row[k] = appliedPoly.coeffs.getOrElse(i) { Rat.ZERO }
            }
            matrix.add(row)
            rhs.add(poly.coeffs.getOrElse(i) { Rat.ZERO })
        }

        val solution = LinearSolve.solveExact(matrix, rhs) ?: return null
        return Particular(
            node = LinearFirstOrderSolver.polynomialNode(solution, x),
            text = formatRationalPolynomial(solution, x),
        )
    }

    /**
     * A polynomial written with exact rational coefficients.
     *
     * `1/2` stays `1/2` rather than becoming a rounded decimal, which is what
     * makes an undetermined-coefficient answer worth reading.
     */
    fun formatRationalPolynomial(coefficients: List<Rat>, x: String): String {
        val terms = mutableListOf<String>()
        for ((power, coefficient) in coefficients.withIndex().reversed()) {
            if (coefficient.isZero()) continue
            val value = coefficient.display()
            val magnitude = if (coefficient.signum() < 0) value.substring(1) else value
            val body = when {
                power == 0 -> magnitude
                power == 1 && magnitude == "1" -> x
                power == 1 -> "$magnitude\u00B7$x"
                magnitude == "1" -> "$x^$power"
                else -> "$magnitude\u00B7$x^$power"
            }
            if (terms.isEmpty()) {
                terms.add((if (coefficient.signum() < 0) "\u2212" else "") + body)
            } else {
                terms.add((if (coefficient.signum() < 0) " \u2212 " else " + ") + body)
            }
        }
        return if (terms.isEmpty()) "0" else terms.joinToString("")
    }

    /**
     * Applies the differential operator to `x^k`, using the engine's own
     * differentiation so the result is exact.
     */
    private fun applyOperator(operator: List<Double>, x: String, power: Int): Node? {
        var term: Node = Node.Pow(Node.Var(x), Node.Num(power.toDouble()))
        var acc: Node = Node.Num(0.0)
        for ((order, coefficient) in operator.withIndex()) {
            if (coefficient == 0.0) continue
            val weighted = Node.Mul(Node.Num(coefficient), term)
            acc = if (acc is Node.Num && acc.value == 0.0) weighted else Node.Add(acc, weighted)
            term = DifferentiationEngine.differentiate(term, x).valueOrNull() ?: return null
        }
        return acc
    }

    /**
     * The particular solution for an exponential forcing term, `c·e^(b·x)`.
     *
     * When the exponential is a root of the characteristic equation the trial
     * has to be multiplied by a power of x, and the amplitude follows from the
     * corresponding derivative of the characteristic polynomial.
     */
    private fun exponentialParticular(
        ode: ParsedOde,
        characteristic: Poly,
        roots: List<CharacteristicRoot>,
        x: String,
    ): Particular? {
        val rhs = ode.rhs
        var coefficient: Double? = null
        var exponent: Double? = null
        val exponential = when {
            rhs is Node.Mul && rhs.a is Node.Num && rhs.b is Node.Fun && rhs.b.name == "exp" -> {
                coefficient = rhs.a.value
                exponentOf(rhs.b, x)
            }
            rhs is Node.Fun && rhs.name == "exp" -> {
                coefficient = 1.0
                exponentOf(rhs, x)
            }
            else -> null
        } ?: return null
        val b = exponent ?: return null
        val c = coefficient ?: return null

        // How often is b a root of the characteristic equation?
        var multiplicity = 0
        for (root in roots) {
            if (root.isReal && abs(root.real - b) < 1e-9) {
                multiplicity = root.multiplicity
            }
        }

        // p^(s)(b) — the s-th derivative of the characteristic polynomial.
        var p: Node = characteristicNode(characteristic, x)
        repeat(multiplicity) {
            p = DifferentiationEngine.differentiate(p, x).valueOrNull() ?: return null
        }
        val value = Evaluator.eval(p, mapOf(x to b)).valueOrNull() ?: return null
        if (abs(value) < 1e-12) return null
        val amplitude = c / value

        val exponentialNode = Node.Fun("exp", listOf(Node.Mul(Node.Num(b), Node.Var(x))))
        val node = if (multiplicity == 0) {
            Node.Mul(Node.Num(amplitude), exponentialNode)
        } else {
            Node.Mul(
                Node.Mul(Node.Num(amplitude), Node.Pow(Node.Var(x), Node.Num(multiplicity.toDouble()))),
                exponentialNode,
            )
        }
        val power = if (multiplicity == 0) "" else if (multiplicity == 1) "$x\u00B7" else "$x^$multiplicity\u00B7"
        return Particular(
            node = node,
            text = "${formatNumber(amplitude)}\u00B7$power" + exponentialText(b),
        )
    }

    /** The exponent b of `e^(b·x)`, when the tree has that shape. */
    private fun exponentOf(node: Node.Fun, x: String): Double? {
        val argument = node.args.firstOrNull() ?: return null
        if (argument is Node.Mul && argument.b is Node.Var && argument.b.name.equals(x, ignoreCase = true)) {
            return (argument.a as? Node.Num)?.value
        }
        if (argument is Node.Var && argument.name.equals(x, ignoreCase = true)) return 1.0
        return null
    }

    /** The characteristic polynomial as a tree in the given variable. */
    fun characteristicNode(polynomial: Poly, variable: String): Node {
        var node: Node = Node.Num(0.0)
        for ((power, coefficient) in polynomial.coeffs.withIndex()) {
            if (coefficient.isZero()) continue
            val term = if (power == 0) {
                Node.Num(coefficient.toDouble())
            } else {
                Node.Mul(
                    Node.Num(coefficient.toDouble()),
                    Node.Pow(Node.Var(variable), Node.Num(power.toDouble())),
                )
            }
            node = if (node is Node.Num && node.value == 0.0) term else Node.Add(node, term)
        }
        return node
    }
}
