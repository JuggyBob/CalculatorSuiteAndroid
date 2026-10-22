package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.algebra.EquationEngine
import com.calcsuite.engine.algebra.EquationKind
import com.calcsuite.engine.algebra.QuadraticRoute
import com.calcsuite.engine.algebra.SystemMethod
import com.calcsuite.engine.algebra.SystemSolver
import com.calcsuite.ui.components.ExplanationCards
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal
import com.calcsuite.ui.tools.prefill

private enum class EquationMode { Linear, Quadratic, System }

/**
 * The definition of what the tool does, for the mode that is in use.
 */
private fun equationDefinition(mode: EquationMode): String = when (mode) {
    EquationMode.Linear ->
        "Solving an equation means finding the value of the unknown that makes " +
            "the two sides equal. A linear equation has the unknown to the first " +
            "power only, so one value solves it - or none does."
    EquationMode.Quadratic ->
        "A quadratic equation has the unknown to the second power. It is brought " +
            "to the standard form ax² + bx + c = 0, and the answer is found from " +
            "its discriminant."
    EquationMode.System ->
        "A system of two equations in two unknowns is solved together. The two " +
            "equations are lines in the plane, and the answer is the point where " +
            "they meet."
}

/** The symbols of the formula, and what they stand for. */
private fun equationSymbols(mode: EquationMode): List<Pair<String, String>> = when (mode) {
    EquationMode.Linear -> listOf(
        "a" to "the coefficient of the unknown",
        "b" to "the constant on the left",
        "c" to "the constant on the right",
    )
    EquationMode.Quadratic -> listOf(
        "a" to "the coefficient of the square of the unknown",
        "b" to "the coefficient of the unknown",
        "c" to "the constant",
        "\u0394" to "the discriminant, b² \u2212 4ac, which says how many roots there are",
    )
    EquationMode.System -> listOf(
        "a\u2081, b\u2081, c\u2081" to "the coefficients of the first equation",
        "a\u2082, b\u2082, c\u2082" to "the coefficients of the second equation",
        "D" to "the determinant a\u2081b\u2082 \u2212 a\u2082b\u2081, which must not be zero",
    )
}

/** The conditions the method needs. */
private fun equationConditions(mode: EquationMode): List<String> = when (mode) {
    EquationMode.Linear -> listOf(
        "The coefficient of the unknown must not be zero. If it is, the equation " +
            "is not linear and the tool says so rather than dividing by zero.",
    )
    EquationMode.Quadratic -> listOf(
        "The coefficient of the square must not be zero; otherwise the equation " +
            "is linear and is solved as one.",
        "The discriminant decides how many roots there are: two, one repeated " +
            "root, or none.",
    )
    EquationMode.System -> listOf(
        "The determinant must not be zero. If it is, the two lines are parallel " +
            "or the same, and the tool reports that rather than a point.",
    )
}

/** How the engine works the answer out. */
private fun equationMethod(mode: EquationMode): String = when (mode) {
    EquationMode.Linear ->
        "Both sides are brought to the standard form ax + b = c, and the unknown " +
            "is then isolated with exact fractions."
    EquationMode.Quadratic ->
        "The equation is brought to the standard form ax² + bx + c = 0, the " +
            "discriminant is worked out, and the roots are found from it. When " +
            "the discriminant is negative the roots are given as complex numbers."
    EquationMode.System ->
        "The two equations are solved together by the chosen method, with exact " +
            "fractions throughout. Each method shows the working that it does."
}

/** What the answer means. */
private fun equationInterpretation(
    mode: EquationMode,
    outcome: EquationOutcome,
): String = when (mode) {
    EquationMode.Linear ->
        "The value is the one that makes the two sides equal, and the check " +
            "puts it back into the equation to show that it does."
    EquationMode.Quadratic ->
        "The roots are the points where the parabola crosses the axis. A " +
            "negative discriminant means it never does."
    EquationMode.System ->
        "The point is where the two lines meet, and the check puts it back " +
            "into both equations."
}

/** What the tool does not reach. */
private fun equationLimitations(mode: EquationMode): String = when (mode) {
    EquationMode.Linear ->
        "One equation in one unknown, and the unknown to the first power. An " +
            "equation with the unknown in a product, or on both sides, is not read."
    EquationMode.Quadratic ->
        "One equation in one unknown, and the unknown to the second power. " +
            "Equations of a higher degree, and systems of them, are not read."
    EquationMode.System ->
        "Two equations in two unknowns, both linear. Three equations, or a " +
            "quadratic system, are not read."
}

/**
 * A number as the solver reads it: no thousands separator and no exponent.
 */
private fun plainNumber(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

/** The formula of the method that solved the system. */
private fun systemFormula(method: SystemMethod): String = when (method) {
    SystemMethod.Automatic -> "A method is chosen for the equations that were entered"
    SystemMethod.Cramer ->
        "D = a₁b₂ − a₂b₁ ;  x = (c₁b₂ − c₂b₁) ÷ D ;  y = (a₁c₂ − a₂c₁) ÷ D"
    SystemMethod.Substitution ->
        "One equation is rearranged and put into the other, leaving one equation in one unknown"
    SystemMethod.Elimination ->
        "The equations are combined so that one variable cancels out"
    SystemMethod.Comparison ->
        "Both equations are written with the same variable on its own, and equated"
}

private data class EquationOutcome(
    val label: String,
    val value: String? = null,
    val copyValue: String? = null,
    val caption: String? = null,
    val error: String? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val steps: List<String> = emptyList(),
    /** The engine's own check of the answer. */
    val verification: String? = null,
    val notes: List<String> = emptyList(),
    val formula: String,
    val explanation: String,
)

@Composable
fun EquationsToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    // The tool the problem came from decides the mode, so that a
    // problem handed over opens in the right form.
    var modeName by rememberSaveable {
        mutableStateOf(
            when (args.tool.id) {
                "algebra.quadratic" -> EquationMode.Quadratic.name
                "algebra.system" -> EquationMode.System.name
                else -> args.prefill("mode") ?: EquationMode.Linear.name
            },
        )
    }
    val mode = EquationMode.valueOf(modeName)
    var equation by rememberSaveable {
        mutableStateOf(args.prefill("equation") ?: "2x + 4 = 10")
    }
    var quadraticEquation by rememberSaveable {
        mutableStateOf(args.prefill("quadraticEquation", "equation") ?: "x^2 - 3x + 2 = 0")
    }
    var variable by rememberSaveable { mutableStateOf(args.prefill("variable") ?: "x") }
    var a1 by rememberSaveable { mutableStateOf(args.prefill("a1") ?: "2") }
    var b1 by rememberSaveable { mutableStateOf(args.prefill("b1") ?: "3") }
    var c1 by rememberSaveable { mutableStateOf(args.prefill("c1") ?: "12") }
    var a2 by rememberSaveable { mutableStateOf(args.prefill("a2") ?: "1") }
    var b2 by rememberSaveable { mutableStateOf(args.prefill("b2") ?: "-1") }
    var c2 by rememberSaveable { mutableStateOf(args.prefill("c2") ?: "1") }
    // The way the system is solved. The choice is kept with the rest of the
    // problem, so that a saved calculation is solved the same way again.
    var systemMethod by rememberSaveable {
        mutableStateOf(args.prefill("systemMethod") ?: SystemMethod.Automatic.name)
    }

    fun num(v: BigDecimal) = fmt.num(v, precision = maxOf(fmt.precision, 10))

    val variableName = variable.trim().ifBlank { "x" }

    val outcome: EquationOutcome = remember(
        fmt, mode, equation, quadraticEquation, variable, a1, b1, c1, a2, b2, c2,
    ) {
        when (mode) {
            EquationMode.Linear -> {
                val solved = EquationEngine.solveLinearEquation(equation, variableName)
                when (solved) {
                    is CalcResult.Err -> EquationOutcome(
                        "Linear equation",
                        error = solved.error.message,
                        formula = "a·x + b = 0  →  x = −b ÷ a",
                        explanation = "Move every term to the left, then divide by the coefficient of the variable.",
                    )
                    is CalcResult.Ok -> {
                        val result = solved.value
                        when (result.kind) {
                            EquationKind.Identity -> EquationOutcome(
                                "Linear equation",
                                value = "Every value of $variableName",
                                caption = "0 = 0 — the equation is an identity",
                                rows = listOf("Standard form" to result.standardForm),
                                steps = result.steps,
                                verification = result.verification,
                                formula = "a·x + b = 0  →  x = −b ÷ a",
                                explanation = "When the coefficient and the constant both vanish the equation is true for every value.",
                            )
                            EquationKind.Contradiction -> EquationOutcome(
                                "Linear equation",
                                value = "No solution",
                                caption = "The variable terms cancel, leaving a false statement",
                                rows = listOf("Standard form" to result.standardForm),
                                steps = result.steps,
                                verification = result.verification,
                                formula = "a·x + b = 0  →  x = −b ÷ a",
                                explanation = "A contradiction has no solution: the two sides are parallel lines that never meet.",
                            )
                            else -> {
                                val root = result.root
                                EquationOutcome(
                                    label = "Solution",
                                    value = if (root != null) "$variableName = ${num(root)}" else "No solution",
                                    copyValue = root?.let { Num.formatForCopy(it) },
                                    rows = buildList {
                                        add("Standard form" to result.standardForm)
                                        add("Coefficient of $variableName" to num(result.coefficient))
                                        add("Constant term" to num(result.constant))
                                    },
                                    steps = result.steps,
                                    verification = result.verification,
                                    formula = "a·x + b = 0  →  x = −b ÷ a",
                                    explanation = "The solution is unique when the coefficient of the variable is not zero.",
                                )
                            }
                        }
                    }
                }
            }

            EquationMode.Quadratic -> {
                val solved = EquationEngine.solveQuadraticEquation(quadraticEquation, variableName)
                when (solved) {
                    is CalcResult.Err -> EquationOutcome(
                        "Quadratic equation",
                        error = solved.error.message,
                        formula = "x = (−b ± √(b² − 4ac)) ÷ 2a",
                        explanation = "The discriminant b² − 4ac decides how many real roots there are.",
                    )
                    is CalcResult.Ok -> {
                        val result = solved.value
                        val value = when {
                            result.route == QuadraticRoute.Linear && result.kind == EquationKind.Identity ->
                                "Every value of $variableName"
                            result.route == QuadraticRoute.Linear && result.kind == EquationKind.Contradiction ->
                                "No solution"
                            result.realRoots.size == 2 ->
                                "$variableName₁ = ${num(result.realRoots[0])}, $variableName₂ = ${num(result.realRoots[1])}"
                            result.realRoots.size == 1 ->
                                "$variableName = ${num(result.realRoots[0])}"
                            result.complexRoots.size == 2 ->
                                "No real roots; $variableName = ${result.complexRoots[0].text} or ${result.complexRoots[1].text}"
                            else -> "No real solution"
                        }
                        EquationOutcome(
                            label = "Roots",
                            value = value,
                            copyValue = result.realRoots.joinToString(", ") { Num.formatForCopy(it) }
                                .ifBlank { result.complexRoots.joinToString(", ") { it.text } },
                            caption = when {
                                result.route == QuadraticRoute.Linear ->
                                    "The x² coefficient is zero, so the equation is linear"
                                result.discriminant != null && result.discriminant.signum() > 0 && result.realRoots.size == 2 ->
                                    "Δ > 0: two distinct real roots"
                                result.discriminant != null && result.discriminant.signum() == 0 ->
                                    "Δ = 0: one double root"
                                else -> "Δ < 0: no real roots, two complex conjugate roots"
                            },
                            rows = buildList {
                                add("Standard form" to result.standardForm)
                                add("a, b, c" to "${num(result.a)}, ${num(result.b)}, ${num(result.c)}")
                                result.discriminant?.let { add("Discriminant Δ = b² − 4ac" to num(it)) }
                                result.realRoots.forEachIndexed { index, root ->
                                    add("Real root ${index + 1}" to num(root))
                                }
                                result.complexRoots.forEachIndexed { index, root ->
                                    add("Complex root ${index + 1}" to root.text)
                                }
                            },
                            steps = result.steps,
                            verification = result.verification,
                            notes = result.notes,
                            formula = "x = (−b ± √(b² − 4ac)) ÷ 2a",
                            explanation = "Δ > 0 gives two real roots, Δ = 0 a double root, Δ < 0 two complex conjugates a ± bi.",
                        )
                    }
                }
            }

            EquationMode.System -> {
                val values = listOf(a1, b1, c1, a2, b2, c2).map { Num.parseOrNull(it) }
                val invalidIndex = values.indexOfFirst { it == null }
                if (invalidIndex >= 0) {
                    val names = listOf("a₁", "b₁", "c₁", "a₂", "b₂", "c₂")
                    EquationOutcome(
                        "2 × 2 system",
                        error = "Enter a valid number for ${names[invalidIndex]}",
                        formula = "D = a₁b₂ − a₂b₁",
                        explanation = "Each equation is written as a·x + b·y = c.",
                    )
                } else {
                    // The equations are written out as they were entered, so
                    // that the solver reads the same system the screen shows.
                    val first = "${plainNumber(values[0]!!)}x + ${plainNumber(values[1]!!)}y = " +
                        plainNumber(values[2]!!)
                    val second = "${plainNumber(values[3]!!)}x + ${plainNumber(values[4]!!)}y = " +
                        plainNumber(values[5]!!)
                    val solved = SystemSolver.solve(first, second, SystemMethod.valueOf(systemMethod))
                    when (solved) {
                        is CalcResult.Err -> EquationOutcome(
                            "2 × 2 system",
                            error = solved.error.message,
                            formula = systemFormula(SystemMethod.valueOf(systemMethod)),
                            explanation = "Each equation is written as a·x + b·y = c.",
                        )
                        is CalcResult.Ok -> {
                            val result = solved.value
                            EquationOutcome(
                                label = "2 × 2 system",
                                value = if (result.solutions.isEmpty()) {
                                    result.message
                                } else {
                                    result.solutions.joinToString("; ") { (x, y) ->
                                        "x = ${num(x)}, y = ${num(y)}"
                                    }
                                },
                                copyValue = if (result.solutions.isEmpty()) {
                                    result.message
                                } else {
                                    result.solutions.joinToString("; ") { (x, y) ->
                                        "x = ${Num.formatForCopy(x)}, y = ${Num.formatForCopy(y)}"
                                    }
                                },
                                caption = result.message,
                                rows = buildList {
                                    add("Method" to result.method.label)
                                    result.solutions.forEachIndexed { index, (x, y) ->
                                        add("Solution ${index + 1}" to "x = ${num(x)}, y = ${num(y)}")
                                    }
                                },
                                steps = result.steps,
                                verification = result.verification.joinToString("\n"),
                                notes = result.notes + (
                                    "The methods that apply to this system: " +
                                        result.available.joinToString(", ") { it.label }
                                    ),
                                formula = systemFormula(result.method),
                                explanation = result.method.description,
                            )
                        }
                    }
                }
            }
        }
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        actions = {
            TextButton(onClick = {
                equation = "2x + 4 = 10"
                quadraticEquation = "x^2 - 3x + 2 = 0"
                variable = "x"
                a1 = "2"; b1 = "3"; c1 = "12"; a2 = "1"; b2 = "-1"; c2 = "1"
                systemMethod = SystemMethod.Automatic.name
            }) { Text("Reset") }
        },
    ) {
        SegmentedChoice(
            options = listOf(
                EquationMode.Linear.name to "Linear",
                EquationMode.Quadratic.name to "Quadratic",
                EquationMode.System.name to "System 2×2",
            ),
            selected = modeName,
            onSelect = { modeName = it },
            label = "Mode",
        )

        when (mode) {
            EquationMode.Linear -> {
                PlainField(
                    label = "Equation",
                    value = equation,
                    onValueChange = { equation = it },
                    placeholder = "2x + 4 = 10",
                    monospace = true,
                )
                PlainField(
                    label = "Variable",
                    value = variable,
                    onValueChange = { variable = it },
                    placeholder = "x",
                    monospace = true,
                )
            }
            EquationMode.Quadratic -> {
                PlainField(
                    label = "Equation",
                    value = quadraticEquation,
                    onValueChange = { quadraticEquation = it },
                    placeholder = "x^2 - 3x + 2 = 0",
                    monospace = true,
                )
                PlainField(
                    label = "Variable",
                    value = variable,
                    onValueChange = { variable = it },
                    placeholder = "x",
                    monospace = true,
                )
            }
            EquationMode.System -> {
                val fields = listOf(
                    Triple(a1, { text: String -> a1 = text }, "a₁"),
                    Triple(b1, { text: String -> b1 = text }, "b₁"),
                    Triple(c1, { text: String -> c1 = text }, "c₁"),
                    Triple(a2, { text: String -> a2 = text }, "a₂"),
                    Triple(b2, { text: String -> b2 = text }, "b₂"),
                    Triple(c2, { text: String -> c2 = text }, "c₂"),
                )
                Text(
                    "First equation: a₁x + b₁y = c₁   ·   Second equation: a₂x + b₂y = c₂",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                fields.forEach { (value, setter, name) ->
                    NumberField(
                        label = name,
                        value = value,
                        onValueChange = setter,
                        error = if (Num.parseOrNull(value) == null) "Enter a valid number" else null,
                    )
                }
                SelectField(
                    label = "Method",
                    options = SystemMethod.entries.map { Option(it, it.label) },
                    selected = SystemMethod.valueOf(systemMethod),
                    onSelect = { systemMethod = it.name },
                )
                InfoNote(SystemMethod.valueOf(systemMethod).description)
            }
        }

        ResultCard(
            label = outcome.label,
            value = outcome.value,
            caption = outcome.caption,
            error = outcome.error,
            onCopy = { copy(it, outcome.label) },
            copyValue = outcome.copyValue,
            onSave = outcome.value?.let { value ->
                {
                    args.onRecord.record(
                        when (mode) {
                            EquationMode.Linear -> "linear: $equation"
                            EquationMode.Quadratic -> "quadratic: $quadraticEquation"
                            EquationMode.System -> "system: $a1 x + $b1 y = $c1 ; $a2 x + $b2 y = $c2"
                        },
                        value,
                        // The values are kept, so that the saved calculation
                        // opens this tool with the same problem in it.
                        mapOf(
                            "equation" to equation,
                            "quadraticEquation" to quadraticEquation,
                            "variable" to variable,
                            "a1" to a1,
                            "b1" to b1,
                            "c1" to c1,
                            "a2" to a2,
                            "b2" to b2,
                            "c2" to c2,
                            "systemMethod" to systemMethod,
                        ),
                    )
                }
            },
        )

        if (outcome.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                outcome.rows.forEach { (label, value) -> ResultRow(label, value) }
            }
        }

        outcome.notes.forEach { InfoNote(it) }

        // The explanation of the mathematics, in the same expandable
        // sections the newer tools use, with the working that actually ran.
        ExplanationCards(
            com.calcsuite.engine.advanced.Explanation(
                definition = equationDefinition(mode),
                formula = outcome.formula,
                symbols = equationSymbols(mode),
                conditions = equationConditions(mode),
                method = equationMethod(mode),
                steps = outcome.steps,
                interpretation = equationInterpretation(mode, outcome),
                verification = outcome.verification,
                limitations = equationLimitations(mode),
            ),
        )

        ExpandablePanel(title = "Syntax help") {
            Text("Numbers: decimals with . or , (2.5 or 2,5)", style = MaterialTheme.typography.bodySmall)
            Text("Variable: one letter or word, default x — you can type t, n, price …", style = MaterialTheme.typography.bodySmall)
            Text("Operators: +  −  × or *  ÷ or /  ^ (exponent 0, 1 or 2)", style = MaterialTheme.typography.bodySmall)
            Text("Implicit multiplication: 2x, 3(x+1), x(x−1) all work", style = MaterialTheme.typography.bodySmall)
            Text("Fractions: 1/2 x + 3 = 4 uses an exact half as the coefficient", style = MaterialTheme.typography.bodySmall)
            Text("Equations need one = sign; both sides may contain the variable", style = MaterialTheme.typography.bodySmall)
            Text("Systems: type the six coefficients a₁, b₁, c₁, a₂, b₂, c₂ of a·x + b·y = c", style = MaterialTheme.typography.bodySmall)
        }
    }
}
