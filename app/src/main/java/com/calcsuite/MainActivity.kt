package com.calcsuite

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.calcsuite.data.Settings
import com.calcsuite.ui.CalcSuiteApp
import com.calcsuite.ui.LocalApp
import com.calcsuite.ui.theme.AmoledBackground
import com.calcsuite.ui.theme.applyAppNightMode
import com.calcsuite.ui.theme.DarkBackground
import com.calcsuite.ui.theme.LightBackground
import com.calcsuite.ui.theme.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Portrait only, for every destination: the welcome pages, the main menu,
        // search, settings, every tool and every fullscreen calculator. The
        // manifest declares it too; setting it here as well means a stray
        // requestedOrientation from a dialog or library cannot unlock landscape.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val container = (application as CalculatorApp).container

        // Set the window background synchronously before the first frame to
        // avoid a white (or dark-grey, for AMOLED) flash. Read the saved theme
        // once with a short bound: the stored AMOLED theme must paint black
        // even while the system is in light mode. A slow or failing store
        // falls back to the system preference.
        val savedMode = try {
            runBlocking {
                withTimeoutOrNull(300L) {
                    container.settings.settings.map { it.themeMode }.first()
                }
            } ?: ThemeMode.System
        } catch (e: Exception) {
            ThemeMode.System
        }

        // Apply the saved choice at the app level once too: the system draws
        // the starting window before the process exists, so this makes the
        // *next* cold start's splash dark for Dark/Amoled (and day for Light),
        // whatever the device setting is. The platform can recreate the
        // Activity once when the night mode changes; the guard, together with
        // the helper's unchanged-value check, keeps that from repeating.
        if (!nightModeApplied) {
            nightModeApplied = true
            applyAppNightMode(this, savedMode)
        }

        val initialDark = when (savedMode) {
            ThemeMode.System -> systemIsDark(resources)
            ThemeMode.Light -> false
            ThemeMode.Dark, ThemeMode.Amoled -> true
        }
        val initialAmoled = savedMode == ThemeMode.Amoled
        window.setBackgroundDrawable(ColorDrawable(windowBackgroundArgb(initialDark, initialAmoled)))

        // Centralised system-bar appearance + window background. These are derived
        // from the chosen theme mode so the OS shells match the app theme, and the
        // system bars are kept hidden everywhere in the app.
        lifecycleScope.launch {
            container.settings.settings.map { it.themeMode }.distinctUntilChanged().collect { mode ->
                val dark: Boolean = when (mode) {
                    ThemeMode.System -> systemIsDark(resources)
                    ThemeMode.Light -> false
                    ThemeMode.Dark, ThemeMode.Amoled -> true
                }
                window.setBackgroundDrawable(
                    ColorDrawable(windowBackgroundArgb(dark, mode == ThemeMode.Amoled)),
                )
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        setContent {
            CompositionLocalProvider(LocalApp provides container) {
                // The theme is applied inside CalcSuiteApp, so the opaque
                // background is provided there too. Putting a Surface here
                // would read MaterialTheme *outside* CalcSuiteTheme and would
                // therefore always use the default (light) baseline scheme,
                // showing through wherever a screen is transparent.
                CalcSuiteApp(container = container, initialSettings = Settings.DEFAULT)
            }
        }
    }

    companion object {
        /** One app-level night-mode write per process, even across a recreate. */
        private var nightModeApplied = false
    }
}

/** The system's current light/dark preference. */
private fun systemIsDark(resources: Resources): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/** The opaque window colour, shared by the first frame and the theme flow. */
private fun windowBackgroundArgb(dark: Boolean, amoled: Boolean): Int {
    val colour = when {
        amoled -> AmoledBackground
        dark -> DarkBackground
        else -> LightBackground
    }
    return colour.toArgb()
}
