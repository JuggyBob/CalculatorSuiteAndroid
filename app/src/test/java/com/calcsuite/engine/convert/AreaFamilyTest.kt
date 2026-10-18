package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.AreaFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

class AreaFamilyTest {

    @Test
    fun oneSquareMetreIsTenThousandSquareCentimetres() {
        assertExact("10000", AreaFamily.convert(BigDecimal.ONE, "m2", "cm2").valueOrNull()!!)
    }

    @Test
    fun oneAcreIsExactInternationalAcre() {
        assertExact("4046.8564224", AreaFamily.convert(BigDecimal.ONE, "acre", "m2").valueOrNull()!!)
    }

    @Test
    fun oneHectareIsTenThousandSquareMetres() {
        assertExact("10000", AreaFamily.convert(BigDecimal.ONE, "ha", "m2").valueOrNull()!!)
    }

    @Test
    fun oneSquareMileIsTwoPointFiveEightNineNineEightEightOneOneZeroThreeThreeSixSquareKilometres() {
        assertExact("2.589988110336", AreaFamily.convert(BigDecimal.ONE, "mi2", "km2").valueOrNull()!!)
    }

    @Test
    fun oneSquareFootIsExactlyZeroPointZeroNineTwoNineZeroThreeZeroFourSquareMetres() {
        assertExact("0.09290304", AreaFamily.convert(BigDecimal.ONE, "ft2", "m2").valueOrNull()!!)
    }

    @Test
    fun oneSquareYardIsNineSquareFeet() {
        assertExact("9", AreaFamily.convert(BigDecimal.ONE, "yd2", "ft2").valueOrNull()!!)
    }

    @Test
    fun oneAcreIsExactlyFortyThreeThousandFiveHundredSixtySquareFeet() {
        assertExact("43560", AreaFamily.convert(BigDecimal.ONE, "acre", "ft2").valueOrNull()!!)
    }

    @Test
    fun oneSquareInchIsSixPointFourFiveOneSixSquareCentimetres() {
        assertExact("6.4516", AreaFamily.convert(BigDecimal.ONE, "in2", "cm2").valueOrNull()!!)
    }

    @Test
    fun oneSquareKilometreIsOneHundredHectares() {
        assertExact("100", AreaFamily.convert(BigDecimal.ONE, "km2", "ha").valueOrNull()!!)
    }

    @Test
    fun oneSquareMillimetreIsZeroPointZeroOneSquareCentimetres() {
        assertExact("0.01", AreaFamily.convert(BigDecimal.ONE, "mm2", "cm2").valueOrNull()!!)
    }

    @Test
    fun hectareToAcresIsApproximatelyTwoPointFourSevenOne() {
        val result = AreaFamily.convert(BigDecimal.ONE, "ha", "acre").valueOrNull()!!
        assertTrue((result - BigDecimal("2.4710538147")).abs() < BigDecimal("1e-9"))
    }

    @Test
    fun squareMileInAcres() {
        assertExact("640", AreaFamily.convert(BigDecimal.ONE, "mi2", "acre").valueOrNull()!!)
    }

    @Test
    fun zeroAndNegativeAreLinear() {
        assertExact("0", AreaFamily.convert(BigDecimal.ZERO, "m2", "ft2").valueOrNull()!!)
        assertExact("-1", AreaFamily.convert(BigDecimal("-0.09290304"), "m2", "ft2").valueOrNull()!!)
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(AreaFamily.convert(BigDecimal.ONE, "m2", "furlong2") is com.calcsuite.core.CalcResult.Err)
    }
}
