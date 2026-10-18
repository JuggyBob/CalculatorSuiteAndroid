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
        val updated = (listOf(entry) + current).take(MAX_ENTRIES)
        prefs[KEY_HISTORY] = encode(updated)
    }

    suspend fun remove(id: Long) = store.edit { prefs ->
        val current = decode(prefs[KEY_HISTORY] ?: "")
        prefs[KEY_HISTORY] = encode(current.filterNot { it.id == id })
    }

    suspend fun clear() = store.edit { prefs -> prefs.remove(KEY_HISTORY) }

    companion object {
        const val MAX_ENTRIES = 500
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
                    },
                )
            }
            return array.toString()
        }

        fun decode(json: String): List<HistoryEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    HistoryEntry(
                        id = obj.optLong("id"),
                        toolId = obj.optString("toolId"),
                        toolTitle = obj.optString("toolTitle"),
                        category = obj.optString("category"),
                        input = obj.optString("input"),
                        result = obj.optString("result"),
                        timestamp = obj.optLong("timestamp"),
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
