package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.PressureFamily
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

class PressureFamilyTest {

    @Test
    fun oneAtmosphereIsOneHundredAndOneThousandThreeHundredAndTwentyFivePascals() {
        assertExact("101325", PressureFamily.convert(BigDecimal.ONE, "atm", "Pa").valueOrNull()!!)
    }

    @Test
    fun oneBarIsOneHundredThousandPascals() {
        assertExact("100000", PressureFamily.convert(BigDecimal.ONE, "bar", "Pa").valueOrNull()!!)
    }

    @Test
    fun oneAtmosphereIsSevenHundredAndSixtyTorr() {
        assertClose("760", PressureFamily.convert(BigDecimal.ONE, "atm", "torr").valueOrNull()!!, "1e-9")
    }

    @Test
    fun onePsiIsApproximatelySixtyEightNinetyFourPointSevenFiveSevenPascals() {
        assertClose("6894.757", PressureFamily.convert(BigDecimal.ONE, "psi", "Pa").valueOrNull()!!, "1e-3")
    }

    @Test
    fun oneMillibarIsOneHundredPascals() {
        assertExact("100", PressureFamily.convert(BigDecimal.ONE, "mbar", "Pa").valueOrNull()!!)
    }

    @Test
    fun oneHectopascalIsOneHundredPascals() {
        assertExact("100", PressureFamily.convert(BigDecimal.ONE, "hPa", "Pa").valueOrNull()!!)
    }

    @Test
    fun oneKilopascalIsOneThousandPascals() {
        assertExact("1000", PressureFamily.convert(BigDecimal.ONE, "kPa", "Pa").valueOrNull()!!)
    }

    @Test
    fun oneMegapascalIsOneMillionPascals() {
        assertExact("1000000", PressureFamily.convert(BigDecimal.ONE, "MPa", "Pa").valueOrNull()!!)
    }

    @Test
    fun torrAndMillimetresOfMercuryAgreeExactly() {
        assertExact("1", PressureFamily.convert(BigDecimal.ONE, "torr", "mmHg").valueOrNull()!!)
    }

    @Test
    fun sevenHundredAndSixtyTorrIsOneAtmosphere() {
        assertClose("1", PressureFamily.convert(BigDecimal("760"), "torr", "atm").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneBarInAtmospheres() {
        assertClose("0.9869232667", PressureFamily.convert(BigDecimal.ONE, "bar", "atm").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneAtmosphereInMillibar() {
        assertExact("1013.25", PressureFamily.convert(BigDecimal.ONE, "atm", "mbar").valueOrNull()!!)
    }

    @Test
    fun twoPointTwoBarInPsi() {
        assertClose("31.90830230064602889225457380137995", PressureFamily.convert(BigDecimal("2.2"), "bar", "psi").valueOrNull()!!, "1e-6")
    }

    @Test
    fun negativeGaugePressureIsLinear() {
        assertExact("-100000", PressureFamily.convert(BigDecimal("-1"), "bar", "Pa").valueOrNull()!!)
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(PressureFamily.convert(BigDecimal.ONE, "Pa", "psf") is com.calcsuite.core.CalcResult.Err)
    }
}
