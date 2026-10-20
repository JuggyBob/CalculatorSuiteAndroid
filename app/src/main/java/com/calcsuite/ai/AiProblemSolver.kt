package com.calcsuite.ai

import com.calcsuite.ai.tool.AiInterpretation
import com.calcsuite.ai.tool.AiInterpretationStatus
import com.calcsuite.ai.tool.AiInterpreter
import com.calcsuite.ai.tool.AiToolExecutor
import com.calcsuite.ai.tool.AiToolOutcome
import com.calcsuite.core.CalcResult
import com.calcsuite.core.err

/**
 * The way a problem in a picture becomes an answer.
 *
 * The steps are the ones the app always takes:
 *
 *  1. the picture is sent to the provider with the instructions and the
 *     list of the tools that may be chosen;
 *  2. the reply is read, and the tool it names is checked against the
 *     registry;
 *  3. the arguments are checked against that tool's own list;
 *  4. the engine does the mathematics.
 *
 * Nothing here guesses: a reply that cannot be read, or that names a tool
 * that does not exist, is refused rather than worked around.
 *
 * The transport can be replaced, so the whole way through can be checked
 * without a key and without a request leaving the phone.
 */
class AiProblemSolver(
    private val transport: AiTransport = HttpTransport(),
) {

    /**
     * Asks the provider to read the picture.
     *
     * The key is used to make the request and is not kept afterwards.
     */
    suspend fun interpret(
        provider: AiProvider,
        model: String,
        apiKey: String,
        image: AiImage,
    ): CalcResult<AiInterpretation> {
        if (apiKey.isBlank()) {
            return err(
                com.calcsuite.core.ErrorKind.InvalidInput,
                "No API key has been entered for ${provider.label}.",
            )
        }
        if (model.isBlank()) {
            return err(
                com.calcsuite.core.ErrorKind.InvalidInput,
                "No model has been entered for ${provider.label}.",
            )
        }
        val client = AiClient(provider, apiKey, model, transport)
        val reply = client.ask(
            AiPrompt(
                system = AiInterpreter.instructions(),
                user = USER_QUESTION,
                image = image,
            ),
        )
        return when (reply) {
            is CalcResult.Err -> reply
            is CalcResult.Ok -> AiInterpreter.read(reply.value.text)
        }
    }

    /**
     * Works the problem out with the tool that was chosen.
     *
     * A reading that is not ready is not worked out, because the problem is
     * not known well enough to be sure of the answer.
     */
    fun solve(interpretation: AiInterpretation): CalcResult<AiToolOutcome> {
        if (interpretation.status != AiInterpretationStatus.READY) {
            return err(
                com.calcsuite.core.ErrorKind.InvalidInput,
                when (interpretation.status) {
                    AiInterpretationStatus.AMBIGUOUS ->
                        "The problem is not clear enough to be worked out."
                    AiInterpretationStatus.UNSUPPORTED ->
                        "No Calculator Suite tool can work this problem out."
                    else -> "The problem was not read."
                },
            )
        }
        val toolId = interpretation.toolId
            ?: return err(
                com.calcsuite.core.ErrorKind.InvalidInput,
                "The model did not choose a tool.",
            )
        return AiToolExecutor.run(toolId, interpretation.arguments)
    }

    private companion object {
        /** The question that is put with the picture. */
        const val USER_QUESTION = "Read the mathematical problem in this picture."
    }
}
