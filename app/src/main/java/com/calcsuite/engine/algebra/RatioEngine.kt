package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcError
import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.core.flatMap
import com.calcsuite.core.map
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Ratio arithmetic.
 *
 * Terms are entered as `a : b : c`; a term may be a decimal (`1.5`), a
 * fraction (`3/4`) or a decimal with an exponent. Negative terms and zero
 * terms are rejected wherever they would make the operation meaningless.
 */
object RatioEngine {

    /** A ratio after scaling decimals away and dividing by the greatest common divisor. */
    data class SimplifiedRatio(
        val input: List<BigDecimal>,
        /** 10^maxScale used to turn the decimal terms into integers. */
        val scaleFactor: BigInteger,
        /** The integer terms before the common divisor is removed. */
        val scaled: List<BigInteger>,
        /** Greatest common divisor of [scaled]. */
        val divisor: BigInteger,
        /** The simplified integer terms. */
        val terms: List<BigInteger>,
        val steps: List<String>,
    ) {
        val text: String get() = terms.joinToString(" : ")
    }

    data class ScaledRatio(
        val factor: BigDecimal,
        val input: List<BigDecimal>,
        val terms: List<BigDecimal>,
        val steps: List<String>,
    ) {
        val text: String get() = terms.joinToString(" : ") { plain(it) }
    }

    data class SplitResult(
        val total: BigDecimal,
        val terms: List<BigInteger>,
        val shares: List<BigDecimal>,
        val decimalPlaces: Int,
        /** True when every share is exact at [decimalPlaces]. */
        val exactDivision: Boolean,
        /** Amount that was shuffled one unit at a time to the largest remainders. */
        val remainderDistributed: BigDecimal,
        /** Part of the total that cannot be shown at [decimalPlaces] (usually zero). */
        val unallocatedRemainder: BigDecimal,
        val steps: List<String>,
    )

    data class UnknownPartResult(
        val terms: List<BigDecimal>,
        val knownIndex: Int,
        val unknownIndex: Int,
        val knownValue: BigDecimal,
        /** Value of one ratio unit (0-based indices). */
        val unit: BigDecimal,
        val value: BigDecimal,
        val steps: List<String>,
        val verification: String,
    )

    data class CombineResult(
        val left: List<BigInteger>,
        val right: List<BigInteger>,
        val leftScaled: List<BigInteger>,
        val rightScaled: List<BigInteger>,
        val terms: List<BigInteger>,
        val steps: List<String>,
    ) {
        val text: String get() = terms.joinToString(" : ")
    }

    enum class RatioRelation { Less, Equal, Greater }

    data class CompareResult(
        val relation: RatioRelation,
        /** a/b rounded for display only; the verdict uses exact cross multiplication. */
        val leftValue: BigDecimal,
        val rightValue: BigDecimal,
        val detail: String,
        val steps: List<String>,
    )

    // ------------------------------------------------------------------ parse

    /** Parses `a : b : c` into two or more numeric terms. */
    fun parse(text: String): CalcResult<List<BigDecimal>> {
        val raw = text.trim()
        if (raw.isEmpty()) return err(ErrorKind.InvalidInput, "Enter a ratio, for example 12 : 18")
        val tokens = raw.split(':').map { it.trim() }
        if (tokens.size < 2) {
            return err(ErrorKind.InvalidInput, "A ratio needs at least two terms separated by \":\"")
        }
        val values = ArrayList<BigDecimal>(tokens.size)
        for (token in tokens) {
            if (token.isEmpty()) return err(ErrorKind.InvalidInput, "Every term must contain a number")
            if (token.contains('/')) {
                val pieces = token.split('/')
                if (pieces.size != 2) {
                    return err(ErrorKind.InvalidInput, "Could not read \"$token\" as a number or fraction")
                }
                val denominator = Num.parseOrNull(pieces[1])
                if (denominator != null && denominator.signum() == 0) {
                    return err(ErrorKind.DivisionByZero, "The term \"$token\" has a zero denominator")
                }
            }
            val value = parseTerm(token)
                ?: return err(ErrorKind.InvalidInput, "Could not read \"$token\" as a number or fraction")
            values.add(value)
        }
        return ok(values)
    }

    private fun parseTerm(token: String): BigDecimal? {
        if (token.contains('/')) {
            val pieces = token.split('/')
            if (pieces.size != 2 || pieces.any { it.isBlank() }) return null
            val numerator = Num.parseOrNull(pieces[0]) ?: return null
            val denominator = Num.parseOrNull(pieces[1]) ?: return null
            if (denominator.signum() == 0) return null
            return numerator.divide(denominator, ALG_CTX)
        }
        return Num.parseOrNull(token)
    }

    private fun validate(terms: List<BigDecimal>): CalcError? {
        if (terms.size < 2) return CalcError(ErrorKind.InvalidInput, "A ratio needs at least two terms")
        if (terms.any { it.signum() < 0 }) {
            return CalcError(ErrorKind.Domain, "Ratio terms must not be negative")
        }
        if (terms.any { it.signum() == 0 }) {
            return CalcError(ErrorKind.Domain, "Ratio terms must be greater than zero")
        }
        return null
    }

    // --------------------------------------------------------------- simplify

    fun simplify(text: String): CalcResult<SimplifiedRatio> = parse(text).flatMap { simplify(it) }

    fun simplify(terms: List<BigDecimal>): CalcResult<SimplifiedRatio> {
        validate(terms)?.let { return CalcResult.Err(it) }
        val maxScale = terms.maxOf { it.stripTrailingZeros().scale().coerceAtLeast(0) }
        if (maxScale > 30) {
            return err(
                ErrorKind.InvalidInput,
                "Terms with more than 30 decimal places cannot be simplified exactly",
            )
        }
        val scaleFactor = BigInteger.TEN.pow(maxScale)
        val scaled = terms.map {
            it.stripTrailingZeros().multiply(BigDecimal(scaleFactor)).toBigIntegerExact()
        }
        val divisor = scaled.reduce { acc, value -> acc.gcd(value) }
        val simplified = scaled.map { it.divide(divisor) }
        val steps = buildList {
            if (maxScale > 0) {
                add("Multiply every term by 10^$maxScale to clear the decimals: ${scaled.joinToString(" : ")}")
            } else {
                add("The terms are already integers: ${scaled.joinToString(" : ")}")
            }
            add("Greatest common divisor of ${scaled.joinToString(", ")} is $divisor")
            add("Divide every term by $divisor: ${simplified.joinToString(" : ")}")
        }
        return ok(SimplifiedRatio(terms, scaleFactor, scaled, divisor, simplified, steps))
    }

    // ------------------------------------------------------------------ scale

    /** Multiplies every term by [factor] (a positive number), keeping the ratio equivalent. */
    fun scale(terms: List<BigDecimal>, factor: BigDecimal): CalcResult<ScaledRatio> {
        validate(terms)?.let { return CalcResult.Err(it) }
        if (factor.signum() <= 0) {
            return err(ErrorKind.Domain, "The scale factor must be greater than zero")
        }
        val scaled = terms.map { it.multiply(factor) }
        val steps = buildList {
            add("Multiply every term of ${terms.joinToString(" : ") { plain(it) }} by ${plain(factor)}")
            add("Equivalent ratio: ${scaled.joinToString(" : ") { plain(it) }}")
        }
        return ok(ScaledRatio(factor, terms, scaled, steps))
    }

    /** The ×10 and ÷10 forms shown in the Scale mode. */
    fun equivalents(terms: List<BigDecimal>): CalcResult<Pair<ScaledRatio, ScaledRatio>> =
        scale(terms, BigDecimal.TEN).flatMap { up ->
            scale(terms, BigDecimal("0.1")).map { down -> Pair(up, down) }
        }

    // ------------------------------------------------------------ split total

    /**
     * Splits [total] in the given ratio. Shares are rounded down to
     * [decimalPlaces] and the leftover is handed out one unit at a time,
     * largest fractional part first.
     */
    fun splitTotal(total: BigDecimal, terms: List<BigDecimal>, decimalPlaces: Int = 2): CalcResult<SplitResult> {
        validate(terms)?.let { return CalcResult.Err(it) }
        if (total.signum() < 0) return err(ErrorKind.Domain, "The total must not be negative")
        if (decimalPlaces < 0 || decimalPlaces > 12) {
            return err(ErrorKind.InvalidInput, "Decimal places must be between 0 and 12")
        }
        val sum = terms.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        val exactShares = terms.map { total.multiply(it).divide(sum, ALG_CTX) }
        val unit = BigDecimal.ONE.movePointLeft(decimalPlaces)
        val floored = exactShares.map { it.setScale(decimalPlaces, RoundingMode.DOWN) }
        var remainder = total.subtract(floored.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) })
        val exactDivision = remainder.signum() == 0
        val shares = floored.toMutableList()
        val distributed = remainder
        val order = exactShares.indices.sortedByDescending { exactShares[it].subtract(floored[it]) }
        var cursor = 0
        while (remainder.compareTo(unit) >= 0 && shares.isNotEmpty()) {
            val index = order[cursor % order.size]
            shares[index] = shares[index].add(unit)
            remainder = remainder.subtract(unit)
            cursor++
            if (cursor > 100_000) break
        }
        val steps = buildList {
            add("Sum of the ratio terms: ${terms.joinToString(" + ") { plain(it) }} = ${plain(sum)}")
            terms.forEachIndexed { index, term ->
                add(
                    "Share ${index + 1} = ${plain(total)} × ${plain(term)} ÷ ${plain(sum)} = " +
                        "${plain(roundForText(exactShares[index]))}",
                )
            }
            if (!exactDivision) {
                add(
                    "Round each share down to $decimalPlaces decimal place(s) and hand out the " +
                        "${plain(roundForText(distributed))} leftover one ${plain(unit)} at a time",
                )
            }
            add("Shares: ${shares.joinToString(" + ") { plain(it) }}")
        }
        return ok(
            SplitResult(
                total = total,
                terms = simplify(terms).valueOrNull()?.terms ?: emptyList(),
                shares = shares,
                decimalPlaces = decimalPlaces,
                exactDivision = exactDivision,
                remainderDistributed = if (exactDivision) BigDecimal.ZERO else distributed,
                unallocatedRemainder = remainder,
                steps = steps,
            ),
        )
    }

    // ----------------------------------------------------------- unknown part

    /** Finds one term of a ratio split from another share: value = known × term ÷ knownTerm. */
    fun unknownPart(
        terms: List<BigDecimal>,
        knownIndex: Int,
        knownValue: BigDecimal,
        unknownIndex: Int,
    ): CalcResult<UnknownPartResult> {
        validate(terms)?.let { return CalcResult.Err(it) }
        if (knownIndex !in terms.indices || unknownIndex !in terms.indices) {
            return err(ErrorKind.InvalidInput, "Term numbers must be between 1 and ${terms.size}")
        }
        if (knownIndex == unknownIndex) {
            return err(ErrorKind.InvalidInput, "Choose two different terms")
        }
        if (knownValue.signum() <= 0) {
            return err(ErrorKind.Domain, "The known share must be greater than zero")
        }
        val unit = knownValue.divide(terms[knownIndex], ALG_CTX)
        val value = terms[unknownIndex].multiply(unit, ALG_CTX)
        val steps = buildList {
            add("Ratio: ${terms.joinToString(" : ") { plain(it) }}")
            add("One ratio unit = ${plain(knownValue)} ÷ ${plain(terms[knownIndex])} = ${plain(roundForText(unit))}")
            add("Term ${unknownIndex + 1} = ${plain(terms[unknownIndex])} × ${plain(roundForText(unit))} = ${plain(roundForText(value))}")
        }
        val verification =
            "${plain(roundForText(knownValue))} : ${plain(roundForText(value))} keeps the ratio " +
                "${terms.joinToString(" : ") { plain(it) }}"
        return ok(
            UnknownPartResult(terms, knownIndex, unknownIndex, knownValue, unit, value, steps, verification),
        )
    }

    // ---------------------------------------------------------------- combine

    /**
     * Chains two ratios through their linking term: `a:b` and `b:c` become
     * `a:b:c`. The linking term must represent the same quantity, so both
     * ratios are rescaled to the least common multiple of the two linking
     * terms before merging.
     */
    fun combine(first: List<BigDecimal>, second: List<BigDecimal>): CalcResult<CombineResult> {
        validate(first)?.let { return CalcResult.Err(it) }
        validate(second)?.let { return CalcResult.Err(it) }
        val left = simplify(first).valueOrNull() ?: return err(ErrorKind.InvalidInput, "Could not simplify the first ratio")
        val right = simplify(second).valueOrNull() ?: return err(ErrorKind.InvalidInput, "Could not simplify the second ratio")
        val link = left.terms.last().gcd(right.terms.first()).let { gcd ->
            left.terms.last().divide(gcd).multiply(right.terms.first())
        }
        val leftScaled = left.terms.map { it.multiply(link).divide(left.terms.last()) }
        val rightScaled = right.terms.map { it.multiply(link).divide(right.terms.first()) }
        val merged = leftScaled + rightScaled.drop(1)
        val common = merged.reduce { acc, value -> acc.gcd(value) }
        val terms = merged.map { it.divide(common) }
        val steps = buildList {
            add("Simplify both ratios: ${left.text} and ${right.text}")
            add("The linking terms are ${left.terms.last()} and ${right.terms.first()}; their LCM is $link")
            add("Rescale: ${leftScaled.joinToString(" : ")} and ${rightScaled.joinToString(" : ")}")
            add("Merge through the common term: $link")
            if (common != BigInteger.ONE) add("Divide by the common divisor $common: ${terms.joinToString(" : ")}")
            add("Combined ratio: ${terms.joinToString(" : ")}")
        }
        return ok(CombineResult(left.terms, right.terms, leftScaled, rightScaled, terms, steps))
    }

    // ---------------------------------------------------------------- compare

    /**
     * Compares two-term ratios. The verdict is exact (cross multiplication);
     * [CompareResult.leftValue] and [CompareResult.rightValue] are decimal
     * quotients rounded to 12 significant digits just for display.
     */
    fun compare(first: List<BigDecimal>, second: List<BigDecimal>): CalcResult<CompareResult> {
        validate(first)?.let { return CalcResult.Err(it) }
        validate(second)?.let { return CalcResult.Err(it) }
        if (first.size != 2 || second.size != 2) {
            return err(ErrorKind.InvalidInput, "Comparison needs two-term ratios, for example 2 : 3 and 4 : 6")
        }
        val leftProduct = first[0].multiply(second[1])
        val rightProduct = second[0].multiply(first[1])
        val relation = when (leftProduct.compareTo(rightProduct)) {
            -1 -> RatioRelation.Less
            0 -> RatioRelation.Equal
            else -> RatioRelation.Greater
        }
        val leftValue = first[0].divide(first[1], ALG_CTX)
        val rightValue = second[0].divide(second[1], ALG_CTX)
        val displayLeft = plain(roundForText(leftValue))
        val displayRight = plain(roundForText(rightValue))
        val verb = when (relation) {
            RatioRelation.Less -> "is smaller than"
            RatioRelation.Equal -> "equals"
            RatioRelation.Greater -> "is greater than"
        }
        val detail = "${first.joinToString(" : ") { plain(it) }} $verb " +
            "${second.joinToString(" : ") { plain(it) }}  ($displayLeft vs $displayRight)"
        val steps = buildList {
            add("Compare the values as fractions: ${plain(first[0])}/${plain(first[1])} and ${plain(second[0])}/${plain(second[1])}")
            add(
                "Cross multiply: ${plain(first[0])} × ${plain(second[1])} = ${plain(leftProduct)} and " +
                    "${plain(second[0])} × ${plain(first[1])} = ${plain(rightProduct)}",
            )
            add("The comparison is exact (no tolerance); the decimal values shown are rounded for display")
        }
        return ok(CompareResult(relation, leftValue, rightValue, detail, steps))
    }
}
