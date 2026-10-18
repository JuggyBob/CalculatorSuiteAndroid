package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs

class UnitPriceEngineTest {

    private fun ok(result: CalcResult<UnitPriceComparison>): UnitPriceComparison {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun errorKind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun assertMoney(expected: String, actual: BigDecimal, message: String = "") {
        assertEquals(
            "$message expected $expected but was $actual",
            0,
            actual.compareTo(BigDecimal(expected)),
        )
    }

    private fun product(
        name: String,
        price: String,
        quantity: String,
        unit: PriceUnit,
        discount: String = "0",
    ) = ProductInput(name, BigDecimal(price), BigDecimal(quantity), unit, BigDecimal(discount))

    @Test
    fun gramsVersusKilogramIsJudgedByUnitPriceNotPackagePrice() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.MASS,
                listOf(
                    product("Small pack", "2.50", "500", PriceUnit.GRAM),
                    product("Kilo pack", "4.60", "1", PriceUnit.KILOGRAM),
                ),
            ),
        )
        assertEquals(2, comparison.rows.size)
        // 500 g for 2.50 → 5.00 per kg; 1 kg for 4.60 → 4.60 per kg.
        assertMoney("5.00", comparison.rows[1].unitPrice, "small pack per kg")
        assertMoney("4.60", comparison.rows[0].unitPrice, "kilo pack per kg")
        assertEquals("Kilo pack", comparison.rows[0].product.name)
        assertTrue(comparison.rows[0].bestValue)
        assertFalse(comparison.rows[1].bestValue)
        assertEquals(1, comparison.rows[0].rank)
        assertEquals(2, comparison.rows[1].rank)
    }

    @Test
    fun litresComparison() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.VOLUME,
                listOf(
                    product("Big bottle", "3.00", "2", PriceUnit.LITRE),
                    product("Small bottle", "2.10", "1.5", PriceUnit.LITRE),
                ),
            ),
        )
        assertMoney("1.50", comparison.rows[1].unitPrice, "2 L for 3.00")
        assertMoney("1.40", comparison.rows[0].unitPrice, "1.5 L for 2.10")
        assertEquals("Small bottle", comparison.best?.product?.name)
    }

    @Test
    fun discountIsAppliedBeforeNormalising() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.MASS,
                listOf(product("Discounted", "2.00", "500", PriceUnit.GRAM, discount = "20")),
            ),
        )
        val row = comparison.rows.single()
        assertMoney("1.60", row.effectivePrice, "price after 20% discount")
        assertMoney("3.20", row.unitPrice, "per kg after discount")
    }

    @Test
    fun imperialMassFactorsAreExact() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.MASS,
                listOf(product("Pound pack", "4.5359237", "1", PriceUnit.POUND)),
            ),
        )
        // 1 lb = 0.45359237 kg exactly, so this is exactly 10 per kg.
        assertMoney("10", comparison.rows.single().unitPrice)
    }

    @Test
    fun fluidOuncesUseTheUsDefinition() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.VOLUME,
                listOf(product("Can pack", "3.00", "12", PriceUnit.FLUID_OUNCE_US)),
            ),
        )
        val perLitre = comparison.rows.single().unitPrice.toDouble()
        // 12 × 29.5735295625 mL = 0.35488235475 L → 3.00 / that = 8.453505675…
        assertTrue("was $perLitre", abs(perLitre - 8.453505675) < 1e-8)
    }

    @Test
    fun countComparisonWorks() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.COUNT,
                listOf(
                    product("Twin pack", "1.00", "2", PriceUnit.ITEM),
                    product("Six pack", "1.20", "3", PriceUnit.ITEM),
                ),
            ),
        )
        assertMoney("0.50", comparison.rows[1].unitPrice, "2 items for 1.00")
        assertMoney("0.40", comparison.rows[0].unitPrice, "3 items for 1.20")
        assertEquals("Six pack", comparison.best?.product?.name)
    }

    @Test
    fun lengthComparisonUsesMetres() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.LENGTH,
                listOf(
                    product("Per foot", "1.00", "1", PriceUnit.FOOT),
                    product("Per metre", "3.00", "1", PriceUnit.METRE),
                ),
            ),
        )
        // 1 ft for 1.00 → 1 / 0.3048 = 3.28084 per metre, dearer than 3.00.
        assertEquals("Per metre", comparison.rows[0].product.name)
        assertEquals("Per foot", comparison.rows[1].product.name)
        assertMoney("3.00", comparison.rows[0].unitPrice, "per metre")
        val perFoot = comparison.rows[1].unitPrice.toDouble()
        assertTrue("was $perFoot", abs(perFoot - 1 / 0.3048) < 1e-9)
        assertTrue(comparison.rows[1].unitPrice > BigDecimal("3.2"))
    }

    @Test
    fun incompatibleUnitsAreRejectedWithAMessage() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.MASS,
                listOf(
                    product("Flour", "2.00", "1", PriceUnit.KILOGRAM),
                    product("Milk", "1.50", "1", PriceUnit.LITRE),
                ),
            ),
        )
        assertEquals(1, comparison.rows.size)
        assertEquals("Flour", comparison.rows.single().product.name)
        assertEquals(1, comparison.rejected.size)
        val message = comparison.rejected.single().message
        assertTrue("message was: $message", message.contains("Milk"))
        assertTrue("message was: $message", message.contains("volume"))
    }

    @Test
    fun allIncompatibleProductsFailExplicitly() {
        val result = UnitPriceEngine.compare(
            Measure.MASS,
            listOf(product("Milk", "1.50", "1", PriceUnit.LITRE)),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
        assertTrue(((result as CalcResult.Err).error.message).contains("volume"))
    }

    @Test
    fun emptyProductListIsRejected() {
        assertEquals(ErrorKind.Insufficient, errorKind(UnitPriceEngine.compare(Measure.MASS, emptyList())))
    }

    @Test
    fun invalidQuantityIsRejectedPerProduct() {
        val comparison = ok(
            UnitPriceEngine.compare(
                Measure.MASS,
                listOf(
                    product("Zero quantity", "1.00", "0", PriceUnit.KILOGRAM),
                    product("Good", "1.00", "1", PriceUnit.KILOGRAM),
                ),
            ),
        )
        assertEquals(1, comparison.rows.size)
        assertEquals(1, comparison.rejected.size)
        assertTrue(comparison.rejected.single().message.contains("Quantity must be greater than zero"))
    }

    @Test
    fun negativePriceIsRejected() {
        val result = UnitPriceEngine.normalise(product("Bad", "-1.00", "1", PriceUnit.KILOGRAM))
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun fullDiscountIsRejected() {
        val result = UnitPriceEngine.normalise(product("Free", "1.00", "1", PriceUnit.KILOGRAM, discount = "100"))
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun crossDimensionNoteExplainsTheLimit() {
        assertTrue(UnitPriceEngine.CROSS_DIMENSION_NOTE.contains("cannot be compared"))
    }
}
