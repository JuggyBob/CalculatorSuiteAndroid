package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs

class InterestEngineTest {

    private fun ok(result: CalcResult<InterestResult>): InterestResult {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun errorKind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun assertMoney(expected: String, actual: BigDecimal, message: String = "") {
        assertEquals(
            "$message expected $expected but was $actual",
            0,
            actual.compareTo(BigDecimal(expected)),
        )
    }

    private fun assertNear(expected: Double, actual: BigDecimal, tolerance: Double, message: String = "") {
        assertTrue(
            "$message expected ≈ $expected but was $actual",
            abs(actual.toDouble() - expected) < tolerance,
        )
    }

    // ------------------------------------------------------------------ simple

    @Test
    fun simpleInterestOneThousandAtTenPercentForOneYear() {
        val result = ok(InterestEngine.simple(BigDecimal("1000"), BigDecimal("10"), BigDecimal("1")))
        assertMoney("1100", result.finalAmount, "final amount")
        assertMoney("100", result.interestEarned, "interest")
        assertEquals(InterestMode.SIMPLE, result.mode)
    }

    @Test
    fun simpleInterestUsesLinearTime() {
        val result = ok(InterestEngine.simple(BigDecimal("1000"), BigDecimal("10"), BigDecimal("2")))
        assertMoney("1200", result.finalAmount)
        assertMoney("200", result.interestEarned)
    }

    @Test
    fun simpleInterestOverFractionalTime() {
        val result = ok(InterestEngine.simple(BigDecimal("500"), BigDecimal("4"), BigDecimal("0.5")))
        assertMoney("510", result.finalAmount)
        assertMoney("10", result.interestEarned)
    }

    @Test
    fun simpleResultIsNotApproximate() {
        val result = ok(InterestEngine.simple(BigDecimal("1000"), BigDecimal("10"), BigDecimal("1")))
        assertFalse(result.approximate)
        assertMoney("10", result.effectiveAnnualRatePct, "simple EAR equals the nominal rate")
    }

    // ---------------------------------------------------------------- compound

    @Test
    fun compoundAnnualTenPercentForTwoYears() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("10"),
                BigDecimal("2"),
                CompoundFrequency.ANNUAL,
            ),
        )
        assertMoney("1210", result.finalAmount)
        assertMoney("210", result.interestEarned)
        assertMoney("10", result.effectiveAnnualRatePct)
        assertFalse(result.approximate)
    }

    @Test
    fun compoundMonthlyTenPercentForOneYear() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("10"),
                BigDecimal("1"),
                CompoundFrequency.MONTHLY,
            ),
        )
        assertNear(1104.71, result.finalAmount, 0.005, "monthly compounding")
        assertNear(104.71, result.interestEarned, 0.005, "interest")
    }

    @Test
    fun compoundMonthlyFivePercentForOneYear() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("5"),
                BigDecimal("1"),
                CompoundFrequency.MONTHLY,
            ),
        )
        assertNear(1051.16, result.finalAmount, 0.005, "monthly compounding at 5%")
    }

    @Test
    fun continuousCompoundingOneYear() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("10"),
                BigDecimal("1"),
                CompoundFrequency.CONTINUOUS,
            ),
        )
        assertNear(1105.17, result.finalAmount, 0.02, "continuous compounding")
        assertTrue("continuous compounding uses the double fallback", result.approximate)
    }

    @Test
    fun compoundQuarterlyBeatsAnnual() {
        val annual = ok(
            InterestEngine.compound(BigDecimal("1000"), BigDecimal("8"), BigDecimal("5"), CompoundFrequency.ANNUAL),
        )
        val quarterly = ok(
            InterestEngine.compound(BigDecimal("1000"), BigDecimal("8"), BigDecimal("5"), CompoundFrequency.QUARTERLY),
        )
        assertTrue(quarterly.finalAmount > annual.finalAmount)
    }

    // ---------------------------------------------------------------------- EAR

    @Test
    fun effectiveAnnualRateMonthlyTenPercent() {
        val ear = InterestEngine.effectiveAnnualRatePct(BigDecimal("10"), CompoundFrequency.MONTHLY)
        assertTrue(ear is CalcResult.Ok)
        assertNear(10.4713, (ear as CalcResult.Ok).value, 1e-4, "EAR monthly 10%")
    }

    @Test
    fun effectiveAnnualRateAnnualEqualsNominal() {
        val ear = InterestEngine.effectiveAnnualRatePct(BigDecimal("7"), CompoundFrequency.ANNUAL)
        assertMoney("7", (ear as CalcResult.Ok).value)
    }

    @Test
    fun effectiveAnnualRateContinuous() {
        val ear = InterestEngine.effectiveAnnualRatePct(BigDecimal("10"), CompoundFrequency.CONTINUOUS)
        assertNear(10.5171, (ear as CalcResult.Ok).value, 0.001, "EAR continuous 10%")
    }

    // ------------------------------------------------------------- contributions

    @Test
    fun endOfPeriodContributionsMatchAnnuityFormula() {
        // 100 per month for one year at 6% nominal: 100·((1.005^12 − 1)/0.005).
        val result = ok(
            InterestEngine.compound(
                BigDecimal.ZERO,
                BigDecimal("6"),
                BigDecimal("1"),
                CompoundFrequency.MONTHLY,
                ContributionPlan(BigDecimal("100"), CompoundFrequency.MONTHLY, atBeginning = false),
            ),
        )
        assertNear(1233.5562, result.finalAmount, 0.01, "end-of-period annuity")
        assertMoney("1200", result.contributionsTotal, "contributions total")
    }

    @Test
    fun beginningOfPeriodContributionsEarnOneExtraPeriod() {
        val endOfPeriod = ok(
            InterestEngine.compound(
                BigDecimal.ZERO,
                BigDecimal("6"),
                BigDecimal("1"),
                CompoundFrequency.MONTHLY,
                ContributionPlan(BigDecimal("100"), CompoundFrequency.MONTHLY, atBeginning = false),
            ),
        )
        val beginning = ok(
            InterestEngine.compound(
                BigDecimal.ZERO,
                BigDecimal("6"),
                BigDecimal("1"),
                CompoundFrequency.MONTHLY,
                ContributionPlan(BigDecimal("100"), CompoundFrequency.MONTHLY, atBeginning = true),
            ),
        )
        assertNear(1239.7240, beginning.finalAmount, 0.01, "annuity due")
        assertTrue(beginning.finalAmount > endOfPeriod.finalAmount)
    }

    @Test
    fun contributionsAddToPrincipalGrowth() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("6"),
                BigDecimal("1"),
                CompoundFrequency.MONTHLY,
                ContributionPlan(BigDecimal("100"), CompoundFrequency.MONTHLY),
            ),
        )
        // 1000 grown monthly plus the annuity: both parts are present.
        assertTrue(result.finalAmount > BigDecimal("2000"))
        assertMoney("2200", result.totalContributed, "principal + contributions")
        assertMoney("1200", result.contributionsTotal)
    }

    @Test
    fun continuousBaseWithMonthlyContributionsIsFlaggedApproximate() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("5"),
                BigDecimal("2"),
                CompoundFrequency.CONTINUOUS,
                ContributionPlan(BigDecimal("50"), CompoundFrequency.MONTHLY),
            ),
        )
        assertTrue(result.approximate)
        assertTrue(result.finalAmount > BigDecimal("1000"))
    }

    @Test
    fun continuousContributionFrequencyRejected() {
        val result = InterestEngine.compound(
            BigDecimal("1000"),
            BigDecimal("5"),
            BigDecimal("2"),
            CompoundFrequency.ANNUAL,
            ContributionPlan(BigDecimal("50"), CompoundFrequency.CONTINUOUS),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    // -------------------------------------------------------------------- edges

    @Test
    fun zeroDurationReturnsPrincipal() {
        val simple = ok(InterestEngine.simple(BigDecimal("500"), BigDecimal("12"), BigDecimal.ZERO))
        assertMoney("500", simple.finalAmount)
        assertMoney("0", simple.interestEarned)
        val compound = ok(
            InterestEngine.compound(BigDecimal("500"), BigDecimal("12"), BigDecimal.ZERO, CompoundFrequency.MONTHLY),
        )
        assertMoney("500", compound.finalAmount)
        assertMoney("0", compound.interestEarned)
    }

    @Test
    fun zeroRateReturnsPrincipalPlusContributions() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("100"),
                BigDecimal.ZERO,
                BigDecimal("1"),
                CompoundFrequency.MONTHLY,
                ContributionPlan(BigDecimal("25"), CompoundFrequency.MONTHLY),
            ),
        )
        assertMoney("400", result.finalAmount)
        assertMoney("0", result.interestEarned)
        assertMoney("300", result.contributionsTotal)
    }

    @Test
    fun negativeInputsRejected() {
        assertEquals(
            ErrorKind.InvalidInput,
            errorKind(InterestEngine.simple(BigDecimal("-1"), BigDecimal("5"), BigDecimal("1"))),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            errorKind(InterestEngine.simple(BigDecimal("1"), BigDecimal("-5"), BigDecimal("1"))),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            errorKind(InterestEngine.simple(BigDecimal("1"), BigDecimal("5"), BigDecimal("-1"))),
        )
    }

    @Test
    fun monthsToYearsConvertsExactly() {
        assertMoney("1.5", InterestEngine.monthsToYears(BigDecimal("18")))
        assertMoney("0.5", InterestEngine.monthsToYears(BigDecimal("6")))
    }

    // ----------------------------------------------------------------- comparison

    @Test
    fun simpleVersusCompoundComparison() {
        val comparison = InterestEngine.compare(BigDecimal("1000"), BigDecimal("10"), BigDecimal("2"), CompoundFrequency.ANNUAL)
        assertTrue(comparison is CalcResult.Ok)
        val value = (comparison as CalcResult.Ok<InterestComparison>).value
        assertMoney("1200", value.simpleFinal)
        assertMoney("1210", value.compoundFinal)
        assertMoney("10", value.difference)
        assertMoney("200", value.simpleInterest)
        assertMoney("210", value.compoundInterest)
    }

    // -------------------------------------------------------------- pow fallback

    @Test
    fun integerPowersStayExactAndFractionalOnesAreFlagged() {
        val exact = InterestEngine.decimalPow(BigDecimal("1.1"), BigDecimal("2"), InterestEngine.MC)
        assertTrue(exact != null)
        assertEquals(false, exact!!.second)
        assertMoney("1.21", exact.first)

        val fractional = InterestEngine.decimalPow(BigDecimal("1.1"), BigDecimal("0.5"), InterestEngine.MC)
        assertTrue(fractional != null)
        assertEquals(true, fractional!!.second)
        assertNear(1.0488088482, fractional.first, 1e-9, "sqrt(1.1) via the double fallback")
    }

    @Test
    fun fractionalDurationCompoundedMonthlyIsFlaggedApproximate() {
        val result = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("10"),
                BigDecimal("0.5"),
                CompoundFrequency.MONTHLY,
            ),
        )
        assertTrue("6 periods are integer: exact", !result.approximate)
        val odd = ok(
            InterestEngine.compound(
                BigDecimal("1000"),
                BigDecimal("10"),
                BigDecimal("0.45"),
                CompoundFrequency.MONTHLY,
            ),
        )
        // 5.4 periods — the fractional exponent falls back to double precision.
        assertTrue(odd.approximate)
    }
}
