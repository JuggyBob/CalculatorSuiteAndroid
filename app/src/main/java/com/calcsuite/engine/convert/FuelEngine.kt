package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * Fuel consumption — a reciprocal family, not a linear one.
 *
 * The canonical form is **litres per 100 km**. Efficiency units are inversely
 * proportional to it:
 *
 * ```
 * L/100 km = 100 / (km/L)
 * L/100 km = 62.1371192237334 / (mi/L)          where 62.1371192237334 = 100 / 1.609344
 * L/100 km = 235.2145832942277 / (mpg US)       where 235.2145832942277 = 100 × 3.785411784 / 1.609344
 * L/100 km = 282.4809363277027 / (mpg Imp)      where 282.4809363277027 = 100 × 4.54609 / 1.609344
 * ```
 *
 * The three constants are computed here from the exact unit definitions
 * (1 mi = 1.609344 km, 1 US gal = 3.785411784 L, 1 Imp gal = 4.54609 L) at the
 * shared 34-digit working precision rather than hard-coded, so they are the
 * mathematically correct values.
 *
 * Zero and negative consumptions are rejected: converting zero consumption
 * would imply infinite distance per unit of fuel (or no fuel at all).
 */

/** Constant numerator of the canonical L/100 km formula for an inverse unit. */
private fun canonicalNumerator(unitId: String): BigDecimal? = when (unitId) {
    "km_l" -> bd("100")
    "mi_l" -> bd("100").divide(bd("1.609344"), UnitFamily.CTX)
    "mpg_us" -> bd("378.5411784").divide(bd("1.609344"), UnitFamily.CTX)
    "mpg_uk" -> bd("454.609").divide(bd("1.609344"), UnitFamily.CTX)
    else -> null
}

/** The reciprocal fuel-consumption family used by the picker UI. */
object FuelFamily : ReciprocalFamily {
    override val id: String = "fuel"
    override val name: String = "Fuel consumption"

    private val entries: List<Triple<String, String, String>> = listOf(
        Triple("l_100km", "Litres per 100 km", "L/100km"),
        Triple("km_l", "Kilometres per litre", "km/L"),
        Triple("mi_l", "Miles per litre", "mi/L"),
        Triple("mpg_us", "Miles per gallon (US)", "mpg (US)"),
        Triple("mpg_uk", "Miles per gallon (Imperial)", "mpg (Imp)"),
    )

    override val unitIds: List<String> get() = entries.map { it.first }

    override fun label(id: String): String = entries.firstOrNull { it.first == id }?.second ?: id

    override fun symbol(id: String): String = entries.firstOrNull { it.first == id }?.third ?: id

    override fun convert(value: BigDecimal, fromId: String, toId: String): CalcResult<BigDecimal> =
        FuelEngine.convert(value, fromId, toId)
}

/** Pure engine for fuel consumption and trip cost. */
object FuelEngine {

    /** All unit ids, in display order. */
    val unitIds: List<String> get() = FuelFamily.unitIds

    fun symbol(unitId: String): String = FuelFamily.symbol(unitId)

    fun label(unitId: String): String = FuelFamily.label(unitId)

    /** Converts a consumption value between any two fuel units via L/100 km. */
    fun convert(value: BigDecimal, fromId: String, toId: String): CalcResult<BigDecimal> {
        if (fromId !in FuelFamily.unitIds) return err(ErrorKind.InvalidInput, "Unknown source unit")
        if (toId !in FuelFamily.unitIds) return err(ErrorKind.InvalidInput, "Unknown target unit")
        when {
            value.signum() == 0 && fromId == "l_100km" ->
                return err(
                    ErrorKind.Domain,
                    "Zero consumption cannot be converted; 0 L/100 km would mean no fuel is ever needed",
                )
            value.signum() == 0 ->
                return err(
                    ErrorKind.Domain,
                    "Zero consumption cannot be converted; it implies infinite distance per unit fuel",
                )
            value.signum() < 0 -> return err(ErrorKind.Domain, "Consumption must be greater than zero")
        }
        val canonical = toCanonical(value, fromId)
            ?: return err(ErrorKind.InvalidInput, "Unknown source unit")
        val result = fromCanonical(canonical, toId)
            ?: return err(ErrorKind.InvalidInput, "Unknown target unit")
        return ok(result)
    }

    /** L/100 km for a value expressed in [unitId]. */
    fun toCanonical(value: BigDecimal, unitId: String): BigDecimal? {
        if (unitId == "l_100km") return value
        val numerator = canonicalNumerator(unitId) ?: return null
        if (value.signum() == 0) return null
        return numerator.divide(value, UnitFamily.CTX)
    }

    /** A value in [unitId] equivalent to a canonical L/100 km figure. */
    fun fromCanonical(canonicalL100: BigDecimal, unitId: String): BigDecimal? {
        if (unitId == "l_100km") return canonicalL100
        val numerator = canonicalNumerator(unitId) ?: return null
        if (canonicalL100.signum() == 0) return null
        return numerator.divide(canonicalL100, UnitFamily.CTX)
    }

    /** Result of a trip calculation. */
    data class TripFuel(
        /** Fuel used on the trip, in litres. */
        val fuelLitres: BigDecimal,
        /** The consumption used, normalised to L/100 km. */
        val litresPer100km: BigDecimal,
        /** Fuel cost = litres × price per litre. */
        val cost: BigDecimal,
        /** Note about gallon definitions when the input was in mpg. */
        val note: String? = null,
    )

    /**
     * Fuel and cost for a trip.
     *
     * The litres burned are always derived from the canonical L/100 km value of
     * the chosen unit, so when [consumptionUnit] is an mpg figure the litre
     * figure is consistent with that gallon definition (US 3.785411784 L or
     * Imperial 4.54609 L).
     */
    fun tripFuel(
        distanceKm: BigDecimal,
        consumptionValue: BigDecimal,
        consumptionUnit: String,
        pricePerLitre: BigDecimal,
    ): CalcResult<TripFuel> {
        if (consumptionUnit !in FuelFamily.unitIds) {
            return err(ErrorKind.InvalidInput, "Unknown consumption unit")
        }
        if (distanceKm.signum() <= 0) return err(ErrorKind.Domain, "Distance must be greater than zero")
        if (pricePerLitre.signum() < 0) return err(ErrorKind.Domain, "Price must not be negative")
        val canonical = when {
            consumptionValue.signum() == 0 -> return err(
                ErrorKind.Domain,
                "Zero consumption cannot be converted; it implies infinite distance per unit fuel",
            )
            consumptionValue.signum() < 0 -> return err(
                ErrorKind.Domain,
                "Consumption must be greater than zero",
            )
            else -> toCanonical(consumptionValue, consumptionUnit)
                ?: return err(ErrorKind.InvalidInput, "Unknown consumption unit")
        }
        val litres = distanceKm.multiply(canonical, UnitFamily.CTX).divide(bd("100"), UnitFamily.CTX)
        val cost = litres.multiply(pricePerLitre, UnitFamily.CTX)
        val note = when (consumptionUnit) {
            "mpg_us" -> "Litres derived from the US gallon: 1 US gal = 3.785411784 L"
            "mpg_uk" -> "Litres derived from the Imperial gallon: 1 Imp gal = 4.54609 L"
            "mi_l" -> "Litres derived from 1 mile = 1.609344 km"
            else -> null
        }
        return ok(TripFuel(fuelLitres = litres, litresPer100km = canonical, cost = cost, note = note))
    }

    /** Trip fuel and cost from a consumption already expressed in L/100 km. */
    fun tripCostFromLitres(
        distanceKm: BigDecimal,
        litresPer100km: BigDecimal,
        pricePerLitre: BigDecimal,
    ): CalcResult<TripFuel> {
        if (distanceKm.signum() <= 0) return err(ErrorKind.Domain, "Distance must be greater than zero")
        if (litresPer100km.signum() <= 0) {
            return err(ErrorKind.Domain, "Consumption must be greater than zero")
        }
        if (pricePerLitre.signum() < 0) return err(ErrorKind.Domain, "Price must not be negative")
        val litres = distanceKm.multiply(litresPer100km, UnitFamily.CTX).divide(bd("100"), UnitFamily.CTX)
        return ok(
            TripFuel(
                fuelLitres = litres,
                litresPer100km = litresPer100km,
                cost = litres.multiply(pricePerLitre, UnitFamily.CTX),
            ),
        )
    }
}
