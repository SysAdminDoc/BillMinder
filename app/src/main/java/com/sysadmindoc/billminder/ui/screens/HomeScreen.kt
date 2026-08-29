package com.sysadmindoc.billminder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.BillCategory
import com.sysadmindoc.billminder.data.SortMode
import com.sysadmindoc.billminder.ui.components.BillCard
import com.sysadmindoc.billminder.ui.components.GroupDivider
import com.sysadmindoc.billminder.ui.components.GroupedSurface
import com.sysadmindoc.billminder.ui.components.MarkPaidDialog
import com.sysadmindoc.billminder.ui.components.SectionHeading
import com.sysadmindoc.billminder.ui.components.SummaryCard
import com.sysadmindoc.billminder.ui.theme.CatBlue
import com.sysadmindoc.billminder.ui.theme.CatCrust
import com.sysadmindoc.billminder.ui.theme.CatDivider
import com.sysadmindoc.billminder.ui.theme.CatGreen
import com.sysadmindoc.billminder.ui.theme.CatOverlay0
import com.sysadmindoc.billminder.ui.theme.CatRed
import com.sysadmindoc.billminder.ui.theme.CatSubtext0
import com.sysadmindoc.billminder.ui.theme.CatSurface0
import com.sysadmindoc.billminder.ui.theme.CatText
import com.sysadmindoc.billminder.ui.theme.CatYellow
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: BillViewModel,
    onAddBill: () -> Unit,
    onBillTap: (Long) -> Unit,
    onEditBill: (Long) -> Unit
) {
    val billsWithStatus by viewModel.billsWithStatus.collectAsState()
    val summary by viewModel.monthlySummary.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val filterCategory by viewModel.filterCategory.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMarkPaidDialog by remember { mutableStateOf<Bill?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    showMarkPaidDialog?.let { bill ->
        MarkPaidDialog(
            bill = bill,
            onDismiss = { showMarkPaidDialog = null },
            onConfirm = { amount, confirmation, paidAt, attachment ->
                viewModel.markAsPaid(bill, amount, confirmation, attachment, paidAt)
                showMarkPaidDialog = null
            }
        )
    }

    val overdue = billsWithStatus.filter { it.isOverdue }
    val upcoming = billsWithStatus.filter { !it.isPaidThisCycle && !it.isOverdue }
    val paid = billsWithStatus.filter { it.isPaidThisCycle }

    Scaffold(
        containerColor = CatCrust,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CatSurface0,
                    contentColor = CatText,
                    actionColor = CatBlue
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeHeader(
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSearchToggle = {
                        if (showSearch) viewModel.setSearchQuery("")
                        showSearch = !showSearch
                    },
                    onAddBill = onAddBill,
                    showSortMenu = showSortMenu,
                    onShowSortMenuChange = { showSortMenu = it },
                    sortMode = sortMode,
                    filterCategory = filterCategory,
                    onSortModeChange = viewModel::setSortMode,
                    onCategoryChange = viewModel::setFilterCategory
                )
            }

            item {
                SummaryCard(
                    summary = summary,
                    onMarkNextPaid = { next -> viewModel.markAsPaid(next.bill) }
                )
            }

            item { WeekHorizon(billsWithStatus = billsWithStatus) }

            if (billsWithStatus.isEmpty()) {
                item {
                    EmptyBillsState(
                        hasFilter = searchQuery.isNotBlank() || filterCategory != null,
                        onAddBill = onAddBill
                    )
                }
            }

            if (overdue.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "Needs attention",
                        color = CatRed,
                        trailing = { BillCount(overdue.size) }
                    )
                    Spacer(Modifier.height(8.dp))
                    BillGroup(
                        bills = overdue,
                        onBillTap = onBillTap,
                        onMarkPaid = viewModel::markAsPaid,
                        onCustomPayment = { showMarkPaidDialog = it },
                        onDelete = viewModel::deleteBill
                    )
                }
            }

            if (upcoming.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "Coming up",
                        trailing = { BillCount(upcoming.size) }
                    )
                    Spacer(Modifier.height(8.dp))
                    BillGroup(
                        bills = upcoming,
                        onBillTap = onBillTap,
                        onMarkPaid = viewModel::markAsPaid,
                        onCustomPayment = { showMarkPaidDialog = it },
                        onDelete = viewModel::deleteBill
                    )
                }
            }

            if (paid.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "Paid",
                        color = CatGreen,
                        trailing = { BillCount(paid.size) }
                    )
                    Spacer(Modifier.height(8.dp))
                    BillGroup(
                        bills = paid,
                        onBillTap = onBillTap,
                        onMarkPaid = viewModel::unmarkAsPaid,
                        onCustomPayment = {},
                        onDelete = viewModel::deleteBill
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun BillCount(count: Int) {
    Text(
        "$count bill${if (count == 1) "" else "s"}",
        color = CatSubtext0,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun HomeHeader(
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onAddBill: () -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    sortMode: SortMode,
    filterCategory: BillCategory?,
    onSortModeChange: (SortMode) -> Unit,
    onCategoryChange: (BillCategory?) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "BillMinder",
                color = CatText,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddBill) {
                Icon(Icons.Filled.Add, "Add bill", tint = CatBlue)
            }
            IconButton(onClick = onSearchToggle) {
                Icon(
                    if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                    if (showSearch) "Close search" else "Search bills",
                    tint = CatBlue
                )
            }
            Box {
                IconButton(onClick = { onShowSortMenuChange(true) }) {
                    Icon(Icons.Filled.SwapVert, "Sort and filter", tint = CatBlue)
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onShowSortMenuChange(false) },
                    containerColor = CatSurface0
                ) {
                    Text(
                        "Sort",
                        color = CatSubtext0,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label, color = if (mode == sortMode) CatBlue else CatText) },
                            onClick = {
                                onSortModeChange(mode)
                                onShowSortMenuChange(false)
                            },
                            leadingIcon = if (mode == sortMode) {
                                { Icon(Icons.Filled.Check, null, tint = CatBlue) }
                            } else null
                        )
                    }
                    HorizontalDivider(color = CatDivider)
                    Text(
                        "Category",
                        color = CatSubtext0,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    DropdownMenuItem(
                        text = { Text("All categories", color = if (filterCategory == null) CatBlue else CatText) },
                        onClick = {
                            onCategoryChange(null)
                            onShowSortMenuChange(false)
                        }
                    )
                    BillCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.label, color = if (category == filterCategory) CatBlue else CatText) },
                            onClick = {
                                onCategoryChange(category)
                                onShowSortMenuChange(false)
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search bills", color = CatOverlay0) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = CatBlue) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CatText,
                    unfocusedTextColor = CatText,
                    focusedBorderColor = CatBlue,
                    unfocusedBorderColor = CatDivider,
                    cursorColor = CatBlue
                )
            )
        }
    }
}

@Composable
private fun WeekHorizon(billsWithStatus: List<BillWithStatus>) {
    val dayName = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val days = remember(today.timeInMillis) {
        List(7) { offset ->
            Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_MONTH, offset)
            }
        }
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEachIndexed { index, day ->
                val matching = billsWithStatus.filter { status ->
                    val due = Calendar.getInstance().apply { timeInMillis = status.nextDueDate }
                    due.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
                        due.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        dayName.format(day.time).uppercase(Locale.getDefault()),
                        color = if (index == 0) CatBlue else CatSubtext0,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        day.get(Calendar.DAY_OF_MONTH).toString(),
                        color = if (index == 0) CatBlue else CatText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(5.dp))
                    if (index == 0) {
                        Box(Modifier.width(28.dp).height(3.dp).background(CatBlue, RoundedCornerShape(1.dp)))
                    } else if (matching.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            matching.take(2).forEach { status ->
                                val marker = when {
                                    status.isPaidThisCycle -> CatGreen
                                    status.isOverdue -> CatRed
                                    status.daysUntilDue <= 1 -> CatYellow
                                    else -> Color(status.bill.color)
                                }
                                Box(Modifier.size(6.dp).clip(CircleShape).background(marker))
                            }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = CatDivider)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillGroup(
    bills: List<BillWithStatus>,
    onBillTap: (Long) -> Unit,
    onMarkPaid: (Bill) -> Unit,
    onCustomPayment: (Bill) -> Unit,
    onDelete: (Bill) -> Unit
) {
    GroupedSurface(contentPadding = PaddingValues(0.dp)) {
        bills.forEachIndexed { index, status ->
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            onDelete(status.bill)
                            true
                        } else {
                            false
                        }
                    }
                ),
                backgroundContent = { SwipeDeleteBackground() },
                enableDismissFromStartToEnd = false
            ) {
                BillCard(
                    billWithStatus = status,
                    onTap = { onBillTap(status.bill.id) },
                    onMarkPaid = { onMarkPaid(status.bill) },
                    onLongPressPaid = { onCustomPayment(status.bill) }
                )
            }
            if (index < bills.lastIndex) GroupDivider(modifier = Modifier.padding(horizontal = 14.dp))
        }
    }
}

@Composable
private fun EmptyBillsState(hasFilter: Boolean, onAddBill: () -> Unit) {
    GroupedSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (hasFilter) "No bills match this view" else "No bills yet",
                color = CatText,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasFilter) "Try a different search or category." else "Add your first bill to start the monthly timeline.",
                color = CatSubtext0,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!hasFilter) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAddBill,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CatBlue,
                        contentColor = CatCrust
                    )
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add bill")
                }
            }
        }
    }
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CatRed.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Filled.Delete, "Delete", tint = CatCrust)
    }
}
