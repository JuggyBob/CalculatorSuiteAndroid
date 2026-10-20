package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tests for the Fourier analysis.
 */
class FourierEngineTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    @Test fun theSineCoefficientIsOne() {
        // The series of sin(x) over a period of 2π has b₁ = 1 and nothing else.
        val series = FourierEngine.series(parse("sin(x)"), "x", 2 * PI, 3)!!
        assertEquals(0.0, series.a0, 1e-9)
        assertEquals(0.0, series.cosine[0], 1e-6)
        assertEquals(1.0, series.sine[0], 1e-6)
        assertEquals(0.0, series.cosine[1], 1e-6)
        assertEquals(0.0, series.sine[1], 1e-6)
    }

    @Test fun theCosineCoefficientIsOne() {
        val series = FourierEngine.series(parse("cos(x)"), "x", 2 * PI, 3)!!
        assertEquals(0.0, series.a0, 1e-9)
        assertEquals(1.0, series.cosine[0], 1e-6)
        assertEquals(0.0, series.sine[0], 1e-6)
    }

    @Test fun aConstantFunctionHasOnlyTheConstantTerm() {
        val series = FourierEngine.series(parse("3"), "x", 2 * PI, 2)!!
        assertEquals(6.0, series.a0, 1e-9)
        assertEquals(0.0, series.cosine[0], 1e-6)
        assertEquals(0.0, series.sine[0], 1e-6)
    }

    @Test fun theSeriesReproducesTheFunctionAtItsPeak() {
        val series = FourierEngine.series(parse("sin(x)"), "x", 2 * PI, 3)!!
        assertEquals(1.0, series.evaluate(PI / 2), 1e-6)
    }

    @Test fun moreHarmonicsMakeTheErrorSmaller() {
        // The function x on [0, 2π) is not periodic, so the series over one
        // period is an approximation; adding harmonics makes it better.
        val sawtooth = parse("x")
        val few = FourierEngine.series(sawtooth, "x", 2 * PI, 2)!!
        val many = FourierEngine.series(sawtooth, "x", 2 * PI, 40)!!
        val errorFew = FourierEngine.rmsError(sawtooth, "x", few)
        val errorMany = FourierEngine.rmsError(sawtooth, "x", many)
        assertTrue(
            "more harmonics should fit better ($errorFew against $errorMany)",
            errorMany < errorFew,
        )
    }

    @Test fun theTransformOfAFourPointWaveHasOneBigBin() {
        // The samples 1, 0, −1, 0 are one full cycle of a sine wave.
        val transform = FourierEngine.transform(doubleArrayOf(1.0, 0.0, -1.0, 0.0), 1.0)
        assertEquals(4, transform.sampleCount)
        // The record is one unit long, so the bins are a unit apart.
        assertEquals(1.0, transform.frequencyResolution, 1e-12)
        // The samples are one full cycle of a wave, so the second bin is the
        // strong one, at a frequency of one cycle per unit.
        val strongest = transform.bins.maxByOrNull { it.magnitude }!!
        assertEquals(1, strongest.index)
        assertEquals(1.0, strongest.frequency, 1e-12)
    }

    @Test fun theFastAndTheDirectTransformsAgree() {
        val samples = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        val direct = FourierEngine.dft(samples)
        val fast = FourierEngine.fft(samples)
        assertEquals(direct.size, fast.size)
        for (i in direct.indices) {
            assertEquals(direct[i].re, fast[i].re, 1e-9)
            assertEquals(direct[i].im, fast[i].im, 1e-9)
        }
    }

    @Test fun theTransformCanBeInverted() {
        val samples = doubleArrayOf(1.0, 0.0, -1.0, 0.0, 1.0, 0.0, -1.0, 0.0)
        val transform = FourierEngine.transform(samples, 1.0)
        val back = FourierEngine.inverseTransform(transform.values)
        for (i in samples.indices) {
            assertEquals(samples[i], back[i], 1e-9)
        }
    }

    @Test fun theExplanationSaysTheCoefficientsAreNumerical() {
        val result = FourierOperations.series(parse("sin(x)"), "x", 2 * PI, 3)!!
        assertNotNull(result.explanation.limitations)
        assertTrue(
            "the limits should mention that the coefficients are numerical",
            result.explanation.limitations!!.contains("numerical"),
        )
        assertTrue(
            "the steps should show where the coefficients come from",
            result.explanation.steps.any { it.contains("a1") || it.contains("a₀") },
        )
    }

    @Test fun theTransformExplanationKeepsTheTwoApart() {
        val result = FourierOperations.transform(doubleArrayOf(1.0, 0.0, -1.0, 0.0), 1.0)
        assertTrue(
            "the explanation should say this is the discrete transform",
            result.explanation.limitations!!.contains("discrete"),
        )
    }
}
