package com.sysadmindoc.billminder.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sysadmindoc.billminder.MainActivity
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.notification.ReminderScheduler

class LockScreenWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = BillRepository(BillDatabase.getDatabase(context).billDao())
        var next: LockScreenBill? = null
        for (bill in repository.getAllBillsList()) {
            val dueDate = ReminderScheduler.getNextDueDate(bill)
            if (repository.getPaymentForBillDue(bill.id, dueDate) == null &&
                (next == null || dueDate < next!!.dueDate)
            ) {
                next = LockScreenBill(bill.name, dueDate, true)
            }
        }

        provideContent {
            LockScreenWidgetContent(next)
        }
    }
}

private data class LockScreenBill(
    val name: String,
    val dueDate: Long,
    val isUnpaid: Boolean
)

@Composable
private fun LockScreenWidgetContent(next: LockScreenBill?) {
    val textColor = ColorProvider(Color(0xFFCDD6F4))
    val subtextColor = ColorProvider(Color(0xFFA6ADC8))
    val accentColor = ColorProvider(Color(0xFF89B4FA))
    val greenColor = ColorProvider(Color(0xFFA6E3A1))
    val daysUntil = next?.let {
        ((it.dueDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt()
    }
    val dueLabel = when {
        daysUntil == null -> "No unpaid bills"
        daysUntil < 0 -> "${-daysUntil}d overdue"
        daysUntil == 0 -> "Due today"
        daysUntil == 1 -> "Due tomorrow"
        else -> "Due in ${daysUntil}d"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF11111B)))
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "BillMinder",
                style = TextStyle(color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                "Tap to open",
                style = TextStyle(color = subtextColor, fontSize = 10.sp)
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            next?.name ?: "All bills paid",
            style = TextStyle(
                color = if (next == null) greenColor else textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Text(
            dueLabel,
            style = TextStyle(color = if (daysUntil != null && daysUntil < 0) accentColor else subtextColor, fontSize = 11.sp)
        )
    }
}

class LockScreenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockScreenWidget()
}
