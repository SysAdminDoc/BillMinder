package com.sysadmindoc.billminder.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.ui.components.BillCard
import com.sysadmindoc.billminder.ui.components.SummaryCard
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import com.sysadmindoc.billminder.viewmodel.BillWithStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: BillViewModel,
    onAddBill: () -> Unit,
    onBillTap: (Long) -> Unit,
    onCalendar: () -> Unit
) {
    val billsWithStatus by viewModel.billsWithStatus.collectAsState()
    val summary by viewModel.monthlySummary.collectAsState()

    Scaffold(
        containerColor = CatCrust,
        topBar = {
            TopAppBar(
                title = { Text("BillMinder", color = CatText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CatCrust),
                actions = {
                    IconButton(onClick = onCalendar) {
                        Icon(Icons.Filled.CalendarMonth, "Calendar", tint = CatSubtext0)
                    }
                }
            )
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
            item {
                SummaryCard(summary = summary)
                Spacer(Modifier.height(8.dp))
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
                            text = "No bills yet.\nTap + to add your first bill.",
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
                    Text(
                        "Overdue",
                        style = MaterialTheme.typography.labelLarge,
                        color = CatRed,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(overdue, key = { _, b -> "overdue_${b.bill.id}" }) { index, bws ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
                    ) {
                        BillCard(
                            billWithStatus = bws,
                            onTap = { onBillTap(bws.bill.id) },
                            onMarkPaid = { viewModel.markAsPaid(bws.bill) }
                        )
                    }
                }
            }

            // Upcoming section
            val upcoming = billsWithStatus.filter { !it.isPaidThisCycle && !it.isOverdue }
            if (upcoming.isNotEmpty()) {
                item {
                    Text(
                        "Upcoming",
                        style = MaterialTheme.typography.labelLarge,
                        color = CatSubtext0,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(upcoming, key = { _, b -> "upcoming_${b.bill.id}" }) { index, bws ->
                    BillCard(
                        billWithStatus = bws,
                        onTap = { onBillTap(bws.bill.id) },
                        onMarkPaid = { viewModel.markAsPaid(bws.bill) }
                    )
                }
            }

            // Paid section
            val paid = billsWithStatus.filter { it.isPaidThisCycle }
            if (paid.isNotEmpty()) {
                item {
                    Text(
                        "Paid",
                        style = MaterialTheme.typography.labelLarge,
                        color = CatGreen,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(paid, key = { _, b -> "paid_${b.bill.id}" }) { index, bws ->
                    BillCard(
                        billWithStatus = bws,
                        onTap = { onBillTap(bws.bill.id) },
                        onMarkPaid = { viewModel.unmarkAsPaid(bws.bill) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
