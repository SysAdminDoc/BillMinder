package com.sysadmindoc.billminder.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.BillCategory
import com.sysadmindoc.billminder.data.SortMode
import com.sysadmindoc.billminder.ui.components.BillCard
import com.sysadmindoc.billminder.ui.components.MarkPaidDialog
import com.sysadmindoc.billminder.ui.components.SummaryCard
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val scope = rememberCoroutineScope()

    // Listen for delete events
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    // Mark paid dialog
    showMarkPaidDialog?.let { bill ->
        MarkPaidDialog(
            bill = bill,
            onDismiss = { showMarkPaidDialog = null },
            onConfirm = { amount, conf ->
                viewModel.markAsPaid(bill, amount, conf)
                showMarkPaidDialog = null
            }
        )
    }

    Scaffold(
        containerColor = CatCrust,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CatSurface0,
                    contentColor = CatText,
                    actionColor = CatBlue
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBill,
                containerColor = CatBlue,
                contentColor = CatCrust
            ) {
                Icon(Icons.Filled.Add, "Add bill")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search + sort bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = showSearch,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search bills...", color = CatOverlay0) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CatText,
                                unfocusedTextColor = CatText,
                                focusedBorderColor = CatBlue,
                                unfocusedBorderColor = CatSurface1,
                                cursorColor = CatBlue
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    showSearch = false
                                }) {
                                    Icon(Icons.Filled.Close, "Clear", tint = CatSubtext0)
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    if (!showSearch) {
                        Spacer(Modifier.weight(1f))
                    }

                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, "Search", tint = CatSubtext0)
                        }
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.SwapVert, "Sort", tint = CatSubtext0)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor = CatSurface0
                        ) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            mode.label,
                                            color = if (mode == sortMode) CatBlue else CatText
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortMode(mode)
                                        showSortMenu = false
                                    },
                                    leadingIcon = if (mode == sortMode) {
                                        { Icon(Icons.Filled.Check, null, tint = CatBlue, modifier = Modifier.size(18.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // Category filter chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { viewModel.setFilterCategory(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CatBlue.copy(alpha = 0.2f),
                            selectedLabelColor = CatBlue,
                            containerColor = CatSurface0,
                            labelColor = CatSubtext0
                        ),
                        border = null
                    )
                    BillCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = filterCategory == cat,
                            onClick = {
                                viewModel.setFilterCategory(if (filterCategory == cat) null else cat)
                            },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CatBlue.copy(alpha = 0.2f),
                                selectedLabelColor = CatBlue,
                                containerColor = CatSurface0,
                                labelColor = CatSubtext0
                            ),
                            border = null
                        )
                    }
                }
            }

            // Summary card
            item {
                SummaryCard(summary = summary)
                Spacer(Modifier.height(4.dp))
            }

            if (billsWithStatus.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank() || filterCategory != null)
                                "No bills match your search."
                            else
                                "No bills yet.\nTap + to add your first bill.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CatSubtext0,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Overdue section
            val overdue = billsWithStatus.filter { it.isOverdue }
            if (overdue.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Overdue", style = MaterialTheme.typography.labelLarge, color = CatRed,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Spacer(Modifier.width(6.dp))
                        Badge(containerColor = CatRed, contentColor = CatCrust) { Text("${overdue.size}") }
                    }
                }
                itemsIndexed(overdue, key = { _, b -> "overdue_${b.bill.id}" }) { index, bws ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { (index + 1) * 40 })
                    ) {
                        SwipeToDismissBox(
                            state = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteBill(bws.bill); true
                                    } else false
                                }
                            ),
                            backgroundContent = { SwipeDeleteBackground() },
                            enableDismissFromStartToEnd = false
                        ) {
                            BillCard(
                                billWithStatus = bws,
                                onTap = { onBillTap(bws.bill.id) },
                                onMarkPaid = { viewModel.markAsPaid(bws.bill) },
                                onLongPressPaid = { showMarkPaidDialog = bws.bill }
                            )
                        }
                    }
                }
            }

            // Upcoming section
            val upcoming = billsWithStatus.filter { !it.isPaidThisCycle && !it.isOverdue }
            if (upcoming.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Upcoming", style = MaterialTheme.typography.labelLarge, color = CatSubtext0,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Spacer(Modifier.width(6.dp))
                        Badge(containerColor = CatSurface1, contentColor = CatText) { Text("${upcoming.size}") }
                    }
                }
                itemsIndexed(upcoming, key = { _, b -> "upcoming_${b.bill.id}" }) { index, bws ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { (index + 1) * 40 })
                    ) {
                        SwipeToDismissBox(
                            state = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteBill(bws.bill); true
                                    } else false
                                }
                            ),
                            backgroundContent = { SwipeDeleteBackground() },
                            enableDismissFromStartToEnd = false
                        ) {
                            BillCard(
                                billWithStatus = bws,
                                onTap = { onBillTap(bws.bill.id) },
                                onMarkPaid = { viewModel.markAsPaid(bws.bill) },
                                onLongPressPaid = { showMarkPaidDialog = bws.bill }
                            )
                        }
                    }
                }
            }

            // Paid section
            val paid = billsWithStatus.filter { it.isPaidThisCycle }
            if (paid.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Paid", style = MaterialTheme.typography.labelLarge, color = CatGreen,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Spacer(Modifier.width(6.dp))
                        Badge(containerColor = CatGreen.copy(alpha = 0.2f), contentColor = CatGreen) { Text("${paid.size}") }
                    }
                }
                itemsIndexed(paid, key = { _, b -> "paid_${b.bill.id}" }) { _, bws ->
                    BillCard(
                        billWithStatus = bws,
                        onTap = { onBillTap(bws.bill.id) },
                        onMarkPaid = { viewModel.unmarkAsPaid(bws.bill) },
                        onLongPressPaid = null
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CatRed, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Filled.Delete, "Delete", tint = CatCrust)
    }
}
