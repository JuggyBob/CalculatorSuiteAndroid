package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The system solver, checked against systems whose answers are known.
 */
class SystemSolverTest {

    private fun solve(
        first: String,
        second: String,
        method: SystemMethod = SystemMethod.Automatic,
    ): SystemAnswer {
        val result = SystemSolver.solve(first, second, method)
        assertTrue(
            "the system should be solved: " +
                ((result as? CalcResult.Err)?.error?.message ?: ""),
            result is CalcResult.Ok,
        )
        return (result as CalcResult.Ok).value
    }

    private fun assertClose(message: String, expected: Double, actual: Double) {
        assertEquals(message, expected, actual, 1e-8)
    }

    // ------------------------------------------------------- the quadratic case

    @Test fun aQuadraticSystemGivesBothRealSolutions() {
        // x² + y = 9 and x + y = 5.
        // The second equation gives y = 5 − x, and so x² − x − 4 = 0,
        // whose roots are (1 ± √17)/2.
        val answer = solve("x^2 + y = 9", "x + y = 5")
        assertEquals("there are two solutions", 2, answer.solutions.size)
        val root = Math.sqrt(17.0) / 2.0
        val expected = listOf(0.5 + root to 4.5 - root, 0.5 - root to 4.5 + root)
        for (solution in answer.solutions) {
            val match = expected.minByOrNull {
                kotlin.math.abs(it.first - solution.first.toDouble()) +
                    kotlin.math.abs(it.second - solution.second.toDouble())
            }
            assertNotNull("the solution should be one of the two", match)
            assertClose("x is right", match!!.first, solution.first.toDouble())
            assertClose("y is right", match.second, solution.second.toDouble())
        }
    }

    @Test fun theQuadraticSolutionsSatisfyBothEquations() {
        val answer = solve("x^2 + y = 9", "x + y = 5")
        for ((x, y) in answer.solutions) {
            val first = x.multiply(x).add(y)
            assertClose("the first equation holds", 9.0, first.toDouble())
            assertClose("the second equation holds", 5.0, x.add(y).toDouble())
        }
    }

    @Test fun theQuadraticSystemNamesBothSolutions() {
        val answer = solve("x^2 + y = 9", "x + y = 5")
        assertTrue("the answer says there are two", answer.message.contains("2 real solutions"))
        assertEquals("both solutions are verified", 2, answer.verification.size)
    }

    @Test fun anExplicitOneBeforeTheOtherIsReadTheSame() {
        val answer = solve("1x^2 + y = 9", "x + y = 5")
        assertEquals("there are two solutions", 2, answer.solutions.size)
    }

    @Test fun aCoefficientIsWrittenOutTheSameWayTheScreenDoesIt() {
        // The screen writes the system out from the six coefficients it asks
        // for: "2x + 3y = 12" and "1x + -1y = 1".
        val answer = solve("2x + 3y = 12", "1x + -1y = 1")
        assertEquals("there is one solution", 1, answer.solutions.size)
        assertClose("x is 3", 3.0, answer.solutions[0].first.toDouble())
        assertClose("y is 2", 2.0, answer.solutions[0].second.toDouble())
    }

    @Test fun aSystemWithOneRealSolutionGivesIt() {
        // (x − 1)² + y = 1 and x + y = 1.
        // The second equation gives y = 1 − x, so (x − 1)² = x, that is
        // x² − 3x + 1 = 0, whose roots are (3 ± √5)/2.
        val answer = solve("(x - 1)^2 + y = 1", "x + y = 1")
        assertEquals("there are two solutions", 2, answer.solutions.size)
        val root = Math.sqrt(5.0) / 2.0
        val expected = listOf(1.5 + root to 1.0 - (1.5 + root), 1.5 - root to 1.0 - (1.5 - root))
        for (solution in answer.solutions) {
            val match = expected.minByOrNull {
                kotlin.math.abs(it.first - solution.first.toDouble()) +
                    kotlin.math.abs(it.second - solution.second.toDouble())
            }
            assertNotNull("the solution should be one of the two", match)
            assertClose("x is right", match!!.first, solution.first.toDouble())
            assertClose("y is right", match.second, solution.second.toDouble())
        }
    }

    @Test fun aSystemWithNoRealSolutionSaysSo() {
        // x² + y = 0 and x + y = 0 give x² = x, which has real roots, so a
        // system with no real solution needs a different pair.
        // x² + y = 0 and x + y = 3 give x² − x + 3 = 0, whose discriminant
        // is 1 − 12, which is negative.
        val answer = solve("x^2 + y = 0", "x + y = 3")
        assertEquals("there is no real solution", SystemOutcome.NoRealSolution, answer.outcome)
        assertTrue("the answer says so", answer.solutions.isEmpty())
    }

    // --------------------------------------------------------- the linear cases

    @Test fun aLinearSystemGivesItsOneSolution() {
        val answer = solve("2x + 3y = 12", "x - y = 1")
        assertEquals("there is one solution", 1, answer.solutions.size)
        assertClose("x is 3", 3.0, answer.solutions[0].first.toDouble())
        assertClose("y is 2", 2.0, answer.solutions[0].second.toDouble())
    }

    @Test fun anInconsistentSystemSaysThereIsNoSolution() {
        val answer = solve("2x + 3y = 12", "4x + 6y = 13")
        assertEquals("there is no solution", SystemOutcome.NoRealSolution, answer.outcome)
    }

    @Test fun aDependentSystemSaysThereAreInfinitelyMany() {
        val answer = solve("2x + 3y = 12", "4x + 6y = 24")
        assertEquals("there are infinitely many", SystemOutcome.Infinite, answer.outcome)
    }

    @Test fun everyMethodGivesTheSameSolutionForALinearSystem() {
        val methods = listOf(
            SystemMethod.Cramer,
            SystemMethod.Substitution,
            SystemMethod.Elimination,
            SystemMethod.Comparison,
        )
        for (method in methods) {
            val answer = solve("2x + 3y = 12", "x - y = 1", method)
            assertEquals("$method finds one solution", 1, answer.solutions.size)
            assertClose("$method finds x", 3.0, answer.solutions[0].first.toDouble())
            assertClose("$method finds y", 2.0, answer.solutions[0].second.toDouble())
        }
    }

    @Test fun everyMethodHasItsOwnWorking() {
        val wordings = listOf(
            SystemMethod.Cramer,
            SystemMethod.Substitution,
            SystemMethod.Elimination,
            SystemMethod.Comparison,
        ).associateWith { method ->
            solve("2x + 3y = 12", "x - y = 1", method).steps.joinToString(" ")
        }
        val cramer = wordings.getValue(SystemMethod.Cramer)
        assertTrue("Cramer names the determinant: $cramer", cramer.contains("determinant"))
        val substitution = wordings.getValue(SystemMethod.Substitution)
        assertTrue(
            "substitution names the equation it rearranged: $substitution",
            substitution.contains("solve it for") || substitution.contains("Put that into"),
        )
        assertTrue(
            "substitution does not use Cramer's wording: $substitution",
            !substitution.contains("determinant"),
        )
        val elimination = wordings.getValue(SystemMethod.Elimination)
        assertTrue(
            "elimination says the terms cancel: $elimination",
            elimination.contains("cancel"),
        )
        assertTrue(
            "elimination does not use Cramer's wording: $elimination",
            !elimination.contains("determinant"),
        )
        val comparison = wordings.getValue(SystemMethod.Comparison)
        assertTrue("comparison equates the two: $comparison", comparison.contains("Equate"))
    }

    @Test fun theSolutionIsVerifiedInBothEquations() {
        val answer = solve("2x + 3y = 12", "x - y = 1")
        assertEquals("the solution is checked", 1, answer.verification.size)
        assertTrue(
            "the check says both sides agree: ${answer.verification.first()}",
            answer.verification.first().contains("both sides agree"),
        )
    }

    // ---------------------------------------------------------- the methods

    @Test fun cramerIsNotOfferedForANonlinearSystem() {
        val answer = solve("x^2 + y = 9", "x + y = 5")
        assertTrue(
            "Cramer is not offered: ${answer.available}",
            SystemMethod.Cramer !in answer.available,
        )
        assertTrue(
            "substitution is offered: ${answer.available}",
            SystemMethod.Substitution in answer.available,
        )
    }

    @Test fun choosingAMethodThatDoesNotApplyIsRefusedWithAReason() {
        val result = SystemSolver.solve("x^2 + y = 9", "x + y = 5", SystemMethod.Cramer)
        assertTrue("the method is refused", result is CalcResult.Err)
        val message = (result as CalcResult.Err).error.message
        assertTrue("the reason names the method: $message", message.contains("Cramer"))
        assertTrue("the reason says what applies: $message", message.contains("Substitution"))
    }

    @Test fun theAutomaticMethodPicksSuitably() {
        val linear = solve("2x + 3y = 12", "x - y = 1")
        assertEquals("a linear system uses Cramer's rule", SystemMethod.Cramer, linear.method)
        val quadratic = solve("x^2 + y = 9", "x + y = 5")
        assertEquals(
            "a system with a square uses substitution",
            SystemMethod.Substitution,
            quadratic.method,
        )
    }

    // -------------------------------------------------------------- the input

    @Test fun anEquationThatCannotBeReadIsRefused() {
        val result = SystemSolver.solve("2x + ", "x + y = 5")
        assertTrue("the equation is refused", result is CalcResult.Err)
    }

    @Test fun anEquationWithTwoEqualsSignsIsRefused() {
        val result = SystemSolver.solve("2x = 3 = 4", "x + y = 5")
        assertTrue("the equation is refused", result is CalcResult.Err)
    }

    @Test fun anUnknownNameIsRefused() {
        val result = SystemSolver.solve("2z + 3y = 12", "x + y = 1")
        assertTrue("the equation is refused", result is CalcResult.Err)
    }
}
