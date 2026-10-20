package com.calcsuite.engine.advanced

import com.calcsuite.engine.calculus.Node
import kotlin.math.abs
import kotlin.math.PI

/**
 * The Fourier operations, each with the working that produced it.
 *
 * The coefficients come from real integrals, so they are numerical
 * estimates, and the explanation says so. The discrete transform is a
 * different thing altogether — it describes a list of samples, not a
 * function — and the explanation keeps the two apart.
 */
object FourierOperations {

    /**
     * The result of a Fourier series calculation.
     */
    data class SeriesResult(
        val series: FourierEngine.Series,
        val error: Double,
        val explanation: Explanation,
    )

    /**
     * The real Fourier series of a function, with its coefficients.
     */
    fun series(
        function: Node,
        variable: String,
        period: Double,
        harmonics: Int,
        from: Double = 0.0,
    ): SeriesResult? {
        val series = FourierEngine.series(function, variable, period, harmonics, from) ?: return null
        val error = FourierEngine.rmsError(function, variable, series)
        val steps = mutableListOf<String>()
        steps.add("The function is expanded in the basis cos(2πnx/T) and sin(2πnx/T), " +
            "which repeats with the period T = ${formatReal(period)}.")
        steps.add("The constant term is")
        steps.add("  a₀ = (2/T)·∫ f(x) dx = ${formatReal(series.a0)}")
        for (n in series.cosine.indices) {
            val k = n + 1
            steps.add("  a$k = (2/T)·∫ f(x)·cos(2π·$k·x/T) dx = " +
                "${formatReal(series.cosine[n])}")
            steps.add("  b$k = (2/T)·∫ f(x)·sin(2π·$k·x/T) dx = " +
                "${formatReal(series.sine[n])}")
        }
        steps.add("The truncated series is")
        steps.add("  " + series.render(variable))
        steps.add("The root-mean-square difference between the function and the truncated " +
            "series over one period is ${formatReal(error)}.")
        return SeriesResult(series, error, Explanation(
            definition = "A Fourier series writes a periodic function as a sum of sines " +
                "and cosines, whose periods are whole fractions of the period of the " +
                "function.",
            formula = "f(x) ≈ a₀/2 + Σₙ₌₁ᴺ [aₙ·cos(2πnx/T) + bₙ·sin(2πnx/T)]",
            symbols = listOf(
                "T" to "the period of the function",
                "aₙ, bₙ" to "the cosine and sine coefficients of the n-th harmonic",
                "N" to "the number of harmonics kept",
            ),
            conditions = listOf(
                "The function has to be periodic with the period T for the series to " +
                    "converge to it.",
                "The coefficients are found by numerical integration, so they are " +
                    "estimates rather than exact values.",
            ),
            method = "Each coefficient is the integral of the function against the basis " +
                "function, divided by the period. The integrals use an adaptive Simpson rule.",
            steps = steps,
            interpretation = "The constant term is the average of the function over one " +
                "period, and the first harmonic is the biggest swing of the shape. Each " +
                "further harmonic adds a finer detail.",
            verification = "The difference between the function and the truncated series " +
                "was measured over one period and is ${formatReal(error)}.",
            limitations = "The coefficients are numerical, and the series only converges to " +
                "the function where the function is periodic and reasonably smooth. Near a " +
                "jump the series overshoots, which is the Gibbs phenomenon, and the " +
                "overshoot does not shrink as more terms are added.",
        ))
    }

    /**
     * The result of a discrete transform.
     */
    data class TransformResult(
        val transform: FourierEngine.Transform,
        val explanation: Explanation,
    )

    /**
     * The discrete Fourier transform of a list of samples.
     */
    fun transform(samples: DoubleArray, interval: Double = 1.0): TransformResult {
        val transform = FourierEngine.transform(samples, interval)
        val steps = mutableListOf<String>()
        steps.add("The ${transform.sampleCount} samples are treated as one period of " +
            "length ${formatReal(transform.interval)}.")
        steps.add("The transform is Xₖ = Σⱼ yⱼ·e^(−2πijk/n), which gives the amplitude " +
            "and the phase of each frequency.")
        val strongest = transform.bins.maxByOrNull { it.magnitude }
        if (strongest != null) {
            steps.add("The largest bin is the ${strongest.index + 1}${ordinal(strongest.index + 1)} " +
                "harmonic, at a frequency of ${formatReal(strongest.frequency)} with an " +
                "amplitude of ${formatReal(strongest.magnitude)}.")
        }
        steps.add("The spacing of the frequency bins is ${formatReal(transform.frequencyResolution)}.")
        return TransformResult(transform, Explanation(
            definition = "The discrete Fourier transform turns a list of samples into a " +
                "list of amplitudes and phases, one for each frequency in the list.",
            formula = "Xₖ = Σⱼ₌₀ⁿ⁻¹ yⱼ·e^(−2πijk/n)",
            symbols = listOf(
                "n" to "the number of samples",
                "Xₖ" to "the value of the transform at bin k",
                "Δf" to "the spacing of the frequency bins, which is 1 over the length of " +
                    "the sampled interval",
            ),
            conditions = listOf(
                "The samples have to be equally spaced in time.",
                "The transform describes the samples, not the function between them.",
                "The number of samples has to be a power of two for the fast algorithm; " +
                    "otherwise the direct transform is used, which gives the same answer.",
            ),
            method = "The transform is computed with the fast algorithm when the number of " +
                "samples allows it, and with the direct sum otherwise.",
            steps = steps,
            interpretation = "A large amplitude at a particular frequency means the " +
                "signal has a strong component at that frequency.",
            verification = null,
            limitations = "This is the discrete transform, not the continuous Fourier " +
                "transform. The samples do not determine the function between them, and " +
                "the frequency resolution is limited by the length of the record.",
        ))
    }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}
