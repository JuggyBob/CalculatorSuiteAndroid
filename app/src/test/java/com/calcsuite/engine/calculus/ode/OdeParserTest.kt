package com.calcsuite.engine.calculus.ode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for reading an equation, classifying it and reading the conditions.
 */
class OdeParserTest {

    @Test
    fun theFractionNotationIsRewritten() {
        assertEquals("yd1 = 2y", OdeParser.rewriteDerivatives("dy/dx = 2y"))
        assertEquals("yd2 - 3yd1 = 0", OdeParser.rewriteDerivatives("y'' - 3y' = 0"))
        assertEquals("yd2 = 0", OdeParser.rewriteDerivatives("d2y/dx2 = 0"))
    }

    @Test
    fun anEquationIsReadIntoTheStandardForm() {
        val parsed = OdeParser.parse("y'' - 3y' + 2y = 0").valueOrNull()
        assertNotNull("the equation should be read", parsed)
        assertEquals(2, parsed!!.order)
        assertEquals("y", parsed.dependent)
        assertEquals("x", parsed.independent)
        assertEquals(2.0, OdeParser.numericValue(parsed.coefficient(0))!!, 1e-12)
        assertEquals(-3.0, OdeParser.numericValue(parsed.coefficient(1))!!, 1e-12)
        assertEquals(1.0, OdeParser.numericValue(parsed.coefficient(2))!!, 1e-12)
    }

    @Test
    fun theEquationIsRenderedWithRealDerivativeMarks() {
        val parsed = OdeParser.parse("y'' - 3y' + 2y = 0").valueOrNull()
        assertEquals("y″ − 3y′ + 2y = 0", parsed!!.display)
    }

    @Test
    fun aNegatedTermKeepsItsSign() {
        val parsed = OdeParser.parse("y'' - 3y' + 2y = 0").valueOrNull()
        assertEquals(-3.0, OdeParser.numericValue(parsed!!.coefficient(1))!!, 1e-12)
    }

    @Test
    fun anEquationWithNoEqualsSignIsRejected() {
        val result = OdeParser.parse("y' + y")
        assertTrue("the equation should be rejected", result is com.calcsuite.core.CalcResult.Err)
    }

    @Test
    fun anEquationWithNoDerivativeIsRejected() {
        val result = OdeParser.parse("x + y = 3")
        assertTrue("the equation should be rejected", result is com.calcsuite.core.CalcResult.Err)
    }

    @Test
    fun anEmptyEquationIsRejected() {
        val result = OdeParser.parse("   ")
        assertTrue("the equation should be rejected", result is com.calcsuite.core.CalcResult.Err)
    }

    // ------------------------------------------------------- classification

    @Test
    fun aSeparableEquationIsRecognised() {
        val parsed = OdeParser.parse("y' = x*y").valueOrNull()!!
        val classification = OdeClassifier.classify(parsed)
        assertEquals(1, classification.order)
        assertTrue("the equation should be separable", classification.separable != null)
        assertTrue("the equation should not be first-order linear", !classification.isFirstOrderLinear)
    }

    @Test
    fun aFirstOrderLinearEquationIsRecognised() {
        val parsed = OdeParser.parse("y' + y = x").valueOrNull()!!
        val classification = OdeClassifier.classify(parsed)
        assertTrue("the equation should be first-order linear", classification.isFirstOrderLinear)
        assertTrue(
            "the equation should not be separable, because y' = x − y cannot be split",
            classification.separable == null,
        )
    }

    @Test
    fun aConstantCoefficientEquationIsRecognised() {
        val parsed = OdeParser.parse("y'' - 3y' + 2y = 0").valueOrNull()!!
        val classification = OdeClassifier.classify(parsed)
        assertTrue("the coefficients are constant", classification.hasConstantCoefficients)
        assertTrue("there is no forcing term", classification.isHomogeneous)
        assertEquals(2, classification.order)
    }

    @Test
    fun anEquationWithYOnTheRightIsNotConstantCoefficient() {
        val parsed = OdeParser.parse("y' = x + y").valueOrNull()!!
        val classification = OdeClassifier.classify(parsed)
        assertTrue(
            "the characteristic equation does not apply when y is on the right",
            !classification.hasConstantCoefficients,
        )
    }

    // ---------------------------------------------------------- conditions

    @Test
    fun aConditionIsReadFromTheUsualNotation() {
        val condition = OdeConditionParser.parse("y(0) = 3")
        assertNotNull("the condition should be read", condition)
        assertEquals(0, condition!!.order)
        assertEquals(0.0, condition.at, 1e-12)
        assertEquals(3.0, condition.value, 1e-12)
    }

    @Test
    fun aConditionOnADerivativeIsRead() {
        val condition = OdeConditionParser.parse("y'(0) = 1")
        assertNotNull("the condition should be read", condition)
        assertEquals(1, condition!!.order)
        assertEquals(1.0, condition.value, 1e-12)
    }

    @Test
    fun aConditionWithADecimalPointIsRead() {
        val condition = OdeConditionParser.parse("y(1.5) = -2.25")
        assertNotNull("the condition should be read", condition)
        assertEquals(1.5, condition!!.at, 1e-12)
        assertEquals(-2.25, condition.value, 1e-12)
    }

    @Test
    fun anUnreadableConditionIsRejected() {
        assertNull(OdeConditionParser.parse("y(0) ="))
        assertNull(OdeConditionParser.parse("y = 3"))
        assertNull(OdeConditionParser.parse("z(0) = 3"))
        assertNull(OdeConditionParser.parse(""))
    }

    @Test
    fun tooFewConditionsAreReported() {
        val problem = OdeConditionParser.checkSufficiency(emptyList(), 2)
        assertNotNull("the missing conditions should be reported", problem)
        assertTrue(problem!!.contains("second-order"))
    }

    @Test
    fun conditionsThatDisagreeAreReported() {
        val problem = OdeConditionParser.checkSufficiency(
            listOf(
                OdeCondition(0, 0.0, 1.0, "y(0) = 1"),
                OdeCondition(0, 0.0, 2.0, "y(0) = 2"),
            ),
            1,
        )
        assertNotNull("the disagreement should be reported", problem)
        assertTrue(problem!!.contains("disagree"))
    }

    @Test
    fun enoughConditionsAreAccepted() {
        assertNull(
            OdeConditionParser.checkSufficiency(
                listOf(
                    OdeCondition(0, 0.0, 1.0, "y(0) = 1"),
                    OdeCondition(1, 0.0, 0.0, "y'(0) = 0"),
                ),
                2,
            ),
        )
    }
}
