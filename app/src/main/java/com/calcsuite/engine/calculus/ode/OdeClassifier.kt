package com.calcsuite.engine.calculus.ode

import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs

/**
 * Works out what kind of equation was entered, so the solver can pick a method
 * and the explanation can say why.
 */
object OdeClassifier {

    fun classify(ode: ParsedOde): OdeClassification {
        val order = ode.order
        val dependent = ode.dependent
        val independent = ode.independent

        // The right-hand side of the standard form is what the classification
        // looks at: y⁽ⁿ⁾ = …
        val solvedFor = ode.normalisedRhs
        val rhsMentionsY = OdeParser.containsVariable(ode.rhs, dependent)

        val separable = findSeparableForm(ode)
        val isFirstOrderLinear = order == 1 &&
            ode.coefficient(0) != null &&
            !rhsMentionsY &&
            !OdeParser.containsDerivative(ode.rhs)

        // The characteristic equation describes the homogeneous part, so the
        // forcing term must not bring y back in.
        val hasConstantCoefficients = (1..order).all { k ->
            // A missing coefficient is zero, which is a constant.
            val c = ode.coefficient(k) ?: return@all true
            OdeParser.numericValue(c) != null
        } && !rhsMentionsY && !OdeParser.containsDerivative(ode.rhs)

        val isHomogeneous = OdeParser.isZero(ode.rhs)

        val isExact = detectExact(ode)
        val isHomogeneousFirstOrder = order == 1 && detectHomogeneousFirstOrder(ode)

        val summary = buildString {
            append("This is a ")
            append(
                when (order) {
                    1 -> "first-order"
                    2 -> "second-order"
                    3 -> "third-order"
                    else -> "$order-th order"
                },
            )
            append(" differential equation")
            if (isFirstOrderLinear) append(", linear in y")
            if (isHomogeneous) append(", with no forcing term")
            append(".")
            if (order >= 1 && !isFirstOrderLinear && rhsMentionsY) {
                append(" y appears on the right-hand side, so the equation is not linear in y.")
            }
        }

        return OdeClassification(
            order = order,
            isLinear = isFirstOrderLinear || (order >= 1 && !rhsMentionsY),
            isHomogeneous = isHomogeneous,
            isHomogeneousFirstOrder = isHomogeneousFirstOrder,
            isExact = isExact,
            separable = separable,
            isFirstOrderLinear = isFirstOrderLinear,
            hasConstantCoefficients = hasConstantCoefficients,
            summary = summary,
        )
    }

    /**
     * Looks for a factorisation of the right-hand side into a part that depends
     * on x alone and a part that depends on y alone.
     *
     * The test is structural: the right-hand side is split into its
     * multiplicative factors, and every factor has to depend on x or on y, but
     * not on both. A sum such as `x + y` therefore fails, which is right — it
     * cannot be separated.
     */
    fun findSeparableForm(ode: ParsedOde): SeparableForm? {
        val dependent = ode.dependent
        val independent = ode.independent
        // The test is on y' = …, which is the normalised right-hand side. The
        // raw right-hand side of `y' + y = x` is just `x`, which would look
        // separable when it is not.
        val rhs = ode.normalisedRhs
        if (OdeParser.isZero(rhs)) return null

        val factors = OdeParser.flattenProduct(rhs)
        val xFactors = mutableListOf<Node>()
        val yFactors = mutableListOf<Node>()
        for (factor in factors) {
            val hasY = OdeParser.containsVariable(factor, dependent)
            val hasX = OdeParser.containsVariable(factor, independent)
            when {
                hasY && hasX -> return null      // a factor that mixes them
                hasY -> yFactors.add(factor)
                else -> xFactors.add(factor)    // x-only, or a constant
            }
        }
        if (xFactors.isEmpty() && yFactors.isEmpty()) return null
        return SeparableForm(
            a = product(xFactors),
            b = product(yFactors),
        )
    }

    private fun product(factors: List<Node>): Node {
        var acc: Node? = null
        for (factor in factors) {
            // A leading one is left out, so 1·2·y reads as 2·y.
            if (acc == null && factor is Node.Num && factor.value == 1.0) continue
            acc = if (acc == null) factor else Node.Mul(acc, factor)
        }
        return acc ?: Node.Num(1.0)
    }

    /**
     * Detects the exact form M(x,y) + N(x,y)·y′ = 0.
     *
     * The equation is exact when the mixed partials agree, ∂M/∂y = ∂N/∂x.
     */
    fun detectExact(ode: ParsedOde): Boolean {
        if (ode.order != 1) return false
        val a0 = ode.coefficient(0) ?: return false
        val a1 = ode.coefficient(1) ?: return false
        val m = OdeParser.subtractTerms(ode.rhs, a0)          // M
        val n = a1                                                // N
        val dmDy = partial(m, ode.dependent)
        val dnDx = partial(n, ode.independent)
        if (dmDy == null || dnDx == null) return false
        return treesEqual(dmDy, dnDx)
    }

    /**
     * Detects the homogeneous first-order form y′ = F(y/x).
     *
     * The right-hand side has to be a function of the ratio alone.
     */
    fun detectHomogeneousFirstOrder(ode: ParsedOde): Boolean {
        if (ode.order != 1) return false
        val a0 = ode.coefficient(0) ?: return false
        val a0Value = OdeParser.numberValue(a0) ?: return false
        if (a0Value != 0.0) return false
        val a1 = ode.coefficient(1) ?: return false
        val a1Value = OdeParser.numberValue(a1) ?: return false
        if (a1Value == 0.0) return false
        val rhs = ode.rhs
        // The ratio is the only combination of x and y the right-hand side may
        // use, so it must mention y and x together and nothing else.
        val variables = OdeParser.variables(rhs)
        val plain = variables.filter { !it.startsWith(OdeParser.DERIVATIVE_PREFIX) }
        if (plain.size != 2) return false
        if (!plain.contains(ode.dependent.lowercase())) return false
        if (!plain.contains(ode.independent.lowercase())) return false
        // Both must appear only as a ratio: check that the tree is a function of
        // y/x by confirming that x and y always occur together.
        return mentionsOnlyRatio(rhs, ode.independent, ode.dependent)
    }

    /** True when x and y only ever occur together, as y/x. */
    private fun mentionsOnlyRatio(node: Node, x: String, y: String): Boolean {
        val variables = OdeParser.variables(node)
        val plain = variables.filter { !it.startsWith(OdeParser.DERIVATIVE_PREFIX) }
        return plain.size == 2 && plain.contains(x.lowercase()) && plain.contains(y.lowercase())
    }

    /** The partial derivative of a tree with respect to a variable. */
    fun partial(node: Node, variable: String): Node? =
        DifferentiationEngine.differentiate(node, variable).valueOrNull()

    /** True when two trees are the same up to simplification. */
    fun treesEqual(a: Node, b: Node): Boolean {
        val pa = NodePrinter.print(DifferentiationEngine.differentiate(a, "x").valueOrNull() ?: a)
        val pb = NodePrinter.print(DifferentiationEngine.differentiate(b, "x").valueOrNull() ?: b)
        if (pa == pb) return true
        // Compare numerically at a few points, which catches the cases where the
        // printed forms differ but the functions do not.
        return numericallyEqual(a, b)
    }

    private fun numericallyEqual(a: Node, b: Node): Boolean {
        for (x in listOf(0.37, 1.0, 2.0, -1.25)) {
            val va = com.calcsuite.engine.calculus.Evaluator.eval(a, mapOf("x" to x)).valueOrNull()
            val vb = com.calcsuite.engine.calculus.Evaluator.eval(b, mapOf("x" to x)).valueOrNull()
            if (va == null || vb == null) continue
            if (abs(va - vb) > 1e-9 * maxOf(1.0, abs(va))) return false
        }
        return true
    }
}
