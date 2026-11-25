package com.calcsuite.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** One recorded calculation. */
data class HistoryEntry(
    val id: Long,
    val toolId: String,
    val toolTitle: String,
    val category: String,
    /** Expression or a human-readable summary of the inputs. */
    val input: String,
    val result: String,
    val timestamp: Long,
    /**
     * The values the calculation was made from, by the name the tool's own
     * fields use.
     *
     * They are what lets a saved calculation open its tool with the problem
     * already in it. An entry saved before this was kept has none, and opens
     * the tool with that tool's own defaults.
     */
    val values: Map<String, String> = emptyMap(),
)

/**
 * Persists the calculation history as a JSON array inside DataStore so that it
 * survives restarts. Capped at [MAX_ENTRIES]; oldest entries are dropped first.
 */
class HistoryRepository(context: Context) {

    private val store = context.appDataStore

    val history: Flow<List<HistoryEntry>> = store.data.map { prefs ->
        decode(prefs[KEY_HISTORY] ?: "")
    }

    suspend fun add(entry: HistoryEntry) = store.edit { prefs ->
        val current = decode(prefs[KEY_HISTORY] ?: "")
        // Bound the new entry before it is written, so nothing oversized can
        // accumulate even if a tool passes a very long summary or result.
        val bounded = entry.copy(
            input = HistoryLimits.limitText(entry.input),
            result = HistoryLimits.limitText(entry.result),
            values = HistoryLimits.limitValues(entry.values),
        )
        val updated = HistoryLimits.limitEntries(listOf(bounded) + current)
        prefs[KEY_HISTORY] = encodeWithinLimit(updated)
    }

    /**
     * Records a result that was copied, so that it is in the history like any
     * other calculation.
     *
     * Only the text of the answer is kept: the picture the problem was read
     * from is never stored. A long answer is cut to the same [HistoryLimits]
     * bound as any other result, so one copy cannot grow the history forever.
     */
    suspend fun addCopy(text: String) = add(
        HistoryEntry(
            id = System.currentTimeMillis() * 1000 + (0..999).random(),
            toolId = "ai.solve",
            toolTitle = "AI problem solver",
            category = "Calculator",
            input = "Problem solved from a picture",
            result = HistoryLimits.limitText(text),
            timestamp = System.currentTimeMillis(),
        ),
    )

    suspend fun remove(id: Long) = store.edit { prefs ->
        val current = decode(prefs[KEY_HISTORY] ?: "")
        prefs[KEY_HISTORY] = encodeWithinLimit(current.filterNot { it.id == id })
    }

    suspend fun clear() = store.edit { prefs -> prefs.remove(KEY_HISTORY) }

    companion object {
        const val MAX_ENTRIES = HistoryLimits.MAX_ENTRIES
        private val KEY_HISTORY = stringPreferencesKey("history_v1")

        fun encode(entries: List<HistoryEntry>): String {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("toolId", e.toolId)
                        put("toolTitle", e.toolTitle)
                        put("category", e.category)
                        put("input", e.input)
                        put("result", e.result)
                        put("timestamp", e.timestamp)
                        // The values, so that the calculation can be opened
                        // again with the problem in it. An entry saved
                        // before this was kept has none.
                        put(
                            "values",
                            JSONObject().apply {
                                e.values.forEach { (name, value) -> put(name, value) }
                            },
                        )
                    },
                )
            }
            return array.toString()
        }

        /**
         * Encodes the newest entries first, dropping the oldest until the
         * whole document fits in [HistoryLimits.MAX_STORED_JSON_LENGTH].
         */
        private fun encodeWithinLimit(entries: List<HistoryEntry>): String {
            var bounded = HistoryLimits.limitEntries(entries)
            var json = encode(bounded)
            while (json.length > HistoryLimits.MAX_STORED_JSON_LENGTH && bounded.isNotEmpty()) {
                bounded = bounded.dropLast(1)
                json = encode(bounded)
            }
            return json
        }

        /**
         * The values of an entry, read back.
         *
         * An entry that was saved before the values were kept has none, and
         * opens its tool with that tool's own defaults.
         */
        private fun readValues(obj: JSONObject?): Map<String, String> {
            if (obj == null) return emptyMap()
            val out = LinkedHashMap<String, String>(HistoryLimits.MAX_VALUES_PER_ENTRY)
            val keys = obj.keys()
            while (keys.hasNext() && out.size < HistoryLimits.MAX_VALUES_PER_ENTRY) {
                val name = keys.next()
                val value = obj.opt(name)
                if (value is String && value.isNotBlank()) {
                    out[name] = HistoryLimits.limitValue(value)
                }
            }
            return out
        }

        fun decode(json: String): List<HistoryEntry> {
            if (json.isBlank()) return emptyList()
            if (!HistoryLimits.jsonWithinLimit(json)) return emptyList()
            return try {
                val array = JSONArray(json)
                // A hostile document may contain far more entries than the cap.
                val count = minOf(array.length(), HistoryLimits.MAX_ENTRIES)
                (0 until count).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    HistoryEntry(
                        id = obj.optLong("id"),
                        toolId = obj.optString("toolId"),
                        toolTitle = obj.optString("toolTitle"),
                        category = obj.optString("category"),
                        input = HistoryLimits.limitText(obj.optString("input")),
                        result = HistoryLimits.limitText(obj.optString("result")),
                        timestamp = obj.optLong("timestamp"),
                        values = readValues(obj.optJSONObject("values")),
                    )
                }
                // Deeply nested JSON can recurse until the stack is exhausted;
                // that is just as unreadable as malformed JSON.
            } catch (_: StackOverflowError) {
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
