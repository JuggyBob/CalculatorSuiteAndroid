package com.calcsuite.engine.electrical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The units and their prefixes.
 *
 * The prefixes are the same everywhere, so a value can never be scaled twice
 * or by the wrong power of ten.
 */
class ElectricalUnitsTest {

    @Test fun thePrefixesAreInOrder() {
        val exponents = ElectricalUnits.prefixes.map { it.exponent }
        assertEquals("the prefixes go from small to large", exponents.sorted(), exponents)
        assertTrue("the plain prefix is there", ElectricalUnits.prefixes.any { it.id == "none" })
        assertTrue("the kilo prefix is there", ElectricalUnits.prefixes.any { it.id == "kilo" })
    }

    @Test fun aPrefixHasOneExponent() {
        assertEquals("milli", -3, ElectricalUnits.prefix("milli").exponent)
        assertEquals("kilo", 3, ElectricalUnits.prefix("kilo").exponent)
        assertEquals("micro", -6, ElectricalUnits.prefix("micro").exponent)
        assertEquals("none", 0, ElectricalUnits.prefix("none").exponent)
    }

    @Test fun anUnknownPrefixFallsBackOnThePlainOne() {
        assertEquals("none", ElectricalUnits.prefix("nonsense").id)
    }

    @Test fun aValueIsScaledIntoItsBaseUnit() {
        assertEquals(
            "1200",
            ElectricalUnits.toBase(BigDecimal("1.2"), "kilo").toPlainString(),
        )
        assertEquals(
            "0.0012",
            ElectricalUnits.toBase(BigDecimal("1.2"), "milli").toPlainString(),
        )
        assertEquals(
            "1200000000",
            ElectricalUnits.toBase(BigDecimal("1.2"), "giga").toPlainString(),
        )
    }

    @Test fun aValueWithNoPrefixIsNotScaled() {
        assertEquals(
            "12",
            ElectricalUnits.toBase(BigDecimal("12"), "none").toPlainString(),
        )
    }

    @Test fun aMissingValueStaysMissing() {
        assertNull("there is no value", ElectricalUnits.toBaseOrNull(null, "kilo"))
    }

    @Test fun aValueIsWrittenWithThePrefixThatReadsBest() {
        assertEquals("120 mA", ElectricalUnits.display(BigDecimal("0.12"), ElectricalUnits.Unit.CURRENT))
        assertEquals("5 k\u03a9", ElectricalUnits.display(BigDecimal("5000"), ElectricalUnits.Unit.RESISTANCE))
        assertEquals("12 V", ElectricalUnits.display(BigDecimal("12"), ElectricalUnits.Unit.VOLTAGE))
        assertEquals("1.5 W", ElectricalUnits.display(BigDecimal("1.5"), ElectricalUnits.Unit.POWER))
        assertEquals("0 A", ElectricalUnits.display(BigDecimal("0"), ElectricalUnits.Unit.CURRENT))
    }

    @Test fun aMissingValueIsWrittenAsAQuestion() {
        assertEquals("?", ElectricalUnits.display(null, ElectricalUnits.Unit.VOLTAGE))
    }

    @Test fun theValueInTheBaseUnitIsWrittenAsItIs() {
        assertEquals(
            "12 V",
            ElectricalUnits.base(BigDecimal("12"), ElectricalUnits.Unit.VOLTAGE),
        )
        assertEquals(
            "0.12 A",
            ElectricalUnits.base(BigDecimal("0.12"), ElectricalUnits.Unit.CURRENT),
        )
    }

    @Test fun everyQuantityHasItsSymbol() {
        assertEquals("V", ElectricalUnits.Unit.VOLTAGE.symbol)
        assertEquals("A", ElectricalUnits.Unit.CURRENT.symbol)
        assertEquals("\u03a9", ElectricalUnits.Unit.RESISTANCE.symbol)
        assertEquals("W", ElectricalUnits.Unit.POWER.symbol)
        assertEquals("F", ElectricalUnits.Unit.CAPACITANCE.symbol)
    }
}
