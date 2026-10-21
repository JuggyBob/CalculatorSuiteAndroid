package com.calcsuite.ai.tool

/**
 * The mathematical input that is, or is to be, worked out.
 *
 * This is the one authoritative description of the problem: the tool that
 * will solve it, and the arguments it will be given, with the tool's own
 * defaults already filled in. The screen shows it and the engine runs it,
 * so the two can never disagree.
 *
 * @param toolId the tool that will be run.
 * @param arguments the arguments it will be given, by name.
 */
data class AiToolInput(
    val toolId: String,
    val arguments: Map<String, String>,
) {
    /**
     * The arguments that are worth showing.
     *
     * The arguments that the tool would have used anyway — the ones that
     * were not given and fall back to their default — are left out, so that
     * the problem is shown as the user gave it and not padded with the
     * tool's own settings.
     */
    fun meaningfulArguments(spec: AiToolSpec): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (argument in spec.arguments) {
            val value = arguments[argument.name].orEmpty()
            if (value.isBlank()) continue
            if (!argument.required && argument.defaultText == value) continue
            out.add(argument.name to value)
        }
        return out
    }
}
