package com.calcsuite.engine.advanced

import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.core.CalcResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The kind of partial differential equation a problem describes. */
enum class PdeKind(
    val label: String,
    /** The short name, for a chip that has to fit in a narrow column. */
    val shortLabel: String,
    val formula: String,
    val description: String,
) {
    HEAT(
        "Heat equation (1D)",
        "Heat",
        "uₜ = α·uₓₓ",
        "Heat spreads along a rod. The second difference in space and the first " +
            "difference in time give the update.",
    ),
    WAVE(
        "Wave equation (1D)",
        "Wave",
        "uₜₜ = c²·uₓₓ",
        "A string or a wave travels along a line. The update uses the current " +
            "value, its two neighbours and the previous value in time.",
    ),
    LAPLACE(
        "Laplace's equation (2D)",
        "Laplace",
        "uₓₓ + u_yy = 0",
        "A steady state with no source. The value at each interior point is " +
            "the average of its four neighbours.",
    ),
    POISSON(
        "Poisson's equation (2D)",
        "Poisson",
        "uₓₓ + u_yy = f(x, y)",
        "A steady state with a source. The value at each interior point is the " +
            "average of its four neighbours, less the source.",
    ),
}

/**
 * How a second-order equation in two variables is classified.
 *
 * For `A·uₓₓ + B·uₓy + C·u_yy = …` the discriminant `B² − 4AC` decides:
 * negative is elliptic, zero is parabolic and positive is hyperbolic.
 */
object PdeClassification {

    /** The class of a second-order equation. */
    enum class Kind(val label: String, val meaning: String) {
        ELLIPTIC(
            "Elliptic",
            "The value at a point is an average of its neighbours, and the " +
                "problem has a unique steady state.",
        ),
        PARABOLIC(
            "Parabolic",
            "The equation describes a process that spreads in one direction, " +
                "such as heat diffusing along a rod.",
        ),
        HYPERBOLIC(
            "Hyperbolic",
            "The equation describes a wave, and information travels along " +
                "characteristics.",
        ),
        UNKNOWN("Not classifiable", "The coefficients do not describe a second-order equation."),
    }

    /**
     * The class of the equation `A·uₓₓ + B·uₓy + C·u_yy = f`.
     */
    fun classify(a: Double, b: Double, c: Double): Kind {
        if (a == 0.0 && b == 0.0 && c == 0.0) return Kind.UNKNOWN
        val discriminant = b * b - 4 * a * c
        val tolerance = 1e-12 * max(1.0, max(abs(a), max(abs(b), abs(c))))
        return when {
            discriminant < -tolerance -> Kind.ELLIPTIC
            discriminant > tolerance -> Kind.HYPERBOLIC
            else -> Kind.PARABOLIC
        }
    }
}

/**
 * The stability information of a finite-difference scheme.
 */
data class StabilityInfo(
    /** The Courant number of the scheme. */
    val courant: Double,
    /** The largest value the Courant number may take. */
    val limit: Double,
    /** True when the chosen step sizes satisfy the restriction. */
    val stable: Boolean,
    /** What the restriction means, in words. */
    val explanation: String,
)

/**
 * A one-dimensional solution, over space and time.
 */
data class PdeSolution1D(
    val kind: PdeKind,
    /** The grid points in space. */
    val x: List<Double>,
    /** The times the solution was computed at. */
    val times: List<Double>,
    /** The values, `values[time][space]`. */
    val values: List<List<Double>>,
    val stability: StabilityInfo,
    /** The scheme that was used. */
    val method: String,
    /** The working that produced the solution. */
    val steps: List<String>,
    /** True when the result is a numerical approximation. */
    val numerical: Boolean = true,
)

/**
 * A two-dimensional field.
 */
data class PdeSolution2D(
    val kind: PdeKind,
    /** The grid points in x. */
    val x: List<Double>,
    /** The grid points in y. */
    val y: List<Double>,
    /** The values, `values[row][column]`, row by row in y. */
    val values: List<List<Double>>,
    val method: String,
    val steps: List<String>,
    val iterations: Int,
    val numerical: Boolean = true,
)

/**
 * Solves the supported partial differential equations by finite differences.
 *
 * The schemes are the standard ones:
 *
 * - The **heat equation** uses forward differences in time and central
 *   differences in space, which needs `α·Δt ÷ Δx² ≤ ½` to stay stable.
 * - The **wave equation** uses the standard three-level scheme, which needs
 *   `c·Δt ÷ Δx ≤ 1`.
 * - **Laplace's and Poisson's equations** are solved by repeated relaxation
 *   on the grid until the values stop changing.
 *
 * The stability conditions are checked before anything is computed, and the
 * result says plainly that it is a numerical approximation.
 */
object PdeEngine {

    /** The largest number of grid points that will be used in one direction. */
    const val MAX_POINTS = 400

    /** The largest number of time steps that will be taken. */
    const val MAX_TIME_STEPS = 4000

    /**
     * Solves the one-dimensional heat or wave equation.
     *
     * @param initial the initial condition, as an expression in `x`.
     * @param left the value at the left end.
     * @param right the value at the right end.
     */
    fun solve1D(
        kind: PdeKind,
        initial: com.calcsuite.engine.calculus.Node,
        variable: String,
        length: Double,
        spaceSteps: Int,
        timeSteps: Int,
        timeEnd: Double,
        left: Double,
        right: Double,
        alpha: Double,
        speed: Double,
        /**
         * The initial velocity, `u_t(x, 0)`, for the wave equation. It is
         * needed because the wave equation is second order in time, so the
         * velocity is a second initial condition; without it the wave can
         * only be taken to start at rest.
         */
        initialVelocity: com.calcsuite.engine.calculus.Node? = null,
    ): PdeSolution1D? {
        if (kind != PdeKind.HEAT && kind != PdeKind.WAVE) return null
        if (length <= 0.0 || timeEnd <= 0.0) return null
        if (spaceSteps < 2 || spaceSteps > MAX_POINTS) return null
        if (timeSteps < 1 || timeSteps > MAX_TIME_STEPS) return null

        val dx = length / spaceSteps
        val dt = timeEnd / timeSteps
        val x = (0..spaceSteps).map { it * dx }

        val steps = mutableListOf<String>()
        steps.add("The interval [0, ${formatReal(length)}] is divided into $spaceSteps " +
            "equal parts, so Δx = ${formatReal(dx)}.")
        steps.add("The time [0, ${formatReal(timeEnd)}] is divided into $timeSteps steps, " +
            "so Δt = ${formatReal(dt)}.")

        val courant: Double
        val limit: Double
        val explanation: String
        val method: String
        if (kind == PdeKind.HEAT) {
            courant = alpha * dt / (dx * dx)
            limit = 0.5
            method = "Forward differences in time, central differences in space"
            steps.add("For the heat equation the update is")
            steps.add("  uᵢ^{n+1} = uᵢⁿ + r·(uᵢ₊₁ⁿ − 2·uᵢⁿ + uᵢ₋₁ⁿ),  with r = α·Δt ÷ Δx².")
            steps.add("Here r = ${formatReal(courant)}.")
            explanation = "The explicit scheme is stable only when r ≤ ½, so the step in " +
                "time has to be small enough compared with the square of the step in space."
        } else {
            courant = speed * dt / dx
            limit = 1.0
            method = "The standard three-level wave scheme"
            steps.add("For the wave equation the update is")
            steps.add("  uᵢ^{n+1} = 2·uᵢⁿ − uᵢ^{n−1} + s²·(uᵢ₊₁ⁿ − 2·uᵢⁿ + uᵢ₋₁ⁿ),  with s = c·Δt ÷ Δx.")
            steps.add("Here s = ${formatReal(courant)}.")
            explanation = "The wave scheme is stable only when s ≤ 1, so the step in time " +
                "has to be small enough compared with the step in space."
        }
        val stable = courant <= limit + 1e-9
        steps.add(
            if (stable) {
                "The stability condition holds: ${formatReal(courant)} ≤ ${formatReal(limit)}."
            } else {
                "The stability condition does not hold: ${formatReal(courant)} is above " +
                    "${formatReal(limit)}. The result would blow up, so a smaller step in " +
                    "time is needed."
            },
        )
        val stability = StabilityInfo(courant, limit, stable, explanation)

        // The initial row, with the boundary values imposed.
        val first = DoubleArray(spaceSteps + 1)
        for (i in 0..spaceSteps) {
            val value = Evaluator.eval(initial, mapOf(variable to x[i])).valueOrNull()
            first[i] = value ?: 0.0
        }
        first[0] = left
        first[spaceSteps] = right

        val times = mutableListOf<Double>()
        val rows = mutableListOf<DoubleArray>()
        times.add(0.0)
        rows.add(first.copyOf())

        if (kind == PdeKind.HEAT) {
            var previous = first.copyOf()
            for (n in 1..timeSteps) {
                val next = DoubleArray(spaceSteps + 1)
                next[0] = left
                next[spaceSteps] = right
                for (i in 1 until spaceSteps) {
                    next[i] = previous[i] + courant * (previous[i + 1] - 2 * previous[i] + previous[i - 1])
                }
                previous = next
                times.add(n * dt)
                rows.add(next.copyOf())
            }
        } else {
            // The wave equation is second order in time, so the first row
            // after the initial one is worked out from the second-order
            // expansion of the solution,
            //
            //   u(x, Δt) = u(x, 0) + Δt·u_t(x, 0) + (Δt²/2)·c²·u_xx(x, 0),
            //
            // which honours the initial velocity and is accurate to the
            // second order. With no velocity the wave simply starts at rest.
            val velocity = DoubleArray(spaceSteps + 1)
            if (initialVelocity != null) {
                for (i in 0..spaceSteps) {
                    velocity[i] = Evaluator.eval(
                        initialVelocity,
                        mapOf(variable to x[i]),
                    ).valueOrNull() ?: 0.0
                }
            }
            val s2 = courant * courant
            val second = DoubleArray(spaceSteps + 1)
            second[0] = left
            second[spaceSteps] = right
            for (i in 1 until spaceSteps) {
                second[i] = first[i] + dt * velocity[i] +
                    (s2 / 2) * (first[i + 1] - 2 * first[i] + first[i - 1])
            }
            var older = first.copyOf()
            var previous = second.copyOf()
            times.add(dt)
            rows.add(second.copyOf())
            for (n in 1 until timeSteps) {
                val next = DoubleArray(spaceSteps + 1)
                next[0] = left
                next[spaceSteps] = right
                for (i in 1 until spaceSteps) {
                    next[i] = 2 * previous[i] - older[i] +
                        courant * courant * (previous[i + 1] - 2 * previous[i] + previous[i - 1])
                }
                older = previous
                previous = next
                // The second row was already the one at time dt, so this row
                // is the one at time (n + 1)·dt.
                times.add((n + 1) * dt)
                rows.add(next.copyOf())
            }
        }

        if (kind == PdeKind.WAVE) {
            steps.add(
                if (initialVelocity == null) {
                    "No initial velocity was given, so the wave starts at rest."
                } else {
                    "The initial velocity is used in the first step, through the second-order " +
                        "expansion of the solution."
                },
            )
        }
        steps.add("The boundary values ${formatReal(left)} and ${formatReal(right)} are held " +
            "at the ends at every step, so the boundary conditions are satisfied exactly.")
        steps.add("The result is a numerical approximation; the finite-difference scheme " +
            "carries a truncation error of order Δx² in space.")

        return PdeSolution1D(
            kind = kind,
            x = x,
            times = times,
            values = rows.map { it.toList() },
            stability = stability,
            method = method,
            steps = steps,
        )
    }

    /**
     * Solves Laplace's or Poisson's equation on a square.
     *
     * The boundary values come from one expression, evaluated along each side,
     * and the source is one expression in `x` and `y`.
     */
    fun solve2D(
        kind: PdeKind,
        boundary: com.calcsuite.engine.calculus.Node,
        source: com.calcsuite.engine.calculus.Node?,
        xVar: String,
        yVar: String,
        size: Double,
        gridPoints: Int,
        iterations: Int,
        tolerance: Double = 1e-8,
    ): PdeSolution2D? {
        if (kind != PdeKind.LAPLACE && kind != PdeKind.POISSON) return null
        if (size <= 0.0) return null
        if (gridPoints < 3 || gridPoints > MAX_POINTS) return null
        if (iterations < 1 || iterations > 200_000) return null

        val n = gridPoints
        val h = size / (n - 1)
        val x = (0 until n).map { it * h }
        val y = (0 until n).map { it * h }

        val steps = mutableListOf<String>()
        steps.add("The square [0, ${formatReal(size)}]² is divided into ${n - 1} equal " +
            "parts in each direction, so h = ${formatReal(h)}.")
        steps.add("At each interior point the equation says the value is the average of " +
            "the four neighbours" + if (kind == PdeKind.POISSON) {
                ", less the source at that point"
            } else {
                ""
            } + ".")

        val grid = Array(n) { DoubleArray(n) }
        // The boundary.
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == 0 || i == n - 1 || j == 0 || j == n - 1) {
                    val value = Evaluator.eval(
                        boundary,
                        mapOf(xVar to x[j], yVar to y[i]),
                    ).valueOrNull() ?: 0.0
                    grid[i][j] = value
                }
            }
        }

        var largestChange = 0.0
        var used = 0
        for (iteration in 1..iterations) {
            largestChange = 0.0
            for (i in 1 until n - 1) {
                for (j in 1 until n - 1) {
                    val f = if (source == null) {
                        0.0
                    } else {
                        Evaluator.eval(source, mapOf(xVar to x[j], yVar to y[i])).valueOrNull() ?: 0.0
                    }
                    val old = grid[i][j]
                    val updated = (grid[i - 1][j] + grid[i + 1][j] + grid[i][j - 1] + grid[i][j + 1]
                        - h * h * f) / 4.0
                    grid[i][j] = updated
                    val change = abs(updated - old)
                    if (change > largestChange) largestChange = change
                }
            }
            used = iteration
            if (largestChange < tolerance) break
        }

        steps.add("The relaxation was repeated until the largest change between " +
            "successive sweeps fell below ${formatReal(tolerance)}, after $used sweeps.")
        steps.add("The result is a numerical approximation of the steady state.")

        return PdeSolution2D(
            kind = kind,
            x = x,
            y = y,
            values = (0 until n).map { i -> (0 until n).map { j -> grid[i][j] } },
            method = "Repeated relaxation (the Gauss–Seidel sweep) on the grid",
            steps = steps,
            iterations = used,
        )
    }

    /**
     * Parses an expression, for the callers that take text.
     */
    fun parse(text: String): CalcResult<com.calcsuite.engine.calculus.Node> =
        CalculusParser.parse(text)

    /**
     * The range of the values in a field, for the colour scale.
     *
     * The range is the true one. When every value is the same, both ends
     * are that value; the caller says so, rather than making a range up
     * that the field does not have.
     */
    fun valueRange(values: List<List<Double>>): Pair<Double, Double> {
        var lowest = Double.POSITIVE_INFINITY
        var highest = Double.NEGATIVE_INFINITY
        for (row in values) {
            for (v in row) {
                if (!v.isFinite()) continue
                lowest = min(lowest, v)
                highest = max(highest, v)
            }
        }
        if (!lowest.isFinite()) return 0.0 to 0.0
        return lowest to highest
    }

    /**
     * True when the field is flat, so that the picture is a single colour.
     *
     * Laplace's equation gives a steady state like that whenever the
     * boundary is a constant. The numbers are worked out by relaxation, so
     * the field is flat to within the solver's own tolerance rather than
     * exactly, and the spread that counts as flat is the one the eye cannot
     * see: a hundredth of the size of the field.
     */
    fun isConstant(values: List<List<Double>>): Boolean {
        val (lowest, highest) = valueRange(values)
        val size = max(abs(lowest), abs(highest))
        return highest - lowest <= 0.01 * max(1.0, size)
    }

    /**
     * The length of a vector, for the explanations.
     */
    fun lengthOf(v: DoubleArray): Double = sqrt(v.sumOf { it * it })
}
