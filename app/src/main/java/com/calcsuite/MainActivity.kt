package com.calcsuite

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.calcsuite.data.Settings
import com.calcsuite.ui.CalcSuiteApp
import com.calcsuite.ui.LocalApp
import com.calcsuite.ui.theme.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

        // Set window background synchronously before first frame to avoid white flash.
        val initialDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawable(
            ColorDrawable(if (initialDark) Color.parseColor("#0F1512") else Color.parseColor("#F6FAF7")),
        )

        // Centralised system-bar appearance + window background. These are derived
        // from the chosen theme mode so the OS shells match the app theme, and the
        // system bars are kept hidden everywhere in the app.
        lifecycleScope.launch {
            container.settings.settings.map { it.themeMode }.distinctUntilChanged().collect { mode ->
                val dark: Boolean = when (mode) {
                    ThemeMode.System ->
                        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES
                    else -> mode.name.lowercase() == "dark"
                }
                window.setBackgroundDrawable(
                    ColorDrawable(if (dark) Color.parseColor("#0F1512") else Color.parseColor("#F6FAF7")),
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
}
