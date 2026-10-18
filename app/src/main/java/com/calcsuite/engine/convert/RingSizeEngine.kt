package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * International ring sizes, table-based.
 *
 * The table is the widely published jeweller conversion chart for US sizes 3
 * to 13 in half steps (reference table, not a standard). Published
 * circumferences are rounded, so π × diameter can differ from the listed
 * circumference by up to about 0.2 mm; equivalents between systems are
 * approximate because national scales are not exactly aligned. The engine
 * never fabricates precision: an inexact match is flagged with its delta.
 */
object RingSizeEngine {

    /** One chart row. Sizes are kept as published text (e.g. "N½", "Z+1"). */
    data class RingSizeRow(
        val circumferenceMm: BigDecimal,
        val diameterMm: BigDecimal,
        val us: String,
        val uk: String,
        val eu: String,
        val jp: String,
    )

    /** A matched row plus whether it is the exact value or only the nearest. */
    data class RingSizeMatch(
        val row: RingSizeRow,
        val nearest: Boolean,
        /** Signed input − row difference in mm (only for measurement systems). */
        val deltaMm: BigDecimal? = null,
        val note: String? = null,
    )

    /** Measurement inputs, as opposed to size lookups. */
    const val SYSTEM_US = "us"
    const val SYSTEM_UK = "uk"
    const val SYSTEM_EU = "eu"
    const val SYSTEM_JP = "jp"
    const val SYSTEM_DIAMETER = "diameter_mm"
    const val SYSTEM_CIRCUMFERENCE = "circumference_mm"

    val systems: List<Pair<String, String>> = listOf(
        SYSTEM_CIRCUMFERENCE to "Circumference (mm)",
        SYSTEM_DIAMETER to "Diameter (mm)",
        SYSTEM_US to "US",
        SYSTEM_UK to "UK",
        SYSTEM_EU to "EU",
        SYSTEM_JP to "JP",
    )

    /** Tolerance within which a measurement counts as an exact row hit. */
    private val EXACT_TOLERANCE = BigDecimal("0.05")

    val rows: List<RingSizeRow> = listOf(
        RingSizeRow(bd("44.2"), bd("14.1"), "3", "F", "44", "4"),
        RingSizeRow(bd("45.5"), bd("14.5"), "3.5", "G", "45.5", "5"),
        RingSizeRow(bd("46.8"), bd("14.9"), "4", "H", "47", "6"),
        RingSizeRow(bd("48.0"), bd("15.3"), "4.5", "I", "48", "7"),
        RingSizeRow(bd("49.3"), bd("15.7"), "5", "J½", "49", "8"),
        RingSizeRow(bd("50.6"), bd("16.1"), "5.5", "K½", "50.5", "9"),
        RingSizeRow(bd("51.9"), bd("16.5"), "6", "L½", "51.5", "10"),
        RingSizeRow(bd("53.1"), bd("16.9"), "6.5", "M½", "53", "12"),
        RingSizeRow(bd("54.4"), bd("17.3"), "7", "N½", "54", "14"),
        RingSizeRow(bd("55.7"), bd("17.7"), "7.5", "O½", "55.5", "15"),
        RingSizeRow(bd("56.9"), bd("18.1"), "8", "P½", "57", "16"),
        RingSizeRow(bd("58.2"), bd("18.5"), "8.5", "Q½", "58", "17"),
        RingSizeRow(bd("59.5"), bd("18.9"), "9", "R½", "59.5", "18"),
        RingSizeRow(bd("60.8"), bd("19.4"), "9.5", "S½", "61", "19"),
        RingSizeRow(bd("62.1"), bd("19.8"), "10", "T½", "62", "20"),
        RingSizeRow(bd("63.4"), bd("20.2"), "10.5", "U½", "63", "22"),
        RingSizeRow(bd("64.6"), bd("20.6"), "11", "V½", "64.5", "23"),
        RingSizeRow(bd("65.9"), bd("21.0"), "11.5", "W½", "66", "24"),
        RingSizeRow(bd("67.2"), bd("21.4"), "12", "X½", "67", "25"),
        RingSizeRow(bd("68.5"), bd("21.8"), "12.5", "Y½", "68.5", "26"),
        RingSizeRow(bd("69.7"), bd("22.2"), "13", "Z+1", "69", "27"),
    )

    /** Normalises UK text: "n 1/2" → "N½", "z+1" → "Z+1". */
    private fun normaliseUk(text: String): String =
        text.trim().uppercase().replace(" ", "").replace("1/2", "½")

    private fun rowFor(system: String, text: String): CalcResult<RingSizeMatch> {
        if (system == SYSTEM_DIAMETER || system == SYSTEM_CIRCUMFERENCE) {
            val parsed = Num.parseOrNull(text)
                ?: return err(ErrorKind.InvalidInput, "Enter a measurement in millimetres")
            if (parsed.signum() <= 0) {
                return err(ErrorKind.Domain, "Measurement must be greater than zero")
            }
            val pick = { row: RingSizeRow -> if (system == SYSTEM_DIAMETER) row.diameterMm else row.circumferenceMm }
            val closest = rows.minByOrNull { (pick(it) - parsed).abs() }
                ?: return err(ErrorKind.InvalidInput, "Ring size table is empty")
            val delta = parsed.subtract(pick(closest))
            val exact = delta.abs() <= EXACT_TOLERANCE
            val where = if (system == SYSTEM_DIAMETER) "diameter" else "circumference"
            return ok(
                RingSizeMatch(
                    row = closest,
                    nearest = !exact,
                    deltaMm = delta,
                    note = if (exact) {
                        "Within 0.05 mm of the listed $where ${pick(closest)} mm."
                    } else {
                        "${Num.formatForCopy(parsed)} mm is " +
                            "${Num.formatForCopy(delta.abs())} mm " +
                            (if (delta.signum() > 0) "above" else "below") +
                            " the nearest row (US ${closest.us}, $where ${pick(closest)} mm)."
                    },
                ),
            )
        }

        val parsed = if (system == SYSTEM_UK) null else Num.parseOrNull(text)
        if (parsed == null && system != SYSTEM_UK) {
            return err(ErrorKind.InvalidInput, "Enter a ${systemLabel(system)} size")
        }
        if (parsed != null && parsed.signum() <= 0) return err(ErrorKind.Domain, "Size must be greater than zero")
        val exactRow = when (system) {
            SYSTEM_US -> rows.firstOrNull { Num.parseOrNull(it.us)?.compareTo(parsed) == 0 }
            SYSTEM_EU -> rows.firstOrNull { Num.parseOrNull(it.eu)?.compareTo(parsed) == 0 }
            SYSTEM_JP -> rows.firstOrNull { Num.parseOrNull(it.jp)?.compareTo(parsed) == 0 }
            SYSTEM_UK -> {
                val wanted = normaliseUk(text)
                rows.firstOrNull { normaliseUk(it.uk) == wanted }
            }
            else -> null
        }
        if (exactRow != null) {
            return ok(RingSizeMatch(row = exactRow, nearest = false))
        }
        // Size lookups that do not exist fall back to the numerically closest row.
        val nearestRow = when (system) {
            SYSTEM_US -> rows.minByOrNull { (Num.parseOrNull(it.us)?.subtract(parsed)?.abs() ?: BigDecimal("1e9")) }
            SYSTEM_EU -> rows.minByOrNull { (Num.parseOrNull(it.eu)?.subtract(parsed)?.abs() ?: BigDecimal("1e9")) }
            SYSTEM_JP -> rows.minByOrNull { (Num.parseOrNull(it.jp)?.subtract(parsed)?.abs() ?: BigDecimal("1e9")) }
            else -> null
        }
        if (nearestRow == null) {
            return err(
                ErrorKind.InvalidInput,
                "'${text.trim()}' is not a UK ring size in this chart (F to Z+1)",
            )
        }
        return ok(
            RingSizeMatch(
                row = nearestRow,
                nearest = true,
                note = "${systemLabel(system)} ${text.trim()} is not a listed size; " +
                    "the closest is ${systemLabel(system)} ${nearestLabel(system, nearestRow)}.",
            ),
        )
    }

    private fun nearestLabel(system: String, row: RingSizeRow): String = when (system) {
        SYSTEM_US -> row.us
        SYSTEM_EU -> row.eu
        SYSTEM_JP -> row.jp
        else -> row.uk
    }

    private fun systemLabel(system: String): String = when (system) {
        SYSTEM_US -> "US"
        SYSTEM_UK -> "UK"
        SYSTEM_EU -> "EU"
        SYSTEM_JP -> "JP"
        SYSTEM_DIAMETER -> "diameter"
        SYSTEM_CIRCUMFERENCE -> "circumference"
        else -> system
    }

    /** Row for a size or measurement expressed in [system]. */
    fun convert(system: String, sizeText: String): CalcResult<RingSizeMatch> {
        if (systems.none { it.first == system }) {
            return err(ErrorKind.InvalidInput, "Unknown ring size system")
        }
        return rowFor(system, sizeText)
    }
}

