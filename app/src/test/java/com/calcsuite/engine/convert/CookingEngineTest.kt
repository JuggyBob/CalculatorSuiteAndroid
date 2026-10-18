package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

private fun assertClose(expected: String, actual: BigDecimal, tol: String) {
    assertTrue("expected $expected ± $tol but was $actual", (actual - BigDecimal(expected)).abs() < BigDecimal(tol))
}

private fun expectError(kind: ErrorKind, result: CalcResult<*>, messagePart: String? = null) {
    assertTrue("expected Err but was $result", result is CalcResult.Err)
    val error = (result as CalcResult.Err).error
    assertEquals(kind, error.kind)
    if (messagePart != null) {
        assertTrue("message '${error.message}' should contain '$messagePart'", error.message.contains(messagePart))
    }
}

class CookingEngineTest {

    @Test
    fun oneUsCupIsTwoThirtySixPointFiveEightEightTwoThreeSixFiveMillilitres() {
        assertExact("236.5882365", CookingEngine.convert(BigDecimal.ONE, "cup_us", "mL").valueOrNull()!!)
    }

    @Test
    fun oneMetricCupIsTwoHundredAndFiftyMillilitres() {
        assertExact("250", CookingEngine.convert(BigDecimal.ONE, "cup_metric", "mL").valueOrNull()!!)
    }

    @Test
    fun oneImperialCupIsTwoEightyFourPointOneThreeZeroSixTwoFiveMillilitres() {
        assertExact("284.130625", CookingEngine.convert(BigDecimal.ONE, "cup_imp", "mL").valueOrNull()!!)
    }

    @Test
    fun oneUsTablespoonIsThreeUsTeaspoons() {
        assertExact("3", CookingEngine.convert(BigDecimal.ONE, "tbsp_us", "tsp_us").valueOrNull()!!)
    }

    @Test
    fun oneMetricTablespoonIsFifteenMillilitres() {
        assertExact("15", CookingEngine.convert(BigDecimal.ONE, "tbsp_metric", "mL").valueOrNull()!!)
    }

    @Test
    fun oneUsTeaspoonIsTheExactUsValue() {
        assertExact("4.92892159375", CookingEngine.convert(BigDecimal.ONE, "tsp_us", "mL").valueOrNull()!!)
    }

    @Test
    fun oneUsGallonIsSixteenUsCups() {
        assertExact("16", CookingEngine.convert(BigDecimal.ONE, "gal_us", "cup_us").valueOrNull()!!)
    }

    @Test
    fun oneImperialPintIsTwentyImperialFluidOunces() {
        assertExact("20", CookingEngine.convert(BigDecimal.ONE, "pt_imp", "floz_imp").valueOrNull()!!)
    }

    @Test
    fun oneLitreIsFourUsCups() {
        assertClose("4.2267528377", CookingEngine.convert(BigDecimal.ONE, "L", "cup_us").valueOrNull()!!, "1e-9")
    }

    @Test
    fun onePoundIsTheExactMassValue() {
        assertExact("453.59237", CookingEngine.convert(BigDecimal.ONE, "lb", "g").valueOrNull()!!)
    }

    @Test
    fun oneOunceInGrams() {
        assertExact("28.349523125", CookingEngine.convert(BigDecimal.ONE, "oz", "g").valueOrNull()!!)
    }

    @Test
    fun oneKilogramIsOneThousandGrams() {
        assertExact("1000", CookingEngine.convert(BigDecimal.ONE, "kg", "g").valueOrNull()!!)
    }

    @Test
    fun gramsToCupsIsRejectedWithoutADensity() {
        val result = CookingEngine.convert(BigDecimal("100"), "g", "cup_us")
        expectError(ErrorKind.Inconsistent, result, "requires an ingredient density")
    }

    @Test
    fun cupsToGramsIsAlsoRejectedWithoutADensity() {
        val result = CookingEngine.convert(BigDecimal.ONE, "cup_us", "g")
        expectError(ErrorKind.Inconsistent, result, "requires an ingredient density")
    }

    @Test
    fun flourAtPointFiveThreeDensityMakesOneCupAboutOneHundredAndTwentyFiveGrams() {
        val result = CookingEngine.convertWithDensity(
            value = BigDecimal.ONE,
            fromId = "cup_us",
            toId = "g",
            densityGPerMl = BigDecimal("0.53"),
        ).valueOrNull()!!
        assertClose("125", result, "0.5")
        assertExact("125.391765345", result)
    }

    @Test
    fun waterIsOneToOneBetweenMillilitresAndGrams() {
        assertExact("250", CookingEngine.convertWithDensity(BigDecimal("250"), "mL", "g", BigDecimal.ONE).valueOrNull()!!)
        assertExact("250", CookingEngine.convertWithDensity(BigDecimal("250"), "g", "mL", BigDecimal.ONE).valueOrNull()!!)
    }

    @Test
    fun honeyIsHeavierThanWater() {
        val honey = CookingEngine.convertWithDensity(BigDecimal.ONE, "cup_us", "g", BigDecimal("1.42")).valueOrNull()!!
        val water = CookingEngine.convertWithDensity(BigDecimal.ONE, "cup_us", "g", BigDecimal.ONE).valueOrNull()!!
        assertExact("335.95529583", honey)
        assertTrue(honey.compareTo(water) > 0)
    }

    @Test
    fun massToVolumeUsesTheDensityInTheOtherDirection() {
        val result = CookingEngine.convertWithDensity(
            value = BigDecimal("250"),
            fromId = "g",
            toId = "mL",
            densityGPerMl = BigDecimal("0.53"),
        ).valueOrNull()!!
        assertClose("471.6981132075", result, "1e-6")
    }

    @Test
    fun densityIsIgnoredForSameDimensionConversions() {
        assertExact("1", CookingEngine.convertWithDensity(BigDecimal("1000"), "mL", "L", BigDecimal.ZERO).valueOrNull()!!)
    }

    @Test
    fun zeroDensityIsRejectedForCrossDimensionConversions() {
        val result = CookingEngine.convertWithDensity(BigDecimal.ONE, "cup_us", "g", BigDecimal.ZERO)
        expectError(ErrorKind.Domain, result, "Density must be greater than zero")
    }

    @Test
    fun unknownUnitsAreRejected() {
        expectError(ErrorKind.InvalidInput, CookingEngine.convert(BigDecimal.ONE, "cup_us", "smidgen"), "Unknown target")
        expectError(ErrorKind.InvalidInput, CookingEngine.convert(BigDecimal.ONE, "smidgen", "cup_us"), "Unknown source")
        expectError(
            ErrorKind.InvalidInput,
            CookingEngine.convertWithDensity(BigDecimal.ONE, "g", "smidgen", BigDecimal.ONE),
            "Unknown",
        )
    }

    @Test
    fun theDensityListContainsTheRequiredIngredients() {
        assertEquals(19, CookingEngine.ingredients.size)
        assertExact("0.53", CookingEngine.ingredient("Flour, all-purpose")!!.densityGPerMl)
        assertExact("1.0", CookingEngine.ingredient("water")!!.densityGPerMl)
        assertExact("1.42", CookingEngine.ingredient("Honey")!!.densityGPerMl)
        assertEquals(null, CookingEngine.ingredient("unobtainium"))
    }

    @Test
    fun theUnitListCoversBothDimensions() {
        assertEquals(21, CookingEngine.units.size)
        assertEquals(CookingEngine.Dimension.VOLUME, CookingEngine.unit("cup_us")!!.dimension)
        assertEquals(CookingEngine.Dimension.MASS, CookingEngine.unit("g")!!.dimension)
        assertEquals(null, CookingEngine.unit("hogshead"))
    }

    @Test
    fun notesCiteTheSourceAndThePackingCaveat() {
        val notes = CookingEngine.notes.joinToString(" ")
        assertTrue(notes.contains("USDA / King Arthur Baking"))
        assertTrue(notes.contains("requires a density"))
    }
}
