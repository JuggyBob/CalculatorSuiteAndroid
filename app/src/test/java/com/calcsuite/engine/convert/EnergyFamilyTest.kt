package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.EnergyFamily
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

class EnergyFamilyTest {

    @Test
    fun oneKilowattHourIsThreeMillionSixHundredThousandJoules() {
        assertExact("3600000", EnergyFamily.convert(BigDecimal.ONE, "kWh", "J").valueOrNull()!!)
    }

    @Test
    fun oneCalorieIsFourPointOneEightFourJoules() {
        assertExact("4.184", EnergyFamily.convert(BigDecimal.ONE, "cal", "J").valueOrNull()!!)
    }

    @Test
    fun oneKilocalorieIsFourThousandOneHundredAndEightyFourJoules() {
        assertExact("4184", EnergyFamily.convert(BigDecimal.ONE, "kcal", "J").valueOrNull()!!)
    }

    @Test
    fun oneBtuIsTheInternationalTableValue() {
        assertExact("1055.05585262", EnergyFamily.convert(BigDecimal.ONE, "BTU", "J").valueOrNull()!!)
    }

    @Test
    fun foodCalorieIsOneKilocalorie() {
        assertExact("1", EnergyFamily.convert(BigDecimal.ONE, "Cal", "kcal").valueOrNull()!!)
    }

    @Test
    fun oneWattHourIsThreeThousandSixHundredJoules() {
        assertExact("3600", EnergyFamily.convert(BigDecimal.ONE, "Wh", "J").valueOrNull()!!)
    }

    @Test
    fun oneElectronvoltIsTheExactSiValue() {
        assertExact("1.602176634e-19", EnergyFamily.convert(BigDecimal.ONE, "eV", "J").valueOrNull()!!)
    }

    @Test
    fun oneJouleInElectronvolts() {
        val result = EnergyFamily.convert(BigDecimal.ONE, "J", "eV").valueOrNull()!!
        assertClose("6.241509074460763e18", result, "1e5")
    }

    @Test
    fun joulesInOneKilocalorie() {
        assertExact("4184", EnergyFamily.convert(BigDecimal.ONE, "kcal", "J").valueOrNull()!!)
    }

    @Test
    fun oneKilowattHourInKilocalories() {
        val result = EnergyFamily.convert(BigDecimal.ONE, "kWh", "kcal").valueOrNull()!!
        assertClose("860.420650096", result, "1e-6")
    }

    @Test
    fun oneMegajouleIsOneMillionJoules() {
        assertExact("1000000", EnergyFamily.convert(BigDecimal.ONE, "MJ", "J").valueOrNull()!!)
    }

    @Test
    fun oneKilojouleInCalories() {
        val result = EnergyFamily.convert(BigDecimal.ONE, "kJ", "cal").valueOrNull()!!
        assertClose("239.005736138", result, "1e-6")
    }

    @Test
    fun tenKilowattHoursInMegajoules() {
        assertExact("36", EnergyFamily.convert(BigDecimal("10"), "kWh", "MJ").valueOrNull()!!)
    }

    @Test
    fun negativeEnergyIsLinear() {
        assertExact("-3600000", EnergyFamily.convert(BigDecimal("-1"), "kWh", "J").valueOrNull()!!)
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(EnergyFamily.convert(BigDecimal.ONE, "J", "erg") is com.calcsuite.core.CalcResult.Err)
    }
}
