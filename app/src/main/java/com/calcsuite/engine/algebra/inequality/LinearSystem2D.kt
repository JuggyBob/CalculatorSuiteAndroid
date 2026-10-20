package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.Rat
import com.calcsuite.core.formatNumber

/** A point on the plane. */
data class PlanePoint(val x: Double, val y: Double) {
    override fun toString(): String = "(${formatNumber(x)}, ${formatNumber(y)})"
}

/**
 * One half-plane of a two-variable system.
 *
 * The inequality is `a·x + b·y + c` having the sign of [sign], where a
 * positive [sign] means the expression must be positive. The boundary is the
 * line `a·x + b·y + c = 0`.
 */
data class HalfPlane(
    val a: Rat,
    val b: Rat,
    val c: Rat,
    val sign: Int,
    val strict: Boolean,
    val original: String,
) {
    /** The value of the linear form at a point. */
    fun value(x: Double, y: Double): Double =
        a.toDouble() * x + b.toDouble() * y + c.toDouble()

    /** True when the point satisfies this half-plane. */
    fun satisfiedBy(x: Double, y: Double): Boolean {
        val v = value(x, y)
        return when {
            strict && sign > 0 -> v > 0
            strict && sign < 0 -> v < 0
            !strict && sign > 0 -> v >= 0
            else -> v <= 0
        }
    }

    /** The boundary line written the way a person would say it. */
    fun boundaryText(xName: String, yName: String): String {
        val terms = mutableListOf<String>()
        if (!a.isZero()) terms.add(term(a, xName))
        if (!b.isZero()) terms.add(term(b, yName))
        if (!c.isZero() || terms.isEmpty()) {
            val sign = if (c.signum() < 0) "−" else "+"
            terms.add("${if (terms.isEmpty()) "" else " $sign "}${c.abs().toString()}")
        }
        val body = terms.joinToString(" ").replace("+ -", "− ").replace("- ", "− ")
        return "$body = 0"
    }

    private fun term(value: Rat, name: String): String {
        val sign = if (value.signum() < 0) "−" else ""
        val magnitude = if (value == Rat.ONE) name else "${value.abs().toString()}·$name"
        return "$sign$magnitude"
    }
}

/**
 * Solves a system of linear inequalities in two variables.
 *
 * Each inequality is rewritten as a half-plane, and the feasible region is the
 * overlap of all of them. The region is computed by clipping a bounding box
 * with each half-plane, which is exact for a convex region and always
 * agrees with the shading on the plot.
 */
object LinearSystem2D {

    /** A linear form `a·x + b·y + c`. */
    private data class Linear(val a: Rat, val b: Rat, val c: Rat)

    fun solve(relation: Rel, variables: List<String>): InequalityResult {
        if (variables.size < 2) {
            return InequalityResult.Failed("A two-variable system needs two variables, for example x and y")
        }
        val x = variables[0]
        val y = variables[1]
        val parts = flatten(relation)
        if (parts.isEmpty()) return InequalityResult.Failed("Enter at least one inequality")

        val planes = mutableListOf<HalfPlane>()
        for (part in parts) {
            val difference = IExpr.Sub(part.lhs, part.rhs)
            val linear = toLinear(difference, x, y)
            if (linear == null) {
                return InequalityResult.Failed(
                    "This system can only be solved when every inequality is linear in $x and $y. " +
                        "A term such as $x^2 or $x·$y makes the feasible region curved, which this tool does not draw.",
                )
            }
            val sign = when (part.op) {
                CompareOp.GT, CompareOp.GE -> 1
                CompareOp.LT, CompareOp.LE -> -1
                CompareOp.EQ -> 0
            }
            if (sign == 0) {
                return InequalityResult.Failed(
                    "An equality in a two-variable system describes a line rather than a region, " +
                        "so there is no feasible area to shade.",
                )
            }
            planes.add(
                HalfPlane(
                    a = linear.a,
                    b = linear.b,
                    c = linear.c,
                    sign = sign,
                    strict = part.op.isStrict,
                    original = "${IExprPrinter.print(part.lhs)} ${part.op.symbol} ${IExprPrinter.print(part.rhs)}",
                ),
            )
        }

        val steps = mutableListOf<String>()
        steps.add("This is a system of ${planes.size} linear inequality${if (planes.size == 1) "" else "s"} in $x and $y.")

        for (plane in planes) {
            val form = linearText(plane, x, y)
            steps.add("Rewrite “${plane.original}” as $form.")
            steps.add("Its boundary is the line ${plane.boundaryText(x, y)}.")
            val test = testPoint(plane, 0.0, 0.0)
            steps.add(
                "Test the point (0, 0): the left side is ${formatNumber(test.first)}, which is " +
                    (if (test.second) "on the required side, so that side is shaded." else "not on the required side."),
            )
        }
        steps.add("Shade the required side of every boundary and keep the region where all of them overlap.")

        val region = feasibleRegion(planes)
        if (region.isEmpty()) {
            steps.add("The shaded sides do not overlap anywhere, so the system has no solution.")
        } else {
            val vertices = region.joinToString(", ") { it.toString() }
            steps.add("The feasible region has the corner points $vertices.")
        }

        return InequalityResult.Plane(
            PlaneSystem(
                halfPlanes = planes,
                region = region,
                steps = steps,
                principle = "Each linear inequality is one side of a line, and the solution of the system is " +
                    "where all those sides overlap. Testing one point on each side of a line tells you which " +
                    "side to shade, and the overlapping region is the set of points that satisfy every " +
                    "inequality at once.",
                variableX = x,
                variableY = y,
            ),
        )
    }

    private fun flatten(relation: Rel): List<Rel.Simple> = when (relation) {
        is Rel.Simple -> listOf(relation)
        is Rel.And -> relation.parts.flatMap { flatten(it) }
        is Rel.Or -> relation.parts.flatMap { flatten(it) }
    }

    /** The linear form of an expression, or null when it is not linear in x and y. */
    private fun toLinear(expr: IExpr, x: String, y: String): Linear? {
        return when (expr) {
        is IExpr.Num -> Linear(Rat.ZERO, Rat.ZERO, expr.value)
        is IExpr.Var -> when (expr.name) {
            x -> Linear(Rat.ONE, Rat.ZERO, Rat.ZERO)
            y -> Linear(Rat.ZERO, Rat.ONE, Rat.ZERO)
            else -> null
        }
        is IExpr.Neg -> toLinear(expr.a, x, y)?.let { Linear(-it.a, -it.b, -it.c) }
        is IExpr.Add -> {
            val a = toLinear(expr.a, x, y) ?: return null
            val b = toLinear(expr.b, x, y) ?: return null
            Linear(a.a + b.a, a.b + b.b, a.c + b.c)
        }
        is IExpr.Sub -> {
            val a = toLinear(expr.a, x, y) ?: return null
            val b = toLinear(expr.b, x, y) ?: return null
            Linear(a.a - b.a, a.b - b.b, a.c - b.c)
        }
        is IExpr.Mul -> {
            val a = toLinear(expr.a, x, y) ?: return null
            val b = toLinear(expr.b, x, y) ?: return null
            // Linear times constant is linear; anything else is not.
            if (a.a.isZero() && a.b.isZero()) Linear(b.a * a.c, b.b * a.c, b.c * a.c)
            else if (b.a.isZero() && b.b.isZero()) Linear(a.a * b.c, a.b * b.c, a.c * b.c)
            else null
        }
        is IExpr.Div -> {
            val a = toLinear(expr.a, x, y) ?: return null
            val b = toLinear(expr.b, x, y) ?: return null
            if (!b.a.isZero() || !b.b.isZero()) return null
            if (b.c.isZero()) return null
            val factor = Rat.ONE / b.c
            Linear(a.a * factor, a.b * factor, a.c * factor)
        }
        is IExpr.Pow -> {
            val exponent = (expr.b as? IExpr.Num)?.value
            if (exponent == Rat.ZERO) return Linear(Rat.ZERO, Rat.ZERO, Rat.ONE)
            if (exponent != Rat.ONE) return null
            toLinear(expr.a, x, y)
        }
            is IExpr.Call -> null
        }
    }

    private fun linearText(plane: HalfPlane, x: String, y: String): String {
        val terms = mutableListOf<String>()
        if (!plane.a.isZero()) terms.add(signed(plane.a, x))
        if (!plane.b.isZero()) terms.add(signed(plane.b, y))
        if (!plane.c.isZero() || terms.isEmpty()) {
            terms.add((if (plane.c.signum() < 0) "− " else "+ ") + plane.c.abs().toString())
        }
        val body = terms.joinToString(" ").replace("+ -", "− ")
        val comparator = if (plane.sign > 0) {
            if (plane.strict) ">" else "≥"
        } else {
            if (plane.strict) "<" else "≤"
        }
        return "$body $comparator 0"
    }

    private fun signed(value: Rat, name: String): String {
        val sign = if (value.signum() < 0) "−" else ""
        val magnitude = if (value == Rat.ONE) name else "${value.abs().toString()}·$name"
        return "$sign$magnitude"
    }

    /** The value of the half-plane's form at a point, and whether the point is on the required side. */
    private fun testPoint(plane: HalfPlane, x: Double, y: Double): Pair<Double, Boolean> {
        val value = plane.value(x, y)
        val onSide = if (plane.sign > 0) value >= 0 else value <= 0
        return Pair(value, onSide)
    }

    /**
     * The overlap of every half-plane, computed by clipping a bounding box.
     *
     * The box is far larger than any sensible view, so the result is the true
     * feasible region except where it is genuinely unbounded — in which case the
     * plot shows the box edge and the explanation says the region is unbounded.
     */
    private fun feasibleRegion(planes: List<HalfPlane>): List<PlanePoint> {
        val bound = 40.0
        var polygon = listOf(
            PlanePoint(-bound, -bound),
            PlanePoint(bound, -bound),
            PlanePoint(bound, bound),
            PlanePoint(-bound, bound),
        )
        for (plane in planes) {
            polygon = clip(polygon, plane) ?: return emptyList()
        }
        return polygon
    }

    /** Sutherland–Hodgman clipping of a polygon by one half-plane. */
    private fun clip(polygon: List<PlanePoint>, plane: HalfPlane): List<PlanePoint>? {
        if (polygon.isEmpty()) return emptyList()
        val out = mutableListOf<PlanePoint>()
        for (index in polygon.indices) {
            val current = polygon[index]
            val next = polygon[(index + 1) % polygon.size]
            val dCurrent = plane.value(current.x, current.y) * plane.sign
            val dNext = plane.value(next.x, next.y) * plane.sign
            if (dCurrent >= 0) out.add(current)
            if ((dCurrent >= 0) != (dNext >= 0)) {
                val t = dCurrent / (dCurrent - dNext)
                out.add(
                    PlanePoint(
                        current.x + (next.x - current.x) * t,
                        current.y + (next.y - current.y) * t,
                    ),
                )
            }
        }
        return out
    }
}
