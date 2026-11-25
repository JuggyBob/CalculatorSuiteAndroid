package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.formatNumber
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import com.calcsuite.engine.calculus.TOO_DEEP_MESSAGE
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

    /** Same wording as the legacy RK4 route in EquationEngine. */
    const val OVERFLOW_MESSAGE = "The numerical solution is not representable"

    /** Outcome of one attempted step: a value, an undefined slope, or overflow. */
    private sealed interface StepResult {
        data class Value(val x: Double, val y: Double) : StepResult
        object Undefined : StepResult
        object Overflow : StepResult
    }

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
    ): NumericalResult = try {
        solveImpl(ode, method, x0, y0, to, step, knownSolution)
    } catch (e: StackOverflowError) {
        refused(
            method, x0, y0, to, step,
            "$TOO_DEEP_MESSAGE No steps were taken.",
        )
    }

    private fun solveImpl(
        ode: ParsedOde,
        method: NumericalMethod,
        x0: Double,
        y0: Double,
        to: Double,
        step: Double,
        knownSolution: Node?,
    ): NumericalResult {
        val x = ode.independent
        val y = ode.dependent
        val f = ode.normalisedRhs

        // Starting values and the requested interval must themselves be finite.
        if (!x0.isFinite() || !y0.isFinite() || !to.isFinite() || !step.isFinite()) {
            return refused(method, x0, y0, to, step, OVERFLOW_MESSAGE, OVERFLOW_MESSAGE)
        }

        // Validate the work against the actual starting point: the condition's
        // x₀ can be far outside the settings' interval. Converting an enormous
        // Double to Int used to saturate at Int.MAX_VALUE and start billions of
        // iterations.
        val needed = (to - x0) / step
        if (!needed.isFinite() || needed <= 0.0 || needed > MAX_STEPS) {
            val count = if (needed.isFinite()) formatNumber(needed) else "an excessive number of"
            return refused(
                method, x0, y0, to, step,
                "Reaching $to from $x0 would take $count steps, which is more than " +
                    "the tool will run (max $MAX_STEPS). No steps were taken.",
            )
        }
        val steps = needed.toInt()
        val points = mutableListOf(NumericalPoint(x0, y0, 0))
        var currentX = x0
        var currentY = y0
        var largestChange = 0.0

        for (i in 1..steps) {
            when (val attempted = advance(f, x, y, method, currentX, currentY, step)) {
                StepResult.Undefined -> break
                StepResult.Overflow -> return failed(method, x0, y0, to, step, points)
                is StepResult.Value -> {
                    // Only finite values are ever stored or reported.
                    val change = abs(attempted.y - currentY)
                    if (change > largestChange) largestChange = change
                    currentX = attempted.x
                    currentY = attempted.y
                    if (points.size < MAX_POINTS) {
                        points.add(NumericalPoint(currentX, currentY, i))
                    }
                }
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

    /** A result that took no steps, used when the request is too large to run. */
    private fun refused(
        method: NumericalMethod,
        x0: Double,
        y0: Double,
        to: Double,
        step: Double,
        reason: String,
        failure: String? = null,
    ): NumericalResult = NumericalResult(
        method = method,
        from = x0,
        to = to,
        step = step,
        // A non-finite initial value must not be published as a point either.
        points = if (failure == null) listOf(NumericalPoint(x0, y0, 0)) else emptyList(),
        steps = listOf(reason),
        errorIndicator = null,
        comparison = null,
        failure = failure,
    )

    /** Stops at the last finite point when a step overflows. */
    private fun failed(
        method: NumericalMethod,
        x0: Double,
        y0: Double,
        to: Double,
        step: Double,
        points: List<NumericalPoint>,
    ): NumericalResult = NumericalResult(
        method = method,
        from = x0,
        to = to,
        step = step,
        points = points,
        steps = listOf(OVERFLOW_MESSAGE),
        errorIndicator = null,
        comparison = null,
        failure = OVERFLOW_MESSAGE,
    )

    /**
     * One step of the chosen method.
     *
     * [StepResult.Undefined] means the right-hand side is not defined at the
     * sampled point. [StepResult.Overflow] means a computed x or y was not
     * finite: it must stop the run rather than become a displayed point.
     */
    private fun advance(
        f: Node,
        x: String,
        y: String,
        method: NumericalMethod,
        currentX: Double,
        currentY: Double,
        h: Double,
    ): StepResult {
        val k1 = slope(f, x, y, currentX, currentY) ?: return StepResult.Undefined
        val advancedX = currentX + h
        if (!advancedX.isFinite()) return StepResult.Overflow
        return when (method) {
            NumericalMethod.EULER -> value(advancedX, currentY + h * k1)
            NumericalMethod.HEUN -> {
                val predictedY = currentY + h * k1
                if (!predictedY.isFinite()) return StepResult.Overflow
                val k2 = slope(f, x, y, advancedX, predictedY) ?: return StepResult.Undefined
                value(advancedX, currentY + h * (k1 + k2) / 2)
            }
            NumericalMethod.RK4 -> {
                val midX = currentX + h / 2
                if (!midX.isFinite()) return StepResult.Overflow
                val midY1 = currentY + h / 2 * k1
                if (!midY1.isFinite()) return StepResult.Overflow
                val k2 = slope(f, x, y, midX, midY1) ?: return StepResult.Undefined
                val midY2 = currentY + h / 2 * k2
                if (!midY2.isFinite()) return StepResult.Overflow
                val k3 = slope(f, x, y, midX, midY2) ?: return StepResult.Undefined
                val endY = currentY + h * k3
                if (!endY.isFinite()) return StepResult.Overflow
                val k4 = slope(f, x, y, advancedX, endY) ?: return StepResult.Undefined
                val nextY = currentY + h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
                value(advancedX, nextY)
            }
        }
    }

    /** Packages a finished step, refusing a non-finite y just like a non-finite x. */
    private fun value(x: Double, y: Double): StepResult =
        if (x.isFinite() && y.isFinite()) StepResult.Value(x, y) else StepResult.Overflow

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
