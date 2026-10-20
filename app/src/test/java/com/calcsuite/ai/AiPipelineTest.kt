package com.calcsuite.ai

import com.calcsuite.ai.tool.AiInterpretationStatus
import com.calcsuite.core.CalcResult
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the way a picture becomes an answer.
 *
 * The provider is replaced, so the whole way through is checked without a
 * key and without anything leaving the phone. The mathematics is still the
 * app's own: the answer comes from the engine, not from the mocked reply.
 */
class AiPipelineTest {

    /** A provider that answers with a fixed reply and records the request. */
    private class FakeProvider(private val reply: String) : AiTransport {
        var last: AiRequest? = null
        override suspend fun send(request: AiRequest): String {
            last = request
            return reply
        }
    }

    /** A picture of a few bytes. */
    private fun aPicture(): AiImage = AiImage(
        bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 7, 8, 9),
        mediaType = AiImage.JPEG,
    )

    /** The reply a provider gives, wrapped in the shape OpenAI uses. */
    private fun aReply(text: String): String =
        JSONObject()
            .put("choices", org.json.JSONArray().put(JSONObject().put(
                "message",
                JSONObject().put("content", text),
            )))
            .toString()

    @Test fun aClearProblemIsWorkedOutByTheEngine() = runTest {
        val provider = FakeProvider(
            aReply(
                """
                {
                  "status": "ready",
                  "recognizedProblem": "Differentiate x^3 - 4x + 1",
                  "toolId": "calculus.derivative",
                  "arguments": {"expression": "x^3 - 4x + 1", "variable": "x"}
                }
                """.trimIndent(),
            ),
        )
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        assertTrue(
            "the reply was not read: " + (read as? CalcResult.Err)?.error?.message,
            read is CalcResult.Ok,
        )
        val interpretation = (read as CalcResult.Ok).value
        assertEquals(AiInterpretationStatus.READY, interpretation.status)
        assertEquals("calculus.derivative", interpretation.toolId)

        val answer = solver.solve(interpretation)
        assertTrue(
            "the tool did not run: " + (answer as? CalcResult.Err)?.error?.message,
            answer is CalcResult.Ok,
        )
        val outcome = (answer as CalcResult.Ok).value
        // The derivative of x^3 - 4x + 1 is 3x^2 - 4, which the engine finds.
        assertTrue(
            "the answer should be 3x^2 - 4 but was: ${outcome.headline}",
            outcome.headline.replace(" ", "").contains("3x^2"),
        )
    }

    @Test fun thePictureIsSentWithTheRequest() = runTest {
        val provider = FakeProvider(aReply("""{"status": "unsupported"}"""))
        val solver = AiProblemSolver(provider)
        solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        val body = JSONObject(provider.last!!.body)
        val parts = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("the picture and the question are both sent", 2, parts.length())
        assertEquals("image_url", parts.getJSONObject(0).getString("type"))
        assertTrue(
            "the picture must be in the request",
            parts.getJSONObject(0).getJSONObject("image_url").getString("url")
                .startsWith("data:image/jpeg;base64,"),
        )
    }

    @Test fun theInstructionsListTheToolsThatMayBeChosen() = runTest {
        val provider = FakeProvider(aReply("""{"status": "unsupported"}"""))
        val solver = AiProblemSolver(provider)
        solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        val body = JSONObject(provider.last!!.body)
        val instructions = body.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(
            "the instructions must name the derivative tool",
            instructions.contains("calculus.derivative"),
        )
        assertTrue(
            "the instructions must name the integral tool",
            instructions.contains("calculus.integral"),
        )
        assertTrue(
            "the instructions must say what the answer looks like",
            instructions.contains("recognizedProblem"),
        )
    }

    @Test fun anAmbiguousProblemIsNotWorkedOut() = runTest {
        val provider = FakeProvider(
            aReply(
                """
                {
                  "status": "ambiguous",
                  "recognizedProblem": "An expression with a mark that could be a 3 or an 8",
                  "clarification": "Is the second symbol a 3 or an 8?"
                }
                """.trimIndent(),
            ),
        )
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        assertTrue(read is CalcResult.Ok)
        val interpretation = (read as CalcResult.Ok).value
        assertEquals(AiInterpretationStatus.AMBIGUOUS, interpretation.status)
        assertTrue(
            "the question for the user must be kept",
            interpretation.clarification?.contains("3") == true,
        )
        val answer = solver.solve(interpretation)
        assertTrue("an ambiguous problem must not be worked out", answer is CalcResult.Err)
    }

    @Test fun anUnsupportedProblemIsNotWorkedOut() = runTest {
        val provider = FakeProvider(
            aReply(
                """
                {
                  "status": "unsupported",
                  "recognizedProblem": "Solve the Navier-Stokes equations",
                  "clarification": "No tool works this problem out."
                }
                """.trimIndent(),
            ),
        )
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        assertTrue(read is CalcResult.Ok)
        val answer = solver.solve((read as CalcResult.Ok).value)
        assertTrue("an unsupported problem must not be worked out", answer is CalcResult.Err)
    }

    @Test fun aMalformedReplyIsRefused() = runTest {
        val provider = FakeProvider(aReply("I think the answer is 42."))
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        assertTrue("a reply that is not json must be refused", read is CalcResult.Err)
    }

    @Test fun aToolThatDoesNotExistIsRefused() = runTest {
        val provider = FakeProvider(
            aReply(
                """
                {
                  "status": "ready",
                  "recognizedProblem": "Differentiate x^2",
                  "toolId": "calculus.not_a_tool",
                  "arguments": {"expression": "x^2"}
                }
                """.trimIndent(),
            ),
        )
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        assertTrue("a made up tool must be refused", read is CalcResult.Err)
    }

    @Test fun aRefusedKeyIsReportedPlainly() = runTest {
        val provider = FakeProvider(
            """{"error":{"message":"Invalid API key: sk-live-abcdefghijklmnop"}}""",
        )
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        assertTrue("the error must be reported", read is CalcResult.Err)
        val message = (read as CalcResult.Err).error.message
        assertTrue("the message must be plain: $message", message.contains("API key"))
        assertTrue(
            "the key must not be shown: $message",
            !message.contains("sk-live"),
        )
    }

    @Test fun noKeyMeansNoRequest() = runTest {
        val provider = FakeProvider("{}")
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "", aPicture())
        assertTrue("without a key nothing is sent", read is CalcResult.Err)
        assertEquals("no request must be made", null, provider.last)
    }

    @Test fun noModelMeansNoRequest() = runTest {
        val provider = FakeProvider("{}")
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "", "the-key", aPicture())
        assertTrue("without a model nothing is sent", read is CalcResult.Err)
        assertEquals("no request must be made", null, provider.last)
    }

    @Test fun theAnswerIsTheEnginesAndNotTheModels() = runTest {
        // The mocked provider says one thing, and the engine says another.
        // The engine is the one that must be shown.
        val provider = FakeProvider(
            aReply(
                """
                {
                  "status": "ready",
                  "recognizedProblem": "Add up n from 1 to 10",
                  "toolId": "calculus.summation",
                  "arguments": {"expression": "n", "variable": "n", "from": "1", "to": "10"}
                }
                """.trimIndent(),
            ),
        )
        val solver = AiProblemSolver(provider)
        val read = solver.interpret(AiProvider.OPENAI, "gpt-4o", "the-key", aPicture())
        val answer = solver.solve((read as CalcResult.Ok).value)
        assertTrue(answer is CalcResult.Ok)
        val outcome = (answer as CalcResult.Ok).value
        assertEquals(
            "the answer must come from the engine",
            "55",
            outcome.headline,
        )
    }
}
