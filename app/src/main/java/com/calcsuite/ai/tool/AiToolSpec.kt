package com.calcsuite.ai.tool

/**
 * One argument a tool needs.
 *
 * The kinds are the ones the model is asked to produce, and they are checked
 * again before the engine is called.
 */
enum class AiArgumentKind {
    /** An expression in the tool's own grammar. */
    EXPRESSION,

    /** A plain number. */
    NUMBER,

    /** A whole number. */
    INTEGER,

    /** The name of a variable, such as `x`. */
    VARIABLE,
}

/**
 * One argument of a tool.
 *
 * @param name the key the model uses.
 * @param kind what the argument holds.
 * @param description what the argument means, shown to the model.
 * @param required true when the tool cannot run without it.
 * @param defaultText the value used when the model leaves it out.
 */
data class AiArgument(
    val name: String,
    val kind: AiArgumentKind,
    val description: String,
    val required: Boolean = true,
    val defaultText: String? = null,
)

/**
 * The mathematics a tool does.
 *
 * @param domain the area of mathematics the tool belongs to.
 */
enum class AiToolDomain(val label: String) {
    CALCULUS("Calculus"),
    ALGEBRA("Algebra"),
    ADVANCED("Advanced mathematics"),
}

/**
 * What a tool can be asked to do.
 *
 * @param id the stable name the model uses to choose the tool.
 * @param title the name shown to the user.
 * @param domain the area of mathematics.
 * @param description when the tool should be used.
 * @param arguments what the tool needs.
 * @param grammar the syntax the tool's own parser accepts.
 * @param limitations what the tool will not do.
 * @param examples inputs the tool accepts.
 */
data class AiToolSpec(
    val id: String,
    val title: String,
    val domain: AiToolDomain,
    val description: String,
    val arguments: List<AiArgument>,
    val grammar: String,
    val limitations: String,
    val examples: List<String>,
) {
    /** The names of the arguments the model must give. */
    val requiredArguments: List<String> get() = arguments.filter { it.required }.map { it.name }
}
