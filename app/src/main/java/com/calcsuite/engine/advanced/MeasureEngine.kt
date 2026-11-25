package com.calcsuite.engine.advanced

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The measure computations the measure theory tool carries out.
 *
 * The model is deliberately narrow and honest: the measure of a set is its
 * length, computed for a finite union of intervals, and the integrable
 * functions are handled with the Riemann sums that decide whether the Riemann
 * integral exists at all. Where a result would need a measure that is not
 * implemented — the Lebesgue measure of an arbitrary set, for instance — the
 * tool says so instead of inventing a number.
 */
object MeasureEngine {

    /**
     * The measure of a finite union of intervals: the total length.
     */
    fun measure(set: RealSet): Double = set.measure

    /**
     * The measure of an interval.
     */
    fun measureOfInterval(from: Double, to: Double): Double = when {
        from.isInfinite() || to.isInfinite() -> Double.POSITIVE_INFINITY
        to <= from -> 0.0
        else -> to - from
    }

    /**
     * The union of two sets, with the measure of each and of the union.
     *
     * The measure of the union is never more than the sum of the measures,
     * and it is less when the sets overlap — which is the point of the
     * example.
     */
    data class UnionResult(
        val first: RealSet,
        val second: RealSet,
        val union: RealSet,
        val firstMeasure: Double,
        val secondMeasure: Double,
        val unionMeasure: Double,
        val overlapMeasure: Double,
    )

    /** The union of two sets and the measures involved. */
    fun union(first: RealSet, second: RealSet): UnionResult {
        val union = RealSet.fromIntervals(first.intervals + second.intervals)
        val intersection = intersect(first, second)
        val firstMeasure = first.measure
        val secondMeasure = second.measure
        val unionMeasure = union.measure
        return UnionResult(
            first = first,
            second = second,
            union = union,
            firstMeasure = firstMeasure,
            secondMeasure = secondMeasure,
            unionMeasure = unionMeasure,
            overlapMeasure = intersection.measure,
        )
    }

    /** The intersection of two sets. */
    fun intersect(first: RealSet, second: RealSet): RealSet {
        val out = mutableListOf<Interval>()
        for (a in first.intervals) {
            for (b in second.intervals) {
                val from = max(a.from, b.from)
                val to = min(a.to, b.to)
                val openFrom = a.openFrom || b.openFrom
                val openTo = a.openTo || b.openTo
                // A single shared point is still in the intersection when
                // both intervals include it; that is how point sets survive.
                if (to > from || (to == from && !openFrom && !openTo)) {
                    out.add(Interval(from, to, openFrom, openTo))
                }
            }
        }
        return RealSet.fromIntervals(out)
    }

    /** The complement of a set, relative to a bounding interval. */
    fun complement(set: RealSet, within: DoubleArray): RealSet {
        if (within.size < 2 || !within[0].isFinite() || !within[1].isFinite() ||
            within[0] >= within[1]
        ) return RealSet.EMPTY
        val low = within[0]
        val high = within[1]
        // Only the part of the set inside the bounds can take points away.
        val clipped = set.intervals.mapNotNull { interval ->
            val from = max(low, interval.from)
            val to = min(high, interval.to)
            val openFrom = if (from > interval.from) false else interval.openFrom
            val openTo = if (to < interval.to) false else interval.openTo
            if (to > from || (to == from && !openFrom && !openTo)) {
                Interval(from, to, openFrom, openTo)
            } else {
                null
            }
        }
        val out = mutableListOf<Interval>()
        var cursor = low
        // True when the point at the cursor belongs to the set, so the gap
        // that follows it has to leave that point out.
        var cursorCovered = false
        for (interval in clipped) {
            if (interval.from > cursor) {
                out.add(
                    Interval(
                        cursor,
                        interval.from,
                        openFrom = cursorCovered,
                        openTo = !interval.openFrom,
                    ),
                )
            }
            if (interval.to > cursor || (interval.to == cursor && !interval.openTo)) {
                cursor = max(cursor, interval.to)
                cursorCovered = !interval.openTo
            }
        }
        if (cursor < high) {
            out.add(Interval(cursor, high, openFrom = cursorCovered))
        }
        return RealSet.fromIntervals(out)
    }

    /**
     * The countable sum of a geometric series, and the limit of its partial
     * sums.
     *
     * The series `½ + ¼ + ⅛ + …` sums to 1, and the intervals of lengths
     * `½, ¼, ⅛, …` can be laid side by side inside `[0, 1]` without
     * overlapping — which is countable additivity at work.
     */
    data class GeometricSum(
        val ratio: Double,
        val first: Double,
        val partialSums: List<Double>,
        val limit: Double,
    )

    /** The partial sums and the limit of a geometric series. */
    fun geometricSum(first: Double, ratio: Double, terms: Int = 20): GeometricSum {
        val partials = mutableListOf<Double>()
        var total = 0.0
        var term = first
        for (i in 0 until terms) {
            total += term
            partials.add(total)
            term *= ratio
        }
        val limit = if (abs(ratio) < 1.0) first / (1.0 - ratio) else Double.NaN
        return GeometricSum(ratio, first, partials, limit)
    }

    /**
     * The intervals of the geometric series, laid side by side inside
     * `[0, 1]`.
     *
     * The nth interval has length `½ⁿ` and starts where the previous one
     * ended, so they do not overlap and their total length is 1.
     */
    fun geometricIntervals(terms: Int = 12): List<Interval> {
        val out = mutableListOf<Interval>()
        var cursor = 0.0
        var length = 0.5
        for (i in 0 until terms) {
            out.add(Interval.closed(cursor, cursor + length))
            cursor += length
            length /= 2
        }
        return out
    }

    /**
     * The lower and upper Riemann sums of a function on an interval.
     *
     * The function is given its value at a point, so the tool can take the
     * minimum and the maximum over each subinterval by sampling it.
     */
    data class RiemannSums(
        val lower: Double,
        val upper: Double,
        val lowerSum: List<Double>,
        val upperSum: List<Double>,
        val samples: Int,
    )

    /**
     * The lower and upper Riemann sums with the given number of subintervals.
     */
    fun riemannSums(
        f: (Double) -> Double?,
        from: Double,
        to: Double,
        subintervals: Int = 64,
        samplesPerSubinterval: Int = 8,
    ): RiemannSums? {
        if (to <= from || subintervals < 1 || samplesPerSubinterval < 1) return null
        if (!from.isFinite() || !to.isFinite()) return null
        val width = (to - from) / subintervals
        val lowerSums = mutableListOf<Double>()
        val upperSums = mutableListOf<Double>()
        var lower = 0.0
        var upper = 0.0
        for (i in 0 until subintervals) {
            val a = from + width * i
            val b = a + width
            var lowest = Double.POSITIVE_INFINITY
            var highest = Double.NEGATIVE_INFINITY
            for (k in 0..samplesPerSubinterval) {
                val value = f(a + width * k / samplesPerSubinterval) ?: return null
                if (!value.isFinite()) return null
                lowest = min(lowest, value)
                highest = max(highest, value)
            }
            lower += lowest * width
            upper += highest * width
            lowerSums.add(lower)
            upperSums.add(upper)
        }
        return RiemannSums(lower, upper, lowerSums, upperSums, subintervals)
    }

    /**
     * A function that is zero except at a few points.
     *
     * Such a function has a Riemann integral of zero, because a finite
     * number of points cannot change an integral; it is equal to the zero
     * function almost everywhere, because the set where it differs has
     * measure zero.
     */
    fun spikeFunction(points: List<Double>, height: Double = 1.0): (Double) -> Double =
        { x -> if (points.any { abs(it - x) < 1e-12 }) height else 0.0 }

    /**
     * The distance from a point to the nearest of a finite list of points.
     */
    fun distanceToNearest(x: Double, points: List<Double>): Double {
        if (points.isEmpty()) return Double.POSITIVE_INFINITY
        return points.minOf { abs(it - x) }
    }

    /**
     * True when a set of measure zero is given, which for the finite
     * constructions here means that it is empty or a finite set of points.
     */
    fun isNull(set: RealSet): Boolean {
        if (set.isEmpty) return true
        return set.intervals.all { it.isPoint }
    }
}
