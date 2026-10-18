package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.ForceFamily
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

class ForceFamilyTest {

    @Test
    fun oneKilogramForceIsNinePointEightZeroSixSixFiveNewtons() {
        assertExact("9.80665", ForceFamily.convert(BigDecimal.ONE, "kgf", "N").valueOrNull()!!)
    }

    @Test
    fun onePoundForceIsFourPointFourFourEightTwoTwoOneSixOneFiveTwoSixZeroFiveNewtons() {
        assertExact("4.4482216152605", ForceFamily.convert(BigDecimal.ONE, "lbf", "N").valueOrNull()!!)
    }

    @Test
    fun oneNewtonIsOneHundredThousandDynes() {
        assertExact("100000", ForceFamily.convert(BigDecimal.ONE, "N", "dyn").valueOrNull()!!)
    }

    @Test
    fun oneDyneIsTenMicronewtons() {
        assertExact("0.00001", ForceFamily.convert(BigDecimal.ONE, "dyn", "N").valueOrNull()!!)
    }

    @Test
    fun onePoundForceIsExactlyZeroPointFourFiveThreeFiveNineTwoThreeSevenKilogramForce() {
        assertExact("0.45359237", ForceFamily.convert(BigDecimal.ONE, "lbf", "kgf").valueOrNull()!!)
    }

    @Test
    fun oneKilonewtonIsOneThousandNewtons() {
        assertExact("1000", ForceFamily.convert(BigDecimal.ONE, "kN", "N").valueOrNull()!!)
    }

    @Test
    fun oneNewtonInPoundsForce() {
        assertClose("0.2248089431", ForceFamily.convert(BigDecimal.ONE, "N", "lbf").valueOrNull()!!, "1e-9")
    }

    @Test
    fun tenKilogramForceInNewtons() {
        assertExact("98.0665", ForceFamily.convert(BigDecimal("10"), "kgf", "N").valueOrNull()!!)
    }

    @Test
    fun hundredPoundsForceInKilogramForce() {
        assertExact("45.359237", ForceFamily.convert(BigDecimal("100"), "lbf", "kgf").valueOrNull()!!)
    }

    @Test
    fun negativeForceIsLinear() {
        assertExact("-4.4482216152605", ForceFamily.convert(BigDecimal("-1"), "lbf", "N").valueOrNull()!!)
    }

    @Test
    fun zeroForce() {
        assertEquals(0, ForceFamily.convert(BigDecimal.ZERO, "kgf", "dyn").valueOrNull()!!.signum())
    }

    @Test
    fun roundTrip() {
        val there = ForceFamily.convert(BigDecimal("12.5"), "N", "lbf").valueOrNull()!!
        val back = ForceFamily.convert(there, "lbf", "N").valueOrNull()!!
        assertClose("12.5", back, "1e-20")
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(ForceFamily.convert(BigDecimal.ONE, "N", "stone-force") is com.calcsuite.core.CalcResult.Err)
    }
}
