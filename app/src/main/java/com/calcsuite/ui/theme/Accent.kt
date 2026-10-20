package com.calcsuite.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * The accent colours the user can choose from.
 *
 * The first one is the colour the app has always used, and it is what a user
 * who has never touched the setting sees.
 */
enum class AccentPreset(
    val id: String,
    val label: String,
    /** The brand colour, before it is adjusted for light or dark. */
    val colour: Color,
) {
    GREEN("green", "Green", Color(0xFF1B6B4B)),
    BLUE("blue", "Blue", Color(0xFF1B5E8C)),
    TEAL("teal", "Teal", Color(0xFF0F6E6E)),
    PURPLE("purple", "Purple", Color(0xFF5B3A9B)),
    ROSE("rose", "Rose", Color(0xFF9B2F5B)),
    ORANGE("orange", "Orange", Color(0xFF9B5518)),
    SLATE("slate", "Slate", Color(0xFF445066));

    companion object {
        /** The accent a user who has not chosen one sees. */
        val DEFAULT = GREEN

        /** The preset with that id, or null. */
        fun byId(id: String): AccentPreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The accent the user has chosen: either one of the presets or a colour they
 * picked themselves.
 */
data class Accent(
    /** The preset, when one has been chosen. */
    val preset: AccentPreset? = AccentPreset.DEFAULT,
    /** The colour, when one has been picked by hand. */
    val custom: Color? = null,
) {
    /** The colour the accent stands for. */
    val colour: Color
        get() = custom ?: preset?.colour ?: AccentPreset.DEFAULT.colour

    /** True when the colour was picked by hand rather than taken from a preset. */
    val isCustom: Boolean get() = custom != null

    /**
     * The accent as it is stored: the id of a preset, or the colour in
     * hexadecimal form.
     */
    fun encode(): String = custom?.let { colourToHex(it) } ?: preset?.id ?: AccentPreset.DEFAULT.id

    companion object {
        /** The accent a user who has never changed it sees. */
        val DEFAULT = Accent()

        /**
         * Reads an accent from what was stored.
         *
         * Anything that cannot be read falls back to the default accent, so a
         * damaged setting never leaves the app without a colour.
         */
        fun decode(stored: String?): Accent {
            if (stored.isNullOrBlank()) return DEFAULT
            AccentPreset.byId(stored)?.let { return Accent(preset = it) }
            val colour = parseColour(stored)
            return if (colour == null) DEFAULT else Accent(preset = null, custom = colour)
        }
    }
}

/**
 * The colour in hexadecimal form, `#RRGGBB` or `#AARRGGBB`.
 */
fun colourToHex(colour: Color): String {
    fun part(value: Float): String {
        val scaled = (value * 255f).toInt().coerceIn(0, 255)
        return scaled.toString(16).padStart(2, '0').uppercase()
    }
    val alpha = if (colour.alpha >= 1f) "" else part(colour.alpha)
    return "#$alpha${part(colour.red)}${part(colour.green)}${part(colour.blue)}"
}

/**
 * Reads a colour from hexadecimal text.
 *
 * The forms accepted are `#RGB`, `#RRGGBB` and `#AARRGGBB`, with or without
 * the `#`. Anything else gives null, so the caller can say the value is not a
 * colour rather than guessing.
 */
fun parseColour(text: String): Color? {
    val cleaned = text.trim().removePrefix("#")
    if (cleaned.isEmpty()) return null
    if (!cleaned.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    return when (cleaned.length) {
        3 -> {
            val r = cleaned[0].digitToInt(16) * 17
            val g = cleaned[1].digitToInt(16) * 17
            val b = cleaned[2].digitToInt(16) * 17
            Color(r / 255f, g / 255f, b / 255f, 1f)
        }
        6 -> Color(
            red = cleaned.substring(0, 2).toInt(16) / 255f,
            green = cleaned.substring(2, 4).toInt(16) / 255f,
            blue = cleaned.substring(4, 6).toInt(16) / 255f,
            alpha = 1f,
        )
        8 -> Color(
            red = cleaned.substring(2, 4).toInt(16) / 255f,
            green = cleaned.substring(4, 6).toInt(16) / 255f,
            blue = cleaned.substring(6, 8).toInt(16) / 255f,
            alpha = cleaned.substring(0, 2).toInt(16) / 255f,
        )
        else -> null
    }
}

/**
 * The colour in hue, saturation and lightness.
 */
data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

/** The colour as hue, saturation and lightness. */
fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val highest = max(r, max(g, b))
    val lowest = min(r, min(g, b))
    val lightness = (highest + lowest) / 2f
    if (highest == lowest) return Hsl(0f, 0f, lightness)
    val difference = highest - lowest
    val saturation = if (lightness > 0.5f) {
        difference / (2f - highest - lowest)
    } else {
        difference / (highest + lowest)
    }
    val hue = when (highest) {
        r -> ((g - b) / difference + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / difference + 2f) / 6f
        else -> ((r - g) / difference + 4f) / 6f
    }
    return Hsl(hue, saturation, lightness)
}

/** The colour with that hue, saturation and lightness. */
fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = (hue % 1f + 1f) % 1f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    if (s == 0f) return Color(l, l, l, 1f)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(t: Float): Float {
        var value = t
        if (value < 0f) value += 1f
        if (value > 1f) value -= 1f
        return when {
            value < 1f / 6f -> p + (q - p) * 6f * value
            value < 1f / 2f -> q
            value < 2f / 3f -> p + (q - p) * (2f / 3f - value) * 6f
            else -> p
        }
    }
    return Color(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f), 1f)
}

/**
 * The colour scheme for an accent colour.
 *
 * The accent decides the hue of the whole app, not only of the buttons. The
 * surfaces, the outlines and the text are built from the same hue, so a pink
 * accent gives a pink app rather than pink buttons on green cards.
 *
 * What the accent does not decide is the *tone*. Every colour keeps the
 * lightness the app already had, so the contrast between text and background
 * is the same whatever colour the user chose, and the error colours keep
 * their own red, because an error should always look like an error.
 *
 * The accent is adjusted so that it can be read: light in the dark theme and
 * deep in the light one, whatever colour the user chose.
 */
fun accentScheme(base: ColorScheme, accent: Color, dark: Boolean): ColorScheme {
    val hsl = accent.toHsl()
    val hue = hsl.hue
    val saturation = hsl.saturation.coerceIn(0.18f, 0.72f)

    // The hue of the app's own green, which is what the surfaces were built
    // with. The difference between it and the accent's hue is the turn the
    // whole palette makes.
    val greenHue = AccentPreset.GREEN.colour.toHsl().hue
    val turn = hue - greenHue

    /**
     * The same colour with its hue turned to the accent's, keeping its own
     * lightness and a little of its own saturation. The tint of the surfaces
     * is kept small, so they read as neutral rather than as colour.
     */
    fun tinted(colour: Color, saturationScale: Float = 1f): Color {
        val own = colour.toHsl()
        // A colour with no colour of its own, such as the white of the
        // buttons, is left alone: there is no hue to turn.
        if (own.saturation < 0.02f) return colour
        return fromHsl(
            hue = own.hue + turn,
            saturation = own.saturation * saturationScale,
            lightness = own.lightness,
        )
    }

    /** A colour of the accent's own hue, at the lightness the app wants. */
    fun accentAt(lightness: Float, saturationScale: Float = 1f): Color =
        fromHsl(hue = hue, saturation = saturation * saturationScale, lightness = lightness)

    return if (dark) {
        base.copy(
            // The accent family, at the lightness the dark theme uses.
            primary = accentAt(0.76f),
            onPrimary = accentAt(0.14f, 0.6f),
            primaryContainer = accentAt(0.22f, 0.5f),
            onPrimaryContainer = accentAt(0.88f, 0.45f),
            inversePrimary = accentAt(0.32f),
            // The secondary family, which is the accent a shade quieter.
            secondary = tinted(base.secondary, 0.9f),
            onSecondary = tinted(base.onSecondary),
            secondaryContainer = tinted(base.secondaryContainer, 0.9f),
            onSecondaryContainer = tinted(base.onSecondaryContainer),
            // The third colour, which the app uses for the favourites and for
            // the second line of a graph. It keeps its own hue, so it stays
            // distinct from the accent.
            // The surfaces and the text, which take the accent's hue.
            background = tinted(base.background),
            onBackground = tinted(base.onBackground),
            surface = tinted(base.surface),
            onSurface = tinted(base.onSurface),
            surfaceVariant = tinted(base.surfaceVariant),
            onSurfaceVariant = tinted(base.onSurfaceVariant),
            surfaceTint = accentAt(0.76f),
            inverseSurface = tinted(base.inverseSurface),
            inverseOnSurface = tinted(base.inverseOnSurface),
            outline = tinted(base.outline),
            outlineVariant = tinted(base.outlineVariant),
            surfaceBright = tinted(base.surfaceBright),
            surfaceDim = tinted(base.surfaceDim),
            surfaceContainer = tinted(base.surfaceContainer),
            surfaceContainerHigh = tinted(base.surfaceContainerHigh),
            surfaceContainerHighest = tinted(base.surfaceContainerHighest),
            surfaceContainerLow = tinted(base.surfaceContainerLow),
            surfaceContainerLowest = tinted(base.surfaceContainerLowest),
        )
    } else {
        base.copy(
            primary = accentAt(0.32f),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = accentAt(0.84f, 0.55f),
            onPrimaryContainer = accentAt(0.16f, 0.6f),
            inversePrimary = accentAt(0.76f),
            secondary = tinted(base.secondary, 0.9f),
            onSecondary = tinted(base.onSecondary),
            secondaryContainer = tinted(base.secondaryContainer, 0.9f),
            onSecondaryContainer = tinted(base.onSecondaryContainer),
            background = tinted(base.background),
            onBackground = tinted(base.onBackground),
            surface = tinted(base.surface),
            onSurface = tinted(base.onSurface),
            surfaceVariant = tinted(base.surfaceVariant),
            onSurfaceVariant = tinted(base.onSurfaceVariant),
            surfaceTint = accentAt(0.32f),
            inverseSurface = tinted(base.inverseSurface),
            inverseOnSurface = tinted(base.inverseOnSurface),
            outline = tinted(base.outline),
            outlineVariant = tinted(base.outlineVariant),
            surfaceBright = tinted(base.surfaceBright),
            surfaceDim = tinted(base.surfaceDim),
            surfaceContainer = tinted(base.surfaceContainer),
            surfaceContainerHigh = tinted(base.surfaceContainerHigh),
            surfaceContainerHighest = tinted(base.surfaceContainerHighest),
            surfaceContainerLow = tinted(base.surfaceContainerLow),
            surfaceContainerLowest = tinted(base.surfaceContainerLowest),
        )
    }
}
