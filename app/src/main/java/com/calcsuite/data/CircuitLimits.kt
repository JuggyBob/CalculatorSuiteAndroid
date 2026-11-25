package com.calcsuite.data

/** Pure bounds for the circuits kept per tool. */
object CircuitLimits {
    /** Tool ids are short, lower-case names such as "other.electrical_circuits". */
    private val TOOL_ID = Regex("^[a-z0-9_.]{1,64}$")

    /** Upper bound for one encoded circuit, in characters. */
    const val MAX_ENCODED_LENGTH = 200_000

    fun isValidToolId(toolId: String): Boolean = TOOL_ID.matches(toolId)

    fun encodedWithinLimit(encoded: String): Boolean = encoded.length <= MAX_ENCODED_LENGTH

    /** The DataStore key under which a tool's circuit is kept. */
    fun circuitKey(toolId: String): String = "circuit_$toolId"
}
