package com.calcsuite.data

/**
 * Where the answer of an AI tool can be opened.
 *
 * @param toolId the id of the tool in the app's registry.
 * @param values the values the tool opens with, by the name the tool's own
 *   fields use.
 */
data class ToolDestination(
    val toolId: String,
    val values: Map<String, String> = emptyMap(),
)

/**
 * The link between the tools the AI knows about and the tools the app has.
 *
 * The AI names a tool by its own identifier, and those identifiers are not
 * always the identifiers the app's registry uses: the AI names the linear
 * equation `algebra.linear`, while the app has one Equations tool that
 * covers the linear, quadratic and system modes. The mapping lives here, in
 * one place, so that no screen has to know about it and so that the
 * identifier the AI gave is always resolved to a tool that really exists.
 */
object ToolDestinations {

    /**
     * The tool of the app that answers each AI tool.
     *
     * A tool that is not in the table is one whose identifier the app and
     * the AI share, and it opens itself.
     */
    private val AI_TOOL: Map<String, String> = mapOf(
        "calculus.derivative" to "calculus.derivatives",
        "calculus.integral" to "calculus.integrals",
        "calculus.limit" to "calculus.limits",
        "calculus.summation" to "calculus.summations",
        "calculus.ode" to "calculus.differential_equations",
        "calculus.partial" to "calculus.multivariable",
        "algebra.linear" to "algebra.equations",
        "algebra.quadratic" to "algebra.equations",
        "algebra.system" to "algebra.equations",
        "algebra.inequality" to "algebra.inequalities",
        "algebra.gcf" to "algebra.gcf_lcm",
        "algebra.lcm" to "algebra.gcf_lcm",
        "algebra.prime" to "algebra.primes",
        "algebra.combinatorics" to "algebra.combinations",
        "advanced.linear_algebra" to "advanced.functional",
        "advanced.complex" to "calculus.complex",
        "advanced.fourier" to "calculus.fourier",
        "advanced.pde" to "calculus.pde",
    )

    /**
     * The mode of the Equations tool that each of the AI's equation tools
     * opens.
     */
    private val EQUATION_MODE: Map<String, String> = mapOf(
        "algebra.linear" to "Linear",
        "algebra.quadratic" to "Quadratic",
        "algebra.system" to "System",
    )

    /**
     * The name each argument takes in the tool it opens.
     *
     * The AI's arguments are named after the tool the AI knows; the tools of
     * the app have their own names for the same thing.
     */
    private val ARGUMENT_NAMES: Map<String, Map<String, String>> = mapOf(
        "calculus.integral" to mapOf("lowerBound" to "lowerLimit", "upperBound" to "upperLimit"),
        "calculus.limit" to mapOf("point" to "atPoint"),
        "calculus.summation" to mapOf("from" to "seriesStart", "to" to "seriesEnd"),
        "calculus.series" to mapOf("firstTerm" to "seriesStart", "lastTerm" to "seriesEnd"),
        "calculus.partial" to mapOf(
            "expression" to "functionText",
            "variable" to "variablesText",
            "order" to "orderText",
        ),
        "calculus.ode" to mapOf("initialPoint" to "from"),
        "algebra.inequality" to mapOf("inequality" to "expression"),
        "advanced.linear_algebra" to mapOf(
            "firstVector" to "first",
            "secondVector" to "second",
        ),
        "advanced.measure" to mapOf(
            "firstSet" to "first",
            "secondSet" to "second",
        ),
        "advanced.complex" to mapOf("expression" to "functionText", "point" to "pointReal"),
        "advanced.fourier" to mapOf("expression" to "functionText"),
        "advanced.pde" to mapOf("initial" to "initialText"),
    )

    /**
     * Where the answer of an AI tool can be opened.
     *
     * @param aiToolId the identifier the AI gave the tool.
     * @param arguments the arguments the AI gave, as they were prepared for
     *   the engine.
     * @return the tool to open and the values to open it with, or null when
     *   the tool is one the app does not have.
     */
    fun forAiTool(aiToolId: String, arguments: Map<String, String>): ToolDestination? {
        val toolId = AI_TOOL[aiToolId] ?: aiToolId
        // A tool that the app does not have is not offered.
        if (!ToolRegistry.byId(toolId)?.id.equals(toolId, ignoreCase = true)) return null

        val names = ARGUMENT_NAMES[aiToolId] ?: emptyMap()
        val values = mutableMapOf<String, String>()
        for ((name, value) in arguments) {
            if (value.isBlank()) continue
            values[names[name] ?: name] = value
        }
        EQUATION_MODE[aiToolId]?.let { values["mode"] = it }

        // The differential equations tool wants its initial condition as one
        // expression, and the AI gives it as two numbers.
        if (aiToolId == "calculus.ode") {
            val from = arguments["initialPoint"]
            val initial = arguments["initialValue"]
            if (from != null && initial != null) {
                val variable = arguments["variable"] ?: "y"
                values["condition"] = "$variable($from) = $initial"
            }
        }

        return ToolDestination(toolId, values)
    }

    /**
     * True when the app has a tool with that identifier.
     */
    fun exists(toolId: String): Boolean = ToolRegistry.byId(toolId) != null
}
