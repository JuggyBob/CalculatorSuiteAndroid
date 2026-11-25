package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions in the numerical ODE stepping. */
class NumericalOdeSolverRegressionTest {

    private fun condition(order: Int, at: Double, value: Double) =
        OdeCondition(order, at, value, "y($at) = $value")

    @Test
    fun eulerStepsKeepTheXAndYColumnsSeparate() {
        val result = OdeSolver.solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(
                    from = 0.0, to = 1.0, step = 0.1, method = NumericalMethod.EULER,
                ),
            ),
        )
        assertTrue("expected a solution but got $result", result is OdeResult.Solved)
        val points = (result as OdeResult.Solved).solution.numerical!!.points
        // y' = 2y with Euler: y_{n+1} = y_n + 0.1·2·y_n = 1.2·y_n.
        assertEquals(0.1, points[1].x, 1e-12)
        assertEquals(1.2, points[1].y, 1e-12)
        assertEquals(0.2, points[2].x, 1e-12)
        assertEquals(1.44, points[2].y, 1e-12)
        assertEquals(1.0, points.last().x, 1e-12)
    }

    @Test
    fun heunStepsKeepTheXAndYColumnsSeparate() {
        val result = OdeSolver.solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(
                    from = 0.0, to = 1.0, step = 0.1, method = NumericalMethod.HEUN,
                ),
            ),
        )
        assertTrue("expected a solution but got $result", result is OdeResult.Solved)
        val points = (result as OdeResult.Solved).solution.numerical!!.points
        // y' = 2y, h = 0.1: k1 = 2y, predicted y = 1.2y, k2 = 2.4y, so
        // y_{n+1} = y_n + 0.1·(k1 + k2)/2 = 1.22·y_n. The x column must still
        // advance by h, not hold the corrected y.
        assertEquals(0.0, points[0].x, 1e-12)
        assertEquals(0.1, points[1].x, 1e-12)
        assertEquals(1.22, points[1].y, 1e-12)
        assertEquals(0.2, points[2].x, 1e-12)
        assertEquals(1.4884, points[2].y, 1e-12)
        assertEquals(1.0, points.last().x, 1e-12)
    }
}
