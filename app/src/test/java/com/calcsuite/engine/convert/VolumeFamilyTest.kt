package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.VolumeFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

private fun assertClose(expected: String, actual: BigDecimal, tol: String) {
    assertTrue("expected $expected ± $tol but was $actual", (actual - BigDecimal(expected)).abs() < BigDecimal(tol))
}

class VolumeFamilyTest {

    @Test
    fun oneLitreIsOneThousandMillilitres() {
        assertExact("1000", VolumeFamily.convert(BigDecimal.ONE, "L", "mL").valueOrNull()!!)
    }

    @Test
    fun oneCubicMetreIsOneThousandLitres() {
        assertExact("1000", VolumeFamily.convert(BigDecimal.ONE, "m3", "L").valueOrNull()!!)
    }

    @Test
    fun oneUsGallonIsThreePointSevenEightFiveFourOneOneSevenEightFourLitres() {
        assertExact("3.785411784", VolumeFamily.convert(BigDecimal.ONE, "us_gal", "L").valueOrNull()!!)
    }

    @Test
    fun oneImperialGallonIsFourPointFiveFourSixZeroNineLitres() {
        assertExact("4.54609", VolumeFamily.convert(BigDecimal.ONE, "imp_gal", "L").valueOrNull()!!)
    }

    @Test
    fun usAndImperialGallonsDiffer() {
        val us = VolumeFamily.convert(BigDecimal.ONE, "us_gal", "L").valueOrNull()!!
        val imp = VolumeFamily.convert(BigDecimal.ONE, "imp_gal", "L").valueOrNull()!!
        assertNotEquals(0, us.compareTo(imp))
        assertClose("0.8326741846", VolumeFamily.convert(BigDecimal.ONE, "us_gal", "imp_gal").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneLitreIsZeroPointTwoSixFourOneSevenTwoZeroFiveTwoFourUsGallons() {
        assertClose("0.2641720524", VolumeFamily.convert(BigDecimal.ONE, "L", "us_gal").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneImperialGallonInUsGallons() {
        assertClose(
            "1.2009499255",
            VolumeFamily.convert(BigDecimal.ONE, "imp_gal", "us_gal").valueOrNull()!!,
            "1e-9",
        )
    }

    @Test
    fun oneUsFluidOunceIsTwentyNinePointFiveSevenThreeFiveTwoNineFiveSixTwoFiveMillilitres() {
        assertExact("29.5735295625", VolumeFamily.convert(BigDecimal.ONE, "us_floz", "mL").valueOrNull()!!)
    }

    @Test
    fun oneImperialFluidOunceIsTwentyEightPointFourOneThreeZeroSixTwoFiveMillilitres() {
        assertExact("28.4130625", VolumeFamily.convert(BigDecimal.ONE, "imp_floz", "mL").valueOrNull()!!)
    }

    @Test
    fun oneUsPintIsSixteenFluidOunces() {
        assertExact("16", VolumeFamily.convert(BigDecimal.ONE, "us_pt", "us_floz").valueOrNull()!!)
    }

    @Test
    fun oneUsGallonIsOneHundredAndTwentyEightFluidOunces() {
        assertExact("128", VolumeFamily.convert(BigDecimal.ONE, "us_gal", "us_floz").valueOrNull()!!)
    }

    @Test
    fun oneUsCupIsEightFluidOunces() {
        assertExact("8", VolumeFamily.convert(BigDecimal.ONE, "us_cup", "us_floz").valueOrNull()!!)
    }

    @Test
    fun oneImperialPintIsTwentyImperialFluidOunces() {
        assertExact("20", VolumeFamily.convert(BigDecimal.ONE, "imp_pt", "imp_floz").valueOrNull()!!)
    }

    @Test
    fun oneCubicFootIsTheExactLitreValue() {
        assertExact("28.316846592", VolumeFamily.convert(BigDecimal.ONE, "ft3", "L").valueOrNull()!!)
    }

    @Test
    fun oneCubicYardIsTwentySevenCubicFeet() {
        assertExact("27", VolumeFamily.convert(BigDecimal.ONE, "yd3", "ft3").valueOrNull()!!)
    }

    @Test
    fun oneCubicInchIsSixteenPointThreeEightSevenZeroSixFourMillilitres() {
        assertExact("16.387064", VolumeFamily.convert(BigDecimal.ONE, "in3", "mL").valueOrNull()!!)
    }

    @Test
    fun oneCubicMetreInUsGallons() {
        assertClose("264.1720523581484153798999216091625", VolumeFamily.convert(BigDecimal.ONE, "m3", "us_gal").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneCubicCentimetreIsOneMillilitre() {
        assertExact("1", VolumeFamily.convert(BigDecimal.ONE, "cm3", "mL").valueOrNull()!!)
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(VolumeFamily.convert(BigDecimal.ONE, "L", "hogshead") is com.calcsuite.core.CalcResult.Err)
    }
}
