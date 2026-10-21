package com.calcsuite.engine.electrical

/**
 * A reduction of a resistor network, in the order it was made.
 *
 * @param kind whether the two parts were in series or in parallel.
 * @param names the labels of the parts that were combined.
 * @param before the resistance of the two parts together, in ohms.
 * @param after the resistance they make, in ohms.
 * @param formula the formula that was used.
 * @param values the resistances of the parts that were combined, in ohms.
 */
data class Reduction(
    val kind: ReductionKind,
    val names: List<String>,
    val before: Double,
    val after: Double,
    val formula: String,
    val values: List<Double> = emptyList(),
)

/** The two ways two parts can be combined. */
enum class ReductionKind(val label: String, val formula: String) {
    SERIES("in series", "R = R₁ + R₂"),
    PARALLEL("in parallel", "1 ÷ R = 1 ÷ R₁ + 1 ÷ R₂"),
}

/**
 * The result of reducing a resistor network.
 *
 * @param steps the reductions, in the order they were made.
 * @param equivalent the resistance the whole network makes, in ohms, when it
 *   could be reduced to one part.
 * @param reduced true when the network was reduced to a single resistance.
 */
data class ReductionResult(
    val steps: List<Reduction> = emptyList(),
    val equivalent: Double? = null,
    val reduced: Boolean = false,
)

/**
 * Reduces a resistor network by the series and parallel rules.
 *
 * The reduction is only done when it is valid: two parts are in series when
 * they meet at a point that joins nothing else, and in parallel when they
 * join the same two points. A network that cannot be reduced is reported as
 * such, so that the explanation can fall back on the nodal method rather
 * than showing a reduction that does not apply.
 */
class SeriesParallelReducer(
    private val circuit: Circuit,
    private val topology: CircuitTopology,
) {

    /** One part of the network. */
    private class Edge(
        val id: String,
        val label: String,
        val a: Int,
        val b: Int,
        var resistance: Double,
        val isSource: Boolean = false,
    )

    /** Reduces the network. */
    fun reduce(): ReductionResult {
        val edges = mutableListOf<Edge>()
        for (component in circuit.components) {
            val terminals = component.terminals
            val a = terminals.firstOrNull()?.let { topology.nodeOf(it) } ?: continue
            val b = terminals.getOrNull(1)?.let { topology.nodeOf(it) }
            when (component.kind) {
                ComponentKind.RESISTOR -> {
                    val value = component.resistance?.toDouble() ?: continue
                    if (b == null) continue
                    edges.add(Edge(component.id, component.label, a, b, value))
                }
                ComponentKind.VOLTAGE_SOURCE -> {
                    if (b == null) continue
                    // The source is a part of the network as well, so that a
                    // point it joins is never reduced away.
                    edges.add(Edge(component.id, component.label, a, b, Double.MAX_VALUE, true))
                }
                else -> Unit
            }
        }
        if (edges.none { !it.isSource }) {
            return ReductionResult()
        }

        val steps = mutableListOf<Reduction>()
        var changed = true
        while (changed) {
            changed = false
            // Two parts in parallel: the same two points.
            val parallel = edges
                .filterNot { it.isSource }
                .groupBy { if (it.a < it.b) it.a to it.b else it.b to it.a }
                .values
                .firstOrNull { it.size >= 2 }
            if (parallel != null) {
                val first = parallel[0]
                val second = parallel[1]
                val combined = first.resistance * second.resistance / (first.resistance + second.resistance)
                steps.add(
                    Reduction(
                        kind = ReductionKind.PARALLEL,
                        names = listOf(first.label, second.label),
                        before = combined,
                        after = combined,
                        formula = "R = (R₁ × R₂) ÷ (R₁ + R₂)",
                        values = listOf(first.resistance, second.resistance),
                    ),
                )
                edges.remove(first)
                edges.remove(second)
                edges.add(
                    Edge(
                        id = first.id + "+" + second.id,
                        label = "${first.label} and ${second.label}",
                        a = first.a,
                        b = first.b,
                        resistance = combined,
                    ),
                )
                changed = true
                continue
            }
            // Two parts in series: a point that joins nothing else.
            val degrees = mutableMapOf<Int, MutableList<Edge>>()
            for (edge in edges) {
                degrees.getOrPut(edge.a) { mutableListOf() }.add(edge)
                if (edge.b != edge.a) {
                    degrees.getOrPut(edge.b) { mutableListOf() }.add(edge)
                }
            }
            val series = degrees.entries.firstOrNull { entry ->
                entry.value.size == 2 && entry.value.none { it.isSource } &&
                    entry.value[0].a != entry.value[1].a
            }
            if (series != null) {
                val first = series.value[0]
                val second = series.value[1]
                val combined = first.resistance + second.resistance
                steps.add(
                    Reduction(
                        kind = ReductionKind.SERIES,
                        names = listOf(first.label, second.label),
                        before = combined,
                        after = combined,
                        formula = "R = R₁ + R₂",
                        values = listOf(first.resistance, second.resistance),
                    ),
                )
                edges.remove(first)
                edges.remove(second)
                val other = if (first.a == series.key) second.b else first.b
                val end = if (first.a == series.key) first.b else second.b
                edges.add(
                    Edge(
                        id = first.id + "+" + second.id,
                        label = "${first.label} and ${second.label}",
                        a = other,
                        b = end,
                        resistance = combined,
                    ),
                )
                changed = true
            }
        }

        val remaining = edges.filterNot { it.isSource }
        val equivalent = if (remaining.size == 1) remaining.first().resistance else null
        return ReductionResult(
            steps = steps,
            equivalent = equivalent,
            reduced = remaining.size == 1,
        )
    }
}
