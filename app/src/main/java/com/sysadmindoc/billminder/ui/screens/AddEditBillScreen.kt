package com.sysadmindoc.billminder.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.data.*
import com.sysadmindoc.billminder.domain.CycleEngine
import com.sysadmindoc.billminder.ui.components.GroupDivider
import com.sysadmindoc.billminder.ui.components.GroupedSurface
import com.sysadmindoc.billminder.ui.components.SectionHeading
import com.sysadmindoc.billminder.ui.components.SettingsStyleRow
import com.sysadmindoc.billminder.ui.components.SquareToggle
import com.sysadmindoc.billminder.ui.components.getCategoryIcon
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
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
    var currency by remember { mutableStateOf("USD") }
    var firstDueDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
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
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var amountMin by remember { mutableStateOf("") }
    var amountMax by remember { mutableStateOf("") }
    var amountRangeError by remember { mutableStateOf<String?>(null) }
    var isSplitBill by remember { mutableStateOf(false) }
    var splitError by remember { mutableStateOf<String?>(null) }
    val payees = remember { mutableStateListOf<PayeeDraft>() }
    var selectedColor by remember { mutableLongStateOf(CatBlue.value.toLong()) }
    var isLoaded by remember { mutableStateOf(billId == null) }
    var loadedBill by remember { mutableStateOf<Bill?>(null) }

    val isEditing = billId != null && billId != 0L

    LaunchedEffect(billId) {
        if (billId != null && billId != 0L) {
            val bill = viewModel.getBillById(billId) ?: return@LaunchedEffect
            name = bill.name
            amount = bill.amount.toBigDecimal().stripTrailingZeros().toPlainString()
            currency = CurrencyCatalog.find(bill.currency).code
            loadedBill = bill
            firstDueDate = CycleEngine.anchor(bill)
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
            showAdvancedOptions = bill.isVariableAmount || payees.isNotEmpty()
            selectedColor = bill.color
            isLoaded = true
        }
    }

    var showCategoryMenu by remember { mutableStateOf(false) }
    var showRecurrenceMenu by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }
    var showSecondReminderMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }

    fun saveBill() {
        val parsedAmount = amount.toDoubleOrNull() ?: 0.0
        val parsedMin = if (isVariableAmount) amountMin.toDoubleOrNull() else null
        val parsedMax = if (isVariableAmount) amountMax.toDoubleOrNull() else null
        if (name.isBlank() || parsedAmount <= 0) return
        if (isVariableAmount) {
            val validationError = BillValidation.variableAmountError(parsedAmount, parsedMin, parsedMax)
            if (validationError != null) {
                amountRangeError = validationError
                return
            }
            amountRangeError = null
        }
        if (isSplitBill) {
            when {
                payees.any { it.name.isBlank() } -> {
                    splitError = "Enter a name for every payee"
                    return
                }
                payees.any { it.sharePercent <= 0.0 } -> {
                    splitError = "Each payee must have a positive share"
                    return
                }
                !PayeeMath.isBalanced(payees) -> {
                    splitError = "Payee shares must total 100%"
                    return
                }
                else -> splitError = null
            }
        }

        val bill = Bill(
            id = if (isEditing) billId!! else 0,
            name = name.trim(),
            amount = parsedAmount,
            // When the date has not moved, keep the day the user originally asked for. A bill set
            // to the 31st is anchored on a short month's last day, and re-deriving from the anchor
            // would quietly rewrite the schedule to that shorter day.
            dueDay = CycleEngine.legacyDueDay(recurrence, firstDueDate, requestedDueDay(loadedBill, firstDueDate)),
            dueMonth = firstDueDate.monthValue - 1,
            dueYear = firstDueDate.year,
            anchorEpochDay = firstDueDate.toEpochDay(),
            createdAt = loadedBill?.createdAt ?: System.currentTimeMillis(),
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
            amountMax = parsedMax,
            currency = currency
        )
        viewModel.saveBill(bill, if (isSplitBill) payees.toList() else emptyList())
        onNavigateBack()
    }

    Scaffold(
        containerColor = CatCrust,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit bill" else "Add bill", color = CatText, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CatCrust),
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CatText)
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::saveBill,
                        enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
                    ) {
                        Text("Save", color = if (name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0) CatBlue else CatOverlay0)
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Quick-add templates (only when adding new bill)
            if (!isEditing) {
                val quickTemplates = listOf(
                    BillTemplate("Rent", BillCategory.RENT, null),
                    BillTemplate("Electric", BillCategory.UTILITIES, null),
                    BillTemplate("Internet", BillCategory.PHONE, null),
                    BillTemplate("Insurance", BillCategory.INSURANCE, null),
                    BillTemplate("Netflix", BillCategory.SUBSCRIPTION, 15.49),
                    BillTemplate("Other", BillCategory.OTHER, null)
                )
                SectionHeading("Quick add")
                GroupedSurface(contentPadding = PaddingValues(6.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickTemplates.chunked(3).forEach { templates ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                templates.forEach { template ->
                                    val isSelected = name == template.name && category == template.category
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable {
                                                name = template.name
                                                category = template.category
                                                recurrence = template.recurrence
                                                selectedColor = CategoryColors[template.category.ordinal % CategoryColors.size].value.toLong()
                                                template.suggestedAmount?.let {
                                                    amount = it.toBigDecimal().stripTrailingZeros().toPlainString()
                                                }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) CatBlue.copy(alpha = 0.11f) else CatSurface0,
                                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) CatBlue else CatDivider)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                getCategoryIcon(template.category),
                                                contentDescription = null,
                                                tint = CategoryColors[template.category.ordinal % CategoryColors.size],
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                template.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = CatText,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SectionHeading("Essentials")
            GroupedSurface(contentPadding = PaddingValues(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bill Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = billFieldColors()
            )
            val normalizedName = MerchantNormalizer.normalize(name)
            if (name.isNotBlank() && normalizedName != name.trim()) {
                Text(
                    "Recognized merchant: $normalizedName",
                    color = CatBlue,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { v -> if (v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = v },
                label = { Text(if (isVariableAmount) "Expected Amount ($currency)" else "Amount ($currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text(CurrencyCatalog.find(currency).symbol.trim(), color = CatSubtext0) },
                colors = billFieldColors()
            )

            Box {
                OutlinedTextField(
                    value = "${currency} · ${CurrencyCatalog.find(currency).name}",
                    onValueChange = {},
                    label = { Text("Bill Currency") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { showCurrencyMenu = true },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = CatSubtext0) },
                    colors = billFieldColors(),
                    enabled = false
                )
                DropdownMenu(
                    expanded = showCurrencyMenu,
                    onDismissRequest = { showCurrencyMenu = false },
                    containerColor = CatSurface0
                ) {
                    CurrencyCatalog.supported.forEach { info ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${info.code} · ${info.name}",
                                    color = if (info.code == currency) CatBlue else CatText
                                )
                            },
                            onClick = {
                                currency = info.code
                                showCurrencyMenu = false
                            }
                        )
                    }
                }
            }

            // Category stays with the core bill identity, matching the ledger form hierarchy.
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

            SettingsStyleRow(
                title = "Advanced options",
                subtitle = "Variable amounts and split bills",
                icon = Icons.Filled.Tune,
                onClick = { showAdvancedOptions = !showAdvancedOptions }
            ) {
                Icon(
                    if (showAdvancedOptions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = CatOverlay0
                )
            }

            if (showAdvancedOptions) {
            SettingsStyleRow(
                title = "Variable amount",
                subtitle = "Track an expected value and allowed range",
                icon = Icons.Filled.Tune
            ) {
                SquareToggle(
                    checked = isVariableAmount,
                    onCheckedChange = {
                        isVariableAmount = it
                        amountRangeError = null
                    }
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
                        label = { Text("Min ($currency)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Text(CurrencyCatalog.find(currency).symbol.trim(), color = CatSubtext0) },
                        colors = billFieldColors()
                    )
                    OutlinedTextField(
                        value = amountMax,
                        onValueChange = { v -> if (v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amountMax = v },
                        label = { Text("Max ($currency)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Text(CurrencyCatalog.find(currency).symbol.trim(), color = CatSubtext0) },
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

            SettingsStyleRow(
                title = "Split bill",
                subtitle = "Assign the bill across payees by percentage",
                icon = Icons.AutoMirrored.Filled.CallSplit
            ) {
                SquareToggle(
                    checked = isSplitBill,
                    onCheckedChange = {
                        isSplitBill = it
                        splitError = null
                        if (it && payees.isEmpty()) payees.add(PayeeDraft("", 100.0))
                    }
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
                                "${payee.name.ifBlank { "Payee ${index + 1}" }}: ${CurrencyFormatter.format(PayeeMath.shareAmount(amount.toDouble(), payee.sharePercent), currency)}",
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
            }
            }
            }

            SectionHeading("Schedule")
            GroupedSurface(contentPadding = PaddingValues(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            OutlinedTextField(
                value = firstDueDate.format(anchorDateFormat),
                onValueChange = {},
                label = {
                    Text(
                        if (recurrence == Recurrence.ONE_TIME) "Due date" else "First due date"
                    )
                },
                readOnly = true,
                singleLine = true,
                enabled = false,
                trailingIcon = { Icon(Icons.Filled.CalendarMonth, null, tint = CatSubtext0) },
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                colors = billFieldColors()
            )
            Text(
                text = anchorHint(recurrence, firstDueDate, CycleEngine.legacyDueDay(recurrence, firstDueDate, requestedDueDay(loadedBill, firstDueDate))),
                style = MaterialTheme.typography.bodySmall,
                color = CatSubtext0
            )

            }
            }

            SectionHeading("Payment & reminders")
            GroupedSurface(contentPadding = PaddingValues(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsStyleRow(
                title = "Auto-pay",
                subtitle = "Show this bill as paid automatically",
                icon = Icons.Filled.Payments
            ) {
                SquareToggle(
                    checked = isAutoPay,
                    onCheckedChange = { isAutoPay = it }
                )
            }
            GroupDivider()
            SettingsStyleRow(
                title = "Reminders",
                subtitle = "Notify me before this bill is due",
                icon = Icons.Filled.NotificationsActive
            ) {
                SquareToggle(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it }
                )
            }

            Box {
                OutlinedTextField(
                    value = reminderTiming.label,
                    onValueChange = {},
                    label = { Text("Reminder timing") },
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
                    label = { Text("Second reminder (optional)") },
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
            }
            }

            SectionHeading("Additional details")
            GroupedSurface(contentPadding = PaddingValues(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = paymentUrl,
                onValueChange = { paymentUrl = it },
                label = { Text("Payment URL (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://...", color = CatOverlay0) },
                colors = billFieldColors()
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. essential, shared, work", color = CatOverlay0) },
                colors = billFieldColors()
            )

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
                            .background(color, RoundedCornerShape(8.dp))
                            .then(
                                if (color.value.toLong() == selectedColor)
                                    Modifier.border(3.dp, CatText, RoundedCornerShape(8.dp))
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
            }
            }

            // Save
            Button(
                onClick = ::saveBill,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CatBlue, contentColor = CatCrust),
                shape = RoundedCornerShape(8.dp),
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text(
                    if (isEditing) "Save changes" else "Save bill",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = firstDueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = EARLIEST_DUE_DATE.year..(LocalDate.now().year + 30),
            selectableDates = object : SelectableDates {
                // Zero epoch day doubles as "no anchor stored", so the picker starts after it.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= EARLIEST_DUE_DATE.atStartOfDay(ZoneOffset.UTC)
                        .toInstant().toEpochMilli()

                override fun isSelectableYear(year: Int): Boolean = year >= EARLIEST_DUE_DATE.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = DatePickerDefaults.colors(containerColor = CatSurface0),
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        firstDueDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Set", color = CatBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = CatSubtext0) }
            }
        ) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = CatSurface0))
        }
    }
}

/**
 * The day of the month the user asked for. An unchanged date keeps whatever the bill already
 * stored, so a bill due on the 31st does not become one due on the 28th just because it was opened.
 */
private fun requestedDueDay(loaded: Bill?, picked: LocalDate): Int =
    if (loaded != null && loaded.anchorEpochDay == picked.toEpochDay()) {
        loaded.dueDay
    } else {
        picked.dayOfMonth
    }

/** Plain-language summary of what the picked date means for the chosen recurrence. */
private fun anchorHint(recurrence: Recurrence, date: LocalDate, dueDay: Int): String = when (recurrence) {
    Recurrence.ONE_TIME -> "Due once on ${date.format(anchorDateFormat)}."
    Recurrence.WEEKLY -> "Repeats every ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}."
    Recurrence.BIWEEKLY ->
        "Repeats every other ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}."
    Recurrence.MONTHLY -> "Repeats on day $dueDay of each month."
    Recurrence.QUARTERLY -> "Repeats on day $dueDay every three months."
    Recurrence.YEARLY ->
        "Repeats every ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $dueDay."
}

private val anchorDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Earliest date a bill can be anchored to. Epoch day zero means "no anchor stored". */
private val EARLIEST_DUE_DATE: LocalDate = LocalDate.of(1971, 1, 1)

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
    cursorColor = CatBlue,
    focusedContainerColor = CatBase,
    unfocusedContainerColor = CatBase,
    disabledContainerColor = CatBase
)
