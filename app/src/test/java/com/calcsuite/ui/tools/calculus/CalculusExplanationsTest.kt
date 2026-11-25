package com.calcsuite.ui.tools.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.calculus.CalculusEngine
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.DerivativeResult
import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.EquationEngine
import com.calcsuite.engine.calculus.ExtremaResult
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.IntegrationResult
import com.calcsuite.engine.calculus.LimitResult
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.RootResult
import com.calcsuite.engine.calculus.SeriesResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The explanation behind every calculus panel.
 *
 * The sections must be real prose taken from the calculation that ran: the
 * steps are the engine's steps, the checks describe checks the tool makes, and
 * the limitations name the limits the engine actually has. A missing result is
 * an error state, not an excuse for an empty card.
 */
class CalculusExplanationsTest {

    // Representative results, produced by the same engines the panels use.

    private fun <T> successful(result: CalcResult<T>): T {
        assertTrue("the calculation should succeed", result is CalcResult.Ok)
        return (result as CalcResult.Ok<T>).value
    }

    private fun derivativeResult(): DerivativeResult =
        successful(DifferentiationEngine.derivative("x^2 + 3x", "x"))

    private fun indefiniteResult(): IntegrationResult =
        successful(IntegrationEngine.indefinite("x^2", "x"))

    private fun definiteResult(): IntegrationResult =
        successful(IntegrationEngine.definite("x^2", "x", "0", "3"))

    private fun limitResult(): LimitResult =
        successful(CalculusEngine.limit("sin(x)/x", "x", "0", true, true))

    private fun seriesResult(): SeriesResult =
        successful(CalculusEngine.series("0.5^k", "k", 0L, 10L))

    private fun taylorResult(): SeriesResult =
        successful(CalculusEngine.taylor("e^x", "x", "0", "6"))

    private fun sumResult(): SeriesResult =
        successful(CalculusEngine.summation("k", "k", "1", "10"))

    private fun productResult(): SeriesResult =
        successful(CalculusEngine.product("k", "k", "1", "5"))

    private fun rootResult(): RootResult =
        successful(EquationEngine.solveRoots("x^2 - 4", "-5", "5"))

    private fun extremaResult(): ExtremaResult =
        successful(EquationEngine.extrema("x^2 - 4x", "-5", "5"))

    private fun parsed(input: String): Node = successful(CalculusParser.parse(input))

    private fun completeExplanations(): List<Pair<String, Explanation>> = listOf(
        "derivative" to derivativeExplanation("x^2 + 3x", derivativeResult()),
        "integral" to integralExplanation("x^2", definiteResult()),
        "limit" to limitExplanation("sin(x)/x", limitResult()),
        "series" to seriesExplanation("0.5^k", seriesResult()),
        "Taylor" to taylorExplanation("e^x", taylorResult()),
        "sum and product" to sumProductExplanation("k", sumResult(), productResult()),
        "roots" to rootsExplanation("x^2 - 4", rootResult()),
        "extrema" to extremaExplanation("x^2 - 4x", extremaResult()),
        "graph" to graphExplanation(
            "x^2 + 3x", parsed("x^2 + 3x"), parsed("2x + 3"),
        ),
    )

    private fun missingResultExplanations(): List<Pair<String, Explanation>> = listOf(
        "derivative" to derivativeExplanation("", null),
        "integral" to integralExplanation("", null),
        "limit" to limitExplanation("", null),
        "series" to seriesExplanation("", null),
        "Taylor" to taylorExplanation("", null),
        "sum and product" to sumProductExplanation("", null, null),
        "roots" to rootsExplanation("", null),
        "extrema" to extremaExplanation("", null),
        "graph" to graphExplanation("", null, null),
    )

    private fun assertNotBlank(label: String, value: String?) {
        assertTrue("$label must be present", value != null)
        assertTrue("$label must not be blank", value!!.isNotBlank())
    }

    /** Every card the shared component can render must have real content. */
    private fun assertComplete(label: String, explanation: Explanation) {
        assertFalse("$label must not be empty", explanation.isEmpty)
        assertNotBlank("$label definition", explanation.definition)
        assertNotBlank("$label formula", explanation.formula)
        assertNotBlank("$label method", explanation.method)
        assertNotBlank("$label interpretation", explanation.interpretation)
        assertNotBlank("$label verification", explanation.verification)
        assertNotBlank("$label limitations", explanation.limitations)

        assertTrue("$label needs two to five symbols", explanation.symbols.size in 2..5)
        explanation.symbols.forEachIndexed { index, (symbol, meaning) ->
            assertNotBlank("$label symbol $index", symbol)
            assertNotBlank("$label symbol $index meaning", meaning)
        }
        assertTrue("$label needs conditions", explanation.conditions.isNotEmpty())
        explanation.conditions.forEachIndexed { index, condition ->
            assertNotBlank("$label condition $index", condition)
        }
        assertTrue("$label needs working", explanation.steps.isNotEmpty())
        explanation.steps.forEachIndexed { index, step ->
            assertNotBlank("$label step $index", step)
        }

        val prose = listOfNotNull(
            explanation.definition, explanation.formula, explanation.method,
            explanation.interpretation, explanation.verification, explanation.limitations,
        ) + explanation.symbols.flatMap { listOf(it.first, it.second) } +
            explanation.conditions + explanation.steps
        prose.forEach { text ->
            assertFalse(
                "$label must not contain a placeholder: $text",
                text.contains("TODO", ignoreCase = true) ||
                    text.contains("placeholder", ignoreCase = true),
            )
            assertFalse(
                "$label must use British spelling: $text",
                listOf("behavior", "center", "recognize", "analyze", "color").any {
                    text.contains(it, ignoreCase = true)
                },
            )
        }
    }

    @Test
    fun allNineOperationsHaveCompleteExplanations() {
        assertEquals(9, completeExplanations().size)
        completeExplanations().forEach { (label, explanation) ->
            assertComplete(label, explanation)
        }
    }

    @Test
    fun everyBuilderIsTotalWhenTheResultIsMissing() {
        assertEquals(9, missingResultExplanations().size)
        missingResultExplanations().forEach { (label, explanation) ->
            assertComplete(label, explanation)
        }
    }

    @Test
    fun stepsAreTheStepsTheEngineProduced() {
        assertEquals(derivativeResult().steps, derivativeExplanation("x^2", derivativeResult()).steps)
        assertEquals(definiteResult().steps, integralExplanation("x^2", definiteResult()).steps)
        assertEquals(indefiniteResult().steps, integralExplanation("x^2", indefiniteResult()).steps)
        assertEquals(limitResult().steps, limitExplanation("sin(x)/x", limitResult()).steps)
        assertEquals(seriesResult().steps, seriesExplanation("0.5^k", seriesResult()).steps)
        assertEquals(taylorResult().steps, taylorExplanation("e^x", taylorResult()).steps)
        assertEquals(rootResult().steps, rootsExplanation("x^2 - 4", rootResult()).steps)
        assertEquals(extremaResult().steps, extremaExplanation("x^2 - 4x", extremaResult()).steps)

        val sumSteps = sumProductExplanation("k", sumResult(), productResult()).steps
        assertTrue("the sum steps must be kept", sumSteps.containsAll(sumResult().steps))
        assertTrue("the product steps must be kept", sumSteps.containsAll(productResult().steps))

        val graphSteps = graphExplanation("x^2 + 3x", parsed("x^2 + 3x"), parsed("2x + 3")).steps
        assertTrue("the graph should say what it plotted", graphSteps[0].contains("x^2 + 3x"))
        assertTrue("the graph should say what it differentiated", graphSteps[1].contains("f′"))
    }

    @Test
    fun formulasNameTheRightOperatorOrOperation() {
        val explanations = completeExplanations().toMap()
        assertTrue(explanations.getValue("derivative").formula!!.contains("d/dx"))
        assertTrue(explanations.getValue("integral").formula!!.contains("∫"))
        assertTrue(explanations.getValue("integral").formula!!.contains("F′(x)"))
        assertTrue(explanations.getValue("limit").formula!!.contains("lim("))
        assertTrue(explanations.getValue("series").formula!!.contains("|r| < 1"))
        assertTrue(explanations.getValue("Taylor").formula!!.contains("Taylor"))
        assertTrue(explanations.getValue("Taylor").formula!!.contains("k!"))
        assertTrue(explanations.getValue("sum and product").formula!!.contains("∑"))
        assertTrue(explanations.getValue("sum and product").formula!!.contains("∏"))
        assertTrue(explanations.getValue("roots").formula!!.contains("f(x) = 0"))
        assertTrue(explanations.getValue("extrema").formula!!.contains("f′(x) = 0"))
        assertTrue(explanations.getValue("graph").formula!!.contains("Graph"))
        assertTrue(explanations.getValue("graph").formula!!.contains("f′(x)"))
    }

    @Test
    fun limitationsStateTheRealLimitsOfEachTool() {
        val explanations = completeExplanations().toMap()
        val expected = mapOf(
            "derivative" to listOf("one point only", "Taylor"),
            "integral" to listOf("no elementary antiderivative", "numerical"),
            "limit" to listOf("small offset", "oscillating"),
            "series" to listOf("5000", "not a general convergence proof"),
            "Taylor" to listOf("12", "double precision"),
            "sum and product" to listOf("100000", "5000", "double-precision"),
            "roots" to listOf("touch", "complex roots"),
            "extrema" to listOf("Endpoints", "f″ = 0"),
            "graph" to listOf("x only", "not the adaptive Simpson value"),
        )
        expected.forEach { (label, phrases) ->
            val limitations = explanations.getValue(label).limitations!!
            phrases.forEach { phrase ->
                assertTrue(
                    "$label limitations should mention $phrase: $limitations",
                    limitations.contains(phrase, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun verificationsDescribeTheChecksTheToolActuallyMakes() {
        val explanations = completeExplanations().toMap()
        val expected = mapOf(
            "derivative" to listOf("central difference", "1e-6", "0.1 per cent"),
            "integral" to listOf("1e-13", "depth of 24"),
            "limit" to listOf("1e-4", "1 per cent"),
            "series" to listOf("|r|", "50 indices"),
            "Taylor" to listOf("Terms card", "coefficient"),
            "sum and product" to listOf("number of terms or factors", "domain error"),
            "roots" to listOf("changes sign", "1e-12"),
            "extrema" to listOf("first derivative", "second derivative"),
            "graph" to listOf("120 to 900", "240 samples", "1e12"),
        )
        expected.forEach { (label, phrases) ->
            val verification = explanations.getValue(label).verification!!
            phrases.forEach { phrase ->
                assertTrue(
                    "$label verification should mention $phrase: $verification",
                    verification.contains(phrase, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun equalLimitsGetTheirOwnZeroWidthVerification() {
        val equal = successful(IntegrationEngine.definite("x^2", "x", "2", "2"))
        val explanation = integralExplanation("x^2", equal)
        assertTrue(equal.exact)
        assertTrue(explanation.verification!!.contains("zero width"))
        assertFalse(explanation.verification!!.contains("1e-13"))
    }

    @Test
    fun graphExplanationHasNoInventedEngineSteps() {
        val withNode = graphExplanation("x^2 + 3x", parsed("x^2 + 3x"), null)
        assertEquals(1, withNode.steps.size)
        assertTrue(withNode.steps[0].startsWith("Plotted f ="))

        val withoutNode = graphExplanation("not a function", null, null)
        assertEquals(1, withoutNode.steps.size)
        assertTrue(withoutNode.steps[0].contains("did not parse"))
        assertComplete("graph without a parsed function", withoutNode)
    }
}
