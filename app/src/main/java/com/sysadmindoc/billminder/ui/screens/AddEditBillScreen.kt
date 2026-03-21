package com.sysadmindoc.billminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.data.*
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillScreen(
    viewModel: BillViewModel,
    billId: Long?,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("1") }
    var dueMonth by remember { mutableStateOf<Int?>(null) }
    var dueYear by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf(BillCategory.OTHER) }
    var recurrence by remember { mutableStateOf(Recurrence.MONTHLY) }
    var isAutoPay by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var reminderTiming by remember { mutableStateOf(ReminderTiming.ONE_DAY) }
    var secondReminder by remember { mutableStateOf<ReminderTiming?>(null) }
    var isEnabled by remember { mutableStateOf(true) }
    var selectedColor by remember { mutableLongStateOf(0xFF89B4FA) }
    var isLoaded by remember { mutableStateOf(billId == null) }

    val isEditing = billId != null && billId != 0L

    LaunchedEffect(billId) {
        if (billId != null && billId != 0L) {
            val bill = viewModel.getBillById(billId) ?: return@LaunchedEffect
            name = bill.name
            amount = bill.amount.toBigDecimal().stripTrailingZeros().toPlainString()
            dueDay = bill.dueDay.toString()
            dueMonth = bill.dueMonth
            dueYear = bill.dueYear
            category = bill.category
            recurrence = bill.recurrence
            isAutoPay = bill.isAutoPay
            notes = bill.notes
            reminderTiming = bill.reminderTiming
            secondReminder = bill.secondReminderTiming
            isEnabled = bill.isEnabled
            selectedColor = bill.color
            isLoaded = true
        }
    }

    var showCategoryMenu by remember { mutableStateOf(false) }
    var showRecurrenceMenu by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }
    var showSecondReminderMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CatCrust,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Bill" else "Add Bill", color = CatText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CatCrust),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = CatText)
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            scope.launch {
                                val bill = viewModel.getBillById(billId!!)
                                bill?.let { viewModel.deleteBill(it) }
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = CatRed)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bill Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = billFieldColors()
            )

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> if (v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = v },
                label = { Text("Amount ($)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text("$", color = CatSubtext0) },
                colors = billFieldColors()
            )

            // Due day
            OutlinedTextField(
                value = dueDay,
                onValueChange = { v ->
                    val num = v.filter { it.isDigit() }
                    if (num.length <= 2) dueDay = num
                },
                label = {
                    Text(
                        when (recurrence) {
                            Recurrence.WEEKLY, Recurrence.BIWEEKLY -> "Day of Week (1=Sun, 7=Sat)"
                            else -> "Day of Month (1-31)"
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = billFieldColors()
            )

            // Category dropdown
            Box {
                OutlinedTextField(
                    value = category.label,
                    onValueChange = {},
                    label = { Text("Category") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { showCategoryMenu = true },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = CatSubtext0) },
                    colors = billFieldColors(),
                    enabled = false
                )
                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false },
                    containerColor = CatSurface0
                ) {
                    BillCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.label, color = CatText) },
                            onClick = {
                                category = cat
                                selectedColor = CategoryColors[cat.ordinal % CategoryColors.size].value.toLong()
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }

            // Recurrence dropdown
            Box {
                OutlinedTextField(
                    value = recurrence.label,
                    onValueChange = {},
                    label = { Text("Recurrence") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { showRecurrenceMenu = true },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = CatSubtext0) },
                    colors = billFieldColors(),
                    enabled = false
                )
                DropdownMenu(
                    expanded = showRecurrenceMenu,
                    onDismissRequest = { showRecurrenceMenu = false },
                    containerColor = CatSurface0
                ) {
                    Recurrence.entries.forEach { rec ->
                        DropdownMenuItem(
                            text = { Text(rec.label, color = CatText) },
                            onClick = {
                                recurrence = rec
                                showRecurrenceMenu = false
                            }
                        )
                    }
                }
            }

            // Reminder timing
            Box {
                OutlinedTextField(
                    value = reminderTiming.label,
                    onValueChange = {},
                    label = { Text("Reminder") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { showReminderMenu = true },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = CatSubtext0) },
                    colors = billFieldColors(),
                    enabled = false
                )
                DropdownMenu(
                    expanded = showReminderMenu,
                    onDismissRequest = { showReminderMenu = false },
                    containerColor = CatSurface0
                ) {
                    ReminderTiming.entries.forEach { timing ->
                        DropdownMenuItem(
                            text = { Text(timing.label, color = CatText) },
                            onClick = {
                                reminderTiming = timing
                                showReminderMenu = false
                            }
                        )
                    }
                }
            }

            // Second reminder
            Box {
                OutlinedTextField(
                    value = secondReminder?.label ?: "None",
                    onValueChange = {},
                    label = { Text("Second Reminder (optional)") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { showSecondReminderMenu = true },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = CatSubtext0) },
                    colors = billFieldColors(),
                    enabled = false
                )
                DropdownMenu(
                    expanded = showSecondReminderMenu,
                    onDismissRequest = { showSecondReminderMenu = false },
                    containerColor = CatSurface0
                ) {
                    DropdownMenuItem(
                        text = { Text("None", color = CatText) },
                        onClick = {
                            secondReminder = null
                            showSecondReminderMenu = false
                        }
                    )
                    ReminderTiming.entries.forEach { timing ->
                        DropdownMenuItem(
                            text = { Text(timing.label, color = CatText) },
                            onClick = {
                                secondReminder = timing
                                showSecondReminderMenu = false
                            }
                        )
                    }
                }
            }

            // Auto-pay toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Auto-Pay", color = CatText, style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isAutoPay,
                    onCheckedChange = { isAutoPay = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CatCrust,
                        checkedTrackColor = CatGreen,
                        uncheckedThumbColor = CatOverlay0,
                        uncheckedTrackColor = CatSurface1
                    )
                )
            }

            // Enabled toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Reminders Enabled", color = CatText, style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CatCrust,
                        checkedTrackColor = CatBlue,
                        uncheckedThumbColor = CatOverlay0,
                        uncheckedTrackColor = CatSurface1
                    )
                )
            }

            // Color picker
            Text("Color", color = CatSubtext0, style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CategoryColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (color.value.toLong() == selectedColor)
                                    Modifier.border(3.dp, CatText, CircleShape)
                                else Modifier
                            )
                            .clickable { selectedColor = color.value.toLong() }
                    )
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                maxLines = 4,
                colors = billFieldColors()
            )

            // Save button
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                    val parsedDay = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    if (name.isBlank() || parsedAmount <= 0) return@Button

                    val bill = Bill(
                        id = if (isEditing) billId!! else 0,
                        name = name.trim(),
                        amount = parsedAmount,
                        dueDay = parsedDay,
                        dueMonth = dueMonth,
                        dueYear = dueYear,
                        category = category,
                        recurrence = recurrence,
                        isAutoPay = isAutoPay,
                        notes = notes.trim(),
                        reminderTiming = reminderTiming,
                        secondReminderTiming = secondReminder,
                        isEnabled = isEnabled,
                        color = selectedColor
                    )
                    viewModel.saveBill(bill)
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CatBlue,
                    contentColor = CatCrust
                ),
                shape = RoundedCornerShape(14.dp),
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text(
                    if (isEditing) "Save Changes" else "Add Bill",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun billFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CatText,
    unfocusedTextColor = CatText,
    disabledTextColor = CatText,
    focusedBorderColor = CatBlue,
    unfocusedBorderColor = CatSurface1,
    disabledBorderColor = CatSurface1,
    focusedLabelColor = CatBlue,
    unfocusedLabelColor = CatSubtext0,
    disabledLabelColor = CatSubtext0,
    cursorColor = CatBlue
)
