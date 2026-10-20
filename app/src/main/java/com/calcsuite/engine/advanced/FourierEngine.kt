package com.calcsuite.engine.advanced

import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fourier analysis: real Fourier series for an expression, and the discrete
 * transform for a list of samples.
 *
 * The two are kept apart deliberately. A Fourier series is built from the
 * coefficients of a function over one period; the discrete transform
 * describes a finite list of samples, and the samples do not determine the
 * function between them.
 */
object FourierEngine {

    /** The coefficients of a real Fourier series. */
    data class Series(
        /** The constant term `a₀`, so the series starts with `a₀/2`. */
        val a0: Double,
        /** The cosine coefficients, `a₁ … aₙ`. */
        val cosine: List<Double>,
        /** The sine coefficients, `b₁ … bₙ`. */
        val sine: List<Double>,
        /** The period the coefficients were computed over. */
        val period: Double,
        /** The interval the coefficients were computed over. */
        val from: Double,
        val to: Double,
        /** The number of harmonics. */
        val harmonics: Int,
        /** True when the coefficients were found by numerical integration. */
        val numerical: Boolean,
    ) {
        /** The value of the truncated series at `x`. */
        fun evaluate(x: Double): Double {
            var sum = a0 / 2
            for (n in cosine.indices) {
                val w = 2 * PI * (n + 1) * x / period
                sum += cosine[n] * cos(w) + sine[n] * sin(w)
            }
            return sum
        }

        /** The series written out. */
        fun render(variable: String = "x"): String {
            val parts = mutableListOf<String>()
            if (abs(a0) > 1e-12) parts.add(formatReal(a0 / 2))
            for (n in cosine.indices) {
                val k = n + 1
                val w = "2π·$k·$variable ÷ $period"
                if (abs(cosine[n]) > 1e-12) {
                    parts.add("${formatReal(cosine[n])}·cos($w)")
                }
                if (abs(sine[n]) > 1e-12) {
                    parts.add("${formatReal(sine[n])}·sin($w)")
                }
            }
            return if (parts.isEmpty()) "0" else parts.joinToString(" + ")
        }
    }

    /**
     * The coefficients of the real Fourier series of a function.
     *
     * The coefficients are found by integrating over one period:
     *
     * ```
     * aₙ = (2/T)∫ f(x)·cos(2πnx/T) dx
     * bₙ = (2/T)∫ f(x)·sin(2πnx/T) dx
     * ```
     *
     * The integrals are numerical, so the coefficients are numerical
     * estimates; the result says so.
     */
    fun series(
        function: Node,
        variable: String,
        period: Double,
        harmonics: Int,
        from: Double = 0.0,
    ): Series? {
        if (period <= 0.0 || harmonics < 0) return null
        val to = from + period
        val n = harmonics
        // Enough panels to resolve the highest harmonic that is kept.
        val panels = (64 * maxOf(1, n)).coerceAtMost(20000)

        // The constant term is the average of the function over the period,
        // and it is wanted even when no harmonics are kept.
        val mean = integrate(function, variable, from, to, 64) ?: return null
        val a0 = 2.0 * mean / period

        val cosines = mutableListOf<Double>()
        val sines = mutableListOf<Double>()
        for (k in 1..n) {
            val w = 2 * PI * k / period
            val cosTerm = Node.Fun("cos", listOf(Node.Mul(Node.Num(w), Node.Var(variable))))
            val sinTerm = Node.Fun("sin", listOf(Node.Mul(Node.Num(w), Node.Var(variable))))
            val cosIntegral = integrate(Node.Mul(function, cosTerm), variable, from, to, panels) ?: return null
            val sinIntegral = integrate(Node.Mul(function, sinTerm), variable, from, to, panels) ?: return null
            cosines.add(2.0 * cosIntegral / period)
            sines.add(2.0 * sinIntegral / period)
        }
        return Series(
            a0 = a0,
            cosine = cosines,
            sine = sines,
            period = period,
            from = from,
            to = to,
            harmonics = n,
            numerical = true,
        )
    }

    /**
     * The numerical value of `∫ f(x) dx` from `lo` to `hi`.
     *
     * A fixed composite Simpson rule is used rather than the adaptive one,
     * because the integrand here is the function times a basis function, and
     * that product oscillates quickly for the higher harmonics. The adaptive
     * rule does not notice such an oscillation and can return a wrong
     * coefficient; a fixed rule with enough panels resolves it.
     */
    private fun integrate(
        function: Node,
        variable: String,
        lo: Double,
        hi: Double,
        panels: Int,
    ): Double? {
        val count = panels.coerceIn(64, 20000)
        if (count % 2 == 1) return null
        val h = (hi - lo) / count
        var total = 0.0
        for (i in 0..count) {
            val x = lo + h * i
            val value = Evaluator.eval(function, mapOf(variable to x)).valueOrNull() ?: return null
            val weight = when {
                i == 0 || i == count -> 1.0
                i % 2 == 1 -> 4.0
                else -> 2.0
            }
            total += weight * value
        }
        return total * h / 3
    }

    /**
     * The root-mean-square error between a function and its truncated series,
     * measured over one period.
     */
    fun rmsError(function: Node, variable: String, series: Series, samples: Int = 200): Double {
        var sum = 0.0
        var count = 0
        val step = series.period / samples
        for (i in 0 until samples) {
            val x = series.from + step * (i + 0.5)
            val exact = Evaluator.eval(function, mapOf(variable to x)).valueOrNull() ?: continue
            val difference = exact - series.evaluate(x)
            sum += difference * difference
            count++
        }
        if (count == 0) return 0.0
        return sqrt(sum / count)
    }

    // ------------------------------------------------------ sampled data

    /** One bin of the discrete transform. */
    data class FrequencyBin(
        /** The index of the bin. */
        val index: Int,
        /** The frequency of the bin, in cycles per unit of `x`. */
        val frequency: Double,
        /** The magnitude of the transform at this bin. */
        val magnitude: Double,
        /** The phase of the transform at this bin, in radians. */
        val phase: Double,
    )

    /** The result of a discrete transform. */
    data class Transform(
        /** The number of samples. */
        val sampleCount: Int,
        /** The length of the sampled interval. */
        val interval: Double,
        /** The bins of the transform. */
        val bins: List<FrequencyBin>,
        /** The transformed values, one per bin. */
        val values: List<ComplexNum>,
    ) {
        /** The resolution of the transform, in cycles per unit of `x`. */
        val frequencyResolution: Double get() = if (interval > 0) 1.0 / interval else 0.0
    }

    /**
     * The discrete Fourier transform of a list of samples.
     *
     * The transform is `Xₖ = Σ yⱼ·e^(−2πi jk/n)`. When the number of
     * samples is a power of two the fast algorithm is used, and the result is
     * the same to working precision.
     */
    fun transform(samples: DoubleArray, interval: Double = 1.0): Transform {
        val n = samples.size
        require(n >= 2) { "a transform needs at least two samples" }
        val values = if (n > 1 && (n and (n - 1)) == 0) fft(samples) else dft(samples)
        val bins = (0 until n).map { k ->
            val magnitude = values[k].modulus
            val phase = values[k].argument
            FrequencyBin(
                index = k,
                frequency = k.toDouble() / interval,
                magnitude = magnitude / n,
                phase = phase,
            )
        }
        return Transform(n, interval, bins, values)
    }

    /** The direct discrete Fourier transform, `O(n²)`. */
    fun dft(samples: DoubleArray): List<ComplexNum> {
        val n = samples.size
        val out = ArrayList<ComplexNum>(n)
        for (k in 0 until n) {
            var re = 0.0
            var im = 0.0
            for (j in 0 until n) {
                val angle = -2 * PI * j * k / n
                re += samples[j] * cos(angle)
                im += samples[j] * sin(angle)
            }
            out.add(ComplexNum(re, im))
        }
        return out
    }

    /**
     * The fast Fourier transform, the radix-2 Cooley–Tukey algorithm.
     *
     * The number of samples has to be a power of two.
     */
    fun fft(samples: DoubleArray): List<ComplexNum> {
        val n = samples.size
        require(n > 0 && (n and (n - 1)) == 0) {
            "the fast transform needs a power of two samples"
        }
        val values = ArrayList<ComplexNum>(n)
        for (j in 0 until n) {
            values.add(ComplexNum(samples[j], 0.0))
        }
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val t = values[i]; values[i] = values[j]; values[j] = t
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2 * PI / length
            val step = ComplexNum(cos(angle), sin(angle))
            var start = 0
            while (start < n) {
                var w = ComplexNum(1.0, 0.0)
                for (k in 0 until length / 2) {
                    val u = values[start + k]
                    val v = values[start + k + length / 2] * w
                    values[start + k] = u + v
                    values[start + k + length / 2] = u - v
                    w = w * step
                }
                start += length
            }
            length = length shl 1
        }
        return values
    }

    /**
     * The inverse discrete Fourier transform, which turns the bins back into
     * samples.
     */
    fun inverseTransform(values: List<ComplexNum>): DoubleArray {
        val n = values.size
        val out = DoubleArray(n)
        for (j in 0 until n) {
            var sum = 0.0
            for (k in 0 until n) {
                val angle = 2 * PI * j * k / n
                val v = values[k]
                sum += v.re * cos(angle) - v.im * sin(angle)
            }
            out[j] = sum / n
        }
        return out
    }

    /**
     * The values a truncated series takes at a list of points, so the graph
     * can draw the two curves together.
     */
    fun sample(series: Series, from: Double, to: Double, count: Int): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        if (count < 2) return out
        val step = (to - from) / (count - 1)
        for (i in 0 until count) {
            val x = from + step * i
            out.add(x to series.evaluate(x))
        }
        return out
    }

    /**
     * The values a function takes at a list of points.
     */
    fun sampleFunction(
        function: Node,
        variable: String,
        from: Double,
        to: Double,
        count: Int,
    ): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        if (count < 2) return out
        val step = (to - from) / (count - 1)
        for (i in 0 until count) {
            val x = from + step * i
            val y = Evaluator.eval(function, mapOf(variable to x)).valueOrNull() ?: continue
            if (!y.isFinite()) continue
            out.add(x to y)
        }
        return out
    }

    /** The expression for the truncated series, for the explanation. */
    fun renderFunction(function: Node): String = NodePrinter.print(function)
}
