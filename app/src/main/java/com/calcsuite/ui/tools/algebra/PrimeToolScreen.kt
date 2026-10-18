package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.algebra.Factorization
import com.calcsuite.engine.algebra.PrimeEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.theme.NumberTextStyle
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigInteger

/** Numbers above this many digits are not factorized live (trial/rho time). */
private const val LIVE_FACTOR_DIGIT_LIMIT = 20

@Composable
fun PrimeToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var input by rememberSaveable { mutableStateOf("97") }
    val n = Num.parseIntOrNull(input)

    val check = if (n != null) remember(input) { PrimeEngine.isPrime(n) } else null
    val factorization: CalcResult<Factorization>? = if (n != null && n >= BigInteger.valueOf(2L) &&
        n.toString().length <= LIVE_FACTOR_DIGIT_LIMIT
    ) {
        remember(input) { PrimeEngine.factorizeDetailed(n) }
    } else {
        null
    }
    val nextPrime = if (n != null) remember(input) { PrimeEngine.nextPrime(n).valueOrNull() } else null
    val previousPrime = if (n != null) remember(input) { PrimeEngine.previousPrime(n) } else null

    val verdict: String? = when {
        n == null -> null
        check == null -> null
        check.isPrime -> "Prime"
        n.signum() < 0 -> "Negative numbers are not prime"
        n.signum() == 0 -> "Not prime (0)"
        n == BigInteger.ONE -> "Neither prime nor composite"
        else -> "Composite"
    }
    val verdictCaption: String? = when {
        check == null -> null
        !check.isPrime -> "Proven by finding a divisor or a Miller-Rabin witness"
        check.proven -> "Proven prime"
        else -> "Strong probable prime (40 fixed Miller-Rabin bases, no known counterexample)"
    }
    val inputError: String? = when {
        input.isBlank() -> "Enter a whole number"
        n == null -> "Enter a valid whole number"
        else -> null
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        actions = {
            TextButton(onClick = { input = "97" }) { Text("Reset") }
        },
    ) {
        NumberField(
            label = "Number",
            value = input,
            onValueChange = { input = it },
            placeholder = "97",
            error = inputError,
        )
        InfoNote("Whole numbers of any size are accepted; very large integers are tested with Miller-Rabin.")

        if (check != null) {
            ResultCard(
                label = "Verdict",
                value = verdict,
                caption = verdictCaption,
                onCopy = { copy(it, "Verdict") },
                copyValue = verdict,
                onSave = verdict?.let { text ->
                    { args.onRecord("primality of ${check.n}", text) }
                },
            )

            Column(Modifier.fillMaxWidth()) {
                ResultRow("Number", check.n.toString())
                ResultRow("Digits", check.n.abs().toString().length.toString())
                nextPrime?.let { ResultRow("Next prime", it.toString()) }
                ResultRow(
                    "Previous prime",
                    previousPrime?.toString() ?: "none (no prime is smaller than 2)",
                )
            }

            if (check.n >= BigInteger.valueOf(2L)) {
                when (factorization) {
                    is CalcResult.Ok -> {
                        val value = factorization.value
                        SectionCard(title = "Prime factorization") {
                            if (value.factors.isEmpty()) {
                                Text(
                                    "1 has no prime factors",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            value.factors.forEach { factor ->
                                ResultRow(
                                    "Factor",
                                    if (factor.exponent == 1) factor.prime.toString()
                                    else "${factor.prime}^${factor.exponent}",
                                )
                            }
                            Text(
                                if (value.factors.isEmpty()) "1 (no prime factors)"
                                else value.factors.joinToString(" × ") { factor ->
                                    if (factor.exponent == 1) factor.prime.toString()
                                    else "${factor.prime}^${factor.exponent}"
                                },
                                style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                    is CalcResult.Err -> InfoNote("Factorization: ${factorization.error.message}")
                    null -> if (n != null && n.toString().length > LIVE_FACTOR_DIGIT_LIMIT) {
                        InfoNote("Factorization is skipped for numbers over $LIVE_FACTOR_DIGIT_LIMIT digits so the screen stays responsive")
                    }
                }
            }

            val steps = buildList {
                when {
                    check.n < BigInteger.valueOf(2L) ->
                        add("A prime is an integer greater than 1 with exactly two divisors")
                    check.isPrime && check.proven ->
                        add("No prime up to 1000 divides the number, and the deterministic Miller-Rabin bases found no witness")
                    check.isPrime ->
                        add("40 fixed-base Miller-Rabin rounds found no witness — a strong probable prime")
                    else -> {
                        add("Finding one divisor other than 1 and the number itself proves that the number is composite")
                        (factorization as? CalcResult.Ok)?.value?.factors?.firstOrNull()?.let { factor ->
                            add("Divisor found: ${factor.prime}")
                        }
                    }
                }
            }
            if (steps.isNotEmpty()) StepsCard(steps)

            InfoNote(check.explanation)
            InfoNote("Select any long number to copy it; the result box also has a copy button.")
        } else if (inputError != null) {
            ResultCard(label = "Verdict", value = null, error = inputError)
        }

        FormulaCard(
            formula = "n is prime ⇔ it has exactly two divisors: 1 and n",
            explanation = "Small factors are found by trial division; Miller-Rabin then proves primality below " +
                "3.3 × 10^24 and gives a very strong probable-prime answer above it.",
            variables = listOf(
                "Miller-Rabin" to "tests witnesses a^d mod n to rule composites out",
                "proven" to "true when the test is exact for the given size",
            ),
        )
        SelectionContainer {
            Text(
                "Factorization uses trial division by primes up to 1000 followed by Pollard's rho with exact checks; " +
                    "the product of the reported factors is verified against the input.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
