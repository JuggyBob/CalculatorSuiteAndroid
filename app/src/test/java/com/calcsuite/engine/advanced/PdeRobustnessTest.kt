package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Regressions for the finite-difference work limits.
 */
class PdeRobustnessTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    private fun heat(
        alpha: Double = 0.1,
        length: Double = 1.0,
        spaceSteps: Int = 40,
        timeSteps: Int = 200,
        timeEnd: Double = 0.5,
    ) = PdeEngine.solve1D(
        kind = PdeKind.HEAT,
        initial = parse("sin(pi*x)"),
        variable = "x",
        length = length,
        spaceSteps = spaceSteps,
        timeSteps = timeSteps,
        timeEnd = timeEnd,
        left = 0.0,
        right = 0.0,
        alpha = alpha,
        speed = 1.0,
    )

    private fun laplace(gridPoints: Int, iterations: Int, tolerance: Double = 1e-8) =
        PdeEngine.solve2D(
            kind = PdeKind.LAPLACE,
            boundary = parse("0"),
            source = null,
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = gridPoints,
            iterations = iterations,
            tolerance = tolerance,
        )

    @Test fun zeroOrNegativeToleranceIsRejected() {
        assertNull("tolerance zero can never stop the sweeps", laplace(10, 100, 0.0))
        assertNull(laplace(10, 100, -1e-9))
        assertNull(laplace(10, 100, Double.NaN))
        assertNotNull("the default tolerance is unchanged", laplace(10, 100))
    }

    @Test fun unboundedTwoDimensionalWorkIsRejected() {
        assertNull(
            "a 400 by 400 grid with 200 000 sweeps would need 31 billion updates",
            laplace(PdeEngine.MAX_POINTS, 200_000),
        )
        assertNull(laplace(10, PdeEngine.MAX_ITERATIONS + 1))
        assertNotNull(
            "the largest grid with its usual sweep count is still available",
            laplace(PdeEngine.MAX_POINTS, 200),
        )
    }

    @Test fun oneDimensionalWorkIsBounded() {
        assertNull(heat(spaceSteps = PdeEngine.MAX_POINTS, timeSteps = PdeEngine.MAX_TIME_STEPS))
        assertNull(heat(spaceSteps = PdeEngine.MAX_POINTS, timeSteps = 1000))
        assertNotNull("the tool's own defaults are still accepted", heat())
    }

    @Test fun nonFiniteCourantNumbersAreRejected() {
        // alpha·dt divided by dx² overflows in both cases.
        assertNull(heat(alpha = 1e308, timeEnd = 1.0))
        assertNull(heat(length = 1e-300))
        assertNotNull(heat())
    }

    @Test fun theDefaultNumericalAnswersAreUnchanged() {
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
            alpha = 0.05,
            speed = 1.0,
        )!!
        val middle = solution.x.indices.first { abs(solution.x[it] - 0.5) < 0.02 }
        val expected = sin(PI * solution.x[middle]) *
            exp(-0.05 * PI * PI * solution.times.last())
        assertEquals(expected, solution.values.last()[middle], 2e-3)

        val poisson = PdeEngine.solve2D(
            kind = PdeKind.POISSON,
            boundary = parse("0"),
            source = parse("1"),
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = 30,
            iterations = 5000,
        )!!
        assertEquals("the known torsion value", -0.0737, poisson.values[15][15], 0.002)

        val wave = PdeEngine.solve1D(
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
            speed = 1.0,
        )!!
        val waveMiddle = wave.x.indices.first { abs(wave.x[it] - 0.5) < 0.02 }
        assertEquals(
            sin(PI * wave.x[waveMiddle]) * cos(PI * wave.times.last()),
            wave.values.last()[waveMiddle],
            5e-3,
        )
    }
}
