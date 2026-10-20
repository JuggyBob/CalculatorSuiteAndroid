package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.engine.advanced.FourierEngine
import com.calcsuite.engine.advanced.FourierOperations
import com.calcsuite.engine.advanced.formatReal
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.Evaluator
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

/**
 * The Fourier Analysis tool.
 *
 * There are two modes: a Fourier series built from an expression, and the
 * discrete transform of a list of samples. They are kept apart, because a
 * series is about a function and the transform is about a finite record.
 */
@Composable
fun FourierToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Fourier Analysis",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        FourierToolContent(args)
    }
}

/** The content of the tool, without the scaffold. */
@Composable
fun FourierToolContent(args: ToolArgs) {
    var modeName by rememberSaveable { mutableStateOf("Series") }
    var functionText by rememberSaveable { mutableStateOf("sin(x)") }
    var variable by rememberSaveable { mutableStateOf("x") }
    var period by rememberSaveable { mutableStateOf("6.283185307179586") }
    var harmonics by rememberSaveable { mutableStateOf("3") }
    var samplesText by rememberSaveable { mutableStateOf("0, 1, 0, -1, 0, 1, 0, -1") }
    var interval by rememberSaveable { mutableStateOf("1") }

    val mode = if (modeName == "Series") "Series" else "Samples"

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

    var seriesResult by remember { mutableStateOf<FourierOperations.SeriesResult?>(null) }
    var transformResult by remember { mutableStateOf<FourierOperations.TransformResult?>(null) }

    LaunchedEffect(parsed, period, harmonics, mode) {
        if (mode == "Series" && parsed != null) {
            val p = period.trim().toDoubleOrNull() ?: 0.0
            val n = harmonics.trim().toIntOrNull() ?: 0
            seriesResult = withContext(Dispatchers.Default) {
                FourierOperations.series(parsed, variable.trim().ifBlank { "x" }, p, n)
            }
        } else {
            seriesResult = null
        }
    }

    LaunchedEffect(samplesText, interval, mode) {
        if (mode == "Samples") {
            val values = samplesText.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
            val step = interval.trim().toDoubleOrNull() ?: 1.0
            transformResult = withContext(Dispatchers.Default) {
                if (values.size < 2) null
                else FourierOperations.transform(values.toDoubleArray(), step)
            }
        } else {
            transformResult = null
        }
    }

    FormulaCard(
        formula = "f(x) ≈ a₀/2 + Σₙ₌₁ᴺ [aₙ·cos(2πnx/T) + bₙ·sin(2πnx/T)]\n" +
            "aₙ = (2/T)·∫ f(x)·cos(2πnx/T) dx\n" +
            "bₙ = (2/T)·∫ f(x)·sin(2πnx/T) dx",
        explanation = "A Fourier series writes a periodic function as a sum of waves. " +
            "Each coefficient measures how much of the function sits at that frequency, " +
            "and adding more of them makes the approximation closer.",
        variables = listOf(
            "T" to "the period of the function",
            "N" to "the number of harmonics kept",
            "aₙ, bₙ" to "the cosine and sine coefficients",
        ),
    )

    SegmentedChoice(
        options = listOf("Series" to "Series", "Samples" to "Samples"),
        selected = mode,
        onSelect = { modeName = it },
        label = "Input",
    )

    if (mode == "Series") {
        SectionCard(title = "The function") {
            PlainField(
                label = "f(x) =",
                value = functionText,
                onValueChange = { functionText = it },
                monospace = true,
                placeholder = "sin(x)",
            )
            PlainField(
                label = "Variable",
                value = variable,
                onValueChange = { variable = it },
                monospace = true,
                placeholder = "x",
            )
            NumberField(
                label = "Period T",
                value = period,
                onValueChange = { period = it },
            )
            NumberField(
                label = "Harmonics",
                value = harmonics,
                onValueChange = { harmonics = it },
                integerOnly = true,
            )
            parseError?.let { ErrorNotice(it) }
            InfoNote(
                "A function has to be periodic for the series to converge to it. A " +
                    "function that is not periodic can still be approximated over one " +
                    "interval, but the result is then an approximation on that interval " +
                    "only, and it is labelled as such.",
            )
        }
    } else {
        SectionCard(title = "The samples") {
            PlainField(
                label = "Sample values, in order",
                value = samplesText,
                onValueChange = { samplesText = it },
                monospace = true,
                placeholder = "0, 1, 0, -1",
            )
            NumberField(
                label = "Length of the record",
                value = interval,
                onValueChange = { interval = it },
            )
            InfoNote(
                "Give the values in the order they were measured, equally spaced in " +
                    "time. The length of the record is how much time the samples cover.",
            )
        }
    }

    // ------------------------------------------------------------ the answer

    val series = seriesResult
    if (series != null) {
        ResultCard(
            label = "Fourier series",
            value = series.series.render(variable.trim().ifBlank { "x" }),
            caption = "N = ${series.series.harmonics} harmonics, T = " +
                formatReal(series.series.period),
            approx = true,
            onCopy = { args.onRecord("Fourier series of $functionText", it) },
            copyValue = series.series.render(variable.trim().ifBlank { "x" }),
        )
        ResultRow(label = "Constant term a₀/2", value = formatReal(series.series.a0 / 2))
        ResultRow(
            label = "Difference from the function",
            value = formatReal(series.error),
        )
        ExplanationCards(series.explanation)
    }

    val transform = transformResult
    if (transform != null) {
        ResultCard(
            label = "Discrete transform",
            value = "${transform.transform.sampleCount} bins, spacing " +
                formatReal(transform.transform.frequencyResolution),
            caption = "The strongest harmonic is " +
                (transform.transform.bins.maxByOrNull { it.magnitude }?.let {
                    "bin ${it.index + 1} at ${formatReal(it.frequency)}"
                } ?: "none"),
            approx = true,
        )
        SectionCard(title = "The bins") {
            for (bin in transform.transform.bins.take(8)) {
                ResultRow(
                    label = "bin ${bin.index + 1}  ·  f = ${formatReal(bin.frequency)}",
                    value = "amplitude ${formatReal(bin.magnitude)}   " +
                        "phase ${formatReal(bin.phase)}",
                )
            }
            if (transform.transform.bins.size > 8) {
                Text(
                    "…and ${transform.transform.bins.size - 8} more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExplanationCards(transform.explanation)
    }

    // ------------------------------------------------------------- the graph

    if (mode == "Series" && parsed != null && series != null) {
        SectionCard(title = "The function and its approximation") {
            FourierGraph(
                function = parsed,
                variable = variable.trim().ifBlank { "x" },
                series = series.series,
                modifier = Modifier.height(260.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = {
                    val n = (harmonics.trim().toIntOrNull() ?: 3) - 1
                    harmonics = (n.coerceAtLeast(1)).toString()
                }) { Text("Fewer harmonics") }
                TextButton(onClick = {
                    val n = (harmonics.trim().toIntOrNull() ?: 3) + 1
                    harmonics = n.coerceAtMost(60).toString()
                }) { Text("More harmonics") }
            }
            Text(
                "The mint curve is the function, and the amber curve is the truncated " +
                    "series. Adding a harmonic brings the two closer, except near a " +
                    "jump, where the series overshoots — the Gibbs phenomenon.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (mode == "Samples") {
        SectionCard(title = "The samples and the spectrum") {
            val values = samplesText.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
            SampleGraph(
                samples = values.toDoubleArray(),
                transform = transform?.transform,
                modifier = Modifier.height(240.dp),
            )
            Text(
                "The left picture is the signal as it was measured; the right picture is " +
                    "the amplitude of each frequency in the transform.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The function and its truncated series, drawn together.
 */
@Composable
private fun FourierGraph(
    function: Node,
    variable: String,
    series: FourierEngine.Series,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.fillMaxWidth()) {
        val from = series.from
        val to = series.from + series.period
        val points = 240
        val functionPoints = FourierEngine.sampleFunction(function, variable, from, to, points)
        val seriesPoints = FourierEngine.sample(series, from, to, points)
        if (functionPoints.isEmpty() && seriesPoints.isEmpty()) return@Canvas

        var lowest = Double.POSITIVE_INFINITY
        var highest = Double.NEGATIVE_INFINITY
        for ((_, y) in functionPoints + seriesPoints) {
            if (y < lowest) lowest = y
            if (y > highest) highest = y
        }
        if (!lowest.isFinite()) return@Canvas
        if (highest - lowest < 1e-9) { highest = lowest + 1.0; lowest -= 1.0 }
        val margin = (highest - lowest) * 0.1

        fun toPx(x: Double, y: Double) = Offset(
            (((x - from) / (to - from)) * size.width).toFloat(),
            (size.height - ((y - lowest + margin) / (highest - lowest + 2 * margin)) * size.height)
                .toFloat(),
        )

        // The axis.
        val axisY = toPx(0.0, 0.0).y
        drawLine(
            Color(0x33FFFFFF),
            Offset(0f, axisY),
            Offset(size.width, axisY),
            strokeWidth = 1.2f,
        )

        // The function.
        if (functionPoints.size > 1) {
            val path = Path()
            functionPoints.forEachIndexed { index, (x, y) ->
                val point = toPx(x, y)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path,
                Color(0xFF7BE3B0),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f),
            )
        }

        // The truncated series.
        if (seriesPoints.size > 1) {
            val path = Path()
            seriesPoints.forEachIndexed { index, (x, y) ->
                val point = toPx(x, y)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path,
                Color(0xFFFFC46B),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f),
            )
        }
    }
}

/**
 * The samples and the spectrum of the transform, side by side.
 */
@Composable
private fun SampleGraph(
    samples: DoubleArray,
    transform: FourierEngine.Transform?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth()) {
        val gap = size.width * 0.06f
        val halfWidth = (size.width - gap) / 2
        if (samples.isEmpty()) return@Canvas

        var lowest = samples.min()
        var highest = samples.max()
        if (highest - lowest < 1e-9) { highest = lowest + 1.0; lowest -= 1.0 }
        val margin = (highest - lowest) * 0.12

        // The signal.
        val step = halfWidth / samples.size
        for (i in samples.indices) {
            val x = i * step
            val y = (size.height -
                ((samples[i] - lowest + margin) / (highest - lowest + 2 * margin)) * size.height)
                .toFloat()
            drawLine(
                Color(0xFF7BE3B0),
                Offset(x, size.height),
                Offset(x, y.toFloat()),
                strokeWidth = 2f,
            )
        }
        drawLine(
            Color(0x33FFFFFF),
            Offset(0f, size.height),
            Offset(halfWidth, size.height),
            strokeWidth = 1.2f,
        )

        // The spectrum.
        if (transform == null) return@Canvas
        val bins = transform.bins.take(transform.bins.size / 2 + 1)
        if (bins.isEmpty()) return@Canvas
        val largest = bins.maxOf { it.magnitude }.takeIf { it > 0.0 } ?: 1.0
        val binWidth = halfWidth / bins.size
        bins.forEachIndexed { index, bin ->
            val x = halfWidth + gap + index * binWidth
            val height = ((bin.magnitude / largest) * (size.height - 8f)).toFloat()
            drawRect(
                Color(0xFFFFC46B),
                topLeft = Offset(x, size.height - height),
                size = androidx.compose.ui.geometry.Size(binWidth * 0.8f, height),
            )
        }
    }
}
