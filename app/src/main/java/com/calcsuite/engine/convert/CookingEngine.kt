package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * Cooking measures in two dimensions: **volume** (base millilitre) and
 * **mass** (base gram).
 *
 * Volume ↔ volume and mass ↔ mass conversions are exact (up to the 34-digit
 * working precision). Crossing dimensions needs a density in g/mL:
 * `mass = volume × density`, `volume = mass ÷ density`. Densities vary with
 * packing and brand, so all ingredient values are approximations.
 *
 * Densities are taken from the ingredient weight charts of the US Department
 * of Agriculture (USDA FoodData Central) and King Arthur Baking; volumes vary
 * with sifting and packing ("1 cup of flour" ranges roughly 120–155 g).
 */
object CookingEngine {

    enum class Dimension { VOLUME, MASS }

    data class CookingUnit(
        val id: String,
        val label: String,
        val symbol: String,
        val dimension: Dimension,
        /** mL for volume units, g for mass units. */
        val factorToBase: BigDecimal,
        val group: String,
    )

    /** Ingredient with an approximate density. */
    data class Ingredient(val name: String, val densityGPerMl: BigDecimal)

    private val imperialTeaspoon: BigDecimal =
        bd("17.7581640625").divide(bd("3"), UnitFamily.CTX)

    val units: List<CookingUnit> = listOf(
        CookingUnit("mL", "Millilitres", "mL", Dimension.VOLUME, bd("1"), "Metric"),
        CookingUnit("L", "Litres", "L", Dimension.VOLUME, bd("1000"), "Metric"),
        CookingUnit("tsp_metric", "Metric teaspoons", "tsp (metric)", Dimension.VOLUME, bd("5"), "Metric"),
        CookingUnit("tbsp_metric", "Metric tablespoons", "tbsp (metric)", Dimension.VOLUME, bd("15"), "Metric"),
        CookingUnit("cup_metric", "Metric cups", "cup (metric)", Dimension.VOLUME, bd("250"), "Metric"),
        CookingUnit("tsp_us", "US teaspoons", "tsp", Dimension.VOLUME, bd("4.92892159375"), "US customary"),
        CookingUnit("tbsp_us", "US tablespoons", "tbsp", Dimension.VOLUME, bd("14.78676478125"), "US customary"),
        CookingUnit("floz_us", "US fluid ounces", "fl oz", Dimension.VOLUME, bd("29.5735295625"), "US customary"),
        CookingUnit("cup_us", "US cups", "cup", Dimension.VOLUME, bd("236.5882365"), "US customary"),
        CookingUnit("pt_us", "US pints", "pt", Dimension.VOLUME, bd("473.176473"), "US customary"),
        CookingUnit("qt_us", "US quarts", "qt", Dimension.VOLUME, bd("946.352946"), "US customary"),
        CookingUnit("gal_us", "US gallons", "gal", Dimension.VOLUME, bd("3785.411784"), "US customary"),
        CookingUnit("floz_imp", "Imperial fluid ounces", "fl oz (Imp)", Dimension.VOLUME, bd("28.4130625"), "Imperial (UK)"),
        CookingUnit("tsp_imp", "Imperial teaspoons", "tsp (Imp)", Dimension.VOLUME, imperialTeaspoon, "Imperial (UK)"),
        CookingUnit("tbsp_imp", "Imperial tablespoons", "tbsp (Imp)", Dimension.VOLUME, bd("17.7581640625"), "Imperial (UK)"),
        CookingUnit("cup_imp", "Imperial cups", "cup (Imp)", Dimension.VOLUME, bd("284.130625"), "Imperial (UK)"),
        CookingUnit("pt_imp", "Imperial pints", "pt (Imp)", Dimension.VOLUME, bd("568.26125"), "Imperial (UK)"),
        CookingUnit("g", "Grams", "g", Dimension.MASS, bd("1"), "Mass"),
        CookingUnit("kg", "Kilograms", "kg", Dimension.MASS, bd("1000"), "Mass"),
        CookingUnit("oz", "Ounces", "oz", Dimension.MASS, bd("28.349523125"), "Mass"),
        CookingUnit("lb", "Pounds", "lb", Dimension.MASS, bd("453.59237"), "Mass"),
    )

    val ingredients: List<Ingredient> = listOf(
        Ingredient("Water", bd("1.0")),
        Ingredient("Milk", bd("1.03")),
        Ingredient("Heavy cream", bd("0.994")),
        Ingredient("Flour, all-purpose", bd("0.53")),
        Ingredient("Flour, bread", bd("0.55")),
        Ingredient("Flour, whole wheat", bd("0.57")),
        Ingredient("Granulated sugar", bd("0.845")),
        Ingredient("Brown sugar (packed)", bd("0.93")),
        Ingredient("Powdered sugar", bd("0.56")),
        Ingredient("Butter", bd("0.911")),
        Ingredient("Honey", bd("1.42")),
        Ingredient("Olive oil", bd("0.918")),
        Ingredient("Vegetable oil", bd("0.92")),
        Ingredient("Rice, uncooked", bd("0.85")),
        Ingredient("Rolled oats", bd("0.41")),
        Ingredient("Cocoa powder", bd("0.52")),
        Ingredient("Table salt", bd("1.217")),
        Ingredient("Peanut butter", bd("1.08")),
        Ingredient("Maple syrup", bd("1.33")),
    )

    val notes: List<String> = listOf(
        "Densities follow USDA / King Arthur Baking ingredient weight charts; packed volumes vary, so treat them as approximate.",
        "US and Imperial spoons and cups differ: 1 US cup = 236.5882365 mL, 1 Imperial cup = 284.130625 mL, 1 metric cup = 250 mL.",
        "Converting between mass and volume requires a density; choose the ingredient that matches how it is packed.",
    )

    fun unit(id: String): CookingUnit? = units.firstOrNull { it.id == id }

    fun ingredient(name: String): Ingredient? =
        ingredients.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Same-dimension conversion; crossing mass/volume is an error here. */
    fun convert(value: BigDecimal, fromId: String, toId: String): CalcResult<BigDecimal> {
        val from = unit(fromId) ?: return err(ErrorKind.InvalidInput, "Unknown source unit")
        val to = unit(toId) ?: return err(ErrorKind.InvalidInput, "Unknown target unit")
        if (from.dimension != to.dimension) {
            return err(
                ErrorKind.Inconsistent,
                "Converting between volume and mass requires an ingredient density",
            )
        }
        val base = value.multiply(from.factorToBase, UnitFamily.CTX)
        return ok(base.divide(to.factorToBase, UnitFamily.CTX))
    }

    /**
     * Converts using an ingredient density in g/mL, allowing volume ↔ mass.
     * Same-dimension pairs convert directly and ignore the density.
     */
    fun convertWithDensity(
        value: BigDecimal,
        fromId: String,
        toId: String,
        densityGPerMl: BigDecimal,
    ): CalcResult<BigDecimal> {
        val from = unit(fromId) ?: return err(ErrorKind.InvalidInput, "Unknown source unit")
        val to = unit(toId) ?: return err(ErrorKind.InvalidInput, "Unknown target unit")
        if (from.dimension == to.dimension) {
            return convert(value, fromId, toId)
        }
        if (densityGPerMl.signum() <= 0) {
            return err(ErrorKind.Domain, "Density must be greater than zero")
        }
        val base = value.multiply(from.factorToBase, UnitFamily.CTX)
        val targetBase = when {
            from.dimension == Dimension.VOLUME -> base.multiply(densityGPerMl, UnitFamily.CTX) // mL → g
            else -> base.divide(densityGPerMl, UnitFamily.CTX) // g → mL
        }
        return ok(targetBase.divide(to.factorToBase, UnitFamily.CTX))
    }
}
