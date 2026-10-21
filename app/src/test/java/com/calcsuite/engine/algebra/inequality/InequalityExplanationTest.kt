package com.calcsuite.engine.algebra.inequality

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The explanation of an inequality.
 *
 * The steps have to show the mathematics that the answer rests on: for a
 * quadratic, the discriminant, the roots it gives and what the sign of the
 * quadratic does between them.
 */
class InequalityExplanationTest {

    private fun stepsFor(input: String): List<String> {
        val result = InequalitySolver.solve(input, InequalityMode.SINGLE)
        assertTrue("the inequality should be solved: $input", result is InequalityResult.Solved)
        return (result as InequalityResult.Solved).steps
    }

    private fun joined(input: String): String = stepsFor(input).joinToString(" | ")

    @Test fun aQuadraticWithTwoRootsShowsTheDiscriminant() {
        val text = joined("x^2 - 4 >= 0")
        assertTrue("the discriminant should be shown: $text", text.contains("discriminant"))
        assertTrue("the formula should be shown: $text", text.contains("b² − 4ac"))
        assertTrue("its value should be shown: $text", text.contains("= 16"))
        assertTrue("the two roots should be shown: $text", text.contains("two distinct"))
        assertTrue("the quadratic formula should be shown: $text", text.contains("−b ± √Δ"))
    }

    @Test fun aQuadraticWithOneRootShowsARepeatedRoot() {
        val text = joined("x^2 - 2x + 1 >= 0")
        assertTrue("the discriminant should be shown: $text", text.contains("discriminant"))
        assertTrue("the repeated root should be shown: $text", text.contains("Δ = 0"))
        assertTrue("it should say the root is repeated: $text", text.contains("repeated root"))
        assertTrue(
            "it should say the sign does not change: $text",
            text.contains("does not change"),
        )
    }

    @Test fun aQuadraticWithNoRealRootsSaysSo() {
        val text = joined("x^2 + 1 > 0")
        assertTrue("the discriminant should be shown: $text", text.contains("discriminant"))
        assertTrue("it should say the roots are not real: $text", text.contains("no real roots"))
    }

    @Test fun theSignPatternFollowsTheLeadingCoefficient() {
        val positive = joined("x^2 - 4 >= 0")
        assertTrue(
            "the positive quadratic should be positive outside the roots: $positive",
            positive.contains("positive outside the two roots"),
        )
        val negative = joined("-x^2 + 4 >= 0")
        assertTrue(
            "the negative quadratic should be negative outside the roots: $negative",
            negative.contains("negative outside the two roots"),
        )
    }

    @Test fun theCriticalPointsAndTheSignAreShown() {
        val result = InequalitySolver.solve("x^2 - 4 >= 0", InequalityMode.SINGLE)
        assertTrue(result is InequalityResult.Solved)
        val solved = result as InequalityResult.Solved
        assertTrue(
            "the critical points should be found",
            solved.criticalPoints.any { it.value == -2.0 } &&
                solved.criticalPoints.any { it.value == 2.0 },
        )
        assertTrue("the sign chart should be there", solved.signChart.isNotEmpty())
        val text = solved.steps.joinToString(" | ")
        assertTrue("the intervals should be tested: $text", text.contains("On ("))
    }

    @Test fun theAnswerIsStillCorrectAlongsideTheExplanation() {
        // The explanation must not change the answer.
        val result = InequalitySolver.solve("x^2 - 4 >= 0", InequalityMode.SINGLE)
        assertTrue(result is InequalityResult.Solved)
        val solved = result as InequalityResult.Solved
        assertTrue(
            "the solution should be the two outer intervals",
            solved.solution.intervals.size == 2,
        )
    }

    @Test fun aLinearInequalityIsNotGivenAQuadraticExplanation() {
        val text = joined("2x + 3 < 11")
        assertTrue(
            "a linear inequality must not be given a discriminant: $text",
            !text.contains("discriminant"),
        )
    }
}
