package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.formatNumber
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs

/**
 * Steps an initial-value problem forward numerically.
 *
 * Three methods are offered, and all three use the same right-hand side
 * `f(x, y)`, so switching between them shows how much the method matters.
 * Euler's method is first-order accurate, Heun's is second-order and RK4 is
 * fourth-order, so for the same step size their answers get closer to the true
 * solution in that order.
 */
object NumericalOdeSolver {

    /** The largest number of steps that will be run. */
    const val MAX_STEPS = 200_000

    /** The largest number of points that will be kept for display. */
    const val MAX_POINTS = 2000

    /**
     * Runs the requested method.
     *
     * @param ode the parsed equation.
     * @param method which method to use.
     * @param x0 where the solution starts.
     * @param y0 the value of the solution there.
     * @param to where to run to.
     * @param step the step size.
     * @param knownSolution an optional expression for the exact solution, used
     *   to report the error the method made.
     */
    fun solve(
        ode: ParsedOde,
        method: NumericalMethod,
        x0: Double,
        y0: Double,
        to: Double,
        step: Double,
        knownSolution: Node? = null,
    ): NumericalResult {
        val x = ode.independent
        val y = ode.dependent
        val f = ode.normalisedRhs

        val steps = ((to - x0) / step).toInt()
        val points = mutableListOf(NumericalPoint(x0, y0, 0))
        var currentX = x0
        var currentY = y0
        var largestChange = 0.0

        for (i in 1..steps) {
            val next = advance(f, x, y, method, currentX, currentY, step)
            val (newX, newY) = next ?: break
            val change = abs(newY - currentY)
            if (change > largestChange) largestChange = change
            currentX = newX
            currentY = newY
            if (points.size < MAX_POINTS) {
                points.add(NumericalPoint(currentX, currentY, i))
            }
        }

        val stepsText = buildSteps(method, f, x, y, x0, y0, step)
        val errorIndicator = buildErrorIndicator(points)
        val comparison = buildComparison(knownSolution, x, points)
        return NumericalResult(
            method = method,
            from = x0,
            to = to,
            step = step,
            points = points,
            steps = stepsText,
            errorIndicator = errorIndicator,
            comparison = comparison,
        )
    }

    /**
     * One step of the chosen method.
     *
     * Returns null when the right-hand side is not defined at the point.
     */
    private fun advance(
        f: Node,
        x: String,
        y: String,
        method: NumericalMethod,
        currentX: Double,
        currentY: Double,
        h: Double,
    ): Pair<Double, Double>? {
        val k1 = slope(f, x, y, currentX, currentY) ?: return null
        return when (method) {
            NumericalMethod.EULER -> {
                val nextY = currentY + h * k1
                nextY to currentY.let { currentX + h }
            }
            NumericalMethod.HEUN -> {
                val predictedX = currentX + h
                val predictedY = currentY + h * k1
                val k2 = slope(f, x, y, predictedX, predictedY) ?: return null
                val nextY = currentY + h * (k1 + k2) / 2
                nextY to predictedX
            }
            NumericalMethod.RK4 -> {
                val k2 = slope(f, x, y, currentX + h / 2, currentY + h / 2 * k1) ?: return null
                val k3 = slope(f, x, y, currentX + h / 2, currentY + h / 2 * k2) ?: return null
                val k4 = slope(f, x, y, currentX + h, currentY + h * k3) ?: return null
                val nextY = currentY + h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
                (currentX + h) to nextY
            }
        }
    }

    /** The value of the right-hand side, or null when it is undefined. */
    private fun slope(f: Node, x: String, y: String, at: Double, value: Double): Double? {
        val environment = mapOf(
            x to at,
            y to value,
            // The placeholders for the derivatives of y are not needed for a
            // first-order equation, but an empty binding keeps the evaluation
            // honest if one appears.
        )
        return Evaluator.eval(f, environment).valueOrNull()
    }

    /**
     * The explanation of the method, with the first step worked out.
     */
    private fun buildSteps(
        method: NumericalMethod,
        f: Node,
        x: String,
        y: String,
        x0: Double,
        y0: Double,
        h: Double,
    ): List<String> {
        val steps = mutableListOf<String>()
        steps.add("The right-hand side is f($x, $y) = ${NodePrinter.print(f)}.")
        steps.add(method.description)
        when (method) {
            NumericalMethod.EULER -> steps.add(
                "Euler's method uses y₁ = y₀ + h·f(x₀, y₀): take the slope at the " +
                    "current point and move in a straight line for one step.",
            )
            NumericalMethod.HEUN -> steps.add(
                "Heun's method predicts with Euler and then corrects: y₁ = y₀ + h·(k₁ + k₂)/2, " +
                    "where k₁ is the slope at the current point and k₂ the slope at the predicted point.",
            )
            NumericalMethod.RK4 -> steps.add(
                "RK4 combines four slope estimates per step — at the start, at the midpoint " +
                    "(twice) and at the end — and combines them with the weights 1, 2, 2, 1.",
            )
        }

        // The first step, worked out.
        val k1 = slope(f, x, y, x0, y0)
        if (k1 == null) {
            steps.add("The right-hand side is not defined at the starting point, so the method cannot start.")
            return steps
        }
        steps.add("The first step, from x = ${formatNumber(x0)} with y = ${formatNumber(y0)}:")
        steps.add("  k₁ = f(${formatNumber(x0)}, ${formatNumber(y0)}) = ${formatNumber(k1)}")
        when (method) {
            NumericalMethod.EULER -> {
                val y1 = y0 + h * k1
                steps.add("  y₁ = ${formatNumber(y0)} + ${formatNumber(h)}·${formatNumber(k1)} = ${formatNumber(y1)}")
            }
            NumericalMethod.HEUN -> {
                val predictedY = y0 + h * k1
                val k2 = slope(f, x, y, x0 + h, predictedY)
                if (k2 == null) {
                    steps.add("  the predicted point leaves the region where the equation is defined")
                } else {
                    val y1 = y0 + h * (k1 + k2) / 2
                    steps.add("  predicted y = ${formatNumber(predictedY)} gives k₂ = ${formatNumber(k2)}")
                    steps.add("  y₁ = ${formatNumber(y0)} + ${formatNumber(h)}·(${formatNumber(k1)} + ${formatNumber(k2)})/2 = ${formatNumber(y1)}")
                }
            }
            NumericalMethod.RK4 -> {
                val k2 = slope(f, x, y, x0 + h / 2, y0 + h / 2 * k1)
                val k3 = if (k2 == null) null else slope(f, x, y, x0 + h / 2, y0 + h / 2 * k2)
                val k4 = if (k3 == null) null else slope(f, x, y, x0 + h, y0 + h * k3)
                if (k2 == null || k3 == null || k4 == null) {
                    steps.add("  the intermediate points leave the region where the equation is defined")
                } else {
                    val y1 = y0 + h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
                    steps.add("  k₁ = ${formatNumber(k1)}, k₂ = ${formatNumber(k2)}, k₃ = ${formatNumber(k3)}, k₄ = ${formatNumber(k4)}")
                    steps.add("  y₁ = ${formatNumber(y0)} + ${formatNumber(h)}/6·(k₁ + 2k₂ + 2k₃ + k₄) = ${formatNumber(y1)}")
                }
            }
        }
        return steps
    }

    /** A rough measure of the error: the largest step-to-step change. */
    private fun buildErrorIndicator(points: List<NumericalPoint>): String? {
        if (points.size < 2) return null
        var largest = 0.0
        for (i in 1 until points.size) {
            val change = abs(points[i].y - points[i - 1].y)
            if (change > largest) largest = change
        }
        return "Largest change between consecutive steps: ${formatNumber(largest)}. " +
            "This is a rough guide to the resolution of the answer, not a guaranteed error bound."
    }

    /** The error against a known solution, when one is available. */
    private fun buildComparison(known: Node?, x: String, points: List<NumericalPoint>): String? {
        if (known == null || points.isEmpty()) return null
        var worst = 0.0
        for (point in points) {
            val exact = Evaluator.eval(known, mapOf(x to point.x)).valueOrNull() ?: return null
            val error = abs(exact - point.y)
            if (error > worst) worst = error
        }
        return "Compared with the known solution, the largest error over the interval is " +
            "${formatNumber(worst)}."
    }
}
