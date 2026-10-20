package com.calcsuite.ai.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the registry of mathematical tools the AI may choose from.
 *
 * The registry is the list of tools that can really answer a mathematical
 * problem, so the tests check that every tool in it runs, that every tool
 * outside it cannot be run, and that the descriptions say what the engines
 * actually accept.
 */
class AiToolRegistryTest {

    @Test fun everyToolHasAnIdThatIsItsOwn() {
        val ids = AiToolRegistry.all.map { it.id }
        assertEquals("the tool ids must be different", ids.size, ids.toSet().size)
    }

    @Test fun everyToolIsInOneOfTheThreeAreas() {
        assertTrue(
            "there must be tools for the calculus",
            AiToolRegistry.inDomain(AiToolDomain.CALCULUS).isNotEmpty(),
        )
        assertTrue(
            "there must be tools for the algebra",
            AiToolRegistry.inDomain(AiToolDomain.ALGEBRA).isNotEmpty(),
        )
        assertTrue(
            "there must be tools for the advanced mathematics",
            AiToolRegistry.inDomain(AiToolDomain.ADVANCED).isNotEmpty(),
        )
    }

    @Test fun everyToolSaysHowItIsUsedAndWhatItCannotDo() {
        for (spec in AiToolRegistry.all) {
            assertTrue("${spec.id} needs a description", spec.description.isNotBlank())
            assertTrue("${spec.id} needs its grammar", spec.grammar.isNotBlank())
            assertTrue("${spec.id} needs to say what it cannot do", spec.limitations.isNotBlank())
            assertTrue("${spec.id} needs an example", spec.examples.isNotEmpty())
        }
    }

    @Test fun everyToolHasAtLeastOneRequiredArgument() {
        for (spec in AiToolRegistry.all) {
            assertTrue(
                "${spec.id} must ask for something",
                spec.requiredArguments.isNotEmpty(),
            )
        }
    }

    @Test fun everyToolRunsThroughItsAdapter() {
        // Each tool is given the arguments its own examples use, so that the
        // registry and the engines are checked together.
        for (spec in AiToolRegistry.all) {
            val arguments = sampleArguments(spec)
            val result = AiToolExecutor.run(spec.id, arguments)
            assertTrue(
                "${spec.id} should work with $arguments but said: " +
                    (result as? com.calcsuite.core.CalcResult.Err)?.error?.message,
                result is com.calcsuite.core.CalcResult.Ok,
            )
            val outcome = (result as com.calcsuite.core.CalcResult.Ok).value
            assertTrue("${spec.id} must give an answer", outcome.headline.isNotBlank())
        }
    }

    @Test fun aToolThatDoesNotExistCannotBeRun() {
        val result = AiToolExecutor.run("nonsense.tool", mapOf("expression" to "x^2"))
        assertTrue(
            "a tool that is not in the registry must be refused",
            result is com.calcsuite.core.CalcResult.Err,
        )
    }

    @Test fun theRandomNumberGeneratorIsNotOffered() {
        // The random generator exists in the app but cannot answer a
        // mathematical problem, so it must not be in the registry.
        val ids = AiToolRegistry.all.map { it.id }
        for (forbidden in listOf("random", "converter", "currency", "tip", "loan")) {
            assertFalse(
                "the $forbidden tool must not be offered to the model",
                ids.any { it.contains(forbidden) },
            )
        }
    }

    @Test fun aMissingArgumentIsRefusedBeforeTheEngineIsCalled() {
        val result = AiToolExecutor.run("calculus.derivative", mapOf("variable" to "x"))
        assertTrue(
            "a missing expression must be refused",
            result is com.calcsuite.core.CalcResult.Err,
        )
    }

    @Test fun anArgumentThatIsNotANumberIsRefused() {
        val result = AiToolExecutor.run(
            "calculus.limit",
            mapOf("expression" to "sin(x)", "point" to "not a number"),
        )
        assertTrue(
            "a point that is not a number must be refused",
            result is com.calcsuite.core.CalcResult.Err,
        )
    }

    @Test fun theToolsGiveTheAnswersTheAppGives() {
        // The answers are the ones the app's own screens give, so a check of
        // a few of them pins the routing to the real engines.
        val derivative = AiToolExecutor.run(
            "calculus.derivative",
            mapOf("expression" to "x^2", "variable" to "x"),
        )
        assertTrue(derivative is com.calcsuite.core.CalcResult.Ok)
        assertEquals(
            "the derivative of x^2 is 2x",
            "2x",
            (derivative as com.calcsuite.core.CalcResult.Ok).value.headline.replace(" ", ""),
        )

        val linear = AiToolExecutor.run(
            "algebra.linear",
            mapOf("equation" to "2x + 4 = 10", "variable" to "x"),
        )
        assertTrue(linear is com.calcsuite.core.CalcResult.Ok)
        assertEquals(
            "the root of 2x + 4 = 10 is 3",
            "x = 3",
            (linear as com.calcsuite.core.CalcResult.Ok).value.headline,
        )

        val quadratic = AiToolExecutor.run(
            "algebra.quadratic",
            mapOf("equation" to "x^2 - 5x + 6 = 0", "variable" to "x"),
        )
        assertTrue(quadratic is com.calcsuite.core.CalcResult.Ok)
        val roots = (quadratic as com.calcsuite.core.CalcResult.Ok).value.headline
        assertTrue(
            "the roots should be 2 and 3 but were: $roots",
            roots.contains("2") && roots.contains("3"),
        )

        val system = AiToolExecutor.run(
            "algebra.system",
            mapOf(
                "a1" to "2", "b1" to "1", "c1" to "5",
                "a2" to "1", "b2" to "-1", "c2" to "1",
            ),
        )
        assertTrue(system is com.calcsuite.core.CalcResult.Ok)
        val solution = (system as com.calcsuite.core.CalcResult.Ok).value.headline
        assertTrue("the solution should be x = 2 but was: $solution", solution.contains("2"))
        assertTrue("the solution should be y = 1 but was: $solution", solution.contains("1"))
    }

    @Test fun aNumericAnswerIsMarkedAsAnApproximation() {
        // The definite integral of x^2 from 0 to 1 is worked out by adaptive
        // Simpson's rule, so the answer is a number, not an exact fraction,
        // and the app has to say so.
        val integral = AiToolExecutor.run(
            "calculus.integral",
            mapOf(
                "expression" to "x^2",
                "variable" to "x",
                "lowerBound" to "0",
                "upperBound" to "1",
            ),
        )
        assertTrue(integral is com.calcsuite.core.CalcResult.Ok)
        val outcome = (integral as com.calcsuite.core.CalcResult.Ok).value
        assertTrue(
            "the answer must be marked as an approximation but was not",
            outcome.approximate,
        )
        assertTrue(
            "the method should say how the answer was found: ${outcome.method}",
            outcome.method.isNotBlank(),
        )
        // The value is the integral of x^2 from 0 to 1, which is a third.
        val value = outcome.headline.toDoubleOrNull()
        assertTrue("the answer should be a number but was: ${outcome.headline}", value != null)
        assertEquals("the integral of x^2 from 0 to 1", 1.0 / 3.0, value!!, 1e-6)
    }

    @Test fun anExactAnswerIsNotMarkedAsAnApproximation() {
        val derivative = AiToolExecutor.run(
            "calculus.derivative",
            mapOf("expression" to "x^2 + 3x", "variable" to "x"),
        )
        assertTrue(derivative is com.calcsuite.core.CalcResult.Ok)
        val outcome = (derivative as com.calcsuite.core.CalcResult.Ok).value
        assertFalse(
            "a symbolic derivative is exact and must not be marked as an approximation",
            outcome.approximate,
        )
    }

    @Test fun aSystemThatDoesNotAddUpIsRefused() {
        // The six coefficients are put back into the two equations, so a set
        // of coefficients that does not describe the equations is refused
        // rather than answered.
        val result = AiToolExecutor.run(
            "algebra.system",
            mapOf(
                "a1" to "2", "b1" to "1", "c1" to "5",
                "a2" to "1", "b2" to "-1", "c2" to "9",
            ),
        )
        assertTrue(
            "the answer did not satisfy the equations but was given anyway",
            result is com.calcsuite.core.CalcResult.Err,
        )
    }

    @Test fun theSearchFindsTheTools() {
        assertTrue(AiToolRegistry.search("integral").isNotEmpty())
        assertTrue(AiToolRegistry.search("derivative").isNotEmpty())
        assertTrue(AiToolRegistry.search("nothing at all").isEmpty())
    }

    /** The arguments each tool's first example needs. */
    private fun sampleArguments(spec: AiToolSpec): Map<String, String> = when (spec.id) {
        "calculus.derivative" -> mapOf("expression" to "x^2")
        "calculus.integral" -> mapOf(
            "expression" to "x^2",
            "lowerBound" to "0",
            "upperBound" to "1",
        )
        "calculus.limit" -> mapOf("expression" to "sin(x)/x", "point" to "0")
        "calculus.summation" -> mapOf("expression" to "n", "from" to "1", "to" to "10")
        "calculus.taylor" -> mapOf("expression" to "sin(x)", "centre" to "0", "order" to "4")
        "calculus.series" -> mapOf("expression" to "1/n^2", "firstTerm" to "1", "lastTerm" to "100")
        "calculus.roots" -> mapOf("expression" to "x^2 - 4", "from" to "-10", "to" to "10")
        "calculus.extrema" -> mapOf("expression" to "x^3 - 3x", "from" to "-5", "to" to "5")
        "calculus.ode" -> mapOf("equation" to "dy/dx = 2y")
        "calculus.partial" -> mapOf("expression" to "x^2 + y^2", "variable" to "x", "secondVariable" to "y")
        "algebra.linear" -> mapOf("equation" to "2x + 4 = 10")
        "algebra.quadratic" -> mapOf("equation" to "x^2 - 5x + 6 = 0")
        "algebra.system" -> mapOf(
            "a1" to "2", "b1" to "1", "c1" to "5",
            "a2" to "1", "b2" to "-1", "c2" to "1",
        )
        "algebra.inequality" -> mapOf("inequality" to "2x + 3 < 11")
        "algebra.gcf" -> mapOf("first" to "12", "second" to "18")
        "algebra.lcm" -> mapOf("first" to "4", "second" to "6")
        "algebra.prime" -> mapOf("number" to "60")
        "algebra.combinatorics" -> mapOf("n" to "5", "r" to "2")
        "advanced.linear_algebra" -> mapOf("operation" to "norm", "firstVector" to "3, 4")
        "advanced.measure" -> mapOf("firstSet" to "0, 2")
        "advanced.complex" -> mapOf("expression" to "z^2", "point" to "1, 1")
        "advanced.fourier" -> mapOf("expression" to "sin(x)", "from" to "0", "to" to "1")
        "advanced.pde" -> mapOf("equation" to "heat", "initial" to "sin(pi*x)")
        else -> error("the tool ${spec.id} has no sample arguments")
    }
}
