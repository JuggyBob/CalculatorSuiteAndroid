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

class BodyEngineTest {

    private val sqrt2 = BigDecimal("1.4142135623730950488016887242096980786")
    private val sqrt3 = BigDecimal("1.7320508075688772935274463415058723669")
    private val sqrt13 = BigDecimal("3.6055512754639892931192212674704959463")
    private val sqrt17 = BigDecimal("4.1231056256176605498214098559740770251")
    private val sqrt29 = BigDecimal("5.3851648071345040312507104915403295563")

    private fun bd(text: String): BigDecimal = BigDecimal(text)

    private fun okValue(result: CalcResult<BodyResult>): BodyResult {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<BodyResult>): CalcError {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error
    }

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

    private fun quantity(result: BodyResult, label: String): BigDecimal =
        result.quantities.firstOrNull { it.first == label }?.second
            ?: throw AssertionError("Missing \"$label\" in ${result.quantities.map { it.first }}")

    private fun exact(expected: String, actual: BigDecimal, message: String = "") {
        assertEquals("$message expected exactly $expected, was $actual", 0, actual.compareTo(bd(expected)))
    }

    private fun rejected(actual: CalcResult<BodyResult>, kind: ErrorKind, message: String = "") {
        val err = error(actual)
        assertEquals("$message kind for ${err.message}", kind, err.kind)
    }

    // -------------------------------------------------------------------- cube

    @Test fun cubeEdgeThree() {
        val r = okValue(BodyEngine.cube(bd("3")))
        exact("27", r.primary!!, "volume")
        exact("54", quantity(r, "Total surface area (unit²)"), "surface")
        exact("36", quantity(r, "Lateral surface area (unit²)"), "lateral")
        assertClose(bd("3").multiply(sqrt2), quantity(r, "Face diagonal (unit)"), message = "face diagonal")
        assertClose(bd("3").multiply(sqrt3), quantity(r, "Space diagonal (unit)"), message = "space diagonal")
    }

    // ------------------------------------------------------- rectangular prism

    @Test fun rectangularPrismTwoThreeFour() {
        val r = okValue(BodyEngine.rectangularPrism(bd("2"), bd("3"), bd("4")))
        exact("24", r.primary!!, "volume")
        exact("52", quantity(r, "Total surface area (unit²)"), "surface")
        exact("40", quantity(r, "Lateral surface area (unit²)"), "lateral")
        exact("6", quantity(r, "Base area (unit²)"), "base")
        assertClose(sqrt29, quantity(r, "Space diagonal (unit)"), message = "space diagonal √29")
    }

    // ------------------------------------------------------------------ sphere

    @Test fun sphereRadiusTwo() {
        val r = okValue(BodyEngine.sphere(bd("2")))
        assertClose(
            Consts.PI.multiply(bd("32")).divide(bd("3"), MathContext(30)),
            r.primary!!,
            message = "volume = 32π/3",
        )
        assertClose(Consts.PI.multiply(bd("16")), quantity(r, "Surface area (unit²)"), message = "surface = 16π")
        assertClose(Consts.PI.multiply(bd("4")), quantity(r, "Great-circle circumference (unit)"), message = "2πr")
    }

    // -------------------------------------------------------------- hemisphere

    @Test fun hemisphereRadiusThree() {
        val r = okValue(BodyEngine.hemisphere(bd("3")))
        assertClose(Consts.PI.multiply(bd("18")), r.primary!!, message = "volume = 18π")
        assertClose(Consts.PI.multiply(bd("27")), quantity(r, "Total surface area (unit²)"), message = "total = 27π")
        assertClose(Consts.PI.multiply(bd("9")), quantity(r, "Base area (unit²)"), message = "base = 9π")
        assertClose(
            Consts.PI.multiply(bd("18")),
            quantity(r, "Curved surface area (unit²)"),
            message = "curved = 18π",
        )
    }

    // ----------------------------------------------------------------- cylinder

    @Test fun cylinderRadiusTwoHeightFive() {
        val r = okValue(BodyEngine.cylinder(bd("2"), bd("5")))
        assertClose(Consts.PI.multiply(bd("20")), r.primary!!, message = "volume = 20π")
        assertClose(Consts.PI.multiply(bd("20")), quantity(r, "Lateral surface area (unit²)"), message = "lateral = 20π")
        assertClose(Consts.PI.multiply(bd("28")), quantity(r, "Total surface area (unit²)"), message = "total = 28π")
        assertClose(Consts.PI.multiply(bd("4")), quantity(r, "Base area (unit²)"), message = "base = 4π")
    }

    // --------------------------------------------------------------------- cone

    @Test fun coneRadiusThreeHeightFour() {
        val r = okValue(BodyEngine.cone(bd("3"), bd("4")))
        exact("5", quantity(r, "Slant height (unit)"), "slant 3-4-5")
        assertClose(Consts.PI.multiply(bd("12")), r.primary!!, message = "volume = 12π")
        assertClose(Consts.PI.multiply(bd("15")), quantity(r, "Lateral surface area (unit²)"), message = "lateral = 15π")
        assertClose(Consts.PI.multiply(bd("24")), quantity(r, "Total surface area (unit²)"), message = "total = 24π")
        assertClose(Consts.PI.multiply(bd("9")), quantity(r, "Base area (unit²)"), message = "base = 9π")
        assertClose(
            BigDecimal.valueOf(2 * Math.toDegrees(kotlin.math.atan(0.75))),
            quantity(r, "Apex angle (°)"),
            1e-9,
            "apex angle = 2·atan(r/h)",
        )
    }

    // ---------------------------------------------------------- square pyramid

    @Test fun squarePyramidBaseFourHeightThree() {
        val r = okValue(BodyEngine.squarePyramid(bd("4"), bd("3")))
        assertClose(sqrt13, quantity(r, "Slant height (unit)"), message = "slant = √13")
        exact("16", r.primary!!, "volume")
        assertClose(bd("8").multiply(sqrt13), quantity(r, "Lateral surface area (unit²)"), message = "lateral = 8√13")
        assertClose(
            bd("16").add(bd("8").multiply(sqrt13)),
            quantity(r, "Total surface area (unit²)"),
            message = "total = 16 + 8√13",
        )
        assertClose(sqrt17, quantity(r, "Lateral edge (unit)"), message = "lateral edge = √17")
    }

    // -------------------------------------------------------- triangular prism

    @Test fun triangularPrismBaseTwoHeightFive() {
        val r = okValue(BodyEngine.triangularPrism(bd("2"), bd("5")))
        assertClose(sqrt3, quantity(r, "Base area (unit²)"), message = "base = √3")
        assertClose(bd("5").multiply(sqrt3), r.primary!!, message = "volume = 5√3")
        exact("30", quantity(r, "Lateral surface area (unit²)"), "lateral = 30")
        assertClose(bd("30").add(bd("2").multiply(sqrt3)), quantity(r, "Total surface area (unit²)"), message = "total")
    }

    // ------------------------------------------------------ triangular pyramid

    @Test fun regularTetrahedronEdgeTwo() {
        val r = okValue(BodyEngine.regularTetrahedron(bd("2")))
        assertClose(
            bd("2").multiply(sqrt2).divide(bd("3"), MathContext(30)),
            r.primary!!,
            message = "volume = (2√2)/3",
        )
        assertClose(bd("4").multiply(sqrt3), quantity(r, "Surface area (unit²)"), message = "surface = 4√3")
        assertClose(sqrt3, quantity(r, "Face area (unit²)"), message = "face = √3")
        assertClose(
            bd("2").multiply(BigDecimal("2.4494897427831780981972840747058913919")).divide(bd("3"), MathContext(30)),
            quantity(r, "Height (unit)"),
            message = "height = a√6/3",
        )
    }

    @Test fun generalTriangularPyramid() {
        val r = okValue(BodyEngine.triangularPyramid(bd("3"), bd("4"), bd("5"), bd("6")))
        exact("6", quantity(r, "Base area (unit²)"), "base area Heron 3-4-5")
        exact("12", r.primary!!, "volume = base×h/3")
        exact("12", quantity(r, "Base perimeter (unit)"), "base perimeter")
        assertTrue("lateral surface must be unavailable", r.unavailable.any { it.startsWith("Lateral surface") })
        assertTrue("total surface must be unavailable", r.unavailable.any { it.startsWith("Total surface") })
    }

    @Test fun triangularPyramidRejectsInvalidBase() {
        val err = error(BodyEngine.triangularPyramid(bd("1"), bd("2"), bd("5"), bd("3")))
        assertEquals(ErrorKind.Domain, err.kind)
        assertTrue("message should name the failing sum: ${err.message}", err.message.contains("1 + 2"))
        rejected(BodyEngine.triangularPyramid(bd("2"), bd("3"), bd("5"), bd("3")), ErrorKind.Domain, "degenerate base")
    }

    // -------------------------------------------------------------- validation

    @Test fun zeroAndNegativeDimensionsRejectedEverywhere() {
        rejected(BodyEngine.cube(bd("0")), ErrorKind.InvalidInput, "cube 0")
        rejected(BodyEngine.cube(bd("-3")), ErrorKind.InvalidInput, "cube -3")
        rejected(BodyEngine.rectangularPrism(bd("0"), bd("3"), bd("4")), ErrorKind.InvalidInput, "prism 0")
        rejected(BodyEngine.rectangularPrism(bd("2"), bd("-3"), bd("4")), ErrorKind.InvalidInput, "prism -3")
        rejected(BodyEngine.sphere(bd("0")), ErrorKind.InvalidInput, "sphere 0")
        rejected(BodyEngine.sphere(bd("-2")), ErrorKind.InvalidInput, "sphere -2")
        rejected(BodyEngine.hemisphere(bd("0")), ErrorKind.InvalidInput, "hemisphere 0")
        rejected(BodyEngine.cylinder(bd("2"), bd("0")), ErrorKind.InvalidInput, "cylinder 0 height")
        rejected(BodyEngine.cylinder(bd("-2"), bd("5")), ErrorKind.InvalidInput, "cylinder -2")
        rejected(BodyEngine.cone(bd("0"), bd("4")), ErrorKind.InvalidInput, "cone 0")
        rejected(BodyEngine.cone(bd("3"), bd("-4")), ErrorKind.InvalidInput, "cone -4")
        rejected(BodyEngine.squarePyramid(bd("0"), bd("3")), ErrorKind.InvalidInput, "pyramid 0")
        rejected(BodyEngine.squarePyramid(bd("4"), bd("0")), ErrorKind.InvalidInput, "pyramid 0 height")
        rejected(BodyEngine.triangularPrism(bd("0"), bd("5")), ErrorKind.InvalidInput, "triangular prism 0")
        rejected(BodyEngine.triangularPrism(bd("2"), bd("-5")), ErrorKind.InvalidInput, "triangular prism -5")
        rejected(BodyEngine.regularTetrahedron(bd("0")), ErrorKind.InvalidInput, "tetrahedron 0")
        rejected(BodyEngine.triangularPyramid(bd("0"), bd("4"), bd("5"), bd("6")), ErrorKind.InvalidInput, "pyramid 0 side")
        rejected(BodyEngine.triangularPyramid(bd("3"), bd("4"), bd("5"), bd("-6")), ErrorKind.InvalidInput, "pyramid -6 height")
    }

    @Test fun everyBodyProducesVolumeSurfaceAndSteps() {
        val results = listOf(
            BodyEngine.cube(bd("3")),
            BodyEngine.rectangularPrism(bd("2"), bd("3"), bd("4")),
            BodyEngine.sphere(bd("2")),
            BodyEngine.hemisphere(bd("3")),
            BodyEngine.cylinder(bd("2"), bd("5")),
            BodyEngine.cone(bd("3"), bd("4")),
            BodyEngine.squarePyramid(bd("4"), bd("3")),
            BodyEngine.triangularPrism(bd("2"), bd("5")),
            BodyEngine.regularTetrahedron(bd("2")),
            BodyEngine.triangularPyramid(bd("3"), bd("4"), bd("5"), bd("6")),
        )
        results.forEachIndexed { index, value ->
            val r = okValue(value)
            assertTrue("body #$index has no primary", r.primary != null)
            assertTrue("body #$index has no steps", r.steps.isNotEmpty())
            assertTrue("body #$index has blank steps", r.steps.all { it.isNotBlank() })
            assertTrue(
                "body #$index should report an area quantity",
                r.quantities.any { it.first.contains("area") },
            )
        }
        // Every one of the nine body kinds is covered above.
        assertEquals(9, BodyKind.entries.size)
    }

    @Test fun labelsNeverMixLinearAndSquaredOrCubedUnits() {
        val results = listOf(
            BodyEngine.cube(bd("1")),
            BodyEngine.rectangularPrism(bd("1"), bd("2"), bd("3")),
            BodyEngine.sphere(bd("1")),
            BodyEngine.hemisphere(bd("1")),
            BodyEngine.cylinder(bd("1"), bd("2")),
            BodyEngine.cone(bd("1"), bd("2")),
            BodyEngine.squarePyramid(bd("2"), bd("1")),
            BodyEngine.triangularPrism(bd("2"), bd("1")),
            BodyEngine.regularTetrahedron(bd("2")),
            BodyEngine.triangularPyramid(bd("3"), bd("4"), bd("5"), bd("2")),
        )
        results.forEach { value ->
            val r = okValue(value)
            r.quantities.forEach { (label, _) ->
                assertTrue(
                    "label \"$label\" must carry exactly one dimension marker",
                    label.contains("unit²") || label.contains("unit)") || label.contains("(°)"),
                )
            }
        }
    }
}
