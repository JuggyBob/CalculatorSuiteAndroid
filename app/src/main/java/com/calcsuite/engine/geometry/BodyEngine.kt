package com.calcsuite.engine.geometry

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext

/**
 * Solid-body engine: typed inputs in, fully labelled results out.
 *
 * ## Unit convention
 * All lengths are given in one unit. Labels carry the dimension of the value:
 * `(unit)` for lengths, `(unit²)` for areas, `(unit³)` for volumes and `(°)`
 * for angles — the literal word `unit` is a placeholder the screen replaces
 * with the user's unit string (e.g. `cm`, so `Volume (unit³)` becomes
 * `Volume (cm³)`). Linear, squared and cubed quantities are never mixed.
 *
 * `primary` is the body's volume in cubed units. Bodies assume a right
 * (upright) solid unless stated otherwise in `validation`.
 */
enum class BodyKind {
    CUBE, RECTANGULAR_PRISM, SPHERE, HEMISPHERE, CYLINDER, CONE,
    SQUARE_PYRAMID, TRIANGULAR_PRISM, TRIANGULAR_PYRAMID,
}

/** How a triangular pyramid is specified. */
enum class TriangularPyramidMode { REGULAR_TETRAHEDRON, GENERAL }

/** One typed input of a body. `id` keys the screen's input map. */
data class BodyField(val id: String, val label: String, val optional: Boolean = false)

/** Result of a body calculation. See the file KDoc for unit conventions. */
data class BodyResult(
    /** Volume in cubed units, when the inputs determine it. */
    val primary: BigDecimal? = null,
    /** Labelled secondary quantities; all are determined by the inputs. */
    val quantities: List<Pair<String, BigDecimal>>,
    /** Quantities the inputs cannot determine (never silently zero). */
    val unavailable: List<String>,
    /** Substituted formulas, one per calculated quantity. */
    val steps: List<String>,
    /** Non-fatal notes (assumptions, precision). */
    val validation: List<String>,
)

object BodyEngine {

    private val CTX: MathContext = GeometryMath.CTX
    private val TWO = BigDecimal("2")
    private val THREE = BigDecimal("3")
    private val FOUR = BigDecimal("4")
    private val SIX = BigDecimal("6")

    // ------------------------------------------------------------ source data

    /** The typed inputs of a body in its default mode (UI field layout). */
    fun fields(kind: BodyKind): List<BodyField> = when (kind) {
        BodyKind.CUBE -> listOf(BodyField("a", "Edge a"))
        BodyKind.RECTANGULAR_PRISM -> listOf(
            BodyField("a", "Edge a"), BodyField("b", "Edge b"), BodyField("c", "Edge c"),
        )
        BodyKind.SPHERE -> listOf(BodyField("radius", "Radius r"))
        BodyKind.HEMISPHERE -> listOf(BodyField("radius", "Radius r"))
        BodyKind.CYLINDER -> listOf(BodyField("radius", "Radius r"), BodyField("height", "Height h"))
        BodyKind.CONE -> listOf(BodyField("radius", "Radius r"), BodyField("height", "Height h"))
        BodyKind.SQUARE_PYRAMID -> listOf(
            BodyField("baseEdge", "Base edge a"), BodyField("height", "Height h"),
        )
        BodyKind.TRIANGULAR_PRISM -> listOf(
            BodyField("baseEdge", "Base edge a"), BodyField("height", "Height h"),
        )
        BodyKind.TRIANGULAR_PYRAMID -> listOf(
            BodyField("a", "Edge a"),
            BodyField("sideA", "Base side a"), BodyField("sideB", "Base side b"),
            BodyField("sideC", "Base side c"), BodyField("height", "Height h"),
        )
    }

    // -------------------------------------------------------------------- cube

    fun cube(edge: BigDecimal): CalcResult<BodyResult> {
        requirePositive(edge, "Edge")?.let { return it }
        val volume = edge.multiply(edge, CTX).multiply(edge, CTX)
        val total = edge.multiply(edge, CTX).multiply(SIX, CTX)
        val lateral = edge.multiply(edge, CTX).multiply(FOUR, CTX)
        val faceDiagonal = edge.multiply(GeometryMath.sqrt(TWO), CTX)
        val spaceDiagonal = edge.multiply(GeometryMath.sqrt(THREE), CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Total surface area (unit²)" to total,
                    "Lateral surface area (unit²)" to lateral,
                    "Face diagonal (unit)" to faceDiagonal,
                    "Space diagonal (unit)" to spaceDiagonal,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Volume = a³ = ${n(edge)}³ = ${n(volume)}",
                    "Total surface area = 6a² = 6 × ${n(edge)}² = ${n(total)}",
                    "Lateral surface area = 4a² = 4 × ${n(edge)}² = ${n(lateral)}",
                    "Face diagonal = a√2 = ${n(edge)} × √2 ≈ ${n(faceDiagonal)}",
                    "Space diagonal = a√3 = ${n(edge)} × √3 ≈ ${n(spaceDiagonal)}",
                ),
                validation = listOf("The diagonals involve √2 and √3; the displayed values are rounded."),
            ),
        )
    }

    // ------------------------------------------------------- rectangular prism

    fun rectangularPrism(a: BigDecimal, b: BigDecimal, c: BigDecimal): CalcResult<BodyResult> {
        requirePositive(a, "Edge a")?.let { return it }
        requirePositive(b, "Edge b")?.let { return it }
        requirePositive(c, "Edge c")?.let { return it }
        val volume = a.multiply(b, CTX).multiply(c, CTX)
        val base = a.multiply(b, CTX)
        val lateral = a.add(b, CTX).multiply(TWO, CTX).multiply(c, CTX)
        val total = base.add(b.multiply(c, CTX), CTX).add(a.multiply(c, CTX), CTX).multiply(TWO, CTX)
        val spaceDiagonal = GeometryMath.sqrt(
            a.multiply(a, CTX).add(b.multiply(b, CTX), CTX).add(c.multiply(c, CTX), CTX),
        )
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Total surface area (unit²)" to total,
                    "Lateral surface area (unit²)" to lateral,
                    "Base area (unit²)" to base,
                    "Space diagonal (unit)" to spaceDiagonal,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Volume = a × b × c = ${n(a)} × ${n(b)} × ${n(c)} = ${n(volume)}",
                    "Base area = a × b = ${n(a)} × ${n(b)} = ${n(base)}",
                    "Lateral surface area = 2(a + b) × c = 2 × ${n(a.add(b, CTX))} × ${n(c)} = ${n(lateral)}",
                    "Total surface area = 2(ab + bc + ca) = ${n(total)}",
                    "Space diagonal = √(a² + b² + c²) ≈ ${n(spaceDiagonal)}",
                ),
                validation = listOf("The space diagonal √(a² + b² + c²) is shown rounded."),
            ),
        )
    }

    // ------------------------------------------------------------------ sphere

    fun sphere(radius: BigDecimal): CalcResult<BodyResult> {
        requirePositive(radius, "Radius")?.let { return it }
        val pi = GeometryMath.PI
        val r3 = radius.multiply(radius, CTX).multiply(radius, CTX)
        val volume = r3.multiply(pi, CTX).multiply(FOUR, CTX).divide(THREE, CTX)
        val surface = radius.multiply(radius, CTX).multiply(FOUR, CTX).multiply(pi, CTX)
        val circumference = radius.multiply(TWO, CTX).multiply(pi, CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Surface area (unit²)" to surface,
                    "Great-circle circumference (unit)" to circumference,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Volume = (4/3)πr³ = (4/3) × π × ${n(radius)}³ ≈ ${n(volume)}",
                    "Surface area = 4πr² = 4 × π × ${n(radius)}² ≈ ${n(surface)}",
                    "Great-circle circumference = 2πr = 2π × ${n(radius)} ≈ ${n(circumference)}",
                ),
                validation = listOf("π is used at 34-digit precision; displayed values are rounded."),
            ),
        )
    }

    // -------------------------------------------------------------- hemisphere

    fun hemisphere(radius: BigDecimal): CalcResult<BodyResult> {
        requirePositive(radius, "Radius")?.let { return it }
        val pi = GeometryMath.PI
        val r2 = radius.multiply(radius, CTX)
        val r3 = r2.multiply(radius, CTX)
        val volume = r3.multiply(TWO, CTX).multiply(pi, CTX).divide(THREE, CTX)
        val curved = r2.multiply(TWO, CTX).multiply(pi, CTX)
        val base = r2.multiply(pi, CTX)
        val total = curved.add(base, CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Total surface area (unit²)" to total,
                    "Curved surface area (unit²)" to curved,
                    "Base area (unit²)" to base,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Volume = (2/3)πr³ = (2/3) × π × ${n(radius)}³ ≈ ${n(volume)}",
                    "Curved surface area = 2πr² = 2 × π × ${n(radius)}² ≈ ${n(curved)}",
                    "Base area = πr² = π × ${n(radius)}² ≈ ${n(base)}",
                    "Total surface area = curved + base = 3πr² ≈ ${n(total)}",
                ),
                validation = listOf("Total surface area includes the flat circular base (3πr²)."),
            ),
        )
    }

    // ----------------------------------------------------------------- cylinder

    fun cylinder(radius: BigDecimal, height: BigDecimal): CalcResult<BodyResult> {
        requirePositive(radius, "Radius")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        val pi = GeometryMath.PI
        val r2 = radius.multiply(radius, CTX)
        val volume = r2.multiply(height, CTX).multiply(pi, CTX)
        val base = r2.multiply(pi, CTX)
        val lateral = radius.multiply(TWO, CTX).multiply(pi, CTX).multiply(height, CTX)
        val total = radius.add(height, CTX).multiply(TWO, CTX).multiply(pi, CTX).multiply(radius, CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Total surface area (unit²)" to total,
                    "Lateral surface area (unit²)" to lateral,
                    "Base area (unit²)" to base,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Volume = πr²h = π × ${n(radius)}² × ${n(height)} ≈ ${n(volume)}",
                    "Base area = πr² ≈ ${n(base)}",
                    "Lateral surface area = 2πrh = 2π × ${n(radius)} × ${n(height)} ≈ ${n(lateral)}",
                    "Total surface area = 2πr(r + h) = 2π × ${n(radius)} × ${n(radius.add(height, CTX))} ≈ ${n(total)}",
                ),
                validation = listOf(
                    "A right circular cylinder is assumed: the axis is perpendicular to the circular bases.",
                ),
            ),
        )
    }

    // --------------------------------------------------------------------- cone

    fun cone(radius: BigDecimal, height: BigDecimal): CalcResult<BodyResult> {
        requirePositive(radius, "Radius")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        val pi = GeometryMath.PI
        val slant = GeometryMath.hypot(radius, height)
        val r2 = radius.multiply(radius, CTX)
        val volume = r2.multiply(height, CTX).multiply(pi, CTX).divide(THREE, CTX)
        val base = r2.multiply(pi, CTX)
        val lateral = radius.multiply(slant, CTX).multiply(pi, CTX)
        val total = lateral.add(base, CTX)
        val apex = GeometryMath.atanDegrees(radius.divide(height, CTX)).multiply(TWO, CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Total surface area (unit²)" to total,
                    "Lateral surface area (unit²)" to lateral,
                    "Base area (unit²)" to base,
                    "Slant height (unit)" to slant,
                    "Apex angle (°)" to apex,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Slant height l = √(r² + h²) = √(${n(radius)}² + ${n(height)}²) = ${n(slant)}",
                    "Volume = πr²h/3 = π × ${n(radius)}² × ${n(height)}/3 ≈ ${n(volume)}",
                    "Base area = πr² ≈ ${n(base)}",
                    "Lateral surface area = πrl = π × ${n(radius)} × ${n(slant)} ≈ ${n(lateral)}",
                    "Total surface area = πr(r + l) ≈ ${n(total)}",
                    "Apex angle = 2·atan(r/h) = 2·atan(${n(radius)}/${n(height)}) ≈ ${n(apex)}°",
                ),
                validation = listOf(
                    "A right circular cone is assumed; the apex angle is the full opening angle at " +
                        "the tip and is approximate to about 15 significant digits.",
                ),
            ),
        )
    }

    // ---------------------------------------------------------- square pyramid

    fun squarePyramid(baseEdge: BigDecimal, height: BigDecimal): CalcResult<BodyResult> {
        requirePositive(baseEdge, "Base edge")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        val half = baseEdge.divide(TWO, CTX)
        val slant = GeometryMath.hypot(height, half)
        val base = baseEdge.multiply(baseEdge, CTX)
        val volume = base.multiply(height, CTX).divide(THREE, CTX)
        val lateral = baseEdge.multiply(TWO, CTX).multiply(slant, CTX)
        val total = base.add(lateral, CTX)
        val lateralEdge = GeometryMath.sqrt(
            height.multiply(height, CTX).add(baseEdge.multiply(baseEdge, CTX).divide(TWO, CTX), CTX),
        )
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Total surface area (unit²)" to total,
                    "Lateral surface area (unit²)" to lateral,
                    "Base area (unit²)" to base,
                    "Slant height (unit)" to slant,
                    "Lateral edge (unit)" to lateralEdge,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Slant height = √(h² + (a/2)²) = √(${n(height)}² + ${n(half)}²) ≈ ${n(slant)}",
                    "Volume = a²h/3 = ${n(baseEdge)}² × ${n(height)}/3 = ${n(volume)}",
                    "Base area = a² = ${n(base)}",
                    "Lateral surface area = 2a × slant = 2 × ${n(baseEdge)} × ${n(slant)} ≈ ${n(lateral)}",
                    "Total surface area = a² + 2a × slant ≈ ${n(total)}",
                    "Lateral edge = √(h² + a²/2) ≈ ${n(lateralEdge)}",
                ),
                validation = listOf(
                    "A regular right square pyramid is assumed: the apex sits vertically above the " +
                        "centre of the square base and all four triangular faces are congruent.",
                ),
            ),
        )
    }

    // -------------------------------------------------------- triangular prism

    fun triangularPrism(baseEdge: BigDecimal, height: BigDecimal): CalcResult<BodyResult> {
        requirePositive(baseEdge, "Base edge")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        val sqrt3 = GeometryMath.sqrt(THREE)
        val base = sqrt3.multiply(baseEdge.multiply(baseEdge, CTX), CTX).divide(FOUR, CTX)
        val volume = base.multiply(height, CTX)
        val lateral = baseEdge.multiply(height, CTX).multiply(THREE, CTX)
        val total = base.multiply(TWO, CTX).add(lateral, CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Total surface area (unit²)" to total,
                    "Lateral surface area (unit²)" to lateral,
                    "Base area (unit²)" to base,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Base area = (√3/4)a² = (√3/4) × ${n(baseEdge)}² ≈ ${n(base)}",
                    "Volume = base area × h = ${n(base)} × ${n(height)} ≈ ${n(volume)}",
                    "Lateral surface area = 3ah = 3 × ${n(baseEdge)} × ${n(height)} = ${n(lateral)}",
                    "Total surface area = 2 × base + lateral ≈ ${n(total)}",
                ),
                validation = listOf(
                    "A right prism is assumed: the three rectangular side faces rise perpendicular to " +
                        "an equilateral base of side a.",
                ),
            ),
        )
    }

    // -------------------------------------------------------- triangular pyramid

    fun regularTetrahedron(edge: BigDecimal): CalcResult<BodyResult> {
        requirePositive(edge, "Edge")?.let { return it }
        val sqrt2 = GeometryMath.sqrt(TWO)
        val sqrt3 = GeometryMath.sqrt(THREE)
        val edge2 = edge.multiply(edge, CTX)
        val volume = edge2.multiply(edge, CTX).divide(SIX.multiply(sqrt2, CTX), CTX)
        val face = sqrt3.multiply(edge2, CTX).divide(FOUR, CTX)
        val surface = edge2.multiply(sqrt3, CTX)
        val lateral = face.multiply(THREE, CTX)
        val height = edge.multiply(GeometryMath.sqrt(TWO)).multiply(sqrt3, CTX).divide(THREE, CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Surface area (unit²)" to surface,
                    "Lateral surface area (unit²)" to lateral,
                    "Face area (unit²)" to face,
                    "Height (unit)" to height,
                ),
                unavailable = emptyList(),
                steps = listOf(
                    "Volume = a³/(6√2) = ${n(edge)}³/(6√2) ≈ ${n(volume)}",
                    "Face area = (√3/4)a² = (√3/4) × ${n(edge)}² ≈ ${n(face)}",
                    "Surface area = 4 × face = √3a² ≈ ${n(surface)}",
                    "Lateral surface area = 3 × face ≈ ${n(lateral)}",
                    "Height = a√6/3 = a√2·√3/3 ≈ ${n(height)}",
                ),
                validation = listOf(
                    "A regular tetrahedron is assumed: all four faces are equilateral triangles of " +
                        "side a and the apex lies above the centroid of the base.",
                ),
            ),
        )
    }

    fun triangularPyramid(
        sideA: BigDecimal,
        sideB: BigDecimal,
        sideC: BigDecimal,
        height: BigDecimal,
    ): CalcResult<BodyResult> {
        requirePositive(sideA, "Base side a")?.let { return it }
        requirePositive(sideB, "Base side b")?.let { return it }
        requirePositive(sideC, "Base side c")?.let { return it }
        requirePositive(height, "Height")?.let { return it }
        val sumAB = sideA.add(sideB, CTX)
        val sumAC = sideA.add(sideC, CTX)
        val sumBC = sideB.add(sideC, CTX)
        val failure = when {
            sumAB.compareTo(sideC) <= 0 -> Triple(sideA, sideB, sideC)
            sumAC.compareTo(sideB) <= 0 -> Triple(sideA, sideC, sideB)
            sumBC.compareTo(sideA) <= 0 -> Triple(sideB, sideC, sideA)
            else -> null
        }
        if (failure != null) {
            val (x, y, z) = failure
            return err(
                ErrorKind.Domain,
                "Base sides ${n(sideA)}, ${n(sideB)} and ${n(sideC)} do not form a triangle: " +
                    "${n(x)} + ${n(y)} = ${n(x.add(y, CTX))} is not greater than ${n(z)}",
            )
        }
        val perimeter = sumAB.add(sideC, CTX)
        val semi = perimeter.divide(TWO, CTX)
        val radicand = semi
            .multiply(semi.subtract(sideA, CTX), CTX)
            .multiply(semi.subtract(sideB, CTX), CTX)
            .multiply(semi.subtract(sideC, CTX), CTX)
        val base = GeometryMath.sqrt(radicand)
        val volume = base.multiply(height, CTX).divide(THREE, CTX)
        return ok(
            BodyResult(
                primary = volume,
                quantities = listOf(
                    "Base area (unit²)" to base,
                    "Base perimeter (unit)" to perimeter,
                ),
                unavailable = listOf(
                    "Lateral surface area (unit²)",
                    "Total surface area (unit²)",
                    "Slant heights (unit)",
                ),
                steps = listOf(
                    "s = (a + b + c)/2 = (${n(sideA)} + ${n(sideB)} + ${n(sideC)})/2 = ${n(semi)}",
                    "Base area = √(s(s − a)(s − b)(s − c)) = √${n(radicand)} ≈ ${n(base)}",
                    "Volume = base area × h/3 = ${n(base)} × ${n(height)}/3 ≈ ${n(volume)}",
                ),
                validation = listOf(
                    "The height is the perpendicular distance from the apex to the plane of the base; " +
                        "the volume does not depend on where the apex sits above the base.",
                    "The lateral faces are not determined by the base sides and the height alone — the " +
                        "horizontal position of the apex is needed as well.",
                ),
            ),
        )
    }

    // ----------------------------------------------------------------- shared

    private fun requirePositive(value: BigDecimal, name: String): CalcResult<BodyResult>? =
        if (value.signum() <= 0) err(ErrorKind.InvalidInput, "$name must be greater than zero") else null

    private fun n(value: BigDecimal): String = GeometryMath.n(value)
}
