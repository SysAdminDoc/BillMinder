package com.sysadmindoc.billminder.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.sysadmindoc.billminder.MainActivity
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.CurrencyConverter
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.data.CurrencyPrefs
import com.sysadmindoc.billminder.domain.BillCycles
import com.sysadmindoc.billminder.domain.CycleEngine
import com.sysadmindoc.billminder.security.PrivacyText
import com.sysadmindoc.billminder.security.SecurityPrefs
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class MonthTotalWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = BillDatabase.getDatabase(context)
        val repo = BillRepository(db)
        val bills = repo.getAllBillsList()
        val displayCurrency = CurrencyPrefs.getDisplayCurrency(context)
        val manualRates = CurrencyPrefs.getManualRates(context)
        val payments = repo.getAllPaymentsForExport()
        val privacyEnabled = SecurityPrefs.maskExternalContent(context)
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        val paidByBill = BillCycles.paidKeys(payments)

        var totalDue = 0.0
        var totalPaid = 0.0
        var paidCount = 0
        var totalCount = 0

        bills.forEach { bill ->
            val paidKeys = paidByBill[bill.id].orEmpty()
            CycleEngine.occurrencesInRange(bill, monthStart, monthEnd).forEach { date ->
                val key = CycleEngine.cycleKey(date)
                totalDue += CurrencyConverter.convert(bill.amount, bill.currency, displayCurrency, manualRates)
                totalCount++
                if (key in paidKeys) {
                    val payment = payments.firstOrNull { it.billId == bill.id && it.cycleKey == key }
                    totalPaid += CurrencyConverter.convert(
                        payment?.amount ?: bill.amount,
                        payment?.currency?.ifBlank { bill.currency } ?: bill.currency,
                        displayCurrency,
                        manualRates
                    )
                    paidCount++
                }
            }
        }

        val remaining = totalDue - totalPaid
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())

        provideContent {
            MonthTotalContent(
                monthName,
                totalDue,
                totalPaid,
                remaining,
                paidCount,
                totalCount,
                displayCurrency,
                privacyEnabled
            )
        }
    }
}

@Composable
private fun MonthTotalContent(
    monthName: String,
    totalDue: Double,
    totalPaid: Double,
    remaining: Double,
    paidCount: Int,
    totalCount: Int,
    displayCurrency: String,
    privacyEnabled: Boolean
) {
    val bgColor = ColorProvider(Color(0xFF11111B))
    val textColor = ColorProvider(Color(0xFFCDD6F4))
    val subtextColor = ColorProvider(Color(0xFFA6ADC8))
    val accentColor = ColorProvider(Color(0xFF89B4FA))
    val greenColor = ColorProvider(Color(0xFFA6E3A1))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            monthName,
            style = TextStyle(color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )

        Spacer(GlanceModifier.height(6.dp))

        if (remaining <= 0 && totalCount > 0) {
            Text(
                "All Paid!",
                style = TextStyle(color = greenColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
        } else {
            Text(
                PrivacyText.externalAmount(
                    CurrencyFormatter.format(remaining, displayCurrency),
                    privacyEnabled
                ),
                style = TextStyle(color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                "remaining",
                style = TextStyle(color = subtextColor, fontSize = 12.sp)
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                "$paidCount/$totalCount paid",
                style = TextStyle(color = subtextColor, fontSize = 12.sp)
            )
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                PrivacyText.externalAmount(
                    CurrencyFormatter.format(totalPaid, displayCurrency),
                    privacyEnabled
                ),
                style = TextStyle(color = greenColor, fontSize = 12.sp)
            )
            Text(
                " of ${PrivacyText.externalAmount(CurrencyFormatter.format(totalDue, displayCurrency), privacyEnabled)}",
                style = TextStyle(color = subtextColor, fontSize = 12.sp)
            )
        }
    }
}

class MonthTotalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthTotalWidget()
}
