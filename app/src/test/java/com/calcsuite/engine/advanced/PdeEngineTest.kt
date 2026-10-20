package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tests for the partial differential equations.
 *
 * The one-dimensional solvers are checked against the exact solutions, which
 * are known for the sine initial condition.
 */
class PdeEngineTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    @Test fun theHeatSolutionMatchesTheExactOne() {
        // With u(x, 0) = sin(πx) and zero ends, the exact solution is
        // u(x, t) = sin(πx)·e^(−απ²t).
        val alpha = 0.05
        val solution = PdeEngine.solve1D(
            kind = PdeKind.HEAT,
            initial = parse("sin(pi*x)"),
            variable = "x",
            length = 1.0,
            spaceSteps = 60,
            timeSteps = 60,
            timeEnd = 0.2,
            left = 0.0,
            right = 0.0,
            alpha = alpha,
            speed = 1.0,
        )!!
        val lastTime = solution.times.last()
        val middle = solution.x.indices.first { abs(solution.x[it] - 0.5) < 0.02 }
        val expected = sin(PI * solution.x[middle]) * exp(-alpha * PI * PI * lastTime)
        assertEquals(
            "the numerical solution should match the exact one",
            expected,
            solution.values.last()[middle],
            2e-3,
        )
    }

    @Test fun theBoundaryConditionsAreHeldExactly() {
        val solution = PdeEngine.solve1D(
            kind = PdeKind.HEAT,
            initial = parse("sin(pi*x)"),
            variable = "x",
            length = 1.0,
            spaceSteps = 20,
            timeSteps = 10,
            timeEnd = 0.1,
            left = 0.0,
            right = 0.0,
            alpha = 0.1,
            speed = 1.0,
        )!!
        for (row in solution.values) {
            assertEquals(0.0, row.first(), 1e-12)
            assertEquals(0.0, row.last(), 1e-12)
        }
    }

    @Test fun theWaveSolutionMatchesTheExactOne() {
        // With u(x, 0) = sin(πx) and u_t(x, 0) = 0, the exact solution is
        // u(x, t) = sin(πx)·cos(cπt).
        val speed = 1.0
        val solution = PdeEngine.solve1D(
            kind = PdeKind.WAVE,
            initial = parse("sin(pi*x)"),
            variable = "x",
            length = 1.0,
            spaceSteps = 60,
            timeSteps = 60,
            timeEnd = 0.2,
            left = 0.0,
            right = 0.0,
            alpha = 0.1,
            speed = speed,
        )!!
        val lastTime = solution.times.last()
        val middle = solution.x.indices.first { abs(solution.x[it] - 0.5) < 0.02 }
        val expected = sin(PI * solution.x[middle]) * cos(speed * PI * lastTime)
        assertEquals(
            "the numerical solution should match the exact one",
            expected,
            solution.values.last()[middle],
            5e-3,
        )
    }

    @Test fun theWaveSolutionWithAVelocityMatchesTheExactOne() {
        // With u(x, 0) = sin(πx) and u_t(x, 0) = 2·sin(πx), the exact solution
        // is u(x, t) = sin(πx)·cos(cπt) + (2 / cπ)·sin(cπt). The velocity has
        // to have the same shape as the displacement, or the boundary
        // conditions and the initial data would not agree.
        val speed = 1.0
        val velocity = 2.0
        val solution = PdeEngine.solve1D(
            kind = PdeKind.WAVE,
            initial = parse("sin(pi*x)"),
            variable = "x",
            length = 1.0,
            spaceSteps = 60,
            timeSteps = 60,
            timeEnd = 0.2,
            left = 0.0,
            right = 0.0,
            alpha = 0.1,
            speed = speed,
            initialVelocity = parse("2*sin(pi*x)"),
        )!!
        val lastTime = solution.times.last()
        val middle = solution.x.indices.first { abs(solution.x[it] - 0.5) < 0.02 }
        val x = solution.x[middle]
        val expected = sin(PI * x) * cos(speed * PI * lastTime) +
            (velocity / (speed * PI)) * sin(speed * PI * lastTime)
        assertEquals(
            "the numerical solution should match the exact one",
            expected,
            solution.values.last()[middle],
            5e-3,
        )
    }

    @Test fun theWaveEquationStartsAtRestWithoutAVelocity() {
        val speed = 1.0
        val timeSteps = 10
        val solution = PdeEngine.solve1D(
            kind = PdeKind.WAVE,
            initial = parse("sin(pi*x)"),
            variable = "x",
            length = 1.0,
            spaceSteps = 40,
            timeSteps = timeSteps,
            timeEnd = 0.05,
            left = 0.0,
            right = 0.0,
            alpha = 0.1,
            speed = speed,
        )!!
        // The rows are the initial condition and one for each step after it.
        assertEquals(timeSteps + 1, solution.values.size)
        assertEquals(timeSteps + 1, solution.times.size)
        // With no velocity the first step is the second-order expansion of a
        // wave that starts at rest.
        val dx = 1.0 / 40
        val dt = 0.05 / timeSteps
        val s = speed * dt / dx
        val x = solution.x[20]
        val expected = sin(PI * x) + (s * s / 2) * (
            sin(PI * (x + dx)) - 2 * sin(PI * x) + sin(PI * (x - dx))
            )
        assertEquals(expected, solution.values[1][20], 1e-9)
        assertTrue(
            "the steps should say the wave starts at rest",
            solution.steps.any { it.contains("at rest") },
        )
    }

    @Test fun theStabilityConditionIsChecked() {
        // A large step in time breaks the stability condition.
        val unstable = PdeEngine.solve1D(
            kind = PdeKind.HEAT,
            initial = parse("sin(pi*x)"),
            variable = "x",
            length = 1.0,
            spaceSteps = 10,
            timeSteps = 10,
            timeEnd = 1.0,
            left = 0.0,
            right = 0.0,
            alpha = 1.0,
            speed = 1.0,
        )!!
        assertTrue("the scheme should be unstable", !unstable.stability.stable)
        assertTrue("the reason should be given", unstable.stability.explanation.isNotBlank())
        assertTrue(
            "the steps should say the numbers would blow up",
            unstable.steps.any { it.contains("blow up") },
        )
    }

    @Test fun aStableChoiceIsAccepted() {
        val stable = PdeEngine.solve1D(
            kind = PdeKind.HEAT,
            initial = parse("sin(pi*x)"),
            variable = "x",
            length = 1.0,
            spaceSteps = 40,
            timeSteps = 40,
            timeEnd = 0.05,
            left = 0.0,
            right = 0.0,
            alpha = 0.01,
            speed = 1.0,
        )!!
        assertTrue("the scheme should be stable", stable.stability.stable)
    }

    @Test fun laplacesEquationWithNoSourceIsZero() {
        val solution = PdeEngine.solve2D(
            kind = PdeKind.LAPLACE,
            boundary = parse("0"),
            source = null,
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = 12,
            iterations = 100,
        )!!
        for (row in solution.values) {
            for (value in row) {
                assertEquals(0.0, value, 1e-12)
            }
        }
    }

    @Test fun laplacesEquationWithAStraightBoundaryIsHarmonic() {
        // With the boundary value u = y on the square [0, 1]², the exact
        // solution is u = y, because the Laplacian of a linear function is
        // zero.
        val solution = PdeEngine.solve2D(
            kind = PdeKind.LAPLACE,
            boundary = parse("y"),
            source = null,
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = 21,
            iterations = 3000,
        )!!
        for (row in solution.values.indices) {
            for (column in solution.values[row].indices) {
                assertEquals(
                    "the value at (${solution.x[column]}, ${solution.y[row]}) should be y",
                    solution.y[row],
                    solution.values[row][column],
                    2e-3,
                )
            }
        }
    }

    @Test fun thePoissonSourcePushesTheSolutionTheRightWay() {
        // With a positive source the solution is larger inside than the
        // boundary value.
        val solution = PdeEngine.solve2D(
            kind = PdeKind.POISSON,
            boundary = parse("0"),
            source = parse("1"),
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = 15,
            iterations = 2000,
        )!!
        // With the convention ∇²u = f, a positive source pushes the solution
        // the other way, so the interior is negative.
        val centre = solution.values[7][7]
        assertTrue("the centre should be negative, but it was $centre", centre < 0.0)
        val corner = solution.values[0][0]
        assertEquals("the boundary should be untouched", 0.0, corner, 1e-12)
    }

    @Test fun theSecondOrderEquationsAreClassified() {
        // Laplace's equation is elliptic.
        assertEquals(
            PdeClassification.Kind.ELLIPTIC,
            PdeClassification.classify(1.0, 0.0, 1.0),
        )
        // The heat equation, uₜ = α·uₓₓ, is uₓₓ − uₜₜ = 0 in the form
        // A·u_xx + C·u_tt, so it is parabolic.
        assertEquals(
            PdeClassification.Kind.PARABOLIC,
            PdeClassification.classify(1.0, 0.0, 0.0),
        )
        // The wave equation, uₜₜ = c²·uₓₓ, is −c²·u_xx + u_tt = 0, and the
        // discriminant 4c² is positive, so it is hyperbolic.
        assertEquals(
            PdeClassification.Kind.HYPERBOLIC,
            PdeClassification.classify(-1.0, 0.0, 1.0),
        )
    }

    @Test fun theHeatFieldVariesAlongTheRodAndOverTime() {
        // The initial temperature is sin(pi*x) on [0, 1], with both ends held
        // at zero. The field must therefore show a hump that spreads out and
        // dies away, not a flat sheet.
        val initial = CalculusParser.parse("sin(pi*x)")
        assertTrue(initial is CalcResult.Ok)
        val node = (initial as CalcResult.Ok).value

        val answer = PdeEngine.solve1D(
            kind = PdeKind.HEAT,
            initial = node,
            variable = "x",
            length = 1.0,
            spaceSteps = 40,
            timeSteps = 200,
            timeEnd = 0.5,
            left = 0.0,
            right = 0.0,
            alpha = 0.1,
            speed = 1.0,
        )
        assertNotNull("the heat equation should be solvable", answer)
        val solution = answer!!

        // The first row is the initial temperature, which is not flat.
        val first = solution.values.first()
        assertTrue(
            "the initial temperature must vary along the rod",
            first.max() - first.min() > 0.5,
        )
        // The maximum is in the middle of the rod.
        val middle = first.size / 2
        assertTrue(
            "the middle of the rod must be the warmest",
            first[middle] > first[0] && first[middle] > first[first.size - 1],
        )

        // The last row is later, so the hump has spread and died down.
        val last = solution.values.last()
        assertTrue(
            "the temperature must fall over time",
            last.max() < first.max(),
        )
        assertTrue(
            "the temperature must still vary along the rod",
            last.max() - last.min() > 0.05,
        )
        // The ends are held at zero throughout.
        for (row in solution.values) {
            assertEquals("the left end is held at zero", 0.0, row[0], 1e-12)
            assertEquals("the right end is held at zero", 0.0, row[row.size - 1], 1e-12)
        }
        // And the times run from zero to the end.
        assertEquals(0.0, solution.times.first(), 1e-12)
        assertEquals(0.5, solution.times.last(), 1e-9)
    }

    @Test fun theDefaultsOfTheToolGiveAStableHeatScheme() {
        // The tool opens with these numbers, so the picture it shows on the
        // first screen must be a real one and not a blown-up one.
        val initial = CalculusParser.parse("sin(pi*x)")
        assertTrue(initial is CalcResult.Ok)
        val node = (initial as CalcResult.Ok).value

        val answer = PdeEngine.solve1D(
            kind = PdeKind.HEAT,
            initial = node,
            variable = "x",
            length = 1.0,
            spaceSteps = 40,
            timeSteps = 200,
            timeEnd = 0.5,
            left = 0.0,
            right = 0.0,
            alpha = 0.1,
            speed = 1.0,
        )
        assertNotNull("the heat equation should be solvable", answer)
        val solution = answer!!

        assertTrue(
            "the heat scheme must be stable with the tool's own numbers, but the " +
                "stability number was ${solution.stability.courant}",
            solution.stability.stable,
        )
        // The temperature falls and spreads, and the field is finite
        // everywhere.
        assertTrue(
            "the temperature must fall over time",
            solution.values.last().max() < solution.values.first().max(),
        )
        for (row in solution.values) {
            for (value in row) {
                assertTrue("the field must stay finite", value.isFinite())
            }
        }
    }

    @Test fun theWaveSchemeIsStableWithTheToolsOwnNumbers() {
        val initial = CalculusParser.parse("sin(pi*x)")
        assertTrue(initial is CalcResult.Ok)
        val node = (initial as CalcResult.Ok).value

        val answer = PdeEngine.solve1D(
            kind = PdeKind.WAVE,
            initial = node,
            variable = "x",
            length = 1.0,
            spaceSteps = 40,
            timeSteps = 200,
            timeEnd = 0.5,
            left = 0.0,
            right = 0.0,
            alpha = 0.1,
            speed = 1.0,
            initialVelocity = CalculusParser.parse("0").let {
                (it as CalcResult.Ok).value
            },
        )
        assertNotNull("the wave equation should be solvable", answer)
        val solution = answer!!

        assertTrue(
            "the wave scheme must be stable with the tool's own numbers, but the " +
                "stability number was ${solution.stability.courant}",
            solution.stability.stable,
        )
        for (row in solution.values) {
            for (value in row) {
                assertTrue("the field must stay finite", value.isFinite())
            }
        }
    }

    @Test fun thePoissonFieldIsTheKnownAnswer() {
        // The square [0, 1]² with every edge held at zero and a source of 1
        // at every interior point has the exact solution
        //
        //     u = (x² + y² − 2x − 2y + 2) ÷ 8,
        //
        // because the Laplacian of that is 1 everywhere inside and it is zero
        // on all four edges. The solver must reproduce it.
        val boundary = CalculusParser.parse("0")
        assertTrue(boundary is CalcResult.Ok)
        val source = CalculusParser.parse("1")
        assertTrue(source is CalcResult.Ok)

        val answer = PdeEngine.solve2D(
            kind = PdeKind.POISSON,
            boundary = (boundary as CalcResult.Ok).value,
            source = (source as CalcResult.Ok).value,
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = 30,
            iterations = 5000,
        )
        assertNotNull("the Poisson equation should be solvable", answer)
        val solution = answer!!

        // The middle of the square, where the answer is largest.
        //
        // The problem is the classic one: the torsion function of the unit
        // square, whose value at the middle is about 0.0737. Here the sign is
        // the other way round, because the scheme solves Δu = +f rather than
        // −Δu = f.
        val middle = solution.x.size / 2
        val computed = solution.values[middle][middle]
        assertEquals(
            "the middle of the square must match the known answer",
            -0.0737,
            computed,
            0.002,
        )

        // The real check: at an interior point the discrete Laplacian must
        // equal the source, which is what the equation says.
        val i = solution.values.size / 2
        val j = solution.values[i].size / 2
        val h = 1.0 / (solution.x.size - 1)
        val laplacian = (
            solution.values[i - 1][j] + solution.values[i + 1][j] +
                solution.values[i][j - 1] + solution.values[i][j + 1] -
                4 * solution.values[i][j]
            ) / (h * h)
        assertEquals(
            "the Laplacian must equal the source at an interior point",
            1.0,
            laplacian,
            0.05,
        )

        // The edges are held at zero.
        for (i in solution.values.indices) {
            assertEquals("the left edge is held at zero", 0.0, solution.values[i][0], 1e-12)
            assertEquals("the right edge is held at zero", 0.0, solution.values[i][solution.values[i].size - 1], 1e-12)
        }
        for (j in solution.values.first().indices) {
            assertEquals("the bottom edge is held at zero", 0.0, solution.values[0][j], 1e-12)
            assertEquals("the top edge is held at zero", 0.0, solution.values[solution.values.size - 1][j], 1e-12)
        }
    }

    @Test fun theLaplaceFieldIsHarmonic() {
        // With no source and a boundary that is not zero, the value at every
        // interior point is the average of its four neighbours.
        val boundary = CalculusParser.parse("x + y")
        assertTrue(boundary is CalcResult.Ok)

        val answer = PdeEngine.solve2D(
            kind = PdeKind.LAPLACE,
            boundary = (boundary as CalcResult.Ok).value,
            source = null,
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = 20,
            iterations = 5000,
        )
        assertNotNull("Laplace's equation should be solvable", answer)
        val solution = answer!!

        // The field must not be flat: with the edges held at x + y there is a
        // difference across the square.
        val all = solution.values.flatten()
        assertTrue("the field must vary", all.max() - all.min() > 0.5)

        // The harmonic condition holds at an interior point.
        val i = solution.values.size / 2
        val j = solution.values[i].size / 2
        val neighbours = (
            solution.values[i - 1][j] + solution.values[i + 1][j] +
                solution.values[i][j - 1] + solution.values[i][j + 1]
            ) / 4
        assertEquals(
            "the value must be the average of the neighbours",
            neighbours,
            solution.values[i][j],
            1e-6,
        )
    }
}
