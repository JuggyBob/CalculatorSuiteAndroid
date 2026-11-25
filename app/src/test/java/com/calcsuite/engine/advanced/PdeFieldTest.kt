package com.calcsuite.engine.advanced

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.calcsuite.ui.tools.calculus.fieldArgbPixels
import com.calcsuite.ui.tools.calculus.fieldScale
import com.calcsuite.ui.theme.fromHsl
import com.calcsuite.ui.theme.toHsl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the two-dimensional field and the picture of it.
 *
 * The steady states are checked against the answers that are known exactly:
 * a constant boundary has a constant steady state, a straight boundary has
 * a harmonic one, and a point source has the bowl.
 */
class PdeFieldTest {

    /** Parses an expression and reads it, the way the tool does. */
    private fun node(text: String) =
        (com.calcsuite.engine.calculus.CalculusParser.parse(text)
            as com.calcsuite.core.CalcResult.Ok).value

    private fun solve(
        kind: PdeKind,
        boundary: String,
        source: String? = null,
        points: Int = 15,
    ): PdeSolution2D? = PdeEngine.solve2D(
        kind = kind,
        boundary = node(boundary),
        source = source?.let { node(it) },
        xVar = "x",
        yVar = "y",
        size = 1.0,
        gridPoints = points,
        iterations = 2000,
    )

    @Test fun aConstantBoundaryHasAConstantSteadyState() {
        // Laplace's equation with the same value all the way round has the
        // same value all the way in: the steady state is that constant.
        for (constant in listOf("0", "1", "-3.5")) {
            val field = solve(PdeKind.LAPLACE, constant)!!
            for (row in field.values) {
                for (v in row) {
                    // The numbers come from a relaxation that stops when the
                    // change is small, so the answer is the constant to
                    // within the solver's own tolerance.
                    assertEquals(
                        "the steady state of Laplace with the boundary $constant",
                        constant.toDouble(),
                        v,
                        1e-6,
                    )
                }
            }
            assertTrue(
                "the field with the boundary $constant is constant",
                PdeEngine.isConstant(field.values),
            )
        }
    }

    @Test fun aVaryingBoundaryGivesAVaryingField() {
        val field = solve(PdeKind.LAPLACE, "sin(pi*x)")!!
        assertFalse(
            "the field with a varying boundary must not be constant",
            PdeEngine.isConstant(field.values),
        )
        // The corners are cold and the middle of the sides is hot, because
        // the boundary is sin(pi*x) and it is zero at both ends.
        val middle = field.values[0][field.values[0].size / 2]
        val corner = field.values[0][0]
        assertTrue(
            "the middle of the side should be hotter than the corner " +
                "($middle against $corner)",
            middle > corner + 0.5,
        )
    }

    @Test fun theRangeOfAConstantFieldIsThatValue() {
        // The range is the true one: a field of zeros has the range 0 to 0,
        // and not a made-up 0 to 1.
        val field = solve(PdeKind.LAPLACE, "0")!!
        val range = PdeEngine.valueRange(field.values)
        assertEquals("the lowest value", 0.0, range.first, 1e-12)
        assertEquals("the highest value", 0.0, range.second, 1e-12)

        // With a boundary of one, the relaxation leaves the field a little
        // short of one, and the range says so rather than inventing one.
        val other = solve(PdeKind.LAPLACE, "1")!!
        val otherRange = PdeEngine.valueRange(other.values)
        assertEquals("the lowest value", 1.0, otherRange.first, 1e-6)
        assertEquals("the highest value", 1.0, otherRange.second, 1e-6)
    }

    @Test fun theRangeOfAVaryingFieldIsTheTrueOne() {
        val field = solve(PdeKind.LAPLACE, "x")!!
        val range = PdeEngine.valueRange(field.values)
        assertEquals("the lowest value", 0.0, range.first, 1e-9)
        assertEquals("the highest value", 1.0, range.second, 1e-9)
        assertFalse(
            "the field must not be constant",
            PdeEngine.isConstant(field.values),
        )
    }

    @Test fun aPartlySettledFlatFieldStillCountsAsFlat() {
        // The tool's own numbers: 500 sweeps on a 30 by 30 grid, which is not
        // quite enough to settle a constant boundary right down, so the
        // field is flat to within a fraction of a percent rather than
        // exactly. The picture is still one colour, so the tool has to say
        // so.
        val field = PdeEngine.solve2D(
            kind = PdeKind.LAPLACE,
            boundary = node("1"),
            source = null,
            xVar = "x",
            yVar = "y",
            size = 1.0,
            gridPoints = 30,
            iterations = 500,
        )!!
        val range = PdeEngine.valueRange(field.values)
        assertTrue(
            "the field should be nearly one everywhere but ran from " +
                "${range.first} to ${range.second}",
            PdeEngine.isConstant(field.values),
        )
        assertTrue(
            "the values should be near one",
            range.first > 0.99 && range.second <= 1.0 + 1e-9,
        )
    }

    @Test fun aSourceMakesTheFieldVaryEvenWithAConstantBoundary() {
        val field = solve(PdeKind.POISSON, "0", "1")!!
        assertFalse(
            "a source must make the field vary",
            PdeEngine.isConstant(field.values),
        )
    }

    @Test fun theColoursOfTheFieldFollowTheAccent() {
        // The low end of the scale is a dark shade of the accent, so the
        // picture is in the app's own palette.
        val accent = Color(0xFF4ADE80)
        val tertiary = Color(0xFFFFB784)
        val (low, middle, high) = fieldScale(accent, tertiary)
        assertEquals("the middle of the scale is the accent", accent, middle)
        assertEquals("the top of the scale is the third colour", tertiary, high)
        val lowHsl = low.toHsl()
        val accentHsl = accent.toHsl()
        assertEquals(
            "the low end keeps the accent's hue",
            accentHsl.hue,
            lowHsl.hue,
            0.02f,
        )
        assertTrue(
            "the low end is darker than the accent",
            lowHsl.lightness < accentHsl.lightness,
        )
    }

    @Test fun theScaleKeepsTheHueForAnyAccent() {
        for (accent in listOf(Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFF472B6))) {
            val (low, _, _) = fieldScale(accent, Color(0xFFFFB784))
            val lowHsl = low.toHsl()
            val accentHsl = accent.toHsl()
            assertEquals(
                "the low end keeps the hue of $accent",
                accentHsl.hue,
                lowHsl.hue,
                0.02f,
            )
            // The colour is a real colour, not a grey.
            assertTrue(
                "the low end of $accent must not be grey",
                lowHsl.saturation > 0.3f,
            )
        }
    }

    @Test fun theRoundTripOfTheColourHelpersHolds() {
        // The helpers the scale is built on have to keep the colour they
        // are given, or the picture would drift.
        for (colour in listOf(Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFF472B6))) {
            val hsl = colour.toHsl()
            val back = fromHsl(hsl.hue, hsl.saturation, hsl.lightness)
            assertEquals("the red of $colour", colour.red, back.red, 0.01f)
            assertEquals("the green of $colour", colour.green, back.green, 0.01f)
            assertEquals("the blue of $colour", colour.blue, back.blue, 0.01f)
        }
    }

    private val bitmapScale = Triple(
        Color(0xFF000000),
        Color(0xFF808080),
        Color(0xFFFFFFFF),
    )

    @Test fun theBitmapUsesTheSameColoursAtTheSamePlacesOnTheScale() {
        // Three columns with values 0, 0.5 and 1 give exactly the three
        // colours of the scale: the colour maths must not have changed when
        // the rectangles became pixels.
        val pixels = fieldArgbPixels(
            rows = 1,
            columns = 3,
            value = { _, column -> column * 0.5 },
            pixelWidth = 3,
            pixelHeight = 1,
            scale = bitmapScale,
        )
        assertEquals(3, pixels.size)
        assertEquals("the low end", bitmapScale.first.toArgb(), pixels[0])
        assertEquals("the middle", bitmapScale.second.toArgb(), pixels[1])
        assertEquals("the high end", bitmapScale.third.toArgb(), pixels[2])
    }

    @Test fun theBitmapKeepsRowZeroAtTheBottom() {
        // Row 0 of a field is drawn at the bottom, exactly as the rectangles
        // were: the first pixel row is field row rows - 1.
        val pixels = fieldArgbPixels(
            rows = 2,
            columns = 3,
            value = { row, _ -> if (row == 0) 0.0 else 1.0 },
            pixelWidth = 3,
            pixelHeight = 2,
            scale = bitmapScale,
        )
        assertEquals(6, pixels.size)
        assertEquals("the top-left pixel", bitmapScale.third.toArgb(), pixels[0])
        assertEquals("the top-right pixel", bitmapScale.third.toArgb(), pixels[2])
        assertEquals("the bottom-left pixel", bitmapScale.first.toArgb(), pixels[3])
        assertEquals("the bottom-right pixel", bitmapScale.first.toArgb(), pixels[5])
    }

    @Test fun theBitmapIsSampledAtMostAtPixelResolution() {
        // A large field sampled into a small canvas makes one pixel per
        // canvas pixel, taking the cell nearest each pixel's centre: the
        // top-left pixel of a 7 by 5 sample reads row 360, column 28.
        val pixels = fieldArgbPixels(
            rows = 400,
            columns = 400,
            value = { row, column -> (row * 400 + column).toDouble() },
            pixelWidth = 7,
            pixelHeight = 5,
            scale = bitmapScale,
        )
        assertEquals(35, pixels.size)
        val topLeft = Color(pixels[0])
        val bottomRight = Color(pixels[34])
        assertTrue(
            "the top of the picture must be the high end of the field",
            topLeft.red > bottomRight.red + 0.3f,
        )
        assertTrue("the sampled colour must be opaque", topLeft.alpha == 1f)
    }
}
