package com.calcsuite.engine.algebra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

/** Precision guards for the shared root helpers. */
class AlgebraSupportTest {

    @Test
    fun bigSqrtKeepsAllDigitsForATinyValue() {
        // An absolute stop tolerance fires after one Newton step here, while
        // the root still has only about 32 correct digits.
        val root = BigDecimal("1.414213562373095048801688724209698E-30")
        val value = root.multiply(root)
        val result = bigSqrt(value)
        assertNotNull(result)
        assertEquals("square root lost digits: $result", 0, result!!.compareTo(root))
    }

    @Test
    fun bigNthRootKeepsAllDigitsForATinyValue() {
        val root = BigDecimal("1.259921049894873164767210607278228E-20")
        val value = root.multiply(root).multiply(root)
        val result = bigNthRoot(value, 3)
        assertNotNull(result)
        assertEquals("cube root lost digits: $result", 0, result!!.compareTo(root))
    }
}
