package com.calcsuite.engine.health

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalorieEngineTest {

    private fun value(result: CalcResult<CalorieEngine.Result>): CalorieEngine.Result {
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<CalorieEngine.Result>): String {
        assertTrue("Expected failure, was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    @Test
    fun briefExampleGrossAndNet() {
        val result = value(
            CalorieEngine.compute(
                massKg = BigDecimal("70"),
                minutes = BigDecimal("30"),
                met = BigDecimal("8.0"),
            ),
        )
        assertEquals(294.0, result.grossKcal.toDouble(), 0.01)
        assertEquals(257.25, result.netKcal.toDouble(), 0.01)
        assertEquals(9.8, result.kcalPerMinute.toDouble(), 0.01)
        assertEquals(8.0, result.met.toDouble(), 0.001)
    }

    @Test
    fun zeroMinutesIsRejected() {
        assertEquals(
            "Duration must be greater than zero",
            error(CalorieEngine.compute(BigDecimal("70"), BigDecimal.ZERO, BigDecimal("8.0"))),
        )
    }

    @Test
    fun invalidMassAndMetAreRejected() {
        assertEquals(
            "Body mass must be between 20 kg and 400 kg",
            error(CalorieEngine.compute(BigDecimal("10"), BigDecimal("30"), BigDecimal("8.0"))),
        )
        assertEquals(
            "Body mass must be greater than zero",
            error(CalorieEngine.compute(BigDecimal("0"), BigDecimal("30"), BigDecimal("8.0"))),
        )
        assertEquals(
            "MET value must be greater than zero",
            error(CalorieEngine.compute(BigDecimal("70"), BigDecimal("30"), BigDecimal.ZERO)),
        )
    }

    @Test
    fun negativeInputsAreRejected() {
        assertEquals(
            "Body mass must be greater than zero",
            error(CalorieEngine.compute(BigDecimal("-70"), BigDecimal("30"), BigDecimal("8.0"))),
        )
        assertEquals(
            "Duration must be greater than zero",
            error(CalorieEngine.compute(BigDecimal("70"), BigDecimal("-30"), BigDecimal("8.0"))),
        )
        assertEquals(
            "MET value must be greater than zero",
            error(CalorieEngine.compute(BigDecimal("70"), BigDecimal("30"), BigDecimal("-8"))),
        )
    }

    @Test
    fun customMetIsRespected() {
        // 6.0 MET for 45 minutes at 80 kg: 6 × 3.5 × 80 / 200 = 8.4 kcal/min → 378 kcal.
        val result = value(CalorieEngine.compute(BigDecimal("80"), BigDecimal("45"), BigDecimal("6.0")))
        assertEquals(378.0, result.grossKcal.toDouble(), 0.01)
        assertEquals(315.0, result.netKcal.toDouble(), 0.01)
    }

    @Test
    fun metTableContainsTheRequiredActivities() {
        assertTrue(CalorieEngine.activities.size >= 28)
        assertEquals(0, CalorieEngine.activity("running_10")!!.met.compareTo(BigDecimal("9.8")))
        assertEquals(0, CalorieEngine.activity("swimming_laps")!!.met.compareTo(BigDecimal("9.8")))
        assertEquals(0, CalorieEngine.activity("jump_rope")!!.met.compareTo(BigDecimal("11.0")))
        assertEquals(0, CalorieEngine.activity("yoga")!!.met.compareTo(BigDecimal("2.5")))
        assertEquals(0, CalorieEngine.activity("martial_arts")!!.met.compareTo(BigDecimal("10.3")))
    }

    @Test
    fun activityLookupDrivesTheCalculation() {
        val result = value(
            CalorieEngine.computeForActivity("yoga", BigDecimal("70"), BigDecimal("60")),
        )
        assertEquals(183.75, result.grossKcal.toDouble(), 0.01)
    }

    @Test
    fun unknownActivityIsRejected() {
        assertEquals(
            "Unknown activity",
            error(CalorieEngine.computeForActivity("teleporting", BigDecimal("70"), BigDecimal("30"))),
        )
    }

    @Test
    fun belowRestingMetGivesNegativeNetWithNote() {
        val result = value(CalorieEngine.compute(BigDecimal("70"), BigDecimal("30"), BigDecimal("0.9")))
        assertTrue(result.netKcal.toDouble() < 0.0)
        assertTrue(result.notes.any { it.contains("negative") })
    }

    @Test
    fun sourceIsCitedInNotes() {
        val result = value(CalorieEngine.compute(BigDecimal("70"), BigDecimal("30"), BigDecimal("8.0")))
        assertTrue(result.notes.any { it.contains("Compendium of Physical Activities") })
        assertTrue(CalorieEngine.SOURCE.contains("Ainsworth"))
    }
}
