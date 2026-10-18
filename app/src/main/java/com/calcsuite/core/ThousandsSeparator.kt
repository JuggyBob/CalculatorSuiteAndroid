package com.calcsuite.core

/**
 * Preferred thousands separator for numbers shown on screen.
 *
 * This is a *display* preference only. Parsing is deliberately kept separate:
 * [Num.parseOrNull] keeps accepting `.`, `,`, `'` and every kind of space as
 * grouping separators no matter which option is selected here, so changing this
 * setting can never reinterpret a mathematical input. The decimal separator is
 * not configurable and always follows the device locale.
 *
 * [Locale] (null [glyph]) keeps the historic behaviour of deriving the separator
 * from the locale, so existing users see no change until they opt in.
 */
enum class ThousandsSeparator(
    /** The separator to insert, or null to derive it from the locale. */
    val glyph: String?,
    val label: String,
) {
    /** Keep the locale-derived separator, so nothing changes until you opt in. */
    Locale(null, "Auto"),
    Comma(",", "Comma"),
    Period(".", "Period"),
    /** A no-break space, so a grouped number never wraps mid-value. */
    Space("\u00A0", "Space"),
    Apostrophe("'", "Apostrophe"),
    ;

    /** The separator to actually use for a locale whose decimal mark is [decimal]. */
    fun resolve(decimal: Char): Char = glyph?.firstOrNull() ?: if (decimal == ',') ' ' else ','

    companion object {
        val DEFAULT = Locale

        fun fromName(name: String?): ThousandsSeparator =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
