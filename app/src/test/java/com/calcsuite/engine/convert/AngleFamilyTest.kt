package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.AngleFamily
import com.calcsuite.expr.Consts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertClose(expected: BigDecimal, actual: BigDecimal, tol: BigDecimal) {
    assertTrue("expected $expected ± $tol but was $actual", (actual - expected).abs() < tol)
}

class AngleFamilyTest {

    @Test
    fun oneEightyDegreesIsPiRadians() {
        val result = AngleFamily.convert(BigDecimal("180"), "deg", "rad").valueOrNull()!!
        assertClose(Consts.PI, result, BigDecimal("1e-30"))
    }

    @Test
    fun fourHundredGradiansIsThreeSixtyDegrees() {
        val result = AngleFamily.convert(BigDecimal("400"), "grad", "deg").valueOrNull()!!
        assertClose(BigDecimal("360"), result, BigDecimal("1e-25"))
    }

    @Test
    fun oneTurnIsTwoPiRadians() {
        val result = AngleFamily.convert(BigDecimal.ONE, "turn", "rad").valueOrNull()!!
        assertClose(Consts.PI.multiply(BigDecimal("2")), result, BigDecimal("1e-30"))
    }

    @Test
    fun negativeNinetyDegreesIsMinusHalfPi() {
        val result = AngleFamily.convert(BigDecimal("-90"), "deg", "rad").valueOrNull()!!
        assertClose(Consts.PI.divide(BigDecimal("-2")), result, BigDecimal("1e-30"))
    }

    @Test
    fun oneDegreeIsSixtyArcminutes() {
        val result = AngleFamily.convert(BigDecimal.ONE, "deg", "arcmin").valueOrNull()!!
        assertClose(BigDecimal("60"), result, BigDecimal("1e-25"))
    }

    @Test
    fun oneArcminuteIsSixtyArcseconds() {
        val result = AngleFamily.convert(BigDecimal.ONE, "arcmin", "arcsec").valueOrNull()!!
        assertClose(BigDecimal("60"), result, BigDecimal("1e-25"))
    }

    @Test
    fun oneRadianInDegrees() {
        val result = AngleFamily.convert(BigDecimal.ONE, "rad", "deg").valueOrNull()!!
        assertClose(BigDecimal("57.295779513082320876798154814105"), result, BigDecimal("1e-25"))
    }

    @Test
    fun ninetyDegreesIsOneHundredGradians() {
        val result = AngleFamily.convert(BigDecimal("90"), "deg", "grad").valueOrNull()!!
        assertClose(BigDecimal("100"), result, BigDecimal("1e-25"))
    }

    @Test
    fun twoTurnsIsSevenHundredTwentyDegrees() {
        val result = AngleFamily.convert(BigDecimal("2"), "turn", "deg").valueOrNull()!!
        assertClose(BigDecimal("720"), result, BigDecimal("1e-25"))
    }

    @Test
    fun moreThanOneRevolutionConverts() {
        val result = AngleFamily.convert(BigDecimal("1.5"), "turn", "deg").valueOrNull()!!
        assertClose(BigDecimal("540"), result, BigDecimal("1e-25"))
    }

    @Test
    fun milliradiansAreThousandthsOfARadian() {
        val result = AngleFamily.convert(BigDecimal("1000"), "mrad", "rad").valueOrNull()!!
        assertEquals(0, result.compareTo(BigDecimal.ONE))
    }

    @Test
    fun fortyFiveDegreesIsQuarterPi() {
        val result = AngleFamily.convert(BigDecimal("45"), "deg", "rad").valueOrNull()!!
        assertClose(Consts.PI.divide(BigDecimal("4"), UnitFamily.CTX), result, BigDecimal("1e-30"))
    }

    @Test
    fun negativeHalfTurn() {
        val result = AngleFamily.convert(BigDecimal("-0.5"), "turn", "rad").valueOrNull()!!
        assertClose(Consts.PI.negate(), result, BigDecimal("1e-30"))
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(AngleFamily.convert(BigDecimal.ONE, "deg", "gradian") is com.calcsuite.core.CalcResult.Err)
    }
}
