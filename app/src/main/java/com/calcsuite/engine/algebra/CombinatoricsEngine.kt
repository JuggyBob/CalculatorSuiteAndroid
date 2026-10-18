package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.BigInteger

enum class SelectionKind { Combination, Permutation }

data class SelectionResult(
    val kind: SelectionKind,
    val n: BigInteger,
    val r: BigInteger,
    val value: BigInteger,
    /** Formula with the numbers substituted. */
    val formula: String,
    val steps: List<String>,
    val note: String,
)

data class ProbabilityResult(
    val probability: BigDecimal,
    val formula: String,
    val steps: List<String>,
    val note: String,
) {
    /** 1 / probability when the probability is not zero. */
    val oneIn: BigDecimal?
        get() = if (probability.signum() > 0) BigDecimal.ONE.divide(probability, ALG_CTX) else null
}

/**
 * Exact combinatorics with [BigInteger] and probability helpers with
 * [BigDecimal].
 *
 * nCr and nPr use multiplicative products with exact interim divisions, so no
 * factorial of `n` is ever materialised and huge arguments stay fast.
 */
object CombinatoricsEngine {

    private val ZERO: BigInteger = BigInteger.ZERO
    private val ONE: BigInteger = BigInteger.ONE

    /** Iteration guard: min(r, n − r) and r may not exceed this. */
    const val MAX_ITERATIONS = 1_000_000

    val CONCEPT_NOTE =
        "Combinations C(n, r) count selections where the order does not matter; " +
            "permutations P(n, r) = C(n, r) × r! count arrangements where the order does matter."

    // ------------------------------------------------------------ selections

    /** Number of ways to choose [r] items out of [n] when order does not matter. */
    fun nCr(n: BigInteger, r: BigInteger): CalcResult<BigInteger> {
        validate(n, r)?.let { return CalcResult.Err(it) }
        var k = r
        val complement = n.subtract(k)
        if (k > complement) k = complement
        if (k > BigInteger.valueOf(MAX_ITERATIONS.toLong())) {
            return err(ErrorKind.Unavailable, "The selection is too large to compute here")
        }
        var result = ONE
        var index = ONE
        val offset = n.subtract(k)
        while (index <= k) {
            result = result.multiply(offset.add(index)).divide(index)
            index = index.add(ONE)
        }
        return ok(result)
    }

    /** Number of ways to arrange [r] items out of [n] when order matters. */
    fun nPr(n: BigInteger, r: BigInteger): CalcResult<BigInteger> {
        validate(n, r)?.let { return CalcResult.Err(it) }
        if (r > BigInteger.valueOf(MAX_ITERATIONS.toLong())) {
            return err(ErrorKind.Unavailable, "The arrangement is too large to compute here")
        }
        var result = ONE
        var index = ZERO
        while (index < r) {
            result = result.multiply(n.subtract(index))
            index = index.add(ONE)
        }
        return ok(result)
    }

    fun factorial(n: BigInteger): CalcResult<BigInteger> {
        if (n.signum() < 0) return err(ErrorKind.InvalidInput, "n must not be negative")
        if (n > BigInteger.valueOf(100_000L)) {
            return err(ErrorKind.Unavailable, "Factorials above 100 000! are too large for this tool")
        }
        var result = ONE
        var index = BigInteger.valueOf(2L)
        while (index <= n) {
            result = result.multiply(index)
            index = index.add(ONE)
        }
        return ok(result)
    }

    /** nCr with a formula, an abbreviated multiplication trace and the theory note. */
    fun combinations(n: BigInteger, r: BigInteger): CalcResult<SelectionResult> {
        val computed = nCr(n, r)
        val exact = when (computed) {
            is CalcResult.Ok -> computed.value
            is CalcResult.Err -> return computed
        }
        val factors = enumerateFactors(n, r, descending = true)
        val formula = "C($n, $r) = ($factors) ÷ ${r}!"
        val steps = ArrayList<String>()
        steps.add("Set up the formula: C(n, r) = n × (n−1) × … × (n−r+1) ÷ r!")
        steps.add("Cancel both factorials: C($n, $r) = ($factors) ÷ (${countFactors(r)})")
        steps.add("Evaluate: ${plainFactorProduct(n, r, descending = true)} ÷ ${factorialValueText(r)} = $exact")
        steps.add("Order does not matter, so (a, b) is the same selection as (b, a)")
        return ok(
            SelectionResult(
                kind = SelectionKind.Combination,
                n = n,
                r = r,
                value = exact,
                formula = formula,
                steps = steps,
                note = CONCEPT_NOTE,
            ),
        )
    }

    /** nPr with a formula, an abbreviated multiplication trace and the theory note. */
    fun permutations(n: BigInteger, r: BigInteger): CalcResult<SelectionResult> {
        val computed = nPr(n, r)
        val exact = when (computed) {
            is CalcResult.Ok -> computed.value
            is CalcResult.Err -> return computed
        }
        val factors = enumerateFactors(n, r, descending = true)
        val steps = ArrayList<String>()
        steps.add("Set up the formula: P(n, r) = n × (n−1) × … × (n−r+1)")
        steps.add("Substitute: P($n, $r) = $factors")
        steps.add("Evaluate: ${plainFactorProduct(n, r, descending = true)} = $exact")
        steps.add("Order matters, so each selection can be arranged in r! = ${factorialValueText(r)} ways")
        return ok(
            SelectionResult(
                kind = SelectionKind.Permutation,
                n = n,
                r = r,
                value = exact,
                formula = "P($n, $r) = $factors",
                steps = steps,
                note = CONCEPT_NOTE,
            ),
        )
    }

    private fun validate(n: BigInteger, r: BigInteger): com.calcsuite.core.CalcError? {
        if (n.signum() < 0) return com.calcsuite.core.CalcError(ErrorKind.InvalidInput, "n must not be negative")
        if (r.signum() < 0) return com.calcsuite.core.CalcError(ErrorKind.InvalidInput, "r must not be negative")
        if (r > n) return com.calcsuite.core.CalcError(ErrorKind.InvalidInput, "r must not exceed n")
        return null
    }

    private fun enumerateFactorCount(r: BigInteger): Int = r.min(BigInteger.valueOf(12L)).toInt()

    private fun enumerateFactors(n: BigInteger, r: BigInteger, descending: Boolean): String {
        val count = enumerateFactorCount(r)
        if (count == 0) return "1"
        val factors = ArrayList<String>(count)
        var index = 0
        while (index < count) {
            val value = if (descending) n.subtract(BigInteger.valueOf(index.toLong()))
            else n.subtract(r).add(ONE).add(BigInteger.valueOf(index.toLong()))
            factors.add(value.toString())
            index++
        }
        val body = factors.joinToString(" × ")
        return if (r > BigInteger.valueOf(count.toLong())) "$body × …" else body
    }

    private fun countFactors(r: BigInteger): String {
        val count = enumerateFactorCount(r)
        if (count == 0) return "1"
        val body = (1..count).joinToString(" × ")
        return if (r > BigInteger.valueOf(count.toLong())) "$body × …" else body
    }

    /** Exact product of the numerator factors, without printing every factor. */
    private fun plainFactorProduct(n: BigInteger, r: BigInteger, descending: Boolean): BigInteger {
        var result = ONE
        var index = ZERO
        while (index < r) {
            val value = if (descending) n.subtract(index) else n.subtract(r).add(ONE).add(index)
            result = result.multiply(value)
            index = index.add(ONE)
        }
        return result
    }

    private fun factorialValueText(r: BigInteger): String {
        val factorial = factorial(r)
        return when (factorial) {
            is CalcResult.Ok -> factorial.value.toString()
            is CalcResult.Err -> "r!"
        }
    }

    // ----------------------------------------------------------- probability

    /**
     * Binomial probability of exactly [k] successes in [n] independent trials
     * with success probability [p]: C(n, k) · pᵏ · (1 − p)^(n−k).
     */
    fun probabilityOfExactlySuccesses(n: BigInteger, k: BigInteger, p: BigDecimal): CalcResult<ProbabilityResult> {
        if (n.signum() < 0) return err(ErrorKind.InvalidInput, "n must not be negative")
        if (k.signum() < 0) return err(ErrorKind.InvalidInput, "k must not be negative")
        if (k > n) return err(ErrorKind.InvalidInput, "k must not exceed n")
        if (p.signum() < 0 || p > BigDecimal.ONE) {
            return err(ErrorKind.Domain, "The probability must be between 0 and 1")
        }
        if (n > BigInteger.valueOf(100_000L)) {
            return err(ErrorKind.Unavailable, "n is too large for this probability calculation")
        }
        val computed = nCr(n, k)
        val choices = when (computed) {
            is CalcResult.Ok -> computed.value
            is CalcResult.Err -> return computed
        }
        val success = p.pow(k.toInt(), ALG_CTX)
        val failure = BigDecimal.ONE.subtract(p).pow(n.subtract(k).toInt(), ALG_CTX)
        val probability = BigDecimal(choices).multiply(success, ALG_CTX).multiply(failure, ALG_CTX)
        val steps = listOf(
            "Binomial model: $n independent trials, each succeeding with probability ${plain(p)}",
            "C($n, $k) = $choices arrangements of exactly $k successes",
            "p^k = ${plain(roundForText(success))} and (1 − p)^(n−k) = ${plain(roundForText(failure))}",
            "Probability = $choices × ${plain(roundForText(success))} × ${plain(roundForText(failure))} = " +
                plain(roundForText(probability, 20)),
        )
        return ok(
            ProbabilityResult(
                probability = probability,
                formula = "P(X = $k) = C($n, $k) · p^k · (1 − p)^(n − k)",
                steps = steps,
                note = "The binomial model assumes independent trials with a constant success probability p",
            ),
        )
    }

    /**
     * Hypergeometric selection probability: exactly [exactFavorable] of the
     * [draws] drawn items are among the [favorable] special items of a
     * population of [total] — e.g. a lottery draw.
     */
    fun selectionProbability(
        total: BigInteger,
        favorable: BigInteger,
        draws: BigInteger,
        exactFavorable: BigInteger,
    ): CalcResult<ProbabilityResult> {
        if (total.signum() < 0) return err(ErrorKind.InvalidInput, "The population must not be negative")
        if (favorable.signum() < 0 || favorable > total) {
            return err(ErrorKind.InvalidInput, "The number of special items must be between 0 and the population")
        }
        if (draws.signum() < 0 || draws > total) {
            return err(ErrorKind.InvalidInput, "The number of drawn items must be between 0 and the population")
        }
        if (exactFavorable.signum() < 0 || exactFavorable > draws || exactFavorable > favorable) {
            return err(ErrorKind.InvalidInput, "The matches must be between 0 and min(drawn, special)")
        }
        val failures = draws.subtract(exactFavorable)
        val missPool = total.subtract(favorable)
        val denominatorComputed = nCr(total, draws)
        val denominator = when (denominatorComputed) {
            is CalcResult.Ok -> denominatorComputed.value
            is CalcResult.Err -> return denominatorComputed
        }
        val hitsComputed = nCr(favorable, exactFavorable)
        val hits = when (hitsComputed) {
            is CalcResult.Ok -> hitsComputed.value
            is CalcResult.Err -> return hitsComputed
        }
        val missesComputed = if (failures > missPool) ok(ZERO) else nCr(missPool, failures)
        val misses = when (missesComputed) {
            is CalcResult.Ok -> missesComputed.value
            is CalcResult.Err -> return missesComputed
        }
        val numerator = hits.multiply(misses)
        val probability = BigDecimal(numerator).divide(BigDecimal(denominator), ALG_CTX)
        val steps = listOf(
            "Exact matches: $exactFavorable of $draws drawn items are among the $favorable special items",
            "Favourable selections: C($favorable, $exactFavorable) × C($missPool, $failures) = " +
                "$hits × $misses = $numerator",
            "All selections: C($total, $draws) = $denominator",
            "Probability = $numerator ÷ $denominator = " + plain(roundForText(probability, 20)),
        )
        return ok(
            ProbabilityResult(
                probability = probability,
                formula = "P = C($favorable, $exactFavorable) · C(${total.subtract(favorable)}, $failures) ÷ C($total, $draws)",
                steps = steps,
                note = "Every subset of $draws items is equally likely; drawing is without replacement (hypergeometric)",
            ),
        )
    }
}
