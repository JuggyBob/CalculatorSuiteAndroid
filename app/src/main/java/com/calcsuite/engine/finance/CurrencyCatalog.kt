package com.calcsuite.engine.finance

/**
 * Static metadata for one currency.
 *
 * Everything here is real registry data (ISO 4217): the code, the English
 * name, the symbol commonly used on receipts and the number of decimal digits
 * used by the currency's minor unit. `aliases` only holds widely accepted
 * synonyms to improve search; it never invents facts.
 *
 * @param minorUnits digits used when a money amount is presented in this
 *        currency: 2 for most, 0 for e.g. JPY/KRW/VND, 3 for KWD/BHD/OMR.
 */
data class CurrencyDef(
    val code: String,
    val name: String,
    val symbol: String,
    val minorUnits: Int,
    val aliases: List<String> = emptyList(),
)

/**
 * Currencies offered by the currency converter. Ordered roughly by how often
 * they are looked up; [search] is order-preserving.
 *
 * The list is intentionally static: exchange rates change, currency identity
 * does not. Rate data comes from `com.calcsuite.data.CurrencyRepository` at
 * runtime and is never baked into this catalog.
 */
object CurrencyCatalog {

    val currencies: List<CurrencyDef> = listOf(
        CurrencyDef("USD", "US dollar", "$", 2, listOf("dollar", "greenback")),
        CurrencyDef("EUR", "Euro", "€", 2, listOf("euro")),
        CurrencyDef("GBP", "British pound", "£", 2, listOf("pound sterling", "sterling", "quid")),
        CurrencyDef("JPY", "Japanese yen", "¥", 0, listOf("yen")),
        CurrencyDef("CHF", "Swiss franc", "CHF", 2, listOf("franc")),
        CurrencyDef("CAD", "Canadian dollar", "C$", 2, listOf("canadian")),
        CurrencyDef("AUD", "Australian dollar", "A$", 2, listOf("australian")),
        CurrencyDef("CNY", "Chinese yuan renminbi", "¥", 2, listOf("renminbi", "rmb", "yuan")),
        CurrencyDef("HKD", "Hong Kong dollar", "HK$", 2, listOf("hong kong")),
        CurrencyDef("SGD", "Singapore dollar", "S$", 2, listOf("singapore")),
        CurrencyDef("NZD", "New Zealand dollar", "NZ$", 2, listOf("new zealand")),
        CurrencyDef("SEK", "Swedish krona", "kr", 2, listOf("swedish", "krona")),
        CurrencyDef("NOK", "Norwegian krone", "kr", 2, listOf("norwegian", "krone")),
        CurrencyDef("DKK", "Danish krone", "kr", 2, listOf("danish")),
        CurrencyDef("PLN", "Polish złoty", "zł", 2, listOf("zloty", "polish")),
        CurrencyDef("CZK", "Czech koruna", "Kč", 2, listOf("koruna", "czech")),
        CurrencyDef("HUF", "Hungarian forint", "Ft", 2, listOf("forint", "hungarian")),
        CurrencyDef("RON", "Romanian leu", "lei", 2, listOf("leu", "romanian")),
        CurrencyDef("BGN", "Bulgarian lev", "лв", 2, listOf("lev", "bulgarian")),
        CurrencyDef("TRY", "Turkish lira", "₺", 2, listOf("lira", "turkish")),
        CurrencyDef("RUB", "Russian ruble", "₽", 2, listOf("rouble", "russian")),
        CurrencyDef("UAH", "Ukrainian hryvnia", "₴", 2, listOf("hryvnia", "ukrainian")),
        CurrencyDef("INR", "Indian rupee", "₹", 2, listOf("rupee", "indian")),
        CurrencyDef("PKR", "Pakistani rupee", "₨", 2, listOf("pakistani")),
        CurrencyDef("BDT", "Bangladeshi taka", "৳", 2, listOf("taka", "bangladeshi")),
        CurrencyDef("LKR", "Sri Lankan rupee", "Rs", 2, listOf("sri lankan")),
        CurrencyDef("KRW", "South Korean won", "₩", 0, listOf("won", "korean")),
        CurrencyDef("TWD", "New Taiwan dollar", "NT$", 2, listOf("taiwan")),
        CurrencyDef("THB", "Thai baht", "฿", 2, listOf("baht", "thai")),
        CurrencyDef("MYR", "Malaysian ringgit", "RM", 2, listOf("ringgit", "malaysian")),
        CurrencyDef("IDR", "Indonesian rupiah", "Rp", 2, listOf("rupiah", "indonesian")),
        CurrencyDef("PHP", "Philippine peso", "₱", 2, listOf("philippine", "piso")),
        CurrencyDef("VND", "Vietnamese đồng", "₫", 0, listOf("dong", "vietnamese")),
        CurrencyDef("AED", "UAE dirham", "د.إ", 2, listOf("dirham", "emirati")),
        CurrencyDef("SAR", "Saudi riyal", "﷼", 2, listOf("riyal", "saudi")),
        CurrencyDef("QAR", "Qatari riyal", "﷼", 2, listOf("qatari")),
        CurrencyDef("KWD", "Kuwaiti dinar", "د.ك", 3, listOf("dinar", "kuwaiti")),
        CurrencyDef("BHD", "Bahraini dinar", "د.ب", 3, listOf("bahraini")),
        CurrencyDef("OMR", "Omani rial", "﷼", 3, listOf("omani", "rial")),
        CurrencyDef("ILS", "Israeli new shekel", "₪", 2, listOf("shekel", "israeli")),
        CurrencyDef("EGP", "Egyptian pound", "E£", 2, listOf("egyptian")),
        CurrencyDef("ZAR", "South African rand", "R", 2, listOf("rand", "south african")),
        CurrencyDef("NGN", "Nigerian naira", "₦", 2, listOf("naira", "nigerian")),
        CurrencyDef("KES", "Kenyan shilling", "KSh", 2, listOf("shilling", "kenyan")),
        CurrencyDef("GHS", "Ghanaian cedi", "₵", 2, listOf("cedi", "ghanaian")),
        CurrencyDef("MAD", "Moroccan dirham", "د.م.", 2, listOf("moroccan")),
        CurrencyDef("BRL", "Brazilian real", "R$", 2, listOf("real", "brazilian")),
        CurrencyDef("MXN", "Mexican peso", "MX$", 2, listOf("mexican")),
        CurrencyDef("ARS", "Argentine peso", "AR$", 2, listOf("argentine")),
        CurrencyDef("CLP", "Chilean peso", "CLP$", 0, listOf("chilean")),
        CurrencyDef("COP", "Colombian peso", "COL$", 2, listOf("colombian")),
        CurrencyDef("PEN", "Peruvian sol", "S/", 2, listOf("sol", "peruvian")),
        CurrencyDef("ISK", "Icelandic króna", "kr", 0, listOf("krona", "icelandic")),
        CurrencyDef(
            "HRK", "Croatian kuna (legacy, superseded by the euro)", "kn", 2,
            listOf("kuna", "croatian"),
        ),
    )

    private val byCodeIndex: Map<String, CurrencyDef> =
        currencies.associateBy { it.code }

    /** Case-insensitive lookup by ISO code (e.g. `usd`, `GBP`). */
    fun byCode(code: String): CurrencyDef? = byCodeIndex[code.trim().uppercase()]

    /** All ISO codes, useful for quick membership tests. */
    val codes: Set<String> = byCodeIndex.keys

    /**
     * Free-text search over code, name, symbol and aliases. A query like
     * `pound` matches GBP by name, `GBP` by code and `£` by symbol.
     */
    fun search(query: String): List<CurrencyDef> {
        val q = query.trim()
        if (q.isEmpty()) return currencies
        return currencies.filter { c ->
            c.code.contains(q, ignoreCase = true) ||
                c.name.contains(q, ignoreCase = true) ||
                c.symbol.contains(q, ignoreCase = true) ||
                c.aliases.any { it.contains(q, ignoreCase = true) }
        }
    }

    /** Convenience for tests and diagnostics. */
    fun count(): Int = currencies.size
}
