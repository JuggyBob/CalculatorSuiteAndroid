package com.calcsuite.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL

/** Immutable snapshot of exchange rates relative to [base]. */
data class RatesSnapshot(
    val base: String,
    val rates: Map<String, BigDecimal>,
    /** When this device fetched the snapshot. */
    val fetchedAtMillis: Long,
    /** Provider's own "last update" timestamp, when reported. */
    val providerUpdatedAtMillis: Long,
    val sourceLabel: String,
) {
    fun rateFor(code: String): BigDecimal? =
        if (code == base) BigDecimal.ONE else rates[code]

    fun isStale(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = STALE_AFTER_MS): Boolean =
        nowMillis - fetchedAtMillis > maxAgeMillis

    companion object {
        /** Rates are refreshed daily by the provider; warn after 24 h. */
        const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * Parses the JSON document returned by open.er-api.com.
 *
 * ```
 * {"result":"success","time_last_update_unix":1699999999,"base_code":"USD",
 *  "rates":{"EUR":0.92, "GBP":0.79, ...}}
 * ```
 * Pure function so that it can be unit-tested without a network connection.
 */
object RateParser {
    const val SOURCE_LABEL = "open.er-api.com (reference rates)"

    fun parse(json: String, fetchedAtMillis: Long): CalcResult<RatesSnapshot> {
        return try {
            val root = JSONObject(json)
            val status = root.optString("result", "success")
            if (status != "success") {
                return err(ErrorKind.Unavailable, root.optString("error-type", "Rate provider reported an error"))
            }
            val base = root.optString("base_code", root.optString("base", "USD"))
            val ratesObject = root.optJSONObject("rates")
                ?: return err(ErrorKind.Unavailable, "Rate data was missing from the provider response")
            val rates = HashMap<String, BigDecimal>(ratesObject.length())
            val keys = ratesObject.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val raw = ratesObject.optDouble(code, Double.NaN)
                if (raw.isFinite() && raw > 0.0) {
                    rates[code] = BigDecimal.valueOf(raw)
                }
            }
            if (rates.isEmpty()) {
                return err(ErrorKind.Unavailable, "The provider returned no usable rates")
            }
            val lastUpdate = root.optLong("time_last_update_unix", fetchedAtMillis / 1000L) * 1000L
            ok(
                RatesSnapshot(
                    base = base,
                    rates = rates,
                    fetchedAtMillis = fetchedAtMillis,
                    providerUpdatedAtMillis = lastUpdate,
                    sourceLabel = SOURCE_LABEL,
                ),
            )
        } catch (_: Exception) {
            err(ErrorKind.Unavailable, "The exchange-rate data could not be read")
        }
    }

    /**
     * Converts [amount] between two currencies using a single base-relative
     * snapshot. Cross rates are derived as rate(to) / rate(from).
     */
    fun convert(
        amount: BigDecimal,
        from: String,
        to: String,
        snapshot: RatesSnapshot,
    ): CalcResult<BigDecimal> {
        val fromRate = snapshot.rateFor(from)
            ?: return err(ErrorKind.InvalidInput, "No rate available for $from")
        val toRate = snapshot.rateFor(to)
            ?: return err(ErrorKind.InvalidInput, "No rate available for $to")
        if (fromRate.signum() == 0) {
            return err(ErrorKind.DivisionByZero, "The rate for $from is zero")
        }
        val value = amount.multiply(toRate, MathContext(20, RoundingMode.HALF_UP))
            .divide(fromRate, MathContext(20, RoundingMode.HALF_UP))
        return ok(value)
    }

    /** The pair rate `from → to` for display. */
    fun pairRate(from: String, to: String, snapshot: RatesSnapshot): CalcResult<BigDecimal> =
        convert(BigDecimal.ONE, from, to, snapshot)
}

/**
 * Loads exchange rates from open.er-api.com, a free, key-less reference-rate
 * service that publishes daily ECB-style rates, and keeps the last successful
 * response in DataStore so the converter works offline.
 */
class CurrencyRepository(context: Context) {

    private val store = context.appDataStore

    /** Cached snapshot, or null when the app has never fetched successfully. */
    val cached: Flow<RatesSnapshot?> = store.data.map { prefs ->
        val json = prefs[KEY_RATES_JSON] ?: return@map null
        val fetchedAt = prefs[KEY_RATES_FETCHED] ?: 0L
        RateParser.parse(json, fetchedAt).valueOrNull()
    }

    /** Fetches fresh rates and stores them on success. */
    suspend fun refresh(base: String = "USD"): CalcResult<RatesSnapshot> = withContext(Dispatchers.IO) {
        val result = fetchRates(base)
        if (result is CalcResult.Ok) {
            store.edit { prefs ->
                prefs[KEY_RATES_JSON] = result.value.toJson()
                prefs[KEY_RATES_FETCHED] = result.value.fetchedAtMillis
                prefs[KEY_RATES_BASE] = result.value.base
            }
        }
        result
    }

    private fun RatesSnapshot.toJson(): String {
        val ratesObject = JSONObject()
        rates.forEach { (code, value) -> ratesObject.put(code, value.toDouble()) }
        return JSONObject().apply {
            put("result", "success")
            put("base_code", base)
            put("time_last_update_unix", providerUpdatedAtMillis / 1000L)
            put("rates", ratesObject)
        }.toString()
    }

    private fun fetchRates(base: String): CalcResult<RatesSnapshot> {
        val url = URL("https://open.er-api.com/v6/latest/$base")
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CalcSuite/1.0 (Android)")
            }
            val code = connection.responseCode
            if (code != 200) {
                return err(ErrorKind.Unavailable, "Exchange-rate service returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            RateParser.parse(body, System.currentTimeMillis())
        } catch (e: java.net.UnknownHostException) {
            err(ErrorKind.Unavailable, "No internet connection")
        } catch (e: java.net.SocketTimeoutException) {
            err(ErrorKind.Unavailable, "The exchange-rate service timed out")
        } catch (e: Exception) {
            err(ErrorKind.Unavailable, "Could not reach the exchange-rate service")
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        val KEY_RATES_JSON = stringPreferencesKey("rates_json")
        val KEY_RATES_FETCHED = longPreferencesKey("rates_fetched_at")
        val KEY_RATES_BASE = stringPreferencesKey("rates_base")
    }
}
