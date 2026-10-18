package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.SpeedFamily
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

class SpeedFamilyTest {

    @Test
    fun oneMetrePerSecondIsThreePointSixKilometresPerHour() {
        assertClose("3.6", SpeedFamily.convert(BigDecimal.ONE, "m_s", "km_h").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneKnotIsOnePointEightFiveTwoKilometresPerHour() {
        assertClose("1.852", SpeedFamily.convert(BigDecimal.ONE, "kn", "km_h").valueOrNull()!!, "1e-9")
    }

    @Test
    fun sixtyMilesPerHourIsTwentySixPointEightTwoTwoFourMetresPerSecond() {
        assertExact("26.8224", SpeedFamily.convert(BigDecimal("60"), "mph", "m_s").valueOrNull()!!)
    }

    @Test
    fun oneMetrePerSecondInMilesPerHour() {
        assertClose("2.2369362921", SpeedFamily.convert(BigDecimal.ONE, "m_s", "mph").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneKilometrePerHourInMilesPerHour() {
        assertClose("0.6213711922", SpeedFamily.convert(BigDecimal.ONE, "km_h", "mph").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneFootPerSecondIsExactlyZeroPointThreeZeroFourEightMetresPerSecond() {
        assertExact("0.3048", SpeedFamily.convert(BigDecimal.ONE, "ft_s", "m_s").valueOrNull()!!)
    }

    @Test
    fun oneMilePerHourIsExactlyOnePointSixZeroNineThreeFourFourKilometresPerHour() {
        assertClose("1.609344", SpeedFamily.convert(BigDecimal.ONE, "mph", "km_h").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneHundredKilometresPerHourInMilesPerHour() {
        assertClose("62.1371192237", SpeedFamily.convert(BigDecimal("100"), "km_h", "mph").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneKnotInMetresPerSecond() {
        assertClose("0.5144444444", SpeedFamily.convert(BigDecimal.ONE, "kn", "m_s").valueOrNull()!!, "1e-9")
    }

    @Test
    fun tenKnotsInKilometresPerHour() {
        assertClose("18.52", SpeedFamily.convert(BigDecimal("10"), "kn", "km_h").valueOrNull()!!, "1e-9")
    }

    @Test
    fun negativeSpeedIsDirection() {
        assertClose("-3.6", SpeedFamily.convert(BigDecimal("-1"), "m_s", "km_h").valueOrNull()!!, "1e-9")
    }

    @Test
    fun zeroStaysZero() {
        assertEquals(0, SpeedFamily.convert(BigDecimal.ZERO, "mph", "kn").valueOrNull()!!.signum())
    }

    @Test
    fun roundTripMilesPerHour() {
        val there = SpeedFamily.convert(BigDecimal("70"), "mph", "m_s").valueOrNull()!!
        val back = SpeedFamily.convert(there, "m_s", "mph").valueOrNull()!!
        assertClose("70", back, "1e-18")
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(SpeedFamily.convert(BigDecimal.ONE, "m_s", "c") is com.calcsuite.core.CalcResult.Err)
    }
}
