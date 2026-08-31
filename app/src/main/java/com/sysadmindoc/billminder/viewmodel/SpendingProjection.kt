package com.sysadmindoc.billminder.viewmodel

object SpendingProjection {
    fun annualized(monthlyTotals: List<Double>): Double {
        val spendingMonths = monthlyTotals.filter { it.isFinite() && it > 0.0 }
        return if (spendingMonths.isEmpty()) 0.0 else spendingMonths.average() * 12.0
    }
}
