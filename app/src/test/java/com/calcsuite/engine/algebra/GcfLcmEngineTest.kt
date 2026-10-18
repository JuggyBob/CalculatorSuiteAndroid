package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class GcfLcmEngineTest {

    private fun big(text: String) = BigInteger(text)

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun message(result: CalcResult<*>): String {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    private fun <T> okValue(result: CalcResult<T>): T {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return result.valueOrNull()!!
    }

    // ---- gcf and lcm --------------------------------------------------------

    @Test
    fun gcfExample() {
        // The brief's example: GCF(18, 24) = 6.
        assertEquals(big("6"), okValue(GcfLcmEngine.gcf(big("18"), big("24"))))
    }

    @Test
    fun lcmExample() {
        // The brief's example: LCM(12, 18) = 36.
        assertEquals(big("36"), okValue(GcfLcmEngine.lcm(big("12"), big("18"))))
    }

    @Test
    fun gcfIgnoresLeadingZeros() {
        assertEquals(big("5"), okValue(GcfLcmEngine.gcf(big("0"), big("5"))))
        assertEquals(big("6"), okValue(GcfLcmEngine.gcf(listOf(big("18"), big("0"), big("24")))))
    }

    @Test
    fun gcfOfZeroAndZeroIsUndefined() {
        val result = GcfLcmEngine.gcf(big("0"), big("0"))
        assertEquals(ErrorKind.Undefined, kind(result))
        assertEquals("GCF of 0 and 0 is undefined", message(result))
    }

    @Test
    fun lcmWithZeroIsZero() {
        assertEquals(BigInteger.ZERO, okValue(GcfLcmEngine.lcm(big("0"), big("5"))))
        assertEquals(BigInteger.ZERO, okValue(GcfLcmEngine.lcm(listOf(big("4"), big("0"), big("6")))))
    }

    @Test
    fun gcfAndLcmOfSets() {
        assertEquals(big("6"), okValue(GcfLcmEngine.gcf(listOf(big("18"), big("24"), big("30")))))
        assertEquals(big("60"), okValue(GcfLcmEngine.lcm(listOf(big("3"), big("4"), big("5")))))
        assertEquals(big("12"), okValue(GcfLcmEngine.lcm(big("4"), big("6"))))
    }

    @Test
    fun emptySetIsRejected() {
        assertEquals(ErrorKind.Insufficient, kind(GcfLcmEngine.gcf(emptyList<BigInteger>())))
        assertEquals(ErrorKind.Insufficient, kind(GcfLcmEngine.lcm(emptyList<BigInteger>())))
        assertEquals(ErrorKind.Insufficient, kind(GcfLcmEngine.analyze(emptyList())))
    }

    @Test
    fun negativesUseAbsoluteValues() {
        assertEquals(big("6"), okValue(GcfLcmEngine.gcf(big("-18"), big("24"))))
        val report = okValue(GcfLcmEngine.analyze(listOf(big("-18"), big("24"))))
        assertEquals(big("6"), report.gcf)
        assertTrue(report.notes.any { it.contains("absolute") })
    }

    // ---- Euclid steps -------------------------------------------------------

    @Test
    fun euclidStepsShowTheChain() {
        val steps = GcfLcmEngine.euclidSteps(big("24"), big("18"))
        assertTrue(steps.first().contains("Euclid's algorithm"))
        assertTrue(steps.any { it == "24 = 1 × 18 + 6" })
        assertTrue(steps.any { it == "18 = 3 × 6 + 0" })
        assertTrue(steps.last().contains("greatest common factor is 6"))
    }

    // ---- full report --------------------------------------------------------

    @Test
    fun analyzeTwoNumbers() {
        val report = okValue(GcfLcmEngine.analyze(listOf(big("18"), big("24"))))
        assertEquals(big("6"), report.gcf)
        assertEquals(big("72"), report.lcm)
        assertEquals(2, report.factorizations.size)
        assertTrue("expected steps, was $report", report.steps.isNotEmpty())
        assertTrue(report.steps.any { it.contains("Common prime powers") })
        assertTrue(report.steps.any { it.contains("All prime powers") })
    }

    @Test
    fun analyzeFactorizationsAreGrouped() {
        val report = okValue(GcfLcmEngine.analyze(listOf(big("18"), big("24"))))
        assertEquals("18 = 2 × 3^2", report.factorizations[0].text)
        assertEquals("24 = 2^3 × 3", report.factorizations[1].text)
    }

    @Test
    fun analyzeThreeNumbers() {
        val report = okValue(GcfLcmEngine.analyze(listOf(big("12"), big("18"), big("24"))))
        assertEquals(big("6"), report.gcf)
        assertEquals(big("72"), report.lcm)
    }

    @Test
    fun analyzeWithZero() {
        val report = okValue(GcfLcmEngine.analyze(listOf(big("0"), big("12"))))
        assertEquals(big("12"), report.gcf)
        assertEquals(BigInteger.ZERO, report.lcm)
        assertTrue(report.notes.any { it.contains("LCM is 0") })
    }

    @Test
    fun analyzeAllZerosIsUndefined() {
        val result = GcfLcmEngine.analyze(listOf(big("0"), big("0")))
        assertEquals(ErrorKind.Undefined, kind(result))
        assertEquals("GCF of 0 and 0 is undefined", message(result))
    }

    // ---- parsing ------------------------------------------------------------

    @Test
    fun parseIntegersList() {
        val values = okValue(GcfLcmEngine.parseIntegers("18, 24, 36"))
        assertEquals(listOf(big("18"), big("24"), big("36")), values)
        assertEquals(listOf(big("18"), big("24")), okValue(GcfLcmEngine.parseIntegers("18; 24")))
    }

    @Test
    fun parseIntegersRejectsGarbage() {
        assertEquals(ErrorKind.InvalidInput, kind(GcfLcmEngine.parseIntegers("x")))
        assertEquals(ErrorKind.InvalidInput, kind(GcfLcmEngine.parseIntegers("18, 2.5")))
        assertEquals(ErrorKind.Insufficient, kind(GcfLcmEngine.parseIntegers("  ")))
    }
}
