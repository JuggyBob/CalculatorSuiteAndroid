package com.calcsuite.engine.health

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class BmiEngineTest {

    private fun value(result: CalcResult<BmiResult>): BmiResult {
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<BmiResult>): String {
        assertTrue("Expected failure, was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    @Test
    fun metricExampleIsHealthy() {
        val result = value(BmiEngine.compute(BigDecimal("70"), BigDecimal("1.75")))
        assertEquals(22.86, result.bmi.toDouble(), 0.01)
        assertEquals(BmiCategory.HEALTHY, result.category)
        assertTrue(result.notes.any { it.contains("WHO adult classification") })
        assertTrue(result.notes.any { it.contains("screening") })
    }

    @Test
    fun imperialExample() {
        // 154 lb and 5 ft 10 in are the same physical person as 69.85 kg / 1.778 m.
        val result = value(
            BmiEngine.computeImperial(BigDecimal("154"), feet = 5, inches = BigDecimal("10")),
        )
        assertEquals(22.1, result.bmi.toDouble(), 0.1)
        assertEquals(BmiCategory.HEALTHY, result.category)
    }

    @Test
    fun zeroHeightIsRejected() {
        val message = error(BmiEngine.compute(BigDecimal("70"), BigDecimal.ZERO))
        assertEquals("Height must be greater than zero", message)
    }

    @Test
    fun implausibleBoundsAreRejected() {
        assertEquals(
            "Height must be between 50 cm and 250 cm",
            error(BmiEngine.compute(BigDecimal("70"), BigDecimal("0.4"))),
        )
        assertEquals(
            "Height must be between 50 cm and 250 cm",
            error(BmiEngine.compute(BigDecimal("70"), BigDecimal("2.6"))),
        )
        assertEquals(
            "Body mass must be between 1 kg and 500 kg",
            error(BmiEngine.compute(BigDecimal("600"), BigDecimal("1.75"))),
        )
        assertEquals(
            "Body mass must be greater than zero",
            error(BmiEngine.compute(BigDecimal("0"), BigDecimal("1.75"))),
        )
    }

    @Test
    fun negativeValuesAreRejected() {
        assertEquals(
            "Body mass must be greater than zero",
            error(BmiEngine.compute(BigDecimal("-70"), BigDecimal("1.75"))),
        )
        assertEquals(
            "Height must be greater than zero",
            error(BmiEngine.compute(BigDecimal("70"), BigDecimal("-1.75"))),
        )
        assertEquals(
            "Height must be greater than zero",
            error(BmiEngine.computeImperial(BigDecimal("154"), feet = 0, inches = BigDecimal.ZERO)),
        )
    }

    @Test
    fun minorsGetNoClassification() {
        val result = value(BmiEngine.compute(BigDecimal("70"), BigDecimal("1.75"), isMinor = true))
        assertNull(result.category)
        assertTrue(result.notes.any { it.contains("under 18") })
        assertTrue(result.notes.any { it.contains("growth charts") })
    }

    @Test
    fun healthyRangeForOnePointSevenFiveMetres() {
        val (min, max) = BmiEngine.healthyRange(BigDecimal("1.75"))
        assertEquals(56.7, min.setScale(1, RoundingMode.HALF_UP).toDouble(), 0.01)
        assertEquals(76.3, max.setScale(1, RoundingMode.HALF_UP).toDouble(), 0.01)
    }

    @Test
    fun categoryBoundariesFollowWhoBands() {
        assertEquals(BmiCategory.UNDERWEIGHT, BmiEngine.classifyAdult(BigDecimal("18.4")))
        assertEquals(BmiCategory.HEALTHY, BmiEngine.classifyAdult(BigDecimal("18.5")))
        assertEquals(BmiCategory.HEALTHY, BmiEngine.classifyAdult(BigDecimal("24.94")))
        assertEquals(BmiCategory.OVERWEIGHT, BmiEngine.classifyAdult(BigDecimal("25.0")))
        assertEquals(BmiCategory.OVERWEIGHT, BmiEngine.classifyAdult(BigDecimal("29.9")))
        assertEquals(BmiCategory.OBESITY_CLASS_1, BmiEngine.classifyAdult(BigDecimal("30.0")))
        assertEquals(BmiCategory.OBESITY_CLASS_2, BmiEngine.classifyAdult(BigDecimal("35.0")))
        assertEquals(BmiCategory.OBESITY_CLASS_3, BmiEngine.classifyAdult(BigDecimal("40.0")))
    }

    @Test
    fun classThreeExampleIsClassified() {
        val result = value(BmiEngine.compute(BigDecimal("110"), BigDecimal("1.7")))
        assertEquals(BmiCategory.OBESITY_CLASS_2, result.category)
        assertNotNull(result.bmi)
        assertTrue(result.bmi.toDouble() > 38.0)
    }

    @Test
    fun notesListHealthyBand() {
        val result = value(BmiEngine.compute(BigDecimal("70"), BigDecimal("1.75")))
        assertTrue(result.notes.any { it.contains("Healthy range") })
    }
}
