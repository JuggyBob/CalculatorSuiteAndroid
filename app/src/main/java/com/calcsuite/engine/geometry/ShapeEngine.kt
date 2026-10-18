package com.calcsuite.engine.geometry

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext

/**
 * Plane-shape engine: typed inputs in, fully labelled results out.
 *
 * ## Label conventions used by [ShapeResult]
 * - The literal word `unit` in a label is a placeholder for the length unit the
 *   screen collects (e.g. `cm`); the UI replaces it, so `Perimeter (unit)`
 *   becomes `Perimeter (cm)`.
 * - `(°)` marks degree values, `(count)` marks dimensionless counts.
 * - `primary` is the shape's area in squared units.
 *
 * Irrational quantities (π, square roots) are computed with
 * [GeometryMath] at [com.calcsuite.core.Num.EVAL_CONTEXT] precision; the
 * engines note where a value is inherently an approximation.
 */
enum class ShapeKind {
    SQUARE, RECTANGLE, TRIANGLE, EQUILATERAL_TRIANGLE, PARALLELOGRAM,
    RHOMBUS, TRAPEZOID, CIRCLE, SEMICIRCLE, ELLIPSE, REGULAR_POLYGON,
}

/** How a triangle is specified. */
enum class TriangleMode { BASE_HEIGHT, THREE_SIDES, TWO_SIDES_INCLUDED_ANGLE }

/** How a circle is specified. */
enum class CircleMode { RADIUS, DIAMETER, CIRCUMFERENCE }

/** How a rhombus is specified. */
enum class RhombusMode { DIAGONALS, SIDE_ANGLE }

/** How a regular polygon is specified. */
enum class PolygonMode { SIDE, CIRCUMRADIUS }

/** One typed input of a shape. `id` keys the screen's input map. */
data class ShapeField(val id: String, val label: String, val optional: Boolean = false)

/** Result of a shape calculation. See the file KDoc for label conventions. */
data class ShapeResult(
    /** Area in squared units, when the inputs determine it. */
    val primary: BigDecimal? = null,
    /** Labelled secondary quantities; all are determined by the inputs. */
    val quantities: List<Pair<String, BigDecimal>>,
    /** Quantities the inputs cannot determine (never silently zero). */
    val unavailable: List<String>,
    /** Substituted formulas, one per calculated quantity. */
    val steps: List<String>,
    /** Non-fatal notes (irrationality, assumptions, precision). */
    val validation: List<String>,
)

object ShapeEngine {

    private val CTX: MathContext = GeometryMath.CTX
    private val TWO = BigDecimal("2")
    private val THREE = BigDecimal("3")
    private val FOUR = BigDecimal("4")
    private val ONE_EIGHTY = BigDecimal("180")
    private val THREE_SIXTY = BigDecimal("360")

    // ------------------------------------------------------------ source data

    /** The typed inputs of a shape in its default mode (UI field layout). */
    fun fields(kind: ShapeKind): List<ShapeField> = when (kind) {
        ShapeKind.SQUARE -> listOf(ShapeField("side", "Side s"))
        ShapeKind.RECTANGLE -> listOf(ShapeField("width", "Width w"), ShapeField("height", "Height h"))
        ShapeKind.TRIANGLE -> listOf(
            ShapeField("base", "Base b"), ShapeField("height", "Height h"),
            ShapeField("sideA", "Side a"), ShapeField("sideB", "Side b"), ShapeField("sideC", "Side c"),
            ShapeField("angle", "Included angle C"),
        )
        ShapeKind.EQUILATERAL_TRIANGLE -> listOf(ShapeField("side", "Side s"))
        ShapeKind.PARALLELOGRAM -> listOf(
            ShapeField("base", "Base b"), ShapeField("height", "Height h"),
            ShapeField("side", "Slanted side a", optional = true),
        )
        ShapeKind.RHOMBUS -> listOf(
            ShapeField("diagP", "Diagonal p"), ShapeField("diagQ", "Diagonal q"),
            ShapeField("side", "Side a"), ShapeField("angle", "Angle θ"),
        )
        ShapeKind.TRAPEZOID -> listOf(
            ShapeField("baseA", "Base a"), ShapeField("baseB", "Base b"), ShapeField("height", "Height h"),
            ShapeField("leg1", "Leg 1", optional = true), ShapeField("leg2", "Leg 2", optional = true),
        )
        ShapeKind.CIRCLE -> listOf(ShapeField("radius", "Radius r"))
        ShapeKind.SEMICIRCLE -> listOf(ShapeField("radius", "Radius r"))
        ShapeKind.ELLIPSE -> listOf(
            ShapeField("semiMajor", "Semi-axis a"), ShapeField("semiMinor", "Semi-axis b"),
        )
        ShapeKind.REGULAR_POLYGON -> listOf(
            ShapeField("sides", "Number of sides n"), ShapeField("side", "Side s"),
        )
    }

    // ---------------------------------------------------------------- square

    fun square(side: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(side, "Side")?.let { return it }
        val area = side.multiply(side, CTX)
        val perimeter = side.multiply(FOUR, CTX)
        val diagonal = side.multiply(GeometryMath.sqrt(TWO), CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Perimeter (unit)" to perimeter,
                    "Diagonal (unit)" to diagonal,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = s² = ${n(side)}² = ${n(area)}",
                    "Perimeter = 4s = 4 × ${n(side)} = ${n(perimeter)}",
                    "Diagonal = s√2 = ${n(side)} × √2 ≈ ${n(diagonal)}",
                ),
                validation = listOf("The diagonal s√2 is irrational; the displayed value is rounded."),
            ),
        )
    }

    // ------------------------------------------------------------- rectangle

    fun rectangle(width: BigDecimal, height: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(width, "Width")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        val area = width.multiply(height, CTX)
        val perimeter = width.add(height, CTX).multiply(TWO, CTX)
        val diagonal = GeometryMath.hypot(width, height)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Perimeter (unit)" to perimeter,
                    "Diagonal (unit)" to diagonal,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = w × h = ${n(width)} × ${n(height)} = ${n(area)}",
                    "Perimeter = 2(w + h) = 2 × (${n(width)} + ${n(height)}) = ${n(perimeter)}",
                    "Diagonal = √(w² + h²) = √(${n(width.multiply(width, CTX))} + " +
                        "${n(height.multiply(height, CTX))}) ≈ ${n(diagonal)}",
                ),
                validation = listOf("The diagonal √(w² + h²) is rational only for special widths and heights; the displayed value is rounded."),
            ),
        )
    }

    // -------------------------------------------------------------- triangle

    fun triangleBaseHeight(base: BigDecimal, height: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(base, "Base")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        val area = base.multiply(height, CTX).divide(TWO, CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = emptyList(),
                unavailable = listOf("Side lengths (unit)", "Perimeter (unit)", "Angles (°)"),
                steps = listOf(
                    "Area = ½ × base × height = ½ × ${n(base)} × ${n(height)} = ${n(area)}",
                ),
                validation = listOf(
                    "A base and a height fix the area only: infinitely many triangles share them, " +
                        "so the side lengths, perimeter and angles are not determined.",
                ),
            ),
        )
    }

    fun triangleThreeSides(a: BigDecimal, b: BigDecimal, c: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(a, "Side a")?.let { return it }
        requirePositive(b, "Side b")?.let { return it }
        requirePositive(c, "Side c")?.let { return it }
        val sumAB = a.add(b, CTX)
        val sumAC = a.add(c, CTX)
        val sumBC = b.add(c, CTX)
        val failure = when {
            sumAB.compareTo(c) <= 0 -> Triple(a, b, c)
            sumAC.compareTo(b) <= 0 -> Triple(a, c, b)
            sumBC.compareTo(a) <= 0 -> Triple(b, c, a)
            else -> null
        }
        if (failure != null) {
            val (x, y, z) = failure
            return err(
                ErrorKind.Domain,
                "Sides ${n(a)}, ${n(b)} and ${n(c)} do not form a triangle: " +
                    "${n(x)} + ${n(y)} = ${n(x.add(y, CTX))} is not greater than ${n(z)}",
            )
        }
        val perimeter = sumAB.add(c, CTX)
        val semi = perimeter.divide(TWO, CTX)
        val radicand = semi
            .multiply(semi.subtract(a, CTX), CTX)
            .multiply(semi.subtract(b, CTX), CTX)
            .multiply(semi.subtract(c, CTX), CTX)
        val area = GeometryMath.sqrt(radicand)
        val angleA = angleOpposite(a, b, c)
        val angleB = angleOpposite(b, a, c)
        val angleC = ONE_EIGHTY.subtract(angleA, CTX).subtract(angleB, CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Perimeter (unit)" to perimeter,
                    "Semi-perimeter (unit)" to semi,
                    "Angle A (opposite a) (°)" to angleA,
                    "Angle B (opposite b) (°)" to angleB,
                    "Angle C (opposite c) (°)" to angleC,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "s = (a + b + c)/2 = (${n(a)} + ${n(b)} + ${n(c)})/2 = ${n(semi)}",
                    "Area = √(s(s − a)(s − b)(s − c)) = " +
                        "√(${n(semi)} × ${n(semi.subtract(a, CTX))} × ${n(semi.subtract(b, CTX))} × " +
                        "${n(semi.subtract(c, CTX))}) = √${n(radicand)} = ${n(area)}",
                    "Perimeter = a + b + c = ${n(perimeter)}",
                    "A = acos((b² + c² − a²)/(2bc)) ≈ ${n(angleA)}°",
                    "B = acos((a² + c² − b²)/(2ac)) ≈ ${n(angleB)}°",
                    "C = 180° − A − B ≈ ${n(angleC)}°",
                ),
                validation = listOf(
                    "The law of cosines is evaluated with inverse trigonometry, so the angles are " +
                        "approximate to about 15 significant digits.",
                ),
            ),
        )
    }

    fun triangleTwoSidesAngle(
        a: BigDecimal,
        b: BigDecimal,
        angleDeg: BigDecimal,
    ): CalcResult<ShapeResult> {
        requirePositive(a, "Side a")?.let { return it }
        requirePositive(b, "Side b")?.let { return it }
        if (angleDeg.signum() <= 0 || angleDeg.compareTo(ONE_EIGHTY) >= 0) {
            return err(ErrorKind.Domain, "Included angle must be greater than 0° and less than 180°")
        }
        val sinC = GeometryMath.sinDegrees(angleDeg)
        val cosC = GeometryMath.cosDegrees(angleDeg)
        val area = a.multiply(b, CTX).multiply(sinC, CTX).divide(TWO, CTX)
        // c² = (a − b)² + 2ab(1 − cos C) avoids the cancellation of the textbook form.
        val cSquared = a.subtract(b, CTX).multiply(a.subtract(b, CTX), CTX)
            .add(a.multiply(b, CTX).multiply(TWO, CTX).multiply(BigDecimal.ONE.subtract(cosC, CTX), CTX), CTX)
        if (cSquared.signum() < 0) {
            return err(ErrorKind.Domain, "The included angle is too close to 180° for these sides")
        }
        val c = GeometryMath.sqrt(cSquared)
        val perimeter = a.add(b, CTX).add(c, CTX)
        val semi = perimeter.divide(TWO, CTX)
        val angleA = angleOpposite(a, b, c)
        val angleB = ONE_EIGHTY.subtract(angleA, CTX).subtract(angleDeg, CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Third side c (unit)" to c,
                    "Perimeter (unit)" to perimeter,
                    "Semi-perimeter (unit)" to semi,
                    "Angle A (opposite a) (°)" to angleA,
                    "Angle B (opposite b) (°)" to angleB,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = ½ab·sin C = ½ × ${n(a)} × ${n(b)} × sin ${n(angleDeg)}° = ${n(area)}",
                    "c = √(a² + b² − 2ab·cos C) = √(${n(a.multiply(a, CTX))} + ${n(b.multiply(b, CTX))} − " +
                        "2 × ${n(a)} × ${n(b)} × cos ${n(angleDeg)}°) ≈ ${n(c)}",
                    "Perimeter = a + b + c ≈ ${n(perimeter)}, so s ≈ ${n(semi)}",
                    "A = acos((b² + c² − a²)/(2bc)) ≈ ${n(angleA)}°",
                    "B = 180° − A − C ≈ ${n(angleB)}°",
                ),
                validation = listOf(
                    "C = ${n(angleDeg)}° is the angle between sides a and b.",
                    "The third side follows from the law of cosines; the angles are approximate to " +
                        "about 15 significant digits.",
                ),
            ),
        )
    }

    // ----------------------------------------------------- equilateral side

    fun equilateralTriangle(side: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(side, "Side")?.let { return it }
        val sqrt3 = GeometryMath.sqrt(THREE)
        val area = sqrt3.multiply(side.multiply(side, CTX), CTX).divide(FOUR, CTX)
        val perimeter = side.multiply(THREE, CTX)
        val height = sqrt3.multiply(side, CTX).divide(TWO, CTX)
        val inradius = sqrt3.multiply(side, CTX).divide(BigDecimal("6"), CTX)
        val circumradius = sqrt3.multiply(side, CTX).divide(THREE, CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Perimeter (unit)" to perimeter,
                    "Height (unit)" to height,
                    "Inradius (unit)" to inradius,
                    "Circumradius (unit)" to circumradius,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = (√3/4)s² = (√3/4) × ${n(side)}² ≈ ${n(area)}",
                    "Perimeter = 3s = 3 × ${n(side)} = ${n(perimeter)}",
                    "Height = (√3/2)s = (√3/2) × ${n(side)} ≈ ${n(height)}",
                    "Inradius = (√3/6)s ≈ ${n(inradius)}",
                    "Circumradius = (√3/3)s ≈ ${n(circumradius)}",
                ),
                validation = listOf(
                    "All values except the perimeter involve √3 and are rounded; the circumradius is " +
                        "exactly twice the inradius (√3/3 = 2 × √3/6).",
                ),
            ),
        )
    }

    // --------------------------------------------------------- parallelogram

    fun parallelogram(
        base: BigDecimal,
        height: BigDecimal,
        side: BigDecimal? = null,
    ): CalcResult<ShapeResult> {
        requirePositive(base, "Base")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        if (side != null) requirePositive(side, "Side")?.let { return it }
        val area = base.multiply(height, CTX)
        val quantities = mutableListOf<Pair<String, BigDecimal>>()
        val unavailable = mutableListOf<String>()
        val notes = mutableListOf<String>()
        if (side != null) {
            if (side.compareTo(height) < 0) {
                return err(
                    ErrorKind.Inconsistent,
                    "The slanted side (${n(side)}) cannot be shorter than the height (${n(height)})",
                )
            }
            val perimeter = base.add(side, CTX).multiply(TWO, CTX)
            quantities += "Perimeter (unit)" to perimeter
            val acute = GeometryMath.asinDegrees(height.divide(side, CTX))
            quantities += "Acute angle (°)" to acute
            quantities += "Obtuse angle (°)" to ONE_EIGHTY.subtract(acute, CTX)
            notes += "The angle follows from sin θ = height / slanted side = ${n(height)}/${n(side)} ≈ ${n(acute)}° " +
                "(the display is rounded)."
        } else {
            unavailable += "Perimeter (unit)"
            unavailable += "Angles (°)"
            notes += "The slanted side is needed for the perimeter and angles only; the area uses base and height alone."
        }
        unavailable += "Diagonals (unit)"
        notes += "The diagonals depend on which way the slanted side leans, which the inputs do not fix."
        val steps = listOf(
            "Area = b × h = ${n(base)} × ${n(height)} = ${n(area)}",
        ).toMutableList()
        if (side != null) {
            steps += "Perimeter = 2(b + a) = 2 × (${n(base)} + ${n(side)}) = ${n(base.add(side, CTX).multiply(TWO, CTX))}"
            steps += "sin θ = h/a = ${n(height)}/${n(side)} ⇒ θ ≈ ${n(GeometryMath.asinDegrees(height.divide(side, CTX)))}°"
        }
        return ok(
            ShapeResult(
                primary = area,
                quantities = quantities,
                unavailable = unavailable,
                steps = steps,
                validation = notes,
            ),
        )
    }

    // ---------------------------------------------------------------- rhombus

    fun rhombusFromDiagonals(p: BigDecimal, q: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(p, "Diagonal p")?.let { return it }
        requirePositive(q, "Diagonal q")?.let { return it }
        val area = p.multiply(q, CTX).divide(TWO, CTX)
        val halfP = p.divide(TWO, CTX)
        val halfQ = q.divide(TWO, CTX)
        val side = GeometryMath.hypot(halfP, halfQ)
        val perimeter = side.multiply(FOUR, CTX)
        val angleAtP = GeometryMath.atanDegrees(q.divide(p, CTX)).multiply(TWO, CTX)
        val angleAtQ = ONE_EIGHTY.subtract(angleAtP, CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Side (unit)" to side,
                    "Perimeter (unit)" to perimeter,
                    "Angle at diagonal p vertices (°)" to angleAtP,
                    "Angle at diagonal q vertices (°)" to angleAtQ,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = (p × q)/2 = (${n(p)} × ${n(q)})/2 = ${n(area)}",
                    "Side = √((p/2)² + (q/2)²) = √(${n(halfP)}² + ${n(halfQ)}²) = ${n(side)}",
                    "Perimeter = 4 × side = 4 × ${n(side)} = ${n(perimeter)}",
                    "Angle at the p-diagonal vertices = 2·atan(q/p) = 2·atan(${n(q)}/${n(p)}) ≈ ${n(angleAtP)}°",
                    "Angle at the q-diagonal vertices = 180° − ${n(angleAtP)}° ≈ ${n(angleAtQ)}°",
                ),
                validation = listOf(
                    "A rhombus with diagonals p and q has side √((p/2)² + (q/2)²); the diagonals " +
                        "bisect each other at a right angle.",
                    "The angles are approximate to about 15 significant digits.",
                ),
            ),
        )
    }

    fun rhombusFromSideAngle(side: BigDecimal, angleDeg: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(side, "Side")?.let { return it }
        if (angleDeg.signum() <= 0 || angleDeg.compareTo(ONE_EIGHTY) >= 0) {
            return err(ErrorKind.Domain, "Angle θ must be greater than 0° and less than 180°")
        }
        val sin = GeometryMath.sinDegrees(angleDeg)
        val cos = GeometryMath.cosDegrees(angleDeg)
        val area = side.multiply(side, CTX).multiply(sin, CTX)
        val perimeter = side.multiply(FOUR, CTX)
        val height = side.multiply(sin, CTX)
        val longDiagonal = side.multiply(GeometryMath.sqrt(TWO.add(TWO.multiply(cos, CTX), CTX)), CTX)
        val shortDiagonal = side.multiply(GeometryMath.sqrt(TWO.subtract(TWO.multiply(cos, CTX), CTX)), CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Perimeter (unit)" to perimeter,
                    "Height (unit)" to height,
                    "Diagonal across θ vertices (unit)" to longDiagonal,
                    "Diagonal across other vertices (unit)" to shortDiagonal,
                    "Obtuse angle (180° − θ) (°)" to ONE_EIGHTY.subtract(angleDeg, CTX),
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = a²·sin θ = ${n(side)}² × sin ${n(angleDeg)}° = ${n(area)}",
                    "Perimeter = 4a = 4 × ${n(side)} = ${n(perimeter)}",
                    "Height = a·sin θ = ${n(side)} × sin ${n(angleDeg)}° ≈ ${n(height)}",
                    "Diagonal across the θ vertices = a√(2 + 2cos θ) ≈ ${n(longDiagonal)}",
                    "Diagonal across the other vertices = a√(2 − 2cos θ) ≈ ${n(shortDiagonal)}",
                ),
                validation = listOf(
                    "The two diagonals satisfy p² + q² = 4a² for the given side a.",
                    "Values involving sin θ, cos θ and square roots are rounded.",
                ),
            ),
        )
    }

    // -------------------------------------------------------------- trapezoid

    fun trapezoid(
        baseA: BigDecimal,
        baseB: BigDecimal,
        height: BigDecimal,
        leg1: BigDecimal? = null,
        leg2: BigDecimal? = null,
    ): CalcResult<ShapeResult> {
        requirePositive(baseA, "Base a")?.let { return it }
        requirePositive(baseB, "Base b")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        if (leg1 != null) requirePositive(leg1, "Leg 1")?.let { return it }
        if (leg2 != null) requirePositive(leg2, "Leg 2")?.let { return it }
        val area = baseA.add(baseB, CTX).divide(TWO, CTX).multiply(height, CTX)
        val midline = baseA.add(baseB, CTX).divide(TWO, CTX)
        val quantities = mutableListOf("Midline (unit)" to midline)
        val unavailable = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val steps = mutableListOf(
            "Area = (a + b)/2 × h = (${n(baseA)} + ${n(baseB)})/2 × ${n(height)} = ${n(area)}",
            "Midline = (a + b)/2 = ${n(midline)}",
        )
        if (leg1 != null && leg2 != null) {
            val perimeter = baseA.add(baseB, CTX).add(leg1, CTX).add(leg2, CTX)
            quantities += "Perimeter (unit)" to perimeter
            steps += "Perimeter = a + b + leg₁ + leg₂ = ${n(perimeter)}"
        } else {
            unavailable += "Perimeter (unit)"
            notes += if (leg1 == null && leg2 == null) {
                "Bases and height fix the area; provide both legs to obtain the perimeter."
            } else {
                "Both legs are required for the perimeter; only one was given."
            }
        }
        notes += "The legs do not affect the area, and the height does not follow from the legs alone."
        return ok(
            ShapeResult(
                primary = area,
                quantities = quantities,
                unavailable = unavailable,
                steps = steps,
                validation = notes,
            ),
        )
    }

    // ----------------------------------------------------------------- circle

    fun circle(mode: CircleMode, value: BigDecimal): CalcResult<ShapeResult> {
        val name = when (mode) {
            CircleMode.RADIUS -> "Radius"
            CircleMode.DIAMETER -> "Diameter"
            CircleMode.CIRCUMFERENCE -> "Circumference"
        }
        requirePositive(value, name)?.let { return it }
        val pi = GeometryMath.PI
        val radius = when (mode) {
            CircleMode.RADIUS -> value
            CircleMode.DIAMETER -> value.divide(TWO, CTX)
            CircleMode.CIRCUMFERENCE -> value.divide(TWO.multiply(pi, CTX), CTX)
        }
        val diameter = when (mode) {
            CircleMode.DIAMETER -> value
            else -> radius.multiply(TWO, CTX)
        }
        val circumference = when (mode) {
            CircleMode.CIRCUMFERENCE -> value
            else -> radius.multiply(TWO, CTX).multiply(pi, CTX)
        }
        val area = when (mode) {
            CircleMode.CIRCUMFERENCE -> value.multiply(value, CTX).divide(FOUR.multiply(pi, CTX), CTX)
            else -> radius.multiply(radius, CTX).multiply(pi, CTX)
        }
        val quantities = mutableListOf<Pair<String, BigDecimal>>()
        if (mode != CircleMode.CIRCUMFERENCE) quantities += "Circumference (unit)" to circumference
        if (mode != CircleMode.RADIUS) quantities += "Radius (unit)" to radius
        if (mode != CircleMode.DIAMETER) quantities += "Diameter (unit)" to diameter
        val steps = mutableListOf<String>()
        when (mode) {
            CircleMode.RADIUS -> {
                steps += "Diameter = 2r = 2 × ${n(value)} = ${n(diameter)}"
                steps += "Area = πr² = π × ${n(value)}² ≈ ${n(area)}"
                steps += "Circumference = 2πr = 2π × ${n(value)} ≈ ${n(circumference)}"
            }
            CircleMode.DIAMETER -> {
                steps += "Radius = d/2 = ${n(value)}/2 = ${n(radius)}"
                steps += "Area = π(d/2)² = π × ${n(radius)}² ≈ ${n(area)}"
                steps += "Circumference = πd = π × ${n(value)} ≈ ${n(circumference)}"
            }
            CircleMode.CIRCUMFERENCE -> {
                steps += "Radius = C/(2π) = ${n(value)}/(2π) ≈ ${n(radius)}"
                steps += "Diameter = C/π ≈ ${n(diameter)}"
                steps += "Area = C²/(4π) = ${n(value)}²/(4π) ≈ ${n(area)}"
            }
        }
        return ok(
            ShapeResult(
                primary = area,
                quantities = quantities,
                unavailable = emptyList(),
                steps = steps,
                validation = listOf("π is used at 34-digit precision; displayed values are rounded."),
            ),
        )
    }

    // ------------------------------------------------------------- semicircle

    fun semicircle(radius: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(radius, "Radius")?.let { return it }
        val pi = GeometryMath.PI
        val area = radius.multiply(radius, CTX).multiply(pi, CTX).divide(TWO, CTX)
        val arc = radius.multiply(pi, CTX)
        val perimeter = arc.add(radius.multiply(TWO, CTX), CTX)
        val diameter = radius.multiply(TWO, CTX)
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Arc length (unit)" to arc,
                    "Perimeter (unit)" to perimeter,
                    "Diameter (unit)" to diameter,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = πr²/2 = π × ${n(radius)}²/2 ≈ ${n(area)}",
                    "Arc length = πr = π × ${n(radius)} ≈ ${n(arc)}",
                    "Perimeter = arc + 2r = ${n(arc)} + ${n(diameter)} ≈ ${n(perimeter)}",
                    "Diameter = 2r = ${n(diameter)}",
                ),
                validation = listOf(
                    "The perimeter of a semicircle includes the straight diameter edge (arc + 2r = r(π + 2)).",
                ),
            ),
        )
    }

    // ----------------------------------------------------------------- ellipse

    fun ellipse(semiAxisA: BigDecimal, semiAxisB: BigDecimal): CalcResult<ShapeResult> {
        requirePositive(semiAxisA, "Semi-axis a")?.let { return it }
        requirePositive(semiAxisB, "Semi-axis b")?.let { return it }
        val pi = GeometryMath.PI
        val area = semiAxisA.multiply(semiAxisB, CTX).multiply(pi, CTX)
        val threeA = semiAxisA.multiply(THREE, CTX)
        val threeB = semiAxisB.multiply(THREE, CTX)
        val rootInside = threeA.add(semiAxisB, CTX).multiply(semiAxisA.add(threeB, CTX), CTX)
        val rootPart = GeometryMath.sqrt(rootInside)
        val perimeter = semiAxisA.add(semiAxisB, CTX).multiply(THREE, CTX)
            .subtract(rootPart, CTX)
            .multiply(pi, CTX)
        val large = semiAxisA.max(semiAxisB)
        val small = semiAxisA.min(semiAxisB)
        val ratio = small.divide(large, CTX)
        val eccentricity = GeometryMath.sqrt(BigDecimal.ONE.subtract(ratio.multiply(ratio, CTX), CTX))
        val notes = mutableListOf<String>()
        if (semiAxisA.compareTo(semiAxisB) < 0) {
            notes += "Semi-axis a was smaller than b; the axes were used exactly as entered " +
                "(the ellipse is the same shape, rotated)."
        }
        notes += "An ellipse has no exact elementary perimeter formula; the value shown is " +
            "Ramanujan's second approximation π[3(a + b) − √((3a + b)(a + 3b))]."
        return ok(
            ShapeResult(
                primary = area,
                quantities = listOf(
                    "Perimeter (Ramanujan) (unit)" to perimeter,
                    "Eccentricity" to eccentricity,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Area = πab = π × ${n(semiAxisA)} × ${n(semiAxisB)} ≈ ${n(area)}",
                    "Perimeter ≈ π[3(a + b) − √((3a + b)(a + 3b))] = " +
                        "π × (3 × ${n(semiAxisA.add(semiAxisB, CTX))} − √${n(rootInside)}) ≈ ${n(perimeter)}",
                    "Eccentricity = √(1 − (b/a)²) with b/a = ${n(ratio)} ≈ ${n(eccentricity)}",
                ),
                validation = notes,
            ),
        )
    }

    // -------------------------------------------------------- regular polygon

    fun regularPolygonFromSide(sides: Int, side: BigDecimal): CalcResult<ShapeResult> {
        checkSideCount(sides)?.let { return it }
        requirePositive(side, "Side")?.let { return it }
        val step = GeometryMath.PI.divide(BigDecimal(sides), CTX)
        val cot = GeometryMath.cotRadians(step)
            ?: return err(ErrorKind.Domain, "The polygon angle produces an undefined cotangent")
        val sin = GeometryMath.sinRadians(step)
        if (sin.signum() == 0) return err(ErrorKind.Domain, "The polygon geometry is degenerate")
        val perimeter = BigDecimal(sides).multiply(side, CTX)
        val apothem = side.multiply(cot, CTX).divide(TWO, CTX)
        val area = BigDecimal(sides).divide(FOUR, CTX).multiply(side.multiply(side, CTX), CTX).multiply(cot, CTX)
        val circumradius = side.divide(TWO.multiply(sin, CTX), CTX)
        return ok(
            polygonResult(
                sides = sides,
                area = area,
                perimeter = perimeter,
                apothem = apothem,
                circumradius = circumradius,
                steps = listOf(
                    "Perimeter = n × s = $sides × ${n(side)} = ${n(perimeter)}",
                    "Apothem = s / (2·tan(π/n)) = ${n(side)} / (2·tan(π/$sides)) ≈ ${n(apothem)}",
                    "Area = (n/4)·s²·cot(π/n) = ($sides/4) × ${n(side)}² × cot(π/$sides) ≈ ${n(area)}",
                    "Area (check) = ½ × perimeter × apothem ≈ ${n(perimeter.divide(TWO, CTX).multiply(apothem, CTX))}",
                    "Circumradius = s / (2·sin(π/n)) ≈ ${n(circumradius)}",
                ),
            ),
        )
    }

    fun regularPolygonFromCircumradius(sides: Int, circumradius: BigDecimal): CalcResult<ShapeResult> {
        checkSideCount(sides)?.let { return it }
        requirePositive(circumradius, "Circumradius")?.let { return it }
        val step = GeometryMath.PI.divide(BigDecimal(sides), CTX)
        val sin = GeometryMath.sinRadians(step)
        val cos = GeometryMath.cosRadians(step)
        val side = circumradius.multiply(TWO.multiply(sin, CTX), CTX)
        val perimeter = BigDecimal(sides).multiply(side, CTX)
        val apothem = circumradius.multiply(cos, CTX)
        val area = BigDecimal(sides)
            .divide(TWO, CTX)
            .multiply(circumradius.multiply(circumradius, CTX), CTX)
            .multiply(GeometryMath.sinRadians(step.multiply(TWO, CTX)), CTX)
        return ok(
            polygonResult(
                sides = sides,
                area = area,
                perimeter = perimeter,
                apothem = apothem,
                circumradius = circumradius,
                steps = listOf(
                    "Side = 2R·sin(π/n) = 2 × ${n(circumradius)} × sin(π/$sides) ≈ ${n(side)}",
                    "Perimeter = n × s ≈ ${n(perimeter)}",
                    "Apothem = R·cos(π/n) ≈ ${n(apothem)}",
                    "Area = (n/2)·R²·sin(2π/n) = ($sides/2) × ${n(circumradius)}² × sin(2π/$sides) ≈ ${n(area)}",
                ),
            ),
        )
    }

    private fun polygonResult(
        sides: Int,
        area: BigDecimal,
        perimeter: BigDecimal,
        apothem: BigDecimal,
        circumradius: BigDecimal,
        steps: List<String>,
    ): ShapeResult {
        val n = BigDecimal(sides)
        val interior = n.subtract(TWO, CTX).multiply(ONE_EIGHTY, CTX).divide(n, CTX)
        val exterior = THREE_SIXTY.divide(n, CTX)
        val diagonals = n.multiply(n.subtract(THREE, CTX), CTX).divide(TWO, CTX)
        return ShapeResult(
            primary = area,
            quantities = listOf(
                "Perimeter (unit)" to perimeter,
                "Apothem (unit)" to apothem,
                "Circumradius (unit)" to circumradius,
                "Interior angle (°)" to interior,
                "Exterior angle (°)" to exterior,
                "Diagonals (count)" to diagonals,
            ),
            unavailable = emptyList(),
            steps = steps + listOf(
                "Interior angle = (n − 2) × 180°/n = ${n(interior)}°",
                "Exterior angle = 360°/n = ${n(exterior)}°",
                "Diagonals = n(n − 3)/2 = ${n(diagonals)}",
            ),
            validation = listOf(
                "The polygon is assumed regular (all sides and angles equal); trigonometric values " +
                    "are computed at 34-digit precision and displayed rounded.",
            ),
        )
    }

    // ----------------------------------------------------------------- shared

    private fun angleOpposite(opposite: BigDecimal, sideB: BigDecimal, sideC: BigDecimal): BigDecimal {
        val numerator = sideB.multiply(sideB, CTX).add(sideC.multiply(sideC, CTX), CTX)
            .subtract(opposite.multiply(opposite, CTX), CTX)
        val denominator = sideB.multiply(sideC, CTX).multiply(TWO, CTX)
        return GeometryMath.acosDegrees(numerator.divide(denominator, CTX))
    }

    private fun checkSideCount(sides: Int): CalcResult<ShapeResult>? =
        if (sides < 3) err(ErrorKind.Domain, "A regular polygon needs at least 3 sides") else null

    private fun requirePositive(value: BigDecimal, name: String): CalcResult<ShapeResult>? =
        if (value.signum() <= 0) err(ErrorKind.InvalidInput, "$name must be greater than zero") else null

    private fun n(value: BigDecimal): String = GeometryMath.n(value)
}
