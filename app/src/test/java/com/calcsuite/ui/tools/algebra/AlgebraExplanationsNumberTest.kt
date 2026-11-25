package com.calcsuite.ui.tools.algebra

import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.algebra.CombinatoricsEngine
import com.calcsuite.engine.algebra.GcfLcmEngine
import com.calcsuite.engine.algebra.PrimeCheck
import com.calcsuite.engine.algebra.PrimeEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * The number-tool explanations are built from the values the screens hold.
 *
 * Every section has to carry real prose, the formula has to belong to the
 * mode that is on screen, and a null or failed result must still produce a
 * readable explanation rather than an exception.
 */
class AlgebraExplanationsNumberTest {

    private fun bi(value: String): BigInteger = BigInteger(value)

    private fun Explanation.assertWellFormed() {
        assertTrue("definition must be written", !definition.isNullOrBlank())
        assertTrue("formula must be written", !formula.isNullOrBlank())
        assertTrue(
            "symbols must be written",
            symbols.isNotEmpty() && symbols.all { it.first.isNotBlank() && it.second.isNotBlank() },
        )
        assertTrue(
            "conditions must be written",
            conditions.isNotEmpty() && conditions.all { it.isNotBlank() },
        )
        assertNotNull(method)
        assertTrue("method must be written", !method.isNullOrBlank())
        assertTrue(
            "steps must be written",
            steps.isNotEmpty() && steps.all { it.isNotBlank() },
        )
        assertNotNull(interpretation)
        assertTrue("interpretation must be written", !interpretation.isNullOrBlank())
        assertNotNull(verification)
        assertTrue("verification must be written", !verification.isNullOrBlank())
        assertNotNull(limitations)
        assertTrue("limitations must be written", !limitations.isNullOrBlank())
        assertFalse("there must be something to show", isEmpty)

        val prose = listOfNotNull(definition, formula, method, interpretation, verification, limitations) +
            conditions + steps + symbols.flatMap { listOf(it.first, it.second) }
        assertTrue(
            "no placeholder text: $prose",
            prose.none {
                it.contains("TODO", ignoreCase = true) ||
                    it.contains("placeholder", ignoreCase = true)
            },
        )
    }

    @Test
    fun gcfLcmShowsTheProductIdentityForTwoValues() {
        val report = GcfLcmEngine.analyze(listOf(bi("18"), bi("24"))).valueOrNull()!!
        val explanation = AlgebraExplanationsNumber.gcfLcmExplanation(report)
        explanation.assertWellFormed()
        assertTrue(explanation.formula!!.contains("|a × b| ÷ gcd(a, b)"))
        assertTrue(explanation.verification!!.contains("6 × 72 = 432"))
        assertTrue(explanation.verification.contains("18 × 24 = 432"))
        assertTrue(explanation.steps.any { it.startsWith("Euclid's algorithm") })
        assertTrue(explanation.conditions.any { it.contains("absolute values") })
        assertTrue(explanation.limitations!!.contains("18 digits"))
    }

    @Test
    fun gcfLcmChecksDivisibilityForMoreThanTwoValues() {
        val report = GcfLcmEngine.analyze(listOf(bi("12"), bi("18"), bi("30"))).valueOrNull()!!
        val explanation = AlgebraExplanationsNumber.gcfLcmExplanation(report)
        explanation.assertWellFormed()
        assertTrue(explanation.verification!!.contains("pairwise product identity applies only to two values"))
        assertTrue(explanation.verification.contains("divisible by 6"))
        assertTrue(explanation.verification.contains("divides 180"))
    }

    @Test
    fun gcfLcmNullReportExplainsTheMethodWithoutCrashing() {
        val explanation = AlgebraExplanationsNumber.gcfLcmExplanation(null)
        explanation.assertWellFormed()
        assertTrue(explanation.steps.any { it.contains("Euclid's algorithm") })
        assertTrue(explanation.verification!!.contains("gcd × lcm = |a × b|"))
    }

    @Test
    fun combinationsShowTheCombinationFormulaAndNotThePermutationFormula() {
        val selection = CombinatoricsEngine.combinations(bi("5"), bi("2")).valueOrNull()!!
        val explanation = AlgebraExplanationsNumber.combinatoricsExplanation("Combination", selection, null)
        explanation.assertWellFormed()
        assertTrue(explanation.formula!!.contains("C(n, r)"))
        assertFalse(explanation.formula.contains("P(n, r)"))
        assertTrue(explanation.formula.contains("(5 × 4) ÷ 2!"))
        assertTrue(explanation.interpretation!!.contains("order does not matter"))
        assertTrue(explanation.steps.any { it.contains("r!") })
    }

    @Test
    fun permutationsShowThePermutationFormulaAndTheFactorialLink() {
        val selection = CombinatoricsEngine.permutations(bi("5"), bi("2")).valueOrNull()!!
        val explanation = AlgebraExplanationsNumber.combinatoricsExplanation("Permutation", selection, null)
        explanation.assertWellFormed()
        assertTrue(explanation.formula!!.contains("P(n, r)"))
        assertFalse(explanation.formula.contains("C(n, r)"))
        assertTrue(explanation.verification!!.contains("P(n, r) = C(n, r) × r!"))
        assertTrue(explanation.interpretation!!.contains("larger"))
        assertTrue(explanation.definition!!.contains("at most once"))
    }

    @Test
    fun probabilityShowsTheHypergeometricRatioAndTheBinomialContrast() {
        val probability = CombinatoricsEngine.selectionProbability(
            bi("49"), bi("6"), bi("6"), bi("6"),
        ).valueOrNull()!!
        val explanation = AlgebraExplanationsNumber.combinatoricsExplanation("Probability", null, probability)
        explanation.assertWellFormed()
        assertTrue(explanation.formula!!.contains("C(K, k)"))
        assertTrue(explanation.formula.contains("C(N, n)"))
        assertTrue(explanation.steps.any { it.contains("C(49, 6)") })
        assertTrue(explanation.interpretation!!.contains("binomial"))
        assertTrue(explanation.interpretation.contains("without replacement"))
        assertTrue(explanation.conditions.any { it.contains("between 0 and 1") })
        assertTrue(explanation.limitations!!.contains("1 000 000"))
    }

    @Test
    fun combinatoricsNullResultsKeepTheModeConditions() {
        val permutation = AlgebraExplanationsNumber.combinatoricsExplanation("Permutation", null, null)
        permutation.assertWellFormed()
        assertTrue(permutation.formula!!.contains("P(n, r)"))
        assertTrue(permutation.steps.any { it.contains("order matters") })

        val combination = AlgebraExplanationsNumber.combinatoricsExplanation("Combination", null, null)
        combination.assertWellFormed()
        assertTrue(combination.formula!!.contains("C(n, r)"))

        val probability = AlgebraExplanationsNumber.combinatoricsExplanation("Probability", null, null)
        probability.assertWellFormed()
        assertTrue(probability.formula!!.contains("C(N, n)"))
        assertTrue(probability.conditions.any { it.contains("n ≥ r ≥ 0") })
    }

    @Test
    fun primeProvenVerdictUsesTheEngineExplanationAndTheFactorCheck() {
        val check = PrimeEngine.isPrime(bi("97"))
        val factorization = PrimeEngine.factorizeDetailed(bi("360"))
        val explanation = AlgebraExplanationsNumber.primeExplanation(check, factorization)
        explanation.assertWellFormed()
        assertTrue(check.proven)
        assertTrue(explanation.interpretation!!.contains("97 is prime"))
        assertTrue(explanation.verification!!.contains("multiplied back together"))
        assertTrue(explanation.verification.contains("360 = 2^3 × 3^2 × 5"))
        assertTrue(explanation.steps.any { it.contains("deterministic bases") })
        assertTrue(explanation.limitations!!.contains("40 fixed Miller-Rabin bases"))
    }

    @Test
    fun primeProbableVerdictSaysItIsNotProven() {
        val check = PrimeCheck(
            n = bi("1000000000000000000000000000003"),
            isPrime = true,
            proven = false,
            explanation = "strong probable prime after 40 fixed Miller-Rabin rounds",
        )
        val explanation = AlgebraExplanationsNumber.primeExplanation(check, null)
        explanation.assertWellFormed()
        assertTrue(explanation.interpretation!!.contains("strong probable prime"))
        assertTrue(explanation.steps.any { it.contains("40 fixed Miller-Rabin bases") })
        assertTrue(explanation.limitations!!.contains("not a proof"))
    }

    @Test
    fun primeNullCheckAndFailedFactorisationDoNotCrash() {
        val failed = PrimeEngine.factorizeDetailed(bi("0"))
        val withoutCheck = AlgebraExplanationsNumber.primeExplanation(null, failed)
        withoutCheck.assertWellFormed()
        assertTrue(withoutCheck.verification!!.contains("0 has no prime factorization"))

        val negative = AlgebraExplanationsNumber.primeExplanation(PrimeEngine.isPrime(bi("-7")), null)
        negative.assertWellFormed()
        assertTrue(negative.interpretation!!.contains("Negative numbers are not prime"))
    }

    @Test
    fun randomIntegerFormulaGivesTheUniformChance() {
        val notes = listOf(
            "Seeded with 42: the same rules reproduce this exact list (java.util.Random is not cryptographically secure)",
            "Both endpoints are inclusive: 1 and 100 can appear",
        )
        val explanation = AlgebraExplanationsNumber.randomExplanation(
            mode = "Integer",
            generated = listOf("4", "7", "9"),
            notes = notes,
            summary = listOf("Count" to "3"),
            lastAction = "3 integer(s) in [1, 100]",
        )
        explanation.assertWellFormed()
        assertTrue(explanation.formula!!.contains("1/(b − a + 1)"))
        assertTrue(explanation.verification!!.contains("reproduce"))
        assertTrue(explanation.steps.any { it.contains("Seeded with 42") })
        assertTrue(explanation.interpretation!!.contains("smallest"))
        assertTrue(explanation.limitations!!.contains("1000"))
    }

    @Test
    fun randomDecimalFormulaUsesTheGridAndSeededVerification() {
        val explanation = AlgebraExplanationsNumber.randomExplanation(
            mode = "Decimal",
            generated = listOf("1.25"),
            notes = listOf("Values carry exactly 2 decimal place(s) and both endpoints are inclusive"),
            summary = emptyList(),
            lastAction = "1 decimal(s) in [1.00, 2.00] with 2 decimal place(s)",
        )
        explanation.assertWellFormed()
        assertTrue(explanation.formula!!.contains("10^−p"))
        assertTrue(explanation.formula.contains("ceil(min × 10^p)"))
        assertTrue(explanation.definition!!.contains("not a draw from the continuous interval"))
        assertTrue(explanation.symbols.any { it.first == "p" })
        assertTrue(explanation.conditions.any { it.contains("0 and 12") })
    }

    @Test
    fun randomSecureNotesSuppressSeedReproducibility() {
        val explanation = AlgebraExplanationsNumber.randomExplanation(
            mode = "Integer",
            generated = emptyList(),
            notes = listOf("Values come from java.security.SecureRandom (cryptographically strong); the seed is ignored"),
            summary = emptyList(),
            lastAction = "",
        )
        explanation.assertWellFormed()
        assertTrue(explanation.verification!!.contains("ignores the seed"))
        assertTrue(explanation.limitations!!.contains("not cryptographically secure"))
        assertTrue(explanation.steps.any { it.contains("SecureRandom") })
    }

    @Test
    fun randomNullInputsDoNotCrash() {
        val explanation = AlgebraExplanationsNumber.randomExplanation(
            mode = "",
            generated = emptyList(),
            notes = emptyList(),
            summary = emptyList(),
            lastAction = "",
        )
        explanation.assertWellFormed()
        assertTrue(explanation.definition!!.contains("uniform"))
        assertTrue(explanation.verification!!.contains("Type a seed"))
    }

    @Test
    fun allBuildersHaveProseSymbolsAndNoPlaceholdersWhenResultsAreMissing() {
        val explanations = listOf(
            AlgebraExplanationsNumber.gcfLcmExplanation(null),
            AlgebraExplanationsNumber.combinatoricsExplanation("Combination", null, null),
            AlgebraExplanationsNumber.combinatoricsExplanation("Permutation", null, null),
            AlgebraExplanationsNumber.combinatoricsExplanation("Probability", null, null),
            AlgebraExplanationsNumber.primeExplanation(null, null),
            AlgebraExplanationsNumber.randomExplanation("Integer", emptyList(), emptyList(), emptyList(), ""),
        )
        explanations.forEach { it.assertWellFormed() }
    }
}
