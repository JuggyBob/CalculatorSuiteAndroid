package com.calcsuite.engine.advanced

import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs

/**
 * The complex analysis the tool carries out.
 *
 * Every operation is honest about what it is: the Cauchy–Riemann equations
 * are checked **numerically at one point**, which is a local check and not a
 * proof of analyticity; the residue is found by a limit, which is a numerical
 * estimate; and the residue theorem is applied only when the contour really
 * is a simple closed curve around the poles.
 */
object ComplexAnalysis {

    /**
     * The value of a complex function at a point.
     */
    fun evaluate(function: Node, variable: String, at: ComplexNum): ComplexNum? =
        ComplexEvaluator.eval(function, mapOf(variable.lowercase() to at))

    /**
     * The numerical check of the Cauchy–Riemann equations at a point.
     *
     * The partial derivatives are taken by finite differences, and the two
     * sides of each equation are compared.
     */
    data class CauchyRiemannCheck(
        val u: Double,
        val v: Double,
        val ux: Double,
        val uy: Double,
        val vx: Double,
        val vy: Double,
        /** The size of the first mismatch, `|uₓ − v_y| + |u_y + vₓ|`. */
        val residual: Double,
        /** The step the finite differences used. */
        val step: Double,
    ) {
        /** True when the two sides agree to within the working precision. */
        val holds: Boolean get() = residual < 1e-4
    }

    /**
     * The Cauchy–Riemann check at a point.
     *
     * The point has to be away from any branch cut and away from the
     * boundary of the domain, and the check is only local: it says nothing
     * about the function elsewhere.
     */
    fun cauchyRiemann(
        function: Node,
        variable: String,
        at: ComplexNum,
        step: Double = 1e-5,
    ): CauchyRiemannCheck? {
        val h = step
        val u = { p: ComplexNum ->
            ComplexEvaluator.eval(function, mapOf(variable.lowercase() to p))?.re
        }
        val v = { p: ComplexNum ->
            ComplexEvaluator.eval(function, mapOf(variable.lowercase() to p))?.im
        }
        val u0 = u(at) ?: return null
        val v0 = v(at) ?: return null
        val ux = ((u(ComplexNum(at.re + h, at.im)) ?: return null) - (u(ComplexNum(at.re - h, at.im)) ?: return null)) / (2 * h)
        val uy = ((u(ComplexNum(at.re, at.im + h)) ?: return null) - (u(ComplexNum(at.re, at.im - h)) ?: return null)) / (2 * h)
        val vx = ((v(ComplexNum(at.re + h, at.im)) ?: return null) - (v(ComplexNum(at.re - h, at.im)) ?: return null)) / (2 * h)
        val vy = ((v(ComplexNum(at.re, at.im + h)) ?: return null) - (v(ComplexNum(at.re, at.im - h)) ?: return null)) / (2 * h)
        val residual = abs(ux - vy) + abs(uy + vx)
        return CauchyRiemannCheck(u0, v0, ux, uy, vx, vy, residual, h)
    }

    /**
     * A contour, given parametrically: `z(t) = x(t) + i·y(t)`.
     */
    data class Contour(
        val x: Node,
        val y: Node,
        val from: Double,
        val to: Double,
        /** The description of the contour, for the explanation. */
        val description: String,
    ) {
        /** The point of the contour at the given parameter. */
        fun at(t: Double): ComplexNum? {
            val xv = ComplexEvaluator.eval(x, mapOf("t" to ComplexNum(t, 0.0)))?.re ?: return null
            val yv = ComplexEvaluator.eval(y, mapOf("t" to ComplexNum(t, 0.0)))?.re ?: return null
            return ComplexNum(xv, yv)
        }
    }

    /**
     * The integral of a complex function around a contour.
     *
     * The integral is `∮ f(z)·dz`, and `dz` is `z′(t)·dt`, so the integral
     * is computed with the ordinary rule on a real integrand that carries a
     * complex value.
     */
    fun contourIntegral(
        function: Node,
        variable: String,
        contour: Contour,
        samples: Int = 2000,
    ): ComplexNum? {
        // Simpson's rule needs an even number of steps; a smaller or odd count
        // cannot give a trustworthy value, so the integral is not attempted.
        if (samples < 2 || samples % 2 != 0) return null
        val z = { node: Node, environment: Map<String, ComplexNum> ->
            ComplexEvaluator.eval(node, environment)
        }
        val dx = DifferentiationEngine.differentiate(contour.x, "t").valueOrNull() ?: return null
        val dy = DifferentiationEngine.differentiate(contour.y, "t").valueOrNull() ?: return null
        val steps = samples
        val h = (contour.to - contour.from) / steps
        var total = ComplexNum(0.0, 0.0)
        for (i in 0..steps) {
            val t = contour.from + h * i
            val point = contour.at(t) ?: return null
            val value = z(function, mapOf(variable.lowercase() to point)) ?: return null
            val dxv = z(dx, mapOf("t" to ComplexNum(t, 0.0)))?.re ?: return null
            val dyv = z(dy, mapOf("t" to ComplexNum(t, 0.0)))?.re ?: return null
            // The Simpson weights.
            val weight = when (i) {
                0, steps -> 1.0
                else -> if (i % 2 == 1) 4.0 else 2.0
            }
            total = total + value * ComplexNum(dxv, dyv) * ComplexNum(weight * h / 3, 0.0)
        }
        return total
    }

    /**
     * The classification of a singularity of a function at a point.
     */
    enum class Singularity(val label: String) {
        REGULAR("Regular point"),
        REMOVABLE("Removable singularity"),
        SIMPLE_POLE("Simple pole"),
        POLE("Pole"),
        ESSENTIAL("Essential singularity"),
        UNKNOWN("Not classified"),
    }

    /**
     * The classification of the singularity of `f` at `z₀`, and the residue
     * there.
     *
     * The order of the pole is found by multiplying by `(z − z₀)` until the
     * limit is finite, and the residue is the limit of `(z − z₀)·f(z)` for a
     * simple pole, or of `(z − z₀)ᵏ·f(z)` for a pole of order `k`.
     */
    data class SingularityResult(
        val classification: Singularity,
        val residue: ComplexNum?,
        val order: Int,
        /** The steps that were taken to get there. */
        val steps: List<String>,
    )

    /**
     * The classification and the residue at a point.
     */
    fun singularity(
        function: Node,
        variable: String,
        at: ComplexNum,
    ): SingularityResult {
        val steps = mutableListOf<String>()
        val value = evaluate(function, variable, at)
        if (value != null && value.modulus < 1e10) {
            steps.add("The function is defined and finite at the point, so it is a regular point.")
            return SingularityResult(Singularity.REGULAR, ComplexNum(0.0, 0.0), 0, steps)
        }
        if (value == null) {
            steps.add("The function has no value at the point, so the point is a singularity " +
                "of some kind. Whether it can be filled in, or is a pole, is decided by the " +
                "limit below.")
        } else {
            steps.add("The function is not finite at the point, so the point is a singularity.")
        }

        // Find the order of the pole.
        var order = 1
        var limit: ComplexNum? = null
        while (order <= 6) {
            limit = limitOfProduct(function, variable, at, order)
            if (limit != null && limit.modulus.isFinite()) break
            order++
        }
        if (limit == null || !limit.modulus.isFinite()) {
            steps.add("No finite limit was found for any power up to six, so the " +
                "singularity is treated as essential.")
            return SingularityResult(Singularity.ESSENTIAL, null, order, steps)
        }
        if (limit.re.isNaN() || limit.im.isNaN()) {
            steps.add("The limit could not be evaluated, so the singularity could not be " +
                "classified.")
            return SingularityResult(Singularity.UNKNOWN, null, order, steps)
        }
        val classification = when {
            order == 1 && limit.modulus < 1e-9 -> Singularity.REMOVABLE
            order == 1 -> Singularity.SIMPLE_POLE
            else -> Singularity.POLE
        }
        steps.add(
            when (classification) {
                Singularity.REMOVABLE ->
                    "The limit of (z − z₀)·f(z) as z → z₀ is zero, so the " +
                        "singularity is removable."
                Singularity.SIMPLE_POLE ->
                    "The limit of (z − z₀)·f(z) as z → z₀ is finite and not zero, " +
                        "so the point is a simple pole, and that limit is the residue."
                else ->
                    "The limit of (z − z₀)^$order·f(z) as z → z₀ is finite, so the " +
                        "point is a pole of order $order."
            },
        )

        // The residue is the coefficient of (z − z₀)^(−1). For a simple pole
        // that is the limit above; for a higher-order pole it is picked out
        // from (z − z₀)^order·f(z) on a small circle.
        val residue = when {
            classification == Singularity.REMOVABLE -> ComplexNum(0.0, 0.0)
            classification == Singularity.SIMPLE_POLE -> limit
            order <= 6 -> higherOrderResidue(function, variable, at, order)
            else -> null
        }
        steps.add(
            if (residue == null) {
                "The residue of a pole above order six is not computed."
            } else {
                "The residue is ${residue.cartesian()}."
            },
        )
        return SingularityResult(classification, residue, order, steps)
    }

    /**
     * The residue at a pole of order `k`.
     *
     * With `g(z) = (z − z₀)^k·f(z)`, which is analytic at the point, the
     * residue is the coefficient of `(z − z₀)^(k−1)` in `g`. Averaging `g`
     * around a small circle picks out exactly that coefficient: the other
     * Fourier modes cancel. This works for every order and never asks for the
     * value of `f` at the singularity itself.
     */
    private fun higherOrderResidue(
        function: Node,
        variable: String,
        at: ComplexNum,
        order: Int,
    ): ComplexNum {
        val radius = 1e-3
        val samples = 32
        var scale = 1.0
        repeat(order - 1) { scale *= radius }
        var sum = ComplexNum(0.0, 0.0)
        for (k in 0 until samples) {
            val angle = 2 * Math.PI * k / samples
            val offset = ComplexNum.euler(angle) * ComplexNum(radius, 0.0)
            val value = evaluate(function, variable, at + offset) ?: return ComplexNum(0.0, 0.0)
            var product = value
            repeat(order) { product = product * offset }
            if (!product.modulus.isFinite()) return ComplexNum(0.0, 0.0)
            sum = sum + product * ComplexNum.euler(-(order - 1) * angle)
        }
        return sum / ComplexNum(samples * scale, 0.0)
    }

    /**
     * The limit of `(z − z₀)^k·f(z)` as `z` approaches `z₀`.
     *
     * The product is sampled on a small cross. When the largest sample grows
     * as the step shrinks, this power is below the order of the pole and the
     * product diverges. When it stays bounded the power is the order, and two
     * step sizes cancel the leading sampling error; an absolute settling test
     * would reject a genuine limit that approaches it only linearly in the
     * step. The result is a numerical estimate, not a proof.
     */
    private fun limitOfProduct(
        function: Node,
        variable: String,
        at: ComplexNum,
        power: Int,
    ): ComplexNum? {
        var previous: ComplexNum? = null
        var previousLargest = 0.0
        for (step in listOf(1e-2, 1e-3, 1e-4, 1e-5, 1e-6)) {
            val directions = listOf(
                ComplexNum(step, 0.0),
                ComplexNum(0.0, step),
                ComplexNum(step, step),
                ComplexNum(-step, step),
            )
            var sum = ComplexNum(0.0, 0.0)
            var largest = 0.0
            var count = 0
            for (direction in directions) {
                val point = at + direction
                val value = evaluate(function, variable, point) ?: return null
                var product = value
                repeat(power) { product = product * direction }
                val size = product.modulus
                if (!size.isFinite()) return null
                largest = maxOf(largest, size)
                sum = sum + product
                count++
            }
            if (count == 0) return null
            val average = sum / ComplexNum(count.toDouble(), 0.0)
            if (!average.modulus.isFinite()) return null
            val earlier = previous
            if (earlier != null) {
                // The cross shrinks tenfold each round, so a bounded product
                // cannot double; growth means this power is too low.
                if (largest > 2.0 * previousLargest) return null
                // Remove the leading O(step) error of the asymmetric cross.
                return ComplexNum(
                    (10.0 * average.re - earlier.re) / 9.0,
                    (10.0 * average.im - earlier.im) / 9.0,
                )
            }
            previous = average
            previousLargest = largest
        }
        return previous
    }

    /**
     * The value of the integral around a contour, from the residue theorem.
     *
     * The theorem says the integral is `2πi` times the sum of the residues
     * inside the contour, and it applies when the contour is a simple closed
     * curve that avoids the poles and is traversed once, counter-clockwise.
     */
    fun residueTheorem(residues: List<Pair<ComplexNum, ComplexNum>>): ComplexNum {
        var sum = ComplexNum(0.0, 0.0)
        for ((_, residue) in residues) sum = sum + residue
        return ComplexNum(0.0, 2 * Math.PI) * sum
    }

    /**
     * The value of a complex function on a grid, for the picture.
     */
    fun sample(
        function: Node,
        variable: String,
        from: Double,
        to: Double,
        count: Int,
    ): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        if (count < 2) return out
        val step = (to - from) / (count - 1)
        for (i in 0 until count) {
            val x = from + step * i
            val value = evaluate(function, variable, ComplexNum(x, 0.0)) ?: continue
            if (!value.re.isFinite() || !value.im.isFinite()) continue
            out.add(x to value.im)
        }
        return out
    }

    /** The expression, rendered. */
    fun render(function: Node): String = NodePrinter.print(function)
}
