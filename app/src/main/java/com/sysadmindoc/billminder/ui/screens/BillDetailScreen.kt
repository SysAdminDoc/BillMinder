package com.sysadmindoc.billminder.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.BillPayee
import com.sysadmindoc.billminder.data.CalendarSync
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.data.HolidayCalendar
import com.sysadmindoc.billminder.data.Payment
import com.sysadmindoc.billminder.notification.ReminderScheduler
import com.sysadmindoc.billminder.security.EncryptedAttachmentStore
import com.sysadmindoc.billminder.ui.components.GroupDivider
import com.sysadmindoc.billminder.ui.components.GroupedSurface
import com.sysadmindoc.billminder.ui.components.IconWell
import com.sysadmindoc.billminder.ui.components.SectionHeading
import com.sysadmindoc.billminder.ui.components.getCategoryIcon
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    viewModel: BillViewModel,
    billId: Long,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bill by remember { mutableStateOf<Bill?>(null) }
    var lifetimeSpending by remember { mutableDoubleStateOf(0.0) }
    var onTimeStreak by remember { mutableIntStateOf(0) }
    var payees by remember { mutableStateOf<List<BillPayee>>(emptyList()) }
    val payments by viewModel.getPaymentsForBill(billId).collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(billId) {
        bill = viewModel.getBillById(billId)
        payees = viewModel.getPayeesForBill(billId)
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
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Bill details", color = CatText, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CatCrust),
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CatText)
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
                                text = { Text("Add to Calendar", color = CatText) },
                                onClick = {
                                    val added = CalendarSync.openInsert(
                                        context,
                                        currentBill,
                                        ReminderScheduler.getNextDueDate(currentBill)
                                    )
                                    if (!added) {
                                        Toast.makeText(context, "No calendar app is available", Toast.LENGTH_LONG).show()
                                    }
                                    showMoreMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Event, null, tint = CatBlue) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share", color = CatText) },
                                onClick = {
                                    val nextDue = ReminderScheduler.getNextDueDate(currentBill)
                                    val shareText = "${currentBill.name}\n" +
                                        "Amount: ${CurrencyFormatter.format(currentBill.amount, currentBill.currency)}\n" +
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
            item {
                val nextDue = ReminderScheduler.getNextDueDate(currentBill)
                val isPaid = payments.any { it.dueDate == nextDue }
                val daysUntil = ((nextDue - System.currentTimeMillis()) / 86_400_000L).toInt()
                val dueStatus = when {
                    isPaid -> "Paid for this cycle"
                    daysUntil < 0 -> "${-daysUntil} days overdue"
                    daysUntil == 0 -> "Due today"
                    daysUntil == 1 -> "Due tomorrow"
                    else -> "Due in $daysUntil days"
                }
                val statusColor = when {
                    isPaid -> CatGreen
                    daysUntil < 0 -> CatRed
                    daysUntil <= 3 -> CatYellow
                    else -> CatBlue
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconWell(
                            icon = getCategoryIcon(currentBill.category),
                            contentDescription = null,
                            tint = storedBillColor(currentBill.color),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentBill.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = CatText
                            )
                            Text(currentBill.category.label, color = CatSubtext0)
                        }
                        if (onTimeStreak > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CatGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, CatGreen.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.LocalFireDepartment, null, tint = CatPeach, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("$onTimeStreak", color = CatGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        CurrencyFormatter.format(currentBill.amount, currentBill.currency),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.9).sp,
                        color = CatText
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dueStatus, color = statusColor, fontWeight = FontWeight.SemiBold)
                        Text("  ·  ${dateFormat.format(Date(nextDue))}", color = CatSubtext0)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.markAsPaid(currentBill) },
                        enabled = !isPaid,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CatGreen,
                            contentColor = CatCrust,
                            disabledContainerColor = CatSurface1,
                            disabledContentColor = CatSubtext0
                        )
                    ) {
                        Icon(if (isPaid) Icons.Filled.CheckCircle else Icons.Filled.Done, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPaid) "Payment recorded" else "Mark paid", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailAction(
                        icon = Icons.Filled.OpenInBrowser,
                        label = "Pay online",
                        tint = CatGreen,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (currentBill.paymentUrl.isBlank()) {
                            Toast.makeText(context, "No payment link saved", Toast.LENGTH_SHORT).show()
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentBill.paymentUrl)))
                        }
                    }
                    DetailAction(
                        icon = Icons.Filled.Event,
                        label = "Calendar",
                        tint = CatBlue,
                        modifier = Modifier.weight(1f)
                    ) {
                        val added = CalendarSync.openInsert(context, currentBill, ReminderScheduler.getNextDueDate(currentBill))
                        if (!added) Toast.makeText(context, "No calendar app is available", Toast.LENGTH_LONG).show()
                    }
                    DetailAction(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        tint = CatMauve,
                        modifier = Modifier.weight(1f)
                    ) {
                        val nextDue = ReminderScheduler.getNextDueDate(currentBill)
                        val shareText = "${currentBill.name}\n" +
                            "Amount: ${CurrencyFormatter.format(currentBill.amount, currentBill.currency)}\n" +
                            "Due: ${dateFormat.format(Date(nextDue))}\n" +
                            "Recurrence: ${currentBill.recurrence.label}\n" +
                            if (currentBill.isAutoPay) "Auto-Pay: Yes" else ""
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText.trim())
                        }, "Share bill"))
                    }
                }
            }

            item {
                val nextDue = ReminderScheduler.getNextDueDate(currentBill)
                val holidayNote = HolidayCalendar.getHolidayNote(nextDue)
                SectionHeading("Details")
                Spacer(Modifier.height(8.dp))
                GroupedSurface {
                    DetailRow("Due date", dateFormat.format(Date(nextDue)), Icons.Filled.CalendarMonth)
                    holidayNote?.let {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = CatYellow, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = CatYellow)
                        }
                    }
                    GroupDivider()
                    DetailRow("Frequency", currentBill.recurrence.label, Icons.Filled.Repeat)
                    GroupDivider()
                    DetailRow("Category", currentBill.category.label, Icons.Filled.Category)
                    GroupDivider()
                    DetailRow("Reminder", currentBill.reminderTiming.label, Icons.Filled.Notifications)
                    currentBill.secondReminderTiming?.let {
                        GroupDivider()
                        DetailRow("Second reminder", it.label, Icons.Filled.NotificationAdd)
                    }
                    if (currentBill.isVariableAmount && currentBill.amountMin != null && currentBill.amountMax != null) {
                        GroupDivider()
                        DetailRow(
                            "Amount range",
                            "${CurrencyFormatter.format(currentBill.amountMin, currentBill.currency)} to ${CurrencyFormatter.format(currentBill.amountMax, currentBill.currency)}",
                            Icons.Filled.Tune
                        )
                    }
                    GroupDivider()
                    DetailRow("Auto-pay", if (currentBill.isAutoPay) "On" else "Off", Icons.Filled.CreditCard)
                    GroupDivider()
                    DetailRow("Reminders", if (currentBill.isEnabled) "On" else "Off", Icons.Filled.NotificationsActive)
                    if (payees.isNotEmpty()) {
                        GroupDivider()
                        DetailRow("Split", payees.joinToString { "${it.name} ${"%.0f".format(it.sharePercent)}%" }, Icons.Filled.Groups)
                    }
                    if (currentBill.tags.isNotBlank()) {
                        GroupDivider()
                        DetailRow("Tags", currentBill.tags, Icons.AutoMirrored.Filled.Label)
                    }
                    if (currentBill.paymentUrl.isNotBlank()) {
                        GroupDivider()
                        DetailRow("Website", currentBill.paymentUrl, Icons.Filled.Language)
                    }
                    if (currentBill.notes.isNotBlank()) {
                        GroupDivider()
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text("Notes", color = CatSubtext0, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(currentBill.notes, color = CatText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                SectionHeading("Spending")
                Spacer(Modifier.height(8.dp))
                GroupedSurface(contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("Lifetime total", style = MaterialTheme.typography.bodySmall, color = CatSubtext0)
                            Text(
                                CurrencyFormatter.format(lifetimeSpending, currentBill.currency),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = CatMauve
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${payments.size} payments", color = CatText, fontWeight = FontWeight.Medium)
                            if (payments.isNotEmpty()) {
                                Text(
                                    "Average ${CurrencyFormatter.format(lifetimeSpending / payments.size, currentBill.currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CatSubtext0
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeading("Payment history")
            }

            if (payments.isEmpty()) {
                item {
                    GroupedSurface {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No payments recorded yet", color = CatSubtext0)
                        }
                    }
                }
            }

            items(payments, key = { it.id }) { payment ->
                PaymentRow(
                    payment = payment,
                    dateFormat = dateTimeFormat,
                    onOpenAttachment = {
                        scope.launch {
                            val file = EncryptedAttachmentStore.decryptToCache(context, payment.attachmentFile)
                            if (file != null) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.files",
                                    file
                                )
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, payment.attachmentMime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            }
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(68.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = CatSurfaceRaised,
        border = BorderStroke(1.dp, CatDivider)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = CatText)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = CatBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = CatSubtext0, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = CatText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun PaymentRow(
    payment: Payment,
    dateFormat: SimpleDateFormat,
    onOpenAttachment: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CatSurfaceRaised,
        border = BorderStroke(1.dp, CatDivider)
    ) {
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
                if (payment.attachmentName.isNotBlank()) {
                    Text(
                        "Receipt: ${payment.attachmentName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CatBlue,
                        maxLines = 1
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(CurrencyFormatter.format(payment.amount, payment.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CatGreen)
                if (payment.attachmentName.isNotBlank()) {
                    TextButton(onClick = onOpenAttachment, contentPadding = PaddingValues(0.dp)) {
                        Text("Open", color = CatBlue, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
