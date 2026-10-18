package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok

/**
 * Roman numerals, 1–3999, using strict subtractive notation.
 *
 * Writing: the largest subtractive steps are consumed greedily, producing the
 * canonical form (`1994 → MCMXCIV`). Parsing accepts **only** canonical forms.
 * For input that is parseable but not canonical (`IIII`, `VX`, `IC`) the engine
 * performs a tolerant parse purely to name the canonical form in the error
 * message.
 *
 * The tolerant parse follows historical practice: I, X and C may subtract from
 * a larger symbol (`IL` = 49, `IC` = 99, `XM` = 990) while V, L and D never
 * subtract (`VX` reads as 5 + 10 = 15).
 */
object RomanEngine {

    /** Numerals must stay at or below this value: there is no standard symbol above M. */
    const val MAX_VALUE = 3999

    private val STEPS: List<Pair<Int, String>> = listOf(
        1000 to "M",
        900 to "CM",
        500 to "D",
        400 to "CD",
        100 to "C",
        90 to "XC",
        50 to "L",
        40 to "XL",
        10 to "X",
        9 to "IX",
        5 to "V",
        4 to "IV",
        1 to "I",
    )

    private val SYMBOL_VALUES: Map<Char, Int> = mapOf(
        'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000,
    )

    /** Symbols with their values and subtraction rules, for the screen legend. */
    val symbolLegend: List<Triple<String, Int, String>> = listOf(
        Triple("I", 1, "subtracts from V and X (IV = 4, IX = 9)"),
        Triple("V", 5, "never subtracts"),
        Triple("X", 10, "subtracts from L and C (XL = 40, XC = 90)"),
        Triple("L", 50, "never subtracts"),
        Triple("C", 100, "subtracts from D and M (CD = 400, CM = 900)"),
        Triple("D", 500, "never subtracts"),
        Triple("M", 1000, "never subtracts"),
    )

    /** Canonical spelling of [value], or null outside 1–3999. */
    fun canonical(value: Int): String? {
        if (value < 1 || value > MAX_VALUE) return null
        var remaining = value
        val builder = StringBuilder()
        for ((step, symbol) in STEPS) {
            while (remaining >= step) {
                builder.append(symbol)
                remaining -= step
            }
        }
        return builder.toString()
    }

    /** Converts 1–3999 to its canonical Roman numeral. */
    fun toRoman(value: Int): CalcResult<String> {
        if (value < 0) return err(ErrorKind.Domain, "Roman numerals cannot represent negative numbers")
        if (value == 0) return err(ErrorKind.Domain, "Roman numerals have no symbol for zero")
        if (value > MAX_VALUE) {
            return err(ErrorKind.Domain, "Roman numerals are limited to $MAX_VALUE (MMMCMXCIX)")
        }
        return ok(canonical(value) ?: return err(ErrorKind.Domain, "Value is outside the Roman range"))
    }

    private val CANONICAL_PATTERN = Regex("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")

    /** True when [text] is a canonical Roman numeral in 1–3999. */
    fun isValidRoman(text: String): Boolean {
        val upper = text.trim().uppercase()
        if (upper.isEmpty()) return false
        if (!CANONICAL_PATTERN.matches(upper)) return false
        return tolerantParse(upper)?.let { it in 1..MAX_VALUE } == true
    }

    /** Parses a canonical Roman numeral; explains the canonical form otherwise. */
    fun parse(text: String): CalcResult<Int> {
        val upper = text.trim().uppercase()
        if (upper.isEmpty()) return err(ErrorKind.InvalidInput, "Enter a Roman numeral")
        val invalidChar = upper.firstOrNull { it !in SYMBOL_VALUES }
        if (invalidChar != null) {
            return err(
                ErrorKind.InvalidInput,
                "'$invalidChar' is not a Roman numeral symbol (I, V, X, L, C, D, M)",
            )
        }
        if (CANONICAL_PATTERN.matches(upper)) {
            val value = tolerantParse(upper)
            if (value != null && value in 1..MAX_VALUE) return ok(value)
        }
        val tolerant = tolerantParse(upper)
        if (tolerant != null) {
            if (tolerant > MAX_VALUE) {
                return err(
                    ErrorKind.Domain,
                    "$upper is not standard Roman notation and $tolerant is above the maximum $MAX_VALUE " +
                        "(largest standard numeral: MMMCMXCIX)",
                )
            }
            if (tolerant in 1..MAX_VALUE) {
                return err(
                    ErrorKind.InvalidInput,
                    "$upper is not standard Roman notation; $tolerant is written ${canonical(tolerant)}",
                )
            }
        }
        return err(ErrorKind.InvalidInput, "$upper is not a valid Roman numeral")
    }

    /**
     * Lenient value of [text] used only to build error messages.
     * I/X/C subtract from a larger following symbol; V/L/D never subtract.
     */
    private fun tolerantParse(upper: String): Int? {
        var total = 0
        var index = 0
        while (index < upper.length) {
            val value = SYMBOL_VALUES[upper[index]] ?: return null
            val next = if (index + 1 < upper.length) SYMBOL_VALUES[upper[index + 1]] else null
            val subtracts = next != null && next > value && (upper[index] == 'I' || upper[index] == 'X' || upper[index] == 'C')
            if (subtracts) {
                total += next!! - value
                index += 2
            } else {
                total += value
                index += 1
            }
        }
        return total
    }
}
