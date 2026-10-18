package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class PrimeEngineTest {

    private fun big(text: String) = BigInteger(text)

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun <T> okValue(result: CalcResult<T>): T {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return result.valueOrNull()!!
    }

    // ---- primality ----------------------------------------------------------

    @Test
    fun smallPrimes() {
        val check = PrimeEngine.isPrime(big("97"))
        assertTrue(check.isPrime)
        assertTrue(check.proven)
        assertTrue(PrimeEngine.isPrime(big("2")).isPrime)
        assertTrue(PrimeEngine.isPrime(big("3")).isPrime)
        assertTrue(!PrimeEngine.isPrime(big("4")).isPrime)
    }

    @Test
    fun carmichaelNumber561IsComposite() {
        val check = PrimeEngine.isPrime(big("561"))
        assertTrue(!check.isPrime)
        assertTrue(check.proven)
        assertTrue(check.explanation.contains("composite"))
    }

    @Test
    fun oneAndZeroAndNegativesAreNotPrime() {
        val one = PrimeEngine.isPrime(BigInteger.ONE)
        assertTrue(!one.isPrime)
        assertTrue(one.explanation.contains("neither prime nor composite"))
        val zero = PrimeEngine.isPrime(BigInteger.ZERO)
        assertTrue(!zero.isPrime)
        assertTrue(zero.explanation.contains("0 is not prime"))
        val negative = PrimeEngine.isPrime(big("-7"))
        assertTrue(!negative.isPrime)
        assertTrue(negative.explanation.contains("Negative"))
    }

    @Test
    fun mersennePrimeBelowTheDeterministicBoundIsProven() {
        // 2^61 − 1 = 2305843009213693951 is prime and < 3.3 × 10^24.
        val check = PrimeEngine.isPrime(big("2305843009213693951"))
        assertTrue(check.isPrime)
        assertTrue(check.proven)
    }

    @Test
    fun largeSixtyFourBitPrimeIsProven() {
        // 18446744073709551557 is the largest prime below 2^64.
        val check = PrimeEngine.isPrime(big("18446744073709551557"))
        assertTrue(check.isPrime)
        assertTrue(check.proven)
    }

    @Test
    fun semiprimeOfTwoLargePrimesIsComposite() {
        val check = PrimeEngine.isPrime(big("1000036000099"))
        assertTrue(!check.isPrime)
        assertTrue(check.proven)
    }

    // ---- neighbours ---------------------------------------------------------

    @Test
    fun nextPrimeExamples() {
        assertEquals(big("101"), okValue(PrimeEngine.nextPrime(big("100"))))
        assertEquals(big("97"), okValue(PrimeEngine.nextPrime(big("89"))))
        assertEquals(big("3"), okValue(PrimeEngine.nextPrime(big("2"))))
    }

    @Test
    fun previousPrimeExamples() {
        assertEquals(big("97"), PrimeEngine.previousPrime(big("100")))
        assertEquals(big("89"), PrimeEngine.previousPrime(big("97")))
        assertEquals(big("2"), PrimeEngine.previousPrime(big("3")))
        assertNull(PrimeEngine.previousPrime(big("2")))
    }

    // ---- factorization ------------------------------------------------------

    @Test
    fun factorizationOf360() {
        assertEquals(
            listOf(big("2"), big("2"), big("2"), big("3"), big("3"), big("5")),
            okValue(PrimeEngine.factorize(big("360"))),
        )
    }

    @Test
    fun factorizedTextGroupsPowers() {
        val factorization = okValue(PrimeEngine.factorizeDetailed(big("360")))
        assertEquals("360 = 2^3 × 3^2 × 5", factorization.text)
        assertEquals(3, factorization.factors.size)
        assertEquals(big("2"), factorization.factors[0].prime)
        assertEquals(3, factorization.factors[0].exponent)
        assertEquals(big("5"), factorization.factors[2].prime)
        assertEquals(1, factorization.factors[2].exponent)
    }

    @Test
    fun primeFactorsIntoItself() {
        assertEquals(listOf(big("97")), okValue(PrimeEngine.factorize(big("97"))))
        assertEquals(
            listOf(big("2305843009213693951")),
            okValue(PrimeEngine.factorize(big("2305843009213693951"))),
        )
    }

    @Test
    fun factorizationOfLargeSemiprime() {
        assertEquals(
            listOf(big("1000003"), big("1000033")),
            okValue(PrimeEngine.factorize(big("1000036000099"))),
        )
    }

    @Test
    fun factorizationOfOneAndValidation() {
        assertTrue(okValue(PrimeEngine.factorize(BigInteger.ONE)).isEmpty())
        assertTrue(okValue(PrimeEngine.factorizeDetailed(BigInteger.ONE)).text.contains("no prime factors"))
        assertEquals(ErrorKind.Domain, kind(PrimeEngine.factorize(BigInteger.ZERO)))
        assertEquals(ErrorKind.InvalidInput, kind(PrimeEngine.factorize(big("-5"))))
    }
}
