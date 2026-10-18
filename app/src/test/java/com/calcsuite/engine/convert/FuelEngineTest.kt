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

class FuelEngineTest {

    @Test
    fun sevenPointEightFourLitresPerHundredIsAboutThirtyMpgUs() {
        val result = FuelEngine.convert(BigDecimal("7.84"), "l_100km", "mpg_us").valueOrNull()!!
        assertClose("30", result, "0.05")
    }

    @Test
    fun thirtyMpgUsIsAboutThirtySixPointOneMpgImperial() {
        val result = FuelEngine.convert(BigDecimal("30"), "mpg_us", "mpg_uk").valueOrNull()!!
        assertClose("36.1", result, "0.1")
    }

    @Test
    fun sixPointSixSevenLitresPerHundredIsAboutFifteenKilometresPerLitre() {
        val result = FuelEngine.convert(BigDecimal("6.67"), "l_100km", "km_l").valueOrNull()!!
        assertClose("15", result, "0.01")
    }

    @Test
    fun thirtyMpgUsIsAboutSevenPointEightFourLitresPerHundred() {
        val result = FuelEngine.convert(BigDecimal("30"), "mpg_us", "l_100km").valueOrNull()!!
        assertClose("7.84", result, "0.01")
    }

    @Test
    fun fifteenKilometresPerLitreIsSixPointSixSevenLitresPerHundred() {
        val result = FuelEngine.convert(BigDecimal("15"), "km_l", "l_100km").valueOrNull()!!
        assertClose("6.67", result, "0.01")
    }

    @Test
    fun canonsicalFormIsIdentity() {
        assertExact("7.84", FuelEngine.convert(BigDecimal("7.84"), "l_100km", "l_100km").valueOrNull()!!)
    }

    @Test
    fun oneMpgImperialInLitresPerHundred() {
        val result = FuelEngine.convert(BigDecimal.ONE, "mpg_uk", "l_100km").valueOrNull()!!
        assertClose("282.4809363318221585938121371192237", result, "1e-9")
    }

    @Test
    fun oneMpgUsInLitresPerHundred() {
        val result = FuelEngine.convert(BigDecimal.ONE, "mpg_us", "l_100km").valueOrNull()!!
        assertClose("235.2145833333333333333333333333333", result, "1e-9")
    }

    @Test
    fun oneMilePerLitreInLitresPerHundred() {
        val result = FuelEngine.convert(BigDecimal.ONE, "mi_l", "l_100km").valueOrNull()!!
        assertClose("62.1371192237334", result, "1e-9")
    }

    @Test
    fun betterMileageMeansFewerLitresPerHundred() {
        val thirsty = FuelEngine.convert(BigDecimal("20"), "mpg_us", "l_100km").valueOrNull()!!
        val efficient = FuelEngine.convert(BigDecimal("40"), "mpg_us", "l_100km").valueOrNull()!!
        assertTrue(efficient.compareTo(thirsty) < 0)
        assertClose("11.76072916666666666666666666666667", thirsty, "1e-9")
        assertClose("5.8803645823556945", efficient, "1e-9")
    }

    @Test
    fun roundTripBetweenMpgAndKmPerLitre() {
        val there = FuelEngine.convert(BigDecimal("35"), "mpg_us", "km_l").valueOrNull()!!
        val back = FuelEngine.convert(there, "km_l", "mpg_us").valueOrNull()!!
        assertClose("35", back, "1e-9")
    }

    @Test
    fun zeroConsumptionIsRejected() {
        val result = FuelEngine.convert(BigDecimal.ZERO, "mpg_us", "l_100km")
        expectError(ErrorKind.Domain, result, "Zero consumption")
    }

    @Test
    fun zeroLitresPerHundredIsRejectedWithSpecificMessage() {
        val result = FuelEngine.convert(BigDecimal.ZERO, "l_100km", "mpg_us")
        expectError(ErrorKind.Domain, result, "no fuel is ever needed")
    }

    @Test
    fun negativeConsumptionIsRejected() {
        val result = FuelEngine.convert(BigDecimal("-5"), "km_l", "l_100km")
        expectError(ErrorKind.Domain, result, "greater than zero")
    }

    @Test
    fun unknownSourceUnitIsRejected() {
        expectError(ErrorKind.InvalidInput, FuelEngine.convert(BigDecimal.ONE, "l_per_mile", "km_l"), "Unknown")
    }

    @Test
    fun unknownTargetUnitIsRejected() {
        expectError(ErrorKind.InvalidInput, FuelEngine.convert(BigDecimal.ONE, "km_l", "buckets"), "Unknown")
    }

    @Test
    fun familyExposesAllFiveUnits() {
        assertEquals(5, FuelFamily.unitIds.size)
        assertEquals("l_100km", FuelFamily.unitIds.first())
        assertEquals("L/100km", FuelFamily.symbol("l_100km"))
        assertEquals("mpg (US)", FuelFamily.symbol("mpg_us"))
        assertEquals("Miles per gallon (Imperial)", FuelFamily.label("mpg_uk"))
    }

    // ---- Trip calculation ---------------------------------------------------

    @Test
    fun tripFuelForFourHundredFiftyKilometres() {
        val trip = FuelEngine.tripFuel(
            distanceKm = BigDecimal("450"),
            consumptionValue = BigDecimal("7.84"),
            consumptionUnit = "l_100km",
            pricePerLitre = BigDecimal("1.75"),
        ).valueOrNull()!!
        assertExact("35.28", trip.fuelLitres)
        assertExact("61.74", trip.cost)
        assertExact("7.84", trip.litresPer100km)
        assertEquals(null, trip.note)
    }

    @Test
    fun tripFuelFromMpgUsesTheUsGallon() {
        val trip = FuelEngine.tripFuel(
            distanceKm = BigDecimal("100"),
            consumptionValue = BigDecimal("30"),
            consumptionUnit = "mpg_us",
            pricePerLitre = BigDecimal("1.50"),
        ).valueOrNull()!!
        assertClose("7.84048611111111111111111111111111", trip.fuelLitres, "1e-9")
        assertClose("11.76072916666666666666666666666667", trip.cost, "1e-9")
        assertTrue("note should mention the US gallon", trip.note!!.contains("US gallon"))
    }

    @Test
    fun tripFuelFromMpgImperialUsesTheImperialGallon() {
        val trip = FuelEngine.tripFuel(
            distanceKm = BigDecimal("100"),
            consumptionValue = BigDecimal("36.1"),
            consumptionUnit = "mpg_uk",
            pricePerLitre = BigDecimal("1.50"),
        ).valueOrNull()!!
        assertTrue("note should mention the Imperial gallon", trip.note!!.contains("Imperial gallon"))
    }

    @Test
    fun tripFuelCostIsLitresTimesPrice() {
        val trip = FuelEngine.tripFuel(
            distanceKm = BigDecimal("1000"),
            consumptionValue = BigDecimal("5"),
            consumptionUnit = "l_100km",
            pricePerLitre = BigDecimal("2"),
        ).valueOrNull()!!
        assertExact("50", trip.fuelLitres)
        assertExact("100", trip.cost)
    }

    @Test
    fun tripFuelRequiresPositiveDistance() {
        val result = FuelEngine.tripFuel(BigDecimal.ZERO, BigDecimal("7.84"), "l_100km", BigDecimal.ONE)
        expectError(ErrorKind.Domain, result, "Distance")
    }

    @Test
    fun tripFuelRejectsNegativePrice() {
        val result = FuelEngine.tripFuel(BigDecimal("100"), BigDecimal("7.84"), "l_100km", BigDecimal("-1"))
        expectError(ErrorKind.Domain, result, "Price")
    }

    @Test
    fun tripFuelRejectsZeroConsumption() {
        val result = FuelEngine.tripFuel(BigDecimal("100"), BigDecimal.ZERO, "mpg_us", BigDecimal.ONE)
        expectError(ErrorKind.Domain, result, "Zero consumption")
    }

    @Test
    fun tripFuelRejectsUnknownConsumptionUnit() {
        expectError(
            ErrorKind.InvalidInput,
            FuelEngine.tripFuel(BigDecimal("100"), BigDecimal("7"), "cubits_per_gallon", BigDecimal.ONE),
            "Unknown",
        )
    }

    @Test
    fun tripCostFromLitresMatchesTripFuel() {
        val viaEngine = FuelEngine.tripCostFromLitres(BigDecimal("250"), BigDecimal("8"), BigDecimal("1.5"))
            .valueOrNull()!!
        assertExact("20", viaEngine.fuelLitres)
        assertExact("30", viaEngine.cost)
        assertExact("8", viaEngine.litresPer100km)
    }

    @Test
    fun tripCostFromLitresRejectsZeroConsumption() {
        val result = FuelEngine.tripCostFromLitres(BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ONE)
        expectError(ErrorKind.Domain, result, "greater than zero")
    }

    @Test
    fun anEconomicalDieselUsesLessThanAGreedySuv() {
        val suv = FuelEngine.tripFuel(BigDecimal("500"), BigDecimal("13.5"), "l_100km", BigDecimal("1.8"))
            .valueOrNull()!!
        val diesel = FuelEngine.tripFuel(BigDecimal("500"), BigDecimal("5.2"), "l_100km", BigDecimal("1.8"))
            .valueOrNull()!!
        assertTrue(diesel.fuelLitres.compareTo(suv.fuelLitres) < 0)
        assertExact("67.5", suv.fuelLitres)
        assertExact("26", diesel.fuelLitres)
    }
}
