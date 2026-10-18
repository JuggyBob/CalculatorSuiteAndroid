package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * Shoe sizes, table-based, for three audiences.
 *
 * The tables are compiled from widely published conversion charts (EU / US /
 * UK / JP foot-length tables). Shoe lasts differ between brands and even
 * between models, sizes overlap by up to half a size, and published tables
 * disagree by half a size at several rows: the equivalents here are
 * approximate and users are advised to measure their feet and compare with
 * the foot lengths shown rather than trusting a single cross-reference.
 *
 * - Men:      US 6 → 13 (EU 38.5 → 48), half steps.
 * - Women:    US 4 → 12 (EU 35 → 44), half steps.
 * - Children: EU 20 → 34; the child scale wraps from 13 into youth sizes
 *   (US 1/1.5/2, UK 13/13.5/1.5) as published charts do.
 */
object ShoeSizeEngine {

    enum class Audience(val label: String) {
        MEN("Men"),
        WOMEN("Women"),
        CHILDREN("Children"),
    }

    /** One table row. Sizes are text so that youth suffixes ("2Y") survive. */
    data class ShoeRow(
        val footLengthMm: Int,
        val eu: String,
        val us: String,
        val uk: String,
        val jp: String,
    )

    data class ShoeMatch(
        val audience: Audience,
        val row: ShoeRow,
        val note: String? = null,
    )

    val systems: List<Pair<String, String>> = listOf(
        "eu" to "EU",
        "us" to "US",
        "uk" to "UK",
        "jp" to "JP (cm)",
    )

    /** Exact-match tolerance for foot lengths, in mm. */
    private val LENGTH_TOLERANCE = BigDecimal("0.5")

    private val men: List<ShoeRow> = listOf(
        ShoeRow(240, "38.5", "6", "5.5", "24.0"),
        ShoeRow(245, "39", "6.5", "6", "24.5"),
        ShoeRow(250, "40", "7", "6.5", "25.0"),
        ShoeRow(255, "40.5", "7.5", "7", "25.5"),
        ShoeRow(260, "41", "8", "7.5", "26.0"),
        ShoeRow(265, "42", "8.5", "8", "26.5"),
        ShoeRow(270, "42.5", "9", "8.5", "27.0"),
        ShoeRow(275, "43", "9.5", "9", "27.5"),
        ShoeRow(280, "44", "10", "9.5", "28.0"),
        ShoeRow(285, "44.5", "10.5", "10", "28.5"),
        ShoeRow(290, "45", "11", "10.5", "29.0"),
        ShoeRow(295, "45.5", "11.5", "11", "29.5"),
        ShoeRow(300, "46", "12", "11.5", "30.0"),
        ShoeRow(305, "47", "12.5", "12", "30.5"),
        ShoeRow(310, "48", "13", "12.5", "31.0"),
    )

    private val women: List<ShoeRow> = listOf(
        ShoeRow(205, "35", "4", "2", "20.5"),
        ShoeRow(209, "35.5", "4.5", "2.5", "21.0"),
        ShoeRow(214, "36", "5", "3", "21.5"),
        ShoeRow(218, "36.5", "5.5", "3.5", "22.0"),
        ShoeRow(223, "37", "6", "4", "22.0"),
        ShoeRow(227, "37.5", "6.5", "4.5", "22.5"),
        ShoeRow(232, "38", "7", "5", "23.0"),
        ShoeRow(236, "38.5", "7.5", "5.5", "23.5"),
        ShoeRow(241, "39", "8", "6", "24.0"),
        ShoeRow(245, "40", "8.5", "6.5", "24.5"),
        ShoeRow(250, "40.5", "9", "7", "25.0"),
        ShoeRow(254, "41", "9.5", "7.5", "25.5"),
        ShoeRow(259, "42", "10", "8", "26.0"),
        ShoeRow(263, "42.5", "10.5", "8.5", "26.5"),
        ShoeRow(268, "43", "11", "9", "27.0"),
        ShoeRow(272, "43.5", "11.5", "9.5", "27.5"),
        ShoeRow(277, "44", "12", "10", "28.0"),
    )

    private val children: List<ShoeRow> = listOf(
        ShoeRow(120, "20", "4", "3.5", "12.0"),
        ShoeRow(127, "21", "5", "4.5", "12.5"),
        ShoeRow(133, "22", "5.5", "5", "13.0"),
        ShoeRow(140, "23", "6.5", "6", "14.0"),
        ShoeRow(147, "24", "7.5", "7", "14.5"),
        ShoeRow(153, "25", "8", "7.5", "15.0"),
        ShoeRow(160, "26", "9", "8.5", "16.0"),
        ShoeRow(167, "27", "9.5", "9", "16.5"),
        ShoeRow(173, "28", "10.5", "10", "17.5"),
        ShoeRow(180, "29", "11", "10.5", "18.0"),
        ShoeRow(187, "30", "12", "11.5", "19.0"),
        ShoeRow(193, "31", "13", "12.5", "19.5"),
        ShoeRow(200, "32", "1", "13", "20.0"),
        ShoeRow(207, "33", "1.5", "13.5", "20.5"),
        ShoeRow(214, "34", "2", "1.5", "21.0"),
    )

    fun rowsFor(audience: Audience): List<ShoeRow> = when (audience) {
        Audience.MEN -> men
        Audience.WOMEN -> women
        Audience.CHILDREN -> children
    }

    private fun sizeOf(row: ShoeRow, system: String): String? = when (system) {
        "eu" -> row.eu
        "us" -> row.us
        "uk" -> row.uk
        "jp" -> row.jp
        else -> null
    }

    /** Converts a size to the other systems of the same audience. */
    fun convert(audience: Audience, system: String, sizeText: String): CalcResult<ShoeMatch> {
        if (systems.none { it.first == system }) {
            return err(ErrorKind.InvalidInput, "Unknown shoe size system")
        }
        val wanted = Num.parseOrNull(sizeText)
            ?: return err(ErrorKind.InvalidInput, "Enter a ${audience.label.lowercase()} shoe size")
        if (wanted.signum() < 0) return err(ErrorKind.Domain, "Size must not be negative")
        val table = rowsFor(audience)
        val exact = table.firstOrNull { row ->
            val value = sizeOf(row, system)
            value != null && Num.parseOrNull(value)?.compareTo(wanted) == 0
        }
        if (exact != null) return ok(ShoeMatch(audience, exact))

        val withDistance = table.mapNotNull { row ->
            val value = sizeOf(row, system)?.let { Num.parseOrNull(it) } ?: return@mapNotNull null
            Triple(row, value, (value - wanted).abs())
        }.sortedBy { it.third }
        if (withDistance.isEmpty()) {
            return err(ErrorKind.InvalidInput, "No sizes are listed for ${audience.label.lowercase()}")
        }
        val systemLabel = systems.first { it.first == system }.second
        val nearest = withDistance.take(2).joinToString(" and ") {
            "$systemLabel ${sizeOf(it.first, system)} (${it.first.footLengthMm} mm)"
        }
        return err(
            ErrorKind.InvalidInput,
            "$systemLabel ${sizeText.trim()} is not a listed ${audience.label.lowercase()} size; nearest sizes: $nearest",
        )
    }

    /** Estimates the best row for a measured foot length in millimetres. */
    fun byFootLength(audience: Audience, mm: BigDecimal): CalcResult<ShoeMatch> {
        if (mm.signum() <= 0) return err(ErrorKind.Domain, "Foot length must be greater than zero")
        val table = rowsFor(audience)
        val pick = table.minByOrNull { (BigDecimal(it.footLengthMm) - mm).abs() }
            ?: return err(ErrorKind.InvalidInput, "No sizes are listed for ${audience.label.lowercase()}")
        val delta = mm.subtract(BigDecimal(pick.footLengthMm))
        val range = "${table.first().footLengthMm}–${table.last().footLengthMm} mm"
        if (delta.abs() > BigDecimal("25")) {
            return err(
                ErrorKind.Domain,
                "${Num.formatForCopy(mm)} mm is far outside the ${audience.label.lowercase()} range ($range); " +
                    "check the audience or the measurement",
            )
        }
        val exact = delta.abs() <= LENGTH_TOLERANCE
        val note = if (exact) {
            "Measured foot length matches the ${pick.footLengthMm} mm row."
        } else {
            "${Num.formatForCopy(delta.abs())} mm " +
                (if (delta.signum() > 0) "above" else "below") +
                " the ${pick.footLengthMm} mm row; fit varies by brand"
        }
        return ok(ShoeMatch(audience, pick, note))
    }

    /** All rows with both sizes and foot lengths, for measurement advice. */
    fun allRows(): List<Triple<Audience, ShoeRow, Int>> =
        Audience.values().flatMap { audience -> rowsFor(audience).map { Triple(audience, it, it.footLengthMm) } }
}
