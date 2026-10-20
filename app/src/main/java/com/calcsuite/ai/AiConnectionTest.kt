package com.calcsuite.ai

import com.calcsuite.core.CalcResult

/**
 * Checks that a provider, a model and a key work together.
 *
 * The check is a single short message, so it costs almost nothing, and it
 * finds the three things that are usually wrong: the key, the model name and
 * the model itself.
 */
object AiConnectionTest {

    /**
     * Sends the check and says what happened.
     *
     * A null answer means the connection works; anything else is the
     * message to show the user.
     */
    suspend fun check(
        provider: AiProvider,
        model: String,
        apiKey: String,
        savedKey: String,
    ): String? {
        if (model.isBlank()) {
            return "Enter the name of the model first."
        }
        val key = apiKey.ifBlank { savedKey }
        if (key.isBlank()) {
            return "Enter the API key first."
        }
        val client = AiClient(provider, key, model)
        val reply = client.ask(
            AiPrompt(
                system = "You are a connection check. Reply with the single word: ready.",
                user = "Reply with the single word: ready.",
            ),
        )
        return when (reply) {
            is CalcResult.Err -> reply.error.message
            is CalcResult.Ok -> {
                val text = reply.value.text.trim()
                when {
                    text.contains("ready", ignoreCase = true) ->
                        "The connection to ${provider.label} works, and the model $model answered."
                    text.isEmpty() ->
                        "The provider accepted the request but sent nothing back."
                    else ->
                        "The provider answered, so the key and the model both work."
                }
            }
        }
    }
}
