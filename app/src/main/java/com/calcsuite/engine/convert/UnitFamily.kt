package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * One unit inside a conversion family.
 *
 * `valueInBase = value * factor + offset` (offset is zero for purely linear
 * units and is used for affine scales such as temperature).
 *
 * @param group optional grouping shown in the picker ("Metric", "US customary", …)
 */
data class UnitDef(
    val id: String,
    val label: String,
    val symbol: String,
    val factor: BigDecimal,
    val offset: BigDecimal = BigDecimal.ZERO,
    val group: String? = null,
) {
    val isLinear: Boolean get() = offset.signum() == 0
}

/**
 * A conversion family: a base unit plus a validated set of related units.
 *
 * Families are deliberately explicit per physical quantity: the app never
 * reuses a linear factor across incompatible dimensions (m² is not m·10 000).
 */
class UnitFamily(
    val id: String,
    val name: String,
    val baseUnitId: String,
    val units: List<UnitDef>,
    val notes: List<String> = emptyList(),
    /** Extra validation, e.g. rejecting temperatures below absolute zero. */
    val baseValidator: ((BigDecimal) -> String?)? = null,
) {
    init {
        require(units.isNotEmpty()) { "A family needs at least one unit" }
        require(units.any { it.id == baseUnitId }) { "Base unit $baseUnitId missing from $id" }
        require(units.map { it.id }.toSet().size == units.size) { "Duplicate unit ids in $id" }
        require(units.all { it.factor.signum() != 0 }) { "Zero factors are not allowed in $id" }
    }

    fun unit(id: String): UnitDef? = units.firstOrNull { it.id == id }

    fun requireUnit(id: String): UnitDef = unit(id) ?: error("Unknown unit $id in family $id")

    /** Converts [value] between two units of this family. */
    fun convert(value: BigDecimal, fromId: String, toId: String): CalcResult<BigDecimal> {
        val from = unit(fromId) ?: return err(ErrorKind.InvalidInput, "Unknown source unit")
        val to = unit(toId) ?: return err(ErrorKind.InvalidInput, "Unknown target unit")
        return convert(value, from, to)
    }

    fun convert(value: BigDecimal, from: UnitDef, to: UnitDef): CalcResult<BigDecimal> {
        val base = value.multiply(from.factor, CTX).add(from.offset, CTX)
        baseValidator?.invoke(base)?.let { message ->
            return err(ErrorKind.Domain, message)
        }
        if (to.factor.signum() == 0) return err(ErrorKind.DivisionByZero, "Target unit has a zero factor")
        val result = base.subtract(to.offset, CTX).divide(to.factor, CTX)
        return ok(result)
    }

    /** The multiplicative factor between two units (for display: 1 from = x to). */
    fun factorBetween(fromId: String, toId: String): CalcResult<BigDecimal> {
        val from = unit(fromId) ?: return err(ErrorKind.InvalidInput, "Unknown source unit")
        val to = unit(toId) ?: return err(ErrorKind.InvalidInput, "Unknown target unit")
        if (!from.isLinear || !to.isLinear) {
            // Affine families do not have a single factor; report the value of 1.
            return convert(BigDecimal.ONE, from, to)
        }
        return ok(from.factor.divide(to.factor, CTX))
    }

    companion object {
        val CTX = MathContext(34, RoundingMode.HALF_UP)
    }
}

// ------------------------------------------------------------------ helpers

/** `1 × 10^n` as a BigDecimal. */
internal fun pow10(n: Int): BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(n)

internal fun bd(text: String): BigDecimal = BigDecimal(text)

internal fun linear(
    id: String,
    label: String,
    symbol: String,
    factor: BigDecimal,
    group: String? = null,
): UnitDef = UnitDef(id, label, symbol, factor, BigDecimal.ZERO, group)

/**
 * Families that are reciprocal rather than linear (fuel consumption) implement
 * this small interface instead of [UnitFamily].
 */
interface ReciprocalFamily {
    val id: String
    val name: String
    val unitIds: List<String>
    fun label(id: String): String
    fun symbol(id: String): String
    fun convert(value: BigDecimal, fromId: String, toId: String): CalcResult<BigDecimal>
}
