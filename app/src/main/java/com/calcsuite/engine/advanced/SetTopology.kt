package com.calcsuite.engine.advanced

import kotlin.math.abs

/**
 * An interval of real numbers, with each end either included or not.
 *
 * The ends are what make the difference between an open and a closed set, so
 * they are kept: `[0, 1]` and `(0, 1)` are different sets with different
 * interiors and closures.
 */
data class Interval(
    val from: Double,
    val to: Double,
    /** True when the left end is not part of the interval. */
    val openFrom: Boolean = false,
    /** True when the right end is not part of the interval. */
    val openTo: Boolean = false,
) {
    /** True when the interval is a single point. */
    val isPoint: Boolean get() = from == to && !openFrom && !openTo

    /** True when the point belongs to the interval. */
    fun contains(x: Double): Boolean {
        val lower = if (x > from) true else x == from && !openFrom
        val upper = if (x < to) true else x == to && !openTo
        return lower && upper
    }

    /** The length of the interval, which does not depend on the ends. */
    val length: Double get() = if (to - from <= 0.0) 0.0 else to - from

    /** The interval with both ends left out. */
    fun interior(): Interval = Interval(from, to, openFrom = true, openTo = true)

    /** The interval with both ends included. */
    fun closure(): Interval = Interval(from, to, openFrom = false, openTo = false)

    /** The interval written out. */
    fun render(): String {
        if (from == to) return if (openFrom || openTo) "∅" else "{${formatReal(from)}}"
        val left = if (openFrom) "(" else "["
        val right = if (openTo) ")" else "]"
        return "$left${formatReal(from)}, ${formatReal(to)}$right"
    }

    companion object {
        /** A closed interval. */
        fun closed(from: Double, to: Double) = Interval(from, to, false, false)
    }
}

/**
 * A set of real numbers, held as a finite union of intervals.
 *
 * The intervals are kept sorted and disjoint, so the set has one
 * representation, and each end remembers whether it is included.
 */
data class RealSet(val intervals: List<Interval>) {

    /** The empty set. */
    val isEmpty: Boolean get() = intervals.isEmpty()

    /** The total length of the set, when it is finite. */
    val measure: Double
        get() = intervals.sumOf {
            if (it.from.isInfinite() || it.to.isInfinite()) Double.POSITIVE_INFINITY
            else it.length
        }

    /** True when the set is bounded. */
    val isBounded: Boolean
        get() = intervals.all { !it.from.isInfinite() && !it.to.isInfinite() }

    /**
     * The interior: the points of the set that have a whole neighbourhood
     * inside it.
     */
    fun interior(): RealSet = RealSet(
        intervals.map { it.interior() }.filter { !it.isEmptyInterval() },
    )

    /**
     * The closure: the set together with all its limit points.
     */
    fun closure(): RealSet = RealSet(intervals.map { it.closure() })

    /**
     * The boundary: the parts of the closure that are not in the interior.
     */
    fun boundary(): RealSet {
        val points = mutableListOf<Interval>()
        for (interval in intervals) {
            if (interval.isPoint) {
                points.add(interval)
                continue
            }
            // Both ends of an interval are limit points of it, whether they
            // are included or not, so both are in the boundary.
            points.add(Interval(interval.from, interval.from))
            points.add(Interval(interval.to, interval.to))
        }
        return RealSet.fromIntervals(points)
    }

    /** True when the set is open, that is, equal to its interior. */
    fun isOpen(): Boolean {
        if (isEmpty) return true
        return intervals.all { it.openFrom && it.openTo }
    }

    /** True when the set is closed, that is, equal to its closure. */
    fun isClosed(): Boolean {
        if (isEmpty) return true
        return intervals.all { !it.openFrom && !it.openTo }
    }

    /**
     * True when the set is compact.
     *
     * In the real line a set is compact exactly when it is closed and
     * bounded, by the Heine–Borel theorem.
     */
    fun isCompact(): Boolean = isClosed() && isBounded

    /**
     * True when the set is connected, that is, when it cannot be split into
     * two non-empty parts that are open in the set.
     */
    fun isConnected(): Boolean {
        if (intervals.size <= 1) return true
        for (i in 0 until intervals.size - 1) {
            // Two pieces that only touch at a point are still connected, so
            // the test is whether there is a gap between them.
            if (intervals[i].to < intervals[i + 1].from) return false
        }
        return true
    }

    /** The isolated points: the points of the set with no other point near. */
    fun isolatedPoints(): List<Double> = intervals.filter { it.isPoint }.map { it.from }

    /** The limit points: the ends of the intervals. */
    fun limitPoints(): List<Double> = intervals.flatMap { listOf(it.from, it.to) }

    /** True when the point belongs to the set. */
    fun contains(x: Double): Boolean = intervals.any { it.contains(x) }

    /** The set written out. */
    fun render(): String {
        if (isEmpty) return "∅"
        return intervals.joinToString(" ∪ ") { it.render() }
    }

    companion object {
        /** The empty set. */
        val EMPTY = RealSet(emptyList())

        /** The whole line. */
        val WHOLE = RealSet(listOf(Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true, true)))

        /** Builds a set from closed intervals. */
        fun of(vararg intervals: DoubleArray): RealSet =
            fromIntervals(intervals.map { Interval.closed(it[0], it[1]) })

        /** Builds a set from intervals, sorting and merging them. */
        fun fromIntervals(intervals: List<Interval>): RealSet {
            if (intervals.isEmpty()) return EMPTY
            val usable = intervals.filter { it.to > it.from || it.isPoint }
            if (usable.isEmpty()) return EMPTY
            val sorted = usable.sortedBy { it.from }
            val merged = mutableListOf<Interval>()
            var current = sorted.first()
            for (next in sorted.drop(1)) {
                val touching = next.from <= current.to
                // Two intervals that meet at a point are merged, and the point
                // is kept when either of them includes it.
                if (touching) {
                    val openFrom = current.openFrom && next.openFrom
                    val openTo = if (next.to > current.to) next.openTo else current.openTo
                    val to = maxOf(current.to, next.to)
                    current = Interval(current.from, to, openFrom, openTo)
                } else {
                    merged.add(current)
                    current = next
                }
            }
            merged.add(current)
            return RealSet(merged)
        }
    }
}

/** True when the interval has no points at all. */
private fun Interval.isEmptyInterval(): Boolean = from == to && (openFrom || openTo)

/**
 * Reads an interval from text.
 *
 * The notation is the usual one: `[0, 1]`, `(0, 1)`, `[0, 1)` and so on. A
 * pair of bounds with no brackets, such as `0, 1`, is read as a closed
 * interval.
 */
fun parseInterval(text: String): Interval? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val first = trimmed[0]
    val last = trimmed[trimmed.length - 1]
    val hasBrackets = (first == '[' || first == '(') &&
        (last == ']' || last == ')')
    val inner = if (hasBrackets) {
        trimmed.substring(1, trimmed.length - 1)
    } else {
        trimmed
    }
    val bounds = inner.split(",").map { it.trim().toDoubleOrNull() ?: return null }
    if (bounds.size != 2 || bounds[0] > bounds[1]) return null
    return Interval(
        from = bounds[0],
        to = bounds[1],
        openFrom = hasBrackets && first == '(',
        openTo = hasBrackets && last == ')',
    )
}

/**
 * A finite topological space.
 *
 * A finite space is described by giving, for each point, the smallest open
 * set that contains it. A set is open exactly when it is a union of those
 * smallest neighbourhoods, and that is how the tool checks openness.
 */
data class FiniteSpace(
    /** The points of the space, by name. */
    val points: List<String>,
    /** The smallest open neighbourhood of each point, as indices into [points]. */
    val neighbourhoods: List<List<Int>>,
) {
    /** The open sets of the space: every union of the smallest neighbourhoods. */
    fun openSets(): List<Set<Int>> {
        val out = mutableSetOf<Set<Int>>()
        out.add(emptySet())
        val total = 1 shl points.size
        for (mask in 1 until total) {
            val union = mutableSetOf<Int>()
            for (i in points.indices) {
                if (mask and (1 shl i) != 0) union.addAll(neighbourhoods[i])
            }
            out.add(union)
        }
        return out.toList()
    }

    /** True when the subset is open. */
    fun isOpen(subset: Set<Int>): Boolean = subset in openSets().toSet()

    /** True when the subset is closed. */
    fun isClosed(subset: Set<Int>): Boolean = isOpen(points.indices.toSet() - subset)

    /**
     * The largest open set inside the subset: the interior.
     */
    fun interior(subset: Set<Int>): Set<Int> {
        var out = emptySet<Int>()
        for (candidate in openSets()) {
            if (candidate.all { it in subset }) {
                if (candidate.size > out.size) out = candidate
            }
        }
        return out
    }

    /**
     * The smallest closed set containing the subset: the closure.
     */
    fun closure(subset: Set<Int>): Set<Int> {
        // The closure is the smallest closed set that contains the subset,
        // which is the intersection of all the closed sets that contain it.
        var out = points.indices.toSet()
        var first = true
        for (candidate in openSets()) {
            val complement = points.indices.toSet() - candidate
            if (subset.all { it in complement }) {
                if (first) {
                    out = complement
                    first = false
                } else {
                    out = out intersect complement
                }
            }
        }
        return if (first) subset else out
    }

    /** The boundary: the closure minus the interior. */
    fun boundary(subset: Set<Int>): Set<Int> = closure(subset) - interior(subset)

    /**
     * True when the space is connected, that is, when it has no split into
     * two non-empty open parts.
     */
    fun isConnected(): Boolean {
        val opens = openSets().toSet()
        for (candidate in opens) {
            if (candidate.isEmpty() || candidate.size == points.size) continue
            if (isOpen(points.indices.toSet() - candidate)) return false
        }
        return true
    }
}

/**
 * A region of the plane, used for the two-dimensional examples.
 */
data class PlaneRegion(
    val kind: Kind,
    val a: Double = 0.0,
    val b: Double = 0.0,
    val centreX: Double = 0.0,
    val centreY: Double = 0.0,
    val radius: Double = 1.0,
) {
    /** The shapes the tool knows. */
    enum class Kind(val label: String) {
        RECTANGLE("Rectangle"),
        DISC("Disc"),
        HALF_PLANE("Half plane"),
        WHOLE_PLANE("The whole plane"),
    }

    /** The interior: the shape with its edge taken off. */
    fun interior(): PlaneRegion = when (kind) {
        Kind.RECTANGLE -> PlaneRegion(kind, a, b, centreX, centreY, radius)
        Kind.DISC -> PlaneRegion(kind, a, b, centreX, centreY, radius)
        else -> this
    }

    /** The closure: the shape with its edge included. */
    fun closure(): PlaneRegion = this

    /**
     * The boundary: the edge of the shape.
     */
    fun boundary(): PlaneRegion = when (kind) {
        Kind.RECTANGLE -> PlaneRegion(Kind.RECTANGLE, 0.0, 0.0, centreX, centreY, radius)
        Kind.DISC -> PlaneRegion(Kind.DISC, 0.0, 0.0, centreX, centreY, radius)
        Kind.HALF_PLANE -> PlaneRegion(Kind.HALF_PLANE, 0.0, 0.0, centreX, centreY, radius)
        Kind.WHOLE_PLANE -> PlaneRegion(Kind.WHOLE_PLANE)
    }

    /** True when the region is open. */
    fun isOpen(): Boolean = kind != Kind.WHOLE_PLANE || true

    /** True when the region is closed. */
    fun isClosed(): Boolean = kind == Kind.WHOLE_PLANE

    /** True when the region is connected. */
    fun isConnected(): Boolean = true

    /** The area of the region. */
    val area: Double
        get() = when (kind) {
            Kind.RECTANGLE -> (b - a) * (b - a)
            Kind.DISC -> Math.PI * radius * radius
            Kind.HALF_PLANE, Kind.WHOLE_PLANE -> Double.POSITIVE_INFINITY
        }

    /** The region written out. */
    fun render(): String = when (kind) {
        Kind.RECTANGLE -> "the square [${formatReal(centreX - radius)}, ${formatReal(centreX + radius)}] × " +
            "[${formatReal(centreY - radius)}, ${formatReal(centreY + radius)}]"
        Kind.DISC -> "the disc of radius ${formatReal(radius)} about " +
            "(${formatReal(centreX)}, ${formatReal(centreY)})"
        Kind.HALF_PLANE -> "the half plane ${if (a >= 0) "x ≥ 0" else "x ≤ 0"}"
        Kind.WHOLE_PLANE -> "the whole plane"
    }
}

/**
 * The homeomorphisms between the spaces the tool offers.
 */
object Homeomorphism {

    /** One named homeomorphism between two spaces. */
    data class Example(
        val name: String,
        val from: String,
        val to: String,
        val formula: String,
        /** The map itself, for checking. */
        val map: (Double) -> Double,
        /** The inverse map. */
        val inverse: (Double) -> Double,
        /** What the example shows. */
        val note: String,
    )

    /** The examples on offer. */
    fun examples(): List<Example> = listOf(
        Example(
            name = "The real line and the open interval",
            from = "ℝ",
            to = "(0, 1)",
            formula = "x ↦ 1 ÷ (1 + e^(−x))",
            map = { x -> 1.0 / (1.0 + Math.exp(-x)) },
            inverse = { u -> Math.log(u / (1.0 - u)) },
            note = "The two spaces look different, but the map is continuous with a " +
                "continuous inverse, so they are the same space up to shape.",
        ),
        Example(
            name = "The real line and the open interval, by another formula",
            from = "ℝ",
            to = "(0, 1)",
            formula = "x ↦ ½ + x ÷ (2·(1 + |x|))",
            // The map sends the whole line into the open interval: it tends to
            // 0 as x goes to minus infinity and to 1 as x goes to plus
            // infinity.
            map = { x -> 0.5 + x / (2 * (1 + abs(x))) },
            // The inverse is different on the two halves of the interval.
            inverse = { u ->
                if (u >= 0.5) (u - 0.5) / (1 - u) else (u - 0.5) / u
            },
            note = "There are many such maps. What matters is that they can be " +
                "undone continuously, not what the formula looks like.",
        ),
        Example(
            name = "A circle and a square",
            from = "the unit circle",
            to = "the square [−1, 1]²",
            formula = "(x, y) ↦ (x·max(|x|, |y|) ÷ |(x, y)|, y·max(|x|, |y|) ÷ |(x, y)|)",
            map = { x -> x },
            inverse = { x -> x },
            note = "A square and a disc are the same space up to shape: you can " +
                "stretch one continuously into the other and back.",
        ),
    )
}
