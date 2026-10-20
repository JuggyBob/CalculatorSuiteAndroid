package com.calcsuite.engine.algebra.inequality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the inequality solver.
 *
 * The important ones check the *content* of the answer — the exact solution set
 * — and then cross-check it against the original inequality at sample points,
 * so a solution set that is merely plausible cannot pass.
 */
class InequalitySolverTest {

    private fun solve(input: String, mode: InequalityMode = InequalityMode.SINGLE): InequalityResult.Solved {
        val result = InequalitySolver.solve(input, mode)
        assertTrue("expected a solution for '$input' but got $result", result is InequalityResult.Solved)
        return result as InequalityResult.Solved
    }

    private fun failure(input: String, mode: InequalityMode = InequalityMode.SINGLE): String {
        val result = InequalitySolver.solve(input, mode)
        assertTrue("expected a failure for '$input' but got $result", result is InequalityResult.Failed)
        return (result as InequalityResult.Failed).message
    }

    /** The solution set in interval notation, with infinity written out. */
    private fun notation(input: String, mode: InequalityMode = InequalityMode.SINGLE): String =
        solve(input, mode).solution.toIntervalNotation()

    // ------------------------------------------------------------ the basics

    @Test fun linearInequality() {
        // 2x + 3 < 11  ⟺  2x < 8  ⟺  x < 4
        val r = solve("2x + 3 < 11")
        assertEquals("linear inequality", r.kind)
        assertEquals("x", r.variable)
        assertEquals("(−∞, 4)", r.solution.toIntervalNotation())
        assertFalse(r.approximate)
        // The zero is exact.
        assertEquals("4", r.criticalPoints.single().display)
        assertTrue(r.criticalPoints.single().isExact)
    }

    @Test fun quadraticInequality() {
        // x^2 - 5x + 6 = (x - 2)(x - 3), so the polynomial is non-positive
        // between its roots and both roots are included.
        val r = solve("x^2 - 5x + 6 <= 0")
        assertEquals("quadratic inequality", r.kind)
        assertEquals("[2, 3]", r.solution.toIntervalNotation())
        assertEquals(listOf("2", "3"), r.criticalPoints.map { it.display })
        assertTrue(r.steps.any { it.contains("(x - 2)") || it.contains("2, 3") })
    }

    @Test fun rationalInequalityExcludesPoles() {
        // (x - 2)/(x + 1) > 0
        val r = solve("(x - 2)/(x + 1) > 0")
        assertEquals("rational inequality", r.kind)
        assertEquals("(−∞, %s1) ∪ (2, ∞)".replace("%s", "−"), r.solution.toIntervalNotation())
        // The pole is a critical point, and it is marked as one.
        val pole = r.criticalPoints.single { it.isPole }
        assertEquals("−1", pole.display)
        assertFalse("the pole must not be in the solution", r.solution.contains(-1.0))
        assertNotNull(r.domainNote)
    }

    @Test fun absoluteValueInequality() {
        // |2x - 3| <= 5  ⟺  -5 <= 2x - 3 <= 5  ⟺  -1 <= x <= 4
        val r = solve("|2x - 3| <= 5")
        assertEquals("[−1, 4]", r.solution.toIntervalNotation())
        assertTrue(r.steps.joinToString(" "), r.steps.any { it.contains("absolute value") })
    }

    @Test fun absoluteValueWithStrictComparison() {
        // |2x - 3| < 5  ⟺  -1 < x < 4
        assertEquals("(−1, 4)", notation("|2x - 3| < 5"))
        // |x| > 3  ⟺  x < -3 or x > 3
        assertEquals("(−∞, −3) ∪ (3, ∞)", notation("|x| > 3"))
    }

    @Test fun absoluteValueWithANegativeThresholdIsAlwaysTrueOrNever() {
        // An absolute value is never negative, so |x| > -1 holds everywhere.
        assertEquals("(−∞, ∞)", notation("|x| > -1"))
        // and |x| < -1 has no solution at all.
        assertEquals("∅", notation("|x| < -1"))
    }

    @Test fun compoundInequality() {
        // -2 < 3x + 1 <= 10  ⟺  -3 < 3x <= 9  ⟺  -1 < x <= 3
        val r = solve("-2 < 3x + 1 <= 10", InequalityMode.COMPOUND)
        assertEquals("(−1, 3]", r.solution.toIntervalNotation())
    }

    @Test fun strictAndNonStrictEndpointsDiffer() {
        // The only difference between these two is the two endpoints.
        assertEquals("[2, 3]", notation("x^2 - 5x + 6 <= 0"))
        assertEquals("(2, 3)", notation("x^2 - 5x + 6 < 0"))
        assertEquals("(2, 3)", notation("x^2 - 5x + 6 < 0"))
        assertTrue(notation("x^2 - 5x + 6 >= 0").startsWith("(−∞"))
    }

    @Test fun systemWithANonEmptyIntersection() {
        // x^2 - 4 > 0  means x < -2 or x > 2;  x - 1 < 4  means x < 5.
        val r = solve("x^2 - 4 > 0\nx - 1 < 4", InequalityMode.SYSTEM)
        assertEquals("(−∞, −2) ∪ (2, 5)", r.solution.toIntervalNotation())
    }

    @Test fun systemWithNoSolution() {
        // x > 5 and x < 0 cannot both hold.
        val r = solve("x > 5\nx < 0", InequalityMode.SYSTEM)
        assertTrue(r.solution.isEmpty)
        assertEquals("∅", r.solution.toIntervalNotation())
    }

    @Test fun inequalityThatHoldsEverywhere() {
        val r = solve("x^2 + 1 > 0")
        assertTrue(r.solution.isEverything)
        assertEquals("(−∞, ∞)", r.solution.toIntervalNotation())
    }

    @Test fun inequalityWithNoRealSolution() {
        val r = solve("x^2 + 1 < 0")
        assertTrue(r.solution.isEmpty)
        assertEquals("∅", r.solution.toIntervalNotation())
        assertTrue(r.steps.any { it.contains("no zeros") })
    }

    @Test fun aCancelledFactorDoesNotBringBackAnExcludedPoint() {
        // (x^2 - 4)/(x - 2) simplifies to x + 2, but the original expression is
        // undefined at x = 2, so 2 must stay out of the solution.
        val r = solve("(x^2 - 4)/(x - 2) > 0")
        assertEquals("(−2, 2) ∪ (2, ∞)", r.solution.toIntervalNotation())
        assertFalse("the cancelled point must stay excluded", r.solution.contains(2.0))
        assertTrue(r.criticalPoints.any { it.isPole && it.value == 2.0 })
    }

    @Test fun higherDegreePolynomial() {
        // x^3 - 6x^2 + 11x - 6 = (x - 1)(x - 2)(x - 3)
        val r = solve("x^3 - 6x^2 + 11x - 6 <= 0")
        assertEquals("(−∞, 1] ∪ [2, 3]", r.solution.toIntervalNotation())
        assertEquals(listOf("1", "2", "3"), r.criticalPoints.map { it.display })
    }

    @Test fun irrationalRootsAreGivenInRadicalFormAndLabelled() {
        // x^2 = 2 has the roots ±√2, which are not rational.
        val r = solve("x^2 - 2 < 0")
        val text = r.solution.toIntervalNotation()
        assertTrue("the radical form should be shown: $text", text.contains("√2"))
        assertTrue("a radical root is exact, not an approximation", !r.approximate)
        assertTrue(r.criticalPoints.all { it.isExact })
        // The interval really does contain the two roots.
        assertTrue(r.solution.contains(-1.4))
        assertTrue(r.solution.contains(1.4))
        assertFalse(r.solution.contains(1.5))
        assertFalse("the endpoints are excluded", r.solution.contains(-1.4142135623730951))
    }

    @Test fun aCubicWithNoRationalRootIsApproximate() {
        // x^3 - 2x - 5 has one real root near 2.0946, and no rational one.
        val r = solve("x^3 - 2x - 5 < 0")
        assertTrue("a numerical result must be labelled", r.approximate)
        assertTrue(r.criticalPoints.any { it.display.startsWith("≈") })
        assertTrue(r.solution.contains(2.0))
        assertFalse(r.solution.contains(2.2))
    }

    @Test fun equalityIsSolvedAsWell() {
        // x^2 - 5x + 6 = 0 has exactly the two roots.
        val r = solve("x^2 - 5x + 6 = 0")
        assertEquals("[2, 2] ∪ [3, 3]", r.solution.toIntervalNotation())
    }

    // ------------------------------------------------------------- failures

    @Test fun invalidSyntaxIsReported() {
        assertTrue(failure("2x +").contains("comparison") || failure("2x +").isNotEmpty())
        assertTrue(failure("2x + 3 <").isNotEmpty())
        assertTrue(failure("<> 3").isNotEmpty())
        assertTrue(failure("").isNotEmpty())
    }

    @Test fun divisionByZeroIsReportedRatherThanCrashing() {
        val message = failure("1/0 < 5")
        assertTrue(message.isNotEmpty())
    }

    @Test fun aMissingVariableIsReported() {
        val message = failure("2 < 5")
        assertTrue(message, message.contains("variable"))
    }

    @Test fun theModeMustMatchTheInput() {
        assertTrue(failure("x > 1", InequalityMode.SYSTEM).contains("several"))
        assertTrue(failure("x > 1", InequalityMode.COMPOUND).contains("chain"))
        assertTrue(failure("x > 1\nx < 5", InequalityMode.SINGLE).contains("single"))
    }

    // ------------------------------------------------- the answer is correct

    /**
     * Cross-checks a solution set against the original inequality.
     *
     * For a spread of sample points the point must be in the solution set
     * exactly when the original inequality holds there. This is what stops a
     * plausible-looking but wrong answer from passing.
     */
    @Test fun everyAnswerAgreesWithTheOriginalInequality() {
        val cases = listOf(
            "2x + 3 < 11",
            "x^2 - 5x + 6 <= 0",
            "x^2 - 5x + 6 < 0",
            "(x - 2)/(x + 1) > 0",
            "(x - 2)/(x + 1) <= 0",
            "|2x - 3| <= 5",
            "|2x - 3| < 5",
            "x^2 - 4 > 0",
            "x^2 + 1 > 0",
            "x^2 + 1 < 0",
            "3x - 12 >= 0",
            "x^3 - 6x^2 + 11x - 6 <= 0",
            "(x^2 - 4)/(x - 2) > 0",
        )
        val samples = listOf(
            -10.0, -3.7, -2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 1.9, 2.0, 2.5, 3.0, 4.0, 7.3, 10.0,
        )
        for (input in cases) {
            val solution = solve(input).solution
            val relation = InequalityParser.parse(input).relation
            for (x in samples) {
                val holds = holdsAt(relation, x)
                if (holds == null) {
                    // The point is outside the domain, so it cannot be a solution.
                    assertFalse(
                        "'$input' included x = $x, which is outside the domain",
                        solution.contains(x),
                    )
                    continue
                }
                assertEquals(
                    "'$input' at x = $x: the solution set and the inequality disagree",
                    holds,
                    solution.contains(x),
                )
            }
        }
    }

    /** Whether the relation holds at [x], or null when the point is undefined. */
    private fun holdsAt(relation: Rel, x: Double): Boolean? {
        val simple = relation as? Rel.Simple ?: return null
        val lhs = NumericEval.eval(simple.lhs, mapOf("x" to x))
        val rhs = NumericEval.eval(simple.rhs, mapOf("x" to x))
        if (lhs.isNaN() || rhs.isNaN()) return null
        val difference = lhs - rhs
        return when (simple.op) {
            CompareOp.LT -> difference < 0
            CompareOp.GT -> difference > 0
            CompareOp.LE -> difference <= 0
            CompareOp.GE -> difference >= 0
            CompareOp.EQ -> difference == 0.0
        }
    }

    // ------------------------------------------------------------ explanations

    @Test fun theExplanationComesFromTheActualWorking() {
        val r = solve("x^2 - 5x + 6 <= 0")
        val text = r.steps.joinToString(" ")
        // It names the type, the zeros, the sign on each interval and the answer.
        assertTrue(text, text.contains("quadratic"))
        assertTrue(text, text.contains("2"))
        assertTrue(text, text.contains("3"))
        assertTrue(text, text.contains("[2, 3]"))
        // The sign of each interval is stated, not just the answer.
        assertTrue("the sign chart should have a row per interval", r.signChart.size >= 3)
        assertTrue(r.signChart.any { it.accepted })
        assertTrue(r.signChart.any { !it.accepted })
        assertTrue(r.principle.isNotBlank())
    }

    @Test fun theExplanationMentionsTheDomainForARationalInequality() {
        val r = solve("(x - 2)/(x + 1) > 0")
        assertTrue(r.steps.joinToString(" "), r.steps.any { it.contains("denominator") })
        assertTrue(r.steps.any { it.contains("undefined") })
    }
}
