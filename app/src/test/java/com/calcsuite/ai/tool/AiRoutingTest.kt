package com.calcsuite.ai.tool

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the reading of the model's reply and for the routing to the
 * tools.
 *
 * The replies here are the ones a model would give, written out by hand, so
 * that the tests are the same every time and no key is needed.
 */
class AiRoutingTest {

    /** The reply a model gives when it has read the problem. */
    private fun ready(toolId: String, arguments: String, problem: String): String =
        """
        {
          "status": "ready",
          "recognizedProblem": "$problem",
          "toolId": "$toolId",
          "arguments": $arguments
        }
        """.trimIndent()

    /**
     * Runs the whole way the app runs it: the reply is read, the tool is
     * looked up, the arguments are checked and the engine does the work.
     */
    private fun run(reply: String): CalcResult<AiToolOutcome> {
        val read = AiInterpreter.read(reply)
        if (read is CalcResult.Err) return read
        val interpretation = (read as CalcResult.Ok).value
        val toolId = interpretation.toolId
            ?: return com.calcsuite.core.err(
                com.calcsuite.core.ErrorKind.InvalidInput,
                "no tool",
            )
        return AiToolExecutor.run(toolId, interpretation.arguments)
    }

    private fun answer(reply: String): AiToolOutcome {
        val result = run(reply)
        assertTrue(
            "the reply was not run: " + (result as? CalcResult.Err)?.error?.message,
            result is CalcResult.Ok,
        )
        return (result as CalcResult.Ok).value
    }

    // ------------------------------------------------------ the calculus

    @Test fun aDefiniteIntegralIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "calculus.integral",
                """{"expression": "x^2", "variable": "x", "lowerBound": "0", "upperBound": "1"}""",
                "Evaluate the definite integral of x^2 from 0 to 1",
            ),
        )
        assertTrue(
            "the answer should be about a third but was: ${outcome.headline}",
            outcome.headline.toDoubleOrNull()?.let { Math.abs(it - 1.0 / 3.0) < 1e-6 } == true,
        )
    }

    @Test fun anIndefiniteIntegralIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "calculus.integral",
                """{"expression": "3x + 2", "variable": "x"}""",
                "Find the antiderivative of 3x + 2",
            ),
        )
        assertTrue(
            "the antiderivative should hold x^2 but was: ${outcome.headline}",
            outcome.headline.contains("x^2"),
        )
    }

    @Test fun aDerivativeIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "calculus.derivative",
                """{"expression": "sin(x)*x", "variable": "x"}""",
                "Differentiate x sin(x)",
            ),
        )
        assertTrue(
            "the derivative should mention the cosine but was: ${outcome.headline}",
            outcome.headline.contains("cos"),
        )
    }

    @Test fun aLimitIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "calculus.limit",
                """{"expression": "(x^2 - 1)/(x - 1)", "variable": "x", "point": "1"}""",
                "Find the limit of (x^2 - 1)/(x - 1) as x approaches 1",
            ),
        )
        assertTrue(
            "the limit should be 2 but was: ${outcome.headline}",
            outcome.headline.toDoubleOrNull()?.let { Math.abs(it - 2.0) < 1e-6 } == true,
        )
    }

    @Test fun aSumIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "calculus.summation",
                """{"expression": "n^2", "variable": "n", "from": "1", "to": "10"}""",
                "Add up n^2 for n from 1 to 10",
            ),
        )
        assertTrue(
            "the sum should be 385 but was: ${outcome.headline}",
            outcome.headline.toDoubleOrNull()?.let { Math.abs(it - 385.0) < 1e-6 } == true,
        )
    }

    @Test fun aNegativeNumberKeepsItsSign() {
        // A minus sign that is read as a positive gives a different answer,
        // so the sign has to reach the engine.
        val outcome = answer(
            ready(
                "calculus.derivative",
                """{"expression": "-x^3", "variable": "x"}""",
                "Differentiate -x^3",
            ),
        )
        // The engine writes its minus sign as U+2212, and it may put the
        // product in brackets, so the text is read rather than compared
        // exactly.
        val shown = outcome.headline
            .replace(" ", "")
            .replace("\u2212", "-")
            .replace("(", "")
            .replace(")", "")
        assertTrue(
            "the derivative should be -3x^2 but was: ${outcome.headline}",
            shown.contains("-3x^2"),
        )
    }

    @Test fun aRootIsReadAsAPower() {
        val outcome = answer(
            ready(
                "calculus.derivative",
                """{"expression": "sqrt(x)", "variable": "x"}""",
                "Differentiate the square root of x",
            ),
        )
        assertTrue(
            "the derivative should mention the square root but was: ${outcome.headline}",
            outcome.headline.contains("sqrt"),
        )
    }

    @Test fun aPartialDerivativeIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "calculus.partial",
                """{"expression": "x^2 + y^2", "variable": "x", "secondVariable": "y", "order": "1"}""",
                "Find the partial derivative of x^2 + y^2 with respect to x",
            ),
        )
        assertTrue(
            "the partial derivative should be 2x but was: ${outcome.headline}",
            outcome.headline.replace(" ", "").contains("2x"),
        )
    }

    // -------------------------------------------------------- the algebra

    @Test fun aLinearEquationIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "algebra.linear",
                """{"equation": "2x + 4 = 10", "variable": "x"}""",
                "Solve 2x + 4 = 10",
            ),
        )
        assertEquals("x = 3", outcome.headline)
    }

    @Test fun aQuadraticEquationIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "algebra.quadratic",
                """{"equation": "x^2 - 5x + 6 = 0", "variable": "x"}""",
                "Solve x^2 - 5x + 6 = 0",
            ),
        )
        assertTrue(
            "the roots should be 2 and 3 but were: ${outcome.headline}",
            outcome.headline.contains("2") && outcome.headline.contains("3"),
        )
    }

    @Test fun aSystemOfTwoEquationsIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "algebra.system",
                """{"a1": "2", "b1": "1", "c1": "5", "a2": "1", "b2": "-1", "c2": "1"}""",
                "Solve 2x + y = 5 and x - y = 1",
            ),
        )
        assertTrue(
            "the solution should be x = 2 and y = 1 but was: ${outcome.headline}",
            outcome.headline.contains("2") && outcome.headline.contains("1"),
        )
    }

    @Test fun anInequalityIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "algebra.inequality",
                """{"inequality": "2x + 3 < 11", "variable": "x"}""",
                "Solve the inequality 2x + 3 < 11",
            ),
        )
        assertTrue(
            "the solution should end at 4 but was: ${outcome.headline}",
            outcome.headline.contains("4"),
        )
    }

    @Test fun aFractionIsReadAsADivision() {
        val outcome = answer(
            ready(
                "calculus.derivative",
                """{"expression": "1/x", "variable": "x"}""",
                "Differentiate 1/x",
            ),
        )
        assertTrue(
            "the derivative should be -1/x^2 but was: ${outcome.headline}",
            outcome.headline.contains("x^2"),
        )
    }

    // ----------------------------------------------------- the advanced

    @Test fun theLengthOfAVectorIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "advanced.linear_algebra",
                """{"operation": "norm", "firstVector": "3, 4"}""",
                "Find the length of the vector (3, 4)",
            ),
        )
        assertEquals("5", outcome.headline)
    }

    @Test fun theMeasureOfAnIntervalIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "advanced.measure",
                """{"firstSet": "0, 2"}""",
                "Find the measure of the interval from 0 to 2",
            ),
        )
        assertEquals("2", outcome.headline)
    }

    @Test fun aComplexFunctionIsWorkedOutByTheEngine() {
        val outcome = answer(
            ready(
                "advanced.complex",
                """{"expression": "z^2", "point": "1, 1"}""",
                "Find z^2 at the point 1 + i",
            ),
        )
        // (1 + i)^2 = 2i
        assertTrue(
            "the answer should be 2i but was: ${outcome.headline}",
            outcome.headline.replace(" ", "").contains("2i"),
        )
    }

    // ------------------------------------------------- the odd replies

    @Test fun anAmbiguousProblemIsNotRun() {
        val result = run(
            """
            {
              "status": "ambiguous",
              "recognizedProblem": "Some expression with a mark that could be 3 or 8",
              "clarification": "Is the second symbol a 3 or an 8?"
            }
            """.trimIndent(),
        )
        assertTrue(
            "an ambiguous problem must not be run",
            result is CalcResult.Err,
        )
    }

    @Test fun anUnsupportedProblemIsNotRun() {
        val result = run(
            """
            {
              "status": "unsupported",
              "recognizedProblem": "Solve the Navier-Stokes equations for a turbulent flow",
              "clarification": "No tool works this problem out."
            }
            """.trimIndent(),
        )
        assertTrue(
            "an unsupported problem must not be run",
            result is CalcResult.Err,
        )
    }

    @Test fun aToolThatDoesNotExistIsRefused() {
        val result = run(
            ready(
                "calculus.does_not_exist",
                """{"expression": "x^2"}""",
                "Differentiate x^2",
            ),
        )
        assertTrue(
            "a tool that is not in the registry must be refused",
            result is CalcResult.Err,
        )
    }

    @Test fun aReplyThatIsNotJsonIsRefused() {
        val result = run("I think the answer is 42.")
        assertTrue("a reply that is not json must be refused", result is CalcResult.Err)
    }

    @Test fun aReplyWithNoStatusIsRefused() {
        val result = run("""{"recognizedProblem": "Differentiate x^2", "toolId": "calculus.derivative"}""")
        assertTrue("a reply with no status must be refused", result is CalcResult.Err)
    }

    @Test fun aReplyWithAnUnknownStatusIsRefused() {
        val result = run("""{"status": "perhaps", "recognizedProblem": "Something"}""")
        assertTrue("a reply with an unknown status must be refused", result is CalcResult.Err)
    }

    @Test fun aReadyReplyWithNoToolIsRefused() {
        val result = run(
            """{"status": "ready", "recognizedProblem": "Differentiate x^2"}""",
        )
        assertTrue("a ready reply with no tool must be refused", result is CalcResult.Err)
    }

    @Test fun aReplyInACodeFenceIsStillRead() {
        val read = AiInterpreter.read(
            "```json\n" + ready(
                "calculus.derivative",
                """{"expression": "x^2"}""",
                "Differentiate x^2",
            ) + "\n```",
        )
        assertTrue("a reply in a code fence must be read", read is CalcResult.Ok)
        assertEquals(
            "calculus.derivative",
            (read as CalcResult.Ok).value.toolId,
        )
    }

    @Test fun aNumberInTheArgumentsIsReadAsText() {
        val read = AiInterpreter.read(
            ready(
                "calculus.summation",
                """{"expression": "n", "from": 1, "to": 10}""",
                "Add up n from 1 to 10",
            ),
        )
        assertTrue(read is CalcResult.Ok)
        val arguments = (read as CalcResult.Ok).value.arguments
        assertEquals("1", arguments["from"])
        assertEquals("10", arguments["to"])
    }

    @Test fun aMissingArgumentIsRefusedWhenTheToolRuns() {
        val read = AiInterpreter.read(
            ready(
                "calculus.derivative",
                """{"variable": "x"}""",
                "Differentiate something",
            ),
        )
        assertTrue(read is CalcResult.Ok)
        val result = AiToolExecutor.run(
            (read as CalcResult.Ok).value.toolId.orEmpty(),
            (read as CalcResult.Ok).value.arguments,
        )
        assertTrue("a missing expression must be refused", result is CalcResult.Err)
    }

    // -------------------------------------------------- the instructions

    @Test fun theInstructionsListOnlyTheToolsInTheRegistry() {
        val instructions = AiInterpreter.instructions()
        for (spec in AiToolRegistry.all) {
            assertTrue(
                "the instructions must describe ${spec.id}",
                instructions.contains(spec.id),
            )
        }
        // The tools that must never be offered are not in the instructions.
        for (forbidden in listOf("random", "converter", "currency", "tip", "loan")) {
            assertFalse(
                "the $forbidden tool must not be offered",
                AiToolRegistry.all.any { it.id.contains(forbidden) },
            )
        }
    }

    @Test fun theInstructionsSayHowToWriteTheExpressions() {
        val instructions = AiInterpreter.instructions()
        assertTrue(
            "the instructions must say how to write a power",
            instructions.contains("^"),
        )
        assertTrue(
            "the instructions must say not to guess",
            instructions.contains("ambiguous"),
        )
        assertTrue(
            "the instructions must say that the tool does the mathematics",
            instructions.contains("never give an answer yourself", ignoreCase = true),
        )
    }
}
