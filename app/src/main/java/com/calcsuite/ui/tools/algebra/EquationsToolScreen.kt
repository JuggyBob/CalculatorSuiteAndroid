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
import com.calcsuite.engine.algebra.SystemKind
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

private enum class EquationMode { Linear, Quadratic, System }

private data class EquationOutcome(
    val label: String,
    val value: String? = null,
    val copyValue: String? = null,
    val caption: String? = null,
    val error: String? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val formula: String,
    val explanation: String,
)

@Composable
fun EquationsToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var modeName by rememberSaveable { mutableStateOf(EquationMode.Linear.name) }
    val mode = EquationMode.valueOf(modeName)
    var equation by rememberSaveable { mutableStateOf("2x + 4 = 10") }
    var quadraticEquation by rememberSaveable { mutableStateOf("x^2 - 3x + 2 = 0") }
    var variable by rememberSaveable { mutableStateOf("x") }
    var a1 by rememberSaveable { mutableStateOf("2") }
    var b1 by rememberSaveable { mutableStateOf("3") }
    var c1 by rememberSaveable { mutableStateOf("12") }
    var a2 by rememberSaveable { mutableStateOf("1") }
    var b2 by rememberSaveable { mutableStateOf("-1") }
    var c2 by rememberSaveable { mutableStateOf("1") }

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
                                formula = "a·x + b = 0  →  x = −b ÷ a",
                                explanation = "When the coefficient and the constant both vanish the equation is true for every value.",
                            )
                            EquationKind.Contradiction -> EquationOutcome(
                                "Linear equation",
                                value = "No solution",
                                caption = "The variable terms cancel, leaving a false statement",
                                rows = listOf("Standard form" to result.standardForm),
                                steps = result.steps,
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
                    val solved = EquationEngine.solveSystem2(
                        values[0]!!, values[1]!!, values[2]!!,
                        values[3]!!, values[4]!!, values[5]!!,
                    )
                    when (solved) {
                        is CalcResult.Err -> EquationOutcome(
                            "2 × 2 system",
                            error = solved.error.message,
                            formula = "D = a₁b₂ − a₂b₁",
                            explanation = "Cramer's rule solves the system when the determinant is not zero.",
                        )
                        is CalcResult.Ok -> {
                            val result = solved.value
                            EquationOutcome(
                                label = "2 × 2 system",
                                value = when (result.kind) {
                                    SystemKind.Unique -> if (result.x != null && result.y != null) {
                                        "x = ${num(result.x)}, y = ${num(result.y)}"
                                    } else {
                                        "One solution"
                                    }
                                    SystemKind.Infinite -> "Infinitely many solutions"
                                    SystemKind.Inconsistent -> "No solution"
                                },
                                copyValue = if (result.x != null && result.y != null) {
                                    "x = ${Num.formatForCopy(result.x)}, y = ${Num.formatForCopy(result.y)}"
                                } else {
                                    result.message
                                },
                                caption = result.message,
                                rows = buildList {
                                    add("Determinant D" to num(result.determinant))
                                    result.x?.let { add("x" to num(it)) }
                                    result.y?.let { add("y" to num(it)) }
                                },
                                steps = result.steps,
                                formula = "D = a₁b₂ − a₂b₁ ;  x = (c₁b₂ − c₂b₁) ÷ D ;  y = (a₁c₂ − a₂c₁) ÷ D",
                                explanation = "A non-zero determinant means the lines cross once; zero means they are parallel or identical.",
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
                    args.onRecord(
                        when (mode) {
                            EquationMode.Linear -> "linear: $equation"
                            EquationMode.Quadratic -> "quadratic: $quadraticEquation"
                            EquationMode.System -> "system: $a1 x + $b1 y = $c1 ; $a2 x + $b2 y = $c2"
                        },
                        value,
                    )
                }
            },
        )

        if (outcome.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                outcome.rows.forEach { (label, value) -> ResultRow(label, value) }
            }
        }

        if (outcome.steps.isNotEmpty()) {
            StepsCard(outcome.steps)
        }

        outcome.notes.forEach { InfoNote(it) }

        FormulaCard(formula = outcome.formula, explanation = outcome.explanation)

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
