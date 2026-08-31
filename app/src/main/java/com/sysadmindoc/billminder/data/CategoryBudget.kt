package com.sysadmindoc.billminder.data

import android.annotation.SuppressLint
import android.content.Context

data class CategoryBudget(
    val amount: Double,
    val currency: String
)

data class BudgetProgress(
    val spent: Double,
    val limit: Double,
    val ratio: Float,
    val remaining: Double
)

object BudgetMath {
    fun progress(spent: Double, limit: Double): BudgetProgress? {
        if (!spent.isFinite() || !limit.isFinite() || limit <= 0.0) return null
        return BudgetProgress(
            spent = spent.coerceAtLeast(0.0),
            limit = limit,
            ratio = (spent / limit).coerceIn(0.0, 1.0).toFloat(),
            remaining = (limit - spent).coerceAtLeast(0.0)
        )
    }
}

object BudgetPrefs {
    private const val PREFS_NAME = "billminder_category_budgets"
    private const val KEY_PREFIX = "budget_"

    fun getAll(context: Context): Map<BillCategory, CategoryBudget> =
        BillCategory.entries.mapNotNull { category ->
            val raw = prefs(context).getString(KEY_PREFIX + category.name, null) ?: return@mapNotNull null
            val parts = raw.split('|', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val amount = parts[0].toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0.0) return@mapNotNull null
            category to CategoryBudget(amount, CurrencyCatalog.find(parts[1]).code)
        }.toMap()

    fun setBudget(context: Context, category: BillCategory, amount: Double?, currency: String) {
        val key = KEY_PREFIX + category.name
        val editor = prefs(context).edit()
        if (amount == null || !amount.isFinite() || amount <= 0.0) {
            editor.remove(key)
        } else {
            editor.putString(key, "$amount|${CurrencyCatalog.find(currency).code}")
        }
        editor.apply()
    }

    @SuppressLint("ApplySharedPref")
    internal fun restoreFromBackup(
        context: Context,
        budgets: Map<BillCategory, CategoryBudget>
    ): Boolean {
        val editor = prefs(context).edit().clear()
        budgets.toSortedMap(compareBy(BillCategory::name)).forEach { (category, budget) ->
            if (budget.amount.isFinite() && budget.amount > 0.0) {
                editor.putString(
                    KEY_PREFIX + category.name,
                    "${budget.amount}|${CurrencyCatalog.find(budget.currency).code}"
                )
            }
        }
        return editor.commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
