package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.convert.families.TemperatureFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

private fun assertClose(expected: String, actual: BigDecimal, tol: String = "1e-9") {
    assertTrue("expected $expected ± $tol but was $actual", (actual - BigDecimal(expected)).abs() < BigDecimal(tol))
}

private fun expectError(kind: com.calcsuite.core.ErrorKind, result: CalcResult<BigDecimal>, messagePart: String) {
    assertTrue("expected Err but was $result", result is CalcResult.Err)
    val error = (result as CalcResult.Err).error
    assertEquals(kind, error.kind)
    assertTrue("message '${error.message}' should contain '$messagePart'", error.message.contains(messagePart))
}

class TemperatureFamilyTest {

    @Test
    fun zeroCelsiusIsThirtyTwoFahrenheit() {
        assertClose("32", TemperatureFamily.convert(BigDecimal.ZERO, "C", "F").valueOrNull()!!)
    }

    @Test
    fun zeroCelsiusIsTwoHundredAndSeventyThreePointOneFiveKelvin() {
        assertExact("273.15", TemperatureFamily.convert(BigDecimal.ZERO, "C", "K").valueOrNull()!!)
    }

    @Test
    fun thirtyTwoFahrenheitIsZeroCelsius() {
        assertClose("0", TemperatureFamily.convert(BigDecimal("32"), "F", "C").valueOrNull()!!)
    }

    @Test
    fun minusFortyIsTheSameInBothScales() {
        assertClose("-40", TemperatureFamily.convert(BigDecimal("-40"), "C", "F").valueOrNull()!!)
        assertClose("-40", TemperatureFamily.convert(BigDecimal("-40"), "F", "C").valueOrNull()!!)
    }

    @Test
    fun oneHundredCelsiusIsThreeSeventyThreePointOneFiveKelvin() {
        assertExact("373.15", TemperatureFamily.convert(BigDecimal("100"), "C", "K").valueOrNull()!!)
    }

    @Test
    fun oneHundredCelsiusIsTwoHundredAndTwelveFahrenheit() {
        assertClose("212", TemperatureFamily.convert(BigDecimal("100"), "C", "F").valueOrNull()!!)
    }

    @Test
    fun twoHundredAndTwelveFahrenheitIsOneHundredCelsius() {
        assertClose("100", TemperatureFamily.convert(BigDecimal("212"), "F", "C").valueOrNull()!!)
    }

    @Test
    fun kelvinToCelsius() {
        assertExact("0", TemperatureFamily.convert(BigDecimal("273.15"), "K", "C").valueOrNull()!!)
    }

    @Test
    fun zeroKelvinIsMinusTwoHundredAndSeventyThreePointOneFiveCelsius() {
        assertExact("-273.15", TemperatureFamily.convert(BigDecimal.ZERO, "K", "C").valueOrNull()!!)
    }

    @Test
    fun zeroKelvinInFahrenheit() {
        assertClose("-459.67", TemperatureFamily.convert(BigDecimal.ZERO, "K", "F").valueOrNull()!!)
    }

    @Test
    fun fahrenheitToKelvin() {
        assertClose("310.9277777778", TemperatureFamily.convert(BigDecimal("100"), "F", "K").valueOrNull()!!, "1e-6")
    }

    @Test
    fun aCelsiusDegreeIsOnePointEightFahrenheitDegrees() {
        val atZero = TemperatureFamily.convert(BigDecimal.ZERO, "C", "F").valueOrNull()!!
        val atOne = TemperatureFamily.convert(BigDecimal.ONE, "C", "F").valueOrNull()!!
        assertClose("1.8", atOne - atZero, "1e-9")
    }

    @Test
    fun belowAbsoluteZeroInCelsiusIsRejected() {
        val result = TemperatureFamily.convert(BigDecimal("-300"), "C", "F")
        expectError(com.calcsuite.core.ErrorKind.Domain, result, "absolute zero")
    }

    @Test
    fun belowAbsoluteZeroInFahrenheitIsRejected() {
        val result = TemperatureFamily.convert(BigDecimal("-500"), "F", "K")
        expectError(com.calcsuite.core.ErrorKind.Domain, result, "absolute zero")
    }

    @Test
    fun belowAbsoluteZeroInKelvinIsRejected() {
        val result = TemperatureFamily.convert(BigDecimal("-1"), "K", "C")
        expectError(com.calcsuite.core.ErrorKind.Domain, result, "absolute zero")
    }

    @Test
    fun absoluteZeroInCelsiusIsAllowed() {
        assertExact("0", TemperatureFamily.convert(BigDecimal("-273.15"), "C", "K").valueOrNull()!!)
    }

    @Test
    fun justAboveAbsoluteZeroInFahrenheitIsAllowed() {
        // −459.66 °F is 0.01 °F above absolute zero: 0.01 × 5/9 K ≈ 0.00555 K.
        assertClose("0.0055555556", TemperatureFamily.convert(BigDecimal("-459.66"), "F", "K").valueOrNull()!!, "1e-9")
    }

    @Test
    fun theValidatorMessageMentionsBothScales() {
        val result = TemperatureFamily.convert(BigDecimal("-300"), "C", "K")
        val message = (result as CalcResult.Err).error.message
        assertTrue(message.contains("-273.15"))
        assertTrue(message.contains("0 K"))
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(TemperatureFamily.convert(BigDecimal.ONE, "C", "R") is CalcResult.Err)
    }
}
