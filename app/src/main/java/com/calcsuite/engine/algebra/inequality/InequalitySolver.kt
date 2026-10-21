package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.CriticalRoot
import com.calcsuite.core.formatNumber
import com.calcsuite.core.PolynomialRoots
import com.calcsuite.core.Rat

import kotlin.math.abs

/** How the input should be read. */
enum class InequalityMode(val label: String, val hint: String) {
    SINGLE("Single", "One inequality in one variable, for example 2x + 3 < 11"),
    COMPOUND("Compound", "One chain, for example -2 < 3x + 1 <= 10"),
    SYSTEM("System", "Several inequalities, one in each field, all of which must hold"),
    // The four mode chips share the row width equally, so a label has to fit in
    // a quarter of it. "Two variables" does not, and was clipped to "Two"; the
    // full description is in the hint line underneath.
    TWO_VARIABLE("Two vars", "Linear inequalities in x and y, for example y > 2x - 1"),
}

/** One row of the sign chart. */
data class SignRow(
    val interval: String,
    val sample: String,
    val sign: String,
    val accepted: Boolean,
    val note: String?,
)

/** A point that divides the number line. */
data class CriticalPoint(
    val value: Double,
    val display: String,
    val isExact: Boolean,
    val isPole: Boolean,
)

/** The outcome of solving an inequality. */
sealed interface InequalityResult {

    data class Solved(
        val solution: SolutionSet,
        val steps: List<String>,
        val principle: String,
        val domainNote: String?,
        val variable: String,
        val kind: String,
        val criticalPoints: List<CriticalPoint>,
        val signChart: List<SignRow>,
        val approximate: Boolean,
    ) : InequalityResult

    /** A two-variable linear system, solved as a set of half-planes. */
    data class Plane(val system: PlaneSystem) : InequalityResult

    data class Failed(val message: String) : InequalityResult
}

/** The result of solving a system of linear inequalities in two variables. */
data class PlaneSystem(
    val halfPlanes: List<HalfPlane>,
    /** The feasible region, as a polygon. Empty when the system has no solution. */
    val region: List<PlanePoint>,
    val steps: List<String>,
    val principle: String,
    val variableX: String,
    val variableY: String,
)

/**
 * Solves inequalities and systems of inequalities.
 *
 * The pipeline for a single relation is:
 *
 * 1. Move everything to one side, so the relation becomes `f(x) REL 0`.
 * 2. Expand an absolute value into its equivalent cases, which is what makes
 *    `|f| ≤ a` solvable by the same machinery as everything else.
 * 3. Turn `f` into a rational function of the variable when that is possible.
 * 4. Find the critical points: the zeros of the numerator and the poles of the
 *    denominator.
 * 5. Test the sign of `f` on each interval between critical points.
 * 6. Read off which intervals satisfy the relation, and whether each critical
 *    point itself is included.
 *
 * Steps 3 to 6 are exact when the expression is a rational function. Anything
 * else — a logarithm, a square root, an exponential — falls back to a numeric
 * scan, and those results are always labelled as approximations.
 */
object InequalitySolver {

    fun solve(input: String, mode: InequalityMode = InequalityMode.SINGLE): InequalityResult {
        if (input.isBlank()) return InequalityResult.Failed("Enter an inequality to solve")
        val parsed = try {
            InequalityParser.parse(input, expectedVariables = if (mode == InequalityMode.TWO_VARIABLE) 2 else 1)
        } catch (e: InequalityParseException) {
            return InequalityResult.Failed(e.message ?: "The inequality could not be read")
        }

        if (mode == InequalityMode.TWO_VARIABLE || parsed.variables.size == 2) {
            return LinearSystem2D.solve(parsed.relation, parsed.variables)
        }

        // The chosen mode has to match what was actually typed, so a mistake is
        // reported instead of being solved in a way the user did not expect.
        val mismatch = when (mode) {
            InequalityMode.SINGLE ->
                if (parsed.relationCount != 1) {
                    "This mode expects a single inequality. Use the Compound or System mode for more."
                } else null
            InequalityMode.COMPOUND ->
                if (parsed.relationCount != 2) {
                    "This mode expects a chain of two comparisons, for example -2 < 3x + 1 <= 10."
                } else null
            InequalityMode.SYSTEM ->
                if (parsed.chainCount < 2) {
                    "This mode expects several inequalities, one in each field, all of which must hold."
                } else null
            InequalityMode.TWO_VARIABLE -> null
        }
        if (mismatch != null) return InequalityResult.Failed(mismatch)

        val expanded = try {
            expandAbs(parsed.relation)
        } catch (e: InequalityParseException) {
            return InequalityResult.Failed(e.message ?: "The inequality could not be read")
        }

        val outcome = solveRelation(expanded, parsed.variable)
        if (outcome is Outcome.Failure) return InequalityResult.Failed(outcome.message)

        val success = outcome as Outcome.Success
        val solution = success.solution
        val opening = if (containsAbsoluteValue(parsed.relation)) {
            listOf(
                "The expression contains an absolute value, so it is split into the cases that " +
                    "an absolute value creates, and the answer is where they overlap.",
            )
        } else {
            emptyList()
        }
        val steps = opening + success.steps + listOf(
            if (solution.isEmpty) {
                "No value satisfies every condition, so the solution set is empty."
            } else {
                "The solution set is ${solution.toIntervalNotation()}."
            },
        )

        return InequalityResult.Solved(
            solution = solution,
            steps = steps,
            principle = success.principle,
            domainNote = success.domainNote,
            variable = parsed.variable,
            kind = success.kind,
            criticalPoints = success.criticalPoints.distinctBy { it.value },
            signChart = success.signChart,
            approximate = success.approximate,
        )
    }

    // ------------------------------------------------------------- outcomes

    private sealed interface Outcome {
        data class Success(
            val solution: SolutionSet,
            val steps: List<String>,
            val signChart: List<SignRow>,
            val criticalPoints: List<CriticalPoint>,
            val kind: String,
            val principle: String,
            val domainNote: String?,
            val approximate: Boolean,
        ) : Outcome

        data class Failure(val message: String) : Outcome
    }

    /** Solves a relation tree, combining "and" by intersection and "or" by union. */
    private fun solveRelation(relation: Rel, variable: String): Outcome {
        return when (relation) {
            is Rel.Simple -> solveSimple(relation, variable)
            is Rel.And -> combine(relation.parts, variable, "and")
            is Rel.Or -> combine(relation.parts, variable, "or")
        }
    }

    private fun combine(parts: List<Rel>, variable: String, connective: String): Outcome {
        val solved = mutableListOf<Outcome.Success>()
        for (part in parts) {
            when (val outcome = solveRelation(part, variable)) {
                is Outcome.Failure -> return outcome
                is Outcome.Success -> solved.add(outcome)
            }
        }
        if (solved.isEmpty()) return Outcome.Failure("Enter at least one inequality")

        var intervals = solved.first().solution.intervals
        for (index in 1 until solved.size) {
            val next = solved[index].solution.intervals
            intervals = if (connective == "or") unionOf(intervals, next) else intersectOf(intervals, next)
        }

        val steps = mutableListOf<String>()
        val signChart = mutableListOf<SignRow>()
        val criticalPoints = mutableListOf<CriticalPoint>()
        for (index in solved.indices) {
            if (index > 0) {
                steps.add(
                    if (connective == "or") {
                        "Take the union with the previous set: a value qualifies if either part is satisfied."
                    } else {
                        "Intersect with the previous set: a value qualifies only if every part is satisfied."
                    },
                )
            }
            steps.addAll(solved[index].steps)
            signChart.addAll(solved[index].signChart)
            criticalPoints.addAll(solved[index].criticalPoints)
        }

        return Outcome.Success(
            solution = SolutionSet(intervals),
            steps = steps,
            signChart = signChart,
            criticalPoints = criticalPoints,
            kind = solved.first().kind,
            principle = solved.first().principle,
            domainNote = solved.firstNotNullOfOrNull { it.domainNote },
            approximate = solved.any { it.approximate },
        )
    }

    // ------------------------------------------------------ absolute values

    /**
     * Replaces `|g| REL a` with the equivalent relations on `g`.
     *
     * The cases come from the fact that `|g| < a` is the same as
     * `-a < g < a`, while `|g| > a` is the same as `g < -a` or `g > a`. The
     * sign of `a` matters: an absolute value is never negative, so `|g| < -1`
     * has no solution at all and `|g| > -1` is true everywhere.
     */
    private fun expandAbs(relation: Rel): Rel = when (relation) {
        is Rel.Simple -> expandAbsSimple(relation)
        is Rel.And -> Rel.And(relation.parts.map { expandAbs(it) })
        is Rel.Or -> Rel.Or(relation.parts.map { expandAbs(it) })
    }

    private fun expandAbsSimple(relation: Rel.Simple): Rel {
        val lhsAbs = (relation.lhs as? IExpr.Call)?.takeIf { it.name == "abs" && it.args.size == 1 }
        val rhsAbs = (relation.rhs as? IExpr.Call)?.takeIf { it.name == "abs" && it.args.size == 1 }
        val lhsNumber = relation.lhs as? IExpr.Num
        val rhsNumber = relation.rhs as? IExpr.Num

        if (lhsAbs != null && rhsNumber != null) {
            return absCases(lhsAbs.args[0], rhsNumber.value, relation.op)
        }
        if (rhsAbs != null && lhsNumber != null) {
            return absCases(rhsAbs.args[0], lhsNumber.value, flip(relation.op))
        }
        return relation
    }

    private fun flip(op: CompareOp): CompareOp = when (op) {
        CompareOp.LT -> CompareOp.GT
        CompareOp.GT -> CompareOp.LT
        CompareOp.LE -> CompareOp.GE
        CompareOp.GE -> CompareOp.LE
        CompareOp.EQ -> CompareOp.EQ
    }

    private fun absCases(inner: IExpr, threshold: Rat, op: CompareOp): Rel {
        val a = IExpr.Num(threshold)
        val minusA = IExpr.Num(threshold * Rat.of(-1L))
        return when (op) {
            CompareOp.LT, CompareOp.LE -> {
                if (threshold.signum() < 0) {
                    // An absolute value is never negative.
                    impossible()
                } else if (threshold.signum() == 0 && op == CompareOp.LT) {
                    impossible()
                } else {
                    // The absolute value has to be removed before the sign chart
                    // can be built, and removing it creates two cases.
                    Rel.And(
                        listOf(
                            // -a < g  is  g > -a, and -a <= g  is  g >= -a.
                            Rel.Simple(inner, flip(op), minusA),
                            Rel.Simple(inner, op, a),
                        ),
                    )
                }
            }
            CompareOp.GT, CompareOp.GE -> {
                if (threshold.signum() < 0) {
                    // Always true: an absolute value is never below zero.
                    everything()
                } else if (threshold.signum() == 0 && op == CompareOp.GE) {
                    everything()
                } else {
                    Rel.Or(
                        listOf(
                            Rel.Simple(inner, CompareOp.LT, minusA),
                            Rel.Simple(inner, CompareOp.GT, a),
                        ),
                    )
                }
            }
            CompareOp.EQ -> {
                if (threshold.signum() < 0) {
                    impossible()
                } else {
                    Rel.And(
                        listOf(
                            Rel.Simple(inner, CompareOp.EQ, a),
                            Rel.Simple(inner, CompareOp.EQ, minusA),
                        ),
                    )
                }
            }
        }
    }

    /** A relation that no value can satisfy. */
    private fun impossible(): Rel =
        Rel.Simple(IExpr.Num(Rat.ONE), CompareOp.LT, IExpr.Num(Rat.ZERO))

    /** A relation that every value satisfies. */
    private fun everything(): Rel =
        Rel.Simple(IExpr.Num(Rat.ONE), CompareOp.LE, IExpr.Num(Rat.ZERO))

    // ------------------------------------------------------ solving one part

    private fun solveSimple(relation: Rel.Simple, variable: String): Outcome {
        val difference = IExpr.Sub(relation.lhs, relation.rhs)
        val rational = difference.toRationalFunction(variable)
        return if (rational != null) {
            solveRational(relation, difference, rational, variable)
        } else {
            solveNumeric(relation, difference, variable)
        }
    }

    /**
     * A quadratic written the way a person would write it.
     *
     * The coefficient of one is left out and the signs are written as a
     * minus, so the expression reads `x^2 - 4` rather than `1x^2 + -4`.
     */
    private fun quadraticText(variable: String, a: Rat, b: Rat, c: Rat): String {
        val leading = when {
            a.isOne() -> "$variable^2"
            a.toDouble() == -1.0 -> "\u2212$variable^2"
            else -> "${a.display()}$variable^2"
        }
        val middle = when {
            b.isZero() -> ""
            b.toDouble() > 0.0 -> " + ${b.display()}$variable"
            else -> " \u2212 ${(-b).display()}$variable"
        }
        val last = when {
            c.isZero() -> ""
            c.toDouble() > 0.0 -> " + ${c.display()}"
            else -> " \u2212 ${(-c).display()}"
        }
        return leading + middle + last
    }

    private fun solveRational(
        relation: Rel.Simple,
        difference: IExpr,
        rational: RatFun,
        variable: String,
    ): Outcome {
        val numeratorRoots = PolynomialRoots.realRoots(rational.num)
        val denominatorRoots = PolynomialRoots.realRoots(rational.den)
        val isRational = rational.den.degree >= 1
        val kind = describeKind(rational, isRational)

        val steps = mutableListOf<String>()
        steps.add("This is a $kind in $variable.")

        val rhsIsZero = relation.rhs is IExpr.Num && (relation.rhs as IExpr.Num).value.isZero()
        if (!rhsIsZero) {
            steps.add(
                "Move everything to one side: f($variable) = ${IExprPrinter.print(relation.lhs)} − " +
                    "${IExprPrinter.print(relation.rhs)} = ${IExprPrinter.print(difference)}.",
            )
        } else {
            steps.add("The inequality is already of the form f($variable) ${relation.op.symbol} 0.")
        }

        // A quadratic is worked out with its discriminant, so that the
        // student can see why there are two roots, one or none.
        val numerator = rational.num
        if (numerator.degree == 2 && rational.den.degree == 0) {
            val a = numerator.coefficient(2)
            val b = numerator.coefficient(1)
            val c = numerator.coefficient(0)
            steps.add(
                "f($variable) is the quadratic ${quadraticText(variable, a, b, c)}, " +
                    "so a = ${a.display()}, b = ${b.display()} and c = ${c.display()}.",
            )
            val delta = b * b - Rat.of(4) * a * c
            steps.add(
                "Its discriminant is Δ = b² − 4ac = (${b.display()})² − 4·(${a.display()})·(${c.display()}) " +
                    "= ${delta.display()}.",
            )
            when {
                delta.isZero() -> {
                    steps.add(
                        "Δ = 0, so the quadratic touches the axis at one point: it has one " +
                            "repeated root, x = −b ÷ 2a = ${b.display()} ÷ ${a.display()}.",
                    )
                    steps.add(
                        "The sign does not change at a repeated root: the quadratic keeps the " +
                            "sign of its leading coefficient on both sides of it.",
                    )
                }
                delta.toDouble() > 0.0 -> {
                    steps.add(
                        "Δ > 0, so the quadratic crosses the axis twice: it has two distinct " +
                            "real roots, x = (−b ± √Δ) ÷ 2a.",
                    )
                    steps.add(
                        "The sign changes at each of them, and because the leading coefficient " +
                            "is ${a.display()}, the quadratic is " +
                            (if (a.toDouble() > 0.0) "positive" else "negative") +
                            " outside the two roots and " +
                            (if (a.toDouble() > 0.0) "negative" else "positive") +
                            " between them.",
                    )
                }
                else -> {
                    steps.add(
                        "Δ < 0, so the quadratic never crosses the axis: it has no real roots.",
                    )
                    steps.add(
                        "The sign is the sign of the leading coefficient everywhere, so the " +
                            "quadratic is ${if (a.toDouble() > 0.0) "positive" else "negative"} " +
                            "for every value of $variable.",
                    )
                }
            }
        }

        val poles = denominatorRoots.map { it.value }.toSet()
        val allRoots = mergeRoots(numeratorRoots, denominatorRoots)

        if (allRoots.isEmpty()) {
            val sample = Rat.ZERO
            val sign = rational.signAt(sample) ?: 0
            val accepted = satisfies(sign, relation.op)
            steps.add("f($variable) has no zeros and no poles, so its sign is the same everywhere.")
            steps.add(
                "Testing $variable = 0 gives f = ${formatNumber(rational.evaluate(sample)?.toDouble() ?: 0.0)}, " +
                    (if (accepted) "which satisfies the inequality." else "which does not satisfy the inequality."),
            )
            steps.add(
                if (accepted) {
                    "So every value of $variable is a solution."
                } else {
                    "So no value of $variable is a solution."
                },
            )
            return Outcome.Success(
                solution = if (accepted) SolutionSet.EVERYTHING else SolutionSet.EMPTY,
                steps = steps,
                signChart = listOf(
                    SignRow("(−∞, ∞)", "0", signText(sign), accepted, null),
                ),
                criticalPoints = emptyList(),
                kind = kind,
                principle = principleFor(kind),
                domainNote = null,
                approximate = false,
            )
        }

        steps.add("Find where f($variable) = 0.")
        if (numeratorRoots.isEmpty()) {
            steps.add("There are no real zeros, so f never passes through zero.")
        } else {
            val exactCount = numeratorRoots.count { it.isExact }
            val described = numeratorRoots.joinToString(", ") { it.display }
            steps.add(
                if (exactCount == numeratorRoots.size) {
                    "The zeros are $described."
                } else {
                    "The zeros are $described (${numeratorRoots.size - exactCount} of them found numerically)."
                },
            )
        }
        if (denominatorRoots.isNotEmpty()) {
            steps.add(
                "The denominator vanishes at " + denominatorRoots.joinToString(", ") { it.display } +
                    ", so those points are not in the domain and are always excluded.",
            )
        }

        val signChart = mutableListOf<SignRow>()
        val segments = mutableListOf<Segment>()
        var previous: Bound? = null
        for (root in allRoots) {
            val bound = Bound(root.value, root.display, root.isExact)
            val open = signInOpenInterval(rational, previous, bound, relation.op)
            signChart.add(
                SignRow(
                    interval = openIntervalText(previous, bound),
                    sample = open.sample,
                    sign = open.signText,
                    accepted = open.accepted,
                    note = if (root.value in poles) "the denominator is zero here, so this point is excluded" else null,
                ),
            )
            if (open.accepted) segments.add(Segment.open(previous, bound))
            val isPole = root.value in poles
            if (!isPole && relation.op.acceptsEquality) segments.add(Segment.point(bound))
            previous = bound
        }
        val last = signInOpenInterval(rational, previous, null, relation.op)
        signChart.add(
            SignRow(
                interval = openIntervalText(previous, null),
                sample = last.sample,
                sign = last.signText,
                accepted = last.accepted,
                note = null,
            ),
        )
        if (last.accepted) segments.add(Segment.open(previous, null))

        steps.add("The sign of f($variable) on each interval decides the answer.")
        for (row in signChart) {
            steps.add(
                "On ${row.interval}, f = ${row.sample} → ${row.sign} — " +
                    (if (row.accepted) "satisfies the inequality." else "does not satisfy the inequality."),
            )
        }
        for (root in allRoots) {
            if (root.value in poles) {
                steps.add("At $variable = ${root.display} the expression is undefined, so it is excluded.")
            } else if (relation.op.acceptsEquality) {
                steps.add("At $variable = ${root.display}, f = 0, so the point is included.")
            } else {
                steps.add("At $variable = ${root.display}, f = 0, so the point is excluded by the strict comparison.")
            }
        }

        val domainNote = if (denominatorRoots.isNotEmpty()) {
            "The domain excludes " + denominatorRoots.joinToString(", ") { it.display } +
                ", where the denominator is zero."
        } else {
            null
        }

        return Outcome.Success(
            solution = SolutionSet(mergeSegments(segments)),
            steps = steps,
            signChart = signChart,
            criticalPoints = allRoots.map {
                CriticalPoint(it.value, it.display, it.isExact, it.value in poles)
            },
            kind = kind,
            principle = principleFor(kind),
            domainNote = domainNote,
            approximate = numeratorRoots.any { !it.isExact },
        )
    }

    // ------------------------------------------------- the numeric fallback

    /**
     * Solves an inequality whose expression is not a rational function, by
     * scanning the number line.
     *
     * This path is inherently approximate: a scan can miss two roots that sit
     * closer together than the scan step. The result is always labelled as
     * approximate and the limitation is stated in the explanation.
     */
    private fun solveNumeric(
        relation: Rel.Simple,
        difference: IExpr,
        variable: String,
    ): Outcome {
        val steps = mutableListOf<String>()
        steps.add(
            "This inequality uses a function that cannot be rearranged into a polynomial, " +
                "so it is solved by scanning the number line.",
        )
        steps.add("Move everything to one side: f($variable) = ${IExprPrinter.print(difference)}.")

        val scan = NumericScan.scan(difference, variable)
        if (scan.roots.isEmpty() && scan.poles.isEmpty()) {
            val sample = scan.sampleBetween(null, null) ?: 0.0
            val value = NumericEval.eval(difference, mapOf(variable to sample))
            if (value.isNaN()) {
                return Outcome.Failure(
                    "The expression could not be evaluated anywhere on the number line, so there is " +
                        "nothing to solve. Check that the function and its arguments are written correctly.",
                )
            }
        }

        val kind = "non-polynomial inequality"
        val signChart = mutableListOf<SignRow>()
        val segments = mutableListOf<Segment>()

        steps.add(
            "Scanning for where f changes sign found " +
                (if (scan.roots.isEmpty()) "no zeros" else "${scan.roots.size} zero(s)") +
                (if (scan.poles.isEmpty()) "" else " and ${scan.poles.size} point(s) where f is undefined") + ".",
        )
        steps.add(
            "A scan can miss zeros that fall between two sample points, so the critical points below " +
                "are approximate.",
        )

        val points = (scan.roots + scan.poles).sortedBy { it.value }
        var previous: Bound? = null
        for (point in points) {
            val bound = Bound(point.value, point.display, false)
            val open = scan.signIn(previous?.value, bound.value, relation.op)
            signChart.add(
                SignRow(
                    interval = openIntervalText(previous, bound),
                    sample = formatNumber(open.third),
                    sign = signText(open.first),
                    accepted = open.second,
                    note = if (point.isPole) "the expression is undefined here, so this point is excluded" else null,
                ),
            )
            if (open.second) segments.add(Segment.open(previous, bound))
            if (!point.isPole && relation.op.acceptsEquality) segments.add(Segment.point(bound))
            previous = bound
        }
        val last = scan.signIn(previous?.value, null, relation.op)
        signChart.add(
            SignRow(
                interval = openIntervalText(previous, null),
                sample = formatNumber(last.third),
                sign = signText(last.first),
                accepted = last.second,
                note = null,
            ),
        )
        if (last.second) segments.add(Segment.open(previous, null))

        for (row in signChart) {
            steps.add(
                "On ${row.interval}, f = ${row.sample} → ${row.sign} — " +
                    (if (row.accepted) "satisfies the inequality." else "does not satisfy the inequality."),
            )
        }
        steps.add("These results come from a numerical scan, so the critical points are approximate.")

        return Outcome.Success(
            solution = SolutionSet(mergeSegments(segments)),
            steps = steps,
            signChart = signChart,
            criticalPoints = points.map { CriticalPoint(it.value, it.display, false, it.isPole) },
            kind = kind,
            principle = principleFor(kind),
            domainNote = "The domain was found by scanning, so it is approximate.",
            approximate = true,
        )
    }

    // -------------------------------------------------------------- helpers

    private fun satisfies(sign: Int, op: CompareOp): Boolean = when (op) {
        CompareOp.LT -> sign < 0
        CompareOp.GT -> sign > 0
        CompareOp.LE -> sign <= 0
        CompareOp.GE -> sign >= 0
        CompareOp.EQ -> sign == 0
    }

    private fun signText(sign: Int): String = when {
        sign > 0 -> "positive"
        sign < 0 -> "negative"
        else -> "zero"
    }

    private class OpenResult(val signText: String, val accepted: Boolean, val sample: String)

    private fun signInOpenInterval(
        rational: RatFun,
        left: Bound?,
        right: Bound?,
        op: CompareOp,
    ): OpenResult {
        val sample = sampleRat(rational, left, right)
        val sign = rational.signAt(sample) ?: 0
        val value = rational.evaluate(sample)?.toDouble() ?: 0.0
        return OpenResult(signText(sign), satisfies(sign, op), formatNumber(value))
    }

    private fun sampleRat(rational: RatFun, left: Bound?, right: Bound?): Rat {
        val lo = left?.let { boundToRat(it) }
        val hi = right?.let { boundToRat(it) }
        if (lo != null && hi != null) return (lo + hi) / Rat.of(2L)
        if (lo == null && hi == null) return Rat.ZERO
        if (lo == null) {
            val anchor = hi!!
            return anchor - maxOf(Rat.ONE, absOf(anchor))
        }
        val anchor = lo
        return anchor + maxOf(Rat.ONE, absOf(anchor))
    }

    /** The exact value of a bound when it has one, otherwise a close rational. */
    private fun boundToRat(bound: Bound): Rat {
        if (bound.isExact) {
            val parsed = bound.display.toRationalOrNull()
            if (parsed != null) return parsed
        }
        return Rat.approximate(bound.value)
    }

    private fun absOf(r: Rat): Rat = if (r.signum() < 0) -r else r

    private fun openIntervalText(left: Bound?, right: Bound?): String {
        val lo = left?.display ?: "−∞"
        val hi = right?.display ?: "∞"
        return "($lo, $hi)"
    }

    private fun describeKind(rational: RatFun, isRational: Boolean): String {
        val degree = rational.num.degree
        return when {
            isRational -> "rational inequality"
            degree <= 0 -> "constant inequality"
            degree == 1 -> "linear inequality"
            degree == 2 -> "quadratic inequality"
            degree == 3 -> "cubic inequality"
            degree == 4 -> "quartic inequality"
            else -> "polynomial inequality of degree $degree"
        }
    }

    private fun principleFor(kind: String): String = when {
        kind.startsWith("rational") ->
            "A rational expression changes sign only where its numerator is zero or its denominator is zero. " +
                "Those points split the number line into intervals, and on each interval the sign is fixed. " +
                "Testing one point per interval therefore settles the whole inequality."
        kind.startsWith("linear") ->
            "A linear expression changes sign exactly once, at its single zero. That zero splits the number " +
                "line into two intervals with opposite signs, so testing one point on each side settles it."
        kind.startsWith("quadratic") || kind.startsWith("cubic") || kind.startsWith("quartic") ||
            kind.startsWith("polynomial") ->
            "A polynomial changes sign only at its real zeros. Those zeros split the number line into " +
                "intervals, and because the sign is continuous it cannot change inside an interval — so " +
                "testing one point per interval settles the whole inequality. A zero is included only when " +
                "the comparison allows equality."
        kind.startsWith("constant") ->
            "The expression does not depend on the variable, so either every value satisfies the inequality " +
                "or none does."
        else ->
            "The expression is not a polynomial, so it is solved by scanning the number line for the points " +
                "where it is zero or undefined and testing the sign between them. The result is approximate."
    }
}


/** True when the relation contains an absolute value. */
private fun containsAbsoluteValue(relation: Rel): Boolean = when (relation) {
    is Rel.Simple -> containsAbsoluteValue(relation.lhs) || containsAbsoluteValue(relation.rhs)
    is Rel.And -> relation.parts.any { containsAbsoluteValue(it) }
    is Rel.Or -> relation.parts.any { containsAbsoluteValue(it) }
}

private fun containsAbsoluteValue(expr: IExpr): Boolean = when (expr) {
    is IExpr.Call -> expr.name == "abs" || expr.args.any { containsAbsoluteValue(it) }
    is IExpr.Neg -> containsAbsoluteValue(expr.a)
    is IExpr.Add -> containsAbsoluteValue(expr.a) || containsAbsoluteValue(expr.b)
    is IExpr.Sub -> containsAbsoluteValue(expr.a) || containsAbsoluteValue(expr.b)
    is IExpr.Mul -> containsAbsoluteValue(expr.a) || containsAbsoluteValue(expr.b)
    is IExpr.Div -> containsAbsoluteValue(expr.a) || containsAbsoluteValue(expr.b)
    is IExpr.Pow -> containsAbsoluteValue(expr.a) || containsAbsoluteValue(expr.b)
    is IExpr.Num, is IExpr.Var -> false
}

/**
 * Merges the numerator's zeros with the denominator's poles into one list of
 * critical points.
 *
 * A point can be both — `(x^2 - 4) / (x - 2)` has 2 as a zero and as a pole —
 * and it must appear only once, otherwise the sign chart would contain a
 * zero-width interval and the point would be reported twice.
 */
private fun mergeRoots(
    numeratorRoots: List<CriticalRoot>,
    denominatorRoots: List<CriticalRoot>,
): List<CriticalRoot> {
    val merged = mutableListOf<CriticalRoot>()
    for (root in (numeratorRoots + denominatorRoots).sortedBy { it.value }) {
        val previous = merged.lastOrNull()
        if (previous != null && kotlin.math.abs(previous.value - root.value) < 1e-9) continue
        merged.add(root)
    }
    return merged
}

/** Parses a plain rational such as `3` or `-1/2`. */
private fun String.toRationalOrNull(): Rat? {
    // The displayed form uses the true minus sign, which BigInteger will not
    // accept, so it is normalised back to a hyphen first.
    val trimmed = trim().replace("\u2212", "-")
    if (trimmed.isEmpty()) return null
    return try {
        val slash = trimmed.indexOf('/')
        if (slash < 0) {
            Rat.of(java.math.BigInteger(trimmed))
        } else {
            val numerator = java.math.BigInteger(trimmed.substring(0, slash).trim())
            val denominator = java.math.BigInteger(trimmed.substring(slash + 1).trim())
            if (denominator.signum() == 0) null else Rat.of(numerator, denominator)
        }
    } catch (_: NumberFormatException) {
        null
    }
}
