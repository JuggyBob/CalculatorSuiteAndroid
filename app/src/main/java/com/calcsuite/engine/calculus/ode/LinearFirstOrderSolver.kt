package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.Rat
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs

/**
 * Solves a first-order linear equation `y′ + P(x)·y = Q(x)`.
 *
 * The method is the integrating factor: multiply the equation by
 * `μ(x) = e^(∫P dx)`, the left-hand side becomes the derivative of `μ·y`,
 * and integrating gives the answer.
 *
 * A particular solution is produced in closed form when the equation allows
 * it — a constant coefficient with a polynomial or exponential forcing term.
 * Otherwise the solution is given in quadrature, which is still exact, and
 * the tool says that the remaining integral is not elementary.
 */
object LinearFirstOrderSolver {

    /** The pieces of the solution. */
    data class Result(
        /** P(x), the coefficient of y. */
        val p: Node,
        /** Q(x), the forcing term. */
        val q: Node,
        /** ∫P dx, the exponent of the integrating factor. */
        val pIntegral: Node,
        /** The integrating factor e^(∫P dx). */
        val integratingFactor: Node,
        /** The homogeneous solution C·e^(−∫P dx). */
        val homogeneous: Node,
        /** The particular solution, when it could be produced. */
        val particular: Node?,
        /** True when the particular solution is only given in quadrature. */
        val quadratureOnly: Boolean,
    )

    /**
     * Works out P, Q and the solution.
     *
     * Returns null when the equation is not first-order linear.
     */
    fun solve(ode: ParsedOde): Result? {
        if (ode.order != 1) return null
        val a0 = ode.coefficient(0) ?: return null
        val a1 = ode.coefficient(1) ?: return null
        val x = ode.independent
        val y = ode.dependent

        // P(x) = a0 / a1, Q(x) = F / a1
        val p = if (OdeParser.isConstantOne(a1)) a0 else Node.Div(a0, a1)
        val q = if (OdeParser.isConstantOne(a1)) ode.rhs else Node.Div(ode.rhs, a1)

        val pIntegral = normaliseIntegral(IntegrationEngine.antiderivative(p, x) ?: return null)
        val integratingFactor = Node.Fun("exp", listOf(pIntegral))
        val homogeneous = Node.Fun("exp", listOf(Node.Neg(pIntegral)))

        val particular = particularSolution(p, q, x)
        return Result(
            p = p,
            q = q,
            pIntegral = pIntegral,
            integratingFactor = integratingFactor,
            homogeneous = homogeneous,
            particular = particular,
            quadratureOnly = particular == null,
        )
    }

    /**
     * Tidies an antiderivative, so that a constant coefficient gives `x`
     * rather than `1x`.
     */
    private fun normaliseIntegral(node: Node): Node = when {
        node is Node.Mul && node.a is Node.Num && node.b is Node.Var -> {
            if (node.a.value == 1.0) node.b else node
        }
        else -> node
    }

    /**
     * A particular solution for `y′ + P·y = Q`.
     *
     * When P is a constant `a` and Q is a polynomial, the particular
     * solution is the polynomial R that satisfies `R′ + a·R = Q`, which is
     * found by solving a triangular system exactly. When Q is a single
     * exponential the answer is a single multiple of it.
     *
     * Returns null when the case is not one of those, in which case the
     * solution is only available in quadrature.
     */
    fun particularSolution(p: Node, q: Node, x: String): Node? {
        val a = OdeParser.numberValue(p) ?: return null
        if (a == 0.0) {
            // P = 0, so the equation is y' = Q and the particular solution is
            // simply the integral of Q.
            return IntegrationEngine.antiderivative(q, x)
        }
        polynomialParticular(a, q, x)?.let { return it }
        exponentialParticular(a, q, x)?.let { return it }
        return null
    }

    /**
     * The polynomial R with `R′ + a·R = Q`, for a polynomial Q.
     *
     * Writing `R = Σ r_k·x^k` and `Q = Σ f_k·x^k`, the coefficient of `x^i`
     * gives the triangular relation
     *
     *     (i + 1)·r_(i+1) + a·r_i = f_i
     *
     * which is solved from the top down. The coefficients stay exact.
     */
    private fun polynomialParticular(a: Double, q: Node, x: String): Node? {
        val poly = SeparableSolver.toPoly(q, x) ?: return null
        if (poly.isZero()) return Node.Num(0.0)
        val degree = poly.degree
        val f = poly.coeffs

        val r = arrayOfNulls<Rat>(degree + 1)
        for (i in degree downTo 0) {
            val next = if (i + 1 <= degree) r[i + 1] ?: Rat.ZERO else Rat.ZERO
            val rhs = f.getOrElse(i) { Rat.ZERO } - Rat.of((i + 1).toLong()) * next
            r[i] = rhs / Rat.ofDecimal(a.toString())
        }
        return polynomialNode(r.map { it ?: Rat.ZERO }, x)
    }

    /**
     * The particular solution for a forcing term that is a single
     * exponential, `Q = c·e^(b x)`.
     */
    private fun exponentialParticular(a: Double, q: Node, x: String): Node? {
        // Q has to be exactly c · e^(b x).
        var coefficient: Double? = null
        var exponent: Double? = null
        when {
            q is Node.Mul && q.a is Node.Num && q.b is Node.Fun && q.b.name == "exp" -> {
                coefficient = q.a.value
                exponent = exponentOf(q.b, x) ?: return null
            }
            q is Node.Fun && q.name == "exp" -> {
                coefficient = 1.0
                exponent = exponentOf(q, x) ?: return null
            }
            else -> return null
        }
        val b = exponent ?: return null
        val denominator = a + b
        if (abs(denominator) < 1e-12) {
            // The exponential resonates with the homogeneous solution, so the
            // trial has to be multiplied by x: y_p = c·x·e^(b x).
            return Node.Mul(
                Node.Num(coefficient),
                Node.Mul(Node.Var(x), Node.Fun("exp", listOf(Node.Mul(Node.Num(b), Node.Var(x))))),
            )
        }
        val amplitude = coefficient / denominator
        return Node.Mul(
            Node.Num(amplitude),
            Node.Fun("exp", listOf(Node.Mul(Node.Num(b), Node.Var(x)))),
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

    /**
     * Builds a polynomial tree from its coefficients, lowest degree first.
     *
     * The terms are written the way a person would: `x` rather than `1·x^1`,
     * and a coefficient of one is left out.
     */
    fun polynomialNode(coefficients: List<Rat>, x: String): Node {
        var node: Node = Node.Num(0.0)
        for ((power, coefficient) in coefficients.withIndex()) {
            if (coefficient.isZero()) continue
            val value = coefficient.toDouble()
            val term = when {
                power == 0 -> Node.Num(value)
                power == 1 && value == 1.0 -> Node.Var(x)
                power == 1 -> Node.Mul(Node.Num(value), Node.Var(x))
                value == 1.0 -> Node.Pow(Node.Var(x), Node.Num(power.toDouble()))
                else -> Node.Mul(Node.Num(value), Node.Pow(Node.Var(x), Node.Num(power.toDouble())))
            }
            node = if (node is Node.Num && node.value == 0.0) term else Node.Add(node, term)
        }
        return node
    }

    /**
     * The value of a solution at a point, used to check a condition or to
     * verify a result.
     */
    fun evaluate(node: Node, x: Double, variable: String): Double? =
        Evaluator.eval(node, mapOf(variable to x)).valueOrNull()

    /** Renders a solution for display. */
    fun render(node: Node): String = NodePrinter.print(node)
}
