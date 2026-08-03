package com.sysadmindoc.billminder.data

import java.util.Locale

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val fractionDigits: Int = 2
)

object CurrencyCatalog {
    val supported: List<CurrencyInfo> = listOf(
        CurrencyInfo("USD", "US Dollar", "$"),
        CurrencyInfo("EUR", "Euro", "€"),
        CurrencyInfo("GBP", "British Pound", "£"),
        CurrencyInfo("CAD", "Canadian Dollar", "CA$"),
        CurrencyInfo("AUD", "Australian Dollar", "A$"),
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ$"),
        CurrencyInfo("JPY", "Japanese Yen", "¥", 0),
        CurrencyInfo("CNY", "Chinese Yuan", "CN¥"),
        CurrencyInfo("INR", "Indian Rupee", "₹"),
        CurrencyInfo("CHF", "Swiss Franc", "CHF "),
        CurrencyInfo("BRL", "Brazilian Real", "R$"),
        CurrencyInfo("MXN", "Mexican Peso", "MX$"),
        CurrencyInfo("KRW", "South Korean Won", "₩", 0),
        CurrencyInfo("SEK", "Swedish Krona", "kr "),
        CurrencyInfo("NOK", "Norwegian Krone", "kr "),
        CurrencyInfo("PLN", "Polish Zloty", "zł "),
        CurrencyInfo("SGD", "Singapore Dollar", "S$"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK$"),
        CurrencyInfo("ZAR", "South African Rand", "R")
    )

    fun find(code: String?): CurrencyInfo =
        supported.firstOrNull { it.code == code?.uppercase(Locale.ROOT) } ?: supported.first()
}

object CurrencyFormatter {
    fun format(amount: Double, currency: String): String {
        val info = CurrencyCatalog.find(currency)
        val sign = if (amount < 0) "-" else ""
        val number = String.format(
            Locale.US,
            "%,.${info.fractionDigits}f",
            kotlin.math.abs(amount)
        )
        return "$sign${info.symbol}$number"
    }
}

/** Bundled offline snapshot. Each value is the number of units per 1 USD. */
object CurrencyConverter {
    val bundledUsdRates: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "EUR" to 0.92,
        "GBP" to 0.79,
        "CAD" to 1.36,
        "AUD" to 1.53,
        "NZD" to 1.67,
        "JPY" to 150.0,
        "CNY" to 7.24,
        "INR" to 83.0,
        "CHF" to 0.89,
        "BRL" to 5.0,
        "MXN" to 17.1,
        "KRW" to 1_350.0,
        "SEK" to 10.5,
        "NOK" to 10.7,
        "PLN" to 4.0,
        "SGD" to 1.35,
        "HKD" to 7.82,
        "ZAR" to 18.2
    )

    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        manualRates: Map<String, Double> = emptyMap()
    ): Double {
        val from = fromCurrency.uppercase(Locale.ROOT)
        val to = toCurrency.uppercase(Locale.ROOT)
        if (from == to) return amount
        val fromRate = effectiveRate(from, manualRates) ?: return amount
        val toRate = effectiveRate(to, manualRates) ?: return amount
        return amount / fromRate * toRate
    }

    private fun effectiveRate(currency: String, manualRates: Map<String, Double>): Double? {
        val manual = manualRates[currency]
        if (manual != null && manual.isFinite() && manual > 0.0) return manual
        return bundledUsdRates[currency]
    }
}
