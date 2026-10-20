package com.calcsuite.engine.advanced

/**
 * The sections of an explanation.
 *
 * Every new tool builds its explanation from this model, so the tools read
 * the same way: what the thing is, the formula that is used and what its
 * symbols mean, the conditions the method needs, the working, what the
 * result means, how it was checked, and what it does not cover.
 *
 * The text is always taken from the calculation that actually ran, so the
 * explanation and the result cannot drift apart.
 */
data class Explanation(
    /** What the mathematical object or operation is. */
    val definition: String? = null,
    /** The formula being used. */
    val formula: String? = null,
    /** The symbols of the formula, and what they stand for. */
    val symbols: List<Pair<String, String>> = emptyList(),
    /** The assumptions the method needs. */
    val conditions: List<String> = emptyList(),
    /** Why the method applies here. */
    val method: String? = null,
    /** The working, step by step. */
    val steps: List<String> = emptyList(),
    /** What the result means. */
    val interpretation: String? = null,
    /** The independent check of the result. */
    val verification: String? = null,
    /** Whether the result is symbolic, numerical or illustrative, and what it does not cover. */
    val limitations: String? = null,
) {
    /** True when there is nothing to show. */
    val isEmpty: Boolean
        get() = definition == null && formula == null && conditions.isEmpty() &&
            method == null && steps.isEmpty() && interpretation == null &&
            verification == null && limitations == null
}
