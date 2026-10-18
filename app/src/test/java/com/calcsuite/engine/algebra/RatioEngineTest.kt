package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class RatioEngineTest {

    private fun bd(text: String) = BigDecimal(text)

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun assertExact(expected: String, actual: BigDecimal) {
        assertEquals("expected exactly $expected but was $actual", 0, actual.compareTo(bd(expected)))
    }

    private fun <T> okValue(result: CalcResult<T>): T {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return result.valueOrNull()!!
    }

    // ---- parse --------------------------------------------------------------

    @Test
    fun parsesSimpleTerms() {
        val terms = okValue(RatioEngine.parse("12 : 18"))
        assertEquals(2, terms.size)
        assertExact("12", terms[0])
        assertExact("18", terms[1])
    }

    @Test
    fun parsesDecimalsAndFractions() {
        val decimals = okValue(RatioEngine.parse("1.5 : 2"))
        assertExact("1.5", decimals[0])
        val fractions = okValue(RatioEngine.parse("3/4 : 1/2"))
        assertExact("0.75", fractions[0])
        assertExact("0.5", fractions[1])
    }

    @Test
    fun parseRejectsBadInput() {
        assertEquals(ErrorKind.InvalidInput, kind(RatioEngine.parse("")))
        assertEquals(ErrorKind.InvalidInput, kind(RatioEngine.parse("12")))
        assertEquals(ErrorKind.InvalidInput, kind(RatioEngine.parse("2 : ")))
        assertEquals(ErrorKind.InvalidInput, kind(RatioEngine.parse("a : b")))
        assertEquals(ErrorKind.DivisionByZero, kind(RatioEngine.parse("2 : 3/0")))
    }

    // ---- simplify -----------------------------------------------------------

    @Test
    fun simplifyIntegers() {
        val simplified = okValue(RatioEngine.simplify("12 : 18"))
        assertEquals("2 : 3", simplified.text)
        assertEquals(BigInteger.valueOf(6), simplified.divisor)
        assertTrue(simplified.steps.any { it.contains("gcd") || it.contains("Greatest") })
    }

    @Test
    fun simplifyClearsDecimals() {
        val simplified = okValue(RatioEngine.simplify("1.5 : 2"))
        assertEquals("3 : 4", simplified.text)
        assertEquals(BigInteger.TEN, simplified.scaleFactor)
    }

    @Test
    fun simplifyFractions() {
        assertEquals("3 : 2", okValue(RatioEngine.simplify("3/4 : 1/2")).text)
    }

    @Test
    fun simplifyRejectsNegativeAndZeroTerms() {
        assertEquals(ErrorKind.Domain, kind(RatioEngine.simplify("2 : -3")))
        assertEquals(ErrorKind.Domain, kind(RatioEngine.simplify("2 : 0")))
        assertEquals(ErrorKind.InvalidInput, kind(RatioEngine.simplify(listOf(bd("5")))))
    }

    // ---- scale --------------------------------------------------------------

    @Test
    fun scaleKeepsTheRatioEquivalent() {
        val scaled = okValue(RatioEngine.scale(listOf(bd("2"), bd("3")), bd("10")))
        assertEquals("20 : 30", scaled.text)
        assertEquals(ErrorKind.Domain, kind(RatioEngine.scale(listOf(bd("2"), bd("3")), bd("0"))))
    }

    @Test
    fun equivalentsAreTenTimesAndTenth() {
        val (up, down) = okValue(RatioEngine.equivalents(listOf(bd("2"), bd("3"))))
        assertEquals("20 : 30", up.text)
        assertEquals("0.2 : 0.3", down.text)
    }

    // ---- split total --------------------------------------------------------

    @Test
    fun splitTwoToThree() {
        val split = okValue(RatioEngine.splitTotal(bd("100"), listOf(bd("2"), bd("3")), 2))
        assertEquals(2, split.shares.size)
        assertExact("40", split.shares[0])
        assertExact("60", split.shares[1])
        assertTrue(split.exactDivision)
        assertExact("0", split.unallocatedRemainder)
    }

    @Test
    fun splitDistributesTheRemainder() {
        val split = okValue(RatioEngine.splitTotal(bd("100"), listOf(bd("1"), bd("1"), bd("1")), 2))
        assertExact("33.34", split.shares[0])
        assertExact("33.33", split.shares[1])
        assertExact("33.33", split.shares[2])
        val sum = split.shares.fold(BigDecimal.ZERO) { acc, share -> acc.add(share) }
        assertExact("100", sum)
        assertExact("0.01", split.remainderDistributed)
        assertExact("0", split.unallocatedRemainder)
    }

    @Test
    fun splitAtZeroDecimalPlaces() {
        val split = okValue(RatioEngine.splitTotal(bd("10"), listOf(bd("1"), bd("1"), bd("1")), 0))
        assertEquals(listOf(4, 3, 3), split.shares.map { it.toInt() })
    }

    @Test
    fun splitRejectsNegativeTotalAndBadScale() {
        assertEquals(ErrorKind.Domain, kind(RatioEngine.splitTotal(bd("-1"), listOf(bd("1"), bd("1")))))
        assertEquals(
            ErrorKind.InvalidInput,
            kind(RatioEngine.splitTotal(bd("10"), listOf(bd("1"), bd("1")), 13)),
        )
    }

    // ---- unknown part -------------------------------------------------------

    @Test
    fun unknownPartUsesTheKnownShare() {
        val solved = okValue(RatioEngine.unknownPart(listOf(bd("2"), bd("3")), 0, bd("40"), 1))
        assertExact("60", solved.value)
        assertExact("20", solved.unit)
        assertTrue(solved.verification.contains("2 : 3"))
    }

    @Test
    fun unknownPartValidatesIndicesAndShare() {
        assertEquals(
            ErrorKind.InvalidInput,
            kind(RatioEngine.unknownPart(listOf(bd("2"), bd("3")), 0, bd("40"), 0)),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            kind(RatioEngine.unknownPart(listOf(bd("2"), bd("3")), 0, bd("40"), 5)),
        )
        assertEquals(
            ErrorKind.Domain,
            kind(RatioEngine.unknownPart(listOf(bd("2"), bd("3")), 0, bd("0"), 1)),
        )
    }

    // ---- combine ------------------------------------------------------------

    @Test
    fun combineChainsThroughTheLink() {
        val combined = okValue(RatioEngine.combine(listOf(bd("1"), bd("2")), listOf(bd("2"), bd("3"))))
        assertEquals("1 : 2 : 3", combined.text)
        assertTrue(combined.steps.isNotEmpty())
    }

    @Test
    fun combineRescalesScaledRatios() {
        val combined = okValue(RatioEngine.combine(listOf(bd("2"), bd("4")), listOf(bd("6"), bd("3"))))
        assertEquals("1 : 2 : 1", combined.text)
    }

    // ---- compare ------------------------------------------------------------

    @Test
    fun compareEqualRatios() {
        val compared = okValue(RatioEngine.compare(listOf(bd("2"), bd("3")), listOf(bd("4"), bd("6"))))
        assertEquals(RatioEngine.RatioRelation.Equal, compared.relation)
        assertTrue(compared.detail.contains("equals"))
    }

    @Test
    fun compareOrdering() {
        val greater = okValue(RatioEngine.compare(listOf(bd("2"), bd("3")), listOf(bd("3"), bd("5"))))
        assertEquals(RatioEngine.RatioRelation.Greater, greater.relation)
        val smaller = okValue(RatioEngine.compare(listOf(bd("2"), bd("3")), listOf(bd("5"), bd("7"))))
        assertEquals(RatioEngine.RatioRelation.Less, smaller.relation)
    }

    @Test
    fun compareNeedsTwoTermRatios() {
        val result = RatioEngine.compare(listOf(bd("1"), bd("2"), bd("3")), listOf(bd("1"), bd("2")))
        assertEquals(ErrorKind.InvalidInput, kind(result))
    }
}
