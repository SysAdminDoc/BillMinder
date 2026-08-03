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
import com.sysadmindoc.billminder.notification.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonthTotalWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = BillDatabase.getDatabase(context)
        val repo = BillRepository(db.billDao())
        val bills = repo.getAllBillsList()

        var totalDue = 0.0
        var totalPaid = 0.0
        var paidCount = 0
        var totalCount = 0

        bills.forEach { bill ->
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val payment = repo.getPaymentForBillDue(bill.id, nextDue)
            totalDue += bill.amount
            totalCount++
            if (payment != null) {
                totalPaid += payment.amount
                paidCount++
            }
        }

        val remaining = totalDue - totalPaid
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())

        provideContent {
            MonthTotalContent(monthName, totalDue, totalPaid, remaining, paidCount, totalCount)
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
    totalCount: Int
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
                formatAmount(remaining),
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
                formatAmount(totalPaid),
                style = TextStyle(color = greenColor, fontSize = 12.sp)
            )
            Text(
                " of ${formatAmount(totalDue)}",
                style = TextStyle(color = subtextColor, fontSize = 12.sp)
            )
        }
    }
}

private fun formatAmount(amount: Double): String =
    String.format(Locale.getDefault(), "$%.0f", amount)

class MonthTotalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthTotalWidget()
}
