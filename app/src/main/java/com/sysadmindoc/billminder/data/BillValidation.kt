package com.sysadmindoc.billminder.data

object BillValidation {
    fun variableAmountError(expected: Double, min: Double?, max: Double?): String? = when {
        expected <= 0.0 -> "Expected amount must be greater than zero"
        min == null || max == null || min <= 0.0 || max <= 0.0 ->
            "Enter both min and max amounts"
        min > max -> "Min amount cannot be higher than max"
        expected < min || expected > max -> "Expected amount must be within the range"
        else -> null
    }
}
