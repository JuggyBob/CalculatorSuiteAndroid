package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The electrical calculators, checked against the answers a textbook gives.
 */
class ElectricalCalculatorsTest {

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

    /** The value of a row, found by its name. */
    private fun row(result: CalculatorResult, name: String): String? =
        result.rows.firstOrNull { it.first == name }?.second

    // ------------------------------------------------------------------ series

    @Test fun resistorsInSeriesGiveTheTextbookAnswer() {
        val result = value(
            ElectricalCalculators.series(
                listOf("R1" to dec("100"), "R2" to dec("200")),
                dec("12"),
            ),
        )
        assertEquals("the total resistance is named", "300 Ω", result.headline.removePrefix("R = "))
        assertEquals("the current is named", "0.04 A", row(result, "Current"))
        assertEquals("the drop across R1 is named", "4 V", row(result, "Drop across R1"))
        assertEquals("the drop across R2 is named", "8 V", row(result, "Drop across R2"))
        assertEquals("the power in R1 is named", "0.16 W", row(result, "Power in R1"))
        assertEquals("the power in R2 is named", "0.32 W", row(result, "Power in R2"))
        assertEquals("the total power is named", "0.48 W", row(result, "Total power"))
        assertTrue("there is a working", result.steps.isNotEmpty())
        assertTrue("there are checks", result.checks.isNotEmpty())
        assertTrue("there is a reason", result.why.isNotBlank())
        assertTrue("there is a formula", result.formula.isNotBlank())
    }

    @Test fun theWorkingOfASeriesNamesTheValuesThatWereUsed() {
        val result = value(
            ElectricalCalculators.series(
                listOf("R1" to dec("100"), "R2" to dec("200")),
                dec("12"),
            ),
        )
        val text = result.steps.joinToString(" ") { step ->
            listOfNotNull(step.title, step.formula, step.substitution, step.calculation, step.result)
                .joinToString(" ")
        }
        assertTrue("the 100 ohm part is named: $text", text.contains("100"))
        assertTrue("the 200 ohm part is named: $text", text.contains("200"))
        assertTrue("the 12 V source is named: $text", text.contains("12"))
    }

    @Test fun aResistorOfZeroResistanceIsRefused() {
        val message = error(
            ElectricalCalculators.series(listOf("R1" to dec("0")), dec("12")),
        )
        assertTrue("the part is named: $message", message.contains("R1"))
    }

    @Test fun anEmptySeriesIsRefused() {
        val message = error(ElectricalCalculators.series(emptyList(), dec("12")))
        assertTrue("the reason is given: $message", message.contains("at least one"))
    }

    // ---------------------------------------------------------------- parallel

    @Test fun resistorsInParallelGiveTheTextbookAnswer() {
        val result = value(
            ElectricalCalculators.parallel(
                listOf("R1" to dec("100"), "R2" to dec("200")),
                dec("12"),
            ),
        )
        assertEquals(
            "the combined resistance is named",
            "66.666667 Ω",
            result.headline.removePrefix("R = "),
        )
        assertEquals("the total current is named", "0.18 A", row(result, "Total current"))
        assertEquals("the branch in R1 is named", "0.12 A", row(result, "Current in R1"))
        assertEquals("the branch in R2 is named", "0.06 A", row(result, "Current in R2"))
        assertEquals("the power in R1 is named", "1.44 W", row(result, "Power in R1"))
        assertEquals("the power in R2 is named", "0.72 W", row(result, "Power in R2"))
        assertEquals("the total power is named", "2.16 W", row(result, "Total power"))
    }

    @Test fun aResistorOfNegativeResistanceIsRefused() {
        val message = error(
            ElectricalCalculators.parallel(listOf("R1" to dec("-5")), dec("12")),
        )
        assertTrue("the reason is given: $message", message.contains("greater than zero"))
    }

    // ------------------------------------------------------------------- mixed

    @Test fun aMixedNetworkGivesTheTextbookAnswer() {
        val result = value(
            ElectricalCalculators.mixed(
                "R1",
                dec("100"),
                listOf("R2" to dec("200"), "R3" to dec("300")),
                dec("12"),
            ),
        )
        // The parallel pair is 120 ohms, so the whole is 220.
        assertEquals("the total resistance is named", "220 Ω", result.headline.removePrefix("R = "))
        assertEquals("the parallel part is named", "120 Ω", row(result, "Parallel part"))
        assertEquals(
            "the current is named",
            "${CircuitExplainer.number(12.0 / 220.0)} A",
            row(result, "Total current"),
        )
        assertEquals(
            "the drop across the series part is named",
            "${CircuitExplainer.number(12.0 * 100.0 / 220.0)} V",
            row(result, "Drop across R1"),
        )
        assertEquals(
            "the voltage across the parallel part is named",
            "${CircuitExplainer.number(12.0 * 120.0 / 220.0)} V",
            row(result, "Voltage across the parallel part"),
        )
        assertEquals(
            "the branch in R2 is named",
            "${CircuitExplainer.number(12.0 * 120.0 / 220.0 / 200.0)} A",
            row(result, "Current in R2"),
        )
    }

    @Test fun aMixedNetworkWithNoParallelPartIsRefused() {
        val message = error(
            ElectricalCalculators.mixed("R1", dec("100"), emptyList(), dec("12")),
        )
        assertTrue("the reason is given: $message", message.contains("at least one"))
    }

    // ---------------------------------------------------------------- dividers

    @Test fun aVoltageDividerGivesTheTextbookAnswer() {
        val result = value(
            ElectricalCalculators.voltageDivider(dec("10000"), dec("20000"), dec("12")),
        )
        assertEquals("the output is named", "8 V", result.headline.removePrefix("V out = "))
        assertEquals("the current is named", "0.0004 A", row(result, "Current"))
        assertEquals(
            "the drop across the first resistor is named",
            "4 V",
            row(result, "Drop across the first resistor"),
        )
        assertEquals("the total resistance is named", "30000 Ω", row(result, "Total resistance"))
    }

    @Test fun aVoltageDividerWithNoResistanceIsRefused() {
        val message = error(
            ElectricalCalculators.voltageDivider(dec("0"), dec("20000"), dec("12")),
        )
        assertTrue("the reason is given: $message", message.contains("greater than zero"))
    }

    @Test fun aCurrentDividerGivesTheTextbookAnswer() {
        val result = value(
            ElectricalCalculators.currentDivider(dec("100"), dec("200"), dec("0.18")),
        )
        // The smaller resistance takes the larger share.
        assertEquals(
            "the first branch is named",
            "0.12 A",
            result.headline.removePrefix("I₁ = "),
        )
        assertEquals(
            "the second branch is named",
            "0.06 A",
            row(result, "Current in the second branch"),
        )
        assertEquals(
            "the combined resistance is named",
            "66.666667 Ω",
            row(result, "Combined resistance"),
        )
    }

    @Test fun everyCalculatorNamesItsVariables() {
        val result = value(
            ElectricalCalculators.series(listOf("R1" to dec("100")), dec("12")),
        )
        assertTrue("there are variables", result.variables.isNotEmpty())
        for (variable in result.variables) {
            assertTrue("the symbol is named", variable.symbol.isNotBlank())
            assertTrue("the meaning is named", variable.meaning.isNotBlank())
            assertTrue("the value is named", variable.value.isNotBlank())
            assertTrue("the unit is named", variable.unit.isNotBlank())
        }
    }
}
