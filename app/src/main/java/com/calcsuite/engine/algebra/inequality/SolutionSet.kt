package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.formatNumber

/**
 * One end of an interval.
 *
 * [value] is always available for plotting. [exactText] carries the exact
 * form when the endpoint is known exactly — `1/3` rather than `0.333333…` — and
 * is null when the endpoint came from a numerical root.
 */
data class Endpoint(
    val value: Double,
    val exactText: String?,
    val closed: Boolean,
) {
    /** How the endpoint should be read: `2`, `1/3` or `≈1.333333`. */
    fun display(): String = when {
        exactText != null -> exactText
        else -> "≈" + formatNumber(value)
    }
}

/**
 * A single interval of the solution set.
 *
 * A null [lo] means minus infinity and a null [hi] means plus infinity.
 */
class Interval(
    val lo: Endpoint?,
    val hi: Endpoint?,
) {
    val isUnboundedBelow: Boolean get() = lo == null
    val isUnboundedAbove: Boolean get() = hi == null

    /** The left end as a number, used when the interval is bounded. */
    fun loValue(): Double = lo?.value ?: Double.NEGATIVE_INFINITY

    fun hiValue(): Double = hi?.value ?: Double.POSITIVE_INFINITY

    fun contains(x: Double): Boolean {
        if (lo != null) {
            if (x < lo.value) return false
            if (x == lo.value && !lo.closed) return false
        }
        if (hi != null) {
            if (x > hi.value) return false
            if (x == hi.value && !hi.closed) return false
        }
        return true
    }

    override fun toString(): String = buildString {
        if (lo == null) {
            append("(−∞")
        } else {
            append(if (lo.closed) "[" else "(")
            append(lo.display())
        }
        append(", ")
        if (hi == null) {
            append("∞)")
        } else {
            append(hi.display())
            append(if (hi.closed) "]" else ")")
        }
    }

    companion object {
        fun closed(lo: Endpoint, hi: Endpoint) = Interval(lo, hi)
    }
}

/**
 * A solution set: an ordered list of disjoint intervals.
 *
 * The empty list means "no value satisfies the inequality".
 */
class SolutionSet(val intervals: List<Interval>) {

    val isEmpty: Boolean get() = intervals.isEmpty()
    val isEverything: Boolean get() = intervals.size == 1 && intervals.first().isUnboundedBelow && intervals.first().isUnboundedAbove

    fun contains(x: Double): Boolean = intervals.any { it.contains(x) }

    /** The set in interval notation, e.g. `(-∞, 4] ∪ (2, ∞)`. */
    fun toIntervalNotation(): String =
        if (isEmpty) "∅" else intervals.joinToString(" ∪ ") { it.toString() }

    /** The set in set-builder notation, e.g. `{ x ∈ ℝ | x ≤ 4 }`. */
    fun toSetBuilder(variable: String): String = when {
        isEmpty -> "∅"
        else -> intervals.joinToString(" ∪ ") { interval ->
            val lo = interval.lo
            val hi = interval.hi
            val condition = when {
                lo == null && hi == null -> "true"
                lo == null -> "$variable ${if (hi!!.closed) "≤" else "<"} ${hi.display()}"
                hi == null -> "$variable ${if (lo.closed) "≥" else ">"} ${lo.display()}"
                lo.value == hi.value && lo.closed && hi.closed -> "$variable = ${lo.display()}"
                else -> {
                    val left = "${lo.display()} ${if (lo.closed) "≤" else "<"} $variable"
                    val right = "${if (hi.closed) "≤" else "<"} ${hi.display()}"
                    "$left $right"
                }
            }
            "{ $variable ∈ ℝ | $condition }"
        }
    }

    companion object {
        val EMPTY = SolutionSet(emptyList())
        val EVERYTHING = SolutionSet(listOf(Interval(null, null)))
    }
}

/** Merges two interval lists into their union, sorted and disjoint. */
fun unionOf(a: List<Interval>, b: List<Interval>): List<Interval> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val all = (a + b).sortedWith(
        compareBy({ it.loValue() }, { if (it.lo?.closed == true) 0 else 1 }),
    )
    val out = mutableListOf<Interval>()
    for (interval in all) {
        val last = out.lastOrNull()
        if (last != null && overlapOrTouch(last, interval)) {
            out[out.lastIndex] = merge(last, interval)
        } else {
            out.add(interval)
        }
    }
    return out
}

/** The intersection of two interval lists. */
fun intersectOf(a: List<Interval>, b: List<Interval>): List<Interval> {
    if (a.isEmpty() || b.isEmpty()) return emptyList()
    val out = mutableListOf<Interval>()
    for (x in a) {
        for (y in b) {
            val lo = maxOfEndpoints(x.lo, y.lo)
            val hi = minOfEndpoints(x.hi, y.hi)
            if (lo != null && hi != null && lo.value > hi.value) continue
            if (lo != null && hi != null && lo.value == hi.value &&
                (!lo.closed || !hi.closed)
            ) continue
            out.add(Interval(lo, hi))
        }
    }
    return mergeAdjacent(out)
}

private fun maxOfEndpoints(a: Endpoint?, b: Endpoint?): Endpoint? = when {
    a == null -> b
    b == null -> a
    a.value > b.value -> a
    a.value < b.value -> b
    else -> if (a.closed) a else b
}

private fun minOfEndpoints(a: Endpoint?, b: Endpoint?): Endpoint? = when {
    a == null -> b
    b == null -> a
    a.value < b.value -> a
    a.value > b.value -> b
    else -> if (a.closed) a else b
}

/**
 * True when the intervals overlap, or meet at a point that at least one of
 * them includes.
 *
 * `[0, 1)` and `[1, 2]` share the point 1, which the second one includes, so
 * their union is the single interval `[0, 2]`. Two open ends meeting at a
 * point leave that point out of the set, so they stay separate.
 */
private fun overlapOrTouch(a: Interval, b: Interval): Boolean {
    if (a.hiValue() < b.loValue()) return false
    if (b.hiValue() < a.loValue()) return false
    if (a.hiValue() == b.loValue()) return a.hi?.closed == true || b.lo?.closed == true
    if (b.hiValue() == a.loValue()) return b.hi?.closed == true || a.lo?.closed == true
    return true
}

private fun merge(a: Interval, b: Interval): Interval {
    val lo = if (a.lo == null) null else if (b.lo == null) a.lo else minOfEndpoints(a.lo, b.lo)
    val hi = if (a.hi == null) null else if (b.hi == null) a.hi else maxOfEndpoints(a.hi, b.hi)
    return Interval(lo, hi)
}

/** Sorts and merges touching intervals, used after an intersection. */
private fun mergeAdjacent(list: List<Interval>): List<Interval> {
    if (list.isEmpty()) return list
    val sorted = list.sortedWith(compareBy({ it.loValue() }, { if (it.lo?.closed == true) 0 else 1 }))
    val out = mutableListOf<Interval>()
    for (interval in sorted) {
        val last = out.lastOrNull()
        if (last != null && overlapOrTouch(last, interval)) {
            out[out.lastIndex] = merge(last, interval)
        } else {
            out.add(interval)
        }
    }
    return out
}
