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
import androidx.compose.foundation.horizontalScroll

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
    var tags by remember { mutableStateOf("") }
    var paymentUrl by remember { mutableStateOf("") }
    var reminderTiming by remember { mutableStateOf(ReminderTiming.ONE_DAY) }
    var secondReminder by remember { mutableStateOf<ReminderTiming?>(null) }
    var isEnabled by remember { mutableStateOf(true) }
    var isVariableAmount by remember { mutableStateOf(false) }
    var amountMin by remember { mutableStateOf("") }
    var amountMax by remember { mutableStateOf("") }
    var amountRangeError by remember { mutableStateOf<String?>(null) }
    var isSplitBill by remember { mutableStateOf(false) }
    var splitError by remember { mutableStateOf<String?>(null) }
    val payees = remember { mutableStateListOf<PayeeDraft>() }
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
            tags = bill.tags
            paymentUrl = bill.paymentUrl
            reminderTiming = bill.reminderTiming
            secondReminder = bill.secondReminderTiming
            isEnabled = bill.isEnabled
            isVariableAmount = bill.isVariableAmount
            amountMin = bill.amountMin?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: ""
            amountMax = bill.amountMax?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: ""
            payees.clear()
            payees.addAll(viewModel.getPayeesForBill(bill.id).map { PayeeDraft(it.name, it.sharePercent) })
            isSplitBill = payees.isNotEmpty()
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

            // Quick-add templates (only when adding new bill)
            if (!isEditing) {
                Text("Quick Add", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    billTemplates.forEach { template ->
                        SuggestionChip(
                            onClick = {
                                name = template.name
                                category = template.category
                                recurrence = template.recurrence
                                selectedColor = CategoryColors[template.category.ordinal % CategoryColors.size].value.toLong()
                                template.suggestedAmount?.let {
                                    amount = it.toBigDecimal().stripTrailingZeros().toPlainString()
                                }
                            },
                            label = { Text(template.name) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = CatSurface0,
                                labelColor = CatSubtext0
                            ),
                            border = null
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bill Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = billFieldColors()
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { v -> if (v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = v },
                label = { Text(if (isVariableAmount) "Expected Amount ($)" else "Amount ($)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text("$", color = CatSubtext0) },
                colors = billFieldColors()
            )

            // Variable amount toggle + range
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Variable Amount", color = CatText, style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isVariableAmount,
                    onCheckedChange = {
                        isVariableAmount = it
                        amountRangeError = null
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CatCrust, checkedTrackColor = CatBlue,
                        uncheckedThumbColor = CatOverlay0, uncheckedTrackColor = CatSurface1
                    )
                )
            }

            if (isVariableAmount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = amountMin,
                        onValueChange = { v -> if (v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amountMin = v },
                        label = { Text("Min ($)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Text("$", color = CatSubtext0) },
                        colors = billFieldColors()
                    )
                    OutlinedTextField(
                        value = amountMax,
                        onValueChange = { v -> if (v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amountMax = v },
                        label = { Text("Max ($)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Text("$", color = CatSubtext0) },
                        colors = billFieldColors()
                    )
                }
                amountRangeError?.let {
                    Text(
                        text = it,
                        color = CatRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Split Bill", color = CatText, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Assign the bill across payees by percentage",
                        color = CatSubtext0,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isSplitBill,
                    onCheckedChange = {
                        isSplitBill = it
                        splitError = null
                        if (it && payees.isEmpty()) payees.add(PayeeDraft("", 100.0))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CatCrust, checkedTrackColor = CatBlue,
                        uncheckedThumbColor = CatOverlay0, uncheckedTrackColor = CatSurface1
                    )
                )
            }

            if (isSplitBill) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    payees.forEachIndexed { index, payee ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = payee.name,
                                onValueChange = { value ->
                                    payees[index] = payee.copy(name = value)
                                    splitError = null
                                },
                                label = { Text("Payee") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f),
                                colors = billFieldColors()
                            )
                            OutlinedTextField(
                                value = if (payee.sharePercent == 0.0) "" else payee.sharePercent.toBigDecimal().stripTrailingZeros().toPlainString(),
                                onValueChange = { value ->
                                    if (value.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                                        payees[index] = payee.copy(sharePercent = value.toDoubleOrNull() ?: 0.0)
                                        splitError = null
                                    }
                                },
                                label = { Text("Share %") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(0.8f),
                                colors = billFieldColors()
                            )
                            IconButton(onClick = {
                                payees.removeAt(index)
                                splitError = null
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove payee", tint = CatRed)
                            }
                        }
                        if (amount.toDoubleOrNull() != null) {
                            Text(
                                "${payee.name.ifBlank { "Payee ${index + 1}" }}: $${"%,.2f".format(PayeeMath.shareAmount(amount.toDouble(), payee.sharePercent))}",
                                color = CatSubtext0,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    TextButton(onClick = { payees.add(PayeeDraft("", 0.0)) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add payee", color = CatBlue)
                    }
                    Text(
                        "Total share: ${"%.2f".format(PayeeMath.totalPercent(payees))}%",
                        color = if (PayeeMath.isBalanced(payees)) CatGreen else CatYellow,
                        style = MaterialTheme.typography.labelMedium
                    )
                    splitError?.let {
                        Text(it, color = CatRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

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

            // Category
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

            // Recurrence
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

            // Reminders
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
                        onClick = { secondReminder = null; showSecondReminderMenu = false }
                    )
                    ReminderTiming.entries.forEach { timing ->
                        DropdownMenuItem(
                            text = { Text(timing.label, color = CatText) },
                            onClick = { secondReminder = timing; showSecondReminderMenu = false }
                        )
                    }
                }
            }

            // Tags
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. essential, shared, work", color = CatOverlay0) },
                colors = billFieldColors()
            )

            // Payment URL
            OutlinedTextField(
                value = paymentUrl,
                onValueChange = { paymentUrl = it },
                label = { Text("Payment URL (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://...", color = CatOverlay0) },
                colors = billFieldColors()
            )

            // Toggles
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
                        checkedThumbColor = CatCrust, checkedTrackColor = CatGreen,
                        uncheckedThumbColor = CatOverlay0, uncheckedTrackColor = CatSurface1
                    )
                )
            }

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
                        checkedThumbColor = CatCrust, checkedTrackColor = CatBlue,
                        uncheckedThumbColor = CatOverlay0, uncheckedTrackColor = CatSurface1
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

            // Save
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                    val maxDueValue = if (recurrence == Recurrence.WEEKLY || recurrence == Recurrence.BIWEEKLY) 7 else 31
                    val parsedDay = dueDay.toIntOrNull()?.coerceIn(1, maxDueValue) ?: 1
                    val parsedMin = if (isVariableAmount) amountMin.toDoubleOrNull() else null
                    val parsedMax = if (isVariableAmount) amountMax.toDoubleOrNull() else null
                    if (name.isBlank() || parsedAmount <= 0) return@Button
                    if (isVariableAmount) {
                        val validationError = BillValidation.variableAmountError(parsedAmount, parsedMin, parsedMax)
                        if (validationError != null) {
                            amountRangeError = validationError
                            return@Button
                        }
                        amountRangeError = null
                    }
                    if (isSplitBill) {
                        when {
                            payees.any { it.name.isBlank() } -> {
                                splitError = "Enter a name for every payee"
                                return@Button
                            }
                            payees.any { it.sharePercent <= 0.0 } -> {
                                splitError = "Each payee must have a positive share"
                                return@Button
                            }
                            !PayeeMath.isBalanced(payees) -> {
                                splitError = "Payee shares must total 100%"
                                return@Button
                            }
                            else -> splitError = null
                        }
                    }

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
                        tags = tags.trim(),
                        paymentUrl = paymentUrl.trim(),
                        reminderTiming = reminderTiming,
                        secondReminderTiming = secondReminder,
                        isEnabled = isEnabled,
                        color = selectedColor,
                        isVariableAmount = isVariableAmount,
                        amountMin = parsedMin,
                        amountMax = parsedMax
                    )
                    viewModel.saveBill(bill, if (isSplitBill) payees.toList() else emptyList())
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CatBlue, contentColor = CatCrust),
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
