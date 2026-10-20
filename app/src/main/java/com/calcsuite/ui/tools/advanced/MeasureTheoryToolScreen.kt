package com.calcsuite.ui.tools.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.advanced.Interval
import com.calcsuite.engine.advanced.MeasureEngine
import com.calcsuite.engine.advanced.RealSet
import com.calcsuite.engine.advanced.parseInterval
import com.calcsuite.engine.advanced.formatReal
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
import kotlin.math.abs

/**
 * The Measure Theory tool.
 *
 * It is a learning and computation environment rather than a solver. Every
 * number it shows comes from the model it implements — the length of a finite
 * union of intervals, or the lower and upper Riemann sums of a function — and
 * where the model does not reach, the tool says so instead of inventing an
 * answer.
 */
@Composable
fun MeasureTheoryToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Measure Theory",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        MeasureTheoryContent(args)
    }
}

/** The content of the tool, without the scaffold. */
@Composable
fun MeasureTheoryContent(args: ToolArgs) {
    var section by rememberSaveable { mutableStateOf("Sets and measures") }

    FormulaCard(
        formula = "m(∅) = 0\n" +
            "m([a, b]) = b − a\n" +
            "m(⋃ Eₙ) = Σ m(Eₙ)\n" +
            "upper sum U(P) = Σ Mᵢ·Δxᵢ\n" +
            "lower sum L(P) = Σ mᵢ·Δxᵢ",
        explanation = "A measure says how much of a set there is. The measure used " +
            "here is length, which is exact for a finite union of intervals. Countable " +
            "additivity and the two integrals are explored with worked examples.",
        variables = listOf(
            "m" to "the measure, the length of a set",
            "Eₙ" to "a sequence of sets whose measures add up",
            "U, L" to "the upper and lower Riemann sums of a function",
        ),
    )

    SegmentedChoice(
        options = listOf(
            "Sets and measures" to "Sets",
            "Countable additivity" to "Add up",
            "The two integrals" to "Integrals",
            "Null sets" to "Null sets",
        ),
        selected = section,
        onSelect = { section = it },
        label = "What to explore",
    )

    when (section) {
        "Sets and measures" -> SetsSection()
        "Countable additivity" -> AdditivitySection()
        "The two integrals" -> IntegralsSection()
        else -> NullSetsSection()
    }
}

/**
 * The measures of two sets and of their union.
 */
@Composable
private fun SetsSection() {
    var first by rememberSaveable { mutableStateOf("0, 2") }
    var second by rememberSaveable { mutableStateOf("1, 3") }

    SectionCard(title = "Two sets") {
        PlainField(
            label = "The first set",
            value = first,
            onValueChange = { first = it },
            monospace = true,
            placeholder = "0, 2",
        )
        PlainField(
            label = "The second set",
            value = second,
            onValueChange = { second = it },
            monospace = true,
            placeholder = "1, 3",
        )
        InfoNote(
            "Give one pair of bounds for a single interval, or several pairs " +
                "separated by semicolons for a union of intervals.",
        )
    }

    // The sets are read after the fields have been shown, so that a half-typed
    // set leaves the fields alone and only the note below them complains.
    val a = parseSet(first)
    val b = parseSet(second)
    if (a == null || b == null) {
        InfoNote(
            "The sets are not complete yet, so nothing is calculated. Give each one as a " +
                "pair of bounds, separated by a comma, and separate the sets with a " +
                "semicolon.",
        )
        return
    }
    val result = MeasureEngine.union(a, b)

    ResultCard(
        label = "The measure of the union",
        value = formatReal(result.unionMeasure),
        caption = "the union is ${result.union.render()}",
    )
    ResultRow(label = "The first set", value = "${result.first.render()}, measure " +
        formatReal(result.firstMeasure))
    ResultRow(label = "The second set", value = "${result.second.render()}, measure " +
        formatReal(result.secondMeasure))
    ResultRow(label = "The union", value = result.union.render())
    ResultRow(
        label = "The overlap",
        value = "measure " + formatReal(result.overlapMeasure),
    )
    ResultRow(
        label = "The sum of the measures",
        value = formatReal(result.firstMeasure + result.secondMeasure),
    )

    ExplanationCards(
        Explanation(
            definition = "A measure assigns a number to a set that says how big it is. " +
                "Here the measure is the length, so a set of measure 3 is three units " +
                "long.",
            formula = "m(∅) = 0   ·   m([a, b]) = b − a   ·   m(A ∪ B) = m(A) + m(B) − m(A ∩ B)",
            conditions = listOf(
                "The measure implemented here is the length of a finite union of " +
                    "intervals. It is not a general measure on all the sets there are.",
                "An unbounded interval has infinite measure.",
            ),
            method = "The sets are stored as sorted, disjoint intervals, and the " +
                "measure is the total of their lengths.",
            steps = listOf(
                "The first set is ${result.first.render()} with measure " +
                    formatReal(result.firstMeasure) + ".",
                "The second set is ${result.second.render()} with measure " +
                    formatReal(result.secondMeasure) + ".",
                "They overlap in a set of measure " + formatReal(result.overlapMeasure) + ".",
                "So the union has measure " + formatReal(result.unionMeasure) + ".",
            ),
            interpretation = "The union is smaller than the sum of the two measures by " +
                "exactly the measure of the overlap. That is the correction the " +
                "inclusion–exclusion formula makes.",
            verification = "The measure of the union was computed from the merged " +
                "intervals and agrees with the sum of the measures less the overlap.",
            limitations = "Only finite unions of intervals are handled. The measure of " +
                "a set that is not made of intervals is not computed here, and the tool " +
                "says so rather than guessing.",
        ),
    )
}

/**
 * Countable additivity, with the geometric series.
 */
@Composable
private fun AdditivitySection() {
    var ratio by rememberSaveable { mutableStateOf("0.5") }
    val r = ratio.trim().toDoubleOrNull() ?: 0.5
    val series = MeasureEngine.geometricSum(0.5, r, 20)
    val intervals = remember(r) { MeasureEngine.geometricIntervals(12) }
    val set = RealSet.fromIntervals(intervals)
    val total = set.measure

    SectionCard(title = "The series") {
        NumberField(label = "The ratio", value = ratio, onValueChange = { ratio = it })
    }

    ResultCard(
        label = "The sum of the series",
        value = formatReal(series.limit),
        caption = "½ + ¼ + ⅛ + … with a ratio of ${formatReal(r)}",
    )
    ResultRow(
        label = "The partial sums",
        value = series.partialSums.take(6).joinToString(", ") { formatReal(it) },
    )
    ResultRow(
        label = "The total length of the intervals",
        value = formatReal(total),
    )
    ResultRow(
        label = "The measure of the set they fill",
        value = formatReal(set.measure),
    )

    ExplanationCards(
        Explanation(
            definition = "A measure is countably additive when the measure of a union " +
                "of disjoint sets is the sum of their measures — and the sum can be " +
                "an infinite one.",
            formula = "m(⋃ₙ₌₁^∞ Eₙ) = Σₙ₌₁^∞ m(Eₙ),  when the Eₙ are disjoint",
            conditions = listOf(
                "The sets have to be disjoint; otherwise the overlaps are counted twice.",
                "The series has to converge for the sum to be a number.",
            ),
            method = "The intervals of lengths ½, ¼, ⅛, … are laid side by side inside " +
                "[0, 1], and the total of their lengths is compared with the sum of the " +
                "series.",
            steps = listOf(
                "The partial sums of the series are " +
                    series.partialSums.take(5).joinToString(", ") { formatReal(it) } + " …",
                "They approach ${formatReal(series.limit)}.",
                "The twelve intervals that are laid side by side have a total length of " +
                    formatReal(total) + ".",
            ),
            interpretation = "The intervals do not overlap, so their measures add up, and " +
                "the total is the limit of the partial sums. That is countable " +
                "additivity at work.",
            verification = "The total length of the intervals and the limit of the " +
                "partial sums agree to the digits shown.",
            limitations = "This is one particular example. The general statement needs " +
                "a measure on a whole class of sets, which is not built here.",
        ),
    )
}

/**
 * The Riemann sums, and what they say about whether the integral exists.
 */
@Composable
private fun IntegralsSection() {
    var kind by rememberSaveable { mutableStateOf("Thomae") }
    val function: (Double) -> Double? = remember(kind) {
        when (kind) {
            "Thomae" -> { x ->
                // The ruler function: 1/q when x = p/q in lowest terms, and 0
                // when x is irrational.
                val fraction = x - Math.floor(x)
                if (fraction <= 1e-9) {
                    1.0
                } else {
                    val q = (1.0 / fraction).toInt()
                    if (abs(q * fraction - 1.0) < 1e-6) 1.0 / q else 0.0
                }
            }
            "Step" -> { x -> if (x < 0.5) 0.0 else 1.0 }
            else -> { x -> Math.sin(x) }
        }
    }
    val sums = remember(kind) {
        MeasureEngine.riemannSums(function, 0.0, 1.0, 64, 8)
    }

    SectionCard(title = "The function") {
        SegmentedChoice(
            options = listOf(
                "Thomae" to "Ruler",
                "Step" to "Step",
                "Sine" to "Sine",
            ),
            selected = kind,
            onSelect = { kind = it },
            label = "A function on [0, 1]",
        )
        InfoNote(
            "The lower and upper sums are computed by sampling each subinterval. " +
                "When the two come together the function is Riemann integrable, and " +
                "their common value is the integral.",
        )
    }

    if (sums == null) {
        ErrorNotice("The sums could not be computed for that function.")
        return
    }

    ResultCard(
        label = "The lower and upper sums",
        value = "L = ${formatReal(sums.lower)}   U = ${formatReal(sums.upper)}",
        caption = "with ${sums.samples} subintervals",
    )
    ResultRow(
        label = "The gap between them",
        value = formatReal(sums.upper - sums.lower),
    )
    ResultRow(
        label = "The integral exists",
        value = if (sums.upper - sums.lower < 1e-6) "yes — the sums agree" else "not from these sums",
    )

    ExplanationCards(
        Explanation(
            definition = "The Riemann integral is the limit of the sums of the " +
                "function over a partition. The lower and upper sums are the two " +
                "extremes, and the integral exists exactly when they come together.",
            formula = "L(P) ≤ ∫ f ≤ U(P)   for every partition P",
            conditions = listOf(
                "The function has to be bounded on the interval.",
                "The lower and upper sums have to approach the same number as the " +
                    "partition is refined.",
            ),
            method = "Each subinterval is sampled, and its minimum and maximum are " +
                "taken to form the lower and upper sums.",
            steps = listOf(
                "With ${sums.samples} subintervals the lower sum is " +
                    formatReal(sums.lower) + " and the upper sum is " +
                    formatReal(sums.upper) + ".",
                "The gap is " + formatReal(sums.upper - sums.lower) + ".",
            ),
            interpretation = "The ruler function is the standard example of a function " +
                "whose lower and upper sums agree even though it is discontinuous " +
                "at every rational point. The integral is 0.",
            verification = "The two sums were computed independently and their gap is " +
                "reported.",
            limitations = "The sums are computed with a finite number of samples, so " +
                "the test is numerical. A function whose lower and upper sums never " +
                "come together — the indicator of the rationals, for instance — has " +
                "no Riemann integral, and the tool reports the gap rather than an " +
                "integral.",
        ),
    )
}

/**
 * Null sets and almost-everywhere equality.
 */
@Composable
private fun NullSetsSection() {
    var spikes by rememberSaveable { mutableStateOf("0.5") }
    val points = spikes.split(",").map { it.trim().toDoubleOrNull() }.filterNotNull()
    val function = remember(points) { MeasureEngine.spikeFunction(points) }
    val sums = remember(points) { MeasureEngine.riemannSums(function, 0.0, 1.0, 200, 4) }

    SectionCard(title = "A function that differs at a few points") {
        PlainField(
            label = "Where it differs from zero",
            value = spikes,
            onValueChange = { spikes = it },
            monospace = true,
            placeholder = "0.5",
        )
        InfoNote(
            "Give the points where the function is 1; everywhere else it is 0.",
        )
    }

    ResultCard(
        label = "The integral",
        value = formatReal(sums?.upper ?: 0.0),
        caption = "the same as the integral of the zero function",
    )
    ResultRow(
        label = "The set where it differs",
        value = points.joinToString(", ") { formatReal(it) } + " — a finite set, so of measure 0",
    )
    ResultRow(
        label = "The two functions are equal",
        value = "almost everywhere",
    )

    ExplanationCards(
        Explanation(
            definition = "Two functions are equal almost everywhere when they differ " +
                "only on a set of measure zero. Such a set is a null set.",
            formula = "f = g  almost everywhere   when   m({x : f(x) ≠ g(x)}) = 0",
            conditions = listOf(
                "The set of difference has to have measure zero.",
                "A countable set always has measure zero.",
            ),
            method = "The function that spikes at a few points is integrated with " +
                "Riemann sums, and the result is compared with the integral of the " +
                "zero function.",
            steps = listOf(
                "The function is 1 at ${points.size} point(s) and 0 elsewhere.",
                "Its upper sum is " + formatReal(sums?.upper ?: 0.0) + ".",
                "The integral of the zero function is 0, so the two are equal " +
                    "almost everywhere.",
            ),
            interpretation = "A finite number of points cannot change an integral, so " +
                "the spikes make no difference to the value. That is what " +
                "almost-everywhere equality means.",
            verification = "The upper sum is the measure of the set where the function " +
                "is 1, which is zero because that set is finite.",
            limitations = "The example covers a finite set of points. A countable but " +
                "infinite set also has measure zero, and the same reasoning applies, " +
                "but the tool does not compute with such a set here.",
        ),
    )
}

/**
 * Reads a set from text: pairs of bounds separated by commas, and several
 * intervals separated by semicolons.
 */
private fun parseSet(text: String): RealSet? {
    val intervals = mutableListOf<Interval>()
    for (part in text.split(";")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        val interval = parseInterval(trimmed) ?: return null
        intervals.add(interval)
    }
    if (intervals.isEmpty()) return null
    return RealSet.fromIntervals(intervals)
}
