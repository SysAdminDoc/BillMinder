package com.sysadmindoc.billminder.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.Payment
import com.sysadmindoc.billminder.notification.ReminderScheduler
import com.sysadmindoc.billminder.ui.components.getCategoryIcon
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    viewModel: BillViewModel,
    billId: Long,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    var bill by remember { mutableStateOf<Bill?>(null) }
    var lifetimeSpending by remember { mutableDoubleStateOf(0.0) }
    var onTimeStreak by remember { mutableIntStateOf(0) }
    val payments by viewModel.getPaymentsForBill(billId).collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(billId) {
        bill = viewModel.getBillById(billId)
        lifetimeSpending = viewModel.getLifetimeSpending(billId)
        onTimeStreak = viewModel.getOnTimeStreak(billId)
    }

    LaunchedEffect(payments.size) {
        lifetimeSpending = viewModel.getLifetimeSpending(billId)
        onTimeStreak = viewModel.getOnTimeStreak(billId)
    }

    val currentBill = bill ?: return

    Scaffold(
        containerColor = CatCrust,
        topBar = {
            TopAppBar(
                title = { Text(currentBill.name, color = CatText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CatCrust),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = CatText)
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(billId) }) {
                        Icon(Icons.Filled.Edit, "Edit", tint = CatBlue)
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "More", tint = CatSubtext0)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            containerColor = CatSurface0
                        ) {
                            DropdownMenuItem(
                                text = { Text("Duplicate Bill", color = CatText) },
                                onClick = {
                                    viewModel.duplicateBill(currentBill)
                                    showMoreMenu = false
                                    onNavigateBack()
                                },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = CatBlue) }
                            )
                            if (currentBill.paymentUrl.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Pay Now", color = CatText) },
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentBill.paymentUrl))
                                        context.startActivity(intent)
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null, tint = CatGreen) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Share", color = CatText) },
                                onClick = {
                                    val nextDue = ReminderScheduler.getNextDueDate(currentBill)
                                    val shareText = "${currentBill.name}\n" +
                                        "Amount: $${"%,.2f".format(currentBill.amount)}\n" +
                                        "Due: ${dateFormat.format(Date(nextDue))}\n" +
                                        "Recurrence: ${currentBill.recurrence.label}\n" +
                                        if (currentBill.isAutoPay) "Auto-Pay: Yes" else ""
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText.trim())
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share bill"))
                                    showMoreMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Share, null, tint = CatSubtext0) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = CatRed) },
                                onClick = {
                                    viewModel.deleteBill(currentBill)
                                    showMoreMenu = false
                                    onNavigateBack()
                                },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = CatRed) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bill info card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CatBase)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(currentBill.color).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    getCategoryIcon(currentBill.category), null,
                                    tint = Color(currentBill.color),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$${"%,.2f".format(currentBill.amount)}",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CatText
                                )
                                Text(
                                    currentBill.category.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CatSubtext0
                                )
                            }
                            // On-time streak badge
                            if (onTimeStreak > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CatGreen.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.LocalFireDepartment, null, tint = CatPeach, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "$onTimeStreak",
                                            color = CatGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = CatSurface1)
                        Spacer(Modifier.height(16.dp))

                        val nextDue = ReminderScheduler.getNextDueDate(currentBill)

                        DetailRow("Due Date", dateFormat.format(Date(nextDue)))
                        DetailRow("Recurrence", currentBill.recurrence.label)
                        DetailRow("Reminder", currentBill.reminderTiming.label)
                        currentBill.secondReminderTiming?.let {
                            DetailRow("2nd Reminder", it.label)
                        }
                        DetailRow("Auto-Pay", if (currentBill.isAutoPay) "Yes" else "No")
                        DetailRow("Reminders", if (currentBill.isEnabled) "Enabled" else "Disabled")
                        if (onTimeStreak > 0) {
                            DetailRow("On-Time Streak", "$onTimeStreak consecutive")
                        }

                        if (currentBill.tags.isNotBlank()) {
                            DetailRow("Tags", currentBill.tags)
                        }

                        if (currentBill.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Notes", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                            Spacer(Modifier.height(4.dp))
                            Text(currentBill.notes, color = CatText, style = MaterialTheme.typography.bodyMedium)
                        }

                        // Pay Now button
                        if (currentBill.paymentUrl.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentBill.paymentUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CatGreen, contentColor = CatCrust),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pay Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Lifetime spending card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CatSurface0)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Lifetime Spending", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                            Text(
                                "$${"%,.2f".format(lifetimeSpending)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = CatMauve
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${payments.size} payments", style = MaterialTheme.typography.bodyMedium, color = CatSubtext0)
                            if (payments.isNotEmpty()) {
                                val avgPayment = lifetimeSpending / payments.size
                                Text("Avg: $${"%,.2f".format(avgPayment)}", style = MaterialTheme.typography.labelMedium, color = CatOverlay0)
                            }
                        }
                    }
                }
            }

            // Payment history
            item {
                Text("Payment History", style = MaterialTheme.typography.titleMedium, color = CatText,
                    modifier = Modifier.padding(top = 8.dp))
            }

            if (payments.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CatSurface0)) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No payments recorded yet", color = CatSubtext0)
                        }
                    }
                }
            }

            items(payments, key = { it.id }) { payment ->
                PaymentRow(payment, dateTimeFormat)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CatSubtext0, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = CatText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PaymentRow(payment: Payment, dateFormat: SimpleDateFormat) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CatSurface0)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = CatGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dateFormat.format(Date(payment.paidAt)), style = MaterialTheme.typography.bodyMedium, color = CatText)
                if (payment.confirmationNumber.isNotBlank()) {
                    Text("Conf: ${payment.confirmationNumber}", style = MaterialTheme.typography.labelMedium, color = CatSubtext0)
                }
                if (payment.note.isNotBlank()) {
                    Text(payment.note, style = MaterialTheme.typography.labelMedium, color = CatSubtext0)
                }
            }
            Text("$${"%,.2f".format(payment.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CatGreen)
        }
    }
}
