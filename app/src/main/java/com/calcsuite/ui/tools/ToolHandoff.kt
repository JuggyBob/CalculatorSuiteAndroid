package com.calcsuite.ui.tools

/**
 * A problem that is being handed from one screen to another.
 *
 * The AI reads a problem and can open the tool that would solve it. The
 * tool needs the values in its own fields, so they are held here until the
 * tool screen asks for them.
 *
 * Only one problem is ever waiting: the last one handed over. A second
 * handover replaces the first, so a tool can never open with a problem that
 * belongs to an earlier request.
 */
class ToolHandoff {

    /** The problem that is waiting, and the tool it is for. */
    private data class Pending(val toolId: String, val values: Map<String, String>)

    private var pending: Pending? = null

    /**
     * Hands a problem to a tool.
     *
     * @param toolId the tool that will open.
     * @param values the values, by the name the tool registry uses.
     */
    fun handOver(toolId: String, values: Map<String, String>) {
        pending = Pending(toolId, values)
    }

    /**
     * Takes the problem for a tool, if one is waiting.
     *
     * The problem is taken once, so that a tool does not fill itself in again
     * when the screen is rebuilt, and a later visit to the same tool opens
     * with its own values.
     */
    fun take(toolId: String): Map<String, String> {
        val waiting = pending ?: return emptyMap()
        if (waiting.toolId != toolId) return emptyMap()
        pending = null
        return waiting.values
    }

    /** Throws away anything that is waiting. */
    fun clear() {
        pending = null
    }
}

/**
 * The value a tool should open with for one of its fields.
 *
 * The arguments are named as the tool registry names them, and a screen asks
 * for the one it wants, so that the two stay in step. The first name that
 * the waiting problem holds is the one that is used, which is what lets a
 * screen accept more than one spelling of the same field.
 */
fun ToolArgs.prefill(vararg names: String): String? =
    names.firstNotNullOfOrNull { name -> prefillValues[name] }
