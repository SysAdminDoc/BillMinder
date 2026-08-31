package com.sysadmindoc.billminder.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.security.PrivacyText
import com.sysadmindoc.billminder.security.SecurityPrefs
import com.sysadmindoc.billminder.ui.theme.BillMinderTheme
import com.sysadmindoc.billminder.ui.theme.CatBlue
import com.sysadmindoc.billminder.ui.theme.CatCrust
import com.sysadmindoc.billminder.ui.theme.CatGreen
import com.sysadmindoc.billminder.ui.theme.CatRed
import com.sysadmindoc.billminder.ui.theme.CatSubtext0
import com.sysadmindoc.billminder.ui.theme.CatText

class ReminderAlarmActivity : FragmentActivity() {
    private var billId: Long = -1L
    private var amount: Double = 0.0
    private var currency: String = "USD"
    private var billName: String = "Bill"
    private var daysUntilDue: Int = 0
    private var isAutoPay: Boolean = false
    private var nextDueDate: Long = 0L
    private var cycleKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        billId = intent.getLongExtra("bill_id", -1L)
        if (billId == -1L) {
            finish()
            return
        }
        billName = intent.getStringExtra("bill_name") ?: "Bill"
        amount = intent.getDoubleExtra("amount", 0.0)
        currency = intent.getStringExtra("currency") ?: "USD"
        daysUntilDue = intent.getIntExtra("days_until_due", 0)
        isAutoPay = intent.getBooleanExtra("is_auto_pay", false)
        nextDueDate = intent.getLongExtra("next_due_date", 0L)
        cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty()
        val privacyEnabled = SecurityPrefs.maskExternalContent(this)

        setContent {
            BillMinderTheme {
                ReminderAlarmContent(
                    billName = PrivacyText.externalBillName(billName, privacyEnabled),
                    amountText = PrivacyText.externalAmount(
                        CurrencyFormatter.format(amount, currency),
                        privacyEnabled
                    ),
                    daysUntilDue = daysUntilDue,
                    isAutoPay = isAutoPay,
                    onPaid = { finishWithAction("MARK_PAID") },
                    onSnooze = { finishWithAction("SNOOZE") },
                    onDismiss = { finishWithAction("REMINDER_DISMISSED") }
                )
            }
        }
    }

    private fun finishWithAction(action: String) {
        val actionIntent = Intent(this, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("next_due_date", nextDueDate)
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
            if (action == "SNOOZE") putExtra("snooze_minutes", 60)
        }
        if (action == "MARK_PAID" || action == "REMINDER_DISMISSED") {
            cancelNotifications()
        }
        sendBroadcast(actionIntent)
        finish()
    }

    private fun cancelNotifications() {
        NotificationHelper.cancelAll(this, billId)
    }
}

@Composable
private fun ReminderAlarmContent(
    billName: String,
    amountText: String,
    daysUntilDue: Int,
    isAutoPay: Boolean,
    onPaid: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val dueText = when {
        daysUntilDue == 0 -> "Due today"
        daysUntilDue == 1 -> "Due tomorrow"
        daysUntilDue > 1 -> "Due in $daysUntilDue days"
        else -> "Overdue by ${-daysUntilDue} days"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = CatCrust) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = CatBlue, modifier = Modifier.padding(8.dp))
            Text("Bill Reminder", color = CatSubtext0, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                billName,
                color = CatText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                amountText,
                color = CatText,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(dueText + if (isAutoPay) "  •  Auto-Pay" else "", color = CatSubtext0)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onPaid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CatGreen, contentColor = CatCrust)
            ) {
                Text("Mark Paid", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                    Text("Snooze 1 hour", color = CatBlue)
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Dismiss", color = CatRed)
                }
            }
        }
    }
}
