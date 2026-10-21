package com.calcsuite.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Keeps the circuit that was being worked on, so that it is still there when
 * the tool is opened again.
 *
 * The circuit is kept as the text written by the circuit's own codec, under
 * the name of the tool it belongs to, so that each tool can have its own
 * circuit without one overwriting another.
 */
class CircuitRepository(context: Context) {

    private val store = context.appDataStore

    /** The circuit saved for a tool, read back. */
    fun circuit(toolId: String): Flow<String?> = store.data.map { prefs ->
        prefs[circuitKey(toolId)]
    }

    /** Saves the circuit of a tool. */
    suspend fun save(toolId: String, encoded: String) = store.edit { prefs ->
        prefs[circuitKey(toolId)] = encoded
    }

    /** Forgets the circuit of a tool. */
    suspend fun clear(toolId: String) = store.edit { prefs ->
        prefs.remove(circuitKey(toolId))
    }

    private fun circuitKey(toolId: String) = stringPreferencesKey("circuit_$toolId")
}
