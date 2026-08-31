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
import com.sysadmindoc.billminder.domain.BillCycles
import com.sysadmindoc.billminder.domain.ResolvedBillCycle
import com.sysadmindoc.billminder.security.PrivacyText
import com.sysadmindoc.billminder.security.SecurityPrefs

class LockScreenWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = BillRepository(BillDatabase.getDatabase(context))
        val payments = repository.getAllPaymentsForExport()
        val privacyEnabled = SecurityPrefs.maskExternalContent(context)
        val bills = repository.getAllBillsList()
        val next = lockScreenSnapshotFrom(BillCycles.currentCycles(bills, payments))

        provideContent {
            LockScreenWidgetContent(next, privacyEnabled)
        }
    }
}

internal data class LockScreenBill(
    val billId: Long,
    val cycleKey: String,
    val name: String,
    val dueDate: Long,
    val daysUntilDue: Int
)

internal fun lockScreenSnapshotFrom(cycles: List<ResolvedBillCycle>): LockScreenBill? =
    cycles.filterNot { it.cycle.isPaid }
        .minWithOrNull(compareBy({ it.cycle.dueAt }, { it.bill.id }))
        ?.let { resolved ->
            LockScreenBill(
                billId = resolved.bill.id,
                cycleKey = resolved.cycle.cycleKey,
                name = resolved.bill.name,
                dueDate = resolved.cycle.dueAt,
                daysUntilDue = resolved.cycle.daysUntilDue
            )
        }

@Composable
private fun LockScreenWidgetContent(next: LockScreenBill?, privacyEnabled: Boolean) {
    val textColor = ColorProvider(Color(0xFFCDD6F4))
    val subtextColor = ColorProvider(Color(0xFFA6ADC8))
    val accentColor = ColorProvider(Color(0xFF89B4FA))
    val greenColor = ColorProvider(Color(0xFFA6E3A1))
    val daysUntil = next?.daysUntilDue
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
            next?.let { PrivacyText.externalBillName(it.name, privacyEnabled) } ?: "All bills paid",
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
