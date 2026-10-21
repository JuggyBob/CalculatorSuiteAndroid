package com.calcsuite.engine.electrical

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One step of a calculation, as it is shown.
 *
 * @param title what is being worked out.
 * @param formula the equation that is used.
 * @param substitution the values that go into it.
 * @param calculation the arithmetic.
 * @param result the answer, with its unit.
 * @param note how the answer is used next.
 */
data class ExplanationStep(
    val title: String,
    val formula: String? = null,
    val substitution: String? = null,
    val calculation: String? = null,
    val result: String? = null,
    val note: String? = null,
)

/**
 * A symbol in a formula, and what it stands for.
 */
data class FormulaVariable(
    val symbol: String,
    val meaning: String,
    val value: String,
    val unit: String,
)

/**
 * The explanation of one result.
 *
 * @param title the name of the quantity.
 * @param value the answer, with its unit.
 * @param meaning what the answer says.
 * @param formula the equation that was used.
 * @param variables every symbol in the formula.
 * @param steps the working, in order.
 * @param why why the equation applies to this circuit.
 * @param checkTitle the name of the check.
 * @param checks the independent checks, with their numbers.
 */
data class CircuitExplanation(
    val title: String,
    val value: String,
    val meaning: String,
    val formula: String,
    val variables: List<FormulaVariable>,
    val steps: List<ExplanationStep>,
    val why: String,
    val checkTitle: String,
    val checks: List<ExplanationStep>,
)

/**
 * The explanation of one component.
 */
data class ComponentExplanation(
    val label: String,
    val kind: ComponentKind,
    val resistance: String?,
    val voltage: String,
    val current: String,
    val power: String,
    val formula: String,
    val variables: List<FormulaVariable>,
    val steps: List<ExplanationStep>,
    val note: String,
)

/**
 * Writes the explanation of a circuit.
 *
 * Everything here is worked out from the circuit that was actually solved
 * and from the numbers the solver returned, so the explanation cannot
 * describe a circuit or a set of values that were not used.
 */
object CircuitExplainer {

    /** How many digits the working is written with. */
    private const val DIGITS = 6

    /** A number written for the working. */
    fun number(value: Double): String {
        if (!value.isFinite()) return "?"
        if (value == 0.0) return "0"
        val magnitude = kotlin.math.abs(value)
        if (magnitude >= 1e6 || magnitude < 1e-4) {
            return "%.6E".format(value)
        }
        val rounded = BigDecimal.valueOf(value).setScale(DIGITS, RoundingMode.HALF_UP)
        return rounded.stripTrailingZeros().toPlainString()
    }

    /**
     * The explanation of the whole circuit.
     *
     * When the resistor network can be reduced by the series and parallel
     * rules, the reduction is shown with the names of the parts that were
     * combined. When it cannot, the nodal method is explained instead, and
     * the equations that were solved are named.
     */
    fun explain(
        circuit: Circuit,
        solution: DcSolution,
        topology: CircuitTopology,
    ): CircuitExplanation {
        val source = circuit.of(ComponentKind.VOLTAGE_SOURCE).firstOrNull()
        val voltage = source?.voltage?.toDouble() ?: 0.0
        val current = solution.totalSourceCurrent
        val equivalent = solution.equivalentResistance
        val reduction = SeriesParallelReducer(circuit, topology).reduce()

        val steps = mutableListOf<ExplanationStep>()
        val why = StringBuilder()

        if (reduction.reduced && equivalent != null) {
            why.append(
                "The parts of this circuit can be reduced one pair at a time: " +
                    "parts in series carry the same current, so their resistances add, " +
                    "and parts in parallel have the same voltage across them, so their " +
                    "conductances add.",
            )
            for (step in reduction.steps) {
                steps.add(
                    ExplanationStep(
                        title = "${step.names.joinToString(" and ")} are ${step.kind.label}",
                        formula = step.formula,
                        calculation = explainReduction(step),
                        result = "${number(step.after)} Ω",
                        note = "This is used in the next step.",
                    ),
                )
            }
            steps.add(
                ExplanationStep(
                    title = "The current the source delivers",
                    formula = "I = V ÷ R",
                    substitution = "= ${number(voltage)} V ÷ ${number(equivalent)} Ω",
                    calculation = "= ${number(voltage)} ÷ ${number(equivalent)}",
                    result = "= ${number(current)} A",
                    note = "This current flows through every part of the series path.",
                ),
            )
        } else {
            why.append(
                "This circuit has branches that cannot be reduced by the series and " +
                    "parallel rules, because reducing them would change the way the " +
                    "branches share the current. The voltages are found instead by " +
                    "nodal analysis: one equation for the current balance at every " +
                    "point that is not the ground, together with the voltage of each " +
                    "source.",
            )
            steps.add(
                ExplanationStep(
                    title = "The voltages are found by nodal analysis",
                    formula = "Σ I leaving a point = 0, with V across each source",
                    result = "the voltage of every point against the ground",
                    note = "The current of every part then follows from its voltage and " +
                        "its resistance.",
                ),
            )
            steps.add(
                ExplanationStep(
                    title = "The current the source delivers",
                    formula = "I = the sum of the currents leaving the source",
                    result = "= ${number(current)} A",
                    note = "This is the current that is shared between the branches.",
                ),
            )
        }

        val variables = buildList {
            add(FormulaVariable("V", "the voltage of the source", "${number(voltage)}", "V"))
            add(FormulaVariable("I", "the current the source delivers", "${number(current)}", "A"))
            if (equivalent != null) {
                add(
                    FormulaVariable(
                        "R",
                        "the resistance the source sees",
                        "${number(equivalent)}",
                        "Ω",
                    ),
                )
            }
        }

        val checks = buildChecks(circuit, solution, topology, current, voltage)

        return CircuitExplanation(
            title = "The circuit",
            value = if (equivalent != null) {
                "${number(equivalent)} Ω · ${number(current)} A"
            } else {
                "${number(current)} A"
            },
            meaning = if (equivalent != null) {
                "The source sees ${number(equivalent)} Ω, so it delivers " +
                    "${number(current)} A and ${number(solution.totalPowerSupplied)} W."
            } else {
                "The source delivers ${number(current)} A and " +
                    "${number(solution.totalPowerSupplied)} W. The circuit has more than " +
                    "one source, so a single equivalent resistance is not defined."
            },
            formula = if (equivalent != null) "I = V ÷ R" else "I = the sum of the branch currents",
            variables = variables,
            steps = steps,
            why = why.toString(),
            checkTitle = "Check your answer",
            checks = checks,
        )
    }

    /** The explanation of one component. */
    fun explainComponent(
        component: CircuitComponent,
        result: ComponentResult,
        solution: DcSolution,
    ): ComponentExplanation {
        val steps = mutableListOf<ExplanationStep>()
        val note: String
        val formula: String
        val variables = mutableListOf<FormulaVariable>()
        when (component.kind) {
            ComponentKind.RESISTOR -> {
                val resistance = component.resistance?.toDouble() ?: 0.0
                formula = "V = I × R, and P = V × I"
                variables.add(
                    FormulaVariable("R", "the resistance", "${number(resistance)}", "Ω"),
                )
                variables.add(
                    FormulaVariable("V", "the voltage across it", "${number(result.voltage)}", "V"),
                )
                variables.add(
                    FormulaVariable("I", "the current through it", "${number(result.current)}", "A"),
                )
                steps.add(
                    ExplanationStep(
                        title = "The current through it",
                        formula = "I = V ÷ R",
                        substitution = "= ${number(result.voltage)} V ÷ ${number(resistance)} Ω",
                        calculation = "= ${number(result.voltage)} ÷ ${number(resistance)}",
                        result = "= ${number(result.current)} A",
                    ),
                )
                steps.add(
                    ExplanationStep(
                        title = "The power it turns into heat",
                        formula = "P = V × I = V² ÷ R",
                        substitution = "= ${number(result.voltage)} V × ${number(result.current)} A",
                        calculation = "= ${number(result.voltage)} × ${number(result.current)}",
                        result = "= ${number(result.power)} W",
                        note = "A positive value means the part is taking the power in.",
                    ),
                )
                note = "The sign of the current says which way it runs through the part: " +
                    "from the first terminal to the second when it is positive."
            }
            ComponentKind.VOLTAGE_SOURCE -> {
                val voltage = component.voltage?.toDouble() ?: 0.0
                val delivered = -(result.current)
                formula = "P = V × I, and the current it delivers"
                variables.add(
                    FormulaVariable("V", "the voltage it holds", "${number(voltage)}", "V"),
                )
                variables.add(
                    FormulaVariable(
                        "I",
                        "the current it delivers",
                        "${number(delivered)}",
                        "A",
                    ),
                )
                steps.add(
                    ExplanationStep(
                        title = "The current it delivers",
                        formula = "I = the current the circuit takes from it",
                        result = "= ${number(delivered)} A",
                        note = "A positive value means the source is supplying power.",
                    ),
                )
                steps.add(
                    ExplanationStep(
                        title = "The power it supplies",
                        formula = "P = V × I",
                        substitution = "= ${number(voltage)} V × ${number(delivered)} A",
                        calculation = "= ${number(voltage)} × ${number(delivered)}",
                        result = "= ${number(solution.totalPowerSupplied)} W",
                    ),
                )
                note = "A source supplies power when the power it absorbs is negative."
            }
            ComponentKind.SWITCH -> {
                formula = "an ideal switch is a conductor when it is closed"
                steps.add(
                    ExplanationStep(
                        title = if (component.switchClosed) {
                            "The switch is closed, so it joins its two terminals"
                        } else {
                            "The switch is open, so no current passes through it"
                        },
                        formula = "I = 0 for an open switch",
                        result = if (component.switchClosed) {
                            "= ${number(result.current)} A through the switch"
                        } else {
                            "= 0 A through the switch"
                        },
                        note = if (component.switchClosed) {
                            "A closed ideal switch has no voltage across it."
                        } else {
                            "The whole voltage of the source appears across an open switch."
                        },
                    ),
                )
                note = "An open switch breaks the branch, so the current in it is zero."
            }
            ComponentKind.CURRENT_SOURCE -> {
                val current = component.current?.toDouble() ?: 0.0
                formula = "P = V × I"
                variables.add(
                    FormulaVariable("I", "the current it pushes", "${number(current)}", "A"),
                )
                steps.add(
                    ExplanationStep(
                        title = "The current it pushes",
                        formula = "I = the current it is set to",
                        result = "= ${number(current)} A",
                    ),
                )
                note = "The current a source pushes is the same through every part of " +
                    "the branch it feeds."
            }
            ComponentKind.GROUND -> {
                formula = "the ground is the reference point"
                steps.add(
                    ExplanationStep(
                        title = "Every voltage is measured against this point",
                        formula = "V = the voltage of a point − the voltage of the ground",
                        result = "= 0 V",
                    ),
                )
                note = "Without a ground the voltages have no reference, and the " +
                    "circuit cannot be solved."
            }
        }

        return ComponentExplanation(
            label = component.label,
            kind = component.kind,
            resistance = component.resistance?.let {
                "${number(it.toDouble())} Ω"
            },
            voltage = "${number(result.voltage)} V",
            current = "${number(result.current)} A",
            power = "${number(result.power)} W",
            formula = formula,
            variables = variables,
            steps = steps,
            note = note,
        )
    }

    /** The independent checks of the answer. */
    private fun buildChecks(
        circuit: Circuit,
        solution: DcSolution,
        topology: CircuitTopology,
        current: Double,
        voltage: Double,
    ): List<ExplanationStep> {
        val checks = mutableListOf<ExplanationStep>()

        // The current balance at every point.
        var worstNode = -1
        var worst = 0.0
        for (node in 0 until topology.nodeCount) {
            if (node == topology.groundNode) continue
            var leaving = 0.0
            for (component in circuit.components) {
                val result = solution.components[component.id] ?: continue
                val terminals = component.terminals
                val nodeA = terminals.firstOrNull()?.let { topology.nodeOf(it) }
                val nodeB = terminals.getOrNull(1)?.let { topology.nodeOf(it) }
                if (nodeA == node) leaving += result.current
                if (nodeB == node) leaving -= result.current
            }
            if (kotlin.math.abs(leaving) > kotlin.math.abs(worst)) {
                worst = leaving
                worstNode = node
            }
        }
        if (worstNode >= 0) {
            checks.add(
                ExplanationStep(
                    title = "The current balances at every point",
                    formula = "Σ I in = Σ I out",
                    result = "the largest mismatch is ${number(worst)} A",
                    note = "This is Kirchhoff's current law, checked on the circuit itself.",
                ),
            )
        }

        // The power balance.
        checks.add(
            ExplanationStep(
                title = "The power the source supplies is the power the parts take in",
                formula = "Σ P supplied = Σ P absorbed",
                substitution = "= ${number(solution.totalPowerSupplied)} W and " +
                    "${number(solution.totalPowerDissipated)} W",
                result = "the difference is ${number(solution.powerBalance)} W",
            ),
        )

        // The drops around the loop, when there is one source.
        val source = circuit.of(ComponentKind.VOLTAGE_SOURCE).firstOrNull()
        if (source != null) {
            val drops = circuit.of(ComponentKind.RESISTOR).sumOf {
                solution.components[it.id]?.voltage ?: 0.0
            }
            checks.add(
                ExplanationStep(
                    title = "The voltage drops add up to the source",
                    formula = "Σ V drops = V of the source",
                    substitution = "= ${number(drops)} V and ${number(voltage)} V",
                    result = "the difference is ${number(voltage - drops)} V",
                    note = "This is Kirchhoff's voltage law around the loop.",
                ),
            )
        }

        return checks
    }

    /** The arithmetic of one reduction, written out. */
    private fun explainReduction(step: Reduction): String {
        val values = step.values
        return when (step.kind) {
            ReductionKind.SERIES ->
                if (values.size == 2) {
                    "= ${number(values[0])} Ω + ${number(values[1])} Ω = ${number(step.after)} Ω"
                } else {
                    "= ${number(step.after)} Ω"
                }
            ReductionKind.PARALLEL ->
                if (values.size == 2) {
                    val conductance = 1.0 / values[0] + 1.0 / values[1]
                    "= 1 ÷ ${number(values[0])} + 1 ÷ ${number(values[1])} = " +
                        "${number(conductance)} S, and 1 ÷ ${number(conductance)} = " +
                        "${number(step.after)} Ω"
                } else {
                    "= ${number(step.after)} Ω"
                }
        }
    }
}
