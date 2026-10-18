package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.map
import com.calcsuite.core.ok
import com.calcsuite.data.RatesSnapshot
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Pure conversion helpers over a [RatesSnapshot].
 *
 * All intermediate math uses 20 significant decimal digits (never binary
 * floating point). Rounding to the target currency's minor unit happens only
 * at the settlement/display boundary and is always HALF_UP; the full-precision
 * result stays available on [Conversion.exactResult].
 */
object CurrencyEngine {

    /** Intermediate-precision context: 20 significant digits, HALF_UP. */
    val MC: MathContext = MathContext(20, RoundingMode.HALF_UP)

    private val HUNDRED = BigDecimal("100")

    /**
     * The outcome of one conversion.
     *
     * @param exactResult full-precision converted amount (20 digits).
     * @param displayResult [exactResult] rounded to the target currency's
     *        minor unit — the amount a cash settlement would use.
     * @param pairRate `1 from = x to`, full precision.
     */
    data class Conversion(
        val from: CurrencyDef,
        val to: CurrencyDef,
        val amount: BigDecimal,
        val exactResult: BigDecimal,
        val displayResult: BigDecimal,
        val pairRate: BigDecimal,
    )

    /** Age/staleness information for the rate panel. */
    data class RateStatus(
        val ageMillis: Long,
        val stale: Boolean,
        /** True when the provider's own timestamp is older than the fetch. */
        val providerUpdateOlder: Boolean,
        val sourceLabel: String,
    )

    /** Converts [amount] from [fromCode] to [toCode] through the snapshot base. */
    fun convert(
        amount: BigDecimal,
        fromCode: String,
        toCode: String,
        snapshot: RatesSnapshot,
    ): CalcResult<Conversion> {
        val from = CurrencyCatalog.byCode(fromCode)
            ?: return err(ErrorKind.InvalidInput, "Unknown currency $fromCode")
        val to = CurrencyCatalog.byCode(toCode)
            ?: return err(ErrorKind.InvalidInput, "Unknown currency $toCode")
        if (amount.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Amount must not be negative")
        }
        val fromRate = snapshot.rateFor(from.code)
            ?: return err(ErrorKind.InvalidInput, "No exchange rate available for ${from.code}")
        val toRate = snapshot.rateFor(to.code)
            ?: return err(ErrorKind.InvalidInput, "No exchange rate available for ${to.code}")
        if (fromRate.signum() <= 0) {
            return err(ErrorKind.DivisionByZero, "The rate for ${from.code} is not positive")
        }
        if (toRate.signum() <= 0) {
            return err(ErrorKind.DivisionByZero, "The rate for ${to.code} is not positive")
        }
        val exact = amount.multiply(toRate, MC).divide(fromRate, MC)
        val pair = toRate.divide(fromRate, MC)
        return ok(
            Conversion(
                from = from,
                to = to,
                amount = amount,
                exactResult = exact,
                displayResult = displayRound(exact, to),
                pairRate = pair,
            ),
        )
    }

    /** The `from → to` pair rate (`1 from = x to`). */
    fun pairRate(fromCode: String, toCode: String, snapshot: RatesSnapshot): CalcResult<BigDecimal> =
        convert(BigDecimal.ONE, fromCode, toCode, snapshot).map { it.pairRate }

    /** Rounds [value] to the currency's minor unit (HALF_UP). */
    fun displayRound(value: BigDecimal, currency: CurrencyDef): BigDecimal =
        value.setScale(currency.minorUnits, RoundingMode.HALF_UP)

    /** Same as [displayRound] but resolves the currency from its code. */
    fun roundToMinorUnits(value: BigDecimal, code: String): CalcResult<BigDecimal> {
        val currency = CurrencyCatalog.byCode(code)
            ?: return err(ErrorKind.InvalidInput, "Unknown currency $code")
        return ok(displayRound(value, currency))
    }

    /**
     * Formats a rate for compact display: rounded to [significantDigits]
     * significant digits, trailing zeros removed, never scientific notation
     * for the usual range.
     */
    fun formatRate(rate: BigDecimal, significantDigits: Int = 6): String {
        val digits = significantDigits.coerceIn(2, 15)
        return rate.round(MathContext(digits, RoundingMode.HALF_UP))
            .stripTrailingZeros()
            .toPlainString()
    }

    /**
     * Staleness for the UI: the app warns after
     * [RatesSnapshot.STALE_AFTER_MS] (24 h) or when the provider's own "last
     * update" is already older than the fetch itself.
     */
    fun statusOf(snapshot: RatesSnapshot, nowMillis: Long = System.currentTimeMillis()): RateStatus {
        val age = (nowMillis - snapshot.fetchedAtMillis).coerceAtLeast(0L)
        return RateStatus(
            ageMillis = age,
            stale = snapshot.isStale(nowMillis),
            providerUpdateOlder = snapshot.providerUpdatedAtMillis < snapshot.fetchedAtMillis,
            sourceLabel = snapshot.sourceLabel,
        )
    }

    /** Human-readable age, e.g. "3 minutes ago" / "2 days ago". */
    fun describeAge(ageMillis: Long): String {
        val minutes = ageMillis / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 1L -> "just now"
            minutes < 60L -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
            hours < 24L -> "$hours hour${if (hours == 1L) "" else "s"} ago"
            days < 30L -> "$days day${if (days == 1L) "" else "s"} ago"
            else -> "${days / 30L} month${if (days / 30L == 1L) "" else "s"} ago"
        }
    }

    /** Converts a percentage change of a base rate (used by the UI hint row). */
    fun percentChange(from: BigDecimal, to: BigDecimal): CalcResult<BigDecimal> {
        if (from.signum() == 0) return err(ErrorKind.DivisionByZero, "The starting rate is zero")
        return ok(to.subtract(from, MC).divide(from, MC).multiply(HUNDRED, MC))
    }
}
