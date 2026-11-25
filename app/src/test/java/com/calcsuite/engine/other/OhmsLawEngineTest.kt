package com.calcsuite.engine.other

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class OhmsLawEngineTest {

    private fun value(known: Map<Quantity, BigDecimal>): OhmsResult {
        val result = OhmsLawEngine.solve(known)
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(known: Map<Quantity, BigDecimal>): CalcResult.Err {
        val result = OhmsLawEngine.solve(known)
        assertTrue("Expected failure, was $result", result is CalcResult.Err)
        return result as CalcResult.Err
    }

    private fun assertRelativelyClose(actual: BigDecimal, expected: String) {
        val exp = BigDecimal(expected)
        val relativeError = actual.subtract(exp).abs().divide(exp.abs(), MathContext(34))
        assertTrue("Expected $actual close to $exp", relativeError.compareTo(BigDecimal("1e-30")) <= 0)
    }

    @Test
    fun twelveVoltsAcrossSixOhms() {
        val result = value(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.RESISTANCE to BigDecimal("6"),
            ),
        )
        assertEquals(0, result.current.compareTo(BigDecimal("2")))
        assertEquals(0, result.power.compareTo(BigDecimal("24")))
        assertEquals(0, result.voltage.compareTo(BigDecimal("12")))
        assertEquals(0, result.resistance.compareTo(BigDecimal("6")))
        assertTrue(result.formulas.contains("I = V ÷ R"))
        assertTrue(result.formulas.contains("P = V² ÷ R"))
    }

    @Test
    fun mainsVoltageAcrossOneKilohm() {
        val result = value(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("230"),
                Quantity.RESISTANCE to BigDecimal("1000"),
            ),
        )
        assertEquals(0, result.current.compareTo(BigDecimal("0.23")))
        assertEquals(0, result.power.compareTo(BigDecimal("52.9")))
    }

    @Test
    fun siPrefixCaseAtEngineLevel() {
        // 5 kΩ with 10 mA is 50 V and 0.5 W once prefixes are resolved to SI.
        val result = value(
            mapOf(
                Quantity.RESISTANCE to BigDecimal("5000"),
                Quantity.CURRENT to BigDecimal("0.01"),
            ),
        )
        assertEquals(0, result.voltage.compareTo(BigDecimal("50")))
        assertEquals(0, result.power.compareTo(BigDecimal("0.5")))
        assertTrue(result.formulas.contains("V = I × R"))
    }

    @Test
    fun knownVoltageAndCurrentSolveResistanceAndPower() {
        val result = value(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.CURRENT to BigDecimal("2"),
            ),
        )
        assertEquals(0, result.resistance.compareTo(BigDecimal("6")))
        assertEquals(0, result.power.compareTo(BigDecimal("24")))
    }

    @Test
    fun knownCurrentAndPowerSolveVoltageAndResistance() {
        val result = value(
            mapOf(
                Quantity.CURRENT to BigDecimal("2"),
                Quantity.POWER to BigDecimal("24"),
            ),
        )
        assertEquals(0, result.voltage.compareTo(BigDecimal("12")))
        assertEquals(0, result.resistance.compareTo(BigDecimal("6")))
    }

    @Test
    fun knownResistanceAndPowerUseThePrincipalRoot() {
        val result = value(
            mapOf(
                Quantity.RESISTANCE to BigDecimal("4"),
                Quantity.POWER to BigDecimal("16"),
            ),
        )
        assertEquals(0, result.voltage.compareTo(BigDecimal("8")))
        assertEquals(0, result.current.compareTo(BigDecimal("2")))
        assertTrue(result.formulas.contains("V = √(P × R)"))
    }

    @Test
    fun zeroResistanceWithVoltageIsASpecificError() {
        val failure = error(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.RESISTANCE to BigDecimal.ZERO,
            ),
        )
        assertEquals(ErrorKind.Domain, failure.error.kind)
        assertEquals(
            "Zero resistance with a non-zero voltage is not a physical ideal-circuit solution",
            failure.error.message,
        )
    }

    @Test
    fun zeroVoltageWithZeroResistanceIsUnderdetermined() {
        val failure = error(
            mapOf(
                Quantity.VOLTAGE to BigDecimal.ZERO,
                Quantity.RESISTANCE to BigDecimal.ZERO,
            ),
        )
        assertEquals(ErrorKind.Insufficient, failure.error.kind)
        assertTrue(failure.error.message.contains("undetermined"))
    }

    @Test
    fun zeroCurrentWithZeroPowerIsUnderdetermined() {
        val failure = error(
            mapOf(
                Quantity.CURRENT to BigDecimal.ZERO,
                Quantity.POWER to BigDecimal.ZERO,
            ),
        )
        assertEquals(ErrorKind.Insufficient, failure.error.kind)
        assertTrue(failure.error.message.contains("undetermined"))
    }

    @Test
    fun zeroCurrentWithNonZeroVoltageIsReported() {
        val failure = error(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.CURRENT to BigDecimal.ZERO,
            ),
        )
        assertEquals(ErrorKind.Domain, failure.error.kind)
        assertTrue(failure.error.message.contains("infinite resistance"))
    }

    @Test
    fun zeroPowerWithNonZeroVoltageIsReported() {
        val failure = error(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.POWER to BigDecimal.ZERO,
            ),
        )
        assertEquals(ErrorKind.Domain, failure.error.kind)
        assertTrue(failure.error.message.contains("infinite resistance"))
    }

    @Test
    fun nonZeroPowerWithZeroVoltageIsReported() {
        val failure = error(
            mapOf(
                Quantity.VOLTAGE to BigDecimal.ZERO,
                Quantity.POWER to BigDecimal("5"),
            ),
        )
        assertEquals(ErrorKind.Domain, failure.error.kind)
    }

    @Test
    fun inconsistentThirdQuantityIsRejected() {
        val failure = error(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.CURRENT to BigDecimal("2"),
                Quantity.RESISTANCE to BigDecimal("5"),
            ),
        )
        assertEquals(ErrorKind.Inconsistent, failure.error.kind)
        assertTrue(failure.error.message.contains("does not match"))
        assertTrue(failure.error.message.contains("resistance"))
    }

    @Test
    fun consistentThirdQuantityIsAcceptedWithANote() {
        val result = value(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.CURRENT to BigDecimal("2"),
                Quantity.RESISTANCE to BigDecimal("6"),
            ),
        )
        assertEquals(0, result.power.compareTo(BigDecimal("24")))
        assertTrue(result.notes.any { it.contains("matched") })
    }

    @Test
    fun fewerThanTwoQuantitiesIsInsufficient() {
        val empty = error(emptyMap())
        assertEquals(ErrorKind.Insufficient, empty.error.kind)
        assertEquals("Provide exactly two of voltage, current, resistance or power", empty.error.message)

        val single = error(mapOf(Quantity.VOLTAGE to BigDecimal("12")))
        assertEquals(ErrorKind.Insufficient, single.error.kind)
    }

    @Test
    fun negativeInputsAreRejectedPerQuantity() {
        assertEquals(
            "Resistance cannot be negative",
            error(
                mapOf(
                    Quantity.VOLTAGE to BigDecimal("12"),
                    Quantity.RESISTANCE to BigDecimal("-5"),
                ),
            ).error.message,
        )
        assertEquals(
            "Voltage cannot be negative",
            error(
                mapOf(
                    Quantity.VOLTAGE to BigDecimal("-12"),
                    Quantity.CURRENT to BigDecimal("2"),
                ),
            ).error.message,
        )
        assertEquals(
            "Power cannot be negative",
            error(
                mapOf(
                    Quantity.POWER to BigDecimal("-1"),
                    Quantity.CURRENT to BigDecimal("2"),
                ),
            ).error.message,
        )
    }

    @Test
    fun assumptionsShipWithTheResult() {
        val result = value(
            mapOf(
                Quantity.VOLTAGE to BigDecimal("12"),
                Quantity.RESISTANCE to BigDecimal("6"),
            ),
        )
        assertTrue(result.assumptions.any { it.contains("No AC impedance") })
        assertTrue(result.assumptions.any { it.contains("temperature") })
        assertTrue(result.notes.any { it.contains("SI base units") })
    }

    @Test
    fun sqrtOfVeryLargeProductsDoesNotThrowOrLeakInfinity() {
        // The resistance/power branch needs sqrt(P*R). P*R = 1e800 overflows a
        // Double; the old implementation leaked a NumberFormatException here.
        val result = value(
            mapOf(
                Quantity.POWER to BigDecimal("1e400"),
                Quantity.RESISTANCE to BigDecimal("1e400"),
            ),
        )
        assertRelativelyClose(result.voltage, "1e400")
        assertRelativelyClose(result.current, "1")
    }

    @Test
    fun sqrtOfVerySmallProductsDoesNotUnderflowToZero() {
        // sqrt(1e-800) must stay a positive 1e-400, not collapse to 0.0.
        val result = value(
            mapOf(
                Quantity.POWER to BigDecimal("1e-400"),
                Quantity.RESISTANCE to BigDecimal("1e-400"),
            ),
        )
        assertEquals(1, result.voltage.signum())
        assertRelativelyClose(result.voltage, "1e-400")
        assertRelativelyClose(result.current, "1")
    }

    @Test
    fun sqrtKeepsTheDeclared34DigitPrecision() {
        // sqrt(2) must not be truncated to Double's ~16 digits.
        val result = value(
            mapOf(
                Quantity.POWER to BigDecimal("1"),
                Quantity.RESISTANCE to BigDecimal("2"),
            ),
        )
        assertTrue(
            result.voltage.subtract(BigDecimal("1.4142135623730950488016887242097")).abs()
                .compareTo(BigDecimal("1e-30")) < 0,
        )
    }
}
