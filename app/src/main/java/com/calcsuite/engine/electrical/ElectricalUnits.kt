package com.calcsuite.engine.electrical

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The units the electrical tools use.
 *
 * Every quantity is held in its SI base unit - volts, amperes, ohms, watts -
 * and the prefix is only a way of writing it. The same table is used for the
 * solver, the calculators and the screen, so a prefix can never be applied
 * twice or to the wrong quantity.
 */
object ElectricalUnits {

    /** The base quantities of the electrical model. */
    enum class Unit(val symbol: String, val base: String) {
        VOLTAGE("V", "volt"),
        CURRENT("A", "ampere"),
        RESISTANCE("Ω", "ohm"),
        POWER("W", "watt"),
        CAPACITANCE("F", "farad"),
        TIME("s", "second"),
        ENERGY("J", "joule"),
    }

    /** A metric prefix. */
    data class Prefix(val id: String, val label: String, val symbol: String, val exponent: Int) {
        /** The short name of the prefix, for a row of them. */
        val shortLabel: String get() = if (symbol.isEmpty()) "—" else symbol
    }

    /** The prefixes the tools offer, from smallest to largest. */
    val prefixes: List<Prefix> = listOf(
        Prefix("pico", "pico", "p", -12),
        Prefix("nano", "nano", "n", -9),
        Prefix("micro", "micro", "µ", -6),
        Prefix("milli", "milli", "m", -3),
        Prefix("none", "none", "", 0),
        Prefix("kilo", "kilo", "k", 3),
        Prefix("mega", "mega", "M", 6),
        Prefix("giga", "giga", "G", 9),
    )

    /** The context used for the exact arithmetic. */
    val context: MathContext = MathContext(34, RoundingMode.HALF_UP)

    fun prefix(id: String): Prefix = prefixes.firstOrNull { it.id == id } ?: prefixes[4]

    /** The value in the base unit. */
    fun toBase(value: BigDecimal, prefixId: String): BigDecimal =
        value.scaleByPowerOfTen(prefix(prefixId).exponent)

    /** The value in the base unit, from a number the user typed. */
    fun toBaseOrNull(value: BigDecimal?, prefixId: String): BigDecimal? =
        value?.let { toBase(it, prefixId) }

    /**
     * The value written in the prefix that reads best.
     *
     * A value is written with the prefix that puts its first digit between
     * one and ten, so that 0.12 A is written as 120 mA and 5000 Ω as 5 kΩ.
     * The number of digits is the precision, and the value is only rounded
     * here, never inside the solver.
     */
    fun display(value: BigDecimal?, unit: Unit, precision: Int = 6): String {
        if (value == null) return "?"
        if (value.signum() == 0) return "0 ${unit.symbol}"
        val magnitude = value.abs()
        val chosen = prefixes
            .filter { it.exponent <= 3 }
            .lastOrNull { magnitude.scaleByPowerOfTen(-it.exponent) >= BigDecimal.ONE }
            ?: prefixes[0]
        val scaled = value.scaleByPowerOfTen(-chosen.exponent).setScale(precision, RoundingMode.HALF_UP)
        val plain = scaled.stripTrailingZeros().toPlainString()
        return "$plain ${chosen.symbol}${unit.symbol}"
    }

    /**
     * The value written in the base unit, for a result that has to be
     * compared with another one.
     */
    fun base(value: BigDecimal?, unit: Unit): String {
        if (value == null) return "?"
        return "${value.stripTrailingZeros().toPlainString()} ${unit.symbol}"
    }
}
