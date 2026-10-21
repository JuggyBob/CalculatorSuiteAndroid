package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The explanation of a circuit.
 *
 * The explanation has to describe the circuit that was actually solved, with
 * the numbers that were actually found. These tests check that the working
 * it shows is the working of the circuit in front of the user, and not a
 * fixed text.
 */
class CircuitExplainerTest {

    private fun example(id: String): CircuitExamples.Example =
        CircuitExamples.byId(id) ?: error("there is no sample called $id")

    private fun explain(example: CircuitExamples.Example): Pair<DcSolution, CircuitExplanation> {
        val result = DcSolver.solve(example.circuit)
        assertTrue("the sample should be solved", result is CalcResult.Ok)
        val solution = (result as CalcResult.Ok).value
        val explanation = CircuitExplainer.explain(
            example.circuit,
            solution,
            CircuitTopology(example.circuit),
        )
        return solution to explanation
    }

    @Test fun theExplanationNamesTheCurrentTheSolverFound() {
        val example = example("series")
        val (solution, explanation) = explain(example)
        assertTrue(
            "the explanation names the current: ${explanation.value}",
            explanation.value.contains(CircuitExplainer.number(solution.totalSourceCurrent)),
        )
    }

    @Test fun theExplanationNamesTheResistanceTheSourceSees() {
        val example = example("series")
        val (solution, explanation) = explain(example)
        assertTrue(
            "the explanation names the resistance: ${explanation.value}",
            explanation.value.contains(CircuitExplainer.number(solution.equivalentResistance!!)),
        )
    }

    @Test fun theWorkingShowsTheRealValuesOfTheParts() {
        val example = example("series")
        val (_, explanation) = explain(example)
        val text = explanation.steps.joinToString(" ") { step ->
            listOfNotNull(step.title, step.formula, step.substitution, step.calculation, step.result)
                .joinToString(" ")
        }
        assertTrue("the 100 ohm part is named: $text", text.contains("100"))
        assertTrue("the 200 ohm part is named: $text", text.contains("200"))
        assertTrue("the 12 V source is named: $text", text.contains("12"))
    }

    @Test fun theReductionNamesThePartsThatWereCombined() {
        val example = example("series")
        val (_, explanation) = explain(example)
        val reduction = explanation.steps.firstOrNull { step -> step.title.contains("R1") }
        assertTrue(
            "the first step names R1: ${explanation.steps.map { it.title }}",
            reduction != null,
        )
        assertTrue(
            "the first step names R2 as well: ${reduction!!.title}",
            reduction.title.contains("R2"),
        )
        assertTrue(
            "the step says they are in series: ${reduction.title}",
            reduction.title.contains("in series"),
        )
    }

    @Test fun theParallelSampleIsReducedAsParallel() {
        val example = example("parallel")
        val (_, explanation) = explain(example)
        val reduction = explanation.steps.firstOrNull { step -> step.title.contains("R1") }
        assertTrue("the reduction is shown", reduction != null)
        assertTrue(
            "the parts are said to be in parallel: ${reduction!!.title}",
            reduction.title.contains("in parallel"),
        )
    }

    @Test fun theWorkingHasAFormulaAndItsVariables() {
        val example = example("battery")
        val (_, explanation) = explain(example)
        assertTrue("there is a formula", explanation.formula.isNotBlank())
        assertTrue("there are variables", explanation.variables.isNotEmpty())
        val symbols = explanation.variables.map { it.symbol }
        assertTrue("the voltage is a variable: $symbols", symbols.contains("V"))
        assertTrue("the current is a variable: $symbols", symbols.contains("I"))
        for (variable in explanation.variables) {
            assertTrue(
                "the variable ${variable.symbol} says what it stands for",
                variable.meaning.isNotBlank(),
            )
            assertTrue(
                "the variable ${variable.symbol} says what it was used as",
                variable.value.isNotBlank(),
            )
        }
    }

    @Test fun thereIsACheckThatHolds() {
        for (id in listOf("battery", "series", "parallel", "mixed")) {
            val example = example(id)
            val (_, explanation) = explain(example)
            assertTrue("there are checks for $id", explanation.checks.isNotEmpty())
            for (check in explanation.checks) {
                assertTrue("the check has a title", check.title.isNotBlank())
                assertTrue("the check has a result", !check.result.isNullOrBlank())
            }
        }
    }

    @Test fun theExplanationSaysWhyItApplies() {
        val example = example("series")
        val (_, explanation) = explain(example)
        assertTrue("there is a reason", explanation.why.isNotBlank())
        assertTrue(
            "the reason mentions the method: ${explanation.why}",
            explanation.why.contains("series") || explanation.why.contains("nodal"),
        )
    }

    @Test fun theExplanationOfAPartUsesItsOwnNumbers() {
        val example = example("mixed")
        val (solution, _) = explain(example)
        val topology = CircuitTopology(example.circuit)
        val resistor = example.circuit.component("R2")!!
        val result = solution.components.getValue("R2")
        val component = CircuitExplainer.explainComponent(resistor, result, solution)
        assertEquals("R2", component.label)
        assertTrue(
            "the voltage is the one that was found: ${component.voltage}",
            component.voltage.contains(CircuitExplainer.number(result.voltage)),
        )
        assertTrue(
            "the current is the one that was found: ${component.current}",
            component.current.contains(CircuitExplainer.number(result.current)),
        )
        assertTrue(
            "the power is the one that was found: ${component.power}",
            component.power.contains(CircuitExplainer.number(result.power)),
        )
        assertTrue("there is a formula", component.formula.isNotBlank())
        assertTrue("there is a note", component.note.isNotBlank())
        // The topology is built so that the part can be found in it.
        assertTrue(
            "the part is in the topology",
            topology.terminalsIn(topology.nodeOf(resistor.terminals.first())!!).isNotEmpty(),
        )
    }

    @Test fun aSourceIsExplainedAsSupplyingPower() {
        val example = example("battery")
        val (solution, _) = explain(example)
        val source = example.circuit.of(ComponentKind.VOLTAGE_SOURCE).first()
        val result = solution.components.getValue(source.id)
        val component = CircuitExplainer.explainComponent(source, result, solution)
        assertTrue(
            "the explanation mentions the power it supplies: ${component.steps.joinToString(" ")}",
            component.steps.joinToString(" ") { it.result.orEmpty() }
                .contains(CircuitExplainer.number(solution.totalPowerSupplied)),
        )
    }

    @Test fun theNumbersAreWrittenWithoutFalsePrecision() {
        // A number is written with the digits that matter and no more.
        assertEquals("0.12", CircuitExplainer.number(0.12))
        assertEquals("0", CircuitExplainer.number(0.0))
        assertEquals("?", CircuitExplainer.number(Double.NaN))
        assertEquals("?", CircuitExplainer.number(Double.POSITIVE_INFINITY))

        val written = CircuitExplainer.number(12.0 / 220.0)
        assertTrue("the number is written in full: $written", written.contains("."))
        val decimals = written.substringAfter(".").length
        assertTrue("there are no more than six decimals: $written", decimals <= 6)
        assertEquals(
            "the number is the right one to within what is written",
            12.0 / 220.0,
            written.toDouble(),
            1e-6,
        )
    }
}
