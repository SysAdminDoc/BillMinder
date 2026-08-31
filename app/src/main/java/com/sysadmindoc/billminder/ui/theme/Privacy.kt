package com.sysadmindoc.billminder.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.security.PrivacyText

val LocalHideAmounts = staticCompositionLocalOf { false }

@Composable
fun privateAmount(amount: Double, currency: String): String = PrivacyText.inAppAmount(
    formattedAmount = CurrencyFormatter.format(amount, currency),
    hidden = LocalHideAmounts.current
)
