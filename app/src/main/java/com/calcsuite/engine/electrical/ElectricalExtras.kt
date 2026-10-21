package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The other electrical calculators.
 *
 * They cover the questions that come up around a circuit rather than in it:
 * writing a value in a different prefix, reading a resistor from its colours,
 * what a capacitor does, how long a circuit takes to settle, and what happens
 * when the supply is alternating.
 */
object ElectricalExtras {

    // ------------------------------------------------- unit conversion

    /**
     * The same value written in another prefix.
     *
     * The value is held in its base unit, so the conversion is exact and the
     * answer can be written in any prefix the user likes.
     */
    fun convert(
        value: BigDecimal,
        unit: ElectricalUnits.Unit,
        fromPrefixId: String,
        toUnit: ElectricalUnits.Unit,
        toPrefixId: String,
    ): CalcResult<CalculatorResult> {
        if (value.signum() < 0) {
            return err(ErrorKind.InvalidInput, "The value cannot be negative.")
        }
        val from = ElectricalUnits.prefix(fromPrefixId)
        val to = ElectricalUnits.prefix(toPrefixId)
        val base = ElectricalUnits.toBase(value, from.id)
        // The same value, written with the other prefix: the number is
        // divided by the power of ten that the prefix stands for.
        val converted = base.scaleByPowerOfTen(-to.exponent)
        val plain = converted.stripTrailingZeros().toPlainString()
        val fromText = "${value.stripTrailingZeros().toPlainString()} ${from.symbol}${unit.symbol}"
        val toText = "$plain ${to.symbol}${toUnit.symbol}"

        val steps = listOf(
            ExplanationStep(
                title = "The value in its base unit",
                formula = "value in base unit = value × 10^${from.exponent}",
                substitution = "= ${value.stripTrailingZeros().toPlainString()} × 10^${from.exponent}",
                result = "= ${base.stripTrailingZeros().toPlainString()} ${unit.symbol}",
            ),
            ExplanationStep(
                title = "The same value in the new prefix",
                formula = "value in new prefix = value in base unit ÷ 10^${to.exponent}",
                substitution = "= ${base.stripTrailingZeros().toPlainString()} ÷ 10^${to.exponent}",
                result = "= $plain ${toUnit.symbol}",
            ),
        )

        return ok(
            CalculatorResult(
                title = "Unit conversion",
                headline = toText,
                rows = listOf(
                    "As given" to fromText,
                    "In the base unit" to "${base.stripTrailingZeros().toPlainString()} ${unit.symbol}",
                    "In the new prefix" to toText,
                ),
                variables = listOf(
                    FormulaVariable("value", "the number as it was given", value.stripTrailingZeros().toPlainString(), unit.symbol),
                    FormulaVariable("10ⁿ", "the power of ten the prefix stands for", from.exponent.toString(), ""),
                ),
                steps = steps,
                why = "Every prefix is a power of ten, so the value itself never " +
                    "changes - only the way it is written. The solver always keeps " +
                    "the value in its base unit, so this is only ever a change of " +
                    "writing, never of the number.",
                checks = listOf(
                    ExplanationStep(
                        title = "Going back gives the original value",
                        formula = "value × 10ⁿ ÷ 10ⁿ = value",
                        substitution = "= $plain × 10^${to.exponent} ÷ 10^${to.exponent}",
                        result = "= ${value.stripTrailingZeros().toPlainString()} ${unit.symbol}",
                    ),
                ),
                formula = "value in prefix = value in base unit ÷ 10ⁿ",
            ),
        )
    }

    // ----------------------------------------------------- colour code

    /**
     * One band of a resistor.
     *
     * @param colour the name of the colour.
     * @param digit the digit the colour stands for, or null when the colour
     *   is a multiplier or a tolerance.
     * @param multiplier the power of ten the colour stands for, when it is a
     *   multiplier band.
     * @param tolerance the tolerance the colour stands for, when it is a
     *   tolerance band.
     */
    data class Band(
        val colour: String,
        val digit: Int? = null,
        val multiplier: Int? = null,
        val tolerance: BigDecimal? = null,
    )

    /** The colours that give the digits of a resistor. */
    val digitBands: List<Band> = listOf(
        Band("Black", digit = 0),
        Band("Brown", digit = 1),
        Band("Red", digit = 2),
        Band("Orange", digit = 3),
        Band("Yellow", digit = 4),
        Band("Green", digit = 5),
        Band("Blue", digit = 6),
        Band("Violet", digit = 7),
        Band("Grey", digit = 8),
        Band("White", digit = 9),
    )

    /** The colours that give the multiplier, from the smallest to the largest. */
    val multiplierBands: List<Band> = listOf(
        Band("Silver", multiplier = -2),
        Band("Gold", multiplier = -1),
        Band("Black", multiplier = 0),
        Band("Brown", multiplier = 1),
        Band("Red", multiplier = 2),
        Band("Orange", multiplier = 3),
        Band("Yellow", multiplier = 4),
        Band("Green", multiplier = 5),
        Band("Blue", multiplier = 6),
        Band("Violet", multiplier = 7),
    )

    /** The colours that say how much the value may differ. */
    val toleranceBands: List<Band> = listOf(
        Band("Brown", tolerance = BigDecimal("1")),
        Band("Red", tolerance = BigDecimal("2")),
        Band("Green", tolerance = BigDecimal("0.5")),
        Band("Blue", tolerance = BigDecimal("0.25")),
        Band("Violet", tolerance = BigDecimal("0.1")),
        Band("Grey", tolerance = BigDecimal("0.05")),
        Band("Gold", tolerance = BigDecimal("5")),
        Band("Silver", tolerance = BigDecimal("10")),
    )

    /**
     * The resistance of a resistor with the given bands.
     *
     * The first two bands are the first two digits, the third is the power of
     * ten, and the fourth, if there is one, is the tolerance.
     */
    fun fromBands(bands: List<Band>): CalcResult<CalculatorResult> {
        if (bands.size < 3) {
            return err(ErrorKind.InvalidInput, "A resistor needs at least three bands.")
        }
        val first = bands[0].digit
        val second = bands[1].digit
        if (first == null || second == null) {
            return err(ErrorKind.InvalidInput, "The first two bands must be the digits.")
        }
        val multiplier = bands[2].multiplier
            ?: return err(ErrorKind.InvalidInput, "The third band is the multiplier.")
        val tolerance = bands.getOrNull(3)?.tolerance ?: BigDecimal("20")
        val value = BigDecimal(first * 10 + second).scaleByPowerOfTen(multiplier)
        val low = value.multiply(BigDecimal.ONE.subtract(tolerance.movePointLeft(2)))
        val high = value.multiply(BigDecimal.ONE.add(tolerance.movePointLeft(2)))

        val steps = listOf(
            ExplanationStep(
                title = "The first two bands give the digits",
                formula = "digits = first digit, second digit",
                substitution = "= ${bands[0].colour} (${first}), ${bands[1].colour} (${second})",
                result = "= ${first * 10 + second}",
            ),
            ExplanationStep(
                title = "The third band is the multiplier",
                formula = "R = digits × 10ⁿ",
                substitution = "= ${first * 10 + second} × 10^$multiplier",
                note = "The multiplier band is ${bands[2].colour}.",
                result = "= ${value.stripTrailingZeros().toPlainString()} Ω",
            ),
            ExplanationStep(
                title = "The tolerance band",
                formula = "± tolerance %",
                substitution = "= ± ${tolerance.stripTrailingZeros().toPlainString()} %",
                result = "= ${low.stripTrailingZeros().toPlainString()} Ω to " +
                    "${high.stripTrailingZeros().toPlainString()} Ω",
            ),
        )

        return ok(
            CalculatorResult(
                title = "Resistor colour code",
                headline = "${value.stripTrailingZeros().toPlainString()} Ω",
                rows = listOf(
                    "Bands" to bands.joinToString(" – ") { it.colour },
                    "Value" to "${value.stripTrailingZeros().toPlainString()} Ω",
                    "Tolerance" to "± ${tolerance.stripTrailingZeros().toPlainString()} %",
                    "Range" to "${low.stripTrailingZeros().toPlainString()} Ω to " +
                        "${high.stripTrailingZeros().toPlainString()} Ω",
                ),
                variables = listOf(
                    FormulaVariable("R", "the resistance", value.stripTrailingZeros().toPlainString(), "Ω"),
                    FormulaVariable("n", "the power of ten of the multiplier band", multiplier.toString(), ""),
                ),
                steps = steps,
                why = "The bands are read from left to right. The first two give the " +
                    "digits, the third gives the power of ten they are multiplied by, " +
                    "and the last, if there is one, says how much the real resistor " +
                    "may differ from the value on the band.",
                checks = listOf(
                    ExplanationStep(
                        title = "The value sits inside the tolerance",
                        formula = "value − tolerance ≤ R ≤ value + tolerance",
                        substitution = "= ${low.stripTrailingZeros().toPlainString()} Ω ≤ " +
                            "${value.stripTrailingZeros().toPlainString()} Ω ≤ " +
                            "${high.stripTrailingZeros().toPlainString()} Ω",
                        result = "the difference is ${(high.subtract(low)).stripTrailingZeros().toPlainString()} Ω",
                    ),
                ),
                formula = "R = (first digit × 10 + second digit) × 10ⁿ",
            ),
        )
    }

    /**
     * The bands that would be printed on a resistor of that value.
     */
    fun toBands(resistance: BigDecimal): CalcResult<CalculatorResult> {
        if (resistance.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "The resistance must be greater than zero.")
        }
        var value = resistance
        var multiplier = 0
        while (value >= BigDecimal("100")) {
            value = value.movePointLeft(1)
            multiplier++
        }
        while (value < BigDecimal("10")) {
            value = value.movePointRight(1)
            multiplier--
        }
        val rounded = value.setScale(0, java.math.RoundingMode.HALF_UP)
        if (rounded < BigDecimal.TEN) {
            return err(
                ErrorKind.InvalidInput,
                "That value cannot be written with the standard bands.",
            )
        }
        val first = (rounded.toInt() / 10)
        val second = (rounded.toInt() % 10)
        val multiplierBand = multiplierBands.firstOrNull { it.multiplier == multiplier }
            ?: return err(
                ErrorKind.InvalidInput,
                "That value needs a multiplier the standard bands do not have.",
            )
        val bands = listOf(
            digitBands[first],
            digitBands[second],
            multiplierBand,
        )
        val value2 = BigDecimal(first * 10 + second).scaleByPowerOfTen(multiplier)
        return ok(
            CalculatorResult(
                title = "Resistor colour code",
                headline = "${value2.stripTrailingZeros().toPlainString()} Ω",
                rows = listOf(
                    "Bands" to bands.joinToString(" – ") { it.colour },
                    "Value" to "${value2.stripTrailingZeros().toPlainString()} Ω",
                    "Closest standard value" to "${resistance.stripTrailingZeros().toPlainString()} Ω",
                ),
                variables = listOf(
                    FormulaVariable("R", "the resistance", value2.stripTrailingZeros().toPlainString(), "Ω"),
                ),
                steps = listOf(
                    ExplanationStep(
                        title = "The digits are written out",
                        formula = "digits = first two digits of R",
                        substitution = "= ${first} and ${second}",
                        result = "= ${bands[0].colour} – ${bands[1].colour}",
                    ),
                    ExplanationStep(
                        title = "The multiplier is written out",
                        formula = "R = digits × 10ⁿ",
                        substitution = "= ${first * 10 + second} × 10^$multiplier",
                        note = "The multiplier band is ${multiplierBand.colour}.",
                        result = "= ${value2.stripTrailingZeros().toPlainString()} Ω",
                    ),
                ),
                why = "A resistor is marked with the two digits of its value and the " +
                    "power of ten they are multiplied by, so that the value can be " +
                    "read without having to write a number with many zeros.",
                checks = listOf(
                    ExplanationStep(
                        title = "Reading the bands back gives the same value",
                        formula = "(digit₁ × 10 + digit₂) × 10ⁿ",
                        substitution = "= (${first} × 10 + ${second}) × 10^$multiplier",
                        result = "= ${value2.stripTrailingZeros().toPlainString()} Ω",
                    ),
                ),
                formula = "R = (first digit × 10 + second digit) × 10ⁿ",
            ),
        )
    }

    // ------------------------------------------------------- capacitor

    /**
     * What a capacitor does: the charge it holds, the energy in it, and how
     * long it takes to charge through a resistance.
     */
    fun capacitor(
        capacitance: BigDecimal,
        voltage: BigDecimal,
        resistance: BigDecimal?,
    ): CalcResult<CalculatorResult> {
        if (capacitance.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "The capacitance must be greater than zero.")
        }
        if (voltage.signum() < 0) {
            return err(ErrorKind.InvalidInput, "The voltage cannot be negative.")
        }
        val c = capacitance.toDouble()
        val v = voltage.toDouble()
        val charge = c * v
        val energy = 0.5 * c * v * v

        val steps = mutableListOf(
            ExplanationStep(
                title = "The charge the capacitor holds",
                formula = "Q = C × V",
                substitution = "= ${CircuitExplainer.number(c)} F × ${CircuitExplainer.number(v)} V",
                calculation = "= ${CircuitExplainer.number(c)} × ${CircuitExplainer.number(v)}",
                result = "Q = ${CircuitExplainer.number(charge)} C",
            ),
            ExplanationStep(
                title = "The energy stored in it",
                formula = "E = ½ × C × V²",
                substitution = "= ½ × ${CircuitExplainer.number(c)} F × " +
                    "(${CircuitExplainer.number(v)} V)²",
                calculation = "= 0.5 × ${CircuitExplainer.number(c)} × " +
                    CircuitExplainer.number(v * v),
                result = "E = ${CircuitExplainer.number(energy)} J",
                note = "The energy is in the electric field between the plates. " +
                    "It is released when the capacitor is discharged.",
            ),
        )
        val rows = mutableListOf(
            "Charge" to "${CircuitExplainer.number(charge)} C",
            "Energy" to "${CircuitExplainer.number(energy)} J",
        )
        val checks = mutableListOf<ExplanationStep>()
        var timeConstant: Double? = null

        if (resistance != null && resistance.signum() > 0) {
            val r = resistance.toDouble()
            timeConstant = r * c
            steps.add(
                ExplanationStep(
                    title = "The time it takes to charge",
                    formula = "τ = R × C",
                    substitution = "= ${CircuitExplainer.number(r)} Ω × ${CircuitExplainer.number(c)} F",
                    calculation = "= ${CircuitExplainer.number(r)} × ${CircuitExplainer.number(c)}",
                    result = "τ = ${CircuitExplainer.number(timeConstant)} s",
                    note = "After one time constant the capacitor has reached 63 % of " +
                        "the supply voltage, and after five it is there to within 1 %.",
                ),
            )
            rows.add("Time constant" to "${CircuitExplainer.number(timeConstant)} s")
            rows.add("One time constant" to "63 % of ${CircuitExplainer.number(v)} V")
            rows.add("Five time constants" to "99 % of ${CircuitExplainer.number(v)} V")
            checks.add(
                ExplanationStep(
                    title = "The voltage after one time constant",
                    formula = "V(τ) = V × (1 − e⁻¹)",
                    substitution = "= ${CircuitExplainer.number(v)} × (1 − 0.3679)",
                    result = "= ${CircuitExplainer.number(v * 0.6321)} V",
                ),
            )
        }

        return ok(
            CalculatorResult(
                title = "A capacitor",
                headline = "Q = ${CircuitExplainer.number(charge)} C",
                rows = rows,
                variables = listOf(
                    FormulaVariable("C", "the capacitance", CircuitExplainer.number(c), "F"),
                    FormulaVariable("V", "the voltage across it", CircuitExplainer.number(v), "V"),
                    FormulaVariable("Q", "the charge it holds", CircuitExplainer.number(charge), "C"),
                ),
                steps = steps,
                why = "A capacitor stores charge on two plates with an insulator " +
                    "between them. The charge it takes is the capacitance times the " +
                    "voltage, and the energy in the field grows with the square of " +
                    "the voltage, which is why doubling the voltage stores four " +
                    "times the energy.",
                checks = checks,
                formula = "Q = C × V,   E = ½ × C × V²,   τ = R × C",
            ),
        )
    }

    // ------------------------------------------------- time constants

    /**
     * The time constant of an RC or an RL circuit, and what happens over
     * the first few of them.
     */
    fun timeConstant(
        kind: String,
        resistance: BigDecimal,
        reactancePart: BigDecimal,
    ): CalcResult<CalculatorResult> {
        if (resistance.signum() <= 0 || reactancePart.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Both values must be greater than zero.")
        }
        val r = resistance.toDouble()
        val second = reactancePart.toDouble()
        val isCapacitive = kind == "capacitor"
        val tau = if (isCapacitive) r * second else second / r
        val symbol = if (isCapacitive) "C" else "L"
        val unit = if (isCapacitive) "F" else "H"
        val formula = if (isCapacitive) "τ = R × C" else "τ = L ÷ R"

        val steps = listOf(
            ExplanationStep(
                title = "The time constant",
                formula = formula,
                substitution = if (isCapacitive) {
                    "= ${CircuitExplainer.number(r)} Ω × ${CircuitExplainer.number(second)} F"
                } else {
                    "= ${CircuitExplainer.number(second)} H ÷ ${CircuitExplainer.number(r)} Ω"
                },
                calculation = if (isCapacitive) {
                    "= ${CircuitExplainer.number(r)} × ${CircuitExplainer.number(second)}"
                } else {
                    "= ${CircuitExplainer.number(second)} ÷ ${CircuitExplainer.number(r)}"
                },
                result = "τ = ${CircuitExplainer.number(tau)} s",
            ),
            ExplanationStep(
                title = "After one time constant",
                formula = "V(τ) = V × (1 − e⁻¹)   (charging)",
                substitution = "= ${CircuitExplainer.number(tau)} s",
                result = "= 63 % of the final voltage",
            ),
            ExplanationStep(
                title = "After five time constants",
                formula = "V(5τ) = V × (1 − e⁻⁵)",
                result = "= 99 % of the final voltage",
            ),
        )

        return ok(
            CalculatorResult(
                title = if (isCapacitive) "An RC circuit" else "An RL circuit",
                headline = "τ = ${CircuitExplainer.number(tau)} s",
                rows = listOf(
                    "Time constant" to "${CircuitExplainer.number(tau)} s",
                    "After 1 τ" to "63 % of the final value",
                    "After 5 τ" to "99 % of the final value",
                ),
                variables = listOf(
                    FormulaVariable("τ", "the time constant", CircuitExplainer.number(tau), "s"),
                    FormulaVariable("R", "the resistance", CircuitExplainer.number(r), "Ω"),
                    FormulaVariable(symbol, if (isCapacitive) "the capacitance" else "the inductance",
                        CircuitExplainer.number(second), unit),
                ),
                steps = steps,
                why = if (isCapacitive) {
                    "The time constant says how quickly a capacitor fills through a " +
                        "resistance. The bigger the resistance or the capacitance, the " +
                        "longer it takes."
                } else {
                    "The time constant says how quickly the current in an inductor " +
                        "builds up. The bigger the inductance, the longer it takes; " +
                        "the bigger the resistance, the quicker it settles."
                },
                checks = listOf(
                    ExplanationStep(
                        title = "The curve is 63 % there after one time constant",
                        formula = "1 − e⁻¹",
                        calculation = "= 1 − 0.3679",
                        result = "= 0.6321",
                    ),
                ),
                formula = formula,
            ),
        )
    }

    // -------------------------------------------------------------- AC

    /**
     * An alternating circuit: a resistance, and optionally an inductance and
     * a capacitance in series with it.
     */
    fun ac(
        frequency: BigDecimal,
        rmsVoltage: BigDecimal,
        resistance: BigDecimal,
        inductance: BigDecimal?,
        capacitance: BigDecimal?,
    ): CalcResult<CalculatorResult> {
        if (frequency.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "The frequency must be greater than zero.")
        }
        if (rmsVoltage.signum() < 0) {
            return err(ErrorKind.InvalidInput, "The voltage cannot be negative.")
        }
        if (resistance.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "The resistance must be greater than zero.")
        }
        val f = frequency.toDouble()
        val v = rmsVoltage.toDouble()
        val r = resistance.toDouble()
        val l = inductance?.toDouble()
        val c = capacitance?.toDouble()
        val omega = 2 * PI * f
        val reactanceInductive = if (l != null) omega * l else 0.0
        val reactanceCapacitive = if (c != null && c > 0) 1.0 / (omega * c) else 0.0
        val reactance = reactanceInductive - reactanceCapacitive
        val impedance = sqrt(r * r + reactance * reactance)
        val current = if (impedance == 0.0) 0.0 else v / impedance
        val phase = if (r == 0.0) 0.0 else atan(reactance / r) * 180 / PI
        val powerFactor = cos(phase * PI / 180)
        val realPower = v * current * powerFactor
        val reactivePower = v * current * sin(phase * PI / 180)
        val peakVoltage = v * sqrt(2.0)

        val steps = mutableListOf(
            ExplanationStep(
                title = "The angular frequency",
                formula = "ω = 2 × π × f",
                substitution = "= 2 × π × ${CircuitExplainer.number(f)} Hz",
                calculation = "= 6.2832 × ${CircuitExplainer.number(f)}",
                result = "ω = ${CircuitExplainer.number(omega)} rad/s",
            ),
        )
        val rows = mutableListOf(
            "Angular frequency" to "${CircuitExplainer.number(omega)} rad/s",
        )
        if (l != null) {
            steps.add(
                ExplanationStep(
                    title = "The inductive reactance",
                    formula = "X L = ω × L",
                    substitution = "= ${CircuitExplainer.number(omega)} rad/s × " +
                        "${CircuitExplainer.number(l)} H",
                    result = "X L = ${CircuitExplainer.number(reactanceInductive)} Ω",
                    note = "An inductor opposes a change of current, so it behaves " +
                        "like a resistance of this size.",
                ),
            )
            rows.add("Inductive reactance" to "${CircuitExplainer.number(reactanceInductive)} Ω")
        }
        if (c != null && c > 0) {
            steps.add(
                ExplanationStep(
                    title = "The capacitive reactance",
                    formula = "X C = 1 ÷ (ω × C)",
                    substitution = "= 1 ÷ (${CircuitExplainer.number(omega)} rad/s × " +
                        "${CircuitExplainer.number(c)} F)",
                    result = "X C = ${CircuitExplainer.number(reactanceCapacitive)} Ω",
                    note = "A capacitor opposes a change of voltage, so it behaves " +
                        "like a resistance of this size.",
                ),
            )
            rows.add("Capacitive reactance" to "${CircuitExplainer.number(reactanceCapacitive)} Ω")
        }
        steps.add(
            ExplanationStep(
                title = "The impedance of the circuit",
                formula = "Z = √(R² + (X L − X C)²)",
                substitution = "= √(${CircuitExplainer.number(r)}² + " +
                    "(${CircuitExplainer.number(reactanceInductive)} − " +
                    "${CircuitExplainer.number(reactanceCapacitive)})²)",
                calculation = "= √(${CircuitExplainer.number(r * r + reactance * reactance)})",
                result = "Z = ${CircuitExplainer.number(impedance)} Ω",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The current the circuit takes",
                formula = "I = V ÷ Z",
                substitution = "= ${CircuitExplainer.number(v)} V ÷ " +
                    "${CircuitExplainer.number(impedance)} Ω",
                result = "I = ${CircuitExplainer.number(current)} A",
            ),
        )
        steps.add(
            ExplanationStep(
                title = "The phase of the current",
                formula = "φ = arctan((X L − X C) ÷ R)",
                substitution = "= arctan((${CircuitExplainer.number(reactanceInductive)} − " +
                    "${CircuitExplainer.number(reactanceCapacitive)}) ÷ ${CircuitExplainer.number(r)})",
                result = "φ = ${CircuitExplainer.number(phase)}°",
                note = if (phase >= 0) {
                    "The current lags the voltage, because the circuit is more " +
                        "inductive than capacitive."
                } else {
                    "The current leads the voltage, because the circuit is more " +
                        "capacitive than inductive."
                },
            ),
        )

        rows.add("Impedance" to "${CircuitExplainer.number(impedance)} Ω")
        rows.add("Current" to "${CircuitExplainer.number(current)} A")
        rows.add("Phase" to "${CircuitExplainer.number(phase)}°")
        rows.add("Power factor" to CircuitExplainer.number(powerFactor))
        rows.add("Real power" to "${CircuitExplainer.number(realPower)} W")
        rows.add("Reactive power" to "${CircuitExplainer.number(reactivePower)} VAR")
        rows.add("Peak voltage" to "${CircuitExplainer.number(peakVoltage)} V")

        return ok(
            CalculatorResult(
                title = "An alternating circuit",
                headline = "I = ${CircuitExplainer.number(current)} A",
                rows = rows,
                variables = listOf(
                    FormulaVariable("f", "the frequency", CircuitExplainer.number(f), "Hz"),
                    FormulaVariable("V", "the RMS voltage", CircuitExplainer.number(v), "V"),
                    FormulaVariable("R", "the resistance", CircuitExplainer.number(r), "Ω"),
                    FormulaVariable("Z", "the impedance", CircuitExplainer.number(impedance), "Ω"),
                    FormulaVariable("φ", "the phase between the voltage and the current",
                        CircuitExplainer.number(phase), "°"),
                ),
                steps = steps,
                why = "An alternating voltage is given as its RMS value, which is " +
                    "the value a plain meter reads. The resistance and the " +
                    "reactances do not add up directly, but their squares do, and " +
                    "that gives the impedance, which behaves like a resistance for " +
                    "the size of the current.",
                checks = listOf(
                    ExplanationStep(
                        title = "The impedance follows from the resistance and the reactance",
                        formula = "Z² = R² + X²",
                        substitution = "= ${CircuitExplainer.number(r * r + reactance * reactance)} Ω² " +
                            "and ${CircuitExplainer.number(r * r)} + ${CircuitExplainer.number(reactance * reactance)}",
                        result = "the difference is ${CircuitExplainer.number(impedance * impedance - (r * r + reactance * reactance))} Ω²",
                    ),
                    ExplanationStep(
                        title = "The real power cannot be more than the total",
                        formula = "P ≤ V × I",
                        substitution = "= ${CircuitExplainer.number(realPower)} W and " +
                            "${CircuitExplainer.number(v * current)} VA",
                        result = "the difference is ${CircuitExplainer.number(v * current - realPower)} VA",
                    ),
                ),
                formula = "Z = √(R² + (X L − X C)²),   I = V ÷ Z,   φ = arctan((X L − X C) ÷ R)",
            ),
        )
    }
}
