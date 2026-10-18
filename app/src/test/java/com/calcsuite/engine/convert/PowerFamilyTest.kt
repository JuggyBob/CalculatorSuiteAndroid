package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.PowerFamily
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

class PowerFamilyTest {

    @Test
    fun oneMechanicalHorsepowerIsTheExactWattValue() {
        assertExact("745.69987158227022", PowerFamily.convert(BigDecimal.ONE, "hp", "W").valueOrNull()!!)
    }

    @Test
    fun oneMetricHorsepowerIsSevenThirtyFivePointFourNineEightSevenFiveWatts() {
        assertExact("735.49875", PowerFamily.convert(BigDecimal.ONE, "PS", "W").valueOrNull()!!)
    }

    @Test
    fun oneKilowattIsOneThousandWatts() {
        assertExact("1000", PowerFamily.convert(BigDecimal.ONE, "kW", "W").valueOrNull()!!)
    }

    @Test
    fun oneMegawattIsOneMillionWatts() {
        assertExact("1000000", PowerFamily.convert(BigDecimal.ONE, "MW", "W").valueOrNull()!!)
    }

    @Test
    fun btuPerHourIsTheInternationalTableValue() {
        assertExact("0.29307107017222", PowerFamily.convert(BigDecimal.ONE, "BTU_h", "W").valueOrNull()!!)
    }

    @Test
    fun oneWattInBtuPerHour() {
        assertClose("3.412141633", PowerFamily.convert(BigDecimal.ONE, "W", "BTU_h").valueOrNull()!!, "1e-6")
    }

    @Test
    fun mechanicalHorsepowerIsAboutOnePointZeroOneFourMetricHorsepower() {
        val result = PowerFamily.convert(BigDecimal.ONE, "hp", "PS").valueOrNull()!!
        assertClose("1.013869665", result, "1e-6")
    }

    @Test
    fun hundredHorsepowerInKilowatts() {
        assertExact("74.569987158227022", PowerFamily.convert(BigDecimal("100"), "hp", "kW").valueOrNull()!!)
    }

    @Test
    fun oneHorsepowerIsFiftyFiveFootPoundsPerSecondTimesAConsistentFigure() {
        // 550 ft·lbf/s = 550 × 0.3048 × 4.4482216152605 W
        val expected = BigDecimal("550")
            .multiply(BigDecimal("0.3048"))
            .multiply(BigDecimal("4.4482216152605"))
        assertExact(expected.toPlainString(), PowerFamily.convert(BigDecimal.ONE, "hp", "W").valueOrNull()!!)
    }

    @Test
    fun negativePowerIsLinear() {
        assertExact("-745.69987158227022", PowerFamily.convert(BigDecimal("-1"), "hp", "W").valueOrNull()!!)
    }

    @Test
    fun roundTripHorsepower() {
        val there = PowerFamily.convert(BigDecimal("3.5"), "hp", "BTU_h").valueOrNull()!!
        val back = PowerFamily.convert(there, "BTU_h", "hp").valueOrNull()!!
        assertClose("3.5", back, "1e-18")
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(PowerFamily.convert(BigDecimal.ONE, "W", "steam-hp") is com.calcsuite.core.CalcResult.Err)
    }
}
