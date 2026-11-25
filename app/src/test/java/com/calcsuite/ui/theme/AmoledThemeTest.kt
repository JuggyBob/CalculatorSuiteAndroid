package com.calcsuite.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the true-black AMOLED scheme and its interaction with accents. */
class AmoledThemeTest {

    @Test fun amoledBackgroundAndSurfaceAreTrueBlack() {
        val scheme = baseScheme(dark = true, amoled = true)
        assertEquals(Color(0xFF000000), scheme.background)
        assertEquals(Color(0xFF000000), scheme.surface)
        // The existing schemes must not have changed.
        assertEquals(Color(0xFF0F1512), baseScheme(dark = true).background)
        assertEquals(Color(0xFFF6FAF7), baseScheme(dark = false).background)
    }

    @Test fun amoledContainersAreDistinctAndNotPureBlack() {
        for (scheme in listOf(
            baseScheme(dark = true, amoled = true),
            accentScheme(baseScheme(dark = true, amoled = true), Color(0xFF3D7FD6), dark = true),
        )) {
            val containers = listOf(
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
            )
            assertEquals("container tones must be distinct", containers.size, containers.toSet().size)
            for (colour in containers) {
                assertTrue("a container must not be pure black", colour != Color(0xFF000000))
            }
            for (i in 1 until containers.size) {
                assertTrue(
                    "container tone $i must be lighter than the one below it",
                    containers[i].toHsl().lightness > containers[i - 1].toHsl().lightness,
                )
            }
        }
    }

    @Test fun amoledOutlinesStayDistinguishableFromBlackAndEachOther() {
        val scheme = baseScheme(dark = true, amoled = true)
        assertTrue("outlines must differ", scheme.outline != scheme.outlineVariant)
        assertTrue(
            "the divider must be visible above black",
            relativeLuminance(scheme.outlineVariant) > relativeLuminance(scheme.surface),
        )
        assertTrue(
            "the outline must be brighter than the divider",
            relativeLuminance(scheme.outline) > relativeLuminance(scheme.outlineVariant),
        )
    }

    @Test fun themeModeParsesAmoledFromItsStoredName() {
        assertEquals(ThemeMode.Amoled, ThemeMode.valueOf("Amoled"))
        assertEquals("Amoled", ThemeMode.Amoled.name)
    }

    @Test fun anAccentKeepsTheAmoledBackgroundBlackAndChangesThePrimary() {
        val base = baseScheme(dark = true, amoled = true)
        val scheme = accentScheme(base, Color(0xFF3D7FD6), dark = true)
        assertEquals("black is achromatic and must be skipped", Color(0xFF000000), scheme.background)
        assertEquals(Color(0xFF000000), scheme.surface)
        assertTrue("the primary must follow the accent", scheme.primary != base.primary)
    }

    @Test fun theGreenAccentLeavesTheAmoledTextAndSurfacesAsTheBase() {
        val base = baseScheme(dark = true, amoled = true)
        val scheme = accentScheme(base, AccentPreset.GREEN.colour, dark = true)
        assertEquals(base.background, scheme.background)
        assertEquals(base.surface, scheme.surface)
        assertEquals(base.onBackground, scheme.onBackground)
        assertEquals(base.onSurface, scheme.onSurface)
    }

    @Test fun amoledTextIsReadableOnBlackAndOnTheRaisedContainers() {
        val base = baseScheme(dark = true, amoled = true)
        for (accent in AccentPreset.entries.map { it.colour }) {
            val scheme = accentScheme(base, accent, dark = true)
            assertTrue(
                "onBackground must be readable on the black background",
                contrastRatio(scheme.onBackground, scheme.background) >= 4.5,
            )
            assertTrue(
                "onSurface must be readable on the black surface",
                contrastRatio(scheme.onSurface, scheme.surface) >= 4.5,
            )
            assertTrue(
                "onSurface must be readable on the highest container",
                contrastRatio(scheme.onSurface, scheme.surfaceContainerHighest) >= 4.5,
            )
        }
    }
}

/** WCAG relative luminance. */
private fun relativeLuminance(colour: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(colour.red) + 0.7152 * channel(colour.green) +
        0.0722 * channel(colour.blue)
}

/** WCAG contrast ratio between two colours. */
private fun contrastRatio(a: Color, b: Color): Double {
    val light = maxOf(relativeLuminance(a), relativeLuminance(b))
    val dark = minOf(relativeLuminance(a), relativeLuminance(b))
    return (light + 0.05) / (dark + 0.05)
}
