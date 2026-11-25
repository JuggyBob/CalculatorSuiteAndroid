package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Regressions found while auditing the numerical solvers and explanations.
 */
class AdvancedSolverRegressionTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    private fun matrix(vararg rows: DoubleArray): Matrix {
        val m = Matrix(rows.size, rows[0].size)
        for (i in rows.indices) for (j in rows[0].indices) m[i, j] = rows[i][j]
        return m
    }

    private fun heat(alpha: Double, length: Double = 1.0) = PdeEngine.solve1D(
        kind = PdeKind.HEAT,
        initial = parse("sin(pi*x)"),
        variable = "x",
        length = length,
        spaceSteps = 40,
        timeSteps = 20,
        timeEnd = 0.1,
        left = 0.0,
        right = 0.0,
        alpha = alpha,
        speed = 1.0,
    )

    @Test fun nonFiniteHeatInputsAreRejected() {
        assertNull(heat(Double.NaN))
        assertNull(heat(0.1, Double.NaN))
        assertNull(heat(Double.POSITIVE_INFINITY))
    }

    @Test fun aNegativeDiffusivityIsRejected() {
        assertNull(heat(-0.1))
    }

    @Test fun nonFiniteTwoDimensionalInputsAreRejected() {
        val boundary = parse("0")
        assertNull(
            PdeEngine.solve2D(
                PdeKind.LAPLACE, boundary, null, "x", "y",
                Double.NaN, 9, 100,
            ),
        )
        assertNull(
            PdeEngine.solve2D(
                PdeKind.LAPLACE, boundary, null, "x", "y",
                1.0, 9, 100, Double.NaN,
            ),
        )
    }

    @Test fun aNonFiniteBoundaryDoesNotPoisonTheField() {
        val solution = PdeEngine.solve2D(
            PdeKind.LAPLACE, parse("1e308*1e308"), null, "x", "y",
            1.0, 9, 100,
        )
        assertNotNull(solution)
        for (row in solution!!.values) {
            for (value in row) assertEquals(0.0, value, 1e-12)
        }
    }

    @Test fun nonFiniteCoefficientsAreNotClassified() {
        assertEquals(
            PdeClassification.Kind.UNKNOWN,
            PdeClassification.classify(Double.NaN, 0.0, 1.0),
        )
        assertEquals(
            PdeClassification.Kind.UNKNOWN,
            PdeClassification.classify(Double.POSITIVE_INFINITY, 0.0, 1.0),
        )
    }

    @Test fun theFourierExplanationMatchesTheMethodUsed() {
        val result = FourierOperations.series(parse("sin(x)"), "x", 2 * PI, 3)
        assertNotNull(result)
        val method = result!!.explanation.method!!
        assertTrue(method.contains("fixed composite Simpson"))
        assertFalse(method.contains("adaptive"))
    }

    @Test fun aSeriesWithAnUnusableIntegrandIsRejected() {
        assertNull(FourierEngine.series(parse("1e308*1e308"), "x", 2 * PI, 1))
    }

    @Test fun theTransformExplanationNamesTheBin() {
        val result = FourierOperations.transform(doubleArrayOf(1.0, 0.0, -1.0, 0.0), 1.0)
        assertTrue(
            "the strongest bin is bin 1, not the second harmonic",
            result.explanation.steps.any { it.contains("bin 1") },
        )
    }

    @Test fun smallMatricesKeepTheirRankAndInverse() {
        val small = matrix(doubleArrayOf(1e-12, 0.0), doubleArrayOf(0.0, 1e-12))
        assertEquals(2, small.rank())
        assertEquals(1e-24, small.determinant(), 1e-30)
        assertNotNull(small.inverse())
        assertNotNull(small.solve(doubleArrayOf(1e-12, 2e-12)))
    }

    @Test fun smallVectorsCanBeProjectedAndOrthonormalised() {
        val projection = LinearAlgebra.projectOnto(
            doubleArrayOf(3e-10, 4e-10), doubleArrayOf(1e-10, 0.0),
        )
        assertNotNull(projection)
        assertEquals(3e-10, projection!![0], 1e-20)
        assertEquals(0.0, projection[1], 1e-20)

        val basis = LinearAlgebra.gramSchmidt(
            listOf(doubleArrayOf(1e-11, 0.0), doubleArrayOf(0.0, 1.0)),
        )
        assertEquals(2, basis.size)
        assertTrue(LinearAlgebra.isOrthonormal(basis))

        val angle = LinearAlgebra.angle(
            doubleArrayOf(1e-10, 0.0), doubleArrayOf(0.0, 1e-10),
        )
        assertNotNull(angle)
        assertEquals(PI / 2, angle!!, 1e-12)
    }
}
