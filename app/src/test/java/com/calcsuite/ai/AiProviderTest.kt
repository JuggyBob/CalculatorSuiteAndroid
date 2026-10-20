package com.calcsuite.ai

import com.calcsuite.core.CalcResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

/**
 * Tests for the ten provider adapters.
 *
 * Each provider is checked on its own: where the request goes, how it is
 * authenticated, how the picture is carried, how the reply is read and how
 * the provider's errors are turned into the app's own. No request is made:
 * the transport is replaced, so the tests cost nothing and need no key.
 */
class AiProviderTest {

    /** A transport that records the request and answers with a fixed reply. */
    private class FakeTransport(private val reply: String) : AiTransport {
        var last: AiRequest? = null
        override suspend fun send(request: AiRequest): String {
            last = request
            return reply
        }
    }

    /** A transport that never answers, so that the cancel can be checked. */
    private class HangingTransport : AiTransport {
        override suspend fun send(request: AiRequest): String {
            CompletableDeferred<Unit>().await()
            return ""
        }
    }

    /** A picture of a few bytes, with a real JPEG head. */
    private fun aPicture(): AiImage = AiImage(
        bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3),
        mediaType = AiImage.JPEG,
    )

    private fun aPrompt(withImage: Boolean = true): AiPrompt = AiPrompt(
        system = "the instructions",
        user = "read the picture",
        image = if (withImage) aPicture() else null,
    )

    // ------------------------------------------------------------ the ten

    @Test fun thereAreExactlyTenProviders() {
        assertEquals(10, AiProvider.entries.size)
        val names = AiProvider.entries.map { it.label }
        assertEquals(
            "the providers must be the ten that are supported",
            listOf(
                "OpenAI",
                "Anthropic",
                "Google Gemini",
                "OpenRouter",
                "DeepSeek",
                "Groq",
                "Mistral AI",
                "xAI",
                "Together AI",
                "Fireworks AI",
            ),
            names,
        )
    }

    @Test fun everyProviderHasItsOwnAddress() {
        val addresses = AiProvider.entries.map { it.baseUrl }
        assertEquals(
            "the providers must not share an address",
            addresses.size,
            addresses.toSet().size,
        )
    }

    @Test fun everyProviderSendsToTheRightPlace() {
        val expected = mapOf(
            AiProvider.OPENAI to "https://api.openai.com/v1/chat/completions",
            AiProvider.ANTHROPIC to "https://api.anthropic.com/v1/messages",
            AiProvider.GEMINI to
                "https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent",
            AiProvider.OPENROUTER to "https://openrouter.ai/api/v1/chat/completions",
            AiProvider.DEEPSEEK to "https://api.deepseek.com/chat/completions",
            AiProvider.GROQ to "https://api.groq.com/openai/v1/chat/completions",
            AiProvider.MISTRAL to "https://api.mistral.ai/v1/chat/completions",
            AiProvider.XAI to "https://api.x.ai/v1/chat/completions",
            AiProvider.TOGETHER to "https://api.together.xyz/v1/chat/completions",
            AiProvider.FIREWORKS to "https://api.fireworks.ai/inference/v1/chat/completions",
        )
        for ((provider, url) in expected) {
            assertEquals(
                "the address for ${provider.label} is wrong",
                url,
                provider.urlFor("test-model"),
            )
        }
    }

    @Test fun everyProviderUsesTheModelNameThatWasGiven() {
        for (provider in AiProvider.entries) {
            val transport = FakeTransport(aReplyFor(provider))
            val client = AiClient(provider, "the-key", "a-model-name", transport)
            val request = client.buildRequest(aPrompt(withImage = false))
            assertTrue(
                "the model name must be in the request for ${provider.label}",
                request.body.contains("a-model-name") || request.url.contains("a-model-name"),
            )
        }
    }

    // ------------------------------------------------------- the requests

    @Test fun theOpenAiRequestHasTheBearerKeyAndTheDataUrl() {
        val transport = FakeTransport("""{"choices":[{"message":{"content":"ok"}}]}""")
        val client = AiClient(AiProvider.OPENAI, "secret-key", "gpt-4o", transport)
        val request = client.buildRequest(aPrompt())

        assertEquals("https://api.openai.com/v1/chat/completions", request.url)
        assertEquals("POST", request.method)
        assertEquals("Bearer secret-key", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Content-Type"])

        val body = JSONObject(request.body)
        assertEquals("gpt-4o", body.getString("model"))
        val messages = body.getJSONArray("messages")
        assertEquals("the instructions and the question are two messages", 2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))

        val parts = messages.getJSONObject(1).getJSONArray("content")
        assertEquals("the picture and the question are two parts", 2, parts.length())
        val picture = parts.getJSONObject(0)
        assertEquals("image_url", picture.getString("type"))
        val url = picture.getJSONObject("image_url").getString("url")
        assertTrue("the picture must be a data URL but was: $url", url.startsWith("data:image/jpeg;base64,"))
        assertTrue("the picture must be in the request", url.length > "data:image/jpeg;base64,".length)
    }

    @Test fun theAnthropicRequestHasTheApiKeyAndTheImageSource() {
        val transport = FakeTransport("""{"content":[{"type":"text","text":"ok"}]}""")
        val client = AiClient(AiProvider.ANTHROPIC, "secret-key", "claude-sonnet-5", transport)
        val request = client.buildRequest(aPrompt())

        assertEquals("https://api.anthropic.com/v1/messages", request.url)
        assertEquals("secret-key", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])

        val body = JSONObject(request.body)
        assertEquals("claude-sonnet-5", body.getString("model"))
        assertEquals("the instructions are at the top", "the instructions", body.getString("system"))
        val parts = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val picture = parts.getJSONObject(0)
        assertEquals("image", picture.getString("type"))
        val source = picture.getJSONObject("source")
        assertEquals("base64", source.getString("type"))
        assertEquals("image/jpeg", source.getString("media_type"))
        assertTrue("the picture must be in the request", source.getString("data").isNotBlank())
    }

    @Test fun theGeminiRequestHasTheKeyAndTheInlineData() {
        val transport = FakeTransport(
            """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""",
        )
        val client = AiClient(AiProvider.GEMINI, "secret-key", "gemini-3.8-flash", transport)
        val request = client.buildRequest(aPrompt())

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
            request.url,
        )
        assertEquals("secret-key", request.headers["x-goog-api-key"])

        val body = JSONObject(request.body)
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val picture = parts.getJSONObject(0)
        assertTrue("the picture must be inline data", picture.has("inline_data"))
        assertEquals("image/jpeg", picture.getJSONObject("inline_data").getString("mime_type"))
        assertTrue(
            "the instructions must be sent to Gemini",
            body.getJSONObject("systemInstruction").getJSONArray("parts")
                .getJSONObject(0).getString("text") == "the instructions",
        )
    }

    @Test fun everyOpenAiCompatibleProviderCarriesThePictureTheSameWay() {
        val openAiLike = listOf(
            AiProvider.OPENAI,
            AiProvider.OPENROUTER,
            AiProvider.DEEPSEEK,
            AiProvider.GROQ,
            AiProvider.MISTRAL,
            AiProvider.XAI,
            AiProvider.TOGETHER,
            AiProvider.FIREWORKS,
        )
        for (provider in openAiLike) {
            val transport = FakeTransport("""{"choices":[{"message":{"content":"ok"}}]}""")
            val client = AiClient(provider, "secret-key", "a-model", transport)
            val request = client.buildRequest(aPrompt())
            val body = JSONObject(request.body)
            val parts = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
            val picture = parts.getJSONObject(0)
            assertEquals(
                "${provider.label} must send the picture as an image",
                "image_url",
                picture.getString("type"),
            )
            assertEquals(
                "${provider.label} must use the bearer key",
                "Bearer secret-key",
                request.headers["Authorization"],
            )
        }
    }

    @Test fun aRequestWithoutAPictureHasNoPicturePart() {
        val transport = FakeTransport("""{"choices":[{"message":{"content":"ok"}}]}""")
        val client = AiClient(AiProvider.OPENAI, "secret-key", "gpt-4o", transport)
        val request = client.buildRequest(aPrompt(withImage = false))
        val body = JSONObject(request.body)
        val parts = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("only the question is sent", 1, parts.length())
        assertEquals("text", parts.getJSONObject(0).getString("type"))
    }

    // ------------------------------------------------------- the replies

    @Test fun theReplyIsReadInEachProvidersOwnShape() {
        val replies = mapOf(
            AiProvider.OPENAI to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.OPENROUTER to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.DEEPSEEK to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.GROQ to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.MISTRAL to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.XAI to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.TOGETHER to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.FIREWORKS to """{"choices":[{"message":{"content":"the answer"}}]}""",
            AiProvider.ANTHROPIC to """{"content":[{"type":"text","text":"the answer"}]}""",
            AiProvider.GEMINI to
                """{"candidates":[{"content":{"parts":[{"text":"the answer"}]}}]}""",
        )
        for ((provider, reply) in replies) {
            val transport = FakeTransport(reply)
            val client = AiClient(provider, "secret-key", "a-model", transport)
            val parsed = client.parseResponse(reply)
            assertTrue(
                "the reply from ${provider.label} could not be read",
                parsed is CalcResult.Ok,
            )
            assertEquals(
                "the reply from ${provider.label} was read wrongly",
                "the answer",
                (parsed as CalcResult.Ok).value.text,
            )
        }
    }

    @Test fun anEmptyReplyIsRefused() {
        val client = AiClient(
            AiProvider.OPENAI,
            "secret-key",
            "gpt-4o",
            FakeTransport("{}"),
        )
        val parsed = client.parseResponse("""{"choices":[]}""")
        assertTrue("an empty reply must be refused", parsed is CalcResult.Err)
    }

    @Test fun aReplyThatIsNotJsonIsRefused() {
        val client = AiClient(
            AiProvider.OPENAI,
            "secret-key",
            "gpt-4o",
            FakeTransport("not json at all"),
        )
        val parsed = client.parseResponse("not json at all")
        assertTrue("a reply that is not json must be refused", parsed is CalcResult.Err)
    }

    @Test fun anErrorInTheReplyIsTurnedIntoTheAppsOwnMessage() {
        val client = AiClient(
            AiProvider.OPENAI,
            "secret-key",
            "gpt-4o",
            FakeTransport("""{"error":{"message":"Incorrect API key provided"}}"""),
        )
        val parsed = client.parseResponse("""{"error":{"message":"Incorrect API key provided"}}""")
        assertTrue("an error must be refused", parsed is CalcResult.Err)
        val message = (parsed as CalcResult.Err).error.message
        assertTrue(
            "the message must not repeat the provider's own words: $message",
            !message.contains("Incorrect API key provided"),
        )
    }

    // ------------------------------------------------------ the errors

    @Test fun theErrorsAreToldApart() {
        val refused = AiClient.classify(401, "bad key")
        assertTrue("a refused key must be shown plainly", refused.second.contains("API key"))

        val missingModel = AiClient.classify(404, "no such model")
        assertTrue(
            "a missing model must be shown plainly: ${missingModel.second}",
            missingModel.second.contains("model"),
        )

        val spent = AiClient.classify(429, "quota")
        assertTrue(
            "a spent quota must be shown plainly: ${spent.second}",
            spent.second.contains("quota") || spent.second.contains("quickly"),
        )

        val down = AiClient.classify(503, "the provider is down")
        assertTrue(
            "the provider being down must be shown plainly: ${down.second}",
            down.second.contains("trouble") || down.second.contains("down"),
        )

        val noImage = AiClient.classify(400, "bad request", type = "invalid_image")
        assertTrue(
            "a model that cannot read pictures must be said so: ${noImage.second}",
            noImage.second.contains("cannot read images"),
        )
    }

    @Test fun anEmptyKeyIsRefusedBeforeARequestIsMade() = runTest {
        val transport = FakeTransport("{}")
        val client = AiClient(AiProvider.OPENAI, "", "gpt-4o", transport)
        val reply = client.ask(aPrompt())
        assertTrue("an empty key must be refused", reply is CalcResult.Err)
        assertEquals("no request must be made", null, transport.last)
    }

    @Test fun anEmptyModelIsRefusedBeforeARequestIsMade() = runTest {
        val transport = FakeTransport("{}")
        val client = AiClient(AiProvider.OPENAI, "secret-key", "", transport)
        val reply = client.ask(aPrompt())
        assertTrue("an empty model must be refused", reply is CalcResult.Err)
        assertEquals("no request must be made", null, transport.last)
    }

    // -------------------------------------------------- the cancellation

    @Test fun aRequestThatIsCancelledStops() = runTest {
        val client = AiClient(AiProvider.OPENAI, "secret-key", "gpt-4o", HangingTransport())
        val job = async { client.ask(aPrompt()) }
        yield()
        job.cancel()
        var cancelled = false
        try {
            job.await()
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue("the request must be stopped when it is cancelled", cancelled)
    }

    // ------------------------------------------------------------ helpers

    /** The reply each provider would give, for the tests above. */
    private fun aReplyFor(provider: AiProvider): String = when (provider.format) {
        AiWireFormat.OPENAI_CHAT -> """{"choices":[{"message":{"content":"ok"}}]}"""
        AiWireFormat.ANTHROPIC_MESSAGES -> """{"content":[{"type":"text","text":"ok"}]}"""
        AiWireFormat.GEMINI_CONTENT ->
            """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""
    }
}
