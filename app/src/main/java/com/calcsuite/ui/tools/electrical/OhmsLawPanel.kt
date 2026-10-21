package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.core.map
import com.calcsuite.engine.other.OhmsLawEngine
import com.calcsuite.engine.other.Quantity
import com.calcsuite.ui.components.Fmt
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

/**
 * Ohm's law solver: pick any two of voltage, current, resistance and power,
 * enter their SI-prefixed values, and all four are reported.
 *
 * This is the calculator that was in the app before the circuit builder, kept
 * exactly as it was and now one of the calculators inside Electrical
 * Circuits.
 *
 * A small Canvas diagram shows the circuit quantities (voltage source,
 * resistor, current direction, power) and every relationship is listed in the
 * formula card. Degenerate combinations (zero resistance with voltage, an
 * inconsistent third value, underdetermined pairs) are reported by the engine.
 */
@Composable
fun OhmsLawPanel(args: ToolArgs) {
    val fmt = rememberFmt()

    var q1 by rememberSaveable { mutableStateOf(Quantity.VOLTAGE) }
    var value1 by rememberSaveable { mutableStateOf("") }
    var prefix1 by rememberSaveable { mutableStateOf("none") }
    var q2 by rememberSaveable { mutableStateOf(Quantity.RESISTANCE) }
    var value2 by rememberSaveable { mutableStateOf("") }
    var prefix2 by rememberSaveable { mutableStateOf("none") }

    val si1 = toSi(Num.parseOrNull(value1), prefix1)
    val si2 = toSi(Num.parseOrNull(value2), prefix2)

    val computed: CalcResult<OhmsResultView>? = if (q1 == q2 || si1 == null || si2 == null) {
        null
    } else {
        OhmsLawEngine.solve(linkedMapOf(q1 to si1, q2 to si2)).map { solved ->
            OhmsResultView(
                voltage = solved.voltage,
                current = solved.current,
                resistance = solved.resistance,
                power = solved.power,
                formulas = solved.formulas,
                assumptions = solved.assumptions,
                notes = solved.notes,
            )
        }
    }
    val result = (computed as? CalcResult.Ok)?.value
    val error = when {
        q1 == q2 -> "Choose two different quantities"
        value1.isNotBlank() && Num.parseOrNull(value1) == null -> "The first value is not a valid number"
        value2.isNotBlank() && Num.parseOrNull(value2) == null -> "The second value is not a valid number"
        else -> (computed as? CalcResult.Err)?.error?.message
    }

    SectionCard(title = "Known quantities (exactly two)") {
        QuantityPicker(
            label = "First known quantity",
            quantity = q1,
            onQuantity = { q1 = it },
            value = value1,
            onValue = { value1 = it },
            prefixId = prefix1,
            onPrefix = { prefix1 = it },
        )
        Spacer(Modifier.height(4.dp))
        QuantityPicker(
            label = "Second known quantity",
            quantity = q2,
            onQuantity = { q2 = it },
            value = value2,
            onValue = { value2 = it },
            prefixId = prefix2,
            onPrefix = { prefix2 = it },
        )
    }

    CircuitDiagram(result)

    ResultCard(
        label = "Derived values",
        value = result?.let { r ->
            derivedQuantities(q1, q2).joinToString(" · ") { q ->
                "${q.symbol} = ${fmt.num(valueOf(r, q), precision = maxOf(fmt.precision, 10))} ${unitSymbol(q)}"
            }
        },
        caption = result?.let { r -> r.formulas.joinToString("   ·   ") },
        error = error,
        onSave = if (result != null && si1 != null && si2 != null) {
            {
                args.onRecord(
                    "Ohm's law: ${q1.label} ${fmt.num(si1, precision = maxOf(fmt.precision, 10))} ${unitSymbol(q1)}, " +
                        "${q2.label} ${fmt.num(si2, precision = maxOf(fmt.precision, 10))} ${unitSymbol(q2)}",
                    Quantity.entries.joinToString(", ") { q ->
                        "${q.symbol} = ${fmt.num(valueOf(result, q), precision = maxOf(fmt.precision, 10))} ${unitSymbol(q)}"
                    },
                )
            }
        } else {
            null
        },
    )

    if (result != null) {
        ResultRow(
            label = "Voltage V",
            value = fmt.num(result.voltage, precision = maxOf(fmt.precision, 10)),
            unit = "V",
        )
        ResultRow(
            label = "Current I",
            value = fmt.num(result.current, precision = maxOf(fmt.precision, 10)),
            unit = "A",
        )
        ResultRow(
            label = "Resistance R",
            value = fmt.num(result.resistance, precision = maxOf(fmt.precision, 10)),
            unit = "Ω",
        )
        ResultRow(
            label = "Power P",
            value = fmt.num(result.power, precision = maxOf(fmt.precision, 10)),
            unit = "W",
            emphasis = true,
        )
        SectionCard(title = "Assumptions") {
            result.assumptions.forEach { assumption ->
                Text(
                    "• $assumption",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        result.notes.forEach { InfoNote(it) }
    }

    FormulaCard(
        formula = "V = I × R\nP = V × I = I² × R = V² ÷ R",
        explanation = "Ohm's law relates voltage, current and resistance; the power law " +
            "combines them. Enter any two quantities and the other two are solved from these " +
            "relationships. SI prefixes (µ, m, k, M) are applied before the engine runs, so " +
            "5 kΩ is passed as 5000 Ω.",
        variables = listOf(
            "V" to "voltage in volts",
            "I" to "current in amperes",
            "R" to "resistance in ohms",
            "P" to "power in watts",
        ),
    )

    if (result == null) {
        InfoNote("Enter two known quantities (for example 12 V and 6 Ω) to see the solution.")
    }
}

/** Engine result flattened for the screen (no derived/known distinction needed). */
private data class OhmsResultView(
    val voltage: BigDecimal,
    val current: BigDecimal,
    val resistance: BigDecimal,
    val power: BigDecimal,
    val formulas: List<String>,
    val assumptions: List<String>,
    val notes: List<String>,
)

@Composable
private fun QuantityPicker(
    label: String,
    quantity: Quantity,
    onQuantity: (Quantity) -> Unit,
    value: String,
    onValue: (String) -> Unit,
    prefixId: String,
    onPrefix: (String) -> Unit,
) {
    SegmentedChoice(
        options = Quantity.entries.map { it to it.label },
        selected = quantity,
        onSelect = onQuantity,
        label = label,
    )
    NumberField(
        label = "Value (${unitSymbol(quantity)})",
        value = value,
        onValueChange = onValue,
        placeholder = if (quantity == Quantity.RESISTANCE) "6" else "12",
        error = if (value.isNotBlank() && Num.parseOrNull(value) == null) "Enter a valid number" else null,
    )
    SegmentedChoice(
        options = siPrefixes.map { it.id to it.shortLabel },
        selected = prefixId,
        onSelect = onPrefix,
        label = "SI prefix",
    )
}

/** Simple labelled circuit loop: battery (V), resistor (R), current arrow (I), power in the middle. */
@Composable
private fun CircuitDiagram(result: OhmsResultView?) {
    val fmt = rememberFmt()
    val lineColor = MaterialTheme.colorScheme.primary
    val labelBackground = MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(28.dp),
        ) {
            val stroke = 2.dp.toPx()
            val top = 0f
            val bottom = size.height
            val left = 0f

            drawRect(
                color = lineColor,
                style = Stroke(width = stroke),
            )

            // Resistor zig-zag on the top edge.
            val zigWidth = size.width * 0.32f
            val zigStart = size.width / 2 - zigWidth / 2
            val segments = 6
            val segmentWidth = zigWidth / segments
            val amplitude = 10.dp.toPx()
            val resistor = Path()
            resistor.moveTo(zigStart, top)
            for (i in 1..segments) {
                val x = zigStart + segmentWidth * i
                val y = if (i % 2 == 1) top - amplitude else top + amplitude
                resistor.lineTo(x, y)
            }
            resistor.lineTo(zigStart + zigWidth, top)
            drawPath(resistor, lineColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

            // Battery symbol on the left edge (long + short plate).
            val midY = size.height / 2
            drawLine(lineColor, Offset(left - 12.dp.toPx(), midY - 9.dp.toPx()), Offset(left + 12.dp.toPx(), midY - 9.dp.toPx()), stroke, StrokeCap.Round)
            drawLine(lineColor, Offset(left - 7.dp.toPx(), midY + 9.dp.toPx()), Offset(left + 7.dp.toPx(), midY + 9.dp.toPx()), stroke, StrokeCap.Round)

            // Current arrow on the bottom edge, pointing clockwise.
            val arrowEnd = size.width * 0.78f
            drawLine(lineColor, Offset(size.width * 0.30f, bottom), Offset(arrowEnd, bottom), stroke, StrokeCap.Round)
            drawLine(lineColor, Offset(arrowEnd - 9.dp.toPx(), bottom - 7.dp.toPx()), Offset(arrowEnd, bottom), stroke, StrokeCap.Round)
            drawLine(lineColor, Offset(arrowEnd - 9.dp.toPx(), bottom + 7.dp.toPx()), Offset(arrowEnd, bottom), stroke, StrokeCap.Round)
        }

        Text(
            text = "R = " + quantityLabel(result?.resistance, "Ω", fmt),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(labelBackground)
                .padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = lineColor,
        )
        Text(
            text = "V = " + quantityLabel(result?.voltage, "V", fmt),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .background(labelBackground)
                .padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = lineColor,
        )
        Text(
            text = "I = " + quantityLabel(result?.current, "A", fmt),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(labelBackground)
                .padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = lineColor,
        )
        Text(
            text = "P = " + quantityLabel(result?.power, "W", fmt),
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun quantityLabel(value: BigDecimal?, unit: String, fmt: Fmt): String =
    if (value == null) "?" else fmt.num(value, precision = maxOf(fmt.precision, 8)) + " " + unit

private data class SiPrefix(val id: String, val shortLabel: String, val exponent: Int)

private val siPrefixes = listOf(
    SiPrefix("micro", "µ", -6),
    SiPrefix("milli", "m", -3),
    SiPrefix("none", "—", 0),
    SiPrefix("kilo", "k", 3),
    SiPrefix("mega", "M", 6),
)

private fun toSi(value: BigDecimal?, prefixId: String): BigDecimal? {
    val prefix = siPrefixes.firstOrNull { it.id == prefixId } ?: return null
    return value?.scaleByPowerOfTen(prefix.exponent)
}

private fun unitSymbol(quantity: Quantity): String = when (quantity) {
    Quantity.VOLTAGE -> "V"
    Quantity.CURRENT -> "A"
    Quantity.RESISTANCE -> "Ω"
    Quantity.POWER -> "W"
}

private fun valueOf(view: OhmsResultView, quantity: Quantity): BigDecimal = when (quantity) {
    Quantity.VOLTAGE -> view.voltage
    Quantity.CURRENT -> view.current
    Quantity.RESISTANCE -> view.resistance
    Quantity.POWER -> view.power
}

private fun derivedQuantities(q1: Quantity, q2: Quantity): List<Quantity> =
    Quantity.entries.filter { it != q1 && it != q2 }
