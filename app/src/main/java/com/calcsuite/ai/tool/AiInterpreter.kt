package com.calcsuite.ai.tool

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import org.json.JSONObject

/**
 * What the model said about the problem in the picture.
 *
 * The statuses are the ones the app tells apart:
 *
 *  - [READY]: the problem was understood and the tool can be run.
 *  - [AMBIGUOUS]: the problem was seen but something is not clear, so the
 *    user is asked rather than the app guessing.
 *  - [UNSUPPORTED]: the problem is real but no tool in the registry can
 *    answer it.
 *  - [INVALID]: the reply could not be read.
 */
enum class AiInterpretationStatus {
    READY,
    AMBIGUOUS,
    UNSUPPORTED,
    INVALID,
}

/**
 * The model's reading of the problem.
 *
 * @param status what the model concluded.
 * @param recognizedProblem the problem in words, for the user to check.
 * @param toolId the tool the model chose, when it chose one.
 * @param arguments the arguments it produced.
 * @param clarification what is unclear, when something is.
 */
data class AiInterpretation(
    val status: AiInterpretationStatus,
    val recognizedProblem: String,
    val toolId: String? = null,
    val arguments: Map<String, String> = emptyMap(),
    val clarification: String? = null,
)

/**
 * Reads the model's reply.
 *
 * The reply is expected to be a JSON object with a status, a description of
 * the problem, the tool and its arguments. Anything that does not fit is
 * refused rather than guessed at, so a malformed reply never reaches an
 * engine.
 */
object AiInterpreter {

    /** The JSON schema the model is asked to answer with. */
    val SCHEMA: JSONObject = JSONObject(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["status", "recognizedProblem"],
          "properties": {
            "status": {
              "type": "string",
              "enum": ["ready", "ambiguous", "unsupported"],
              "description": "ready when the problem can be solved by one of the listed tools, ambiguous when something in the picture cannot be read with certainty, unsupported when no listed tool can answer it."
            },
            "recognizedProblem": {
              "type": "string",
              "description": "The mathematical problem as it was read, in words, so the user can check it."
            },
            "toolId": {
              "type": "string",
              "description": "The id of the tool to run. Only when the status is ready."
            },
            "arguments": {
              "type": "object",
              "description": "The arguments for the chosen tool, using the names in the tool's own list."
            },
            "clarification": {
              "type": "string",
              "description": "What is unclear or what is missing, when the status is ambiguous or unsupported."
            }
          }
        }
        """.trimIndent(),
    )

    /**
     * Reads the reply.
     *
     * A reply that is not JSON, or that has no status, is refused. The tool
     * is only accepted when it is one the registry holds, so a made-up tool
     * name never reaches an engine.
     */
    fun read(reply: String): CalcResult<AiInterpretation> {
        val text = reply.trim()
        // A reply that is wrapped in a code fence is still read.
        val cleaned = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val root = try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            return err(
                ErrorKind.Syntax,
                "The reply from the model could not be read.",
            )
        }

        val status = when (root.optString("status").lowercase()) {
            "ready" -> AiInterpretationStatus.READY
            "ambiguous" -> AiInterpretationStatus.AMBIGUOUS
            "unsupported" -> AiInterpretationStatus.UNSUPPORTED
            else -> return err(
                ErrorKind.Syntax,
                "The reply from the model did not say what to do.",
            )
        }

        val recognized = root.optString("recognizedProblem").trim()
        val clarification = root.optString("clarification").trim().ifEmpty { null }

        if (status != AiInterpretationStatus.READY) {
            return ok(
                AiInterpretation(
                    status = status,
                    recognizedProblem = recognized,
                    clarification = clarification,
                ),
            )
        }

        val toolId = root.optString("toolId").trim()
        if (toolId.isEmpty()) {
            return err(
                ErrorKind.Syntax,
                "The model did not choose a tool.",
            )
        }
        val spec = AiToolRegistry.byId(toolId)
            ?: return err(
                ErrorKind.InvalidInput,
                "The model chose a tool that does not exist: \"$toolId\".",
            )

        val arguments = mutableMapOf<String, String>()
        root.optJSONObject("arguments")?.let { given ->
            val keys = given.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = given.opt(key)
                arguments[key] = when {
                    value == null -> ""
                    value is String -> value
                    // A null in the reply is an empty value, not the word.
                    value === JSONObject.NULL -> ""
                    else -> value.toString()
                }
            }
        }

        return ok(
            AiInterpretation(
                status = AiInterpretationStatus.READY,
                recognizedProblem = recognized,
                toolId = toolId,
                arguments = arguments,
                clarification = clarification,
            ),
        )
    }

    /**
     * The instructions the model is given.
     *
     * The tools that may be chosen are described with their own grammar and
     * their own limits, because the model has to write arguments the engine
     * will accept. The list is the registry's, so a tool that is not in the
     * registry cannot be asked for.
     */
    fun instructions(): String = buildString {
        appendLine("You read a photograph of a mathematical problem and decide which ")
        appendLine("Calculator Suite tool should work it out.")
        appendLine()
        appendLine("Answer with one JSON object and nothing else. Use this shape:")
        appendLine(SCHEMA.toString())
        appendLine()
        appendLine("The tools you may choose from are below. Each one gives its own ")
        appendLine("arguments, the exact syntax its parser accepts, and what it cannot do.")
        appendLine()
        for (spec in AiToolRegistry.all) {
            appendLine("Tool id: ${spec.id}")
            appendLine("  What it does: ${spec.description}")
            appendLine("  Arguments:")
            for (argument in spec.arguments) {
                val required = if (argument.required) "required" else "optional"
                val fallback = argument.defaultText?.let { ", default $it" } ?: ""
                appendLine("    ${argument.name} ($required, ${argument.kind.name.lowercase()}): ${argument.description}$fallback")
            }
            appendLine("  Syntax: ${spec.grammar}")
            appendLine("  Limits: ${spec.limitations}")
            appendLine("  Examples: ${spec.examples.joinToString("; ")}")
            appendLine()
        }
        appendLine("Rules:")
        appendLine("  - Read the expression exactly as it is written. Keep the sign of a ")
        appendLine("    negative number, the denominator of a fraction and the base of a power.")
        appendLine("  - Use the syntax the tool's own parser accepts, not LaTeX and not ")
        appendLine("    words. Write x^2, not x²; 1/2, not \\frac{1}{2}.")
        appendLine("  - Put the variable in the variable argument, and the limits in the ")
        appendLine("    lower and upper arguments.")
        appendLine("  - If a symbol could be read in more than one way and the readings would ")
        appendLine("    give different answers, use the status ambiguous and say what is ")
        appendLine("    unclear. Do not choose a reading yourself.")
        appendLine("  - If no listed tool can work the problem out, use the status ")
        appendLine("    unsupported and say why.")
        appendLine("  - Never give an answer yourself. The tool you choose does the ")
        appendLine("    mathematics; you only read the problem and pick the tool.")
    }
}
