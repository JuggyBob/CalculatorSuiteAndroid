package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.AccelerationFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertClose(expected: String, actual: BigDecimal, tol: String = "1e-9") {
    assertTrue("expected $expected ± $tol but was $actual", (actual - BigDecimal(expected)).abs() < BigDecimal(tol))
}

class AccelerationFamilyTest {

    @Test
    fun standardGravityEqualsOneG() {
        val result = AccelerationFamily.convert(BigDecimal("9.80665"), "m_s2", "g0").valueOrNull()!!
        assertEquals(0, result.compareTo(BigDecimal.ONE))
    }

    @Test
    fun oneGInMetresPerSecondSquared() {
        val result = AccelerationFamily.convert(BigDecimal.ONE, "g0", "m_s2").valueOrNull()!!
        assertEquals(0, result.compareTo(BigDecimal("9.80665")))
    }

    @Test
    fun footPerSecondSquaredIsExactlyZeroPointThreeZeroFourEight() {
        val result = AccelerationFamily.convert(BigDecimal.ONE, "ft_s2", "m_s2").valueOrNull()!!
        assertEquals(0, result.compareTo(BigDecimal("0.3048")))
    }

    @Test
    fun oneGInFeetPerSecondSquared() {
        val result = AccelerationFamily.convert(BigDecimal.ONE, "g0", "ft_s2").valueOrNull()!!
        assertClose("32.17404855643044619422572178477690", result, "1e-12")
    }

    @Test
    fun centimetresPerSecondSquared() {
        val result = AccelerationFamily.convert(BigDecimal("100"), "cm_s2", "m_s2").valueOrNull()!!
        assertEquals(0, result.compareTo(BigDecimal.ONE))
    }

    @Test
    fun kilometresPerSecondSquared() {
        val result = AccelerationFamily.convert(BigDecimal.ONE, "km_s2", "m_s2").valueOrNull()!!
        assertEquals(0, result.compareTo(BigDecimal("1000")))
    }

    @Test
    fun negativeAccelerationIsAllowed() {
        val result = AccelerationFamily.convert(BigDecimal("-1"), "g0", "m_s2").valueOrNull()!!
        assertEquals(0, result.compareTo(BigDecimal("-9.80665")))
    }

    @Test
    fun zeroConvertsToZero() {
        val result = AccelerationFamily.convert(BigDecimal.ZERO, "ft_s2", "g0").valueOrNull()!!
        assertEquals(0, result.signum())
    }

    @Test
    fun factorToGravity() {
        val factor = AccelerationFamily.factorBetween("m_s2", "g0").valueOrNull()!!
        assertClose("0.10197162129779283", factor, "1e-12")
    }

    @Test
    fun unknownUnitIsRejected() {
        val result = AccelerationFamily.convert(BigDecimal.ONE, "m_s2", "parsecs_per_fortnight")
        assertTrue(result is com.calcsuite.core.CalcResult.Err)
    }

    @Test
    fun roundTripReturnsTheOriginalValue() {
        val there = AccelerationFamily.convert(BigDecimal("3.7"), "m_s2", "ft_s2").valueOrNull()!!
        val back = AccelerationFamily.convert(there, "ft_s2", "m_s2").valueOrNull()!!
        assertClose("3.7", back, "1e-20")
    }
}
