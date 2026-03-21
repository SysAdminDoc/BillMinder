package com.sysadmindoc.billminder.widget

import android.content.Context
import androidx.compose.runtime.Composable
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

class BillMinderWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = BillDatabase.getDatabase(context)
        val repo = BillRepository(db.billDao())
        val bills = repo.getAllBillsList()

        val upcoming = bills.map { bill ->
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val now = System.currentTimeMillis()
            val daysUntil = ((nextDue - now) / (1000 * 60 * 60 * 24)).toInt()
            val payment = repo.getPaymentForBillDue(bill.id, nextDue)
            WidgetBillItem(
                name = bill.name,
                amount = bill.amount,
                daysUntilDue = daysUntil,
                isPaid = payment != null,
                isOverdue = payment == null && daysUntil < 0,
                isAutoPay = bill.isAutoPay
            )
        }.filter { !it.isPaid }
            .sortedBy { it.daysUntilDue }
            .take(5)

        val totalDue = upcoming.sumOf { it.amount }

        provideContent {
            WidgetContent(upcoming, totalDue)
        }
    }
}

data class WidgetBillItem(
    val name: String,
    val amount: Double,
    val daysUntilDue: Int,
    val isPaid: Boolean,
    val isOverdue: Boolean,
    val isAutoPay: Boolean
)

@Composable
private fun WidgetContent(bills: List<WidgetBillItem>, totalDue: Double) {
    val bgColor = ColorProvider(android.graphics.Color.parseColor("#11111B"))
    val textColor = ColorProvider(android.graphics.Color.parseColor("#CDD6F4"))
    val subtextColor = ColorProvider(android.graphics.Color.parseColor("#A6ADC8"))
    val accentColor = ColorProvider(android.graphics.Color.parseColor("#89B4FA"))
    val redColor = ColorProvider(android.graphics.Color.parseColor("#F38BA8"))
    val yellowColor = ColorProvider(android.graphics.Color.parseColor("#F9E2AF"))
    val greenColor = ColorProvider(android.graphics.Color.parseColor("#A6E3A1"))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                "BillMinder",
                style = TextStyle(color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "$${"%.0f".format(totalDue)} due",
                style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        if (bills.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "All bills paid!",
                    style = TextStyle(color = greenColor, fontSize = 14.sp)
                )
            }
        } else {
            bills.forEach { bill ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        bill.name,
                        style = TextStyle(color = textColor, fontSize = 13.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    val dueLabel = when {
                        bill.isOverdue -> "${-bill.daysUntilDue}d late"
                        bill.daysUntilDue == 0 -> "Today"
                        bill.daysUntilDue == 1 -> "Tmrw"
                        else -> "${bill.daysUntilDue}d"
                    }
                    val dueColor = when {
                        bill.isOverdue -> redColor
                        bill.daysUntilDue <= 3 -> yellowColor
                        else -> subtextColor
                    }
                    Text(
                        dueLabel,
                        style = TextStyle(color = dueColor, fontSize = 12.sp)
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        "$${"%.0f".format(bill.amount)}",
                        style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

class BillMinderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BillMinderWidget()
}
