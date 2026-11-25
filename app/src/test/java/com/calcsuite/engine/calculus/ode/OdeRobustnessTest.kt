package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hostile ODE inputs must be refused, not parsed into enormous work. */
class OdeRobustnessTest {

    private fun condition(order: Int, at: Double, value: Double) =
        OdeCondition(order, at, value, "y($at) = $value")

    @Test(timeout = 5000)
    fun aMillionthOrderDerivativeIsRejected() {
        val text = "d1000000y/dx1000000 = 0"
        assertTrue("the parser must refuse the order", OdeParser.parse(text) is CalcResult.Err)
        val result = OdeSolver.solve(OdeRequest(text))
        assertTrue("the solver must refuse the order but got $result", result is OdeResult.Invalid)
        assertTrue(
            "the message should name the limit: ${(result as OdeResult.Invalid).message}",
            result.message.contains("10"),
        )
    }

    @Test(timeout = 5000)
    fun aLongPrimeChainIsRejected() {
        val text = "y" + "'".repeat(11) + " = 0"
        assertTrue(OdeParser.parse(text) is CalcResult.Err)
    }

    @Test(timeout = 5000)
    fun anOverlongEquationIsRejectedBeforeTheRegexRewrites() {
        val text = "y' = " + "1+".repeat(5000)
        assertTrue("the equation must be refused", OdeParser.parse(text) is CalcResult.Err)
        assertTrue(
            "an order limit before the regex should be reported",
            try {
                OdeParser.rewriteDerivatives("d1000000y/dx1000000 = 0")
                false
            } catch (e: OdeParseException) {
                true
            },
        )
    }

    @Test(timeout = 5000)
    fun deeplyNestedOdeIsRejected() {
        val deep = "(".repeat(5000) + "1" + ")".repeat(5000)
        assertTrue(OdeParser.parse("y' = $deep") is CalcResult.Err)
        val result = OdeSolver.solve(OdeRequest("y' = $deep"))
        assertTrue("the solver must refuse a deep equation", result is OdeResult.Invalid)
    }

    @Test(timeout = 5000)
    fun aFarInitialConditionIsRejectedWithoutRunningMillionsOfSteps() {
        val result = OdeSolver.solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, -1e9, 1.0)),
                numerical = NumericalSettings(from = 0.0, to = 5.0, step = 0.1),
            ),
        )
        assertTrue("the far condition must be refused but got $result", result is OdeResult.Invalid)
        assertTrue(
            "the message should mention steps",
            (result as OdeResult.Invalid).message.contains("steps"),
        )

        val parsed = (OdeParser.parse("y' = 2y") as CalcResult.Ok).value
        val direct = NumericalOdeSolver.solve(
            parsed, NumericalMethod.RK4, -1e9, 1.0, 5.0, 0.1,
        )
        assertEquals("no steps should have been taken", 1, direct.points.size)
    }

    @Test(timeout = 5000)
    fun anOverflowingStepIsAnInvalidResultNotAnInfinitePoint() {
        val parsed = (OdeParser.parse("y' = y") as CalcResult.Ok).value
        for (method in listOf(NumericalMethod.EULER, NumericalMethod.HEUN, NumericalMethod.RK4)) {
            val result = OdeSolver.solve(
                OdeRequest(
                    "y' = y",
                    mode = SolveMode.NUMERICAL,
                    // k1 is finite (1e308), but y + h·k1 overflows for h = 10.
                    conditions = listOf(condition(0, 0.0, 1e308)),
                    numerical = NumericalSettings(
                        from = 0.0, to = 20.0, step = 10.0, method = method,
                    ),
                ),
            )
            assertTrue("$method must not claim a solution but got $result", result is OdeResult.Invalid)
            assertEquals(NumericalOdeSolver.OVERFLOW_MESSAGE, (result as OdeResult.Invalid).message)

            val direct = NumericalOdeSolver.solve(parsed, method, 0.0, 1e308, 20.0, 10.0)
            assertEquals("$method should record the overflow", NumericalOdeSolver.OVERFLOW_MESSAGE, direct.failure)
            assertTrue(
                "$method published a non-finite point",
                direct.points.all { it.x.isFinite() && it.y.isFinite() },
            )
        }
    }

    @Test(timeout = 5000)
    fun anOrdinaryNumericalSolutionIsUnchanged() {
        val result = OdeSolver.solve(
            OdeRequest(
                "y' = y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(
                    from = 0.0, to = 1.0, step = 0.1, method = NumericalMethod.RK4,
                ),
            ),
        )
        assertTrue("expected a solution but got $result", result is OdeResult.Solved)
        val numerical = (result as OdeResult.Solved).solution.numerical!!
        assertNull("a normal run must not carry a failure", numerical.failure)
        assertTrue(numerical.points.all { it.x.isFinite() && it.y.isFinite() })
        val finalY = numerical.points.last().y
        // RK4 is O(h⁴): at h = 0.1 the truncation error for y' = y over [0, 1]
        // is about 2.1e-6, so 1e-6 against the exact e was too tight. 1e-5 is
        // still far below Euler's (~1.2e-1) and Heun's (~4.2e-3) error here.
        assertEquals(Math.exp(1.0), finalY, 1e-5)

        // Pin the method itself with the textbook RK4 recurrence, computed
        // independently of the engine; this tolerance cannot hide a different
        // method even though it is much tighter than the exact-solution check.
        var reference = 1.0
        repeat(10) {
            val k1 = reference
            val k2 = reference + 0.05 * k1
            val k3 = reference + 0.05 * k2
            val k4 = reference + 0.1 * k3
            reference += (0.1 / 6.0) * (k1 + 2 * k2 + 2 * k3 + k4)
        }
        assertEquals(reference, finalY, 1e-12)
    }
}
