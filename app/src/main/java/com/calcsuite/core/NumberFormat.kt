package com.calcsuite.core

import kotlin.math.abs

/**
 * Formats a number the way the app's result panels do: whole numbers without
 * a decimal point, and at most ten decimals with the trailing zeros removed.
 *
 * It is deliberately independent of the locale and of the display
 * preferences, because it is used for mathematical output rather than for a
 * user-facing value.
 */
fun formatNumber(value: Double): String {
    if (value.isNaN()) return "undefined"
    if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
    if (value == kotlin.math.floor(value) && abs(value) < 1e15) {
        return value.toLong().toString()
    }
    var s = String.format(java.util.Locale.ROOT, "%.10f", value)
    s = s.trimEnd('0').trimEnd('.')
    return if (s.isEmpty() || s == "-") "0" else s
}
