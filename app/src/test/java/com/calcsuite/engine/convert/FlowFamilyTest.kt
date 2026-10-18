package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.engine.convert.families.FlowFamily
import com.calcsuite.engine.convert.families.FlowTimeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

private fun assertClose(expected: String, actual: BigDecimal, tol: String) {
    assertTrue("expected $expected ± $tol but was $actual", (actual - BigDecimal(expected)).abs() < BigDecimal(tol))
}

class FlowFamilyTest {

    @Test
    fun oneCubicMetrePerSecondIsOneThousandLitresPerSecond() {
        assertExact("1000", FlowFamily.convert(BigDecimal.ONE, "m3_s", "L_s").valueOrNull()!!)
    }

    @Test
    fun oneCubicMetrePerSecondIsSixtyThousandLitresPerMinute() {
        assertClose("60000", FlowFamily.convert(BigDecimal.ONE, "m3_s", "L_min").valueOrNull()!!, "1e-20")
    }

    @Test
    fun oneLitrePerSecondIsSixtyLitresPerMinute() {
        assertClose("60", FlowFamily.convert(BigDecimal.ONE, "L_s", "L_min").valueOrNull()!!, "1e-20")
    }

    @Test
    fun oneLitrePerMinuteIsAboutZeroPointTwoSixFourUsGallonsPerMinute() {
        assertClose("0.2641720524", FlowFamily.convert(BigDecimal.ONE, "L_min", "us_gpm").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneImperialGallonPerMinuteIsFourPointFiveFourSixZeroNineLitresPerMinute() {
        assertClose("4.54609", FlowFamily.convert(BigDecimal.ONE, "imp_gpm", "L_min").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneUsGallonPerMinuteIsThreePointSevenEightFiveFourOneOneSevenEightFourLitresPerMinute() {
        assertClose("3.785411784", FlowFamily.convert(BigDecimal.ONE, "us_gpm", "L_min").valueOrNull()!!, "1e-9")
    }

    @Test
    fun usAndImperialGpmDiffer() {
        val us = FlowFamily.convert(BigDecimal.ONE, "us_gpm", "L_min").valueOrNull()!!
        val imp = FlowFamily.convert(BigDecimal.ONE, "imp_gpm", "L_min").valueOrNull()!!
        assertTrue(imp.compareTo(us) > 0)
        assertClose("0.8326741846", FlowFamily.convert(BigDecimal.ONE, "us_gpm", "imp_gpm").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneCubicFootPerSecondIsTheExactCubicMetreValue() {
        assertExact("0.028316846592", FlowFamily.convert(BigDecimal.ONE, "ft3_s", "m3_s").valueOrNull()!!)
    }

    @Test
    fun oneCubicFootPerSecondIsTwentyEightPointThreeOneSixEightFourSixFiveNineTwoLitresPerSecond() {
        assertExact("28.316846592", FlowFamily.convert(BigDecimal.ONE, "ft3_s", "L_s").valueOrNull()!!)
    }

    @Test
    fun oneCubicMetrePerHourIsZeroPointTwoSevenSevenSevenSevenSevenSevenSevenSevenEightLitresPerSecond() {
        assertClose("0.2777777778", FlowFamily.convert(BigDecimal.ONE, "m3_h", "L_s").valueOrNull()!!, "1e-9")
    }

    @Test
    fun threeThousandSixHundredCubicMetresPerHourIsOneCubicMetrePerSecond() {
        assertClose("1", FlowFamily.convert(BigDecimal("3600"), "m3_h", "m3_s").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneMillilitrePerSecondIsOneThousandthOfALitrePerSecond() {
        assertExact("0.001", FlowFamily.convert(BigDecimal.ONE, "mL_s", "L_s").valueOrNull()!!)
    }

    @Test
    fun negativeFlowIsLinear() {
        assertExact("-1000", FlowFamily.convert(BigDecimal("-1"), "m3_s", "L_s").valueOrNull()!!)
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(FlowFamily.convert(BigDecimal.ONE, "m3_s", "gph") is CalcResult.Err)
    }

    // ---- FlowTimeEngine -----------------------------------------------------

    @Test
    fun tenLitresPerMinuteForOneMinuteIsTenLitres() {
        val result = FlowTimeEngine.volumeFrom(BigDecimal("10"), "L_min", BigDecimal("60")).valueOrNull()!!
        assertClose("10", result, "1e-20")
    }

    @Test
    fun oneCubicMetrePerSecondForOneHourIsThreeThousandSixHundredCubicMetres() {
        val result = FlowTimeEngine.volumeFrom(BigDecimal.ONE, "m3_s", BigDecimal("3600")).valueOrNull()!!
        assertClose("3600", result, "1e-15")
    }

    @Test
    fun oneUsGallonPerMinuteForOneMinuteIsOneUsGallon() {
        val result = FlowTimeEngine.volumeFrom(BigDecimal.ONE, "us_gpm", BigDecimal("60")).valueOrNull()!!
        assertClose("1", result, "1e-20")
    }

    @Test
    fun oneImperialGallonPerMinuteForOneMinuteIsOneImperialGallon() {
        val result = FlowTimeEngine.volumeFrom(BigDecimal.ONE, "imp_gpm", BigDecimal("60")).valueOrNull()!!
        assertClose("1", result, "1e-20")
    }

    @Test
    fun volumeComesBackInTheNaturalUnitOfTheFlowUnit() {
        assertEquals("m3", FlowTimeEngine.volumeUnitFor("m3_s"))
        assertEquals("L", FlowTimeEngine.volumeUnitFor("L_min"))
        assertEquals("mL", FlowTimeEngine.volumeUnitFor("mL_s"))
        assertEquals("us_gal", FlowTimeEngine.volumeUnitFor("us_gpm"))
        assertEquals("imp_gal", FlowTimeEngine.volumeUnitFor("imp_gpm"))
        assertEquals("ft3", FlowTimeEngine.volumeUnitFor("ft3_s"))
    }

    @Test
    fun negativeTimeIsRejected() {
        val result = FlowTimeEngine.volumeFrom(BigDecimal.ONE, "L_min", BigDecimal("-1"))
        assertEquals(ErrorKind.Domain, (result as CalcResult.Err).error.kind)
    }

    @Test
    fun unknownFlowUnitIsRejectedByVolumeFrom() {
        assertTrue(FlowTimeEngine.volumeFrom(BigDecimal.ONE, "buckets_s", BigDecimal.ONE) is CalcResult.Err)
    }

    @Test
    fun oneHundredLitresAtTenLitresPerMinuteTakesTenMinutes() {
        val result = FlowTimeEngine.timeFor(BigDecimal("100"), "L", BigDecimal("10"), "L_min").valueOrNull()!!
        assertClose("600", result, "1e-15")
    }

    @Test
    fun oneCubicMetreAtOneCubicMetrePerSecondTakesOneSecond() {
        val result = FlowTimeEngine.timeFor(BigDecimal.ONE, "m3", BigDecimal.ONE, "m3_s").valueOrNull()!!
        assertClose("1", result, "1e-20")
    }

    @Test
    fun mixingVolumeAndFlowUnitsIsAllowed() {
        // One US gallon at one litre per minute = 3.785411784 min = 227.12470704 s.
        val result = FlowTimeEngine.timeFor(BigDecimal.ONE, "us_gal", BigDecimal.ONE, "L_min").valueOrNull()!!
        assertClose("227.12470704", result, "1e-9")
    }

    @Test
    fun zeroFlowRateIsRejected() {
        val result = FlowTimeEngine.timeFor(BigDecimal.ONE, "L", BigDecimal.ZERO, "L_min")
        assertEquals(ErrorKind.DivisionByZero, (result as CalcResult.Err).error.kind)
    }

    @Test
    fun negativeFlowRateIsRejected() {
        val result = FlowTimeEngine.timeFor(BigDecimal.ONE, "L", BigDecimal("-1"), "L_min")
        assertEquals(ErrorKind.Domain, (result as CalcResult.Err).error.kind)
    }

    @Test
    fun unknownVolumeUnitIsRejected() {
        assertTrue(FlowTimeEngine.timeFor(BigDecimal.ONE, "bucket", BigDecimal.ONE, "L_min") is CalcResult.Err)
    }
}
