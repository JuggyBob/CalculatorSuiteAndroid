package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigInteger

data class GcfLcmResult(
    val values: List<BigInteger>,
    val gcf: BigInteger,
    val lcm: BigInteger,
    /** Prime factorizations when they could be computed in time. */
    val factorizations: List<Factorization>,
    val steps: List<String>,
    val notes: List<String>,
)

/**
 * Greatest common factor and least common multiple.
 *
 * Conventions:
 * - Negatives are treated as their absolute values.
 * - A single zero inside a set is ignored for the GCF; an all-zero set has no GCF.
 * - The LCM of anything that contains a zero is 0 (`lcm(x, 0) = 0`).
 */
object GcfLcmEngine {

    private val ZERO: BigInteger = BigInteger.ZERO

    /** Parses a comma/semicolon/new-line separated list of whole numbers. */
    fun parseIntegers(text: String): CalcResult<List<BigInteger>> {
        val tokens = text.split(',', ';', '\n', '\r', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one whole number")
        val values = ArrayList<BigInteger>(tokens.size)
        for (token in tokens) {
            val value = Num.parseIntOrNull(token)
                ?: return err(ErrorKind.InvalidInput, "Could not read \"$token\" as a whole number")
            values.add(value)
        }
        return ok(values)
    }

    /** GCF of two integers; `gcf(0, 0)` is undefined. */
    fun gcf(a: BigInteger, b: BigInteger): CalcResult<BigInteger> {
        if (a.signum() == 0 && b.signum() == 0) {
            return err(ErrorKind.Undefined, "GCF of 0 and 0 is undefined")
        }
        return ok(a.abs().gcd(b.abs()))
    }

    fun gcf(values: List<BigInteger>): CalcResult<BigInteger> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        val nonZero = values.filter { it.signum() != 0 }
        if (nonZero.isEmpty()) return err(ErrorKind.Undefined, "GCF of 0 and 0 is undefined")
        return ok(nonZero.map { it.abs() }.reduce { acc, value -> acc.gcd(value) })
    }

    /** LCM of two integers; `lcm(x, 0) = 0` by convention. */
    fun lcm(a: BigInteger, b: BigInteger): CalcResult<BigInteger> {
        if (a.signum() == 0 || b.signum() == 0) return ok(ZERO)
        val left = a.abs()
        val right = b.abs()
        return ok(left.divide(left.gcd(right)).multiply(right))
    }

    fun lcm(values: List<BigInteger>): CalcResult<BigInteger> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        if (values.any { it.signum() == 0 }) return ok(ZERO)
        val abs = values.map { it.abs() }
        return ok(abs.reduce { acc, value -> acc.divide(acc.gcd(value)).multiply(value) })
    }

    /** Step-by-step Euclid chain, e.g. `24 = 1 × 18 + 6`. */
    fun euclidSteps(a: BigInteger, b: BigInteger): List<String> {
        val steps = ArrayList<String>()
        var x = a.abs()
        var y = b.abs()
        if (x < y) {
            val swap = x
            x = y
            y = swap
        }
        steps.add("Euclid's algorithm on ${x} and ${y}")
        while (y.signum() != 0) {
            val quotient = x.divide(y)
            val remainder = x.mod(y)
            steps.add("$x = $quotient × $y + $remainder")
            x = y
            y = remainder
        }
        steps.add("The remainder is 0, so the greatest common factor is $x")
        return steps
    }

    /** Full GCF/LCM report: values, both results, factorizations, steps and notes. */
    fun analyze(values: List<BigInteger>): CalcResult<GcfLcmResult> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        val nonZero = values.filter { it.signum() != 0 }
        if (nonZero.isEmpty()) return err(ErrorKind.Undefined, "GCF of 0 and 0 is undefined")
        val abs = nonZero.map { it.abs() }
        val hasZero = values.any { it.signum() == 0 }
        val gcfValue = abs.reduce { acc, value -> acc.gcd(value) }
        val lcmValue = if (hasZero) {
            ZERO
        } else {
            abs.reduce { acc, value -> acc.divide(acc.gcd(value)).multiply(value) }
        }

        val notes = ArrayList<String>()
        if (hasZero) {
            notes.add("A zero is present, so the LCM is 0 by convention (lcm(x, 0) = 0)")
            notes.add("Zeros are skipped when finding the greatest common factor")
        }
        if (values.any { it.signum() < 0 }) {
            notes.add("Negative values are used as their absolute values")
        }

        val steps = ArrayList<String>()
        when (abs.size) {
            1 -> steps.add("Only one non-zero value, so GCF = LCM = ${abs[0]}")
            2 -> {
                steps.addAll(euclidSteps(abs[0], abs[1]))
                steps.add(
                    "LCM = |${abs[0]} × ${abs[1]}| ÷ GCF = ${abs[0].multiply(abs[1])} ÷ $gcfValue = $lcmValue",
                )
            }
            else -> {
                var runningGcf = abs[0]
                for (index in 1 until abs.size) {
                    runningGcf = runningGcf.gcd(abs[index])
                    steps.add("GCF(${abs[index - 1]}, ${abs[index]}) folded in: running GCF = $runningGcf")
                }
                var runningLcm = abs[0]
                for (index in 1 until abs.size) {
                    val previous = runningLcm
                    runningLcm = previous.divide(previous.gcd(abs[index])).multiply(abs[index])
                    steps.add(
                        "LCM($previous, ${abs[index]}) = ${abs[index].multiply(previous)} ÷ " +
                            "${previous.gcd(abs[index])} = $runningLcm",
                    )
                }
            }
        }

        val factorizations = ArrayList<Factorization>()
        if (abs.size <= 4 && abs.all { it.toString().length <= 18 }) {
            for (value in abs) {
                val factorization = PrimeEngine.factorizeDetailed(value)
                if (factorization is CalcResult.Ok) {
                    factorizations.add(factorization.value)
                } else {
                    break
                }
            }
            if (factorizations.size == abs.size) {
                if (abs.size >= 2) {
                    val common = commonPrimePowers(factorizations)
                    val union = unionPrimePowers(factorizations)
                    if (common.isNotEmpty()) {
                        steps.add("Common prime powers (smallest exponent of each prime): ${common.joinToString(" × ")} = $gcfValue")
                    }
                    if (union.isNotEmpty()) {
                        steps.add("All prime powers (largest exponent of each prime): ${union.joinToString(" × ")} = $lcmValue")
                    }
                }
            } else {
                factorizations.clear()
                notes.add("Prime factorizations were skipped because a value is too large to factor quickly")
            }
        } else if (abs.size > 4) {
            notes.add("Prime factorizations are shown for up to four values")
        }

        return ok(
            GcfLcmResult(
                values = values.toList(),
                gcf = gcfValue,
                lcm = lcmValue,
                factorizations = factorizations,
                steps = steps,
                notes = notes,
            ),
        )
    }

    private fun commonPrimePowers(factorizations: List<Factorization>): List<String> {
        val shared = LinkedHashMap<BigInteger, Int>()
        factorizations.first().factors.forEach { shared[it.prime] = it.exponent }
        for (factorization in factorizations.drop(1)) {
            val map = factorization.factors.associate { it.prime to it.exponent }
            val keys = shared.keys.toList()
            for (prime in keys) {
                val exponent = map[prime]
                if (exponent == null) shared.remove(prime) else shared[prime] = minOf(shared.getValue(prime), exponent)
            }
        }
        return shared.map { (prime, exponent) ->
            if (exponent == 1) prime.toString() else "$prime^$exponent"
        }
    }

    private fun unionPrimePowers(factorizations: List<Factorization>): List<String> {
        val all = LinkedHashMap<BigInteger, Int>()
        for (factorization in factorizations) {
            for (factor in factorization.factors) {
                all[factor.prime] = maxOf(all[factor.prime] ?: 0, factor.exponent)
            }
        }
        return all.entries.sortedBy { it.key }.map { (prime, exponent) ->
            if (exponent == 1) prime.toString() else "$prime^$exponent"
        }
    }
}
