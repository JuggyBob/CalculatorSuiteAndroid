package com.calcsuite.ai

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * A picture to send to a provider.
 *
 * The image is kept as raw bytes and the media type is recorded, because the
 * providers differ in how they want the two: OpenAI-compatible providers take
 * a data URL, Anthropic takes base64 with a media type, and Gemini takes
 * base64 with a MIME type.
 */
data class AiImage(
    val bytes: ByteArray,
    val mediaType: String,
) {
    /** The bytes as base64, which is what every provider needs. */
    fun base64(): String = java.util.Base64.getEncoder().encodeToString(bytes)

    /** The bytes as a data URL, which is what the OpenAI shape needs. */
    fun dataUrl(): String = "data:$mediaType;base64,${base64()}"

    override fun equals(other: Any?): Boolean =
        other is AiImage && mediaType == other.mediaType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()

    companion object {
        const val JPEG = "image/jpeg"
        const val PNG = "image/png"
        const val WEBP = "image/webp"

        /**
         * The media type that matches the bytes.
         *
         * The type is read from the file itself rather than from its name,
         * because the providers look at the content.
         */
        fun mediaTypeOf(bytes: ByteArray): String? = when {
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> JPEG
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> PNG
            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() &&
                bytes[11] == 0x50.toByte() -> WEBP
            else -> null
        }
    }
}

/**
 * What the user asked the provider.
 *
 * The prompt and the picture are separate so that the same request can be
 * built for any provider.
 */
data class AiPrompt(
    /** The instructions that describe the task. */
    val system: String,
    /** The question about the picture. */
    val user: String,
    /** The picture, when the request has one. */
    val image: AiImage? = null,
)

/**
 * Sends a prompt to a provider and reads the reply.
 *
 * The provider's own shape is used for both the request and the reply, so
 * each provider behaves the way its own documentation says.
 */
class AiClient(
    private val provider: AiProvider,
    private val apiKey: String,
    private val model: String,
    private val transport: AiTransport = HttpTransport(),
    private val timeoutMillis: Int = 60_000,
) {

    /** The reply from the provider. */
    data class Reply(val text: String)

    /**
     * Asks the provider to interpret the prompt.
     *
     * The errors are the ones the app shows the user: an unusable key, a
     * model the provider does not have, a spent quota, a timeout and the
     * provider being down are all told apart, because the user can do
     * something about each of them.
     */
    suspend fun ask(prompt: AiPrompt): CalcResult<Reply> {
        if (apiKey.isBlank()) {
            return err(ErrorKind.InvalidInput, "No API key has been entered for ${provider.label}.")
        }
        if (model.isBlank()) {
            return err(ErrorKind.InvalidInput, "No model has been entered for ${provider.label}.")
        }
        val request = buildRequest(prompt)
        val response = try {
            // The request is made off the main thread, and the work is
            // cancelled when the caller goes away, so that a request that is
            // no longer wanted does not go on.
            withContext(Dispatchers.IO) { transport.send(request) }
        } catch (e: AiTransportException) {
            return err(e.kind, e.message)
        } catch (e: CancellationException) {
            throw e
        }
        return parseResponse(response)
    }

    /** The request that would be sent, for the tests and for the connection check. */
    fun buildRequest(prompt: AiPrompt): AiRequest = when (provider.format) {
        AiWireFormat.OPENAI_CHAT -> buildOpenAiRequest(prompt)
        AiWireFormat.ANTHROPIC_MESSAGES -> buildAnthropicRequest(prompt)
        AiWireFormat.GEMINI_CONTENT -> buildGeminiRequest(prompt)
    }

    private fun buildOpenAiRequest(prompt: AiPrompt): AiRequest {
        val content = JSONArray()
        prompt.image?.let { image ->
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", image.dataUrl())),
            )
        }
        content.put(JSONObject().put("type", "text").put("text", prompt.user))
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", prompt.system))
                    .put(JSONObject().put("role", "user").put("content", content)),
            )
            .put("max_tokens", 4096)
        return AiRequest(
            url = provider.urlFor(model),
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json",
            ),
            body = body.toString(),
        )
    }

    private fun buildAnthropicRequest(prompt: AiPrompt): AiRequest {
        val content = JSONArray()
        prompt.image?.let { image ->
            content.put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", image.mediaType)
                            .put("data", image.base64()),
                    ),
            )
        }
        content.put(JSONObject().put("type", "text").put("text", prompt.user))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("system", prompt.system)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", content)),
            )
        return AiRequest(
            url = provider.urlFor(model),
            method = "POST",
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json",
            ),
            body = body.toString(),
        )
    }

    private fun buildGeminiRequest(prompt: AiPrompt): AiRequest {
        val parts = JSONArray()
        prompt.image?.let { image ->
            parts.put(
                JSONObject()
                    .put("inline_data", JSONObject().put("mime_type", image.mediaType).put("data", image.base64())),
            )
        }
        parts.put(JSONObject().put("text", prompt.user))
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", parts),
                ),
            )
            .put(
                "systemInstruction",
                JSONObject()
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt.system))),
            )
            .put("generationConfig", JSONObject().put("maxOutputTokens", 4096))
        return AiRequest(
            url = provider.urlFor(model),
            method = "POST",
            headers = mapOf(
                "x-goog-api-key" to apiKey,
                "Content-Type" to "application/json",
            ),
            body = body.toString(),
        )
    }

    /** Reads the reply in the provider's own shape. */
    fun parseResponse(response: String): CalcResult<Reply> {
        val root = try {
            JSONObject(response)
        } catch (e: Exception) {
            return err(ErrorKind.Syntax, "The reply from ${provider.label} could not be read.")
        }
        // The error shapes the providers use are different, so they are all
        // looked for before the reply is read.
        findApiError(root)?.let { return err(it.first, it.second) }

        val text = when (provider.format) {
            AiWireFormat.OPENAI_CHAT -> root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }

            AiWireFormat.ANTHROPIC_MESSAGES -> root.optJSONArray("content")
                ?.let { array ->
                    (0 until array.length())
                        .map { array.optJSONObject(it) }
                        .firstOrNull { it?.optString("type") == "text" }
                        ?.optString("text")
                }

            AiWireFormat.GEMINI_CONTENT -> root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.let { array ->
                    (0 until array.length())
                        .map { array.optJSONObject(it) }
                        .firstOrNull { it?.optString("text")?.isNotBlank() == true }
                        ?.optString("text")
                }
        }
        if (text.isNullOrBlank()) {
            val finish = root.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason")
            if (finish == "length") {
                return err(
                    ErrorKind.Overflow,
                    "The reply from ${provider.label} was cut short. Try a shorter problem.",
                )
            }
            return err(ErrorKind.Syntax, "The reply from ${provider.label} was empty.")
        }
        return ok(Reply(text))
    }

    /** The error the provider reported, if it reported one. */
    private fun findApiError(root: JSONObject): Pair<ErrorKind, String>? {
        val error = root.optJSONObject("error") ?: return null
        val raw = error.optString("message").ifBlank { "The provider reported an error." }
        val type = error.optString("type") + " " + error.optString("code")
        // The reply does not always carry the status, so the kind of problem
        // is worked out from the words the provider used.
        val status = guessStatus(raw, type)
        return classify(status = status, message = redact(raw), type = type)
    }

    /**
     * The status the provider's own words point at.
     *
     * The reply often has no status, but the wording says what went wrong:
     * a key that was refused, a model that is not there, a spent quota.
     */
    private fun guessStatus(message: String, type: String): Int {
        val text = (message + " " + type).lowercase()
        return when {
            text.contains("api key") || text.contains("unauthorized") ||
                text.contains("authentication") || text.contains("permission") -> 401
            text.contains("rate limit") || text.contains("quota") ||
                text.contains("too many requests") -> 429
            text.contains("model") && (text.contains("not found") ||
                text.contains("does not exist") || text.contains("unknown") ||
                text.contains("no such")) -> 404
            text.contains("image") || text.contains("vision") ||
                text.contains("multimodal") -> 400
            else -> 0
        }
    }

    /**
     * The provider's own words, with anything that looks like a key taken
     * out.
     *
     * A provider sometimes repeats the key, or part of it, in its error, and
     * that must not be shown to the user.
     */
    private fun redact(message: String): String {
        val cleaned = message
            .replace(Regex("(sk|pk|rk|key|api)[-_][A-Za-z0-9_\\-]{4,}"), "[the key]")
            .replace(Regex("[A-Za-z0-9_\\-]{28,}"), "[hidden]")
        return if (cleaned.length > 200) cleaned.take(200) + "…" else cleaned
    }

    companion object {
        /**
         * Turns a provider's error into one the app shows.
         *
         * The HTTP status says what kind of problem it is: 401 and 403 mean
         * the key is wrong, 404 means the model is not there, 429 means the
         * quota is spent, and 5xx means the provider is having trouble.
         */
        fun classify(status: Int, message: String, type: String = ""): Pair<ErrorKind, String> {
            val text = type.lowercase()
            return when {
                status == 401 || status == 403 ->
                    ErrorKind.InvalidInput to "The API key was refused. Check the key and try again."
                status == 404 || text.contains("model") && text.contains("not") ->
                    ErrorKind.InvalidInput to "The provider does not have that model. Check the model name."
                status == 429 || text.contains("rate") || text.contains("quota") ->
                    ErrorKind.Overflow to "The quota is spent or the request was made too quickly. Try again later."
                status == 413 ->
                    ErrorKind.Overflow to "The image is too large for this provider."
                status >= 500 ->
                    ErrorKind.Unavailable to "${message.ifBlank { "The provider is having trouble." }}"
                status == 400 && text.contains("image") ->
                    ErrorKind.InvalidInput to
                        "This model cannot read images. Choose a model that supports pictures."
                else ->
                    ErrorKind.InvalidInput to message.ifBlank { "The provider rejected the request." }
            }
        }
    }
}

/** One HTTP request. */
data class AiRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String,
)

/** A failure while talking to the provider. */
class AiTransportException(
    val kind: ErrorKind,
    override val message: String,
) : Exception(message)

/**
 * Sends a request.
 *
 * The tests replace this, so no request is made in them.
 */
interface AiTransport {
    /** Sends the request and returns the body of the reply. */
    suspend fun send(request: AiRequest): String
}

/**
 * The real transport, over `HttpURLConnection`.
 *
 * The app has no networking library and does not need one: the request is a
 * single POST with a JSON body and a JSON reply.
 */
class HttpTransport : AiTransport {
    override suspend fun send(request: AiRequest): String = withContext(Dispatchers.IO) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = 60_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        // When the request is cancelled, the connection is closed, so that
        // the wait for the answer ends at once.
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause: Throwable? ->
            connection.disconnect()
        }
        try {
            connection.outputStream.use { it.write(request.body.toByteArray()) }
            val status = connection.responseCode
            val stream: InputStream? =
                if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { readAll(it) } ?: ""
            if (status !in 200..299) {
                val message = extractMessage(text)
                val (kind, shown) = AiClient.classify(status, message)
                throw AiTransportException(kind, shown)
            }
            text
        } catch (e: AiTransportException) {
            throw e
        } catch (e: UnknownHostException) {
            throw AiTransportException(ErrorKind.Unavailable, "There is no connection to ${request.url}.")
        } catch (e: SocketTimeoutException) {
            throw AiTransportException(ErrorKind.Unavailable, "The provider did not answer in time.")
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                throw AiTransportException(ErrorKind.Unavailable, "The provider could not be reached.")
            }
            throw e
        } finally {
            handle?.dispose()
            connection.disconnect()
        }
    }

    private fun readAll(stream: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = stream.read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    /** The message inside an error body, when there is one. */
    private fun extractMessage(text: String): String = try {
        val root = JSONObject(text)
        root.optJSONObject("error")?.optString("message")
            ?: root.optString("message")
    } catch (e: Exception) {
        ""
    }
}
