package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

/**
 * Tests for the differential equations solver.
 *
 * The checks are on the actual mathematics — the expression that comes back,
 * the constant that was solved for, the residual of the verification and the
 * error of the numerical method — rather than on whether an object came back
 * at all.
 */
class OdeSolverTest {

    private fun solve(request: OdeRequest): OdeSolution {
        val result = OdeSolver.solve(request)
        assertTrue(
            "expected a solution but got $result",
            result is OdeResult.Solved,
        )
        return (result as OdeResult.Solved).solution
    }

    private fun condition(order: Int, at: Double, value: Double) =
        OdeCondition(order, at, value, "y($at) = $value")

    // --------------------------------------------------------- separable

    @Test
    fun separableEquationGivesTheGeneralSolution() {
        val solution = solve(OdeRequest("y' = 2y"))
        assertEquals(OdeMethod.SEPARABLE, solution.method)
        assertEquals("y = C·e^(2x)", solution.generalSolution)
        assertNull("no condition was given, so there is no particular solution", solution.particularSolution)
        assertTrue(solution.steps.any { it.contains("separates") })
    }

    @Test
    fun theInitialConditionDeterminesTheConstant() {
        val solution = solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.INITIAL_VALUE,
                conditions = listOf(condition(0, 0.0, 3.0)),
            ),
        )
        assertEquals("y = 3·e^(2x)", solution.particularSolution)
        assertEquals(listOf("C" to "3"), solution.constants)
    }

    @Test
    fun theSolvedSolutionReallyIsASolution() {
        val solution = solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.INITIAL_VALUE,
                conditions = listOf(condition(0, 0.0, 3.0)),
            ),
        )
        val verification = solution.verification
        assertNotNull("the answer should be verifiable", verification)
        assertTrue("the residual should vanish", verification!!.verified)
    }

    @Test
    fun aSeparableEquationInXAndYIsSolved() {
        val solution = solve(OdeRequest("y' = x*y"))
        assertEquals(OdeMethod.SEPARABLE, solution.method)
        // y = C·e^(x²/2)
        assertTrue(
            "expected an exponential of x²/2 but got ${solution.generalSolution}",
            solution.generalSolution!!.contains("e^(x^2"),
        )
    }

    @Test
    fun theConstantSolutionsLostByDividingAreReported() {
        // y' = y(1 - y) loses the solutions y = 0 and y = 1.
        val solution = solve(OdeRequest("y' = y*(1 - y)"))
        val text = solution.steps.joinToString(" ")
        assertTrue(
            "the constant solutions should be called out, but the steps were: $text",
            text.contains("constant solutions"),
        )
        assertTrue("y = 0 should be mentioned", text.contains("0"))
    }

    // ----------------------------------------------------- first-order linear

    @Test
    fun firstOrderLinearEquationIsSolvedByTheIntegratingFactor() {
        val solution = solve(OdeRequest("y' + y = x"))
        assertEquals(OdeMethod.FIRST_ORDER_LINEAR, solution.method)
        // y = -1 + x + C·e^(-x)
        val general = solution.generalSolution!!
        assertTrue("expected -1 + x in $general", general.contains("-1 + x"))
        assertTrue("expected e^(\u2212x) in $general", general.contains("e^(\u2212x)"))
        assertTrue(
            "the integrating factor should be shown",
            solution.steps.any { it.contains("integrating factor") },
        )
    }

    @Test
    fun theFirstOrderLinearConstantIsSolvedFor() {
        // y = -1 + x + C·e^(-x); at x = 0 this is -1 + C, so C = 4 for y(0) = 3.
        val solution = solve(
            OdeRequest(
                "y' + y = x",
                mode = SolveMode.INITIAL_VALUE,
                conditions = listOf(condition(0, 0.0, 3.0)),
            ),
        )
        assertEquals(1, solution.constants.size)
        assertEquals("4", solution.constants.first().second)
    }

    // -------------------------------------------- constant coefficient equations

    @Test
    fun secondOrderConstantCoefficientEquationIsSolved() {
        val solution = solve(OdeRequest("y'' - 3y' + 2y = 0"))
        assertEquals(OdeMethod.CONSTANT_COEFFICIENT, solution.method)
        // The roots are 1 and 2, so y = C₁·e^(x) + C₂·e^(2x).
        val general = solution.generalSolution!!
        assertTrue("expected e^(x) in $general", general.contains("e^(x)"))
        assertTrue("expected e^(2x) in $general", general.contains("e^(2x)"))
        assertTrue(
            "the characteristic equation should be shown",
            solution.steps.any { it.contains("Characteristic equation: x^2 \u2212 3x + 2 = 0") },
        )
    }

    @Test
    fun aRepeatedCharacteristicRootGivesTheExtraTerm() {
        val solution = solve(OdeRequest("y'' - 2y' + y = 0"))
        // The root 1 has multiplicity two, so y = C₁·e^(x) + C₂·x·e^(x).
        val general = solution.generalSolution!!
        assertTrue("expected x·e^(x) in $general", general.contains("x·e^(x)"))
        assertTrue(
            "the multiplicity should be mentioned",
            solution.steps.any { it.contains("multiplicity 2") },
        )
    }

    @Test
    fun complexRootsGiveTrigonometricTerms() {
        val solution = solve(OdeRequest("y'' + y = 0"))
        val general = solution.generalSolution!!
        assertTrue("expected cos(x) in $general", general.contains("cos(x)"))
        assertTrue("expected sin(x) in $general", general.contains("sin(x)"))
    }

    @Test
    fun aPolynomialForcingTermGivesAParticularSolution() {
        // y'' - 3y' + 2y = 1 has the particular solution y = 1/2.
        val solution = solve(OdeRequest("y'' - 3y' + 2y = 1"))
        val steps = solution.steps.joinToString(" ")
        assertTrue(
            "a particular solution should have been produced, but the steps were: $steps",
            steps.contains("A particular solution by undetermined coefficients"),
        )
        assertTrue("the particular solution is 1/2, but the steps were: $steps", steps.contains("y_p = 1/2"))
    }

    @Test
    fun theSecondOrderConstantsAreSolvedFor() {
        // y = C₁·e^(x) + C₂·e^(2x), with y(0) = 1 and y'(0) = 0 gives C₁ = 2, C₂ = -1.
        val solution = solve(
            OdeRequest(
                "y'' - 3y' + 2y = 0",
                mode = SolveMode.INITIAL_VALUE,
                conditions = listOf(condition(0, 0.0, 1.0), condition(1, 0.0, 0.0)),
            ),
        )
        assertEquals(2, solution.constants.size)
        assertEquals("2", solution.constants[0].second)
        assertEquals("-1", solution.constants[1].second)
    }

    // ------------------------------------------------------ the failures

    @Test
    fun inconsistentConditionsAreReported() {
        val result = OdeSolver.solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.INITIAL_VALUE,
                conditions = listOf(condition(0, 0.0, 1.0), condition(0, 0.0, 2.0)),
            ),
        )
        assertTrue("the problem should be reported", result is OdeResult.Invalid)
        val message = (result as OdeResult.Invalid).message
        assertTrue("the message should explain the clash, but it said: $message", message.contains("disagree"))
    }

    @Test
    fun insufficientConditionsAreReported() {
        val result = OdeSolver.solve(
            OdeRequest(
                "y'' - 3y' + 2y = 0",
                mode = SolveMode.INITIAL_VALUE,
                conditions = listOf(condition(0, 0.0, 1.0)),
            ),
        )
        assertTrue("the problem should be reported", result is OdeResult.Invalid)
        val message = (result as OdeResult.Invalid).message
        assertTrue("the message should say what is missing, but it said: $message", message.contains("y′"))
    }

    @Test
    fun anUnsupportedEquationIsReportedWithAReason() {
        val result = OdeSolver.solve(OdeRequest("y' = x + y"))
        assertTrue("the equation should be reported as unsupported", result is OdeResult.Unsupported)
        val failure = (result as OdeResult.Unsupported).failure
        assertTrue(
            "the reason should name the shape of the equation, but it said: ${failure.message}",
            failure.message.contains("not separable"),
        )
        assertNotNull("a numerical route should be offered", failure.suggestion)
    }

    @Test
    fun invalidSyntaxIsReported() {
        val result = OdeSolver.solve(OdeRequest("y' = "))
        assertTrue("the syntax error should be reported", result is OdeResult.Invalid)
    }

    @Test
    fun anEquationWithNoEqualsSignIsReported() {
        val result = OdeSolver.solve(OdeRequest("y' + y"))
        assertTrue("the missing equals sign should be reported", result is OdeResult.Invalid)
        assertTrue(
            "the message should mention the equals sign",
            (result as OdeResult.Invalid).message.contains("equals sign"),
        )
    }

    // ------------------------------------------------------------- numerical

    @Test
    fun theNumericalSolutionMatchesTheKnownOne() {
        val solution = solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(from = 0.0, to = 1.0, step = 0.01),
            ),
        )
        val numerical = solution.numerical!!
        val last = numerical.points.last()
        // The exact answer is e^2.
        assertEquals(exp(2.0), last.y, 1e-6)
    }

    @Test
    fun rk4IsMoreAccurateThanEuler() {
        fun error(method: NumericalMethod): Double {
            val solution = solve(
                OdeRequest(
                    "y' = 2y",
                    mode = SolveMode.NUMERICAL,
                    conditions = listOf(condition(0, 0.0, 1.0)),
                    numerical = NumericalSettings(from = 0.0, to = 1.0, step = 0.1, method = method),
                ),
            )
            val last = solution.numerical!!.points.last()
            return abs(last.y - exp(2.0))
        }
        val euler = error(NumericalMethod.EULER)
        val heun = error(NumericalMethod.HEUN)
        val rk4 = error(NumericalMethod.RK4)
        assertTrue("RK4 ($rk4) should beat Euler ($euler)", rk4 < euler)
        assertTrue("Heun ($heun) should beat Euler ($euler)", heun < euler)
        assertTrue("Heun ($heun) should beat RK4 ($rk4)", rk4 < heun)
    }

    @Test
    fun theNumericalMethodIsNamedAndExplained() {
        val solution = solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(from = 0.0, to = 1.0, step = 0.25),
            ),
        )
        assertEquals(OdeMethod.NUMERICAL, solution.method)
        assertEquals(NumericalMethod.RK4, solution.numerical!!.method)
        val steps = solution.steps.joinToString(" ")
        assertTrue("the recurrence should be shown, but the steps were: $steps", steps.contains("k₁"))
        assertTrue("the first step should be worked out", steps.contains("The first step"))
    }

    @Test
    fun anInvalidNumericalRangeIsReported() {
        val zeroStep = OdeSolver.solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(from = 0.0, to = 1.0, step = 0.0),
            ),
        )
        assertTrue("a zero step should be reported", zeroStep is OdeResult.Invalid)
        assertTrue(
            "the message should mention the step size",
            (zeroStep as OdeResult.Invalid).message.contains("step size"),
        )

        val backwards = OdeSolver.solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(from = 1.0, to = 0.0, step = 0.1),
            ),
        )
        assertTrue("a backwards interval should be reported", backwards is OdeResult.Invalid)
    }

    @Test
    fun tooManyStepsIsRefused() {
        val result = OdeSolver.solve(
            OdeRequest(
                "y' = 2y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 1.0)),
                numerical = NumericalSettings(from = 0.0, to = 1000.0, step = 0.0001),
            ),
        )
        assertTrue("the request should be refused", result is OdeResult.Invalid)
        assertTrue(
            "the message should mention the number of steps",
            (result as OdeResult.Invalid).message.contains("steps"),
        )
    }

    @Test
    fun aSingularityIsReportedRatherThanCrashing() {
        // y' = 1/y is undefined at y = 0, which is where the solution starts.
        val result = OdeSolver.solve(
            OdeRequest(
                "y' = 1/y",
                mode = SolveMode.NUMERICAL,
                conditions = listOf(condition(0, 0.0, 0.0)),
                numerical = NumericalSettings(from = 0.0, to = 1.0, step = 0.1),
            ),
        )
        // The tool must not crash, and it must not claim a solution either.
        val points = (result as? OdeResult.Solved)?.solution?.numerical?.points?.size ?: 0
        assertTrue(
            "the singularity should be reported, but it said: $result",
            result is OdeResult.Invalid || result is OdeResult.Unsupported || points <= 1,
        )
    }

    @Test
    fun switchingToANewEquationDoesNotKeepTheOldAnswer() {
        val first = solve(OdeRequest("y' = 2y"))
        val second = solve(OdeRequest("y' = 3y"))
        assertTrue(first.generalSolution!!.contains("2x"))
        assertTrue(second.generalSolution!!.contains("3x"))
        assertTrue(
            "the second answer must not repeat the first",
            !second.generalSolution!!.contains("e^(2x)"),
        )
    }
}
