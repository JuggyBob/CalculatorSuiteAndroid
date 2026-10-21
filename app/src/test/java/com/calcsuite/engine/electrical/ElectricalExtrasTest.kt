package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The calculators that sit around the circuit: prefixes, colours,
 * capacitors, time constants and alternating current.
 */
class ElectricalExtrasTest {

    private fun dec(value: String) = BigDecimal(value)

    private fun <T> value(result: CalcResult<T>): T {
        assertTrue(
            "the calculation should work: " +
                ((result as? CalcResult.Err)?.error?.message ?: ""),
            result is CalcResult.Ok,
        )
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<*>): String {
        assertTrue("the calculation should be refused", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    private fun row(result: CalculatorResult, name: String): String? =
        result.rows.firstOrNull { it.first == name }?.second

    // ----------------------------------------------------------- the prefixes

    @Test fun aValueIsWrittenInAnotherPrefix() {
        val result = value(
            ElectricalExtras.convert(
                dec("0.12"),
                ElectricalUnits.Unit.CURRENT,
                "none",
                ElectricalUnits.Unit.CURRENT,
                "milli",
            ),
        )
        assertEquals("the value is named", "120 mA", result.headline)
        assertEquals("the value as given is named", "0.12 A", row(result, "As given"))
        assertEquals(
            "the value in the base unit is named",
            "0.12 A",
            row(result, "In the base unit"),
        )
    }

    @Test fun goingBackGivesTheOriginalValue() {
        val result = value(
            ElectricalExtras.convert(
                dec("4700"),
                ElectricalUnits.Unit.RESISTANCE,
                "none",
                ElectricalUnits.Unit.RESISTANCE,
                "kilo",
            ),
        )
        assertEquals("the value is named", "4.7 kΩ", result.headline)
        assertEquals("the value as given is named", "4700 Ω", row(result, "As given"))
    }

    @Test fun aNegativeValueIsRefused() {
        val message = error(
            ElectricalExtras.convert(
                dec("-1"),
                ElectricalUnits.Unit.VOLTAGE,
                "none",
                ElectricalUnits.Unit.VOLTAGE,
                "milli",
            ),
        )
        assertTrue("the reason is given: $message", message.contains("negative"))
    }

    // -------------------------------------------------------- the colour code

    @Test fun theBandsOfAResistorAreRead() {
        val bands = listOf(
            ElectricalExtras.digitBands[1],
            ElectricalExtras.digitBands[2],
            ElectricalExtras.multiplierBands.first { it.multiplier == 1 },
            ElectricalExtras.toleranceBands.first { it.colour == "Gold" },
        )
        val result = value(ElectricalExtras.fromBands(bands))
        assertEquals("the value is named", "120 Ω", result.headline)
        assertEquals("the tolerance is named", "± 5 %", row(result, "Tolerance"))
        assertEquals("the range is named", "114 Ω to 126 Ω", row(result, "Range"))
    }

    @Test fun theBandsOfAResistorWithAGoldMultiplierAreRead() {
        val bands = listOf(
            ElectricalExtras.digitBands[4],
            ElectricalExtras.digitBands[7],
            ElectricalExtras.multiplierBands.first { it.multiplier == -1 },
        )
        val result = value(ElectricalExtras.fromBands(bands))
        assertEquals("the value is named", "4.7 Ω", result.headline)
    }

    @Test fun tooFewBandsAreRefused() {
        val message = error(
            ElectricalExtras.fromBands(listOf(ElectricalExtras.digitBands[1])),
        )
        assertTrue("the reason is given: $message", message.contains("at least three"))
    }

    @Test fun theBandsOfAValueAreWritten() {
        val result = value(ElectricalExtras.toBands(dec("4700")))
        assertEquals("the value is named", "4700 Ω", result.headline)
        val bands = row(result, "Bands")!!
        assertTrue("the bands are named: $bands", bands.contains("Yellow"))
        assertTrue("the bands are named: $bands", bands.contains("Violet"))
        assertTrue("the bands are named: $bands", bands.contains("Red"))
    }

    @Test fun aValueThatCannotBeWrittenIsRefused() {
        val message = error(ElectricalExtras.toBands(dec("0")))
        assertTrue("the reason is given: $message", message.contains("greater than zero"))
    }

    // ------------------------------------------------------------ the capacitor

    @Test fun aCapacitorGivesTheTextbookAnswer() {
        val result = value(
            ElectricalExtras.capacitor(dec("0.0001"), dec("12"), dec("1000")),
        )
        assertEquals("the charge is named", "0.0012 C", result.headline.removePrefix("Q = "))
        assertEquals("the energy is named", "0.0072 J", row(result, "Energy"))
        assertEquals("the time constant is named", "0.1 s", row(result, "Time constant"))
    }

    @Test fun aCapacitorWithNoCapacitanceIsRefused() {
        val message = error(ElectricalExtras.capacitor(dec("0"), dec("12"), null))
        assertTrue("the reason is given: $message", message.contains("greater than zero"))
    }

    // --------------------------------------------------------- the time constant

    @Test fun theTimeConstantOfAnRcCircuitIsFound() {
        val result = value(ElectricalExtras.timeConstant("capacitor", dec("1000"), dec("0.0001")))
        assertEquals("the time constant is named", "0.1 s", result.headline.removePrefix("τ = "))
    }

    @Test fun theTimeConstantOfAnRlCircuitIsFound() {
        val result = value(ElectricalExtras.timeConstant("inductor", dec("100"), dec("1")))
        assertEquals("the time constant is named", "0.01 s", result.headline.removePrefix("τ = "))
    }

    @Test fun aTimeConstantWithNoValueIsRefused() {
        val message = error(ElectricalExtras.timeConstant("capacitor", dec("0"), dec("1")))
        assertTrue("the reason is given: $message", message.contains("greater than zero"))
    }

    // ------------------------------------------------------ the alternating one

    @Test fun anAlternatingCircuitWithOnlyAResistanceIsFound() {
        // A 100 ohm resistor at 50 Hz and 230 V: the impedance is the
        // resistance, so the current is 2.3 A and in step with the voltage.
        val result = value(
            ElectricalExtras.ac(dec("50"), dec("230"), dec("100"), null, null),
        )
        assertEquals("the current is named", "2.3 A", result.headline.removePrefix("I = "))
        assertEquals("the impedance is named", "100 Ω", row(result, "Impedance"))
        assertEquals("the phase is named", "0°", row(result, "Phase"))
        assertEquals("the real power is named", "529 W", row(result, "Real power"))
    }

    @Test fun anAlternatingCircuitWithAnInductorLagsTheVoltage() {
        // 100 ohms in series with 0.1 H at 50 Hz: the reactance is 31.4 ohms.
        val result = value(
            ElectricalExtras.ac(dec("50"), dec("230"), dec("100"), dec("0.1"), null),
        )
        val reactance = 2 * Math.PI * 50 * 0.1
        assertEquals(
            "the inductive reactance is named",
            "${CircuitExplainer.number(reactance)} Ω",
            row(result, "Inductive reactance"),
        )
        val phase = Math.toDegrees(Math.atan(reactance / 100.0))
        assertEquals(
            "the phase is named",
            "${CircuitExplainer.number(phase)}°",
            row(result, "Phase"),
        )
        assertTrue(
            "the current lags: ${row(result, "Phase")}",
            phase > 0.0,
        )
    }

    @Test fun anAlternatingCircuitWithACapacitorLeadsTheVoltage() {
        val result = value(
            ElectricalExtras.ac(dec("50"), dec("230"), dec("100"), null, dec("0.000047")),
        )
        val reactance = 1.0 / (2 * Math.PI * 50 * 0.000047)
        assertEquals(
            "the capacitive reactance is named",
            "${CircuitExplainer.number(reactance)} Ω",
            row(result, "Capacitive reactance"),
        )
        val phase = Math.toDegrees(Math.atan(-reactance / 100.0))
        assertEquals(
            "the phase is named",
            "${CircuitExplainer.number(phase)}°",
            row(result, "Phase"),
        )
        assertTrue(
            "the current leads: ${row(result, "Phase")}",
            phase < 0.0,
        )
    }

    @Test fun anAlternatingCircuitWithNoFrequencyIsRefused() {
        val message = error(ElectricalExtras.ac(dec("0"), dec("230"), dec("100"), null, null))
        assertTrue("the reason is given: $message", message.contains("greater than zero"))
    }

    @Test fun anAlternatingCircuitWithNoResistanceIsRefused() {
        val message = error(ElectricalExtras.ac(dec("50"), dec("230"), dec("0"), null, null))
        assertTrue("the reason is given: $message", message.contains("greater than zero"))
    }

    @Test fun everyExtraNamesItsVariablesAndChecks() {
        val result = value(ElectricalExtras.capacitor(dec("0.0001"), dec("12"), dec("1000")))
        assertTrue("there are variables", result.variables.isNotEmpty())
        assertTrue("there are checks", result.checks.isNotEmpty())
        assertTrue("there is a working", result.steps.isNotEmpty())
        assertTrue("there is a reason", result.why.isNotBlank())
    }
}
