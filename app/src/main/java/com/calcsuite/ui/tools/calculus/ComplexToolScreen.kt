package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.engine.advanced.ComplexAnalysis
import com.calcsuite.engine.advanced.ComplexNum
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.advanced.formatReal
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.Node
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.ExplanationCards
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import com.calcsuite.ui.tools.prefill

/**
 * The Complex Analysis tool.
 *
 * It has two halves that are kept apart: a calculator for complex numbers,
 * which is exact arithmetic, and a workspace for complex functions, where the
 * answers are numerical and the limits of each method are stated.
 */
@Composable
fun ComplexToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Complex Analysis",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        ComplexToolContent(args)
    }
}

/** The content of the tool, without the scaffold. */
@Composable
fun ComplexToolContent(args: ToolArgs) {
    var modeName by rememberSaveable { mutableStateOf("Calculator") }
    val mode = modeName

    FormulaCard(
        formula = "z = a + bi\n" +
            "|z| = √(a² + b²)\n" +
            "arg z = atan2(b, a)\n" +
            "z = |z|·e^(i·arg z)\n" +
            "e^(iθ) = cos θ + i·sin θ",
        explanation = "A complex number is a point in the plane. The modulus is its " +
            "distance from the origin and the argument is the angle it makes with the " +
            "real axis, and together they give the polar form.",
        variables = listOf(
            "a, b" to "the real and imaginary parts",
            "|z|" to "the modulus, the distance from the origin",
            "arg z" to "the argument, the angle, taken in (−π, π]",
        ),
    )

    SegmentedChoice(
        options = listOf(
            "Calculator" to "Calculator",
            "Function" to "Function",
            "Contour" to "Contour",
            "Residue" to "Residue",
        ),
        selected = mode,
        onSelect = { modeName = it },
        label = "What to do",
    )

    when (mode) {
        "Calculator" -> CalculatorSection(args)
        "Function" -> FunctionSection(args)
        "Contour" -> ContourSection(args)
        "Residue" -> ResidueSection(args)
    }
}

/**
 * The complex-number calculator.
 */
@Composable
private fun CalculatorSection(args: ToolArgs) {
    var realA by rememberSaveable { mutableStateOf("3") }
    var imagA by rememberSaveable { mutableStateOf("4") }
    var realB by rememberSaveable { mutableStateOf("1") }
    var imagB by rememberSaveable { mutableStateOf("2") }
    var operation by rememberSaveable { mutableStateOf("Add") }
    var power by rememberSaveable { mutableStateOf("2") }

    val a = ComplexNum(realA.trim().toDoubleOrNull() ?: 0.0, imagA.trim().toDoubleOrNull() ?: 0.0)
    val b = ComplexNum(realB.trim().toDoubleOrNull() ?: 0.0, imagB.trim().toDoubleOrNull() ?: 0.0)

    SectionCard(title = "Two complex numbers") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "a (real)",
                value = realA,
                onValueChange = { realA = it },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "a (imaginary)",
                value = imagA,
                onValueChange = { imagA = it },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "b (real)",
                value = realB,
                onValueChange = { realB = it },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "b (imaginary)",
                value = imagB,
                onValueChange = { imagB = it },
                modifier = Modifier.weight(1f),
            )
        }
        SegmentedChoice(
            options = listOf(
                "Add" to "a + b",
                "Subtract" to "a − b",
                "Multiply" to "a · b",
                "Divide" to "a ÷ b",
                "Power" to "aⁿ",
            ),
            selected = operation,
            onSelect = { operation = it },
            label = "Operation",
        )
        if (operation == "Power") {
            NumberField(
                label = "Exponent",
                value = power,
                onValueChange = { power = it },
                integerOnly = true,
            )
        }
    }

    val result = when (operation) {
        "Add" -> a + b
        "Subtract" -> a - b
        "Multiply" -> a * b
        "Divide" -> if (b.modulus < 1e-12) null else a / b
        else -> {
            val n = power.trim().toIntOrNull() ?: 0
            a.pow(n)
        }
    }

    if (result == null) {
        ErrorNotice("b is zero, so the division is not defined.")
    } else {
        ResultCard(
            label = operation,
            value = result.cartesian(),
            caption = result.polar(),
            onCopy = { copied -> args.onRecord("z = ${result.cartesian()}", copied) },
            copyValue = result.cartesian(),
        )
        ResultRow(label = "Real part", value = formatReal(result.re))
        ResultRow(label = "Imaginary part", value = formatReal(result.im))
        ResultRow(label = "Modulus", value = formatReal(result.modulus))
        ResultRow(
            label = "Argument",
            value = "${formatReal(result.argument * 180 / Math.PI)}°",
        )
        ResultRow(label = "Polar form", value = result.polarRectangular())
        ResultRow(label = "Conjugate", value = result.conjugate.cartesian())
    }

    SectionCard(title = "The powers and the roots") {
        val n = power.trim().toIntOrNull() ?: 2
        ResultRow(label = "a²", value = a.pow(2).cartesian())
        ResultRow(label = "a³", value = a.pow(3).cartesian())
        ResultRow(label = "aⁿ (n = $n)", value = a.pow(n).cartesian())
        ResultRow(label = "The principal square root", value = a.sqrt().cartesian())
        val roots = a.roots(if (n >= 1) n else 2)
        ResultRow(
            label = "All $n-th roots",
            value = roots.joinToString("   ") { it.cartesian() },
        )
        ResultRow(label = "e^(ia)", value = a.exp().cartesian())
        ResultRow(label = "The principal logarithm of a", value = a.ln().cartesian())
    }

    ExplanationCards(
        Explanation(
            definition = "Complex arithmetic extends the real numbers by adding an " +
                "imaginary part, with i² = −1. It is what the plane, the " +
                "polynomials and the exponential function need.",
            formula = "z = a + bi   ·   z·w = (ac − bd) + (ad + bc)i   ·   " +
                "z/w = z·w̄ / |w|²",
            symbols = listOf(
                "i" to "the imaginary unit, with i² = −1",
                "z̄" to "the conjugate of z, which is a − bi",
            ),
            conditions = listOf(
                "Division needs a non-zero divisor.",
            ),
            method = "The rules are the ordinary rules of arithmetic, with i² = −1 kept " +
                "track of, and the denominator is rationalised by multiplying by the " +
                "conjugate.",
            steps = listOf(
                "a = ${a.cartesian()} and b = ${b.cartesian()}.",
                "The result is ${result?.cartesian() ?: "not defined"}.",
            ),
            interpretation = "The modulus is the distance from the origin and the " +
                "argument is the angle, so a complex number is a point and a length " +
                "with a direction.",
            verification = null,
            limitations = "The logarithm, the roots and the powers are all taken on the " +
                "principal branch, so a value with an imaginary part between −π and π. " +
                "The other values differ by multiples of 2πi.",
        ),
    )
}

/**
 * The complex-function workspace: evaluation, the Cauchy–Riemann check and
 * the picture.
 */
@Composable
private fun FunctionSection(args: ToolArgs) {
    var functionText by rememberSaveable {
        mutableStateOf(args.prefill("functionText", "expression") ?: "z^2")
    }
    var pointReal by rememberSaveable {
        mutableStateOf(args.prefill("pointReal") ?: "1")
    }
    var pointImag by rememberSaveable {
        mutableStateOf(args.prefill("pointImag") ?: "1")
    }
    var view by remember { mutableStateOf(PlotViewport(span = 8.0)) }

    val parsed = remember(functionText) {
        when (val result = CalculusParser.parse(functionText)) {
            is CalcResult.Ok -> result.value
            is CalcResult.Err -> null
        }
    }
    val parseError = remember(functionText) {
        when (val result = CalculusParser.parse(functionText)) {
            is CalcResult.Ok -> null
            is CalcResult.Err -> result.error.message
        }
    }

    var outcome by remember { mutableStateOf<CalcResult<Pair<ComplexNum, ComplexAnalysis.CauchyRiemannCheck?>>?>(null) }
    LaunchedEffect(parsed, pointReal, pointImag) {
        if (parsed == null) {
            outcome = null
            return@LaunchedEffect
        }
        val point = ComplexNum(
            pointReal.trim().toDoubleOrNull() ?: 0.0,
            pointImag.trim().toDoubleOrNull() ?: 0.0,
        )
        outcome = withContext(Dispatchers.Default) {
            val value = ComplexAnalysis.evaluate(parsed, "z", point)
            val check = ComplexAnalysis.cauchyRiemann(parsed, "z", point)
            if (value == null) {
                CalcResult.Err(
                    com.calcsuite.core.CalcError(
                        com.calcsuite.core.ErrorKind.Domain,
                        "The function has no value at that point.",
                    ),
                )
            } else {
                CalcResult.Ok(value to check)
            }
        }
    }

    SectionCard(title = "The function") {
        PlainField(
            label = "f(z) =",
            value = functionText,
            onValueChange = { functionText = it },
            monospace = true,
            placeholder = "z^2",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "Real part of z",
                value = pointReal,
                onValueChange = { pointReal = it },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Imaginary part of z",
                value = pointImag,
                onValueChange = { pointImag = it },
                modifier = Modifier.weight(1f),
            )
        }
        parseError?.let { ErrorNotice(it) }
        InfoNote("Use z as the complex variable, for example z^2, 1/z or exp(z).")
    }

    when (val current = outcome) {
        null -> if (parseError == null) InfoNote("Working…")
        is CalcResult.Err -> ErrorNotice(current.error.message)
        is CalcResult.Ok -> {
            val (value, check) = current.value
            ResultCard(
                label = "f(z)",
                value = value.cartesian(),
                caption = value.polar(),
            )
            ResultRow(label = "Modulus", value = formatReal(value.modulus))
            ResultRow(
                label = "Argument",
                value = "${formatReal(value.argument * 180 / Math.PI)}°",
            )
            if (check != null) {
                ResultRow(
                    label = "Cauchy–Riemann residual",
                    value = formatReal(check.residual),
                )
                ResultRow(
                    label = "uₓ, u_y",
                    value = "${formatReal(check.ux)}, ${formatReal(check.uy)}",
                )
                ResultRow(
                    label = "vₓ, v_y",
                    value = "${formatReal(check.vx)}, ${formatReal(check.vy)}",
                )
            }
            ExplanationCards(
                Explanation(
                    definition = "A complex function is holomorphic where it can be " +
                        "differentiated as a complex function. The Cauchy–Riemann " +
                        "equations are the condition for that.",
                    formula = "uₓ = v_y   and   u_y = −vₓ",
                    symbols = listOf(
                        "u, v" to "the real and imaginary parts of the function",
                    ),
                    conditions = listOf(
                        "The function has to be defined and differentiable in a " +
                            "neighbourhood of the point, away from any branch cut.",
                    ),
                    method = "The four partial derivatives are taken by finite " +
                        "differences with a small step, and the two sides of each " +
                        "equation are compared.",
                    steps = listOf(
                        "The function was evaluated at the point, and the partial " +
                            "derivatives were taken with a step of " +
                            (check?.step?.let { formatReal(it) } ?: "1e-5") + ".",
                        "The residual |uₓ − v_y| + |u_y + vₓ| is " +
                            (check?.let { formatReal(it.residual) } ?: "not available") + ".",
                    ),
                    interpretation = "When the residual is small the two equations hold " +
                        "to within the precision of the check, which is what you would " +
                        "expect of a function that is holomorphic there.",
                    verification = "The residual is a direct numerical check of the two " +
                        "equations at that point.",
                    limitations = "This is a numerical check at one point, not a proof. A " +
                        "single point where the equations happen to hold says nothing " +
                        "about the function elsewhere, and the finite differences can " +
                        "be fooled by a function that is not differentiable.",
                ),
            )
        }
    }

    // The picture: the image of a circle under the function.
    SectionCard(title = "The complex plane") {
        // The circle that is mapped is fixed: it is the circle of radius two
        // about the origin, so that moving the picture only shows a different
        // part of the same image and never changes the function.
        val image = remember(parsed) {
            if (parsed == null) {
                emptyList()
            } else {
                val steps = 160
                (0..steps).mapNotNull { i ->
                    val angle = 2 * Math.PI * i / steps
                    // The unit circle, which is the standard curve to map.
                    val z = ComplexNum(Math.cos(angle), Math.sin(angle))
                    ComplexAnalysis.evaluate(parsed, "z", z)
                }
            }
        }
        ComplexPlaneView(
            points = listOf(
                ComplexNum(0.0, 0.0) to Color(0xFFFFC46B),
            ),
            curves = listOf(image),
            view = view,
            onViewChange = { view = it },
            // A square picture, so the complex plane is not stretched.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { view = PlotViewport(span = 8.0) }) { Text("Reset view") }
        }
        Text(
            "The mint curve is the image of the unit circle under the function. " +
                "Moving the picture shows another part of the same image; the function " +
                "itself does not change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The contour integral.
 */
@Composable
private fun ContourSection(args: ToolArgs) {
    var functionText by rememberSaveable { mutableStateOf("1/z") }
    var xText by rememberSaveable { mutableStateOf("cos(t)") }
    var yText by rememberSaveable { mutableStateOf("sin(t)") }
    var from by rememberSaveable { mutableStateOf("0") }
    var to by rememberSaveable { mutableStateOf("6.283185307179586") }
    var view by remember { mutableStateOf(PlotViewport(span = 8.0)) }

    val parsed = remember(functionText) {
        when (val result = CalculusParser.parse(functionText)) {
            is CalcResult.Ok -> result.value
            is CalcResult.Err -> null
        }
    }
    val curveX = remember(xText) {
        when (val result = CalculusParser.parse(xText)) {
            is CalcResult.Ok -> result.value
            is CalcResult.Err -> null
        }
    }
    val curveY = remember(yText) {
        when (val result = CalculusParser.parse(yText)) {
            is CalcResult.Ok -> result.value
            is CalcResult.Err -> null
        }
    }

    var outcome by remember { mutableStateOf<CalcResult<ComplexNum>?>(null) }
    LaunchedEffect(parsed, curveX, curveY, from, to) {
        if (parsed == null || curveX == null || curveY == null) {
            outcome = null
            return@LaunchedEffect
        }
        outcome = withContext(Dispatchers.Default) {
            val contour = ComplexAnalysis.Contour(
                x = curveX,
                y = curveY,
                from = from.trim().toDoubleOrNull() ?: 0.0,
                to = to.trim().toDoubleOrNull() ?: 0.0,
                description = "the curve (x(t), y(t))",
            )
            val value = ComplexAnalysis.contourIntegral(parsed, "z", contour)
            if (value == null) {
                CalcResult.Err(
                    com.calcsuite.core.CalcError(
                        com.calcsuite.core.ErrorKind.Domain,
                        "The integral could not be evaluated along the whole curve.",
                    ),
                )
            } else {
                CalcResult.Ok(value)
            }
        }
    }

    SectionCard(title = "The function and the contour") {
        PlainField(
            label = "f(z) =",
            value = functionText,
            onValueChange = { functionText = it },
            monospace = true,
            placeholder = "1/z",
        )
        PlainField(
            label = "x(t)",
            value = xText,
            onValueChange = { xText = it },
            monospace = true,
            placeholder = "cos(t)",
        )
        PlainField(
            label = "y(t)",
            value = yText,
            onValueChange = { yText = it },
            monospace = true,
            placeholder = "sin(t)",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "From t",
                value = from,
                onValueChange = { from = it },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "To t",
                value = to,
                onValueChange = { to = it },
                modifier = Modifier.weight(1f),
            )
        }
        InfoNote(
            "The contour is given parametrically. The unit circle counter-clockwise " +
                "is x(t) = cos t, y(t) = sin t, for t from 0 to 2π.",
        )
    }

    when (val current = outcome) {
        null -> InfoNote("Working…")
        is CalcResult.Err -> ErrorNotice(current.error.message)
        is CalcResult.Ok -> {
            ResultCard(
                label = "∮ f(z) dz",
                value = current.value.cartesian(),
                caption = "around the contour, counter-clockwise",
                approx = true,
            )
            ExplanationCards(
                Explanation(
                    definition = "A contour integral adds up the values of a complex " +
                        "function along a curve in the complex plane.",
                    formula = "∮ᴄ f(z) dz = ∫ₐᵇ f(z(t))·z′(t) dt",
                    symbols = listOf(
                        "C" to "the contour, given by z(t)",
                        "z′(t)" to "the derivative of the curve, which carries the " +
                            "direction and the length of each little piece",
                    ),
                    conditions = listOf(
                        "The function has to be defined along the whole contour.",
                        "The result depends on the direction the contour is traversed, " +
                            "and on which contour is taken.",
                    ),
                    method = "The function is pulled back onto the curve, multiplied by " +
                        "the derivative of the curve, and integrated with an adaptive " +
                        "Simpson rule.",
                    steps = listOf(
                        "The curve is (x(t), y(t)) = ($xText, $yText).",
                        "The integral was evaluated to ${current.value.cartesian()}.",
                    ),
                    interpretation = "When the function has a pole inside a simple closed " +
                        "contour, the integral is 2πi times the residue, and the value " +
                        "does not depend on the exact shape of the contour.",
                    verification = null,
                    limitations = "The result is a numerical approximation of the " +
                        "contour integral, not a closed form.",
                ),
            )
        }
    }

    SectionCard(title = "The contour") {
        val points = remember(curveX, curveY, from, to) {
            if (curveX == null || curveY == null) {
                emptyList()
            } else {
                val steps = 90
                (0..steps).mapNotNull { i ->
                    val t = (from.trim().toDoubleOrNull() ?: 0.0) +
                        ((to.trim().toDoubleOrNull() ?: 0.0) - (from.trim().toDoubleOrNull() ?: 0.0)) * i / steps
                    val x = com.calcsuite.engine.calculus.Evaluator.eval(curveX, mapOf("t" to t))
                        .valueOrNull() ?: return@mapNotNull null
                    val y = com.calcsuite.engine.calculus.Evaluator.eval(curveY, mapOf("t" to t))
                        .valueOrNull() ?: return@mapNotNull null
                    ComplexNum(x, y)
                }
            }
        }
        ComplexPlaneView(
            points = emptyList(),
            curves = listOf(points),
            view = view,
            onViewChange = { view = it },
            // A square picture, so the complex plane is not stretched.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { view = PlotViewport(span = 8.0) }) { Text("Reset view") }
        }
    }
}

/**
 * The residue at a point.
 */
@Composable
private fun ResidueSection(args: ToolArgs) {
    var functionText by rememberSaveable { mutableStateOf("1/z") }
    var pointReal by rememberSaveable { mutableStateOf("0") }
    var pointImag by rememberSaveable { mutableStateOf("0") }

    val parsed = remember(functionText) {
        when (val result = CalculusParser.parse(functionText)) {
            is CalcResult.Ok -> result.value
            is CalcResult.Err -> null
        }
    }

    var outcome by remember { mutableStateOf<CalcResult<ComplexAnalysis.SingularityResult>?>(null) }
    LaunchedEffect(parsed, pointReal, pointImag) {
        if (parsed == null) {
            outcome = null
            return@LaunchedEffect
        }
        outcome = withContext(Dispatchers.Default) {
            val point = ComplexNum(
                pointReal.trim().toDoubleOrNull() ?: 0.0,
                pointImag.trim().toDoubleOrNull() ?: 0.0,
            )
            CalcResult.Ok(ComplexAnalysis.singularity(parsed, "z", point))
        }
    }

    SectionCard(title = "The function and the point") {
        PlainField(
            label = "f(z) =",
            value = functionText,
            onValueChange = { functionText = it },
            monospace = true,
            placeholder = "1/z",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "Real part of z₀",
                value = pointReal,
                onValueChange = { pointReal = it },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Imaginary part of z₀",
                value = pointImag,
                onValueChange = { pointImag = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    when (val current = outcome) {
        null -> InfoNote("Working…")
        is CalcResult.Err -> ErrorNotice(current.error.message)
        is CalcResult.Ok -> {
            val result = current.value
            ResultCard(
                label = "The point",
                value = result.classification.label,
                caption = result.residue?.let { "residue = ${it.cartesian()}" },
            )
            ResultRow(label = "Order of the pole", value = result.order.toString())
            ExplanationCards(
                Explanation(
                    definition = "A residue is the coefficient of (z − z₀)⁻¹ in the " +
                        "expansion of a function near a pole. It is what the residue " +
                        "theorem counts.",
                    formula = "res(f, z₀) = lim(z→z₀) (z − z₀)ᵏ·f(z), where k is the order " +
                        "of the pole",
                    symbols = listOf(
                        "z₀" to "the point being examined",
                        "k" to "the order of the pole",
                    ),
                    conditions = listOf(
                        "The point has to be a singularity of the function for a residue " +
                            "to matter.",
                    ),
                    method = "The limit of (z − z₀)ᵏ·f(z) is taken along several " +
                        "directions with a shrinking step, and the values are averaged.",
                    steps = result.steps,
                    interpretation = "The residue theorem says that the integral of the " +
                        "function around a simple closed contour is 2πi times the sum of " +
                        "the residues of the poles inside it.",
                    verification = null,
                    limitations = "The residue is a numerical estimate from a limit, and " +
                        "the limit is taken only along a few directions. It is not a " +
                        "symbolic calculation of the coefficient.",
                ),
            )
        }
    }
}
