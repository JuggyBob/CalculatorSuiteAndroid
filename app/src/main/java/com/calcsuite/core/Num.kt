package com.calcsuite.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/**
 * Number parsing and formatting.
 *
 * ## Parsing convention (documented, applied uniformly)
 * Spaces (including non-breaking / thin spaces) are always grouping separators
 * and are removed.
 * - If both `.` and `,` appear, the last one to appear is the **decimal
 *   separator**; the other is a grouping separator and is removed.
 * - If only one kind of separator appears exactly once, it is the decimal
 *   separator (`1,5` → 1.5 and `1.5` → 1.5).
 * - If a separator appears more than once, it is a grouping separator; every
 *   group after the first must contain exactly three digits (`1,234,567`).
 *   Otherwise the input is rejected rather than guessed at.
 * - Scientific notation with `e`/`E` is accepted (`1.5e-9`).
 *
 * Parsing never consults the display preferences, so choosing a thousands
 * separator (see [ThousandsSeparator]) cannot change how an input is read. The
 * one consequence is that a separator matching your locale's decimal mark (a
 * period where the decimal point is also a period) makes a displayed decimal
 * ambiguous to retype; the app rejects that rather than guess.
 *
 * ## Formatting
 * Results are rounded to the configured number of significant digits
 * (3..15) at display time only; engines keep full precision. Values with a
 * magnitude outside the plain range are rendered in scientific notation.
 */
object Num {

    /** Internal evaluation precision (34 significant digits, like DECIMAL128). */
    val EVAL_CONTEXT: MathContext = MathContext(34, RoundingMode.HALF_UP)

    const val MIN_PRECISION = 3
    const val MAX_PRECISION = 15
    const val DEFAULT_PRECISION = 12

    fun clampPrecision(p: Int): Int = p.coerceIn(MIN_PRECISION, MAX_PRECISION)

    // ------------------------------------------------------------------ parse

    /**
     * Parses user text into a [BigDecimal] using the convention documented on
     * [Num]. Returns null when the text is not a valid number.
     */
    fun parseOrNull(raw: String?): BigDecimal? {
        if (raw == null) return null
        val compact = raw.trim()
            .replace("\u00A0", "")   // no-break space
            .replace("\u202F", "")   // narrow no-break space
            .replace(" ", "")
            .replace("'", "")        // Swiss grouping
        if (compact.isEmpty()) return null

        // Split mantissa / exponent.
        val eIndex = compact.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa: String
        var exponentPart: String? = null
        if (eIndex >= 0) {
            mantissa = compact.substring(0, eIndex)
            exponentPart = compact.substring(eIndex + 1)
            if (exponentPart.isEmpty()) return null
        } else {
            mantissa = compact
        }

        val normalizedMantissa = normalizeMantissa(mantissa) ?: return null
        val exponent = if (exponentPart == null) 0 else {
            val exp = exponentPart.removePrefix("+")
            val parsed = exp.toIntOrNull() ?: return null
            if (parsed < -10_000 || parsed > 10_000) return null
            parsed
        }
        return try {
            var value = BigDecimal(normalizedMantissa)
            if (exponent != 0) value = value.scaleByPowerOfTen(exponent)
            value
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Returns the parsed value or throws nothing: null is mapped to [err]. */
    fun parseOrNull(raw: String?, field: String): BigDecimal? = parseOrNull(raw)

    private fun normalizeMantissa(s: String): String? {
        if (s.isEmpty()) return null
        var body = s
        val negative = when (body.firstOrNull()) {
            '-' -> { body = body.substring(1); true }
            '+' -> { body = body.substring(1); false }
            else -> false
        }
        if (body.isEmpty()) return null

        val dots = body.count { it == '.' }
        val commas = body.count { it == ',' }
        if (dots == 0 && commas == 0) {
            if (body.any { !it.isDigit() }) return null
            return (if (negative) "-" else "") + body
        }

        // Only one kind of separator present.
        if (dots == 0 || commas == 0) {
            val sep = if (dots > 0) '.' else ','
            val parts = body.split(sep)
            val count = parts.size - 1
            if (count == 1) {
                // A single separator is the decimal separator: "1,5" -> 1.5.
                val intPart = parts[0]
                val fracPart = parts[1]
                if (intPart.any { !it.isDigit() } || fracPart.any { !it.isDigit() }) return null
                if (intPart.isEmpty() && fracPart.isEmpty()) return null
                return buildString {
                    if (negative) append('-')
                    append(intPart.ifEmpty { "0" })
                    if (fracPart.isNotEmpty()) {
                        append('.')
                        append(fracPart)
                    }
                }
            }
            // A repeated separator is a grouping separator; every group after
            // the first must contain exactly three digits ("1,234,567").
            // Anything else is rejected rather than guessed at: "12,345,67" is
            // far more likely to be a typo than 12345.67, and guessing would
            // silently reinterpret what the user typed.
            asGroupedInteger(parts)?.let { return (if (negative) "-" else "") + it }
            return null
        }

        val lastDot = body.lastIndexOf('.')
        val lastComma = body.lastIndexOf(',')
        val decimalIdx = maxOf(lastDot, lastComma)
        val decimalSep = body[decimalIdx]

        // Everything that is not the chosen decimal separator must be a grouping
        // separator; validate the groups that follow grouping separators.
        val integerPartAndGroups = body.substring(0, decimalIdx)
        val fraction = body.substring(decimalIdx + 1)

        val separatorCount = if (decimalSep == '.') commas else dots
        if (separatorCount > 0) {
            val groupingSep = if (decimalSep == '.') ',' else '.'
            val parts = integerPartAndGroups.split(groupingSep)
            if (parts.first().isEmpty()) return null
            if (parts.first().any { !it.isDigit() }) return null
            for (i in 1 until parts.size) {
                if (parts[i].length != 3) return null
                if (parts[i].any { !it.isDigit() }) return null
            }
        } else {
            if (integerPartAndGroups.any { !it.isDigit() }) return null
        }
        if (fraction.any { !it.isDigit() }) return null
        if (fraction.isEmpty() && integerPartAndGroups.isEmpty()) return null

        val digits = integerPartAndGroups.replace(",", "").replace(".", "")
        val normalized = buildString {
            if (negative) append('-')
            append(digits.ifEmpty { "0" })
            if (fraction.isNotEmpty()) {
                append('.')
                append(fraction)
            }
        }
        return normalized
    }

    /**
     * Joins the digit groups of an integer written with a grouping separator
     * ("1", "234", "567" -> "1234567"), or returns null when the groups are not
     * well formed: the first group must be non-empty and every later group must
     * be exactly three digits.
     */
    private fun asGroupedInteger(parts: List<String>): String? {
        val first = parts.first()
        if (first.isEmpty() || first.any { !it.isDigit() }) return null
        for (i in 1 until parts.size) {
            if (parts[i].length != 3 || parts[i].any { !it.isDigit() }) return null
        }
        return parts.joinToString("")
    }

    /** Parses integers with optional `+` sign; rejects decimals. */
    fun parseIntOrNull(raw: String?): BigInteger? {
        val value = parseOrNull(raw) ?: return null
        if (value.stripTrailingZeros().scale() > 0) return null
        return value.toBigIntegerExactOrNull() ?: return null
    }

    fun isValidInteger(raw: String?): Boolean = parseIntOrNull(raw) != null

    // ----------------------------------------------------------------- format

    /** Locale-aware separators used for display. */
    data class Sep(val decimal: Char, val grouping: Char?)

    /**
     * Separators used for display.
     *
     * The decimal separator always follows [locale]. The thousands separator
     * follows the user's [thousandsSeparator] choice unless grouping is off;
     * [ThousandsSeparator.Locale] keeps the locale-derived default.
     */
    fun sepsFor(
        locale: Locale,
        useGrouping: Boolean,
        thousandsSeparator: ThousandsSeparator = ThousandsSeparator.Locale,
    ): Sep {
        val decimal = decimalSeparatorFor(locale)
        val grouping = if (!useGrouping) null else thousandsSeparator.resolve(decimal)
        return Sep(decimal, grouping)
    }

    fun decimalSeparatorFor(locale: Locale): Char {
        val symbols = java.text.DecimalFormatSymbols.getInstance(locale)
        return symbols.decimalSeparator
    }

    /**
     * Formats [value] for display: rounded to [precision] significant digits,
     * trailing zeros removed, grouping applied unless disabled.
     */
    fun format(
        value: BigDecimal,
        precision: Int = DEFAULT_PRECISION,
        seps: Sep = Sep('.', ','),
    ): String {
        val sig = clampPrecision(precision)
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(sig, RoundingMode.HALF_UP))
        val stripped = rounded.stripTrailingZeros()
        val exponent = stripped.precision() - stripped.scale() - 1
        return when {
            exponent >= 13 || exponent <= -6 -> formatScientific(stripped, seps)
            else -> formatPlain(stripped, seps)
        }
    }

    fun format(
        value: BigDecimal,
        precision: Int,
        locale: Locale,
        useGrouping: Boolean,
        thousandsSeparator: ThousandsSeparator = ThousandsSeparator.Locale,
    ): String = format(value, precision, sepsFor(locale, useGrouping, thousandsSeparator))

    /** Formats a [Double] using its shortest meaningful representation. */
    fun format(value: Double, precision: Int = DEFAULT_PRECISION, seps: Sep = Sep('.', ',')): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        return format(BigDecimal.valueOf(value), precision, seps)
    }

    /** Always-plain string for copy actions: full precision, `E` for extremes. */
    fun formatForCopy(value: BigDecimal): String = value.stripTrailingZeros().let {
        val exponent = it.precision() - it.scale() - 1
        if (exponent >= 13 || exponent <= -6) it.toEngineeringString() else it.toPlainString()
    }

    private fun formatPlain(value: BigDecimal, seps: Sep): String {
        var plain = value.toPlainString()
        if (seps.decimal != '.') plain = plain.replace('.', seps.decimal)
        return applyGrouping(plain, seps)
    }

    private fun applyGrouping(plain: String, seps: Sep): String {
        val grouping = seps.grouping ?: return plain
        val negative = plain.startsWith("-")
        val body = if (negative) plain.substring(1) else plain
        val decimalIdx = body.indexOf(seps.decimal)
        val intPart = if (decimalIdx >= 0) body.substring(0, decimalIdx) else body
        val fracPart = if (decimalIdx >= 0) body.substring(decimalIdx) else ""
        if (intPart.length <= 3 || !intPart.all { it.isDigit() }) return plain
        val grouped = StringBuilder()
        var count = 0
        for (i in intPart.indices.reversed()) {
            grouped.append(intPart[i])
            count++
            if (count % 3 == 0 && i != 0) grouped.append(grouping)
        }
        return buildString {
            if (negative) append('-')
            append(grouped.reversed())
            append(fracPart)
        }
    }

    private fun formatScientific(value: BigDecimal, seps: Sep): String {
        val exponent = value.precision() - value.scale() - 1
        val mantissa = value.movePointLeft(exponent).stripTrailingZeros()
        var mantissaText = mantissa.toPlainString()
        if (seps.decimal != '.') mantissaText = mantissaText.replace('.', seps.decimal)
        return "${mantissaText}e$exponent"
    }

    // ---------------------------------------------------------------- helpers

    /** Strips insignificant zeros and returns a value with a clean scale. */
    fun clean(value: BigDecimal): BigDecimal = value.stripTrailingZeros()

    /** Rounds half-up to [digits] decimal places. */
    fun roundTo(value: BigDecimal, digits: Int): BigDecimal =
        value.setScale(digits, RoundingMode.HALF_UP)

    fun BigDecimal.toBigIntegerExactOrNull(): BigInteger? = try {
        this.toBigIntegerExact()
    } catch (_: ArithmeticException) {
        null
    }
}
