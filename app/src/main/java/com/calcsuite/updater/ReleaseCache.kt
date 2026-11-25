package com.calcsuite.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.calcsuite.data.appDataStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * Keeps the last release the app read, so that the About screen can show its
 * notes when there is no connection.
 */
class ReleaseCache(context: Context) {

    private val store = context.appDataStore

    /** Stores the release, so that it can be shown offline. */
    suspend fun save(release: ReleaseInfo) {
        store.edit { prefs ->
            prefs[KEY_JSON] = ReleaseParser.toJson(release)
            prefs[KEY_SAVED_AT] = System.currentTimeMillis()
        }
    }

    /** The release that was stored, or null when there is none. */
    suspend fun load(): ReleaseInfo? {
        val json = store.data.first()[KEY_JSON] ?: return null
        return ReleaseParser.parse(json)
    }

    /** When the stored release was read, in milliseconds since the epoch. */
    suspend fun savedAt(): Long? = store.data.first()[KEY_SAVED_AT]

    /** The release tags the person asked never to be shown again. */
    suspend fun suppressedTags(): Set<String> =
        store.data.first()[KEY_SUPPRESSED_TAGS] ?: emptySet()

    /** Remembers one release tag as suppressed, without touching the others. */
    suspend fun suppress(tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        store.edit { prefs ->
            prefs[KEY_SUPPRESSED_TAGS] = (prefs[KEY_SUPPRESSED_TAGS] ?: emptySet()) + clean
        }
    }

    /** When the last automatic check ran, in milliseconds since the epoch. */
    suspend fun lastAutoCheckAt(): Long? = store.data.first()[KEY_LAST_AUTO_CHECK]

    /** Records an automatic check, so a restart does not check again too soon. */
    suspend fun markAutoChecked(now: Long = System.currentTimeMillis()) {
        store.edit { prefs -> prefs[KEY_LAST_AUTO_CHECK] = now }
    }

    private companion object {
        val KEY_JSON = stringPreferencesKey("last_release")
        val KEY_SAVED_AT = longPreferencesKey("last_release_at")
        val KEY_SUPPRESSED_TAGS = stringSetPreferencesKey("suppressed_release_tags")
        val KEY_LAST_AUTO_CHECK = longPreferencesKey("last_auto_check")
    }
}

/**
 * The date a release was published, in the words of the device's calendar.
 *
 * Returns null when the date cannot be read, so that the screen simply leaves
 * the date out rather than showing something wrong.
 */
fun formatPublishedAt(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val instant = try {
        Instant.parse(text)
    } catch (e: Exception) {
        return null
    }
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}
