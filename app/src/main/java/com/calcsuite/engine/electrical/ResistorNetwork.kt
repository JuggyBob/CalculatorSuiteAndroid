package com.calcsuite.engine.electrical

import java.math.BigDecimal

/**
 * A resistor network, as a tree of series and parallel groups.
 *
 * A network is either one resistor, or a group of networks joined in series
 * or in parallel. Nothing in it is limited to a fixed number of resistors:
 * a group may hold as many as the user needs, and a group may hold other
 * groups, to any depth.
 */
sealed interface ResistorNetwork {

    /** The name of one part of the network, for the working. */
    val name: String

    /** One resistor. */
    data class Resistor(
        override val name: String,
        val resistance: BigDecimal,
    ) : ResistorNetwork

    /** A group of networks joined in series, one after another. */
    data class Series(val children: List<ResistorNetwork>) : ResistorNetwork {
        override val name: String get() = "the series group"
    }

    /** A group of networks joined in parallel, side by side. */
    data class Parallel(val children: List<ResistorNetwork>) : ResistorNetwork {
        override val name: String get() = "the parallel group"
    }
}

/**
 * One step of the reduction of a network.
 *
 * @param kind whether the group was joined in series or in parallel.
 * @param names the names of the parts that were combined.
 * @param detail the arithmetic of the step.
 * @param result the resistance the group makes.
 */
data class NetworkStep(
    val kind: ReductionKind,
    val names: List<String>,
    val detail: String,
    val result: Double,
)

/**
 * The result of reducing a network.
 *
 * @param equivalent the resistance the whole network makes, in ohms.
 * @param steps the reductions, in the order they were made.
 */
data class NetworkReduction(
    val equivalent: Double,
    val steps: List<NetworkStep>,
)

/**
 * Reduces a resistor network to one resistance.
 *
 * The reduction is done from the inside out: the smallest groups are reduced
 * first, and each one is replaced by the resistance it makes, until only one
 * resistance is left. The steps say which parts were combined and what the
 * arithmetic was, so that the explanation follows the network the user
 * actually built.
 */
object ResistorNetworkReducer {

    /**
     * Reduces a network to one resistance.
     *
     * @param network the network to reduce.
     * @return the resistance it makes and the steps that produced it, or null
     *   when the network cannot be reduced, which happens when a part has no
     *   resistance or a resistance of zero.
     */
    fun reduce(network: ResistorNetwork): NetworkReduction? {
        val steps = mutableListOf<NetworkStep>()
        val value = reduceOne(network, steps) ?: return null
        return NetworkReduction(value, steps)
    }

    /**
     * The resistance a network makes, and the steps that produced it.
     */
    private fun reduceOne(network: ResistorNetwork, steps: MutableList<NetworkStep>): Double? =
        when (network) {
            is ResistorNetwork.Resistor -> network.resistance.toDouble()
            is ResistorNetwork.Series -> reduceGroup(network, steps, series = true)
            is ResistorNetwork.Parallel -> reduceGroup(network, steps, series = false)
        }

    /**
     * Reduces a group: the parts are reduced first, and then they are
     * combined.
     */
    private fun reduceGroup(
        group: ResistorNetwork.Series,
        steps: MutableList<NetworkStep>,
        series: Boolean,
    ): Double? = reduceGroupParts(group.children, steps, series)

    private fun reduceGroup(
        group: ResistorNetwork.Parallel,
        steps: MutableList<NetworkStep>,
        series: Boolean,
    ): Double? = reduceGroupParts(group.children, steps, series)

    private fun reduceGroupParts(
        children: List<ResistorNetwork>,
        steps: MutableList<NetworkStep>,
        series: Boolean,
    ): Double? {
        if (children.isEmpty()) return null
        val values = mutableListOf<Double>()
        val names = mutableListOf<String>()
        for (child in children) {
            val value = reduceOne(child, steps) ?: return null
            if (value <= 0.0) return null
            values.add(value)
            names.add(child.name)
        }
        if (values.size == 1) return values.first()

        val result: Double
        val detail: String
        if (series) {
            result = values.sum()
            detail = values.joinToString(" + ") { CircuitExplainer.number(it) } +
                " = " + CircuitExplainer.number(result)
        } else {
            val conductance = values.sumOf { 1.0 / it }
            result = 1.0 / conductance
            detail = values.joinToString(" + ") { "1 ÷ " + CircuitExplainer.number(it) } +
                " = " + CircuitExplainer.number(conductance) + " S, " +
                "so 1 ÷ that = " + CircuitExplainer.number(result) + " Ω"
        }
        steps.add(
            NetworkStep(
                kind = if (series) ReductionKind.SERIES else ReductionKind.PARALLEL,
                names = names,
                detail = detail,
                result = result,
            ),
        )
        return result
    }

    /**
     * The formula of a group, for the explanation.
     */
    fun formula(kind: ReductionKind, count: Int): String = when (kind) {
        ReductionKind.SERIES -> "R = R₁ + R₂ + … + Rₙ"
        ReductionKind.PARALLEL -> "1 ÷ R = 1 ÷ R₁ + 1 ÷ R₂ + … + 1 ÷ Rₙ"
    }
}
