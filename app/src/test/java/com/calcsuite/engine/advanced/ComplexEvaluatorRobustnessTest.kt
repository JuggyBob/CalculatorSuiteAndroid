package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for non-finite results and deep expression trees.
 */
class ComplexEvaluatorRobustnessTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    @Test fun nonFiniteResultsAreRejected() {
        assertNull(ComplexEvaluator.eval(parse("exp(1000)"), emptyMap()))
        assertNull(ComplexEvaluator.eval(parse("ln(0)"), emptyMap()))
        assertNull(ComplexEvaluator.eval(parse("1/0"), emptyMap()))
        assertNull(ComplexEvaluator.eval(Node.Num(Double.NaN), emptyMap()))
        assertNull(
            ComplexEvaluator.eval(
                Node.Var("z"),
                mapOf("z" to ComplexNum(Double.POSITIVE_INFINITY, 0.0)),
            ),
        )
    }

    @Test fun ordinaryValuesAreUnchanged() {
        val value = ComplexEvaluator.eval(parse("log(8,2)"), emptyMap())
        assertNotNull(value)
        assertEquals(3.0, value!!.re, 1e-9)
        assertEquals(0.0, value.im, 1e-9)
        val at = ComplexEvaluator.eval(
            parse("z^2 + 1"), mapOf("z" to ComplexNum(0.0, 1.0)),
        )
        assertNotNull(at)
        assertEquals(0.0, at!!.re, 1e-12)
        assertEquals(0.0, at.im, 1e-12)
    }

    @Test fun aDeepTreeIsRejectedWithoutOverflowingTheStack() {
        var deep: Node = Node.Num(1.0)
        repeat(300) { deep = Node.Neg(deep) }
        assertNull(ComplexEvaluator.eval(deep, emptyMap()))

        var shallow: Node = Node.Num(1.0)
        repeat(100) { shallow = Node.Neg(shallow) }
        val value = ComplexEvaluator.eval(shallow, emptyMap())
        assertNotNull(value)
        assertEquals(1.0, value!!.re, 1e-12)
    }

    @Test fun complexNumbersReportFiniteness() {
        assertTrue(ComplexNum(1.0, 2.0).isFinite)
        assertTrue(!ComplexNum(Double.NaN, 0.0).isFinite)
        assertTrue(!ComplexNum(0.0, Double.NEGATIVE_INFINITY).isFinite)
    }
}
