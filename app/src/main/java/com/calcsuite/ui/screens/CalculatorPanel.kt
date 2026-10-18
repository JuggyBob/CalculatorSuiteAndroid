package com.calcsuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.expr.AngleMode
import com.calcsuite.expr.ExpressionEngine
import com.calcsuite.ui.components.LocalSettings
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.theme.NumberTextStyle
import java.math.BigDecimal

private enum class KeyKind { Digit, Operator, Function, Equals, Utility }

private data class CalcKey(
    val label: String,
    val insert: String = "",
    val kind: KeyKind = KeyKind.Digit,
    val action: String? = null,
)

/**
 * The standard / scientific calculator used by the main screen (standard pad
 * with an expandable scientific pad) and by the dedicated scientific screen.
 *
 * Behaviour notes:
 * - Expressions are evaluated by [ExpressionEngine] (decimal arithmetic).
 * - `%` follows the everyday convention `200 + 10% = 220`.
 * - After "=" the next digit/function starts a new expression from the result.
 *
 * @param onRecord receives (expression, formatted result) on every "=".
 */
@Composable
fun CalculatorPanel(
    modifier: Modifier = Modifier,
    scientific: Boolean = false,
    onToggleScientific: (() -> Unit)? = null,
    onCycleAngleMode: (() -> Unit)? = null,
    onRecord: (String, String) -> Unit = { _, _ -> },
    onOpenHistory: (() -> Unit)? = null,
) {
    val fmt = rememberFmt()
    val settings = LocalSettings.current
    val haptic = LocalHapticFeedback.current
    val copy = rememberCopyAction()

    var expr by rememberSaveable { mutableStateOf("") }
    var committed by rememberSaveable { mutableStateOf<String?>(null) }
    var committedCopy by rememberSaveable { mutableStateOf<String?>(null) }
    var justEvaluated by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var second by rememberSaveable { mutableStateOf(false) }
    var memory by rememberSaveable { mutableStateOf("0") }

    fun tap() {
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun append(text: String) {
        expr = if (justEvaluated && committedCopy != null) committedCopy + text else expr + text
        justEvaluated = false
        committed = null
        errorText = null
    }

    fun clearAll() {
        expr = ""
        committed = null
        committedCopy = null
        justEvaluated = false
        errorText = null
    }

    fun evaluate() {
        when (val result = ExpressionEngine.evaluate(expr, settings.angleMode)) {
            is CalcResult.Ok -> {
                val rendered = fmt.num(result.value.value, precision = maxOf(fmt.precision, 10))
                committed = (if (result.value.exact) "" else "≈ ") + rendered
                committedCopy = Num.formatForCopy(result.value.value)
                justEvaluated = true
                errorText = null
                onRecord(expr, rendered)
            }
            is CalcResult.Err -> {
                committed = null
                committedCopy = null
                errorText = result.error.message
                justEvaluated = true
            }
        }
    }

    fun toggleSign() {
        val trimmed = expr.trimEnd()
        if (trimmed.isEmpty()) {
            append("−")
            return
        }
        val match = Regex("(\\d+\\.?\\d*|\\d*\\.?\\d+)$").find(trimmed)
        if (match == null) {
            append("−")
            return
        }
        val before = trimmed.substring(0, match.range.first)
        val number = match.value
        expr = if (before.endsWith("−")) before.dropLast(1) + number else before + "−" + number
        justEvaluated = false
        committed = null
    }

    fun handleKey(key: CalcKey) {
        tap()
        when (key.action) {
            "clear" -> clearAll()
            "backspace" -> {
                if (justEvaluated) clearAll()
                else expr = expr.dropLast(1)
                errorText = null
            }
            "sign" -> toggleSign()
            "equals" -> if (expr.isNotBlank()) evaluate()
            "second" -> second = !second
            "decimal" -> {
                val tail = Regex("[0-9.]*$").find(expr)?.value ?: ""
                if (!tail.contains('.')) append(".")
            }
            else -> append(key.insert)
        }
    }

    val livePreview: String? = remember(
        expr,
        settings.angleMode,
        settings.precision,
        settings.grouping,
        settings.thousandsSeparator,
        committed,
    ) {
        if (expr.isBlank() || committed != null) {
            null
        } else {
            when (val r = ExpressionEngine.evaluate(expr, settings.angleMode)) {
                is CalcResult.Ok -> (if (r.value.exact) "" else "≈ ") +
                    fmt.num(r.value.value, precision = maxOf(fmt.precision, 10))
                is CalcResult.Err -> null
            }
        }
    }

    val scientificRows: List<List<CalcKey>> = listOf(
        listOf(
            CalcKey(if (second) "sin⁻¹" else "sin", insert = if (second) "asin(" else "sin(", kind = KeyKind.Function),
            CalcKey(if (second) "cos⁻¹" else "cos", insert = if (second) "acos(" else "cos(", kind = KeyKind.Function),
            CalcKey(if (second) "tan⁻¹" else "tan", insert = if (second) "atan(" else "tan(", kind = KeyKind.Function),
            CalcKey("π", insert = "π", kind = KeyKind.Function),
            CalcKey("e", insert = "e", kind = KeyKind.Function),
        ),
        listOf(
            CalcKey(if (second) "x³" else "x²", insert = if (second) "^3" else "^2", kind = KeyKind.Function),
            CalcKey("xʸ", insert = "^", kind = KeyKind.Function),
            CalcKey(if (second) "eˣ" else "ln", insert = if (second) "exp(" else "ln(", kind = KeyKind.Function),
            CalcKey(if (second) "10ˣ" else "log", insert = if (second) "10^(" else "log(", kind = KeyKind.Function),
            CalcKey(if (second) "∛" else "√", insert = if (second) "cbrt(" else "sqrt(", kind = KeyKind.Function),
        ),
        listOf(
            CalcKey("x⁻¹", insert = "^(-1)", kind = KeyKind.Function),
            CalcKey("(", insert = "(", kind = KeyKind.Function),
            CalcKey(")", insert = ")", kind = KeyKind.Function),
            CalcKey("n!", insert = "!", kind = KeyKind.Function),
            CalcKey("mod", insert = " mod ", kind = KeyKind.Function),
        ),
    )

    val basicRows: List<List<CalcKey>> = listOf(
        listOf(
            CalcKey("C", kind = KeyKind.Utility, action = "clear"),
            CalcKey("⌫", kind = KeyKind.Utility, action = "backspace"),
            CalcKey("%", insert = "%", kind = KeyKind.Utility),
            CalcKey("÷", insert = "÷", kind = KeyKind.Operator),
        ),
        listOf(CalcKey("7", "7"), CalcKey("8", "8"), CalcKey("9", "9"), CalcKey("×", "×", KeyKind.Operator)),
        listOf(CalcKey("4", "4"), CalcKey("5", "5"), CalcKey("6", "6"), CalcKey("−", "−", KeyKind.Operator)),
        listOf(CalcKey("1", "1"), CalcKey("2", "2"), CalcKey("3", "3"), CalcKey("+", "+", KeyKind.Operator)),
        listOf(
            CalcKey("±", kind = KeyKind.Utility, action = "sign"),
            CalcKey("0", "0"),
            CalcKey(fmt.decimalSeparator.toString(), ".", KeyKind.Digit, action = "decimal"),
            CalcKey("=", kind = KeyKind.Equals, action = "equals"),
        ),
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    text = when (settings.angleMode) {
                        AngleMode.DEG -> "DEG"
                        AngleMode.RAD -> "RAD"
                        AngleMode.GRAD -> "GRAD"
                    },
                    onClick = { tap(); onCycleAngleMode?.invoke() },
                )
                Spacer(Modifier.width(6.dp))
                StatusChip(
                    text = "fx",
                    selected = scientific,
                    onClick = { tap(); onToggleScientific?.invoke() },
                    icon = true,
                )
                Spacer(Modifier.weight(1f))
                if (memory != "0") {
                    Text(
                        "M $memory",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = { onOpenHistory?.invoke() },
                    modifier = Modifier.size(32.dp),
                    enabled = onOpenHistory != null,
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = {
                        tap()
                        val toCopy = committedCopy ?: expr
                        if (toCopy.isNotBlank()) copy(toCopy, "Calculation")
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = committedCopy != null || expr.isNotBlank(),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                BasicTextField(
                    value = expr,
                    onValueChange = {
                        expr = it
                        committed = null
                        committedCopy = null
                        justEvaluated = false
                        errorText = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp),
                    textStyle = NumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    maxLines = 3,
                    decorationBox = { inner ->
                        if (expr.isEmpty()) {
                            Text(
                                "0",
                                style = NumberTextStyle.copy(
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        } else {
                            inner()
                        }
                    },
                )
                val display = committed ?: livePreview
                Text(
                    text = display ?: "",
                    style = NumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (committed != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorText != null) {
                    Text(
                        errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Absorbs the spare height when the panel is expanded, so the keypad
            // stays anchored at the bottom while the display keeps its natural
            // size (a weight here would collapse it in wrapped mode).
            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MemoryKey("MC", Modifier.weight(1f)) { tap(); memory = "0" }
                MemoryKey("MR", Modifier.weight(1f)) { tap(); if (memory != "0") append(memory) }
                MemoryKey("M+", Modifier.weight(1f)) {
                    tap()
                    evaluateQuiet(expr, settings.angleMode)?.let {
                        memory = (Num.parseOrNull(memory) ?: BigDecimal.ZERO).add(it).stripTrailingZeros().toPlainString()
                    }
                }
                MemoryKey("M−", Modifier.weight(1f)) {
                    tap()
                    evaluateQuiet(expr, settings.angleMode)?.let {
                        memory = (Num.parseOrNull(memory) ?: BigDecimal.ZERO).subtract(it).stripTrailingZeros().toPlainString()
                    }
                }
            }

            if (scientific) {
                scientificRows.forEach { row -> CalcRow(row, ::handleKey) }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
                    KeyButton(
                        CalcKey(if (second) "1st" else "2nd", kind = KeyKind.Utility, action = "second"),
                        Modifier.weight(2f),
                        selected = second,
                        onClick = ::handleKey,
                    )
                    KeyButton(CalcKey("(", insert = "(", kind = KeyKind.Function), Modifier.weight(1f), onClick = ::handleKey)
                    KeyButton(CalcKey(")", insert = ")", kind = KeyKind.Function), Modifier.weight(1f), onClick = ::handleKey)
                }
            }
            basicRows.forEach { row -> CalcRow(row, ::handleKey) }
        }
    }
}

private fun evaluateQuiet(expr: String, angle: AngleMode): BigDecimal? {
    if (expr.isBlank()) return null
    return when (val r = ExpressionEngine.evaluate(expr, angle)) {
        is CalcResult.Ok -> r.value.value
        is CalcResult.Err -> null
    }
}

@Composable
private fun CalcRow(row: List<CalcKey>, onKey: (CalcKey) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        row.forEach { key -> KeyButton(key, Modifier.weight(1f), onClick = onKey) }
    }
}

@Composable
private fun KeyButton(
    key: CalcKey,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (CalcKey) -> Unit,
) {
    val bg = when (key.kind) {
        KeyKind.Digit -> MaterialTheme.colorScheme.surfaceContainerHighest
        KeyKind.Operator -> MaterialTheme.colorScheme.secondaryContainer
        KeyKind.Function -> MaterialTheme.colorScheme.surfaceContainerHigh
        KeyKind.Equals -> MaterialTheme.colorScheme.primary
        KeyKind.Utility -> MaterialTheme.colorScheme.surfaceContainer
    }
    val fg = when (key.kind) {
        KeyKind.Equals -> MaterialTheme.colorScheme.onPrimary
        KeyKind.Operator -> MaterialTheme.colorScheme.onSecondaryContainer
        KeyKind.Function -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.heightIn(min = if (key.kind == KeyKind.Function) 38.dp else 50.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else bg,
        onClick = { onClick(key) },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (key.label == "⌫") {
                Icon(
                    Icons.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = fg,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    key.label,
                    style = NumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = if (key.kind == KeyKind.Equals) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MemoryKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 32.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon) {
                Icon(
                    Icons.Filled.Functions,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
