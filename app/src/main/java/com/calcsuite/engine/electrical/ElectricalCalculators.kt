package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * The answer of one of the electrical calculators.
 *
 * Every calculator gives the same shape, so that they all read the same
 * way and can share the same screen.
 */
data class CalculatorResult(
    val title: String,
    /** The main answer, with its unit. */
    val headline: String,
    /** The other values, as label and value. */
    val rows: List<Pair<String, String>> = emptyList(),
    /** The symbols of the formula, and what they stand for. */
    val variables: List<FormulaVariable> = emptyList(),
    /** The working, in order. */
    val steps: List<ExplanationStep> = emptyList(),
    /** Why the formula applies. */
    val why: String = "",
    /** The independent checks, with their numbers. */
    val checks: List<ExplanationStep> = emptyList(),
    /** The formula that was used. */
    val formula: String = "",
)

/**
 * The electrical calculators.
 *
 * Each one works in SI units and is checked against the textbook answers.
 */
object ElectricalCalculators {

    // ---------------------------------------------------------- series

    /**
     * The resistors in series across a source.
     *
     * The same current flows through all of them, their resistances add, and
     * the voltage drops add up to the source.
     */
    fun series(
        resistances: List<Pair<String, BigDecimal>>,
        voltage: BigDecimal,
    ): CalcResult<CalculatorResult> {
        if (resistances.isEmpty()) {
            return err(ErrorKind.InvalidInput, "Add at least one resistor.")
        }
        for ((label, value) in resistances) {
            if (value.signum() <= 0) {
                return err(ErrorKind.InvalidInput, "The resistance of $label must be greater than zero.")
            }
        }
        val v = voltage.toDouble()
        val values = resistances.map { it.second.toDouble() }
        val total = values.sum()
        val current = if (total == 0.0) 0.0 else v / total
        val drops = values.map { it * current }
        val powers = values.map { current * current * it }
        val totalPower = current * v

        val steps = mutableListOf<ExplanationStep>()
        steps.add(
            ExplanationStep(
                title = "The resistances add",
                formula = "R = R₁ + R₂ + … + Rₙ",
                substitution = "= " + resistances.joinToString(" + ") { it.second.toPlainString() },
                calculation = "= ${CircuitExplainer.number(total)} Ω",
                result = "R = ${CircuitExplainer.number(total)} Ω",
                note = "The same current flows through every part, because there is " +
                    "nowhere for it to go.",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The current through the circuit",
                formula = "I = V ÷ R",
                substitution = "= ${CircuitExplainer.number(v)} V ÷ ${CircuitExplainer.number(total)} Ω",
                calculation = "= ${CircuitExplainer.number(v)} ÷ ${CircuitExplainer.number(total)}",
                result = "I = ${CircuitExplainer.number(current)} A",
            ),
        )
        for ((index, entry) in resistances.withIndex()) {
            steps.add(
                ExplanationStep(
                    title = "The drop across ${entry.first}",
                    formula = "V = I × R",
                    substitution = "= ${CircuitExplainer.number(current)} A × ${entry.second.toPlainString()} Ω",
                    calculation = "= ${CircuitExplainer.number(current)} × ${entry.second.toPlainString()}",
                    result = "V = ${CircuitExplainer.number(drops[index])} V",
                    note = "The power it turns into heat is P = I² × R = " +
                        "${CircuitExplainer.number(powers[index])} W.",
                ),
            )
        }
        steps.add(
            ExplanationStep(
                title = "The power the whole circuit takes",
                formula = "P = V × I",
                substitution = "= ${CircuitExplainer.number(v)} V × ${CircuitExplainer.number(current)} A",
                calculation = "= ${CircuitExplainer.number(v)} × ${CircuitExplainer.number(current)}",
                result = "P = ${CircuitExplainer.number(totalPower)} W",
            ),
        )

        return ok(
            CalculatorResult(
                title = "Resistors in series",
                headline = "R = ${CircuitExplainer.number(total)} Ω",
                rows = buildList {
                    add("Total resistance" to "${CircuitExplainer.number(total)} Ω")
                    add("Current" to "${CircuitExplainer.number(current)} A")
                    resistances.forEachIndexed { index, entry ->
                        add("Drop across ${entry.first}" to "${CircuitExplainer.number(drops[index])} V")
                        add("Power in ${entry.first}" to "${CircuitExplainer.number(powers[index])} W")
                    }
                    add("Total power" to "${CircuitExplainer.number(totalPower)} W")
                },
                variables = listOf(
                    FormulaVariable("V", "the voltage of the source", CircuitExplainer.number(v), "V"),
                    FormulaVariable("I", "the current through the circuit", CircuitExplainer.number(current), "A"),
                    FormulaVariable("R", "the total resistance", CircuitExplainer.number(total), "Ω"),
                ),
                steps = steps,
                why = "The parts are in series because the same current flows through " +
                    "every one of them, so their resistances add and the voltage " +
                    "drops add up to the source.",
                checks = listOf(
                    ExplanationStep(
                        title = "The drops add up to the source",
                        formula = "Σ V drops = V of the source",
                        substitution = "= " + drops.joinToString(" + ") {
                            CircuitExplainer.number(it)
                        } + " V and ${CircuitExplainer.number(v)} V",
                        result = "the difference is ${CircuitExplainer.number(v - drops.sum())} V",
                    ),
                    ExplanationStep(
                        title = "The powers add up to the power of the source",
                        formula = "Σ P = P of the source",
                        substitution = "= ${CircuitExplainer.number(powers.sum())} W and " +
                            "${CircuitExplainer.number(totalPower)} W",
                        result = "the difference is ${CircuitExplainer.number(totalPower - powers.sum())} W",
                    ),
                ),
                formula = "R = R₁ + R₂ + … + Rₙ,   I = V ÷ R,   V = I × R,   P = V × I",
            ),
        )
    }

    // -------------------------------------------------------- parallel

    /**
     * The resistors in parallel across a source.
     *
     * Each branch has the whole of the source voltage across it, and the
     * branch currents add up to the current the source delivers.
     */
    fun parallel(
        resistances: List<Pair<String, BigDecimal>>,
        voltage: BigDecimal,
    ): CalcResult<CalculatorResult> {
        if (resistances.isEmpty()) {
            return err(ErrorKind.InvalidInput, "Add at least one resistor.")
        }
        for ((label, value) in resistances) {
            if (value.signum() <= 0) {
                return err(ErrorKind.InvalidInput, "The resistance of $label must be greater than zero.")
            }
        }
        val v = voltage.toDouble()
        val values = resistances.map { it.second.toDouble() }
        val conductance = values.sumOf { 1.0 / it }
        val total = if (conductance == 0.0) 0.0 else 1.0 / conductance
        val currents = values.map { v / it }
        val powers = values.map { v * v / it }
        val totalCurrent = currents.sum()
        val totalPower = v * totalCurrent

        val steps = mutableListOf<ExplanationStep>()
        steps.add(
            ExplanationStep(
                title = "The conductances add",
                formula = "1 ÷ R = 1 ÷ R₁ + 1 ÷ R₂ + … + 1 ÷ Rₙ",
                substitution = "= " + resistances.joinToString(" + ") {
                    "1 ÷ ${it.second.toPlainString()}"
                },
                calculation = "= " + values.joinToString(" + ") {
                    "1 ÷ ${CircuitExplainer.number(it)}"
                },
                result = "= ${CircuitExplainer.number(conductance)} S",
                note = "The resistance of the combination is the reciprocal of that.",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The resistance of the combination",
                formula = "R = 1 ÷ (1 ÷ R₁ + 1 ÷ R₂ + … + 1 ÷ Rₙ)",
                substitution = "= 1 ÷ ${CircuitExplainer.number(conductance)}",
                calculation = "= 1 ÷ ${CircuitExplainer.number(conductance)}",
                result = "R = ${CircuitExplainer.number(total)} Ω",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The current the source delivers",
                formula = "I = V ÷ R",
                substitution = "= ${CircuitExplainer.number(v)} V ÷ ${CircuitExplainer.number(total)} Ω",
                calculation = "= ${CircuitExplainer.number(v)} ÷ ${CircuitExplainer.number(total)}",
                result = "I = ${CircuitExplainer.number(totalCurrent)} A",
            ),
        )
        for ((index, entry) in resistances.withIndex()) {
            steps.add(
                ExplanationStep(
                    title = "The current through ${entry.first}",
                    formula = "I = V ÷ R",
                    substitution = "= ${CircuitExplainer.number(v)} V ÷ ${entry.second.toPlainString()} Ω",
                    calculation = "= ${CircuitExplainer.number(v)} ÷ ${entry.second.toPlainString()}",
                    result = "I = ${CircuitExplainer.number(currents[index])} A",
                    note = "The power it turns into heat is P = V² ÷ R = " +
                        "${CircuitExplainer.number(powers[index])} W.",
                ),
            )
        }

        return ok(
            CalculatorResult(
                title = "Resistors in parallel",
                headline = "R = ${CircuitExplainer.number(total)} Ω",
                rows = buildList {
                    add("Combined resistance" to "${CircuitExplainer.number(total)} Ω")
                    add("Total current" to "${CircuitExplainer.number(totalCurrent)} A")
                    resistances.forEachIndexed { index, entry ->
                        add("Current in ${entry.first}" to "${CircuitExplainer.number(currents[index])} A")
                        add("Power in ${entry.first}" to "${CircuitExplainer.number(powers[index])} W")
                    }
                    add("Total power" to "${CircuitExplainer.number(totalPower)} W")
                },
                variables = listOf(
                    FormulaVariable("V", "the voltage of the source", CircuitExplainer.number(v), "V"),
                    FormulaVariable("I", "the total current", CircuitExplainer.number(totalCurrent), "A"),
                    FormulaVariable("R", "the combined resistance", CircuitExplainer.number(total), "Ω"),
                ),
                steps = steps,
                why = "The parts are in parallel because each branch is joined to the " +
                    "same two points, so each has the whole of the source voltage " +
                    "across it and the branch currents add up.",
                checks = listOf(
                    ExplanationStep(
                        title = "The branch currents add up to the total",
                        formula = "Σ I branch = I total",
                        substitution = "= " + currents.joinToString(" + ") {
                            CircuitExplainer.number(it)
                        } + " A and ${CircuitExplainer.number(totalCurrent)} A",
                        result = "the difference is ${CircuitExplainer.number(totalCurrent - currents.sum())} A",
                    ),
                    ExplanationStep(
                        title = "The powers add up to the power of the source",
                        formula = "Σ P = P of the source",
                        substitution = "= ${CircuitExplainer.number(powers.sum())} W and " +
                            "${CircuitExplainer.number(totalPower)} W",
                        result = "the difference is ${CircuitExplainer.number(totalPower - powers.sum())} W",
                    ),
                ),
                formula = "1 ÷ R = 1 ÷ R₁ + 1 ÷ R₂ + … + 1 ÷ Rₙ,   I = V ÷ R,   P = V² ÷ R",
            ),
        )
    }

    // ----------------------------------------------------------- mixed

    /**
     * A resistor in series with a parallel pair.
     *
     * This is the commonest mixed network: the parallel part is reduced
     * first, and then the series part is added to it.
     */
    fun mixed(
        seriesLabel: String,
        seriesResistance: BigDecimal,
        parallel: List<Pair<String, BigDecimal>>,
        voltage: BigDecimal,
    ): CalcResult<CalculatorResult> {
        if (seriesResistance.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "The series resistance must be greater than zero.")
        }
        if (parallel.isEmpty()) {
            return err(ErrorKind.InvalidInput, "Add at least one resistor to the parallel part.")
        }
        for ((label, value) in parallel) {
            if (value.signum() <= 0) {
                return err(ErrorKind.InvalidInput, "The resistance of $label must be greater than zero.")
            }
        }
        val v = voltage.toDouble()
        val seriesValue = seriesResistance.toDouble()
        val parallelValues = parallel.map { it.second.toDouble() }
        val parallelConductance = parallelValues.sumOf { 1.0 / it }
        val parallelResistance = 1.0 / parallelConductance
        val total = seriesValue + parallelResistance
        val current = v / total
        val seriesDrop = current * seriesValue
        val parallelVoltage = v - seriesDrop
        val branchCurrents = parallelValues.map { parallelVoltage / it }
        val totalPower = v * current

        val steps = mutableListOf<ExplanationStep>()
        steps.add(
            ExplanationStep(
                title = "The parallel part is reduced first",
                formula = "1 ÷ R = 1 ÷ R₁ + 1 ÷ R₂",
                substitution = "= " + parallel.joinToString(" + ") {
                    "1 ÷ ${it.second.toPlainString()}"
                },
                result = "R parallel = ${CircuitExplainer.number(parallelResistance)} Ω",
                note = "The parts that are in parallel share the same voltage.",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The series part is then added to it",
                formula = "R = R series + R parallel",
                substitution = "= ${seriesResistance.toPlainString()} + " +
                    CircuitExplainer.number(parallelResistance),
                result = "R total = ${CircuitExplainer.number(total)} Ω",
                note = "The current through the series part is the current of the whole " +
                    "circuit, and it then splits between the branches.",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The current through the series part",
                formula = "I = V ÷ R",
                substitution = "= ${CircuitExplainer.number(v)} V ÷ ${CircuitExplainer.number(total)} Ω",
                result = "I = ${CircuitExplainer.number(current)} A",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The drop across the series part",
                formula = "V = I × R",
                substitution = "= ${CircuitExplainer.number(current)} A × " +
                    "${seriesResistance.toPlainString()} Ω",
                result = "V = ${CircuitExplainer.number(seriesDrop)} V",
                note = "What is left of the source voltage is across the parallel part.",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The voltage across the parallel part",
                formula = "V = V source − V series",
                substitution = "= ${CircuitExplainer.number(v)} V − ${CircuitExplainer.number(seriesDrop)} V",
                result = "V = ${CircuitExplainer.number(parallelVoltage)} V",
            ),
        )
        for ((index, entry) in parallel.withIndex()) {
            steps.add(
                ExplanationStep(
                    title = "The current through ${entry.first}",
                    formula = "I = V ÷ R",
                    substitution = "= ${CircuitExplainer.number(parallelVoltage)} V ÷ " +
                        "${entry.second.toPlainString()} Ω",
                    result = "I = ${CircuitExplainer.number(branchCurrents[index])} A",
                ),
            )
        }

        return ok(
            CalculatorResult(
                title = "A series part and a parallel part",
                headline = "R = ${CircuitExplainer.number(total)} Ω",
                rows = buildList {
                    add("Parallel part" to "${CircuitExplainer.number(parallelResistance)} Ω")
                    add("Total resistance" to "${CircuitExplainer.number(total)} Ω")
                    add("Total current" to "${CircuitExplainer.number(current)} A")
                    add("Drop across $seriesLabel" to "${CircuitExplainer.number(seriesDrop)} V")
                    add("Voltage across the parallel part" to "${CircuitExplainer.number(parallelVoltage)} V")
                    parallel.forEachIndexed { index, entry ->
                        add("Current in ${entry.first}" to "${CircuitExplainer.number(branchCurrents[index])} A")
                    }
                    add("Total power" to "${CircuitExplainer.number(totalPower)} W")
                },
                variables = listOf(
                    FormulaVariable("V", "the voltage of the source", CircuitExplainer.number(v), "V"),
                    FormulaVariable("I", "the total current", CircuitExplainer.number(current), "A"),
                    FormulaVariable("R", "the total resistance", CircuitExplainer.number(total), "Ω"),
                ),
                steps = steps,
                why = "The current goes through $seriesLabel and then splits between " +
                    "the branches, so the series part is added to the reduced " +
                    "parallel part.",
                checks = listOf(
                    ExplanationStep(
                        title = "The branch currents add up to the current in the series part",
                        formula = "Σ I branch = I series",
                        substitution = "= " + branchCurrents.joinToString(" + ") {
                            CircuitExplainer.number(it)
                        } + " A and ${CircuitExplainer.number(current)} A",
                        result = "the difference is ${CircuitExplainer.number(current - branchCurrents.sum())} A",
                    ),
                    ExplanationStep(
                        title = "The drops add up to the source",
                        formula = "V series + V parallel = V source",
                        substitution = "= ${CircuitExplainer.number(seriesDrop)} V + " +
                            "${CircuitExplainer.number(parallelVoltage)} V",
                        result = "the difference is ${CircuitExplainer.number(v - seriesDrop - parallelVoltage)} V",
                    ),
                ),
                formula = "R = R series + R parallel,   1 ÷ R parallel = Σ 1 ÷ R branch,   I = V ÷ R",
            ),
        )
    }

    // ------------------------------------------------------ dividers

    /**
     * The output of a voltage divider: two resistors in series, the output
     * taken across the second of them.
     */
    fun voltageDivider(
        topResistance: BigDecimal,
        bottomResistance: BigDecimal,
        inputVoltage: BigDecimal,
    ): CalcResult<CalculatorResult> {
        if (topResistance.signum() <= 0 || bottomResistance.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Both resistances must be greater than zero.")
        }
        val r1 = topResistance.toDouble()
        val r2 = bottomResistance.toDouble()
        val vin = inputVoltage.toDouble()
        val total = r1 + r2
        val current = vin / total
        val vout = current * r2

        val steps = listOf(
            ExplanationStep(
                title = "The current through the divider",
                formula = "I = V ÷ (R₁ + R₂)",
                substitution = "= ${CircuitExplainer.number(vin)} V ÷ " +
                    "(${CircuitExplainer.number(r1)} + ${CircuitExplainer.number(r2)}) Ω",
                result = "I = ${CircuitExplainer.number(current)} A",
            ),
            ExplanationStep(
                title = "The output voltage, across the second resistor",
                formula = "V out = V in × R₂ ÷ (R₁ + R₂)",
                substitution = "= ${CircuitExplainer.number(vin)} V × ${CircuitExplainer.number(r2)} Ω ÷ " +
                    "(${CircuitExplainer.number(r1)} + ${CircuitExplainer.number(r2)}) Ω",
                result = "V out = ${CircuitExplainer.number(vout)} V",
            ),
        )
        return ok(
            CalculatorResult(
                title = "Voltage divider",
                headline = "V out = ${CircuitExplainer.number(vout)} V",
                rows = listOf(
                    "Current" to "${CircuitExplainer.number(current)} A",
                    "Drop across the first resistor" to "${CircuitExplainer.number(current * r1)} V",
                    "Total resistance" to "${CircuitExplainer.number(total)} Ω",
                ),
                variables = listOf(
                    FormulaVariable("V in", "the input voltage", CircuitExplainer.number(vin), "V"),
                    FormulaVariable("V out", "the output voltage", CircuitExplainer.number(vout), "V"),
                    FormulaVariable("R₁", "the resistor above the output", CircuitExplainer.number(r1), "Ω"),
                    FormulaVariable("R₂", "the resistor below the output", CircuitExplainer.number(r2), "Ω"),
                ),
                steps = steps,
                why = "The two resistors are in series, so the same current flows through " +
                    "both of them and the output is the part of the input voltage " +
                    "across the second one. The formula assumes nothing is drawn " +
                    "from the output; a load on the output changes the answer.",
                checks = listOf(
                    ExplanationStep(
                        title = "The two drops add up to the input",
                        formula = "V₁ + V out = V in",
                        substitution = "= ${CircuitExplainer.number(current * r1)} V + " +
                            "${CircuitExplainer.number(vout)} V",
                        result = "the difference is ${CircuitExplainer.number(vin - current * r1 - vout)} V",
                    ),
                ),
                formula = "V out = V in × R₂ ÷ (R₁ + R₂)",
            ),
        )
    }

    /**
     * The currents of a current divider: one current split between two
     * resistors in parallel.
     */
    fun currentDivider(
        firstResistance: BigDecimal,
        secondResistance: BigDecimal,
        totalCurrent: BigDecimal,
    ): CalcResult<CalculatorResult> {
        if (firstResistance.signum() <= 0 || secondResistance.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Both resistances must be greater than zero.")
        }
        val r1 = firstResistance.toDouble()
        val r2 = secondResistance.toDouble()
        val itotal = totalCurrent.toDouble()
        val equivalent = r1 * r2 / (r1 + r2)
        // The smaller resistance takes the larger share of the current.
        val i1 = itotal * r2 / (r1 + r2)
        val i2 = itotal * r1 / (r1 + r2)

        val steps = listOf(
            ExplanationStep(
                title = "The combined resistance of the two branches",
                formula = "R = (R₁ × R₂) ÷ (R₁ + R₂)",
                substitution = "= (${CircuitExplainer.number(r1)} × ${CircuitExplainer.number(r2)}) ÷ " +
                    "(${CircuitExplainer.number(r1)} + ${CircuitExplainer.number(r2)})",
                result = "R = ${CircuitExplainer.number(equivalent)} Ω",
            ),
            ExplanationStep(
                title = "The current through the first branch",
                formula = "I₁ = I total × R₂ ÷ (R₁ + R₂)",
                substitution = "= ${CircuitExplainer.number(itotal)} A × " +
                    "${CircuitExplainer.number(r2)} Ω ÷ " +
                    "(${CircuitExplainer.number(r1)} + ${CircuitExplainer.number(r2)}) Ω",
                result = "I₁ = ${CircuitExplainer.number(i1)} A",
                note = "The branch with the smaller resistance takes the larger share " +
                    "of the current.",
            ),
            ExplanationStep(
                title = "The current through the second branch",
                formula = "I₂ = I total × R₁ ÷ (R₁ + R₂)",
                result = "I₂ = ${CircuitExplainer.number(i2)} A",
            ),
        )
        return ok(
            CalculatorResult(
                title = "Current divider",
                headline = "I₁ = ${CircuitExplainer.number(i1)} A",
                rows = listOf(
                    "Current in the first branch" to "${CircuitExplainer.number(i1)} A",
                    "Current in the second branch" to "${CircuitExplainer.number(i2)} A",
                    "Combined resistance" to "${CircuitExplainer.number(equivalent)} Ω",
                ),
                variables = listOf(
                    FormulaVariable("I", "the total current", CircuitExplainer.number(itotal), "A"),
                    FormulaVariable("I₁", "the current in the first branch", CircuitExplainer.number(i1), "A"),
                    FormulaVariable("R₁", "the resistance of the first branch", CircuitExplainer.number(r1), "Ω"),
                    FormulaVariable("R₂", "the resistance of the second branch", CircuitExplainer.number(r2), "Ω"),
                ),
                steps = steps,
                why = "The two branches are in parallel, so the same voltage is across " +
                    "both of them, and the smaller resistance takes the larger share " +
                    "of the current.",
                checks = listOf(
                    ExplanationStep(
                        title = "The branch currents add up to the total",
                        formula = "I₁ + I₂ = I total",
                        substitution = "= ${CircuitExplainer.number(i1)} A + " +
                            "${CircuitExplainer.number(i2)} A",
                        result = "the difference is ${CircuitExplainer.number(itotal - i1 - i2)} A",
                    ),
                ),
                formula = "I₁ = I total × R₂ ÷ (R₁ + R₂)",
            ),
        )
    }
}
