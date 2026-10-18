package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.util.TreeMap
import java.util.TreeSet

/**
 * Central-tendency statistics over a small data set.
 *
 * A dataset is entered as text: tokens separated by commas, semicolons or line
 * breaks, each parsed with [Num.parseOrNull]. Spaces are grouping separators
 * and therefore stay inside a token ("1 234" is one number).
 */
object AverageEngine {

    /** Parsed dataset with the facts every results card shows. */
    data class Dataset(
        val values: List<BigDecimal>,
        val sum: BigDecimal,
        val sorted: List<BigDecimal>,
        val min: BigDecimal,
        val max: BigDecimal,
    ) {
        val count: Int get() = values.size
    }

    data class WeightedResult(
        val mean: BigDecimal,
        val weightSum: BigDecimal,
        val weightedSum: BigDecimal,
    )

    /** Splits and parses dataset text. Reports the first token that is not a number. */
    fun parseDataset(text: String): CalcResult<List<BigDecimal>> {
        val tokens = text.split(',', ';', '\n', '\r', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        val values = ArrayList<BigDecimal>(tokens.size)
        for (token in tokens) {
            val value = Num.parseOrNull(token)
                ?: return err(ErrorKind.InvalidInput, "Could not read \"$token\" as a number")
            values.add(value)
        }
        return ok(values)
    }

    fun summarize(values: List<BigDecimal>): CalcResult<Dataset> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        var sum = BigDecimal.ZERO
        for (value in values) sum = sum.add(value)
        val sorted = values.sortedWith(naturalOrder())
        return ok(Dataset(values.toList(), sum, sorted, sorted.first(), sorted.last()))
    }

    fun arithmeticMean(values: List<BigDecimal>): CalcResult<BigDecimal> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        var sum = BigDecimal.ZERO
        for (value in values) sum = sum.add(value)
        return ok(sum.divide(BigDecimal.valueOf(values.size.toLong()), ALG_CTX))
    }

    /** Σ(vᵢ·wᵢ) ÷ Σwᵢ. Weights must be non-negative and must not sum to zero. */
    fun weightedMean(values: List<BigDecimal>, weights: List<BigDecimal>): CalcResult<WeightedResult> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        if (values.size != weights.size) {
            return err(
                ErrorKind.InvalidInput,
                "Provide one weight per value: ${values.size} values but ${weights.size} weights",
            )
        }
        weights.firstOrNull { it.signum() < 0 }?.let {
            return err(ErrorKind.Domain, "Weights must not be negative (found ${plain(it)})")
        }
        var weighted = BigDecimal.ZERO
        var weightSum = BigDecimal.ZERO
        for (index in values.indices) {
            weighted = weighted.add(values[index].multiply(weights[index]), ALG_CTX)
            weightSum = weightSum.add(weights[index], ALG_CTX)
        }
        if (weightSum.signum() == 0) {
            return err(ErrorKind.Domain, "The weights add up to zero, so the weighted mean is undefined")
        }
        return ok(WeightedResult(weighted.divide(weightSum, ALG_CTX), weightSum, weighted))
    }

    /** Median: the middle value, or the mean of the two middle values for an even count. */
    fun median(values: List<BigDecimal>): CalcResult<BigDecimal> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        val sorted = values.sortedWith(naturalOrder())
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            ok(sorted[middle])
        } else {
            ok(sorted[middle - 1].add(sorted[middle]).divide(BigDecimal.valueOf(2), ALG_CTX))
        }
    }

    /**
     * Every value that occurs most often. Returns an empty list when all values
     * appear exactly once, and several values for a multi-modal dataset.
     */
    fun mode(values: List<BigDecimal>): CalcResult<List<BigDecimal>> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        val counts = TreeMap<BigDecimal, Int>()
        for (value in values) counts[value] = (counts[value] ?: 0) + 1
        val most = counts.values.max()
        if (most <= 1) return ok(emptyList())
        val modes = TreeSet<BigDecimal>()
        for ((value, count) in counts) if (count == most) modes.add(value)
        return ok(modes.toList())
    }

    /** Geometric mean `(Πxᵢ)^(1/n)`; requires at least one strictly positive value set. */
    fun geometricMean(values: List<BigDecimal>): CalcResult<BigDecimal> {
        if (values.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one number")
        values.firstOrNull { it.signum() <= 0 }?.let {
            return err(
                ErrorKind.Domain,
                "The geometric mean needs positive values only (found ${plain(it)})",
            )
        }
        if (values.size == 1) return ok(values[0])
        var product = BigDecimal.ONE
        for (value in values) product = product.multiply(value)
        val root = bigNthRoot(product, values.size)
            ?: return err(ErrorKind.Domain, "The geometric mean of these values is not defined")
        return ok(root)
    }
}
