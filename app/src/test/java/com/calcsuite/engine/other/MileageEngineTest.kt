package com.calcsuite.engine.other

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MileageEngineTest {

    private fun value(result: CalcResult<MileageResult>): MileageResult {
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<MileageResult>): String {
        assertTrue("Expected failure, was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    /**
     * 300 km on 20 L of fuel. The correct figures for this trip are
     * 6.667 L/100 km, 15 km/L, 35.28 mpg (US) and 42.37 mpg (Imperial);
     * the imperial figure is naturally about 20% higher than the US figure.
     */
    @Test
    fun briefExampleFuelEconomy() {
        val result = value(
            MileageEngine.compute(
                distance = BigDecimal("300"),
                distanceUnit = DistanceUnit.KILOMETRES,
                fuel = BigDecimal("20"),
                fuelUnit = FuelUnit.LITRES,
            ),
        )
        assertEquals(6.67, result.litresPer100Km.toDouble(), 0.01)
        assertEquals(0, result.kmPerLitre.compareTo(BigDecimal("15")))
        assertEquals(35.28, result.mpgUs.toDouble(), 0.2)
        assertEquals(42.37, result.mpgImperial.toDouble(), 0.2)
        // The two gallon figures must stand in the exact gallon ratio.
        assertEquals(1.2009, result.mpgImperial.toDouble() / result.mpgUs.toDouble(), 0.0005)
    }

    @Test
    fun briefExampleTripCost() {
        val result = value(
            MileageEngine.compute(
                distance = BigDecimal("300"),
                distanceUnit = DistanceUnit.KILOMETRES,
                fuel = BigDecimal("20"),
                fuelUnit = FuelUnit.LITRES,
                pricePerFuelUnit = BigDecimal("1.50"),
            ),
        )
        assertEquals(0, result.totalCost!!.compareTo(BigDecimal("30.00")))
        assertEquals(0.1, result.costPerKm!!.toDouble(), 1e-9)
        assertEquals(0.1609344, result.costPerMile!!.toDouble(), 1e-6)
    }

    @Test
    fun noPriceMeansNoCostRows() {
        val result = value(
            MileageEngine.compute(
                BigDecimal("300"), DistanceUnit.KILOMETRES, BigDecimal("20"), FuelUnit.LITRES,
            ),
        )
        assertNull(result.totalCost)
        assertNull(result.costPerKm)
        assertNull(result.costPerMile)
    }

    @Test
    fun reversedOdometerIsRejected() {
        val message = error(
            MileageEngine.fromOdometer(
                startOdometer = BigDecimal("50000"),
                endOdometer = BigDecimal("49000"),
                distanceUnit = DistanceUnit.KILOMETRES,
                fuel = BigDecimal("20"),
                fuelUnit = FuelUnit.LITRES,
            ),
        )
        assertEquals("End odometer must not be less than the start odometer", message)
    }

    @Test
    fun equalOdometerReadingsAreRejected() {
        val message = error(
            MileageEngine.fromOdometer(
                BigDecimal("50000"), BigDecimal("50000"), DistanceUnit.KILOMETRES,
                BigDecimal("20"), FuelUnit.LITRES,
            ),
        )
        assertEquals("Trip distance must be greater than zero", message)
    }

    @Test
    fun odometerTripMatchesDirectDistance() {
        val result = value(
            MileageEngine.fromOdometer(
                BigDecimal("50000"), BigDecimal("50300"), DistanceUnit.KILOMETRES,
                BigDecimal("20"), FuelUnit.LITRES,
            ),
        )
        assertEquals(0, result.distanceKm.compareTo(BigDecimal("300")))
        assertEquals(6.67, result.litresPer100Km.toDouble(), 0.01)
    }

    @Test
    fun zeroAndNegativeInputsAreRejected() {
        assertEquals(
            "Trip distance must be greater than zero",
            error(MileageEngine.compute(BigDecimal.ZERO, DistanceUnit.KILOMETRES, BigDecimal("20"), FuelUnit.LITRES)),
        )
        assertEquals(
            "Fuel used must be greater than zero",
            error(MileageEngine.compute(BigDecimal("300"), DistanceUnit.KILOMETRES, BigDecimal.ZERO, FuelUnit.LITRES)),
        )
        assertEquals(
            "Price must not be negative",
            error(MileageEngine.compute(BigDecimal("300"), DistanceUnit.KILOMETRES, BigDecimal("20"), FuelUnit.LITRES, BigDecimal("-1"))),
        )
    }

    @Test
    fun milesAndUsGallonsPrimeExample() {
        // 100 miles on 10 US gallons is exactly 10 mpg (US).
        val result = value(
            MileageEngine.compute(BigDecimal("100"), DistanceUnit.MILES, BigDecimal("10"), FuelUnit.GALLONS_US),
        )
        assertEquals(0, result.mpgUs.compareTo(BigDecimal("10")))
        assertEquals(23.52, result.litresPer100Km.toDouble(), 0.01)
    }

    @Test
    fun imperialGallonFuelIsConvertedCorrectly() {
        val result = value(
            MileageEngine.compute(BigDecimal("100"), DistanceUnit.KILOMETRES, BigDecimal("4.54609"), FuelUnit.LITRES),
        )
        assertEquals(4.54609, result.litresPer100Km.toDouble(), 0.0001)
        assertEquals(22.0, result.kmPerLitre.toDouble(), 0.01)

        val usGallons = value(
            MileageEngine.compute(BigDecimal("100"), DistanceUnit.KILOMETRES, BigDecimal("1"), FuelUnit.GALLONS_US),
        )
        assertEquals(3.785411784, usGallons.fuelLitres.toDouble(), 1e-9)

        val imperialGallons = value(
            MileageEngine.compute(BigDecimal("100"), DistanceUnit.KILOMETRES, BigDecimal("1"), FuelUnit.GALLONS_IMPERIAL),
        )
        assertEquals(4.54609, imperialGallons.fuelLitres.toDouble(), 1e-9)
    }

    @Test
    fun notesDocumentUnitsAndDistinctQuantities() {
        val result = value(
            MileageEngine.compute(BigDecimal("300"), DistanceUnit.KILOMETRES, BigDecimal("20"), FuelUnit.LITRES),
        )
        assertTrue(result.notes.any { it.contains("1 mile = 1.609 344 km") })
        assertTrue(result.notes.any { it.contains("distinct quantities") })
    }
}
