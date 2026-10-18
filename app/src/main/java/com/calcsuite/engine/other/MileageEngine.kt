package com.calcsuite.engine.other

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Distance units accepted for odometer readings and trip distances. */
enum class DistanceUnit(val label: String, val symbol: String, val kmPerUnit: BigDecimal) {
    KILOMETRES("Kilometres", "km", BigDecimal.ONE),
    MILES("Miles", "mi", BigDecimal("1.609344")),
}

/** Fuel volume units; the price is entered per selected unit. */
enum class FuelUnit(val label: String, val symbol: String, val litresPerUnit: BigDecimal) {
    LITRES("Litres", "L", BigDecimal.ONE),
    GALLONS_US("US gallons", "gal (US)", BigDecimal("3.785411784")),
    GALLONS_IMPERIAL("Imperial gallons", "gal (UK)", BigDecimal("4.54609")),
}

/**
 * Trip distance, fuel economy and trip cost.
 *
 * Fuel economy (L/100 km, mpg) and fuel consumption (km/L) are reciprocal
 * views of the same measurement, and the trip cost is a separate money
 * quantity. The result keeps them apart so each can be labelled precisely.
 *
 * Exact definitions used: 1 mile = 1.609 344 km, 1 US gallon = 3.785 411 784 L,
 * 1 imperial gallon = 4.546 09 L.
 */
data class MileageResult(
    val distanceKm: BigDecimal,
    val distanceMiles: BigDecimal,
    val fuelLitres: BigDecimal,
    val fuelUsGallons: BigDecimal,
    val fuelImperialGallons: BigDecimal,
    /** Fuel economy: litres per 100 km. */
    val litresPer100Km: BigDecimal,
    /** Fuel consumption: kilometres per litre. */
    val kmPerLitre: BigDecimal,
    /** Fuel economy in US miles per US gallon. */
    val mpgUs: BigDecimal,
    /** Fuel economy in miles per imperial gallon. */
    val mpgImperial: BigDecimal,
    /** Total fuel cost, null when no price was supplied. */
    val totalCost: BigDecimal?,
    val costPerKm: BigDecimal?,
    val costPerMile: BigDecimal?,
    val notes: List<String>,
)

object MileageEngine {

    private val CTX = MathContext(34, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal(100)

    /** Direct trip distance in [distanceUnit]. */
    fun compute(
        distance: BigDecimal,
        distanceUnit: DistanceUnit,
        fuel: BigDecimal,
        fuelUnit: FuelUnit,
        pricePerFuelUnit: BigDecimal? = null,
    ): CalcResult<MileageResult> {
        if (distance.signum() <= 0) return err(ErrorKind.InvalidInput, "Trip distance must be greater than zero")
        if (fuel.signum() <= 0) return err(ErrorKind.InvalidInput, "Fuel used must be greater than zero")
        if (pricePerFuelUnit != null && pricePerFuelUnit.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Price must not be negative")
        }

        val distanceKm = distance.multiply(distanceUnit.kmPerUnit, CTX)
        val distanceMiles = distanceKm.divide(DistanceUnit.MILES.kmPerUnit, CTX)
        val fuelLitres = fuel.multiply(fuelUnit.litresPerUnit, CTX)
        val fuelUsGallons = fuelLitres.divide(FuelUnit.GALLONS_US.litresPerUnit, CTX)
        val fuelImperialGallons = fuelLitres.divide(FuelUnit.GALLONS_IMPERIAL.litresPerUnit, CTX)

        val litresPer100Km = fuelLitres.divide(distanceKm, CTX).multiply(HUNDRED, CTX)
        val kmPerLitre = distanceKm.divide(fuelLitres, CTX)
        val mpgUs = distanceMiles.divide(fuelUsGallons, CTX)
        val mpgImperial = distanceMiles.divide(fuelImperialGallons, CTX)

        var totalCost: BigDecimal? = null
        var costPerKm: BigDecimal? = null
        var costPerMile: BigDecimal? = null
        pricePerFuelUnit?.let { price ->
            val cost = fuel.multiply(price, CTX)
            totalCost = cost
            costPerKm = cost.divide(distanceKm, CTX)
            costPerMile = cost.divide(distanceMiles, CTX)
        }

        val notes = buildList {
            add(
                "Exact definitions: 1 mile = 1.609 344 km, 1 US gallon = 3.785 411 784 L, " +
                    "1 imperial gallon = 4.546 09 L.",
            )
            add(
                "Trip distance, fuel economy (L/100 km, mpg), fuel consumption (km/L) and trip cost " +
                    "are distinct quantities and are shown separately.",
            )
            add(
                "US and imperial gallons differ by about 20%: check which gallon your pump and " +
                    "vehicle figures use.",
            )
            if (pricePerFuelUnit != null) {
                add("The price is interpreted per ${fuelUnit.label.lowercase()}, the unit the fuel was entered in.")
            }
        }

        return ok(
            MileageResult(
                distanceKm = distanceKm,
                distanceMiles = distanceMiles,
                fuelLitres = fuelLitres,
                fuelUsGallons = fuelUsGallons,
                fuelImperialGallons = fuelImperialGallons,
                litresPer100Km = litresPer100Km,
                kmPerLitre = kmPerLitre,
                mpgUs = mpgUs,
                mpgImperial = mpgImperial,
                totalCost = totalCost,
                costPerKm = costPerKm,
                costPerMile = costPerMile,
                notes = notes,
            ),
        )
    }

    /** Trip from odometer readings; the end reading must not be lower than the start. */
    fun fromOdometer(
        startOdometer: BigDecimal,
        endOdometer: BigDecimal,
        distanceUnit: DistanceUnit,
        fuel: BigDecimal,
        fuelUnit: FuelUnit,
        pricePerFuelUnit: BigDecimal? = null,
    ): CalcResult<MileageResult> {
        if (startOdometer.signum() < 0 || endOdometer.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Odometer readings must not be negative")
        }
        if (endOdometer < startOdometer) {
            return err(ErrorKind.InvalidInput, "End odometer must not be less than the start odometer")
        }
        val distance = endOdometer.subtract(startOdometer, CTX)
        if (distance.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Trip distance must be greater than zero")
        }
        return compute(distance, distanceUnit, fuel, fuelUnit, pricePerFuelUnit)
    }
}
