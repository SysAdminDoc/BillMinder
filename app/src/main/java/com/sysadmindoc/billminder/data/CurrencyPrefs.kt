package com.sysadmindoc.billminder.data

import android.content.Context
import java.util.Locale

object CurrencyPrefs {
    private const val PREFS_NAME = "billminder_currency"
    private const val DISPLAY_CURRENCY = "display_currency"
    private const val MANUAL_RATES = "manual_rates"

    fun getDisplayCurrency(context: Context): String =
        CurrencyCatalog.find(prefs(context).getString(DISPLAY_CURRENCY, "USD") ?: "USD").code

    fun setDisplayCurrency(context: Context, currency: String) {
        val code = currency.uppercase(Locale.ROOT)
        if (CurrencyConverter.bundledUsdRates.containsKey(code)) {
            prefs(context).edit().putString(DISPLAY_CURRENCY, code).apply()
        }
    }

    fun getManualRates(context: Context): Map<String, Double> =
        (prefs(context).getString(MANUAL_RATES, "") ?: "")
            .split(';')
            .mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val code = parts[0].uppercase(Locale.ROOT)
                val value = parts[1].toDoubleOrNull()
                if (code == "USD" || code !in CurrencyConverter.bundledUsdRates || value == null || !value.isFinite() || value <= 0.0) {
                    null
                } else {
                    code to value
                }
            }
            .toMap()

    fun setManualRate(context: Context, currency: String, rate: Double?) {
        val code = currency.uppercase(Locale.ROOT)
        val rates = getManualRates(context).toMutableMap()
        if (rate == null || !rate.isFinite() || rate <= 0.0 || code == "USD") {
            rates.remove(code)
        } else if (code in CurrencyConverter.bundledUsdRates) {
            rates[code] = rate
        }
        val serialized = rates.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs(context).edit().putString(MANUAL_RATES, serialized).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
