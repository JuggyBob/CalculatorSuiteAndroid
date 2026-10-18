package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigInteger

/**
 * Numeric base (radix) conversions for bases 2–36, with two's-complement
 * helpers for fixed-width integers.
 *
 * Parsing accepts an optional leading sign, an optional matching prefix
 * (`0b` for base 2, `0o` for base 8, `0x` for base 16) and upper- or lowercase
 * digits. Errors name the offending character and the base.
 *
 * Rendering supports uppercase output, `0b`/`0o`/`0x` prefixes and binary
 * grouping in nibbles (`1111 0000`).
 *
 * Two's complement: an unrestricted value keeps its sign; a fixed-width value
 * wraps negatives into two's complement and reports overflow with the exact
 * range for the chosen width.
 */
object NumericBaseEngine {

    const val MIN_BASE = 2
    const val MAX_BASE = 36

    /** Widths offered by the fixed-width panel. */
    val WIDTHS: List<Int> = listOf(8, 16, 32, 64)

    private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun digitValue(c: Char): Int = DIGITS.indexOf(c.lowercaseChar())

    /** Human description of the digits a base uses, e.g. "0–9 and a–f". */
    fun digitRange(base: Int): String = if (base <= 10) {
        "0–${DIGITS[base - 1]}"
    } else {
        "0–9 and a–${DIGITS[base - 1]}"
    }

    private fun validBase(base: Int): Boolean = base in MIN_BASE..MAX_BASE

    /**
     * Parses [text] as a base-[base] integer.
     * Returns [ErrorKind.InvalidInput] with a message naming the invalid
     * character when a digit does not belong to the base.
     */
    fun parse(text: String, base: Int): CalcResult<BigInteger> {
        if (!validBase(base)) {
            return err(ErrorKind.InvalidInput, "Base must be between $MIN_BASE and $MAX_BASE")
        }
        var body = text.trim()
        if (body.isEmpty()) return err(ErrorKind.InvalidInput, "Enter a value in base $base")

        var negative = false
        when (body.first()) {
            '-' -> {
                negative = true
                body = body.substring(1).trim()
            }
            '+' -> body = body.substring(1).trim()
        }

        // Optional matching base prefix (0b / 0o / 0x).
        if (body.length >= 2 && body[0] == '0') {
            val marker = body[1].lowercaseChar()
            if ((base == 2 && marker == 'b') || (base == 8 && marker == 'o') || (base == 16 && marker == 'x')) {
                body = body.substring(2)
            }
        }
        if (body.isEmpty()) return err(ErrorKind.InvalidInput, "Enter a value in base $base")

        var result = BigInteger.ZERO
        val radix = BigInteger.valueOf(base.toLong())
        for (c in body) {
            if (c == ' ' || c == '_') continue // grouping separators are tolerated
            val digit = digitValue(c)
            if (digit < 0 || digit >= base) {
                return err(
                    ErrorKind.InvalidInput,
                    "Character '$c' is not a valid digit in base $base (valid digits: ${digitRange(base)})",
                )
            }
            result = result.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }
        return ok(if (negative) result.negate() else result)
    }

    /**
     * Renders [value] in [base].
     *
     * @param uppercase use A–Z instead of a–z for digits above 9
     * @param prefix prepend 0b / 0o / 0x (only for bases 2, 8 and 16)
     * @param groupBinary insert a space every 4 binary digits (base 2 only)
     */
    fun render(
        value: BigInteger,
        base: Int,
        uppercase: Boolean = false,
        prefix: Boolean = false,
        groupBinary: Boolean = false,
    ): String {
        require(validBase(base)) { "Base must be between $MIN_BASE and $MAX_BASE" }
        if (value.signum() == 0) return "0"
        val radix = BigInteger.valueOf(base.toLong())
        val digits = StringBuilder()
        var magnitude = value.abs()
        while (magnitude.signum() != 0) {
            val (quotient, remainder) = magnitude.divideAndRemainder(radix)
            digits.append(DIGITS[remainder.toInt()])
            magnitude = quotient
        }
        var text = digits.reverse().toString()
        if (uppercase) text = text.uppercase()
        if (groupBinary && base == 2) {
            text = text.reversed().chunked(4).joinToString(" ").reversed()
        }
        val marker = when {
            !prefix -> ""
            base == 2 -> "0b"
            base == 8 -> "0o"
            base == 16 -> "0x"
            else -> ""
        }
        return (if (value.signum() < 0) "-" else "") + marker + text
    }

    /** Decimal range description for a fixed width, e.g. "-128 to 127". */
    fun rangeText(bits: Int, signed: Boolean = true): String {
        val two = BigInteger.ONE.shiftLeft(bits)
        return if (signed) {
            val min = two.shiftRight(1).negate()
            val max = two.shiftRight(1).subtract(BigInteger.ONE)
            "$min to $max"
        } else {
            "0 to ${two.subtract(BigInteger.ONE)}"
        }
    }

    /**
     * Fixed-width binary representation of [value].
     *
     * Signed mode uses two's complement for negatives and fits
     * −2^(bits−1) … 2^(bits−1)−1; unsigned mode fits 0 … 2^bits−1 and rejects
     * negatives. Values outside the range return [ErrorKind.Overflow] with the
     * exact range in the message. The result is not grouped.
     */
    fun toFixedWidth(value: BigInteger, bits: Int, signed: Boolean = true): CalcResult<String> {
        if (bits !in WIDTHS) {
            return err(ErrorKind.InvalidInput, "Fixed width must be 8, 16, 32 or 64 bits")
        }
        val two = BigInteger.ONE.shiftLeft(bits)
        val binary = if (signed) {
            val min = two.shiftRight(1).negate()
            val max = two.shiftRight(1).subtract(BigInteger.ONE)
            if (value < min || value > max) {
                return err(
                    ErrorKind.Overflow,
                    "$value does not fit in a signed $bits-bit value (range ${rangeText(bits, true)})",
                )
            }
            if (value.signum() < 0) two.add(value) else value
        } else {
            if (value.signum() < 0) {
                return err(
                    ErrorKind.Overflow,
                    "$value is negative and cannot be an unsigned $bits-bit value (range ${rangeText(bits, false)})",
                )
            }
            if (value >= two) {
                return err(
                    ErrorKind.Overflow,
                    "$value does not fit in an unsigned $bits-bit value (range ${rangeText(bits, false)})",
                )
            }
            value
        }
        return ok(padBinary(binary, bits))
    }

    private fun padBinary(value: BigInteger, bits: Int): String {
        val raw = value.toString(2)
        return "0".repeat((bits - raw.length).coerceAtLeast(0)) + raw
    }

    /**
     * Interprets [text] (a base-[base] unsigned bit pattern) as a signed
     * two's-complement value of [bits] bits: the top bit selects the sign.
     */
    fun fromTwosComplement(text: String, base: Int, bits: Int): CalcResult<BigInteger> {
        if (bits !in WIDTHS) {
            return err(ErrorKind.InvalidInput, "Fixed width must be 8, 16, 32 or 64 bits")
        }
        val parsed = when (val parsedResult = parse(text, base)) {
            is CalcResult.Ok -> parsedResult.value
            is CalcResult.Err -> return err(parsedResult.error.kind, parsedResult.error.message)
        }
        if (parsed.signum() < 0) {
            return err(
                ErrorKind.InvalidInput,
                "Two's complement input is a bit pattern; remove the minus sign",
            )
        }
        val two = BigInteger.ONE.shiftLeft(bits)
        if (parsed >= two) {
            return err(
                ErrorKind.Overflow,
                "$parsed does not fit in $bits bits (maximum ${two.subtract(BigInteger.ONE)})",
            )
        }
        return ok(if (parsed >= two.shiftRight(1)) parsed.subtract(two) else parsed)
    }
}
