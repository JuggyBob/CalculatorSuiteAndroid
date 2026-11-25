package com.calcsuite.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the pure part of the app night-mode helper.
 *
 * [AppNightMode] stands for the platform constants MODE_NIGHT_AUTO / _NO /
 * _YES; the translation to those Android symbols lives in the Android-only
 * function and is deliberately kept out of this test.
 */
class AppNightModeTest {

    @Test fun systemFollowsTheDeviceNightMode() {
        assertEquals(AppNightMode.Auto, appNightModeFor(ThemeMode.System))
    }

    @Test fun lightForcesTheDayTheme() {
        assertEquals(AppNightMode.No, appNightModeFor(ThemeMode.Light))
    }

    @Test fun darkAndAmoledForceTheNightTheme() {
        assertEquals(AppNightMode.Yes, appNightModeFor(ThemeMode.Dark))
        assertEquals(AppNightMode.Yes, appNightModeFor(ThemeMode.Amoled))
    }

    @Test fun everyThemeModeHasAMapping() {
        val mapped = ThemeMode.entries.map(::appNightModeFor)
        assertEquals(ThemeMode.entries.size, mapped.size)
        assertEquals(setOf(AppNightMode.Auto, AppNightMode.No, AppNightMode.Yes), mapped.toSet())
    }
}
