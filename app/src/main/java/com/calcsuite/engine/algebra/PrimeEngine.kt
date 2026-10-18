package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigInteger

/** Result of a primality test; [proven] distinguishes a proof from a probable prime. */
data class PrimeCheck(
    val n: BigInteger,
    val isPrime: Boolean,
    val proven: Boolean,
    val explanation: String,
)

data class PrimeFactor(val prime: BigInteger, val exponent: Int)

data class Factorization(
    val n: BigInteger,
    val factors: List<PrimeFactor>,
) {
    /** e.g. `360 = 2^3 × 3^2 × 5`; `1` has no prime factors. */
    val text: String
        get() {
            val body = if (factors.isEmpty()) "1" else factors.joinToString(" × ") { factor ->
                if (factor.exponent == 1) factor.prime.toString()
                else "${factor.prime}^${factor.exponent}"
            }
            return if (factors.isEmpty()) "1 (no prime factors)" else "$n = $body"
        }
}

/**
 * Primality testing, factorization and nearest primes for arbitrary integers.
 *
 * - Below 3 317 044 064 679 887 385 961 981 the Miller-Rabin bases
 *   2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37 give a **proof**.
 * - Above that bound 40 fixed bases are used: the answer is a strong
 *   probable prime (no known counterexample, but not a proof).
 * - Factorization uses trial division by primes up to 1000, then Pollard's
 *   rho with Miller-Rabin tests.
 */
object PrimeEngine {

    private val ZERO: BigInteger = BigInteger.ZERO
    private val ONE: BigInteger = BigInteger.ONE
    private val TWO: BigInteger = BigInteger.valueOf(2L)
    private val THREE: BigInteger = BigInteger.valueOf(3L)

    /** Numbers with more digits than this are only tested, never factored. */
    const val MAX_FACTOR_DIGITS = 27

    private val SMALL_PRIMES: List<BigInteger> = run {
        val limit = 1000
        val composite = BooleanArray(limit + 1)
        val primes = ArrayList<BigInteger>()
        var i = 2
        while (i <= limit) {
            if (!composite[i]) {
                primes.add(BigInteger.valueOf(i.toLong()))
                var j = i * i
                while (j <= limit) {
                    composite[j] = true
                    j += i
                }
            }
            i++
        }
        primes
    }

    private val DETERMINISTIC_LIMIT: BigInteger = BigInteger("3317044064679887385961981")

    private val DETERMINISTIC_BASES: List<BigInteger> =
        listOf(2L, 3L, 5L, 7L, 11L, 13L, 17L, 19L, 23L, 29L, 31L, 37L).map { BigInteger.valueOf(it) }

    private val PROBABLE_BASES: List<BigInteger> = run {
        val bases = ArrayList<BigInteger>()
        var candidate = BigInteger.valueOf(2L)
        while (bases.size < 40) {
            if (isProbablePrime(candidate, 12, DETERMINISTIC_BASES)) bases.add(candidate)
            candidate = candidate.add(ONE)
        }
        bases
    }

    // ------------------------------------------------------------- primality

    fun isPrime(n: BigInteger): PrimeCheck {
        if (n < TWO) {
            val explanation = when {
                n.signum() < 0 -> "Negative numbers are not prime; a prime is greater than 1"
                n.signum() == 0 -> "0 is not prime: every integer divides it"
                else -> "1 is neither prime nor composite: a prime has exactly two divisors"
            }
            return PrimeCheck(n = n, isPrime = false, proven = true, explanation = explanation)
        }
        for (prime in SMALL_PRIMES) {
            if (n == prime) return PrimeCheck(n = n, isPrime = true, proven = true, explanation = "$n is prime")
            if (n.mod(prime).signum() == 0) {
                return PrimeCheck(
                    n = n,
                    isPrime = false,
                    proven = true,
                    explanation = "$n is composite: it is divisible by $prime",
                )
            }
        }
        val deterministic = n < DETERMINISTIC_LIMIT
        val bases = if (deterministic) DETERMINISTIC_BASES else PROBABLE_BASES
        for (base in bases) {
            if (base >= n) continue
            if (!isProbablePrime(n, 1, listOf(base))) {
                return PrimeCheck(
                    n = n,
                    isPrime = false,
                    proven = true,
                    explanation = "$n is composite: $base is a Miller-Rabin witness against primality",
                )
            }
        }
        return if (deterministic) {
            PrimeCheck(
                n = n,
                isPrime = true,
                proven = true,
                explanation = "$n is prime (proven: the deterministic Miller-Rabin bases are exact below 3.3 × 10^24)",
            )
        } else {
            PrimeCheck(
                n = n,
                isPrime = true,
                proven = false,
                explanation = "$n passed 40 Miller-Rabin rounds with fixed bases, so it is a strong probable prime — no known counterexample",
            )
        }
    }

    private fun isProbablePrime(n: BigInteger, rounds: Int, bases: List<BigInteger>): Boolean {
        if (n < TWO) return false
        if (n == TWO || n == THREE) return true
        if (n.mod(TWO).signum() == 0) return false
        for (base in bases.take(rounds)) {
            if (base >= n) continue
            if (!passesMillerRabin(n, base)) return false
        }
        return true
    }

    private fun passesMillerRabin(n: BigInteger, base: BigInteger): Boolean {
        var d = n.subtract(ONE)
        var power = 0
        while (!d.testBit(0)) {
            d = d.shiftRight(1)
            power++
        }
        var x = base.modPow(d, n)
        if (x == ONE || x == n.subtract(ONE)) return true
        repeat(power - 1) {
            x = x.multiply(x).mod(n)
            if (x == n.subtract(ONE)) return true
        }
        return false
    }

    // ---------------------------------------------------------- factorization

    /** Prime factors of [n] with multiplicity, sorted ascending. `1` has none. */
    fun factorize(n: BigInteger): CalcResult<List<BigInteger>> {
        if (n.signum() < 0) return err(ErrorKind.InvalidInput, "Enter a positive integer to factor")
        if (n.signum() == 0) return err(ErrorKind.Domain, "0 has no prime factorization: every prime divides it")
        if (n == ONE) return ok(emptyList())
        if (n.toString().length > MAX_FACTOR_DIGITS) {
            if (isPrime(n).isPrime) return ok(listOf(n))
            return err(
                ErrorKind.Unavailable,
                "This number is too large to factor quickly; the prime test is still exact",
            )
        }
        val factors = ArrayList<BigInteger>()
        factorInto(n, factors)
        factors.sort()
        var product = ONE
        for (factor in factors) product = product.multiply(factor)
        if (product != n) {
            return err(ErrorKind.Undefined, "Internal factorization check failed; no result was produced")
        }
        return ok(factors)
    }

    /** [factorize] grouped into prime powers for display. */
    fun factorizeDetailed(n: BigInteger): CalcResult<Factorization> {
        val list = factorize(n)
        return when (list) {
            is CalcResult.Err -> list
            is CalcResult.Ok -> {
                val grouped = LinkedHashMap<BigInteger, Int>()
                for (factor in list.value) grouped[factor] = (grouped[factor] ?: 0) + 1
                ok(Factorization(n, grouped.map { PrimeFactor(it.key, it.value) }))
            }
        }
    }

    /** Factorization of several numbers at once (used by the GCF & LCM tool). */
    fun factorizeAll(values: List<BigInteger>): CalcResult<List<Factorization>> {
        val out = ArrayList<Factorization>(values.size)
        for (value in values) {
            when (val factorization = factorizeDetailed(value)) {
                is CalcResult.Ok -> out.add(factorization.value)
                is CalcResult.Err -> return factorization
            }
        }
        return ok(out)
    }

    private fun factorInto(n: BigInteger, out: MutableList<BigInteger>) {
        if (n == ONE) return
        if (isPrime(n).isPrime) {
            out.add(n)
            return
        }
        var remaining = n
        for (prime in SMALL_PRIMES) {
            while (remaining.mod(prime).signum() == 0) {
                out.add(prime)
                remaining = remaining.divide(prime)
            }
            if (remaining == ONE) return
        }
        if (remaining == ONE) return
        if (isPrime(remaining).isPrime) {
            out.add(remaining)
            return
        }
        val divisor = pollardRho(remaining)
        factorInto(divisor, out)
        factorInto(remaining.divide(divisor), out)
    }

    /** Pollard's rho with Floyd cycle detection; deterministic c = 1, 2, 3, … */
    private fun pollardRho(n: BigInteger): BigInteger {
        if (n.mod(TWO).signum() == 0) return TWO
        if (n.mod(THREE).signum() == 0) return THREE
        var c = ONE
        while (true) {
            var x = TWO
            var y = TWO
            var divisor = ONE
            while (divisor == ONE) {
                x = x.multiply(x).add(c).mod(n)
                y = y.multiply(y).add(c).mod(n)
                y = y.multiply(y).add(c).mod(n)
                divisor = x.subtract(y).abs().gcd(n)
            }
            if (divisor != n) return divisor
            c = c.add(ONE)
        }
    }

    // ------------------------------------------------------------- neighbours

    /** Smallest prime strictly greater than [n]. */
    fun nextPrime(n: BigInteger): CalcResult<BigInteger> {
        var candidate = n.add(ONE)
        if (candidate <= TWO) return ok(TWO)
        if (!candidate.testBit(0)) candidate = candidate.add(ONE)
        while (!isPrime(candidate).isPrime) candidate = candidate.add(TWO)
        return ok(candidate)
    }

    /** Largest prime strictly smaller than [n], or null when n ≤ 2. */
    fun previousPrime(n: BigInteger): BigInteger? {
        if (n <= TWO) return null
        var candidate = n.subtract(ONE)
        if (candidate == TWO) return TWO
        if (!candidate.testBit(0)) candidate = candidate.subtract(ONE)
        while (candidate >= TWO) {
            if (isPrime(candidate).isPrime) return candidate
            candidate = candidate.subtract(TWO)
        }
        return null
    }
}
