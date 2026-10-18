package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.TorqueFamily
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

class TorqueFamilyTest {

    @Test
    fun oneNewtonMetreInPoundForceFeet() {
        assertClose("0.7375621493", TorqueFamily.convert(BigDecimal.ONE, "Nm", "lbf_ft").valueOrNull()!!, "1e-9")
    }

    @Test
    fun oneKilogramForceMetreIsNinePointEightZeroSixSixFiveNewtonMetres() {
        assertExact("9.80665", TorqueFamily.convert(BigDecimal.ONE, "kgf_m", "Nm").valueOrNull()!!)
    }

    @Test
    fun onePoundForceFootIsTheExactNewtonMetreValue() {
        assertExact("1.3558179483314004", TorqueFamily.convert(BigDecimal.ONE, "lbf_ft", "Nm").valueOrNull()!!)
    }

    @Test
    fun onePoundForceInchIsTheExactNewtonMetreValue() {
        assertExact("0.1129848290276167", TorqueFamily.convert(BigDecimal.ONE, "lbf_in", "Nm").valueOrNull()!!)
    }

    @Test
    fun onePoundForceFootIsTwelvePoundForceInches() {
        assertExact("12", TorqueFamily.convert(BigDecimal.ONE, "lbf_ft", "lbf_in").valueOrNull()!!)
    }

    @Test
    fun oneKilogramForceMetreInPoundForceFeet() {
        assertClose("7.2330138512", TorqueFamily.convert(BigDecimal.ONE, "kgf_m", "lbf_ft").valueOrNull()!!, "1e-9")
    }

    @Test
    fun tenNewtonMetresInPoundForceInches() {
        assertClose("88.50745791", TorqueFamily.convert(BigDecimal("10"), "Nm", "lbf_in").valueOrNull()!!, "1e-6")
    }

    @Test
    fun tighteningSpecIsConvertible() {
        // 90 N·m is a common wheel-bolt torque; in lbf·ft it is about 66.4.
        val result = TorqueFamily.convert(BigDecimal("90"), "Nm", "lbf_ft").valueOrNull()!!
        assertClose("66.380593435", result, "1e-6")
    }

    @Test
    fun negativeTorqueIsAllowed() {
        assertExact("-9.80665", TorqueFamily.convert(BigDecimal("-1"), "kgf_m", "Nm").valueOrNull()!!)
    }

    @Test
    fun roundTrip() {
        val there = TorqueFamily.convert(BigDecimal("25"), "Nm", "lbf_in").valueOrNull()!!
        val back = TorqueFamily.convert(there, "lbf_in", "Nm").valueOrNull()!!
        assertClose("25", back, "1e-18")
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(TorqueFamily.convert(BigDecimal.ONE, "Nm", "ozf_in") is com.calcsuite.core.CalcResult.Err)
    }
}
