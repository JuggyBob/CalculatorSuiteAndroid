package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample circuits, checked against the answers a textbook gives.
 *
 * Every sample is a real circuit that can be edited, so the numbers it works
 * out have to be the right ones.
 */
class CircuitExamplesTest {

    private fun solve(example: CircuitExamples.Example): DcSolution {
        val result = DcSolver.solve(example.circuit)
        assertTrue(
            "the sample should be solved: " +
                ((result as? CalcResult.Err)?.error?.message ?: ""),
            result is CalcResult.Ok,
        )
        return (result as CalcResult.Ok).value
    }

    private fun assertClose(message: String, expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertEquals(message, expected, actual, tolerance)
    }

    @Test fun everySampleIsSolvedAndBalanced() {
        for (example in CircuitExamples.all) {
            val solution = solve(example)
            assertClose(
                "the power balances in ${example.title}",
                0.0,
                solution.powerBalance,
                1e-9,
            )
        }
    }

    @Test fun everySampleHasAPartAndASource() {
        for (example in CircuitExamples.all) {
            assertTrue(
                "the sample ${example.title} has a source",
                example.circuit.of(ComponentKind.VOLTAGE_SOURCE).isNotEmpty(),
            )
            assertTrue(
                "the sample ${example.title} has a ground",
                example.circuit.of(ComponentKind.GROUND).isNotEmpty(),
            )
        }
    }

    @Test fun theSamplesAreAllDifferent() {
        val ids = CircuitExamples.all.map { it.id }
        assertEquals("every sample has its own name", ids.size, ids.toSet().size)
    }

    @Test fun aBatteryAndAResistorGivesTheTextbookAnswer() {
        val example = CircuitExamples.byId("battery")!!
        val solution = solve(example)
        assertClose("the current is 0.12 A", 0.12, solution.totalSourceCurrent, 1e-12)
        val resistor = solution.components.getValue("R1")
        assertClose("the whole of the voltage is across it", 12.0, resistor.voltage, 1e-9)
        assertClose("the current is 0.12 A", 0.12, resistor.current, 1e-12)
        assertClose("the power is 1.44 W", 1.44, resistor.power, 1e-9)
    }

    @Test fun twoResistorsInSeriesGiveTheTextbookAnswer() {
        val example = CircuitExamples.byId("series")!!
        val solution = solve(example)
        assertClose("the current is 0.04 A", 0.04, solution.totalSourceCurrent, 1e-12)
        assertClose("the drop across R1 is 4 V", 4.0, solution.components.getValue("R1").voltage, 1e-9)
        assertClose("the drop across R2 is 8 V", 8.0, solution.components.getValue("R2").voltage, 1e-9)
        assertClose("the total resistance is 300 ohms", 300.0, solution.equivalentResistance!!, 1e-6)
        assertClose("the total power is 0.48 W", 0.48, solution.totalPowerDissipated, 1e-9)
    }

    @Test fun twoResistorsInParallelGiveTheTextbookAnswer() {
        val example = CircuitExamples.byId("parallel")!!
        val solution = solve(example)
        assertClose("the total current is 0.18 A", 0.18, solution.totalSourceCurrent, 1e-12)
        assertClose("the branch in R1 is 0.12 A", 0.12, solution.components.getValue("R1").current, 1e-12)
        assertClose("the branch in R2 is 0.06 A", 0.06, solution.components.getValue("R2").current, 1e-12)
        assertClose("the total power is 2.16 W", 2.16, solution.totalPowerDissipated, 1e-9)
        assertClose(
            "the combined resistance is 66.667 ohms",
            200.0 / 3.0,
            solution.equivalentResistance!!,
            1e-6,
        )
    }

    @Test fun theMixedNetworkGivesTheTextbookAnswer() {
        val example = CircuitExamples.byId("mixed")!!
        val solution = solve(example)
        assertClose("the total current is 0.05454545 A", 12.0 / 220.0, solution.totalSourceCurrent, 1e-12)
        assertClose(
            "the drop across R1 is 5.454545 V",
            12.0 * 100.0 / 220.0,
            solution.components.getValue("R1").voltage,
            1e-9,
        )
        assertClose(
            "the total power is 0.65454545 W",
            144.0 / 220.0,
            solution.totalPowerDissipated,
            1e-9,
        )
    }

    @Test fun theSwitchSampleGivesTheTextbookAnswer() {
        val example = CircuitExamples.byId("switch")!!
        val closed = solve(example)
        assertClose("the current is 0.12 A", 0.12, closed.totalSourceCurrent, 1e-12)
        assertClose(
            "there is no voltage across the closed switch",
            0.0,
            closed.components.getValue("S1").voltage,
            1e-9,
        )

        // The same circuit with the switch open.
        val opened = example.circuit.copy(
            components = example.circuit.components.map {
                if (it.kind == ComponentKind.SWITCH) it.copy(switchClosed = false) else it
            },
        )
        val open = solve(CircuitExamples.Example("open", "open", "open", opened))
        assertClose("no current flows", 0.0, open.totalSourceCurrent, 1e-12)
        assertClose(
            "the whole voltage is across the open switch",
            12.0,
            open.components.getValue("S1").voltage,
            1e-9,
        )
    }

    @Test fun theThreeBranchSampleGivesTheTextbookAnswer() {
        val example = CircuitExamples.byId("branches")!!
        val solution = solve(example)
        // Each branch has the whole of the source voltage across it.
        assertClose("the branch in R1 is 0.12 A", 0.12, solution.components.getValue("R1").current, 1e-12)
        assertClose("the branch in R2 is 0.054545 A", 12.0 / 220.0, solution.components.getValue("R2").current, 1e-12)
        assertClose("the branch in R3 is 0.0255319 A", 12.0 / 470.0, solution.components.getValue("R3").current, 1e-12)
        assertClose(
            "the branches add up",
            solution.totalSourceCurrent,
            solution.components.getValue("R1").current +
                solution.components.getValue("R2").current +
                solution.components.getValue("R3").current,
            1e-12,
        )
    }

    @Test fun theDividerSampleGivesTheTextbookAnswer() {
        val example = CircuitExamples.byId("divider")!!
        val solution = solve(example)
        // The output is the part of the voltage across the second resistor.
        assertClose("the output is 8 V", 8.0, solution.components.getValue("R2").voltage, 1e-9)
        assertClose("the current is 0.0004 A", 0.0004, solution.totalSourceCurrent, 1e-12)
    }

    @Test fun everySampleHasAnExplanationThatMatchesTheAnswer() {
        for (example in CircuitExamples.all) {
            val assessment = CircuitValidator.assess(example.circuit)
            assertTrue(
                "the sample ${example.title} should be solved",
                assessment.state == CircuitState.SOLVED,
            )
            val explanation = assessment.explanation
            assertNotNull("the sample ${example.title} has an explanation", explanation)
            val value = explanation!!.value
            assertTrue(
                "the explanation of ${example.title} names the current it found: $value",
                value.contains(CircuitExplainer.number(assessment.solution!!.totalSourceCurrent)),
            )
        }
    }
}
