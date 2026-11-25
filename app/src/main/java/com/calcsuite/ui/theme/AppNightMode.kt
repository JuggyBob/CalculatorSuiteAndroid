package com.calcsuite.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.jvm.Volatile

/**
 * The app-level night mode to request, without Android types so the mapping
 * from [ThemeMode] can be tested on the JVM.
 */
internal enum class AppNightMode { Auto, No, Yes }

/**
 * The last mode this process sent to the platform. UiModeManager has a setter
 * but no public getter, so idempotence is tracked here instead. Process-local
 * state: null until the first write, reset by a new process, and deliberately
 * kept across Activity recreations so a night-mode change cannot repeat.
 */
@Volatile
private var lastApplied: AppNightMode? = null

/** The pure mapping from the user's theme choice to the night mode to request. */
internal fun appNightModeFor(mode: ThemeMode): AppNightMode = when (mode) {
    ThemeMode.System -> AppNightMode.Auto
    ThemeMode.Light -> AppNightMode.No
    ThemeMode.Dark, ThemeMode.Amoled -> AppNightMode.Yes
}

/**
 * Makes the system's launch window follow the app's theme rather than only
 * the device's: on Android 12 (API 31) and above the app-level night mode
 * decides which `values-night` theme is used for the starting window, so a
 * saved Dark or Amoled theme is dark even while the system is in light mode.
 * Light stays day, and System follows the device again.
 *
 * Below API 31 `setApplicationNightMode` does not exist, so this is a no-op
 * and the launch window follows the system DayNight configuration as before.
 *
 * A change may recreate the hosting Activity once. Call this only when the
 * theme is chosen and once at startup — never on every recomposition.
 */
fun applyAppNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val target = appNightModeFor(mode)
    // Writing the same value again can trigger a needless configuration
    // change, so only a real change reaches the platform.
    if (lastApplied == target) return
    val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return
    // A platform failure must never stop the app from starting or changing a
    // setting; the in-app theme still applies. Only a successful write is
    // remembered, so a failure can be retried.
    runCatching { applyNightMode(manager, target) }
        .onSuccess { lastApplied = target }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun applyNightMode(manager: UiModeManager, choice: AppNightMode) {
    val platformMode = when (choice) {
        AppNightMode.Auto -> UiModeManager.MODE_NIGHT_AUTO
        AppNightMode.No -> UiModeManager.MODE_NIGHT_NO
        AppNightMode.Yes -> UiModeManager.MODE_NIGHT_YES
    }
    manager.setApplicationNightMode(platformMode)
}
