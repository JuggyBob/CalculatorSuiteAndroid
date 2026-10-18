package com.calcsuite.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.calcsuite.core.Num
import com.calcsuite.core.ThousandsSeparator
import com.calcsuite.expr.AngleMode
import com.calcsuite.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single DataStore shared by all repositories (one instance per process). */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "calcsuite")

/** User-visible application settings. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val angleMode: AngleMode = AngleMode.DEG,
    val precision: Int = Num.DEFAULT_PRECISION,
    val grouping: Boolean = true,
    val thousandsSeparator: ThousandsSeparator = ThousandsSeparator.DEFAULT,
    val haptics: Boolean = true,
    val dynamicColor: Boolean = false,
    /** Set once the welcome pages have been completed or skipped. */
    val onboardingComplete: Boolean = false,
) {
    companion object {
        val DEFAULT = Settings()
    }
}

class SettingsRepository(context: Context) {

    private val store = context.appDataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            angleMode = prefs[KEY_ANGLE]?.let { runCatching { AngleMode.valueOf(it) }.getOrNull() }
                ?: AngleMode.DEG,
            precision = Num.clampPrecision(prefs[KEY_PRECISION] ?: Num.DEFAULT_PRECISION),
            grouping = prefs[KEY_GROUPING] ?: true,
            thousandsSeparator = ThousandsSeparator.fromName(prefs[KEY_THOUSANDS]),
            haptics = prefs[KEY_HAPTICS] ?: true,
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            onboardingComplete = prefs[KEY_ONBOARDING] ?: false,
        )
    }

    val favorites: Flow<Set<String>> = store.data.map { it[KEY_FAVORITES] ?: emptySet() }

    val recents: Flow<List<String>> = store.data.map { prefs ->
        prefs[KEY_RECENTS]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME] = mode.name }
    suspend fun setAngleMode(mode: AngleMode) = store.edit { it[KEY_ANGLE] = mode.name }
    suspend fun setPrecision(value: Int) = store.edit { it[KEY_PRECISION] = Num.clampPrecision(value) }
    suspend fun setGrouping(value: Boolean) = store.edit { it[KEY_GROUPING] = value }
    suspend fun setThousandsSeparator(value: ThousandsSeparator) =
        store.edit { it[KEY_THOUSANDS] = value.name }

    suspend fun setHaptics(value: Boolean) = store.edit { it[KEY_HAPTICS] = value }
    suspend fun setDynamicColor(value: Boolean) = store.edit { it[KEY_DYNAMIC] = value }

    /** Called when onboarding is finished or skipped, so it never shows again. */
    suspend fun setOnboardingComplete(value: Boolean = true) =
        store.edit { it[KEY_ONBOARDING] = value }

    suspend fun toggleFavorite(toolId: String) = store.edit { prefs ->
        val current = prefs[KEY_FAVORITES] ?: emptySet()
        prefs[KEY_FAVORITES] = if (toolId in current) current - toolId else current + toolId
    }

    /** Adds a tool to the front of the recent list (most recent first, max 8). */
    suspend fun markRecent(toolId: String) = store.edit { prefs ->
        val current = prefs[KEY_RECENTS]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        val updated = (listOf(toolId) + current.filter { it != toolId }).take(8)
        prefs[KEY_RECENTS] = updated.joinToString("|")
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_ANGLE = stringPreferencesKey("angle_mode")
        val KEY_PRECISION = intPreferencesKey("precision")
        val KEY_GROUPING = booleanPreferencesKey("grouping")
        val KEY_THOUSANDS = stringPreferencesKey("thousands_separator")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        val KEY_RECENTS = stringPreferencesKey("recents")
    }
}
