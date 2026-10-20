package com.calcsuite.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the accent colour: the presets, the hexadecimal values and the
 * colour scheme each accent produces.
 */
class AccentTest {

    @Test fun theDefaultAccentIsTheGreenTheAppHasAlwaysUsed() {
        assertEquals(AccentPreset.GREEN, Accent.DEFAULT.preset)
        assertEquals(AccentPreset.GREEN.colour, Accent.DEFAULT.colour)
        assertTrue("the default accent is not a custom one", !Accent.DEFAULT.isCustom)
    }

    @Test fun aPresetIsReadBackFromItsId() {
        val accent = Accent.decode("blue")
        assertEquals(AccentPreset.BLUE, accent.preset)
        assertEquals(AccentPreset.BLUE.colour, accent.colour)
        assertTrue("a preset is not a custom colour", !accent.isCustom)
    }

    @Test fun aCustomColourIsReadBackFromItsHexadecimalValue() {
        val accent = Accent.decode("#3D7FD6")
        assertTrue("the colour should be custom", accent.isCustom)
        assertEquals(61.0 / 255.0, accent.colour.red.toDouble(), 1e-6)
        assertEquals(127.0 / 255.0, accent.colour.green.toDouble(), 1e-6)
        assertEquals(214.0 / 255.0, accent.colour.blue.toDouble(), 1e-6)
    }

    @Test fun aDamagedSettingFallsBackToTheDefaultAccent() {
        assertEquals(Accent.DEFAULT.colour, Accent.decode("not a colour").colour)
        assertEquals(Accent.DEFAULT.colour, Accent.decode(null).colour)
        assertEquals(Accent.DEFAULT.colour, Accent.decode("").colour)
    }

    @Test fun theHexadecimalFormsAreRead() {
        for (text in listOf("#FF0000", "FF0000", "#F00")) {
            val colour = parseColour(text)
            assertNotNull("$text should be read", colour)
            assertEquals("$text should be red", 1.0, colour!!.red.toDouble(), 1e-6)
            assertEquals("$text should be green", 0.0, colour.green.toDouble(), 1e-6)
            assertEquals("$text should be blue", 0.0, colour.blue.toDouble(), 1e-6)
        }
        // The form with the alpha first.
        val translucent = parseColour("#80FF0000")
        assertNotNull(translucent)
        assertEquals(128.0 / 255.0, translucent!!.alpha.toDouble(), 1e-6)
    }

    @Test fun aMalformedColourIsRefused() {
        assertNull(parseColour("#GG0000"))
        assertNull(parseColour("#12345"))
        assertNull(parseColour(""))
        assertNull(parseColour("hello"))
    }

    @Test fun aColourSurvivesTheRoundTrip() {
        for (colour in listOf(
            Color(0xFF1B6B4B),
            Color(0xFF3D7FD6),
            Color(0xFF8A2BE2),
        )) {
            val text = colourToHex(colour)
            val back = parseColour(text)
            assertNotNull("the colour $text should be read back", back)
            assertEquals(colour.red.toDouble(), back!!.red.toDouble(), 1e-6)
            assertEquals(colour.green.toDouble(), back.green.toDouble(), 1e-6)
            assertEquals(colour.blue.toDouble(), back.blue.toDouble(), 1e-6)
        }
    }

    @Test fun theAccentChangesTheAccentFamily() {
        val scheme = accentScheme(baseScheme(dark = true), Color(0xFF3D7FD6), dark = true)
        // The accent family follows the colour: the primary has the same hue
        // as the accent, adjusted so that it can be read.
        val accentHsl = Color(0xFF3D7FD6).toHsl()
        val primaryHsl = scheme.primary.toHsl()
        assertEquals("the hue should not change", accentHsl.hue.toDouble(), primaryHsl.hue.toDouble(), 0.02)
        // The error colours keep their own red, because an error should
        // always look like an error.
        assertEquals(baseScheme(dark = true).error, scheme.error)
        assertEquals(baseScheme(dark = true).onError, scheme.onError)
    }

    @Test fun theSurfacesFollowTheAccentToo() {
        // The bug this fixes: a pink accent on green cards. Every colour that
        // carries a tint must move to the accent's hue, so nothing is left
        // in the old colour.
        val accent = Color(0xFF9B2F5B)
        val accentHue = accent.toHsl().hue
        val greenHue = AccentPreset.GREEN.colour.toHsl().hue
        for (dark in listOf(true, false)) {
            val scheme = accentScheme(baseScheme(dark = dark), accent, dark = dark)
            for ((name, colour) in listOf(
                "background" to scheme.background,
                "surface" to scheme.surface,
                "surfaceVariant" to scheme.surfaceVariant,
                "surfaceContainer" to scheme.surfaceContainer,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                "surfaceContainerLow" to scheme.surfaceContainerLow,
                "outline" to scheme.outline,
                "secondaryContainer" to scheme.secondaryContainer,
            )) {
                val hsl = colour.toHsl()
                val toAccent = kotlin.math.abs(hsl.hue - accentHue)
                val toGreen = kotlin.math.abs(hsl.hue - greenHue)
                assertTrue(
                    "the $name must take the accent's hue in the " +
                        "${if (dark) "dark" else "light"} theme, but it is at " +
                        "${hsl.hue} rather than near $accentHue",
                    toAccent < toGreen,
                )
            }
        }
    }

    @Test fun theTextKeepsItsContrastWhateverTheAccent() {
        // The accent decides the hue, never the lightness, so the contrast
        // between text and background is the same for every colour.
        for (accent in AccentPreset.entries.map { it.colour } + listOf(Color(0xFF3D7FD6))) {
            for (dark in listOf(true, false)) {
                val scheme = accentScheme(baseScheme(dark = dark), accent, dark = dark)
                val base = baseScheme(dark = dark)
                assertEquals(
                    "the text must be as light as it was",
                    base.onSurface.toHsl().lightness,
                    scheme.onSurface.toHsl().lightness,
                    0.001f,
                )
                assertEquals(
                    "the background must be as light as it was",
                    base.background.toHsl().lightness,
                    scheme.background.toHsl().lightness,
                    0.001f,
                )
            }
        }
    }

    @Test fun theGreenAccentLeavesTheAppThemeExactlyAsItWas() {
        // A user who has not chosen an accent must see the app they know.
        for (dark in listOf(true, false)) {
            val scheme = accentScheme(baseScheme(dark = dark), AccentPreset.GREEN.colour, dark = dark)
            val base = baseScheme(dark = dark)
            assertEquals("the background must be the app's own", base.background, scheme.background)
            assertEquals("the surface must be the app's own", base.surface, scheme.surface)
            assertEquals("the text must be the app's own", base.onSurface, scheme.onSurface)
            assertEquals("the outline must be the app's own", base.outline, scheme.outline)
            assertEquals(
                "the surface containers must be the app's own",
                base.surfaceContainer,
                scheme.surfaceContainer,
            )
        }
    }

    @Test fun theAccentIsReadableInBothThemes() {
        for (preset in AccentPreset.entries) {
            val dark = accentScheme(baseScheme(dark = true), preset.colour, dark = true)
            val light = accentScheme(baseScheme(dark = false), preset.colour, dark = false)
            // The primary has to be light on the dark background and deep on
            // the light one, or the numbers could not be read.
            assertTrue(
                "${preset.label} is too dark for the dark theme",
                dark.primary.luminance() > dark.background.luminance(),
            )
            assertTrue(
                "${preset.label} is too light for the light theme",
                light.primary.luminance() < light.background.luminance(),
            )
        }
    }

    @Test fun theAccentKeepsItsHue() {
        // Whatever colour the user picks, the scheme has to be the same hue,
        // only lighter or deeper.
        val accent = Color(0xFF8A2BE2)
        val hsl = accent.toHsl()
        val dark = accentScheme(baseScheme(dark = true), accent, dark = true)
        val darkHsl = dark.primary.toHsl()
        assertEquals("the hue should not change", hsl.hue.toDouble(), darkHsl.hue.toDouble(), 0.02)
    }
}

/** The relative brightness of a colour. */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
