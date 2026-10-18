package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class RandomEngineTest {

    private fun big(value: Long) = BigInteger.valueOf(value)

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun message(result: CalcResult<*>): String {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    private fun integers(
        min: BigInteger,
        max: BigInteger,
        count: Int,
        unique: Boolean = false,
        parity: RandomParity = RandomParity.Any,
        seed: Long? = null,
        secure: Boolean = false,
    ): GenerationResult<BigInteger> {
        val result = RandomEngine.generateIntegers(
            IntegerGeneratorConfig(
                min = min,
                max = max,
                count = count,
                unique = unique,
                parity = parity,
                seed = seed,
                useSecureRandom = secure,
            ),
        )
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun decimals(
        min: String,
        max: String,
        count: Int,
        places: Int,
        seed: Long? = null,
    ): GenerationResult<BigDecimal> {
        val result = RandomEngine.generateDecimals(
            DecimalConfig(
                min = BigDecimal(min),
                max = BigDecimal(max),
                count = count,
                decimalPlaces = places,
                seed = seed,
            ),
        )
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    // ---- integers -----------------------------------------------------------

    @Test
    fun integersStayInRange() {
        val generated = integers(big(1), big(100), count = 25, seed = 7)
        assertEquals(25, generated.values.size)
        assertTrue(generated.values.all { it >= big(1) && it <= big(100) })
        assertTrue(generated.notes.isNotEmpty())
    }

    @Test
    fun bothEndpointsAreInclusive() {
        val generated = integers(big(5), big(5), count = 4)
        assertTrue(generated.values.all { it == big(5) })
    }

    @Test
    fun boundsAreSwappedWhenReversed() {
        val generated = integers(big(10), big(1), count = 5, seed = 3)
        assertTrue(generated.values.all { it >= big(1) && it <= big(10) })
        assertTrue(generated.notes.any { it.contains("swapped") })
    }

    @Test
    fun parityFiltersValues() {
        val even = integers(big(1), big(10), count = 10, parity = RandomParity.Even, seed = 11)
        assertTrue(even.values.all { it.mod(big(2)) == BigInteger.ZERO })
        val odd = integers(big(1), big(10), count = 10, parity = RandomParity.Odd, seed = 11)
        assertTrue(odd.values.all { it.mod(big(2)) == big(1) })
    }

    @Test
    fun parityWithoutAnyCandidateIsRejected() {
        assertEquals(
            ErrorKind.Domain,
            kind(
                RandomEngine.generateIntegers(
                    IntegerGeneratorConfig(big(1), big(1), 1, parity = RandomParity.Even),
                ),
            ),
        )
        assertEquals(
            ErrorKind.Domain,
            kind(
                RandomEngine.generateIntegers(
                    IntegerGeneratorConfig(big(2), big(2), 1, parity = RandomParity.Odd),
                ),
            ),
        )
    }

    @Test
    fun uniqueValuesAreDistinct() {
        val generated = integers(big(1), big(10), count = 10, unique = true, seed = 5)
        assertEquals(10, generated.values.distinct().size)
        assertTrue(generated.notes.any { it.contains("distinct") })
    }

    @Test
    fun tooManyUniqueValuesIsRejected() {
        val result = RandomEngine.generateIntegers(
            IntegerGeneratorConfig(big(1), big(10), 11, unique = true),
        )
        assertEquals(ErrorKind.Domain, kind(result))
        assertEquals("Cannot generate 11 unique values from a range of 10", message(result))
    }

    @Test
    fun uniqueParityUsesTheFilteredRangeSize() {
        val result = RandomEngine.generateIntegers(
            IntegerGeneratorConfig(big(1), big(10), 6, unique = true, parity = RandomParity.Even),
        )
        assertEquals(ErrorKind.Domain, kind(result))
        assertEquals("Cannot generate 6 unique values from a range of 5", message(result))
    }

    @Test
    fun countLimitsAreEnforced() {
        assertEquals(
            ErrorKind.InvalidInput,
            kind(RandomEngine.generateIntegers(IntegerGeneratorConfig(big(1), big(10), 0))),
        )
        val tooMany = RandomEngine.generateIntegers(
            IntegerGeneratorConfig(big(1), big(1_000_000), RandomEngine.MAX_COUNT + 1),
        )
        assertEquals(ErrorKind.InvalidInput, kind(tooMany))
        assertEquals("At most 1000 values can be generated at once", message(tooMany))
    }

    @Test
    fun seedReproducesTheSameList() {
        val first = integers(big(1), big(1_000_000), count = 10, seed = 42)
        val second = integers(big(1), big(1_000_000), count = 10, seed = 42)
        assertEquals(first.values, second.values)
        val other = integers(big(1), big(1_000_000), count = 10, seed = 43)
        assertNotEquals(first.values, other.values)
    }

    @Test
    fun secureRandomIsUsedWhenRequested() {
        val generated = integers(big(1), big(100), count = 5, seed = 1, secure = true)
        assertTrue(generated.secure)
        assertTrue(generated.values.all { it >= big(1) && it <= big(100) })
        assertTrue(generated.notes.any { it.contains("SecureRandom") })
    }

    @Test
    fun hugeIntegerRange() {
        val min = BigInteger("100000000000000000000")
        val max = min.add(big(10))
        val generated = integers(min, max, count = 20, seed = 9)
        assertTrue(generated.values.all { it >= min && it <= max })
    }

    // ---- decimals -----------------------------------------------------------

    @Test
    fun decimalsStayInRangeWithRequestedScale() {
        val generated = decimals("1", "2", count = 30, places = 2, seed = 21)
        assertTrue(generated.values.all { it >= BigDecimal("1") && it <= BigDecimal("2") })
        assertTrue(generated.values.all { it.scale() == 2 })
    }

    @Test
    fun decimalBoundariesAreInclusive() {
        val generated = decimals("0.50", "0.50", count = 3, places = 2)
        assertTrue(generated.values.all { it.compareTo(BigDecimal("0.50")) == 0 })
    }

    @Test
    fun decimalPlacesAreValidated() {
        val result = RandomEngine.generateDecimals(
            DecimalConfig(BigDecimal("1"), BigDecimal("2"), 1, decimalPlaces = 13),
        )
        assertEquals(ErrorKind.InvalidInput, kind(result))
        assertEquals("Decimal places must be between 0 and 12", message(result))
    }

    @Test
    fun emptyDecimalGridIsRejected() {
        val result = RandomEngine.generateDecimals(
            DecimalConfig(BigDecimal("1.001"), BigDecimal("1.002"), 1, decimalPlaces = 2),
        )
        assertEquals(ErrorKind.Domain, kind(result))
        assertEquals(
            "There is no value with 2 decimal place(s) inside the selected range",
            message(result),
        )
    }

    @Test
    fun decimalSeedReproducesTheSameList() {
        val first = decimals("1", "100", count = 10, places = 3, seed = 1234)
        val second = decimals("1", "100", count = 10, places = 3, seed = 1234)
        assertEquals(first.values, second.values)
    }
}
