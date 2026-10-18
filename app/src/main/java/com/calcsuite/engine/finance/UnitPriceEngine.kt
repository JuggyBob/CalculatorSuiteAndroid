package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Physical dimension a product's quantity belongs to. */
enum class Measure(val id: String, val label: String, val baseSymbol: String, val perLabel: String) {
    MASS("mass", "Mass", "kg", "per kilogram"),
    VOLUME("volume", "Volume", "L", "per litre"),
    COUNT("count", "Count", "item", "per item"),
    LENGTH("length", "Length", "m", "per metre");

    companion object {
        fun byId(id: String): Measure? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Sale units accepted for prices. [factor] converts one unit into the common
 * comparison base of its [measure] (kg, L, item, m).
 *
 * The factors are exact definitions:
 * 1 oz = 28.349523125 g, 1 lb = 0.45359237 kg, 1 US fl oz = 29.5735295625 mL,
 * 1 metric cup = 250 mL, 1 in = 25.4 mm, 1 ft = 0.3048 m.
 */
enum class PriceUnit(
    val id: String,
    val label: String,
    val symbol: String,
    val measure: Measure,
    val factor: BigDecimal,
) {
    GRAM("g", "Grams", "g", Measure.MASS, BigDecimal("0.001")),
    KILOGRAM("kg", "Kilograms", "kg", Measure.MASS, BigDecimal.ONE),
    OUNCE("oz", "Ounces", "oz", Measure.MASS, BigDecimal("0.028349523125")),
    POUND("lb", "Pounds", "lb", Measure.MASS, BigDecimal("0.45359237")),
    MILLILITRE("ml", "Millilitres", "mL", Measure.VOLUME, BigDecimal("0.001")),
    LITRE("l", "Litres", "L", Measure.VOLUME, BigDecimal.ONE),
    FLUID_OUNCE_US("floz", "US fluid ounces", "fl oz", Measure.VOLUME, BigDecimal("0.0295735295625")),
    CUP_METRIC("cup", "Metric cups (250 mL)", "cup", Measure.VOLUME, BigDecimal("0.25")),
    ITEM("item", "Items", "item", Measure.COUNT, BigDecimal.ONE),
    PACK("pack", "Packs", "pack", Measure.COUNT, BigDecimal.ONE),
    MILLIMETRE("mm", "Millimetres", "mm", Measure.LENGTH, BigDecimal("0.001")),
    CENTIMETRE("cm", "Centimetres", "cm", Measure.LENGTH, BigDecimal("0.01")),
    METRE("m", "Metres", "m", Measure.LENGTH, BigDecimal.ONE),
    INCH("in", "Inches", "in", Measure.LENGTH, BigDecimal("0.0254")),
    FOOT("ft", "Feet", "ft", Measure.LENGTH, BigDecimal("0.3048"));

    companion object {
        fun byId(id: String): PriceUnit? = entries.firstOrNull { it.id == id }
    }
}

/** One product line the user entered. */
data class ProductInput(
    val name: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val unit: PriceUnit,
    /** 0..99.x; applied as `price × (1 − d/100)` before normalisation. */
    val discountPercent: BigDecimal = BigDecimal.ZERO,
)

/** A product that could not take part in the comparison, with the reason. */
data class RejectedProduct(val product: ProductInput, val message: String)

/** One comparable product with its normalised unit price. */
data class UnitPriceRow(
    val product: ProductInput,
    /** Price after the discount, before normalisation. */
    val effectivePrice: BigDecimal,
    /** Quantity expressed in the comparison base (kg, L, item, m). */
    val quantityInBase: BigDecimal,
    /** effectivePrice / quantityInBase, full 20-digit precision. */
    val unitPrice: BigDecimal,
    val rank: Int,
    val bestValue: Boolean,
)

/** Result of a comparison for one dimension. */
data class UnitPriceComparison(
    val dimension: Measure,
    /** Ranked ascending by unit price; rank 1 is the best value. */
    val rows: List<UnitPriceRow>,
    val rejected: List<RejectedProduct>,
) {
    val best: UnitPriceRow? get() = rows.firstOrNull()
}

/**
 * "Which package is actually cheaper?" — normalises every product to a common
 * comparison unit (per kg / per L / per item / per m) and ranks them.
 *
 * A product is never ranked by its package price; a bigger pack only wins when
 * its normalised price is lower. Discounts are applied to the price before
 * normalising. Products whose units belong to another dimension are rejected
 * with an explicit message instead of being silently converted, because mass,
 * volume, count and length cannot be compared with each other.
 */
object UnitPriceEngine {

    val MC: MathContext = MathContext(20, RoundingMode.HALF_UP)

    private val HUNDRED = BigDecimal("100")

    const val CROSS_DIMENSION_NOTE =
        "Products measured in the same dimension can be compared (mass per kg, volume per L, " +
            "count per item or length per m). Products in different dimensions cannot be compared."

    /** Normalises one product into the comparison base of its own dimension. */
    fun normalise(product: ProductInput): CalcResult<UnitPriceRow> {
        if (product.quantity.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Quantity must be greater than zero")
        }
        if (product.price.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Price must not be negative")
        }
        if (product.discountPercent.signum() < 0 || product.discountPercent >= HUNDRED) {
            return err(ErrorKind.InvalidInput, "Discount must be between 0 and 100")
        }
        val effective = applyDiscount(product.price, product.discountPercent)
        val quantityInBase = product.quantity.multiply(product.unit.factor, MC)
        val unitPrice = effective.divide(quantityInBase, MC)
        return ok(
            UnitPriceRow(
                product = product,
                effectivePrice = effective,
                quantityInBase = quantityInBase,
                unitPrice = unitPrice,
                rank = 0,
                bestValue = false,
            ),
        )
    }

    /** `price × (1 − discount/100)`, 20-digit precision. */
    fun applyDiscount(price: BigDecimal, discountPercent: BigDecimal): BigDecimal {
        val multiplier = BigDecimal.ONE.subtract(
            discountPercent.divide(HUNDRED, MC),
            MC,
        )
        return price.multiply(multiplier, MC)
    }

    /**
     * Compares [products] by [dimension]. Products of another dimension or
     * with invalid numbers are returned in [UnitPriceComparison.rejected]
     * instead of aborting the whole comparison.
     */
    fun compare(dimension: Measure, products: List<ProductInput>): CalcResult<UnitPriceComparison> {
        if (products.isEmpty()) {
            return err(ErrorKind.Insufficient, "Add at least one product to compare")
        }
        val rows = ArrayList<UnitPriceRow>(products.size)
        val rejected = ArrayList<RejectedProduct>()
        for (product in products) {
            if (product.unit.measure != dimension) {
                val name = product.name.ifBlank { "Product" }
                rejected += RejectedProduct(
                    product,
                    "$name is measured in ${product.unit.measure.label.lowercase()} " +
                        "(${product.unit.symbol}); it cannot be compared with " +
                        "${dimension.label.lowercase()} (${dimension.baseSymbol})",
                )
                continue
            }
            when (val normalised = normalise(product)) {
                is CalcResult.Ok -> rows += normalised.value
                is CalcResult.Err -> rejected += RejectedProduct(
                    product,
                    "${product.name.ifBlank { "Product" }}: ${normalised.error.message}",
                )
            }
        }
        if (rows.isEmpty()) {
            return err(
                ErrorKind.InvalidInput,
                rejected.firstOrNull()?.message ?: "No products could be compared",
            )
        }
        val sorted = rows.sortedWith(compareBy { it.unitPrice })
        val withRank = sorted.mapIndexed { index, row ->
            row.copy(rank = index + 1, bestValue = index == 0)
        }
        return ok(UnitPriceComparison(dimension = dimension, rows = withRank, rejected = rejected))
    }
}
