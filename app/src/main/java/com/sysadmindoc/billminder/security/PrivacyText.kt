package com.sysadmindoc.billminder.security

object PrivacyText {
    const val HIDDEN_AMOUNT = "••••"
    const val HIDDEN_EXTERNAL_AMOUNT = "Amount hidden"
    const val HIDDEN_BILL_NAME = "Bill due"

    fun inAppAmount(formattedAmount: String, hidden: Boolean): String =
        if (hidden) HIDDEN_AMOUNT else formattedAmount

    fun externalAmount(formattedAmount: String, hidden: Boolean): String =
        if (hidden) HIDDEN_EXTERNAL_AMOUNT else formattedAmount

    fun externalBillName(billName: String, hidden: Boolean): String =
        if (hidden) HIDDEN_BILL_NAME else billName
}
