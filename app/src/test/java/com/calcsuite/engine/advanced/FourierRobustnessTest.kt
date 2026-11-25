package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Regressions for the Fourier work limits and the non-throwing overloads.
 */
class FourierRobustnessTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    @Test fun anAbsurdHarmonicCountIsRejectedWithoutRunning() {
        assertNull(FourierEngine.series(parse("sin(x)"), "x", 2 * PI, 100_000))
        assertNull(
            FourierEngine.series(
                parse("sin(x)"), "x", 2 * PI, FourierEngine.MAX_HARMONICS + 1,
            ),
        )
    }

    @Test fun acceptedHarmonicsKeepTheGoldenCoefficients() {
        val series = FourierEngine.series(parse("sin(x)"), "x", 2 * PI, 3)!!
        assertEquals(0.0, series.a0, 1e-9)
        assertEquals(1.0, series.sine[0], 1e-6)
        assertEquals(0.0, series.cosine[0], 1e-6)
        assertEquals(1.0, series.evaluate(PI / 2), 1e-6)
        val error = FourierEngine.rmsError(parse("sin(x)"), "x", series)
        assertTrue("the error should be measurable, but it was $error", error.isFinite())
        assertTrue("a three-harmonic sine fit should be close, but it was $error", error < 1e-2)
    }

    @Test fun aHugeButFiniteIntegrandIsRejected() {
        // Every sampled value is finite (1e308), but Simpson's weighted sum
        // overflows. The coefficients must not come back as Infinity.
        assertNull(FourierEngine.series(parse("1e308"), "x", 2 * PI, 3))
        assertNull(FourierOperations.series(parse("1e308"), "x", 2 * PI, 3))
    }

    @Test fun anUnusableApproximationNeverReportsZeroError() {
        val broken = FourierEngine.Series(
            a0 = Double.POSITIVE_INFINITY,
            cosine = emptyList(),
            sine = emptyList(),
            period = 2 * PI,
            from = 0.0,
            to = 2 * PI,
            harmonics = 0,
            numerical = true,
        )
        assertNull(FourierEngine.rmsErrorOrNull(parse("sin(x)"), "x", broken))
        val reported = FourierEngine.rmsError(parse("sin(x)"), "x", broken)
        assertTrue("the error must not be reported as zero, but it was $reported", reported != 0.0)
        assertTrue("the error must signal that it is unusable", reported.isInfinite())
    }

    @Test fun aNonFinitePeriodIsRejected() {
        assertNull(FourierEngine.series(parse("sin(x)"), "x", Double.NaN, 3))
        assertNull(FourierEngine.series(parse("sin(x)"), "x", Double.POSITIVE_INFINITY, 3))
    }

    @Test fun overlongSamplesAreRejectedWithoutThrowing() {
        val tooMany = DoubleArray(FourierEngine.MAX_SAMPLES + 1)
        assertNull(FourierEngine.transformOrNull(tooMany))
        assertNull(FourierOperations.transformOrNull(tooMany))
        assertNull(FourierEngine.dftOrNull(tooMany))
        assertNull(FourierEngine.fftOrNull(tooMany))
        val values = List(tooMany.size) { ComplexNum.ZERO }
        assertNull(FourierEngine.inverseTransformOrNull(values))
    }

    @Test fun nonFiniteSamplesAndValuesAreRejected() {
        assertNull(FourierEngine.transformOrNull(doubleArrayOf(1.0, Double.NaN, -1.0)))
        assertNull(
            FourierEngine.transformOrNull(
                doubleArrayOf(1.0, 0.0, -1.0, 0.0), Double.NaN,
            ),
        )
        assertNull(FourierEngine.dftOrNull(doubleArrayOf(Double.POSITIVE_INFINITY, 0.0)))
        assertNull(FourierEngine.fftOrNull(doubleArrayOf(1.0, Double.NaN)))
        assertNull(
            FourierEngine.inverseTransformOrNull(
                listOf(ComplexNum(1.0, 0.0), ComplexNum(Double.NaN, 0.0)),
            ),
        )
    }

    @Test fun theLegacyThrowingOverloadsStillThrowForBadInput() {
        assertThrows(IllegalArgumentException::class.java) {
            FourierEngine.transform(DoubleArray(FourierEngine.MAX_SAMPLES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FourierOperations.transform(DoubleArray(FourierEngine.MAX_SAMPLES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FourierEngine.transform(doubleArrayOf(1.0, Double.NaN))
        }
    }

    @Test fun theLargestAcceptedSampleCountStillTransformsAndInverts() {
        val samples = DoubleArray(FourierEngine.MAX_SAMPLES) {
            sin(2 * PI * it / 64.0)
        }
        val transform = FourierEngine.transformOrNull(samples)
        assertNotNull(transform)
        assertEquals(FourierEngine.MAX_SAMPLES, transform!!.sampleCount)
        assertTrue(transform.bins.all { it.magnitude.isFinite() })
        val restored = FourierEngine.inverseTransformOrNull(transform.values)
        assertNotNull(restored)
        for (i in samples.indices step 128) {
            assertEquals("sample $i should survive the round trip", samples[i], restored!![i], 1e-9)
        }
    }
}
