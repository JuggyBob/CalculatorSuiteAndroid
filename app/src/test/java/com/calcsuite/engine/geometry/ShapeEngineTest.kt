package com.calcsuite.engine.geometry

import com.calcsuite.core.CalcError
import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.expr.Consts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class ShapeEngineTest {

    private val sqrt2 = BigDecimal("1.4142135623730950488016887242096980786")
    private val sqrt3 = BigDecimal("1.7320508075688772935274463415058723669")

    private fun bd(text: String): BigDecimal = BigDecimal(text)

    private fun okValue(result: CalcResult<ShapeResult>): ShapeResult {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<ShapeResult>): CalcError {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error
    }

    /** Relative comparison with 1e-9 default tolerance. */
    private fun assertClose(
        expected: BigDecimal,
        actual: BigDecimal,
        tol: Double = 1e-9,
        message: String = "",
    ) {
        val diff = actual.subtract(expected).abs()
        val scale = expected.abs().max(BigDecimal("1e-12"))
        val relative = diff.divide(scale, MathContext(20)).toDouble()
        assertTrue("$message expected ≈ $expected, was $actual (relative diff $relative)", relative < tol)
    }

    private fun quantity(result: ShapeResult, label: String): BigDecimal =
        result.quantities.firstOrNull { it.first == label }?.second
            ?: throw AssertionError("Missing \"$label\" in ${result.quantities.map { it.first }}")

    private fun exact(expected: String, actual: BigDecimal, message: String = "") {
        assertEquals("$message expected exactly $expected, was $actual", 0, actual.compareTo(bd(expected)))
    }

    private fun rejected(actual: CalcResult<ShapeResult>, kind: ErrorKind, message: String = "") {
        val err = error(actual)
        assertEquals("$message kind for ${err.message}", kind, err.kind)
    }

    // ------------------------------------------------------------------ square

    @Test fun squareSideThree() {
        val r = okValue(ShapeEngine.square(bd("3")))
        exact("9", r.primary!!, "area")
        exact("12", quantity(r, "Perimeter (unit)"), "perimeter")
        assertClose(bd("3").multiply(sqrt2), quantity(r, "Diagonal (unit)"), message = "diagonal")
        assertTrue("irrational note missing", r.validation.any { it.contains("irrational") })
    }

    // --------------------------------------------------------------- rectangle

    @Test fun rectangleThreeByFour() {
        val r = okValue(ShapeEngine.rectangle(bd("3"), bd("4")))
        exact("12", r.primary!!, "area")
        exact("14", quantity(r, "Perimeter (unit)"), "perimeter")
        exact("5", quantity(r, "Diagonal (unit)"), "diagonal")
    }

    @Test fun rectangleIsSymmetric() {
        val a = okValue(ShapeEngine.rectangle(bd("3"), bd("4")))
        val b = okValue(ShapeEngine.rectangle(bd("4"), bd("3")))
        assertEquals(0, a.primary!!.compareTo(b.primary!!))
        exact("5", quantity(a, "Diagonal (unit)"))
    }

    // ---------------------------------------------------------------- triangle

    @Test fun triangleBaseHeight() {
        val r = okValue(ShapeEngine.triangleBaseHeight(bd("6"), bd("4")))
        exact("12", r.primary!!, "area")
        assertTrue("perimeter must be unavailable", r.unavailable.any { it.startsWith("Perimeter") })
        assertTrue("side lengths must be unavailable", r.unavailable.any { it.startsWith("Side lengths") })
    }

    @Test fun triangleHeronThreeFourFive() {
        val r = okValue(ShapeEngine.triangleThreeSides(bd("3"), bd("4"), bd("5")))
        exact("6", r.primary!!, "area")
        exact("12", quantity(r, "Perimeter (unit)"), "perimeter")
        exact("6", quantity(r, "Semi-perimeter (unit)"), "semi-perimeter")
        assertClose(bd("36.869897645844021296855612559093"), quantity(r, "Angle A (opposite a) (°)"), 1e-9, "angle A")
        assertClose(bd("90"), quantity(r, "Angle C (opposite c) (°)"), 1e-9, "right angle C")
        assertTrue("no angles left over", r.unavailable.isEmpty())
    }

    @Test fun triangleInequalityRejects() {
        val err = error(ShapeEngine.triangleThreeSides(bd("1"), bd("2"), bd("5")))
        assertEquals(ErrorKind.Domain, err.kind)
        assertTrue("message should name the failing sum: ${err.message}", err.message.contains("1 + 2"))
        rejected(ShapeEngine.triangleThreeSides(bd("5"), bd("1"), bd("2")), ErrorKind.Domain, "b+c")
        rejected(ShapeEngine.triangleThreeSides(bd("3"), bd("9"), bd("3")), ErrorKind.Domain, "a+c")
        rejected(ShapeEngine.triangleThreeSides(bd("2"), bd("3"), bd("5")), ErrorKind.Domain, "degenerate")
    }

    @Test fun triangleTwoSidesIncludedAngle() {
        val r = okValue(ShapeEngine.triangleTwoSidesAngle(bd("3"), bd("4"), bd("90")))
        exact("6", r.primary!!, "area")
        exact("5", quantity(r, "Third side c (unit)"), "third side")
        exact("12", quantity(r, "Perimeter (unit)"), "perimeter")
        assertClose(bd("36.869897645844021296855612559093"), quantity(r, "Angle A (opposite a) (°)"), 1e-9, "A")
        // sin 60° × 2² / 2 = √3
        val equilateral = okValue(ShapeEngine.triangleTwoSidesAngle(bd("2"), bd("2"), bd("60")))
        assertClose(sqrt3, equilateral.primary!!, message = "SAS equilateral area")
        assertClose(bd("2"), quantity(equilateral, "Third side c (unit)"), 1e-9, "third side of equilateral")
    }

    @Test fun triangleAngleBounds() {
        rejected(ShapeEngine.triangleTwoSidesAngle(bd("1"), bd("1"), bd("0")), ErrorKind.Domain, "0°")
        rejected(ShapeEngine.triangleTwoSidesAngle(bd("1"), bd("1"), bd("180")), ErrorKind.Domain, "180°")
        rejected(ShapeEngine.triangleTwoSidesAngle(bd("1"), bd("1"), bd("-30")), ErrorKind.Domain, "negative")
        rejected(ShapeEngine.triangleTwoSidesAngle(bd("1"), bd("-1"), bd("60")), ErrorKind.InvalidInput, "negative side")
    }

    // ------------------------------------------------------------ equilateral

    @Test fun equilateralSideTwo() {
        val r = okValue(ShapeEngine.equilateralTriangle(bd("2")))
        assertClose(sqrt3, r.primary!!, message = "area")
        assertClose(sqrt3, quantity(r, "Height (unit)"), message = "height")
        exact("6", quantity(r, "Perimeter (unit)"), "perimeter")
        assertClose(sqrt3.divide(bd("3"), MathContext(30)), quantity(r, "Inradius (unit)"), 1e-9, "inradius")
        assertClose(sqrt3.multiply(bd("2")).divide(bd("3"), MathContext(30)), quantity(r, "Circumradius (unit)"), 1e-9, "circumradius")
        assertClose(
            quantity(r, "Circumradius (unit)"),
            quantity(r, "Inradius (unit)").multiply(bd("2")),
            1e-30,
            "circumradius is twice the inradius",
        )
    }

    // ----------------------------------------------------------- parallelogram

    @Test fun parallelogramWithSide() {
        val r = okValue(ShapeEngine.parallelogram(bd("5"), bd("3"), bd("4")))
        exact("15", r.primary!!, "area")
        exact("18", quantity(r, "Perimeter (unit)"), "perimeter")
        val acute = quantity(r, "Acute angle (°)")
        assertClose(BigDecimal.valueOf(Math.toDegrees(kotlin.math.asin(0.75))), acute, 1e-9, "acute angle")
    }

    @Test fun parallelogramWithoutSide() {
        val r = okValue(ShapeEngine.parallelogram(bd("5"), bd("3")))
        exact("15", r.primary!!, "area")
        assertTrue("perimeter unavailable", r.unavailable.any { it.startsWith("Perimeter") })
        assertTrue("angles unavailable", r.unavailable.any { it.startsWith("Angles") })
        assertTrue("diagonals unavailable", r.unavailable.any { it.startsWith("Diagonals") })
        assertTrue("no perimeter row", r.quantities.none { it.first.startsWith("Perimeter") })
    }

    @Test fun parallelogramSideShorterThanHeight() {
        rejected(ShapeEngine.parallelogram(bd("5"), bd("3"), bd("2")), ErrorKind.Inconsistent, "side < height")
    }

    // ---------------------------------------------------------------- rhombus

    @Test fun rhombusDiagonalsSixEight() {
        val r = okValue(ShapeEngine.rhombusFromDiagonals(bd("6"), bd("8")))
        exact("24", r.primary!!, "area")
        exact("5", quantity(r, "Side (unit)"), "side")
        exact("20", quantity(r, "Perimeter (unit)"), "perimeter")
        val expectedAngle = BigDecimal.valueOf(2 * Math.toDegrees(kotlin.math.atan(8.0 / 6.0)))
        assertClose(expectedAngle, quantity(r, "Angle at diagonal p vertices (°)"), 1e-9, "angle at p")
        assertClose(
            bd("180").subtract(expectedAngle),
            quantity(r, "Angle at diagonal q vertices (°)"),
            1e-9,
            "angle at q",
        )
    }

    @Test fun rhombusSideAndAngle() {
        val square = okValue(ShapeEngine.rhombusFromSideAngle(bd("5"), bd("90")))
        exact("25", square.primary!!, "area")
        exact("20", quantity(square, "Perimeter (unit)"), "perimeter")
        assertClose(bd("5").multiply(sqrt2), quantity(square, "Diagonal across θ vertices (unit)"), 1e-9, "diagonal θ")
        val expected = bd("5").multiply(bd("5")).multiply(
            BigDecimal.valueOf(kotlin.math.sin(Math.toRadians(60.0))),
        )
        val sixty = okValue(ShapeEngine.rhombusFromSideAngle(bd("5"), bd("60")))
        assertClose(expected, sixty.primary!!, 1e-9, "area at 60°")
    }

    @Test fun rhombusRejects() {
        rejected(ShapeEngine.rhombusFromDiagonals(bd("0"), bd("8")), ErrorKind.InvalidInput, "zero diagonal")
        rejected(ShapeEngine.rhombusFromSideAngle(bd("5"), bd("180")), ErrorKind.Domain, "180°")
        rejected(ShapeEngine.rhombusFromSideAngle(bd("-5"), bd("60")), ErrorKind.InvalidInput, "negative side")
    }

    // -------------------------------------------------------------- trapezoid

    @Test fun trapezoidArea() {
        val r = okValue(ShapeEngine.trapezoid(bd("4"), bd("6"), bd("3")))
        exact("15", r.primary!!, "area")
        exact("5", quantity(r, "Midline (unit)"), "midline")
        assertTrue("perimeter unavailable without legs", r.unavailable.any { it.startsWith("Perimeter") })
    }

    @Test fun trapezoidWithLegs() {
        val r = okValue(ShapeEngine.trapezoid(bd("4"), bd("6"), bd("3"), bd("3"), bd("5")))
        exact("15", r.primary!!, "area")
        exact("18", quantity(r, "Perimeter (unit)"), "perimeter")
        assertTrue(r.unavailable.isEmpty())
        val oneLeg = okValue(ShapeEngine.trapezoid(bd("4"), bd("6"), bd("3"), bd("3")))
        assertTrue("one leg is not enough", oneLeg.unavailable.any { it.startsWith("Perimeter") })
    }

    // ----------------------------------------------------------------- circle

    @Test fun circleRadiusTwo() {
        val r = okValue(ShapeEngine.circle(CircleMode.RADIUS, bd("2")))
        assertClose(Consts.PI.multiply(bd("4")), r.primary!!, message = "area = 4π")
        assertClose(Consts.PI.multiply(bd("4")), quantity(r, "Circumference (unit)"), message = "circumference = 4π")
        exact("4", quantity(r, "Diameter (unit)"), "diameter")
    }

    @Test fun circleDiameterPath() {
        val r = okValue(ShapeEngine.circle(CircleMode.DIAMETER, bd("4")))
        assertClose(Consts.PI.multiply(bd("4")), r.primary!!, message = "area = 4π")
        exact("2", quantity(r, "Radius (unit)"), "radius")
        assertClose(Consts.PI.multiply(bd("4")), quantity(r, "Circumference (unit)"), message = "circumference")
    }

    @Test fun circleCircumferencePath() {
        val c = Consts.PI.multiply(bd("4"))
        val r = okValue(ShapeEngine.circle(CircleMode.CIRCUMFERENCE, c))
        assertClose(Consts.PI.multiply(bd("4")), r.primary!!, message = "area")
        assertClose(bd("2"), quantity(r, "Radius (unit)"), message = "radius")
        assertClose(bd("4"), quantity(r, "Diameter (unit)"), message = "diameter")
    }

    @Test fun circleRejectsZero() {
        rejected(ShapeEngine.circle(CircleMode.RADIUS, bd("0")), ErrorKind.InvalidInput, "zero radius")
        rejected(ShapeEngine.circle(CircleMode.CIRCUMFERENCE, bd("-2")), ErrorKind.InvalidInput, "negative circumference")
    }

    // ------------------------------------------------------------- semicircle

    @Test fun semicircleRadiusTwo() {
        val r = okValue(ShapeEngine.semicircle(bd("2")))
        assertClose(Consts.PI.multiply(bd("2")), r.primary!!, message = "area = 2π")
        assertClose(Consts.PI.multiply(bd("2")), quantity(r, "Arc length (unit)"), message = "arc = 2π")
        assertClose(Consts.PI.multiply(bd("2")).add(bd("4")), quantity(r, "Perimeter (unit)"), message = "perimeter = 2π + 4")
    }

    // ---------------------------------------------------------------- ellipse

    @Test fun ellipseThreeByTwo() {
        val r = okValue(ShapeEngine.ellipse(bd("3"), bd("2")))
        assertClose(Consts.PI.multiply(bd("6")), r.primary!!, message = "area = 6π")
        val perimeter = quantity(r, "Perimeter (Ramanujan) (unit)")
        assertTrue(
            "perimeter $perimeter not within 1e-5 of 15.86543959",
            perimeter.subtract(bd("15.86543959")).abs().compareTo(bd("0.00001")) < 0,
        )
        val expected = Consts.PI.multiply(
            bd("15").subtract(BigDecimal.valueOf(kotlin.math.sqrt(99.0))),
        )
        assertClose(expected, perimeter, 1e-9, "Ramanujan perimeter")
        assertClose(
            BigDecimal.valueOf(kotlin.math.sqrt(5.0) / 3.0),
            quantity(r, "Eccentricity"),
            1e-9,
            "eccentricity = √5/3",
        )
    }

    // ----------------------------------------------------- regular polygon

    @Test fun regularHexagonFromSide() {
        val r = okValue(ShapeEngine.regularPolygonFromSide(6, bd("2")))
        assertClose(bd("6").multiply(sqrt3), r.primary!!, message = "area = 6√3")
        exact("12", quantity(r, "Perimeter (unit)"), "perimeter")
        assertClose(sqrt3, quantity(r, "Apothem (unit)"), message = "apothem = √3")
        exact("9", quantity(r, "Diagonals (count)"), "diagonals")
        exact("120", quantity(r, "Interior angle (°)"), "interior angle")
        exact("60", quantity(r, "Exterior angle (°)"), "exterior angle")
    }

    @Test fun regularHexagonFromCircumradius() {
        val r = okValue(ShapeEngine.regularPolygonFromCircumradius(6, bd("2")))
        assertClose(sqrt3, quantity(r, "Apothem (unit)"), message = "apothem")
        assertClose(bd("6").multiply(sqrt3), r.primary!!, message = "area = 6√3")
        assertClose(bd("12"), quantity(r, "Perimeter (unit)"), 1e-9, "perimeter")
        exact("2", quantity(r, "Circumradius (unit)"), "circumradius stays the input")
    }

    @Test fun regularPolygonRejectsTooFewSides() {
        rejected(ShapeEngine.regularPolygonFromSide(2, bd("1")), ErrorKind.Domain, "n = 2")
        rejected(ShapeEngine.regularPolygonFromCircumradius(0, bd("1")), ErrorKind.Domain, "n = 0")
        rejected(ShapeEngine.regularPolygonFromSide(6, bd("0")), ErrorKind.InvalidInput, "zero side")
    }

    @Test fun triangleShapeAreaCheckAgainstSides() {
        // 5-5-6 isosceles: height √(25 − 9) = 4, area 12, apex angle acos(14/50).
        val r = okValue(ShapeEngine.triangleThreeSides(bd("5"), bd("5"), bd("6")))
        exact("12", r.primary!!, "area")
        assertClose(
            BigDecimal.valueOf(Math.toDegrees(kotlin.math.acos(0.28))),
            quantity(r, "Angle C (opposite c) (°)"),
            1e-9,
            "apex angle",
        )
    }

    // -------------------------------------------------------------- validation

    @Test fun zeroAndNegativeDimensionsRejectedEverywhere() {
        rejected(ShapeEngine.square(bd("0")), ErrorKind.InvalidInput, "square 0")
        rejected(ShapeEngine.square(bd("-3")), ErrorKind.InvalidInput, "square -3")
        rejected(ShapeEngine.rectangle(bd("0"), bd("4")), ErrorKind.InvalidInput, "rectangle 0")
        rejected(ShapeEngine.rectangle(bd("3"), bd("-4")), ErrorKind.InvalidInput, "rectangle -4")
        rejected(ShapeEngine.triangleBaseHeight(bd("0"), bd("4")), ErrorKind.InvalidInput, "triangle 0 base")
        rejected(ShapeEngine.triangleBaseHeight(bd("6"), bd("-4")), ErrorKind.InvalidInput, "triangle -4 height")
        rejected(ShapeEngine.triangleThreeSides(bd("0"), bd("4"), bd("5")), ErrorKind.InvalidInput, "zero side")
        rejected(ShapeEngine.equilateralTriangle(bd("0")), ErrorKind.InvalidInput, "equilateral 0")
        rejected(ShapeEngine.parallelogram(bd("0"), bd("3")), ErrorKind.InvalidInput, "parallelogram 0")
        rejected(ShapeEngine.rhombusFromDiagonals(bd("6"), bd("0")), ErrorKind.InvalidInput, "rhombus 0")
        rejected(ShapeEngine.trapezoid(bd("4"), bd("6"), bd("0")), ErrorKind.InvalidInput, "trapezoid 0 height")
        rejected(ShapeEngine.trapezoid(bd("4"), bd("6"), bd("3"), bd("-1")), ErrorKind.InvalidInput, "negative leg")
        rejected(ShapeEngine.circle(CircleMode.RADIUS, bd("0")), ErrorKind.InvalidInput, "circle 0")
        rejected(ShapeEngine.circle(CircleMode.DIAMETER, bd("-4")), ErrorKind.InvalidInput, "circle -4")
        rejected(ShapeEngine.semicircle(bd("0")), ErrorKind.InvalidInput, "semicircle 0")
        rejected(ShapeEngine.ellipse(bd("0"), bd("2")), ErrorKind.InvalidInput, "ellipse 0")
        rejected(ShapeEngine.ellipse(bd("3"), bd("-2")), ErrorKind.InvalidInput, "ellipse -2")
        rejected(ShapeEngine.regularPolygonFromSide(4, bd("0")), ErrorKind.InvalidInput, "polygon 0")
        rejected(ShapeEngine.regularPolygonFromCircumradius(4, bd("-1")), ErrorKind.InvalidInput, "polygon -1")
    }

    @Test fun everyShapeProducesSteps() {
        val results = listOf(
            ShapeEngine.square(bd("3")),
            ShapeEngine.rectangle(bd("3"), bd("4")),
            ShapeEngine.triangleBaseHeight(bd("6"), bd("4")),
            ShapeEngine.triangleThreeSides(bd("3"), bd("4"), bd("5")),
            ShapeEngine.triangleTwoSidesAngle(bd("3"), bd("4"), bd("60")),
            ShapeEngine.equilateralTriangle(bd("2")),
            ShapeEngine.parallelogram(bd("5"), bd("3"), bd("4")),
            ShapeEngine.rhombusFromDiagonals(bd("6"), bd("8")),
            ShapeEngine.rhombusFromSideAngle(bd("5"), bd("60")),
            ShapeEngine.trapezoid(bd("4"), bd("6"), bd("3")),
            ShapeEngine.circle(CircleMode.RADIUS, bd("2")),
            ShapeEngine.semicircle(bd("2")),
            ShapeEngine.ellipse(bd("3"), bd("2")),
            ShapeEngine.regularPolygonFromSide(5, bd("2")),
            ShapeEngine.regularPolygonFromCircumradius(5, bd("2")),
        )
        results.forEachIndexed { index, value ->
            val r = okValue(value)
            assertTrue("shape #$index has no steps", r.steps.isNotEmpty())
            assertTrue("shape #$index has empty steps", r.steps.all { it.isNotBlank() })
            assertTrue("shape #$index has no primary", r.primary != null)
        }
    }

    @Test fun labelConventionsUseUnitPlaceholder() {
        val r = okValue(ShapeEngine.circle(CircleMode.RADIUS, bd("1")))
        assertTrue(
            "labels should use the 'unit' placeholder: ${r.quantities.map { it.first }}",
            r.quantities.all { it.first.contains("unit") || it.first.contains("°") || it.first.contains("count") },
        )
    }
}
