package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.algebra.GcfLcmEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import com.calcsuite.ui.tools.prefill

@Composable
fun GcfLcmToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var input by rememberSaveable { mutableStateOf(args.prefill("input") ?: "18, 24") }

    val parsed = GcfLcmEngine.parseIntegers(input)
    val values = (parsed as? CalcResult.Ok)?.value
    val parseError = (parsed as? CalcResult.Err)?.error?.message

    val report = remember(fmt, input) {
        values?.let { GcfLcmEngine.analyze(it).valueOrNull() }
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        actions = {
            TextButton(onClick = { input = "18, 24" }) { Text("Reset") }
        },
    ) {
        PlainField(
            label = "Numbers",
            value = input,
            onValueChange = { input = it },
            placeholder = "18, 24, 36",
            error = parseError,
            singleLine = false,
            monospace = true,
        )
        InfoNote("Separate whole numbers with commas, semicolons or new lines. Negatives are used as their absolute values.")

        if (report != null) {
            ResultCard(
                label = "Greatest common factor of ${report.values.size} number(s)",
                value = report.gcf.toString(),
                caption = if (report.lcm.signum() == 0) "LCM is 0 (a zero is present)" else null,
                onCopy = { copy(it, "GCF") },
                copyValue = report.gcf.toString(),
                onSave = {
                    args.onRecord.record(
                        "GCF & LCM of ${report.values.joinToString(", ")}",
                        "GCF = ${report.gcf}, LCM = ${report.lcm}",
                    )
                },
            )

            Column(Modifier.fillMaxWidth()) {
                ResultRow("Least common multiple", report.lcm.toString(), emphasis = true)
                ResultRow("Count", report.values.size.toString())
                ResultRow("Values", report.values.joinToString(", "))
            }

            if (report.factorizations.isNotEmpty()) {
                SectionCard(title = "Prime factorizations") {
                    report.factorizations.forEach { factorization ->
                        ResultRow(
                            factorization.n.toString(),
                            factorization.factors.joinToString(" × ") { factor ->
                                if (factor.exponent == 1) factor.prime.toString()
                                else "${factor.prime}^${factor.exponent}"
                            },
                        )
                    }
                }
            }

            if (report.steps.isNotEmpty()) {
                StepsCard(report.steps)
            }

            report.notes.forEach { InfoNote(it) }
        } else if (parseError != null) {
            ResultCard(label = "Results", value = null, error = parseError)
        }

        FormulaCard(
            formula = "gcd(a, b) via Euclid;  lcm(a, b) = |a × b| ÷ gcd(a, b)",
            explanation = "Euclid's algorithm replaces the larger number by the remainder until the remainder is zero. " +
                "The LCM of a set is built by folding the pairwise formula.",
            variables = listOf(
                "gcd" to "greatest common factor (divisor)",
                "lcm" to "least common multiple; lcm(x, 0) = 0",
            ),
        )
        InfoNote("Exact integer arithmetic (BigInteger): results stay exact for arbitrarily large inputs.")
    }
}
