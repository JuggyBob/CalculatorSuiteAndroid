package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.security.SecureRandom
import java.util.Random

enum class RandomParity { Any, Even, Odd }

data class IntegerGeneratorConfig(
    val min: BigInteger,
    val max: BigInteger,
    val count: Int,
    val unique: Boolean = false,
    val parity: RandomParity = RandomParity.Any,
    /** Same seed + same rules → same list (java.util.Random). */
    val seed: Long? = null,
    /** Use java.security.SecureRandom instead; the seed is then ignored. */
    val useSecureRandom: Boolean = false,
)

data class DecimalConfig(
    val min: BigDecimal,
    val max: BigDecimal,
    val count: Int,
    val decimalPlaces: Int = 2,
    val seed: Long? = null,
    val useSecureRandom: Boolean = false,
)

data class GenerationResult<T>(
    val values: List<T>,
    val notes: List<String>,
    val seeded: Boolean,
    val secure: Boolean,
)

/**
 * Random number generation.
 *
 * Both endpoints are **inclusive** in either mode. A seeded generator uses
 * `java.util.Random` and is deterministic — it is fine for games and test
 * data, but not cryptographically secure. Turning the secure option on
 * switches to `java.security.SecureRandom` and ignores the seed.
 */
object RandomEngine {

    /** Upper bound for generated lists, so the UI stays responsive. */
    const val MAX_COUNT = 1000

    fun generateIntegers(config: IntegerGeneratorConfig): CalcResult<GenerationResult<BigInteger>> {
        val notes = ArrayList<String>()
        var min = config.min
        var max = config.max
        if (min > max) {
            val swap = min
            min = max
            max = swap
            notes.add("The minimum was larger than the maximum, so the two bounds were swapped")
        }
        if (config.count < 1) return err(ErrorKind.InvalidInput, "Count must be at least 1")
        if (config.count > MAX_COUNT) {
            return err(ErrorKind.InvalidInput, "At most $MAX_COUNT values can be generated at once")
        }
        val rng = Rng(config.seed, config.useSecureRandom, notes)

        val domainMin: BigInteger
        val domainMax: BigInteger
        val shift: (BigInteger) -> BigInteger
        when (config.parity) {
            RandomParity.Any -> {
                domainMin = min
                domainMax = max
                shift = { it }
            }
            RandomParity.Even -> {
                domainMin = ceilDiv2(min)
                domainMax = floorDiv2(max)
                if (domainMin > domainMax) {
                    return err(ErrorKind.Domain, "There is no even number in the selected range")
                }
                shift = { it.shiftLeft(1) }
            }
            RandomParity.Odd -> {
                domainMin = ceilDiv2(min.subtract(BigInteger.ONE))
                domainMax = floorDiv2(max.subtract(BigInteger.ONE))
                if (domainMin > domainMax) {
                    return err(ErrorKind.Domain, "There is no odd number in the selected range")
                }
                shift = { it.shiftLeft(1).add(BigInteger.ONE) }
            }
        }
        val domainSize = domainMax.subtract(domainMin).add(BigInteger.ONE)
        val parityText = when (config.parity) {
            RandomParity.Any -> "value"
            RandomParity.Even -> "even value"
            RandomParity.Odd -> "odd value"
        }
        if (config.unique && BigInteger.valueOf(config.count.toLong()) > domainSize) {
            return err(
                ErrorKind.Domain,
                "Cannot generate ${config.count} unique values from a range of $domainSize",
            )
        }

        val values = ArrayList<BigInteger>(config.count)
        if (config.unique && domainSize <= BigInteger.valueOf(20_000L)) {
            val pool = ArrayList<BigInteger>(domainSize.toInt())
            var cursor = domainMin
            while (cursor <= domainMax) {
                pool.add(shift(cursor))
                cursor = cursor.add(BigInteger.ONE)
            }
            rng.shuffle(pool)
            values.addAll(pool.subList(0, config.count))
            notes.add("All ${config.count} values are distinct (drawn from a $domainSize $parityText pool)")
        } else if (config.unique) {
            val seen = LinkedHashSet<BigInteger>()
            var attempts = 0
            val attemptLimit = config.count * 10_000 + 1_000
            while (seen.size < config.count && attempts < attemptLimit) {
                seen.add(shift(rng.nextBigInteger(domainSize).add(domainMin)))
                attempts++
            }
            if (seen.size < config.count) {
                return err(ErrorKind.Unavailable, "Could not draw enough distinct values; try a smaller count")
            }
            values.addAll(seen)
            notes.add("All ${config.count} values are distinct")
        } else {
            repeat(config.count) {
                values.add(shift(rng.nextBigInteger(domainSize).add(domainMin)))
            }
        }
        notes.add("Both endpoints are inclusive: $min and $max can appear")
        return ok(
            GenerationResult(
                values = values,
                notes = notes,
                seeded = rng.seeded,
                secure = rng.secure,
            ),
        )
    }

    fun generateDecimals(config: DecimalConfig): CalcResult<GenerationResult<BigDecimal>> {
        val notes = ArrayList<String>()
        var min = config.min
        var max = config.max
        if (min > max) {
            val swap = min
            min = max
            max = swap
            notes.add("The minimum was larger than the maximum, so the two bounds were swapped")
        }
        if (config.count < 1) return err(ErrorKind.InvalidInput, "Count must be at least 1")
        if (config.count > MAX_COUNT) {
            return err(ErrorKind.InvalidInput, "At most $MAX_COUNT values can be generated at once")
        }
        if (config.decimalPlaces < 0 || config.decimalPlaces > 12) {
            return err(ErrorKind.InvalidInput, "Decimal places must be between 0 and 12")
        }
        val scale = config.decimalPlaces
        val low = min.setScale(scale, RoundingMode.CEILING).unscaledValue()
        val high = max.setScale(scale, RoundingMode.FLOOR).unscaledValue()
        if (low > high) {
            return err(
                ErrorKind.Domain,
                "There is no value with $scale decimal place(s) inside the selected range",
            )
        }
        val domainSize = high.subtract(low).add(BigInteger.ONE)
        val rng = Rng(config.seed, config.useSecureRandom, notes)
        val values = ArrayList<BigDecimal>(config.count)
        repeat(config.count) {
            val unscaled = rng.nextBigInteger(domainSize).add(low)
            values.add(BigDecimal(unscaled, scale))
        }
        notes.add("Values carry exactly $scale decimal place(s) and both endpoints are inclusive")
        return ok(
            GenerationResult(
                values = values,
                notes = notes,
                seeded = rng.seeded,
                secure = rng.secure,
            ),
        )
    }

    // ------------------------------------------------------------- internals

    private fun floorDiv2(value: BigInteger): BigInteger = value.shiftRight(1)

    private fun ceilDiv2(value: BigInteger): BigInteger = value.add(BigInteger.ONE).shiftRight(1)

    private class Rng(seed: Long?, secure: Boolean, notes: MutableList<String>) {
        val secureRandom: SecureRandom? = if (secure) SecureRandom() else null
        val random: Random? = if (secure) null else if (seed != null) Random(seed) else Random()
        val seeded: Boolean = !secure && seed != null
        val secure: Boolean = secure

        init {
            when {
                secure -> notes.add(
                    "Values come from java.security.SecureRandom (cryptographically strong); the seed is ignored",
                )
                seed != null -> notes.add(
                    "Seeded with $seed: the same rules reproduce this exact list (java.util.Random is not cryptographically secure)",
                )
                else -> notes.add(
                    "java.util.Random is not cryptographically secure; turn on the secure option for sensitive use",
                )
            }
        }

        fun nextBytes(bytes: ByteArray) {
            secureRandom?.nextBytes(bytes) ?: random!!.nextBytes(bytes)
        }

        /** Uniform value in [0, bound) for bound > 0. */
        fun nextBigInteger(bound: BigInteger): BigInteger {
            if (bound <= BigInteger.ONE) return BigInteger.ZERO
            val bits = bound.bitLength()
            val bytes = ByteArray((bits + 7) / 8)
            val excess = bytes.size * 8 - bits
            while (true) {
                nextBytes(bytes)
                var candidate = BigInteger(1, bytes)
                if (excess > 0) candidate = candidate.shiftRight(excess)
                if (candidate < bound) return candidate
            }
        }

        /** Fisher–Yates shuffle using this source (works for both RNG kinds). */
        fun <T> shuffle(list: MutableList<T>) {
            for (index in list.size - 1 downTo 1) {
                val swapIndex = nextBigInteger(BigInteger.valueOf((index + 1).toLong())).toInt()
                val swap = list[index]
                list[index] = list[swapIndex]
                list[swapIndex] = swap
            }
        }
    }
}
