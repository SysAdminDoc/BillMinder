package com.sysadmindoc.billminder.ui.screens

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
    var bill by remember { mutableStateOf<Bill?>(null) }
    val payments by viewModel.getPaymentsForBill(billId).collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    LaunchedEffect(billId) {
        bill = viewModel.getBillById(billId)
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
                                    getCategoryIcon(currentBill.category),
                                    null,
                                    tint = Color(currentBill.color),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
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

                        if (currentBill.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Notes", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                            Spacer(Modifier.height(4.dp))
                            Text(currentBill.notes, color = CatText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Payment history
            item {
                Text(
                    "Payment History",
                    style = MaterialTheme.typography.titleMedium,
                    color = CatText,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (payments.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CatSurface0)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CatSubtext0, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = CatText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PaymentRow(payment: Payment, dateFormat: SimpleDateFormat) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CatSurface0)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                null,
                tint = CatGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dateFormat.format(Date(payment.paidAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CatText
                )
                if (payment.note.isNotBlank()) {
                    Text(
                        payment.note,
                        style = MaterialTheme.typography.labelMedium,
                        color = CatSubtext0
                    )
                }
            }
            Text(
                "$${"%,.2f".format(payment.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CatGreen
            )
        }
    }
}
