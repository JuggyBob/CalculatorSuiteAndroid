package com.calcsuite.ui.tools.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.algebra.ProportionEngine
import com.calcsuite.engine.algebra.ProportionMode
import com.calcsuite.engine.algebra.ProportionSlot
import com.calcsuite.engine.algebra.ProportionSolution
import com.calcsuite.engine.algebra.RatioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The four elementary explanation builders are pure, so every section can be
 * checked on the JVM without Compose or a device.
 */
class AlgebraExplanationsBasicTest {

    private fun assertComplete(explanation: Explanation, label: String) {
        assertFalse("$label should not be empty", explanation.isEmpty)
        val prose = listOf(
            "definition" to explanation.definition,
            "formula" to explanation.formula,
            "method" to explanation.method,
            "interpretation" to explanation.interpretation,
            "verification" to explanation.verification,
            "limitations" to explanation.limitations,
        )
        for ((field, text) in prose) {
            assertNotNull("$label: $field should be present", text)
            assertTrue("$label: $field should not be blank", text!!.isNotBlank())
        }
        assertTrue("$label should have symbols", explanation.symbols.isNotEmpty())
        assertTrue("$label should have at most six symbols", explanation.symbols.size <= 6)
        explanation.symbols.forEachIndexed { index, (symbol, meaning) ->
            assertTrue("$label: symbol $index should not be blank", symbol.isNotBlank())
            assertTrue("$label: meaning $index should not be blank", meaning.isNotBlank())
        }
        assertTrue("$label should have conditions", explanation.conditions.isNotEmpty())
        explanation.conditions.forEachIndexed { index, condition ->
            assertTrue("$label: condition $index should not be blank", condition.isNotBlank())
        }
        val all = (
            prose.mapNotNull { it.second } +
                explanation.symbols.flatMap { listOf(it.first, it.second) } +
                explanation.conditions +
                explanation.steps
            ).joinToString(" ")
        assertFalse("$label should not contain TODO: $all", all.contains("TODO", ignoreCase = true))
        assertFalse("$label should not contain placeholder: $all", all.contains("placeholder", ignoreCase = true))
    }

    private val percentageModes = listOf(
        "PercentOf", "FindWhole", "WhatPercent", "Change",
        "Original", "Successive", "Points", "Relative",
    )
    private val averageModes = listOf("Mean", "Weighted", "Median", "Mode", "Geometric")
    private val ratioModes = listOf("Simplify", "Scale", "Split", "UnknownPart", "Combine", "Compare")

    @Test fun conditionsNameTheConstraintsTheEnginesActuallyEnforce() {
        fun conditionsOf(label: String, explanation: Explanation): String =
            explanation.conditions.joinToString(" ").also { assertTrue(label, it.isNotBlank()) }

        assertTrue(
            conditionsOf(
                "percentage change needs a non-zero start",
                AlgebraExplanationsBasic.percentageExplanation("Change"),
            ).contains("starting value must not be zero", ignoreCase = true),
        )
        assertTrue(
            conditionsOf(
                "what percent needs a non-zero reference",
                AlgebraExplanationsBasic.percentageExplanation("WhatPercent"),
            ).contains("reference value must not be zero", ignoreCase = true),
        )
        assertTrue(
            conditionsOf(
                "weighted mean needs non-negative weights with a non-zero sum",
                AlgebraExplanationsBasic.averageExplanation("Weighted"),
            ).contains("must not add to zero", ignoreCase = true),
        )
        assertTrue(
            conditionsOf(
                "geometric mean needs positive values",
                AlgebraExplanationsBasic.averageExplanation("Geometric"),
            ).contains("strictly positive", ignoreCase = true),
        )
        assertTrue(
            conditionsOf(
                "proportion needs three values and a non-zero divisor",
                AlgebraExplanationsBasic.proportionExplanation(ProportionMode.Direct),
            ).contains("Three of the four values", ignoreCase = true),
        )
        assertTrue(
            conditionsOf(
                "simplifying rejects more than 30 decimal places",
                AlgebraExplanationsBasic.ratioExplanation("Simplify"),
            ).contains("30 decimal places", ignoreCase = true),
        )
        assertTrue(
            conditionsOf(
                "splitting bounds the decimal places",
                AlgebraExplanationsBasic.ratioExplanation("Split"),
            ).contains("between 0 and 12", ignoreCase = true),
        )
        assertTrue(
            conditionsOf(
                "comparison needs two-term ratios",
                AlgebraExplanationsBasic.ratioExplanation("Compare"),
            ).contains("exactly two terms", ignoreCase = true),
        )
    }

    // --------------------------------------------------------------- percentage

    @Test fun everyPercentageModeHasCompleteProseAndRealSymbols() {
        percentageModes.forEach { mode ->
            assertComplete(AlgebraExplanationsBasic.percentageExplanation(mode), "percentage $mode")
        }
    }

    @Test fun percentageFormulasFollowTheMode() {
        val expected = mapOf(
            "PercentOf" to "amount = value × percentage ÷ 100",
            "FindWhole" to "whole = part × 100 ÷ percentage",
            "WhatPercent" to "percentage = part ÷ whole × 100",
            "Change" to "change % = (end − start) ÷ start × 100",
            "Original" to "original = final ÷ (1 ± percentage ÷ 100)",
            "Successive" to "final = base × Π(1 + changeᵢ ÷ 100)",
            "Points" to "percentage points = p₁ − p₂",
            "Relative" to "relative difference = |a − b| ÷ |(a + b) ÷ 2| × 100",
        )
        expected.forEach { (mode, formula) ->
            assertEquals(
                "the $mode formula should match the arithmetic on screen",
                formula,
                AlgebraExplanationsBasic.percentageExplanation(mode).formula,
            )
        }
    }

    @Test fun percentageLimitationsNameTheEightFormsAndTheModeSpecificBounds() {
        assertTrue(
            AlgebraExplanationsBasic.percentageExplanation("PercentOf").limitations!!
                .contains("Eight percentage forms are supported", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.percentageExplanation("Change").limitations!!
                .contains("starting value must not be zero", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.percentageExplanation("Original").limitations!!
                .contains("100% decrease has no recoverable original", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.percentageExplanation("Points").limitations!!
                .contains("compares percentages with each other", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.percentageExplanation("Relative").limitations!!
                .contains("no numerical tolerance", ignoreCase = true),
        )
    }

    @Test fun percentageKeepsTheWorkingItIsGiven() {
        val steps = listOf("Subtract the values", "Divide by the start", "Scale to 100")
        assertEquals(steps, AlgebraExplanationsBasic.percentageExplanation("Change", steps).steps)
        assertTrue(AlgebraExplanationsBasic.percentageExplanation("Change").steps.isEmpty())
    }

    @Test fun percentageAcceptsUnknownAndNullInputsWithoutCrashing() {
        val nullMode = AlgebraExplanationsBasic.percentageExplanation(null, null)
        val unknownMode = AlgebraExplanationsBasic.percentageExplanation("Nonsense", emptyList())
        assertComplete(nullMode, "percentage with a null mode")
        assertComplete(unknownMode, "percentage with an unknown mode")
        assertTrue(nullMode.steps.isEmpty())
        assertTrue(unknownMode.formula!!.contains("part ÷ whole × 100", ignoreCase = true))
    }

    // ------------------------------------------------------------------ average

    @Test fun everyAverageModeHasCompleteProseAndRealSymbols() {
        averageModes.forEach { mode ->
            assertComplete(AlgebraExplanationsBasic.averageExplanation(mode), "average $mode")
        }
    }

    @Test fun averageFormulasFollowTheMode() {
        val expected = mapOf(
            "Mean" to "mean = Σxᵢ ÷ n",
            "Weighted" to "weighted mean = Σ(xᵢ·wᵢ) ÷ Σwᵢ",
            "Median" to "median = middle value of the sorted list (mean of the two middles when n is even)",
            "Mode" to "mode = value(s) with the highest frequency",
            "Geometric" to "geometric mean = (x₁ × x₂ × … × xₙ)^(1/n)",
        )
        expected.forEach { (mode, formula) ->
            assertEquals(
                "the $mode formula should match the arithmetic on screen",
                formula,
                AlgebraExplanationsBasic.averageExplanation(mode).formula,
            )
        }
    }

    @Test fun averageLimitationsNameTheFiveAveragesAndTheRealBounds() {
        assertTrue(
            AlgebraExplanationsBasic.averageExplanation("Mean").limitations!!
                .contains("Five averages are supported", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.averageExplanation("Weighted").limitations!!
                .contains("must be non-negative", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.averageExplanation("Geometric").limitations!!
                .contains("zero and negative values are rejected", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.averageExplanation("Nonsense").limitations!!
                .contains("unrecognised mode", ignoreCase = true),
        )
    }

    @Test fun averageKeepsTheWorkingItIsGiven() {
        val steps = listOf("Add every value", "Divide by the count")
        assertEquals(steps, AlgebraExplanationsBasic.averageExplanation("Mean", steps).steps)
        assertTrue(AlgebraExplanationsBasic.averageExplanation("Mean").steps.isEmpty())
    }

    @Test fun averageAcceptsUnknownAndNullInputsWithoutCrashing() {
        val nullMode = AlgebraExplanationsBasic.averageExplanation(null, null)
        val unknownMode = AlgebraExplanationsBasic.averageExplanation("Nonsense", emptyList())
        assertComplete(nullMode, "average with a null mode")
        assertComplete(unknownMode, "average with an unknown mode")
        assertTrue(nullMode.steps.isEmpty())
        assertTrue(unknownMode.formula!!.contains("Σxᵢ ÷ n", ignoreCase = true))
    }

    // --------------------------------------------------------------- proportion

    @Test fun everyProportionModeHasCompleteProseAndRealSymbols() {
        ProportionMode.entries.forEach { mode ->
            assertComplete(
                AlgebraExplanationsBasic.proportionExplanation(mode, unknown = ProportionSlot.D),
                "proportion $mode",
            )
        }
    }

    @Test fun proportionFormulasMatchTheMode() {
        assertTrue(
            AlgebraExplanationsBasic.proportionExplanation(ProportionMode.Direct).formula!!
                .contains("a / b = c / d", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.proportionExplanation(ProportionMode.Direct).formula!!
                .contains("a × d = b × c", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.proportionExplanation(ProportionMode.Inverse).formula!!
                .contains("a × b = c × d", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.proportionExplanation(ProportionMode.Direct).limitations!!
                .contains("Exactly one of the four slots may be unknown", ignoreCase = true),
        )
    }

    @Test fun proportionUsesTheEngineVerificationAndTheEngineSteps() {
        val solution = ProportionEngine.solve(
            ProportionMode.Direct,
            a = BigDecimal("2"),
            b = BigDecimal("3"),
            c = BigDecimal("8"),
            d = null,
            unknown = ProportionSlot.D,
        )
        assertTrue(solution is CalcResult.Ok)
        val solved = (solution as CalcResult.Ok<ProportionSolution>).value
        val explanation = AlgebraExplanationsBasic.proportionExplanation(
            mode = solved.mode,
            steps = solved.steps,
            verification = solved.verification,
            unknown = solved.unknown,
        )
        assertEquals(solved.steps, explanation.steps)
        assertEquals(solved.verification, explanation.verification)
        assertComplete(explanation, "proportion with engine verification")
    }

    @Test fun proportionDescribesTheCheckWhenThereIsNoSolution() {
        val explanation = AlgebraExplanationsBasic.proportionExplanation(
            mode = ProportionMode.Direct,
            steps = null,
            verification = null,
        )
        assertComplete(explanation, "proportion without a solution")
        assertTrue(explanation.steps.isEmpty())
        assertTrue(explanation.verification!!.contains("Verification", ignoreCase = true))
        assertTrue(explanation.verification!!.contains("cross products", ignoreCase = true))
    }

    @Test fun proportionAcceptsNullInputsWithoutCrashing() {
        val explanation = AlgebraExplanationsBasic.proportionExplanation(
            mode = null,
            steps = null,
            verification = null,
            unknown = null,
        )
        assertComplete(explanation, "proportion with null inputs")
        assertTrue(explanation.formula!!.contains("a / b = c / d", ignoreCase = true))
    }

    // --------------------------------------------------------------------- ratio

    @Test fun everyRatioModeHasCompleteProseAndRealSymbols() {
        ratioModes.forEach { mode ->
            assertComplete(AlgebraExplanationsBasic.ratioExplanation(mode), "ratio $mode")
        }
    }

    @Test fun ratioFormulasFollowTheMode() {
        val expected = mapOf(
            "Simplify" to "a : b = (a ÷ g) : (b ÷ g),  g = gcd(a, b)",
            "Scale" to "a : b → (k · a) : (k · b)",
            "Split" to "shareᵢ = total × termᵢ ÷ (Σ of all terms)",
            "UnknownPart" to "unknown share = known share × its term ÷ the known term",
            "Combine" to "a : b  and  b : c  →  a : b : c",
            "Compare" to "a : b ⋛ c : d  ⟺  a × d ⋛ c × b",
        )
        expected.forEach { (mode, formula) ->
            assertEquals(
                "the $mode formula should match the arithmetic on screen",
                formula,
                AlgebraExplanationsBasic.ratioExplanation(mode).formula,
            )
        }
    }

    @Test fun ratioLimitationsNameTheSixFormsAndTheRealBounds() {
        assertTrue(
            AlgebraExplanationsBasic.ratioExplanation("Simplify").limitations!!
                .contains("Six ratio forms are supported", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.ratioExplanation("Simplify").limitations!!
                .contains("30 decimal places", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.ratioExplanation("Split").limitations!!
                .contains("banker's rounding", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.ratioExplanation("Combine").limitations!!
                .contains("two-term chain", ignoreCase = true),
        )
        assertTrue(
            AlgebraExplanationsBasic.ratioExplanation("Compare").limitations!!
                .contains("no numerical tolerance", ignoreCase = true),
        )
    }

    @Test fun ratioKeepsTheEngineStepsAndVerification() {
        val simplified = RatioEngine.simplify("12 : 18")
        assertTrue(simplified is CalcResult.Ok)
        val steps = (simplified as CalcResult.Ok<RatioEngine.SimplifiedRatio>).value.steps
        val verification = "5 : 8 keeps the ratio 40 : 64"
        val explanation = AlgebraExplanationsBasic.ratioExplanation(
            modeName = "Simplify",
            steps = steps,
            verification = verification,
        )
        assertEquals(steps, explanation.steps)
        assertEquals(verification, explanation.verification)
        assertComplete(explanation, "ratio with engine steps")

        val unknown = AlgebraExplanationsBasic.ratioExplanation(
            modeName = "UnknownPart",
            steps = null,
            verification = verification,
        )
        assertEquals(verification, unknown.verification)
    }

    @Test fun ratioAcceptsUnknownAndNullInputsWithoutCrashing() {
        val nullMode = AlgebraExplanationsBasic.ratioExplanation(null, null, null)
        val unknownMode = AlgebraExplanationsBasic.ratioExplanation("Nonsense", emptyList(), "")
        assertComplete(nullMode, "ratio with a null mode")
        assertComplete(unknownMode, "ratio with an unknown mode and blank verification")
        assertTrue(nullMode.steps.isEmpty())
        assertTrue(unknownMode.verification!!.isNotBlank())
    }
}
