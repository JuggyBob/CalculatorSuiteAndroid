package com.calcsuite.ai.tool

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The input that is worked out, and the input that is shown.
 *
 * The engine and the screen must be working with the same thing, so that
 * the problem that is shown and the problem that is answered cannot drift
 * apart.
 */
class AiInputTest {

    private fun run(toolId: String, arguments: Map<String, String>) =
        AiToolExecutor.run(toolId, arguments)

    @Test fun theOutcomeCarriesTheInputItWasComputedFrom() {
        val result = run("calculus.derivative", mapOf("expression" to "x^2"))
        assertTrue("the derivative should be found", result is CalcResult.Ok)
        val outcome = (result as CalcResult.Ok).value
        val input = outcome.input
        assertNotNull("the outcome must say what it was computed from", input)
        assertEquals("calculus.derivative", input!!.toolId)
        assertEquals("x^2", input.arguments["expression"])
    }

    @Test fun theDefaultsAreFilledInAndKept() {
        // The variable is not given, so the tool's own default is used, and
        // the outcome must show the value that was really used.
        val result = run("calculus.derivative", mapOf("expression" to "x^2"))
        assertTrue(result is CalcResult.Ok)
        val input = (result as CalcResult.Ok).value.input!!
        assertEquals("x", input.arguments["variable"])
    }

    @Test fun theCorrectedArgumentsAreTheOnesTheEngineGets() {
        // The model read one thing, the user corrected it to another, and
        // the answer must be the one for the corrected problem.
        val wrong = run("calculus.derivative", mapOf("expression" to "x^2"))
        val right = run("calculus.derivative", mapOf("expression" to "x^3"))
        assertTrue(wrong is CalcResult.Ok)
        assertTrue(right is CalcResult.Ok)
        assertEquals(
            "the answer must be the one for the corrected problem",
            (right as CalcResult.Ok).value.headline.replace(" ", ""),
            "3x^2",
        )
        assertEquals(
            "the input that is shown must be the one that was worked out",
            "x^3",
            right.value.input!!.arguments["expression"],
        )
        assertFalse(
            "the two answers must not be the same",
            (wrong as CalcResult.Ok).value.headline == right.value.headline,
        )
    }

    @Test fun twoRequestsDoNotShareTheirArguments() {
        // A second request must not be able to pick up the first one's
        // arguments.
        val first = run("calculus.derivative", mapOf("expression" to "x^2"))
        val second = run("calculus.derivative", mapOf("expression" to "x^3"))
        assertTrue(first is CalcResult.Ok)
        assertTrue(second is CalcResult.Ok)
        assertEquals(
            "x^2",
            (first as CalcResult.Ok).value.input!!.arguments["expression"],
        )
        assertEquals(
            "x^3",
            (second as CalcResult.Ok).value.input!!.arguments["expression"],
        )
    }

    @Test fun aMissingArgumentIsRefusedAndNothingIsRun() {
        val result = run("calculus.derivative", mapOf("variable" to "x"))
        assertTrue("a missing expression must be refused", result is CalcResult.Err)
    }

    @Test fun thePreparedArgumentsAreTheOnesThatAreRun() {
        val spec = AiToolRegistry.byId("calculus.derivative")!!
        val prepared = AiToolExecutor.prepare(spec, mapOf("expression" to "x^2"))
        assertTrue(prepared is CalcResult.Ok)
        assertEquals("x^2", (prepared as CalcResult.Ok).value["expression"])
        assertEquals("x", prepared.value["variable"])
    }

    @Test fun thePreparationRefusesAMissingArgument() {
        val spec = AiToolRegistry.byId("calculus.derivative")!!
        val prepared = AiToolExecutor.prepare(spec, mapOf("variable" to "x"))
        assertTrue("a missing expression must be refused", prepared is CalcResult.Err)
    }

    @Test fun theProblemThatIsShownIsTheOneThatWasWorkedOut() {
        // The screen shows the arguments that are meaningful for the tool:
        // the ones the user gave, and not the ones the tool would have used
        // anyway.
        val result = run("calculus.derivative", mapOf("expression" to "x^2"))
        assertTrue(result is CalcResult.Ok)
        val outcome = (result as CalcResult.Ok).value
        val spec = AiToolRegistry.byId("calculus.derivative")!!
        val shown = outcome.input!!.meaningfulArguments(spec)
        assertEquals(1, shown.size)
        assertEquals("expression" to "x^2", shown[0])
    }

    @Test fun everyToolCanBeRunWithItsOwnArguments() {
        // A tool with several arguments must keep all of them, so that a
        // problem handed over carries every part of it.
        val result = run(
            "calculus.integral",
            mapOf(
                "expression" to "x^2",
                "variable" to "x",
                "lowerBound" to "0",
                "upperBound" to "2",
            ),
        )
        assertTrue(result is CalcResult.Ok)
        val input = (result as CalcResult.Ok).value.input!!
        assertEquals("x^2", input.arguments["expression"])
        assertEquals("0", input.arguments["lowerBound"])
        assertEquals("2", input.arguments["upperBound"])
    }
}
