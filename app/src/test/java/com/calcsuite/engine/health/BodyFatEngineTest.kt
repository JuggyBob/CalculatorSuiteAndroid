package com.calcsuite.engine.health

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs

class BodyFatEngineTest {

    private fun value(result: CalcResult<BodyFatResult>): BodyFatResult {
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<BodyFatResult>): String {
        assertTrue("Expected failure, was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    /**
     * Cross-check with java.lang.Math. The published Hodgdon–Beckett constants
     * are defined with measurements in inches, so the centimetre inputs are
     * converted (1 in = 2.54 cm) exactly as the engine does.
     */
    private fun expectedMale(heightCm: Double, neckCm: Double, abdomenCm: Double): Double =
        86.010 * Math.log10((abdomenCm - neckCm) / 2.54) -
            70.041 * Math.log10(heightCm / 2.54) + 36.76

    private fun expectedFemale(heightCm: Double, neckCm: Double, waistCm: Double, hipCm: Double): Double =
        163.205 * Math.log10((waistCm + hipCm - neckCm) / 2.54) -
            97.684 * Math.log10(heightCm / 2.54) - 78.387

    @Test
    fun maleExampleMatchesTheDocumentedFormula() {
        val result = value(
            BodyFatEngine.estimate(
                BodyFatInput(
                    variant = FormulaVariant.MALE,
                    heightCm = BigDecimal("180"),
                    neckCm = BigDecimal("38"),
                    abdomenCm = BigDecimal("90"),
                ),
            ),
        )
        val expected = expectedMale(180.0, 38.0, 90.0)
        assertTrue(
            "male estimate ${result.percent} vs formula $expected",
            abs(result.percent.toDouble() - expected) < 0.5,
        )
        // The documented formula with these measurements gives roughly 19.9%;
        // it must stay in a physiologically plausible band.
        assertTrue(result.percent.toDouble() in 15.0..25.0)
    }

    @Test
    fun maleHighBodyFatExampleIsPlausible() {
        // 90 cm abdomen at 180 cm: a moderate estimate, never negative or absurd.
        val result = value(
            BodyFatEngine.estimate(
                BodyFatInput(FormulaVariant.MALE, BigDecimal("180"), BigDecimal("40"), BigDecimal("105")),
            ),
        )
        assertTrue(result.percent.toDouble() > 20.0)
        assertTrue(result.percent.toDouble() < 40.0)
    }

    @Test
    fun femaleExampleIsPlausible() {
        val result = value(
            BodyFatEngine.estimate(
                BodyFatInput(
                    variant = FormulaVariant.FEMALE,
                    heightCm = BigDecimal("165"),
                    neckCm = BigDecimal("33"),
                    waistCm = BigDecimal("72"),
                    hipCm = BigDecimal("96"),
                ),
            ),
        )
        val expected = expectedFemale(165.0, 33.0, 72.0, 96.0)
        assertTrue("female estimate ${result.percent} vs formula $expected", abs(result.percent.toDouble() - expected) < 0.5)
        assertTrue("female estimate ${result.percent} should be 24–30%", result.percent.toDouble() in 24.0..30.0)
    }

    @Test
    fun abdomenNotGreaterThanNeckIsRejected() {
        val message = error(
            BodyFatEngine.estimate(
                BodyFatInput(FormulaVariant.MALE, BigDecimal("180"), BigDecimal("90"), BigDecimal("85")),
            ),
        )
        assertEquals("Abdomen circumference must be greater than the neck circumference", message)
    }

    @Test
    fun zeroMeasurementsAreRejected() {
        assertEquals(
            "Height must be greater than zero",
            error(BodyFatEngine.estimate(BodyFatInput(FormulaVariant.MALE, BigDecimal.ZERO, BigDecimal("38"), BigDecimal("90")))),
        )
        assertEquals(
            "Neck circumference must be greater than zero",
            error(BodyFatEngine.estimate(BodyFatInput(FormulaVariant.MALE, BigDecimal("180"), BigDecimal.ZERO, BigDecimal("90")))),
        )
        assertEquals(
            "Waist circumference must be greater than zero",
            error(
                BodyFatEngine.estimate(
                    BodyFatInput(
                        FormulaVariant.FEMALE,
                        BigDecimal("165"),
                        BigDecimal("33"),
                        waistCm = BigDecimal("0"),
                        hipCm = BigDecimal("96"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun femaleNeckNotExceededByWaistPlusHipIsRejected() {
        val message = error(
            BodyFatEngine.estimate(
                BodyFatInput(
                    variant = FormulaVariant.FEMALE,
                    heightCm = BigDecimal("165"),
                    neckCm = BigDecimal("100"),
                    waistCm = BigDecimal("40"),
                    hipCm = BigDecimal("50"),
                ),
            ),
        )
        assertEquals("Waist plus hip circumference must exceed the neck circumference", message)
    }

    @Test
    fun extremeLowEstimateIsClampedWithNote() {
        val result = value(
            BodyFatEngine.estimate(
                BodyFatInput(FormulaVariant.MALE, BigDecimal("250"), BigDecimal("100"), BigDecimal("101")),
            ),
        )
        assertTrue(result.clamped)
        assertEquals(0, result.percent.compareTo(BigDecimal("3.0")))
        assertTrue(result.notes.any { it.contains("unreliable") })
    }

    @Test
    fun optionalMassGivesLeanAndFatMass() {
        val result = value(
            BodyFatEngine.estimate(
                BodyFatInput(
                    variant = FormulaVariant.MALE,
                    heightCm = BigDecimal("180"),
                    neckCm = BigDecimal("38"),
                    abdomenCm = BigDecimal("90"),
                    bodyMassKg = BigDecimal("80"),
                ),
            ),
        )
        val fat = result.fatMassKg!!
        val lean = result.leanMassKg!!
        assertEquals(80.0, fat.add(lean).toDouble(), 0.01)
        assertEquals(80.0 * result.percent.toDouble() / 100.0, fat.toDouble(), 0.01)
        assertTrue(result.notes.any { it.contains("Lean and fat mass") })
    }

    @Test
    fun methodAndLimitationsAreReported() {
        val result = value(
            BodyFatEngine.estimate(
                BodyFatInput(FormulaVariant.MALE, BigDecimal("180"), BigDecimal("38"), BigDecimal("90")),
            ),
        )
        assertTrue(result.methodName.contains("Navy"))
        assertEquals(4, result.limitations.size)
        assertTrue(result.limitations.any { it.contains("±3–4 percentage points") })
        assertTrue(result.notes.any { it.contains("inches") })
    }
}
