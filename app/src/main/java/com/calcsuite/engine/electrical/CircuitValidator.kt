package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok

/**
 * How the circuit stands.
 *
 * The four states are told apart, because they need different things from
 * the user: something to add, something to finish, something to correct, and
 * something that cannot be solved as it stands.
 */
enum class CircuitState {
    /** Nothing has been placed yet. */
    EMPTY,

    /** There is a circuit, but it is not finished. */
    INCOMPLETE,

    /** The circuit cannot be worked out as it is. */
    INVALID,

    /** The circuit is finished and has been solved. */
    SOLVED,
}

/**
 * What is wrong with the circuit, and what to do about it.
 *
 * @param state how the circuit stands.
 * @param message what is wrong, in the words of somebody looking at it.
 * @param suggestion what to do about it.
 * @param componentId the part the problem is in, when there is one.
 * @param solution the answer, when the circuit could be solved.
 */
data class CircuitAssessment(
    val state: CircuitState,
    val message: String? = null,
    val suggestion: String? = null,
    val componentId: String? = null,
    val solution: DcSolution? = null,
    val explanation: CircuitExplanation? = null,
) {
    /** True when the circuit can be worked out. */
    val isSolved: Boolean get() = state == CircuitState.SOLVED && solution != null
}

/**
 * Looks at a circuit and says whether it can be worked out.
 *
 * The checks are in the order a person would make them: is there anything
 * there, is everything joined up, is there a ground, and then can the
 * equations be solved.
 */
object CircuitValidator {

    /** The assessment of a circuit. */
    fun assess(circuit: Circuit): CircuitAssessment {
        if (circuit.components.isEmpty()) {
            return CircuitAssessment(
                state = CircuitState.EMPTY,
                message = "The circuit is empty.",
                suggestion = "Put a source and a part on the canvas to begin.",
            )
        }
        val topology = CircuitTopology(circuit)

        // A wire that does not end on a terminal.
        val broken = topology.brokenWires()
        if (broken.isNotEmpty()) {
            return CircuitAssessment(
                state = CircuitState.INCOMPLETE,
                message = "A wire does not end on a terminal.",
                suggestion = "Finish the wire or delete it.",
            )
        }

        // A part with a terminal that joins nothing.
        val dangling = topology.danglingTerminals()
        if (dangling.isNotEmpty()) {
            val first = dangling.first()
            val label = circuit.component(first.componentId)?.label ?: "a part"
            return CircuitAssessment(
                state = CircuitState.INCOMPLETE,
                message = "$label is not joined to anything on one side.",
                suggestion = "Wire $label to the rest of the circuit, or delete it.",
                componentId = first.componentId,
            )
        }

        // A ground is needed so that the voltages have a reference.
        if (topology.groundNode == null) {
            return CircuitAssessment(
                state = CircuitState.INCOMPLETE,
                message = "The circuit has no ground.",
                suggestion = "Add a ground, so that the voltages have something to be " +
                    "measured against.",
            )
        }

        // A part with no value.
        for (component in circuit.components) {
            when (component.kind) {
                ComponentKind.RESISTOR -> {
                    val value = component.resistance?.toDouble()
                    if (value == null) {
                        return CircuitAssessment(
                            state = CircuitState.INCOMPLETE,
                            message = "${component.label} has no resistance yet.",
                            suggestion = "Give ${component.label} a resistance.",
                            componentId = component.id,
                        )
                    }
                    if (value <= 0.0) {
                        return CircuitAssessment(
                            state = CircuitState.INVALID,
                            message = "The resistance of ${component.label} must be greater " +
                                "than zero.",
                            suggestion = "A part of zero resistance is a wire, not a resistor.",
                            componentId = component.id,
                        )
                    }
                }
                ComponentKind.VOLTAGE_SOURCE -> {
                    if (component.voltage == null) {
                        return CircuitAssessment(
                            state = CircuitState.INCOMPLETE,
                            message = "${component.label} has no voltage yet.",
                            suggestion = "Give ${component.label} a voltage.",
                            componentId = component.id,
                        )
                    }
                }
                ComponentKind.CURRENT_SOURCE -> {
                    if (component.current == null) {
                        return CircuitAssessment(
                            state = CircuitState.INCOMPLETE,
                            message = "${component.label} has no current yet.",
                            suggestion = "Give ${component.label} a current.",
                            componentId = component.id,
                        )
                    }
                }
                else -> Unit
            }
        }

        // Now the equations themselves.
        return when (val solved = DcSolver.solve(circuit)) {
            is CalcResult.Err -> CircuitAssessment(
                state = CircuitState.INVALID,
                message = solved.error.message,
                suggestion = suggestionFor(solved.error.kind),
            )
            is CalcResult.Ok -> CircuitAssessment(
                state = CircuitState.SOLVED,
                solution = solved.value,
                explanation = CircuitExplainer.explain(circuit, solved.value, topology),
            )
        }
    }

    /** What to suggest for a failure. */
    private fun suggestionFor(kind: ErrorKind): String = when (kind) {
        ErrorKind.Insufficient ->
            "The circuit needs more information before it can be worked out."
        ErrorKind.Inconsistent ->
            "Check the sources: two of them may be asking for different voltages at the " +
                "same pair of points."
        else ->
            "Check that every part is joined to the ground, and that the values make sense."
    }
}
