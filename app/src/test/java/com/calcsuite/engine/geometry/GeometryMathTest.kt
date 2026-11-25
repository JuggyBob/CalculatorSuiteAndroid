package com.calcsuite.engine.geometry

import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class GeometryMathTest {

    private fun bd(text: String): BigDecimal = BigDecimal(text)

    private fun assertRelClose(expected: BigDecimal, actual: BigDecimal, message: String) {
        val diff = actual.subtract(expected).abs()
        val scale = expected.abs().max(BigDecimal("1e-1000"))
        val relative = diff.divide(scale, MathContext(20)).toDouble()
        assertTrue("$message expected ≈ $expected, was $actual (relative $relative)", relative < 1e-30)
    }

    /** The double-based seed is unavailable beyond the double range. */
    @Test fun sqrtOutsideDoubleRangeUsesExponentSeed() {
        assertRelClose(bd("1e200"), GeometryMath.sqrt(bd("1e400")), "sqrt(1e400)")
        assertRelClose(bd("1e-200"), GeometryMath.sqrt(bd("1e-400")), "sqrt(1e-400)")
        assertRelClose(
            bd("1.414213562373095048801688724209698e200"),
            GeometryMath.sqrt(bd("2e400")),
            "sqrt(2e400)",
        )
    }
}
