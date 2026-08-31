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
import com.sysadmindoc.billminder.domain.ResolvedBillCycle
import com.sysadmindoc.billminder.security.PrivacyText
import com.sysadmindoc.billminder.security.SecurityPrefs

class BillMinderWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = BillDatabase.getDatabase(context)
        val repo = BillRepository(db)
        val bills = repo.getAllBillsList()
        val displayCurrency = CurrencyPrefs.getDisplayCurrency(context)
        val manualRates = CurrencyPrefs.getManualRates(context)
        val privacyEnabled = SecurityPrefs.maskExternalContent(context)

        val payments = repo.getAllPaymentsForExport()
        val snapshot = upcomingWidgetSnapshotFrom(
            BillCycles.currentCycles(bills, payments),
            displayCurrency,
            manualRates
        )

        provideContent {
            WidgetContent(snapshot.items, snapshot.totalDue, displayCurrency, privacyEnabled)
        }
    }
}

data class WidgetBillItem(
    val billId: Long,
    val cycleKey: String,
    val name: String,
    val amount: Double,
    val daysUntilDue: Int,
    val isPaid: Boolean,
    val isOverdue: Boolean,
    val isAutoPay: Boolean,
    val currency: String
)

internal data class UpcomingWidgetSnapshot(
    val items: List<WidgetBillItem>,
    val totalDue: Double
)

internal fun upcomingWidgetSnapshotFrom(
    cycles: List<ResolvedBillCycle>,
    displayCurrency: String,
    manualRates: Map<String, Double> = emptyMap()
): UpcomingWidgetSnapshot {
    val items = cycles.map { resolved ->
        val bill = resolved.bill
        val cycle = resolved.cycle
        WidgetBillItem(
            billId = bill.id,
            cycleKey = cycle.cycleKey,
            name = bill.name,
            amount = bill.amount,
            daysUntilDue = cycle.daysUntilDue,
            isPaid = cycle.isPaid,
            isOverdue = cycle.isOverdue,
            isAutoPay = bill.isAutoPay,
            currency = bill.currency
        )
    }.filterNot(WidgetBillItem::isPaid)
        .sortedWith(compareBy({ it.daysUntilDue }, { it.billId }))
        .take(3)
    return UpcomingWidgetSnapshot(
        items = items,
        totalDue = items.sumOf {
            CurrencyConverter.convert(it.amount, it.currency, displayCurrency, manualRates)
        }
    )
}

@Composable
private fun WidgetContent(
    bills: List<WidgetBillItem>,
    totalDue: Double,
    displayCurrency: String,
    privacyEnabled: Boolean
) {
    val bgColor = ColorProvider(Color(0xFF11111B))
    val textColor = ColorProvider(Color(0xFFCDD6F4))
    val subtextColor = ColorProvider(Color(0xFFA6ADC8))
    val accentColor = ColorProvider(Color(0xFF89B4FA))
    val redColor = ColorProvider(Color(0xFFF38BA8))
    val yellowColor = ColorProvider(Color(0xFFF9E2AF))
    val greenColor = ColorProvider(Color(0xFFA6E3A1))

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
                "${PrivacyText.externalAmount(CurrencyFormatter.format(totalDue, displayCurrency), privacyEnabled)} due",
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
                        PrivacyText.externalBillName(bill.name, privacyEnabled),
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
                        PrivacyText.externalAmount(
                            CurrencyFormatter.format(bill.amount, bill.currency),
                            privacyEnabled
                        ),
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
