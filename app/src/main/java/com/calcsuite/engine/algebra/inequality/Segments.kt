package com.calcsuite.engine.algebra.inequality

/**
 * One end of a solution segment.
 *
 * [display] carries the exact text when the endpoint is known exactly, and the
 * approximate text otherwise, so the number line can label a point `1/3`
 * rather than `0.3333333333`.
 */
data class Bound(val value: Double, val display: String, val isExact: Boolean)

/**
 * A piece of the solution set before merging.
 *
 * A null [lo] means minus infinity and a null [hi] means plus infinity.
 */
class Segment(
    val lo: Bound?,
    val loClosed: Boolean,
    val hi: Bound?,
    val hiClosed: Boolean,
) {
    fun toInterval(): Interval = Interval(
        lo = lo?.let { Endpoint(it.value, it.display.takeIf { _ -> it.isExact }, loClosed) },
        hi = hi?.let { Endpoint(it.value, it.display.takeIf { _ -> it.isExact }, hiClosed) },
    )

    companion object {
        fun open(lo: Bound?, hi: Bound?) = Segment(lo, false, hi, false)
        fun point(at: Bound) = Segment(at, true, at, true)
    }
}

/**
 * Merges solution segments into the fewest possible intervals.
 *
 * Two segments join only when they meet at a point that at least one of them
 * includes. An open end meeting an open end leaves that point out of the
 * solution, so the segments stay separate — which is exactly the case of a
 * strict inequality around a zero of the expression.
 */
fun mergeSegments(segments: List<Segment>): List<Interval> {
    if (segments.isEmpty()) return emptyList()
    val sorted = segments.sortedWith(
        compareBy({ it.lo?.value ?: Double.NEGATIVE_INFINITY }, { if (it.loClosed) 0 else 1 }),
    )
    val merged = mutableListOf<Segment>()
    for (segment in sorted) {
        val last = merged.lastOrNull()
        if (last != null && canMerge(last, segment)) {
            merged[merged.lastIndex] = Segment(
                lo = last.lo,
                loClosed = last.loClosed,
                hi = segment.hi,
                hiClosed = segment.hiClosed,
            )
        } else {
            merged.add(segment)
        }
    }
    return merged.map { it.toInterval() }
}

/** True when two segments meet at a point that at least one of them includes. */
private fun canMerge(a: Segment, b: Segment): Boolean {
    val aHi = a.hi ?: return false
    val bLo = b.lo ?: return false
    if (aHi.value != bLo.value) return false
    return a.hiClosed || b.loClosed
}
