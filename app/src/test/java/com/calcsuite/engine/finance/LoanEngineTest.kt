package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LoanEngineTest {

    private fun ok(result: CalcResult<LoanEngine.LoanResult>): LoanEngine.LoanResult {
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
        val diff = actual.toDouble() - expected
        assertTrue("$message expected ≈ $expected but was $actual", kotlin.math.abs(diff) < tolerance)
    }

    private fun zeroInterestLoan(): LoanEngine.LoanResult = ok(
        LoanEngine.compute(
            LoanEngine.LoanInput(
                principal = BigDecimal("1200"),
                annualRatePct = BigDecimal.ZERO,
                numberOfPayments = 12,
                paymentsPerYear = 12,
                includeSchedule = true,
            ),
        ),
    )

    @Test
    fun zeroInterestLoanPaysExactlyPrincipal() {
        val result = zeroInterestLoan()
        assertMoney("100.00", result.scheduledPayment)
        assertMoney("1200.00", result.totalPaid)
        assertMoney("0.00", result.totalInterest)
        assertEquals(12, result.payoffPeriods)
        assertTrue(result.zeroInterest)
    }

    @Test
    fun zeroInterestScheduleHasNoInterest() {
        val result = zeroInterestLoan()
        assertEquals(12, result.schedule.size)
        result.schedule.forEach { row ->
            assertMoney("0.00", row.interest, "interest in period ${row.period}")
            assertMoney("100.00", row.payment, "payment in period ${row.period}")
        }
        assertMoney("0.00", result.schedule.last().balance, "final balance")
    }

    @Test
    fun mortgageTenThousandAtSixPercent() {
        val result = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("10000"),
                    annualRatePct = BigDecimal("6"),
                    numberOfPayments = 60,
                    paymentsPerYear = 12,
                ),
            ),
        )
        assertNear(193.33, result.scheduledPayment, 0.005, "payment")
        assertNear(1599.68, result.totalInterest, 0.05, "total interest")
        assertEquals(60, result.payoffPeriods)
    }

    @Test
    fun schedulePrincipalSumsToLoanAndEndsAtZero() {
        val result = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("10000"),
                    annualRatePct = BigDecimal("6"),
                    numberOfPayments = 60,
                    paymentsPerYear = 12,
                    includeSchedule = true,
                ),
            ),
        )
        assertEquals(60, result.schedule.size)
        val principalSum = result.schedule.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.principal) }
        assertMoney("10000", principalSum, "sum of principal parts")
        assertMoney("0.00", result.schedule.last().balance, "final balance")
        assertFalse(result.scheduleTruncated)
    }

    @Test
    fun extraFiftyPerMonthShortensTermAndInterest() {
        val base = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("10000"),
                    annualRatePct = BigDecimal("6"),
                    numberOfPayments = 60,
                ),
            ),
        )
        val withExtra = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("10000"),
                    annualRatePct = BigDecimal("6"),
                    numberOfPayments = 60,
                    extraPerPeriod = BigDecimal("50"),
                ),
            ),
        )
        assertTrue(
            "payoff ${withExtra.payoffPeriods} should be shorter than ${base.payoffPeriods}",
            withExtra.payoffPeriods < base.payoffPeriods,
        )
        assertTrue(
            "interest ${withExtra.totalInterest} should be lower than ${base.totalInterest}",
            withExtra.totalInterest < base.totalInterest,
        )
    }

    @Test
    fun oneOffExtraPaymentAlsoSavesInterest() {
        val base = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("10000"),
                    annualRatePct = BigDecimal("6"),
                    numberOfPayments = 60,
                ),
            ),
        )
        val lumpSum = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("10000"),
                    annualRatePct = BigDecimal("6"),
                    numberOfPayments = 60,
                    oneOffExtra = BigDecimal("1000"),
                    oneOffAtPeriod = 12,
                ),
            ),
        )
        assertTrue(lumpSum.payoffPeriods < base.payoffPeriods)
        assertTrue(lumpSum.totalInterest < base.totalInterest)
        assertEquals(base.scheduledPayment, lumpSum.scheduledPayment)
    }

    @Test
    fun textbookMortgageTwoHundredThousand() {
        val result = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("200000"),
                    annualRatePct = BigDecimal("5"),
                    termYears = BigDecimal("30"),
                    paymentsPerYear = 12,
                ),
            ),
        )
        assertEquals(360, result.contractualPayments)
        assertNear(1073.64, result.scheduledPayment, 0.005, "payment")
        assertEquals(360, result.payoffPeriods)
    }

    @Test
    fun weeklyFrequencyIsSupported() {
        val result = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("1000"),
                    annualRatePct = BigDecimal.ZERO,
                    termYears = BigDecimal("1"),
                    paymentsPerYear = 52,
                ),
            ),
        )
        assertEquals(52, result.contractualPayments)
        assertMoney("1000.00", result.totalPaid)
    }

    @Test
    fun longScheduleIsTruncatedWithNote() {
        // A 1202-payment zero-interest loan pays off in exactly 1202 periods,
        // two rows beyond the schedule cap.
        val result = ok(
            LoanEngine.compute(
                LoanEngine.LoanInput(
                    principal = BigDecimal("1202"),
                    annualRatePct = BigDecimal.ZERO,
                    numberOfPayments = 1202,
                    includeSchedule = true,
                ),
            ),
        )
        assertEquals(1202, result.payoffPeriods)
        assertEquals(LoanEngine.MAX_SCHEDULE_ROWS, result.schedule.size)
        assertTrue(result.scheduleTruncated)
        val note = LoanEngine.scheduleNote(result)
        assertTrue(note != null && note.contains("1200") && note.contains("1202"))
    }

    @Test
    fun negativePrincipalRejected() {
        val result = LoanEngine.compute(
            LoanEngine.LoanInput(
                principal = BigDecimal("-100"),
                annualRatePct = BigDecimal("5"),
                numberOfPayments = 12,
            ),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun zeroPaymentsRejected() {
        val result = LoanEngine.compute(
            LoanEngine.LoanInput(
                principal = BigDecimal("100"),
                annualRatePct = BigDecimal("5"),
                numberOfPayments = 0,
            ),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun missingTermIsInsufficientInformation() {
        val result = LoanEngine.compute(
            LoanEngine.LoanInput(principal = BigDecimal("100"), annualRatePct = BigDecimal("5")),
        )
        assertEquals(ErrorKind.Insufficient, errorKind(result))
    }

    @Test
    fun negativeRateRejected() {
        val result = LoanEngine.compute(
            LoanEngine.LoanInput(
                principal = BigDecimal("100"),
                annualRatePct = BigDecimal("-1"),
                numberOfPayments = 12,
            ),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun paymentWrapperMatchesEngine() {
        val payment = LoanEngine.payment(BigDecimal("1200"), BigDecimal.ZERO, 12)
        assertTrue(payment is CalcResult.Ok)
        assertMoney("100", (payment as CalcResult.Ok<BigDecimal>).value)
    }

    @Test
    fun payoffLabelReadsInYearsAndMonths() {
        assertEquals("5 years", LoanEngine.payoffLabel(60, 12))
        assertEquals("30 years", LoanEngine.payoffLabel(360, 12))
        assertEquals("3 years 11 months", LoanEngine.payoffLabel(47, 12))
        assertEquals("1 month", LoanEngine.payoffLabel(1, 12))
    }
}
