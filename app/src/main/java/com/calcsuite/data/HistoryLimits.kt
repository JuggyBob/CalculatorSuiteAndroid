package com.calcsuite.data

/**
 * Pure bounds and sanitising helpers for persisted history.
 *
 * History is kept unencrypted in DataStore, so a corrupt or hostile value in
 * that file must not be allowed to grow without limit or crash the app while
 * it is being read. Every helper here leaves an ordinary value untouched.
 */
object HistoryLimits {
    /** Oldest entries are dropped first once this many have been kept. */
    const val MAX_ENTRIES = 500

    /** Upper bound for the whole stored JSON document (about 2 MB). */
    const val MAX_STORED_JSON_LENGTH = 2_000_000

    /** Upper bound for an entry's input summary and result text. */
    const val MAX_TEXT_LENGTH = 20_000

    /** Upper bound for the number of named values kept with one entry. */
    const val MAX_VALUES_PER_ENTRY = 50

    /** Upper bound for the length of one named value. */
    const val MAX_VALUE_LENGTH = 20_000

    /** True when a stored document is small enough to parse. */
    fun jsonWithinLimit(json: String): Boolean = json.length <= MAX_STORED_JSON_LENGTH

    /** Truncates oversized text; ordinary text is returned unchanged. */
    fun limitText(value: String): String = value.limitTo(MAX_TEXT_LENGTH)

    /** Truncates an oversized named value; ordinary values are unchanged. */
    fun limitValue(value: String): String = value.limitTo(MAX_VALUE_LENGTH)

    /** Keeps at most [MAX_VALUES_PER_ENTRY] entries, each within [MAX_VALUE_LENGTH]. */
    fun limitValues(values: Map<String, String>): Map<String, String> {
        if (values.size <= MAX_VALUES_PER_ENTRY &&
            values.values.all { it.length <= MAX_VALUE_LENGTH }
        ) {
            return values
        }
        val out = LinkedHashMap<String, String>(minOf(values.size, MAX_VALUES_PER_ENTRY))
        for ((name, value) in values) {
            if (out.size >= MAX_VALUES_PER_ENTRY) break
            out[name] = limitValue(value)
        }
        return out
    }

    /** Keeps only the first [MAX_ENTRIES] items (newest first in history). */
    fun <T> limitEntries(entries: List<T>): List<T> = entries.take(MAX_ENTRIES)

    private fun String.limitTo(max: Int): String = if (length <= max) this else take(max)
}
